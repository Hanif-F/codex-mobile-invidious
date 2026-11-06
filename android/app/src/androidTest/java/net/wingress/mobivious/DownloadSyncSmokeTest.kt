package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DownloadSyncSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val app get() = activity.application as MobiviousApplication
    private val video = "testvideo01"
    private fun until(condition: () -> Boolean) = compose.waitUntil(60_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Before fun open() {
        command("reset"); command("download-sync", """{"downloadSync":true}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.closePlayer(); app.store.save(null); activity.model.switchServer("http://127.0.0.1:18080")
            app.store.guestPreferences(AccountPreferences()); activity.model.refreshSharedSettings()
        }
        until { !activity.model.browse.value.loading }
        runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
    }
    @After fun close() {
        if (::activity.isInitialized) {
            command("download-sync", """{"playbackWriteStatus":0,"playbackReadStatus":0}""")
            compose.runOnUiThread { activity.model.closePlayer() }
            runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
            compose.runOnUiThread {
                app.playbackSync.discard(app.api.context()); app.store.save(null); activity.finishAndRemoveTask()
            }
        }
    }
    private fun login() {
        compose.runOnUiThread { activity.model.action { activity.model.login("DownloadSyncViewer", "fixture-password") } }
        until { activity.model.account.value != null && activity.model.preferences.value.savePosition && activity.model.tab == "You" }
        compose.runOnUiThread { app.playbackSync.discard(app.api.context()) }
    }
    private fun download(): String {
        val id = runBlocking {
            app.downloads.create(app.store.server, app.api.downloads(video), DownloadSelection(audio = "aen"))
        }
        // Await artwork too before a test takes its baseline of network requests.
        until { app.downloads.find(id)?.let { it.playable && !it.active } == true }
        return id
    }
    private fun play(id: String) {
        compose.runOnUiThread { activity.model.playDownload(id) }
        until { activity.model.playback.value.downloadId == id && activity.model.playback.value.playerState == Player.STATE_READY && activity.model.playback.value.playing }
    }
    private fun seek(position: Long) {
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(position) }
        until { kotlin.math.abs(activity.model.playback.value.position - position) < 500 }
    }
    private fun recreate() {
        val old = activity
        compose.runOnUiThread { activity.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    @Test fun onlineDownloadSharesHistoryProgressAndCompletion() {
        login(); val id = download(); val before = state().getInt("mediaRequests")
        play(id)
        until { state().getJSONArray("watched").toString().contains(video) }
        seek(45_000)
        until { state().getJSONObject("positions").optLong(video) == 45L }
        until { app.downloads.find(id)!!.positionMs == 45_000L }
        assertTrue(video in app.watched.state.value.watched)
        assertEquals(45L, app.watched.state.value.positions[video])
        seek(119_000)
        until { !state().getJSONObject("positions").has(video) }
        assertTrue(video in app.watched.state.value.watched)
        assertEquals(before, state().getInt("mediaRequests"))
    }
    @Test fun serverResumeLoadsAndRetryKeepsTheCurrentPosition() {
        login(); val id = download()
        command("watched", """{"positions":{"$video":35}}""")
        play(id)
        assertTrue(activity.model.playback.value.position in 35_000..40_000)
        seek(55_000)
        compose.runOnUiThread { activity.model.retryPlayback() }
        until { activity.model.playback.value.playerState == Player.STATE_READY && !activity.model.playback.value.loading }
        assertTrue(activity.model.playback.value.position in 54_500..55_500)
    }
    @Test fun downloadedMusicStartsOverForGuestsAndAccountsAndKeepsWatchHistory() {
        command("music-resume")
        val id = download()
        runBlocking { app.downloads.position(id, 45_000) }
        val before = state().getJSONArray("allRequests").toString()
        play(id)
        assertTrue(activity.model.playback.value.position < 3_000L)
        seek(55_000)
        compose.runOnUiThread { activity.model.closePlayer() }
        until { activity.model.queue.value.current == null }
        assertEquals(45_000L, app.downloads.find(id)!!.positionMs)
        assertEquals(before, state().getJSONArray("allRequests").toString())

        login()
        command("watched", """{"positions":{"$video":60}}""")
        play(id)
        assertTrue(activity.model.playback.value.position < 3_000L)
        until { state().getJSONArray("watched").toString().contains(video) }
        seek(75_000)
        compose.runOnUiThread { activity.model.retryPlayback() }
        until { !activity.model.queue.value.loading && activity.model.playback.value.playerState == Player.STATE_READY }
        assertTrue(activity.model.playback.value.position in 74_500L..75_500L)
        compose.runOnUiThread { activity.model.closePlayer() }
        until { activity.model.queue.value.current == null }
        assertEquals(45_000L, app.downloads.find(id)!!.positionMs)
        assertNull(app.playbackSync.pending()[video]?.position)
        val fixture = state()
        assertEquals(60L, fixture.getJSONObject("positions").getLong(video))
        val requests = fixture.getJSONArray("allRequests")
        assertFalse((0 until requests.length()).any { requests.getJSONObject(it).getString("path") == "/api/v1/auth/playback/$video" })
    }
    @Test fun failedSyncPersistsAcrossRecreationAndReconnectDrainsWithoutReopeningVideo() {
        login(); val id = download()
        command("download-sync", """{"playbackWriteStatus":503}""")
        play(id); seek(47_000)
        until { app.playbackSync.pending()[video]?.seconds == 47L }
        val restored = SessionStore(activity)
        assertEquals(47L, restored.playbackJournal(restored.positionContext()).pending[video]!!.seconds)
        recreate()
        assertEquals(id, activity.model.playback.value.downloadId)
        assertEquals(47L, app.playbackSync.pending()[video]?.seconds)
        command("download-sync", """{"playbackWriteStatus":0}""")
        compose.runOnUiThread { app.syncCoordinator.resume() }
        until { app.playbackSync.pending().isEmpty() && state().getJSONObject("positions").optLong(video) == 47L }
        assertTrue(state().getJSONArray("watched").toString().contains(video))
    }
    @Test fun preferencesDisableAccountWritesButKeepLocalDownloadResume() {
        login(); val id = download()
        runBlocking { app.api.preferences(JSONObject().put("watch_history", false).put("save_player_pos", false), app.api.context()) }
        compose.runOnUiThread { activity.model.refreshSharedSettings() }
        until { !activity.model.preferences.value.watchHistory && !activity.model.preferences.value.savePosition }
        play(id); seek(42_000)
        until { app.downloads.find(id)!!.positionMs == 42_000L }
        assertTrue(app.playbackSync.pending().isEmpty())
        assertFalse(state().getJSONObject("positions").has(video))
        assertFalse(state().getJSONArray("watched").toString().contains(video))
        compose.runOnUiThread { activity.model.closePlayer() }
        play(id)
        assertTrue(activity.model.playback.value.position in 42_000..47_000)
    }
    @Test fun settingChangesDuringPlaybackImmediatelyStopAndRestartPositionSync() {
        login(); val id = download(); play(id); seek(30_000)
        until { state().getJSONObject("positions").optLong(video) == 30L }
        runBlocking { app.api.preferences(JSONObject().put("watch_history", false).put("save_player_pos", false), app.api.context()) }
        compose.runOnUiThread { activity.model.refreshSharedSettings() }
        until { !activity.model.preferences.value.savePosition && !activity.model.preferences.value.watchHistory }
        seek(50_000)
        until { app.downloads.find(id)!!.positionMs == 50_000L }
        assertTrue(app.playbackSync.pending().isEmpty()); assertFalse(state().getJSONObject("positions").has(video))
        runBlocking { app.api.preferences(JSONObject().put("watch_history", true).put("save_player_pos", true), app.api.context()) }
        // Confirmed API settings also reach the service without a ViewModel refresh.
        until { app.playbackSync.settings()?.position == true }
        seek(60_000)
        until { state().getJSONObject("positions").optLong(video) == 60L }
    }
    @Test fun resumeAuthenticationFailureFallsBackToLocalWithoutRevokingPlayback() {
        login(); val id = download(); val owner = app.store.account.value
        runBlocking { app.downloads.position(id, 40_000) }
        command("download-sync", """{"playbackReadStatus":401}""")
        play(id)
        assertTrue(activity.model.playback.value.position in 40_000..45_000)
        assertEquals(owner, app.store.account.value)
        assertEquals(id, activity.model.playback.value.downloadId)
    }
    @Test fun authenticationFailureKeepsLocalPlaybackAndOtherAccountsCannotDrainTheQueue() {
        login(); val id = download(); val owner = app.api.context()
        command("download-sync", """{"playbackWriteStatus":401}""")
        play(id); seek(44_000)
        until { app.playbackSync.pending()[video]?.seconds == 44L }
        compose.runOnUiThread { activity.model.controller.value!!.play() }
        until { activity.model.playback.value.playing }
        assertEquals(owner.account, app.store.account.value)
        compose.runOnUiThread { app.store.save(owner.account!!.copy(username = "OtherViewer", profileId = "b".repeat(64))) }
        until { app.api.context().account?.username == "OtherViewer" }
        assertTrue(app.playbackSync.pending().isEmpty())
        assertEquals(44L, app.store.playbackJournal(owner).pending[video]!!.seconds)
        compose.runOnUiThread { app.store.save(owner.account) }
        command("download-sync", """{"playbackWriteStatus":0}""")
        compose.runOnUiThread { app.syncCoordinator.resume() }
        until { app.playbackSync.pending().isEmpty() }
    }
}
