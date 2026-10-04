package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChannelTest {
    private val id = "UC" + "a".repeat(22)
    private suspend fun page(api: InvidiousApi, tab: ChannelTab, continuation: String = ""): Page<Video> = when (tab) {
        ChannelTab.VIDEOS -> api.channelVideos(id, continuation)
        ChannelTab.STREAMS -> api.channelStreams(id, continuation)
        ChannelTab.PLAYLISTS -> error("Playlist pages have a separate result type")
    }

    @Test fun metadataKeepsAdvertisedTabsAndIgnoresMalformedEntries() {
        val channel = ApiParser.channel(JSONObject("""{"authorId":"$id","author":"WAN Show","tabs":["streams","podcasts",null,42,"posts"]}"""))
        assertEquals(id, channel.id)
        assertEquals(listOf("streams", "podcasts", "posts"), channel.tabs)
        assertEquals(listOf(ChannelTab.STREAMS), channel.contentTabs)
        assertEquals(ChannelTab.STREAMS, channel.preferredTab())
    }

    @Test fun supportedTabsUseStableOrderAndPreferUploads() {
        listOf(
            listOf("videos") to listOf(ChannelTab.VIDEOS),
            listOf("streams") to listOf(ChannelTab.STREAMS),
            listOf("streams", "posts", "videos", "streams") to listOf(ChannelTab.VIDEOS, ChannelTab.STREAMS),
            listOf("shorts", "podcasts") to listOf(ChannelTab.VIDEOS),
            emptyList<String>() to listOf(ChannelTab.VIDEOS)
        ).forEach { (advertised, expected) ->
            val channel = Channel(id, "Channel", tabs = advertised)
            assertEquals(expected, channel.contentTabs)
            assertEquals(expected.first(), channel.preferredTab())
        }
        assertEquals(ChannelTab.VIDEOS, ApiParser.channel(JSONObject("{}")).preferredTab())
        assertEquals(ChannelTab.VIDEOS, ApiParser.channel(JSONObject("""{"tabs":"streams"}""")).preferredTab())
    }

    @Test fun refreshPreservesAnAvailableSelectionAndFallsBackWhenItDisappears() {
        val mixed = Channel(id, "Channel", tabs = listOf("videos", "streams"))
        assertEquals(ChannelTab.STREAMS, mixed.preferredTab(ChannelTab.STREAMS))
        assertEquals(ChannelTab.VIDEOS, mixed.preferredTab(ChannelTab.VIDEOS))
        assertEquals(ChannelTab.VIDEOS, mixed.copy(tabs = listOf("videos")).preferredTab(ChannelTab.STREAMS))
        assertEquals(ChannelTab.STREAMS, mixed.copy(tabs = listOf("streams")).preferredTab(ChannelTab.VIDEOS))
    }

    @Test fun firstPagesOmitDefaultEmptyAndWhitespaceTokensOnBothEndpoints() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("private-token", "Viewer", Long.MAX_VALUE, address) })
            listOf(ChannelTab.VIDEOS, ChannelTab.STREAMS).forEach { tab ->
                repeat(3) { variant ->
                    server.enqueue(MockResponse().setBody("""{"videos":[]}"""))
                    val result = when (variant) {
                        0 -> page(api, tab)
                        1 -> page(api, tab, "")
                        else -> page(api, tab, " \t\n")
                    }
                    val request = server.takeRequest()
                    assertEquals("/api/v1/channels/$id/${tab.path}", request.path)
                    assertFalse(request.requestUrl!!.queryParameterNames.contains("continuation"))
                    assertNull(request.getHeader("Authorization"))
                    assertTrue(result.items.isEmpty())
                    assertEquals("", result.continuation)
                }
            }
        }
    }

    @Test fun followUpPagesPreserveOpaqueTokensAndParseVideosAndEndOfList() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = InvidiousApi({ server.url("/").toString().trimEnd('/') }, { null })
            val token = " next+/=%25&終 "
            listOf(ChannelTab.VIDEOS, ChannelTab.STREAMS).forEach { tab ->
                server.enqueue(MockResponse().setBody(JSONObject().put("videos", org.json.JSONArray("""[{"videoId":"abcdefghijk","title":"First","liveNow":true}]""")).put("continuation", token).toString()))
                val first = page(api, tab)
                server.takeRequest()
                assertEquals(token, first.continuation)
                assertEquals("abcdefghijk", first.items.single().id)
                assertTrue(first.items.single().live)
                server.enqueue(MockResponse().setBody("""{"videos":[{"videoId":"bbbbbbbbbbb","title":"Last"}],"continuation":null}"""))
                val last = page(api, tab, first.continuation)
                val request = server.takeRequest()
                assertEquals("/api/v1/channels/$id/${tab.path}", request.requestUrl!!.encodedPath)
                assertEquals(setOf("continuation"), request.requestUrl!!.queryParameterNames)
                assertEquals(token, request.requestUrl!!.queryParameter("continuation"))
                assertEquals("bbbbbbbbbbb", last.items.single().id)
                assertEquals("", last.continuation)
            }
        }
    }

    @Test fun transientErrorsCanRetryTheSameSelectedEndpointFromItsFirstPage() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = InvidiousApi({ server.url("/").toString().trimEnd('/') }, { null })
            listOf(ChannelTab.VIDEOS, ChannelTab.STREAMS).forEach { tab ->
                server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"Temporary channel failure"}"""))
                try { page(api, tab); fail("Channel failure ignored") }
                catch (error: ApiException) { assertEquals(503, error.status); assertEquals("Temporary channel failure", error.message) }
                val failed = server.takeRequest()
                server.enqueue(MockResponse().setBody("""{"videos":[{"videoId":"abcdefghijk","title":"Recovered"}]}"""))
                assertEquals("Recovered", page(api, tab).items.single().title)
                assertEquals(failed.path, server.takeRequest().path)
            }
        }
    }
}
