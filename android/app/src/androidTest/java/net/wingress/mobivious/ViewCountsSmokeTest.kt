package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackService
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Real UI and MediaSession handoffs against opt-in localhost count responses. */
@RunWith(AndroidJUnit4::class)
class ViewCountsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val app get() = activity.application as MobiviousApplication
    private val base = "http://127.0.0.1:18080"
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("$base/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }
            inputStream.close(); disconnect()
        }
    }
    private fun detailRequests() = JSONObject(URL("$base/test/state").readText()).getJSONArray("videoDetailRequests").length()

    @Before fun launch() {
        command("reset"); command("view-counts")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            vm.closePlayer(); vm.store.save(null); vm.switchServer(base)
            vm.store.guestPreferences(AccountPreferences(autoplay = false, watchHistory = false, thinMode = true, uiDensity = "compact"))
            vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() && vm.controller.value != null && vm.queue.value.token.isEmpty() }
        runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
    }
    @After fun close() {
        if (::activity.isInitialized) {
            ui { vm.closePlayer() }
            runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
            ui { vm.store.save(null); activity.finishAndRemoveTask() }
        }
    }
    private fun account() {
        ui { vm.store.save(Account("fixture-token", "fixture-user", Long.MAX_VALUE, base)) }
        until { vm.account.value != null && vm.preferences.value.uiDensity == "compact" }
    }
    private fun browseCount(id: String, expected: String) {
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("video-metadata-$id"))
        compose.onNodeWithTag("video-metadata-$id").assertTextContains(expected, substring = true)
    }
    private fun queueOpen() {
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("playback-queue-header"))
        compose.onNodeWithTag("playback-queue-header").performClick()
        until { vm.queueExpanded.value }
    }
    private fun recreate() {
        val old = activity
        ui { old.recreate() }
        until {
            var resumed = false
            ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }
            resumed
        }
        ui { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }

    @Test fun upNextAndManualQueueKeepSuppliedCountsWithoutExtraVideoRequests() {
        browseCount("testvideo01", "0 views")
        assertEquals(0, detailRequests())
        ui { vm.play("testvideo01"); activity.sharedVideo.value = true }
        until { vm.playback.value.details != null && !vm.playback.value.loading }
        compose.onNodeWithTag("video-metadata-testvideo01").assertTextContains("0 views", substring = true)
        for ((id, expected) in listOf("testvideo02" to "1.2K views", "testvideo03" to "12 watching")) {
            compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("video-metadata-$id"))
            compose.onNodeWithTag("video-metadata-$id").assertTextContains(expected, substring = true)
        }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("video-card-unknownvid1"))
        compose.onNodeWithTag("video-metadata-unknownvid1").assertTextEquals("today")
        captureWatchScreenshot("view-counts-up-next")
        ui { vm.insertQueue(vm.playback.value.details!!.recommendations.first(), false) }
        until { vm.queue.value.hasExplicitQueue && vm.queue.value.items.size == 2 }
        queueOpen()
        compose.onNodeWithTag("video-view-count-testvideo02", true).assertTextEquals("1.2K views")
        assertEquals(1, detailRequests())
        captureWatchScreenshot("view-counts-manual-queue")
    }

    @Test fun historyPlaylistAndMixPaginationUseSuppliedCountsWithoutFetchingVideos() {
        account()
        ui { vm.navigate("You", "history") }
        until { !vm.browse.value.loading && vm.browse.value.history != null }
        assertEquals(2, vm.browse.value.videos.size)
        ui { vm.more() }
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 4 }
        browseCount("testvideo01", "0 views"); browseCount("testvideo02", "1.2K views"); browseCount("testvideo03", "12 watching")
        captureWatchScreenshot("view-counts-history")
        ui { vm.openPlaylist(Playlist("PLcounts", "Counts", 4)) }
        until { !vm.browse.value.loading && vm.route == "playlist:PLcounts" }
        browseCount("testvideo01", "0 views"); browseCount("testvideo02", "1.2K views")
        ui { vm.more() }
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 4 }
        assertEquals(2, vm.browse.value.videos.count { it.id == "testvideo01" && it.views == 0L })
        assertNull(vm.browse.value.videos.last().views)
        captureWatchScreenshot("view-counts-playlist")
        ui { vm.openPlaylist(Playlist("RDfixture", "Mix", -1, seedVideoId = "testvideo01")) }
        until { !vm.browse.value.loading && vm.route == "playlist:RDfixture" }
        browseCount("testvideo01", "0 views"); browseCount("testvideo02", "1.2K views")
        ui { vm.more() }
        until { !vm.browse.value.loading && vm.browse.value.videos.any { it.id == "testvideo03" } }
        browseCount("testvideo03", "12 watching")
        assertEquals(0, detailRequests())
    }

    @Test fun duplicatePlaylistQueueCountsSurvivePaginationAndRecreation() {
        ui { vm.play("testvideo01", source = "PLcounts", index = 0); activity.sharedVideo.value = true }
        until { vm.queue.value.details != null && !vm.queue.value.loading && !vm.queue.value.sourceLoading }
        ui { vm.queueCommand(PlaybackService.QUEUE_MORE) }
        until { !vm.queue.value.sourceLoading && vm.queue.value.items.size == 4 }
        val repeated = vm.queue.value.items.filter { it.video.id == "testvideo01" }
        assertEquals(2, repeated.size)
        assertTrue(repeated.all { it.video.views == 0L })
        recreate()
        until { vm.playback.value.details != null }
        queueOpen()
        for (entry in repeated) {
            val row = hasTestTag("queue-occurrence-${entry.key}")
            compose.onNodeWithTag("playback-queue-items").performScrollToNode(row)
            compose.onNode(hasTestTag("video-view-count-testvideo01") and hasAnyAncestor(row), true).assertTextEquals("0 views")
        }
        captureWatchScreenshot("view-counts-duplicate-queue")
        assertEquals(1, detailRequests())
    }

    @Test fun clipsIdentifySourceCountsAndDownloadsRetainCountsOffline() {
        command("clips", """{"count":1}""")
        account()
        ui { vm.navigate("You", "clips") }
        until { !vm.browse.value.loading && vm.browse.value.clips.isNotEmpty() }
        compose.onNodeWithTag("video-view-count-testvideo01", true).assertTextEquals("Source video · 0 views")
        val clip = vm.browse.value.clips.first()
        compose.onNodeWithTag("clip-card-${clip.id}").performClick()
        until { vm.playback.value.clip != null && !vm.playback.value.loading }
        compose.onNodeWithTag("clip-details-list").performScrollToNode(hasTestTag("clip-full-video"))
        compose.onNode(hasTestTag("video-view-count-testvideo01") and hasAnyAncestor(hasTestTag("clip-full-video")), true)
            .assertTextEquals("Source video · 0 views")
        captureWatchScreenshot("view-counts-clip-source")
        ui { vm.closePlayer() }
        val id = runBlocking { app.downloads.create(base, app.api.downloads("testvideo01"), DownloadSelection(audio = "aes")) }
        until { app.downloads.find(id)?.playable == true }
        command("downloads", """{"downloadDisabled":true}""")
        val before = detailRequests()
        ui { vm.navigate("You", "downloads") }
        compose.onNodeWithTag("video-view-count-testvideo01", true).assertTextEquals("0 views")
        recreate()
        compose.onNodeWithTag("video-view-count-testvideo01", true).assertTextEquals("0 views")
        captureWatchScreenshot("view-counts-download-row")
        ui { vm.playDownload(id); activity.sharedVideo.value = true }
        until { vm.playback.value.downloadId == id && !vm.playback.value.loading }
        compose.onNodeWithTag("video-metadata-testvideo01").assertTextContains("0 views", substring = true)
        assertEquals(before, detailRequests())
        captureWatchScreenshot("view-counts-offline")
    }
}
