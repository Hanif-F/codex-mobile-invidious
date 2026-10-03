package net.wingress.mobivious.data

import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class Video(val id: String, val title: String, val author: String = "", val channelId: String = "",
    val thumbnail: String = "", val duration: Long = 0, val views: Long = 0, val published: String = "",
    val live: Boolean = false, val indexId: String = "", val unavailable: Boolean = false, val membersOnly: Boolean = false)
data class Caption(val label: String, val language: String, val url: String)
data class AudioIdentity(val id: String, val name: String, val default: Boolean?)
data class StreamFormat(val id: String, val mimeType: String, val codec: String,
    val width: Int, val height: Int, val fps: Float, val bitrate: Long, val bytes: Long,
    val audio: AudioIdentity? = null, val drc: Boolean? = null)
data class VideoDetails(val video: Video, val description: String, val dash: String, val hls: String,
    val fallback: String, val captions: List<Caption>, val recommendations: List<Video>, val formats: List<StreamFormat> = emptyList())
enum class ChannelTab(val path: String, val label: String) {
    VIDEOS("videos", "Videos"), STREAMS("streams", "Streams")
}
data class Channel(val id: String, val name: String, val description: String = "", val subscribers: String = "", val image: String = "",
    val tabs: List<String> = emptyList()) {
    val contentTabs: List<ChannelTab>
        get() = ChannelTab.entries.filter { it.path in tabs }.ifEmpty { listOf(ChannelTab.VIDEOS) }
    fun preferredTab(current: ChannelTab? = null): ChannelTab = current?.takeIf { it in contentTabs } ?: contentTabs.first()
}
data class Playlist(val id: String, val title: String, val count: Int, val privacy: String = "private", val description: String = "")
data class Comment(val author: String, val text: String, val published: String, val likes: Long)
data class Page<T>(val items: List<T>, val continuation: String = "")
data class Account(val token: String, val username: String, val expiresAt: Long, val server: String)
data class ApiContext(val server: String, val account: Account?)
data class DeArrowIdentity(val ready: Boolean, val configured: Boolean)
data class DeArrowSubmission(val title: String, val original: Boolean, val votes: Int, val locked: Boolean, val uuid: String)
data class VideoLink(val id: String, val seconds: Long? = null)

