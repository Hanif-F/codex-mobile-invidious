package net.wingress.mobivious

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeArrowTest {
    private suspend fun until(condition: () -> Boolean) { withTimeout(5000) { while (!condition()) delay(10) } }

    @Test fun titleAndIdentityValidationMatchesServerAndKeepsOriginalVoteRules() {
        assertTrue(DeArrowRules.validTitle("  A clear title  "))
        assertTrue(DeArrowRules.validTitle("😀".repeat(110)))
        listOf("", "   ", "a".repeat(111), "two\nlines", "two\rlines").forEach { assertFalse(DeArrowRules.validTitle(it)) }
        assertTrue(DeArrowRules.validPrivateId("")); assertTrue(DeArrowRules.validPrivateId("a".repeat(64)))
        assertFalse(DeArrowRules.validPrivateId("public-name"))
        assertFalse(DeArrowRules.canDownvote(null))
        assertFalse(DeArrowRules.canDownvote(DeArrowSubmission("Locked", false, 4, true, "locked")))
        assertTrue(DeArrowRules.canDownvote(DeArrowSubmission("Proposal", false, -3, false, "proposal")))
    }

    @Test fun parsingPreservesProposalOrderAndPreferencePatchesContainOnlyChanges() {
        val old = ApiParser.preferences(JSONObject("{}"))
        assertFalse(old.dearrowEnabled); assertTrue(old.dearrowShowOriginal)
        assertEquals(0, old.changesFrom(old).length())
        val changed = old.copy(dearrowEnabled = true).changesFrom(old)
        assertEquals(setOf("dearrow_enabled"), changed.keys().asSequence().toSet())
        val items = ApiParser.dearrowSubmissions(JSONObject("""{"titles":[{"title":"Original","original":true,"votes":2,"locked":true,"UUID":"one"},{"title":"A >proposal","original":false,"votes":-3,"locked":false,"UUID":"two"}]}"""))
        assertEquals(listOf("one", "two"), items.map { it.uuid }); assertEquals("A >proposal", items[1].title)
    }

    @Test fun lookupsDeduplicateAndBoundConcurrencyToFour() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val active = AtomicInteger(); val max = AtomicInteger(); val calls = AtomicInteger(); val gate = CompletableDeferred<Unit>()
            val titles = DeArrowTitles(scope, { "instance" }) { id ->
                calls.incrementAndGet(); val count = active.incrementAndGet(); max.updateAndGet { previous -> maxOf(previous, count) }
                gate.await(); active.decrementAndGet(); "Replacement $id"
            }
            repeat(8) { i -> repeat(3) { titles.ensure(i.toString().padStart(11, '0')) } }
            until { active.get() == 4 }; assertEquals(4, calls.get()); gate.complete(Unit)
            until { titles.titles.value.size == 8 }
            assertEquals(8, calls.get()); assertEquals(4, max.get())
        } finally { scope.cancel() }
    }

    @Test fun failuresAndMissingTitlesKeepOriginalUntilExplicitInvalidation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = AtomicInteger()
            val titles = DeArrowTitles(scope, { "instance" }) { id -> calls.incrementAndGet(); if (id == "abcdefghijk") throw IOException("offline") else null }
            titles.ensure("abcdefghijk"); titles.ensure("bbbbbbbbbbb"); titles.ensure("invalid")
            until { titles.titles.value.size == 2 }; assertNull(titles.titles.value["abcdefghijk"])
            titles.ensure("abcdefghijk"); delay(30); assertEquals(2, calls.get())
            titles.invalidate("abcdefghijk"); titles.ensure("abcdefghijk")
            until { calls.get() == 3 && "abcdefghijk" in titles.titles.value }
        } finally { scope.cancel() }
    }

    @Test fun lateResponseCannotCrossInstancesOrReplaceANewerLookup() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val instance = AtomicReference("first"); val gate = CompletableDeferred<Unit>(); val calls = AtomicInteger()
            val titles = DeArrowTitles(scope, { instance.get() }) {
                if (calls.incrementAndGet() == 1) withContext(NonCancellable) { gate.await(); "Old title" } else "New title"
            }
            titles.ensure("abcdefghijk"); until { calls.get() == 1 }
            instance.set("second"); titles.clear(); titles.ensure("abcdefghijk")
            until { titles.titles.value["abcdefghijk"] == "New title" }; gate.complete(Unit); delay(100)
            assertEquals("New title", titles.titles.value["abcdefghijk"])
            titles.invalidate("abcdefghijk"); assertFalse("abcdefghijk" in titles.titles.value)
        } finally { scope.cancel() }
    }

    @Test fun nativeContractUsesBearerAndJsonBooleansWithoutSendingIdentityOnReads() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) }); val context = api.context()
            server.enqueue(MockResponse().setBody("""{"title":null}""")); assertNull(api.dearrowTitle("abcdefghijk"))
            assertNull(server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"ready":true,"configured":false}""")); assertFalse(api.dearrowIdentity().configured)
            val identity = server.takeRequest(); assertEquals("Bearer token", identity.getHeader("Authorization")); assertEquals(0L, identity.bodySize)
            server.enqueue(MockResponse().setBody("""{"ok":true}""")); api.contributeDeArrow("abcdefghijk", JSONObject().put("action", "submit").put("title", "A title").put("confirmed", true), context)
            val request = server.takeRequest(); assertEquals("POST", request.method); assertEquals("/api/v1/auth/dearrow/abcdefghijk", request.path)
            val body = JSONObject(request.body.readUtf8()); assertEquals(true, body.get("confirmed")); assertFalse(body.has("userID"))
            server.enqueue(MockResponse().setBody("""{"watch_history":true,"dearrow_enabled":true,"unrelated":"preserved"}"""))
            assertTrue(api.preferences(JSONObject().put("dearrow_enabled", true), context).dearrowEnabled)
            assertEquals(setOf("dearrow_enabled"), JSONObject(server.takeRequest().body.readUtf8()).keys().asSequence().toSet())
        }
    }

    @Test fun writeTimeoutIsNotRetriedAndAnOldContextCannotWriteForANewAccount() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            var account = Account("first-token", "Alice", Long.MAX_VALUE, address)
            val client = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS).build()
            val api = InvidiousApi({ address }, { account }, client = client); val context = api.context()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try { api.contributeDeArrow("abcdefghijk", JSONObject().put("action", "upvote").put("original", true), context); fail("Timeout ignored") } catch (_: IOException) { }
            assertEquals(1, server.requestCount)
            account = account.copy(token = "second-token", username = "Bob")
            try { api.importDeArrowIdentity("a".repeat(64), context); fail("Stale context wrote") } catch (_: CancellationException) { }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun oldServersAndOldTokensHaveActionableErrorsWithoutSigningOut() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/'); var expired = false
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) }, { expired = true })
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
            try { api.dearrowIdentity(); fail("Missing API ignored") } catch (e: ApiException) { assertTrue(e.message!!.contains("DeArrow API update")) }
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Invalid scope"}"""))
            try { api.dearrowIdentity(); fail("Missing scope ignored") } catch (e: ApiException) { assertTrue(e.message!!.contains("sign in again")) }
            assertFalse(expired)
        }
    }

    @Test fun privateIdentityImportCannotFollowARedirectEvenWithAnInjectedClient() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { other ->
            first.start(); other.start(); val address = first.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) }, client = OkHttpClient())
            first.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/identity")))
            try { api.importDeArrowIdentity("c".repeat(64), api.context()); fail("Identity redirect followed") } catch (e: ApiException) { assertEquals(307, e.status) }
            assertEquals(1, first.requestCount); assertEquals(0, other.requestCount)
        } }
    }
}
