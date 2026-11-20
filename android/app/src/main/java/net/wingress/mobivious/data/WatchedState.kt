package net.wingress.mobivious.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.math.roundToInt

internal enum class HistoryEvent(val wire: String) { VISIT("visit"), COMPLETE("complete") }
internal enum class HistoryProtocol { LEGACY, COMPLETION_ONLY, EVENTS }

data class PlaybackSnapshot(val watched: Set<String> = emptySet(), val positions: Map<String, Long> = emptyMap(),
    val completed: Set<String>? = null, val historyEvents: Boolean = false) {
    internal val historyProtocol get() = when {
        historyEvents -> HistoryProtocol.EVENTS
        completed != null -> HistoryProtocol.COMPLETION_ONLY
        else -> HistoryProtocol.LEGACY
    }

    companion object {
        private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
        private fun org.json.JSONArray.videoIds() =
            (0 until length()).mapNotNull { opt(it) as? String }.filter(videoId::matches).toSet()

        fun parse(json: JSONObject): PlaybackSnapshot {
            val positions = json.getJSONObject("positions")
            val watched = json.getJSONArray("watched")
            val completed = if (json.has("completed")) json.optJSONArray("completed")?.videoIds().orEmpty() else null
            return PlaybackSnapshot(watched.videoIds(), positions.keys().asSequence().mapNotNull { id ->
                val value = positions.opt(id)
                if (videoId.matches(id) && value is Number && value.toDouble().isFinite() &&
                    value.toDouble() >= 0 && value.toDouble() <= Int.MAX_VALUE && value.toDouble() == value.toLong().toDouble())
                    id to value.toLong() else null
            }.toMap(), completed, json.opt("historyEvents") == true)
        }
    }
}

data class WatchedState(val context: ApiContext? = null, val watched: Set<String> = emptySet(),
    val positions: Map<String, Long> = emptyMap(), val loading: Boolean = false, val error: String? = null,
    val historyRevision: Long = 0, val completed: Set<String> = emptySet())

data class VideoIndicator(val watched: Boolean, val percent: Int?, val bar: Float?) {
    val description: String get() = listOfNotNull(if (watched) "In watch history" else null,
        if (bar == 1f) "Playback complete" else percent?.let { "Playback progress $it percent" }).joinToString(". ")
}

object WatchedIndicators {
    fun forVideo(video: Video, state: WatchedState): VideoIndicator {
        val watched = video.id in state.watched
        val position = state.positions[video.id]?.takeIf { it > 0 }
        val percent = if (position != null && video.duration > 0 && !video.live)
            (position.toDouble() / video.duration * 100).roundToInt().coerceIn(0, 99) else null
        if (!video.live && video.id in state.completed && (percent == null || percent >= 95))
            return VideoIndicator(watched, null, 1f)
        return VideoIndicator(watched, percent, percent?.let { it.coerceAtLeast(5) / 100f })
    }
}

/** Device fallback values are scoped separately from shared server history. */
interface LocalPlaybackPositions {
    fun positions(context: ApiContext): Map<String, Long>
    fun setPosition(context: ApiContext, id: String, seconds: Long)
    fun clearPositions(context: ApiContext)
}

