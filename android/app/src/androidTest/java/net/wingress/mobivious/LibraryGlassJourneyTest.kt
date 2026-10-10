package net.wingress.mobivious

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Library presentation against disposable accounts and real local media. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LibraryGlassJourneyTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5000)
        for (command in listOf("mkdir -p /data/local/tmp/mobivious-library-glass", "screencap -p /data/local/tmp/mobivious-library-glass/$name.png"))
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun page(route: String) {
        compose.runOnUiThread { vm.navigate("You", route) }
        until { vm.route == route && !vm.browse.value.loading }
    }
    @Before fun launch() {
        command("reset"); command("playlist-rss"); command("clips", """{"count":3}""")
        command("downloads", """{"downloadDuration":12,"downloadSlow":false,"downloadFailKey":""}""")
        command("search-history", """{"historyEntries":[
            {"video_id":"testvideo01","title":"Saved today","channel_name":"Mobivious Studio","latest_watched":"2026-10-04","length_seconds":120},
            {"video_id":"testvideo02","title":"A moment to return to","channel_name":"Mobivious Studio","latest_watched":"2026-10-03","length_seconds":120}
        ]}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences()); vm.store.reduceTransparency(false)
            vm.store.save(Account("fixture-token", "LibraryViewer", Long.MAX_VALUE, vm.store.server))
        }
        until { vm.account.value != null && vm.controller.value != null && vm.playlists.value.any { it.owned } && !vm.browse.value.loading }
        runBlocking { vm.downloads.records.value.forEach { vm.downloads.delete(it.id) } }
    }
    @After fun close() {
        if (::activity.isInitialized) {
            compose.runOnUiThread { vm.closeClipEditor(); vm.dismissDownload(); vm.closePlayer(); vm.store.reduceTransparency(false) }
            runBlocking { vm.downloads.records.value.forEach { vm.downloads.delete(it.id) } }
            compose.runOnUiThread { vm.store.save(null); vm.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask() }
        }
    }

    @Test fun libraryPagesAndEditorsKeepPlaybackAndTheirGlassControls() {
        for (dark in listOf(false, true)) {
            compose.runOnUiThread { vm.preferences.value = vm.preferences.value.copy(darkMode = if (dark) "dark" else "light") }
            val appearance = if (dark) "dark" else "light"
            page(""); capture("you-$appearance")
            compose.onNodeWithTag("you-playlists").performClick()
            until { vm.route == "playlists" && !vm.browse.value.loading }
            capture("playlists-$appearance")
            compose.onNodeWithTag("playlist-create").performClick()
            compose.onNodeWithTag("playlist-form-title").performTextInput("An afternoon collection")
            capture("playlist-editor-$appearance")
            compose.onNodeWithContentDescription("Close").performClick()
            val playlist = vm.playlists.value.first { it.owned }
            compose.runOnUiThread { vm.openPlaylist(playlist) }
            until { vm.playlist.value?.id == playlist.id && !vm.browse.value.loading }
            capture("playlist-details-$appearance")
            page("history"); capture("history-$appearance")
            page("clips"); capture("clips-$appearance")
        }
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")) }
        until { vm.playback.value.playing && vm.playback.value.details != null }
        val token = vm.queue.value.token
        page("history")
        compose.onNodeWithTag("mini-player-preview").assertExists()
        compose.runOnUiThread { vm.openClipEditor(); vm.clipTitle("An afternoon moment"); vm.clipTimes(10000, 30000) }
        until { vm.clipEditor.value.open && !vm.clipEditor.value.loadingFrames }
        compose.onNodeWithTag("clip-publish").assertIsDisplayed()
        capture("clip-editor-dark")
        compose.onNodeWithContentDescription("Close clip editor").performClick()
        assertEquals(token, vm.queue.value.token)
        compose.runOnUiThread { vm.openDownload(vm.playback.value.details!!.video) }
        until { !vm.downloadDialog.value.loading && vm.downloadDialog.value.catalog != null }
        compose.onNodeWithTag("download-confirm").assertIsDisplayed()
        capture("download-selection-dark")
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(token, vm.queue.value.token)
        compose.runOnUiThread { vm.store.reduceTransparency(true) }
        page(""); capture("you-dark-opaque-mini")
        assertTrue(vm.playback.value.playing)
        assertEquals(token, vm.queue.value.token)
    }

    @Test fun downloadProgressFailuresDetailsAndOfflinePlaybackRemainActionable() {
        command("downloads", """{"downloadSlow":true}""")
        val catalog = runBlocking { vm.api.downloads("testvideo01") }
        val id = runBlocking { vm.downloads.create(vm.store.server, catalog, DownloadSelection(audio = "aes")) }
        until { vm.downloads.find(id)?.active == true }
        page("downloads"); capture("downloads-active-light")
        compose.runOnUiThread { vm.cancelDownload(id) }
        until { vm.downloads.find(id)?.active == false }
        command("downloads", """{"downloadSlow":false,"downloadFailKey":"aes"}""")
        compose.runOnUiThread { vm.retryDownload(id) }
        until { vm.downloads.find(id)?.assets?.any { it.status == DownloadStatus.FAILED } == true }
        capture("downloads-failed-light")
        command("downloads", """{"downloadFailKey":""}""")
        compose.onNodeWithText("Retry", substring = false).performClick()
        until { vm.downloads.find(id)?.playable == true }
        compose.onNodeWithTag("download-details-$id").performClick()
        capture("downloads-details-light")
        compose.runOnUiThread { vm.preferences.value = vm.preferences.value.copy(darkMode = "dark", thinMode = true, uiDensity = "compact") }
        capture("downloads-dark-compact-thin")
        compose.onNodeWithTag("download-actions-$id").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.onNodeWithText("Delete download?").assertIsDisplayed()
        capture("download-delete-dark")
        compose.onNodeWithText("Cancel", substring = false).performClick()
        assertNotNull(vm.downloads.find(id))
        compose.onNodeWithTag("download-play-$id").performClick()
        until { vm.playback.value.downloadId == id && vm.playback.value.playing }
        assertTrue(vm.playback.value.details!!.video.thumbnail.startsWith("file:"))
    }
}
