package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackService
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Local generated media only; scripts/test-android.sh starts the fixture. */
@RunWith(AndroidJUnit4::class)
class QueueLibrarySmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    private fun command(name: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    @Before fun launch() {
        command("reset"); command("queue")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui { activity.model.switchServer("http://127.0.0.1:18080"); activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings() }
        waitFor { activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() { if (::activity.isInitialized) ui { activity.model.closePlayer(); activity.finishAndRemoveTask() } }
    private fun playSource(id: String = "PLfixture", index: Int = 0) {
        ui { activity.model.play("testvideo01", source = id, index = index); activity.sharedVideo.value = true }
        waitFor { !activity.model.queue.value.loading && activity.model.playback.value.playing }
    }
    private fun finishCurrent() { ui { activity.model.seekTo(119_700) } }
    @Test fun guestSaveSignInCreateAndRetryKeepsCreatedPlaylist() {
        compose.onAllNodesWithTag("video-actions-testvideo01").onFirst().performClick()
        compose.onNodeWithText("Save to playlist").performClick()
        compose.onNodeWithTag("save-playlist-sheet").assertExists()
        compose.onNodeWithText("Sign in").performClick()
        command("queue", "{\"failSave\":true}")
        ui { activity.model.store.save(Account("fixture-token", "Alice", Long.MAX_VALUE, activity.model.store.server)) }
        waitFor { !activity.model.saveSheet.value.loading }
        compose.onNodeWithTag("save-playlist-title").performTextInput("Create and save fixture")
        compose.onNodeWithTag("create-and-save").performClick()
        waitFor { activity.model.saveSheet.value.created != null && activity.model.saveSheet.value.error != null }
        val id = activity.model.saveSheet.value.created!!.id
        ui { activity.model.saveVideo() }
        waitFor { activity.model.saveSheet.value.video == null }
        assertEquals(1, fixture().getJSONArray("events").let { events -> (0 until events.length()).count { events.getJSONObject(it).optString("action") == "playlist-create" } })
        assertTrue(fixture().getJSONArray("playlists").let { lists -> (0 until lists.length()).any { lists.getJSONObject(it).getString("playlistId") == id && lists.getJSONObject(it).getInt("videoCount") == 1 } })
    }
    @Test fun audioMenuStartsWithoutVideoAndCarriesThroughNext() {
        compose.onAllNodesWithTag("video-actions-testvideo01").onFirst().performClick()
        compose.onNodeWithText("Audio mode").performClick()
        waitFor { activity.model.playback.value.playing }
        assertTrue(C.TRACK_TYPE_VIDEO in activity.model.controller.value!!.trackSelectionParameters.disabledTrackTypes)
        ui { activity.model.insertQueue(Video("testvideo02", "Next audio"), true) }
        waitFor { activity.model.queue.value.items.size == 2 }
        finishCurrent()
        waitFor { activity.model.queue.value.current?.video?.id == "testvideo02" && activity.model.playback.value.playing }
        assertTrue(C.TRACK_TYPE_VIDEO in activity.model.controller.value!!.trackSelectionParameters.disabledTrackTypes)
        assertFalse(activity.model.preferences.value.listen)
    }
    @Test fun duplicateOccurrencesAutomaticAdvanceAndRepeatAll() {
        playSource()
        val first = activity.model.queue.value.currentKey
        finishCurrent()
        waitFor { activity.model.queue.value.currentKey != first && activity.model.playback.value.playing }
        assertEquals("testvideo02", activity.model.queue.value.current!!.video.id)
        ui { activity.model.queueCommand(PlaybackService.QUEUE_MORE) }
        waitFor { activity.model.queue.value.sourceComplete }
        ui { activity.model.repeatQueue(QueueRepeat.ALL); activity.model.selectQueue(activity.model.queue.value.items.last().key) }
        waitFor { activity.model.queue.value.currentIndex == activity.model.queue.value.items.lastIndex && activity.model.playback.value.playing }
        finishCurrent()
        waitFor { activity.model.queue.value.currentIndex == 0 && activity.model.playback.value.playing }
    }
    @Test fun localRemovalDoesNotDeleteSavedPlaylistAndCurrentFinishes() {
        ui { activity.model.store.save(Account("fixture-token", "Alice", Long.MAX_VALUE, activity.model.store.server)) }
        playSource("IVqueue")
        val key = activity.model.queue.value.currentKey!!
        ui { activity.model.queueCommand(PlaybackService.QUEUE_REMOVE) { putString("key", key) } }
        waitFor { activity.model.queue.value.current?.removed == true }
        assertTrue(activity.model.playback.value.playing)
        assertEquals(3, fixture().getJSONArray("playlists").getJSONObject(0).getInt("videoCount"))
        finishCurrent()
        waitFor { activity.model.queue.value.currentKey != key && activity.model.playback.value.playing }
        assertEquals("testvideo01", activity.model.queue.value.current!!.video.id)
    }
    @Test fun backgroundQueueAndActivityRecreationUseServiceState() {
        playSource()
        val token = activity.model.queue.value.token
        ui { activity.model.store.background = true; activity.model.store.pip = false; activity.model.seekTo(119_700); activity.moveTaskToBack(true) }
        waitFor { activity.model.queue.value.currentIndex == 1 && activity.model.playback.value.playing }
        assertEquals(token, activity.model.queue.value.token)
        ui { activity.recreate() }
        waitFor { activity.model.queue.value.token == token }
    }
    @Test fun mixAndPlaylistLinksRetainContextsAndMixDisablesAll() {
        ui { activity.model.openLink(VideoLinks.parse("https://youtube.com/watch?v=testvideo01&list=RDtestvideo01&t=0", activity.model.store.server)!!) }
        waitFor { activity.model.playback.value.playing && activity.model.queue.value.source?.mix == true }
        val loaded = activity.model.queue.value.items.size
        ui { activity.model.queueCommand(PlaybackService.QUEUE_MORE) }
        waitFor { !activity.model.queue.value.sourceLoading && activity.model.queue.value.items.size > loaded }
        assertNull(activity.model.queue.value.sourceError)
        assertFalse(activity.model.queue.value.sourceComplete)
        ui { activity.model.repeatQueue(QueueRepeat.ALL); activity.model.queueOpen.value = true }
        compose.onNodeWithTag("queue-repeat-ALL").assertIsNotEnabled()
        finishCurrent()
        waitFor { activity.model.queue.value.current?.video?.id == "testvideo02" && activity.model.playback.value.playing }
    }
    @Test fun repeatOneRestartsAndRemovedPlayingSourceStillAdvances() {
        ui { activity.model.store.save(Account("fixture-token", "Alice", Long.MAX_VALUE, activity.model.store.server)) }
        playSource("IVqueue")
        val key = activity.model.queue.value.currentKey!!
        ui { activity.model.repeatQueue(QueueRepeat.ONE) }
        finishCurrent()
        waitFor { activity.model.queue.value.currentKey == key && activity.model.playback.value.position < 3000 && activity.model.playback.value.playing }
        ui { activity.model.queueCommand(PlaybackService.QUEUE_DELETE_SOURCE) { putString("key", key) } }
        waitFor { activity.model.queue.value.current!!.removed }
        assertTrue(activity.model.playback.value.playing)
        assertEquals(2, fixture().getJSONArray("playlists").getJSONObject(0).getInt("videoCount"))
        finishCurrent()
        waitFor { activity.model.queue.value.currentKey != key && activity.model.playback.value.playing }
    }
    @Test fun pictureInPictureKeepsServiceOwnedAdvancement() {
        org.junit.Assume.assumeTrue(activity.supportsPip())
        playSource()
        ui { activity.enterPip() }
        waitFor { activity.isInPictureInPictureMode }
        finishCurrent()
        waitFor { activity.model.queue.value.currentIndex == 1 && activity.model.playback.value.playing }
        assertTrue(activity.isInPictureInPictureMode)
    }
    @Test fun blockUndoAndAccountChangeInvalidateOldUndoAndQueue() {
        ui { activity.model.store.save(Account("fixture-token", "Alice", Long.MAX_VALUE, activity.model.store.server)) }
        waitFor { activity.model.blocked.value.loaded }
        ui { activity.model.toggleBlocked("UC" + "a".repeat(22), "Studio") }
        waitFor { activity.model.blockUndo.value != null }
        val undo = activity.model.blockUndo.value!!
        compose.onNodeWithText("Undo").performClick()
        waitFor { undo.id !in activity.model.blocked.value.ids }
        playSource()
        ui { activity.model.store.save(null); activity.model.undoBlock(undo) }
        waitFor { activity.model.queue.value.token.isEmpty() }
        assertNull(activity.model.blockUndo.value)
    }
}
