package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreferencesTest {
    @Test fun seekIntervalsRoundTripAndStaySparseForAccountsAndGuests() = runBlocking {
        val before = AccountPreferences()
        for (seconds in PreferenceRules.seekIntervals) {
            val value = before.copy(seekSeconds = seconds)
            assertEquals(seconds, AccountPreferences.parse(value.json()).seekSeconds)
            assertEquals(seconds, LocalPreferences.read(value.json()).seekSeconds)
            assertEquals(seconds, value.merge(JSONObject().put("speed", 2)).seekSeconds)
            if (seconds != 10) assertEquals(setOf("seek_seconds"), value.changesFrom(before).keys().asSequence().toSet())
        }
        for (raw in listOf("0", "15", "-5", "5.0", "5.5", "null", "true", "\"20\"", "[]", "{}")) {
            val malformed = JSONObject("""{"seek_seconds":$raw}""")
            assertEquals(10, AccountPreferences.parse(malformed).seekSeconds)
            assertEquals(10, LocalPreferences.read(malformed).seekSeconds)
        }
        assertEquals(10, AccountPreferences.parse(JSONObject()).seekSeconds)
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) })
            server.enqueue(MockResponse().setBody(before.copy(seekSeconds = 30).json().toString()))
            assertEquals(30, api.preferences(before.copy(seekSeconds = 30).changesFrom(before), api.context()).seekSeconds)
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method); assertEquals("""{"seek_seconds":30}""", patch.body.readUtf8())
            server.enqueue(MockResponse().setBody("{}"))
            assertEquals(10, api.preferences().seekSeconds)
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"Unsupported native preference"}"""))
            try { api.preferences(JSONObject().put("seek_seconds", 20), api.context()); fail("Older server must reject unsupported saves") }
            catch (_: java.io.IOException) { }
        }
    }
    @Test fun codecPreferencesRoundTripAndProduceSparseAccountPatches() = runBlocking {
        val before = AccountPreferences()
        for (codec in listOf("auto", "av1", "h264")) {
            val saved = before.copy(videoCodec = codec)
            assertEquals(codec, AccountPreferences.parse(saved.json()).videoCodec)
        }
        for (raw in listOf("null", "true", "123", "{}", "[]", "\"AV1\"", "\"vp9\"", "\"\""))
            assertEquals("auto", AccountPreferences.parse(JSONObject("""{"video_codec":$raw}""")).videoCodec)
        assertEquals("auto", AccountPreferences.parse(JSONObject()).videoCodec)
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("token", "Alice", Long.MAX_VALUE, address) })
            val changed = before.copy(videoCodec = "av1")
            server.enqueue(MockResponse().setBody(changed.copy(region = "ID").json().toString()))
            val saved = api.preferences(changed.changesFrom(before), api.context())
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method); assertEquals("Bearer token", patch.getHeader("Authorization"))
            assertEquals("""{"video_codec":"av1"}""", patch.body.readUtf8())
            assertEquals("ID", saved.region); assertEquals("av1", saved.videoCodec)
            server.enqueue(MockResponse().setBody(saved.copy(videoCodec = "h264").json().toString()))
            assertEquals("h264", api.preferences().videoCodec)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("ID", before.copy(region = "ID").merge(changed.changesFrom(before)).region)
        }
    }
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
        assertEquals("Trending" to "trending", PreferenceRules.destination("Trending", false))
        assertEquals("Search" to "popular", PreferenceRules.destination("", false))
        assertEquals("You" to "popular", PreferenceRules.destination("Playlists", true))
        assertEquals("Popular" to "popular", PreferenceRules.destination("Subscriptions", false))
        assertEquals(listOf("Popular", "Trending", "Subscriptions", "You"), PreferenceRules.navigation())
        assertEquals("You (library)", PreferenceRules.homeLabel("Playlists"))
        assertEquals("Search", PreferenceRules.homeLabel(""))
        assertEquals("Popular" to "popular", PreferenceRules.destination("Playlists", false))
        assertEquals("Popular" to "popular", PreferenceRules.destination("unsupported", true))
    }
    @Test fun nativeHomepageChangesPreserveTheWebsiteFeedMenu() {
        val before = AccountPreferences(feedMenu = listOf("Trending", "", "Playlists", "Subscriptions"))
        val changed = before.copy(defaultHome = "Playlists")
        val patch = changed.changesFrom(before)
        assertEquals(setOf("default_home"), patch.keys().asSequence().toSet())
        assertEquals(before.feedMenu, AccountPreferences.parse(changed.json()).feedMenu)
        assertEquals(before.feedMenu, before.merge(patch).feedMenu)
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
