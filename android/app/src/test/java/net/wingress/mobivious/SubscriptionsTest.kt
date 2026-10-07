package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubscriptionsTest {
    private val context = ApiContext("https://instance.test", Account("fixture-token", "Alice", Long.MAX_VALUE, "https://instance.test"))

    @Test fun channelNamesSortIgnoringCaseAndEqualNamesKeepDistinctIds() {
        val channels = listOf(Channel("z", "Zulu"), Channel("b", "alpha"), Channel("a", "Alpha"), Channel("c", "Bravo"))
        val state = SubscriptionChannelsState(channels = channels)
        assertEquals(listOf("a", "b", "c", "z"), state.matches.map { it.id })
        assertEquals(channels, state.channels)
    }

    @Test fun searchMatchesNameSubstringsImmediatelyWithoutSearchingOtherFields() {
        val state = SubscriptionChannelsState(channels = listOf(Channel("a", "Café Studio"), Channel("studio-id", "Zulu", description = "Studio")))
        assertEquals(listOf("a"), state.copy(query = "  sTuDIO  ").matches.map { it.id })
        assertEquals(listOf("a"), state.copy(query = "CAFÉ").matches.map { it.id })
        assertTrue(state.copy(query = "absent").matches.isEmpty())
        assertEquals(2, state.copy(query = " \t ").matches.size)
    }

    @Test fun refreshKeepsLoadedChannelsAndQueryThroughErrorsAndRetry() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var failure = false; var calls = 0
            val controller = SubscriptionsController(scope, { context }, {
                calls++
                if (failure) throw IllegalStateException("Temporarily unavailable")
                SubscriptionDirectory(listOf(Channel("a", "Studio"), Channel("a", "Duplicate response")))
            }, { it.message.orEmpty() })
            controller.refresh(); controller.search("stud")
            assertEquals(1, controller.state.value.channels.size)
            assertTrue(controller.state.value.loaded)
            assertEquals(1, calls) // Filtering never sends a request.
            failure = true; controller.refresh()
            assertEquals("Temporarily unavailable", controller.state.value.error)
            assertEquals("stud", controller.state.value.query)
            assertEquals("Studio", controller.state.value.channels.single().name)
            assertFalse(controller.state.value.loading)
            failure = false; controller.refresh()
            assertNull(controller.state.value.error)
            assertEquals("stud", controller.state.value.query)
        } finally { scope.cancel() }
    }

    @Test fun newestRefreshWinsEvenWhenCanceledTransportFinishesLate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        try {
            var calls = 0
            val controller = SubscriptionsController(scope, { context }, {
                val call = ++calls
                if (call == 1) withContext(NonCancellable) { gate.await() }
                SubscriptionDirectory(listOf(Channel("$call", "Response $call")))
            }, { it.message.orEmpty() })
            controller.refresh(); assertTrue(controller.state.value.loading)
            controller.refresh(); gate.complete(Unit); yield()
            assertEquals("Response 2", controller.state.value.channels.single().name)
            assertFalse(controller.state.value.loading)
        } finally { gate.complete(Unit); scope.cancel() }
    }

    @Test fun accountServerAndGenerationChangesClearChannelsQueryAndLateErrors() = runBlocking {
        for (replacement in listOf(context.copy(account = null), context.copy(account = context.account!!.copy(username = "Bob")),
            context.copy(server = "https://another.test", account = context.account.copy(server = "https://another.test")), context.copy(generation = 1))) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val gate = CompletableDeferred<Unit>()
            try {
                var active = context; var delay = false
                val controller = SubscriptionsController(scope, { active }, {
                    if (delay) { withContext(NonCancellable) { gate.await() }; throw IllegalStateException("Old account error") }
                    SubscriptionDirectory(listOf(Channel("a", "Alice's channel")))
                }, { it.message.orEmpty() })
                controller.refresh(); controller.search("Alice")
                delay = true; controller.refresh()
                active = replacement; controller.reset(); gate.complete(Unit); yield()
                assertEquals(replacement, controller.state.value.context)
                assertTrue(controller.state.value.channels.isEmpty()); assertEquals("", controller.state.value.query)
                assertNull(controller.state.value.error); assertFalse(controller.state.value.loading)
            } finally { gate.complete(Unit); scope.cancel() }
        }
    }

    @Test fun guestRefreshMakesNoAuthenticatedRequest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = SubscriptionsController(scope, { context.copy(account = null) }, { error("Guest fetch") }, { it.message.orEmpty() })
            controller.refresh()
            assertTrue(controller.state.value.loaded); assertFalse(controller.state.value.loading)
            assertTrue(controller.state.value.channels.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun allSortsUseTheirOwnMetricAndBreakTiesByNameAndId() {
        val channels = listOf(Channel("b", "same"), Channel("a", "Same"), Channel("c", "Zulu"), Channel("d", "Dormant"))
        val state = SubscriptionChannelsState(channels = channels, stats = mapOf(
            "a" to SubscriptionStats(100, 8, 0, 0.0), "b" to SubscriptionStats(200, 1, 1, 1.5),
            "c" to SubscriptionStats(null, 6, 6, 4.0), "d" to SubscriptionStats(null, 0, 0, 0.0)))
        val expected = mapOf(SubscriptionSort.ALPHABETICAL to listOf("d", "a", "b", "c"),
            SubscriptionSort.LATEST to listOf("b", "a", "d", "c"),
            SubscriptionSort.MOST_WATCHED to listOf("a", "c", "b", "d"),
            SubscriptionSort.RELEVANCE to listOf("c", "b", "d", "a"))
        expected.forEach { (sort, order) -> assertEquals(order, state.copy(sort = sort).matches.map { it.id }) }
        assertEquals(listOf("b", "a"), state.copy(sort = SubscriptionSort.LATEST, query = " SaMe ").matches.map { it.id })
        assertEquals(listOf("a", "b"), state.copy(sort = SubscriptionSort.RELEVANCE,
            channels = channels.take(2), stats = emptyMap()).matches.map { it.id })
        assertEquals(SubscriptionSort.RELEVANCE, SubscriptionSort.saved(null))
        assertEquals(SubscriptionSort.RELEVANCE, SubscriptionSort.saved("invalid"))
    }

    @Test fun sortChoicePersistsWithoutFetchAndSurvivesFailureAndLegacyFallback() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0; var failRead = false; var legacy = false
            val saved = mutableMapOf<String, SubscriptionSort>()
            var active = context
            val controller = SubscriptionsController(scope, { active }, {
                calls++; if (failRead) error("Offline")
                SubscriptionDirectory(listOf(Channel("b", "Beta"), Channel("a", "Alpha")),
                    mapOf("b" to SubscriptionStats(null, 3, 2, 2.0), "a" to SubscriptionStats(null, 1, 1, 1.0)),
                    if (legacy) SubscriptionDirectory.LEGACY else null)
            }, { it.message.orEmpty() }, { saved[it.server] ?: SubscriptionSort.RELEVANCE }, { ctx, sort -> saved[ctx.server] = sort })
            controller.refresh(); controller.search("a"); controller.sort(SubscriptionSort.MOST_WATCHED)
            assertEquals(1, calls); assertEquals(SubscriptionSort.MOST_WATCHED, saved[context.server])
            failRead = true; controller.refresh()
            assertEquals("Offline", controller.state.value.error); assertEquals("a", controller.state.value.query)
            assertEquals(SubscriptionSort.MOST_WATCHED, controller.state.value.sort)
            assertEquals(listOf("b", "a"), controller.state.value.matches.map { it.id })
            failRead = false; legacy = true; controller.refresh()
            assertEquals(SubscriptionSort.ALPHABETICAL, controller.state.value.effectiveSort)
            assertEquals(SubscriptionSort.MOST_WATCHED, saved[context.server])
            controller.sort(SubscriptionSort.LATEST)
            assertEquals(SubscriptionSort.MOST_WATCHED, controller.state.value.sort)
            legacy = false; controller.refresh()
            assertEquals(SubscriptionSort.MOST_WATCHED, controller.state.value.effectiveSort)
            active = active.copy(server = "https://second.test"); controller.reset()
            assertEquals(SubscriptionSort.RELEVANCE, controller.state.value.sort)
            assertTrue(controller.state.value.stats.isEmpty())
            active = context.copy(generation = 1); controller.reset()
            assertEquals(SubscriptionSort.MOST_WATCHED, controller.state.value.sort)
        } finally { scope.cancel() }
    }

    @Test fun directoryParserAcceptsZeroAndUnknownButRejectsMalformedStats() {
        val stats = """{"latestUpload":null,"allTimeWatched":0,"recentWatched":0,"relevance":0}"""
        val body = """[{"authorId":"a","author":"Alpha","subscriptionStats":$stats}]"""
        assertEquals(SubscriptionStats(null, 0, 0, 0.0), SubscriptionDirectory.parse(body).stats["a"])
        assertNull(SubscriptionDirectory.parse("[]").unavailableReason)
        assertEquals(SubscriptionDirectory.LEGACY, SubscriptionDirectory.parse("""[{"authorId":"a","author":"Alpha"}]""").unavailableReason)
        for (malformed in listOf(stats.replace("\"relevance\":0", "\"relevance\":\"NaN\""),
            stats.replace("\"recentWatched\":0", "\"recentWatched\":1"),
            stats.replace("\"allTimeWatched\":0", "\"allTimeWatched\":-1"),
            stats.replace("\"latestUpload\":null", "\"latestUpload\":1.5"),
            stats.replace("\"allTimeWatched\":0", "\"allTimeWatched\":\"0\""), "null", "{}")) {
            assertThrows(IllegalArgumentException::class.java) { SubscriptionDirectory.parse(body.replace(stats, malformed)) }
        }
    }

    @Test fun directoryApiRequestsStatsAndOnlyFallsBackForMissingHistoryPermission() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val active = context.copy(server = address, account = context.account!!.copy(server = address))
            val api = InvidiousApi({ address }, { active.account })
            val body = """[{"authorId":"a","author":"Alpha","subscriptionStats":{"latestUpload":123,"allTimeWatched":4,"recentWatched":2,"relevance":1.5}}]"""
            server.enqueue(MockResponse().setBody(body))
            assertEquals(4, api.subscriptionDirectory(active).stats["a"]!!.allTimeWatched)
            val enriched = server.takeRequest()
            assertEquals("/api/v1/auth/subscriptions?include_stats=true", enriched.path)
            assertEquals("Bearer fixture-token", enriched.getHeader("Authorization")); assertNull(enriched.getHeader("Cookie"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Channel sorting requires history read permission."}"""))
            server.enqueue(MockResponse().setBody("""[{"authorId":"a","author":"Alpha"}]"""))
            assertEquals(SubscriptionDirectory.HISTORY_PERMISSION, api.subscriptionDirectory(active).unavailableReason)
            assertEquals("/api/v1/auth/subscriptions?include_stats=true", server.takeRequest().path)
            assertEquals("/api/v1/auth/subscriptions", server.takeRequest().path)
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"Unavailable"}"""))
            assertTrue(runCatching { api.subscriptionDirectory(active) }.exceptionOrNull() is ApiException)
            server.takeRequest(); assertEquals(4, server.requestCount)
            server.enqueue(MockResponse().setBody("""[{"authorId":"a","author":"Alpha"}]"""))
            assertEquals(SubscriptionDirectory.LEGACY, api.subscriptionDirectory(active).unavailableReason)
        }
    }

    @Test fun subscriptionApiUsesCapturedBearerContextForReadsAndChanges() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val active = context.copy(server = address, account = context.account!!.copy(server = address))
            val api = InvidiousApi({ address }, { active.account })
            server.enqueue(MockResponse().setBody("""[{"authorId":"channel","author":"Studio","authorThumbnails":[{"url":"/avatar","width":88}]}]"""))
            val channel = api.subscriptions(active).single()
            assertEquals("Studio", channel.name); assertEquals("/avatar", channel.image)
            val read = server.takeRequest(); assertEquals("/api/v1/auth/subscriptions", read.path)
            assertEquals("Bearer fixture-token", read.getHeader("Authorization"))
            for (subscribed in listOf(true, false)) {
                server.enqueue(MockResponse().setResponseCode(204)); api.subscribe("channel", subscribed, active)
                val write = server.takeRequest(); assertEquals(if (subscribed) "POST" else "DELETE", write.method)
                assertEquals("/api/v1/auth/subscriptions/channel", write.path)
                assertEquals("Bearer fixture-token", write.getHeader("Authorization"))
            }
        }
    }

    @Test fun apiRejectsResponsesFromAFormerSessionAndRejectsStaleWritesBeforeSending() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            var active = context.copy(server = address, account = context.account!!.copy(server = address))
            val api = InvidiousApi({ address }, { active.account }, generation = { active.generation })
            val before = active
            server.enqueue(MockResponse().setBody("[]").setBodyDelay(200, TimeUnit.MILLISECONDS))
            val result = async(Dispatchers.IO) { runCatching { api.subscriptionDirectory(before) } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            active = active.copy(generation = 1)
            assertTrue(result.await().exceptionOrNull() is CancellationException)
            assertTrue(runCatching { api.subscribe("channel", true, before) }.exceptionOrNull() is CancellationException)
            assertEquals(1, server.requestCount)
        }
    }
}
