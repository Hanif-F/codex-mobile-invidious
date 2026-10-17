package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.net.URL

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AnnotationsViewingSmokeTest {
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
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun ready(id: String) = until { vm.playback.value.mediaId == id && !vm.playback.value.loading && vm.playback.value.playerState == Player.STATE_READY }
    private fun open(id: String = "spherevid01") {
        ui { vm.play(id, 1) }; ready(id); ui { vm.controller.value!!.pause() }
        until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithContentDescription("Minimize player").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("mini-player-preview").performClick()
        until { compose.onAllNodesWithContentDescription("Minimize player").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun controls() {
        if (compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
        until { compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun menu(label: String) {
        controls(); compose.onNodeWithContentDescription("Player settings").performClick()
        if (label == "Annotations") compose.expandPlayerSettingsMore()
        compose.onNodeWithTag("player-settings-list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
    }
    private fun closeMenu() = compose.onNodeWithContentDescription("Close player settings").performClick()
    private fun recreate() {
        val id = vm.queue.value.details?.video?.id
        val old = activity; ui { old.recreate() }
        until { var next: MainActivity? = null; ui { next = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull { it !== old } }; if (next != null) activity = next!!; next != null }
        until { vm.controller.value != null }; if (id != null) ready(id)
    }
    private fun color(label: String, predicate: (Float, Float, Float) -> Boolean) {
        // Sample beside the central transport target, using the decoded picture pixels.
        until {
            val bounds = compose.onNodeWithTag("player-surface").fetchSemanticsNode().boundsInWindow
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return@until false
            var total = 0; var matching = 0
            for (y in (bounds.top + bounds.height * .30f).toInt() until (bounds.top + bounds.height * .40f).toInt())
                for (x in (bounds.left + bounds.width * .58f).toInt() until (bounds.left + bounds.width * .63f).toInt()) {
                    val p = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)); total++
                    if (predicate(android.graphics.Color.red(p) / 255f, android.graphics.Color.green(p) / 255f, android.graphics.Color.blue(p) / 255f)) matching++
                }
            bitmap.recycle()
            total > 0 && matching > total * .65
        }
        captureWatchScreenshot("360-$label")
    }
    private fun front() = color("front") { r,g,b -> g > .35f && r < .25f && b < .25f }
    private fun right() = color("right") { r,g,b -> b > .35f && r < .25f && g < .25f }
    private fun up() = color("up") { r,g,b -> r < .25f && g > .35f && b > .35f }
    private fun down() = color("down") { r,g,b -> r > .35f && g < .25f && b > .35f }

    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext.applicationContext as MobiviousApplication
            app.store.save(null); app.store.server = "http://127.0.0.1:18080"; app.cache.clear()
            app.store.guestPreferences(AccountPreferences(autoplay = false, continueAutoplay = false))
            app.store.background = true; app.store.pip = false
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
    @Test fun moreStartsCollapsedRestoresWithinTheSheetAndResetsOnReopenAndNewOccurrence() {
        open("testvideo01"); controls()
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Viewing mode").assertDoesNotExist()
        compose.onNodeWithText("Annotations").assertDoesNotExist()
        compose.onNodeWithText("Audio only").assertDoesNotExist()
        compose.onNodeWithTag("player-settings-more").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Collapsed"))
        compose.expandPlayerSettingsMore()
        compose.onNodeWithText("Annotations").performClick()
        compose.onNodeWithContentDescription("Back to player settings").performClick()
        compose.onNodeWithText("Annotations").assertExists()
        recreate()
        compose.onNodeWithContentDescription("Close player settings").assertExists()
        compose.onNodeWithText("Annotations").assertExists()
        captureWatchScreenshot("player-more-restored")
        closeMenu(); controls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Annotations").assertDoesNotExist()
        compose.expandPlayerSettingsMore()
        val previous = vm.queue.value.currentKey
        ui { vm.play("testvideo01", 1) }
        until { vm.queue.value.currentKey != previous }; ready("testvideo01")
        compose.onNodeWithText("Annotations").assertDoesNotExist()
        compose.expandPlayerSettingsMore()
        compose.onNodeWithTag("player-settings-list").performScrollToNode(hasText("Audio only"))
        compose.onNodeWithText("Audio only").performClick()
        until { vm.playback.value.selection?.disabledTrackTypes?.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO) == true }
        compose.onNodeWithText("Quality").assertIsNotEnabled()
        compose.onNodeWithText("Audio only").performClick()
        until { vm.playback.value.selection?.disabledTrackTypes?.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO) == false }
        closeMenu()
    }

    @Test fun vrMenuUsesMetadataWhenAutomaticViewingIsOffOrRenderingFails() {
        ui { vm.preferences.value = vm.preferences.value.copy(vrMode = false) }
        open()
        assertEquals(Projection.FLAT, vm.playback.value.projection)
        menu("Viewing mode")
        compose.onNodeWithText("360° equirectangular", useUnmergedTree = true).performClick()
        until { vm.playback.value.projection == Projection.EQUIRECTANGULAR }
        closeMenu()
        ui { vm.viewing.value = vm.viewing.value.copy(error = "Projection failed") }
        menu("Viewing mode")
        compose.onNodeWithText("Projection failed").assertExists()
        closeMenu()
    }

    @Test fun equirectangularDecodedDirectionsDragRecenterAndCaptions() {
        open(); assertEquals(Projection.EQUIRECTANGULAR, vm.playback.value.projection); front()
        ui { vm.panViewing(90f, 0f) }; right()
        ui { vm.recenterViewing(); vm.panViewing(0f, 80f) }; up()
        ui { vm.recenterViewing(); vm.panViewing(0f, -80f) }; down()
        ui { vm.recenterViewing() }; front()
        compose.onNodeWithTag("player-gestures").performTouchInput { swipe(Offset(width * .8f, height * .5f), Offset(width * .3f, height * .5f), 500) }
        until { vm.viewing.value.yaw > 60 }; assertTrue(vm.playback.value.position < 3000)
        menu("Viewing mode"); compose.onNodeWithTag("viewing-recenter").performClick()
        assertEquals(0f, vm.viewing.value.yaw); closeMenu(); front()
        ui { vm.captions("en"); vm.seekTo(1500) }
        until { var captions = ""; ui { captions = vm.controller.value!!.currentCues.cues.joinToString { it.text.toString() } }; captions.contains("360 CAPTION") }
        captureWatchScreenshot("360-captions")
    }
    @Test fun eacDecodedDirectionsMatchIndependentFfmpegConversion() {
        open("cubemap0001"); assertEquals(Projection.EAC, vm.playback.value.projection); front()
        ui { vm.panViewing(90f, 0f) }; right()
        ui { vm.recenterViewing(); vm.panViewing(0f, 80f) }; up()
        ui { vm.recenterViewing(); vm.panViewing(0f, -80f) }; down()
    }
    @Test fun viewingSurvivesFullscreenRecreationAndResetsForNextOccurrence() {
        open(); ui { vm.panViewing(90f, 0f) }; right(); recreate(); right()
        controls(); compose.onNodeWithContentDescription("Full screen").performClick()
        until { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }; right()
        compose.onNodeWithContentDescription("Exit full screen").performClick(); ready("spherevid01")
        menu("Viewing mode"); compose.onNodeWithText("Flat", useUnmergedTree = true).performClick()
        until { vm.playback.value.projection == Projection.FLAT }; closeMenu()
        menu("Viewing mode"); compose.onNodeWithText("Automatic", useUnmergedTree = true).performClick(); closeMenu()
        until { vm.playback.value.projection == Projection.EQUIRECTANGULAR }; right()
        open("cubemap0001"); assertEquals(ViewingMode.AUTO, vm.viewing.value.mode); assertEquals(0f, vm.viewing.value.yaw); front()
    }
    @Test fun annotationsRenderDismissSeekSuspendAndRestoreWithoutAffectingPlayback() {
        open(); menu("Annotations"); compose.onNodeWithTag("annotations-toggle").performClick(); closeMenu()
        until { vm.annotations.value.loaded }; compose.onNodeWithText("Archived popup").assertExists()
        assertEquals(5, vm.annotations.value.active(1000).size); captureWatchScreenshot("annotations-styles")
        compose.onNodeWithContentDescription("Dismiss annotation Archived popup").performClick()
        compose.onNodeWithText("Archived popup").assertDoesNotExist()
        val position = vm.playback.value.position
        recreate(); assertEquals(position, vm.playback.value.position); compose.onNodeWithText("Archived popup").assertDoesNotExist()
        ui { vm.audioOnly(true) }; until { !vm.annotations.value.visible }
        ui { vm.audioOnly(false) }; until { vm.annotations.value.visible }
        controls(); compose.onNodeWithContentDescription("Minimize player").performClick()
        until { !vm.annotations.value.visible }; compose.onNodeWithTag("annotation-overlays").assertDoesNotExist()
        compose.onNodeWithTag("mini-player-preview").performClick(); until { vm.annotations.value.visible }
        compose.onNodeWithText("Archived speech").performClick()
        until { vm.playback.value.position >= 8900 }; compose.onNodeWithTag("annotation-overlays").assertDoesNotExist()
        ui { vm.seekTo(1000) }; until { vm.annotations.value.active(1000).size == 4 }
        assertFalse(fixture().getJSONArray("annotationRequests").getJSONObject(0).getBoolean("authorized"))
    }
    @Test fun unavailableEmptyAndDelayedArchivesStayIsolatedAndRetryable() {
        command("player-tools", """{"annotationStatus":503}""")
        open(); ui { vm.setAnnotations(true) }; until { vm.annotations.value.error != null }
        command("player-tools", """{"annotationStatus":404}""")
        menu("Annotations"); compose.onNodeWithText("Retry annotations").performClick()
        until { vm.annotations.value.loaded }; compose.onNodeWithText("No archived annotations found.").assertExists(); closeMenu()
        command("player-tools", """{"annotationStatus":200,"annotationDelayNext":1500}""")
        ui { vm.retryAnnotations() }; until { vm.annotations.value.loading }
        open("cubemap0001"); until { vm.annotations.value.binding?.videoId == "cubemap0001" }
        assertFalse(vm.annotations.value.enabled); assertTrue(vm.annotations.value.items.isEmpty())
        ui { vm.setAnnotations(true) }; until { vm.annotations.value.loaded }
        assertTrue(vm.annotations.value.items.all { it.url?.contains("cubemap0001") == true })
    }
    @Test fun annotationAndVrDefaultsPersistForGuestsAndSyncAsSparseAccountChanges() {
        fun settings() {
            compose.selectFeed("You"); compose.openAppSettings(); compose.onNodeWithText("Playback").performClick()
            for (label in listOf("Legacy annotations", "Annotations on subscriptions", "Automatic 360° viewing")) {
                compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label)); compose.onNodeWithText(label).performClick()
            }
            until { vm.preferenceSaves.edits.value.isEmpty() }
            compose.onNodeWithContentDescription("Back from Playback").performClick()
            compose.onNodeWithContentDescription("Back from Settings").performClick()
        }
        settings(); assertTrue(vm.store.guestPreferences().annotations); assertTrue(vm.store.guestPreferences().annotationsSubscribed)
        assertFalse(vm.store.guestPreferences().vrMode); recreate()
        ui { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        until { vm.account.value != null && vm.preferences.value.vrMode && !vm.preferences.value.annotations }
        settings()
        val prefs = fixture().getJSONObject("preferences")
        assertTrue(prefs.getBoolean("annotations")); assertTrue(prefs.getBoolean("annotations_subscribed")); assertFalse(prefs.getBoolean("vr_mode"))
        assertEquals("preserved", prefs.getString("unrelated_setting"))
        ui { vm.openLink(VideoLink("spherevid01", playback = LinkPlayback(options = PlaybackLinkOptions(annotations = false, vrMode = true)))) }
        ready("spherevid01")
        compose.onNodeWithTag("mini-player-preview").performClick()
        until { vm.playback.value.projection == Projection.EQUIRECTANGULAR }; assertFalse(vm.annotations.value.enabled)
    }

    @Test fun clipAnnotationTimingAndActionsUseAbsoluteSourceTime() {
        open("testvideo01")
        val clip = Clip("IVCL" + "9".repeat(32), "Annotation clip", 2000, 10_000, vm.playback.value.details!!.video, vm.store.server)
        ui { vm.watchClip(clip) }; until { vm.playback.value.clip != null && vm.playback.value.duration == 8000L && !vm.playback.value.loading && vm.playback.value.playerState == Player.STATE_READY }
        ui { vm.controller.value!!.pause(); vm.seekTo(1000); vm.setAnnotations(true) }
        until { vm.annotations.value.loaded }; compose.onNodeWithText("Archived speech").assertExists()
        compose.onNodeWithText("Archived speech").performClick()
        until { vm.playback.value.position >= 6900 }; compose.onNodeWithTag("annotation-overlays").assertDoesNotExist()
        ui { vm.seekTo(1000) }; compose.onNodeWithText("Archived speech").assertExists()
    }

    @Test fun miniPipBackgroundAndOfflineKeepProjectionAndSuspendAnnotations() {
        open(); ui { vm.panViewing(90f, 0f); vm.setAnnotations(true); vm.store.pip = true }; until { vm.annotations.value.loaded }
        controls(); compose.onNodeWithContentDescription("Minimize player").performClick()
        until { !vm.annotations.value.visible }; right()
        compose.onNodeWithTag("mini-player-preview").performClick(); until { vm.annotations.value.visible }
        ui { activity.enterPip() }; until { activity.isInPictureInPictureMode }; until { !vm.annotations.value.visible }
        Thread.sleep(1200); captureWatchScreenshot("360-pip")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -n ${instrumentation.targetContext.packageName}/net.wingress.mobivious.MainActivity --activity-clear-top")).use { it.readBytes() }
        until { !activity.isInPictureInPictureMode }; ready("spherevid01"); until { vm.annotations.value.visible }
        ui { vm.setAnnotations(false); vm.store.pip = false; vm.controller.value!!.play() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")).use { it.readBytes() }
        Thread.sleep(1000); assertFalse(vm.annotations.value.visible)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -n ${instrumentation.targetContext.packageName}/net.wingress.mobivious.MainActivity --activity-clear-top")).use { it.readBytes() }
        ready("spherevid01"); ui { vm.controller.value!!.pause() }; assertEquals(90f, vm.viewing.value.yaw)
        val id = runBlocking { app.downloads.create(app.store.server, app.api.downloads("spherevid01"), DownloadSelection("v360", null)) }
        downloads += id
        until { app.downloads.find(id)?.assets?.all { it.status == DownloadStatus.COMPLETE } == true }
        val before = fixture().getJSONArray("annotationRequests").length()
        ui { vm.playDownload(id) }; ready("spherevid01"); ui { vm.setAnnotations(true) }
        assertEquals(Projection.EQUIRECTANGULAR, vm.playback.value.projection); front()
        assertEquals(before, fixture().getJSONArray("annotationRequests").length())
        menu("Viewing mode"); compose.onNodeWithText("Flat", useUnmergedTree = true).performClick(); closeMenu()
        menu("Viewing mode"); compose.onNodeWithText("Automatic", useUnmergedTree = true).performClick(); closeMenu()
        until { vm.playback.value.projection == Projection.EQUIRECTANGULAR }
    }
}
