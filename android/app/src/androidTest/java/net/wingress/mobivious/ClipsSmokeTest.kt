package net.wingress.mobivious

import android.content.Intent
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackService
import net.wingress.mobivious.ui.chapters
import net.wingress.mobivious.ui.AvatarImageLoader
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
class ClipsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val id = "IVCL" + "0".repeat(32)
    private val base = "http://127.0.0.1:18080"
    private val owner = "UC" + "a".repeat(22)
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun until(block: () -> Boolean) {
        try { compose.waitUntil(30_000, block) } catch (e: Throwable) {
            screenshot("failure")
            runCatching { android.util.Log.i("ClipTestTree", compose.onRoot(useUnmergedTree = true).printToString()) }
            throw AssertionError("Clip error=${vm.clipEditor.value.error}; editorOpen=${vm.clipEditor.value.open}; queueError=${vm.queue.value.error}; position=${vm.playback.value.position}; duration=${vm.playback.value.duration}; playing=${vm.playback.value.playWhenReady}; source=${vm.playback.value.mediaId}", e)
        }
    }
    private fun command(name: String, body: String = "{}") {
        (URL("$base/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("$base/test/state?fresh=${System.nanoTime()}").readText())
    private fun incoming(path: String) = ui { activity.startActivity(Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$base$path")) }
    private fun serviceState(): Bundle {
        lateinit var result: ListenableFuture<SessionResult>
        ui { result = vm.controller.value!!.sendCustomCommand(SessionCommand(PlaybackService.QUEUE_STATE, Bundle.EMPTY), Bundle().apply {
            putString("server", vm.store.server); putString("account", vm.account.value?.username); putLong("generation", vm.store.contextGeneration)
        }) }
        return result.get(5, TimeUnit.SECONDS).extras
    }
    private fun ready(clipId: String? = null, title: String? = null) = until {
        var attached = false
        ui { attached = vm.controller.value?.let { p -> p.playbackState == Player.STATE_READY && p.isCurrentMediaItemSeekable &&
            p.mediaMetadata.extras?.getString("occurrence") == vm.queue.value.currentKey &&
            p.mediaMetadata.extras?.getBoolean("clip", false) == (vm.queue.value.current?.clip != null) } == true }
        attached && !vm.queue.value.loading && vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.details != null &&
            (clipId == null || vm.queue.value.current?.clip?.id == clipId) && (title == null || vm.queue.value.current?.clip?.title == title)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(750) // Compose idleness does not include Android window/rotation animations.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/mobivious-clips-screenshots", "screencap -p /data/local/tmp/mobivious-clips-screenshots/$name.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }
    @Before fun launch() {
        command("reset"); command("clips", """{"count":31}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            vm.store.save(null); vm.switchServer(base)
            vm.store.guestDeArrow(AccountPreferences()); vm.refreshSharedSettings()
            vm.store.save(Account("fixture-token", "fixture-user", Long.MAX_VALUE, base))
        }
        until { vm.account.value != null && vm.preferences.value.watchHistory && vm.controller.value != null && vm.queue.value.token.isEmpty() && !vm.browse.value.loading }
        compose.waitForIdle()
    }
    @After fun close() {
        if (::activity.isInitialized) ui { vm.closeClipEditor(); vm.closePlayer(); vm.store.save(null); activity.finishAndRemoveTask() }
    }
    @Test fun everyClipRowTapOpensTheClipWhileOverflowKeepsItsActions() {
        ui { vm.navigate("Library", "clips") }; until { !vm.browse.value.loading && vm.browse.value.clips.isNotEmpty() }
        val clip = vm.browse.value.clips.first()
        compose.onNodeWithTag("clip-actions-${clip.id}").performClick()
        compose.onNodeWithText("Share clip").assertExists(); compose.onNodeWithText("Delete clip").assertExists()
        compose.onNodeWithText("Copy link").performClick()
        assertTrue(vm.queue.value.token.isEmpty())
        for (target in listOf("channel", "title", "metadata", "thumbnail", "padding")) {
            val card = hasTestTag("clip-card-${clip.id}")
            when (target) {
                "channel" -> compose.onNode(hasText("from ${clip.video.author}") and hasAnyAncestor(card), true).performTouchInput { click() }
                "title" -> compose.onNode(hasText(clip.title) and hasAnyAncestor(card), true).performTouchInput { click() }
                "metadata" -> compose.onNode(hasText(clip.creator, substring = true) and hasAnyAncestor(card), true).performTouchInput { click() }
                "thumbnail" -> compose.onNode(card).performTouchInput { click(Offset(70f * activity.resources.displayMetrics.density, height * .5f)) }
                else -> compose.onNode(card).performTouchInput { click(Offset(2f, height * .5f)) }
            }
            ready(clip.id)
            assertEquals("clips", vm.route)
            compose.onNodeWithContentDescription("Close clip").performClick()
            until { vm.queue.value.token.isEmpty() && vm.playback.value.details == null }
            compose.onNodeWithTag("mini-player").assertDoesNotExist()
        }
    }

    @Test fun clipCloseStopsPlaybackWhileBackAndSwipeOnlyMinimize() {
        incoming("/clip/$id"); ready(id)
        ui { vm.controller.value!!.pause(); vm.seekTo(5000); vm.speed(1.5f) }
        until { vm.playback.value.position in 4998L..5002L }
        val token = vm.queue.value.token
        val key = vm.queue.value.currentKey
        compose.onNodeWithContentDescription("Back").performClick()
        until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini-player-preview").performClick()
        val density = activity.resources.displayMetrics.density
        compose.onNodeWithTag("player-gestures").performTouchInput {
            val start = Offset(width * .5f, height * .2f)
            swipe(start, start + Offset(0f, 96f * density), durationMillis = 600)
        }
        until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
        ui {
            assertEquals(token, vm.queue.value.token); assertEquals(key, vm.queue.value.currentKey)
            assertEquals(id, vm.queue.value.current!!.clip!!.id)
            assertEquals(30444L, vm.controller.value!!.duration); assertEquals(1.5f, vm.controller.value!!.playbackParameters.speed)
            assertTrue(vm.controller.value!!.currentPosition in 4998L..5002L); assertFalse(vm.controller.value!!.playWhenReady)
        }
        compose.onNodeWithTag("mini-player-preview").performClick()
        compose.onNodeWithContentDescription("Close clip").performClick()
        until { vm.queue.value.token.isEmpty() && vm.playback.value.details == null && !vm.playback.value.playing }
        compose.onNodeWithTag("mini-player").assertDoesNotExist(); compose.onNodeWithTag("player-surface").assertDoesNotExist()
    }

    @Test fun playerSettingsEndWithRefreshAndKeepClipPlaybackChoices() {
        incoming("/clip/$id"); ready(id)
        ui { vm.controller.value!!.pause(); vm.seekTo(5000); vm.speed(1.5f) }
        until { vm.playback.value.position in 4998L..5002L }
        compose.onNodeWithContentDescription("Player settings").performClick()
        compose.onNodeWithText("SponsorBlock").assertDoesNotExist()
        val labels = listOf("Quality", "Audio", "Captions", "Playback speed", "Audio only", "Picture in picture", "Refresh buffer")
        val positions = labels.map { label -> compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("player-settings-list"))).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Player settings should follow the agreed order", positions.zipWithNext().all { (first, second) -> first < second })
        screenshot("player-settings-ordered")
        compose.onNodeWithText("Refresh buffer").performClick(); ready(id)
        ui {
            assertEquals(30444L, vm.controller.value!!.duration); assertEquals(1.5f, vm.controller.value!!.playbackParameters.speed)
            assertTrue(vm.controller.value!!.currentPosition in 4998L..5002L); assertFalse(vm.controller.value!!.playWhenReady)
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    @Test fun clipChannelUsesDiskCachedAvatarAndKeepsThinModeAndFailureFallback() {
        ui { vm.preferences.value = vm.preferences.value.copy(thinMode = true) }; compose.waitForIdle()
        val loader = AvatarImageLoader.get(activity)
        loader.memoryCache?.clear(); loader.diskCache?.clear()
        val avatar = "$base/ggpht/studio=s176"
        runBlocking { assertTrue(loader.execute(coil.request.ImageRequest.Builder(activity).data(avatar).size(176).build()) is coil.request.SuccessResult) }
        loader.memoryCache?.clear()
        command("avatars", """{"fail":true}""")
        runBlocking {
            val result = loader.execute(coil.request.ImageRequest.Builder(activity).data(avatar).size(96).build())
            assertTrue(result is coil.request.SuccessResult)
            assertEquals(coil.decode.DataSource.DISK, (result as coil.request.SuccessResult).dataSource)
        }
        loader.memoryCache?.clear()
        val metadataReads = fixture().getJSONArray("channelRequests").length()
        incoming("/clip/$id"); ready(id)
        ui { vm.preferences.value = vm.preferences.value.copy(thinMode = false) }
        compose.onNodeWithTag("clip-channel").performScrollTo()
        compose.onNodeWithTag("clip-channel-avatar", true).assertExists()
        screenshot("channel-cached-avatar")
        assertEquals(0, fixture().getJSONArray("avatarRequests").length())
        assertEquals(metadataReads, fixture().getJSONArray("channelRequests").length())
        ui { vm.preferences.value = vm.preferences.value.copy(thinMode = true) }; compose.waitForIdle()
        compose.onNodeWithTag("clip-channel-avatar", true).assertDoesNotExist()
        loader.memoryCache?.clear(); loader.diskCache?.clear()
        ui { vm.preferences.value = vm.preferences.value.copy(thinMode = false) }
        until { fixture().getJSONArray("avatarRequests").length() > 0 }
        compose.onNodeWithTag("clip-channel-avatar", true).assertExists()
        compose.onNodeWithTag("clip-channel").assert(hasText("Mobivious Studio"))
        screenshot("channel-failed-avatar")
        compose.onNodeWithTag("clip-channel").performClick()
        until { vm.channel.value?.id == owner && !vm.browse.value.loading }
    }

    @Test fun libraryAndChannelListsPaginateRestoreAndShowBothThemes() {
        ui { vm.navigate("Library") }; until { !vm.browse.value.loading }
        compose.onNodeWithTag("library-clips").performScrollTo().performClick()
        until { vm.browse.value.clips.size == 30 && !vm.browse.value.loading }
        assertFalse(vm.browse.value.end)
        screenshot("library-light")
        ui { vm.more() }; until { vm.browse.value.clips.size == 31 && !vm.browse.value.loading }
        assertTrue(vm.browse.value.end)
        ui { vm.preferences.value = vm.preferences.value.copy(darkMode = "dark") }
        screenshot("library-dark")
        compose.onNodeWithTag("browse-video-list").performScrollToIndex(12)
        until { vm.browse.value.position.index >= 10 }
        val position = vm.browse.value.position
        incoming("/channel/$owner/clips")
        until { vm.channelTab.value == ChannelTab.CLIPS && !vm.browse.value.loading }
        assertEquals(30, vm.browse.value.clips.size)
        ui { vm.backBrowse() }; until { vm.route == "clips" && vm.browse.value.clips.size == 31 }
        assertEquals(31, vm.browse.value.clips.size)
        until { vm.browse.value.position == position }
        val reads = fixture().getJSONArray("clipRequests")
        assertTrue((0 until reads.length()).any { !reads.getJSONObject(it).getBoolean("authorized") && reads.getJSONObject(it).getString("path").contains("/channels/") })
        val deleted = vm.browse.value.clips.first()
        ui { vm.confirmDeleteClip(deleted) }
        compose.onNodeWithTag("clip-delete-confirm").performClick()
        until { vm.clipDelete.value == null && !vm.browse.value.loading && vm.browse.value.clips.size == 30 }
        assertEquals(2, vm.browse.value.page)
        assertTrue(vm.browse.value.end)
        assertFalse(vm.browse.value.clips.any { it.id == deleted.id })
    }
    @Test fun realDashHlsAndMp4UseIndependentBoundsAndNeverWriteSourceProgress() {
        for (stream in listOf("dash", "hls", "mp4")) {
            command("stream", """{"type":"$stream"}""")
            android.util.Log.i("ClipsTest", "Testing clip stream $stream")
            val path = "testvideo01"
            val source = Video(path, "Source", duration = 120)
            val clip = Clip(id, "Bounded $stream", 12345, 42789, source, base, creator = "fixture-user")
            ui { vm.watchClip(clip) }; ready(id, clip.title)
            ui { vm.controller.value!!.pause(); vm.repeatQueue(QueueRepeat.OFF); vm.seekTo(5000) }
            until { vm.playback.value.position in 4998L..5002L }
            ui { assertEquals(30444L, vm.controller.value!!.duration) }
            ui { assertEquals("Bounded $stream", vm.controller.value!!.mediaMetadata.title.toString()) }
            ui { vm.seekTo(-100) }; until { vm.playback.value.position == 0L }
            ui { vm.seekTo(99_000_000) }; until { vm.playback.value.position in 30442L..30444L }
            assertEquals(path, vm.queue.value.current!!.video.id)
            ui { vm.seekTo(30000); vm.controller.value!!.play() }
            until { vm.playback.value.playerState == Player.STATE_ENDED && !vm.playback.value.playWhenReady }
            assertEquals(path, vm.playback.value.details!!.video.id)
            assertEquals(1, vm.queue.value.items.size)
            ui { vm.repeatQueue(QueueRepeat.ONE) }; until { vm.queue.value.repeat == QueueRepeat.ONE }
            ui { vm.seekTo(30200); vm.controller.value!!.play() }
            until { vm.playback.value.playing && vm.playback.value.position < 1500 }
            ui { vm.controller.value!!.pause() }
        }
        val writes = fixture().getJSONArray("events")
        assertFalse((0 until writes.length()).any { writes.getJSONObject(it).getString("path").contains("/auth/playback") || writes.getJSONObject(it).getString("path").contains("/auth/history") })
    }
    @Test fun fullVideoContinuesAtTheSameSceneAndKeepsPlaybackChoices() {
        incoming("/clip/$id"); ready(id)
        ui { vm.controller.value!!.pause(); vm.seekTo(5000); vm.speed(1.5f) }
        until { vm.playback.value.position in 4998L..5002L }
        ui { assertEquals(30444L, vm.controller.value!!.duration) }
        screenshot("details-light")
        ui { vm.preferences.value = vm.preferences.value.copy(darkMode = "dark") }
        screenshot("details-dark")
        compose.onNodeWithTag("clip-full-video").performScrollTo().performClick()
        ready(); until { vm.queue.value.current?.clip == null && vm.playback.value.position == 17345L }
        ui { assertTrue(kotlin.math.abs(vm.controller.value!!.duration - 120000L) <= 2) }
        ui { assertEquals(1.5f, vm.controller.value!!.playbackParameters.speed) }
        ui { assertFalse(vm.controller.value!!.playWhenReady) }
    }
    @Test fun createPreviewFailedPublishDraftAndDeletionCompleteTheFlow() {
        incoming("/watch?v=testvideo01&t=45.6&autoplay=1"); ready()
        until { vm.playback.value.playing }
        until { compose.onAllNodesWithTag("create-clip").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithTag("create-clip").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("mini-player-preview").performClick()
        compose.onNodeWithTag("create-clip").performScrollTo().performClick()
        until { vm.clipEditor.value.open && !vm.clipEditor.value.loadingFrames }
        ui { assertFalse(vm.controller.value!!.playWhenReady) }
        assertEquals(6, vm.clipEditor.value.frames.size)
        until { !serviceState().getBoolean("playing") }
        Thread.sleep(200) // Let the main player's final pause-position write complete.
        val progress = fixture().getLong("position")
        val history = fixture().getJSONArray("watched").toString()
        compose.onNodeWithTag("clip-title").performTextInput("A beautiful moment 😀")
        compose.onNodeWithTag("clip-start").performTextReplacement("0:12.3")
        compose.onNodeWithTag("clip-end").performTextReplacement("0:42.7")
        compose.onNodeWithTag("clip-preview-toggle").performScrollTo().performClick()
        screenshot("editor-light")
        ui { vm.preferences.value = vm.preferences.value.copy(darkMode = "dark") }
        screenshot("editor-dark")
        assertEquals(progress, fixture().getLong("position"))
        assertEquals(history, fixture().getJSONArray("watched").toString())
        command("clips", """{"clipCreateFailNext":true}""")
        val before = fixture().getJSONArray("clipRequests").length()
        compose.onNodeWithTag("clip-publish").assertIsEnabled().performClick()
        until { fixture().getJSONArray("clipRequests").length() > before && !vm.clipEditor.value.busy && vm.clipEditor.value.error != null }
        assertEquals("A beautiful moment 😀", vm.clipEditor.value.title)
        assertEquals(12300L, vm.clipEditor.value.startMs)
        val requests = fixture().getJSONArray("clipRequests").length()
        compose.onNodeWithTag("clip-publish").performClick()
        until { fixture().getJSONArray("clipRequests").length() > requests && !vm.clipEditor.value.busy }
        assertNull("Requests: ${fixture().getJSONArray("clipRequests")}; before=$requests; published=${vm.clipEditor.value.published}", vm.clipEditor.value.error)
        val clip = vm.clipEditor.value.published!!
        assertEquals(30400L, clip.durationMs)
        compose.onNodeWithText("Copy link").performScrollTo().performClick()
        ui { assertEquals(clip.permalink, (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip!!.getItemAt(0).text.toString()) }
        compose.onNodeWithTag("clip-watch-published").performScrollTo().performClick(); ready(clip.id)
        assertEquals(clip.id, vm.queue.value.current!!.clip!!.id)
        ui { vm.controller.value!!.pause(); vm.confirmDeleteClip(clip) }
        command("clips", """{"clipDeleteFailNext":true}""")
        compose.onNodeWithTag("clip-delete-confirm").performClick()
        until { !vm.clipDeleteBusy.value && vm.clipDeleteError.value != null }
        compose.onNodeWithTag("clip-delete-confirm").performClick()
        until { vm.clipDelete.value == null && vm.queue.value.token.isEmpty() }
        assertFalse((0 until fixture().getJSONArray("clips").length()).any { fixture().getJSONArray("clips").getJSONObject(it).getString("clipId") == clip.id })
        assertTrue(fixture().getJSONArray("storyboardRequests").let { reads -> (0 until reads.length()).all { !reads.getJSONObject(it).getBoolean("authorized") } })
    }
    @Test fun editorAndClipSurviveRecreationWithoutReplayingAnIncomingLink() {
        incoming("/watch?v=testvideo01&t=40&autoplay=0"); ready()
        ui { vm.openClipEditor(); vm.clipTitle("Draft survives"); vm.clipTimes(12300, 42700) }
        ui { activity.recreate() }
        until {
            var resumed: MainActivity? = null
            ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
            if (resumed != null) activity = resumed!!
            resumed != null && vm.controller.value != null
        }
        assertTrue(vm.clipEditor.value.open); assertEquals("Draft survives", vm.clipEditor.value.title)
        assertEquals(12300L, vm.clipEditor.value.startMs)
        ui { vm.closeClipEditor() }
        incoming("/clip/$id"); ready(id); ui { vm.controller.value!!.pause(); vm.seekTo(5000) }
        until { vm.playback.value.position in 4998L..5002L }
        val token = vm.queue.value.token
        until { serviceState().getLong("positionMs") in 4998L..5002L && !serviceState().getBoolean("playing") }
        ui { activity.recreate() }
        until {
            var resumed: MainActivity? = null
            ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
            if (resumed != null) activity = resumed!!
            resumed != null && vm.controller.value != null
        }
        ui { assertEquals(token, vm.queue.value.token); assertEquals(30444L, vm.controller.value!!.duration) }
        ui { assertTrue("Restored position ${vm.controller.value!!.currentPosition}", vm.controller.value!!.currentPosition in 4998L..5002L) }
    }
    @Test fun notificationsClampToTheClipAndLegacyFailuresNeverOpenFullVideo() {
        incoming("/clip/$id"); ready(id); ui { vm.controller.value!!.pause(); vm.seekTo(5000) }
        ui { activity.startService(Intent(activity, PlaybackService::class.java).setAction("mobivious.rewind")) }
        until { vm.playback.value.position == 0L }
        ui { vm.seekTo(29000); activity.startService(Intent(activity, PlaybackService::class.java).setAction("mobivious.forward")) }
        until { vm.playback.value.position <= 30444L && vm.playback.value.position >= 30000L }
        incoming("/clip/legacy-clip"); ready("legacy-clip")
        ui { assertFalse(vm.queue.value.current!!.clip!!.native); assertEquals(5555L, vm.controller.value!!.duration) }
        command("clips", """{"clipLegacyInvalid":true}""")
        val token = vm.queue.value.token
        incoming("/clip/legacy-clip"); until { vm.clipResolution.value.error != null }
        assertEquals(token, vm.queue.value.token)
        ui { vm.dismissClipResolution(); vm.openClipLink(ClipLink(id, "https://other.test", "https://other.test/clip/$id")) }
        assertTrue(vm.clipResolution.value.foreign)
        assertEquals(base, vm.store.server)
    }
    @Test fun missingScopesAndUnavailableStoryboardsRemainActionable() {
        command("clips", """{"clipScopeFail":true,"clipStoryboardFail":true}""")
        ui { vm.navigate("Library", "clips") }
        until { !vm.browse.value.loading && vm.browse.value.error != null }
        assertTrue(vm.browse.value.error!!.contains("sign in again"))
        incoming("/watch?v=testvideo01&autoplay=0"); ready()
        ui { vm.openClipEditor() }; until { !vm.clipEditor.value.loadingFrames }
        assertTrue(vm.clipEditor.value.frames.isEmpty())
        compose.onNodeWithTag("clip-filmstrip").performScrollTo().assertExists()
        compose.onNodeWithTag("clip-title").performTextInput("Still usable")
        compose.onNodeWithTag("clip-publish").assertIsEnabled()
    }
    @Test fun guestSignInReturnsToCreationAndCancelRestoresPlayingIntent() {
        ui { vm.closePlayer(); vm.store.save(null) }; until { vm.account.value == null && !vm.preferences.value.watchHistory && vm.queue.value.token.isEmpty() && !vm.browse.value.loading }
        compose.waitForIdle()
        incoming("/watch?v=testvideo01&t=40&autoplay=1"); ready()
        until { vm.playback.value.playing && (compose.onAllNodesWithTag("create-clip").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty()) }
        if (compose.onAllNodesWithTag("create-clip").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("mini-player-preview").performClick()
        until { compose.onAllNodesWithTag("create-clip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("create-clip").performScrollTo().performClick()
        until { vm.tab == "Account" }
        compose.onNodeWithTag("account-username").performScrollTo().performTextInput("fixture-user")
        compose.onNodeWithTag("account-password").performScrollTo().performTextInput("fixture-password")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.account.value?.username == "fixture-user" && vm.clipEditor.value.open }
        assertEquals("testvideo01", vm.clipEditor.value.details!!.video.id)
        ui { assertFalse(vm.controller.value!!.playWhenReady); vm.closeClipEditor() }
        until { vm.playback.value.playWhenReady }
        ui { vm.controller.value!!.pause() }; until { !vm.playback.value.playWhenReady }
        ui { vm.openClipEditor(); vm.clipTitle("Paused draft"); vm.closeClipEditor() }
        ui { assertFalse(vm.controller.value!!.playWhenReady) }
        ui { vm.openClipEditor() }; assertEquals("Paused draft", vm.clipEditor.value.title)
    }
    @Test fun staleReadsAndUnavailableSourcesKeepCurrentPlaybackAndClearPrivateDrafts() {
        command("clips", """{"clipDelayNext":500}""")
        incoming("/clip/$id")
        until { vm.clipResolution.value.loading }
        ui { vm.navigate("Library") }
        until { fixture().getJSONArray("clipRequests").length() > 0 }
        Thread.sleep(600)
        assertNull(vm.queue.value.current?.clip)
        command("stream", """{"type":"dash","failNext":true}""")
        incoming("/clip/$id")
        until { vm.queue.value.current?.clip?.id == id && vm.queue.value.error != null }
        assertNotNull(vm.queue.value.current?.clip)
        ui { vm.retryPlayback() }; ready(id)
        ui { vm.watchFullClip() }; until { vm.queue.value.current?.clip == null && !vm.queue.value.loading }; ready()
        ui { vm.openClipEditor(); vm.clipTitle("Private account draft"); vm.store.save(null) }
        until { !vm.clipEditor.value.open && vm.clipEditor.value.title.isBlank() && vm.clipResolution.value.link == null }
    }
    @Test fun clipBoundsSurviveBufferRefreshFullscreenMiniPlayerAndBackground() {
        incoming("/clip/$id"); ready(id)
        ui { vm.controller.value!!.pause(); vm.seekTo(5000); vm.speed(1.5f); vm.audioOnly(true) }
        until { serviceState().getLong("positionMs") in 4998L..5002L }
        ui { vm.refreshBuffer() }; ready(id)
        ui { assertEquals(30444L, vm.controller.value!!.duration); assertEquals(1.5f, vm.controller.value!!.playbackParameters.speed); assertFalse(vm.controller.value!!.playWhenReady) }
        until { compose.onAllNodesWithContentDescription("Full screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Exit full screen").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        until { compose.onAllNodesWithTag("clip-details-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini-player-preview").performClick()
        until { compose.onAllNodesWithTag("clip-details-list").fetchSemanticsNodes().isNotEmpty() }
        ui { vm.store.background = true; vm.store.pip = false; vm.controller.value!!.play(); activity.moveTaskToBack(true) }
        until { serviceState().getLong("positionMs") > 5000 && serviceState().getBoolean("playing") }
        ui { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); vm.controller.value!!.pause(); vm.store.background = false; vm.store.pip = true }
        assertEquals(30444L, serviceState().getLong("durationMs"))
        assertTrue(vm.playback.value.chapters.isEmpty()); assertFalse(vm.chatReplay.value.available)
    }
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8).trim() }
    @Test fun trimAccessibilityKeyboardRotationAndWideEditorKeepTheDraftUsable() {
        incoming("/watch?v=testvideo01&t=40&autoplay=0"); ready()
        ui { vm.openClipEditor(); vm.clipTitle("A moment worth keeping 😀"); vm.clipTimes(30000, 60000) }
        until { !vm.clipEditor.value.loadingFrames }
        compose.onNodeWithTag("clip-filmstrip").performScrollTo()
        val actions = compose.onNodeWithContentDescription("Clip range").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        ui {
            assertTrue(actions.first { it.label == "Start 0.1 seconds earlier" }.action())
        }
        assertEquals(29900L, vm.clipEditor.value.startMs)
        compose.onNodeWithTag("clip-filmstrip").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .375f, height * .5f), androidx.compose.ui.geometry.Offset(width * .475f, height * .5f), 400)
        }
        assertEquals(30100L, vm.clipEditor.value.endMs - vm.clipEditor.value.startMs)
        assertTrue(vm.clipEditor.value.startMs > 29900)
        assertEquals(ClipRules.timestamp(vm.clipEditor.value.startMs), vm.clipEditor.value.startText)
        val title = vm.clipEditor.value.title
        val start = vm.clipEditor.value.startMs
        val keyboardSetting = shell("settings get secure show_ime_with_hard_keyboard").ifBlank { "null" }
        val handwritingSetting = shell("settings get secure stylus_handwriting_enabled").ifBlank { "null" }
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("settings put secure stylus_handwriting_enabled 0")
            Thread.sleep(750)
            compose.onNodeWithTag("clip-title").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("clip-title").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.onNodeWithTag("clip-title").assertIsFocused()
            compose.onNodeWithTag("clip-publish").assertIsDisplayed().assertIsEnabled()
            screenshot("editor-keyboard")
        } finally {
            shell(if (keyboardSetting == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $keyboardSetting")
            shell(if (handwritingSetting == "null") "settings delete secure stylus_handwriting_enabled" else "settings put secure stylus_handwriting_enabled $handwritingSetting")
        }
        compose.onNodeWithTag("clip-preview-toggle").performScrollTo().performClick()
        val font = shell("settings get system font_scale").ifBlank { "1.0" }
        val density = shell("wm density")
        try {
            shell("settings put system font_scale 1.6")
            until { activity.resources.configuration.fontScale >= 1.5f }
            compose.onNodeWithTag("clip-filmstrip").performScrollTo()
            compose.onNodeWithTag("clip-publish").assertIsDisplayed()
            screenshot("editor-large-text")
            ui { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            until { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithTag("clip-start").performScrollTo().assertIsDisplayed()
            screenshot("editor-landscape")
            assertEquals(title, vm.clipEditor.value.title); assertEquals(start, vm.clipEditor.value.startMs)
            ui { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            shell("settings put system font_scale 1.0"); shell("wm density 320")
            until { activity.resources.configuration.smallestScreenWidthDp >= 600 }
            compose.onNodeWithTag("clip-filmstrip").performScrollTo(); screenshot("editor-wide")
            assertEquals(title, vm.clipEditor.value.title); assertEquals(start, vm.clipEditor.value.startMs)
        } finally {
            shell("settings put system font_scale $font")
            val override = Regex("Override density: (\\d+)").find(density)?.groupValues?.get(1)
            shell(if (override == null) "wm density reset" else "wm density $override")
            ui { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
    @Test fun pictureInPictureKeepsTheRelativeTimelineAndScopedActions() {
        incoming("/clip/$id"); ready(id)
        ui { vm.controller.value!!.pause(); vm.seekTo(5000); vm.store.pip = true; activity.enterPip() }
        until { var pip = false; ui { pip = activity.isInPictureInPictureMode }; pip }
        assertEquals(30444L, serviceState().getLong("durationMs"))
        ui { activity.startService(Intent(activity, PlaybackService::class.java).setAction("mobivious.rewind")) }
        until { serviceState().getLong("positionMs") == 0L }
        Thread.sleep(1500) // Let the system finish its PiP entry animation before the launcher expands it.
        android.util.Log.i("ClipsTest", shell("sh -c 'am start --windowingMode 1 -n net.wingress.mobivious.debug/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task 2>&1'"))
        ui { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        until { var pip = true; ui { pip = activity.isInPictureInPictureMode }; !pip }
        assertEquals(id, vm.queue.value.current!!.clip!!.id)
    }
}
