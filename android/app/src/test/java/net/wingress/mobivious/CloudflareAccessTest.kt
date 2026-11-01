package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareAccessTest {
    private class Passes : VerificationPersistence {
        var value: VerificationPass? = null
        override fun read(origin: String) = value
        override fun write(origin: String, pass: VerificationPass?) { value = pass }
    }
    private class Cookies : CloudflarePersistence {
        val values = mutableMapOf<String, CloudflareClearance>()
        override fun read(origin: String) = values[origin]
        override fun write(origin: String, clearance: CloudflareClearance?) {
            if (clearance == null) values.remove(origin) else values[origin] = clearance
        }
    }
    private val agent = "Fixture WebView"
    private fun cookie(url: HttpUrl, value: String = "candidate", persistent: Boolean = true) =
        CloudflareClearance.parse(url, "cf_clearance=$value; Path=/; HttpOnly" + if (persistent) "; Expires=Wed, 01 Jan 2031 00:00:00 GMT" else "", agent)!!
    private fun disabled() = MockResponse().setBody("""{"enabled":false,"required":false,"verified":false,"expiresAt":null}""")
    private fun challenge(code: Int = 403) = MockResponse().setResponseCode(code).setHeader("cf-mitigated", "challenge").setBody("<html>challenge</html>")

    @Test fun challengeHeaderPrecedesStatusJsonAndAccountExpiry() = runBlocking {
        assertEquals(AccessReason.CLOUDFLARE, InstanceAccess.classify(200, null, "{}", "challenge"))
        assertNull(InstanceAccess.classify(403, null, "<html>ordinary error</html>"))
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val context = ApiContext(address, Account("account", "A", Long.MAX_VALUE, address))
            var expired = false
            val access = InstanceAccess({ context }, Passes())
            val api = InvidiousApi({ address }, { context.account }, { expired = true }, access = access)
            for (code in listOf(200, 401, 403, 503)) {
                server.enqueue(challenge(code).setBody("x".repeat(20_000)))
                val error = runCatching { api.request("api/v1/popular") }.exceptionOrNull()
                assertEquals(AccessReason.CLOUDFLARE, (error as InstanceAccessException).reason)
            }
            assertFalse(expired)
            access.report(context, AccessReason.LOGIN)
            assertEquals(AccessReason.CLOUDFLARE, access.issue.value?.reason)
        }
    }

    @Test fun clearanceUsesActualExpiryAgentOriginAndBootstrapIsolation() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            var context = ApiContext(address, Account("account", "A", Long.MAX_VALUE, address))
            val cookies = Cookies(); val passes = Passes().apply { value = VerificationPass("pass", Long.MAX_VALUE) }
            var currentAgent = agent; var now = 10L
            val access = InstanceAccess({ context }, passes, cookies, { currentAgent }) { now }
            server.enqueue(disabled()); assertTrue(access.completeClearance(context, cookie(server.url("/"))))
            val bootstrap = access.headers(server.url("/api/v1/mobile/login"), context)
            assertEquals(mapOf("Cookie" to "cf_clearance=candidate", "User-Agent" to agent), bootstrap)
            val probe = server.takeRequest()
            assertEquals("pass", probe.getHeader(InstanceAccess.HEADER)); assertNull(probe.getHeader("Authorization"))
            assertEquals("cf_clearance=candidate", probe.getHeader("Cookie")); assertEquals(agent, probe.getHeader("User-Agent"))
            val restored = InstanceAccess({ context }, passes, cookies, { currentAgent }) { now }
            assertNotNull(restored.clearance(context))
            currentAgent = "Updated WebView"; assertNull(restored.clearance(context)); currentAgent = agent
            now = 2_000_000_000; assertNull(restored.clearance(context)); now = 10
            assertTrue(access.headers("https://other.example/media".toHttpUrl(), context).isEmpty())
            assertTrue(access.headers(server.url("/").newBuilder().port(server.port + 1).build(), context).isEmpty())
            val old = context; context = context.copy(generation = 2)
            assertTrue(access.headers(server.url("/media"), old).isEmpty())
        }
    }

    @Test fun sessionCookiesAreNotPersistedAcrossInstances() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val cookies = Cookies(); val passes = Passes()
            val access = InstanceAccess({ context }, passes, cookies, { agent })
            server.enqueue(disabled()); access.completeClearance(context, cookie(server.url("/"), persistent = false))
            assertNotNull(access.clearance(context)); assertTrue(cookies.values.isEmpty())
            assertNull(InstanceAccess({ context }, passes, cookies, { agent }).clearance(context))
        }
    }

    @Test fun rejectedNativeProbeDoesNotSaveCookieOrPublishRecovery() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val cookies = Cookies(); val access = InstanceAccess({ context }, Passes(), cookies, { agent })
            access.report(context, AccessReason.CLOUDFLARE)
            server.enqueue(challenge()); assertTrue(runCatching { access.completeClearance(context, cookie(server.url("/"))) }.isFailure)
            assertTrue(cookies.values.isEmpty()); assertEquals(0L, access.revision.value)
            assertEquals(AccessReason.CLOUDFLARE, access.issue.value?.reason)
            server.enqueue(MockResponse().setResponseCode(503)); assertTrue(runCatching { access.completeClearance(context, cookie(server.url("/"))) }.isFailure)
            server.enqueue(MockResponse().setBody("not json")); assertTrue(runCatching { access.completeClearance(context, cookie(server.url("/"))) }.isFailure)
            assertTrue(cookies.values.isEmpty())
        }
    }

    @Test fun edgeClearanceAndBackendPassMustBothBeAccepted() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val passes = Passes(); val access = InstanceAccess({ context }, passes, Cookies(), { agent }) { 10 }
            access.report(context, AccessReason.CLOUDFLARE)
            server.enqueue(MockResponse().setBody("""{"enabled":true,"required":true,"verified":false,"expiresAt":null}"""))
            assertFalse(access.completeClearance(context, cookie(server.url("/"))))
            assertNotNull(access.clearance(context)); assertNull(passes.value); assertNotNull(access.issue.value)
            server.enqueue(MockResponse().setBody("""{"enabled":true,"required":false,"verified":true,"expiresAt":100}"""))
            access.complete(context, "signed-pass", 100, true)
            assertNull(access.issue.value); assertEquals("signed-pass", passes.value?.token)
            server.takeRequest(); val request = server.takeRequest()
            assertEquals("signed-pass", request.getHeader(InstanceAccess.HEADER)); assertEquals("cf_clearance=candidate", request.getHeader("Cookie"))
        }
    }

    @Test fun edgeRecoveryPreservesUnsupportedDiscoveryButNotOutages() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Passes(), Cookies(), { agent })
            server.enqueue(MockResponse().setResponseCode(404)); assertTrue(access.completeClearance(context, cookie(server.url("/"))))
            server.enqueue(challenge()); assertEquals(AccessReason.CLOUDFLARE, (runCatching { access.discover(context) }.exceptionOrNull() as InstanceAccessException).reason)
        }
    }

    @Test fun redirectsStripClearanceAndAgentButPreserveRange() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { external ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Passes(), Cookies(), { agent })
            server.enqueue(disabled()); access.completeClearance(context, cookie(server.url("/"))); server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/same")))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", external.url("/media")))
            external.enqueue(MockResponse().setBody("media"))
            access.client(OkHttpClient()).newCall(Request.Builder().url(server.url("/media")).header("Range", "bytes=10-").header("Cookie", "account=never").build()).execute().close()
            repeat(2) { assertEquals("cf_clearance=candidate", server.takeRequest().getHeader("Cookie")) }
            val request = external.takeRequest(); assertNull(request.getHeader("Cookie")); assertNotEquals(agent, request.getHeader("User-Agent")); assertEquals("bytes=10-", request.getHeader("Range"))
        } }
    }

    @Test fun rotationAndDeletionOnlyAcceptClearanceFromCurrentOrigin() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Passes(), Cookies(), { agent })
            server.enqueue(disabled()); access.completeClearance(context, cookie(server.url("/")))
            server.enqueue(MockResponse().addHeader("Set-Cookie", "account=never; Path=/").addHeader("Set-Cookie", cookie(server.url("/"), "rotated").cookie.toString()))
            access.client().newCall(Request.Builder().url(server.url("/media")).build()).execute().close()
            assertEquals("rotated", access.clearance(context)?.cookie?.value)
            server.enqueue(MockResponse().addHeader("Set-Cookie", "cf_clearance=unrelated; Path=/different"))
            access.client().newCall(Request.Builder().url(server.url("/media")).build()).execute().close()
            assertEquals("rotated", access.clearance(context)?.cookie?.value)
            server.enqueue(MockResponse().addHeader("Set-Cookie", "cf_clearance=; Max-Age=0; Path=/"))
            access.client().newCall(Request.Builder().url(server.url("/media")).build()).execute().close()
            assertNull(access.clearance(context))
        }
    }

    @Test fun lateChallengeReplaysOnlySafeReadsOnceAndCannotRejectNewClearance() = runBlocking {
        for (method in listOf("GET", "POST")) MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Passes(), Cookies(), { agent })
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/api/v1/verification" -> disabled()
                    else -> if (request.getHeader("Cookie") == null) { entered.countDown(); release.await(5, TimeUnit.SECONDS); challenge() } else MockResponse().setBody("accepted")
                }
            }
            val pending = async(Dispatchers.IO) { runCatching { access.client().newCall(Request.Builder().url(server.url("/media")).method(method, if (method == "POST") "{}".toRequestBody() else null).build()).execute().use { it.body!!.string() } } }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); access.completeClearance(context, cookie(server.url("/"))); release.countDown()
            assertEquals(method == "GET", pending.await().isSuccess); assertNotNull(access.clearance(context)); assertNull(access.issue.value)
            assertEquals(if (method == "GET") 3 else 2, server.requestCount)
        }
    }

    @Test fun cancelledAndStaleProbesCannotPersistClearance() = runBlocking {
        MockWebServer().use { server ->
            var context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val cookies = Cookies(); val access = InstanceAccess({ context }, Passes(), cookies, { agent })
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { entered.countDown(); release.await(5, TimeUnit.SECONDS); return disabled() } }
            val pending = async(Dispatchers.IO) { runCatching { access.completeClearance(context, cookie(server.url("/"))) } }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); context = context.copy(generation = 1); access.contextChanged(); release.countDown()
            assertTrue(pending.await().isFailure); assertTrue(cookies.values.isEmpty()); assertNull(access.issue.value)
        }
    }
    @Test fun successfulNativeProbePersistsItsRotatedCookie() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val access = InstanceAccess({ context }, Passes(), Cookies(), { agent })
            server.enqueue(disabled().addHeader("Set-Cookie", cookie(server.url("/"), "probe-rotation").cookie.toString()))
            assertTrue(access.completeClearance(context, cookie(server.url("/"))))
            assertEquals("probe-rotation", access.clearance(context)?.cookie?.value)
            server.enqueue(disabled().addHeader("Set-Cookie", "cf_clearance=; Path=/; Max-Age=0"))
            assertNotNull(access.discover(context)); assertNull(access.clearance(context))
        }
    }

    @Test fun cookieExpiringDuringProbeCannotPublishRecovery() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            var now = 10L
            val cookies = Cookies(); val access = InstanceAccess({ context }, Passes(), cookies, { agent }) { now }
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { now = 2_000_000_000; return disabled() }
            }
            access.report(context, AccessReason.CLOUDFLARE)
            assertTrue(runCatching { access.completeClearance(context, cookie(server.url("/"))) }.isFailure)
            assertTrue(cookies.values.isEmpty()); assertNotNull(access.issue.value)
        }
    }

    @Test fun cancellationClosesProbeWithoutSavingItsCandidate() = runBlocking {
        MockWebServer().use { server ->
            val context = ApiContext(server.url("/").toString().trimEnd('/'), null)
            val cookies = Cookies(); val access = InstanceAccess({ context }, Passes(), cookies, { agent })
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { entered.countDown(); release.await(5, TimeUnit.SECONDS); return disabled() }
            }
            val pending = launch(Dispatchers.IO) { access.completeClearance(context, cookie(server.url("/"))) }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); pending.cancelAndJoin(); release.countDown()
            assertTrue(cookies.values.isEmpty()); assertEquals(0L, access.revision.value)
        }
    }

}
