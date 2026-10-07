package net.wingress.mobivious.data

import java.math.BigDecimal
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Null means inherit; false and empty caption/comment lists are explicit overrides. */
data class PlaybackLinkOptions(val listen: Boolean? = null, val speed: Float? = null, val local: Boolean? = null,
    val autoplay: Boolean? = null, val continueNext: Boolean? = null, val continueAutoplay: Boolean? = null,
    val loop: Boolean? = null, val quality: String? = null, val codec: String? = null, val captions: List<String>? = null,
    val region: String? = null, val comments: List<String>? = null, val related: Boolean? = null,
    val description: Boolean? = null, val savePosition: Boolean? = null) {
    fun apply(base: AccountPreferences) = base.copy(listen = listen ?: base.listen, speed = speed ?: base.speed,
        local = local ?: base.local, autoplay = autoplay ?: base.autoplay, continueNext = continueNext ?: base.continueNext,
        continueAutoplay = continueAutoplay ?: base.continueAutoplay, videoLoop = loop ?: base.videoLoop,
        qualityDash = quality ?: base.qualityDash, videoCodec = codec ?: base.videoCodec, captions = captions ?: base.captions,
        region = region ?: base.region, comments = comments ?: base.comments, relatedVideos = related ?: base.relatedVideos,
        extendDescription = description ?: base.extendDescription, savePosition = savePosition ?: base.savePosition)
    fun carried() = PlaybackLinkOptions(listen, speed, local)
    fun json() = JSONObject().apply {
        listOf("listen" to listen, "speed" to speed, "local" to local, "autoplay" to autoplay, "continue" to continueNext,
            "continue_autoplay" to continueAutoplay, "loop" to loop, "quality_dash" to quality, "video_codec" to codec,
            "region" to region, "related_videos" to related, "extend_desc" to description, "save_player_pos" to savePosition)
            .forEach { (key, value) -> if (value != null) put(key, value) }
        captions?.let { put("subtitles", it.joinToString(",")) }; comments?.let { put("comments", it.joinToString(",")) }
    }
    companion object {
        fun parse(j: JSONObject): PlaybackLinkOptions {
            fun bool(key: String): Boolean? = when (j.opt(key)?.toString()?.lowercase()) { "1", "true" -> true; "0", "false" -> false; else -> null }
            fun choices(key: String): List<String>? = (j.opt(key) as? String)?.takeIf { it.length <= 300 }?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            return PlaybackLinkOptions(bool("listen"), j.opt("speed")?.toString()?.removeSuffix("x")?.toFloatOrNull()?.takeIf { it.isFinite() && it in .25f..2f },
                bool("local"), bool("autoplay"), bool("continue"), bool("continue_autoplay"), bool("loop"),
                (j.opt("quality_dash") as? String)?.takeIf { it in PreferenceRules.qualities },
                (j.opt("video_codec") as? String)?.takeIf { value -> PreferenceRules.videoCodecs.any { it.first == value } },
                choices("subtitles")?.take(3), (j.opt("region") as? String)?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{2}")) },
                choices("comments")?.takeIf { values -> values.all { it.lowercase() in listOf("youtube", "reddit") } }?.map { it.lowercase() },
                bool("related_videos"), bool("extend_desc"), bool("save_player_pos"))
        }
        fun parse(url: HttpUrl) = parse(JSONObject().apply { url.queryParameterNames.forEach { key -> put(key, url.queryParameter(key)) } })
    }
}

data class LinkPlayback(val startMs: Long? = null, val endMs: Long? = null, val options: PlaybackLinkOptions = PlaybackLinkOptions(),
    val invalidEnd: Boolean = false) {
    fun json() = JSONObject().put("startMs", startMs).put("endMs", endMs).put("options", options.json()).put("invalidEnd", invalidEnd)
    fun bounded(durationMs: Long): LinkPlayback {
        val end = endMs?.let { if (durationMs > 0) minOf(it, durationMs) else it }
        return if (end != null && end > (startMs ?: 0)) copy(endMs = end)
            else copy(endMs = null, invalidEnd = invalidEnd || endMs != null)
    }
    companion object {
        fun parse(j: JSONObject) = LinkPlayback((j.opt("startMs") as? Number)?.toLong()?.takeIf { it >= 0 },
            (j.opt("endMs") as? Number)?.toLong()?.takeIf { it > 0 }, PlaybackLinkOptions.parse(j.optJSONObject("options") ?: JSONObject()), j.optBoolean("invalidEnd"))
    }
}

