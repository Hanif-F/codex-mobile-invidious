package net.wingress.mobivious

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.ui.liquidColorScheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Real SurfaceView copies, paused seeks and device preference changes against local media. */
@RunWith(AndroidJUnit4::class)
class AmbientModeSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private var animator = "1.0"
    private var originalServer = ""
    private var originalAmbient = true
    private var originalTransparency = false
    private var originalDensity: String? = null
    private var originalFontScale = "1.0"
    private fun ui(action: () -> Unit) = compose.runOnUiThread(action)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun command(path: String) {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write("{}".toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun currentActivity() = ui {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
            .firstOrNull()?.let { activity = it }
    }
    @Before fun launch() {
        command("reset")
        animator = shell("settings get global animator_duration_scale")
        originalDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        originalFontScale = shell("settings get system font_scale").takeIf { it.toFloatOrNull() != null } ?: "1.0"
        shell("settings put global animator_duration_scale 1")
        val instrument = InstrumentationRegistry.getInstrumentation()
        activity = instrument.startActivitySync(Intent(instrument.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            originalServer = vm.store.server; originalAmbient = vm.store.ambientMode.value
            originalTransparency = vm.store.reduceTransparency.value
            vm.closePlayer(); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences(autoplay = false, darkMode = "dark")); vm.refreshSharedSettings()
            vm.store.reduceTransparency(false); vm.store.ambientMode(true); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        compose.onNodeWithTag("video-card-testvideo01").performClick()
        until { vm.playback.value.details != null }
        ui { vm.play("ambient0001") }
        until { vm.playback.value.mediaId == "ambient0001" && vm.playback.value.playerState == Player.STATE_READY }
    }
    @After fun close() {
        compose.mainClock.autoAdvance = true
        currentActivity()
        if (::activity.isInitialized) ui {
            vm.closePlayer(); vm.store.ambientMode(originalAmbient); vm.store.reduceTransparency(originalTransparency)
            vm.store.guestDeArrow(AccountPreferences()); vm.switchServer(originalServer); activity.finishAndRemoveTask()
        }
        shell(if (animator == "null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $animator")
        shell("wm density ${originalDensity ?: "reset"}")
        shell("settings put system font_scale $originalFontScale")
    }
    private fun background(): Color {
        val pixels = compose.onNodeWithTag("watch-ambient-background").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
    private fun expectColor(red: Boolean) {
        until {
            if (compose.onAllNodesWithTag("watch-ambient-background").fetchSemanticsNodes().isEmpty()) false
            else background().let { if (red) it.red > it.blue + .04f else it.blue > it.red + .04f }
        }
    }

    @Test fun pausedSeeksAndRestorationUseLiveFramesAndBothTogglesShareTheSavedPreference() {
        expectColor(true)
        assertFalse(vm.playback.value.playing)
        captureWatchScreenshot("ambient-live-dark-red")
        ui { vm.seekTo(9_000) }
        expectColor(false)
        captureWatchScreenshot("ambient-live-dark-blue")
        ui { vm.store.guestDeArrow(AccountPreferences(autoplay = false, darkMode = "light")); vm.refreshSharedSettings() }
        until { vm.preferences.value.darkMode == "light" }
        until { background().red > .35f }
        captureWatchScreenshot("ambient-live-light-blue")
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithContentDescription("Ambient mode").performClick()
        assertFalse(vm.store.ambientMode.value)
        assertFalse(SessionStore(activity).ambientMode.value)
        compose.onNodeWithContentDescription("Close player settings").performClick()
        compose.onNodeWithTag("watch-ambient-background").assertDoesNotExist()
        val metadata = compose.onNodeWithTag("watch-metadata").captureToImage().toPixelMap()
        for (y in listOf(1, metadata.height / 2, metadata.height - 2)) {
            val surface = liquidColorScheme(false, vm.store.coloredBackgrounds.value).surface
            assertEquals(surface.red, metadata[1, y].red, .01f)
            assertEquals(surface.green, metadata[1, y].green, .01f)
            assertEquals(surface.blue, metadata[1, y].blue, .01f)
        }
        captureWatchScreenshot("player-background-ambient-off")
        compose.openAppSettings()
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        compose.onNodeWithText("Ambient mode").performScrollTo().performClick()
        assertTrue(vm.store.ambientMode.value)
        assertTrue(SessionStore(activity).ambientMode.value)
        compose.onNodeWithContentDescription("Back from Appearance").performClick()
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        compose.onNodeWithTag("mini-player-preview").performClick()
        expectColor(false)
        ui { vm.store.reduceTransparency(true) }
        compose.onNodeWithTag("watch-ambient-background").assertDoesNotExist()
        ui { vm.store.reduceTransparency(false) }
        expectColor(false)
        ui { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("mini-player-preview").assertIsDisplayed()
        compose.onNodeWithTag("watch-ambient-background").assertDoesNotExist()
        compose.onNodeWithTag("mini-player-preview").performClick()
        expectColor(false)
        ui { vm.audioOnly(true) }
        compose.onNodeWithTag("watch-ambient-background").assertDoesNotExist()
    }

    @Test fun liveColorPhasesAndSystemAnimationPreferenceAreObserved() {
        expectColor(true)
        val record = InstrumentationRegistry.getArguments().getString("ambientRecord") == "true"
        ui { vm.togglePlay() }
        until { vm.playback.value.playing && vm.playback.value.position > 4_300 }
        until { background().let { it.green > it.red + .04f && it.green > it.blue + .04f } }
        if (record) {
            until { vm.playback.value.position > 9_300 }
            expectColor(false)
        }
        ui { vm.togglePlay() }
        shell("settings put global animator_duration_scale 0")
        compose.onNodeWithTag("watch-ambient-background").assertDoesNotExist()
        shell("settings put global animator_duration_scale 1")
        until { compose.onAllNodesWithTag("watch-ambient-background").fetchSemanticsNodes().isNotEmpty() }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun playerBackingAndNativeShutterStayBlackAcrossWatchAndFullscreen() {
        fun nativePlayer(view: android.view.View): androidx.media3.ui.PlayerView? {
            if (view is androidx.media3.ui.PlayerView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                nativePlayer(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        fun assertShutter(expected: Int) = ui {
            val player = nativePlayer(activity.window.decorView)!!
            val shutter = player.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)
            assertEquals(expected, (shutter.background as android.graphics.drawable.ColorDrawable).color)
        }
        fun assertBarsBlack() {
            val bounds = compose.onNodeWithTag("player-surface").fetchSemanticsNode().boundsInRoot
            val ratio = vm.playback.value.geometry.ratio!!
            val sideBars = bounds.width / bounds.height > ratio
            val barWidth = if (sideBars) (bounds.width - bounds.height * ratio) / 2
                else (bounds.height - bounds.width / ratio) / 2
            // An exactly fitted watch picture has no unused pixels to sample.
            if (barWidth < 4f) return
            // System rotation/cutout animations can outlive Compose's idle state.
            compose.waitUntil(5_000) {
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
                try {
                    listOf(false, true).all { farEdge ->
                        val x = if (!sideBars) bounds.center.x.toInt() else if (farEdge) bounds.right.toInt() - 3 else bounds.left.toInt() + 2
                        val y = if (sideBars) bounds.center.y.toInt() else if (farEdge) bounds.bottom.toInt() - 3 else bounds.top.toInt() + 2
                        bitmap.getPixel(x, y) == android.graphics.Color.BLACK
                    }
                } finally { bitmap.recycle() }
            }
        }
        for ((id, night) in listOf("portrait001" to true, "landscape01" to false)) {
            ui {
                vm.store.guestDeArrow(AccountPreferences(autoplay = false, darkMode = if (night) "dark" else "light"))
                vm.refreshSharedSettings(); vm.play(id)
            }
            until { vm.playback.value.mediaId == id && vm.playback.value.playerState == Player.STATE_READY &&
                vm.playback.value.geometry.ratio != null }
            compose.onNodeWithContentDescription("Full screen").performClick()
            val orientation = if (night) android.content.res.Configuration.ORIENTATION_PORTRAIT
                else android.content.res.Configuration.ORIENTATION_LANDSCAPE
            until { currentActivity(); activity.resources.configuration.orientation == orientation &&
                compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            assertShutter(android.graphics.Color.BLACK)
            assertBarsBlack()
            captureWatchScreenshot("player-background-fullscreen-${if (night) "portrait" else "landscape"}-shown")
            compose.onNodeWithTag("player-gestures").performClick()
            until { compose.onAllNodesWithTag("player-controls").fetchSemanticsNodes().isEmpty() }
            compose.waitForIdle()
            assertBarsBlack()
            captureWatchScreenshot("player-background-fullscreen-${if (night) "portrait" else "landscape"}-hidden")
            ui { activity.onBackPressedDispatcher.onBackPressed() }
            until { currentActivity(); activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
                compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("player-gestures").performClick()
            until { currentActivity(); compose.onAllNodesWithContentDescription("Full screen").fetchSemanticsNodes().isNotEmpty() }
            assertShutter(android.graphics.Color.BLACK)
            assertBarsBlack()
            captureWatchScreenshot("player-background-watch-${if (night) "portrait" else "landscape"}")
        }
    }

    @Test fun wideAndLargeTextWatchLayoutsAndCommentsRetainTheirReadingSurfaces() {
        expectColor(true)
        shell("wm density 200")
        until { currentActivity(); activity.resources.configuration.screenWidthDp >= 840 }
        compose.onNodeWithTag("watch-more").performScrollTo().assertIsDisplayed()
        until { compose.onAllNodesWithTag("watch-ambient-background").fetchSemanticsNodes().isNotEmpty() }
        captureWatchScreenshot("ambient-live-wide")
        shell("wm density 640")
        shell("settings put system font_scale 2.0")
        until { currentActivity(); activity.resources.configuration.screenWidthDp <= 360 && activity.resources.configuration.fontScale >= 2f }
        compose.onNodeWithTag("watch-more").performScrollTo().assertIsDisplayed()
        until { compose.onAllNodesWithTag("watch-ambient-background").fetchSemanticsNodes().isNotEmpty() }
        captureWatchScreenshot("ambient-live-large-text")
        compose.onNodeWithText("Comments").performScrollTo().performClick()
        compose.onNodeWithTag("comments-toolbar").assertIsDisplayed()
        captureWatchScreenshot("ambient-live-comments")
    }
}
