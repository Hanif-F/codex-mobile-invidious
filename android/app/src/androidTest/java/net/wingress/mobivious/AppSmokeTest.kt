package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.URL

/** Requires scripts/fixture-server.py on port 18080 and `adb reverse tcp:18080 tcp:18080`. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    @Before fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    @After fun closeActivity() {
        if (::activity.isInitialized) InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.model.closePlayer(); activity.updatePip(false); activity.finishAndRemoveTask() }
    }
    private fun waitFor(timeout: Long = 20_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)
    private fun fixture(): JSONObject = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply { requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect() }
    }
    @Test fun hlsExplicitResumeCaptionsAndAudioOnly() {
        command("reset"); command("stream", "{\"type\":\"hls\"}")
        compose.runOnUiThread { activity.model.switchServer("http://127.0.0.1:18080"); activity.model.action {
            activity.model.login("HlsViewer", "fixture-password")
            activity.model.api.position("testvideo01", 90)
            activity.model.play("testvideo01", 40)
        } }
        waitFor(40_000) { activity.model.playback.value.playing }
        compose.runOnUiThread {
            val controller = activity.model.controller.value!!
            assertTrue(controller.currentPosition in 40_000..55_000)
            activity.model.captions("en")
            activity.model.audioOnly(true)
            assertEquals("en", controller.trackSelectionParameters.preferredTextLanguages.first())
            assertTrue(controller.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO))
        }
        waitFor { activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.audioOnly(false); activity.model.closePlayer(); activity.updatePip(false) }
    }
    @Test fun accountBrowsingAndServicePlayback() {
        command("reset")
        compose.runOnUiThread { activity.model.switchServer("http://127.0.0.1:18080") }
        waitFor { activity.model.browse.value.videos.isNotEmpty() }
        compose.onNodeWithContentDescription("Account").performClick()
        compose.onNodeWithText("Username").performTextInput("EmulatorViewer")
        compose.onNodeWithText("Password").performTextInput("fixture-password")
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performClick()
        waitFor { activity.model.account.value != null }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        waitFor(40_000) { activity.model.playback.value.playing }
        assertNull(activity.model.playback.value.error)
        waitFor { fixture().getJSONArray("watched").length() == 1 }
        compose.runOnUiThread { activity.model.controller.value!!.seekTo(30_000) }
        waitFor { fixture().getLong("position") >= 29 }
        compose.onNodeWithText("Player", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Speed: 1.0").performClick()
        compose.onNodeWithText("1.5").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnUiThread { assertEquals(1.5f, activity.model.controller.value!!.playbackParameters.speed) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.onNodeWithContentDescription("Library", useUnmergedTree = true).performClick()
        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Title").performTextInput("Emulator playlist")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        waitFor { activity.model.playlists.value.any { it.title == "Emulator playlist" } }
        compose.onNodeWithText("Emulator playlist").performClick()
        compose.onNodeWithText("Edit playlist").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Renamed playlist")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        waitFor { activity.model.playlist.value?.title == "Renamed playlist" }
        assertEquals(1, fixture().getJSONArray("playlists").length())
        compose.runOnUiThread { activity.model.controller.value!!.pause() }
        assertTrue(fixture().getLong("position") >= 29)
        compose.runOnUiThread { activity.model.controller.value!!.play() }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        compose.runOnUiThread { activity.enterPip() }
        waitFor { activity.isInPictureInPictureMode }
        Thread.sleep(1500) // Allow the system PiP enter animation to finish before reopening.
        compose.runOnUiThread { assertTrue(activity.model.controller.value!!.playWhenReady) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Simulate a user opening the launcher. App-originated background launches
        // are restricted on recent Android versions and don't represent this action.
        fun reopen() { instrumentation.uiAutomation.executeShellCommand("am start -n ${context.packageName}/net.wingress.mobivious.MainActivity --activity-clear-top").let { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } } }
        reopen()
        waitFor { !activity.isInPictureInPictureMode }
        compose.runOnUiThread { activity.model.store.pip = false; activity.updatePip(false); activity.model.store.background = true; activity.moveTaskToBack(true) }
        Thread.sleep(2000)
        compose.runOnUiThread { assertTrue(activity.model.controller.value!!.playWhenReady) }
        reopen()
        waitFor { !activity.isInPictureInPictureMode && activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        waitFor { activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.closePlayer(); activity.updatePip(false) }
        compose.waitForIdle()
        // Remove the formerly pinned task before ActivityScenario's cleanup.
        compose.runOnUiThread { activity.finishAndRemoveTask() }
    }
}
