package net.wingress.mobivious

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.DownloadSelection
import net.wingress.mobivious.player.StatisticKey
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackStatisticsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val app get() = activity.application as MobiviousApplication
    private val statistics get() = vm.playbackStatistics
    private var download: String? = null
    private var oldBackground = false
    private var oldPip = false
    private fun ui(action: () -> Unit) = compose.runOnUiThread(action)
    private fun until(condition: () -> Boolean) {
        try { compose.waitUntil(45_000, condition) }
        catch (e: Throwable) { throw AssertionError("lifecycle=${activity.lifecycle.currentState} pip=${activity.isInPictureInPictureMode} statistics=${statistics.ui.value} state=${vm.playback.value.playerState} source=${statistics.snapshot.value.videoId} error=${vm.playback.value.error}", e) }
    }
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun ready() = !vm.playback.value.loading && vm.playback.value.playerState == Player.STATE_READY
    private fun position(): Long { var value = 0L; ui { value = vm.controller.value!!.currentPosition }; return value }
    private fun foreground() {
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.waitForIdle(500, 5000) // Let the system PiP transition finish before expanding its task.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(shell.executeShellCommand(
            "am start --windowingMode 1 -n ${activity.packageName}/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task")).use { it.readBytes() }
        ui { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        until {
            ui { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                .singleOrNull()?.let { activity = it } }
            !activity.isInPictureInPictureMode && activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        until { statistics.ui.value.visible }
    }
    @Before fun open() {
        command("reset"); command("stream", """{"type":"rich"}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            oldBackground = vm.store.background; oldPip = vm.store.pip
            vm.store.background = true; vm.store.pip = false
            vm.store.guestPreferences(AccountPreferences()); vm.refreshSharedSettings()
        }
        until { vm.browse.value.videos.isNotEmpty() && vm.controller.value != null }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { ready() && vm.playback.value.playing }
        ui { vm.controller.value!!.pause(); vm.seekTo(5000) }
        until { ready() && !vm.playback.value.playWhenReady && vm.playback.value.position == 5000L }
    }
    @After fun close() {
        if (::activity.isInitialized) {
            ui { vm.closePlayer(); vm.store.background = oldBackground; vm.store.pip = oldPip }
            download?.let { runBlocking { app.downloads.delete(it) } }
            ui { activity.finishAndRemoveTask() }
        }
    }
    private fun enable() {
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Playback statistics").assertDoesNotExist()
        compose.expandPlayerSettingsMore()
        compose.onNodeWithText("Playback statistics").performScrollTo().performClick()
        until { statistics.ui.value.enabled && statistics.ui.value.visible && statistics.snapshot.value.metrics.isNotEmpty() }
        compose.onNodeWithTag("playback-statistics").assertIsDisplayed()
    }
    private fun recreate() {
        val old = activity
        ui { activity.recreate() }
        until {
            var resumed = false
            ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }
            resumed
        }
        ui { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    private fun advanceObservation() {
        val previous = statistics.snapshot.value.observationSeconds
        until { statistics.snapshot.value.observationSeconds > previous + .5 }
    }

    @Test fun moreEntryShowsRealDecoderDataAndCopiesFreshSanitizedDiagnosticsWithoutRequests() {
        val requests = fixture().getJSONArray("videoDetailRequests").length()
        enable()
        until { statistics.snapshot.value.metrics[StatisticKey.frames]?.value != null && statistics.snapshot.value.metrics[StatisticKey.bandwidth]?.value != null }
        assertEquals("DASH", statistics.snapshot.value.mode)
        assertNotNull(statistics.snapshot.value.metrics[StatisticKey.resolution]?.value)
        captureWatchScreenshot("statistics-compact")
        compose.onNodeWithContentDescription("Playback statistics details").performClick()
        ui { vm.speed(1.5f) }
        compose.onNodeWithContentDescription("Copy diagnostics").performClick()
        lateinit var copied: String
        ui { copied = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString() }
        val json = JSONObject(copied)
        assertEquals("testvideo01", json.getString("videoId"))
        assertTrue(json.getJSONObject("metrics").getString("state").contains("1.5×"))
        assertFalse(copied.contains("http")); assertFalse(copied.contains("Bearer")); assertFalse(copied.contains("/media/"))
        compose.onNodeWithText("Diagnostics copied").assertIsDisplayed()
        assertEquals(requests, fixture().getJSONArray("videoDetailRequests").length())
        assertFalse(vm.playback.value.playWhenReady)
        assertEquals(5000, position())
        compose.onNodeWithContentDescription("Close playback statistics").performClick()
        compose.onNodeWithTag("playback-statistics").assertDoesNotExist()
        assertFalse(statistics.ui.value.enabled)
    }

    @Test fun fullscreenRecreationBackAndPanelGesturesPreservePlayback() {
        enable()
        compose.onNodeWithContentDescription("Playback statistics details").performClick()
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("statistics-buffer-graph").performScrollTo().assertIsDisplayed()
        captureWatchScreenshot("statistics-fullscreen-details")
        val beforePosition = position()
        compose.onNodeWithTag("playback-statistics-content").performTouchInput { swipeDown() }
        assertEquals(beforePosition, position())
        assertFalse(vm.playback.value.playWhenReady)
        recreate()
        until { statistics.ui.value.visible }
        assertTrue(statistics.ui.value.enabled); assertTrue(statistics.ui.value.expanded)
        compose.onNodeWithTag("playback-statistics").assertIsDisplayed()
        ui { activity.onBackPressedDispatcher.onBackPressed() }
        until { !statistics.ui.value.expanded }
        assertTrue(statistics.ui.value.enabled)
        ui { activity.onBackPressedDispatcher.onBackPressed() }
        until { !statistics.ui.value.enabled }
        compose.onNodeWithContentDescription("Exit full screen").assertExists()
    }

    @Test fun hiddenBackgroundAndPipSuspendObservationAndResumeTheSameOverlay() {
        enable(); advanceObservation()
        ui { vm.store.pip = false; activity.moveTaskToBack(true) }
        until { !statistics.ui.value.visible }
        val hidden = statistics.snapshot.value.observationSeconds
        android.os.SystemClock.sleep(1200)
        assertEquals(hidden, statistics.snapshot.value.observationSeconds, .05)
        assertTrue(statistics.ui.value.enabled)
        foreground(); advanceObservation()
        ui { vm.controller.value!!.play() }
        until { vm.playback.value.playing }
        ui { vm.store.pip = true; activity.enterPip() }
        until { activity.isInPictureInPictureMode && !statistics.ui.value.visible }
        assertTrue(statistics.ui.value.enabled)
        foreground()
        compose.onNodeWithTag("playback-statistics").assertIsDisplayed()
    }

    @Test fun miniPlayerAndSettingsSuspendStatisticsAndRestoreTheSessionToggle() {
        enable()
        compose.onNodeWithContentDescription("Player settings").performClick()
        until { !statistics.ui.value.visible }
        assertTrue(statistics.ui.value.enabled)
        compose.expandPlayerSettingsMore()
        compose.onNode(hasText("Playback statistics") and hasText("On")).assertExists()
        compose.onNodeWithContentDescription("Close player settings").performClick()
        until { statistics.ui.value.visible }
        val density = activity.resources.displayMetrics.density
        compose.onNodeWithTag("player-gestures").performTouchInput {
            val start = Offset(width * .95f, height * .35f)
            swipe(start, start + Offset(0f, 100 * density), durationMillis = 600)
        }
        until { !statistics.ui.value.visible && compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(statistics.ui.value.enabled)
        compose.onNodeWithTag("mini-player-preview").performClick()
        until { statistics.ui.value.visible }
        compose.onNodeWithTag("playback-statistics").assertIsDisplayed()
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.expandPlayerSettingsMore()
        compose.onNodeWithText("Playback statistics").performScrollTo().performClick()
        until { !statistics.ui.value.enabled }
        compose.onNodeWithTag("playback-statistics").assertDoesNotExist()
    }

    @Test fun refreshQueueAdvanceHlsProgressiveAndAudioOnlyResetSourceMeasurements() {
        enable(); advanceObservation()
        ui { vm.refreshBuffer() }
        until { ready() && statistics.snapshot.value.metrics.isNotEmpty() }
        assertTrue(statistics.ui.value.enabled)
        for (mode in listOf("hls", "mp4", "rich")) {
            command("stream", """{"type":"$mode"}""")
            ui { vm.retryPlayback() }
            val expected = when (mode) { "hls" -> "HLS"; "mp4" -> "progressive"; else -> "DASH" }
            until { ready() && statistics.snapshot.value.mode == expected }
            assertTrue(statistics.ui.value.enabled)
            assertNotNull(statistics.snapshot.value.metrics[StatisticKey.buffer]?.value)
        }
        ui { vm.audioOnly(true) }
        until { statistics.snapshot.value.mode.startsWith("audio-only") && statistics.snapshot.value.metrics[StatisticKey.resolution]?.value == null }
        assertEquals("Unavailable", statistics.snapshot.value.metrics[StatisticKey.resolution]?.reason)
        val previous = vm.queue.value.currentKey
        ui { vm.insertQueue(net.wingress.mobivious.data.Video("testvideo02", "Second"), true) }
        until { vm.queue.value.items.size >= 2 }
        ui { vm.controller.value!!.seekToNextMediaItem() }
        until { ready() && vm.queue.value.currentKey != previous && statistics.snapshot.value.videoId == "testvideo02" && statistics.snapshot.value.metrics.isNotEmpty() }
        assertTrue(statistics.ui.value.enabled)
        assertEquals("0 / 0", statistics.snapshot.value.metrics[StatisticKey.stalls]?.value)
    }

    @Test fun downloadedPlaybackMarksNetworkMetricsUnavailable() {
        enable()
        command("downloads", """{"downloadDisabled":false,"downloadSlow":false,"downloadFailKey":"","downloadDuration":12}""")
        val id = runBlocking { app.downloads.create(app.store.server, app.api.downloads("testvideo01"), DownloadSelection("v360", "aen")) }
        download = id
        until { app.downloads.find(id)?.playable == true }
        ui { vm.playDownload(id) }
        until { ready() && statistics.snapshot.value.mode == "offline" }
        for (key in listOf(StatisticKey.bytes, StatisticKey.requests, StatisticKey.bandwidth)) {
            assertNull(statistics.snapshot.value.metrics[key]?.value)
            assertEquals("Unavailable", statistics.snapshot.value.metrics[key]?.reason)
        }
        assertNotNull(statistics.snapshot.value.metrics[StatisticKey.resolution]?.value)
    }

    @Test fun liveWindowReportsAvailableEdgeDistance() {
        enable()
        command("recovery", """{"recoveryLive":true}""")
        ui { vm.retryPlayback() }
        until { ready() && vm.playback.value.live && statistics.snapshot.value.metrics[StatisticKey.live]?.value != null }
        assertTrue(statistics.snapshot.value.metrics[StatisticKey.live]!!.value!!.toDouble() >= 0)
    }
}
