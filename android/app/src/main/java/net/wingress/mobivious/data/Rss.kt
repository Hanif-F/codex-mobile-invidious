package net.wingress.mobivious.data

import okhttp3.HttpUrl.Companion.toHttpUrl

object RssLinks {
    private fun id(value: String): String {
        require(value.matches(Regex("^[A-Za-z0-9_-]{1,100}$"))) { "Invalid feed source." }
        return value
    }
    fun channel(server: String, channel: String) = server.toHttpUrl().newBuilder()
        .addPathSegments("feed/channel").addPathSegment(id(channel)).build().toString()
    fun playlist(server: String, list: Playlist): String {
        require(!list.owned || list.privacy != "private") { "Export this private playlist as an Atom file." }
        return server.toHttpUrl().newBuilder().addPathSegments("feed/playlist").addPathSegment(id(list.id))
            .apply { if (list.mix) {
                val seed = list.seedVideoId ?: list.id.removePrefix("RD")
                require(seed.matches(Regex("[A-Za-z0-9_-]{11}"))) { "This mix needs a seed video. Open it from discovery or a video link." }
                addQueryParameter("continuation", seed)
            } }.build().toString()
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

data class RssState(val open: Boolean = false, val title: String = "", val context: ApiContext? = null,
    val url: String? = null, val xml: String? = null, val filename: String = "feed.xml",
    val loading: Boolean = false, val error: String? = null, val privateLink: Boolean = false,
    val playlist: Playlist? = null, val opml: Boolean = false, val format: String = "rss")
