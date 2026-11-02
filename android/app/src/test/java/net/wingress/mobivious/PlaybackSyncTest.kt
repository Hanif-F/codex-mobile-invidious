package net.wingress.mobivious

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSyncTest {
    private val id = "abcdefghijk"
    private class Memory : PlaybackSyncStore, LocalPlaybackPositions {
        private val journals = mutableMapOf<DeviceProfile, String>()
        private val positions = mutableMapOf<DeviceProfile, Map<String, Long>>()
        override fun playbackJournal(context: ApiContext) = PlaybackJournal.parse(journals[DeviceProfile.of(context)])
        override fun savePlaybackJournal(context: ApiContext, journal: PlaybackJournal, position: Pair<String, Long>?) {
            journals[DeviceProfile.of(context)] = journal.json().toString()
            position?.let { setPosition(context, it.first, it.second) }
        }
        override fun positions(context: ApiContext) = positions[DeviceProfile.of(context)].orEmpty()
        override fun setPosition(context: ApiContext, id: String, seconds: Long) {
            positions[DeviceProfile.of(context)] = if (seconds == 0L) positions(context) - id else positions(context) + (id to seconds)
        }
        override fun clearPositions(context: ApiContext) { positions.remove(DeviceProfile.of(context)) }
    }
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        var address = server.url("/").toString().trimEnd('/')
        var account: Account? = Account("token", "Alice", Long.MAX_VALUE, address, "a".repeat(64))
        var expired = 0
        val api = InvidiousApi({ address }, { account }, { expired++ })
        val store = Memory()
        val sync = PlaybackSyncRepository(api, store)
        val context get() = api.context()
        init { sync.configure(context, AccountPreferences(watchHistory = true, savePosition = true)) }
        override fun close() = server.close()
    }
    private fun success() = MockResponse().setResponseCode(204)
    private fun preferences(history: Boolean = true, position: Boolean = true) = MockResponse()
        .setBody("""{"watch_history":$history,"save_player_pos":$position}""")

    @Test fun journalCoalescesPositionsPreservesCompletionAndSurvivesRecreation() = runBlocking {
        Fixture().use { f ->
            f.sync.enqueueHistory(f.context, id)
            f.sync.enqueuePosition(f.context, id, 40)
            f.sync.enqueuePosition(f.context, id, 0)
            val restored = PlaybackSyncRepository(f.api, f.store)
            assertEquals(0L, restored.resolvePosition(f.context, id, 99))
            assertTrue(restored.pending()[id]!!.history != null)
            f.server.enqueue(preferences()); f.server.enqueue(success()); f.server.enqueue(success())
            assertEquals(PlaybackSyncResult.COMPLETE, restored.flush())
            assertTrue(restored.pending().isEmpty())
            assertEquals("/api/v1/auth/preferences", f.server.takeRequest().path)
            assertEquals("POST", f.server.takeRequest().method)
            assertEquals("DELETE", f.server.takeRequest().method)
            assertTrue(f.store.positions(f.context).isEmpty())
        }
    }
    @Test fun oldAcknowledgementCannotEraseNewerSeek() = runBlocking {
        Fixture().use { f ->
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    arrived.countDown(); check(release.await(5, TimeUnit.SECONDS)); return success()
                }
            }
            f.sync.enqueuePosition(f.context, id, 20)
            val upload = async(Dispatchers.Default) { f.sync.flush() }
            try {
                assertTrue(arrived.await(5, TimeUnit.SECONDS))
                f.sync.enqueuePosition(f.context, id, 45)
            } finally { release.countDown() }
            upload.await()
            assertEquals(45L, f.sync.pending()[id]!!.seconds)
            assertEquals(PlaybackSyncResult.COMPLETE, f.sync.flush())
            assertTrue(f.sync.pending().isEmpty())
        }
    }
    @Test fun accountInstanceAndTokenChangesKeepOwnershipWithoutCredentialsInJournal() = runBlocking {
        Fixture().use { f ->
            val alice = f.context
            f.sync.enqueueHistory(alice, id); f.sync.enqueuePosition(alice, id, 20)
            f.account = f.account!!.copy(token = "replacement")
            assertEquals(20L, f.sync.pending()[id]!!.seconds)
            f.account = f.account!!.copy(username = "Bob", profileId = "b".repeat(64))
            assertFalse(f.sync.hasPending())
            f.sync.configure(f.context, AccountPreferences(savePosition = true))
            f.sync.enqueuePosition(f.context, id, 80)
            f.account = alice.account
            assertEquals(20L, f.sync.resolvePosition(f.context, id, 0))
            assertFalse(f.store.playbackJournal(alice).json().toString().contains("token"))
            f.address = "https://another.example"
            assertFalse(f.sync.hasPending())
            f.address = alice.server; f.account = null
            assertFalse(f.sync.enqueueHistory(f.context, id)); assertFalse(f.sync.enqueuePosition(f.context, id, 40))
            f.account = alice.account
            assertEquals(20L, f.sync.pending()[id]!!.seconds)
        }
    }
    @Test fun confirmedSettingsAndRemovalDiscardOnlyTheAppropriateFields() {
        Fixture().use { f ->
            f.sync.enqueueHistory(f.context, id); f.sync.enqueuePosition(f.context, id, 20)
            f.sync.discard(f.context, id)
            assertNull(f.sync.pending()[id]!!.history); assertEquals(20L, f.sync.pending()[id]!!.seconds)
            f.sync.enqueueHistory(f.context, id)
            f.sync.configure(f.context, AccountPreferences(watchHistory = false, savePosition = true))
            assertNull(f.sync.pending()[id]!!.history)
            assertFalse(f.sync.enqueueHistory(f.context, id))
            f.sync.configure(f.context, AccountPreferences(watchHistory = true, savePosition = false))
            assertTrue(f.sync.pending().isEmpty()); assertFalse(f.sync.enqueuePosition(f.context, id, 99))
            f.sync.enqueueHistory(f.context, id); f.sync.discard(f.context)
            assertTrue(f.sync.pending().isEmpty())
            val unknown = f.context.copy(account = f.account!!.copy(profileId = "c".repeat(64)))
            f.account = unknown.account
            assertFalse(f.sync.enqueueHistory(unknown, id)); assertFalse(f.sync.enqueuePosition(unknown, id, 10))
        }
    }
    @Test fun resumeUsesRemoteIncludingMissingPositionAndNeverOverwritesPendingProgress() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setBody("""{"position":60}"""))
            assertEquals(60L, f.sync.resolvePosition(f.context, id, 30))
            f.server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(0L, f.sync.resolvePosition(f.context, id, 30))
            f.sync.enqueuePosition(f.context, id, 40)
            assertEquals(40L, f.sync.resolvePosition(f.context, id, 30, optional = false))
            assertEquals(2, f.server.requestCount)
        }
    }
    @Test fun optionalAuthenticationFailuresPauseWithoutExpiringThePlaybackOwner() = runBlocking {
        Fixture().use { f ->
            f.sync.enqueueHistory(f.context, id)
            f.server.enqueue(MockResponse().setResponseCode(401))
            assertEquals(PlaybackSyncResult.PAUSED, f.sync.flush()); assertEquals(0, f.expired)
            assertEquals(PlaybackSyncResult.PAUSED, f.sync.flush()); assertEquals(1, f.server.requestCount)
            f.sync.reconnect(); f.server.enqueue(preferences()); f.server.enqueue(success())
            assertEquals(PlaybackSyncResult.COMPLETE, f.sync.flush()); assertTrue(f.sync.pending().isEmpty())
            f.server.enqueue(MockResponse().setResponseCode(401))
            try { f.api.watched(id) } catch (_: ApiException) { }
            assertEquals(1, f.expired)
        }
    }
    @Test fun serverPreferenceChangesAreRecheckedBeforeRestartedUploads() = runBlocking {
        Fixture().use { f ->
            f.sync.enqueueHistory(f.context, id); f.sync.enqueuePosition(f.context, id, 20)
            f.sync.reconnect(); f.server.enqueue(preferences(history = false, position = false))
            assertEquals(PlaybackSyncResult.COMPLETE, f.sync.flush())
            assertTrue(f.sync.pending().isEmpty()); assertEquals(1, f.server.requestCount)
        }
    }
    @Test fun streamedProgressSupersedesDownloadProgressAndHistoryClearCannotResurrectIt() = runBlocking {
        Fixture().use { f ->
            val watched = WatchedRepository(f.api, f.store, f.sync)
            watched.reset(); watched.configure(f.context, true)
            f.sync.changed = watched::reconcilePending
            f.sync.enqueuePosition(f.context, id, 20)
            watched.savePosition(f.context, id, 70)
            watched.recordWatched(f.context, id)
            assertEquals(70L, f.sync.resolvePosition(f.context, id, 0))
            assertTrue(id in watched.state.value.watched)
            f.server.enqueue(success()); watched.removeHistory(f.context, id)
            assertNull(f.sync.pending()[id]!!.history); assertNotNull(f.sync.pending()[id]!!.position)
            f.server.enqueue(success()); watched.clearHistory(f.context)
            assertTrue(f.sync.pending().isEmpty()); assertTrue(watched.state.value.positions.isEmpty())
        }
    }
    @Test fun reconnectRefreshesPreviouslyUnknownSettingsEvenWithoutPendingActivity() = runBlocking {
        Fixture().use { f ->
            f.account = f.account!!.copy(profileId = "d".repeat(64))
            assertNull(f.sync.settings()); assertFalse(f.sync.hasPending())
            f.sync.reconnect(); f.server.enqueue(preferences())
            assertEquals(PlaybackSyncResult.COMPLETE, f.sync.flush())
            assertEquals(PlaybackSyncSettings(true, true), f.sync.settings())
            assertTrue(f.sync.enqueueHistory(f.context, id))
        }
    }
    @Test fun delayedRemoteResumeLosesToANewerLocalSeek() = runBlocking {
        Fixture().use { f ->
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    arrived.countDown(); check(release.await(5, TimeUnit.SECONDS))
                    return MockResponse().setBody("""{"position":10}""")
                }
            }
            val resume = async(Dispatchers.Default) { f.sync.resolvePosition(f.context, id, 0) }
            try {
                assertTrue(arrived.await(5, TimeUnit.SECONDS)); f.sync.enqueuePosition(f.context, id, 65)
            } finally { release.countDown() }
            assertEquals(65L, resume.await())
        }
    }
    @Test fun delayedRemoteResumeCannotOverwriteANewerAcknowledgedCompletion() = runBlocking {
        Fixture().use { f ->
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method == "GET") {
                        arrived.countDown(); check(release.await(5, TimeUnit.SECONDS))
                        return MockResponse().setBody("""{"position":10}""")
                    }
                    return success()
                }
            }
            val resume = async(Dispatchers.Default) { f.sync.resolvePosition(f.context, id, 50) }
            try {
                assertTrue(arrived.await(5, TimeUnit.SECONDS))
                f.sync.enqueuePosition(f.context, id, 0); f.sync.flush()
                assertTrue(f.sync.pending().isEmpty())
            } finally { release.countDown() }
            assertEquals(0L, resume.await())
        }
    }
    @Test fun stalePreferenceReadCannotReenableSyncAfterANewerSave() = runBlocking {
        Fixture().use { f ->
            lateinit var sync: PlaybackSyncRepository
            val api = InvidiousApi({ f.address }, { f.account }, onPreferences = { context, prefs -> sync.configure(context, prefs) })
            sync = PlaybackSyncRepository(api, f.store)
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method == "GET") {
                        arrived.countDown(); check(release.await(5, TimeUnit.SECONDS)); return preferences()
                    }
                    return preferences(history = false, position = false)
                }
            }
            val read = async(Dispatchers.Default) { sync.refreshSettings(f.context) }
            try {
                assertTrue(arrived.await(5, TimeUnit.SECONDS))
                api.preferences(JSONObject().put("watch_history", false).put("save_player_pos", false), f.context)
            } finally { release.countDown() }
            read.await()
            assertEquals(PlaybackSyncSettings(false, false), sync.settings())
        }
    }
    @Test fun delayedSnapshotCannotEraseAnAlreadyAcknowledgedUpload() = runBlocking {
        Fixture().use { f ->
            val watched = WatchedRepository(f.api, f.store, f.sync)
            watched.reset(); watched.configure(f.context, true); f.sync.changed = watched::reconcilePending
            watched.recordWatched(f.context, id); watched.savePosition(f.context, id, 50)
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/api/v1/auth/playback") {
                        arrived.countDown(); check(release.await(5, TimeUnit.SECONDS))
                        return MockResponse().setBody("""{"watched":[],"positions":{}}""")
                    }
                    return success()
                }
            }
            val refresh = async(Dispatchers.Default) { watched.refresh() }
            try {
                assertTrue(arrived.await(5, TimeUnit.SECONDS)); f.sync.flush()
            } finally { release.countDown() }
            refresh.await()
            assertTrue(id in watched.state.value.watched); assertEquals(50L, watched.state.value.positions[id])
        }
    }
    @Test fun pendingHistorySurvivesRepositoryRestartAndDisabledUnsentHistoryDisappears() = runBlocking {
        Fixture().use { f ->
            f.sync.enqueueHistory(f.context, id)
            val watched = WatchedRepository(f.api, f.store, f.sync)
            watched.reset(); watched.configure(f.context, true); f.sync.changed = watched::reconcilePending
            assertTrue(id in watched.state.value.watched)
            f.server.enqueue(success()); f.sync.flush()
            assertTrue(id in watched.state.value.watched)
            val second = "bbbbbbbbbbb"
            watched.recordWatched(f.context, second)
            assertTrue(second in watched.state.value.watched)
            f.sync.configure(f.context, AccountPreferences(watchHistory = false, savePosition = true))
            assertTrue(id in watched.state.value.watched)
            assertFalse(second in watched.state.value.watched)
        }
    }
    @Test fun reconnectRetriesSettingsFailuresWithoutPendingActivity() = runTest {
        var attempts = 0
        var result = PlaybackSyncResult.RETRY
        val queue = object : PlaybackSyncQueue {
            override fun hasPending() = false
            override fun reconnect() = Unit
            override suspend fun flush(): PlaybackSyncResult { attempts++; return result }
        }
        val coordinator = PlaybackSyncCoordinator(backgroundScope, queue)
        coordinator.resume(); runCurrent(); assertEquals(1, attempts)
        advanceTimeBy(5_000); runCurrent(); assertEquals(2, attempts)
        result = PlaybackSyncResult.COMPLETE
        advanceTimeBy(10_000); runCurrent(); assertEquals(3, attempts)
        advanceTimeBy(600_000); runCurrent(); assertEquals(3, attempts)
    }
    @Test fun coordinatorBackoffSurvivesCheckpointsAndReconnectWakesIt() = runTest {
        var attempts = 0
        var pending = true
        var online = true
        var result = PlaybackSyncResult.RETRY
        val queue = object : PlaybackSyncQueue {
            override fun hasPending() = pending
            override fun reconnect() = Unit
            override suspend fun flush(): PlaybackSyncResult { attempts++; return result }
        }
        val coordinator = PlaybackSyncCoordinator(backgroundScope, queue) { online }
        coordinator.request(); runCurrent(); assertEquals(1, attempts)
        coordinator.request(); advanceTimeBy(4_999); runCurrent(); assertEquals(1, attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(2, attempts)
        advanceTimeBy(9_999); runCurrent(); assertEquals(2, attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(3, attempts)
        coordinator.resume(); runCurrent(); assertEquals(4, attempts)
        online = false; advanceTimeBy(5_000); runCurrent(); assertEquals(4, attempts)
        online = true; result = PlaybackSyncResult.PAUSED
        coordinator.resume(); runCurrent(); assertEquals(5, attempts)
        advanceTimeBy(600_000); runCurrent(); assertEquals(5, attempts)
        pending = false; coordinator.resume(); runCurrent(); assertEquals(6, attempts)
    }
}
