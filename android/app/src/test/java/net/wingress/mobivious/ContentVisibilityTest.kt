package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContentVisibilityTest {
    private val first = "UC" + "a".repeat(22)
    private val second = "UC" + "b".repeat(22)
    private val public = Video("abcdefghijk", "Public", channelId = first)
    private val member = Video("bbbbbbbbbbb", "Member", channelId = second, membersOnly = true)
    private class Memory : VisibilityStore {
        val blocks = mutableMapOf<Pair<String, String?>, List<BlockedChannel>>()
        val searches = mutableMapOf<Pair<String, String?>, SearchVisibility>()
        private fun key(c: ApiContext) = c.server to c.account?.username
        override fun searchVisibility(context: ApiContext) = searches[key(context)] ?: SearchVisibility()
        override fun saveSearchVisibility(context: ApiContext, value: SearchVisibility) { searches[key(context)] = value }
        override fun blockedSnapshot(context: ApiContext) = blocks[key(context)]
        override fun saveBlockedSnapshot(context: ApiContext, value: List<BlockedChannel>) { blocks[key(context)] = value }
    }
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        var address = server.url("/").toString().trimEnd('/')
        var account: Account? = Account("token", "Alice", Long.MAX_VALUE, address)
        val api = InvidiousApi({ address }, { account })
        val local = Memory()
        val repo = BlockedRepository(api, local).apply { reset() }
        val context get() = api.context()
        override fun close() = server.close()
    }
    private fun response(vararg channels: BlockedChannel) = MockResponse().setBody(JSONArray(channels.map { it.json() }).toString())
    private fun await(latch: CountDownLatch) { assertTrue(latch.await(5, TimeUnit.SECONDS)) }

    @Test fun membershipUsesBooleanMetadataAndNeverInfersFromPremiumOrTitle() {
        listOf("{}", "{\"isMember\":false}", "{\"isMember\":\"true\"}", "{\"premium\":true,\"title\":\"Members only\"}").forEach {
            assertFalse(ApiParser.video(JSONObject(it)).membersOnly)
        }
        assertTrue(ApiParser.video(JSONObject("{\"isMember\":true}")).membersOnly)
        assertTrue(ApiParser.details(JSONObject("{\"recommendedVideos\":[{\"videoId\":\"abcdefghijk\",\"isMember\":true}]}")).recommendations.single().membersOnly)
    }
    @Test fun memberPreferenceDefaultsOffRoundTripsAndPatchesOnlyChangedFields() {
        val before = AccountPreferences()
        assertFalse(before.showMemberVideos)
        val next = before.copy(showMemberVideos = true)
        assertEquals(next, AccountPreferences.parse(next.json()))
        assertEquals(setOf("show_member_videos"), next.changesFrom(before).keys().asSequence().toSet())
        assertTrue(before.copy(region = "ID").merge(next.changesFrom(before)).showMemberVideos)
    }
    @Test fun blocksFilterOnlyDiscoverySearchAndRecommendations() {
        ContentSurface.entries.forEach { surface ->
            val filtered = ContentVisibility.filter(listOf(public, member), surface, true, blocked = setOf(first))
            assertEquals(if (surface in listOf(ContentSurface.DISCOVERY, ContentSurface.SEARCH, ContentSurface.RECOMMENDATIONS)) listOf(member) else listOf(public, member), filtered)
        }
    }
    @Test fun membershipFiltersEveryListExceptHistoryAndSearchCanOverrideBothFilters() {
        ContentSurface.entries.forEach { surface ->
            assertEquals(if (surface == ContentSurface.HISTORY) listOf(public, member) else listOf(public),
                ContentVisibility.filter(listOf(public, member), surface, false))
        }
        assertEquals(listOf(public, member), ContentVisibility.filter(listOf(public, member), ContentSurface.SEARCH, false, SearchVisibility(true, true), setOf(first, second)))
        assertEquals(emptyList<Video>(), ContentVisibility.filter(listOf(public, member), ContentSurface.SEARCH, true, SearchVisibility(false), setOf(first)))
    }
    @Test fun searchOverrideRetainsExplicitFalseAndResetUsesCurrentDefault() {
        listOf(SearchVisibility(), SearchVisibility(true, true), SearchVisibility(false)).forEach {
            assertEquals(it, SearchVisibility.parse(it.json()))
        }
        val reset = SearchVisibility(false, true).copy(showMembers = null)
        assertEquals(listOf(member), ContentVisibility.filter(listOf(member), ContentSurface.SEARCH, true, reset))
        assertTrue(ContentVisibility.filter(listOf(member), ContentSurface.SEARCH, false, reset).isEmpty())
        assertTrue(reset.includeBlocked)
    }
    @Test fun hiddenPagesAndDuplicatePlaylistOccurrencesPreservePaginationAndOriginalData() {
        val raw = listOf(member.copy(indexId = "A"), member.copy(indexId = "B"))
        assertTrue(ContentVisibility.filter(raw, ContentSurface.PLAYLIST, false).isEmpty())
        assertFalse(ContentVisibility.exhausted(emptyList(), raw))
        assertEquals(raw + public, ContentVisibility.merge(raw, listOf(raw.first(), public)))
        assertEquals(2, raw.size)
        assertTrue(ContentVisibility.exhausted(raw, listOf(raw.first())))
        assertTrue(ContentVisibility.exhausted(raw, emptyList()))
    }
    @Test fun blockApiUsesBearerJsonAndPublicSearchUsesCorrectSortWithoutAuthorization() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(response(BlockedChannel(first, "Alpha")))
            assertEquals(first, f.api.blockedChannels(f.context).single().id)
            val read = f.server.takeRequest(); assertEquals("Bearer token", read.getHeader("Authorization")); assertNull(read.getHeader("Cookie"))
            f.server.enqueue(MockResponse().setResponseCode(204)); f.api.blockChannel(first, "Alpha", true, f.context)
            val post = f.server.takeRequest(); assertEquals("POST", post.method); assertEquals("Alpha", JSONObject(post.body.readUtf8()).getString("name"))
            f.server.enqueue(MockResponse().setResponseCode(204)); f.api.blockChannel(first, "Alpha", false, f.context)
            assertEquals("DELETE", f.server.takeRequest().method)
            f.server.enqueue(MockResponse().setBody("[]")); f.api.search("cats", 2, "view_count", "week", "short")
            val search = f.server.takeRequest(); assertEquals("views", search.requestUrl!!.queryParameter("sort"))
            assertNull(search.requestUrl!!.queryParameter("sort_by")); assertNull(search.getHeader("Authorization"))
        }
    }
    @Test fun oldServerAndScopeFailuresExplainTheRequiredUpdate() = runBlocking {
        Fixture().use { f ->
            listOf(404 to "API update", 405 to "API update", 403 to "sign in again").forEach { (status, message) ->
                f.server.enqueue(MockResponse().setResponseCode(status).setBody("{\"error\":\"Invalid scope\"}"))
                try { f.api.blockedChannels(f.context); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains(message)) }
            }
        }
    }
    @Test fun confirmedSnapshotSurvivesOfflineFailureButFailedWritesDoNotChangeIt() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(response(BlockedChannel(first, "Alpha"))); f.repo.refresh()
            f.server.enqueue(MockResponse().setResponseCode(503)); f.repo.refresh()
            assertEquals(setOf(first), f.repo.state.value.ids); assertNotNull(f.repo.state.value.error)
            f.server.enqueue(MockResponse().setResponseCode(503))
            try { f.repo.setBlocked(f.context, first, "Alpha", false); fail() } catch (_: ApiException) { }
            assertEquals(setOf(first), f.repo.state.value.ids); assertNotNull(f.repo.state.value.actionErrors[first])
            assertEquals(setOf(first), BlockedRepository(f.api, f.local).apply { reset() }.state.value.ids)
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.setBlocked(f.context, first, "Alpha", false)
            assertTrue(f.repo.state.value.ids.isEmpty()); assertTrue(f.local.blockedSnapshot(f.context)!!.isEmpty())
            assertNull(f.repo.state.value.actionErrors[first])
        }
    }
    @Test fun delayedRefreshCannotUndoSuccessfulBlockOrUnblock() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(response(BlockedChannel(first, "Alpha"))); f.repo.refresh()
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.method == "GET") {
                    arrived.countDown(); await(release); response(BlockedChannel(first, "Alpha"))
                } else MockResponse().setResponseCode(204)
            }
            val refresh = async(Dispatchers.Default) { f.repo.refresh() }
            try {
                await(arrived); f.repo.refresh()
                f.repo.setBlocked(f.context, first, "Alpha", false)
                f.repo.setBlocked(f.context, second, "Beta", true)
            } finally { release.countDown() }
            refresh.await(); assertEquals(setOf(second), f.repo.state.value.ids)
            assertEquals(4, f.server.requestCount)
        }
    }
    @Test fun contextChangesRejectDelayedReadsAndWritesAndRestoreOnlyOwnedSnapshots() = runBlocking {
        Fixture().use { f ->
            f.local.saveBlockedSnapshot(f.context, listOf(BlockedChannel(first, "Alpha"))); f.repo.reset()
            val old = f.context
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { arrived.countDown(); await(release); return response(BlockedChannel(first, "Alpha")) }
            }
            val refresh = async(Dispatchers.Default) { f.repo.refresh() }
            try {
                await(arrived); f.account = f.account!!.copy(username = "Bob", token = "new-token"); f.repo.reset()
                assertTrue(f.repo.state.value.ids.isEmpty())
                try { f.repo.setBlocked(old, second, "Beta", true); fail() } catch (_: CancellationException) { }
            } finally { release.countDown() }
            refresh.await(); assertTrue(f.repo.state.value.ids.isEmpty()); assertEquals(1, f.server.requestCount)
            f.account = old.account; f.repo.reset(); assertEquals(setOf(first), f.repo.state.value.ids)
            f.address = "https://another.instance"; f.repo.reset(); assertTrue(f.repo.state.value.ids.isEmpty())
            f.account = null; f.repo.reset(); assertTrue(f.repo.state.value.ids.isEmpty())
        }
    }
    @Test fun mutationResponseCannotPublishIntoAnotherAccount() = runBlocking {
        Fixture().use { f ->
            val old = f.context; f.local.saveBlockedSnapshot(old, emptyList())
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { arrived.countDown(); await(release); return MockResponse().setResponseCode(204) }
            }
            val write = async(Dispatchers.Default) { f.repo.setBlocked(old, first, "Alpha", true) }
            try {
                await(arrived); f.account = f.account!!.copy(username = "Bob", token = "new-token"); f.repo.reset()
            } finally { release.countDown() }
            try { write.await(); fail() } catch (_: CancellationException) { }
            assertTrue(f.repo.state.value.ids.isEmpty()); assertTrue(f.repo.state.value.busy.isEmpty())
            assertTrue(f.local.blockedSnapshot(old)!!.isEmpty())
        }
    }
    @Test fun guestsNeverSendBlockListReadsOrInheritAccountSnapshot() = runBlocking {
        Fixture().use { f ->
            f.local.saveBlockedSnapshot(f.context, listOf(BlockedChannel(first, "Alpha")))
            f.account = null; f.repo.reset(); f.repo.refresh()
            assertTrue(f.repo.state.value.loaded); assertTrue(f.repo.state.value.ids.isEmpty()); assertEquals(0, f.server.requestCount)
        }
    }
    @Test fun successfulActionAfterFailedInitialReadIsStillSavedForOfflineFiltering() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setResponseCode(503)); f.repo.refresh()
            f.server.enqueue(MockResponse().setResponseCode(204)); f.repo.setBlocked(f.context, first, "Alpha", true)
            assertEquals(setOf(first), BlockedRepository(f.api, f.local).apply { reset() }.state.value.ids)
            assertNotNull(f.repo.state.value.error)
        }
    }
    @Test fun offlinePublicCacheRetainsRawMembershipAndCanBeRefilteredWithoutAnotherRequest() = runBlocking {
        Fixture().use { f ->
            val directory = java.nio.file.Files.createTempDirectory("visibility-cache").toFile()
            try {
                var offline = false
                val api = InvidiousApi({ f.address }, { f.account }, cache = ResponseCache(directory), onOffline = { offline = it })
                f.server.enqueue(MockResponse().setBody("""[{"videoId":"abcdefghijk","title":"Public","authorId":"$first"},{"videoId":"bbbbbbbbbbb","title":"Member","isMember":true}]"""))
                val raw = api.discovery("popular")
                f.server.enqueue(MockResponse().setResponseCode(503))
                val cached = api.discovery("popular")
                assertTrue(offline); assertEquals(raw, cached)
                assertTrue(ContentVisibility.filter(cached, ContentSurface.DISCOVERY, false, blocked = setOf(first)).isEmpty())
                assertEquals(2, ContentVisibility.filter(cached, ContentSurface.DISCOVERY, true).size)
                repeat(2) { assertNull(f.server.takeRequest().getHeader("Authorization")) }
            } finally { directory.deleteRecursively() }
        }
    }
}
