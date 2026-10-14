package net.wingress.mobivious

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ChannelSpoilersSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val app get() = activity.application as MobiviousApplication
    private val channel = "UC" + "a".repeat(22)
    private val other = "UC" + "c".repeat(22)
    private val original = "A quiet moment · playback fixture"
    private val replacement = "A calm scene"
    private val rule = ChannelSpoilerRule("Mobivious Studio", true, true)
    private fun until(timeout: Long = 30_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)
    private fun command(path: String, body: JSONObject = JSONObject()) {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestPreferences(AccountPreferences()); activity.model.refreshSharedSettings(); activity.model.navigate("Popular")
        }
        until { activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun cleanup() {
        if (::activity.isInitialized) {
            compose.runOnUiThread { activity.model.closePlayer(); activity.model.store.save(null); activity.model.store.guestPreferences(AccountPreferences()); activity.model.refreshSharedSettings() }
            runBlocking { app.downloads.records.value.forEach { app.downloads.delete(it.id) } }
            compose.runOnUiThread { activity.finishAndRemoveTask() }
        }
    }
    private fun guest(prefs: AccountPreferences) {
        compose.runOnUiThread { activity.model.store.guestPreferences(prefs); activity.model.refreshSharedSettings() }
        until { activity.model.channelSpoilers.state.value.rules == prefs.channelSpoilers }
    }
    private fun login() {
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.channelSpoilers.state.value.ready }
        compose.runOnUiThread { activity.model.navigate("Popular") }
        until { !activity.model.browse.value.loading }
    }
    private fun settings() { compose.openAppSettings(); compose.onNodeWithText(channelSpoilerSettingsPage).performScrollTo().performClick() }
    private fun configure() {
        compose.onNodeWithTag("spoiler-channel-input").performTextInput(channel)
        compose.onNodeWithText("Configure channel").performClick()
        until { compose.onAllNodesWithTag("spoiler-hide-title").fetchSemanticsNodes().isNotEmpty() }
        until { compose.onAllNodes(hasTestTag("spoiler-hide-title") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun returnToFeed() {
        compose.onNodeWithContentDescription("Close channel title and thumbnail settings").performClick()
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        compose.selectFeed("Popular")
    }
    private fun controllerTitle(): String? {
        var title: String? = null; compose.runOnUiThread { title = activity.model.controller.value?.mediaMetadata?.title?.toString() }; return title
    }

    @Test fun guestEditorValidationPersistenceAndThumbnailFirstTap() {
        settings(); configure()
        compose.onNodeWithTag("spoiler-hide-title").performClick()
        compose.onNodeWithTag("spoiler-hide-thumbnail").performClick()
        until { activity.model.preferences.value.channelSpoilers[channel]?.hideThumbnail == true }
        compose.onNodeWithTag("spoiler-reveal-days").performTextInput("0")
        compose.onNodeWithText("Enter a whole number from 1 to 2147483647, or leave blank.").assertExists()
        assertNull(activity.model.store.guestPreferences().channelSpoilers[channel]?.revealAfterDays)
        compose.onNodeWithTag("spoiler-reveal-days").performTextClearance()
        compose.onNodeWithTag("spoiler-reveal-days").performImeAction()
        until { activity.model.preferenceSaves.edits.value.isEmpty() }
        captureWatchScreenshot("spoiler-settings")
        returnToFeed()
        compose.onNodeWithText(original).assertDoesNotExist()
        compose.onNodeWithTag("spoiler-thumbnail-testvideo01").assertExists()
        captureWatchScreenshot("spoiler-hidden-feed")
        compose.onNodeWithTag("spoiler-thumbnail-testvideo01").performTouchInput { click() }
        compose.onNodeWithTag("spoiler-thumbnail-testvideo01").assertDoesNotExist()
        assertNull(activity.model.playback.value.details)
        compose.runOnUiThread { activity.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== activity } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        until { compose.onAllNodesWithTag("spoiler-thumbnail-testvideo01").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(activity.model.store.guestPreferences().channelSpoilers[channel]!!.hideTitle)
        val events = state().getJSONArray("events")
        assertFalse((0 until events.length()).any { events.getJSONObject(it).optString("method") == "PATCH" && events.getJSONObject(it).optString("path") == "/api/v1/auth/preferences" })
    }
    @Test fun accountSparseSaveFailureRetryAndResetPreserveOtherRules() {
        command("spoilers", JSONObject().put("rules", ChannelSpoilerRules.json(mapOf(other to rule.copy(name = "Other")))))
        login(); settings(); configure()
        command("sponsorblock", JSONObject().put("failPreferences", true))
        compose.onNodeWithTag("spoiler-hide-title").performClick()
        until { activity.model.preferenceSaves.edits.value.values.any { it.status == PreferenceSaveStatus.FAILED } }
        assertFalse(activity.model.preferences.value.channelSpoilers.containsKey(channel))
        command("sponsorblock", JSONObject().put("failPreferences", false))
        compose.onNodeWithText("Retry").performClick()
        until { activity.model.preferences.value.channelSpoilers[channel]?.hideTitle == true }
        assertTrue(activity.model.preferences.value.channelSpoilers.containsKey(other))
        val preferences = state().getJSONObject("preferences")
        assertEquals("preserved", preferences.getString("unrelated_setting"))
        val patches = state().getJSONArray("spoilerPatches")
        val body = patches.getJSONObject(patches.length() - 1)
        assertEquals(setOf(channel), body.keys().asSequence().toSet())
        assertFalse(body.getJSONObject(channel).has("name"))
        compose.onNodeWithText("Reset").performClick()
        until { !activity.model.preferences.value.channelSpoilers.containsKey(channel) }
        assertTrue(activity.model.preferences.value.channelSpoilers.containsKey(other))
    }
    @Test fun delayedDeArrowCyclesAndPlaybackNotificationStayMasked() {
        command("spoilers", JSONObject().put("titleDelay", 1200))
        guest(AccountPreferences(dearrowEnabled = true, dearrowShowOriginal = false, channelSpoilers = mapOf(channel to rule)))
        compose.onNodeWithTag("spoiler-title-reveal-testvideo01").performClick()
        compose.onNodeWithText(original).assertDoesNotExist()
        until { compose.onAllNodesWithText(replacement).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("spoiler-title-reveal-testvideo01").performClick(); compose.onNodeWithText(original).assertExists()
        compose.onNodeWithTag("spoiler-title-reveal-testvideo01").performClick(); compose.onNodeWithText(original).assertDoesNotExist()
        compose.runOnUiThread { activity.model.playVideo(activity.model.browse.value.videos.first()) }
        until(60_000) { activity.model.playback.value.playing && controllerTitle()?.startsWith("Title hidden") == true }
        compose.runOnUiThread { assertNull(activity.model.controller.value!!.mediaMetadata.artworkUri) }
        compose.onNodeWithTag("mini-player-preview").performClick()
        until { compose.onAllNodesWithTag("watch-details-list").fetchSemanticsNodes().isNotEmpty() }
        captureWatchScreenshot("spoiler-hidden-watch")
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("spoiler-title-reveal-testvideo01"))
        compose.onNode(hasTestTag("spoiler-title-reveal-testvideo01") and hasAnyAncestor(hasTestTag("watch-details-list"))).performClick()
        until { controllerTitle() == replacement }
        val occurrence = activity.model.queue.value.currentKey
        compose.runOnUiThread { activity.model.channelSpoilers.revealTitle(occurrence!!, SpoilerTitleMode.HIDDEN) }
        until { controllerTitle()?.startsWith("Title hidden") == true }
        assertTrue(activity.model.playback.value.playing)
    }
    @Test fun timedRulesUseCachedUploadAgeAndRetainUnknownMisses() {
        val old = System.currentTimeMillis() / 1000 - 8 * 86_400
        command("spoilers", JSONObject().put("dates", JSONObject().put("testvideo01", old)))
        guest(AccountPreferences(channelSpoilers = mapOf(channel to rule.copy(revealAfterDays = 7))))
        until { compose.onAllNodesWithText(original).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(state().getJSONArray("spoilerRequests").length() > 0)
        compose.onNodeWithTag("spoiler-thumbnail-testvideo01").assertDoesNotExist()
        command("spoilers", JSONObject().put("dates", JSONObject()))
        compose.runOnUiThread { activity.model.refresh() }
        until { compose.onAllNodesWithTag("spoiler-thumbnail-testvideo01").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(original).assertDoesNotExist()
        assertTrue(activity.model.channelSpoilers.state.value.decision(activity.model.browse.value.videos.first()).unknown)
        val calls = state().getJSONArray("spoilerRequests").length()
        compose.waitForIdle(); assertEquals(calls, state().getJSONArray("spoilerRequests").length())
    }
    @Test fun duplicateOccurrencesRevealIndependently() {
        guest(AccountPreferences(channelSpoilers = mapOf(channel to rule)))
        val vm = activity.model
        val source = vm.browse.value.videos.first()
        compose.runOnUiThread { activity.setContent { AppTheme(false, AccentPreset.BLUE) {
            Column { repeat(2) { index ->
                val video = source.copy(indexId = index.toString())
                DeArrowTitle(vm, video, MaterialTheme.typography.titleMedium)
                ChannelSpoilerArtwork(vm, video, Modifier.size(200.dp, 90.dp)) { }
            } }
        } } }
        compose.onAllNodesWithContentDescription("Reveal title")[0].performClick()
        compose.onAllNodesWithText(original).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Reveal title").assertCountEquals(1)
        compose.onAllNodesWithTag("spoiler-thumbnail-testvideo01")[0].performTouchInput { click() }
        compose.onAllNodesWithTag("spoiler-thumbnail-testvideo01").assertCountEquals(1)
    }
    @Test fun channelThumbnailTakesPrecedenceOverAiWarningAndExplicitRevealShowsOriginal() {
        command("ai", JSONObject().put("aiMatches", JSONObject().put(channel, org.json.JSONArray(listOf("blocklist")))))
        val ai = AiFilterSettings(enabled = true).withAction(AiListKind.BLOCKLIST, AiPageGroup.FEEDS, AiAction.REPLACE)
        guest(AccountPreferences(aiFilter = ai, channelSpoilers = mapOf(channel to rule)))
        until { activity.model.aiFilter.state.value.matches[channel]?.contains(AiListKind.BLOCKLIST) == true }
        compose.onNodeWithTag("spoiler-thumbnail-testvideo01").assertExists()
        compose.onAllNodes(hasTestTag("ai-thumbnail-blocklist") and hasAnyAncestor(hasTestTag("video-card-testvideo01"))).assertCountEquals(0)
        compose.onNodeWithTag("spoiler-thumbnail-testvideo01").performTouchInput { click() }
        compose.onNodeWithTag("spoiler-thumbnail-testvideo01").assertDoesNotExist()
        compose.onAllNodes(hasTestTag("ai-thumbnail-blocklist") and hasAnyAncestor(hasTestTag("video-card-testvideo01"))).assertCountEquals(0)
        assertNull(activity.model.playback.value.details)
    }
    @Test fun accountPreferenceReadFailureKeepsSafePlaceholdersAndCanRetry() {
        command("spoilers", JSONObject().put("rules", ChannelSpoilerRules.json(mapOf(channel to rule))).put("failRead", true))
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.spoilerSettingsError.value != null }
        assertFalse(activity.model.channelSpoilers.state.value.ready)
        compose.onNodeWithText(original).assertDoesNotExist()
        compose.runOnUiThread { activity.model.playVideo(Video("testvideo01", original, channelId = channel)) }
        until(60_000) { activity.model.playback.value.playing }
        assertFalse(activity.model.channelSpoilers.state.value.ready)
        assertEquals("Loading channel settings…", controllerTitle())
        compose.runOnUiThread { activity.model.closePlayer() }
        settings()
        compose.onNodeWithText("Retry loading settings").assertExists()
        command("spoilers", JSONObject().put("failRead", false))
        compose.onNodeWithText("Retry loading settings").performClick()
        until { activity.model.channelSpoilers.state.value.ready }
        assertTrue(activity.model.channelSpoilers.state.value.rules.containsKey(channel))
    }
    @Test fun clipsAndOfflineDownloadsRespectSourceChannelRules() {
        guest(AccountPreferences(channelSpoilers = mapOf(channel to rule)))
        command("clips", JSONObject().put("count", 1))
        compose.runOnUiThread { activity.model.navigate("Popular", "channel:$channel") }
        until { activity.model.channel.value != null }
        compose.onNodeWithTag("channel-tab-clips").performClick()
        until { compose.onAllNodesWithTag("spoiler-thumbnail-testvideo01").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("A memorable moment").assertDoesNotExist()
        val record = runBlocking { val catalog = app.api.downloads("testvideo01"); app.downloads.create(app.store.server, catalog, DownloadSelection(video = "v360")) }
        until(60_000) { app.downloads.find(record)?.playable == true }
        compose.runOnUiThread { activity.model.navigate("You", "downloads") }
        until { compose.onAllNodesWithTag("download-entry-$record").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(original).assertDoesNotExist()
        captureWatchScreenshot("spoiler-hidden-download")
        compose.runOnUiThread { activity.model.playDownload(record) }
        until(60_000) { activity.model.playback.value.playerState == Player.STATE_READY && controllerTitle()?.startsWith("Title hidden") == true }
        compose.runOnUiThread { assertNull(activity.model.controller.value!!.mediaMetadata.artworkUri) }
    }
}
