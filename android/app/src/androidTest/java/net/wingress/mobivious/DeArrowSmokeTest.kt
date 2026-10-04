package net.wingress.mobivious

import android.content.Intent
import androidx.media3.common.C
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.AccountPreferences
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Uses only the disposable localhost fixture; never sends real contributions. */
@RunWith(AndroidJUnit4::class)
class DeArrowSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    private val original = "A quiet moment · playback fixture"
    private val replacement = "A calm scene"
    private fun until(timeout: Long = 20_000, condition: () -> Boolean) = compose.waitUntil(timeout, condition)
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
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
            activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings()
        }
        until { activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread { activity.model.closePlayer(); activity.model.store.save(null); activity.model.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask() }
    }
    private fun toggle(label: String) { compose.onNodeWithText(label).performScrollTo().performClick() }
    private fun settings() { compose.onNodeWithContentDescription("App settings").performClick(); compose.onNodeWithText("DeArrow").performScrollTo().performClick() }
    private fun waitForReplacement() { until { compose.onAllNodesWithText(replacement).fetchSemanticsNodes().isNotEmpty() } }
    private fun save() { compose.onNodeWithTag("settings-save").performClick(); until { compose.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithContentDescription("Back from Settings").performClick() }
    private fun login() {
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null }
    }
    private fun openVideo(title: String) { compose.onNodeWithText(title).performClick(); until(40_000) { activity.model.playback.value.playing } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /data/local/tmp/mobivious-dearrow-screenshots").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        automation.executeShellCommand("screencap -p /data/local/tmp/mobivious-dearrow-screenshots/$name.png").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
    }

    @Test fun guestSettingsAndOriginalTitleControls() {
        assertEquals(0, state().getJSONObject("titleLookups").length())
        settings(); toggle("Replace video titles with DeArrow"); save()
        until { compose.onAllNodesWithText(replacement).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Show original title").performClick()
        compose.onNodeWithText(original).assertExists()
        assertNull(activity.model.playback.value.details)
        compose.onNodeWithContentDescription("Show DeArrow title").performClick()
        compose.onNodeWithText(replacement).assertExists()
        settings(); toggle("Show original titles using the DeArrow icon"); save()
        compose.onNodeWithContentDescription("Show original title").assertDoesNotExist()
        compose.onNodeWithText(replacement).assertExists()
        compose.runOnUiThread { activity.recreate() }
        until { var resumed = false; compose.runOnUiThread { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== activity } }; resumed }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        until { compose.onAllNodesWithText(replacement).fetchSemanticsNodes().isNotEmpty() }
        // Refresh settings from the persisted guest store without any account PATCH.
        val events = state().getJSONArray("events")
        assertFalse((0 until events.length()).any { events.getJSONObject(it).optString("path") == "/api/v1/auth/preferences" })
    }

    @Test fun sharedTitlesAcrossListsAndMetadataPreservePlayback() {
        command("dearrow", """{"dearrow_enabled":true,"seedPlaylist":true}""")
        login()
        until { compose.onAllNodesWithText(replacement).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Search videos or paste a link").performTextInput("fixture")
        compose.onNode(hasContentDescription("Search") and hasAnyAncestor(hasSetTextAction()), useUnmergedTree = true).performClick()
        until { activity.model.browse.value.videos.isNotEmpty() }; waitForReplacement(); compose.onNodeWithText(replacement).assertExists()
        compose.onNodeWithText("Subscriptions").performClick()
        until { activity.model.browse.value.videos.isNotEmpty() }; waitForReplacement(); compose.onNodeWithText(replacement).assertExists()
        compose.onNodeWithText("Mobivious Studio").performClick()
        until { activity.model.channel.value != null }; waitForReplacement(); compose.onNodeWithText(replacement).assertExists()
        compose.onNodeWithText("Library").performClick()
        until { compose.onAllNodesWithText("DeArrow fixture playlist").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("DeArrow fixture playlist").performClick()
        until { activity.model.playlist.value != null }; waitForReplacement(); compose.onNodeWithText(replacement).assertExists()
        openVideo(replacement)
        until { state().getJSONArray("watched").length() == 1 }
        until { activity.model.controller.value?.mediaMetadata?.title?.toString() == replacement }
        assertEquals(original, activity.model.playback.value.details!!.video.title)
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(30_000); activity.model.speed(1.5f); activity.model.quality(720) }
        val watchedBefore = state().getJSONArray("events").let { events -> (0 until events.length()).count { events.getJSONObject(it).optString("method") == "POST" && events.getJSONObject(it).optString("path") == "/api/v1/auth/history/testvideo01" } }
        settings(); toggle("Replace video titles with DeArrow"); save()
        until { activity.model.controller.value?.mediaMetadata?.title?.toString() == original }
        assertEquals(30_000L, activity.model.playback.value.position); assertEquals(1.5f, activity.model.playback.value.speed)
        assertFalse(activity.model.controller.value!!.playWhenReady)
        settings(); toggle("Replace video titles with DeArrow"); save()
        until { activity.model.controller.value?.mediaMetadata?.title?.toString() == replacement }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasText("Another calm scene"))
        compose.onNodeWithText("Another calm scene").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        until { compose.onAllNodesWithText(replacement).fetchSemanticsNodes().size == 2 } // playlist card and mini-player
        compose.onNodeWithText("Library").performClick(); compose.onNodeWithText("Watch history").performClick()
        until { activity.model.browse.value.videos.isNotEmpty() }
        until { compose.onAllNodesWithText(replacement).fetchSemanticsNodes().size == 2 }
        val watchedAfter = state().getJSONArray("events").let { events -> (0 until events.length()).count { events.getJSONObject(it).optString("method") == "POST" && events.getJSONObject(it).optString("path") == "/api/v1/auth/history/testvideo01" } }
        assertEquals(watchedBefore, watchedAfter)
        val videoOverride = activity.model.controller.value!!.trackSelectionParameters.overrides.values.single { it.type == C.TRACK_TYPE_VIDEO }
        assertEquals(360, videoOverride.mediaTrackGroup.getFormat(videoOverride.trackIndices.single()).height)
    }

    @Test fun contributionsGuidelinesFailuresAndPrivateIdentity() {
        login(); openVideo(original)
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("watch-actions"))
        compose.onNodeWithText("DeArrow Title").performScrollTo().performClick()
        until { activity.model.dearrowContribution.value.loaded }
        compose.onNodeWithTag("dearrow-contributions-list").performScrollToNode(hasContentDescription("Downvote: Locked community title"))
        compose.onNodeWithContentDescription("Downvote: Locked community title").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Upvote: Locked community title").performClick()
        until { state().getJSONArray("contributions").length() == 1 && !activity.model.dearrowContribution.value.busy }
        compose.onNodeWithTag("dearrow-contributions-list").performScrollToNode(hasTestTag("dearrow-draft"))
        compose.onNodeWithTag("dearrow-draft").performTextInput("A thoughtful description")
        screenshot("draft-keyboard")
        compose.onNodeWithTag("dearrow-contributions-list").performScrollToNode(hasText("Review title"))
        compose.onNodeWithText("Review title").performClick()
        compose.onNodeWithTag("dearrow-contributions-list").performScrollToNode(hasText("Submit title"))
        compose.onNodeWithText("Submit title").assertIsNotEnabled()
        val checks = listOf("My title uses sentence capitalization.", "My title does not merely answer the original title’s question.", "My title avoids unnecessary spoilers and conclusions.", "My title does not fact-check, mock or critique the video or creator.")
        checks.forEach { text -> compose.onNodeWithTag("dearrow-contributions-list").performScrollToNode(hasContentDescription(text)); compose.onNodeWithContentDescription(text).performClick() }
        command("dearrow", """{"failContribution":true}""")
        compose.onNodeWithTag("dearrow-contributions-list").performScrollToNode(hasText("Submit title"))
        compose.onNodeWithText("Submit title").performClick()
        until { !activity.model.dearrowContribution.value.busy && activity.model.dearrowContribution.value.status?.contains("did not confirm") == true }
        assertEquals("A thoughtful description", activity.model.dearrowContribution.value.draft)
        assertEquals(1, state().getJSONArray("contributions").length())
        command("dearrow", """{"failContribution":false}""")
        compose.onNodeWithText("Submit title").performClick()
        until { !activity.model.dearrowContribution.value.busy && activity.model.dearrowContribution.value.draft.isEmpty() }
        assertEquals(2, state().getJSONArray("contributions").length())
        screenshot("submissions")
        compose.onNodeWithContentDescription("Close DeArrow contributions").performClick()
        settings()
        compose.onNodeWithTag("dearrow-private-id").performScrollTo().performTextInput("c".repeat(64))
        compose.onNodeWithText("Import private user ID").performScrollTo().performClick()
        until { activity.model.dearrowIdentity.value?.configured == true && compose.onAllNodesWithText("Importing…").fetchSemanticsNodes().isEmpty() }
        assertEquals("", compose.onNodeWithTag("dearrow-private-id").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertFalse(state().toString().contains("c".repeat(64)))
        compose.onNodeWithContentDescription("Back from DeArrow").performClick(); compose.onNodeWithContentDescription("Back from Settings").performClick()
        command("dearrow", """{"originalMode":"missing"}""")
        compose.runOnUiThread { activity.model.openDeArrow("testvideo01"); activity.model.refreshDeArrow() }
        until { !activity.model.dearrowContribution.value.busy && activity.model.dearrowContribution.value.titles.none { it.original } }
        compose.onNodeWithContentDescription("Downvote: $original").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Upvote: $original").assertIsEnabled()
        compose.runOnUiThread { activity.model.store.save(null) }
        until { !activity.model.dearrowContribution.value.open && activity.model.dearrowIdentity.value == null }
    }
}
