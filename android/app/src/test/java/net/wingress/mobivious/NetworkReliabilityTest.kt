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
