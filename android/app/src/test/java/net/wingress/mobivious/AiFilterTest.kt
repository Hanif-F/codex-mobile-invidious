package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AiFilterTest {
    private val first = "UC" + "a".repeat(22)
    private val second = "UC" + "b".repeat(22)
    private val block = setOf(AiListKind.BLOCKLIST)
    private val both = AiListKind.entries.toSet()
    private fun settings(group: AiPageGroup = AiPageGroup.FEEDS, action: AiAction = AiAction.HIDE) = AiFilterSettings(true).withAction(AiListKind.BLOCKLIST, group, action)
    private fun response(ids: List<String> = listOf(first), matched: Set<AiListKind> = block, resolved: Boolean = true, version: String = "2026-10-09T00:00:00Z") =
        AiResponse(AiListKind.entries.associateWith { AiListStatus(true, false, 42, version) }, ids.associateWith { AiChannelMatch(matched, resolved) })

    @Test fun preferencesRoundTripSparsePatchesAndPausePreserveAllActions() {
        var ai = settings().withAction(AiListKind.WARNLIST, AiPageGroup.SEARCH, AiAction.REPLACE)
            .withAction(AiListKind.BLOCKLIST, AiPageGroup.OTHER, AiAction.REPLACE)
        val before = AccountPreferences(aiFilter = ai, region = "ID")
        assertEquals(before, AccountPreferences.parse(before.json()))
        ai = ai.copy(enabled = false)
        val patch = before.copy(aiFilter = ai).changesFrom(before)
        assertEquals(setOf("ai_filter_enabled"), patch.keys().asSequence().toSet())
        assertEquals(ai, before.merge(patch).aiFilter)
        assertEquals("ID", before.merge(patch).region)
        assertEquals(ai, LocalPreferences.read(before.copy(aiFilter = ai).json()).aiFilter)
    }
    @Test fun legacyChoicesAndInvalidCanonicalValuesFollowWebsiteRules() {
        val j = JSONObject("""{"ai_blocklist_feeds":true,"ai_blocklist_action":"replace_thumbnail","ai_warnlist_search":true,"ai_warnlist_other_pages":true}""")
        val ai = AiFilterSettings.parse(j)
        assertTrue(ai.enabled); assertEquals(AiAction.REPLACE, ai.saved(AiListKind.BLOCKLIST, AiPageGroup.FEEDS))
        assertEquals(AiAction.HIDE, ai.saved(AiListKind.WARNLIST, AiPageGroup.SEARCH))
        assertEquals(AiAction.REPLACE, ai.saved(AiListKind.WARNLIST, AiPageGroup.OTHER))
        assertEquals(ai, LocalPreferences.read(j).aiFilter)
        j.put("ai_filter_enabled", false).put("ai_blocklist_feeds_action", "invalid").put("ai_warnlist_other_pages_action", "hide")
        val invalid = AiFilterSettings.parse(j)
        assertFalse(invalid.enabled); assertEquals(AiAction.OFF, invalid.saved(AiListKind.BLOCKLIST, AiPageGroup.FEEDS))
        assertEquals(AiAction.OFF, invalid.saved(AiListKind.WARNLIST, AiPageGroup.OTHER))
        assertFalse(AiFilterSettings.parse(JSONObject()).enabled)
    }
    @Test fun hideWinsAndReplacementUsesBlocklistWithoutHidingLibrary() {
        var ai = settings(action = AiAction.REPLACE).withAction(AiListKind.WARNLIST, AiPageGroup.FEEDS, AiAction.HIDE)
        assertTrue(AiFilter.decide(ai, AiPageGroup.FEEDS, both).hidden)
        ai = ai.withAction(AiListKind.WARNLIST, AiPageGroup.FEEDS, AiAction.REPLACE)
        assertEquals(AiListKind.BLOCKLIST, AiFilter.decide(ai, AiPageGroup.FEEDS, both).warning)
        assertEquals(AiDecision(), AiFilter.decide(ai.copy(enabled = false), AiPageGroup.FEEDS, both))
        assertEquals(AiDecision(), AiFilter.decide(ai, AiPageGroup.OTHER, both))
        assertEquals(AiAction.OFF, ai.withAction(AiListKind.BLOCKLIST, AiPageGroup.OTHER, AiAction.HIDE).saved(AiListKind.BLOCKLIST, AiPageGroup.OTHER))
    }
    @Test fun scopedSearchesAndLibraryNeverUseDiscoveryActions() {
        assertEquals(AiPageGroup.FEEDS, AiPageGroup.browsing("Trending", ""))
        assertEquals(AiPageGroup.SEARCH, AiPageGroup.browsing("You", "hashtag:fixture"))
        assertEquals(AiPageGroup.SEARCH, AiPageGroup.browsing("Search", ""))
        for (destination in listOf("Subscriptions" to "", "Subscriptions" to "subscription-channels", "Search" to "channel:$first", "You" to "history", "Popular" to "playlist:IVfixture", "You" to "clips"))
            assertEquals(AiPageGroup.OTHER, AiPageGroup.browsing(destination.first, destination.second))
    }
    @Test fun apiUsesPublicBoundedQueriesAndParsesUnavailableLists() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) })
            val body = """{"lists":{"blocklist":{"available":true,"stale":true,"channelCount":42,"updatedAt":"2026-10-09T00:00:00Z"},"warnlist":{"available":false,"stale":true,"channelCount":0,"updatedAt":null}},"channels":{"$first":{"matches":["blocklist"],"resolved":false}}}"""
            server.enqueue(MockResponse().setBody(body))
            val value = api.aiChannels(listOf(first), both, api.context())
            assertEquals(block, value.channels[first]?.matches); assertFalse(value.channels[first]!!.resolved)
            assertFalse(value.lists[AiListKind.WARNLIST]!!.available)
            val request = server.takeRequest()
            assertEquals("/api/v1/ai/channels", request.requestUrl!!.encodedPath)
            assertEquals(first, request.requestUrl!!.queryParameter("ids")); assertEquals("blocklist,warnlist", request.requestUrl!!.queryParameter("lists"))
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
        }
    }
    @Test fun batchesDeduplicatesExpiresAndInvalidatesWhenListVersionChanges() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = AtomicInteger(); var now = 0L; var version = "2026-10-09T00:00:00Z"
            val owner = ApiContext("https://one.example", null)
            val repo = AiFilterRepository(scope, { owner }, { response(version = version) }, { ids, kinds, _ ->
                assertTrue(ids.size <= 100); assertEquals(block, kinds); calls.incrementAndGet(); response(ids, version = version)
            }, { now })
            val ids = (0..200).map { "UC" + it.toString().padStart(22, 'a') }
            repo.prepare(ids + ids, settings(), AiPageGroup.FEEDS)
            assertEquals(3, calls.get()); assertEquals(201, repo.state.value.matches.size)
            repo.prepare(ids, settings(), AiPageGroup.FEEDS); assertEquals(3, calls.get())
            now = 300_001; repo.prepare(ids, settings(), AiPageGroup.FEEDS); assertEquals(6, calls.get())
            version = "2026-10-09T06:00:00Z"; repo.refreshStatus(); assertTrue(repo.state.value.matches.isEmpty())
            repo.prepare(ids, settings(), AiPageGroup.FEEDS); assertEquals(9, calls.get())
        } finally { scope.cancel() }
    }
    @Test fun overlappingSurfacesShareInflightChannelAndListWork() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>(); val calls = AtomicInteger()
            val owner = ApiContext("https://one.example", null)
            val repo = AiFilterRepository(scope, { owner }, { response() }, { ids, _, _ -> calls.incrementAndGet(); gate.await(); response(ids) })
            repo.configure(settings())
            val jobs = repo.ensure(listOf(first, first), block) + repo.ensure(listOf(first), both)
            withTimeout(2_000) { while (calls.get() < 2) yield() }
            assertEquals(2, calls.get()); gate.complete(Unit); jobs.joinAll()
            assertEquals(block, repo.state.value.matches[first])
        } finally { scope.cancel() }
    }
    @Test fun unknownResponsesAreRetriedOnLaterLoadsAndFailuresKeepKnownMatches() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val owner = ApiContext("https://one.example", null); val calls = AtomicInteger(); var fail = false; var now = 0L
            val repo = AiFilterRepository(scope, { owner }, { response() }, { ids, _, _ ->
                if (fail) throw ApiException(503, "Offline")
                response(ids, resolved = calls.incrementAndGet() != 1)
            }, { now })
            repo.prepare(listOf(first), settings(), AiPageGroup.FEEDS)
            repo.prepare(listOf(first), settings(), AiPageGroup.FEEDS); assertEquals(2, calls.get())
            fail = true; now = 300_001; repo.prepare(listOf(first, second), settings(), AiPageGroup.FEEDS)
            assertEquals(block, repo.state.value.matches[first]); assertNull(repo.state.value.matches[second]); assertEquals("Offline", repo.state.value.error)
        } finally { scope.cancel() }
    }
    @Test fun boundedPageWaitLeavesTheBackgroundRequestAlive() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>(); val owner = ApiContext("https://one.example", null)
            val repo = AiFilterRepository(scope, { owner }, { response() }, { ids, _, _ -> gate.await(); response(ids) })
            withTimeout(3_000) { repo.prepare(listOf(first), settings(), AiPageGroup.FEEDS) }
            assertTrue(repo.state.value.matches.isEmpty())
            gate.complete(Unit)
            withTimeout(2_000) { while (repo.state.value.matches[first] != block) yield() }
        } finally { scope.cancel() }
    }
    @Test fun pendingClassificationsRetryOnlyOnceAfterFiveSeconds() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = AtomicInteger(); val owner = ApiContext("https://one.example", null)
            val repo = AiFilterRepository(scope, { owner }, { response() }, { ids, _, _ -> calls.incrementAndGet(); response(ids, resolved = false) })
            repo.prepare(listOf(first), settings(), AiPageGroup.FEEDS)
            assertEquals(1, calls.get())
            delay(200); assertEquals(1, calls.get())
            withTimeout(7_000) { while (calls.get() < 2) delay(50) }
            delay(5_200); assertEquals(2, calls.get())
        } finally { scope.cancel() }
    }
    @Test fun aLateBatchCannotRestoreAnOlderListSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>(); val owner = ApiContext("https://one.example", null)
            val newer = "2026-10-09T06:00:00Z"
            val repo = AiFilterRepository(scope, { owner }, { response(version = newer) }, { ids, _, _ -> started.complete(Unit); gate.await(); response(ids) })
            repo.configure(settings()); val jobs = repo.ensure(listOf(first), block); started.await()
            repo.refreshStatus(); gate.complete(Unit); jobs.joinAll()
            assertEquals(newer, repo.state.value.lists[AiListKind.BLOCKLIST]?.updatedAt)
            assertTrue(repo.state.value.matches.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun pauseAndInstanceChangesCancelOldWorkAndDoNotScheduleDisabledLists() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var owner = ApiContext("https://one.example", null); val gate = CompletableDeferred<Unit>(); val calls = AtomicInteger()
            val repo = AiFilterRepository(scope, { owner }, { throw ApiException(404, "Not found") }, { ids, _, _ -> calls.incrementAndGet(); gate.await(); response(ids) })
            repo.prepare(listOf(first), AiFilterSettings(), AiPageGroup.FEEDS); assertEquals(0, calls.get())
            repo.configure(settings()); val pending = repo.ensure(listOf(first), block)
            withTimeout(2_000) { while (calls.get() == 0) yield() }
            owner = ApiContext("https://two.example", null, 1); repo.reset(); gate.complete(Unit); pending.joinAll()
            assertTrue(repo.state.value.matches.isEmpty()); assertEquals(owner, repo.state.value.context)
            repo.refreshStatus(); assertTrue(repo.state.value.error!!.contains("Update this server"))
            repo.configure(AiFilterSettings()); assertTrue(repo.ensure(listOf(second), both).isEmpty())
        } finally { scope.cancel() }
    }
}