// The seconds argument remains compatible with older native callers. Parsed links retain millisecond precision.
data class VideoLink(val id: String, val seconds: Long? = null, val playlistId: String? = null, val index: Int? = null,
    val seedVideoId: String? = null, val playback: LinkPlayback = LinkPlayback()) {
    val startMs get() = playback.startMs ?: seconds?.takeIf { it in 0..Long.MAX_VALUE / 1000 }?.times(1000)
}
data class ChannelLink(val id: String? = null, val resolveUrl: String = "", val tab: ChannelTab? = null)
sealed interface ContentLink {
    data class Video(val link: VideoLink) : ContentLink
    data class Channel(val link: ChannelLink) : ContentLink
    data class Post(val link: PostLink) : ContentLink
    data class Hashtag(val tag: String) : ContentLink
    data class Clip(val link: ClipLink) : ContentLink
    data class Seek(val milliseconds: Long) : ContentLink
    data class External(val url: String) : ContentLink
}
object ContentLinks {
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be",
        "youtube-nocookie.com", "www.youtube-nocookie.com")
    fun youtube(url: HttpUrl) = url.host in youtubeHosts && url.port == if (url.isHttps) 443 else 80
    fun accepted(url: HttpUrl, instance: String): Boolean {
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val base = instance.toHttpUrlOrNull()
        return youtube(url) || base != null && url.scheme == base.scheme && url.host == base.host && url.port == base.port ||
            url.isHttps && url.port == 443 && url.host in setOf("invidious.wingress.net", "mobivious.wingress.net")
    }
    fun candidate(text: String): HttpUrl? = (Regex("https?://[^\\s<]+", RegexOption.IGNORE_CASE).find(text)?.value
        ?.trimEnd('.', ',', ')', '>', '"') ?: text.trim()).toHttpUrlOrNull()
    fun parse(text: String, instance: String): ContentLink? = candidate(text)?.let { native(it, instance) }
    fun resolve(raw: String, instance: String, currentVideo: String = ""): ContentLink? {
        val base = instance.toHttpUrlOrNull() ?: return null
        var url = base.resolve(raw.trim()) ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (accepted(url, instance) && url.encodedPath == "/redirect") {
            url = (url.queryParameter("q") ?: url.queryParameter("url"))?.toHttpUrlOrNull() ?: return null
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        }
        val result = native(url, instance) ?: return ContentLink.External(url.toString())
        if (result is ContentLink.Video && result.link.id == currentVideo && result.link.startMs != null &&
            result.link.playlistId == null && result.link.playback.endMs == null && result.link.playback.options == PlaybackLinkOptions())
            return ContentLink.Seek(result.link.startMs!!)
        return result
    }
    private fun native(url: HttpUrl, instance: String): ContentLink? {
        val clipPath = url.pathSegments.dropLastWhile { it.isEmpty() }
        if (clipPath.size == 2 && clipPath[0] == "clip" && ClipRules.validId(clipPath[1]) && url.username.isEmpty() && url.password.isEmpty()) {
            val native = ClipRules.nativeId(clipPath[1])
            if (accepted(url, instance) || native) {
                val origin = if (native) url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/') else instance
                return ContentLink.Clip(ClipLink(clipPath[1], origin, url.toString()))
            }
        }
        if (!accepted(url, instance)) return null
        PostLinks.parse(url.toString(), instance)?.let { return ContentLink.Post(it) }
        VideoLinks.parseUrl(url, instance)?.let { return ContentLink.Video(it) }
        var path = url.pathSegments.dropLastWhile { it.isEmpty() }
        if (path.firstOrNull() == "attribution_link") {
            val target = url.queryParameter("u")?.let { url.resolve(it) } ?: return null
            if (!accepted(target, instance) || target.encodedPath == "/attribution_link") return null
            return native(target, instance)
        }
        if (path.firstOrNull() == "profile") path = listOf("user", url.queryParameter("user") ?: return null)
        if (path.size == 2 && path[0] == "hashtag") return path[1].takeIf { it.isNotBlank() && it.length <= 100 && '/' !in it && it.none(Char::isISOControl) }?.let(ContentLink::Hashtag)
        val first = path.firstOrNull() ?: return null
        val handle = first.startsWith('@')
        if (!handle && first !in listOf("channel", "c", "user")) return null
        val name = (if (handle) first.drop(1) else path.getOrNull(1))?.takeIf {
            it.isNotBlank() && it.length <= 128 && '/' !in it && '\\' !in it && it.none(Char::isISOControl)
        } ?: return null
        val suffix = path.getOrNull(if (handle) 1 else 2)
        val tab = ChannelTab.entries.firstOrNull { it.path == when (suffix) { "community" -> "posts"; "live" -> "streams"; else -> suffix } }
        if (first == "channel") return name.takeIf(ContentVisibility::validChannel)?.let { ContentLink.Channel(ChannelLink(it, tab = tab)) }
        val canonical = "https://www.youtube.com".toHttpUrlOrNull()!!.newBuilder().apply {
            if (handle) addPathSegment("@$name") else { addPathSegment(first); addPathSegment(name) }
        }.build().toString()
        return ContentLink.Channel(ChannelLink(resolveUrl = canonical, tab = tab))
    }
}