object VideoLinks {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    fun parse(text: String, instance: String): VideoLink? {
        val candidate = Regex("https?://[^\\s]+").find(text)?.value?.trimEnd('.', ',', ')') ?: text.trim()
        val url = candidate.toHttpUrlOrNull() ?: return null
        val host = url.host.removePrefix("www.").removePrefix("m.")
        val instanceHost = instance.toHttpUrlOrNull()?.host
        val id = when {
            host == "youtu.be" -> url.pathSegments.firstOrNull()
            host == "youtube.com" || host == "youtube-nocookie.com" || host == instanceHost || host == "invidious.wingress.net" || host == "mobivious.wingress.net" ->
                url.queryParameter("v") ?: if (url.pathSegments.firstOrNull() in listOf("shorts", "embed", "live")) url.pathSegments.getOrNull(1) else null
            else -> null
        } ?: return null
        if (!idPattern.matches(id)) return null
        val timestamp = url.queryParameter("t") ?: url.queryParameter("start") ?: url.fragment?.removePrefix("t=")
        return VideoLink(id, timestamp?.let(::timestampSeconds))
    }
    fun timestampSeconds(value: String): Long? {
        value.removeSuffix("s").toLongOrNull()?.let { return it.takeIf { seconds -> seconds >= 0 } }
        if (!Regex("^(?:\\d+h)?(?:\\d+m)?(?:\\d+s)?$").matches(value) || value.isEmpty()) return null
        return runCatching { Regex("(\\d+)([hms])").findAll(value).sumOf { it.groupValues[1].toLong() * when(it.groupValues[2]) { "h" -> 3600; "m" -> 60; else -> 1 } } }.getOrNull()
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.text(key: String, fallback: String = ""): String = if (isNull(key)) fallback else optString(key, fallback)
object ApiParser {
    fun preferences(json: JSONObject) = AccountPreferences.parse(json)
    fun dearrowSubmissions(json: JSONObject) = json.getJSONArray("titles").objects().map {
        DeArrowSubmission(it.getString("title"), it.getBoolean("original"), it.getInt("votes"), it.getBoolean("locked"), it.getString("UUID"))
    }
    fun thumbnail(json: JSONObject): String = json.optJSONArray("videoThumbnails")?.objects()?.let { images ->
        images.firstOrNull { it.text("quality") in listOf("medium", "high", "sddefault") } ?: images.firstOrNull()
    }?.text("url") ?: ""
    fun video(json: JSONObject): Video = Video(json.text("videoId", json.text("video_id")),
        json.text("title", "Unavailable video"), json.text("author", json.text("channel_name")),
        json.text("authorId", json.text("channel_id")), thumbnail(json),
        json.optLong("lengthSeconds", json.optLong("length_seconds")), json.optLong("viewCount"),
        json.text("publishedText", json.text("latest_watched")), json.optBoolean("liveNow"), json.text("indexId"), json.isNull("title"), json.opt("isMember") == true)
    fun videos(array: JSONArray): List<Video> = array.objects().filter { it.text("videoId", it.text("video_id")).isNotBlank() }.map(::video)
    fun details(json: JSONObject): VideoDetails = VideoDetails(video(json), json.text("description"),
        json.text("dashUrl"), json.text("hlsUrl"), json.optJSONArray("formatStreams")?.objects()?.lastOrNull()?.text("url") ?: "",
        json.optJSONArray("captions")?.objects()?.map { Caption(it.text("label"), it.text("languageCode", it.text("language_code")), it.text("url")) } ?: emptyList(),
        json.optJSONArray("recommendedVideos")?.let(::videos) ?: emptyList(),
        (json.optJSONArray("adaptiveFormats")?.objects().orEmpty() + json.optJSONArray("formatStreams")?.objects().orEmpty()).map(::streamFormat))
    fun streamFormat(j: JSONObject): StreamFormat {
        val size = j.text("size").split('x')
        val type = j.text("type", j.text("mimeType"))
        val codec = Regex("codecs=\"?([^\";]+)").find(type)?.groupValues?.get(1) ?: j.text("encoding")
        val audio = j.optJSONObject("audioTrack")?.let { AudioIdentity(it.text("id"), it.text("displayName"),
            if (it.opt("audioIsDefault") is Boolean) it.getBoolean("audioIsDefault") else null) }
        return StreamFormat(j.text("itag"), type.substringBefore(';').trim(), codec,
            size.getOrNull(0)?.toIntOrNull() ?: j.optInt("width"), size.getOrNull(1)?.toIntOrNull() ?: j.optInt("height"),
            j.optDouble("fps", 0.0).toFloat().takeIf { it.isFinite() && it > 0 } ?: 0f,
            j.optLong("bitrate", 0).coerceAtLeast(0), j.optLong("clen", j.optLong("contentLength", 0)).coerceAtLeast(0),
            audio, if (j.opt("isDrc") is Boolean) j.getBoolean("isDrc") else null)
    }
    fun playlist(json: JSONObject) = Playlist(json.text("playlistId"), json.text("title"), json.optInt("videoCount"), json.text("privacy", "private"), json.text("description"))
    fun channel(json: JSONObject) = Channel(json.text("authorId"), json.text("author"), json.text("description"), json.text("subCountText", json.optLong("subCount").toString()),
        json.optJSONArray("authorThumbnails")?.objects()?.lastOrNull()?.text("url") ?: "",
        json.optJSONArray("tabs")?.let { tabs -> (0 until tabs.length()).mapNotNull { tabs.opt(it) as? String } } ?: emptyList())
}
