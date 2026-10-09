package net.wingress.mobivious

import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DisplayFormatsTest {
    @Test fun compactCountsRoundAcrossUnitsWithoutLosingLargeIntegerPrecision() {
        mapOf(0L to "0", 1L to "1", 999L to "999", 1000L to "1K", 1050L to "1.1K",
            999_949L to "999.9K", 999_950L to "1M", 5_600_000L to "5.6M",
            999_950_000L to "1B", 1_000_000_000_000L to "1T").forEach { (value, expected) ->
            assertEquals(expected, DisplayFormats.compact(value, Locale.US))
        }
        assertEquals("9,223,372T", DisplayFormats.compact(Long.MAX_VALUE, Locale.US))
        assertEquals("9,223,372,036,854,775,807", DisplayFormats.grouped(Long.MAX_VALUE, Locale.US))
        assertEquals("", DisplayFormats.compact(null)); assertEquals("", DisplayFormats.compact(-1))
    }

    @Test fun localeChangesSeparatorsWithoutChangingSuffixesOrSourcePrecision() {
        val id = Locale.forLanguageTag("id-ID")
        assertEquals("5,6M", DisplayFormats.compact(5_600_000, id))
        assertEquals("5.600.000", DisplayFormats.grouped(5_600_000, id))
        assertEquals("5,6M subscribers", DisplayFormats.subscribers("5600000", id))
        assertEquals("5,6M subscribers", DisplayFormats.subscribers("5.6M subscribers", id))
        assertEquals("1 subscriber", DisplayFormats.subscribers("1", Locale.US))
        assertEquals("1.2K subscribers", DisplayFormats.subscribers("1,200", Locale.US))
        assertEquals("", DisplayFormats.subscribers("-")); assertEquals("", DisplayFormats.subscribers("-10"))
        assertEquals("", DisplayFormats.subscribers("N/A")); assertEquals("", DisplayFormats.subscribers("NaN"))
        assertEquals("12 juta subscribers", DisplayFormats.subscribers("12 juta", id))
        assertEquals("0 views", DisplayFormats.audience(0, "view", locale = Locale.US))
        assertEquals("1 video", DisplayFormats.inventory(1, "video", locale = Locale.US))
    }

    @Test fun sizesDurationsAndBitratesUseConsistentUnitsAndHandleZero() {
        assertEquals("0 B", DisplayFormats.bytes(0, Locale.US)); assertEquals("", DisplayFormats.bytes(null))
        assertEquals("1 MB", DisplayFormats.bytes(999_950, Locale.US))
        assertEquals("5,6 MB", DisplayFormats.bytes(5_600_000, Locale.GERMANY))
        assertEquals("128 kbps", DisplayFormats.bitrate(128_000, Locale.US))
        assertEquals("1,25 Mbps", DisplayFormats.bitrate(1_250_000, Locale.GERMANY))
        assertEquals("", DisplayFormats.duration(0)); assertEquals("", DisplayFormats.duration(-1))
        assertEquals("59:59", DisplayFormats.duration(3599)); assertEquals("1:00:00", DisplayFormats.duration(3600))
        assertEquals("25:01:01", DisplayFormats.duration(90061)); assertEquals("0:00", DisplayFormats.clock(0))
    }

    @Test fun apiCountsDistinguishZeroUnknownAndFractions() {
        for (raw in listOf("null", "-1", "1.5", "9223372036854775808", "true", "\"broken\"")) {
            val video = ApiParser.video(JSONObject("""{"viewCount":$raw}"""))
            assertNull("$raw must not become a display count", video.views)
        }
        assertNull(ApiParser.video(JSONObject("{}")).views)
        assertEquals(0L, ApiParser.video(JSONObject("""{"viewCount":0}""")).views)
        assertEquals(5600000L, ApiParser.video(JSONObject("""{"viewCount":"5600000"}""")).views)
        val unverified = ApiParser.details(JSONObject("""{"viewCount":5600000,"likeCount":0}"""))
        assertEquals(CountPrecision.UNVERIFIED, unverified.video.viewCountPrecision)
        assertEquals(CountPrecision.UNVERIFIED, unverified.likeCountPrecision)
        val exact = ApiParser.details(JSONObject("""{"viewCount":5600001,"likeCount":0,"viewCountPrecision":"exact","likeCountPrecision":"exact"}"""))
        assertEquals(CountPrecision.EXACT, exact.video.viewCountPrecision); assertEquals(0L, exact.likes)
        val unknown = ApiParser.details(JSONObject("""{"viewCount":0,"likeCount":0,"viewCountPrecision":"unknown","likeCountPrecision":"unknown"}"""))
        assertNull(unknown.video.views); assertNull(unknown.likes)
        assertEquals(-1, ApiParser.playlist(JSONObject("{}")).count)
    }

    @Test fun publicationPlaceholdersCannotOverrideValidationWithRelativeText() {
        for (raw in listOf("0", "1", "-1", "null", "\"bad\"", "1800000000000", "9223372036854775807")) {
            val video = ApiParser.video(JSONObject("""{"published":$raw,"publishedText":"56 years ago"}"""))
            assertEquals("", video.published); assertNull(video.publishedAt)
        }
        assertEquals("today", ApiParser.video(JSONObject("""{"publishedText":"today"}""")).published)
        assertNull(DisplayFormats.publication(1_800_000_000, now = 1_700_000_000))
        assertEquals(1_700_000_000L, DisplayFormats.publication(1_700_000_000, now = 1_700_000_000))
        for (raw in listOf("0", "1.5", "null", "1800000000000", "9223372036854775807")) {
            assertNull(ApiParser.details(JSONObject("""{"premiereTimestamp":$raw}""")).premiereTimestamp)
        }
    }

    @Test fun calendarDatesDoNotShiftAndTimestampsUseTheChosenTimezone() {
        val date = LocalDate.of(2026, 10, 9)
        assertEquals("Oct 9, 2026", DisplayFormats.date(date, Locale.US))
        assertEquals("9 Okt 2026", DisplayFormats.date(date, Locale.forLanguageTag("id-ID")))
        assertEquals("Jan 1, 2024", DisplayFormats.timestamp(1_704_060_000, Locale.US, ZoneId.of("Asia/Jakarta"), dateOnly = true))
        assertEquals("Dec 31, 2023", DisplayFormats.timestamp(1_704_060_000, Locale.US, ZoneId.of("UTC"), dateOnly = true))
        assertEquals("", DisplayFormats.timestamp(Long.MAX_VALUE)); assertEquals("", DisplayFormats.timestamp(0))
    }
}