object VideoLinks {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    fun parse(text: String, instance: String): VideoLink? = ContentLinks.candidate(text)?.let { parseUrl(it, instance) }
    internal fun parseUrl(url: HttpUrl, instance: String): VideoLink? {
        if (!ContentLinks.accepted(url, instance)) return null
        val path = url.pathSegments.dropLastWhile { it.isEmpty() }
        val route = path.firstOrNull()
        val id = when {
            url.host in setOf("youtu.be", "www.youtu.be") && path.size == 1 -> path[0]
            route == "watch" && path.size == 1 -> url.queryParameter("v")
            route in listOf("watch", "w", "v", "e", "shorts", "embed", "live") && path.size == 2 -> path[1]
            route in listOf("playlist", "mix") && path.size == 1 -> null
            else -> return null
        }
        val list = url.queryParameter("list")?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{1,100}$")) }
        if (id == null && list == null || id != null && !idPattern.matches(id)) return null
        val index = url.queryParameter("index")?.toLongOrNull()?.let { if (ContentLinks.youtube(url)) it - 1 else it }
            ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
        val rawStart = url.queryParameter("t") ?: url.queryParameter("time_continue") ?: url.queryParameter("start") ?: url.fragment?.removePrefix("t=")
        val start = rawStart?.let(::timestampMillis)
        val rawEnd = url.queryParameter("end")
        val end = rawEnd?.let(::timestampMillis)?.takeIf { it > (start ?: 0) }
        val preciseStart = start?.takeIf { it % 1000 != 0L }
        return VideoLink(id.orEmpty(), start?.div(1000), list, index,
            if (list?.startsWith("RD") == true) url.queryParameter("continuation")?.takeIf { idPattern.matches(it) } ?: id else null,
            LinkPlayback(preciseStart, end, PlaybackLinkOptions.parse(url), invalidEnd = rawEnd != null && end == null))
    }
    fun timestampSeconds(value: String): Long? = timestampMillis(value)?.div(1000)
    fun timestampMillis(value: String): Long? = runCatching {
        val number = if (value.matches(Regex("\\d+(?:\\.\\d+)?s?"))) BigDecimal(value.removeSuffix("s")).multiply(BigDecimal(1000))
        else if (value.matches(Regex("\\d{1,}:\\d{2}(?::\\d{2})?"))) {
            val pieces = value.split(':').map(String::toLong)
            if (pieces.drop(1).any { it >= 60 }) return null
            pieces.fold(BigDecimal.ZERO) { total, n -> total.multiply(BigDecimal(60)).add(BigDecimal(n)) }.multiply(BigDecimal(1000))
        } else {
            if (!value.matches(Regex("(?:\\d+h)?(?:\\d+m)?(?:\\d+(?:\\.\\d+)?s)?(?:\\d+ms)?")) || value.isEmpty()) return null
            Regex("(\\d+(?:\\.\\d+)?)(ms|h|m|s)").findAll(value).fold(BigDecimal.ZERO) { total, part ->
                total.add(BigDecimal(part.groupValues[1]).multiply(BigDecimal(when (part.groupValues[2]) { "h" -> 3600000; "m" -> 60000; "s" -> 1000; else -> 1 })))
            }
        }
        number.toBigInteger().takeIf { it.signum() >= 0 && it.bitLength() <= 63 }?.toLong()
    }.getOrNull()
    fun share(server: String, queue: PlaybackQueueSnapshot, positionMs: Long): String? {
        if (queue.context != null && queue.context.server.toHttpUrlOrNull() != server.toHttpUrlOrNull()) return null
        val current = queue.current ?: return null
        val id = queue.details?.video?.id ?: current.video.id
        if (!idPattern.matches(id)) return null
        val url = server.toHttpUrlOrNull()?.newBuilder()?.addPathSegment("watch")?.addQueryParameter("v", id) ?: return null
        val playback = current.linkPlayback
        val at = positionMs.coerceAtLeast(0).let { playback?.endMs?.let { end -> minOf(it, (end - 1).coerceAtLeast(0)) } ?: it }
        url.addQueryParameter("t", time(at))
        if (current.sourceIndex != null && queue.source != null && current.key.startsWith("${queue.source.id}:")) {
            url.addQueryParameter("list", queue.source.id).addQueryParameter("index", current.sourceIndex.toString())
            if (queue.source.mix) url.addQueryParameter("continuation", id)
        }
        playback?.endMs?.let { url.addQueryParameter("end", time(it)) }
        queue.shareOptions.json().let { j -> j.keys().forEach { key -> url.addQueryParameter(key, j.get(key).toString()) } }
        return url.build().toString()
    }
    private fun time(ms: Long) = BigDecimal(ms).divide(BigDecimal(1000)).stripTrailingZeros().toPlainString()
}
