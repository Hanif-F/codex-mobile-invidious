package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccountApiTest {
    private val sessionJson = """{"accessToken":"new-token","username":"Renamed","expiresAt":9999999999}"""
    @Test fun registrationChallengeAndSignupUseCapturedInstanceWithoutBearer() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { null }); val context = api.context()
            server.enqueue(MockResponse().setBody("""{"loginEnabled":true,"registrationEnabled":true,"captcha":{"image":"data:image/png;base64,test","token":"single-use"}}"""))
            val registration = api.registration(context)
            assertTrue(registration.enabled); assertEquals("single-use", registration.captchaToken)
            assertNull(server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody(sessionJson))
            val account = api.register("Renamed", "a long uncommon password", "a long uncommon password", "1:05:10", registration.captchaToken, context)
            assertEquals(address, account.server); assertEquals("new-token", account.token)
            val request = server.takeRequest(); assertEquals("/api/v1/mobile/register", request.path)
            assertNull(request.getHeader("Authorization")); assertFalse(request.path!!.contains("password"))
            val body = JSONObject(request.body.readUtf8()); assertEquals("single-use", body.getString("captchaToken")); assertEquals("1:05:10", body.getString("captchaAnswer"))
        }
    }
    @Test fun wrongCurrentPasswordDoesNotExpireTheBearerButRevocationDoes() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val account = Account("old-token", "Alice", Long.MAX_VALUE, address); var expirations = 0
            val api = InvidiousApi({ address }, { account }, { expirations++ })
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Incorrect current password.","code":"invalid_password"}"""))
            try { api.changeCredentials("username", JSONObject().put("username", "Renamed").put("password", "wrong"), api.context()); fail("Wrong password accepted") }
            catch (e: ApiException) { assertEquals(401, e.status) }
            assertEquals(0, expirations); assertEquals("Bearer old-token", server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Request must be authenticated"}"""))
            try { api.accountSessions(api.context()); fail("Revoked token accepted") } catch (_: ApiException) { }
            assertEquals(1, expirations)
        }
    }
    @Test fun credentialsSessionsAndTokensRetainTheirContracts() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("old-token", "Alice", Long.MAX_VALUE, address) }); val context = api.context()
            server.enqueue(MockResponse().setBody(sessionJson))
            assertEquals("Renamed", api.changeCredentials("username", JSONObject().put("username", "Renamed").put("password", "secret"), context).username)
            assertEquals("/api/v1/auth/account/username", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""[{"id":"opaque","type":"browser","issuedAt":100,"expiresAt":null,"current":false}]"""))
            val session = api.accountSessions(context).single(); assertNull(session.expiresAt); assertFalse(session.current); server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(204)); api.revokeSession(session.id, context)
            assertEquals("opaque", JSONObject(server.takeRequest().body.readUtf8()).getString("id"))
            server.enqueue(MockResponse().setBody("""{"accessToken":"external-secret"}"""))
            assertEquals("external-secret", api.createToken("secret", listOf("GET:preferences"), null, context))
            val tokenRequest = server.takeRequest(); assertEquals("/api/v1/auth/account/tokens", tokenRequest.path)
            val fields = JSONObject(tokenRequest.body.readUtf8()); assertTrue(fields.isNull("expiresAt")); assertEquals(1, fields.getJSONArray("scopes").length())
            server.enqueue(MockResponse().setResponseCode(204)); api.deleteAccount("secret", context)
            assertEquals("POST", server.takeRequest().method)
        }
    }
    @Test fun oldServersAndOldScopesExplainTheRequiredUpdate() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) })
            for (code in listOf(404, 403)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("""{"error":"Invalid scope"}"""))
                try { api.accountSessions(api.context()); fail("Missing support accepted") }
                catch (e: ApiException) { assertTrue(e.message!!.contains(if (code == 404) "Update this server" else "Sign out and sign in")) }
            }
        }
    }
    @Test fun lateLoginCannotBindItsTokenToAnotherInstance() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/'); var current = address
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { entered.countDown(); release.await(5, TimeUnit.SECONDS); return MockResponse().setBody(sessionJson) }
            }
            val api = InvidiousApi({ current }, { null })
            val result = async(Dispatchers.Default) { api.login("Alice", "password") }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); current = "https://another.example"; release.countDown()
            try { result.await(); fail("Late login was accepted") } catch (_: CancellationException) { }
        }
    }
    @Test fun guidedAndAdvancedPermissionsRequireAnExplicitValidSelection() {
        assertFalse(AccountPermissions.valid(AccountPermissions.scopes(emptySet(), "")))
        assertEquals(listOf("GET:preferences", "GET:clips"), AccountPermissions.scopes(setOf("Read preferences"), "GET:clips, GET:preferences"))
        assertTrue(AccountPermissions.valid(listOf(":*", "GET;POST:playlists/*")))
        assertFalse(AccountPermissions.valid(listOf("GET:preferences\n:*")))
        assertFalse(AccountPermissions.valid(listOf("GET:/invalid?scope")))
        assertEquals(30L, AccountPermissions.expiries["30 days"])
    }
    @Test fun staleAccountOperationsNeverSendOrAcceptCredentialsForAnotherAccount() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            var current = Account("old-token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { current }); val old = api.context()
            current = current.copy(token = "replacement-token", username = "Bob")
            try { api.deleteAccount("secret", old); fail("Stale deletion was sent") } catch (_: CancellationException) { }
            assertEquals(0, server.requestCount)
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    entered.countDown(); release.await(5, TimeUnit.SECONDS)
                    return MockResponse().setBody("""{"accessToken":"one-time-secret"}""")
                }
            }
            val result = async(Dispatchers.Default) { api.createToken("secret", listOf("GET:preferences"), null, api.context()) }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); current = current.copy(token = "third-token"); release.countDown()
            try { result.await(); fail("Stale token response was accepted") } catch (_: CancellationException) { }
        }
    }
}
