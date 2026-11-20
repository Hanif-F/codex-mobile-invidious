package net.wingress.mobivious

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.ApiParser
import net.wingress.mobivious.data.VideoChapter
import net.wingress.mobivious.data.VideoDetails
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's real regex engine without an Activity, network, or playback fixture. */
@RunWith(AndroidJUnit4::class)
class ChaptersRuntimeTest {
    private fun details(description: String? = null, live: Boolean = false, html: String? = null): VideoDetails = ApiParser.details(
        JSONObject().put("videoId", "abcdefghijk").put("title", "Video").put("lengthSeconds", 120)
            .put("liveNow", live).apply {
                description?.let { put("description", it) }
                html?.let { put("descriptionHtml", it) }
            }
    )

    @Test fun videoWithoutDescriptionLoadsWithoutInitializingAnInvalidRegex() {
        val video = details()
        assertEquals("abcdefghijk", video.video.id)
        assertTrue(video.chapters.isEmpty())
    }

    @Test fun emptyDescriptionLoadsWithoutChapters() {
        assertTrue(details("").chapters.isEmpty())
    }

    @Test fun ordinaryDescriptionLoadsWithoutChapters() {
        assertTrue(details("An ordinary video description.").chapters.isEmpty())
    }

    @Test fun manualChaptersLoadOnAndroid() {
        assertEquals(listOf(VideoChapter(0, "Intro"), VideoChapter(60000, "Second")),
            details("0:00 Intro\n1:00 Second").chapters)
    }

    @Test fun richDescriptionsSupplyMissingChaptersWithoutOverridingPlainChapters() {
        val html = """<p><a href="/watch?v=abcdefghijk&t=0">0:00</a> Intro &amp; welcome<br>0:45 Findings</p>
            <div>1:30 Recap</div><script>1:40 Fake</script><p>2:00 Outside the duration</p>"""
        assertEquals(listOf(VideoChapter(0, "Intro & welcome"), VideoChapter(45_000, "Findings"), VideoChapter(90_000, "Recap")),
            details("0:00 Only one plain chapter", html = html).chapters)
        assertEquals(listOf(VideoChapter(0, "Plain"), VideoChapter(60_000, "Second")),
            details("0:00 Plain\n1:00 Second", html = html).chapters)
        assertTrue(details(live = true, html = html).chapters.isEmpty())
    }

    @Test fun unicodeWhitespaceAndTitlesLoadOnAndroid() {
        val description = "\u00a0•\u20030:00\u00a0Intro\u2003\n\u2003-\t1:00\u0085—\u2003日本語\u00a0"
        assertEquals(listOf(VideoChapter(0, "Intro"), VideoChapter(60000, "日本語")), details(description).chapters)
    }

    @Test fun liveVideoLoadsWithoutChapters() {
        val video = details("0:00 Intro\n1:00 Second", live = true)
        assertTrue(video.video.live)
        assertTrue(video.chapters.isEmpty())
    }
}
