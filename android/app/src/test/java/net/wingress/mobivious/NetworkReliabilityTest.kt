package net.wingress.mobivious

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NetworkReliabilityTest {
    @Test fun invalidVideoResponsesAreActionableAndAnExplicitRetryCanRecoverWithoutPreviews() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { null })
            for (body in listOf("", " \n\t", "<html>Proxy failure</html>", "{broken", "[]", "null")) {
                val requests = server.requestCount
                server.enqueue(MockResponse().setBody(body))
                val error = runCatching { api.video("testvideo01") }.exceptionOrNull()
                assertTrue(error is ApiException)
                assertEquals(502, (error as ApiException).status)
                assertEquals("The instance returned invalid video information. Try again.", error.message)
                assertEquals(requests + 1, server.requestCount)

                server.enqueue(MockResponse().setBody("""{"videoId":"testvideo01","title":"Post-live recording","dashUrl":"/media/dash.mpd","storyboards":[]}"""))
                val details = api.video("testvideo01")
                assertEquals("testvideo01", details.video.id)
                assertEquals("/media/dash.mpd", details.dash)
                assertTrue(details.storyboards.isEmpty())
            }
        }
    }

    @Test fun videoHttpErrorsAndSuccessfulEmptyWritesKeepTheirExistingContracts() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val account = Account("fixture", "Viewer", Long.MAX_VALUE, address)
            val api = InvidiousApi({ address }, { account })
            for (status in listOf(404, 500)) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error":"Video unavailable"}"""))
                val error = runCatching { api.video("testvideo01") }.exceptionOrNull()
                assertTrue(error is ApiException)
                assertEquals(status, (error as ApiException).status)
                assertEquals("Video unavailable", error.message)
            }
            server.enqueue(MockResponse().setResponseCode(204))
            api.removeHistory("testvideo01")
        }
    }

    @Test fun explicitRefreshReportsFailuresWithoutDiscardingTheNormalOfflineFallback() = runBlocking {
        for (authenticated in listOf(false, true)) MockWebServer().use { server ->
            val directory = Files.createTempDirectory("mobivious-refresh-network").toFile()
            try {
                val address = server.url("/").toString().trimEnd('/')
                val account = if (authenticated) Account("fixture", "Viewer", Long.MAX_VALUE, address) else null
                val events = mutableListOf<Boolean>()
                val api = InvidiousApi({ address }, { account }, cache = ResponseCache(directory), onOffline = { events.add(it) })
                val read: suspend (Boolean) -> List<Video> = if (authenticated)
                    { fallback -> api.feed(1, allowCachedFallback = fallback) }
                else { fallback -> api.discovery("popular", allowCachedFallback = fallback) }
                val rows = "[{\"videoId\":\"testvideo01\",\"title\":\"Saved\"}]"
                server.enqueue(MockResponse().setBody(if (authenticated) "{\"videos\":$rows}" else rows))
                assertEquals("Saved", read(true).single().title)
                val snapshot = directory.listFiles()!!.single().readText()
                for (response in listOf(MockResponse().setResponseCode(503).setBody("{\"error\":\"Try again\"}"),
                    MockResponse().setBody("<html>Proxy failure</html>"))) {
                    server.enqueue(response)
                    assertTrue(runCatching { read(false) }.exceptionOrNull() is ApiException)
                    assertEquals(snapshot, directory.listFiles()!!.single().readText())
                    assertEquals(listOf(false), events)
                }
                server.enqueue(MockResponse().setResponseCode(503))
                assertEquals("Saved", read(true).single().title)
                assertEquals(listOf(false, true), events)
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun cancellingAResponseBodyReleasesTheCallAndDoesNotPublishOrCache() = runBlocking {
        MockWebServer().use { server ->
            val directory = Files.createTempDirectory("mobivious-network").toFile()
            try {
                val address = server.url("/").toString().trimEnd('/')
                val client = OkHttpClient()
                val events = mutableListOf<Boolean>()
                val api = InvidiousApi({ address }, { null }, client = client, cache = ResponseCache(directory), onOffline = { events.add(it) })
                server.enqueue(MockResponse().setBody("[" + " ".repeat(100) + "]").throttleBody(1, 1, TimeUnit.SECONDS))
                val request = launch { api.discovery("popular") }
                withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
                delay(100)
                withTimeout(1500) {
                    request.cancelAndJoin()
                    while (client.dispatcher.runningCallsCount() != 0) delay(10)
                }
                assertTrue(events.isEmpty())
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun oldSessionSuccessAndFailureCannotChangeSnapshotsOrOfflineStatus() = runBlocking {
        MockWebServer().use { server ->
            val directory = Files.createTempDirectory("mobivious-network").toFile()
            try {
                val address = server.url("/").toString().trimEnd('/')
                var generation = 0L
                val events = mutableListOf<Boolean>()
                val api = InvidiousApi({ address }, { null }, cache = ResponseCache(directory), generation = { generation }, onOffline = { events.add(it) })
                server.enqueue(MockResponse().setBody("[]")); api.discovery("popular")
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                events.clear()
                val snapshot = directory.listFiles()!!.single().readText()
                for (code in listOf(200, 500)) {
                    server.enqueue(MockResponse().setResponseCode(code).setBody("[{\"videoId\":\"testvideo01\"}]").setBodyDelay(150, TimeUnit.MILLISECONDS))
                    val result = async { runCatching { api.discovery("popular") } }
                    withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
                    generation++
                    assertTrue(result.await().exceptionOrNull() is CancellationException)
                    assertTrue(events.isEmpty())
                    assertEquals(snapshot, directory.listFiles()!!.single().readText())
                }
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun successfulHtmlDoesNotPoisonTheLastUsableFeed() = runBlocking {
        MockWebServer().use { server ->
            val directory = Files.createTempDirectory("mobivious-network").toFile()
            try {
                val address = server.url("/").toString().trimEnd('/')
                val events = mutableListOf<Boolean>()
                val api = InvidiousApi({ address }, { null }, cache = ResponseCache(directory), onOffline = { events.add(it) })
                server.enqueue(MockResponse().setBody("[{\"videoId\":\"testvideo01\",\"title\":\"Saved\"}]"))
                assertEquals("Saved", api.discovery("popular").single().title)
                server.enqueue(MockResponse().setBody("<html>Proxy error</html>"))
                assertEquals("Saved", api.discovery("popular").single().title)
                assertEquals(listOf(false, true), events)
                assertTrue(directory.listFiles()!!.single().readText().startsWith("["))
            } finally { directory.deleteRecursively() }
        }
    }
}
