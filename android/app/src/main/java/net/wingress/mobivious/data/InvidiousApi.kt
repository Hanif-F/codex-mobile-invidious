package net.wingress.mobivious.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val cache: ResponseCache? = null, private val onOffline: (Boolean) -> Unit = {}, private val generation: () -> Long = { 0 },
    private val onProfile: (ApiContext, String) -> Unit = { _, _ -> }) {
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
        if ((path.startsWith("api/v1/mobile/") || path.startsWith("api/v1/auth/account/")) && target != this@InvidiousApi.context())
            throw CancellationException("Account or instance changed")
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
        try {
            val response = transport.newCall(builder.build()).awaitBody()
            coroutineContext.ensureActive()
            val body = response.body
            if (target != this@InvidiousApi.context()) throw CancellationException("Account or instance changed")
            if (!response.isSuccessful) {
                val chatSettings = path == "api/v1/auth/chat_preferences" || path.startsWith("api/v1/auth/chat_timing/")
                val invalidPassword = path.startsWith("api/v1/auth/account/") &&
                    runCatching { JSONObject(body).text("code") == "invalid_password" }.getOrDefault(false)
                // Optional chat sync must not revoke the active playback owner on an auth failure.
                if (response.code == 401 && auth && !invalidPassword && !chatSettings && target == this@InvidiousApi.context()) expired()
                val error = runCatching { JSONObject(body).text("error") }.getOrDefault("")
                if (auth && !chatSettings && response.code == 403 && (error == "Request must be authenticated" || error.startsWith("Token is expired")) && target == this@InvidiousApi.context()) expired()
                throw ApiException(response.code, when {
                    chatSettings && (response.code == 401 || response.code == 403 && (error == "Request must be authenticated" || error.startsWith("Token is expired"))) -> "Sign out and sign in again to restore chat settings sync. Playback can continue."
                    response.code in listOf(404, 405) && (path == "api/v1/auth/clips" || path.startsWith("api/v1/channels/") && path.endsWith("/clips")) -> "This server needs the native Clips API update."
                    response.code == 403 && error == "Invalid scope" && path.startsWith("api/v1/auth/clips") -> "Sign out and sign in again to enable Clips with an updated token."
                    response.code == 429 -> "Too many attempts. Try again after ${response.header("Retry-After") ?: "a few"} seconds."
                    response.code == 404 && path == "api/v1/mobile/login" -> "This server needs the Mobivious native sign-in update."
                    response.code in listOf(404, 405) && error != "Session no longer exists." && (path.startsWith("api/v1/mobile/registration") || path == "api/v1/mobile/register" || path.startsWith("api/v1/auth/account/")) -> "Update this server to enable native registration and account management."
                    response.code == 403 && error == "Invalid scope" && path.startsWith("api/v1/auth/account/") -> "Sign out and sign in again to enable account management with an updated token."
                    response.code == 404 && path.startsWith("api/v1/auth/dearrow/") -> "This server needs the Mobivious DeArrow API update."
                    response.code == 403 && error == "Invalid scope" && path.startsWith("api/v1/auth/dearrow/") -> "Sign out and sign in again to enable DeArrow contributions with an updated token."
                    response.code in listOf(404, 405) && path.startsWith("api/v1/auth/blocked_channels") -> "This server needs the Mobivious channel-blocking API update."
                    response.code == 403 && error == "Invalid scope" && path.startsWith("api/v1/auth/blocked_channels") -> "Sign out and sign in again to enable channel blocking with an updated token."
                    response.code in listOf(404, 405) && path == "api/v1/auth/subscriptions/search" -> "This server needs the Mobivious subscription-search API update."
                    response.code == 403 && error == "Invalid scope" && path == "api/v1/auth/subscriptions/search" -> "Sign out and sign in again to enable subscription search with an updated token."
                    response.code in listOf(404, 405) && (path.startsWith("api/v1/auth/saved_playlists/") || path == "api/v1/auth/feed/rss" || path == "api/v1/auth/subscriptions/export" || path.endsWith("/feed")) -> "This server needs the Mobivious playlist/RSS API update."
                    response.code == 403 && error == "Invalid scope" && (path.startsWith("api/v1/auth/saved_playlists/") || path == "api/v1/auth/feed/rss" || path == "api/v1/auth/subscriptions/export") -> "Sign out and sign in again to enable playlist subscriptions and RSS exports with an updated token."
                    response.code == 403 && error == "Invalid scope" && (path == "api/v1/auth/chat_preferences" || path.startsWith("api/v1/auth/chat_timing/")) -> "Sign out and sign in again to enable chat settings and timing with an updated token."
                    response.code in listOf(404, 405) && (path == "api/v1/auth/chat_preferences" || path.startsWith("api/v1/auth/chat_timing/")) -> "This server needs the Mobivious chat settings and timing API update."
                    error.isNotBlank() -> error.take(300)
                    response.code >= 500 -> "The server could not complete this request. Try again."
                    else -> "Request failed (${response.code})."
                }, response.header("Retry-After"))
            }
            if (auth && path == "api/v1/auth/preferences" && method == "GET") {
                DeviceProfile.validId(response.header("X-Invidious-Account-Profile"))?.let { onProfile(target, it) }
                if (target != this@InvidiousApi.context()) throw CancellationException("Account profile resolved")
            }
            if (cacheKey != null) {
                val valid = runCatching { if (path == "api/v1/auth/feed") JSONObject(body) else JSONArray(body) }.isSuccess
                if (!valid) throw ApiException(502, "The instance returned an invalid feed. Try again.")
                cache?.write(cacheKey, body); onOffline(false)
            }
            body
        } catch (e: IOException) {
            coroutineContext.ensureActive()
            if (target != this@InvidiousApi.context()) throw CancellationException("Account or instance changed")
            if (e is ApiException && e.status < 500) throw e
            val saved = cacheKey?.let { cache?.read(it) } ?: throw e
            onOffline(true)
            saved
        }
    }
    private fun accountResponse(body: String, context: ApiContext): Account {
        if (context != this.context()) throw CancellationException("Account or instance changed")
        val json = JSONObject(body)
        return Account(json.getString("accessToken"), json.getString("username"), json.getLong("expiresAt"), context.server, DeviceProfile.validId(json.opt("profileId") as? String))
    }
    suspend fun login(username: String, password: String, context: ApiContext = context()): Account = accountResponse(
        request("api/v1/mobile/login", "POST", JSONObject().put("username", username).put("password", password), context = context), context)
    suspend fun registration(context: ApiContext) = Registration.parse(JSONObject(request("api/v1/mobile/registration", context = context)))
    suspend fun register(username: String, password: String, confirmation: String, captchaAnswer: String, captchaToken: String, context: ApiContext): Account = accountResponse(
        request("api/v1/mobile/register", "POST", JSONObject().put("username", username).put("password", password)
            .put("passwordConfirmation", confirmation).put("captchaAnswer", captchaAnswer).put("captchaToken", captchaToken), context = context), context)
    suspend fun changeCredentials(kind: String, fields: JSONObject, context: ApiContext): Account {
        require(kind in listOf("username", "password"))
        return accountResponse(request("api/v1/auth/account/$kind", "POST", fields, auth = true, context = context), context)
    }
    suspend fun deleteAccount(password: String, context: ApiContext) {
        request("api/v1/auth/account/delete", "POST", JSONObject().put("password", password), auth = true, context = context)
    }
    suspend fun accountSessions(context: ApiContext) = JSONArray(request("api/v1/auth/account/sessions", auth = true, context = context)).objects().map(AccountSession::parse)
    suspend fun revokeSession(id: String, context: ApiContext) {
        request("api/v1/auth/account/sessions/revoke", "POST", JSONObject().put("id", id), auth = true, context = context)
    }
    suspend fun createToken(password: String, scopes: List<String>, expiresAt: Long?, context: ApiContext): String {
        require(AccountPermissions.valid(scopes)) { "Select valid API permissions." }
        return JSONObject(request("api/v1/auth/account/tokens", "POST", JSONObject().put("password", password)
            .put("scopes", JSONArray(scopes)).put("expiresAt", expiresAt ?: JSONObject.NULL), auth = true, context = context)).getString("accessToken")
    }
    suspend fun discovery(kind: String, region: String = "US", category: TrendingCategory = TrendingCategory.LIVESTREAMS,
        context: ApiContext = context()) = ApiParser.videos(JSONArray(scopedRead("api/v1/$kind",
        if (kind == "trending") mapOf("region" to region, "type" to category.apiValue) else emptyMap(), false, context)))
    suspend fun searchResults(q: String, page: Int, type: SearchType, sort: String, date: String, duration: String,
        context: ApiContext = context()): GeneralSearchPage {
        val order = if (type != SearchType.CHANNELS && sort in listOf("views", "view_count")) "views" else "relevance"
        val query = mutableMapOf("q" to q, "page" to page.toString(), "sort" to order, "type" to type.apiValue)
        if (type.videoFilters) { query["date"] = date; query["duration"] = duration }
        return GeneralSearchPage.parse(JSONArray(scopedRead("api/v1/search", query, false, context)), type)
    }
    suspend fun search(q: String, page: Int, sort: String, date: String, duration: String): List<Video> {
        val order = if (sort in listOf("views", "view_count")) "views" else "relevance"
        return ApiParser.videos(JSONArray(request("api/v1/search", query = mapOf("q" to q, "page" to page.toString(), "sort" to order, "date" to date, "duration" to duration, "type" to "video"))))
    }
    suspend fun searchPlaylists(q: String, page: Int, sort: String, context: ApiContext = context()) =
        ApiParser.playlists(JSONArray(scopedRead("api/v1/search", mapOf("q" to q, "page" to "$page", "sort" to sort, "type" to "playlist"), false, context)))
    suspend fun channelPlaylists(id: String, continuation: String = "", sort: String = "last", context: ApiContext = context()): Page<Playlist> =
        channelPlaylistPage(id, ChannelTab.PLAYLISTS, continuation, sort, context)
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
    suspend fun clip(id: String, context: ApiContext = context()): Clip {
        require(ClipRules.validId(id)) { "Invalid clip ID." }
        return Clip.parse(JSONObject(scopedRead("api/v1/clips/$id", mapOf("local" to "true"), false, context)), id, context.server)
    }
    suspend fun clips(channel: String? = null, page: Int = 1, context: ApiContext = context()): List<Clip> {
        require(page >= 1)
        require(channel == null || ContentVisibility.validChannel(channel))
        val path = if (channel == null) "api/v1/auth/clips" else "api/v1/channels/$channel/clips"
        return JSONArray(scopedRead(path, mapOf("page" to page.toString()), channel == null, context)).objects().map {
            Clip.parse(it, it.text("clipId"), context.server)
        }
    }
    suspend fun createClip(video: String, title: String, start: Long, end: Long, context: ApiContext): Clip {
        require(video.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid source video." }
        require(ClipRules.error(title, start, end, Long.MAX_VALUE) == null) { "Choose a title and a valid 5–120 second range." }
        return Clip.parse(JSONObject(request("api/v1/auth/clips", "POST", JSONObject().put("videoId", video).put("title", title.trim())
            .put("startTime", start / 1000.0).put("endTime", end / 1000.0), auth = true, context = context).also {
                if (context != this.context()) throw CancellationException("Account or instance changed")
            }), "", context.server)
    }
    suspend fun deleteClip(id: String, context: ApiContext) {
        require(ClipRules.nativeId(id))
        request("api/v1/auth/clips/$id", "DELETE", auth = true, context = context)
        if (context != this.context()) throw CancellationException("Account or instance changed")
    }
    suspend fun storyboard(track: StoryboardTrack, context: ApiContext): List<StoryboardFrame> {
        val target = context.server.toHttpUrlOrNull()?.resolve(track.url) ?: return emptyList()
        require(target.host == context.server.toHttpUrlOrNull()?.host) { "Invalid storyboard address." }
        val path = target.encodedPath.trimStart('/')
        val body = request(path, query = target.queryParameterNames.associateWith { target.queryParameter(it).orEmpty() },
            context = context, accept = "text/vtt")
        if (context != this.context()) throw CancellationException("Account or instance changed")
        return StoryboardVtt.parse(body, target.toString())
    }
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
    suspend fun video(id: String, local: Boolean = true, region: String? = null, context: ApiContext = context()) =
        ApiParser.details(JSONObject(scopedRead("api/v1/videos/$id", buildMap { put("local", local.toString()); region?.let { put("region", it) } }, false, context)))
    suspend fun downloads(id: String, context: ApiContext = context()): DownloadCatalog {
        try { return DownloadCatalog.parse(JSONObject(scopedRead("api/v1/videos/$id/downloads", emptyMap(), false, context))) }
        catch (e: ApiException) { if (e.status in listOf(404, 405)) throw ApiException(e.status, "Update this server to enable native downloads."); throw e }
    }
    suspend fun resolveChannel(link: ChannelLink, context: ApiContext = context()): String {
        link.id?.takeIf(ContentVisibility::validChannel)?.let { return it }
        require(ContentLinks.parse(link.resolveUrl, context.server) is ContentLink.Channel) { "Invalid channel link." }
        val j = JSONObject(scopedRead("api/v1/resolveurl", mapOf("url" to link.resolveUrl), false, context))
        return sequenceOf(j.text("ucid"), j.text("browseId")).firstOrNull(ContentVisibility::validChannel)
            ?: throw ApiException(404, "This channel could not be resolved.")
    }
    suspend fun hashtag(tag: String, page: Int, context: ApiContext = context()): List<Video> {
        require(tag.isNotBlank() && tag.length <= 100 && '/' !in tag && tag.none(Char::isISOControl))
        val path = "api/v1/hashtag/$tag"
        return ApiParser.videos(JSONObject(scopedRead(path, mapOf("page" to page.toString()), false, context)).optJSONArray("results") ?: JSONArray())
    }
    suspend fun sponsorBlock(id: String, context: ApiContext) = SponsorBlockRules.segments(JSONObject(request("api/v1/sponsorblock/$id", context = context)))
    suspend fun aiStatus(context: ApiContext) = AiResponse.parse(JSONObject(scopedRead("api/v1/ai/status", emptyMap(), false, context)))
    suspend fun aiChannels(ids: List<String>, lists: Set<AiListKind>, context: ApiContext) = AiResponse.parse(JSONObject(scopedRead("api/v1/ai/channels",
        mapOf("ids" to ids.joinToString(","), "lists" to AiListKind.entries.filter { it in lists }.joinToString(",") { it.wire }), false, context)))
    suspend fun channel(id: String, context: ApiContext = context()) =
        ApiParser.channel(JSONObject(scopedRead("api/v1/channels/$id", emptyMap(), false, context)))
    suspend fun channelVideos(id: String, continuation: String = "") = channelVideoPage(id, ChannelTab.VIDEOS, continuation)
    suspend fun channelStreams(id: String, continuation: String = "") = channelVideoPage(id, ChannelTab.STREAMS, continuation)
    suspend fun channelVideoPage(id: String, tab: ChannelTab, continuation: String = "", sort: ChannelSort = ChannelSort.NEWEST,
        context: ApiContext = context()): Page<Video> {
        require(tab.videoTab)
        val j = JSONObject(scopedRead("api/v1/channels/$id/${tab.path}", buildMap {
            if (sort != ChannelSort.NEWEST) put("sort_by", sort.apiValue)
            if (continuation.isNotBlank()) put("continuation", continuation)
        }, false, context))
        return Page(ApiParser.videos(j.optJSONArray("videos") ?: JSONArray()), j.text("continuation"))
    }
    suspend fun channelPlaylistPage(id: String, tab: ChannelTab, continuation: String = "", sort: String = "last",
        context: ApiContext = context()): Page<Playlist> {
        require(tab.playlistTab)
        val j = JSONObject(scopedRead("api/v1/channels/$id/${tab.path}", buildMap {
            if (tab == ChannelTab.PLAYLISTS) put("sort_by", sort)
            if (continuation.isNotBlank()) put("continuation", continuation)
        }, false, context))
        return Page(ApiParser.playlists(j.optJSONArray("playlists") ?: JSONArray()), j.text("continuation"))
    }
    suspend fun channelRelated(id: String, continuation: String = "", context: ApiContext = context()): Page<Channel> {
        val j = JSONObject(scopedRead("api/v1/channels/$id/channels", continuationQuery(continuation), false, context))
        return Page(j.optJSONArray("relatedChannels")?.objects().orEmpty().map(ApiParser::channel), j.text("continuation"))
    }
    suspend fun channelPosts(id: String, continuation: String = "", context: ApiContext = context()): PostPage =
        PostPage.parse(JSONObject(scopedRead("api/v1/channels/$id/posts", continuationQuery(continuation), false, context)))
    suspend fun post(link: PostLink, context: ApiContext = context()): CommunityPost {
        require(PostLinks.validId(link.id))
        val j = JSONObject(scopedRead("api/v1/post/${link.id}", buildMap {
            link.channelId?.takeIf(ContentVisibility::validChannel)?.let { put("ucid", it) }
        }, false, context))
        return PostPage.parse(j).items.firstOrNull { it.id == link.id }
            ?: throw ApiException(404, "This post is unavailable.")
    }
    suspend fun postComments(target: CommentTarget.Post, sort: CommentSort = CommentSort.TOP, continuation: String = "",
        context: ApiContext = context()): CommentPage {
        require(PostLinks.validId(target.id) && ContentVisibility.validChannel(target.channelId))
        return CommentPage.parse(JSONObject(scopedRead("api/v1/post/${target.id}/comments", buildMap {
            put("ucid", target.channelId); put("sort_by", sort.apiValue)
            if (continuation.isNotBlank()) put("continuation", continuation)
        }, false, context)))
    }
    suspend fun comments(target: CommentTarget, sort: CommentSort, continuation: String, context: ApiContext): CommentPage =
        when (target) {
            is CommentTarget.Video -> comments(target.id, sort, continuation, context)
            is CommentTarget.Post -> postComments(target, sort, continuation, context)
        }
    private fun continuationQuery(continuation: String) = if (continuation.isBlank()) emptyMap() else mapOf("continuation" to continuation)
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
    suspend fun subscriptionDirectory(context: ApiContext = context()): SubscriptionDirectory = try {
        SubscriptionDirectory.parse(scopedRead("api/v1/auth/subscriptions", mapOf("include_stats" to "true"), true, context))
    } catch (e: ApiException) {
        if (e.status != 403 || e.message != "Channel sorting requires history read permission.") throw e
        SubscriptionDirectory(subscriptions(context), unavailableReason = SubscriptionDirectory.HISTORY_PERMISSION)
    }
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
    suspend fun chatReplay(id: String, offsetMs: Long, continuation: String, context: ApiContext): ChatChunk {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")) && offsetMs >= 0 && continuation.toByteArray(Charsets.UTF_8).size <= 4096)
        return ChatChunk.parse(JSONObject(scopedRead("api/v1/live_chat/$id", buildMap {
            put("offset_ms", offsetMs.toString()); if (continuation.isNotEmpty()) put("continuation", continuation)
        }, false, context)))
    }
    suspend fun chatPreferences(changes: JSONObject, context: ApiContext): AccountPreferences =
        ApiParser.preferences(JSONObject(request("api/v1/auth/chat_preferences", "PATCH", changes, true, context = context)))
    suspend fun chatTiming(id: String, context: ApiContext): Int = JSONObject(request("api/v1/auth/chat_timing/$id", auth = true, context = context)).getInt("offsetMs")
    suspend fun chatTiming(id: String, value: Int, context: ApiContext) {
        require(value in -3_600_000..3_600_000)
        request("api/v1/auth/chat_timing/$id", "PUT", JSONObject().put("offsetMs", value), true, context = context)
    }
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
