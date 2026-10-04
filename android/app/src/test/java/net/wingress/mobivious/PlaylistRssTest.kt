package net.wingress.mobivious

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaylistRssTest {
    private fun account(server: MockWebServer) = Account("native-secret", "Alice", Long.MAX_VALUE, server.url("/").toString().trimEnd('/'))

    @Test fun subscribedNativePlaylistIsReadOnlyAndRetainsSourcePrivacy() {
        val list = ApiParser.playlist(JSONObject("""{"playlistId":"IVother","title":"Owner title","isOwned":false,"isSaved":true,"privacy":"unlisted"}"""), legacyOwned = true)
        assertFalse(list.owned); assertTrue(list.saved); assertEquals("unlisted", list.privacy)
        assertEquals("Invidious playlist", list.sourceLabel)
        assertTrue(ApiParser.playlist(JSONObject("""{"playlistId":"IVown"}"""), legacyOwned = true).owned)
        assertFalse(ApiParser.playlist(JSONObject("""{"playlistId":"IVother"}""")).owned)
    }

    @Test fun typedSearchPreservesMixSeedsAndSendsNoVideoFilters() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = InvidiousApi({ account(server).server }, { null })
            server.enqueue(MockResponse().setBody("""[{"type":"playlist","playlistId":"RDopaque","title":"Mix","playlistThumbnail":"/vi/abcdefghijk/mqdefault.jpg","videoCount":-1},{"type":"video","videoId":"abcdefghijk"}]"""))
            val result = api.searchPlaylists("music", 2, "relevance")
            assertEquals("abcdefghijk", result.single().seedVideoId); assertTrue(result.single().mix)
            val request = server.takeRequest(); assertNull(request.getHeader("Authorization"))
            assertEquals(setOf("q", "page", "sort", "type"), request.requestUrl!!.queryParameterNames)
            assertEquals("playlist", request.requestUrl!!.queryParameter("type"))
            assertEquals("2", request.requestUrl!!.queryParameter("page"))
        }
    }

    @Test fun channelPlaylistContinuationAndSortingAreEncoded() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = InvidiousApi({ account(server).server }, { null })
            server.enqueue(MockResponse().setBody("""{"playlists":[{"playlistId":"PLx","title":"Channel list"}],"continuation":"next&token"}"""))
            val first = api.channelPlaylists("UCchannel", sort = "newest"); server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"playlists":[],"continuation":null}"""))
            assertTrue(api.channelPlaylists("UCchannel", first.continuation, "newest").continuation.isEmpty())
            val request = server.takeRequest(); assertEquals("next&token", request.requestUrl!!.queryParameter("continuation"))
            assertEquals("newest", request.requestUrl!!.queryParameter("sort_by"))
            assertEquals(listOf(ChannelTab.PLAYLISTS), Channel("UCchannel", "Only lists", tabs = listOf("playlists")).contentTabs)
        }
    }

    @Test fun subscribeAndUnsubscribeUseDedicatedRoutesWithoutDeletingTheSource() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val account = account(server); val api = InvidiousApi({ account.server }, { account })
            val list = Playlist("RDopaque", "Mix", -1, seedVideoId = "abcdefghijk")
            server.enqueue(MockResponse().setBody("""{"playlistId":"RDopaque","title":"Mix","isOwned":false,"isSaved":true,"seedVideoId":"abcdefghijk"}"""))
            assertTrue(api.subscribePlaylist(list, true, api.context())!!.saved)
            val put = server.takeRequest(); assertEquals("PUT", put.method)
            assertEquals("/api/v1/auth/saved_playlists/RDopaque", put.path)
            assertEquals("Bearer native-secret", put.getHeader("Authorization"))
            assertEquals("abcdefghijk", JSONObject(put.body.readUtf8()).getString("seedVideoId"))
            server.enqueue(MockResponse().setResponseCode(204)); assertNull(api.subscribePlaylist(list, false, api.context()))
            val delete = server.takeRequest(); assertEquals("DELETE", delete.method); assertEquals(put.path, delete.path)
        }
    }

    @Test fun ownerUpdatesReachDetailsAndAuthenticatedMixesPreserveTheirSeed() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val account = account(server); val api = InvidiousApi({ account.server }, { account })
            repeat(2) { i ->
                server.enqueue(MockResponse().setBody("""{"playlistId":"IVother","title":"Title $i","videoCount":${i + 1},"isOwned":false,"isSaved":true,"privacy":"public","videos":[{"videoId":"abcdefghijk","index":$i}]}"""))
                val page = api.queuePage("IVother"); assertEquals("Title $i", page.playlist!!.title); assertFalse(page.source.owned)
                assertEquals(i, page.videos.single().playlistIndex); server.takeRequest()
            }
            server.enqueue(MockResponse().setBody("""{"mixId":"RDopaque","title":"Mix","videos":[]}"""))
            api.queuePage("RDopaque", continuation = "abcdefghijk")
            val request = server.takeRequest(); assertEquals("/api/v1/auth/playlists/RDopaque", request.requestUrl!!.encodedPath)
            assertEquals("abcdefghijk", request.requestUrl!!.queryParameter("continuation"))
            assertEquals("abcdefghijk", VideoLinks.parse("https://invidious.wingress.net/mix?list=RDopaque&continuation=abcdefghijk", account.server)!!.seedVideoId)
            assertEquals("abcdefghijk", VideoLinks.parse("https://youtube.com/watch?v=abcdefghijk&list=RDopaque", account.server)!!.seedVideoId)
        }
    }

    @Test fun rssLinksUseSelectedInstanceAndRejectPrivateOrForeignUrls() {
        val server = "https://mobivious.wingress.net"
        assertEquals("$server/feed/channel/UCexample", RssLinks.channel(server, "UCexample"))
        assertEquals("$server/feed/playlist/RDopaque?continuation=abcdefghijk", RssLinks.playlist(server, Playlist("RDopaque", "Mix", -1, seedVideoId = "abcdefghijk")))
        assertThrows(IllegalArgumentException::class.java) { RssLinks.playlist(server, Playlist("IVown", "Private", 1, "private", owned = true)) }
        assertThrows(IllegalArgumentException::class.java) { RssLinks.playlist(server, Playlist("RDopaque", "Mix without seed", -1)) }
        listOf("https://evil.test/feed/private?token=x", "//evil.test/feed/private?token=x", "/feed/private?token=").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { RssLinks.privateFeed(server, path) }
        }
    }

    @Test fun rssAndXmlExportsNeverPutTheNativeTokenIntoTheSharedContent() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val account = account(server); val api = InvidiousApi({ account.server }, { account })
            server.enqueue(MockResponse().setBody("""{"feedPath":"/feed/private?token=rss-only-secret"}"""))
            val link = api.subscriptionFeedLink(api.context()); assertFalse(link.contains(account.token)); assertTrue(link.contains("rss-only-secret"))
            assertEquals("Bearer native-secret", server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("<opml version=\"1.1\"><body/></opml>"))
            assertTrue(api.subscriptionOpml("newpipe", api.context()).startsWith("<opml"))
            val opml = server.takeRequest(); assertEquals("newpipe", opml.requestUrl!!.queryParameter("format")); assertEquals("application/xml", opml.getHeader("Accept"))
            server.enqueue(MockResponse().setBody("<feed xmlns=\"http://www.w3.org/2005/Atom\"/>"))
            api.playlistAtom("IVown", api.context())
            val atom = server.takeRequest(); assertEquals("/api/v1/auth/playlists/IVown/feed", atom.path); assertEquals("application/atom+xml", atom.getHeader("Accept"))
        }
    }

    @Test fun staleReadsAreDiscardedAfterAnAccountChange() = runBlocking {
        MockWebServer().use { server ->
            server.start(); var selected: Account? = account(server)
            val api = InvidiousApi({ account(server).server }, { selected })
            val context = api.context()
            server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse {
                selected = null
                return MockResponse().setBody("[]")
            } }
            try { api.playlists(context); fail("Stale account response accepted") } catch (_: CancellationException) { }
        }
    }

    @Test fun missingEndpointsAndOldScopesGiveSeparateExplanations() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val account = account(server); val api = InvidiousApi({ account.server }, { account })
            server.enqueue(MockResponse().setResponseCode(404))
            try { api.subscriptionFeedLink(api.context()); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains("API update")) }
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Invalid scope"}"""))
            try { api.subscriptionFeedLink(api.context()); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains("Sign out and sign in")) }
        }
    }
}
