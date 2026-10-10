package net.wingress.mobivious

import net.wingress.mobivious.data.AccountPreferences

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.compose.ui.test.*
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
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    @Before fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null)
            activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(net.wingress.mobivious.data.AccountPreferences())
            activity.model.refreshSharedSettings(); activity.model.navigate("Popular")
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.store.pip = true
        }
    }
    @After fun closeActivity() {
        if (::activity.isInitialized) InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.model.audioOnly(false); activity.model.captions(null); activity.model.speed(1f)
            activity.model.closePlayer(); activity.updatePip(false); activity.finishAndRemoveTask()
        }
    }
    private fun waitFor(timeout: Long = 20_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)
    private fun fixture(): JSONObject = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply { requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect() }
    }

    @Test fun failedInitialPlaybackRetryPreservesAutoplayAndPausedIntent() {
        command("reset")
        for (autoplay in listOf(true, false)) {
            command("stream", """{"type":"dash","failNext":true}""")
            compose.runOnUiThread {
                activity.model.openLink(net.wingress.mobivious.data.VideoLinks.parse(
                    "http://127.0.0.1:18080/watch?v=testvideo01&autoplay=${if (autoplay) 1 else 0}",
                    activity.model.store.server)!!)
                activity.sharedVideo.value = true
            }
            waitFor { activity.model.queue.value.error != null && !activity.model.queue.value.loading }
            val token = activity.model.queue.value.token
            val occurrence = activity.model.queue.value.currentKey
            compose.runOnUiThread { activity.model.retryPlayback() }
            waitFor(40_000) { activity.model.playback.value.playerState == Player.STATE_READY &&
                !activity.model.queue.value.loading && activity.model.queue.value.error == null }
            assertEquals(token, activity.model.queue.value.token)
            assertEquals(occurrence, activity.model.queue.value.currentKey)
            assertEquals(autoplay, activity.model.playback.value.playWhenReady)
        }
    }
    private fun showControls() {
        if (compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
        }
        waitFor(2500) { compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Player settings").assertExists()
    }
    private fun openFixture() {
        command("reset")
        compose.runOnUiThread { activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.guestDeArrow(net.wingress.mobivious.data.AccountPreferences()); activity.model.refreshSharedSettings() }
        waitFor { activity.model.browse.value.videos.isNotEmpty() }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        waitFor(40_000) { activity.model.playback.value.playing }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = if (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        // UTP uninstalls the debug app after tests; keep QA captures in the shell's temporary directory.
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /data/local/tmp/mobivious-player-screenshots").let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/mobivious-player-screenshots/$name-$mode.png").let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    @Test fun repeatedRecommendationsRenderOnceAndKeepPlaybackUsable() {
        command("reset")
        command("stream", """{"type":"dash","duplicateRecommendations":true}""")
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(net.wingress.mobivious.data.AccountPreferences(thinMode = true))
            activity.model.refreshSharedSettings()
            activity.model.play("testvideo01")
            activity.sharedVideo.value = true
        }
        waitFor(40_000) { activity.model.playback.value.playing }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("watch-up-next"))
        compose.onAllNodesWithTag("video-card-testvideo02").assertCountEquals(1)
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("video-card-testvideo03"))
        compose.onNodeWithTag("video-card-testvideo03").assertExists()
        assertEquals(listOf("testvideo02", "testvideo03"), activity.model.playback.value.details!!.recommendations.map { it.id })
        assertTrue(activity.model.playback.value.playing)
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("video-card-testvideo02"))
        compose.onNode(hasText("Another original title") and hasAnyAncestor(hasTestTag("video-card-testvideo02")))
            .performScrollTo().performClick()
        waitFor(40_000) { activity.model.queue.value.current?.video?.id == "testvideo02" && activity.model.playback.value.playing }
        assertNull(activity.model.playback.value.error)
    }
    @Test fun unifiedControlsGesturesAndFullscreen() {
        openFixture()
        // Let real playback idle: every control, including fullscreen, must disappear.
        compose.mainClock.advanceTimeBy(3800)
        waitFor(8000) { compose.onAllNodesWithTag("player-controls").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("player-controls").assertDoesNotExist()
        compose.onNodeWithContentDescription("Full screen").assertDoesNotExist()
        compose.onNodeWithText("Player", useUnmergedTree = true).assertDoesNotExist()
        showControls()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.runOnUiThread { activity.model.seekTo(40_000) }
        waitFor { activity.model.playback.value.position == 40_000L }
        screenshot("player-portrait")
        compose.onNodeWithTag("player-surface").performTouchInput { doubleClick(Offset(width * .15f, height * .25f)) }
        waitFor { activity.model.playback.value.position == 30_000L }
        compose.onNodeWithTag("player-surface").performTouchInput { doubleClick(Offset(width * .85f, height * .25f)) }
        waitFor { activity.model.playback.value.position == 40_000L }
        val accessibleSeek = compose.onNodeWithTag("player-gestures").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Back 10 seconds", "Forward 10 seconds", "Minimize player"), accessibleSeek.map { it.label })
        compose.runOnUiThread { assertTrue(accessibleSeek[1].action()) }
        waitFor { activity.model.playback.value.position == 50_000L }
        compose.runOnUiThread { assertTrue(accessibleSeek[0].action()) }
        waitFor { activity.model.playback.value.position == 40_000L }
        compose.runOnUiThread { activity.model.seekTo(2000) }
        compose.onNodeWithTag("player-surface").performTouchInput { doubleClick(Offset(width * .15f, height * .25f)) }
        waitFor { activity.model.playback.value.position == 0L }
        compose.runOnUiThread { activity.model.seekTo(115_000) }
        compose.onNodeWithTag("player-surface").performTouchInput { doubleClick(Offset(width * .85f, height * .25f)) }
        waitFor { activity.model.playback.value.position in 119_000L..120_000L }
        compose.runOnUiThread { activity.model.seekTo(30_000) }
        compose.onNodeWithTag("player-timeline").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(60_000f)) }
        waitFor { activity.model.playback.value.position == 60_000L }
        compose.onNodeWithTag("player-timeline").performTouchInput { swipe(Offset(width * .4f, height / 2f), Offset(width * .65f, height / 2f), 600) }
        waitFor { activity.model.playback.value.position in 70_000L..90_000L }
        showControls()
        compose.onNodeWithContentDescription("Player settings").performClick()
        screenshot("player-settings-portrait")
        compose.onNodeWithText("Playback speed").performClick()
        compose.onNodeWithText("0.25×").performClick()
        waitFor { activity.model.playback.value.speed == .25f }
        compose.onNodeWithText("Quality").performClick()
        compose.onNodeWithText("360p24 · H.264").performClick()
        compose.onNodeWithText("Captions").performClick()
        compose.onNodeWithText("English").performClick()
        waitFor { activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_TEXT) &&
            activity.model.playback.value.selection?.overrides?.values?.any { it.type == C.TRACK_TYPE_VIDEO } == true }
        compose.onNodeWithContentDescription("Close player settings").performClick()
        showControls()
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("0.25×").assertExists()
        compose.onNodeWithText("360p24 · H.264", substring = true).assertExists()
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithText("Auto").assertExists()
        // The fixture's audio has no language tag and must still have a selectable row.
        compose.onNodeWithText("Audio track 1").performClick()
        compose.runOnUiThread { assertTrue(activity.model.controller.value!!.trackSelectionParameters.overrides.values.any { it.type == C.TRACK_TYPE_AUDIO }) }
        compose.onNodeWithText("Quality").performClick()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.onNodeWithText("Refresh buffer").assertExists()
        compose.onNodeWithContentDescription("Close player settings").performClick()
        showControls()
        compose.onNodeWithContentDescription("Full screen").performClick()
        waitFor { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        // A clean emulator may show Android's own first-use immersive tutorial.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(20) {
            automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Got it")?.forEach { node -> node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) }
            Thread.sleep(100)
        }
        showControls()
        screenshot("player-fullscreen")
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        // An ordinary video tap hides controls; it must never leave fullscreen.
        compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
        waitFor(2500) { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Exit full screen").assertDoesNotExist()
        showControls()
        compose.onNodeWithContentDescription("Exit full screen").assertExists()
        compose.onNodeWithContentDescription("Player settings").performClick()
        screenshot("player-settings-landscape")
        compose.onNodeWithTag("player-settings-list").performScrollToNode(hasText("Picture in picture"))
        compose.onNodeWithText("Picture in picture").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        waitFor { compose.onAllNodesWithContentDescription("Close player settings").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Exit full screen").assertExists()
        compose.runOnUiThread { activity.model.playback.value = activity.model.playback.value.copy(error = "Playback failed. Retry to refresh the stream.") }
        screenshot("player-error-fullscreen")
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        waitFor(40_000) { activity.model.playback.value.playerState == Player.STATE_READY && activity.model.playback.value.error == null }
        compose.runOnUiThread { assertFalse(activity.model.controller.value!!.playWhenReady) }
        showControls()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        waitFor { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
            compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("watch-content").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit full screen").assertDoesNotExist()
        showControls()
        compose.onNodeWithContentDescription("Full screen").assertExists()
        compose.runOnUiThread { activity.model.playback.value = activity.model.playback.value.copy(error = "Playback failed. Retry to refresh the stream.") }
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        waitFor(40_000) { activity.model.playback.value.playerState == Player.STATE_READY && activity.model.playback.value.error == null }
        compose.runOnUiThread { assertFalse(activity.model.controller.value!!.playWhenReady) }
    }
    @Test fun refreshPreservesPausedAndPlayingSettings() {
        openFixture()
        compose.runOnUiThread {
            activity.model.controller.value!!.pause()
            activity.model.seekTo(45_000)
            activity.model.speed(1.5f)
            activity.model.quality(720)
            activity.model.captions("en")
            activity.model.audioOnly(true)
            val audioGroup = activity.model.controller.value!!.currentTracks.groups.first { it.type == C.TRACK_TYPE_AUDIO }
            activity.model.selectTrack(audioGroup.mediaTrackGroup, 0)
        }
        waitFor { activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_TEXT) &&
            activity.model.playback.value.selection?.overrides?.values?.any { it.type == C.TRACK_TYPE_VIDEO } == true &&
            activity.model.playback.value.selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true }
        val requests = fixture().getInt("mediaRequests")
        showControls()
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Refresh buffer").performClick()
        waitFor(40_000) { activity.model.playback.value.playerState == Player.STATE_READY && fixture().getInt("mediaRequests") > requests }
        compose.runOnUiThread {
            val p = activity.model.controller.value!!
            assertFalse(p.playWhenReady); assertEquals(45_000L, p.currentPosition)
            assertEquals(1.5f, p.playbackParameters.speed)
            val videoOverride = p.trackSelectionParameters.overrides.values.single { it.type == C.TRACK_TYPE_VIDEO }
            assertEquals(360, videoOverride.mediaTrackGroup.getFormat(videoOverride.trackIndices.single()).height)
            assertEquals("en", p.trackSelectionParameters.preferredTextLanguages.first())
            assertTrue(p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
            assertTrue(p.trackSelectionParameters.overrides.values.any { it.type == C.TRACK_TYPE_AUDIO })
            p.play()
        }
        waitFor { activity.model.playback.value.playing }
        val playingRequests = fixture().getInt("mediaRequests")
        compose.runOnUiThread { activity.model.refreshBuffer() }
        waitFor(40_000) { activity.model.playback.value.playing && fixture().getInt("mediaRequests") > playingRequests }
        compose.runOnUiThread { assertTrue(activity.model.controller.value!!.currentPosition in 45_000..65_000) }
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
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("you-sign-in").performClick()
        compose.onNodeWithText("Username").performTextInput("EmulatorViewer")
        compose.onNodeWithText("Password").performTextInput("fixture-password")
        compose.onNodeWithTag("account-auth-submit").performClick()
        waitFor { activity.model.account.value != null }
        compose.selectDiscoveryFeed("Popular")
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        waitFor(40_000) { activity.model.playback.value.playing }
        assertNull(activity.model.playback.value.error)
        waitFor { fixture().getJSONArray("watched").length() == 1 }
        compose.runOnUiThread { activity.model.controller.value!!.seekTo(30_000) }
        waitFor { fixture().getLong("position") >= 29 }
        showControls()
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Playback speed").performClick()
        compose.onNodeWithText("1.5×").performClick()
        compose.onNodeWithContentDescription("Close player settings").performClick()
        compose.runOnUiThread { assertEquals(1.5f, activity.model.controller.value!!.playbackParameters.speed) }
        compose.navigateBack()
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("you-playlists").performClick()
        waitFor { activity.model.route == "playlists" && !activity.model.browse.value.loading }
        compose.onNodeWithTag("playlist-create").performClick()
        compose.onNodeWithText("Title").performTextInput("Emulator playlist")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        waitFor { activity.model.playlists.value.any { it.title == "Emulator playlist" } }
        compose.onNodeWithText("Emulator playlist").performClick()
        waitFor { activity.model.playlist.value?.title == "Emulator playlist" }
        compose.onNodeWithTag("playlist-actions-${activity.model.playlist.value!!.id}").performScrollTo().performClick()
        compose.onNodeWithText("Edit playlist").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Renamed playlist")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        waitFor { activity.model.playlist.value?.title == "Renamed playlist" }
        assertEquals(1, fixture().getJSONArray("playlists").length())
        compose.runOnUiThread { activity.model.controller.value!!.pause() }
        assertTrue(fixture().getLong("position") >= 29)
        compose.runOnUiThread { activity.model.controller.value!!.play() }
        waitFor { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini-player-preview").performClick()
        showControls()
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithTag("player-settings-list").performScrollToNode(hasText("Picture in picture"))
        compose.onNodeWithText("Picture in picture").performClick()
        waitFor { activity.isInPictureInPictureMode }
        compose.onNodeWithTag("player-controls").assertDoesNotExist()
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
