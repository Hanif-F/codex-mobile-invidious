package net.wingress.mobivious.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class PlaybackSyncSettings(val history: Boolean, val position: Boolean)
data class PendingPlayback(val history: Long? = null, val position: Long? = null, val seconds: Long = 0)
data class PlaybackJournal(val revision: Long = 0, val settings: PlaybackSyncSettings? = null,
    val pending: Map<String, PendingPlayback> = emptyMap()) {
    fun json() = JSONObject().put("revision", revision).apply {
        settings?.let { put("settings", JSONObject().put("history", it.history).put("position", it.position)) }
        put("pending", JSONObject().apply { pending.forEach { (id, entry) ->
            put(id, JSONObject().put("history", entry.history).put("position", entry.position).put("seconds", entry.seconds))
        } })
    }
    companion object {
        fun parse(raw: String?): PlaybackJournal {
            if (raw == null) return PlaybackJournal()
            val json = JSONObject(raw)
            val entries = json.optJSONObject("pending") ?: JSONObject()
            return PlaybackJournal(json.getLong("revision"), json.optJSONObject("settings")?.let {
                PlaybackSyncSettings(it.getBoolean("history"), it.getBoolean("position"))
            }, entries.keys().asSequence().filter(SponsorBlockRules::validVideo).associateWith { id ->
                val value = entries.getJSONObject(id)
                PendingPlayback(if (value.isNull("history")) null else value.getLong("history"),
                    if (value.isNull("position")) null else value.getLong("position"),
                    value.getLong("seconds").coerceIn(0, Int.MAX_VALUE.toLong()))
            })
        }
    }
}

interface PlaybackSyncStore {
    fun playbackJournal(context: ApiContext): PlaybackJournal
    /** The optional local position and journal must commit together. */
    fun savePlaybackJournal(context: ApiContext, journal: PlaybackJournal, position: Pair<String, Long>? = null)
}

enum class PlaybackSyncResult { COMPLETE, RETRY, PAUSED }

/** Only the active profile's credentials may drain its journal. No credentials are journaled. */
interface PlaybackSyncQueue {
    fun hasPending(): Boolean
    fun reconnect()
    suspend fun flush(): PlaybackSyncResult
}

class PlaybackSyncRepository(private val api: InvidiousApi, private val store: PlaybackSyncStore) : PlaybackSyncQueue {
    private val lock = Any()
    internal val writes = Mutex()
    var changed: () -> Unit = {}
    private val configurationRevision = MutableStateFlow(0L)
    val configurationChanges = configurationRevision.asStateFlow()
    private val positionWrites = mutableMapOf<Pair<DeviceProfile, String>, Pair<Long, Long>>()
    private var settingsRevision = 0L
    private var confirmed: ApiContext? = null
    private var paused: ApiContext? = null

