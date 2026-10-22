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
import java.net.HttpURLConnection
import java.net.URL

/** The restart runner force-stops the process between these two phases. */
@RunWith(AndroidJUnit4::class)
class ProtectedDownloadsPersistenceSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private fun open(): MainActivity {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("downloadRestart") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    private fun command(path: String, data: String = "{}"): JSONObject {
        val connection = URL("http://127.0.0.1:18080/$path").openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(data.toByteArray()) }
            try { inputStream.bufferedReader().use { JSONObject(it.readText().ifBlank { "{}" }) } } finally { disconnect() }
        }
    }
    @Test fun seedProtectedTransfers() {
        val activity = open(); val app = activity.application as MobiviousApplication
        command("test/reset")
        compose.runOnUiThread { activity.model.switchServer("http://127.0.0.1:18080"); app.store.save(null); activity.model.closePlayer() }
        runBlocking { app.downloads.completion(); app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
        command("test/access", """{"accessEnabled":true}""")
        val pass = command("api/v1/verification/complete", """{"transport":"native","turnstileToken":"fixture-response"}""")
        runBlocking { app.access.complete(app.api.context(), pass.getString("verificationToken"), pass.getLong("expiresAt"), true) }
        command("test/downloads", """{"downloadSlow":true}""")
        val id = runBlocking { val catalog = app.api.downloads("testvideo01"); app.downloads.create(app.store.server, catalog, DownloadSelection("v360", "aes", setOf("cen"))) }
        compose.waitUntil(60_000) { app.downloads.find(id)!!.assets.any { it.status == DownloadStatus.RUNNING && it.bytes > 0 } }
        activity.getSharedPreferences("protected-download-restart", 0).edit().putString("id", id).putInt("pid", android.os.Process.myPid()).commit()
        compose.runOnUiThread { activity.finishAndRemoveTask() }
    }
    @Test fun restoreProtectedTransfersRequireRetryAndRetainOfflinePlayback() {
        val activity = open(); val app = activity.application as MobiviousApplication
        val marker = activity.getSharedPreferences("protected-download-restart", 0)
        assertNotEquals(marker.getInt("pid", 0), android.os.Process.myPid())
        val id = marker.getString("id", "")!!
        runBlocking { app.downloads.completion() }
        val record = app.downloads.find(id)!!
        assertFalse(record.active); assertTrue(record.assets.any { it.status == DownloadStatus.FAILED })
        assertTrue(record.assets.all { it.managed && it.transferId == 0L })
        assertNotNull(app.access.pass(app.api.context()))
        command("test/downloads", """{"downloadSlow":false}""")
        runBlocking { app.downloads.retry(id) }
        compose.waitUntil(60_000) { app.downloads.find(id)?.playable == true }
        command("test/access", """{"accessLogin":true,"accessExpiry":1}""")
        compose.runOnUiThread { activity.model.playDownload(id) }
        compose.waitUntil(60_000) { activity.model.playback.value.downloadId == id && activity.model.playback.value.playerState == Player.STATE_READY }
        command("test/reset")
        compose.runOnUiThread { activity.model.closePlayer(); activity.finishAndRemoveTask() }
        runBlocking { app.downloads.delete(id) }
        marker.edit().clear().commit()
    }
}
