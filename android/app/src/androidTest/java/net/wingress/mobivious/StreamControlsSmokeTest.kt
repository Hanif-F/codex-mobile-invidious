package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.player.AudioSection
import net.wingress.mobivious.player.StreamCatalog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Requires the generated rich DASH fixture, including real video/audio bitrate alternatives. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamControlsSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun open() {
        command("reset"); command("stream", """{"type":"rich"}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.save(null)
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings()
        }
        until { activity.model.browse.value.videos.isNotEmpty() }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(40_000) }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread { activity.model.closePlayer(); activity.model.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask() }
    }
    private fun showControls() {
        if (compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
        until { compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tap(forward: Boolean, double: Boolean = false) {
        compose.onNodeWithTag("player-surface").performTouchInput {
            val point = Offset(width * if (forward) .85f else .15f, height * .25f)
            if (double) doubleClick(point) else click(point)
        }
    }
    private fun selected(type: Int) = StreamCatalog.choices(activity.model.playback.value.tracks, type, activity.model.playback.value.details?.formats.orEmpty())
        .singleOrNull { it.explicitlySelected(activity.model.playback.value.selection) }
    private fun recreate() {
        val old = activity
        compose.runOnUiThread { activity.recreate() }
        until {
            var ready = false
            compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }
            ready
        }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    private fun screenshot(name: String) {
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /data/local/tmp/mobivious-stream-screenshots").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
        shell.executeShellCommand("screencap -p /data/local/tmp/mobivious-stream-screenshots/$name.png").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
    }
    @Test fun exactTracksMenusRefreshRetryAndReconnection() {
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Quality").performClick()
        screenshot("quality-portrait")
        compose.onNodeWithText("720p · 24 FPS · High Bitrate").performScrollTo().performClick()
        until { selected(C.TRACK_TYPE_VIDEO)?.height == 720 }
        val videoKey = selected(C.TRACK_TYPE_VIDEO)!!.key
        assertEquals("auto", activity.model.store.guestDeArrow().qualityDash)
        compose.onNodeWithText("Audio").performClick()
        screenshot("audio-portrait")
        compose.onNodeWithText("Stable Volume").assertExists()
        compose.onNodeWithText("Dubbed Audio").assertExists()
        compose.onNodeWithText("English Stable Volume").performScrollTo().performClick()
        until { selected(C.TRACK_TYPE_AUDIO)?.section == AudioSection.STABLE }
        val audioKey = selected(C.TRACK_TYPE_AUDIO)!!.key
        compose.onNodeWithText("Playback speed").performClick(); compose.onNodeWithText("0.25×").performClick()
        compose.onNodeWithText("Captions").performClick(); compose.onNodeWithText("English").performClick()
        compose.onNodeWithContentDescription("Close player settings").performClick()
        compose.runOnUiThread { activity.model.refreshBuffer() }
        until { activity.model.playback.value.playerState == Player.STATE_READY }
        assertEquals(videoKey, selected(C.TRACK_TYPE_VIDEO)?.key); assertEquals(audioKey, selected(C.TRACK_TYPE_AUDIO)?.key)
        compose.runOnUiThread { activity.model.retryPlayback() }
        until { !activity.model.playback.value.loading && selected(C.TRACK_TYPE_VIDEO)?.key == videoKey }
        assertEquals(audioKey, selected(C.TRACK_TYPE_AUDIO)?.key)
        assertEquals(.25f, activity.model.playback.value.speed)
        assertFalse(activity.model.controller.value!!.playWhenReady)
        assertTrue(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_TEXT))
        recreate()
        assertEquals(videoKey, selected(C.TRACK_TYPE_VIDEO)?.key)
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Quality").performClick(); compose.onNodeWithText("720p · 24 FPS · High Bitrate").assertIsSelected()
        compose.onNodeWithText("Auto").performClick(); until { selected(C.TRACK_TYPE_VIDEO) == null }
    }
    @Test fun savedQualityDefaultsSelectActualSupportedTracks() {
        for (preference in listOf("best", "worst", "360p", "4320p", "auto")) {
            compose.runOnUiThread { activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, qualityDash = preference)); activity.model.play("testvideo01", 40) }
            until { !activity.model.playback.value.loading && activity.model.playback.value.playerState == Player.STATE_READY &&
                (preference == "auto" || selected(C.TRACK_TYPE_VIDEO) != null) }
            val choices = StreamCatalog.choices(activity.model.playback.value.tracks, C.TRACK_TYPE_VIDEO)
            assertEquals(StreamCatalog.defaultVideo(choices, preference)?.key, selected(C.TRACK_TYPE_VIDEO)?.key)
        }
    }
    @Test fun consecutiveSingleTapsAccumulateAndOppositeTapResets() {
        tap(true, true); tap(true); tap(true)
        compose.runOnUiThread { assertEquals(30_000L, activity.model.pendingSeek.value?.offset); assertEquals(40_000L, activity.model.controller.value!!.currentPosition) }
        compose.onNodeWithText("+30 seconds").assertExists()
        tap(false)
        compose.runOnUiThread { assertEquals(-10_000L, activity.model.pendingSeek.value?.offset); assertEquals(40_000L, activity.model.controller.value!!.currentPosition) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 30_000L }
        assertFalse(activity.model.controller.value!!.playWhenReady)
        tap(false, true); tap(false); tap(true)
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 40_000L }
        compose.runOnUiThread { activity.model.seekTo(115_000) }
        tap(true, true); tap(true)
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position >= 119_000L }
    }
    @Test fun accumulationFreezesPlayingVideoAndCompetingActionsCancel() {
        compose.runOnUiThread { activity.model.controller.value!!.play() }
        until { activity.model.playback.value.playing }
        tap(true, true)
        compose.runOnUiThread { assertNotNull(activity.model.pendingSeek.value); assertFalse(activity.model.controller.value!!.playWhenReady) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(40_000); activity.model.accumulateSeek(1); activity.model.refreshBuffer() }
        until { activity.model.playback.value.playerState == Player.STATE_READY }
        assertNull(activity.model.pendingSeek.value); assertEquals(40_000L, activity.model.controller.value!!.currentPosition)
        compose.runOnUiThread { activity.model.accumulateSeek(1); activity.model.seekTo(15_000) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 15_000L }
        compose.runOnUiThread { activity.model.accumulateSeek(1); activity.model.controller.value!!.seekTo(25_000) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 25_000L }
        showControls(); tap(true, true)
        compose.onNodeWithContentDescription("Player settings").performClick()
        assertNull(activity.model.pendingSeek.value)
        compose.onNodeWithContentDescription("Close player settings").performClick()
        compose.runOnUiThread { activity.model.accumulateSeek(1) }
        recreate()
        until { activity.model.pendingSeek.value == null }
        assertEquals(35_000L, activity.model.controller.value!!.currentPosition)
    }
    @Test fun fullscreenKeepsAccumulationAndPipEntryCancelsIt() {
        showControls()
        compose.runOnUiThread { activity.model.accumulateSeek(1) }
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 50_000L }
        assertFalse(activity.model.controller.value!!.playWhenReady)
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Quality").performClick(); screenshot("quality-landscape")
        compose.onNodeWithContentDescription("Close player settings").performClick()
        if (activity.supportsPip()) {
            compose.runOnUiThread { activity.model.accumulateSeek(1); activity.enterPip() }
            until { activity.isInPictureInPictureMode }
            assertNull(activity.model.pendingSeek.value)
            assertEquals(50_000L, activity.model.controller.value!!.currentPosition)
        }
    }
}
