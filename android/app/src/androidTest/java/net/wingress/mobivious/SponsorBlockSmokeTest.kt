package net.wingress.mobivious

import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackService
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Real Media3 playback against generated media and a disposable localhost API. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SponsorBlockSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val title = "A quiet moment · playback fixture"
    private val id = "UC" + "a".repeat(22)
    private fun until(timeout: Long = 20_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings(); activity.model.navigate("Popular")
            activity.model.store.background = true
        }
        until { activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask()
        }
    }
    private fun openVideo() {
        compose.onNodeWithText(title).performClick()
        until(40_000) { activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.controller.value!!.pause() }
    }
    private fun guestSettings(settings: SponsorBlockSettings) {
        compose.runOnUiThread { activity.model.store.guestDeArrow(AccountPreferences(sponsorBlock = settings)); activity.model.refreshSharedSettings() }
    }
    private fun seek(position: Long) { compose.runOnUiThread { activity.model.seekTo(position) } }
    private fun saveSheet() { compose.onNode(hasText("Save") and hasAnyAncestor(hasTestTag("sponsorblock-sheet"))).performClick(); until { compose.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /data/local/tmp/mobivious-sponsorblock-screenshots").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { s -> s.readBytes() } }
        automation.executeShellCommand("screencap -p /data/local/tmp/mobivious-sponsorblock-screenshots/$name.png").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { s -> s.readBytes() } }
    }

    @Test fun guestSheetColorValidationPersistenceAndMarkers() {
        command("sponsorblock", """{"sponsorSegments":[{"id":"a","category":"sponsor","start":10,"end":20}]}""")
        openVideo(); assertEquals(0, fixture().getInt("sponsorRequests"))
        compose.openAppSettings(); compose.onNodeWithText("SponsorBlock").performClick()
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("sponsorblock-sheet"))).performClick()
        val color = compose.onNode(hasSetTextAction() and hasText("Sponsor color (#RRGGBB)"))
        color.performScrollTo().performTextReplacement("bad")
        compose.onNode(hasText("Save") and hasAnyAncestor(hasTestTag("sponsorblock-sheet"))).assertIsNotEnabled()
        color.performTextReplacement("#123456")
        screenshot("guest-colors")
        saveSheet()
        assertTrue(activity.model.store.guestDeArrow().sponsorBlock.enabled)
        // Fullscreen SponsorBlock returns to the Settings screen.
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        compose.selectDiscoveryFeed("Popular")
        compose.onNodeWithTag("mini-player-preview").performClick()
        until { activity.model.sponsorBlock.value.segments.size == 1 }
        assertFalse(fixture().getBoolean("sponsorAuthorized"))
        compose.runOnUiThread { activity.model.refreshSharedSettings() }
        assertEquals("#123456", activity.model.store.guestDeArrow().sponsorBlock.colors[SponsorBlockCategory.SPONSOR])
        seek(12_000)
        compose.onNodeWithTag("sponsorblock-prompt").assertExists(); screenshot("portrait-manual")
        compose.onNodeWithTag("player-timeline").assertExists()
    }

    @Test fun manualDismissAutoOverlapReplayFullscreenAndRefresh() {
        command("sponsorblock", """{"sponsorSegments":[
            {"id":"manual","category":"sponsor","start":10,"end":20},
            {"id":"intro-a","category":"intro","start":30,"end":40},
            {"id":"intro-b","category":"intro","start":40,"end":50},
            {"id":"marker","category":"outro","start":55,"end":60},
            {"id":"hidden","category":"filler","start":0,"end":90}]}""")
        guestSettings(SponsorBlockSettings(enabled = true).let { it.copy(modes = it.modes + mapOf(SponsorBlockCategory.INTRO to SponsorBlockMode.AUTO,
            SponsorBlockCategory.OUTRO to SponsorBlockMode.MARKER, SponsorBlockCategory.FILLER to SponsorBlockMode.DISABLED)) })
        openVideo(); until { activity.model.sponsorBlock.value.segments.size == 4 }
        seek(12_000); until { activity.model.sponsorBlock.value.active?.id == "manual" }
        compose.onNodeWithContentDescription("Dismiss SponsorBlock segment").performClick()
        until { activity.model.sponsorBlock.value.active == null }
        seek(25_000); seek(12_000); until { activity.model.sponsorBlock.value.active?.id == "manual" }
        compose.onNodeWithText("Skip").performClick(); until { activity.model.playback.value.position >= 20_000 }
        seek(32_000); until { activity.model.playback.value.position >= 50_000 }
        compose.runOnUiThread { assertFalse(activity.model.controller.value!!.playWhenReady) }
        compose.onNodeWithTag("sponsorblock-notice").assertExists()
        seek(32_000); until { activity.model.sponsorBlock.value.active?.id == "intro-a" }
        compose.runOnUiThread { activity.model.refreshBuffer() }
        until(40_000) { activity.model.playback.value.canRefresh && activity.model.sponsorBlock.value.active?.id == "intro-a" }
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { compose.onAllNodesWithContentDescription("Exit full screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sponsorblock-prompt").assertExists(); screenshot("fullscreen-replay")
        compose.onNodeWithText("Skip").performClick()
        until { activity.model.sponsorBlock.value.active?.id == "intro-b" }
        seek(57_000); until { activity.model.sponsorBlock.value.active == null }
        assertEquals(1, fixture().getInt("sponsorRequests"))
    }

    @Test fun sharedChannelOverridesFailedDraftAndReset() {
        command("sponsorblock", """{"sponsorblock_enabled":true,"sponsorblock_channel_overrides":{"$id":{"name":"Mobivious Studio","enabled":false,"modes":{}}},"sponsorSegments":[{"id":"a","category":"sponsor","start":10,"end":20}]}""")
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.preferences.value.sponsorBlock.channels.containsKey(id) && activity.model.tab == "You" }
        compose.selectDiscoveryFeed("Popular")
        openVideo(); assertEquals(0, fixture().getInt("sponsorRequests"))
        compose.onNodeWithText("Channel SponsorBlock settings").assertDoesNotExist()
        compose.onAllNodes(hasText("Mobivious Studio") and hasClickAction() and hasAnyAncestor(hasTestTag("watch-details-list"))).onFirst().performScrollTo().performClick()
        until { activity.model.channel.value?.id == id }
        compose.onNodeWithTag("channel-actions-$id").performScrollTo().performClick()
        compose.onNodeWithText("Channel SponsorBlock settings").performClick()
        compose.onNodeWithTag("sponsor-channel-enabled").assert(hasText("Disabled")).performClick()
        compose.onNodeWithText("Enabled").performClick()
        command("sponsorblock", """{"failPreferences":true}""")
        compose.onNode(hasText("Save") and hasAnyAncestor(hasTestTag("sponsorblock-sheet"))).performClick()
        until { compose.onAllNodesWithTag("sponsorblock-save-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sponsor-channel-enabled").assert(hasText("Enabled"))
        assertFalse(activity.model.preferences.value.sponsorBlock.channels[id]!!.enabled!!)
        command("sponsorblock", """{"failPreferences":false}""")
        compose.onNode(hasText("Save") and hasAnyAncestor(hasTestTag("sponsorblock-sheet"))).performClick()
        until { activity.model.preferences.value.sponsorBlock.channels[id]?.enabled == true }
        until { activity.model.sponsorBlock.value.segments.size == 1 }
        compose.onNode(hasText("Mobivious Studio") and hasAnyAncestor(hasTestTag("sponsorblock-sheet"))).performClick()
        compose.onNodeWithText("Reset to global").performClick()
        until { id !in activity.model.preferences.value.sponsorBlock.channels }
        screenshot("channel-manager")
        compose.runOnUiThread { activity.model.store.save(null) }
        until { activity.model.sponsorSettingsChannel.value == null && activity.model.sponsorBlock.value.active == null }
    }

    @Test fun serviceBackgroundAudioPipAndStaleCommands() {
        command("sponsorblock", """{"sponsorSegments":[{"id":"a","category":"sponsor","start":10,"end":20}]}""")
        guestSettings(SponsorBlockSettings(enabled = true).let { it.copy(modes = it.modes + (SponsorBlockCategory.SPONSOR to SponsorBlockMode.AUTO)) })
        openVideo(); until { activity.model.sponsorBlock.value.segments.isNotEmpty() }
        val old = activity.model.sponsorBlock.value
        compose.runOnUiThread {
            activity.model.audioOnly(true); activity.model.controller.value!!.seekTo(9500); activity.model.controller.value!!.play()
            activity.model.store.pip = false; activity.moveTaskToBack(true)
        }
        until { activity.model.playback.value.position >= 20_000 }
        until { !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("am start --windowingMode 1 -n ${activity.packageName}/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        compose.runOnUiThread { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        until { activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(11_000); activity.model.store.pip = true; activity.enterPip() }
        until { activity.isInPictureInPictureMode }
        compose.runOnUiThread { assertNotNull(activity.model.sponsorBlock.value.active) }
        automation.waitForIdle(500, 5_000)
        automation.executeShellCommand("am start --windowingMode 1 -n ${activity.packageName}/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        compose.runOnUiThread { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        until { !activity.isInPictureInPictureMode }
        compose.runOnUiThread { activity.model.closePlayer(); activity.model.play("testvideo01", 0) }
        until(40_000) { activity.model.sponsorBlock.value.token.isNotEmpty() && activity.model.sponsorBlock.value.token != old.token }
        lateinit var reply: com.google.common.util.concurrent.ListenableFuture<SessionResult>
        compose.runOnUiThread { reply = activity.model.controller.value!!.sendCustomCommand(SessionCommand(PlaybackService.SPONSOR_SKIP, Bundle.EMPTY), Bundle().apply { putString("token", old.token); putString("mediaId", old.mediaId); putString("segment", "a") }) }
        assertNotEquals(SessionResult.RESULT_SUCCESS, reply.get().resultCode)
    }

    @Test fun failureAndLivePlaybackStayUsableWithoutPrompts() {
        command("sponsorblock", """{"failSponsor":true}""")
        guestSettings(SponsorBlockSettings(enabled = true))
        openVideo(); until { fixture().getInt("sponsorRequests") == 1 }
        assertTrue(activity.model.sponsorBlock.value.segments.isEmpty()); assertNull(activity.model.playback.value.error)
        compose.runOnUiThread { activity.model.closePlayer() }
        guestSettings(SponsorBlockSettings(enabled = true, modes = SponsorBlockCategory.entries.associateWith { SponsorBlockMode.DISABLED }))
        compose.runOnUiThread { activity.model.play("testvideo01", 0) }
        until(40_000) { activity.model.playback.value.playing }
        assertEquals(1, fixture().getInt("sponsorRequests"))
        compose.runOnUiThread { activity.model.closePlayer() }
        guestSettings(SponsorBlockSettings(enabled = true))
        command("sponsorblock", """{"liveNow":true,"failSponsor":false}""")
        compose.runOnUiThread { activity.model.play("testvideo01", 0) }
        until(40_000) { activity.model.playback.value.playing }
        assertEquals(1, fixture().getInt("sponsorRequests")); assertTrue(activity.model.sponsorBlock.value.segments.isEmpty())
    }
}
