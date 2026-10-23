package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class VideoViewCountsTest {
    private fun video(fields: String) = ApiParser.video(JSONObject("""{"videoId":"testvideo01","title":"Video",$fields}"""))
    private fun label(fields: String, exact: Boolean = false) = VideoViewCounts.label(video(fields), exact, Locale.US)

    @Test fun knownCountsKeepZeroPrecisionAndExplicitUnits() {
        assertEquals("0 views", label(""""viewCount":0,"viewCountPrecision":"exact","viewCountType":"views""""))
        assertEquals("1 view", label(""""viewCount":1"""))
        assertEquals("1.2K watching", label(""""viewCount":1200,"viewCountPrecision":"approximate","viewCountType":"watching","liveNow":true"""))
        assertEquals("1,234 watching", label(""""viewCount":1234,"viewCountPrecision":"exact","viewCountType":"watching"""", true))
        assertEquals("42 views", label(""""viewCount":42,"viewCountPrecision":"unknown","viewCountType":"views""""))
        assertEquals("0 watching", label(""""viewCount":0,"viewCountPrecision":"unknown","viewCountType":"watching""""))
        assertEquals("", label(""""viewCount":0,"viewCountPrecision":"unknown","viewCountText":"0 views""""))
        assertEquals("", label(""""viewCount":42,"viewCountPrecision":"unknown","viewCountType":"views"""", true))
        assertEquals("", label(""""viewCount":42,"viewCountPrecision":"approximate"""", true))
        assertEquals("", label(""""viewCount":42""", true))
    }

    @Test fun malformedValuesAndAmbiguousLiveCountsStayHidden() {
        for (raw in listOf("null", "-1", "1.5", "9223372036854775808", "true", "\"broken\"")) {
            assertNull(video(""""viewCount":$raw,"viewCountType":"views"""").views)
            assertEquals("", label(""""viewCount":$raw,"viewCountType":"views""""))
        }
        assertEquals("", label(""""viewCount":12,"liveNow":true"""))
        assertEquals("", label(""""viewCount":12,"viewCountType":"future","viewCountText":"12 views""""))
        assertEquals("9,223,372,036,854,775,807 views", label(""""viewCount":"9223372036854775807","viewCountPrecision":"exact"""", true))
    }

    @Test fun legacyTextIsDisplayOnlyAndNumericCountsWin() {
        assertEquals("1.2K views", label(""""viewCountText":"1.2K""""))
        assertEquals("1 view", label(""""viewCountText":"1""""))
        assertEquals("12 watching", label(""""viewCountText":"12 watching","liveNow":true"""))
        assertEquals("0 views", label(""""viewCountText":"0 views""""))
        assertEquals("No views", label(""""viewCountText":"No views""""))
        assertEquals("42 views", label(""""viewCount":42,"viewCountText":"1.2K""""))
        assertEquals("", label(""""viewCountText":"1.2K"""", true))
        assertNull(video(""""viewCountText":"1.2K"""").views)
        for (text in listOf("", "-", "—", "N/A", "null", "0", "0.0K", "-1 views", "unavailable", "NaN", "infinity")) {
            assertEquals(text, "", label(""""viewCountText":"$text""""))
        }
        assertEquals("", label(""""viewCountText":"1.2K","liveNow":true"""))
        assertEquals("", label(""""viewCountText":42"""))
        assertEquals("", label(""""viewCountText":"12 watching","viewCountType":"views""""))
    }

    @Test fun countHandoffsAndClipSerializationRetainValuesWithoutInventingPrecision() {
        for (fields in listOf(""""viewCount":0,"viewCountPrecision":"exact","viewCountType":"views"""",
            """"viewCount":12,"viewCountPrecision":"unknown","viewCountType":"watching"""", """"viewCountText":"1.2K"""")) {
            val original = video(fields)
            val encoded = VideoViewCounts.write(original, JSONObject().put("videoId", original.id).put("title", original.title))
            assertEquals(original, ApiParser.video(encoded))
            val clip = Clip("IVCL" + "a".repeat(32), "Moment", 0, 5000, original, "https://instance.test")
            assertEquals(clip, Clip.parse(clip.json(), clip.id, clip.server))
        }
        assertFalse(VideoViewCounts.write(video(""""viewCount":12"""), JSONObject()).has("viewCountPrecision"))
    }

    @Test fun collectionResponsesKeepCountsWithoutExtraMetadataRequests() = runBlocking {
        MockWebServer().use { mock ->
            val server = mock.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ server }, { Account("token", "Viewer", Long.MAX_VALUE, server) })
            val record = """{"videoId":"testvideo01","title":"Video","viewCount":0,"viewCountPrecision":"unknown","viewCountType":"views"}"""
            mock.enqueue(MockResponse().setBody("""{"videoId":"testvideo02","title":"Playing","recommendedVideos":[$record]}"""))
            assertEquals(0L, api.video("testvideo02").recommendations.single().views)
            mock.enqueue(MockResponse().setBody("""{"entries":[${record.replace("videoId", "video_id")}],"today":"2026-10-23","timezone":"UTC","total":1,"hasMore":false}"""))
            assertEquals(0L, api.history(1).entries.single().views)
            assertEquals(0L, History.parse("[$record]").entries.single().views)
            for (id in listOf("PLfixture", "RDfixture")) {
                mock.enqueue(MockResponse().setBody("""{"playlistId":"$id","viewCount":9999,"videos":[$record,$record]}"""))
                val page = api.queuePage(id)
                assertEquals(listOf(0L, 0L), page.videos.map { it.views })
            }
            val saved = DownloadRecord("saved", server, record, 0, emptyList())
            assertEquals(0L, saved.details.video.views)
            assertEquals(4, mock.requestCount)
            repeat(4) { mock.takeRequest() }
        }
    }
}
