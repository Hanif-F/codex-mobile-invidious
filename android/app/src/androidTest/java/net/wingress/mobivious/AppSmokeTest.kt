package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.URL

/** Requires scripts/fixture-server.py on port 18080 and `adb reverse tcp:18080 tcp:18080`. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(timeout: Long = 20_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)
    private fun fixture(): JSONObject = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Test fun accountBrowsingAndServicePlayback() {
        compose.runOnUiThread { compose.activity.model.switchServer("http://127.0.0.1:18080") }
        waitFor { compose.activity.model.browse.value.videos.isNotEmpty() }
        compose.onNodeWithContentDescription("Account").performClick()
        compose.onNodeWithText("Username").performTextInput("EmulatorViewer")
        compose.onNodeWithText("Password").performTextInput("fixture-password")
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performClick()
        waitFor { compose.activity.model.account.value != null }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        waitFor(40_000) { compose.activity.model.playback.value.playing }
        assertNull(compose.activity.model.playback.value.error)
        waitFor { fixture().getJSONArray("watched").length() == 1 }
        compose.runOnUiThread { compose.activity.model.controller.value!!.seekTo(30_000) }
        waitFor { fixture().getLong("position") >= 29 }
        compose.onNodeWithText("Player", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Speed: 1.0").performClick()
        compose.onNodeWithText("1.5").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnUiThread { assertEquals(1.5f, compose.activity.model.controller.value!!.playbackParameters.speed) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Title").performTextInput("Emulator playlist")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        waitFor { compose.activity.model.playlists.value.any { it.title == "Emulator playlist" } }
        compose.onNodeWithText("Emulator playlist").performClick()
        compose.onNodeWithText("Edit playlist").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Renamed playlist")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        waitFor { compose.activity.model.playlist.value?.title == "Renamed playlist" }
        assertEquals(1, fixture().getJSONArray("playlists").length())
        compose.runOnUiThread { compose.activity.model.controller.value!!.pause() }
        assertTrue(fixture().getLong("position") >= 29)
        compose.runOnUiThread { compose.activity.model.controller.value!!.play() }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        compose.runOnUiThread { compose.activity.enterPip() }
        waitFor { compose.activity.isInPictureInPictureMode }
        assertTrue(compose.activity.model.controller.value!!.playWhenReady)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        waitFor { !compose.activity.isInPictureInPictureMode }
        compose.runOnUiThread { compose.activity.model.store.pip = false; compose.activity.model.store.background = true; compose.activity.moveTaskToBack(true) }
        Thread.sleep(2000)
        assertTrue(compose.activity.model.controller.value!!.playWhenReady)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        waitFor { compose.activity.model.playback.value.playing }
        compose.runOnUiThread { compose.activity.model.closePlayer() }
    }
}
