package net.wingress.mobivious.data

import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

data class QueueSource(val id: String, val title: String = "", val count: Int = 0, val mix: Boolean = id.startsWith("RD"), val owned: Boolean = false, val seedVideoId: String? = null)
data class QueuePage(val source: QueueSource, val videos: List<Video>, val playlist: Playlist? = null)
data class QueueOccurrence(val key: String, val video: Video, val sourceIndex: Int? = null,
    val removed: Boolean = false, val tail: Boolean = false) {
    companion object {
        fun local(video: Video, tail: Boolean = false) = QueueOccurrence(UUID.randomUUID().toString(), video, tail = tail)
        fun source(id: String, video: Video, index: Int) = QueueOccurrence("$id:${video.indexId.ifBlank { "$index" }}", video, index)
    }
}
enum class QueueRepeat(val label: String) { OFF("Off"), ONE("One"), ALL("All") }
data class PlaybackQueueSnapshot(val token: String = "", val context: ApiContext? = null,
    val source: QueueSource? = null, val items: List<QueueOccurrence> = emptyList(), val currentKey: String? = null,
    val details: VideoDetails? = null, val loading: Boolean = false, val error: String? = null,
    val sourceError: String? = null, val sourceLoading: Boolean = false, val sourceComplete: Boolean = true,
    val repeat: QueueRepeat = QueueRepeat.OFF, val explicitQueue: Boolean = false) {
    val currentIndex get() = items.indexOfFirst { it.key == currentKey }
    val current get() = items.getOrNull(currentIndex)
    fun json() = JSONObject().put("token", token).put("current", currentKey).put("repeat", repeat.name)
        .put("loading", loading).put("error", error).put("sourceError", sourceError).put("sourceLoading", sourceLoading)
        .put("sourceComplete", sourceComplete).put("explicitQueue", explicitQueue)
        .put("source", source?.let { JSONObject().put("id", it.id).put("title", it.title).put("count", it.count).put("mix", it.mix).put("owned", it.owned).put("seedVideoId", it.seedVideoId) })
        .put("items", JSONArray(items.map { JSONObject().put("key", it.key).put("videoId", it.video.id).put("title", it.video.title)
            .put("author", it.video.author).put("authorId", it.video.channelId).put("index", it.sourceIndex).put("indexId", it.video.indexId)
            .put("unavailable", it.video.unavailable).put("isMember", it.video.membersOnly).put("removed", it.removed).put("tail", it.tail) }))
}

object QueueRules {
    fun eligible(item: QueueOccurrence, showMembers: Boolean) = !item.removed && !item.video.unavailable && (showMembers || !item.video.membersOnly)
    fun successor(state: PlaybackQueueSnapshot, direction: Int, showMembers: Boolean, blocked: Set<String> = emptySet()): QueueOccurrence? {
        val indices = if (direction > 0) (state.currentIndex + 1 until state.items.size) else (state.currentIndex - 1 downTo 0)
        return indices.firstNotNullOfOrNull { state.items.getOrNull(it)?.takeIf { item -> eligible(item, showMembers) && item.video.channelId !in blocked } }
    }
    // Source windows overlap. Keep occurrences and local edits, never deduplicate video IDs.
    fun merge(existing: List<QueueOccurrence>, incoming: List<QueueOccurrence>, mix: Boolean = false): List<QueueOccurrence> {
        val result = existing.toMutableList()
        incoming.sortedBy { it.sourceIndex }.forEach { entry ->
            if (result.none { it.key == entry.key }) {
                val before = result.indexOfFirst { it.sourceIndex != null && it.sourceIndex > (entry.sourceIndex ?: 0) }
                val tail = if (mix) -1 else result.indexOfFirst { it.tail }
                result.add(if (before >= 0) before else if (tail >= 0) tail else result.size, entry)
            }
        }
        return result
    }
    fun mergePage(state: PlaybackQueueSnapshot, page: QueuePage, index: Int, continuation: String?): List<QueueOccurrence> {
        val incoming = page.videos.mapIndexed { offset, video -> QueueOccurrence.source(page.source.id, video, video.playlistIndex ?: index + offset) }
        // Mix indices are local to each refreshed window. Anchor keys to the continuation.
        val lastIndex = state.items.mapNotNull { it.sourceIndex }.maxOrNull() ?: -1
        val entries = if (page.source.mix && continuation != null) incoming.filter { it.video.id != continuation }.mapIndexed { offset, item ->
            item.copy(key = "${page.source.id}:$continuation:${item.key}", sourceIndex = lastIndex + offset + 1)
        } else incoming
        return merge(state.items, entries, page.source.mix).also {
            if (page.source.mix && continuation != null && it.size == state.items.size)
                throw IllegalStateException("The mix returned no new successor. Retry the queue.")
        }
    }
    fun resolveSeed(items: List<QueueOccurrence>, seedKey: String?, sourceKey: String): List<QueueOccurrence> {
        val seedIndex = items.indexOfFirst { it.key == seedKey }
        if (seedIndex < 0) return items
        val inserted = items.drop(seedIndex + 1).takeWhile { it.sourceIndex == null && !it.tail }
        val keys = inserted.map { it.key }.toSet() + seedKey
        val result = items.filterNot { it.key in keys }.toMutableList()
        result.addAll(result.indexOfFirst { it.key == sourceKey } + 1, inserted)
        return result
    }
    fun insert(state: PlaybackQueueSnapshot, video: Video, next: Boolean): PlaybackQueueSnapshot {
        val entries = state.items.toMutableList()
        entries.add(if (next && state.currentIndex >= 0) state.currentIndex + 1 else entries.size, QueueOccurrence.local(video, !next))
        return state.copy(items = entries, explicitQueue = true)
    }
    fun remove(state: PlaybackQueueSnapshot, key: String) = state.copy(items = state.items.map { if (it.key == key) it.copy(removed = true) else it })
    fun automaticStart(prefs: AccountPreferences) = prefs.autoplay || prefs.continueAutoplay
    fun missingIndex(state: PlaybackQueueSnapshot, next: QueueOccurrence?): Int? {
        val source = state.source?.takeUnless { it.mix } ?: return null
        val anchor = state.current?.sourceIndex ?: state.items.take(state.currentIndex.coerceAtLeast(0)).lastOrNull { it.sourceIndex != null }?.sourceIndex ?: -1
        val end = next?.sourceIndex ?: source.count
        val known = state.items.mapNotNull { it.sourceIndex }.toSet()
        return (anchor + 1 until end).firstOrNull { it !in known }
    }
    fun missingPreviousIndex(state: PlaybackQueueSnapshot, previous: QueueOccurrence?): Int? {
        state.source?.takeUnless { it.mix } ?: return null
        val anchor = state.current?.sourceIndex ?: return null
        val known = state.items.mapNotNull { it.sourceIndex }.toSet()
        return (anchor - 1 downTo ((previous?.sourceIndex ?: -1) + 1)).firstOrNull { it !in known }
    }
}

data class PlaylistSaveState(val video: Video? = null, val context: ApiContext? = null, val lists: List<Playlist> = emptyList(),
    val loading: Boolean = false, val busy: Boolean = false, val error: String? = null, val created: Playlist? = null,
    val title: String = "", val privacy: String = "private")
data class BlockUndo(val id: String, val name: String, val context: ApiContext, val expires: Long)
