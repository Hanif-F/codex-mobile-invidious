package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.AccountPreferences
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Uses the disposable localhost fixture, never a real account. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WatchedIndicatorsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val first = "testvideo01"
    private val second = "testvideo02"
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.clearPositions(); activity.model.store.save(null)
            activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.clearPositions(); activity.model.store.guestDeArrow(AccountPreferences())
            activity.model.refreshSharedSettings(); activity.model.navigate("Popular")
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.clearPositions(); activity.model.store.save(null)
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.store.pip = true
            activity.finishAndRemoveTask()
        }
    }
    private fun login() {
        compose.runOnUiThread { activity.model.action { activity.model.login("IndicatorViewer", "fixture-password") } }
        until { activity.model.account.value != null && activity.model.preferences.value.savePosition && !activity.model.watched.value.loading && activity.model.tab == "You" }
        navigate("Popular")
    }
    private fun navigate(tab: String, route: String = "") {
        compose.runOnUiThread { activity.model.query = "fixture"; activity.model.navigate(tab, route) }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
    }
    private fun card(id: String) {
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("video-card-$id"))
    }
    private fun watched(id: String) = compose.onNodeWithTag("video-watched-$id", useUnmergedTree = true)
    private fun progress(id: String) = compose.onNodeWithTag("video-progress-$id", useUnmergedTree = true)
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val mode = if (activity.model.preferences.value.darkMode == "dark") "dark" else "light"
        listOf("mkdir -p /data/local/tmp/mobivious-watched-screenshots",
            "screencap -p /data/local/tmp/mobivious-watched-screenshots/$name-$mode.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    @Test fun sharedCardsCoverListsAndCompactThumbnailFreeLayouts() {
        command("watched", """{"indicatorVideos":true,"seedPlaylist":true,"watched":["$first","$second"],"positions":{"$first":40}}""")
        login(); until { first in activity.model.watched.value.watched }
        listOf("Popular" to "", "Search" to "", "Subscriptions" to "", "Popular" to "channel:${"UC" + "a".repeat(22)}",
            "You" to "playlist:IVfixture", "You" to "history").forEach { (tab, route) ->
            navigate(tab, route); card(first); watched(first).assertExists(); progress(first).assertExists()
            assertEquals(1f / 3, progress(first).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .01f)
            compose.onNodeWithTag("video-card-$first").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "In watch history. Playback progress 33 percent"))
        }
        navigate("Popular")
        listOf("light", "dark").forEach { mode ->
            compose.runOnUiThread { activity.model.preferences.value = activity.model.preferences.value.copy(darkMode = mode, uiDensity = "compact", thinMode = false) }
            card(first); watched(first).assertExists(); progress(first).assertExists(); screenshot("compact")
            compose.runOnUiThread { activity.model.preferences.value = activity.model.preferences.value.copy(thinMode = true) }
            card(first); watched(first).assertExists(); progress(first).assertTextEquals("Progress: 33%"); screenshot("thin")
        }
        compose.onAllNodesWithText("Mark watched").assertCountEquals(0)
        compose.onAllNodesWithText("Mark unwatched").assertCountEquals(0)
        compose.runOnUiThread { activity.model.preferences.value = activity.model.preferences.value.copy(thinMode = false) }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.details != null }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("video-card-$second"))
        compose.onNode(hasTestTag("video-watched-$second") and hasAnyAncestor(hasTestTag("watch-details-list")), true).assertExists()
        compose.onNode(hasTestTag("video-progress-$second") and hasAnyAncestor(hasTestTag("watch-details-list")), true).assertExists(); screenshot("recommendations")
    }

    @Test fun guestsUseLocalProgressOnlyWhenResumeIsEnabled() {
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(savePosition = true))
            activity.model.store.position(first, 35); activity.model.refreshSharedSettings()
            val guest = activity.model.api.context()
            val signed = guest.copy(account = net.wingress.mobivious.data.Account("fixture-token", "OtherViewer", Long.MAX_VALUE, guest.server))
            try { activity.model.store.setPosition(signed, first, 90); fail("Inactive account accepted a device save") }
            catch (_: kotlinx.coroutines.CancellationException) { }
            assertEquals(35L, activity.model.store.position(first, guest))
            assertEquals(0L, activity.model.store.position(first, signed))
            assertEquals(0L, activity.model.store.position(first, guest.copy(server = "https://other.test")))
        }
        until { activity.model.watched.value.positions[first] == 35L }
        progress(first).assertExists(); watched(first).assertDoesNotExist()
        assertEquals(0, fixture().getInt("playbackRequests"))
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(savePosition = false)); activity.model.refreshSharedSettings()
        }
        until { activity.model.watched.value.positions.isEmpty() }
        progress(first).assertDoesNotExist(); watched(first).assertDoesNotExist()
        assertEquals(0, fixture().getInt("playbackRequests"))
    }

    @Test fun unknownDurationAndLiveCardsKeepBadgesWithoutPartialBars() {
        command("watched", """{"indicatorVideos":true,"watched":["$first","$second","unknownvid1","streamvid01"],"positions":{"$first":40,"unknownvid1":10,"streamvid01":30}}""")
        login(); until { activity.model.watched.value.watched.size == 4 }
        navigate("Popular")
        card(second); watched(second).assertExists()
        assertEquals(ProgressBarRangeInfo(1f, 0f..1f), progress(second).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo])
        listOf("unknownvid1", "streamvid01").forEach { id -> card(id); watched(id).assertExists(); progress(id).assertDoesNotExist() }
    }

    @Test fun historyRemovalPreservesProgressAndClearRemovesBoth() {
        command("watched", """{"watched":["$first","$second"],"positions":{"$first":40}}""")
        login(); until { first in activity.model.watched.value.watched }
        navigate("You", "history"); card(first)
        compose.onNodeWithContentDescription("Remove A quiet moment · playback fixture").performClick()
        until { first !in activity.model.watched.value.watched && !activity.model.browse.value.loading }
        assertEquals(40L, activity.model.watched.value.positions[first])
        navigate("Popular"); watched(first).assertDoesNotExist(); progress(first).assertExists()
        navigate("You", "history")
        compose.onNodeWithTag("history-actions").performClick()
        compose.onNodeWithText("Clear watch history").performClick(); compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        until { activity.model.watched.value.watched.isEmpty() && activity.model.watched.value.positions.isEmpty() }
        assertTrue(fixture().getJSONObject("positions").length() == 0)
        navigate("Popular"); watched(first).assertDoesNotExist(); progress(first).assertDoesNotExist()
    }

    @Test fun playbackUpdatesInBackgroundAndCompletionKeepsTheWatchedBadge() {
        login()
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.playing && first in activity.model.watched.value.watched }
        compose.runOnUiThread { activity.model.store.background = true; activity.model.store.pip = false; activity.model.audioOnly(true); activity.model.controller.value!!.seekTo(45_000) }
        until { (activity.model.watched.value.positions[first] ?: 0) >= 44 }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")).use { it.readBytes() }
        until { (activity.model.watched.value.positions[first] ?: 0) >= 55 }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -n ${activity.packageName}/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task")).use { it.readBytes() }
        compose.runOnUiThread { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        until { activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && activity.window.decorView.hasWindowFocus() && activity.model.playback.value.playing }
        compose.runOnUiThread { activity.model.controller.value!!.seekTo(119_000) }
        until { first !in activity.model.watched.value.positions }
        compose.navigateBack()
        watched(first).assertExists()
        assertEquals(1f, progress(first).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
        screenshot("completed")
        val events = fixture().getJSONArray("events")
        assertEquals(1, (0 until events.length()).count { events.getJSONObject(it).optString("method") == "POST" && events.getJSONObject(it).optString("path") == "/api/v1/auth/history/$first" })
    }

    @Test fun refreshFailureRetainsStateAndSignOutHidesAccountIndicators() {
        command("watched", """{"watched":["$first"],"positions":{"$first":40}}""")
        login(); until { first in activity.model.watched.value.watched }
        command("watched", """{"failPlayback":true}""")
        compose.runOnUiThread { activity.model.refresh() }
        until { activity.model.watched.value.error != null }
        watched(first).assertExists(); progress(first).assertExists()
        compose.runOnUiThread { activity.model.logout() }
        until { activity.model.account.value == null && !activity.model.browse.value.loading }
        watched(first).assertDoesNotExist(); progress(first).assertDoesNotExist()
    }
}
