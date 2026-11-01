package net.wingress.mobivious.data

import java.io.IOException
import java.net.ProtocolException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

enum class AccessReason(val message: String) {
    CLOUDFLARE("Cloudflare requires verification to access this instance."),
    LOGIN("This instance requires sign-in."),
    VERIFICATION("This instance requires verification."),
    UNAVAILABLE("Verification is temporarily unavailable. Try again.")
}

class InstanceAccessException(val reason: AccessReason, val context: ApiContext? = null,
    internal val revision: Long? = null) : IOException(reason.message)
data class VerificationPass(val token: String, val expiresAt: Long)
data class AccessIssue(val context: ApiContext, val reason: AccessReason)
internal data class VerificationRecovery(val context: ApiContext, val revision: Long)
private class SupersededAccessException : ProtocolException("Instance access changed. Retry the action.")
private data class VerificationProbe(val status: VerificationStatus?, val edge: CloudflareClearance?)
data class VerificationStatus(val enabled: Boolean, val required: Boolean, val verified: Boolean, val expiresAt: Long?) {
    companion object {
        fun parse(value: JSONObject) = VerificationStatus(value.getBoolean("enabled"), value.getBoolean("required"),
            value.getBoolean("verified"), if (value.isNull("expiresAt")) null else value.getLong("expiresAt"))
    }
}

/** Pass persistence is separate from account/profile persistence. Implementations must encrypt tokens. */
interface VerificationPersistence {
    fun read(origin: String): VerificationPass?
    fun write(origin: String, pass: VerificationPass?)
}

