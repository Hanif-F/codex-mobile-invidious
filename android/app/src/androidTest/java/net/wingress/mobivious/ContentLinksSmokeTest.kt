package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
class ContentLinksSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    private var background = false
    private val owner = "UC" + "a".repeat(22)
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun until(block: () -> Boolean) = compose.waitUntil(30_000, block)
    private fun command(name: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun incoming(path: String) = ui { activity.startActivity(Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "http://127.0.0.1:18080$path")) }
    private fun ready() = until { !activity.model.queue.value.loading && activity.model.playback.value.playerState == Player.STATE_READY }
    @Before fun launch() {
        command("reset"); command("content-links")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            background = activity.model.store.background
            activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, continueAutoplay = false))
            activity.model.refreshSharedSettings()
        }
    }
    @After fun close() { if (::activity.isInitialized) ui { activity.model.store.background = background; activity.model.closePlayer(); activity.finishAndRemoveTask() } }

    @Test fun sharedChannelAliasesResolveAndRetryWithoutSearching() {
        command("content-links", """{"resolveFailNext":true}""")
        incoming("/@fixture/shorts")
        until { activity.model.linkResolution.value.error != null }
        compose.onNodeWithText("Retry").performClick()
        until { activity.model.route == "channel:$owner" && !activity.model.browse.value.loading }
        assertEquals(ChannelTab.SHORTS, activity.model.channelTab.value)
        assertEquals(2, fixture().getJSONArray("resolveRequests").length())
        assertEquals(0, fixture().getJSONArray("searchRequests").length())
    }
    @Test fun sharedHashtagsUseNativePaginationAndChannelLinksPreservePlayback() {
        incoming("/w/testvideo01?autoplay=1")
        ready(); until { activity.model.playback.value.playing }
        val token = activity.model.queue.value.token
        incoming("/hashtag/music")
        until { activity.model.route == "hashtag:music" && !activity.model.browse.value.loading }
        assertEquals(60, activity.model.browse.value.videos.size)
        ui { activity.model.more() }
        until { !activity.model.browse.value.loading && activity.model.browse.value.page == 2 }
        assertEquals(61, activity.model.browse.value.videos.size); assertTrue(activity.model.browse.value.end)
        assertEquals(token, activity.model.queue.value.token); assertTrue(activity.model.playback.value.playWhenReady)
    }
    @Test fun richWatchInformationAndTimestampSeekKeepTheQueueOccurrence() {
        incoming("/watch?v=testvideo01&extend_desc=1&autoplay=0")
        ready()
        compose.onNodeWithTag("watch-description-text").assertTextContains("Rich description", substring = true)
        compose.onNodeWithText("Verified channel").assertExists()
        val key = activity.model.queue.value.currentKey
        ui { activity.model.openContent(ContentLinks.resolve("/watch?v=testvideo01&t=30", activity.model.store.server, "testvideo01")!!) }
        until { activity.model.playback.value.position == 30000L }
        assertEquals(key, activity.model.queue.value.currentKey)
        compose.onNodeWithTag("video-information-toggle").assertDoesNotExist()
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("video-license"))
        compose.onNodeWithText("License: Standard YouTube license").assertExists()
        compose.onNodeWithTag("watch-description-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("watch-description-text").assertDoesNotExist()
        compose.onNodeWithTag("video-information").assertDoesNotExist()
        compose.onNodeWithTag("watch-description-toggle").performClick()
        compose.onNodeWithTag("video-license").assertExists()
    }
    @Test fun boundsPauseWithoutAdvancingAndReplayStartsAtTheRequestedTime() {
        incoming("/watch?v=testvideo01&list=PLlive&t=2.345&end=5.678&autoplay=1&continue_autoplay=1")
        ready()
        val key = activity.model.queue.value.currentKey
        ui { activity.model.controller.value!!.seekTo(20000) }
        until { activity.model.playback.value.position == 5678L && !activity.model.playback.value.playWhenReady }
        assertEquals(key, activity.model.queue.value.currentKey)
        ui { activity.model.controller.value!!.play() }
        until { activity.model.playback.value.playWhenReady && activity.model.playback.value.position in 2345L..4500L }
        ui { activity.model.refreshBuffer() }
        ready(); assertEquals(5678L, activity.model.queue.value.current!!.linkPlayback!!.endMs)
    }
    @Test fun boundedLoopAndBackgroundModeKeepBoundsInTheService() {
        ui { activity.model.store.background = true }
        incoming("/watch?v=testvideo01&t=2&end=4&loop=1&autoplay=1")
        ready(); until { activity.model.playback.value.playing }
        val key = activity.model.queue.value.currentKey
        ui { activity.model.controller.value!!.seekTo(8000); activity.moveTaskToBack(true) }
        until { activity.model.playback.value.position in 2000L..3999L && activity.model.playback.value.playWhenReady }
        assertEquals(key, activity.model.queue.value.currentKey)
        assertEquals(4000L, activity.model.queue.value.current!!.linkPlayback!!.endMs)
    }
    @Test fun recreationRetainsOverridesAndSuccessorsKeepOnlyCarriedOptions() {
        incoming("/watch?v=testvideo01&list=PLlive&t=1.234&listen=1&speed=1.5&local=0&extend_desc=1&region=ID&autoplay=0")
        ready()
        val token = activity.model.queue.value.token
        val requests = fixture().getJSONArray("videoDetailRequests").length()
        ui { activity.recreate() }
        until {
            var resumed: MainActivity? = null
            ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
            if (resumed != null) activity = resumed!!
            resumed != null && activity.model.controller.value != null
        }
        assertEquals(token, activity.model.queue.value.token)
        assertEquals(requests, fixture().getJSONArray("videoDetailRequests").length())
        assertEquals(1234L, activity.model.queue.value.current!!.linkPlayback!!.startMs)
        assertEquals(1f, activity.model.store.guestDeArrow().speed)
        ui { activity.model.nextQueue(1) }; ready()
        until { activity.model.playback.value.details?.video?.id == "testvideo02" }
        val effective = activity.model.queue.value.effective(activity.model.preferences.value)
        assertTrue(effective.listen); assertEquals(1.5f, effective.speed); assertFalse(effective.local); assertFalse(effective.extendDescription)
        assertNull(activity.model.queue.value.current!!.linkPlayback)
        val shared = VideoLinks.parse(VideoLinks.share(activity.model.store.server, activity.model.queue.value, 5000)!!, activity.model.store.server)!!
        assertEquals("PLlive", shared.playlistId); assertEquals(1, shared.index)
    }

    @Test fun sourceOnlyLinksApplyOptionsAndBoundsToTheFirstOccurrence() {
        incoming("/playlist?list=PLlive&t=1.234&end=8&listen=1&autoplay=0&extend_desc=1")
        until { activity.model.route == "playlist:PLlive" && !activity.model.browse.value.loading }
        ui { activity.model.play("", source = "PLlive") }
        ready()
        val current = activity.model.queue.value.current!!
        val linked = current.linkPlayback!!
        assertEquals("testvideo01", current.video.id)
        assertEquals(1234L, linked.startMs)
        assertEquals(8000L, linked.endMs)
        assertTrue(linked.options.description == true)
        assertFalse(activity.model.playback.value.playWhenReady)
    }
}
