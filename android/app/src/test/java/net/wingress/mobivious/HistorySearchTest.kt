package net.wingress.mobivious

import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class HistorySearchTest {
    private val channel = "UC" + "a".repeat(22)
    private val entry = """{"video_id":"abcdefghijk","title":"Saved title","channel_name":"Saved channel","channel_id":"$channel","length_seconds":123,"release_date":"2024-02-29","latest_watched":"2026-01-02","archived_dates":["2025-12-01"]}"""
    private fun organized(entries: String = "[$entry]", more: Boolean = false) = """{"entries":$entries,"total":3,"hasMore":$more,"today":"2026-01-03","timezone":"Asia/Jakarta"}"""

    @Test fun historyDatesStaySeparateFromPublicationAndRetainSavedMetadata() {
        val page = History.parse(organized(more = true))
        val video = page.entries.single()
        assertEquals("Saved title", video.title); assertEquals("Saved channel", video.author)
        assertEquals(channel, video.channelId); assertEquals(123L, video.duration)
        assertEquals("", video.published)
        assertEquals(LocalDate.of(2024, 2, 29), video.history!!.released)
        assertEquals(LocalDate.of(2026, 1, 2), video.history!!.watched)
        assertEquals(3, page.total); assertTrue(page.hasMore); assertTrue(page.organized)
        assertEquals("Asia/Jakarta", page.timezone)
    }

    @Test fun unavailableHistoryRetainsIdsAndUnknownDates() {
        val page = History.parse(organized("""[{"video_id":"abcdefghijk","title":null,"release_date":"2026-02-30","latest_watched":"2026-01-02T00:00:00Z"}]"""))
        val video = page.entries.single()
        assertTrue(video.unavailable); assertEquals("abcdefghijk", video.id)
        assertEquals("Unavailable video", video.title); assertEquals("Unknown channel", video.author)
        assertNull(video.history!!.released); assertNull(video.history!!.watched)
        assertEquals(HistoryGroup.OLDER, History.group(video.history!!.watched, page.today))
    }

    @Test fun rollingGroupsAreDisjointAcrossYearAndLeapBoundaries() {
        val today = LocalDate.of(2026, 1, 3)
        val boundaries = mapOf(0L to HistoryGroup.TODAY, 1L to HistoryGroup.YESTERDAY,
            2L to HistoryGroup.WEEK, 6L to HistoryGroup.WEEK, 7L to HistoryGroup.MONTH,
            29L to HistoryGroup.MONTH, 30L to HistoryGroup.OLDER, -1L to HistoryGroup.OLDER)
        boundaries.forEach { (age, expected) -> assertEquals(expected, History.group(today.minusDays(age), today)) }
        assertEquals(HistoryGroup.OLDER, History.group(null, today))
        assertEquals(HistoryGroup.YESTERDAY, History.group(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 1)))
    }

    @Test fun organizedCalendarComesFromServerRatherThanDeviceClock() {
        val page = History.parse(organized())
        assertEquals(HistoryGroup.YESTERDAY, History.group(page.entries.single().history!!.watched, page.today))
        assertEquals(LocalDate.of(2026, 1, 3), page.today)
    }

    @Test fun oldServerArraysRetainViewingAndDeclareSearchUnsupported() {
        val page = History.parse("[$entry]")
        assertFalse(page.organized); assertNull(page.today); assertNull(page.total)
        assertEquals("Saved title", page.entries.single().title)
        assertFalse(History.parse("[]").hasMore)
        val ids = History.parse("""["abcdefghijk",null,"bbbbbbbbbbb"]""")
        assertEquals(listOf("abcdefghijk", "bbbbbbbbbbb"), ids.entries.map { it.id })
        assertTrue(ids.entries.all { it.unavailable })
    }

    @Test fun malformedOrganizedResponsesDoNotPretendToBeLegacyHistory() {
        assertThrows(Exception::class.java) { History.parse("""{"entries":[],"total":0,"hasMore":false,"today":"bad","timezone":"UTC"}""") }
        assertThrows(Exception::class.java) { History.parse("{}") }
    }

    @Test fun mixedChannelResultsUseOriginalPageSizeForPagination() {
        val raw = JSONArray().put(JSONObject("""{"videoId":"abcdefghijk","title":"Video"}"""))
        repeat(19) { raw.put(JSONObject("""{"playlistId":"PL$it","title":"Playlist"}""")) }
        val first = SearchPage.parse(raw.toString())
        assertEquals(1, first.items.size); assertTrue(first.hasMore)
        assertFalse(SearchPage.parse("[]").hasMore)
        assertFalse(SearchPage.parse("""[{"videoId":"bbbbbbbbbbb","title":"Last"}]""").hasMore)
    }

    @Test fun scopedQueriesAreEncodedAndOnlySubscriptionsUseBearer() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val account = Account("private-token", "Viewer", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            val query = "channel:other & café + 100%"
            server.enqueue(MockResponse().setBody("[]")); api.channelSearch(channel, query, 2)
            val public = server.takeRequest()
            assertEquals("/api/v1/channels/$channel/search", public.requestUrl!!.encodedPath)
            assertEquals(query, public.requestUrl!!.queryParameter("q"))
            assertEquals("2", public.requestUrl!!.queryParameter("page")); assertNull(public.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("[]")); api.subscriptionSearch(query, 3)
            val private = server.takeRequest()
            assertEquals("/api/v1/auth/subscriptions/search", private.requestUrl!!.encodedPath)
            assertEquals(query, private.requestUrl!!.queryParameter("q")); assertEquals("3", private.requestUrl!!.queryParameter("page"))
            assertEquals("Bearer private-token", private.getHeader("Authorization"))
        }
    }

    @Test fun historyRequestsOrganizedSearchAndPreservesExplicitEnd() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Viewer", Long.MAX_VALUE, address) })
            server.enqueue(MockResponse().setBody(organized()))
            assertFalse(api.history(2, "café & title").hasMore)
            val request = server.takeRequest()
            assertEquals("Bearer token", request.getHeader("Authorization"))
            assertEquals("true", request.requestUrl!!.queryParameter("organized")); assertEquals("true", request.requestUrl!!.queryParameter("details"))
            assertEquals("café & title", request.requestUrl!!.queryParameter("q")); assertEquals("2", request.requestUrl!!.queryParameter("page"))
        }
    }

    @Test fun missingSubscriptionRoutesAndOldTokensExplainUpgradeWithoutLoggingOut() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            var expired = false
            val api = InvidiousApi({ address }, { Account("token", "Viewer", Long.MAX_VALUE, address) }, { expired = true })
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
            try { api.subscriptionSearch("title", 1); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains("API update")) }
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Invalid scope"}"""))
            try { api.subscriptionSearch("title", 1); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains("Sign out and sign in")) }
            assertFalse(expired)
        }
    }

    @Test fun scopedRequestsRejectCapturedContextsAfterAccountChanges() = runBlocking {
        val original = Account("Alice", "Alice", Long.MAX_VALUE, "https://instance.test")
        val api = InvidiousApi({ "https://instance.test" }, { original.copy(token = "Bob", username = "Bob") })
        val context = ApiContext("https://instance.test", original)
        try { api.subscriptionSearch("title", 1, context); fail() } catch (_: CancellationException) { }
        try { api.history(1, "title", context); fail() } catch (_: CancellationException) { }
    }

    @Test fun delayedScopedReadsNeverReturnThePreviousAccountsResults() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val owner = AtomicReference(Account("Alice", "Alice", Long.MAX_VALUE, address))
            val api = InvidiousApi({ address }, { owner.get() })
            repeat(3) { kind ->
                owner.set(owner.get().copy(token = "Alice", username = "Alice"))
                val context = api.context()
                server.enqueue(MockResponse().setBody(if (kind == 2) organized() else "[]").setBodyDelay(200, TimeUnit.MILLISECONDS))
                val pending = async(Dispatchers.Default) {
                    when (kind) {
                        0 -> api.channelSearch(channel, "title", 1, context)
                        1 -> api.subscriptionSearch("title", 1, context)
                        else -> api.history(1, "title", context)
                    }
                }
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                owner.set(owner.get().copy(token = "Bob", username = "Bob"))
                try { pending.await(); fail("Previous account result escaped") } catch (_: CancellationException) { }
            }
        }
    }
}
