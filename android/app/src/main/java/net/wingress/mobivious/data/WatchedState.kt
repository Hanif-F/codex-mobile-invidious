package net.wingress.mobivious.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.math.roundToInt

data class PlaybackSnapshot(val watched: Set<String> = emptySet(), val positions: Map<String, Long> = emptyMap()) {
    companion object {
        private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
        fun parse(json: JSONObject): PlaybackSnapshot {
            val positions = json.getJSONObject("positions")
            val watched = json.getJSONArray("watched")
            return PlaybackSnapshot((0 until watched.length()).mapNotNull { watched.opt(it) as? String }
                .filter { videoId.matches(it) }.toSet(), positions.keys().asSequence().mapNotNull { id ->
                val value = positions.opt(id)
                if (videoId.matches(id) && value is Number && value.toDouble().isFinite() &&
                    value.toDouble() >= 0 && value.toDouble() <= Int.MAX_VALUE && value.toDouble() == value.toLong().toDouble())
                    id to value.toLong() else null
            }.toMap())
        }
    }
}

data class WatchedState(val context: ApiContext? = null, val watched: Set<String> = emptySet(),
    val positions: Map<String, Long> = emptyMap(), val loading: Boolean = false, val error: String? = null,
    val historyRevision: Long = 0)

data class VideoIndicator(val watched: Boolean, val percent: Int?, val bar: Float?) {
    val description: String get() = listOfNotNull(if (watched) "In watch history" else null,
        percent?.let { "Playback progress $it percent" }).joinToString(". ")
}

object WatchedIndicators {
    fun forVideo(video: Video, state: WatchedState): VideoIndicator {
        val watched = video.id in state.watched
        val position = state.positions[video.id]
        val percent = if (position != null && video.duration > 0 && !video.live)
            (position.toDouble() / video.duration * 100).roundToInt().coerceIn(0, 100) else null
        val bar = when {
            position != null -> percent?.let { if (it > 90) 1f else it.coerceAtLeast(5) / 100f }
            watched -> 1f
            else -> null
        }
        return VideoIndicator(watched, percent, bar)
    }
}

/** Device fallback values are scoped separately from shared server history. */
interface LocalPlaybackPositions {
    fun positions(context: ApiContext): Map<String, Long>
    fun setPosition(context: ApiContext, id: String, seconds: Long)
    fun clearPositions(context: ApiContext)
}

/** Shared by the UI and service, including while no Activity is attached. */
class WatchedRepository(private val api: InvidiousApi, private val local: LocalPlaybackPositions) {
    private val lock = Any()
    private val writes = Mutex()
    private val mutableState = MutableStateFlow(WatchedState())
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var revision = 0L
    private var historyRevision = 0L
    private var clearGeneration = 0L
    private var positionGeneration = 0L
    private val historyGeneration = mutableMapOf<String, Long>()
    private val historyRevisions = mutableMapOf<String, Long>()
    private val positionRevisions = mutableMapOf<String, Long>()
    private val pendingPositions = mutableSetOf<String>()
    private var clearedAt = 0L
    private var positionsClearedAt = 0L
    private var savePosition = false
    private var positionsConfigured = false
    private var snapshot = PlaybackSnapshot()
    private data class Refresh(val context: ApiContext, val epoch: Long, val revision: Long)
    private var refreshing: Refresh? = null
    private data class Write(val context: ApiContext, val epoch: Long, val clear: Long, val position: Long, val history: Long)

    fun reset(context: ApiContext = api.context()) = synchronized(lock) {
        if (mutableState.value.context == context) return@synchronized
        epoch++; revision++; clearGeneration++; positionGeneration++
        historyGeneration.clear(); historyRevisions.clear(); positionRevisions.clear(); pendingPositions.clear()
        historyRevision = 0
        refreshing = null; clearedAt = revision; positionsClearedAt = revision
        savePosition = false; positionsConfigured = false; snapshot = PlaybackSnapshot()
        mutableState.value = WatchedState(context)
    }
    fun configure(context: ApiContext, rememberPosition: Boolean) = synchronized(lock) {
        if (context != api.context()) return@synchronized
        reset(context)
        if (!rememberPosition && (!positionsConfigured || savePosition)) {
            revision++
            positionsClearedAt = revision
            positionGeneration++
            local.clearPositions(context)
            snapshot = snapshot.copy(positions = emptyMap())
        }
        savePosition = rememberPosition
        positionsConfigured = true
        if (context.account == null) snapshot = PlaybackSnapshot(positions = if (savePosition) local.positions(context) else emptyMap())
        publish()
    }
    private fun publish() {
        mutableState.value = mutableState.value.copy(watched = snapshot.watched,
            positions = if (savePosition) snapshot.positions else emptyMap(), historyRevision = historyRevision)
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
                    historyRevisions.filterValues { it > request.revision }.keys.forEach { id ->
                        if (id in snapshot.watched) watched.add(id) else watched.remove(id)
                    }
                    val positions = when {
                        !savePosition -> mutableMapOf()
                        positionsClearedAt > request.revision -> snapshot.positions.toMutableMap()
                        else -> fetched.positions.toMutableMap()
                    }
                    if (savePosition) (positionRevisions.filterValues { it > request.revision }.keys + pendingPositions).forEach { id ->
                        snapshot.positions[id]?.let { positions[id] = it } ?: positions.remove(id)
                    }
                    snapshot = PlaybackSnapshot(watched, positions)
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
    suspend fun recordWatched(context: ApiContext, id: String) {
        val ticket = ticket(context, id)
        if (context.account == null) return
        write(ticket, { ticket.history == (historyGeneration[id] ?: 0L) }, operation = { api.watched(id, context) }) {
            snapshot = snapshot.copy(watched = snapshot.watched + id)
            historyRevisions[id] = revision + 1
            historyRevision++
        }
    }
    suspend fun savePosition(context: ApiContext, id: String, seconds: Long) {
        val ticket = ticket(context)
        val value = seconds.coerceIn(0, Int.MAX_VALUE.toLong())
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
            historyGeneration[id] = (historyGeneration[id] ?: 0L) + 1
            snapshot = snapshot.copy(watched = snapshot.watched - id)
            historyRevisions[id] = revision + 1
            historyRevision++
        }
    }
    suspend fun clearHistory(context: ApiContext) {
        val ticket = ticket(context)
        write(ticket, operation = { api.clearHistory(context) }) {
            clearGeneration++; clearedAt = revision + 1; positionsClearedAt = clearedAt
            local.clearPositions(context); snapshot = PlaybackSnapshot()
            historyRevision++
        }
    }
}
