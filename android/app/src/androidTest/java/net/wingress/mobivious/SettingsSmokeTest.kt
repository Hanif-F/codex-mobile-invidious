package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.net.URL
import net.wingress.mobivious.data.AccountPreferences
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SettingsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread { activity.model.closePlayer(); activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings(); activity.model.navigate("Home") }
        until { activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread { activity.model.closePlayer(); activity.model.store.save(null); activity.model.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask() }
    }
    private fun open(page: String) { compose.onNodeWithTag("navigation-Account").performClick(); compose.onNodeWithTag("account-settings").performClick(); compose.onNodeWithText(page).performScrollTo().performClick() }
    private fun toggle(label: String) { compose.onNodeWithText(label).performScrollTo().performClick() }
    private fun save(page: String) { compose.onNodeWithTag("settings-save").performClick(); until { compose.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithContentDescription("Back from Settings").performClick() }

    @Test fun fullScreenNavigationAndDraftSurviveRecreation() {
        open("Browsing")
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        toggle("Show related videos")
        compose.runOnUiThread { activity.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== activity } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        save("Browsing")
        assertFalse(activity.model.store.guestDeArrow().relatedVideos)
        compose.onNodeWithTag("navigation-Account").performClick(); compose.onNodeWithTag("account-settings").performClick()
        compose.onNodeWithText("Playback").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back from Playback").performClick()
        compose.onNodeWithTag("settings-root").assertExists()
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        compose.onNodeWithTag("account-screen").assertExists()
    }

    @Test fun guestPlaybackDefaultsAndLocalResumeReachPlayer() {
        open("Playback"); toggle("Autoplay opened videos"); toggle("Audio only by default"); save("Playback")
        open("History & library"); toggle("Remember playback position"); save("History & library")
        compose.runOnUiThread { activity.model.store.position("testvideo01", 35) }
        compose.onNodeWithTag("navigation-Home").performClick()
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.details != null && activity.model.playback.value.duration > 0 }
        assertFalse(activity.model.playback.value.playWhenReady)
        assertTrue(activity.model.playback.value.position in 34_000..36_000)
        compose.runOnUiThread { assertTrue(activity.model.controller.value!!.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO)) }
        val events = state().getJSONArray("events")
        assertFalse((0 until events.length()).any { events.getJSONObject(it).optString("path") == "/api/v1/auth/preferences" })
    }

    @Test fun failedSharedSaveKeepsDraftAndRetryPreservesOtherPreferences() {
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null && activity.model.tab == "Account" && !activity.model.accountBusy.value }
        open("Browsing"); toggle("Show related videos")
        command("sponsorblock", """{"failPreferences":true}""")
        compose.onNodeWithTag("settings-save").performClick()
        until { compose.onAllNodesWithTag("settings-save-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back from Browsing").assertExists()
        command("sponsorblock", """{"failPreferences":false}""")
        save("Browsing")
        val prefs = state().getJSONObject("preferences")
        assertFalse(prefs.getBoolean("related_videos")); assertEquals("preserved", prefs.getString("unrelated_setting"))
        open("Subscriptions")
        toggle("Latest video from each channel"); toggle("Unwatched videos only"); save("Subscriptions")
        assertTrue(state().getJSONObject("preferences").getBoolean("latest_only")); assertTrue(state().getJSONObject("preferences").getBoolean("unseen_only"))
    }
    @Test fun codecSettingPrecedesQualityAndSyncsInBothDirections() {
        fun option(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isPopup()))
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null && activity.model.tab == "Account" && !activity.model.accountBusy.value }
        open("Playback")
        compose.onNodeWithText("Preferred video codec").performScrollTo()
        assertTrue(compose.onNodeWithText("Preferred video codec").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("Default quality").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("Preferred video codec").performClick()
        option("Auto").assertIsSelected(); option("H.264").assertExists()
        option("AV1").performClick(); save("Playback")
        assertEquals("av1", state().getJSONObject("preferences").getString("video_codec"))
        assertEquals("preserved", state().getJSONObject("preferences").getString("unrelated_setting"))
        command("preferences", """{"video_codec":"h264"}""")
        compose.runOnUiThread { activity.model.refreshSharedSettings() }
        until { activity.model.preferences.value.videoCodec == "h264" }
        open("Playback"); compose.onNodeWithText("Preferred video codec").performScrollTo().performClick()
        option("H.264").assertIsSelected()
        option("Auto").performClick(); save("Playback")
        assertEquals("auto", state().getJSONObject("preferences").getString("video_codec"))
    }
}
