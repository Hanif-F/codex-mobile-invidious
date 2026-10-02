package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackRules
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CoreTest {
    @Test fun offlineSnapshotsExpireAndNeverCrossAccountKeys() {
        val directory = Files.createTempDirectory("mobivious-cache").toFile()
        try {
            var now = System.currentTimeMillis()
            val cache = ResponseCache(directory) { now }
            cache.write("server|Alice", "saved-feed")
            assertEquals("saved-feed", cache.read("server|Alice"))
            assertNull(cache.read("server|Bob"))
            now += 25 * 60 * 60 * 1000L
            assertNull(cache.read("server|Alice"))
            cache.clear(); assertEquals(0, directory.listFiles()!!.size)
        } finally { directory.deleteRecursively() }
    }
    @Test fun linksAndTimestamps() {
        assertEquals(VideoLink("abcdefghijk", 3723), VideoLinks.parse("Watch https://youtu.be/abcdefghijk?t=1h2m3s", "https://instance.test"))
        assertEquals(VideoLink("abcdefghijk", 0), VideoLinks.parse("https://instance.test/watch?v=abcdefghijk&t=0", "https://instance.test"))
        assertEquals(VideoLink("abcdefghijk"), VideoLinks.parse("https://www.youtube.com/shorts/abcdefghijk", "https://instance.test"))
        assertNull(VideoLinks.parse("https://youtube.com.evil.test/watch?v=abcdefghijk", "https://instance.test"))
        assertNull(VideoLinks.parse("https://instance.test/watch?v=../../unsafe", "https://instance.test"))
        assertNull(VideoLinks.timestampSeconds("999999999999999999999h"))
    }
    @Test fun explicitTimeOverridesResumeAndFinishedVideosRestart() {
        assertEquals(50, PlaybackRules.resume(50, 100, null))
        assertEquals(0, PlaybackRules.resume(90, 100, null))
        assertEquals(0, PlaybackRules.resume(50, 100, 0))
        assertEquals(25, PlaybackRules.resume(50, 100, 25))
        assertEquals(0, PlaybackRules.save(90, 100, false))
        assertEquals(50, PlaybackRules.save(50, 100, false))
        assertEquals(0, PlaybackRules.save(50, 100, true))
    }
    @Test fun parsersKeepUnavailableHistoryAndCaptionVariants() {
        val videos = ApiParser.videos(JSONArray("""[{"video_id":"abcdefghijk","title":null},{"video_id":"bbbbbbbbbbb","title":"Saved","length_seconds":123}]"""))
        assertEquals(2, videos.size); assertTrue(videos[0].unavailable); assertEquals(123, videos[1].duration)
        val details = ApiParser.details(JSONObject("""{"videoId":"abcdefghijk","title":"Video","captions":[{"label":"English","language_code":"en","url":"/captions"}],"recommendedVideos":[]}"""))
        assertEquals("en", details.captions.first().language)
    }
    @Test fun rejectInsecureAndCredentialBearingInstances() {
        assertEquals("https://instance.test", InvidiousApi.normalizeServer("https://instance.test/", false))
        listOf("http://instance.test", "https://user:secret@instance.test", "https://instance.test/path").forEach { value -> assertThrows(IllegalArgumentException::class.java) { InvidiousApi.normalizeServer(value, false) } }
    }
    @Test fun nativeSignInAndBearerOnlyForAuthenticatedRequests() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val account = Account("signed-token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            server.enqueue(MockResponse().setBody("[]")); api.discovery("popular")
            assertNull(server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("{}")); api.preferences()
            assertEquals("Bearer signed-token", server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"accessToken":"new-token","username":"Alice","expiresAt":9999999999}"""))
            assertEquals("new-token", api.login("Alice", "transient-password").token)
            val login = server.takeRequest(); assertNull(login.getHeader("Authorization")); assertTrue(login.body.readUtf8().contains("transient-password"))
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "900").setBody("{}"))
            try { api.login("Alice", "wrong"); fail("Throttle ignored") } catch(e: ApiException) { assertEquals("900", e.retryAfter) }
        }
    }
    @Test fun apiDoesNotForwardCredentialsOnRedirectAndInvalidatesExpiredSessions() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { other ->
            first.start(); other.start(); val address = first.url("/").toString().trimEnd('/')
            var expired = false
            val api = InvidiousApi({ address }, { Account("private-token", "Alice", Long.MAX_VALUE, address) }, { expired = true })
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/stolen")))
            try { api.preferences(); fail("Redirect followed") } catch(e: ApiException) { assertEquals(302, e.status) }
            assertEquals(0, other.requestCount)
            first.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Request must be authenticated"}"""))
            try { api.preferences() } catch(_: ApiException) { }
            assertTrue(expired)
        } }
    }
}
