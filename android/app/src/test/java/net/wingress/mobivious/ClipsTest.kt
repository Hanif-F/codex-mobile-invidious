package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ClipsTest {
    private val id = "IVCL" + "a".repeat(32)
    private val server = "https://instance.test"
    private val source = JSONObject().put("videoId", "abcdefghijk").put("title", "Full source")
        .put("author", "Channel").put("authorId", "UC" + "b".repeat(22)).put("lengthSeconds", 120).put("thumbnail", "/vi/abcdefghijk/mqdefault.jpg")
    private fun metadata(native: Boolean = true) = JSONObject().put("clipTitle", "A moment 😀").put("startTime", 12.345)
        .put("endTime", 42.789).put("video", source).apply { if (native) put("type", "invidiousClip").put("clipId", id).put("creator", "Alice").put("createdAt", 1791302400) }
    @Test fun nativeAndLegacyKeepPreciseBoundsAndSafeIdentity() {
        val clip = Clip.parse(metadata(), id, server)
        assertEquals(12345L, clip.startMs); assertEquals(30444L, clip.durationMs)
        assertEquals("$server/clip/$id", clip.permalink); assertTrue(clip.native)
        assertEquals(clip, Clip.parse(clip.json(), id, server))
        assertTrue(clip.owned(ApiContext(server, Account("token", "Alice", Long.MAX_VALUE, server))))
        assertFalse(clip.owned(ApiContext(server, Account("token", "Bob", Long.MAX_VALUE, server))))
        assertFalse(clip.owned(ApiContext("https://other.test", Account("token", "Alice", Long.MAX_VALUE, "https://other.test"))))
        val legacy = Clip.parse(metadata(false), "legacy-id", server)
        assertFalse(legacy.native); assertEquals("https://www.youtube.com/clip/legacy-id", legacy.permalink)
        assertEquals("", legacy.creator); assertNull(legacy.createdAt)
    }
    @Test fun missingAndInvalidBoundsNeverBecomeFullVideoPlayback() {
        for (j in listOf(metadata().put("startTime", JSONObject.NULL), metadata().put("endTime", "42"), metadata().put("endTime", 12),
            metadata().put("startTime", -1), metadata().put("endTime", 1000), metadata().put("startTime", 1e100))) {
            try { Clip.parse(j, id, server); fail("Invalid clip accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun sourceAvatarSurvivesClipPlaybackHandoffAndRestoration() {
        val metadata = metadata().apply { getJSONObject("video").put("authorThumbnails",
            JSONArray().put(JSONObject().put("url", "/ggpht/channel=s88"))) }
        val clip = Clip.parse(metadata, id, server)
        assertEquals("/ggpht/channel=s88", clip.video.authorAvatar)
        assertEquals(clip, Clip.parse(clip.json(), id, server))
        assertEquals("/ggpht/channel=s88", clip.json().getJSONObject("video").getString("authorThumbnail"))
    }
    @Test fun unicodeTitlesAndLimitsMatchTheServer() {
        assertEquals(140, ClipRules.titleCount("😀".repeat(140)))
        assertNull(ClipRules.error("😀".repeat(140), 0, 5000, 5000))
        assertNotNull(ClipRules.error("😀".repeat(141), 0, 5000, 5000))
        assertNotNull(ClipRules.error(" \n ", 0, 5000, 120000))
        assertNull(ClipRules.error("OK", 0, 120000, 120000))
        assertNotNull(ClipRules.error("OK", 0, 4999, 120000))
        assertNotNull(ClipRules.error("OK", 0, 120001, 120001))
        assertNotNull(ClipRules.error("OK", 10000, 20000, 19999))
        assertNotNull(ClipRules.error("OK", Long.MAX_VALUE - 5000, -1, Long.MAX_VALUE))
    }
    @Test fun tenthSecondFieldsHandleLongArchivesAndRejectMalformedValues() {
        assertEquals(62300L, ClipRules.parseTimestamp("1:02.3"))
        assertEquals(3662300L, ClipRules.parseTimestamp("1:01:02.3"))
        assertEquals("1:01:02.3", ClipRules.timestamp(3662345))
        assertEquals("0:59.9", ClipRules.timestamp(59999))
        for (value in listOf("1:60.0", "1:99:00.0", "-1:02.0", "0:01.23", "1:02:03:04", "9e8:00", "0:NaN")) assertNull(ClipRules.parseTimestamp(value))
        assertEquals(0L..5000L, ClipRules.defaultRange(0, 5000))
        assertEquals(90000L..120000L, ClipRules.defaultRange(119950, 120000))
        assertEquals(47300L..77300L, ClipRules.defaultRange(62345, 120000))
    }
    @Test fun clipAndChannelLinksRetainOriginAndDoNotTreatClipsAsVideoLinks() {
        val link = ContentLinks.parse("$server/clip/$id", server) as ContentLink.Clip
        assertEquals(server, link.link.server); assertNull(VideoLinks.parse(link.link.url, server))
        assertEquals(server, (ContentLinks.parse("https://www.youtube.com/clip/legacy-id", server) as ContentLink.Clip).link.server)
        assertEquals("https://other.test", (ContentLinks.parse("https://other.test/clip/$id", server) as ContentLink.Clip).link.server)
        assertEquals(ChannelTab.CLIPS, (ContentLinks.parse("$server/@creator/clips", server) as ContentLink.Channel).link.tab)
        assertNull(ContentLinks.parse("https://secret:password@instance.test/clip/$id", server))
    }
    @Test fun storyboardCuesResolveSpritesAndIgnoreMalformedBlocks() {
        val frames = StoryboardVtt.parse("WEBVTT\n\n00:00:00.000 --> 00:00:20.000\n/sb/a.jpg#xywh=160,0,160,90\n\nbad\n\n00:00:20.000 --> 00:00:40.000\n/sb/a.jpg#xywh=320,0,160,90\n", "$server/api/v1/storyboards/abcdefghijk")
        assertEquals(2, frames.size); assertEquals("$server/sb/a.jpg", frames[0].url)
        assertEquals(160, frames[0].x); assertEquals(20000L, frames[1].startMs)
    }
    @Test fun apiListsCreatesAndDeletesWithExactFieldsAndScopes() = runBlocking {
        MockWebServer().use { mock ->
            val address = mock.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) })
            val context = api.context()
            mock.enqueue(MockResponse().setBody(JSONArray().put(metadata()).toString()))
            assertEquals(1, api.clips(page = 2, context = context).size)
            val own = mock.takeRequest(); assertEquals("/api/v1/auth/clips?page=2", own.path); assertEquals("Bearer token", own.getHeader("Authorization"))
            mock.enqueue(MockResponse().setBody(JSONArray().put(metadata()).toString()))
            api.clips("UC" + "b".repeat(22), 1, context)
            assertNull(mock.takeRequest().getHeader("Authorization"))
            mock.enqueue(MockResponse().setResponseCode(201).setBody(metadata().toString()))
            api.createClip("abcdefghijk", " 😀 ", 12345, 42789, context)
            val create = mock.takeRequest(); val body = JSONObject(create.body.readUtf8())
            assertEquals("😀", body.getString("title")); assertEquals(12.345, body.getDouble("startTime"), 0.000001)
            assertEquals(42.789, body.getDouble("endTime"), 0.000001)
            mock.enqueue(MockResponse().setResponseCode(204)); api.deleteClip(id, context)
            assertEquals("DELETE", mock.takeRequest().method)
        }
    }
    @Test fun missingScopeExplainsTokenRenewalAndStaleContextsSendNothing() = runBlocking {
        MockWebServer().use { mock ->
            val address = mock.url("/").toString().trimEnd('/')
            var account = Account("token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            mock.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Invalid scope"}"""))
            try { api.clips(); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains("sign in again")) }
            mock.takeRequest()
            val old = api.context(); account = account.copy(username = "Bob")
            try { api.createClip("abcdefghijk", "Title", 0, 5000, old); fail() } catch (_: CancellationException) { }
            assertNull(mock.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun storyboardReadsArePublicAndLimitedToTheInstance() = runBlocking {
        MockWebServer().use { mock ->
            val address = mock.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) })
            mock.enqueue(MockResponse().setBody("WEBVTT\n\n00:00:00.000 --> 00:00:20.000\n/sb/a.jpg#xywh=0,0,160,90\n"))
            assertEquals(1, api.storyboard(StoryboardTrack("/api/v1/storyboards/abcdefghijk?width=160", 160, 90), api.context()).size)
            val request = mock.takeRequest(); assertNull(request.getHeader("Authorization")); assertEquals("text/vtt", request.getHeader("Accept"))
            try { api.storyboard(StoryboardTrack("https://foreign.test/assets", 160, 90), api.context()); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun creationRejectsInvalidDraftsBeforeSendingAndDropsLateAccountResponses() = runBlocking {
        MockWebServer().use { mock ->
            val address = mock.url("/").toString().trimEnd('/')
            var account = Account("token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            for ((title, end) in listOf("" to 5000L, "😀".repeat(141) to 5000L, "OK" to 4999L, "OK" to 120001L)) {
                try { api.createClip("abcdefghijk", title, 0, end, api.context()); fail() } catch (_: IllegalArgumentException) { }
            }
            assertEquals(0, mock.requestCount)
            mock.enqueue(MockResponse().setBody(metadata().toString()).setBodyDelay(500, TimeUnit.MILLISECONDS))
            val old = api.context()
            val request = async { api.clip(id, old) }
            withContext(Dispatchers.IO) { mock.takeRequest() }
            account = account.copy(username = "Bob")
            try { request.await(); fail() } catch (_: CancellationException) { }
        }
    }
}
