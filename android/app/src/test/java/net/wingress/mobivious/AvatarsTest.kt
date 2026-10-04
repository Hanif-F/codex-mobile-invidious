package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AvatarsTest {
    private val instance = "https://instance.test"

    @Test fun parsesModernLegacyAndMissingAvatarsAcrossModels() {
        val json = JSONObject("""{"videoId":"abcdefghijk","playlistId":"PLtest","authorId":"UCtest","authorThumbnail":"/ggpht/direct=s48","authorThumbnails":[{"url":"/ggpht/array=s512"}]}""")
        assertEquals("/ggpht/direct=s48", ApiParser.video(json).authorAvatar)
        assertEquals("/ggpht/direct=s48", ApiParser.playlist(json).authorAvatar)
        assertEquals("/ggpht/direct=s48", ApiParser.channel(json).image)
        assertEquals("/ggpht/direct=s48", CommentPage.parse(JSONObject().put("comments", org.json.JSONArray().put(json))).items.single().avatar)
        json.put("authorThumbnail", JSONObject.NULL)
        assertEquals("/ggpht/array=s512", Avatars.parse(json))
        json.put("authorThumbnail", 42).put("authorThumbnails", org.json.JSONArray("""[{"url":"/ggpht/valid=s88"},{"url":42},null]"""))
        assertEquals("/ggpht/valid=s88", Avatars.parse(json))
        json.put("authorThumbnails", org.json.JSONArray("""[null,{},42,{"url":""}]"""))
        assertEquals("", Avatars.parse(json))
        assertEquals("", ApiParser.video(JSONObject()).authorAvatar)
    }

    @Test fun normalizesAllSupportedSourcesToOneInstanceImage() {
        listOf("https://yt3.ggpht.com/avatar=s48-c-k", "//yt3.googleusercontent.com/avatar=s512-c-k",
            "/ggpht/avatar=s88-c-k", "$instance/ggpht/avatar=s100-c-k").forEach {
            assertEquals("$instance/ggpht/avatar=s176-c-k", Avatars.url(instance, it))
        }
        assertEquals("$instance/ggpht/s176-c-k/avatar", Avatars.url(instance, "https://yt3.ggpht.com/s48-c-k/avatar"))
    }

    @Test fun preservesQueryAndEncodedPathWithoutLeakingFragments() {
        assertEquals("$instance/ggpht/avatar%2Bname=s176?key=a%2Bb&size=88",
            Avatars.url(instance, "https://yt3.ggpht.com/avatar%2Bname=s48?key=a%2Bb&size=88#private"))
    }

    @Test fun refusesUnrelatedAndMalformedSources() {
        listOf("", " ", "/", "/media/image.png", "https://yt3.ggpht.com/", "/ggpht/",
            "javascript:alert(1)", "data:image/png;base64,a", "https://other.test/ggpht/avatar",
            "https://yt3.ggpht.com.evil.test/avatar", "https://user:secret@yt3.ggpht.com/avatar",
            "https://yt3.ggpht.com:1234/avatar", "https://yt3.ggpht.com/a\\b", "/ggpht/../watch",
            "http://instance.test/ggpht/avatar", "https://instance.test:1234/ggpht/avatar").forEach { assertNull(it, Avatars.url(instance, it)) }
    }

    @Test fun instanceChangesProduceDistinctCacheKeys() {
        val image = "https://yt3.ggpht.com/avatar=s88"
        assertNotEquals(Avatars.url(instance, image), Avatars.url("https://second.test", image))
        assertNull(Avatars.url("https://second.test", "$instance/ggpht/avatar=s88"))
        assertEquals("http://127.0.0.1:18080/ggpht/avatar=s176", Avatars.url("http://127.0.0.1:18080", "/ggpht/avatar=s48"))
    }

    @Test fun initialsPreserveUnicodeAndUseIconForNonletters() {
        mapOf("  studio" to "S", "éclair" to "É", "e\u0301cole" to "É", "山田" to "山", "ßeta" to "ß").forEach { (name, initial) -> assertEquals(initial, Avatars.initial(name)) }
        listOf("", "  ", "123", "😊 Studio", ".Studio").forEach { assertNull(Avatars.initial(it)) }
    }

    @Test fun thinModeAndOwnerSuppressionApplyWithoutHidingOtherCreators() {
        assertFalse(Avatars.show(true, "UCtest"))
        assertFalse(Avatars.show(false, ""))
        assertFalse(Avatars.show(false, "UCtest", "UCtest"))
        assertTrue(Avatars.show(false, "UCother", "UCtest"))
    }

    @Test fun detailedHistoryRetainsAvatarAlongsideSavedMetadata() {
        val history = History.parse("""{"entries":[{"video_id":"abcdefghijk","channel_id":"UCtest","channel_name":"Studio","title":"Saved","authorThumbnails":[{"url":"/ggpht/history=s88"}]}],"today":"2026-10-04","timezone":"UTC","total":1,"hasMore":false}""")
        assertEquals("/ggpht/history=s88", history.entries.single().authorAvatar)
        assertEquals("Studio", history.entries.single().author)
    }

    @Test fun existingApiResponsesNeedNoAvatarMetadataRequests() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { null })
            server.enqueue(MockResponse().setBody("""[{"videoId":"abcdefghijk","title":"Video","authorThumbnails":[{"url":"https://yt3.ggpht.com/avatar=s48"}]}]"""))
            val item = api.discovery("popular").single()
            assertEquals("$address/ggpht/avatar=s176", Avatars.url(address, item.authorAvatar))
            assertEquals("/api/v1/popular", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun imageClientNeverFollowsRedirectsOrSendsCredentials() {
        MockWebServer().use { origin -> MockWebServer().use { other ->
            origin.start(); other.start()
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/upstream")))
            Avatars.client().newCall(Request.Builder().url(origin.url("/ggpht/avatar")).build()).execute().use { assertEquals(302, it.code) }
            val request = origin.takeRequest()
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            assertNull(other.takeRequest(100, TimeUnit.MILLISECONDS))
        } }
    }
}
