package net.wingress.mobivious.data

data class VideoChapter(val startMs: Long, val title: String)

/** Matches the web fork's manual description chapters; requires no upstream lookup. */
object ChapterRules {
    private val timestamp = Regex("(?U)^\\s*(?:[-*•]\\s+)?([0-9]+):([0-9]{2})(?::([0-9]{2}))?(?:\\s+|\\s*[-–—|]\\s*)(.+?)\\s*$")
    private val separator = Regex("^[-–—|:]\\s*")

    fun parse(description: String, durationSeconds: Long, live: Boolean = false): List<VideoChapter> {
        if (live || durationSeconds <= 0) return emptyList()
        val seen = mutableSetOf<Long>()
        val chapters = description.lineSequence().mapNotNull { line ->
            val match = timestamp.matchEntire(line) ?: return@mapNotNull null
            val first = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val second = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val third = match.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull()
            // Bound before multiplication, including pathological descriptions.
            if (first > Int.MAX_VALUE || second >= 60 || third != null && third >= 60) return@mapNotNull null
            val start = if (third == null) first * 60 + second else first * 3600 + second * 60 + third
            val title = match.groupValues[4].replaceFirst(separator, "").trim()
            if (title.isEmpty() || start >= durationSeconds || !seen.add(start)) null else VideoChapter(start * 1000, title)
        }.sortedBy(VideoChapter::startMs).toList()
        return chapters.takeIf { it.size >= 2 }.orEmpty()
    }

    fun available(chapters: List<VideoChapter>, durationMs: Long, live: Boolean): List<VideoChapter> =
        if (live || durationMs <= 0) emptyList()
        else chapters.filter { it.startMs in 0 until durationMs }.takeIf { it.size >= 2 }.orEmpty()

    fun current(chapters: List<VideoChapter>, positionMs: Long): VideoChapter? =
        chapters.lastOrNull { it.startMs <= positionMs }
}
