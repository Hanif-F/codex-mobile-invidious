package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.media3.common.Player
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBack
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

/** Native UI and service-owned playback against the isolated localhost replay fixture. */
@RunWith(AndroidJUnit4::class)
class LiveChatSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val base = "http://127.0.0.1:18080"
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun until(block: () -> Boolean) = compose.waitUntil(30_000, block)
    private fun command(name: String, body: String = "{}") {
        (URL("$base/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("$base/test/state").readText())
    private fun requests() = fixture().getJSONArray("chatRequests").length()
    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).let {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        shell("mkdir -p /data/local/tmp/mobivious-chat-screenshots")
        shell("screencap -p /data/local/tmp/mobivious-chat-screenshots/$name.png")
    }
    private fun account() {
        command("preferences", """{"autoplay":false,"continue_autoplay":false,"thin_mode":true}""")
        ui { activity.model.store.save(Account("fixture-token", "Fixture", 9999999999L, base)) }
        until { activity.model.account.value != null && activity.model.preferences.value.thinMode && activity.model.preferences.value.watchHistory }
        compose.waitForIdle()
    }
    private fun incoming(id: String = "testvideo01") = ui {
        activity.startActivity(Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "$base/watch?v=$id&autoplay=0"))
    }
    private fun ready() {
        try { until {
            activity.model.playback.value.playerState == Player.STATE_READY && !activity.model.queue.value.loading &&
                activity.model.queue.value.details != null
        }
            restoreWatch()
            if (compose.onAllNodesWithTag("player-play-pause").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("player-surface").performClick()
            compose.onNodeWithTag("player-play-pause").assertExists()
        } catch (e: Throwable) {
            android.util.Log.i("ChatTest", "Replay setup: playback=${activity.model.playback.value}, chat=${activity.model.chatReplay.value}")
            screenshot("setup-failure"); throw e
        }
    }
    private fun reacquire() = until {
        var resumed: MainActivity? = null
        ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
        if (resumed != null) activity = resumed!!
        resumed != null && activity.model.controller.value != null
    }
    private fun chat(at: Long = 10_000) {
        ui { activity.model.seekTo(at) }
        compose.onNodeWithTag("player-chat").performClick()
        until { activity.model.chatReplay.value.loaded && !activity.model.chatReplay.value.loading }
        compose.onNodeWithTag("chat-panel").assertIsDisplayed()
    }
    private fun settings() { compose.onNodeWithTag("chat-settings").performClick(); compose.onNodeWithTag("chat-settings-sheet").assertExists() }
    private fun restoreWatch() {
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("mini-player-preview").performClick()
        until { compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun saveFiltersAndTiming(words: String, timing: String) {
        compose.onNodeWithTag("chat-settings-list").performScrollToNode(hasTestTag("chat-words"))
        compose.onNodeWithTag("chat-words").performTextReplacement(words)
        compose.onNodeWithTag("chat-settings-list").performScrollToNode(hasTestTag("chat-timing"))
        compose.onNodeWithTag("chat-timing").performTextReplacement(timing)
        compose.onNodeWithTag("chat-settings-list").performScrollToNode(hasTestTag("chat-settings-save"))
        compose.onNodeWithTag("chat-settings-save").performClick()
    }
    @Before fun launch() {
        command("reset"); command("chat"); command("chapters")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext.applicationContext as MobiviousApplication
            app.store.save(null); app.store.server = base; app.cache.clear(); app.store.chatAppearance(ChatAppearance())
            app.store.chatTiming("testvideo01", 0, app.api.context())
            app.store.guestDeArrow(AccountPreferences(autoplay = false, continueAutoplay = false, thinMode = true))
        }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    @After fun close() { if (::activity.isInitialized) ui { activity.model.closePlayer(); activity.finishAndRemoveTask() } }

    @Test fun replayOpensOnDemandAndNeverShowsFutureMessagesOrChangesPausedPlayback() {
        incoming(); ready(); assertEquals(0, requests()); assertEquals(0, fixture().getJSONArray("chatTimingRequests").length())
        chat(); compose.onNodeWithTag("chat-message-chat-2").assertExists(); compose.onNodeWithTag("chat-message-chat-3").assertDoesNotExist()
        ui { activity.model.speed(2f) }; until { activity.model.playback.value.speed == 2f }
        assertFalse(activity.model.playback.value.playWhenReady)
        ui { activity.model.seekTo(17_000) }; until { activity.model.chatReplay.value.messages.any { it.id == "chat-3" } }
        compose.onNodeWithText("Paid message · $5").assertExists()
        ui { activity.model.seekTo(6_000) }; until { activity.model.chatReplay.value.messages.none { it.offsetMs > 6_000 } }
        assertFalse(activity.model.playback.value.playWhenReady)
        val events = fixture().getJSONArray("chatRequests")
        for (i in 0 until events.length()) assertTrue(events.getJSONObject(i).isNull("authorization"))
    }
    @Test fun failedReplayRetriesItsOffsetWhilePlaybackRemainsUsable() {
        command("chat", """{"chatFailNext":true}"""); incoming(); ready()
        compose.onNodeWithTag("player-chat").performClick(); until { activity.model.chatReplay.value.error != null }
        compose.onNodeWithTag("chat-retry").performClick(); until { activity.model.chatReplay.value.loaded }
        val events = fixture().getJSONArray("chatRequests")
        assertEquals(events.getJSONObject(0).getLong("offset"), events.getJSONObject(1).getLong("offset"))
        compose.onNodeWithTag("player-play-pause").performClick(); until { activity.model.playback.value.playing }
    }
    @Test fun unavailableReplayAndUnsupportedVideoDoNotBreakPlayback() {
        command("chat", """{"chatUnavailable":true}"""); incoming(); ready()
        compose.onNodeWithTag("player-chat").performClick(); until { activity.model.chatReplay.value.unavailable }
        compose.onNodeWithText("Chat replay is unavailable for this video.").assertExists(); compose.onNodeWithTag("chat-retry").assertDoesNotExist()
        compose.onNodeWithTag("chat-close").performClick()
        command("sponsorblock", """{"liveNow":true}"""); incoming("testvideo02"); ready()
        compose.onNodeWithTag("player-chat").assertDoesNotExist()
    }
    @Test fun fullscreenDocksBesideAndBackClosesChatBeforeLeavingFullscreen() {
        incoming(); ready(); chat()
        compose.onNodeWithContentDescription("Full screen").performClick(); reacquire()
        until { compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot().let { it.right - it.left > it.bottom - it.top } }
        val video = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("chat-panel").getUnclippedBoundsInRoot()
        assertTrue(panel.left >= video.right - androidx.compose.ui.unit.Dp(2f))
        screenshot("landscape-dock")
        // Dispatch through the Activity: shell captures can leave the Android 16 AVD without input focus.
        ui { activity.onBackPressedDispatcher.onBackPressed() }
        until { !activity.model.chatReplay.value.open }; compose.onNodeWithTag("chat-panel").assertDoesNotExist(); compose.onNodeWithContentDescription("Exit full screen").assertExists()
        ui { activity.onBackPressedDispatcher.onBackPressed() }
        reacquire(); until { compose.onAllNodesWithContentDescription("Full screen").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun overlayDragAndResizeSaveCancelWithoutSeekingOrMinimizing() {
        incoming(); ready(); chat(); ui { activity.model.setChatAppearance(ChatAppearance(overlay = true)) }
        compose.onNodeWithTag("chat-overlay").assertExists()
        val before = activity.model.chatAppearance.value
        compose.onNodeWithTag("chat-overlay-edit").performClick()
        compose.onNodeWithTag("chat-overlay-move").performTouchInput { swipe(center, center - Offset(40f, 20f), 400) }
        compose.onNodeWithTag("chat-overlay-save").performClick()
        assertTrue(activity.model.chatAppearance.value.x < before.x)
        val saved = activity.model.chatAppearance.value
        compose.onNodeWithTag("chat-overlay-edit").performClick()
        compose.onNodeWithTag("chat-overlay-resize").performTouchInput { swipe(center, center - Offset(30f, 20f), 400) }
        compose.onNodeWithTag("chat-overlay-cancel").performClick()
        assertEquals(saved, activity.model.chatAppearance.value)
        screenshot("overlay")
        compose.onNodeWithContentDescription("Full screen").performClick(); reacquire()
        until { compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot().let { it.right - it.left > it.bottom - it.top } }
        val surface = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        val overlay = compose.onNodeWithTag("chat-overlay").getUnclippedBoundsInRoot()
        assertTrue(overlay.left >= surface.left && overlay.right <= surface.right && overlay.top >= surface.top && overlay.bottom <= surface.bottom)
        compose.onNodeWithTag("chat-overlay-edit").performClick()
        val actions = compose.onNodeWithTag("chat-overlay-move").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        ui { assertTrue(actions.first { it.label == "Move left" }.action()) }
        compose.onNodeWithTag("chat-overlay-save").performClick()
        assertTrue(activity.model.chatAppearance.value.x < saved.x)
        assertEquals(10_000L, activity.model.playback.value.position); assertFalse(activity.model.playback.value.playWhenReady)
        compose.onNodeWithContentDescription("Exit full screen").assertExists()
    }
    @Test fun filtersAndTimingSaveForGuestsAndSurviveRecreation() {
        incoming(); ready(); chat(); settings(); saveFiltersAndTiming("spam /nothing/", "2.5")
        until { activity.model.preferences.value.chat.words == "spam /nothing/" && activity.model.chatReplay.value.timingOffsetMs == 2500 }
        pressBack(); compose.onNodeWithTag("chat-message-chat-2").assertDoesNotExist()
        assertEquals(2500, activity.model.store.chatTiming("testvideo01", activity.model.api.context()))
        assertEquals(0, fixture().getJSONArray("chatPreferenceWrites").length()); assertEquals(0, fixture().getJSONArray("chatTimingRequests").length())
        ui { activity.recreate() }; reacquire(); compose.onNodeWithTag("chat-panel").assertExists()
        assertEquals(2500, activity.model.chatReplay.value.timingOffsetMs)
        assertEquals("spam /nothing/", activity.model.store.guestDeArrow().chat.words)
    }
    @Test fun signedInFiltersAndTimingUseDedicatedApisAndSaveFailuresRetry() {
        account(); incoming(); ready(); chat(); settings()
        command("chat", """{"chatSaveFail":true}"""); saveFiltersAndTiming("spam", "-1.5")
        compose.onNodeWithText("Chat settings could not be saved").assertExists()
        command("chat", """{"chatSaveFail":false}"""); compose.onNodeWithTag("chat-settings-save").performClick()
        until { activity.model.chatReplay.value.timingOffsetMs == -1500 && !activity.model.chatReplay.value.timingSaving }
        val saved = fixture(); assertEquals(-1500, saved.getJSONObject("chatTimings").getInt("testvideo01"))
        val patch = saved.getJSONArray("chatPreferenceWrites").getJSONObject(0)
        assertEquals(1, patch.length()); assertEquals("spam", patch.getString("chat_word_blacklist"))
        assertEquals("preserved", saved.getJSONObject("preferences").getString("unrelated_setting"))
    }
    @Test fun invalidRegexIsRejectedAndOldScopesExplainHowToRestoreSync() {
        account(); command("chat", """{"chatScopeFail":true}""")
        incoming(); ready(); chat(); settings(); saveFiltersAndTiming("/(?=bad)/", "0")
        compose.onNodeWithTag("chat-settings-list").performScrollToNode(hasText("Invalid or unsupported regex. Lookaround and backreferences are unsupported."))
        compose.onNodeWithText("Invalid or unsupported regex. Lookaround and backreferences are unsupported.").assertExists()
        assertTrue(activity.model.chatReplay.value.timingError!!.contains("Sign out and sign in")); assertNotNull(activity.model.account.value)
        assertEquals(0, fixture().getJSONArray("chatPreferenceWrites").length())
    }
    @Test fun editingTimingWhileItsAccountReadIsDelayedKeepsTheNewValue() {
        account(); command("chat", """{"chatTimingDelayNext":2500,"chatTimings":{"testvideo01":5000}}""")
        incoming(); ready(); compose.onNodeWithTag("player-chat").performClick()
        until { activity.model.chatReplay.value.timingLoading }
        settings(); saveFiltersAndTiming("", "2")
        until { !activity.model.chatReplay.value.timingSaving && activity.model.chatReplay.value.timingOffsetMs == 2000 }
        assertEquals(2000, fixture().getJSONObject("chatTimings").getInt("testvideo01"))
        compose.onNodeWithTag("chat-timing").assertTextContains("2")
    }
    @Test fun chaptersAndCommentsReplaceChatAndTheSameVideoNewOccurrenceStartsClosed() {
        incoming(); ready(); chat()
        compose.onNodeWithTag("player-chapter-title").performClick(); compose.onNodeWithTag("chapters-panel").assertExists(); assertFalse(activity.model.chatReplay.value.open)
        compose.onNodeWithTag("player-chat").performClick(); compose.onNodeWithTag("chapters-panel").assertDoesNotExist(); compose.onNodeWithTag("chat-panel").assertExists()
        ui { activity.model.openComments() }; compose.onNodeWithTag("comments-drawer").assertExists(); assertFalse(activity.model.chatReplay.value.open)
        ui { activity.model.openChat() }; until { activity.model.chatReplay.value.open }; val occurrence = activity.model.queue.value.currentKey
        incoming("testvideo02"); ready(); until { activity.model.queue.value.currentKey != occurrence }
        assertFalse(activity.model.chatReplay.value.open); compose.onNodeWithTag("chat-panel").assertDoesNotExist()
    }
    @Test fun overlayAndDockSupportLargeFontsBothThemesAndAccessibleCloseTargets() {
        incoming(); ready(); chat(110_000)
        for (theme in listOf("light", "dark")) {
            ui { activity.model.preferences.value = activity.model.preferences.value.copy(darkMode = theme); activity.model.setChatAppearance(ChatAppearance(fontScale = 300, belowFraction = .7f)) }
            compose.onNodeWithTag("chat-close").assertIsDisplayed()
            val close = compose.onNodeWithTag("chat-close").getUnclippedBoundsInRoot()
            assertTrue((close.right - close.left).value >= 47.5 && (close.bottom - close.top).value >= 47.5)
            screenshot("large-font-$theme")
            compose.onNodeWithTag("chat-list").performTouchInput { swipeDown() }
            until { !activity.model.chatReplay.value.following }
            if (theme == "light") {
                val rows = activity.model.chatReplay.value.messages
                ui { activity.recreate() }; reacquire()
                assertFalse(activity.model.chatReplay.value.following)
                assertEquals(rows, activity.model.chatReplay.value.messages)
                compose.onNodeWithTag("chat-follow").assertExists()
            }
            compose.onNodeWithTag("chat-follow").performClick(); until { activity.model.chatReplay.value.following }
        }
    }
    @Test fun miniplayerSuspendsReplayAndRestorationRetainsTheOpenedPanel() {
        incoming(); ready(); chat(); compose.onNodeWithContentDescription("Minimize player").performClick()
        until { !activity.model.chatReplay.value.visible }; val before = requests()
        ui { activity.model.seekTo(80_000) }; compose.waitForIdle(); assertEquals(before, requests())
        compose.onNodeWithTag("mini-player-preview").performClick()
        until { activity.model.chatReplay.value.visible && activity.model.chatReplay.value.messages.any { it.offsetMs >= 65_000 } }
        compose.onNodeWithTag("chat-panel").assertExists()
    }
    @Test fun portraitFullscreenDocksBelowAndKeepsTheDecodedVideoAspect() {
        incoming("portrait001"); ready(); chat(5_000)
        compose.onNodeWithContentDescription("Full screen").performClick(); reacquire()
        until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithTag("chat-panel").assertIsDisplayed()
        val video = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("chat-panel").getUnclippedBoundsInRoot()
        assertTrue(panel.top >= video.bottom - androidx.compose.ui.unit.Dp(2f))
        assertEquals(9f / 16f, activity.model.playback.value.geometry.ratio!!, .01f)
        screenshot("portrait-dock")
    }
    @Test fun guestAndAccountSettingsStayIsolatedAndAppearanceStaysLocal() {
        incoming(); ready(); chat(); settings(); saveFiltersAndTiming("guest-word", "1")
        pressBack(); until { activity.model.preferences.value.chat.words == "guest-word" }
        ui { activity.model.setChatAppearance(ChatAppearance(fontScale = 150)) }
        account(); until { !activity.model.chatReplay.value.open }
        assertEquals("", activity.model.preferences.value.chat.words)
        incoming(); ready(); chat()
        assertEquals(0, activity.model.chatReplay.value.timingOffsetMs)
        ui { activity.model.store.save(null) }
        until { activity.model.account.value == null && activity.model.preferences.value.chat.words == "guest-word" }
        compose.waitForIdle(); incoming(); ready(); chat()
        assertEquals(1000, activity.model.chatReplay.value.timingOffsetMs)
        assertEquals(150, activity.model.chatAppearance.value.fontScale)
        assertEquals(0, fixture().getJSONArray("chatPreferenceWrites").length())
    }
    @Test fun pipAndBackgroundSuspendRequestsAndRestoreAtTheCurrentPosition() {
        Assume.assumeTrue(activity.supportsPip())
        incoming(); ready(); chat(); val occurrence = activity.model.queue.value.currentKey
        val oldPip = activity.model.store.pip
        try {
            ui { activity.enterPip() }; until { activity.isInPictureInPictureMode && !activity.model.chatReplay.value.visible }
            val before = requests(); ui { activity.model.seekTo(60_000) }; compose.waitForIdle(); assertEquals(before, requests())
            foreground(); until { activity.model.chatReplay.value.visible && activity.model.chatReplay.value.messages.any { it.offsetMs >= 45_000 } }
            assertEquals(occurrence, activity.model.queue.value.currentKey)
            ui { activity.model.store.pip = false; activity.moveTaskToBack(true) }
            until { !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !activity.model.chatReplay.value.visible }
            val hidden = requests(); ui { activity.model.seekTo(90_000) }; compose.waitForIdle(); assertEquals(hidden, requests())
            foreground(); until { activity.model.chatReplay.value.visible && activity.model.chatReplay.value.messages.any { it.offsetMs >= 75_000 } }
            compose.onNodeWithTag("chat-panel").assertExists(); assertEquals(occurrence, activity.model.queue.value.currentKey)
        } finally { ui { activity.model.store.pip = oldPip } }
    }
    private fun foreground() {
        android.util.Log.i("ChatTest", shell("am start --windowingMode 1 -n net.wingress.mobivious.debug/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task").decodeToString())
        ui { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        reacquire(); until { !activity.isInPictureInPictureMode && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    }
}
