package net.wingress.mobivious

import android.content.Intent
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Run the two phases with scripts/test-download-restart.sh, which stops the app between them. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DownloadsPersistenceSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private fun open(): MainActivity {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("downloadRestart") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    private fun until(condition: () -> Boolean) = compose.waitUntil(90_000, condition)
    private fun command(body: String) {
        (URL("http://127.0.0.1:18080/test/downloads").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Test fun seedBackgroundTransfers() {
        val activity = open(); val app = activity.application as MobiviousApplication
        compose.runOnUiThread { activity.model.switchServer("http://127.0.0.1:18080"); app.store.save(null); activity.model.closePlayer() }
        until { activity.model.browse.value.videos.isNotEmpty() }
        runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
        command("""{"downloadSlow":true,"downloadDisabled":false,"downloadFailKey":""}""")
        val ids = runBlocking {
            val catalog = app.api.downloads("testvideo01")
            listOf(app.downloads.create(app.store.server, catalog, DownloadSelection("v360", "aen", setOf("cen"))), app.downloads.create(app.store.server, catalog, DownloadSelection(audio = "aes")))
        }
        until { app.downloads.find(ids.first())!!.assets.any { it.status == DownloadStatus.RUNNING } }
        runBlocking { app.downloads.position(ids.first(), 4000) }
        val transfers = JSONObject().apply { app.downloads.records.value.forEach { record -> record.assets.forEach { asset -> put("${record.id}:${asset.choice.key}", asset.transferId) } } }
        activity.getSharedPreferences("download-restart-test", 0).edit().putString("first", ids[0]).putString("second", ids[1]).putInt("pid", android.os.Process.myPid()).putString("transfers", transfers.toString()).commit()
        // Emulate the durable export journal at an abrupt process interruption.
        val output = activity.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "interrupted-${ids[0]}.mp4"); put(android.provider.MediaStore.Downloads.MIME_TYPE, "video/mp4") })!!
        activity.contentResolver.openOutputStream(output)!!.use { it.write("partial".toByteArray()) }
        java.io.File(activity.cacheDir, "export-${ids[0]}.mp4").writeText("partial")
        activity.getSharedPreferences("download-export", 0).edit().putString("id", ids[0]).putString("destination", output.toString()).putBoolean("pending", true).commit()
        activity.getSharedPreferences("download-restart-test", 0).edit().putString("output", output.toString()).commit()
        compose.runOnUiThread { activity.finishAndRemoveTask() }
    }
    @Test fun restoreTransfersAndOfflineResume() {
        val activity = open(); val app = activity.application as MobiviousApplication
        val marker = activity.getSharedPreferences("download-restart-test", 0)
        assertNotEquals(marker.getInt("pid", 0), android.os.Process.myPid())
        val first = marker.getString("first", "")!!; val second = marker.getString("second", "")!!
        assertEquals("Interrupted", net.wingress.mobivious.downloads.DownloadExportService.state.value.phase)
        until { !java.io.File(activity.cacheDir, "export-$first.mp4").exists() && runCatching { activity.contentResolver.openFileDescriptor(android.net.Uri.parse(marker.getString("output", "")), "r")?.use { it.statSize == 0L } ?: true }.getOrDefault(true) }
        val transfers = JSONObject(marker.getString("transfers", "{}")!!)
        until { app.downloads.records.value.size == 2 && activity.model.browse.value.videos.isNotEmpty() }
        until { app.downloads.records.value.all { record -> record.assets.all { it.status == DownloadStatus.COMPLETE } } }
        app.downloads.records.value.forEach { record -> record.assets.forEach { asset -> assertEquals(transfers.getLong("${record.id}:${asset.choice.key}"), asset.transferId) } }
        assertEquals(4000L, app.downloads.find(first)!!.positionMs)
        assertEquals(0L, app.downloads.find(second)!!.positionMs)
        val before = JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONArray("allRequests").toString()
        compose.runOnUiThread { activity.model.playDownload(first) }
        until { activity.model.playback.value.downloadId == first && !activity.model.playback.value.loading && activity.model.playback.value.playerState == Player.STATE_READY && activity.model.playback.value.position >= 4000 }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.navigate("You", "downloads", rememberOrigin = true) }
        assertEquals(before, JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONArray("allRequests").toString())
        compose.runOnUiThread { activity.model.deleteDownload(first) }
        until { app.downloads.find(first) == null && activity.model.queue.value.current == null }
        assertTrue(app.downloads.find(second)!!.playable)
        runBlocking { app.downloads.delete(second) }
        marker.edit().clear().commit()
        compose.runOnUiThread { activity.finishAndRemoveTask() }
    }
}
