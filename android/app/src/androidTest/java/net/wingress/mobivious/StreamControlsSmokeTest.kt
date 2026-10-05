package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.player.AudioSection
import net.wingress.mobivious.player.StreamCatalog
import net.wingress.mobivious.player.PlaybackService
import net.wingress.mobivious.player.VideoSelectionMode
import net.wingress.mobivious.data.Video
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Requires the generated rich DASH fixture, including real video/audio bitrate alternatives. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamControlsSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun open() {
        command("reset"); command("stream", """{"type":"rich"}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.save(null)
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings()
        }
        until { activity.model.browse.value.videos.isNotEmpty() }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(40_000) }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread { activity.model.closePlayer(); activity.model.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask() }
    }
    private fun showControls() {
        if (compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
        until { compose.onAllNodesWithContentDescription("Player settings").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tap(forward: Boolean, double: Boolean = false) {
        compose.onNodeWithTag("player-surface").performTouchInput {
            val point = Offset(width * if (forward) .85f else .15f, height * .25f)
            if (double) doubleClick(point) else click(point)
        }
    }
    private fun selected(type: Int) = StreamCatalog.choices(activity.model.playback.value.tracks, type, activity.model.playback.value.details?.formats.orEmpty())
        .singleOrNull { it.explicitlySelected(activity.model.playback.value.selection) }
    private fun recreate() {
        val old = activity
        compose.runOnUiThread { activity.recreate() }
        until {
            var ready = false
            compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }
            ready
        }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    private fun screenshot(name: String) {
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /data/local/tmp/mobivious-stream-screenshots").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
        shell.executeShellCommand("screencap -p /data/local/tmp/mobivious-stream-screenshots/$name.png").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
    }
    @Test fun exactTracksMenusRefreshRetryAndReconnection() {
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Quality").performClick()
        screenshot("quality-portrait")
        compose.onNode(hasText("720p24 · H.264") and hasText("1.4 Mbps")).performScrollTo().performClick()
        until { selected(C.TRACK_TYPE_VIDEO)?.height == 720 }
        val videoKey = selected(C.TRACK_TYPE_VIDEO)!!.key
        assertEquals("auto", activity.model.store.guestDeArrow().qualityDash)
        compose.onNodeWithText("Audio").performClick()
        screenshot("audio-portrait")
        compose.onNodeWithText("Stable Volume").assertExists()
        compose.onNodeWithText("Dubbed Audio").assertExists()
        compose.onNodeWithText("English Stable Volume").performScrollTo().performClick()
        until { selected(C.TRACK_TYPE_AUDIO)?.section == AudioSection.STABLE }
        val audioKey = selected(C.TRACK_TYPE_AUDIO)!!.key
        compose.onNodeWithText("Playback speed").performClick(); compose.onNodeWithText("0.25×").performClick()
        compose.onNodeWithText("Captions").performClick(); compose.onNodeWithText("English").performClick()
        compose.onNodeWithContentDescription("Close player settings").performClick()
        compose.runOnUiThread { activity.model.refreshBuffer() }
        until { activity.model.playback.value.playerState == Player.STATE_READY }
        assertEquals(videoKey, selected(C.TRACK_TYPE_VIDEO)?.key); assertEquals(audioKey, selected(C.TRACK_TYPE_AUDIO)?.key)
        compose.runOnUiThread { activity.model.retryPlayback() }
        until { !activity.model.playback.value.loading && selected(C.TRACK_TYPE_VIDEO)?.key == videoKey }
        assertEquals(audioKey, selected(C.TRACK_TYPE_AUDIO)?.key)
        assertEquals(.25f, activity.model.playback.value.speed)
        assertFalse(activity.model.playback.value.playWhenReady)
        assertTrue(activity.model.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_TEXT))
        recreate()
        assertEquals(videoKey, selected(C.TRACK_TYPE_VIDEO)?.key)
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Quality").performClick(); compose.onNode(hasText("720p24 · H.264") and hasText("1.4 Mbps")).assertIsSelected()
        compose.onNodeWithText("Auto").performClick(); until { selected(C.TRACK_TYPE_VIDEO) == null }
    }
    @Test fun savedQualityDefaultsSelectActualSupportedTracks() {
        for (preference in listOf("best", "worst", "360p", "4320p", "auto")) {
            compose.runOnUiThread { activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, qualityDash = preference)); activity.model.play("testvideo01", 40) }
            until { !activity.model.playback.value.loading && activity.model.playback.value.playerState == Player.STATE_READY &&
                (preference == "auto" || selected(C.TRACK_TYPE_VIDEO) != null) }
            val choices = StreamCatalog.choices(activity.model.playback.value.tracks, C.TRACK_TYPE_VIDEO)
            assertEquals(StreamCatalog.defaultVideo(choices, preference)?.key, selected(C.TRACK_TYPE_VIDEO)?.key)
        }
    }
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun codecStart(codec: String, quality: String = "auto", source: String = "codec") {
        compose.runOnUiThread { activity.model.closePlayer() }
        until { activity.model.queue.value.token.isEmpty() }
        command("stream", """{"type":"$source"}"""); command("media-reset")
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, videoCodec = codec, qualityDash = quality))
            activity.model.play("testvideo01", 0)
        }
        until { !activity.model.playback.value.loading && activity.model.playback.value.playerState == Player.STATE_READY &&
            activity.model.playback.value.tracks.groups.isNotEmpty() && (quality == "auto" || selected(C.TRACK_TYPE_VIDEO) != null) }
    }
    private fun firstCodecVideo(): String {
        val pattern = Regex("/media/codec/chunk-stream([0-6])-")
        until { state().getJSONArray("mediaPaths").let { paths -> (0 until paths.length()).any { pattern.containsMatchIn(paths.getString(it)) } } }
        val paths = state().getJSONArray("mediaPaths")
        return (0 until paths.length()).firstNotNullOf { pattern.find(paths.getString(it))?.groupValues?.get(1) }
    }
    @Test fun codecPreferencesReachTheFirstDashSegmentAndFallbackWithinTheResolution() {
        for ((codec, quality, source) in listOf(Triple("av1", "auto", "codec"), Triple("h264", "auto", "codec"),
            Triple("av1", "720p", "codec"), Triple("h264", "best", "codec"), Triple("av1", "worst", "codec"),
            Triple("av1", "360p", "codec-missing"), Triple("av1", "auto", "codec-unsupported"))) {
            codecStart(codec, quality, source)
            val choices = StreamCatalog.choices(activity.model.playback.value.tracks, C.TRACK_TYPE_VIDEO)
            val expected = StreamCatalog.defaultVideo(choices, quality, codec)
            val initial = firstCodecVideo()
            if (expected != null) assertEquals(expected.key.id, initial)
            else assertTrue(StreamCatalog.automaticVideo(choices, codec).any { it.key.id == initial })
            if (source == "codec-missing") assertEquals("0", initial)
            if (source == "codec-unsupported") assertTrue(choices.none { it.codec == "AV1" })
            assertNull(activity.model.playback.value.error)
        }
    }
    @Test fun codecAutoAndManualChoicesSurviveRefreshRetryAndReconnection() {
        codecStart("av1")
        val choices = StreamCatalog.choices(activity.model.playback.value.tracks, C.TRACK_TYPE_VIDEO)
        val manual = choices.first { it.codec == "H.264" && it.height == 720 }
        compose.runOnUiThread { activity.model.selectTrack(manual.group, manual.index) }
        until { selected(C.TRACK_TYPE_VIDEO)?.key == manual.key }
        compose.runOnUiThread { activity.model.seekTo(3000); activity.model.speed(1.5f); activity.model.refreshBuffer() }
        until { activity.model.playback.value.playerState == Player.STATE_READY && selected(C.TRACK_TYPE_VIDEO)?.key == manual.key }
        compose.runOnUiThread { activity.model.retryPlayback() }
        until { !activity.model.playback.value.loading && selected(C.TRACK_TYPE_VIDEO)?.key == manual.key }
        assertFalse(activity.model.playback.value.playWhenReady); assertEquals(1.5f, activity.model.playback.value.speed)
        recreate(); until { selected(C.TRACK_TYPE_VIDEO)?.key == manual.key }
        // A preference refresh updates future defaults while the open occurrence
        // retains its captured AV1 policy, including an explicit return to Auto.
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(videoCodec = "h264")); activity.model.refreshSharedSettings()
            activity.model.refreshBuffer(); activity.model.autoQuality()
        }
        until { activity.model.playback.value.playerState == Player.STATE_READY && activity.model.playback.value.videoSelection.mode == VideoSelectionMode.AUTO && selected(C.TRACK_TYPE_VIDEO) == null }
        assertEquals("av1", activity.model.playback.value.videoSelection.codec)
        command("media-reset")
        compose.runOnUiThread { activity.model.refreshBuffer() }
        until { activity.model.playback.value.playerState == Player.STATE_READY }
        val initial = firstCodecVideo()
        val supported = StreamCatalog.choices(activity.model.playback.value.tracks, C.TRACK_TYPE_VIDEO)
        assertTrue(StreamCatalog.automaticVideo(supported, "av1").any { it.key.id == initial })
    }
    @Test fun nextQueueOccurrenceUsesSavedDefaultsAndMenusRetainAtMostFourPerGroup() {
        codecStart("h264")
        val choices = StreamCatalog.choices(activity.model.playback.value.tracks, C.TRACK_TYPE_VIDEO)
        val manual = choices.firstOrNull { it.key.id == "5" } ?: choices.first { it.height == 720 }
        compose.runOnUiThread { activity.model.selectTrack(manual.group, manual.index) }
        until { selected(C.TRACK_TYPE_VIDEO)?.key == manual.key }
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNode(hasText("Quality") and hasText(StreamCatalog.qualityText(manual))).assertExists()
        compose.onNodeWithText("Quality").performClick(); screenshot("codec-quality-portrait")
        val menu = StreamCatalog.qualityMenu(choices)
        assertTrue(menu.groupBy { it.height to kotlin.math.round(it.fps) }.all { it.value.size <= 4 })
        if (menu.none { it.key == manual.key }) compose.onAllNodes(isSelected() and hasText(manual.primary)).assertCountEquals(0)
        // Check visible choice nodes against the catalog, including numeric details.
        menu.forEach {
            val predicate = hasText(it.primary) and hasText(it.secondary)
            compose.onNodeWithTag("player-settings-list").performScrollToNode(predicate)
            compose.onNode(predicate).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Close player settings").performClick()
        showControls(); compose.onNodeWithContentDescription("Full screen").performClick()
        until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Quality").performClick(); screenshot("codec-quality-landscape")
        compose.onNodeWithContentDescription("Close player settings").performClick()
        compose.runOnUiThread { activity.model.insertQueue(Video("testvideo02", "Successor"), true) }
        until { activity.model.queue.value.items.size == 2 }
        command("media-reset")
        compose.runOnUiThread { activity.model.controller.value!!.seekToNextMediaItem() }
        until { activity.model.playback.value.mediaId == "testvideo02" && !activity.model.playback.value.loading && activity.model.playback.value.playerState == Player.STATE_READY }
        assertEquals(VideoSelectionMode.AUTO, activity.model.playback.value.videoSelection.mode)
        assertNull(selected(C.TRACK_TYPE_VIDEO)); assertEquals("h264", activity.model.playback.value.videoSelection.codec)
        assertNull(activity.model.playback.value.videoSelection.manual)
        val nextChoices = StreamCatalog.choices(activity.model.playback.value.tracks, C.TRACK_TYPE_VIDEO)
        assertTrue(StreamCatalog.automaticVideo(nextChoices, "h264").any { it.key.id == firstCodecVideo() })
    }
    @Test fun consecutiveSingleTapsAccumulateAndOppositeTapResets() {
        tap(true, true); tap(true); tap(true)
        compose.runOnUiThread { assertEquals(30_000L, activity.model.pendingSeek.value?.offset); assertEquals(40_000L, activity.model.playback.value.position) }
        compose.onNodeWithText("+30 seconds").assertExists()
        tap(false)
        compose.runOnUiThread { assertEquals(-10_000L, activity.model.pendingSeek.value?.offset); assertEquals(40_000L, activity.model.playback.value.position) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 30_000L }
        assertFalse(activity.model.playback.value.playWhenReady)
        tap(false, true); tap(false); tap(true)
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 40_000L }
        compose.runOnUiThread { activity.model.seekTo(115_000) }
        tap(true, true); tap(true)
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position >= 119_000L }
    }
    @Test fun accumulationFreezesPlayingVideoAndCompetingActionsCancel() {
        compose.runOnUiThread { activity.model.controller.value!!.play() }
        until { activity.model.playback.value.playing }
        tap(true, true)
        compose.runOnUiThread { assertNotNull(activity.model.pendingSeek.value); assertFalse(activity.model.playback.value.playWhenReady) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(40_000); activity.model.accumulateSeek(1); activity.model.refreshBuffer() }
        until { activity.model.playback.value.playerState == Player.STATE_READY }
        assertNull(activity.model.pendingSeek.value); assertEquals(40_000L, activity.model.playback.value.position)
        compose.runOnUiThread { activity.model.accumulateSeek(1); activity.model.seekTo(15_000) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 15_000L }
        compose.runOnUiThread { activity.model.accumulateSeek(1); activity.model.controller.value!!.seekTo(25_000) }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 25_000L }
        showControls(); tap(true, true)
        compose.onNodeWithContentDescription("Player settings").performClick()
        assertNull(activity.model.pendingSeek.value)
        compose.onNodeWithContentDescription("Close player settings").performClick()
        compose.runOnUiThread { activity.model.accumulateSeek(1) }
        recreate()
        until { activity.model.pendingSeek.value == null }
        assertEquals(35_000L, activity.model.playback.value.position)
    }
    @Test fun fullscreenKeepsAccumulationAndPipEntryCancelsIt() {
        showControls()
        compose.runOnUiThread { activity.model.accumulateSeek(1) }
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        until { activity.model.pendingSeek.value == null && activity.model.playback.value.position == 50_000L }
        assertFalse(activity.model.playback.value.playWhenReady)
        showControls(); compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("Quality").performClick(); screenshot("quality-landscape")
        compose.onNodeWithContentDescription("Close player settings").performClick()
        if (activity.supportsPip()) {
            compose.runOnUiThread { activity.model.accumulateSeek(1); activity.enterPip() }
            until { activity.isInPictureInPictureMode }
            assertNull(activity.model.pendingSeek.value)
            assertEquals(50_000L, activity.model.playback.value.position)
        }
    }
}
