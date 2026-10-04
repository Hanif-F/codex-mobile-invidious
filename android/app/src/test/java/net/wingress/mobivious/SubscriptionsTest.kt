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
                listOf(Channel("a", "Studio"), Channel("a", "Duplicate response"))
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
                listOf(Channel("$call", "Response $call"))
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
                    listOf(Channel("a", "Alice's channel"))
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
            val result = async(Dispatchers.IO) { runCatching { api.subscriptions(before) } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            active = active.copy(generation = 1)
            assertTrue(result.await().exceptionOrNull() is CancellationException)
            assertTrue(runCatching { api.subscribe("channel", true, before) }.exceptionOrNull() is CancellationException)
            assertEquals(1, server.requestCount)
        }
    }
}
