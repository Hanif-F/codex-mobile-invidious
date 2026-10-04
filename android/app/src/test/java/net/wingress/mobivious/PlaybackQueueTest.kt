package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers

class PlaybackQueueTest {
    private fun entry(index: Int) = QueueOccurrence.source("IVlist", Video("testvideo01", "Video $index", indexId = "${index + 10}", playlistIndex = index), index)
    @Test fun duplicatesAndOverlappingWindowsKeepLocalEdits() {
        val first = entry(0); val duplicate = entry(1)
        var state = PlaybackQueueSnapshot(items = listOf(first, duplicate), currentKey = first.key)
        state = QueueRules.insert(state, Video("testvideo02", "Next"), true)
        state = QueueRules.insert(state, Video("testvideo03", "Tail"), false)
        val merged = QueueRules.merge(state.items, listOf(duplicate, entry(2)))
        assertEquals(listOf("testvideo01", "testvideo02", "testvideo01", "testvideo01", "testvideo03"), merged.map { it.video.id })
        assertEquals(5, merged.map { it.key }.distinct().size)
        val wire = state.copy(items = merged).json().getJSONArray("items")
        assertEquals(5, wire.length())
        assertNotEquals(wire.getJSONObject(0).getString("key"), wire.getJSONObject(2).getString("key"))
    }
    @Test fun removingCurrentRetainsItUntilCompletionAndFindsSuccessor() {
        val current = entry(0); val next = entry(1)
        val removed = QueueRules.remove(PlaybackQueueSnapshot(items = listOf(current, next), currentKey = current.key), current.key)
        assertTrue(removed.current!!.removed)
        assertEquals(next.key, QueueRules.successor(removed, 1, false)?.key)
        assertTrue(QueueRules.merge(removed.items, listOf(current, next)).first().removed)
    }
    @Test fun navigationSkipsUnavailableHiddenAndRemovedOccurrences() {
        val entries = listOf(entry(0), entry(1).copy(video = entry(1).video.copy(unavailable = true)),
            entry(2).copy(video = entry(2).video.copy(membersOnly = true)), entry(3).copy(removed = true), entry(4))
        val state = PlaybackQueueSnapshot(items = entries, currentKey = entries.first().key)
        assertEquals(entries.last().key, QueueRules.successor(state, 1, false)?.key)
        assertEquals(entries[2].key, QueueRules.successor(state, 1, true)?.key)
        assertNull(QueueRules.successor(state, -1, false))
    }
    @Test fun previousAndFiniteEndDoNotWrapImplicitly() {
        val entries = listOf(entry(0), entry(1), entry(2))
        val state = PlaybackQueueSnapshot(items = entries, currentKey = entries.last().key)
        assertEquals(entries[1].key, QueueRules.successor(state, -1, false)?.key)
        assertNull(QueueRules.successor(state, 1, false))
    }
    @Test fun mixPagesFollowQueuedTailAndFinitePagesPrecedeIt() {
        val state = QueueRules.insert(PlaybackQueueSnapshot(items = listOf(entry(0)), currentKey = entry(0).key), Video("testvideo02", "Tail"), false)
        assertEquals("Tail", QueueRules.merge(state.items, listOf(entry(1))).last().video.title)
        assertEquals("Video 1", QueueRules.merge(state.items, listOf(entry(1)), mix = true).last().video.title)
    }
    @Test fun mixContinuationAddsOccurrencesAndRejectsEmptyOrRepeatedWindows() {
        val source = QueueSource("RDtestvideo01")
        val current = QueueOccurrence.source(source.id, Video("testvideo01", "Anchor"), 0)
        val state = QueueRules.insert(PlaybackQueueSnapshot(source = source, items = listOf(current), currentKey = current.key), Video("testvideo03", "Local tail"), false)
        val page = QueuePage(source, listOf(current.video, Video("testvideo02", "Successor", playlistIndex = 1)))
        assertEquals(current.key, QueueRules.mergePage(PlaybackQueueSnapshot(source = source), page, 0, null).first().key)
        val merged = QueueRules.mergePage(state, page, 1, "testvideo01")
        assertEquals(listOf("testvideo01", "testvideo03", "testvideo02"), merged.map { it.video.id })
        assertEquals(1, merged.last().sourceIndex)
        assertThrows(IllegalStateException::class.java) { QueueRules.mergePage(state.copy(items = merged), page, 2, "testvideo01") }
        assertThrows(IllegalStateException::class.java) { QueueRules.mergePage(state, QueuePage(source, listOf(current.video)), 1, "testvideo01") }
    }
    @Test fun resolvingInitialSourceKeepsPlayNextAfterTheSelectedOccurrence() {
        val seed = QueueOccurrence.local(entry(3).video).copy(sourceIndex = 3)
        val pending = QueueRules.insert(PlaybackQueueSnapshot(items = listOf(seed), currentKey = seed.key), Video("testvideo02", "Play next"), true)
        val merged = QueueRules.merge(pending.items, (0..4).map { entry(it) })
        val resolved = QueueRules.resolveSeed(merged, seed.key, entry(1).key)
        assertEquals(listOf("Video 0", "Video 1", "Play next", "Video 2", "Video 3", "Video 4"), resolved.map { it.video.title })
    }
    @Test fun previousLoadsMissingWindowsAndRecommendationsReevaluateBlocking() {
        val state = PlaybackQueueSnapshot(source = QueueSource("IVlist", count = 300), items = listOf(entry(100), entry(250)), currentKey = entry(250).key)
        assertEquals(249, QueueRules.missingPreviousIndex(state, entry(100)))
        val recommendation = entry(1).copy(video = entry(1).video.copy(channelId = "UCblocked"))
        val recommendations = PlaybackQueueSnapshot(items = listOf(entry(0), recommendation, entry(2)), currentKey = entry(0).key)
        assertEquals(entry(2).key, QueueRules.successor(recommendations, 1, true, setOf("UCblocked"))?.key)
    }
    @Test fun allWebAutoplayCombinations() {
        listOf(false, true).forEach { opened -> listOf(false, true).forEach { next ->
            assertEquals(opened || next, QueueRules.automaticStart(AccountPreferences(autoplay = opened, continueAutoplay = next)))
        } }
    }
    @Test fun repeatWrapDoesNotJumpAcrossUnloadedMiddlePages() {
        val firstWindow = (0..99).map { entry(it) }; val laterWindow = (450..549).map { entry(it) }
        val state = PlaybackQueueSnapshot(source = QueueSource("IVlist", count = 550), items = firstWindow + laterWindow,
            currentKey = firstWindow.last().key, sourceComplete = true)
        assertEquals(100, QueueRules.missingIndex(state, QueueRules.successor(state, 1, false)))
        assertNull(QueueRules.missingIndex(state.copy(currentKey = firstWindow.first().key), firstWindow[1]))
    }
    @Test fun linksKeepContextNormalizeIndexesAndRejectOtherHosts() {
        val server = "https://instance.test"
        assertEquals(VideoLink("testvideo01", 20, "PLqueue", 2), VideoLinks.parse("https://youtube.com/watch?v=testvideo01&list=PLqueue&index=3&t=20", server))
        assertEquals(VideoLink("testvideo01", 0, "IVqueue", 3), VideoLinks.parse("$server/watch?v=testvideo01&list=IVqueue&index=3&t=0", server))
        assertEquals(VideoLink("", playlistId = "PLqueue"), VideoLinks.parse("https://youtube.com/playlist?list=PLqueue", server))
        assertEquals(VideoLink("", playlistId = "RDtestvideo01"), VideoLinks.parse("$server/mix?list=RDtestvideo01", server))
        assertNull(VideoLinks.parse("https://evil.test/playlist?list=PLqueue", server))
        assertNull(VideoLinks.parse("$server/playlist?list=../../evil", server))
    }
    @Test fun nextAndLoopDefaultsAndSparseSharedDeltas() {
        val before = AccountPreferences()
        assertFalse(before.continueNext); assertTrue(before.continueAutoplay); assertFalse(before.videoLoop)
        val after = before.copy(continueNext = true, continueAutoplay = false, videoLoop = true)
        val delta = after.changesFrom(before)
        assertEquals(setOf("continue", "continue_autoplay", "video_loop"), delta.keys().asSequence().toSet())
        assertEquals(after, before.merge(delta))
    }
    @Test fun publicPrivateAndMixEndpointsAndCredentials() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            var account: Account? = null
            val api = InvidiousApi({ address }, { account })
            server.enqueue(MockResponse().setBody("""{"title":"Public","videoCount":300,"videos":[{"videoId":"testvideo01","title":"Repeated","index":150}]}"""))
            assertEquals(150, api.queuePage("PLlist", 150).videos.single().playlistIndex)
            val public = server.takeRequest(); assertEquals("/api/v1/playlists/PLlist?index=150", public.path); assertNull(public.getHeader("Authorization"))
            account = Account("token", "Alice", Long.MAX_VALUE, address)
            server.enqueue(MockResponse().setBody("""{"videos":[]}""")); api.queuePage("IVprivate")
            val private = server.takeRequest(); assertTrue(private.path!!.startsWith("/api/v1/auth/playlists/IVprivate")); assertEquals("Bearer token", private.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"videos":[]}""")); api.queuePage("RDtestvideo01", continuation = "testvideo02")
            val mix = server.takeRequest(); assertEquals("/api/v1/auth/playlists/RDtestvideo01?continuation=testvideo02", mix.path); assertEquals("Bearer token", mix.getHeader("Authorization"))
        }
    }
    @Test fun creationAndFailedSaveRetryUseSamePlaylistAndStableRemovalId() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) })
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"playlistId":"IVnew","title":"New"}"""))
            val created = api.createPlaylist("New", "private")
            server.enqueue(MockResponse().setResponseCode(502).setBody("{}"))
            assertThrows(ApiException::class.java) { runBlocking { api.addToPlaylist(created.id, "testvideo01") } }
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"videoId":"testvideo01","title":"Saved","indexId":"ABC","index":0}"""))
            assertEquals("ABC", api.addToPlaylist(created.id, "testvideo01").indexId)
            assertEquals("/api/v1/auth/playlists", server.takeRequest().path)
            repeat(2) { assertEquals("/api/v1/auth/playlists/IVnew/videos", server.takeRequest().path) }
            server.enqueue(MockResponse().setResponseCode(204)); api.removeFromPlaylist(created.id, "ABC")
            assertEquals("/api/v1/auth/playlists/IVnew/videos/ABC", server.takeRequest().path)
        }
    }
    @Test fun staleReadsAndWritesDoNotReachNetwork() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            var account: Account? = Account("token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account }); val context = api.context(); account = null
            assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking { api.queuePage("IVprivate", context = context) } }
            assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking { api.addToPlaylist("IVprivate", "testvideo01", context) } }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun delayedResponseIsRejectedAfterReturningToSameAccountAndInstance() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val generation = AtomicLong(1)
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) }, generation = { generation.get() })
            server.enqueue(MockResponse().setBody("""{"videos":[]}""").setBodyDelay(250, TimeUnit.MILLISECONDS))
            val captured = api.context()
            val pending = async(Dispatchers.IO) { runCatching { api.queuePage("PLlist", context = captured) } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            generation.addAndGet(2) // Leave and return; credentials and URL are identical.
            assertTrue(pending.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
            assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking { api.addToPlaylist("IVprivate", "testvideo01", captured) } }
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"playlistId":"IVnew","title":"New"}""").setBodyDelay(250, TimeUnit.MILLISECONDS))
            val writingContext = api.context()
            val writing = async(Dispatchers.IO) { runCatching { api.createPlaylist("New", "private", writingContext) } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            generation.incrementAndGet()
            assertTrue(writing.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        }
    }
}