/** One process-wide gate. Requests carry their original identity through every redirect. */
class InstanceAccess(private val current: () -> ApiContext, private val persistence: VerificationPersistence,
    private val clearancePersistence: CloudflarePersistence = MemoryCloudflarePersistence(),
    private val userAgent: () -> String = { "" },
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }) {
    val issue = MutableStateFlow<AccessIssue?>(null)
    val revision = MutableStateFlow(0L)
    internal val recovery = MutableStateFlow<VerificationRecovery?>(null)
    val promptRevision = MutableStateFlow(0L)
    fun prompt() { promptRevision.value++ }
    private val rejected = mutableSetOf<String>()
    private val rejectedClearance = mutableSetOf<String>()
    private val sessionClearance = mutableMapOf<String, CloudflareClearance>()
    private var reauthentication: Pair<ApiContext, Account>? = null
    private val statusClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    companion object {
        const val HEADER = "X-Invidious-Verification"
        internal const val CONTEXT_HEADER = "X-Mobivious-Request-Context"
        fun origin(url: HttpUrl) = "${url.scheme}://${url.host.let { if (':' in it) "[$it]" else it }}:${url.port}"
        fun sameOrigin(url: HttpUrl, server: String) = origin(url) == origin(server.toHttpUrl())
        fun classify(code: Int, header: String?, body: String, mitigated: String? = null): AccessReason? {
            if (mitigated.equals("challenge", ignoreCase = true)) return AccessReason.CLOUDFLARE
            val error = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
            return when {
                code == 401 && error == "login_required" -> AccessReason.LOGIN
                code == 403 && (header in listOf("verification_required", "verification_expired") ||
                    error in listOf("verification_required", "verification_expired")) -> AccessReason.VERIFICATION
                code == 503 && error == "verification_unavailable" -> AccessReason.UNAVAILABLE
                else -> null
            }
        }
        fun failure(error: Throwable): InstanceAccessException? = generateSequence(error) { it.cause }
            .take(12).filterIsInstance<InstanceAccessException>().firstOrNull()
        fun cacheScope(context: ApiContext): String = MessageDigest.getInstance("SHA-256")
            .digest("${context.server}|${context.account?.token.orEmpty()}|${context.generation}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        private fun bootstrap(path: String) = path.startsWith("/api/v1/mobile/") ||
            path == "/api/v1/verification" || path == "/api/v1/verification/complete" || path == "/verify"
    }

    @Synchronized fun pass(context: ApiContext): VerificationPass? {
        val key = origin(context.server.toHttpUrl())
        if (key in rejected) return null
        return persistence.read(key)?.takeIf { it.expiresAt > now() }
    }
    @Synchronized fun clearance(context: ApiContext): CloudflareClearance? {
        val url = context.server.toHttpUrl()
        val key = origin(url)
        if (key in rejectedClearance) return null
        return (sessionClearance[key] ?: clearancePersistence.read(key))?.takeIf { it.usable(url, userAgent(), now()) }
    }
    private fun edgeHeaders(value: CloudflareClearance?, url: HttpUrl): Map<String, String> =
        if (value?.usable(url, userAgent(), now()) == true)
            mapOf("Cookie" to "cf_clearance=${value.cookie.value}", "User-Agent" to value.userAgent) else emptyMap()

    fun headers(url: HttpUrl, context: ApiContext): Map<String, String> {
        if (context != current() || !sameOrigin(url, context.server)) return emptyMap()
        return buildMap {
            putAll(edgeHeaders(clearance(context), url))
            if (!bootstrap(url.encodedPath)) {
                context.account?.takeIf { it.expiresAt > now() && sameOrigin(url, it.server) }?.let { put("Authorization", "Bearer ${it.token}") }
                pass(context)?.let { put(HEADER, it.token) }
            }
        }
    }
    @Synchronized fun report(context: ApiContext, reason: AccessReason, expectedRevision: Long = revision.value): Boolean {
        if (context != current() || expectedRevision != revision.value) return false
        if (reason == AccessReason.VERIFICATION) rejected.add(origin(context.server.toHttpUrl()))
        if (reason == AccessReason.CLOUDFLARE) rejectedClearance.add(origin(context.server.toHttpUrl()))
        // Edge verification precedes the backend's login and verification gates.
        fun priority(value: AccessReason?) = when (value) {
            AccessReason.CLOUDFLARE -> 3; AccessReason.LOGIN -> 2; AccessReason.VERIFICATION -> 1; else -> 0
        }
        if (priority(reason) >= priority(issue.value?.reason)) issue.value = AccessIssue(context, reason)
        return true
    }
    @Synchronized fun clear(context: ApiContext) {
        if (context != current()) throw CancellationException("Account or instance changed")
        issue.value = null; revision.value++; prompt()
    }
    @Synchronized fun contextChanged() { issue.value = null; recovery.value = null; revision.value++; prompt() }
    /** Only a gate-driven sign-in to the same identity may retain the stopped player. */
    @Synchronized fun prepareLogin(context: ApiContext, account: Account) {
        reauthentication = if (issue.value == AccessIssue(context, AccessReason.LOGIN) && context.server == account.server &&
            (context.account == null || context.account.username.equals(account.username, ignoreCase = true) &&
                (context.account.profileId == null || account.profileId == null || context.account.profileId == account.profileId))) context to account else null
    }
    @Synchronized fun retainPlayback(before: ApiContext, after: ApiContext): Boolean {
        val expected = reauthentication
        reauthentication = null
        return expected?.first == before && expected.second == after.account && before.server == after.server
    }
    private fun check(context: ApiContext) {
        if (context != current()) throw CancellationException("Account or instance changed")
    }
    /** Network interceptors run again on redirects, unlike application interceptors. */
    fun client(base: OkHttpClient = statusClient, context: () -> ApiContext = current): OkHttpClient = base.newBuilder()
        .addInterceptor { chain ->
            val target = chain.request().tag(ApiContext::class.java) ?: context()
            val imageContext = chain.request().header(CONTEXT_HEADER)
            if (imageContext != null && imageContext != cacheScope(target)) throw IOException("Account or instance changed")
            val request = chain.request().newBuilder().removeHeader(CONTEXT_HEADER).tag(ApiContext::class.java, target).build()
            try { chain.proceed(request) }
            catch (e: SupersededAccessException) {
                // Repeat only safe reads, once, after closing the obsolete response. Never replay a write.
                if (target != current() || request.method !in listOf("GET", "HEAD")) throw e
                chain.proceed(request)
            }
        }.addNetworkInterceptor(Interceptor { chain ->
            val request = chain.request()
            val target = requireNotNull(request.tag(ApiContext::class.java))
            val (sentRevision, outgoing) = synchronized(this@InstanceAccess) {
                if (target != current()) throw IOException("Account or instance changed")
                val builder = request.newBuilder().removeHeader(HEADER).removeHeader("Authorization").removeHeader("Cookie")
                if (!sameOrigin(request.url, target.server) && request.header("User-Agent") == userAgent()) builder.removeHeader("User-Agent")
                headers(request.url, target).forEach { (key, value) -> builder.header(key, value) }
                revision.value to builder.build()
            }
            val response = chain.proceed(outgoing)
            val reason = if (sameOrigin(request.url, target.server)) {
                if (response.header("cf-mitigated").equals("challenge", true)) AccessReason.CLOUDFLARE
                else if (response.code in listOf(401, 403, 503))
                    classify(response.code, response.header("X-Invidious-Verification-Required"), response.peekBody(8192).string()) else null
            } else null
            if (reason != null) {
                response.close()
                if (!report(target, reason, sentRevision)) throw SupersededAccessException()
                throw InstanceAccessException(reason, target, sentRevision)
            }
            if (target != current()) { response.close(); throw IOException("Account or instance changed") }
            try { synchronized(this@InstanceAccess) {
                if (target == current() && sentRevision == revision.value && outgoing.header("Cookie") != null && sameOrigin(request.url, target.server)) {
                    val updates = response.headers.values("Set-Cookie").mapNotNull { CloudflareClearance.parse(request.url, it, userAgent()) }
                        .filter { it.cookie.matches(target.server.toHttpUrl()) }
                    updates.lastOrNull()?.let { update ->
                        val next = update.takeIf { it.usable(target.server.toHttpUrl(), userAgent(), now()) }
                        if (next != clearance(target)) { saveClearance(target, next); revision.value++ }
                    }
                }
            } } catch (e: Exception) { response.close(); throw e }
            response
        }).build()

    private suspend fun status(context: ApiContext, candidate: VerificationPass?, edge: CloudflareClearance?,
        expectedRevision: Long, reportChallenge: Boolean): VerificationProbe = withContext(Dispatchers.IO) {
        check(context)
        val url = context.server.toHttpUrl().resolve("/api/v1/verification")!!
        val request = Request.Builder().url(url).apply {
            candidate?.let { header(HEADER, it.token) }
            edgeHeaders(edge, url).forEach { (name, value) -> header(name, value) }
        }.build()
        val transport = statusClient.newBuilder().addNetworkInterceptor { chain ->
            synchronized(this) { checkAttempt(context, expectedRevision) }
            val response = chain.proceed(chain.request())
            if (response.header("cf-mitigated").equals("challenge", true)) {
                response.close()
                if (reportChallenge) {
                    if (!report(context, AccessReason.CLOUDFLARE, expectedRevision)) throw IOException("Instance access changed. Retry.")
                    throw InstanceAccessException(AccessReason.CLOUDFLARE, context, expectedRevision)
                }
                throw IOException("Cloudflare did not accept verification for native requests. Retry verification.")
            }
            response
        }.build()
        val response = transport.newCall(request).awaitBody(8192)
        check(context)
        val replacement = if (edge == null) null else response.headers.values("Set-Cookie")
            .mapNotNull { CloudflareClearance.parse(url, it, edge.userAgent) }
            .lastOrNull { it.cookie.matches(context.server.toHttpUrl()) }
        val acceptedEdge = replacement?.takeIf { it.usable(context.server.toHttpUrl(), userAgent(), now()) } ?: edge.takeIf { replacement == null }
        if (response.code in listOf(404, 405)) return@withContext VerificationProbe(null, acceptedEdge)
        if (!response.isSuccessful) throw IOException("Unable to check verification (${response.code}). Try again.")
        VerificationProbe(VerificationStatus.parse(JSONObject(response.body)), acceptedEdge)
    }

    suspend fun discover(context: ApiContext, candidate: VerificationPass? = pass(context)): VerificationStatus? {
        val expectedRevision = revision.value
        val edge = clearance(context)
        val result = status(context, candidate, edge, expectedRevision, true)
        synchronized(this) {
            check(context)
            if (expectedRevision == revision.value) {
                if (result.status?.enabled == false) {
                    val key = origin(context.server.toHttpUrl())
                    if (persistence.read(key) != null) persistence.write(key, null)
                    rejected.remove(key)
                }
                if (result.edge != edge) { saveClearance(context, result.edge); revision.value++ }
            }
        }
        return result.status
    }

    private fun saveClearance(context: ApiContext, value: CloudflareClearance?) {
        val key = origin(context.server.toHttpUrl())
        // Session cookies must never acquire an invented persistent expiry.
        clearancePersistence.write(key, value?.takeIf { it.cookie.persistent })
        if (value != null && !value.cookie.persistent) sessionClearance[key] = value else sessionClearance.remove(key)
        rejectedClearance.remove(key)
    }
    private fun checkAttempt(context: ApiContext, expectedRevision: Long) {
        check(context)
        if (expectedRevision != revision.value) throw IOException("Instance access changed. Retry verification.")
    }
    private fun recovered(context: ApiContext) {
        clear(context)
        recovery.value = VerificationRecovery(context, revision.value)
    }
    /** Cookie appearance is only a candidate. The native transport must pass Cloudflare too. */
    suspend fun completeClearance(context: ApiContext, edge: CloudflareClearance, expectedRevision: Long = revision.value): Boolean {
        checkAttempt(context, expectedRevision)
        require(edge.usable(context.server.toHttpUrl(), userAgent(), now())) { "Invalid Cloudflare clearance. Retry verification." }
        val result = status(context, pass(context), edge, expectedRevision, false)
        currentCoroutineContext().ensureActive()
        synchronized(this) {
            checkAttempt(context, expectedRevision)
            require(result.edge?.usable(context.server.toHttpUrl(), userAgent(), now()) == true) { "Cloudflare clearance expired. Retry verification." }
            saveClearance(context, result.edge)
            if (result.status?.required == true) { revision.value++; return false }
            if (result.status?.enabled == false) {
                persistence.write(origin(context.server.toHttpUrl()), null)
                rejected.remove(origin(context.server.toHttpUrl()))
            }
            recovered(context)
            return true
        }
    }

    suspend fun complete(context: ApiContext, token: String?, expiresAt: Long?, enabled: Boolean,
        edge: CloudflareClearance? = clearance(context), expectedRevision: Long = revision.value) {
        check(context)
        val needsEdge = synchronized(this) { issue.value?.reason == AccessReason.CLOUDFLARE || origin(context.server.toHttpUrl()) in rejectedClearance }
        require(!needsEdge || edge != null) { "Cloudflare has not issued clearance yet. Complete verification or retry." }
        require(edge == null || edge.usable(context.server.toHttpUrl(), userAgent(), now())) { "Invalid Cloudflare clearance. Retry verification." }
        val candidate = if (enabled) {
            require(!token.isNullOrBlank() && token.length <= 2048 && token.none { it <= ' ' || it == '\u007f' } && expiresAt != null && expiresAt > now()) { "Invalid verification response. Retry verification." }
            VerificationPass(token, expiresAt)
        } else null
        val result = status(context, candidate, edge, expectedRevision, true)
        val status = result.status ?: throw IOException("Verification is not available on this instance.")
        check(context)
        require(!status.enabled || candidate != null && status.verified && !status.required && status.expiresAt != null && status.expiresAt > now()) { "Verification was not accepted. Try a fresh challenge." }
        currentCoroutineContext().ensureActive()
        synchronized(this) {
            checkAttempt(context, expectedRevision)
            val key = origin(context.server.toHttpUrl())
            val accepted = if (status.enabled) candidate!!.copy(expiresAt = status.expiresAt!!) else null
            if (recovery.value?.context == context && issue.value == null && persistence.read(key) == accepted && key !in rejected) return
            if (edge != null) {
                require(result.edge?.usable(context.server.toHttpUrl(), userAgent(), now()) == true) { "Cloudflare clearance expired. Retry verification." }
                saveClearance(context, result.edge)
            }
            persistence.write(key, accepted)
            rejected.remove(key); recovered(context)
        }
    }
}
