package net.wingress.mobivious

import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChaptersTest {
    @Test fun manualDescriptionIncludesMinuteAndHourTimestamps() {
        val chapters = ChapterRules.parse("Timestamps (courtesy of NoKi1119):\n0:00 Chapters\n1:45 Intro\n2:42 Topic #1: Apple Event\n1:40:05 MSI", 10000)
        assertEquals(listOf(0L, 105000L, 162000L, 6005000L), chapters.map { it.startMs })
        assertEquals("MSI", chapters.last().title)
    }
    @Test fun shortLaterChaptersSortAndKeepTheFirstDuplicateWithUnicodeTitles() {
        val chapters = ChapterRules.parse(" • 1:03 — 日本語\r\n- 1:01 Intro\n* 1:02|Middle\n1:01 duplicate", 100)
        assertEquals(listOf(VideoChapter(61000, "Intro"), VideoChapter(62000, "Middle"), VideoChapter(63000, "日本語")), chapters)
    }
    @Test fun malformedOverflowingMissingAndOutOfDurationEntriesAreRejected() {
        val text = "0:60 Bad\n1:99:00 Bad\n1:02:99 Bad\n999999999999999999999:00 Bad\n2147483648:00 Bad\n0:01\n0:02 -\n-1:03 Bad\n3:00 End\n0:10 Good\n0:20 Also good"
        assertEquals(listOf(10000L, 20000L), ChapterRules.parse(text, 180).map { it.startMs })
        assertTrue(ChapterRules.parse("0:10 One", 180).isEmpty())
        assertTrue(ChapterRules.parse("No timestamps here", 180).isEmpty())
        assertTrue(ChapterRules.parse("0:10 One\n0:20 Two", 0).isEmpty())
        assertTrue(ChapterRules.parse("0:10 One\n0:20 Two", 180, live = true).isEmpty())
    }
    @Test fun titlesRemainPlainTextAndApiUsesOnlyTheSuppliedDescription() {
        val details = ApiParser.details(JSONObject("""{"videoId":"abcdefghijk","title":"Video","lengthSeconds":2,"description":"0:00 <script>alert(1)</script>\n0:01 A & B","chapters":[{"start":0,"title":"Automatic"}],"storyboards":[{"url":"/api/v1/storyboards/abcdefghijk"}]}"""))
        assertEquals(listOf(VideoChapter(0, "<script>alert(1)</script>"), VideoChapter(1000, "A & B")), details.chapters)
        assertTrue(ApiParser.details(JSONObject("{}")).chapters.isEmpty())
        assertTrue(ApiParser.details(JSONObject("""{"lengthSeconds":120,"liveNow":true,"description":"0:00 A\n1:00 B"}""")).chapters.isEmpty())
    }
    @Test fun currentChapterUsesExactBoundariesWithoutInventingAnInitialChapter() {
        val chapters = ChapterRules.parse("0:10 First\n0:20 Second", 30)
        assertNull(ChapterRules.current(chapters, 9999))
        assertEquals(chapters[0], ChapterRules.current(chapters, 10000))
        assertEquals(chapters[0], ChapterRules.current(chapters, 19999))
        assertEquals(chapters[1], ChapterRules.current(chapters, 20000))
        assertEquals(chapters[1], ChapterRules.current(chapters, 30000))
    }
    @Test fun runtimeDurationAndLiveChangesRemoveUnavailableChapters() {
        val chapters = ChapterRules.parse("0:00 First\n0:10 Second\n0:20 Third", 30)
        assertEquals(chapters.take(2), ChapterRules.available(chapters, 20000, false))
        assertTrue(ChapterRules.available(chapters, 10000, false).isEmpty())
        assertTrue(ChapterRules.available(chapters, 0, false).isEmpty())
        assertTrue(ChapterRules.available(chapters, 30000, true).isEmpty())
    }
}
