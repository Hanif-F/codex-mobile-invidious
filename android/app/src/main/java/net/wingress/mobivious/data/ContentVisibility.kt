package net.wingress.mobivious.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class SearchVisibility(val showMembers: Boolean? = null, val includeBlocked: Boolean = false) {
    fun json() = JSONObject().put("showMembers", showMembers ?: JSONObject.NULL).put("includeBlocked", includeBlocked)
    companion object {
        fun parse(json: JSONObject) = SearchVisibility(json.opt("showMembers") as? Boolean, json.opt("includeBlocked") == true)
    }
}

enum class ContentSurface { DISCOVERY, SEARCH, CHANNEL, SUBSCRIPTIONS, PLAYLIST, HISTORY, RECOMMENDATIONS }

object ContentVisibility {
    fun validChannel(id: String) = Regex("^UC[A-Za-z0-9_-]{22}$").matches(id)
    fun filter(videos: List<Video>, surface: ContentSurface, showMembers: Boolean, search: SearchVisibility = SearchVisibility(),
        blocked: Set<String> = emptySet()): List<Video> {
        val members = if (surface == ContentSurface.SEARCH) search.showMembers ?: showMembers else showMembers
        val filterBlocked = surface in listOf(ContentSurface.DISCOVERY, ContentSurface.RECOMMENDATIONS) ||
            surface == ContentSurface.SEARCH && !search.includeBlocked
        return videos.filter { (surface == ContentSurface.HISTORY || members || !it.membersOnly) &&
            (!filterBlocked || it.channelId !in blocked) }
    }
    // Pagination must operate on the original response, including hidden occurrences.
    fun merge(old: List<Video>, next: List<Video>) = (old + next).distinctBy { Triple(it.id, it.indexId, it.playlistIndex) }
    fun exhausted(old: List<Video>, next: List<Video>): Boolean = next.isEmpty() ||
        next.all { video -> old.any { it.id == video.id && it.indexId == video.indexId && it.playlistIndex == video.playlistIndex } }
}

data class BlockedChannel(val id: String, val name: String) {
    fun json() = JSONObject().put("authorId", id).put("author", name)
    companion object {
        fun parse(array: JSONArray) = array.objects().mapNotNull {
            val id = it.text("authorId")
            if (ContentVisibility.validChannel(id)) BlockedChannel(id, it.text("author").ifBlank { id }) else null
        }.distinctBy { it.id }.sortedWith(compareBy({ it.name }, { it.id }))
    }
}

interface VisibilityStore {
    fun searchVisibility(context: ApiContext): SearchVisibility
    fun saveSearchVisibility(context: ApiContext, value: SearchVisibility)
    fun blockedSnapshot(context: ApiContext): List<BlockedChannel>?
    fun saveBlockedSnapshot(context: ApiContext, value: List<BlockedChannel>)
}

data class BlockedState(val context: ApiContext? = null, val channels: List<BlockedChannel> = emptyList(),
    val loaded: Boolean = false, val loading: Boolean = false, val error: String? = null,
    val busy: Set<String> = emptySet(), val actionErrors: Map<String, String> = emptyMap()) {
    val ids: Set<String> get() = channels.map { it.id }.toSet()
}

/** Account-owned confirmed state; refreshes cannot undo newer actions or cross contexts. */
class BlockedRepository(private val api: InvidiousApi, private val local: VisibilityStore) {
    private val lock = Any()
    private val writes = Mutex()
    private val mutableState = MutableStateFlow(BlockedState())
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var revision = 0L
    private val changes = mutableMapOf<String, Long>()
    private data class Refresh(val context: ApiContext, val epoch: Long, val revision: Long)
    private var refreshing: Refresh? = null

    fun reset(context: ApiContext = api.context()) = synchronized(lock) {
        if (mutableState.value.context == context) return@synchronized
        epoch++; revision = 0; changes.clear(); refreshing = null
        val cached = if (context.account != null) local.blockedSnapshot(context) else null
        mutableState.value = BlockedState(context, cached.orEmpty(), loaded = context.account == null || cached != null)
    }

    suspend fun refresh() {
        val ticket = synchronized(lock) {
            val context = api.context(); reset(context)
            if (context.account == null || refreshing != null) return
            Refresh(context, epoch, revision).also { refreshing = it; mutableState.value = mutableState.value.copy(loading = true, error = null) }
        }
        try {
            val fetched = api.blockedChannels(ticket.context)
            synchronized(lock) {
                if (refreshing !== ticket || !valid(ticket.context, ticket.epoch)) return@synchronized
                val values = fetched.associateBy { it.id }.toMutableMap()
                val current = mutableState.value.channels.associateBy { it.id }
                (changes.filterValues { it > ticket.revision }.keys + mutableState.value.busy).forEach { id ->
                    current[id]?.let { values[id] = it } ?: values.remove(id)
                }
                publish(values.values.toList(), loaded = true)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { synchronized(lock) {
            if (valid(ticket.context, ticket.epoch)) mutableState.value = mutableState.value.copy(error = e.message ?: "Could not refresh blocked channels.")
        } } finally { synchronized(lock) {
            if (refreshing === ticket) { refreshing = null; mutableState.value = mutableState.value.copy(loading = false) }
        } }
    }

    private fun valid(context: ApiContext, version: Long) = context == api.context() && version == epoch
    private fun publish(values: List<BlockedChannel>, loaded: Boolean = mutableState.value.loaded) {
        val sorted = values.sortedWith(compareBy({ it.name }, { it.id }))
        mutableState.value = mutableState.value.copy(channels = sorted, loaded = loaded)
        if (loaded) local.saveBlockedSnapshot(mutableState.value.context!!, sorted)
    }

    suspend fun setBlocked(context: ApiContext, id: String, name: String, blocked: Boolean) {
        require(ContentVisibility.validChannel(id)) { "This video has no valid channel ID." }
        val version = synchronized(lock) {
            if (context != api.context()) throw CancellationException("Account or instance changed")
            reset(context); epoch
        }
        writes.withLock {
            synchronized(lock) {
                if (!valid(context, version)) throw CancellationException("Account or instance changed")
                changes[id] = ++revision
                mutableState.value = mutableState.value.copy(busy = mutableState.value.busy + id,
                    actionErrors = mutableState.value.actionErrors - id)
            }
            try {
                api.blockChannel(id, name, blocked, context)
                synchronized(lock) {
                    if (!valid(context, version)) throw CancellationException("Account or instance changed")
                    val values = mutableState.value.channels.filter { it.id != id }
                    publish(if (blocked) values + BlockedChannel(id, name.trim().take(200).ifBlank { id }) else values, loaded = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                synchronized(lock) { if (valid(context, version)) mutableState.value = mutableState.value.copy(
                    actionErrors = mutableState.value.actionErrors + (id to (e.message ?: "Could not update this channel."))) }
                throw e
            } finally { synchronized(lock) {
                if (valid(context, version)) {
                    changes[id] = ++revision
                    mutableState.value = mutableState.value.copy(busy = mutableState.value.busy - id)
                }
            } }
        }
    }
}
