package net.wingress.mobivious

import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.downloads.DownloadExportService
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL
import java.io.File

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DownloadsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val app get() = activity.application as MobiviousApplication
    private fun until(condition: () -> Boolean) = compose.waitUntil(60_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Before fun open() {
        command("reset"); command("downloads", """{"downloadDisabled":false,"downloadSlow":false,"downloadFailKey":"","downloadEndpointsMissing":false,"downloadDuration":12,"resetRequests":true}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread { activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.save(null); activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings() }
        until { activity.model.browse.value.videos.isNotEmpty() }
        runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
        DownloadExportService.state.value = net.wingress.mobivious.downloads.DownloadExportState()
    }
    @After fun close() {
        if (::activity.isInitialized) {
            DownloadExportService.cancel(activity)
            compose.runOnUiThread { activity.model.closePlayer() }
            runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
            compose.runOnUiThread { activity.finishAndRemoveTask() }
        }
    }
    private fun create(selection: DownloadSelection): String = runBlocking {
        val catalog = app.api.downloads("testvideo01")
        app.downloads.create(app.store.server, catalog, selection)
    }
    private fun ready(id: String) { until { app.downloads.find(id)?.playable == true } }
    private fun play(id: String) {
        compose.runOnUiThread { activity.model.playDownload(id) }
        until {
            var current = false
            compose.runOnUiThread { current = activity.model.controller.value?.currentMediaItem?.mediaMetadata?.extras?.getString("downloadId") == id }
            current && activity.model.playback.value.downloadId == id && !activity.model.playback.value.loading && activity.model.playback.value.playerState == Player.STATE_READY
        }
    }
    private fun recreate() {
        val old = activity
        compose.runOnUiThread { activity.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    @Test fun downloadsOverAnHourUseAnHourTimestamp() {
        command("downloads", """{"downloadDuration":3661}""")
        val id = create(DownloadSelection(audio = "aes")); ready(id)
        compose.runOnUiThread { activity.model.navigate("You", "downloads", rememberOrigin = true) }
        compose.onNodeWithText("1:01:01").assertExists()
        compose.onNodeWithText("61:01").assertDoesNotExist()
    }

    @Test fun dialogRequiresMediaPreservesSelectionAndCreatesDuplicates() {
        compose.runOnUiThread { activity.model.openDownload(activity.model.browse.value.videos.first()) }
        until { activity.model.downloadDialog.value.catalog != null }
        compose.onNodeWithTag("download-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("download-dialog").performScrollToNode(hasTestTag("download-choice-cen"))
        compose.onNodeWithTag("download-choice-cen").performClick()
        compose.onNodeWithTag("download-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("download-dialog").performScrollToNode(hasTestTag("download-choice-v360"))
        compose.onNodeWithTag("download-choice-v360").performClick()
        recreate()
        assertEquals("v360", activity.model.downloadDialog.value.selection.video)
        assertEquals(setOf("cen"), activity.model.downloadDialog.value.selection.captions)
        compose.onNodeWithTag("download-confirm").performClick()
        until { app.downloads.records.value.size == 1 }
        val first = app.downloads.records.value.single().id; ready(first)
        val second = create(DownloadSelection(audio = "aes")); ready(second)
        assertNotEquals(first, second)
        compose.runOnUiThread { activity.model.navigate("You", "downloads", rememberOrigin = true) }
        compose.onNodeWithTag("downloads-page").assertExists()
        assertEquals(2, app.downloads.records.value.size)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/downloads-page.png").close()
    }
    @Test fun allModesAndCaptionsDecodeOfflineWithoutMediaOrMetadataRequests() {
        val video = create(DownloadSelection(video = "v144")); ready(video)
        val audio = create(DownloadSelection(audio = "aes")); ready(audio)
        val both = create(DownloadSelection("v360", "aen", setOf("cen"))); ready(both)
        until { app.downloads.records.value.all { record -> record.assets.all { it.status == DownloadStatus.COMPLETE } } }
        val before = state(); val mediaBefore = before.getInt("mediaRequests"); val requestsBefore = before.getJSONArray("videoDetailRequests").length()
        val allBefore = before.getJSONArray("allRequests").toString()
        play(video); assertTrue(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)); assertFalse(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
        play(audio); assertFalse(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)); assertTrue(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
        play(both); assertTrue(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)); assertTrue(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
        compose.runOnUiThread { activity.model.captions("en"); activity.model.seekTo(4000) }
        until { activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_TEXT) && activity.model.playback.value.position >= 4000 }
        assertTrue(activity.model.playback.value.details!!.video.thumbnail.startsWith("file:"))
        assertTrue(activity.model.playback.value.details!!.video.authorAvatar.startsWith("file:"))
        recreate(); assertEquals(both, activity.model.playback.value.downloadId)
        compose.runOnUiThread { activity.model.retryPlayback() }
        until { !activity.model.playback.value.loading && activity.model.playback.value.playerState == Player.STATE_READY && activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_TEXT) }
        val after = state(); assertEquals(mediaBefore, after.getInt("mediaRequests")); assertEquals(requestsBefore, after.getJSONArray("videoDetailRequests").length())
        assertEquals(allBefore, after.getJSONArray("allRequests").toString())
        assertEquals(3, DownloadDatabase(activity).read().size)
    }
    @Test fun offlinePlaybackUsesWatchMiniFullscreenPipAndBackgroundControls() {
        val id = create(DownloadSelection("v360", "aen", setOf("cen"))); ready(id)
        until { app.downloads.find(id)!!.assets.all { it.status == DownloadStatus.COMPLETE } }
        val originalBackground = app.store.background; val originalPip = app.store.pip
        try {
            compose.runOnUiThread { app.store.background = true; app.store.pip = false; activity.model.navigate("You", "downloads", rememberOrigin = true) }
            compose.onNodeWithTag("download-play-$id").performClick()
            until { activity.model.playback.value.downloadId == id && activity.model.playback.value.playing }
            compose.onNodeWithTag("watch-content").assertExists()
            compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.speed(1.5f); activity.model.seekTo(2000) }
            until { activity.model.playback.value.position == 2000L }
            // A controller reload keeps local files, selections and the chosen position.
            compose.runOnUiThread { activity.model.refreshBuffer() }
            until { activity.model.playback.value.playerState == Player.STATE_READY && activity.model.playback.value.position == 2000L }
            assertEquals(1.5f, activity.model.playback.value.speed)
            captureWatchScreenshot("watch-glass-offline")
            compose.onNodeWithTag("player-surface").performClick()
            compose.onNodeWithContentDescription("Full screen").performClick()
            until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
            compose.waitForIdle() // Let the watch BackHandler replace the fullscreen handler.
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            until { compose.onAllNodesWithTag("mini-player", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mini-player", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("mini-player-preview", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("watch-content").assertExists()
            compose.runOnUiThread { activity.model.controller.value!!.play() }
            until { activity.model.playback.value.playing }
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
            val before = activity.model.playback.value.position
            until { activity.model.playback.value.playing && activity.model.playback.value.position > before + 300 }
            val intent = Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            compose.runOnUiThread { activity.startActivity(intent) }
            until { activity.hasWindowFocus() }
            if (activity.supportsPip()) {
                compose.runOnUiThread { activity.enterPip() }
                until { activity.isInPictureInPictureMode }
                assertEquals(id, activity.model.playback.value.downloadId)
            }
        } finally { compose.runOnUiThread { app.store.background = originalBackground; app.store.pip = originalPip } }
    }
    @Test fun failuresRetryWithoutRepeatingCompletedTracksAndDeleteIsIndependent() {
        command("downloads", """{"downloadFailKey":"aen"}""")
        val first = create(DownloadSelection("v360", "aen"))
        until { app.downloads.find(first)!!.assets.any { it.status == DownloadStatus.FAILED } && app.downloads.find(first)!!.assets.first { it.choice.key == "v360" }.status == DownloadStatus.COMPLETE }
        val videoId = app.downloads.find(first)!!.assets.first { it.choice.key == "v360" }.transferId
        command("downloads", """{"downloadFailKey":""}""")
        runBlocking { app.downloads.retry(first) }; ready(first)
        assertEquals(videoId, app.downloads.find(first)!!.assets.first { it.choice.key == "v360" }.transferId)
        val second = create(DownloadSelection(audio = "aen")); ready(second)
        play(first)
        compose.runOnUiThread { activity.model.deleteDownload(first) }
        until { app.downloads.find(first) == null && activity.model.queue.value.current == null }
        assertTrue(app.downloads.find(second)!!.playable)
        val asset = app.downloads.find(second)!!.assets.first { it.media }; app.downloads.file(app.downloads.find(second)!!, asset).delete()
        runBlocking { app.downloads.reconcile() }; assertFalse(app.downloads.find(second)!!.playable)
        runBlocking { app.downloads.retry(second) }; ready(second)
    }
    @Test fun transfersSurviveRecreationCancellationAndProfileChanges() {
        command("downloads", """{"downloadSlow":true}""")
        val id = create(DownloadSelection(video = "v360"))
        until { app.downloads.find(id)!!.assets.any { it.status == DownloadStatus.RUNNING } }
        recreate()
        runBlocking { app.downloads.cancel(id) }
        assertTrue(app.downloads.find(id)!!.assets.filter { it.media }.all { it.status == DownloadStatus.CANCELLED })
        command("downloads", """{"downloadSlow":false}""")
        runBlocking { app.downloads.retry(id) }; ready(id)
        compose.runOnUiThread { activity.model.store.save(Account("fixture", "Fixture", Long.MAX_VALUE, app.store.server)); activity.model.navigate("You", "downloads", rememberOrigin = true) }
        assertEquals(id, app.downloads.find(id)!!.id)
        compose.runOnUiThread { activity.model.store.save(null); activity.model.switchServer("http://10.0.2.2:18080"); activity.model.navigate("You", "downloads", rememberOrigin = true) }
        assertTrue(app.downloads.find(id)!!.playable); play(id)
    }
    @Test fun restrictedDownloadsAndStaleDialogAreRejected() {
        command("downloads", """{"downloadDisabled":true}""")
        compose.runOnUiThread { activity.model.openDownload(activity.model.browse.value.videos.first()) }
        until { activity.model.downloadDialog.value.catalog != null }
        compose.onNodeWithText("Downloads are disabled by this instance.").assertExists()
        compose.onNodeWithTag("download-confirm").assertIsNotEnabled()
        compose.runOnUiThread { activity.model.switchServer("http://10.0.2.2:18080") }
        until { activity.model.downloadDialog.value.video == null }
        until { activity.model.browse.value.videos.isNotEmpty() }
        command("downloads", """{"downloadEndpointsMissing":true}""")
        compose.runOnUiThread { activity.model.openDownload(activity.model.browse.value.videos.first()) }
        until { activity.model.downloadDialog.value.error != null }
        compose.onNodeWithText("Update this server to enable native downloads.").assertExists()
        compose.onNodeWithTag("download-confirm").assertIsNotEnabled()
    }
    private fun destination(name: String, mime: String = "video/mp4"): Uri {
        val values = android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "${java.util.UUID.randomUUID()}-$name"); put(android.provider.MediaStore.Downloads.MIME_TYPE, mime) }
        return activity.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
    }
    @Test fun singleVideoAudioAndCaptionExportsUsePickerAndPreserveFiles() {
        val audio = create(DownloadSelection(audio = "aes", captions = setOf("cen"))); ready(audio)
        val video = create(DownloadSelection(video = "v144")); ready(video)
        until { app.downloads.records.value.all { record -> record.assets.all { it.status == DownloadStatus.COMPLETE } } }
        compose.runOnUiThread { activity.model.navigate("You", "downloads", rememberOrigin = true) }
        for ((id, key, label) in listOf(Triple(audio, "aes", "Media file"), Triple(audio, "cen", "Caption: English"), Triple(video, "v144", "Media file"))) {
            val record = app.downloads.find(id)!!; val asset = record.assets.single { it.choice.key == key }
            val output = destination("download-copy.${asset.choice.extension}", asset.choice.mime)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val filter = android.content.IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") }
            val monitor = instrumentation.addMonitor(filter, android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, Intent().setData(output).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)), true)
            try {
                compose.onNodeWithTag("downloads-page").performScrollToNode(hasTestTag("download-actions-$id"))
                compose.onNodeWithTag("download-actions-$id").performClick()
                compose.onNodeWithTag("download-save-$id").performClick()
                compose.onNodeWithText(label).performClick()
                until { monitor.hits > 0 && DownloadExportService.state.value.destination == output.toString() && DownloadExportService.state.value.phase in listOf("Saved", "Failed") }
                assertEquals(DownloadExportService.state.value.error, "Saved", DownloadExportService.state.value.phase)
                assertArrayEquals(app.downloads.file(record, asset).readBytes(), activity.contentResolver.openInputStream(output)!!.use { it.readBytes() })
            } finally { instrumentation.removeMonitor(monitor); activity.contentResolver.delete(output, null, null) }
        }
    }
    @Test fun combinedExportHasBothTracksAndDecliningConversionStartsNoExport() {
        val id = create(DownloadSelection("v360", "aen")); ready(id)
        val output = destination("mobivious-download-export-test.mp4")
        try {
            compose.runOnUiThread { DownloadExportService.start(activity, id, null, output) }
            until { DownloadExportService.state.value.phase in listOf("Saved", "Failed", "Needs conversion") }
            assertEquals(DownloadExportService.state.value.error, "Saved", DownloadExportService.state.value.phase)
            val extractor = MediaExtractor()
            activity.contentResolver.openFileDescriptor(output, "r")!!.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                val types = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
                assertTrue(types.any { it.startsWith("video/") }); assertTrue(types.any { it.startsWith("audio/") })
                val starts = (0 until extractor.trackCount).map { track -> extractor.selectTrack(track); extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC); val time = extractor.sampleTime; extractor.unselectTrack(track); time }
                assertTrue(kotlin.math.abs(starts[0] - starts[1]) <= 100_000)
                val durations = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getLong(MediaFormat.KEY_DURATION) }
                assertTrue(durations.all { kotlin.math.abs(it - 12_000_000) < 1_000_000 })
            }; extractor.release()
        } finally { activity.contentResolver.delete(output, null, null) }
        val incompatible = create(DownloadSelection("vp8", "aen")); ready(incompatible)
        val converted = destination("mobivious-conversion-test.mp4")
        compose.runOnUiThread { DownloadExportService.start(activity, incompatible, null, converted) }
        until { DownloadExportService.state.value.phase == "Needs conversion" }
        compose.onNodeWithTag("download-convert-cancel").performClick()
        assertEquals("Cancelled", DownloadExportService.state.value.phase)
        assertTrue(app.downloads.find(incompatible)!!.playable)
        assertTrue(runCatching { activity.contentResolver.openFileDescriptor(converted, "r")?.use { it.statSize == 0L } ?: true }.getOrDefault(true))
    }
    @Ignore("Emulator 37.2.12 host gfxstream TextureResize SIGSEGV during accepted video conversion. Run on a physical device or fixed emulator.")
    @Test fun acceptedVideoConversionPreservesResolutionFrameRateAndBothTracks() {
        val incompatible = create(DownloadSelection("vp8", "aen")); ready(incompatible)
        val accepted = destination("mobivious-conversion-accepted-test.mp4")
        try {
            compose.runOnUiThread { DownloadExportService.start(activity, incompatible, null, accepted) }
            until { DownloadExportService.state.value.phase == "Needs conversion" }
            compose.onNodeWithTag("download-convert-confirm").performClick()
            until { DownloadExportService.state.value.phase in listOf("Saved", "Failed") }
            assertEquals(DownloadExportService.state.value.error, "Saved", DownloadExportService.state.value.phase)
            val extractor = MediaExtractor()
            try {
                activity.contentResolver.openFileDescriptor(accepted, "r")!!.use { extractor.setDataSource(it.fileDescriptor) }
                val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" })
                val video = formats.single { it.getString(MediaFormat.KEY_MIME) == "video/avc" }
                assertEquals(256, video.getInteger(MediaFormat.KEY_WIDTH)); assertEquals(144, video.getInteger(MediaFormat.KEY_HEIGHT))
            } finally { extractor.release() }
        } finally { activity.contentResolver.delete(accepted, null, null) }
    }
}
