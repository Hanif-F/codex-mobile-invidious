package net.wingress.mobivious.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Clip bounds always use absolute source milliseconds; the player exposes relative milliseconds. */
data class Clip(val id: String, val title: String, val startMs: Long, val endMs: Long,
    val video: Video, val server: String, val native: Boolean = true, val creator: String = "", val createdAt: Long? = null) {
    val durationMs get() = endMs - startMs
    val permalink get() = if (native) "${server.trimEnd('/')}/clip/$id" else "https://www.youtube.com/clip/$id"
    fun owned(context: ApiContext) = native && server.trimEnd('/') == context.server.trimEnd('/') && creator.isNotBlank() && creator == context.account?.username
    fun json() = JSONObject().put("clipId", id).put("clipTitle", title).put("startTime", startMs / 1000.0)
        .put("endTime", endMs / 1000.0).put("type", if (native) "invidiousClip" else "youtubeClip")
        .put("creator", creator).put("createdAt", createdAt).put("server", server)
        .put("video", JSONObject().put("videoId", video.id).put("title", video.title).put("author", video.author)
            .put("authorId", video.channelId).put("lengthSeconds", video.duration).put("thumbnail", video.thumbnail)
            .apply { if (video.authorAvatar.isNotBlank()) put("authorThumbnail", video.authorAvatar) })
    companion object {
        fun parse(j: JSONObject, id: String, server: String): Clip {
            val native = j.text("type") == "invidiousClip"
            val actualId = j.text("clipId", id)
            require(ClipRules.validId(actualId) && (!native || ClipRules.nativeId(actualId))) { "Invalid clip ID." }
            val start = ClipRules.milliseconds(j.opt("startTime"))
            val end = ClipRules.milliseconds(j.opt("endTime"))
            require(start != null && end != null && end > start) { "This clip has no valid playback range." }
            val source = j.getJSONObject("video")
            val video = ApiParser.video(source).copy(thumbnail = source.text("thumbnail", ApiParser.thumbnail(source)))
            require(video.id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "This clip has no valid source video." }
            require(!native || end - start in 5_000..120_000) { "Invalid clip duration." }
            return Clip(actualId, j.text("clipTitle", "Clip").ifBlank { "Clip" }, start, end, video, server.trimEnd('/'), native,
                j.text("creator"), (j.opt("createdAt") as? Number)?.toLong()?.takeIf { it > 0 })
        }
    }
}
data class ClipLink(val id: String, val server: String, val url: String)
object ClipRules {
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,128}"))
    fun nativeId(id: String) = id.matches(Regex("IVCL[A-Za-z0-9_-]{32}"))
    fun milliseconds(value: Any?): Long? = runCatching {
        if (value !is Number) return null
        val n = BigDecimal(value.toString()).multiply(BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        n.takeIf { it >= 0 }
    }.getOrNull()
    fun titleCount(value: String) = value.codePointCount(0, value.length)
    fun error(title: String, start: Long, end: Long, duration: Long): String? = when {
        title.trim().isEmpty() || titleCount(title.trim()) > 140 -> "Add a title containing 1–140 characters."
        start < 0 || start > duration || end < start || end > duration || end - start !in 5_000..120_000 -> "Choose 5–120 seconds within the video."
        else -> null
    }
    fun eligible(details: VideoDetails) = !details.video.live && details.upcoming != true && !details.video.membersOnly &&
        details.video.duration in 5..Long.MAX_VALUE / 1000 && details.notice.isBlank() && (details.premiereTimestamp == null || details.premiereTimestamp <= System.currentTimeMillis() / 1000)
    fun defaultRange(position: Long, duration: Long): LongRange {
        val length = minOf(30_000L, duration)
        val start = ((position / 100) * 100 - 15_000).coerceIn(0, (duration - length).coerceAtLeast(0))
        return start..start + length
    }
    fun timestamp(ms: Long, hours: Boolean = ms >= 3_600_000): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return if (hours) String.format(Locale.ROOT, "%d:%02d:%02d.%d", seconds / 3600, seconds / 60 % 60, seconds % 60, ms / 100 % 10)
        else String.format(Locale.ROOT, "%d:%02d.%d", seconds / 60, seconds % 60, ms / 100 % 10)
    }
    fun parseTimestamp(raw: String): Long? = runCatching {
        val parts = raw.trim().split(':')
        require(parts.size in 2..3 && parts.all { it.matches(Regex("[0-9]+(?:\\.[0-9])?")) })
        val seconds = BigDecimal(parts.last()); require(seconds >= BigDecimal.ZERO && seconds < BigDecimal(60))
        val minutes = parts[parts.lastIndex - 1].toLong(); require(parts.size == 2 || minutes < 60)
        val hours = if (parts.size == 3) parts[0].toLong() else 0
        BigDecimal(hours).multiply(BigDecimal(3600)).add(BigDecimal(minutes).multiply(BigDecimal(60))).add(seconds)
            .multiply(BigDecimal(1000)).longValueExact()
    }.getOrNull()
}
data class StoryboardTrack(val url: String, val width: Int, val height: Int)
data class StoryboardFrame(val startMs: Long, val endMs: Long, val url: String, val x: Int, val y: Int, val width: Int, val height: Int)
object StoryboardVtt {
    fun parse(text: String, base: String): List<StoryboardFrame> {
        val timing = Regex("(\\d{2,}:\\d{2}:\\d{2}\\.\\d{3})\\s+-->\\s+(\\d{2,}:\\d{2}:\\d{2}\\.\\d{3})")
        fun ms(s: String): Long = s.split(':').let { p -> (p[0].toLong() * 3600 + p[1].toLong() * 60) * 1000 + BigDecimal(p[2]).multiply(BigDecimal(1000)).longValueExact() }
        return text.replace("\r", "").split(Regex("\n\\s*\n")).mapNotNull { block -> runCatching {
            val lines = block.lines(); val index = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (index < 0) return@runCatching null
            val range = timing.find(lines[index])!!; val asset = lines.getOrNull(index + 1)?.trim() ?: return@runCatching null
            val rect = Regex("#xywh=(\\d+),(\\d+),(\\d+),(\\d+)").find(asset) ?: return@runCatching null
            val url = base.toHttpUrlOrNull()?.resolve(asset.substringBefore('#')) ?: return@runCatching null
            val r = rect.groupValues.drop(1).map(String::toInt)
            require(r[2] in 1..4096 && r[3] in 1..4096 && ms(range.groupValues[2]) > ms(range.groupValues[1]))
            StoryboardFrame(ms(range.groupValues[1]), ms(range.groupValues[2]), url.toString(), r[0], r[1], r[2], r[3])
        }.getOrNull() }.take(100_000)
    }
}