    private fun current(context: ApiContext) {
        if (context != api.context()) throw CancellationException("Account or instance changed")
    }
    fun settings(context: ApiContext = api.context()) = synchronized(lock) { store.playbackJournal(context).settings }
    fun pending(context: ApiContext = api.context()) = synchronized(lock) { store.playbackJournal(context).pending }
    override fun hasPending() = api.context().account != null && pending().isNotEmpty()
    fun configure(context: ApiContext, preferences: AccountPreferences) = configure(context, preferences, null)
    private fun configure(context: ApiContext, preferences: AccountPreferences, expectedRevision: Long?) {
        synchronized(lock) {
            current(context)
            if (expectedRevision != null && settingsRevision != expectedRevision) return
            if (context.account == null) return
            val settings = PlaybackSyncSettings(preferences.watchHistory, preferences.savePosition)
            val before = store.playbackJournal(context)
            val pending = before.pending.mapValues { (_, entry) -> entry.copy(
                history = entry.history.takeIf { settings.history }, position = entry.position.takeIf { settings.position })
            }.filterValues { it.history != null || it.position != null }
            val next = before.copy(settings = settings, pending = pending)
            if (next != before) store.savePlaybackJournal(context, next)
            settingsRevision++; confirmed = context; paused = null
        }
        configurationRevision.update { it + 1 }
        changed()
    }
    override fun reconnect() = synchronized(lock) { confirmed = null; paused = null }
    fun enqueueHistory(context: ApiContext, id: String): Boolean {
        synchronized(lock) {
            current(context); require(SponsorBlockRules.validVideo(id))
            if (context.account == null) return false
            val before = store.playbackJournal(context)
            if (before.settings?.history != true) return false
            val revision = before.revision + 1
            val entry = (before.pending[id] ?: PendingPlayback()).copy(history = revision)
            store.savePlaybackJournal(context, before.copy(revision = revision, pending = before.pending + (id to entry)))
        }
        changed(); return true
    }
    fun enqueuePosition(context: ApiContext, id: String, seconds: Long): Boolean {
        synchronized(lock) {
            current(context); require(SponsorBlockRules.validVideo(id))
            if (context.account == null) return false
            val before = store.playbackJournal(context)
            if (before.settings?.position != true) return false
            val revision = before.revision + 1
            val value = seconds.coerceIn(0, Int.MAX_VALUE.toLong())
            val entry = (before.pending[id] ?: PendingPlayback()).copy(position = revision, seconds = value)
            store.savePlaybackJournal(context, before.copy(revision = revision, pending = before.pending + (id to entry)), id to value)
            positionWrites[DeviceProfile.of(context) to id] = revision to value
        }
        changed(); return true
    }
    fun discard(context: ApiContext, id: String? = null) {
        synchronized(lock) {
            current(context)
            val before = store.playbackJournal(context)
            val pending = if (id == null) emptyMap() else before.pending.mapValues { (key, entry) ->
                if (key == id) entry.copy(history = null) else entry
            }.filterValues { it.history != null || it.position != null }
            store.savePlaybackJournal(context, before.copy(pending = pending))
        }
        changed()
    }
    private fun acknowledge(context: ApiContext, id: String, sent: PendingPlayback, history: Boolean) {
        synchronized(lock) {
            current(context)
            val before = store.playbackJournal(context)
            val entry = before.pending[id] ?: return
            val next = if (history && entry.history == sent.history) entry.copy(history = null)
                else if (!history && entry.position == sent.position) entry.copy(position = null) else entry
            val pending = if (next.history == null && next.position == null) before.pending - id else before.pending + (id to next)
            store.savePlaybackJournal(context, before.copy(pending = pending))
        }
        changed()
    }
    suspend fun resolvePosition(context: ApiContext, id: String, fallback: Long, optional: Boolean = true): Long {
        current(context)
        pending(context)[id]?.takeIf { it.position != null }?.let { return it.seconds }
        if (context.account == null || settings(context)?.position != true) return fallback
        val key = DeviceProfile.of(context) to id
        val version = synchronized(lock) { positionWrites[key]?.first }
        val remote = api.position(id, context, optionalSync = optional)
        return synchronized(lock) {
            current(context)
            if (store.playbackJournal(context).settings?.position != true) return@synchronized fallback
            // A newer checkpoint can already have been acknowledged while this read was in flight.
            val latest = positionWrites[key]
            store.playbackJournal(context).pending[id]?.takeIf { it.position != null }?.seconds
                ?: latest?.takeIf { it.first != version }?.second ?: remote
        }
    }
    suspend fun refreshSettings(context: ApiContext) {
        if (context.account == null) return
        val revision = synchronized(lock) { settingsRevision }
        val preferences = api.preferences(context, optionalSync = true)
        configure(context, preferences, revision)
    }
    override suspend fun flush(): PlaybackSyncResult = writes.withLock {
        val context = api.context()
        if (context.account == null) return@withLock PlaybackSyncResult.COMPLETE
        if (synchronized(lock) { paused == context }) return@withLock PlaybackSyncResult.PAUSED
        try {
            if (synchronized(lock) { confirmed != context }) refreshSettings(context)
            val batch = pending(context)
            for ((id, entry) in batch) {
                current(context)
                if (entry.history != null && pending(context)[id]?.history == entry.history) {
                    api.watched(id, context, optionalSync = true)
                    acknowledge(context, id, entry, history = true)
                }
                if (entry.position != null && pending(context)[id]?.position == entry.position) {
                    api.position(id, entry.seconds, context, optionalSync = true)
                    acknowledge(context, id, entry, history = false)
                }
            }
            PlaybackSyncResult.COMPLETE
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val blocked = InstanceAccess.failure(e) != null || e is ApiException && e.status in listOf(400, 401, 403, 404, 405)
            if (blocked) synchronized(lock) { if (context == api.context()) paused = context }
            // A preference changed on another client. Re-read it before another upload.
            if (e is ApiException && e.status == 409) synchronized(lock) { confirmed = null }
            if (blocked) PlaybackSyncResult.PAUSED else PlaybackSyncResult.RETRY
        }
    }
}

/** Process lifetime only; new checkpoints cannot defeat an existing failure backoff. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSyncCoordinator(scope: CoroutineScope, private val repository: PlaybackSyncQueue,
    private val online: () -> Boolean = { true }) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val recoveries = Channel<Unit>(Channel.CONFLATED)
    fun request() { requests.trySend(Unit) }
    fun resume() { repository.reconnect(); recoveries.trySend(Unit) }
    init { scope.launch {
        while (isActive) {
            var refresh = select<Boolean> { requests.onReceive { false }; recoveries.onReceive { true } }
            var backoff = 5_000L
            while (online() && (refresh || repository.hasPending())) {
                refresh = false
                val result = try { repository.flush() }
                    catch (e: CancellationException) { currentCoroutineContext().ensureActive(); break }
                if (result == PlaybackSyncResult.PAUSED) break
                if (result == PlaybackSyncResult.COMPLETE) { backoff = 5_000; continue }
                refresh = true
                select<Unit> {
                    onTimeout(backoff) { backoff = (backoff * 2).coerceAtMost(300_000) }
                    recoveries.onReceive { backoff = 5_000 }
                }
            }
        }
    } }
}
