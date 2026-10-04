package net.wingress.mobivious.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

data class PostImage(val url: String, val width: Int = 0, val height: Int = 0) {
    companion object {
        fun parse(images: JSONArray?): PostImage? = images?.objects()?.filter { it.text("url").isNotBlank() }
            ?.minByOrNull { kotlin.math.abs(it.optInt("width") - 1280) }?.let {
                PostImage(it.text("url"), it.optInt("width").coerceAtLeast(0), it.optInt("height").coerceAtLeast(0))
            }
    }
}
data class PostChoice(val text: String, val image: PostImage? = null, val correct: Boolean? = null)
sealed interface PostAttachment {
    data class Images(val images: List<PostImage>) : PostAttachment
    data class VideoItem(val video: Video) : PostAttachment
    data class PlaylistItem(val playlist: Playlist) : PostAttachment
    data class Poll(val choices: List<PostChoice>, val votes: Long?, val quiz: Boolean = false) : PostAttachment
    data object Unavailable : PostAttachment

    companion object {
        fun parse(json: JSONObject?): PostAttachment? {
            if (json == null) return null
            return when (json.text("type")) {
                "image" -> PostImage.parse(json.optJSONArray("imageThumbnails"))?.let { Images(listOf(it)) } ?: Unavailable
                "multiImage" -> json.optJSONArray("images")?.let { images ->
                    (0 until images.length()).mapNotNull { PostImage.parse(images.optJSONArray(it)) }
                }.orEmpty().takeIf { it.isNotEmpty() }?.let(::Images) ?: Unavailable
                "video" -> if (json.text("error").isBlank() && json.text("videoId").matches(Regex("[A-Za-z0-9_-]{11}")))
                    VideoItem(ApiParser.video(json)) else Unavailable
                "playlist" -> if (json.text("playlistId").matches(Regex("[A-Za-z0-9_-]{1,100}")))
                    PlaylistItem(ApiParser.playlist(json)) else Unavailable
                "poll", "quiz" -> Poll(json.optJSONArray("choices")?.objects().orEmpty().map {
                    PostChoice(it.text("text"), PostImage.parse(it.optJSONArray("image")), it.opt("isCorrect") as? Boolean)
                }, (json.opt("totalVotes") as? Number)?.toLong()?.takeIf { it >= 0 }, json.text("type") == "quiz")
                else -> Unavailable
            }
        }
    }
}
data class CommunityPost(val comment: Comment, val channelId: String, val commentCount: Long? = null,
    val attachment: PostAttachment? = null) {
    val id get() = comment.id
    val key get() = comment.key
}
data class PostPage(val items: List<CommunityPost>, val continuation: String = "", val channelId: String = "") {
    companion object {
        fun parse(json: JSONObject): PostPage {
            val owner = json.text("authorId")
            val raw = json.optJSONArray("comments")?.objects().orEmpty()
            val comments = CommentPage.parse(json).items
            return PostPage(raw.zip(comments).map { (item, comment) ->
                CommunityPost(comment, owner.ifBlank { comment.authorId },
                    (item.opt("replyCount") as? Number)?.toLong()?.takeIf { it >= 0 }, PostAttachment.parse(item.optJSONObject("attachment")))
            }, json.text("continuation"), owner)
        }
    }
}
data class PostLink(val id: String, val channelId: String? = null)
object PostLinks {
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,128}"))
    fun parse(text: String, instance: String): PostLink? {
        val candidate = Regex("https?://[^\\s]+").find(text)?.value?.trimEnd('.', ',', ')') ?: text.trim()
        val base = instance.toHttpUrlOrNull() ?: return null
        val url = candidate.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host.removePrefix("www.").removePrefix("m.")
        if (host != "youtube.com" && !(url.scheme == base.scheme && url.host == base.host && url.port == base.port) &&
            host !in listOf("invidious.wingress.net", "mobivious.wingress.net")) return null
        val path = url.pathSegments.dropLastWhile { it.isEmpty() }
        val id = when {
            path.size == 2 && path[0] == "post" -> path[1]
            path.firstOrNull() == "channel" && path.getOrNull(2) in listOf("community", "posts") -> url.queryParameter("lb")
            path.firstOrNull()?.startsWith('@') == true && path.getOrNull(1) in listOf("community", "posts") -> url.queryParameter("lb")
            else -> null
        }?.takeIf(::validId) ?: return null
        val owner = (url.queryParameter("ucid") ?: path.getOrNull(1).takeIf { path.firstOrNull() == "channel" })
            ?.takeIf(ContentVisibility::validChannel)
        return PostLink(id, owner)
    }
    fun url(server: String, post: CommunityPost): String = server.toHttpUrlOrNull()!!.newBuilder()
        .addPathSegment("post").addPathSegment(post.id).apply {
            if (ContentVisibility.validChannel(post.channelId)) addQueryParameter("ucid", post.channelId)
        }.build().toString()
}

/** GGPHT images use the instance proxy. Unlike avatars, banners/post images keep their selected size. */
object ChannelImages {
    fun url(server: String, raw: String): String? {
        val base = server.toHttpUrlOrNull() ?: return null
        if (raw.isBlank() || '\\' in raw) return null
        val source = (if (raw.startsWith("//")) "https:$raw" else raw).toHttpUrlOrNull() ?: base.resolve(raw)
            ?: return null
        if (source.username.isNotEmpty() || source.password.isNotEmpty()) return null
        val same = source.scheme == base.scheme && source.host == base.host && source.port == base.port
        val path = when {
            same && source.encodedPath.startsWith("/ggpht/") -> source.encodedPath
            source.host in setOf("yt3.ggpht.com", "yt3.googleusercontent.com", "lh3.googleusercontent.com") &&
                source.port == (if (source.isHttps) 443 else 80) -> "/ggpht${source.encodedPath}"
            else -> return null
        }
        if (path == "/ggpht/") return null
        return base.newBuilder().encodedPath(path).encodedQuery(source.encodedQuery).fragment(null).build().toString()
    }
}
