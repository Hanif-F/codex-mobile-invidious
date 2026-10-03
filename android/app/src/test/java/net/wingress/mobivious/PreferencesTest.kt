package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreferencesTest {
    @Test fun webPreferencesParseAndRoundTrip() {
        val json = JSONObject("""{"autoplay":false,"listen":true,"local":false,"speed":1.5,"quality_dash":"720p","captions":["Indonesian","English",""],"dark_mode":"dark","ui_density":"compact","thin_mode":true,"default_home":"Trending","feed_menu":["Trending","Popular","Playlists","Subscriptions"],"region":"ID","related_videos":false,"extend_desc":true,"comments":["","reddit"],"max_results":60,"sort":"channel name","latest_only":true,"unseen_only":true,"notifications_only":true,"default_playlist":"IVfixture"}""")
        val p = ApiParser.preferences(json)
        assertFalse(p.autoplay); assertTrue(p.listen); assertFalse(p.local)
        assertEquals(1.5f, p.speed); assertEquals(720, p.maxHeight); assertEquals("Indonesian", p.captions.first())
        assertEquals("dark", p.darkMode); assertEquals("compact", p.uiDensity); assertTrue(p.thinMode)
        assertEquals("ID", p.region); assertEquals(60, p.maxResults); assertEquals("channel name", p.feedSort)
        assertTrue(p.latestOnly && p.unseenOnly && p.notificationsOnly); assertFalse(p.showYoutubeComments)
        assertEquals(p, ApiParser.preferences(p.json()))
    }
    @Test fun sparseDeltasPreserveOtherSettingsAndNestedSponsorBlock() {
        val before = AccountPreferences(comments = listOf("reddit", "youtube"), sponsorBlock = SponsorBlockSettings(enabled = true))
        val delta = before.copy(speed = 1.5f, thinMode = true).changesFrom(before)
        assertEquals(setOf("speed", "thin_mode"), delta.keys().asSequence().toSet())
        val concurrent = before.copy(region = "ID", sponsorBlock = before.sponsorBlock.copy(colors = before.sponsorBlock.colors + (SponsorBlockCategory.INTRO to "#123456")))
        val merged = concurrent.merge(delta)
        assertEquals("ID", merged.region); assertEquals("#123456", merged.sponsorBlock.colors[SponsorBlockCategory.INTRO]); assertEquals(before.comments, merged.comments)
        assertEquals(0, before.changesFrom(before).length())
    }
    @Test fun captionsUsePriorityAndAvailableTracks() {
        val available = listOf(Caption("English", "en", "/en"), Caption("Indonesian", "id", "/id"), Caption("French (auto-generated)", "fr", "/fr"))
        assertEquals("id", PreferenceRules.caption(listOf("Unavailable", "Indonesian", "English"), available)?.language)
        assertEquals("fr", PreferenceRules.caption(listOf("French (auto-generated)"), available)?.language)
        assertEquals("en", PreferenceRules.caption(listOf("en"), available)?.language)
        assertNull(PreferenceRules.caption(listOf("", "German"), available))
    }
    @Test fun homeAndNavigationKeepSupportedDestinationsReachable() {
        assertEquals("Home" to "trending", PreferenceRules.destination("Trending", false))
        assertEquals("Search" to "popular", PreferenceRules.destination("", false))
        assertEquals("Library" to "popular", PreferenceRules.destination("Playlists", true))
        assertEquals("Home" to "popular", PreferenceRules.destination("Subscriptions", false))
        assertEquals(listOf("Library", "Subscriptions", "Home", "Search"), PreferenceRules.navigation(listOf("Playlists", "Subscriptions", "Trending", "Popular")))
        assertEquals(4, PreferenceRules.navigation(emptyList()).size)
    }
    @Test fun malformedPreferencesHaveUsableDefaults() {
        val p = ApiParser.preferences(JSONObject("""{"speed":999,"dark_mode":"other","max_results":99999,"ui_density":"other","default_playlist":null}"""))
        assertEquals(1f, p.speed); assertEquals("", p.darkMode); assertEquals(1500, p.maxResults)
        assertEquals("balanced", p.uiDensity); assertEquals("", p.defaultPlaylist)
    }
    @Test fun feedAndHistoryRespectServerPageSizesAndNotificationFilter() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val account = Account("token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            server.enqueue(MockResponse().setBody("""{"notifications":[],"videos":[{"videoId":"abcdefghijk","title":"Normal"}]}"""))
            assertTrue(api.feed(2, true).isEmpty())
            val feed = server.takeRequest(); assertEquals("2", feed.requestUrl?.queryParameter("page")); assertNull(feed.requestUrl?.queryParameter("max_results"))
            server.enqueue(MockResponse().setBody("[]")); api.history(3)
            assertNull(server.takeRequest().requestUrl?.queryParameter("max_results"))
        }
    }
    @Test fun streamProxyPreferenceReachesVideoApiAndSharedPatchesStaySparse() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val account = Account("token", "Alice", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            server.enqueue(MockResponse().setBody("{}")); api.video("abcdefghijk", false)
            val read = server.takeRequest(); assertEquals("false", read.requestUrl?.queryParameter("local")); assertNull(read.getHeader("Authorization"))
            val before = AccountPreferences(); server.enqueue(MockResponse().setBody(before.copy(speed = 1.5f).json().toString()))
            api.preferences(before.copy(speed = 1.5f).changesFrom(before), api.context())
            val patch = server.takeRequest(); assertEquals("PATCH", patch.method); assertEquals("Bearer token", patch.getHeader("Authorization"))
            assertEquals(setOf("speed"), JSONObject(patch.body.readUtf8()).keys().asSequence().toSet())
        }
    }
}
