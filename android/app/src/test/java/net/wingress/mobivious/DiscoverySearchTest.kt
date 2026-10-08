package net.wingress.mobivious

import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class DiscoverySearchTest {
    private val channel = "UC" + "a".repeat(22)
    private val mixed = """[{"type":"video","videoId":"abcdefghijk","authorId":"$channel","isMember":true},
        {"type":"channel","authorId":"$channel","author":"Creator","subCount":42,"videoCount":7,"channelHandle":"@creator","authorVerified":true},
        {"type":"playlist","playlistId":"RDopaque","playlistThumbnail":"/vi/abcdefghijk/mqdefault.jpg","authorId":"$channel"},
        {"type":"hashtag","title":"Unsupported"},{"type":"video","videoId":"abcdefghijk"}]"""

    @Test fun discoverySendsCategoryAndRegionOnlyToTrending() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = InvidiousApi({ server.url("/").toString() }, { null })
            server.enqueue(MockResponse().setBody("[]")); api.discovery("trending", "ID", TrendingCategory.GAMING)
            val request = server.takeRequest()
            assertEquals("gaming", request.requestUrl!!.queryParameter("type")); assertEquals("ID", request.requestUrl!!.queryParameter("region"))
            assertNull(request.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("[]")); api.discovery("popular", "JP", TrendingCategory.GAMING)
            assertTrue(server.takeRequest().requestUrl!!.queryParameterNames.isEmpty())
            server.enqueue(MockResponse().setBody("[]")); api.discovery("trending")
            assertEquals("livestreams", server.takeRequest().requestUrl!!.queryParameter("type"))
        }
    }

    @Test fun eachSearchTypeUsesItsOwnFiltersWithoutAuthorization() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString()
            val api = InvidiousApi({ address }, { Account("secret", "Viewer", Long.MAX_VALUE, address) })
            for (type in SearchType.entries) {
                server.enqueue(MockResponse().setBody(mixed))
                val page = api.searchResults("café & games", 2, type, "views", "week", "short")
                val request = server.takeRequest(); val url = request.requestUrl!!
                assertEquals(type.apiValue, url.queryParameter("type")); assertEquals("2", url.queryParameter("page"))
                assertEquals("café & games", url.queryParameter("q")); assertNull(request.getHeader("Authorization"))
                assertEquals(if (type == SearchType.CHANNELS) "relevance" else "views", url.queryParameter("sort"))
                assertEquals(if (type.videoFilters) "week" else null, url.queryParameter("date"))
                assertEquals(if (type.videoFilters) "short" else null, url.queryParameter("duration"))
                assertEquals(if (type == SearchType.ALL) 3 else 1, page.items.size)
            }
        }
    }

    @Test fun mixedParserPreservesOrderChannelMetadataAndMixSeed() {
        val page = GeneralSearchPage.parse(JSONArray(mixed))
        assertEquals(listOf("video:abcdefghijk", "channel:$channel", "playlist:RDopaque"), page.items.map { it.key })
        val creator = (page.items[1] as SearchResult.ChannelItem).channel
        assertEquals("@creator", creator.handle); assertEquals("42", creator.subscribers); assertEquals(7L, creator.videoCount); assertTrue(creator.verified)
        assertEquals("abcdefghijk", (page.items[2] as SearchResult.PlaylistItem).playlist.seedVideoId)
        assertEquals(4, page.sourceKeys.size)
    }

    @Test fun missingOptionalChannelMetadataStaysAbsentAndInvalidIdsAreSkipped() {
        val page = GeneralSearchPage.parse(JSONArray("""[{"type":"channel","authorId":"$channel","author":"Minimal"},
            {"type":"channel","authorId":"invalid"},{"type":"other","videoId":"abcdefghijk"}]"""))
        val creator = (page.items.single() as SearchResult.ChannelItem).channel
        assertEquals("", creator.subscribers); assertEquals("", creator.handle); assertNull(creator.videoCount)
        assertEquals(3, page.sourceKeys.size)
    }

    @Test fun legacyResultsWithoutTypeAreRecognized() {
        val page = GeneralSearchPage.parse(JSONArray("""[{"videoId":"abcdefghijk"},{"authorId":"$channel"},{"playlistId":"PLtest"}]"""))
        assertEquals(3, page.items.size)
    }

    @Test fun rawPagesGovernPaginationIncludingUnsupportedAndRepeatedPages() {
        val first = GeneralSearchPage.parse(JSONArray("""[{"type":"hashtag","title":"Ignored"},17]"""))
        assertTrue(first.items.isEmpty()); assertFalse(first.exhausted(emptySet()))
        assertTrue(first.exhausted(first.sourceKeys))
        val next = GeneralSearchPage.parse(JSONArray(mixed))
        assertFalse(next.exhausted(first.sourceKeys)); assertTrue(next.exhausted(first.sourceKeys + next.sourceKeys))
        assertTrue(GeneralSearchPage.parse(JSONArray("[]")).exhausted(next.sourceKeys))
    }

    @Test fun mergingDeduplicatesWithinTypesWithoutRegrouping() {
        val first = GeneralSearchPage.parse(JSONArray(mixed)).items
        val extra = SearchResult.VideoItem(Video("RDopaque", "Same ID, different type"))
        assertEquals(first + extra, SearchResults.merge(first, listOf(first[1], extra)))
    }

    @Test fun visibilityFiltersAllTypesAndOnlyAppliesMembershipToVideos() {
        val items = GeneralSearchPage.parse(JSONArray(mixed)).items
        assertTrue(SearchResults.visible(items, true, SearchVisibility(), setOf(channel)).isEmpty())
        assertEquals(2, SearchResults.visible(items, false, SearchVisibility(includeBlocked = true), setOf(channel)).size)
        assertEquals(3, SearchResults.visible(items, false, SearchVisibility(true, true), setOf(channel)).size)
        assertEquals(2, SearchResults.visible(items, false, SearchVisibility(), emptySet()).size)
    }

    @Test fun countrySearchSupportsLocalizedNamesCodesAndDefaultCategory() {
        assertTrue(ContentRegions.choices("Indonesia", Locale.ENGLISH).contains("ID"))
        assertTrue(ContentRegions.choices(" id ", Locale.ENGLISH).contains("ID"))
        assertEquals("Indonesia (ID)", ContentRegions.label("ID", Locale.ENGLISH))
        assertTrue(ContentRegions.choices("no-country-matches", Locale.ENGLISH).isEmpty())
        assertEquals(ContentRegions.codes.size, ContentRegions.codes.distinct().size)
        assertEquals(TrendingCategory.LIVESTREAMS, TrendingCategory.saved("invalid"))
    }

    @Test fun delayedDiscoveryAndSearchCannotEscapeAnAccountSwitch() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString()
            val owner = AtomicReference<Account?>(null)
            val api = InvidiousApi({ address }, { owner.get() })
            for (search in listOf(false, true)) {
                owner.set(null); val context = api.context()
                server.enqueue(MockResponse().setBody(mixed).setBodyDelay(200, TimeUnit.MILLISECONDS))
                val pending = async(Dispatchers.Default) {
                    if (search) api.searchResults("creator", 1, SearchType.ALL, "relevance", "", "", context)
                    else api.discovery("trending", "ID", TrendingCategory.GAMING, context)
                }
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                owner.set(Account("new", "Other", Long.MAX_VALUE, address))
                try { pending.await(); fail("Stale response escaped") } catch (_: CancellationException) { }
            }
        }
    }
}
