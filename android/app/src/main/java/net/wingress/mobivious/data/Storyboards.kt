package net.wingress.mobivious.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class StoryboardTrack(val url: String, val width: Int, val height: Int)
data class StoryboardFrame(val startMs: Long, val endMs: Long, val url: String, val x: Int, val y: Int, val width: Int, val height: Int)

object StoryboardRules {
    fun track(tracks: List<StoryboardTrack>, sharpest: Boolean = true): StoryboardTrack? {
        val valid = tracks.filter { it.width in 1..4096 && it.height in 1..4096 && it.url.isNotBlank() }
        return if (sharpest) valid.maxByOrNull { it.width.toLong() * it.height }
            else valid.minByOrNull { kotlin.math.abs(it.width - 160) }
    }

    /** Frames are sorted; cue intervals are half open, including at exact boundaries. */
    fun frame(frames: List<StoryboardFrame>, sourceMs: Long): StoryboardFrame? {
        var low = 0; var high = frames.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (frames[mid].startMs <= sourceMs) low = mid + 1 else high = mid - 1
        }
        return frames.getOrNull(high)?.takeIf { sourceMs < it.endMs }
    }

    fun sourcePosition(targetMs: Long, durationMs: Long, clipStartMs: Long = 0): Long =
        targetMs.coerceIn(0, (durationMs - 1).coerceAtLeast(0)) + clipStartMs

    fun fits(frame: StoryboardFrame, width: Int, height: Int): Boolean =
        frame.x >= 0 && frame.y >= 0 && frame.width > 0 && frame.height > 0 &&
            frame.x.toLong() + frame.width <= width && frame.y.toLong() + frame.height <= height
}

object StoryboardVtt {
    private val timing = Regex("(\\d{2,}:\\d{2}:\\d{2}\\.\\d{3})\\s+-->\\s+(\\d{2,}:\\d{2}:\\d{2}\\.\\d{3})")
    private val rectangle = Regex("#xywh=(\\d+),(\\d+),(\\d+),(\\d+)$")
    private fun ms(text: String): Long = text.split(':').let { p ->
        require(p[1].toInt() < 60 && p[2].substringBefore('.').toInt() < 60)
        Math.addExact(Math.multiplyExact(p[0].toLong(), 3_600_000), p[1].toLong() * 60_000 + p[2].replace(".", "").toLong())
    }
    fun parse(text: String, base: String): List<StoryboardFrame> {
        val origin = base.toHttpUrlOrNull() ?: return emptyList()
        return text.replace("\r", "").splitToSequence(Regex("\n\\s*\n")).mapNotNull { block -> runCatching {
            val lines = block.lines(); val index = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (index < 0) return@runCatching null
            val range = timing.find(lines[index])!!; val asset = lines.getOrNull(index + 1)?.trim() ?: return@runCatching null
            val rect = rectangle.find(asset) ?: return@runCatching null
            val url = origin.resolve(asset.substringBefore('#')) ?: return@runCatching null
            require(url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port && url.username.isEmpty() && url.password.isEmpty())
            val r = rect.groupValues.drop(1).map(String::toInt)
            val start = ms(range.groupValues[1]); val end = ms(range.groupValues[2])
            require(r[2] in 1..4096 && r[3] in 1..4096 && end > start)
            StoryboardFrame(start, end, url.toString(), r[0], r[1], r[2], r[3])
        }.getOrNull() }.take(100_000).sortedBy { it.startMs }.toList()
    }
}

/** Shared by player previews and clip editing; only completed indexes enter the bounded cache. */
class StoryboardRepository(private val fetch: suspend (StoryboardTrack, ApiContext) -> List<StoryboardFrame>) {
    private val mutex = Mutex()
    private val indexes = LinkedHashMap<Pair<ApiContext, StoryboardTrack>, List<StoryboardFrame>>(16, .75f, true)
    suspend fun frames(track: StoryboardTrack, context: ApiContext): List<StoryboardFrame> = mutex.withLock {
        val key = context to track
        indexes[key] ?: fetch(track, context).also {
            indexes[key] = it
            while (indexes.size > 16) indexes.remove(indexes.keys.first())
        }
    }
}
