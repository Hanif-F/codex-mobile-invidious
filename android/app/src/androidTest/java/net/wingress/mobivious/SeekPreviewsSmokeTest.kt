package net.wingress.mobivious

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.videoEnabled
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
class SeekPreviewsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val base = "http://127.0.0.1:18080"
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun until(block: () -> Boolean) = compose.waitUntil(30_000, block)
    private fun intervalReady(seconds: Int): Boolean {
        var ready = false
        ui { ready = vm.preferences.value.seekSeconds == seconds && vm.controller.value?.seekBackIncrement == seconds * 1000L && vm.controller.value?.seekForwardIncrement == seconds * 1000L }
        return ready
    }
    private fun command(name: String, body: String = "{}") {
        (URL("$base/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }
            inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("$base/test/state?fresh=${System.nanoTime()}").readText())
    private fun images() = fixture().getJSONArray("previewImageRequests")
    private fun incoming(path: String = "/watch?v=testvideo01&autoplay=0") = ui {
        activity.startActivity(Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$base$path"))
    }
    private fun ready() = until {
        vm.controller.value != null && !vm.playback.value.loading && vm.playback.value.playerState == Player.STATE_READY &&
            vm.playback.value.seekable && vm.playback.value.details != null && vm.playback.value.details == vm.queue.value.details &&
            compose.onAllNodesWithTag("player-timeline").fetchSemanticsNodes().isNotEmpty()
    }
    private fun beginDrag(fraction: Float) = compose.onNodeWithTag("player-timeline").performTouchInput {
        down(center); moveTo(Offset(width * fraction, centerY), delayMillis = 100)
    }
    private fun releaseDrag() = compose.onNodeWithTag("player-timeline").performTouchInput { up() }
    private fun preview() {
        until { vm.seekPreviews.state.value.sheet != null }
        compose.onNodeWithTag("player-seek-preview").assertIsDisplayed()
    }
    private fun reacquire() = until {
        var resumed: MainActivity? = null
        ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
        if (resumed != null) activity = resumed!!
        resumed != null && vm.controller.value != null
    }
    @Before fun launch() {
        command("reset"); command("chapters"); command("seek-previews")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext.applicationContext as MobiviousApplication
            app.store.save(null); app.store.server = base; app.cache.clear()
            app.store.guestPreferences(AccountPreferences(autoplay = false, continueAutoplay = false, thinMode = true))
        }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    @After fun close() { if (::activity.isInitialized) ui { vm.closePlayer(); activity.finishAndRemoveTask() } }

    @Test fun draggingShowsFullPlayerHighestResolutionAndRestoresPlayingOrPausedIntent() {
        incoming(); ready()
        assertEquals(0, fixture().getJSONArray("previewIndexRequests").length())
        beginDrag(.25f); preview()
        assertFalse(vm.playback.value.playWhenReady)
        val surface = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        assertEquals(surface, compose.onNodeWithTag("player-seek-preview").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("player-time", useUnmergedTree = true).assertTextContains(
            DisplayFormats.clock(vm.scrubSeek.value!!.targetMs / 1000), substring = true)
        val index = fixture().getJSONArray("previewIndexRequests").getJSONObject(0)
        assertTrue(index.getString("path").contains("width=320")); assertFalse(index.getBoolean("authorized"))
        captureWatchScreenshot("seek-preview-portrait")
        releaseDrag()
        until { vm.playback.value.position in 25_000L..35_000L && vm.scrubSeek.value == null }
        assertFalse(vm.playback.value.playWhenReady)
        compose.onNodeWithTag("player-seek-preview").assertDoesNotExist()
        ui { vm.togglePlay() }; until { vm.playback.value.playing }
        beginDrag(.7f); preview()
        assertFalse(vm.playback.value.playWhenReady)
        releaseDrag(); until { vm.playback.value.position in 78_000L..90_000L && vm.playback.value.playing }
        assertEquals(1, fixture().getJSONArray("previewIndexRequests").length())
    }
    @Test fun dragCancellationAndFullscreenTransitionClearPreviewsWithoutCommitting() {
        incoming(); ready(); ui { vm.seekTo(10_000); vm.togglePlay() }; until { vm.playback.value.playing }
        beginDrag(.75f); preview()
        compose.onNodeWithTag("player-timeline").performTouchInput { cancel() }
        until { vm.scrubSeek.value == null && vm.playback.value.playing }
        assertTrue("Cancelled drag moved to ${vm.playback.value.position}", vm.playback.value.position < 30_000)
        ui { vm.controller.value!!.pause(); vm.updateScrub(50_000) }; preview()
        compose.onNodeWithContentDescription("Full screen").performClick(); reacquire()
        until { vm.scrubSeek.value == null }
        compose.onNodeWithTag("player-seek-preview").assertDoesNotExist()
        until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        beginDrag(.6f); preview(); captureWatchScreenshot("seek-preview-fullscreen")
        releaseDrag(); until { vm.scrubSeek.value == null }
        assertFalse(vm.playback.value.playWhenReady)
    }
    @Test fun tapDownloadsUpdateWithin250MillisecondsAndReuseSheetsAcrossBursts() {
        incoming(); ready(); ui { vm.seekTo(0) }
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < 1700) {
            ui { vm.accumulateSeek(1) }
            Thread.sleep(150)
        }
        val requests = images()
        assertEquals(3, requests.length())
        for (i in 1 until requests.length())
            assertTrue(requests.getJSONObject(i).getDouble("time") - requests.getJSONObject(i - 1).getDouble("time") >= .22)
        assertTrue(requests.getJSONObject(2).getString("path").endsWith("/2.jpg"))
        assertTrue((0 until requests.length()).none { requests.getJSONObject(it).getBoolean("authorized") })
        until { vm.pendingSeek.value == null }
        assertFalse(vm.playback.value.playWhenReady)
        // All sheets are cached, so a later burst updates without another image request.
        ui { vm.seekTo(45_000); vm.accumulateSeek(1) }
        until { vm.pendingSeek.value == null }
        assertEquals(3, images().length())
        compose.onNodeWithTag("player-seek-preview").assertDoesNotExist()
    }
    @Test fun everyIntervalReachesAccessibilityMediaAndPipActionsAtEveryPlaybackSpeed() {
        incoming(); ready()
        for (seconds in listOf(5, 10, 20, 30)) {
            ui { vm.store.guestPreferences(vm.preferences.value.copy(seekSeconds = seconds)); vm.refreshSharedSettings() }
            until { intervalReady(seconds) }
            for (speed in listOf(.5f, 2f)) {
                ui { vm.controller.value!!.setPlaybackSpeed(speed); vm.seekTo(50_000) }
                until { vm.playback.value.position == 50_000L }
                ui { vm.controller.value!!.seekForward() }
                until { vm.playback.value.position == 50_000L + seconds * 1000L }
                ui { vm.controller.value!!.seekBack() }
                until { vm.playback.value.position == 50_000L }
                val actions = compose.onNodeWithTag("player-gestures").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                ui { assertTrue(actions.single { it.label == "Forward $seconds seconds" }.action()) }
                until { vm.playback.value.position == 50_000L + seconds * 1000L }
                ui { activity.startService(Intent(activity, net.wingress.mobivious.player.PlaybackService::class.java).setAction("mobivious.rewind")) }
                until { vm.playback.value.position == 50_000L }
                ui { activity.startService(Intent(activity, net.wingress.mobivious.player.PlaybackService::class.java).setAction("mobivious.forward")) }
                until { vm.playback.value.position == 50_000L + seconds * 1000L }
                ui { activity.updatePip(true) }
                assertFalse(vm.playback.value.playWhenReady)
            }
        }
    }
    @Test fun tapBatchCapturesItsIntervalAndNextBatchUsesRefreshedSettings() {
        incoming(); ready(); ui { vm.seekTo(50_000) }; until { vm.playback.value.position == 50_000L }
        ui {
            vm.preferences.value = vm.preferences.value.copy(seekSeconds = 5)
            vm.accumulateSeek(1)
            vm.preferences.value = vm.preferences.value.copy(seekSeconds = 30)
            vm.accumulateSeek(1)
            assertEquals(10_000L, vm.pendingSeek.value!!.offset)
            vm.accumulateSeek(-1)
            assertEquals(-5_000L, vm.pendingSeek.value!!.offset)
        }
        until { vm.pendingSeek.value == null && vm.playback.value.position == 45_000L }
        ui { vm.accumulateSeek(1) }
        until { vm.pendingSeek.value == null && vm.playback.value.position == 75_000L }
        assertFalse(vm.playback.value.playWhenReady)
    }
    @Test fun mediaSeekCommandsRespectExplicitVideoEndBounds() {
        incoming("/watch?v=testvideo01&start=10&end=55&autoplay=0"); ready()
        ui { vm.store.guestPreferences(vm.preferences.value.copy(seekSeconds = 30)); vm.refreshSharedSettings() }
        until { intervalReady(30) }
        ui { vm.seekTo(40_000) }; until { vm.playback.value.position == 40_000L }
        ui { vm.controller.value!!.seekForward() }
        until { vm.playback.value.position == 55_000L }
        assertFalse(vm.playback.value.playWhenReady)
        ui { vm.controller.value!!.seekBack() }
        until { vm.playback.value.position == 25_000L }
        assertFalse(vm.playback.value.playWhenReady)
    }
    @Test fun pipLabelsAndButtonsUseTheSelectedInterval() {
        incoming(); ready()
        Assume.assumeTrue(activity.supportsPip())
        ui { vm.store.guestPreferences(vm.preferences.value.copy(seekSeconds = 30)); vm.refreshSharedSettings() }
        until { intervalReady(30) }
        ui { vm.seekTo(50_000); activity.enterPip() }
        until { activity.isInPictureInPictureMode }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        try {
            automation.waitForIdle(500, 5000)
            val bounds = android.graphics.Rect()
            until { automation.windows.firstOrNull { it.root?.packageName?.toString() == instrumentation.targetContext.packageName }
                ?.let { it.getBoundsInScreen(bounds); !bounds.isEmpty } == true }
            fun find(node: android.view.accessibility.AccessibilityNodeInfo, label: String): android.view.accessibility.AccessibilityNodeInfo? {
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
                return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let { child -> find(child, label) } }
            }
            fun action(label: String) = automation.windows.firstNotNullOfOrNull { it.root?.let { node -> find(node, label) } }
            var lastMenuTap = 0L
            until {
                val found = action("Forward 30 seconds") != null && action("Back 30 seconds") != null
                // The OS can still be moving the PiP window after the app enters PiP.
                // Use its current bounds when opening the menu, including after a prior PiP test.
                if (!found && SystemClock.elapsedRealtime() - lastMenuTap >= 1500) {
                    automation.windows.firstOrNull { it.root?.packageName?.toString() == instrumentation.targetContext.packageName }
                        ?.getBoundsInScreen(bounds)
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                        "input tap ${bounds.centerX()} ${bounds.centerY()}")).use { it.readBytes() }
                    lastMenuTap = SystemClock.elapsedRealtime()
                }
                found
            }
            captureWatchScreenshot("seek-interval-pip")
            assertTrue(action("Forward 30 seconds")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            until { vm.playback.value.position == 80_000L }
            assertTrue(action("Back 30 seconds")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            until { vm.playback.value.position == 50_000L }
        } finally { info.flags = originalFlags; automation.serviceInfo = info }
    }
    @Test fun clipsUseSourceOffsetsButKeepRelativeTimelineLabelsAndBounds() {
        command("clips", """{"count":1}""")
        incoming("/clip/IVCL" + "0".repeat(32)); ready()
        assertNotNull(vm.playback.value.clip)
        val wasPlaying = vm.playback.value.playWhenReady
        ui { vm.updateScrub(15_000) }; preview()
        assertEquals(20_000L, vm.seekPreviews.state.value.frame!!.startMs)
        assertEquals(15_000L, vm.seekPreviews.state.value.targetMs)
        compose.onNodeWithTag("player-time", useUnmergedTree = true).assertTextContains("0:15", substring = true)
        captureWatchScreenshot("seek-preview-clip")
        ui { vm.finishScrub() }; until { vm.playback.value.position in 15_000L..18_000L }
        assertEquals(wasPlaying, vm.playback.value.playWhenReady)
        assertTrue(vm.playback.value.duration < 31_000)
        ui {
            vm.controller.value!!.pause()
            vm.store.guestPreferences(vm.preferences.value.copy(seekSeconds = 30)); vm.refreshSharedSettings()
        }
        until { intervalReady(30) }
        ui { vm.controller.value!!.seekBack() }
        until { vm.playback.value.position == 0L }
        assertFalse(vm.playback.value.playWhenReady)
    }
    @Test fun unavailableAndInvalidCropsLeaveSeekingUsableAndNeverShowTheSpriteSheet() {
        command("seek-previews", """{"previewBadCrop":true}""")
        incoming(); ready()
        beginDrag(.25f)
        until { images().length() == 1 }
        Thread.sleep(300); compose.waitForIdle()
        compose.onNodeWithTag("player-seek-preview").assertDoesNotExist()
        releaseDrag(); until { vm.playback.value.position in 25_000L..35_000L }
        command("seek-previews", """{"previewFail":true}""")
        beginDrag(.8f)
        until { images().length() >= 2 }
        releaseDrag(); until { vm.playback.value.position > 90_000 }
        assertFalse(vm.playback.value.playWhenReady)
        compose.onNodeWithTag("player-seek-preview").assertDoesNotExist()
    }
    @Test fun hiddenThumbnailsAndAudioOnlyPlaybackDoNotRequestPreviews() {
        incoming(); ready()
        ui { vm.channelSpoilers.configure(vm.preferences.value.copy(channelSpoilers = mapOf(
            vm.playback.value.details!!.video.channelId to ChannelSpoilerRule("Fixture", hideThumbnail = true))), vm.api.context()) }
        compose.waitForIdle()
        beginDrag(.3f); Thread.sleep(300); releaseDrag()
        assertEquals(0, fixture().getJSONArray("previewIndexRequests").length())
        ui { vm.channelSpoilers.configure(vm.preferences.value, vm.api.context()); vm.audioOnly(true) }
        until { !vm.playback.value.videoEnabled }
        beginDrag(.6f); Thread.sleep(300); releaseDrag()
        assertEquals(0, fixture().getJSONArray("previewIndexRequests").length())
    }
    @Test fun slowImagesCannotAppearAfterCancellationOrARepeatedVideoOccurrenceChange() {
        command("seek-previews", """{"previewDelay":1500}""")
        incoming("/watch?v=testvideo01&list=PLfixture&index=0&autoplay=0"); ready()
        beginDrag(.25f); until { images().length() == 1 }
        compose.onNodeWithTag("player-timeline").performTouchInput { cancel() }
        val next = vm.queue.value.items[2].key
        ui { vm.selectQueue(next) }; until { vm.queue.value.currentKey == next }; ready()
        Thread.sleep(1600)
        compose.onNodeWithTag("player-seek-preview").assertDoesNotExist()
        assertNull(vm.scrubSeek.value)
        assertEquals("testvideo01", vm.playback.value.mediaId)
    }
    @Test fun minimizationAndPipCancelPreviewsAndRestorePlayingIntent() {
        incoming(); ready(); ui { vm.togglePlay() }; until { vm.playback.value.playing }
        ui { vm.updateScrub(60_000) }; preview()
        compose.onNodeWithContentDescription("Minimize player").performClick()
        until { vm.scrubSeek.value == null && vm.playback.value.playing }
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        compose.onNodeWithTag("player-seek-preview").assertDoesNotExist()
        compose.onNodeWithTag("mini-player-preview").performClick(); ready()
        ui { vm.updateScrub(50_000) }; preview()
        if (activity.supportsPip()) {
            ui { activity.enterPip() }
            until { activity.isInPictureInPictureMode && vm.scrubSeek.value == null && vm.playback.value.playing }
            assertNull(vm.seekPreviews.state.value.kind)
        }
    }
    @Test fun liveVideoMetadataSuppressesPreviewRequestsEvenWhenTheFixtureTimelineIsSeekable() {
        command("sponsorblock", """{"liveNow":true}""")
        incoming(); ready()
        assertTrue(vm.playback.value.details!!.video.live)
        beginDrag(.4f); Thread.sleep(300); releaseDrag()
        until { vm.scrubSeek.value == null }
        assertEquals(0, fixture().getJSONArray("previewIndexRequests").length())
    }
}
