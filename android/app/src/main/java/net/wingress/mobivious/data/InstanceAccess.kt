package net.wingress.mobivious.data

import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

enum class AccessReason(val message: String) {
    LOGIN("This instance requires sign-in."),
    VERIFICATION("This instance requires verification."),
    UNAVAILABLE("Verification is temporarily unavailable. Try again.")
}

class InstanceAccessException(val reason: AccessReason, val context: ApiContext? = null) : IOException(reason.message)
data class VerificationPass(val token: String, val expiresAt: Long)
data class AccessIssue(val context: ApiContext, val reason: AccessReason)
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
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }) {
    val issue = MutableStateFlow<AccessIssue?>(null)
    val revision = MutableStateFlow(0L)
    val promptRevision = MutableStateFlow(0L)
    fun prompt() { promptRevision.value++ }
    private val rejected = mutableSetOf<String>()
    private var reauthentication: Pair<ApiContext, Account>? = null
    private val statusClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    companion object {
        const val HEADER = "X-Invidious-Verification"
        internal const val CONTEXT_HEADER = "X-Mobivious-Request-Context"
        fun origin(url: HttpUrl) = "${url.scheme}://${url.host.let { if (':' in it) "[$it]" else it }}:${url.port}"
        fun sameOrigin(url: HttpUrl, server: String) = origin(url) == origin(server.toHttpUrl())
        fun classify(code: Int, header: String?, body: String): AccessReason? {
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
    fun headers(url: HttpUrl, context: ApiContext): Map<String, String> {
        if (context != current() || !sameOrigin(url, context.server) || bootstrap(url.encodedPath)) return emptyMap()
        return buildMap {
            context.account?.takeIf { it.expiresAt > now() && sameOrigin(url, it.server) }?.let { put("Authorization", "Bearer ${it.token}") }
            pass(context)?.let { put(HEADER, it.token) }
        }
    }
    @Synchronized fun report(context: ApiContext, reason: AccessReason) {
        if (context != current()) return
        if (reason == AccessReason.VERIFICATION) rejected.add(origin(context.server.toHttpUrl()))
        // A required login precedes verification at the server. Do not let concurrent image failures replace it.
        if (issue.value?.reason != AccessReason.LOGIN || reason == AccessReason.LOGIN) issue.value = AccessIssue(context, reason)
    }
    @Synchronized fun clear(context: ApiContext) {
        if (context != current()) throw CancellationException("Account or instance changed")
        issue.value = null; revision.value++; prompt()
    }
    @Synchronized fun contextChanged() { issue.value = null; revision.value++; prompt() }
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
            chain.proceed(chain.request().newBuilder().removeHeader(CONTEXT_HEADER).tag(ApiContext::class.java, target).build())
        }.addNetworkInterceptor(Interceptor { chain ->
            val request = chain.request()
            val target = requireNotNull(request.tag(ApiContext::class.java))
            if (target != current()) throw IOException("Account or instance changed")
            val outgoing = request.newBuilder().removeHeader(HEADER).removeHeader("Authorization").removeHeader("Cookie")
            // Explicit account API authentication remains valid on bootstrap-free same-origin requests.
            headers(request.url, target).forEach { (key, value) -> outgoing.header(key, value) }
            val response = chain.proceed(outgoing.build())
            val reason = if (sameOrigin(request.url, target.server) && response.code in listOf(401, 403, 503))
                classify(response.code, response.header("X-Invidious-Verification-Required"), response.peekBody(8192).string()) else null
            if (reason != null) {
                response.close(); report(target, reason); throw InstanceAccessException(reason, target)
            }
            if (target != current()) { response.close(); throw IOException("Account or instance changed") }
            response
        }).build()

    suspend fun discover(context: ApiContext, candidate: VerificationPass? = pass(context)): VerificationStatus? = withContext(Dispatchers.IO) {
        check(context)
        val request = Request.Builder().url(context.server.toHttpUrl().resolve("/api/v1/verification")!!)
            .apply { candidate?.let { header(HEADER, it.token) } }.build()
        val response = statusClient.newCall(request).awaitBody(8192)
        check(context)
        if (response.code in listOf(404, 405)) return@withContext null
        if (!response.isSuccessful) throw IOException("Unable to check verification (${response.code}). Try again.")
        VerificationStatus.parse(JSONObject(response.body)).also { status ->
            if (!status.enabled) synchronized(this@InstanceAccess) {
                check(context)
                val key = origin(context.server.toHttpUrl())
                if (persistence.read(key) != null) persistence.write(key, null)
                rejected.remove(key)
            }
        }
    }
    suspend fun complete(context: ApiContext, token: String?, expiresAt: Long?, enabled: Boolean) {
        check(context)
        val candidate = if (enabled) {
            require(!token.isNullOrBlank() && token.length <= 2048 && token.none { it <= ' ' || it == '\u007f' } && expiresAt != null && expiresAt > now()) { "Invalid verification response. Retry verification." }
            VerificationPass(token, expiresAt)
        } else null
        val status = discover(context, candidate) ?: throw IOException("Verification is not available on this instance.")
        check(context)
        require(!status.enabled || candidate != null && status.verified && !status.required && status.expiresAt != null && status.expiresAt > now()) { "Verification was not accepted. Try a fresh challenge." }
        synchronized(this) {
            check(context)
            val key = origin(context.server.toHttpUrl())
            persistence.write(key, if (status.enabled) candidate!!.copy(expiresAt = status.expiresAt!!) else null)
            rejected.remove(key); clear(context)
        }
    }
}
