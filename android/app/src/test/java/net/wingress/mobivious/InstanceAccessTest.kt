package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InstanceAccessTest {
    private class Memory : VerificationPersistence {
        val values = mutableMapOf<String, VerificationPass>()
        override fun read(origin: String) = values[origin]
        override fun write(origin: String, pass: VerificationPass?) { if (pass == null) values.remove(origin) else values[origin] = pass }
    }
    @Test fun discoveryDefaultsAndOnlyKnownCodesBecomeAccessErrors() {
        assertFalse(Registration.parse(JSONObject("{}" )).loginRequired)
        assertTrue(Registration.parse(JSONObject("""{"loginRequired":true}""")).loginRequired)
        assertEquals(AccessReason.LOGIN, InstanceAccess.classify(401, null, """{"error":"login_required"}"""))
        assertNull(InstanceAccess.classify(401, null, """{"error":"Incorrect username or password."}"""))
        assertNull(InstanceAccess.classify(403, null, """{"error":"Invalid scope"}"""))
        assertEquals(AccessReason.VERIFICATION, InstanceAccess.classify(403, "verification_expired", ""))
        assertEquals(AccessReason.UNAVAILABLE, InstanceAccess.classify(503, null, """{"error":"verification_unavailable"}"""))
    }
    @Test fun headersAreBoundToOriginExpiryAndCurrentContext() {
        val address = "https://instance.example"
        var context = ApiContext(address, Account("account", "Alice", 100, address), 2)
        val memory = Memory(); memory.write(InstanceAccess.origin(address.toHttpUrl()), VerificationPass("pass", 90))
        var now = 50L; val access = InstanceAccess({ context }, memory) { now }
        assertEquals(mapOf("Authorization" to "Bearer account", InstanceAccess.HEADER to "pass"), access.headers("$address/media".toHttpUrl(), context))
        for (foreign in listOf("https://other.example/media", "http://instance.example/media", "https://instance.example:444/media"))
            assertTrue(access.headers(foreign.toHttpUrl(), context).isEmpty())
        assertTrue(access.headers("$address/api/v1/mobile/login".toHttpUrl(), context).isEmpty())
        assertEquals("https://[::1]:443", InstanceAccess.origin("https://[::1]/verify".toHttpUrl()))
        val before = context; context = context.copy(generation = 3)
        assertTrue(access.headers("$address/media".toHttpUrl(), before).isEmpty())
        now = 101; assertTrue(access.headers("$address/media".toHttpUrl(), context).isEmpty())
    }
    @Test fun redirectHopsStripBothSecretsAndPreserveRange() {
        MockWebServer().use { origin -> MockWebServer().use { external ->
            val address = origin.url("/").toString().trimEnd('/')
            val context = ApiContext(address, Account("account", "Alice", Long.MAX_VALUE, address))
            val memory = Memory(); memory.write(InstanceAccess.origin(address.toHttpUrl()), VerificationPass("pass", Long.MAX_VALUE))
            val access = InstanceAccess({ context }, memory)
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", origin.url("/same")))
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", external.url("/media")))
            external.enqueue(MockResponse().setBody("media"))
            access.client(OkHttpClient()).newCall(Request.Builder().url(origin.url("/start")).header("Range", "bytes=10-").build()).execute().close()
            repeat(2) { val request = origin.takeRequest(); assertEquals("Bearer account", request.getHeader("Authorization")); assertEquals("pass", request.getHeader(InstanceAccess.HEADER)) }
            val request = external.takeRequest(); assertNull(request.getHeader("Authorization")); assertNull(request.getHeader(InstanceAccess.HEADER)); assertEquals("bytes=10-", request.getHeader("Range"))
        } }
    }
    @Test fun gateDenialsNeverExpireAccountsOrUseFeedFallback() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val context = ApiContext(address, Account("account", "Alice", Long.MAX_VALUE, address)); var expired = 0
            val access = InstanceAccess({ context }, Memory())
            val api = InvidiousApi({ address }, { context.account }, { expired++ }, access = access)
            for ((code, error, reason) in listOf(Triple(403, "verification_expired", AccessReason.VERIFICATION), Triple(503, "verification_unavailable", AccessReason.UNAVAILABLE), Triple(401, "login_required", AccessReason.LOGIN))) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("""{"error":"$error"}"""))
                try { api.request("api/v1/popular"); fail("Gate accepted") } catch (e: InstanceAccessException) { assertEquals(reason, e.reason) }
            }
            assertEquals(0, expired); assertEquals(AccessReason.LOGIN, access.issue.value?.reason)
        }
    }
    @Test fun completionRechecksPassAndPersistsAcrossAccountsButNotInstances() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/'); var context = ApiContext(address, null)
            val memory = Memory(); val access = InstanceAccess({ context }, memory) { 10 }
            server.enqueue(MockResponse().setBody("""{"enabled":true,"required":false,"verified":true,"expiresAt":100}"""))
            access.complete(context, "signed-pass", 100, true)
            assertEquals("signed-pass", server.takeRequest().getHeader(InstanceAccess.HEADER))
            assertEquals("signed-pass", access.pass(context)?.token)
            context = context.copy(account = Account("account", "Alice", 200, address), generation = 1)
            assertEquals("signed-pass", access.pass(context)?.token)
            val old = context; context = ApiContext("https://other.example", null, 2)
            assertNull(access.pass(context)); access.report(old, AccessReason.LOGIN); assertNull(access.issue.value)
            try { access.complete(old, "late-pass", 100, true); fail("Stale completion accepted") } catch (_: CancellationException) { }
            assertNotEquals(InstanceAccess.cacheScope(old), InstanceAccess.cacheScope(context))
        }
    }
    @Test fun oldServersAreUnsupportedButOutagesAndRejectedPassesStayErrors() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Memory()) { 10 }
            for (code in listOf(404, 405)) { server.enqueue(MockResponse().setResponseCode(code)); assertNull(access.discover(context)) }
            server.enqueue(MockResponse().setResponseCode(503))
            try { access.discover(context); fail("Outage treated as unsupported") } catch (_: java.io.IOException) { }
            server.enqueue(MockResponse().setBody("""{"enabled":true,"required":true,"verified":false,"expiresAt":null}"""))
            try { access.complete(context, "forged", 100, true); fail("Invalid pass saved") } catch (_: IllegalArgumentException) { }
            assertNull(access.pass(context))
        }
    }
    @Test fun lateDenialsAndCompletionsCannotChangeANewContext() = runBlocking {
        MockWebServer().use { server ->
            var context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Memory())
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { entered.countDown(); release.await(5, TimeUnit.SECONDS); return MockResponse().setResponseCode(403).setBody("""{"error":"verification_required"}""") }
            }
            val call = async(Dispatchers.IO) { runCatching { access.client().newCall(Request.Builder().url(server.url("/media")).build()).execute().close() } }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); context = context.copy(generation = 1); release.countDown(); assertTrue(call.await().isFailure)
            assertNull(access.issue.value)
        }
    }
    @Test fun cachedFeedsAreAccountScopedAndUnavailableGatesNeverServeSnapshots() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            var account = Account("alice", "Alice", Long.MAX_VALUE, address)
            val folder = java.nio.file.Files.createTempDirectory("access-cache").toFile()
            try {
                val access = InstanceAccess({ ApiContext(address, account) }, Memory())
                val api = InvidiousApi({ address }, { account }, cache = ResponseCache(folder), access = access)
                server.enqueue(MockResponse().setBody("[1]")); assertEquals("[1]", api.request("api/v1/popular"))
                server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"verification_unavailable"}"""))
                try { api.request("api/v1/popular"); fail("Protected cached feed leaked") } catch (_: InstanceAccessException) { }
                account = account.copy(token = "bob", username = "Bob"); access.contextChanged()
                server.enqueue(MockResponse().setResponseCode(500))
                try { api.request("api/v1/popular"); fail("Another account's feed leaked") } catch (_: ApiException) { }
            } finally { folder.deleteRecursively() }
        }
    }
    @Test fun accessErrorsAreExcludedFromAutomaticBufferRecovery() {
        val error = androidx.media3.common.PlaybackException("blocked", InstanceAccessException(AccessReason.VERIFICATION),
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertFalse(net.wingress.mobivious.player.BufferRecoveryPolicy.eligible(error, true))
    }
    @Test fun onlyGateDrivenSameIdentityAuthenticationMayRetainPlayback() {
        val oldAccount = Account("old", "Alice", Long.MAX_VALUE, "https://instance.example", "profile-a")
        val before = ApiContext(oldAccount.server, oldAccount)
        var context = before
        val access = InstanceAccess({ context }, Memory())
        val renewed = oldAccount.copy(token = "renewed")
        access.prepareLogin(before, renewed)
        assertFalse(access.retainPlayback(before, before.copy(account = renewed)))
        access.report(before, AccessReason.LOGIN); access.prepareLogin(before, renewed)
        context = before.copy(account = renewed, generation = 1); access.contextChanged()
        assertTrue(access.retainPlayback(before, context)); assertFalse(access.retainPlayback(before, context))
        access.report(context, AccessReason.LOGIN); access.prepareLogin(context, renewed.copy(username = "Bob", profileId = "profile-b"))
        assertFalse(access.retainPlayback(context, context.copy(account = renewed.copy(username = "Bob", profileId = "profile-b"))))
    }
    @Test fun queuedImageContextMarkersRejectNewAccountsBeforeSendingAndNeverReachTheWire() {
        MockWebServer().use { server ->
            var context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Memory())
            val client = access.client()
            val marker = InstanceAccess.cacheScope(context)
            context = context.copy(generation = 1)
            val stale = Request.Builder().url(server.url("/image")).header(InstanceAccess.CONTEXT_HEADER, marker).build()
            try { client.newCall(stale).execute().close(); fail("Stale image sent") } catch (_: java.io.IOException) { }
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("image"))
            client.newCall(stale.newBuilder().header(InstanceAccess.CONTEXT_HEADER, InstanceAccess.cacheScope(context)).build()).execute().close()
            assertNull(server.takeRequest().getHeader(InstanceAccess.CONTEXT_HEADER))
        }
    }
}
