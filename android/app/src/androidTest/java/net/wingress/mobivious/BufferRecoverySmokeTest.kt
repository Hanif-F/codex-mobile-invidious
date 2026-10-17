package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.Clip
import net.wingress.mobivious.player.AudioSection
import net.wingress.mobivious.player.StreamCatalog
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Real malformed DASH responses, real ExoPlayer errors, and the production recovery path. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BufferRecoverySmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val observer = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var sawRecovery = false
    @Volatile private var prematureError = false
    private fun ui(action: () -> Unit) = compose.runOnUiThread(action)
    private fun until(condition: () -> Boolean) {
        try { compose.waitUntil(45_000, condition) }
        catch (e: Throwable) { throw AssertionError("state=${vm.playback.value.playerState} error=${vm.playback.value.error} recovering=${vm.queue.value.recovering} requests=${fixture().getJSONArray("recoveryManifestRequests")}", e) }
    }
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun ready() = !vm.queue.value.loading && !vm.queue.value.recovering && vm.playback.value.error == null &&
        vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.details != null

    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestPreferences(AccountPreferences()); vm.refreshSharedSettings()
            observer.launch {
                vm.playback.collect {
                    if (it.recovering) { sawRecovery = true; if (it.error != null || !it.buffering) prematureError = true }
                }
            }
        }
        until { vm.controller.value != null && !vm.browse.value.loading }
    }
    @After fun close() {
        observer.cancel()
        if (::activity.isInitialized) ui { vm.closePlayer(); vm.store.guestPreferences(AccountPreferences()); activity.finishAndRemoveTask() }
    }
    private fun startup(autoplay: Boolean) {
        command("recovery", """{"recoveryFailures":1}""")
        ui { vm.store.guestPreferences(AccountPreferences(autoplay = autoplay)); vm.play("testvideo01", 40) }
        until { ready() && fixture().getJSONArray("recoveryManifestRequests").length() >= 2 }
        assertTrue(sawRecovery); assertFalse(prematureError)
        assertEquals(1, fixture().getJSONArray("videoDetailRequests").length())
        val requests = fixture().getJSONArray("recoveryManifestRequests")
        assertEquals(2, requests.length())
        assertTrue(requests.getJSONObject(0).getBoolean("failed")); assertFalse(requests.getJSONObject(1).getBoolean("failed"))
        assertTrue(requests.getJSONObject(1).getDouble("at") - requests.getJSONObject(0).getDouble("at") >= .9)
        ui {
            assertEquals(autoplay, vm.controller.value!!.playWhenReady)
            assertTrue(vm.controller.value!!.currentPosition in 40_000..55_000)
        }
    }
    @Test fun initialFailureAutomaticallyRecoversWithoutMetadataRefetch() = startup(true)
    @Test fun initialPausedFailureRemainsPausedAfterAutomaticRecovery() = startup(false)

    @Test fun httpLoadRetriesFinishBeforeAutomaticBufferRecovery() {
        command("recovery", """{"recoveryFailures":4,"recoveryHttpStatus":503}""")
        ui { vm.play("testvideo01") }
        until { ready() && fixture().getJSONArray("recoveryManifestRequests").length() > 4 }
        assertTrue(sawRecovery); assertFalse(prematureError)
        val requests = fixture().getJSONArray("recoveryManifestRequests")
        assertEquals(5, requests.length())
        for (index in 0..3) assertTrue(requests.getJSONObject(index).getBoolean("failed"))
        assertFalse(requests.getJSONObject(4).getBoolean("failed"))
        assertEquals(1, fixture().getJSONArray("videoDetailRequests").length())
    }

    @Test fun threeFailuresExposeManualControlsAndManualRefreshStartsAFreshBudget() {
        command("recovery", """{"recoveryAlwaysFail":true}""")
        ui { vm.play("testvideo01") }
        until { vm.playback.value.error != null && !vm.queue.value.recovering }
        val requests = fixture().getJSONArray("recoveryManifestRequests")
        assertEquals(4, requests.length()) // Initial preparation plus three buffer refreshes.
        for ((index, delay) in listOf(.9, 1.9, 3.9).withIndex())
            assertTrue(requests.getJSONObject(index + 1).getDouble("at") - requests.getJSONObject(index).getDouble("at") >= delay)
        compose.onNodeWithText("Retry").assertExists()
        assertFalse(prematureError)
        command("recovery", """{"recoveryAlwaysFail":false,"recoveryFailures":1,"clearRequests":true}""")
        ui { vm.refreshBuffer() }
        until { ready() && fixture().getJSONArray("recoveryManifestRequests").length() == 2 }
        assertEquals(1, fixture().getJSONArray("videoDetailRequests").length())
    }

    @Test fun automaticRefreshPreservesExactTracksCaptionsSpeedPositionAndAudioOnly() {
        command("stream", """{"type":"rich"}""")
        ui { vm.play("testvideo01") }; until { ready() && vm.playback.value.playing }
        ui {
            val p = vm.controller.value!!
            p.pause(); vm.seekTo(40_000); vm.speed(1.5f); vm.quality(720); vm.captions("en")
            val audio = StreamCatalog.choices(p.currentTracks, C.TRACK_TYPE_AUDIO, vm.playback.value.details!!.formats)
                .first { it.section == AudioSection.STABLE }
            vm.selectTrack(audio.group, audio.index)
        }
        until { vm.playback.value.tracks.isTypeSelected(C.TRACK_TYPE_TEXT) && vm.playback.value.audioSelection.manual != null &&
            vm.playback.value.selection?.overrides?.values?.any { it.type == C.TRACK_TYPE_VIDEO } == true }
        val video = vm.playback.value.videoSelection
        val audio = vm.playback.value.audioSelection
        ui { vm.audioOnly(true) }
        until { vm.playback.value.selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true }
        command("recovery", """{"recoveryFailures":1,"clearRequests":true}""")
        ui { vm.refreshBuffer() }
        until { ready() && fixture().getJSONArray("recoveryManifestRequests").length() == 2 }
        assertTrue(sawRecovery); assertFalse(prematureError)
        assertEquals(video, vm.playback.value.videoSelection); assertEquals(audio, vm.playback.value.audioSelection)
        ui {
            val p = vm.controller.value!!
            assertFalse(p.playWhenReady); assertEquals(40_000L, p.currentPosition); assertEquals(1.5f, p.playbackParameters.speed)
            assertTrue(p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
            assertEquals("en", p.trackSelectionParameters.preferredTextLanguages.first())
            assertTrue(p.currentTracks.isTypeSelected(C.TRACK_TYPE_TEXT))
        }
        assertEquals(1, fixture().getJSONArray("videoDetailRequests").length())
    }

    @Test fun closeAndVideoReplacementCancelPendingRecovery() {
        command("recovery", """{"recoveryAlwaysFail":true}""")
        ui { vm.play("testvideo01") }; until { vm.queue.value.recovering }
        ui { vm.closePlayer() }; until { vm.queue.value.current == null }
        val before = fixture().getJSONArray("recoveryManifestRequests").length()
        Thread.sleep(1500)
        assertEquals(before, fixture().getJSONArray("recoveryManifestRequests").length())
        ui { vm.play("testvideo01") }; until { vm.queue.value.recovering }
        command("recovery", """{"recoveryAlwaysFail":false}""")
        ui { vm.play("testvideo02") }; until { ready() && vm.playback.value.mediaId == "testvideo02" }
        assertNull(vm.playback.value.error); assertFalse(vm.queue.value.recovering)
    }

    @Test fun liveStartupRecoverySeeksToTheLiveEdge() {
        command("recovery", """{"recoveryFailures":1,"recoveryLive":true}""")
        ui { vm.play("testvideo01") }
        until { ready() && vm.playback.value.live && vm.playback.value.playing }
        assertTrue(sawRecovery); assertFalse(prematureError)
        ui { assertTrue(vm.controller.value!!.currentPosition > 90_000) }
        assertEquals(1, fixture().getJSONArray("videoDetailRequests").length())
    }

    @Test fun clipRecoveryKeepsRelativePositionAndBoundaries() {
        command("clips", """{"count":1}""")
        val data = JSONObject(URL("http://127.0.0.1:18080/api/v1/clips/IVCL00000000000000000000000000000000").readText())
        val clip = Clip.parse(data, "IVCL00000000000000000000000000000000", "http://127.0.0.1:18080")
        command("recovery", """{"recoveryFailures":1}""")
        ui { vm.watchClip(clip) }
        until { ready() && vm.queue.value.current?.clip?.id == clip.id }
        assertTrue(sawRecovery); assertFalse(prematureError)
        ui {
            val p = vm.controller.value!!
            assertEquals(clip.durationMs, p.duration)
            assertTrue(p.currentPosition in 0 until clip.durationMs)
        }
        assertEquals(1, fixture().getJSONArray("videoDetailRequests").length())
    }
}
