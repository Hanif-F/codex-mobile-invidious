package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WatchedStateTest {
    private val first = "abcdefghijk"
    private val second = "bbbbbbbbbbb"
    private class MemoryPositions : LocalPlaybackPositions {
        var failClear = false
        private val values = mutableMapOf<Pair<String, String?>, Map<String, Long>>()
        private fun key(context: ApiContext) = context.server to context.account?.username
        override fun positions(context: ApiContext) = values[key(context)].orEmpty()
        override fun setPosition(context: ApiContext, id: String, seconds: Long) {
            values[key(context)] = if (seconds == 0L) positions(context) - id else positions(context) + (id to seconds)
        }
        override fun clearPositions(context: ApiContext) {
            if (failClear) throw java.io.IOException("Device save failed")
            values.remove(key(context))
        }
    }
    private class Fixture(guest: Boolean = false) : AutoCloseable {
        val server = MockWebServer().apply { start() }
        var address = server.url("/").toString().trimEnd('/')
        var account: Account? = if (guest) null else Account("token", "Alice", Long.MAX_VALUE, address)
        val api = InvidiousApi({ address }, { account })
        val local = MemoryPositions()
        val repo = WatchedRepository(api, local).apply { reset(); configure(api.context(), true) }
        val context get() = api.context()
        override fun close() = server.close()
    }
    private fun snapshot(watched: List<String> = emptyList(), positions: Map<String, Long> = emptyMap()) = MockResponse()
        .setBody(JSONObject().put("watched", org.json.JSONArray(watched)).put("positions", JSONObject(positions)).toString())
    private fun await(latch: CountDownLatch) { assertTrue("Fixture request did not arrive", latch.await(5, TimeUnit.SECONDS)) }

    @Test fun historyRevisionAdvancesForRepeatWatchesButNotPlaybackPositions() = runBlocking {
        Fixture().use { f ->
            repeat(2) { index ->
                f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.recordWatched(f.context, first)
                assertEquals(index + 1L, f.repo.state.value.historyRevision)
            }
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.savePosition(f.context, first, 10)
            assertEquals(2L, f.repo.state.value.historyRevision)
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.removeHistory(f.context, first)
            assertEquals(3L, f.repo.state.value.historyRevision)
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.clearHistory(f.context)
            assertEquals(4L, f.repo.state.value.historyRevision)
            f.account = null; f.repo.reset()
            assertEquals(0L, f.repo.state.value.historyRevision)
        }
    }

    @Test fun snapshotParsesIndependentStateAndRejectsMalformedEntries() {
        val parsed = PlaybackSnapshot.parse(JSONObject("""{"watched":["$first","$first",null,4,"bad"],"positions":{"$first":0,"$second":42,"ccccccccccc":-1,"ddddddddddd":1.5,"eeeeeeeeeee":"20","fffffffffff":2147483648,"bad":10}}"""))
        assertEquals(setOf(first), parsed.watched)
        assertEquals(mapOf(first to 0L, second to 42L), parsed.positions)
        assertThrows(org.json.JSONException::class.java) { PlaybackSnapshot.parse(JSONObject("{}")) }
    }
    @Test fun barsMatchWebRoundingMinimumAndCompletionThresholds() {
        val video = Video(first, "Video", duration = 1000)
        listOf(0L to .05f, 10L to .05f, 50L to .05f, 900L to .9f, 904L to .9f, 905L to 1f, 1200L to 1f).forEach { (position, expected) ->
            val indicator = WatchedIndicators.forVideo(video, WatchedState(positions = mapOf(first to position)))
            assertEquals(expected, indicator.bar!!, .0001f)
            assertFalse(indicator.watched)
        }
    }
    @Test fun historyMembershipAndProgressHaveDistinctAccessibleDescriptions() {
        val indicator = WatchedIndicators.forVideo(Video(first, "Video", duration = 100), WatchedState(watched = setOf(first), positions = mapOf(first to 40)))
        assertTrue(indicator.watched); assertEquals(40, indicator.percent); assertEquals(.4f, indicator.bar!!, .0001f)
        assertEquals("In watch history. Playback progress 40 percent", indicator.description)
        val complete = WatchedIndicators.forVideo(Video(first, "Video"), WatchedState(watched = setOf(first)))
        assertEquals(1f, complete.bar!!, .0001f); assertNull(complete.percent)
        assertEquals("In watch history", complete.description)
    }
    @Test fun liveAndUnknownDurationsNeverInventPartialProgress() {
        listOf(Video(first, "Unknown"), Video(first, "Invalid", duration = -1), Video(first, "Live", duration = 120, live = true)).forEach { video ->
            val indicator = WatchedIndicators.forVideo(video, WatchedState(watched = setOf(first), positions = mapOf(first to 30)))
            assertTrue(indicator.watched); assertNull(indicator.percent); assertNull(indicator.bar)
        }
        assertNull(WatchedIndicators.forVideo(Video(first, "Unseen", duration = 100), WatchedState()).bar)
    }
    @Test fun bulkApiUsesExistingBearerContractWithoutCookies() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(snapshot(listOf(first), mapOf(second to 30)))
            assertEquals(PlaybackSnapshot(setOf(first), mapOf(second to 30L)), f.api.playback())
            val request = f.server.takeRequest()
            assertEquals("/api/v1/auth/playback", request.path); assertEquals("GET", request.method)
            assertEquals("Bearer token", request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
        }
    }
    @Test fun staleApiContextsCannotSendHistoryOrPositionRequests() = runBlocking {
        Fixture().use { f ->
            val old = f.context
            f.account = f.account!!.copy(username = "Bob", token = "new-token")
            listOf<suspend () -> Unit>({ f.api.playback(old) }, { f.api.watched(first, old) },
                { f.api.position(first, 20, old) }, { f.api.removeHistory(first, old) }, { f.api.clearHistory(old) }).forEach { call ->
                try { call(); fail("Stale context accepted") } catch (_: CancellationException) { }
            }
            assertEquals(0, f.server.requestCount)
        }
    }
    @Test fun refreshCoalescesWhileKeepingExistingIndicatorsAndRetainsThemAfterFailure() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(snapshot(listOf(first), mapOf(first to 30))); f.repo.refresh()
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { arrived.countDown(); await(release); return MockResponse().setResponseCode(503).setBody("{}") }
            }
            val refresh = async(Dispatchers.Default) { f.repo.refresh() }
            try {
                await(arrived); assertTrue(f.repo.state.value.loading)
                f.repo.refresh(); assertEquals(2, f.server.requestCount)
                assertEquals(setOf(first), f.repo.state.value.watched)
            } finally { release.countDown() }
            refresh.await()
            assertFalse(f.repo.state.value.loading); assertNotNull(f.repo.state.value.error)
            assertEquals(mapOf(first to 30L), f.repo.state.value.positions)
        }
    }
    @Test fun failedInitialReadDoesNotPreventAutomaticUpdatesOrSuccessfulRetry() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setResponseCode(404)); f.repo.refresh()
            assertNotNull(f.repo.state.value.error); assertTrue(f.repo.state.value.watched.isEmpty())
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.recordWatched(f.context, first)
            assertEquals(setOf(first), f.repo.state.value.watched)
            f.server.enqueue(snapshot(listOf(first), mapOf(first to 25))); f.repo.refresh()
            assertNull(f.repo.state.value.error); assertEquals(25L, f.repo.state.value.positions[first])
        }
    }
    @Test fun delayedReadCannotUndoNewHistoryAndPositionWrites() = runBlocking {
        Fixture().use { f ->
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.path == "/api/v1/auth/playback") {
                    arrived.countDown(); await(release); snapshot(listOf(second), mapOf(second to 20))
                } else MockResponse().setResponseCode(204)
            }
            val refresh = async(Dispatchers.Default) { f.repo.refresh() }
            try {
                await(arrived); f.repo.recordWatched(f.context, first); f.repo.savePosition(f.context, first, 45)
            } finally { release.countDown() }
            refresh.await()
            assertEquals(setOf(first, second), f.repo.state.value.watched)
            assertEquals(mapOf(first to 45L, second to 20L), f.repo.state.value.positions)
        }
    }
    @Test fun changedAccountsClearImmediatelyAndCanRefreshBeforeOldResponseFinishes() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(snapshot(listOf(first), mapOf(first to 30))); f.repo.refresh()
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.getHeader("Authorization") == "Bearer token") {
                    arrived.countDown(); await(release); snapshot(listOf(first), mapOf(first to 60))
                } else snapshot(listOf(second), mapOf(second to 20))
            }
            val old = async(Dispatchers.Default) { f.repo.refresh() }
            try {
                await(arrived)
                f.account = f.account!!.copy(username = "Bob", token = "bob-token")
                f.repo.reset(); assertTrue(f.repo.state.value.watched.isEmpty()); assertFalse(f.repo.state.value.loading)
                f.repo.configure(f.context, true); f.repo.refresh()
            } finally { release.countDown() }
            try { old.await(); fail("Former account read was accepted") } catch (_: CancellationException) { }
            assertEquals(setOf(second), f.repo.state.value.watched); assertEquals(mapOf(second to 20L), f.repo.state.value.positions)
        }
    }
    @Test fun historyRemovalAndClearingWinAgainstOlderBulkSnapshots() = runBlocking {
        listOf(false, true).forEach { clear -> Fixture().use { f ->
            f.server.enqueue(snapshot(listOf(first, second), mapOf(first to 40))); f.repo.refresh()
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.method == "GET") {
                    arrived.countDown(); await(release); snapshot(listOf(first, second), mapOf(first to 40))
                } else MockResponse().setResponseCode(204)
            }
            val refresh = async(Dispatchers.Default) { f.repo.refresh() }
            try {
                await(arrived)
                if (clear) f.repo.clearHistory(f.context) else f.repo.removeHistory(f.context, first)
            } finally { release.countDown() }
            refresh.await()
            assertEquals(if (clear) emptySet<String>() else setOf(second), f.repo.state.value.watched)
            assertEquals(if (clear) emptyMap<String, Long>() else mapOf(first to 40L), f.repo.state.value.positions)
        } }
    }
    @Test fun readDuringPendingPositionWriteKeepsTheLatestDeviceProgress() = runBlocking {
        Fixture().use { f ->
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.method == "PUT") {
                    arrived.countDown(); await(release); MockResponse().setResponseCode(204)
                } else snapshot(listOf(second), mapOf(first to 10))
            }
            val save = async(Dispatchers.Default) { f.repo.savePosition(f.context, first, 45) }
            try {
                await(arrived); f.repo.refresh()
                assertEquals(setOf(second), f.repo.state.value.watched)
                assertEquals(45L, f.repo.state.value.positions[first])
            } finally { release.countDown() }
            save.await(); assertEquals(45L, f.repo.state.value.positions[first])
        }
    }
    @Test fun guestsUseOnlyTheirScopedDevicePositionsAndNeverCallAccountApis() = runBlocking {
        Fixture(guest = true).use { f ->
            f.local.setPosition(f.context, first, 30); f.repo.configure(f.context, true); f.repo.refresh()
            assertEquals(mapOf(first to 30L), f.repo.state.value.positions); assertTrue(f.repo.state.value.watched.isEmpty())
            f.repo.savePosition(f.context, second, 45)
            assertEquals(45L, f.local.positions(f.context)[second])
            val guest = f.context
            f.account = Account("token", "Alice", Long.MAX_VALUE, f.address)
            f.local.setPosition(f.context, first, 80); f.repo.reset(); f.repo.configure(f.context, true)
            assertTrue(f.repo.state.value.positions.isEmpty())
            f.account = null; f.repo.reset(); f.repo.configure(f.context, true)
            assertEquals(30L, f.repo.state.value.positions[first])
            f.address = "https://other.test"; f.repo.reset(); f.repo.configure(f.context, true)
            assertTrue(f.repo.state.value.positions.isEmpty()); assertEquals(30L, f.local.positions(guest)[first])
            assertEquals(0, f.server.requestCount)
        }
    }
    @Test fun guestCompletionRemovesProgressWithoutCreatingWatchedHistory() = runBlocking {
        Fixture(guest = true).use { f ->
            f.repo.savePosition(f.context, first, 45); f.repo.savePosition(f.context, first, 0)
            assertTrue(f.repo.state.value.positions.isEmpty()); assertTrue(f.local.positions(f.context).isEmpty())
            assertTrue(f.repo.state.value.watched.isEmpty()); assertEquals(0, f.server.requestCount)
        }
    }
    @Test fun accountCompletionRetainsWatchedBadgeAndUsesPositionDelete() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.recordWatched(f.context, first)
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.savePosition(f.context, first, 45)
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.savePosition(f.context, first, 0)
            assertEquals(setOf(first), f.repo.state.value.watched); assertTrue(f.repo.state.value.positions.isEmpty())
            f.server.takeRequest(); f.server.takeRequest()
            val request = f.server.takeRequest(); assertEquals("DELETE", request.method); assertEquals("/api/v1/auth/playback/$first", request.path)
        }
    }
    @Test fun historyRemovalKeepsPlaybackPositionAndOnlyDeletesHistory() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(snapshot(listOf(first, second), mapOf(first to 40))); f.repo.refresh()
            f.local.setPosition(f.context, first, 40)
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.removeHistory(f.context, first)
            assertEquals(setOf(second), f.repo.state.value.watched)
            assertEquals(40L, f.repo.state.value.positions[first]); assertEquals(40L, f.local.positions(f.context)[first])
            f.server.takeRequest(); val request = f.server.takeRequest()
            assertEquals("DELETE", request.method); assertEquals("/api/v1/auth/history/$first", request.path)
            assertEquals(2, f.server.requestCount)
        }
    }
    @Test fun clearHistoryRemovesBothKindsOfStateAndDropsPreviouslyQueuedSaves() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(snapshot(listOf(first), mapOf(first to 40))); f.repo.refresh()
            f.local.setPosition(f.context, first, 40)
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { arrived.countDown(); await(release); return MockResponse().setResponseCode(204) }
            }
            val clear = async(Dispatchers.Default) { f.repo.clearHistory(f.context) }
            val queued: Deferred<Unit>
            try {
                await(arrived)
                queued = async(start = CoroutineStart.UNDISPATCHED) { f.repo.savePosition(f.context, first, 50) }
            } finally { release.countDown() }
            clear.await(); queued.await()
            assertTrue(f.repo.state.value.watched.isEmpty()); assertTrue(f.repo.state.value.positions.isEmpty())
            assertTrue(f.local.positions(f.context).isEmpty()); assertEquals(2, f.server.requestCount)
        }
    }
    @Test fun disabledResumeHidesProgressButKeepsRecordedHistory() = runBlocking {
        Fixture().use { f ->
            f.local.setPosition(f.context, first, 40)
            f.repo.configure(f.context, false)
            f.server.enqueue(snapshot(listOf(first), mapOf(first to 40))); f.repo.refresh()
            assertEquals(setOf(first), f.repo.state.value.watched); assertTrue(f.repo.state.value.positions.isEmpty())
            assertTrue(f.local.positions(f.context).isEmpty())
            f.repo.savePosition(f.context, first, 60); assertEquals(1, f.server.requestCount)
        }
    }
    @Test fun disablingResumeDuringWriteCannotRestoreDeviceProgress() = runBlocking {
        Fixture().use { f ->
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { arrived.countDown(); await(release); return MockResponse().setResponseCode(204) }
            }
            val save = async(Dispatchers.Default) { f.repo.savePosition(f.context, first, 45) }
            try { await(arrived); f.repo.configure(f.context, false) } finally { release.countDown() }
            save.await(); assertTrue(f.repo.state.value.positions.isEmpty()); assertTrue(f.local.positions(f.context).isEmpty())
        }
    }
    @Test fun failedHistoryMutationsLeaveIndicatorsIntactAndFailedPositionKeepsLocalFallback() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(snapshot(listOf(first), mapOf(first to 40))); f.repo.refresh()
            listOf<suspend () -> Unit>({ f.repo.removeHistory(f.context, first) }, { f.repo.clearHistory(f.context) },
                { f.repo.recordWatched(f.context, second) }).forEach { call ->
                f.server.enqueue(MockResponse().setResponseCode(503))
                try { call(); fail("Failure swallowed") } catch (_: ApiException) { }
                assertEquals(setOf(first), f.repo.state.value.watched); assertEquals(40L, f.repo.state.value.positions[first])
            }
            f.server.enqueue(MockResponse().setResponseCode(503))
            try { f.repo.savePosition(f.context, first, 55); fail("Failure swallowed") } catch (_: ApiException) { }
            assertEquals(55L, f.local.positions(f.context)[first]); assertEquals(55L, f.repo.state.value.positions[first])
        }
    }
    @Test fun failedDeviceClearPreservesProgressAndCanRetryWithoutStoppingObservers() {
        Fixture(guest = true).use { f ->
            f.local.setPosition(f.context, first, 30); f.repo.configure(f.context, true)
            f.local.failClear = true
            f.repo.configure(f.context, false)
            assertEquals("Device save failed", f.repo.state.value.error)
            assertEquals(30L, f.repo.state.value.positions[first]); assertEquals(30L, f.local.positions(f.context)[first])
            f.local.failClear = false; f.repo.configure(f.context, false)
            assertNull(f.repo.state.value.error); assertTrue(f.repo.state.value.positions.isEmpty()); assertTrue(f.local.positions(f.context).isEmpty())
            assertEquals(0, f.server.requestCount)
        }
    }
}
