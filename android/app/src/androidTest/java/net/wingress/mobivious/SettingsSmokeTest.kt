package net.wingress.mobivious

import android.content.Intent
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.net.URL
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.data.AccentPreset
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
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.guestPreferences(AccountPreferences()); activity.model.refreshSharedSettings(); activity.model.navigate("Popular")
        }
        until { activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.store.guestPreferences(AccountPreferences()); activity.finishAndRemoveTask()
        }
    }
    private fun open(page: String) { compose.selectFeed("You"); compose.openAppSettings(); compose.onNodeWithText(page).performScrollTo().performClick() }
    private fun toggle(label: String) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
    }
    private fun save(page: String) {
        until {activity.model.preferenceSaves.edits.value.isEmpty()}
        compose.onNodeWithContentDescription("Back from $page").performClick()
        compose.onNodeWithTag("settings-root").assertExists()
        compose.onNodeWithContentDescription("Back from Settings").performClick()
    }
    private fun recreate() {
        val previous=activity;compose.runOnUiThread {activity.recreate()}
        until {var ready=false;compose.runOnUiThread {ready=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any {it is MainActivity && it!==previous}};ready}
        compose.runOnUiThread {activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()}
    }
    @Test fun seekIntervalPersistsForGuestsAndAccountsAndPreservesFailedSaves() {
        fun option(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isPopup()))
        open("Playback")
        compose.onNodeWithText("Seek interval").performScrollTo().performClick()
        option("10 seconds").assertIsSelected()
        for (seconds in listOf(5, 10, 20, 30)) option("$seconds seconds").assertExists()
        option("20 seconds").performClick(); save("Playback")
        assertEquals(20, SessionStore(activity).guestPreferences().seekSeconds)
        recreate(); open("Playback")
        compose.onNodeWithText("Seek interval").performScrollTo().performClick(); option("20 seconds").assertIsSelected()
        option("30 seconds").performClick(); save("Playback")
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null && activity.model.tab == "You" && !activity.model.accountBusy.value }
        open("Playback")
        command("sponsorblock", """{"failPreferences":true}""")
        compose.onNodeWithText("Seek interval").performScrollTo().performClick(); option("10 seconds").assertIsSelected()
        option("5 seconds").performClick()
        until { activity.model.preferenceSaves.edits.value["seek_seconds"]?.error != null }
        assertEquals(10, activity.model.preferences.value.seekSeconds)
        recreate()
        command("sponsorblock", """{"failPreferences":false}""")
        compose.onNodeWithText("Retry").performScrollTo().performClick(); save("Playback")
        assertEquals(5, state().getJSONObject("preferences").getInt("seek_seconds"))
        assertEquals("preserved", state().getJSONObject("preferences").getString("unrelated_setting"))
        command("preferences", """{"seek_seconds":20}""")
        compose.runOnUiThread { activity.model.refreshSharedSettings() }
        until { activity.model.preferences.value.seekSeconds == 20 }
        open("Playback"); compose.onNodeWithText("Seek interval").performScrollTo().performClick()
        option("20 seconds").assertIsSelected(); option("10 seconds").performClick(); save("Playback")
    }
    @Test fun outstandingChatValidationLinksToTheActualControlsAndReverts() {
        compose.runOnUiThread {
            val model=activity.model
            model.requestChatPreferences(model.preferences.value.chat.copy(words="/(?=invalid)/"),debounce=false)
        }
        until { activity.model.preferenceSaves.edits.value["chat_filters"]?.status == net.wingress.mobivious.ui.PreferenceSaveStatus.INVALID }
        compose.selectFeed("You"); compose.openAppSettings()
        compose.onNodeWithTag("preference-status-Chat settings").performClick()
        compose.onNodeWithTag("chat-settings-list").performScrollToNode(hasTestTag("chat-words"))
        compose.onNodeWithTag("chat-words").assertTextContains("/(?=invalid)/")
        compose.onNodeWithTag("chat-settings-list").performScrollToNode(hasText("Revert to saved"))
        compose.onNode(hasText("Revert to saved") and hasAnyAncestor(hasTestTag("chat-settings-list"))).performClick()
        until { activity.model.preferenceSaves.edits.value.isEmpty() }
        compose.onNodeWithContentDescription("Close chat settings").performClick()
        compose.onNodeWithTag("preference-status-Chat settings").assertDoesNotExist()
        assertEquals("",activity.model.preferences.value.chat.words)
    }
    @Test fun deviceAccentSavesImmediatelyAndSurvivesRecreationAndAccountChange() {
        val previous=activity.model.store.accentPreset.value
        try {
            open("Appearance")
            compose.onNodeWithTag("accent-teal").performScrollTo().performClick()
            assertEquals(AccentPreset.TEAL,SessionStore(activity).accentPreset.value)
            recreate()
            compose.onNodeWithTag("accent-teal").performScrollTo().assertIsSelected()
            compose.onAllNodesWithText("Ambient mode").assertCountEquals(0)
            compose.onAllNodesWithText("Reduce transparency").assertCountEquals(0)
            save("Appearance")
            compose.runOnUiThread {activity.model.action {activity.model.login("Fixture","transient-password")}}
            until {activity.model.account.value!=null && !activity.model.accountBusy.value}
            assertEquals(AccentPreset.TEAL,activity.model.store.accentPreset.value)
            open("Appearance");compose.onAllNodesWithText("Ambient mode").assertCountEquals(0)
            compose.onAllNodesWithText("Reduce transparency").assertCountEquals(0)
            compose.onAllNodesWithText("Colored backgrounds").assertCountEquals(0)
            compose.onAllNodesWithText("Adaptive control colors").assertCountEquals(0)
        } finally {compose.runOnUiThread {activity.model.store.accentPreset(previous)}}
    }
    @Test fun retiredDeviceOptionsStayAbsentInAppearanceAndPlayerSettings() {
        val saved = activity.getSharedPreferences("mobivious", 0)
        val keys = listOf("appearance.ambientMode", "accessibility.reduceTransparency")
        val previous = keys.associateWith { saved.all[it] }
        try {
            saved.edit().putBoolean(keys[0], true).putBoolean(keys[1], true).commit()
            open("Appearance")
            compose.onAllNodesWithText("Ambient mode").assertCountEquals(0)
            compose.onAllNodesWithText("Reduce transparency").assertCountEquals(0)
            recreate()
            compose.onAllNodesWithText("Ambient mode").assertCountEquals(0)
            compose.onAllNodesWithText("Reduce transparency").assertCountEquals(0)
            save("Appearance")
            compose.runOnUiThread { activity.model.play("testvideo01") }
            until { activity.model.playback.value.details?.video?.id == "testvideo01" &&
                activity.model.playback.value.playerState == androidx.media3.common.Player.STATE_READY }
            compose.runOnUiThread { activity.model.controller.value?.pause() }
            until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mini-player-preview").performClick()
            until { compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription("More player controls").fetchSemanticsNodes().isNotEmpty() }
            val surface = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
            val transport = compose.onNodeWithTag("player-play-pause").getUnclippedBoundsInRoot()
            assertEquals((surface.left.value + surface.right.value) / 2, (transport.left.value + transport.right.value) / 2, .5f)
            assertEquals((surface.top.value + surface.bottom.value) / 2, (transport.top.value + transport.bottom.value) / 2, .5f)
            if (compose.onAllNodesWithContentDescription("More player controls").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithContentDescription("More player controls").performClick()
            compose.onNodeWithContentDescription("Player settings").performClick()
            compose.onNodeWithTag("player-settings-list").assertExists()
            compose.onAllNodesWithText("Ambient mode").assertCountEquals(0)
            compose.onAllNodesWithText("Reduce transparency").assertCountEquals(0)
            assertTrue(saved.getBoolean(keys[0], false)); assertTrue(saved.getBoolean(keys[1], false))
            compose.onNodeWithContentDescription("Close player settings").performClick()
        } finally {
            saved.edit().apply { previous.forEach { (key, value) -> when (value) {
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                else -> remove(key)
            } } }.commit()
        }
    }

    @Test(timeout=90_000) fun systemAppearanceChangesPreserveAutomaticPreferencePage() {
        val manager=activity.getSystemService(UiModeManager::class.java)
        val original=manager.nightMode
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        open("Appearance");automation.adoptShellPermissionIdentity()
        try {
            for(dark in listOf(true,false,true)) {
                compose.runOnUiThread {manager.setNightMode(if(dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)}
                until {
                    var matches=false
                    compose.runOnUiThread {val current=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                        if(current!=null) {activity=current;matches=(current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)==dark}}
                    matches
                }
                compose.onNodeWithText("Color mode").assertExists()
                assertEquals("",activity.model.preferences.value.darkMode)
                compose.onAllNodesWithTag("settings-save").assertCountEquals(0)
            }
        } finally {
            compose.runOnUiThread {manager.setNightMode(original)};automation.dropShellPermissionIdentity()
            compose.waitForIdle()
            compose.runOnUiThread {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()?.let {activity=it}}
        }
    }

    @Test fun fullScreenNavigationAndAutomaticChangesSurviveRecreation() {
        open("Browsing")
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        toggle("Show related videos")
        compose.runOnUiThread { activity.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== activity } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        save("Browsing")
        assertFalse(activity.model.store.guestPreferences().relatedVideos)
        compose.selectFeed("You"); compose.openAppSettings()
        compose.onNodeWithText("Playback").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back from Playback").performClick()
        compose.onNodeWithTag("settings-root").assertExists()
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        compose.onNodeWithTag("you-guest").assertExists()
    }

    @Test fun guestPlaybackDefaultsAndLocalResumeReachPlayer() {
        open("Playback"); toggle("Autoplay opened videos"); toggle("Audio only by default"); save("Playback")
        open("History & library"); toggle("Remember playback position"); save("History & library")
        compose.runOnUiThread { activity.model.store.position("testvideo01", 35) }
        compose.selectFeed("Popular")
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.details != null && activity.model.playback.value.duration > 0 }
        assertFalse(activity.model.playback.value.playWhenReady)
        assertTrue(activity.model.playback.value.position in 34_000..36_000)
        compose.runOnUiThread { assertTrue(activity.model.controller.value!!.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO)) }
        val events = state().getJSONArray("events")
        assertFalse((0 until events.length()).any { events.getJSONObject(it).optString("path") == "/api/v1/auth/preferences" })
    }

    @Test fun failedAutomaticSaveKeepsRequestedValueAcrossNavigationRecreationAndRetry() {
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null && activity.model.tab == "You" && !activity.model.accountBusy.value }
        open("Browsing")
        command("sponsorblock", """{"failPreferences":true}""")
        toggle("Show related videos")
        compose.onNodeWithText("Show related videos").assertIsOff()
        until {activity.model.preferenceSaves.edits.value["related_videos"]?.error!=null}
        assertTrue(activity.model.preferences.value.relatedVideos)
        compose.onNodeWithContentDescription("Back from Browsing").performClick()
        compose.onNodeWithTag("preference-save-status").assertExists()
        compose.onNodeWithTag("preference-status-Browsing").performScrollTo().performClick()
        recreate()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Show related videos"))
        compose.onNodeWithText("Show related videos").assertIsOff()
        command("sponsorblock", """{"failPreferences":false}""")
        compose.onNodeWithText("Retry").performScrollTo().performClick()
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
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null && activity.model.tab == "You" && !activity.model.accountBusy.value }
        open("Playback")
        compose.onNodeWithText("Preferred video codec").performScrollTo()
        // The following row can be below the viewport after scrollTo. Compare
        // layout positions rather than clipped bounds (which collapse to zero).
        assertTrue(compose.onNodeWithText("Preferred video codec").getUnclippedBoundsInRoot().top <
            compose.onNodeWithText("Default quality").getUnclippedBoundsInRoot().top)
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
    @Test fun preferredAudioSavesForGuestsAndAccountsAndRetainsFailedEdits() {
        fun option(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isPopup()))
        open("Playback")
        compose.onNodeWithText("Preferred audio track").performScrollTo().performClick()
        option("Original Audio").assertIsSelected()
        option("Original Audio (Stable Volume)").performClick(); save("Playback")
        assertEquals("original_stable", SessionStore(activity).guestPreferences().preferredAudio)
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null && activity.model.tab == "You" && !activity.model.accountBusy.value }
        open("Playback")
        command("sponsorblock", """{"failPreferences":true}""")
        compose.onNodeWithText("Preferred audio track").performScrollTo().performClick()
        option("Original Audio").assertIsSelected()
        option("Original Audio (Stable Volume)").performClick()
        until { activity.model.preferenceSaves.edits.value["preferred_audio"]?.error != null }
        assertEquals("original", activity.model.preferences.value.preferredAudio)
        recreate()
        compose.onNodeWithText("Preferred audio track").performScrollTo().performClick()
        option("Original Audio (Stable Volume)").assertIsSelected()
        option("Original Audio (Stable Volume)").performClick()
        command("sponsorblock", """{"failPreferences":false}""")
        compose.onNodeWithText("Retry").performScrollTo().performClick(); save("Playback")
        assertEquals("original_stable", state().getJSONObject("preferences").getString("preferred_audio"))
        assertEquals("preserved", state().getJSONObject("preferences").getString("unrelated_setting"))
        command("preferences", """{"preferred_audio":"pt-BR"}""")
        compose.runOnUiThread { activity.model.refreshSharedSettings() }
        until { activity.model.preferences.value.preferredAudio == "pt-BR" }
        open("Playback")
        compose.onNodeWithText("Portuguese (Brazil)").performScrollTo().assertExists()
    }
}