/** Shared by the UI and service, including while no Activity is attached. */
class WatchedRepository(private val api: InvidiousApi, private val local: LocalPlaybackPositions,
    private val sync: PlaybackSyncRepository? = null) {
    private val lock = Any()
    private val writes = sync?.writes ?: Mutex()
    private val mutableState = MutableStateFlow(WatchedState())
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var revision = 0L
    private var historyRevision = 0L
    private var clearGeneration = 0L
    private var positionGeneration = 0L
    private val historyGeneration = mutableMapOf<String, Long>()
    private val historyRevisions = mutableMapOf<String, Long>()
    private val completionRevisions = mutableMapOf<String, Long>()
    private val positionRevisions = mutableMapOf<String, Long>()
    private val pendingPositions = mutableSetOf<String>()
    private var journalPending = emptyMap<String, PendingPlayback>()
    private var clearedAt = 0L
    private var positionsClearedAt = 0L
    private var savePosition = false
    private var positionsConfigured = false
    private var snapshot = PlaybackSnapshot()
    private val localVisits = mutableSetOf<String>()
    private data class Refresh(val context: ApiContext, val epoch: Long, val revision: Long)
    private var refreshing: Refresh? = null
    private data class Write(val context: ApiContext, val epoch: Long, val clear: Long, val position: Long, val history: Long)

    fun reset(context: ApiContext = api.context()) = synchronized(lock) {
        if (mutableState.value.context == context) return@synchronized
        epoch++; revision++; clearGeneration++; positionGeneration++
        historyGeneration.clear(); historyRevisions.clear(); completionRevisions.clear(); positionRevisions.clear(); pendingPositions.clear()
        historyRevision = 0
        localVisits.clear()
        journalPending = sync?.pending(context).orEmpty()
        refreshing = null; clearedAt = revision; positionsClearedAt = revision
        savePosition = sync?.settings(context)?.position == true; positionsConfigured = false; snapshot = PlaybackSnapshot(positions = if (savePosition) local.positions(context) else emptyMap())
        mutableState.value = WatchedState(context)
    }
    fun configure(context: ApiContext, rememberPosition: Boolean) = synchronized(lock) {
        if (context != api.context()) return@synchronized
        reset(context)
        if (!rememberPosition && (!positionsConfigured || savePosition)) {
            try { local.clearPositions(context) }
            catch (_: CancellationException) { return@synchronized }
            catch (e: Exception) {
                mutableState.value = mutableState.value.copy(error = e.message ?: "Could not clear playback positions on this device.")
                return@synchronized
            }
            revision++
            positionsClearedAt = revision
            positionGeneration++
            snapshot = snapshot.copy(positions = emptyMap())
        }
        savePosition = rememberPosition
        positionsConfigured = true
        mutableState.value = mutableState.value.copy(error = null)
        if (context.account == null) snapshot = PlaybackSnapshot(positions = if (savePosition) local.positions(context) else emptyMap())
        publish()
    }
    fun reconcilePending() = synchronized(lock) {
        val context = api.context()
        if (mutableState.value.context == context) {
            val next = sync?.pending(context).orEmpty()
            val historyEnabled = sync?.settings(context)?.history == true
            val protocol = api.knownHistoryProtocol(context)
            (journalPending.keys + next.keys).forEach { id ->
                val before = journalPending[id]; val after = next[id]
                if (before?.history != after?.history || before?.completion != after?.completion) {
                    historyRevisions[id] = ++revision
                    if (historyEnabled) {
                        if (before?.history != null && after?.history == null) acknowledgeHistory(id, HistoryEvent.VISIT, protocol)
                        if (before?.completion != null && after?.completion == null) {
                            acknowledgeHistory(id, HistoryEvent.COMPLETE, protocol)
                            completionRevisions[id] = revision
                        }
                    }
                    if (before?.history != null && after?.history == null || before?.completion != null && after?.completion == null) {
                        historyRevision++
                    }
                }
                if (before?.position != after?.position) {
                    positionRevisions[id] = ++revision
                    if (after?.position != null) snapshot = snapshot.copy(positions = if (after.seconds == 0L)
                        snapshot.positions - id else snapshot.positions + (id to after.seconds))
                }
            }
            journalPending = next
            savePosition = sync?.settings(context)?.position ?: savePosition
            publish()
        }
    }
    private fun acknowledgeHistory(id: String, event: HistoryEvent, protocol: HistoryProtocol?) {
        if (event == HistoryEvent.VISIT && protocol == HistoryProtocol.COMPLETION_ONLY) {
            localVisits.add(id)
            return
        }
        localVisits.remove(id)
        snapshot = snapshot.copy(watched = snapshot.watched + id,
            completed = if (event == HistoryEvent.COMPLETE) snapshot.completed.orEmpty() + id else snapshot.completed)
    }
    private fun publish() {
        val context = mutableState.value.context
        val pending = if (context == api.context() && context.account != null) sync?.pending(context).orEmpty() else emptyMap()
        val completed = snapshot.completed.orEmpty() + pending.filterValues { it.completion != null }.keys
        val watched = snapshot.watched + localVisits + pending.filterValues { it.history != null || it.completion != null }.keys
        val positions = snapshot.positions.toMutableMap()
        pending.forEach { (id, entry) -> if (entry.position != null) {
            if (entry.seconds == 0L) positions.remove(id) else positions[id] = entry.seconds
        } }
        mutableState.value = mutableState.value.copy(watched = watched, completed = completed,
            positions = if (savePosition) positions else emptyMap(), historyRevision = historyRevision)
    }
    suspend fun refresh() {
        val request = synchronized(lock) {
            val context = api.context(); reset(context)
            if (context.account == null) {
                snapshot = PlaybackSnapshot(positions = if (savePosition) local.positions(context) else emptyMap()); publish()
                return
            }
            if (refreshing?.context == context) return
            Refresh(context, epoch, revision).also { refreshing = it; mutableState.value = mutableState.value.copy(loading = true, error = null) }
        }
        try {
            val fetched = api.playback(request.context)
            synchronized(lock) {
                if (refreshing !== request || request.context != api.context() || request.epoch != epoch) return@synchronized
                // Merge untouched videos while retaining changes newer than this read.
                // Clearing history invalidates the whole older snapshot.
                if (clearedAt <= request.revision) {
                    val watched = fetched.watched.toMutableSet()
                    val completed = (fetched.completed ?: snapshot.completed.orEmpty().intersect(watched)).toMutableSet()
                    localVisits.removeAll(fetched.watched)
                    historyRevisions.filterValues { it > request.revision }.keys.forEach { id ->
                        if (id in snapshot.watched) watched.add(id) else watched.remove(id)
                    }
                    completionRevisions.filterValues { it > request.revision }.keys.forEach { id ->
                        if (id in snapshot.completed.orEmpty()) completed.add(id) else completed.remove(id)
                    }
                    val positions = when {
                        !savePosition -> mutableMapOf()
                        positionsClearedAt > request.revision -> snapshot.positions.toMutableMap()
                        else -> fetched.positions.toMutableMap()
                    }
                    if (savePosition) (positionRevisions.filterValues { it > request.revision }.keys + pendingPositions).forEach { id ->
                        snapshot.positions[id]?.let { positions[id] = it } ?: positions.remove(id)
                    }
                    snapshot = fetched.copy(watched = watched, positions = positions, completed = completed)
                }
                publish()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            synchronized(lock) {
                if (refreshing === request && request.context == api.context())
                    mutableState.value = mutableState.value.copy(error = e.message ?: "Unable to refresh playback indicators.")
            }
        } finally {
            synchronized(lock) {
                if (refreshing === request) { refreshing = null; mutableState.value = mutableState.value.copy(loading = false) }
            }
        }
    }
    private fun ticket(context: ApiContext, id: String = ""): Write = synchronized(lock) {
        if (context != api.context()) throw CancellationException("Account or instance changed")
        reset(context)
        Write(context, epoch, clearGeneration, positionGeneration, historyGeneration[id] ?: 0L)
    }
    private fun valid(write: Write) = write.context == api.context() && write.epoch == epoch && write.clear == clearGeneration
    private suspend fun write(ticket: Write, allowed: () -> Boolean = { true }, positionId: String? = null, operation: suspend () -> Unit,
        update: () -> Unit) = writes.withLock {
        synchronized(lock) {
            if (!valid(ticket) || !allowed()) return@withLock
            positionId?.let { pendingPositions.add(it) }; revision++
        }
        try {
            operation()
            synchronized(lock) { if (valid(ticket) && allowed()) { update(); revision++; publish() } }
        } finally {
            synchronized(lock) { if (ticket.epoch == epoch) {
                revision++
                positionId?.let { pendingPositions.remove(it); positionRevisions[it] = revision }
            } }
        }
    }
    internal suspend fun recordWatched(context: ApiContext, id: String, event: HistoryEvent = HistoryEvent.VISIT) {
        val ticket = ticket(context, id)
        if (context.account == null) return
        if (sync != null) {
            synchronized(lock) {
                if (valid(ticket) && ticket.history == (historyGeneration[id] ?: 0L) && sync.enqueueHistory(context, id, event)) {
                    historyRevisions[id] = ++revision
                    historyRevision++
                    publish()
                }
            }
            return
        }
        val protocol = api.knownHistoryProtocol(context) ?: HistoryProtocol.LEGACY
        write(ticket, { ticket.history == (historyGeneration[id] ?: 0L) },
            operation = { api.sendHistoryEvent(id, event, protocol, context) }) {
            acknowledgeHistory(id, event, protocol)
            historyRevisions[id] = revision + 1
            if (event == HistoryEvent.COMPLETE) completionRevisions[id] = revision + 1
            historyRevision++
        }
    }
    suspend fun savePosition(context: ApiContext, id: String, seconds: Long) {
        val ticket = ticket(context)
        val value = seconds.coerceIn(0, Int.MAX_VALUE.toLong())
        if (sync != null && context.account != null) {
            if (sync.enqueuePosition(context, id, value)) synchronized(lock) {
                if (valid(ticket) && savePosition && ticket.position == positionGeneration) {
                    snapshot = snapshot.copy(positions = if (value == 0L) snapshot.positions - id else snapshot.positions + (id to value))
                    positionRevisions[id] = ++revision; publish()
                }
            }
            return
        }
        write(ticket, { savePosition && ticket.position == positionGeneration }, positionId = id, operation = {
            // Keep the existing local resume fallback even when the network write fails.
            synchronized(lock) { if (valid(ticket) && savePosition && ticket.position == positionGeneration) {
                local.setPosition(context, id, value)
                snapshot = snapshot.copy(positions = if (value == 0L) snapshot.positions - id else snapshot.positions + (id to value))
                revision++; positionRevisions[id] = revision; publish()
            } }
            if (context.account != null) api.position(id, value, context)
        }) { /* Position was already published from the device save. */ }
    }
    suspend fun removeHistory(context: ApiContext, id: String) {
        val ticket = ticket(context)
        write(ticket, operation = { api.removeHistory(id, context) }) {
            sync?.discard(context, id)
            historyGeneration[id] = (historyGeneration[id] ?: 0L) + 1
            localVisits.remove(id)
            snapshot = snapshot.copy(watched = snapshot.watched - id, completed = snapshot.completed?.minus(id))
            historyRevisions[id] = revision + 1
            completionRevisions[id] = revision + 1
            historyRevision++
        }
    }
    suspend fun clearHistory(context: ApiContext) {
        val ticket = ticket(context)
        write(ticket, operation = { api.clearHistory(context) }) {
            sync?.discard(context)
            clearGeneration++; clearedAt = revision + 1; positionsClearedAt = clearedAt
            localVisits.clear()
            local.clearPositions(context); snapshot = PlaybackSnapshot()
            historyRevision++
        }
    }
}
