package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import org.junit.*
import org.junit.Assert.*
import java.net.URL

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CaptionsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val app get() = activity.application as MobiviousApplication
    private val downloads = mutableListOf<String>()
    private fun ui(action: () -> Unit) = compose.runOnUiThread(action)
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun command(name: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun cues(): String { var text = ""; ui { text = vm.controller.value?.currentCues?.cues?.joinToString { it.text.toString() }.orEmpty() }; return text }
    private fun ready() = until { !vm.playback.value.loading && vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.details != null }
    private fun open(type: String = "dash", caption: String? = "en", seconds: Long = 5) {
        command("stream", """{"type":"$type"}""")
        ui { vm.play("testvideo01", seconds) }; ready()
        ui { vm.captions(caption) }
        if (caption != null) until { cues().isNotBlank() }
    }
    private fun controls() {
        if (compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
        until { compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun captionMenu() { controls(); compose.onNodeWithContentDescription("Player settings").performClick(); compose.onNodeWithText("Captions").performClick() }
    private fun recreate() {
        val active = vm.playback.value.details != null
        val old = activity; ui { activity.recreate() }
        until { var next: MainActivity? = null; ui { next = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull { it !== old } }; if (next != null) activity = next!!; next != null }
        until { vm.controller.value != null }; if (active) ready()
    }
    private fun visibleMagenta(tag: String = "player-surface", minimum: Int = 100) {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val p = pixels[x,y]; if (p.red > .7f && p.green < .4f && p.blue > .7f) count++
        }
        assertTrue("Decoded captions must paint visible text, not just select a track", count > minimum)
    }
    private fun captionPixelsDisappearWhenDisabled() {
        val on = compose.onNodeWithTag("player-surface").captureToImage().toPixelMap()
        ui { vm.captions(null) }; until { cues().isEmpty() }
        val off = compose.onNodeWithTag("player-surface").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until on.height) for (x in 0 until on.width) {
            val a = on[x,y]; val b = off[x,y]
            if (a.red > .7f && a.green < .4f && a.blue > .7f &&
                !(b.red > .7f && b.green < .4f && b.blue > .7f)) changed++
        }
        assertTrue("Caption glyphs, independent of the colored video fixture, must disappear when turned off", changed > 100)
        ui { vm.captions("en") }; until { cues().contains("ENGLISH CAPTION") }
    }
    @Before fun launch() {
        command("reset"); command("captions")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext.applicationContext as MobiviousApplication
            app.store.save(null); app.store.server = "http://127.0.0.1:18080"; app.cache.clear()
            app.store.guestPreferences(AccountPreferences(autoplay = false, continueAutoplay = false))
            app.store.captionAppearance(CaptionAppearance())
        }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    @After fun close() {
        if (::activity.isInitialized) {
            ui { vm.closePlayer() }
            runBlocking { downloads.forEach { app.downloads.delete(it) } }
            ui { activity.finishAndRemoveTask() }
        }
    }

    @Test fun decodedCaptionsPaintAndSurviveLanguageOffGapsRefreshRetryAndRecreation() {
        open(); assertTrue(cues().contains("ENGLISH CAPTION"))
        ui { vm.setCaptionAppearance(CaptionAppearance(size = 150, textColor = "#FF00FF", edge = "none")) }
        until { vm.preferenceSaves.edits.value.isEmpty() }; visibleMagenta(); captionPixelsDisappearWhenDisabled()
        ui { vm.captions("id") }; until { cues().contains("KETERANGAN INDONESIA") }; visibleMagenta()
        ui { vm.captions(null) }; until { cues().isEmpty() }
        ui { vm.captions("en"); vm.seekTo(30_000) }; until { vm.playback.value.position == 30_000L && cues().isEmpty() }
        assertNull(vm.captionFailure.value)
        ui { vm.seekTo(5000) }; until { cues().contains("ENGLISH CAPTION") }
        ui { vm.refreshBuffer() }; ready(); until { cues().contains("ENGLISH CAPTION") }
        ui { vm.retryPlayback() }; ready(); until { cues().contains("ENGLISH CAPTION") }
        recreate(); until { cues().contains("ENGLISH CAPTION") }; visibleMagenta()
        assertEquals(150, SessionStore(activity).captionAppearance().size)
        captureWatchScreenshot("caption-decoded-watch")
    }

    @Test fun appearanceEditorIsSharedAndChangesPreservePlaybackAndSelection() {
        open(); captionMenu(); compose.onNodeWithText("Caption appearance").performClick()
        compose.onNodeWithText("Text size").performScrollTo().performClick()
        compose.onNode(hasText("200%") and hasAnyAncestor(isPopup())).performClick()
        until { vm.captionAppearance.value.size == 200 }
        compose.onNode(hasText("Text size") and hasText("200%")).assertExists()
        captureWatchScreenshot("caption-appearance-player")
        assertTrue(cues().contains("ENGLISH CAPTION")); assertFalse(vm.playback.value.playWhenReady)
        assertTrue(vm.playback.value.position in 4800..5200)
        compose.onNodeWithContentDescription("Close player settings").performClick()
        ui { vm.closePlayer() }; until { vm.playback.value.details == null }
        compose.selectFeed("You"); compose.openAppSettings(); compose.onNodeWithText("Playback").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Caption appearance"))
        compose.onNodeWithText("Caption appearance").performClick()
        compose.onNodeWithText("Text size").performScrollTo().performClick()
        compose.onNode(hasText("200%") and hasAnyAncestor(isPopup())).assertIsSelected().performClick()
        recreate()
        captureWatchScreenshot("caption-appearance-settings")
        compose.onNodeWithTag("caption-reset").performScrollTo().performClick()
        until { vm.preferenceSaves.edits.value.isEmpty() && vm.captionAppearance.value == CaptionAppearance() }
        assertEquals(CaptionAppearance(), SessionStore(activity).captionAppearance())
    }

    @Test fun networkErrorsAreReportedForCaptionsAndRetryRecovers() {
        command("captions", """{"fail":true}"""); open(caption = null)
        ui { vm.captions("en") }
        until { vm.captionFailure.value != null }
        captionMenu(); compose.onNodeWithTag("caption-load-error").assertExists()
        command("captions"); compose.onNodeWithText("Retry captions").performClick()
        ready(); until { cues().contains("ENGLISH CAPTION") && vm.captionFailure.value == null }
        compose.onNodeWithText("Off").performScrollTo().performClick(); until { cues().isEmpty() }
    }

    @Test fun hlsProgressiveFullscreenAndOfflineUseTheSameVisibleStyle() {
        ui { vm.setCaptionAppearance(CaptionAppearance(size = 150, textColor = "#FF00FF", edge = "none")) }
        until { vm.preferenceSaves.edits.value.isEmpty() }
        for (type in listOf("hls", "mp4")) {
            open(type); assertTrue(cues().contains("ENGLISH CAPTION")); visibleMagenta()
            ui { vm.closePlayer() }; until { vm.playback.value.details == null }
        }
        open(); controls(); compose.onNodeWithContentDescription("Full screen").performClick()
        until { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }
        until { cues().contains("ENGLISH CAPTION") }; visibleMagenta(); captureWatchScreenshot("caption-fullscreen")
        compose.onNodeWithContentDescription("Exit full screen").performClick()
        ui { vm.closePlayer() }; until { vm.playback.value.details == null }
        val id = runBlocking { app.downloads.create(app.store.server, app.api.downloads("testvideo01"), DownloadSelection("v360", "aen", setOf("cen"))) }
        downloads += id
        until { app.downloads.find(id)?.assets?.all { it.status == DownloadStatus.COMPLETE } == true }
        ui { vm.playDownload(id) }; ready(); ui { vm.captions("en"); vm.seekTo(4000) }
        until { cues().contains("Mobivious caption test") }; visibleMagenta(); captureWatchScreenshot("caption-offline")
    }

    @Test fun miniPlayerAndPipRetainCaptionCuesAndAppearance() {
        open()
        ui { vm.setCaptionAppearance(CaptionAppearance(size = 150, textColor = "#FF00FF", edge = "none")); vm.store.pip = true }
        until { vm.preferenceSaves.edits.value.isEmpty() }; controls()
        compose.onNodeWithContentDescription("Minimize player").performClick()
        until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
        until { cues().contains("ENGLISH CAPTION") }; visibleMagenta(minimum = 20); captureWatchScreenshot("caption-mini")
        compose.onNodeWithTag("mini-player-preview").performClick()
        controls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithTag("player-settings-list").performScrollToNode(hasText("Picture in picture"))
        compose.onNodeWithText("Picture in picture").performClick()
        until { activity.isInPictureInPictureMode }
        compose.onNodeWithContentDescription("Close player settings").assertDoesNotExist()
        compose.onNodeWithTag("player-controls").assertDoesNotExist()
        assertTrue(cues().contains("ENGLISH CAPTION"))
        // PiP is STARTED, so Compose's RESUMED-only capture cannot see its root.
        // Capture the actual system window after the OS enter animation instead.
        Thread.sleep(1200)
        captureWatchScreenshot("caption-pip")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("am start -n ${instrumentation.targetContext.packageName}/net.wingress.mobivious.MainActivity --activity-clear-top").let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
        }
        until { !activity.isInPictureInPictureMode }; ready(); visibleMagenta()
    }

    @Test fun clipPreviewInheritsSelectedLanguageAndUsesTheSharedStyle() {
        ui { vm.action { vm.login("CaptionViewer", "fixture-password") } }
        until { vm.account.value != null && !vm.accountBusy.value }
        open()
        ui { vm.controller.value!!.pause(); vm.setCaptionAppearance(CaptionAppearance(size = 150, textColor = "#FF00FF", edge = "none")) }
        until { vm.preferenceSaves.edits.value.isEmpty() }
        ui { vm.openClipEditor(); vm.clipTimes(5000, 15000) }
        until { vm.clipEditor.value.open && !vm.clipEditor.value.loadingFrames }
        compose.onNodeWithTag("clip-preview-toggle").performScrollTo().performClick()
        fun previewCues(): String {
            var text = ""
            ui {
                fun find(view: android.view.View) {
                    if (view is androidx.media3.ui.PlayerView && view.player is androidx.media3.exoplayer.ExoPlayer)
                        text = view.player!!.currentCues.cues.joinToString { it.text.toString() }
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))
                }
                android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::find)
            }
            return text
        }
        until { previewCues().contains("ENGLISH CAPTION") }; visibleMagenta("clip-preview"); captureWatchScreenshot("caption-clip-preview")
        ui { vm.closeClipEditor() }
    }
}
