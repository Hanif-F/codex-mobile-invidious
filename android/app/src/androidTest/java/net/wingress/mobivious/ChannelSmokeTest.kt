package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.ChannelTab
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Requires the disposable localhost fixture and adb reverse, like AppSmokeTest. */
@RunWith(AndroidJUnit4::class)
class ChannelSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private fun until(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun requests(): List<JSONObject> = fixture().getJSONArray("channelRequests").let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun command(body: String) {
        (URL("http://127.0.0.1:18080/test/channel").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        (URL("http://127.0.0.1:18080/test/reset").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; inputStream.close(); disconnect()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.store.save(null)
            activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings()
            activity.model.navigate("Home")
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.finishAndRemoveTask()
        }
    }
    private fun openChannel() {
        compose.onNodeWithText("Mobivious Studio", substring = true).performClick()
        until { !activity.model.browse.value.loading && activity.model.channel.value != null }
    }
    private fun choose(tab: ChannelTab) {
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("channel-tab-${tab.path}"))
        compose.onNodeWithTag("channel-tab-${tab.path}").performClick()
        until { !activity.model.browse.value.loading && activity.model.channelTab.value == tab }
    }
    private fun loadMore() {
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").performClick()
    }

    @Test fun channelNavigationSwitchingAndIndependentPagination() {
        openChannel()
        assertNull(activity.model.browse.value.error)
        compose.onNodeWithTag("channel-tab-videos").assertIsSelected()
        compose.onNodeWithTag("channel-tab-streams").assertIsNotSelected()
        loadMore()
        until { activity.model.browse.value.end }
        assertEquals(listOf("testvideo01", "testvideo02"), activity.model.browse.value.videos.map { it.id })
        compose.onNodeWithText("Load more").assertDoesNotExist()
        choose(ChannelTab.STREAMS)
        assertEquals(listOf("streamvid01"), activity.model.browse.value.videos.map { it.id })
        compose.onNodeWithTag("channel-tab-streams").assertIsSelected()
        loadMore()
        until { activity.model.browse.value.end }
        assertEquals(listOf("streamvid01", "streamvid02"), activity.model.browse.value.videos.map { it.id })
        choose(ChannelTab.VIDEOS)
        assertEquals(listOf("testvideo01"), activity.model.browse.value.videos.map { it.id })
        val pages = requests().filter { it.getString("tab") != "metadata" }
        assertEquals(listOf("videos", "videos", "streams", "streams", "videos"), pages.map { it.getString("tab") })
        assertEquals(listOf(null, "videos+/page=2%&", null, "streams+/page=2%&", null), pages.map { if (it.isNull("continuation")) null else it.getString("continuation") })
    }

    @Test fun fullDescriptionUsesLoadedMetadataAndClosesWhenLeavingTheChannel() {
        val description = (1..30).joinToString("\n\n") { "Channel description paragraph $it." } + "\n\nFinal channel line."
        command(JSONObject().put("channelDescription", description).toString())
        openChannel()
        val reads = requests().size
        compose.onNodeWithTag("channel-description-open").performClick()
        compose.onNodeWithTag("channel-description-text").assertTextEquals(description)
        compose.onNodeWithContentDescription("Close channel description").performClick()
        assertEquals(reads, requests().size)
        compose.onNodeWithTag("channel-description-open").performClick()
        compose.runOnUiThread { activity.model.navigate("Home") }
        until { activity.model.channel.value == null && !activity.model.browse.value.loading }
        compose.onNodeWithTag("channel-description-sheet").assertDoesNotExist()
    }

    @Test fun streamsOnlyChannelsOpenOnStreamsAndRetryPreservesTheSelection() {
        command("""{"channelTabs":["streams","podcasts","posts"]}""")
        openChannel()
        compose.onNodeWithTag("channel-tab-videos").assertDoesNotExist()
        compose.onNodeWithTag("channel-tab-streams").assertIsSelected()
        assertEquals("Channel stream one", activity.model.browse.value.videos.single().title)
        command("""{"channelTabs":["videos","streams","posts"]}""")
        compose.runOnUiThread { activity.model.refresh() }
        until { !activity.model.browse.value.loading }
        compose.onNodeWithTag("channel-tab-videos").assertIsNotSelected()
        compose.onNodeWithTag("channel-tab-streams").assertIsSelected()
        command("""{"channelFailNext":true}""")
        compose.runOnUiThread { activity.model.refresh() }
        until { activity.model.browse.value.error != null }
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Retry"))
        compose.onNodeWithText("Fixture channel temporarily unavailable").assertExists()
        compose.onNodeWithText("Retry").performClick()
        until { !activity.model.browse.value.loading && activity.model.browse.value.error == null }
        assertEquals(ChannelTab.STREAMS, activity.model.channelTab.value)
        assertEquals(listOf("streamvid01"), activity.model.browse.value.videos.map { it.id })
        val pages = requests().filter { it.getString("tab") != "metadata" }
        assertEquals(4, pages.size)
        assertTrue(pages.all { it.getString("tab") == "streams" && it.isNull("continuation") })
        command("""{"channelTabs":["videos"]}""")
        compose.runOnUiThread { activity.model.refresh() }
        until { !activity.model.browse.value.loading }
        assertEquals(ChannelTab.VIDEOS, activity.model.channelTab.value)
        assertEquals("testvideo01", activity.model.browse.value.videos.single().id)
    }

    @Test fun delayedPagesCannotReplaceTheSelectedTabOrAppendToAnotherChannelRoute() {
        openChannel()
        command("""{"channelDelayNext":{"tab":"streams","millis":1500}}""")
        compose.onNodeWithTag("channel-tab-streams").performClick()
        until { requests().any { it.getString("tab") == "streams" && !it.getBoolean("completed") } }
        choose(ChannelTab.VIDEOS)
        until { requests().all { it.getBoolean("completed") } }
        compose.waitForIdle()
        assertEquals(ChannelTab.VIDEOS, activity.model.channelTab.value)
        assertEquals(listOf("testvideo01"), activity.model.browse.value.videos.map { it.id })
        command("""{"channelDelayNext":{"tab":"videos","millis":1500}}""")
        loadMore()
        until { requests().any { it.getString("tab") == "videos" && !it.getBoolean("completed") } }
        compose.onNodeWithContentDescription("Back").performClick()
        until { activity.model.route.isEmpty() && !activity.model.browse.value.loading }
        until { requests().all { it.getBoolean("completed") } }
        compose.waitForIdle()
        assertNull(activity.model.channel.value)
        assertNull(activity.model.channelTab.value)
        assertEquals(listOf("testvideo01"), activity.model.browse.value.videos.map { it.id })
        assertEquals("", activity.model.browse.value.continuation)
    }

    @Test fun delayedMetadataCannotRestoreAChannelAfterLeavingIt() {
        command("""{"channelDelayNext":{"tab":"metadata","millis":1500}}""")
        compose.onNodeWithText("Mobivious Studio", substring = true).performClick()
        until { requests().any { it.getString("tab") == "metadata" && !it.getBoolean("completed") } }
        compose.onNodeWithContentDescription("Back").performClick()
        until { activity.model.route.isEmpty() && !activity.model.browse.value.loading }
        until { requests().all { it.getBoolean("completed") } }
        compose.waitForIdle()
        assertNull(activity.model.channel.value)
        assertNull(activity.model.channelTab.value)
        assertEquals(listOf("testvideo01"), activity.model.browse.value.videos.map { it.id })
        assertEquals(listOf("metadata"), requests().map { it.getString("tab") })
    }
}
