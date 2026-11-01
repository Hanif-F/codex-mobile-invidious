package net.wingress.mobivious.data

import okhttp3.HttpUrl.Companion.toHttpUrl

object RssLinks {
    private fun id(value: String): String {
        require(value.matches(Regex("^[A-Za-z0-9_-]{1,100}$"))) { "Invalid feed source." }
        return value
    }
    fun channel(server: String, channel: String, showOriginalTitle: Boolean? = null) = server.toHttpUrl().newBuilder()
        .addPathSegments("feed/channel").addPathSegment(id(channel))
        .apply { showOriginalTitle?.let { setQueryParameter("show_original_title", it.toString()) } }.build().toString()
    fun playlist(server: String, list: Playlist, showOriginalTitle: Boolean? = null): String {
        require(!list.owned || list.privacy != "private") { "Export this private playlist as an Atom file." }
        return server.toHttpUrl().newBuilder().addPathSegments("feed/playlist").addPathSegment(id(list.id))
            .apply { if (list.mix) {
                val seed = list.seedVideoId ?: list.id.removePrefix("RD")
                require(seed.matches(Regex("[A-Za-z0-9_-]{11}"))) { "This mix needs a seed video. Open it from discovery or a video link." }
                addQueryParameter("continuation", seed)
            }; showOriginalTitle?.let { setQueryParameter("show_original_title", it.toString()) } }.build().toString()
    }
    fun privateFeed(server: String, path: String): String {
        val base = server.toHttpUrl()
        require(path.startsWith("/feed/private?") && !path.startsWith("//")) { "Invalid subscription feed link." }
        val url = base.resolve(path) ?: error("Invalid subscription feed link.")
        require(url.scheme == base.scheme && url.host == base.host && url.port == base.port && url.encodedPath == "/feed/private" &&
            !url.queryParameter("token").isNullOrBlank() && url.username.isEmpty() && url.password.isEmpty()) { "Invalid subscription feed link." }
        return url.toString()
    }
}

enum class SubscriptionExportFormat(val apiValue: String, val label: String, val mimeType: String, val filename: String) {
    INVIDIOUS_OPML("rss", "Invidious OPML", "application/xml", "subscriptions.opml"),
    YOUTUBE_OPML("newpipe", "YouTube OPML", "application/xml", "subscriptions.opml"),
    FREETUBE_OPML("opml", "FreeTube OPML", "application/xml", "freetube_subscriptions.opml"),
    NEWPIPE_JSON("newpipe_json", "NewPipe JSON", "application/json", "newpipe_subscriptions")
}

data class RssState(val open: Boolean = false, val title: String = "", val context: ApiContext? = null,
    val url: String? = null, val body: String? = null, val filename: String = "feed.xml",
    val loading: Boolean = false, val error: String? = null, val privateLink: Boolean = false,
    val playlist: Playlist? = null, val exportFormat: SubscriptionExportFormat? = null, val channel: Channel? = null) {
    val mimeType get() = exportFormat?.mimeType ?: "application/atom+xml"
}
