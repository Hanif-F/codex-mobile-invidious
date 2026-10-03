package net.wingress.mobivious.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

class ApiException(val status: Int, message: String, val retryAfter: String? = null) : IOException(message)

class InvidiousApi(private val server: () -> String, private val account: () -> Account?,
    private val expired: () -> Unit = {}, private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val cache: ResponseCache? = null, private val onOffline: (Boolean) -> Unit = {}) {
    private val contributionClient = client.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    fun context() = ApiContext(server(), account())
    companion object {
        fun normalizeServer(input: String, debug: Boolean): String {
            val url = input.trim().trimEnd('/').toHttpUrl()
            require(url.username.isEmpty() && url.password.isEmpty() && url.encodedPath == "/" && url.query == null && url.fragment == null) { "Enter the instance address without a path or credentials." }
            require(url.isHttps || debug && url.host in listOf("localhost", "127.0.0.1", "10.0.2.2")) { "Use an HTTPS instance address." }
            return url.toString().trimEnd('/')
        }
    }
    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl = server().toHttpUrl().newBuilder()
        .addPathSegments(path.removePrefix("/")).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
    suspend fun request(path: String, method: String = "GET", data: JSONObject? = null, auth: Boolean = false,
        query: Map<String, String> = emptyMap(), context: ApiContext? = null): String = withContext(Dispatchers.IO) {
        val target = context ?: this@InvidiousApi.context()
        if (context != null && (target.server != server() || target.account != account())) throw CancellationException("Account or instance changed")
        val address = target.server.toHttpUrl().newBuilder().addPathSegments(path.removePrefix("/"))
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val builder = Request.Builder().url(address).header("Accept", "application/json")
        if (auth) {
            val session = target.account ?: throw ApiException(401, "Sign in to use your account.")
            if (session.server.toHttpUrl() != target.server.toHttpUrl() || session.expiresAt <= System.currentTimeMillis() / 1000) { if (target == this@InvidiousApi.context()) expired(); throw ApiException(401, "Your session expired. Sign in again.") }
            builder.header("Authorization", "Bearer ${session.token}")
        }
        builder.method(method, if (method in listOf("POST", "PUT", "PATCH")) (data ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()) else null)
        val cacheKey = if (method == "GET" && path in listOf("api/v1/popular", "api/v1/trending", "api/v1/search", "api/v1/auth/feed")) "$address|${if (auth) target.account?.token else "public"}" else null
        val transport = if (method != "GET" && path.startsWith("api/v1/auth/dearrow/")) contributionClient else client
        try { transport.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401 && auth && target == this@InvidiousApi.context()) expired()
                val error = runCatching { JSONObject(body).text("error") }.getOrDefault("")
                if (auth && response.code == 403 && (error == "Request must be authenticated" || error.startsWith("Token is expired")) && target == this@InvidiousApi.context()) expired()
                throw ApiException(response.code, when {
                    response.code == 429 -> "Too many attempts. Try again after ${response.header("Retry-After") ?: "a few"} seconds."
                    response.code == 404 && path == "api/v1/mobile/login" -> "This server needs the Mobivious native sign-in update."
                    response.code == 404 && path.startsWith("api/v1/auth/dearrow/") -> "This server needs the Mobivious DeArrow API update."
                    response.code == 403 && error == "Invalid scope" && path.startsWith("api/v1/auth/dearrow/") -> "Sign out and sign in again to enable DeArrow contributions with an updated token."
                    error.isNotBlank() -> error.take(300)
                    response.code >= 500 -> "The server could not complete this request. Try again."
                    else -> "Request failed (${response.code})."
                }, response.header("Retry-After"))
            }
            if (cacheKey != null) { cache?.write(cacheKey, body); onOffline(false) }
            body
        } } catch (e: IOException) {
            if (e is ApiException && e.status < 500) throw e
            val saved = cacheKey?.let { cache?.read(it) } ?: throw e
            onOffline(true)
            saved
        }
    }
    suspend fun login(username: String, password: String): Account {
        val json = JSONObject(request("api/v1/mobile/login", "POST", JSONObject().put("username", username).put("password", password)))
        return Account(json.getString("accessToken"), json.getString("username"), json.getLong("expiresAt"), server())
    }
    suspend fun discovery(kind: String, region: String = "US") = ApiParser.videos(JSONArray(request("api/v1/$kind", query = if (kind == "trending") mapOf("region" to region) else emptyMap())))
    suspend fun search(q: String, page: Int, sort: String, date: String, duration: String): List<Video> = ApiParser.videos(JSONArray(request("api/v1/search", query = mapOf("q" to q, "page" to page.toString(), "sort_by" to sort, "date" to date, "duration" to duration, "type" to "video"))))
    suspend fun video(id: String, local: Boolean = true) = ApiParser.details(JSONObject(request("api/v1/videos/$id", query = mapOf("local" to local.toString()))))
    suspend fun sponsorBlock(id: String, context: ApiContext) = SponsorBlockRules.segments(JSONObject(request("api/v1/sponsorblock/$id", context = context)))
    suspend fun channel(id: String) = ApiParser.channel(JSONObject(request("api/v1/channels/$id")))
    suspend fun channelVideos(id: String, continuation: String = ""): Page<Video> {
        val j = JSONObject(request("api/v1/channels/$id/videos", query = mapOf("continuation" to continuation)))
        return Page(ApiParser.videos(j.optJSONArray("videos") ?: JSONArray()), j.text("continuation"))
    }
    suspend fun comments(id: String, continuation: String = ""): Page<Comment> {
        val j = JSONObject(request("api/v1/comments/$id", query = mapOf("continuation" to continuation)))
        return Page(j.optJSONArray("comments")?.objects()?.map { Comment(it.text("author"), it.text("content"), it.text("publishedText"), it.optLong("likeCount")) } ?: emptyList(), j.text("continuation"))
    }
    suspend fun feed(page: Int, notificationsOnly: Boolean = false): List<Video> {
        val j = JSONObject(request("api/v1/auth/feed", auth = true, query = mapOf("page" to "$page")))
        val notifications = ApiParser.videos(j.optJSONArray("notifications") ?: JSONArray())
        return if (notificationsOnly) notifications else notifications + ApiParser.videos(j.optJSONArray("videos") ?: JSONArray())
    }
    suspend fun subscriptions() = JSONArray(request("api/v1/auth/subscriptions", auth = true)).objects().map(ApiParser::channel)
    suspend fun subscribe(id: String, subscribe: Boolean) { request("api/v1/auth/subscriptions/$id", if (subscribe) "POST" else "DELETE", auth = true) }
    suspend fun history(page: Int) = ApiParser.videos(JSONArray(request("api/v1/auth/history", auth = true, query = mapOf("details" to "true", "page" to "$page"))))
    suspend fun playlists() = JSONArray(request("api/v1/auth/playlists", auth = true)).objects().map(ApiParser::playlist)
    suspend fun playlist(id: String, page: Int = 1): Pair<Playlist, List<Video>> {
        val j = JSONObject(request("api/v1/auth/playlists/$id", auth = true, query = mapOf("page" to "$page")))
        return ApiParser.playlist(j) to ApiParser.videos(j.optJSONArray("videos") ?: JSONArray())
    }
    suspend fun createPlaylist(title: String, privacy: String) { request("api/v1/auth/playlists", "POST", JSONObject().put("title", title).put("privacy", privacy), true) }
    suspend fun editPlaylist(id: String, title: String, privacy: String, description: String) { request("api/v1/auth/playlists/$id", "PATCH", JSONObject().put("title", title).put("privacy", privacy).put("description", description), true) }
    suspend fun deletePlaylist(id: String) { request("api/v1/auth/playlists/$id", "DELETE", auth = true) }
    suspend fun addToPlaylist(id: String, video: String) { request("api/v1/auth/playlists/$id/videos", "POST", JSONObject().put("videoId", video), true) }
    suspend fun removeFromPlaylist(id: String, index: String) { request("api/v1/auth/playlists/$id/videos/$index", "DELETE", auth = true) }
    suspend fun preferences(context: ApiContext = context()): AccountPreferences = ApiParser.preferences(JSONObject(request("api/v1/auth/preferences", auth = true, context = context)))
    suspend fun preferences(changes: JSONObject, context: ApiContext): AccountPreferences = ApiParser.preferences(JSONObject(request("api/v1/auth/preferences", "PATCH", changes, true, context = context)))
    suspend fun dearrowTitle(id: String, context: ApiContext = context()): String? = JSONObject(request("api/v1/dearrow/$id", context = context)).text("title").trim().takeIf { it.isNotEmpty() }
    suspend fun dearrowIdentity(context: ApiContext = context()) = JSONObject(request("api/v1/auth/dearrow/identity", auth = true, context = context)).let { DeArrowIdentity(it.getBoolean("ready"), it.getBoolean("configured")) }
    suspend fun importDeArrowIdentity(privateId: String, context: ApiContext) { request("api/v1/auth/dearrow/identity", "PUT", JSONObject().put("privateId", privateId), true, context = context) }
    suspend fun dearrowSubmissions(id: String, context: ApiContext) = ApiParser.dearrowSubmissions(JSONObject(request("api/v1/auth/dearrow/$id/submissions", auth = true, context = context)))
    suspend fun contributeDeArrow(id: String, fields: JSONObject, context: ApiContext) { request("api/v1/auth/dearrow/$id", "POST", fields, true, context = context) }
    suspend fun position(id: String): Long = try { JSONObject(request("api/v1/auth/playback/$id", auth = true)).optLong("position") } catch (e: ApiException) { if (e.status == 404) 0 else throw e }
    suspend fun position(id: String, seconds: Long) { if (seconds == 0L) request("api/v1/auth/playback/$id", "DELETE", auth = true) else request("api/v1/auth/playback/$id", "PUT", JSONObject().put("position", seconds.coerceAtMost(Int.MAX_VALUE.toLong())), true) }
    suspend fun watched(id: String) { request("api/v1/auth/history/$id", "POST", auth = true) }
    suspend fun removeHistory(id: String) { request("api/v1/auth/history/$id", "DELETE", auth = true) }
    suspend fun clearHistory() { request("api/v1/auth/history", "DELETE", auth = true) }
    suspend fun logout() { request("api/v1/auth/tokens/unregister", "POST", auth = true) }
}
