package net.wingress.mobivious

import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OriginalTitlesTest {
    @Test fun defaultsRoundTripsAndSparsePatchesPreserveOtherSettings() {
        for (raw in listOf("{}", """{"show_original_title":null}""", """{"show_original_title":"false"}"""))
            assertTrue(AccountPreferences.parse(JSONObject(raw)).showOriginalTitle)
        val before = AccountPreferences()
        for (enabled in listOf(false, true)) {
            val value = before.copy(showOriginalTitle = enabled)
            assertEquals(enabled, AccountPreferences.parse(value.json()).showOriginalTitle)
            assertEquals(enabled, LocalPreferences.read(value.json()).showOriginalTitle)
        }
        val patch = before.copy(showOriginalTitle = false).changesFrom(before)
        assertEquals(setOf("show_original_title"), patch.keys().asSequence().toSet())
        assertFalse(patch.getBoolean("show_original_title"))
        val merged = before.copy(region = "ID", dearrowEnabled = true).merge(patch)
        assertEquals("ID", merged.region); assertTrue(merged.dearrowEnabled); assertFalse(merged.showOriginalTitle)
        assertEquals(0, before.changesFrom(before).length())
    }

    @Test fun concurrentInitialReadsSharePreferenceFetchAndSuccessfulSaveWins() = runBlocking {
        val context = ApiContext("https://example.test", Account("token", "Alice", Long.MAX_VALUE, "https://example.test"))
        val entered = CompletableDeferred<Unit>(); val result = CompletableDeferred<AccountPreferences>()
        var calls = 0
        val source = OriginalTitlePreferences({ context }, { true }) { calls++; entered.complete(Unit); result.await() }
        val a = async { source.resolve(context) }; val b = async { source.resolve(context) }
        entered.await()
        source.confirm(context, false)
        result.complete(AccountPreferences(showOriginalTitle = true))
        assertFalse(a.await()); assertFalse(b.await()); assertEquals(1, calls)
        assertFalse(source.resolve(context))
    }

    @Test fun failuresDefaultOnAndDoNotEraseConfirmedChoiceOrCrossContexts() = runBlocking {
        val alice = Account("token", "Alice", Long.MAX_VALUE, "https://example.test")
        var context = ApiContext(alice.server, alice)
        val source = OriginalTitlePreferences({ context }, { false }) { throw IOException("offline") }
        assertTrue(source.resolve(context))
        source.confirm(context, false)
        assertFalse(source.resolve(context))
        assertEquals(false, source.known(context))
        val previous = context
        context = context.copy(generation = 1)
        assertNull(source.known(context)); assertNull(source.known(previous))
        assertTrue(source.resolve(context))
        source.confirm(previous, false)
        assertTrue(source.resolve(context))
        try { source.resolve(previous); fail("Stale request was accepted") } catch (_: CancellationException) { }
        context = ApiContext(alice.server, null, 2)
        assertFalse(source.resolve(context))
    }

    @Test fun canceledAccountReadNeverLeaksIntoAnotherProfile() = runBlocking {
        val account = Account("token", "Alice", Long.MAX_VALUE, "https://example.test")
        var context = ApiContext(account.server, account)
        val pending = CompletableDeferred<AccountPreferences>(); val entered = CompletableDeferred<Unit>()
        val source = OriginalTitlePreferences({ context }, { true }) { entered.complete(Unit); pending.await() }
        val request = async { source.resolve(context) }
        entered.await(); context = ApiContext(account.server, null, 1)
        pending.complete(AccountPreferences(showOriginalTitle = false))
        try { request.await(); fail("Stale response was accepted") } catch (_: CancellationException) { }
        assertTrue(source.resolve(context))
    }

    @Test fun failedInitialLookupDoesNotInvalidateAnAlreadyRunningSuccessfulRead() = runBlocking {
        val account = Account("token", "Alice", Long.MAX_VALUE, "https://example.test")
        val context = ApiContext(account.server, account)
        val source = OriginalTitlePreferences({ context }, { true }) { throw IOException("offline") }
        val inFlightRead = source.version()
        assertTrue(source.resolve(context))
        source.confirm(context, false, inFlightRead)
        assertFalse(source.resolve(context))
    }

    @Test fun allVideoReadsSelectExactlyOneChoiceAndUnrelatedRequestsStayUnchanged() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val context = ApiContext(address, null)
            var choice = false
            val source = OriginalTitlePreferences({ context }, { choice }) { error("Guest must not fetch account preferences") }
            val api = InvidiousApi({ address }, { null }, originalTitles = source)
            val paths = listOf("popular", "trending", "search", "hashtag/music", "videos/abcdefghijk", "videos/abcdefghijk/downloads",
                "channels/UCexample", "channels/UCexample/videos", "channels/UCexample/streams", "channels/UCexample/shorts",
                "channels/UCexample/latest", "channels/UCexample/search", "channels/UCexample/clips", "channels/UCexample/courses", "playlists/PLexample", "mixes/RDexample",
                "clips/CLexample", "auth/feed", "auth/history", "auth/playlists", "auth/playlists/IVexample", "auth/playlists/IVexample/feed",
                "auth/clips", "auth/saved_playlists")
            for (path in paths) {
                server.enqueue(MockResponse().setBody(if (path in listOf("popular", "trending", "search")) "[]" else "{}"))
                api.request("api/v1/$path", query = mapOf("show_original_title" to "true", "page" to "2"))
                val read = server.takeRequest()
                assertEquals(path, listOf("false"), read.requestUrl!!.queryParameterValues("show_original_title"))
                assertEquals("2", read.requestUrl!!.queryParameter("page")); assertNull(read.getHeader("Authorization"))
            }
            choice = true
            server.enqueue(MockResponse().setBody("{}")); api.request("api/v1/videos/abcdefghijk")
            assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("show_original_title"))
            for (path in listOf("api/v1/search/suggestions", "api/v1/dearrow/abcdefghijk", "api/v1/comments/abcdefghijk",
                "api/v1/videos/abcdefghijk/download", "api/v1/captions/abcdefghijk", "api/v1/auth/preferences", "api/manifest/dash/id/abcdefghijk", "sb/storyboard.vtt")) {
                server.enqueue(MockResponse().setBody("{}")); api.request(path)
                assertNull(path, server.takeRequest().requestUrl!!.queryParameter("show_original_title"))
            }
            server.enqueue(MockResponse().setBody("{}")); api.request("api/v1/auth/clips", "POST")
            assertNull(server.takeRequest().requestUrl!!.queryParameter("show_original_title"))
        }
    }

    @Test fun accountChoiceResolvesBeforePublicMetadataAndPatchUpdatesSubsequentReads() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val account = Account("token", "Alice", Long.MAX_VALUE, address)
            val context = ApiContext(address, account)
            lateinit var api: InvidiousApi
            val source = OriginalTitlePreferences({ context }, { true }) { api.preferences(it) }
            api = InvidiousApi({ address }, { account }, originalTitles = source)
            server.enqueue(MockResponse().setBody(AccountPreferences(showOriginalTitle = false).json().toString()))
            server.enqueue(MockResponse().setBody("{}")); api.video("abcdefghijk")
            val prefs = server.takeRequest(); assertEquals("/api/v1/auth/preferences", prefs.path)
            assertEquals("Bearer token", prefs.getHeader("Authorization"))
            val metadata = server.takeRequest(); assertNull(metadata.getHeader("Authorization"))
            assertEquals("false", metadata.requestUrl!!.queryParameter("show_original_title"))
            server.enqueue(MockResponse().setBody(AccountPreferences().json().toString()))
            api.preferences(JSONObject().put("show_original_title", true), context)
            assertEquals("""{"show_original_title":true}""", server.takeRequest().body.readUtf8())
            server.enqueue(MockResponse().setBody("{}")); api.video("abcdefghijk")
            assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("show_original_title"))
        }
    }

    @Test fun offlineCacheAndRssKeepTitleSelectionsSeparate() = runBlocking {
        val directory = Files.createTempDirectory("original-titles-cache").toFile()
        try { MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val context = ApiContext(address, null)
            var choice = true
            val source = OriginalTitlePreferences({ context }, { choice }) { error("No account") }
            val api = InvidiousApi({ address }, { null }, cache = ResponseCache(directory), originalTitles = source)
            server.enqueue(MockResponse().setBody("""[{"videoId":"abcdefghijk","title":"原題"}]"""))
            assertEquals("原題", api.discovery("popular").single().title)
            choice = false
            server.enqueue(MockResponse().setResponseCode(503))
            try { api.discovery("popular"); fail("Reused cache for the other choice") } catch (_: ApiException) { }
            server.enqueue(MockResponse().setBody("""[{"videoId":"abcdefghijk","title":"Translated"}]"""))
            assertEquals("Translated", api.discovery("popular").single().title)
            choice = true
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals("原題", api.discovery("popular").single().title)
            assertEquals("$address/feed/channel/UCexample?show_original_title=false", RssLinks.channel(address, "UCexample", false))
            assertEquals("$address/feed/playlist/RDopaque?continuation=abcdefghijk&show_original_title=true",
                RssLinks.playlist(address, Playlist("RDopaque", "Mix", -1, seedVideoId = "abcdefghijk"), true))
        } } finally { directory.deleteRecursively() }
    }
}
