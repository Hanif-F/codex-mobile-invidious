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
    private val cache: ResponseCache? = null, private val onOffline: (Boolean) -> Unit = {}, private val generation: () -> Long = { 0 }) {
    private val contributionClient = client.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    fun context() = ApiContext(server(), account(), generation())
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
        query: Map<String, String> = emptyMap(), context: ApiContext? = null, accept: String = "application/json"): String = withContext(Dispatchers.IO) {
        val target = context ?: this@InvidiousApi.context()
        if (context != null && target != this@InvidiousApi.context()) throw CancellationException("Account or instance changed")
        val address = target.server.toHttpUrl().newBuilder().addPathSegments(path.removePrefix("/"))
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val builder = Request.Builder().url(address).header("Accept", accept)
        if (auth) {
            val session = target.account ?: throw ApiException(401, "Sign in to use your account.")
            if (session.server.toHttpUrl() != target.server.toHttpUrl() || session.expiresAt <= System.currentTimeMillis() / 1000) { if (target == this@InvidiousApi.context()) expired(); throw ApiException(401, "Your session expired. Sign in again.") }
            builder.header("Authorization", "Bearer ${session.token}")
        }
        builder.method(method, if (method in listOf("POST", "PUT", "PATCH")) (data ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()) else null)
        val cacheKey = if (method == "GET" && path in listOf("api/v1/popular", "api/v1/trending", "api/v1/search", "api/v1/auth/feed")) "$address|${if (auth) target.account?.token else "public"}" else null
        val transport = if (method != "GET") contributionClient else client
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
                    response.code in listOf(404, 405) && path.startsWith("api/v1/auth/blocked_channels") -> "This server needs the Mobivious channel-blocking API update."
                    response.code == 403 && error == "Invalid scope" && path.startsWith("api/v1/auth/blocked_channels") -> "Sign out and sign in again to enable channel blocking with an updated token."
                    response.code in listOf(404, 405) && path == "api/v1/auth/subscriptions/search" -> "This server needs the Mobivious subscription-search API update."
                    response.code == 403 && error == "Invalid scope" && path == "api/v1/auth/subscriptions/search" -> "Sign out and sign in again to enable subscription search with an updated token."
                    response.code in listOf(404, 405) && (path.startsWith("api/v1/auth/saved_playlists/") || path == "api/v1/auth/feed/rss" || path == "api/v1/auth/subscriptions/export" || path.endsWith("/feed")) -> "This server needs the Mobivious playlist/RSS API update."
                    response.code == 403 && error == "Invalid scope" && (path.startsWith("api/v1/auth/saved_playlists/") || path == "api/v1/auth/feed/rss" || path == "api/v1/auth/subscriptions/export") -> "Sign out and sign in again to enable playlist subscriptions and RSS exports with an updated token."
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
    suspend fun search(q: String, page: Int, sort: String, date: String, duration: String): List<Video> {
        val order = if (sort in listOf("views", "view_count")) "views" else "relevance"
        return ApiParser.videos(JSONArray(request("api/v1/search", query = mapOf("q" to q, "page" to page.toString(), "sort" to order, "date" to date, "duration" to duration, "type" to "video"))))
    }
    suspend fun searchPlaylists(q: String, page: Int, sort: String, context: ApiContext = context()) =
        ApiParser.playlists(JSONArray(scopedRead("api/v1/search", mapOf("q" to q, "page" to "$page", "sort" to sort, "type" to "playlist"), false, context)))
    suspend fun channelPlaylists(id: String, continuation: String = "", sort: String = "last", context: ApiContext = context()): Page<Playlist> {
        val j = JSONObject(scopedRead("api/v1/channels/$id/playlists", buildMap { put("sort_by", sort); if (continuation.isNotBlank()) put("continuation", continuation) }, false, context))
        return Page(ApiParser.playlists(j.optJSONArray("playlists") ?: JSONArray()), j.text("continuation"))
    }
    suspend fun subscribePlaylist(list: Playlist, subscribe: Boolean, context: ApiContext): Playlist? {
        require(list.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid playlist ID" }
        val raw = scopedPlaylistWrite("api/v1/auth/saved_playlists/${list.id}", if (subscribe) "PUT" else "DELETE", context,
            if (subscribe) JSONObject().apply { list.seedVideoId?.let { put("seedVideoId", it) } } else null)
        return if (subscribe) ApiParser.playlist(JSONObject(raw)) else null
    }
    suspend fun subscriptionFeedLink(context: ApiContext): String = RssLinks.privateFeed(context.server,
        JSONObject(scopedRead("api/v1/auth/feed/rss", emptyMap(), true, context)).getString("feedPath"))
    suspend fun subscriptionOpml(format: String, context: ApiContext): String {
        require(format in listOf("rss", "newpipe"))
        return request("api/v1/auth/subscriptions/export", auth = true, query = mapOf("format" to format), context = context, accept = "application/xml")
            .also { if (context != this.context()) throw CancellationException("Account or instance changed") }
    }
    suspend fun playlistAtom(id: String, context: ApiContext): String = request("api/v1/auth/playlists/$id/feed", auth = true,
        context = context, accept = "application/atom+xml").also { if (context != this.context()) throw CancellationException("Account or instance changed") }
    suspend fun channelSearch(id: String, q: String, page: Int, context: ApiContext = context()) =
        SearchPage.parse(scopedRead("api/v1/channels/$id/search", mapOf("q" to q, "page" to "$page"), false, context))
    suspend fun subscriptionSearch(q: String, page: Int, context: ApiContext = context()) =
        SearchPage.parse(scopedRead("api/v1/auth/subscriptions/search", mapOf("q" to q, "page" to "$page"), true, context))
    private suspend fun scopedRead(path: String, query: Map<String, String>, auth: Boolean, context: ApiContext): String =
        request(path, query = query, auth = auth, context = context).also {
            if (context != this.context()) throw CancellationException("Account or instance changed")
        }
    private suspend fun scopedPlaylistWrite(path: String, method: String, context: ApiContext, data: JSONObject? = null): String =
        request(path, method, data, auth = true, context = context).also {
            if (context != this.context()) throw CancellationException("Account or instance changed")
        }
    suspend fun video(id: String, local: Boolean = true) = ApiParser.details(JSONObject(request("api/v1/videos/$id", query = mapOf("local" to local.toString()))))
    suspend fun sponsorBlock(id: String, context: ApiContext) = SponsorBlockRules.segments(JSONObject(request("api/v1/sponsorblock/$id", context = context)))
    suspend fun channel(id: String) = ApiParser.channel(JSONObject(request("api/v1/channels/$id")))
    suspend fun channelVideos(id: String, continuation: String = "") = channelPage(id, ChannelTab.VIDEOS, continuation)
    suspend fun channelStreams(id: String, continuation: String = "") = channelPage(id, ChannelTab.STREAMS, continuation)
    private suspend fun channelPage(id: String, tab: ChannelTab, continuation: String): Page<Video> {
        val query = if (continuation.isBlank()) emptyMap() else mapOf("continuation" to continuation)
        val j = JSONObject(request("api/v1/channels/$id/${tab.path}", query = query))
        return Page(ApiParser.videos(j.optJSONArray("videos") ?: JSONArray()), j.text("continuation"))
    }
    suspend fun comments(id: String, sort: CommentSort = CommentSort.TOP, continuation: String = "", context: ApiContext = context()): CommentPage =
        CommentPage.parse(JSONObject(scopedRead("api/v1/comments/$id", buildMap {
            put("source", "youtube"); put("sort_by", sort.apiValue)
            if (continuation.isNotBlank()) put("continuation", continuation)
        }, false, context)))
    suspend fun feed(page: Int, notificationsOnly: Boolean = false): List<Video> {
        val j = JSONObject(request("api/v1/auth/feed", auth = true, query = mapOf("page" to "$page")))
        val notifications = ApiParser.videos(j.optJSONArray("notifications") ?: JSONArray())
        return if (notificationsOnly) notifications else notifications + ApiParser.videos(j.optJSONArray("videos") ?: JSONArray())
    }
    suspend fun subscriptions(context: ApiContext = context()) = JSONArray(scopedRead("api/v1/auth/subscriptions", emptyMap(), true, context)).objects().map(ApiParser::channel)
    suspend fun blockedChannels(context: ApiContext) = BlockedChannel.parse(JSONArray(request("api/v1/auth/blocked_channels", auth = true, context = context)))
    suspend fun blockChannel(id: String, name: String, blocked: Boolean, context: ApiContext) {
        require(ContentVisibility.validChannel(id)) { "This video has no valid channel ID." }
        request("api/v1/auth/blocked_channels/$id", if (blocked) "POST" else "DELETE",
            if (blocked) JSONObject().put("name", name) else null, auth = true, context = context)
    }
    suspend fun subscribe(id: String, subscribe: Boolean, context: ApiContext = context()) {
        request("api/v1/auth/subscriptions/$id", if (subscribe) "POST" else "DELETE", auth = true, context = context)
            .also { if (context != this.context()) throw CancellationException("Account or instance changed") }
    }
    suspend fun history(page: Int, q: String = "", context: ApiContext = context()) = History.parse(scopedRead("api/v1/auth/history",
        mapOf("details" to "true", "organized" to "true", "q" to q, "page" to "$page"), true, context))
    suspend fun playlists(context: ApiContext = context()) = JSONArray(scopedRead("api/v1/auth/playlists", emptyMap(), true, context)).objects().map { ApiParser.playlist(it, legacyOwned = true) }
    suspend fun playlist(id: String, page: Int = 1): Pair<Playlist, List<Video>> {
        val result = queuePage(id, (page - 1) * 100)
        return (result.playlist ?: Playlist(result.source.id, result.source.title, result.source.count)) to result.videos
    }
    suspend fun queuePage(id: String, index: Int = 0, continuation: String? = null, context: ApiContext = context()): QueuePage {
        require(id.matches(Regex("^[A-Za-z0-9_-]{1,100}$"))) { "Invalid playlist ID." }
        val mix = id.startsWith("RD")
        val auth = context.account != null
        val path = if (mix && !auth) "api/v1/mixes/$id" else "api/v1/${if (auth) "auth/" else ""}playlists/$id"
        val query = if (mix) continuation?.let { mapOf("continuation" to it) }.orEmpty() else buildMap {
            put("index", "$index"); if (continuation != null && index == 0) put("continuation", continuation)
        }
        val j = JSONObject(scopedRead(path, query, auth, context))
        val list = ApiParser.playlist(j)
        return QueuePage(QueueSource(id, list.title, list.count, mix, list.owned, list.seedVideoId), ApiParser.videos(j.optJSONArray("videos") ?: JSONArray()), list)
    }
    suspend fun createPlaylist(title: String, privacy: String, context: ApiContext = context()): Playlist {
        val j = JSONObject(scopedPlaylistWrite("api/v1/auth/playlists", "POST", context, JSONObject().put("title", title).put("privacy", privacy)))
        return Playlist(j.getString("playlistId"), j.text("title", title), 0, privacy, owned = true)
    }
    suspend fun editPlaylist(id: String, title: String, privacy: String, description: String) { request("api/v1/auth/playlists/$id", "PATCH", JSONObject().put("title", title).put("privacy", privacy).put("description", description), true) }
    suspend fun deletePlaylist(id: String) { request("api/v1/auth/playlists/$id", "DELETE", auth = true) }
    suspend fun addToPlaylist(id: String, video: String, context: ApiContext = context()): Video = ApiParser.video(JSONObject(scopedPlaylistWrite("api/v1/auth/playlists/$id/videos", "POST", context, JSONObject().put("videoId", video))))
    suspend fun removeFromPlaylist(id: String, index: String, context: ApiContext = context()) { require(index.matches(Regex("^[A-Fa-f0-9]+$"))) { "Missing playlist occurrence ID." }; scopedPlaylistWrite("api/v1/auth/playlists/$id/videos/$index", "DELETE", context) }
    suspend fun preferences(context: ApiContext = context()): AccountPreferences = ApiParser.preferences(JSONObject(request("api/v1/auth/preferences", auth = true, context = context)))
    suspend fun preferences(changes: JSONObject, context: ApiContext): AccountPreferences = ApiParser.preferences(JSONObject(request("api/v1/auth/preferences", "PATCH", changes, true, context = context)))
    suspend fun dearrowTitle(id: String, context: ApiContext = context()): String? = JSONObject(request("api/v1/dearrow/$id", context = context)).text("title").trim().takeIf { it.isNotEmpty() }
    suspend fun dearrowIdentity(context: ApiContext = context()) = JSONObject(request("api/v1/auth/dearrow/identity", auth = true, context = context)).let { DeArrowIdentity(it.getBoolean("ready"), it.getBoolean("configured")) }
    suspend fun importDeArrowIdentity(privateId: String, context: ApiContext) { request("api/v1/auth/dearrow/identity", "PUT", JSONObject().put("privateId", privateId), true, context = context) }
    suspend fun dearrowSubmissions(id: String, context: ApiContext) = ApiParser.dearrowSubmissions(JSONObject(request("api/v1/auth/dearrow/$id/submissions", auth = true, context = context)))
    suspend fun contributeDeArrow(id: String, fields: JSONObject, context: ApiContext) { request("api/v1/auth/dearrow/$id", "POST", fields, true, context = context) }
    suspend fun playback(context: ApiContext = context()) = PlaybackSnapshot.parse(JSONObject(request("api/v1/auth/playback", auth = true, context = context)))
    suspend fun position(id: String, context: ApiContext = context()): Long = try { JSONObject(request("api/v1/auth/playback/$id", auth = true, context = context)).optLong("position") } catch (e: ApiException) { if (e.status == 404) 0 else throw e }
    suspend fun position(id: String, seconds: Long, context: ApiContext = context()) { if (seconds == 0L) request("api/v1/auth/playback/$id", "DELETE", auth = true, context = context) else request("api/v1/auth/playback/$id", "PUT", JSONObject().put("position", seconds.coerceAtMost(Int.MAX_VALUE.toLong())), true, context = context) }
    suspend fun watched(id: String, context: ApiContext = context()) { request("api/v1/auth/history/$id", "POST", auth = true, context = context) }
    suspend fun removeHistory(id: String, context: ApiContext = context()) { request("api/v1/auth/history/$id", "DELETE", auth = true, context = context) }
    suspend fun clearHistory(context: ApiContext = context()) { request("api/v1/auth/history", "DELETE", auth = true, context = context) }
    suspend fun logout() { request("api/v1/auth/tokens/unregister", "POST", auth = true) }
}
