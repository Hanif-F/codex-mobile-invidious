package net.wingress.mobivious

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.Video
import net.wingress.mobivious.data.VideoLink
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Generated moving video only; requires scripts/test-android.sh's local fixture. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackVisibilitySmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private var originalBackground = false
    private var originalPip = false
    private val title = "A quiet moment · playback fixture"
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun command(name: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes().toString(Charsets.UTF_8).trim() } }

    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            originalBackground = activity.model.store.background; originalPip = activity.model.store.pip
            activity.model.store.background = true; activity.model.store.pip = true
            activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.save(null)
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings()
        }
        until { activity.model.browse.value.videos.isNotEmpty() }
    }

    @After fun close() {
        if (::activity.isInitialized) ui {
            activity.model.closePlayer(); activity.model.store.background = originalBackground; activity.model.store.pip = originalPip
            activity.finishAndRemoveTask()
        }
    }

    private fun openVideo() {
        compose.onNodeWithText(title).performClick()
        until { activity.model.playback.value.playing && activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_VIDEO) }
        awake(true)
    }

    // View.keepScreenOn is propagated to WindowManager by traversal, not to Window.attributes.
    private fun playerViews(view: View = activity.window.decorView): List<PlayerView> = when (view) {
        is PlayerView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { playerViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun awake(expected: Boolean) {
        until {
            var matches = false
            ui {
                matches = playerViews().any { it.keepScreenOn } == expected
            }
            matches
        }
    }

    private fun frame(): Bitmap {
        lateinit var surface: SurfaceView
        ui { surface = playerViews().single { it.player != null }.videoSurfaceView as SurfaceView }
        val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = -1
        PixelCopy.request(surface, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("Video frame copy timed out", done.await(5, TimeUnit.SECONDS))
        assertEquals("Video must render into the active surface", PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun showControls() {
        if (compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("player-gestures").performClick()
        }
        until { compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun playerDrag(dx: Float = 0f, dy: Float = 0f, duration: Long = 600) {
        val density = activity.resources.displayMetrics.density
        compose.onNodeWithTag("player-gestures").performTouchInput {
            val start = Offset(width * .5f, height * .2f)
            swipe(start, start + Offset(dx * density, dy * density), durationMillis = duration)
        }
        compose.waitForIdle()
    }

    @Test fun swipesMinimizeAndRestoreTheSameLiveSurfaceAndPreservePausedPlayback() {
        openVideo()
        val controller = activity.model.controller.value!!
        lateinit var view: PlayerView
        ui { view = playerViews().single(); controller.pause(); activity.model.seekTo(30_000); activity.model.speed(1.5f); activity.model.quality(360) }
        until { activity.model.playback.value.position == 30_000L && activity.model.playback.value.playerState == Player.STATE_READY }
        val token = activity.model.queue.value.token
        val key = activity.model.queue.value.currentKey
        val selection = activity.model.playback.value.selection!!
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("watch-description-toggle"))
        compose.onNodeWithTag("watch-description-toggle").performClick()
        playerDrag(dy = 96f)
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        compose.onNodeWithTag("watch-details-list").assertDoesNotExist()
        assertEquals("Home", activity.model.tab)
        ui { assertSame(view, playerViews().single()); assertSame(controller, activity.model.controller.value) }
        awake(false)
        playerDrag(dy = -96f)
        compose.onNodeWithTag("watch-details-list").assertIsDisplayed()
        compose.onNodeWithTag("watch-description-toggle").assertIsDisplayed()
        compose.onNodeWithText("A generated test video. No YouTube access is involved.").assertExists()
        ui {
            assertSame(view, playerViews().single()); assertEquals(token, activity.model.queue.value.token)
            assertEquals(key, activity.model.queue.value.currentKey); assertEquals(selection, controller.trackSelectionParameters)
            assertEquals(30_000L, controller.currentPosition); assertEquals(1.5f, controller.playbackParameters.speed, .001f)
            assertFalse(controller.playWhenReady)
            controller.play()
        }
        until { activity.model.playback.value.playing }
        playerDrag(dy = 96f)
        val first = frame(); Thread.sleep(350); val next = frame()
        assertFalse("The minimized video must continue rendering moving frames", first.sameAs(next))
        first.recycle(); next.recycle(); awake(true)
        compose.onNodeWithTag("mini-player-preview").performClick()
        compose.onNodeWithTag("watch-details-list").assertExists()
    }

    @Test fun portraitBrowsingResizeKeepsTheLiveSurfaceSessionAndSelections() {
        ui { activity.model.openLink(VideoLink("portrait001")); activity.sharedVideo.value = true }
        until { activity.model.playback.value.mediaId == "portrait001" && activity.model.playback.value.geometry.ratio != null && activity.model.playback.value.playing }
        val controller = activity.model.controller.value!!
        lateinit var view: PlayerView
        ui { view = playerViews().single(); controller.pause(); activity.model.seekTo(4_000); activity.model.speed(1.5f) }
        until { activity.model.playback.value.position == 4_000L && activity.model.playback.value.playerState == Player.STATE_READY }
        val token = activity.model.queue.value.token
        val selection = activity.model.playback.value.selection!!
        val content = compose.onNodeWithTag("watch-content").getUnclippedBoundsInRoot()
        val contentHeight = (content.bottom - content.top).value
        val density = activity.resources.displayMetrics.density
        val expanded = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("watch-details-list").performTouchInput {
            down(center); moveBy(Offset(0f, -180f * density), delayMillis = 600); advanceEventTime(200); up()
        }
        compose.waitForIdle()
        val compact = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        assertEquals(contentHeight * .4f, (compact.bottom - compact.top).value, 1f)
        assertTrue((compact.bottom - compact.top) < (expanded.bottom - expanded.top))
        ui {
            assertSame(view, playerViews().single()); assertSame(controller, activity.model.controller.value)
            assertEquals(4_000L, controller.currentPosition); assertFalse(controller.playWhenReady)
            assertEquals(selection, controller.trackSelectionParameters); assertEquals(token, activity.model.queue.value.token)
        }
        playerDrag(dy = 96f)
        playerDrag(dy = -96f)
        assertEquals(compact, compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("watch-details-list").performTouchInput { swipeDown(durationMillis = 100) }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        assertEquals((expanded.bottom - expanded.top).value, (restored.bottom - restored.top).value, 1f)
        ui { assertSame(view, playerViews().single()); controller.play() }
        until { activity.model.playback.value.playing }
        awake(true)
    }

    @Test fun incompleteAndCanceledDragsReturnAndBothSidewaysDirectionsStopPlayback() {
        openVideo()
        val watchBounds = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        playerDrag(dy = 20f)
        assertEquals(watchBounds, compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot())
        val density = activity.resources.displayMetrics.density
        compose.onNodeWithTag("player-gestures").performTouchInput {
            down(Offset(width * .5f, height * .2f)); moveBy(Offset(0f, 80f * density), delayMillis = 600)
        }
        compose.waitForIdle()
        val moving = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
        assertTrue("The video should shrink while the finger is still down", moving.right - moving.left < watchBounds.right - watchBounds.left)
        assertTrue(moving.top > watchBounds.top)
        ui { assertEquals(1, playerViews().size) }
        compose.onNodeWithTag("player-gestures").performTouchInput { cancel() }
        compose.waitForIdle()
        compose.onNodeWithTag("watch-details-list").assertExists()
        for (direction in listOf(-1f, 1f)) {
            playerDrag(dy = 96f)
            val miniBounds = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
            playerDrag(dx = 20f * direction)
            assertEquals(miniBounds, compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot())
            compose.onNodeWithTag("mini-player").performTouchInput {
                val start = Offset(width * .5f, height * .5f)
                swipe(start, start + Offset(20f * density * direction, 0f), durationMillis = 600)
            }
            compose.waitForIdle()
            assertEquals(miniBounds, compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot())
            playerDrag(dx = activity.resources.configuration.screenWidthDp * .5f * direction)
            until { activity.model.playback.value.details == null }
            compose.onNodeWithTag("mini-player").assertDoesNotExist(); compose.onNodeWithTag("player-surface").assertDoesNotExist()
            awake(false)
            if (direction < 0) openVideo()
        }
    }

    @Test fun claimedPresentationDragCancelsAccumulatedSeekAndRestoresPlayingIntent() {
        openVideo()
        val controller = activity.model.controller.value!!
        val position = activity.model.playback.value.position
        ui { activity.model.accumulateSeek(1) }
        assertNotNull(activity.model.pendingSeek.value)
        playerDrag(dy = 96f, duration = 100)
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        ui {
            assertNull(activity.model.pendingSeek.value); assertTrue(controller.playWhenReady)
            assertTrue("Canceling the batch must not apply its 10-second seek", controller.currentPosition < position + 5_000)
        }
    }

    @Test fun fullscreenSwipeReturnsToWatchBeforeMinimizingAndButtonsAndSemanticsMatch() {
        openVideo(); showControls()
        val controller = activity.model.controller.value!!
        val token = activity.model.queue.value.token
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }
        playerDrag(dy = 96f)
        compose.onNodeWithTag("watch-details-list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit full screen").assertDoesNotExist()
        showControls(); compose.onNodeWithContentDescription("Minimize player").performClick()
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        val restore = compose.onNodeWithTag("player-gestures").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Restore player" }
        ui { assertTrue(restore.action()); assertSame(controller, activity.model.controller.value); assertEquals(token, activity.model.queue.value.token) }
        compose.waitForIdle(); compose.onNodeWithTag("watch-details-list").assertIsDisplayed()
        val minimize = compose.onNodeWithTag("player-gestures").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Minimize player" }
        ui { assertTrue(minimize.action()) }; compose.waitForIdle()
        val dismiss = compose.onNodeWithTag("player-gestures").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Dismiss player" }
        ui { assertTrue(dismiss.action()) }; until { activity.model.playback.value.details == null }
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
    }

    @Test fun timelineCommentsQueueAndMiniButtonsKeepPriorityOverPresentationGestures() {
        openVideo(); showControls()
        compose.onNodeWithTag("player-timeline").performTouchInput { swipe(centerLeft, centerRight, durationMillis = 400) }
        compose.onNodeWithTag("watch-details-list").assertExists()
        ui { activity.model.openComments() }; until { activity.model.comments.value.open }
        compose.onNodeWithTag("comments-list").performTouchInput { swipeDown() }
        compose.onNodeWithTag("watch-content").assertExists()
        compose.onNodeWithContentDescription("Close comments").performClick()
        ui { activity.model.insertQueue(Video("testvideo02", "Next video"), true) }
        until { activity.model.queue.value.items.size == 2 }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("playback-queue"))
        if (compose.onAllNodesWithTag("playback-queue-items").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("playback-queue-header").performClick()
        compose.onNodeWithTag("playback-queue-items").performTouchInput { swipeDown() }
        compose.onNodeWithTag("watch-content").assertExists()
        playerDrag(dy = 96f)
        compose.onNodeWithContentDescription("Pause").performClick(); until { !activity.model.playback.value.playWhenReady }
        compose.onNodeWithTag("mini-player").assertExists()
        compose.onNodeWithContentDescription("Play").performClick(); until { activity.model.playback.value.playWhenReady }
        compose.onNodeWithTag("mini-player").assertExists()
        compose.onNodeWithContentDescription("Close player").performClick(); until { activity.model.playback.value.details == null }
    }

    @Test fun miniStateSurvivesActivityRecreationAndSystemAnimationScaleZero() {
        openVideo(); playerDrag(dy = 96f)
        val controller = activity.model.controller.value!!
        val token = activity.model.queue.value.token
        ui { activity.recreate() }
        until {
            var found = false
            ui { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                .singleOrNull()?.let { activity = it; found = true } }
            found
        }
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        ui { assertSame(controller, activity.model.controller.value); assertEquals(token, activity.model.queue.value.token) }
        val scale = shell("settings get global animator_duration_scale")
        try {
            shell("settings put global animator_duration_scale 0")
            playerDrag(dy = -96f); compose.onNodeWithTag("watch-details-list").assertExists()
            playerDrag(dy = 96f); compose.onNodeWithTag("mini-player").assertIsDisplayed()
        } finally {
            shell(if (scale.toFloatOrNull() != null) "settings put global animator_duration_scale $scale" else "settings delete global animator_duration_scale")
        }
    }

    private fun foreground() {
        shell("am start --windowingMode 1 -n ${activity.packageName}/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task")
        until { !activity.isInPictureInPictureMode && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    }

    @Test fun miniPlayerShowsMovingFramesAndPreservesPlaybackAndAudioMode() {
        openVideo()
        val controller = activity.model.controller.value!!
        ui { controller.pause(); activity.model.seekTo(30_000); activity.model.speed(1.5f); activity.model.quality(360) }
        until { activity.model.playback.value.position == 30_000L && activity.model.playback.value.playerState == Player.STATE_READY &&
            activity.model.playback.value.selection?.overrides?.values?.any { it.type == C.TRACK_TYPE_VIDEO } == true }
        awake(false)
        val token = activity.model.queue.value.token
        val occurrence = activity.model.queue.value.currentKey
        val selection = activity.model.playback.value.selection!!
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("mini-player").assertExists().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(80f))
        ui {
            assertSame(controller, activity.model.controller.value)
            assertEquals(30_000L, controller.currentPosition); assertEquals(selection, controller.trackSelectionParameters)
            assertEquals(1.5f, controller.playbackParameters.speed); assertFalse(controller.playWhenReady)
        }
        compose.onNodeWithContentDescription("Play").performClick()
        awake(true)
        compose.onNodeWithTag("mini-player").assertExists() // Play must not open the watch screen.
        until { activity.model.playback.value.position > 31_000 }
        val first = frame()
        val start = SystemClock.elapsedRealtime()
        until { SystemClock.elapsedRealtime() - start > 800 }
        val second = frame()
        assertFalse("Mini-player must show moving video, not a frozen frame", first.sameAs(second))
        first.recycle(); second.recycle()
        val position = activity.model.playback.value.position
        compose.onNodeWithTag("mini-player-preview").performTouchInput { click() }
        compose.onNodeWithTag("watch-details-list").assertExists()
        awake(true)
        ui {
            assertSame(controller, activity.model.controller.value)
            assertTrue(controller.currentPosition >= position)
            assertEquals(selection, controller.trackSelectionParameters)
            assertEquals(token, activity.model.queue.value.token); assertEquals(occurrence, activity.model.queue.value.currentKey)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        ui { activity.model.audioOnly(true) }
        until { C.TRACK_TYPE_VIDEO in activity.model.playback.value.selection!!.disabledTrackTypes }
        compose.onNodeWithTag("mini-player-artwork", useUnmergedTree = true).assertExists()
        awake(false)
        ui { assertTrue(playerViews().isEmpty()); assertTrue(controller.playWhenReady) }
        ui { activity.model.audioOnly(false) }
        awake(true)
        compose.onNodeWithContentDescription("Pause").performClick()
        awake(false)
        compose.onNodeWithTag("mini-player").assertExists()
        compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag("mini-player"))).performClick()
        compose.onNodeWithTag("watch-details-list").assertExists()
        awake(false)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Close player").performClick()
        until { activity.model.playback.value.details == null }
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        awake(false)
    }

    @Test fun fullScreenPipHiddenPlaybackAndSettingsHaveCorrectWakeOwnership() {
        Assume.assumeTrue(activity.supportsPip())
        openVideo(); showControls()
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }
        awake(true)
        compose.onNodeWithContentDescription("Exit full screen").performClick()
        ui { activity.enterPip() }
        until { activity.isInPictureInPictureMode }
        awake(true)
        ui { activity.model.controller.value!!.pause() }; awake(false)
        ui { activity.model.controller.value!!.play() }; awake(true)
        foreground(); awake(true)
        ui { activity.model.store.pip = false; activity.moveTaskToBack(true) }
        until { !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        awake(false)
        ui { assertTrue(activity.model.controller.value!!.playWhenReady) }
        foreground(); awake(true)
        compose.onNodeWithTag("navigation-Account").performClick(); compose.onNodeWithTag("account-settings").performClick()
        awake(false)
        ui { assertTrue(playerViews().isEmpty()) }
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        awake(true)
        ui { activity.model.closePlayer() }; awake(false)
    }

    @Test fun miniPlayerAdvancesQueueAndCompletionReleasesWake() {
        openVideo()
        ui { activity.model.insertQueue(Video("testvideo02", "Next video"), true) }
        until { activity.model.queue.value.items.size == 2 }
        compose.onNodeWithContentDescription("Back").performClick()
        ui { activity.model.seekTo(119_700) }
        until { activity.model.playback.value.mediaId == "testvideo02" && activity.model.playback.value.playing }
        compose.onNodeWithTag("mini-player").assertExists(); awake(true)
        ui { activity.model.seekTo(119_700) }
        until { activity.model.playback.value.playerState == Player.STATE_ENDED }
        awake(false)
        compose.onNodeWithContentDescription("Replay").performClick()
        until { activity.model.playback.value.playing }; awake(true)
    }

    @Test fun visibleVideoOutlastsSystemScreenTimeout() {
        openVideo()
        val originalTimeout = shell("settings get system screen_off_timeout")
        val originalStayAwake = shell("settings get global stay_on_while_plugged_in")
        try {
            shell("settings put system screen_off_timeout 5000")
            shell("settings put global stay_on_while_plugged_in 0")
            Thread.sleep(12_000)
            assertTrue((activity.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive)
            awake(true)
        } finally {
            shell(if (originalTimeout.toLongOrNull() != null) "settings put system screen_off_timeout $originalTimeout" else "settings delete system screen_off_timeout")
            shell(if (originalStayAwake.toIntOrNull() != null) "settings put global stay_on_while_plugged_in $originalStayAwake" else "settings delete global stay_on_while_plugged_in")
            shell("input keyevent KEYCODE_WAKEUP")
        }
    }

    @Test fun narrowLayoutsKeepActionsAndMiniPlayerAccessibleWithLargeTitles() {
        ui { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.tab == "Account" && !activity.model.accountBusy.value }
        compose.onNodeWithTag("navigation-Home").performClick()
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
        openVideo()
        ui { activity.model.controller.value!!.pause() }
        val originalSize = shell("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }?.substringAfter(":")?.trim()
        val originalDensity = shell("wm density").lineSequence().firstOrNull { it.startsWith("Override density:") }?.substringAfter(":")?.trim()
        val originalFont = shell("settings get system font_scale")
        fun resumed(width: Int? = null) {
            until {
                var ready = false
                ui {
                    val current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
                    if (current != null) {
                        activity = current
                        ready = width == null || current.resources.configuration.screenWidthDp == width
                    }
                }
                ready
            }
        }
        try {
            shell("wm density 480")
            shell("settings put system font_scale 1.3")
            for (width in listOf(320, 390)) {
                shell("wm size ${width * 3}x2100")
                resumed(width)
                for (mode in listOf("light", "dark")) {
                    command("preferences", """{"dark_mode":"$mode"}""")
                    ui { activity.model.refreshSharedSettings() }
                    until { activity.model.preferences.value.darkMode == mode }
                    ui {
                        val state = activity.model.playback.value
                        val details = requireNotNull(state.details)
                        activity.model.playback.value = state.copy(details = details.copy(video = details.video.copy(title = "$title " + "A very long descriptive title ".repeat(6))))
                    }
                    compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("watch-actions"))
                    compose.onNodeWithText("Suggest / vote on titles").assertDoesNotExist()
                    compose.onNodeWithText("Channel SponsorBlock settings").assertDoesNotExist()
                    compose.onNodeWithText("DeArrow Title").performScrollTo().assertIsDisplayed().performClick()
                    until { activity.model.dearrowContribution.value.open }
                    compose.onNodeWithText("Suggest / vote on titles").assertExists()
                    compose.onNodeWithContentDescription("Close DeArrow contributions").performClick()
                    shell("mkdir -p /data/local/tmp/mobivious-playback-visibility-screenshots")
                    compose.waitForIdle()
                    shell("screencap -p /data/local/tmp/mobivious-playback-visibility-screenshots/watch-$width-$mode-large-text.png")
                    compose.onNodeWithContentDescription("Back").performClick()
                    compose.onNodeWithTag("mini-player").assertIsDisplayed()
                    compose.onNodeWithTag("mini-player-preview").assertIsDisplayed()
                    compose.onNodeWithContentDescription("Play").assertIsDisplayed()
                    compose.onNodeWithContentDescription("Close player").assertIsDisplayed()
                    compose.waitForIdle()
                    shell("screencap -p /data/local/tmp/mobivious-playback-visibility-screenshots/mini-$width-$mode-large-text.png")
                    compose.onNodeWithTag("mini-player-preview").performClick()
                }
            }
        } finally {
            shell(if (originalSize != null) "wm size $originalSize" else "wm size reset")
            shell(if (originalDensity != null) "wm density $originalDensity" else "wm density reset")
            shell(if (originalFont.toFloatOrNull() != null) "settings put system font_scale $originalFont" else "settings delete system font_scale")
            resumed()
            ui { activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings() }
        }
    }
}
