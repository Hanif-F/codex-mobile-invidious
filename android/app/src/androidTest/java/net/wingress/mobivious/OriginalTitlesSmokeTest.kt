package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.net.URL
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Original-title selection is served entirely by the disposable local backend. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OriginalTitlesSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val original = "原題 <日本語> & café"
    private val translated = "A quiet moment · playback fixture"
    private val app get() = activity.application as MobiviousApplication
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Before fun launch() {
        command("reset"); command("original-titles")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestPreferences(AccountPreferences()); activity.model.refreshSharedSettings(); activity.model.navigate("Popular")
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.firstOrNull()?.title == original }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.store.guestPreferences(AccountPreferences())
            activity.finishAndRemoveTask()
        }
    }
    private fun settings() {
        compose.openAppSettings(); compose.onNodeWithText("Browsing").performScrollTo().performClick()
        titleToggle()
    }
    private fun titleToggle(): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Show original title"))
        return compose.onNodeWithText("Show original title")
    }
    private fun leaveSettings() {
        compose.finishAutomaticPreferencePage()
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        compose.selectFeed("Popular")
    }
    private fun save(value: AccountPreferences) {
        compose.runOnUiThread { activity.model.action {
            activity.model.savePreferences(value, activity.model.preferences.value, activity.model.api.context())
        } }
        until { activity.model.preferences.value == LocalPreferences.normalize(value) || activity.model.preferences.value == value }
    }
    private fun recreate() {
        compose.runOnUiThread { activity.recreate() }
        until {
            var resumed = false
            compose.runOnUiThread { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== activity } }
            resumed
        }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    private fun controllerTitle(): String? {
        var title: String? = null
        compose.runOnUiThread { title = activity.model.controller.value?.mediaMetadata?.title?.toString() }
        return title
    }
    private fun login() {
        compose.runOnUiThread { activity.model.action { activity.model.login("Fixture", "transient-password") } }
        until { activity.model.account.value != null && activity.model.dearrowIdentity.value != null && !activity.model.accountBusy.value }
    }

    @Test fun guestTogglePersistsAndReopenedFeedUsesSelectedTitle() {
        compose.onNodeWithText(original).assertExists()
        settings(); titleToggle().assertIsOn().performClick()
        until { !activity.model.preferences.value.showOriginalTitle && activity.model.preferenceSaves.edits.value.isEmpty() }
        assertFalse(SessionStore(activity).guestPreferences().showOriginalTitle)
        leaveSettings()
        until { activity.model.browse.value.videos.firstOrNull()?.title == translated }
        compose.onNodeWithText(translated).assertExists()
        recreate(); settings(); titleToggle().assertIsOff().performClick()
        until { activity.model.preferences.value.showOriginalTitle && activity.model.preferenceSaves.edits.value.isEmpty() }
        leaveSettings(); until { activity.model.browse.value.videos.firstOrNull()?.title == original }
        val events = state().getJSONArray("allRequests")
        assertFalse((0 until events.length()).any { events.getJSONObject(it).optString("path") == "/api/v1/auth/preferences" })
    }

    @Test fun accountChoiceSyncsAndFailedSaveCanRevertAndRetry() {
        command("preferences", """{"show_original_title":false}"""); login()
        until { !activity.model.preferences.value.showOriginalTitle }
        compose.runOnUiThread { activity.model.navigate("Popular") }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.firstOrNull()?.title == translated }
        settings(); command("sponsorblock", """{"failPreferences":true}""")
        titleToggle().assertIsOff().performClick()
        until { activity.model.preferenceSaves.edits.value["show_original_title"]?.error != null }
        assertFalse(activity.model.preferences.value.showOriginalTitle)
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Revert to saved"))
        compose.onNodeWithText("Revert to saved").performClick()
        titleToggle().assertIsOff().performClick()
        until { activity.model.preferenceSaves.edits.value["show_original_title"]?.error != null }
        recreate(); command("sponsorblock", """{"failPreferences":false}""")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Retry"))
        compose.onNodeWithText("Retry").performClick()
        until { activity.model.preferenceSaves.edits.value.isEmpty() && activity.model.preferences.value.showOriginalTitle }
        assertTrue(state().getJSONObject("preferences").getBoolean("show_original_title"))
        assertEquals("preserved", state().getJSONObject("preferences").getString("unrelated_setting"))
        leaveSettings(); until { activity.model.browse.value.videos.firstOrNull()?.title == original }
        command("preferences", """{"show_original_title":false}""")
        compose.runOnUiThread { activity.model.refreshSharedSettings() }
        until { !activity.model.preferences.value.showOriginalTitle }
        assertEquals(original, activity.model.browse.value.videos.first().title)
        compose.runOnUiThread { activity.model.refreshFeed() }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.firstOrNull()?.title == translated }
    }

    @Test fun activePlaybackKeepsTitleUntilRefreshAndPreservesPositionAndSpeed() {
        compose.onNodeWithText(original).performClick()
        until { activity.model.playback.value.playing && controllerTitle() == original }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(30_000); activity.model.speed(1.5f); activity.model.quality(720) }
        until { activity.model.playback.value.position == 30_000L && activity.model.playback.value.speed == 1.5f }
        until { activity.model.playback.value.selection?.overrides?.values?.any { it.type == C.TRACK_TYPE_VIDEO } == true }
        save(activity.model.preferences.value.copy(showOriginalTitle = false))
        assertEquals(original, controllerTitle()); assertEquals(original, activity.model.playback.value.details!!.video.title)
        compose.runOnUiThread { activity.model.retryPlayback() }
        until { !activity.model.playback.value.loading && controllerTitle() == translated }
        until { activity.model.playback.value.selection?.overrides?.values?.any { it.type == C.TRACK_TYPE_VIDEO } == true }
        assertEquals(30_000L, activity.model.playback.value.position); assertEquals(1.5f, activity.model.playback.value.speed)
        assertFalse(activity.model.playback.value.playWhenReady)
        assertEquals(translated, activity.model.queue.value.current!!.video.title)
        val track = activity.model.playback.value.selection!!.overrides.values.single { it.type == C.TRACK_TYPE_VIDEO }
        assertEquals(360, track.mediaTrackGroup.getFormat(track.trackIndices.single()).height)
    }

    @Test fun loadingMoreAfterAChoiceChangeRestartsSearchAtPageOne() {
        command("discovery-search", """{"searchMixed":true}""")
        compose.runOnUiThread { activity.model.openGlobalSearch("fixture") }
        until { !activity.model.browse.value.loading && activity.model.browse.value.searchResults.isNotEmpty() }
        assertEquals(original, activity.model.browse.value.videos.first().title)
        save(activity.model.preferences.value.copy(showOriginalTitle = false))
        assertEquals(original, activity.model.browse.value.videos.first().title)
        compose.runOnUiThread { activity.model.more() }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.firstOrNull()?.title == translated }
        assertEquals(1, activity.model.browse.value.page)
        val events = state().getJSONArray("allRequests")
        val search = (0 until events.length()).map { events.getJSONObject(it).getString("path") }.last { it.startsWith("/api/v1/search?") }
        assertTrue(search.contains("page=1")); assertTrue(search.contains("show_original_title=false"))
    }

    @Test fun dearrowAndSpoilersUseSelectedTitleAndUnavailableOriginalFallsBack() {
        save(activity.model.preferences.value.copy(dearrowEnabled = true))
        until { compose.onAllNodesWithText("A calm scene").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Show original title").performClick()
        compose.onNodeWithText(original).assertExists()
        save(activity.model.preferences.value.copy(channelSpoilers = mapOf(("UC" + "a".repeat(22)) to ChannelSpoilerRule("Studio", hideTitle = true))))
        compose.runOnUiThread { activity.model.refresh() }
        until { !activity.model.browse.value.loading }
        compose.onNodeWithText(original).assertDoesNotExist()
        compose.onNodeWithTag("spoiler-title-reveal-testvideo01").assertExists()
        save(activity.model.preferences.value.copy(dearrowEnabled = false, channelSpoilers = emptyMap()))
        command("original-titles", """{"unavailable":true}""")
        compose.runOnUiThread { activity.model.refresh() }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.firstOrNull()?.title == translated }
        compose.onNodeWithText(translated).assertExists()
        compose.runOnUiThread { activity.model.openChannelRss(Channel("UC" + "a".repeat(22), "Studio")) }
        until { !activity.model.rss.value.loading }
        assertTrue(activity.model.rss.value.url!!.endsWith("show_original_title=true"))
    }
}
