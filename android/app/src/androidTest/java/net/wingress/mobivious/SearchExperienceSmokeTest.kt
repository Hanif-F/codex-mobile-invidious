package net.wingress.mobivious

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Full search entry/results acceptance using only the disposable local instance. */
@RunWith(AndroidJUnit4::class)
class SearchExperienceSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val channel = "UC" + "a".repeat(22)
    private fun until(condition: () -> Boolean) = compose.waitUntil(25_000, condition)
    private fun command(path: String = "search-history", body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun requests() = fixture().getJSONArray("searchRequests").objects()
    private fun suggestions() = fixture().getJSONArray("suggestionRequests").objects()
    private fun currentActivity() { compose.runOnUiThread {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()?.let { activity = it }
    } }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun screenshot(name: String) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        shell("mkdir -p /data/local/tmp/mobivious-search-experience")
        shell("screencap -p /data/local/tmp/mobivious-search-experience/$name.png")
    }
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestPreferences(AccountPreferences()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        compose.runOnUiThread { vm.searchEntry.bind(vm.api.context()); vm.searchEntry.settings(SearchAssistanceSettings()); vm.searchEntry.clear() }
        command(body = """{"searchTest":true}""")
        command("discovery-search", """{"searchMixed":true}""")
    }
    @After fun close() {
        currentActivity()
        compose.runOnUiThread { vm.searchEntry.cancel(); vm.closePlayer(); activity.finishAndRemoveTask() }
    }
    private fun submit(text: String = "mixed") {
        compose.openGlobalSearch(); compose.onNodeWithTag("main-search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("main-search").performTextReplacement(text); compose.onNodeWithTag("main-search").performImeAction()
        until { vm.tab == "Search" && !vm.browse.value.loading && vm.query == text }
    }
    @Test fun freshSearchEditCancelRecreationAndFiltersPreserveTheRightState() {
        val original = vm.browse.value
        compose.openGlobalSearch(); compose.onNodeWithTag("main-search").assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("Search filters").assertDoesNotExist()
        compose.onNodeWithTag("main-search").performTextInput("unfinished")
        val old = activity; compose.runOnUiThread { old.recreate() }
        until { currentActivity(); activity !== old }
        compose.onNodeWithTag("main-search").assertTextContains("unfinished")
        compose.onNodeWithContentDescription("Close search").performClick()
        assertEquals("Popular", vm.tab); assertEquals(original, vm.browse.value)
        submit()
        compose.runOnUiThread { vm.sort = "views"; vm.date = "week"; vm.durationFilter = "short"; vm.setSearchType(SearchType.VIDEOS) }
        until { !vm.browse.value.loading && vm.searchType.value == SearchType.VIDEOS }
        val loaded = vm.browse.value; val count = requests().size
        compose.onNodeWithTag("search-query").performClick(); compose.onNodeWithTag("main-search").assertTextContains("mixed")
        compose.onNodeWithTag("main-search-clear").performClick(); compose.onNodeWithTag("main-search").assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("Close search").performClick()
        assertEquals(loaded, vm.browse.value); assertEquals(count, requests().size)
        compose.onNodeWithTag("search-query").performClick(); compose.onNodeWithTag("main-search").performTextReplacement("edited")
        compose.onNodeWithTag("main-search").performImeAction(); until { !vm.browse.value.loading && vm.query == "edited" }
        assertEquals(SearchType.VIDEOS, vm.searchType.value); assertEquals("views", vm.sort); assertEquals("week", vm.date)
        submit("fresh")
        assertEquals(SearchType.ALL, vm.searchType.value); assertEquals("relevance", vm.sort); assertEquals("", vm.date); assertEquals("", vm.durationFilter)
        compose.onNodeWithContentDescription("Close search").performClick()
        until { vm.tab == "Popular" }; assertEquals(original.videos, vm.browse.value.videos)
        compose.openGlobalSearch(); compose.onNodeWithTag("main-search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
    @Test fun recentAndLiveRowsSubmitOnceAndSupportEditingRemovingAndClearing() {
        compose.runOnUiThread { vm.searchEntry.remember("recent"); vm.searchEntry.remember("other") }
        compose.openGlobalSearch(); screenshot("entry-recent")
        compose.onNodeWithTag("search-recent-1-insert").performClick()
        compose.onNodeWithTag("main-search").assertTextContains("recent")
        until { vm.searchEntry.state.value.suggestions.isNotEmpty() }
        assertTrue(suggestions().all { !it.getBoolean("authorized") && it.getString("region") == "US" })
        screenshot("entry-suggestions")
        compose.onNodeWithTag("search-suggestion-0").performClick()
        until { !vm.browse.value.loading && vm.query == "recent music" }
        assertEquals(1, requests().count { it.getString("q") == "recent music" })
        assertEquals("recent music", vm.store.recentSearches(vm.api.context()).first())
        compose.openGlobalSearch(); compose.onNodeWithTag("main-search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("search-recent-0-remove").performClick()
        assertFalse(vm.store.recentSearches(vm.api.context()).contains("recent music"))
        compose.onNodeWithTag("search-clear-recent").performClick(); compose.onNodeWithTag("search-confirm-clear").performClick()
        assertTrue(vm.store.recentSearches(vm.api.context()).isEmpty())
    }
    @Test fun suggestionFailuresAndDelayedResponsesNeverBlockOrReplaceTheCurrentQuery() {
        command(body = """{"suggestionDelayNext":1200}""")
        compose.openGlobalSearch(); compose.onNodeWithTag("main-search").performTextInput("old")
        until { suggestions().any { it.getString("q") == "old" } }
        compose.onNodeWithTag("main-search").performTextReplacement("latest")
        until { vm.searchEntry.state.value.suggestions.contains("latest music") }
        until { suggestions().first { it.getString("q") == "old" }.getBoolean("completed") }
        assertEquals(listOf("latest music", "latest videos"), vm.searchEntry.state.value.suggestions)
        command(body = """{"suggestionStatus":404}""")
        compose.onNodeWithTag("main-search").performTextReplacement("unsupported")
        until { vm.searchEntry.state.value.failed }
        compose.onNodeWithTag("search-suggestions-error").assertIsDisplayed()
        compose.onNodeWithTag("main-search").performImeAction(); until { !vm.browse.value.loading && vm.query == "unsupported" }
        val count = suggestions().size
        compose.openGlobalSearch(); compose.onNodeWithTag("main-search").performTextReplacement("https://youtu.be/testvideo01?t=25")
        compose.waitForIdle(); assertEquals(count, suggestions().size)
        compose.onNodeWithTag("main-search").performImeAction()
        until { vm.playback.value.details != null && vm.playback.value.position >= 25_000 }
        assertFalse(vm.store.recentSearches(vm.api.context()).any(RecentSearches::urlLike))
    }
    @Test fun historyAndOptionsAreLocalToTheActiveProfileAndSurviveRecreation() {
        compose.runOnUiThread { vm.searchEntry.remember("guest"); vm.searchEntry.settings(SearchAssistanceSettings(false, false)) }
        assertEquals(listOf("guest"), vm.store.recentSearches(vm.api.context()))
        compose.openGlobalSearch(); compose.onNodeWithText("Recent searches").assertDoesNotExist()
        compose.onNodeWithTag("main-search").performTextInput("not saved"); compose.onNodeWithTag("main-search").performImeAction()
        until { !vm.browse.value.loading && vm.query == "not saved" }
        assertEquals(listOf("guest"), vm.store.recentSearches(vm.api.context()))
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        until { vm.account.value != null && vm.searchEntry.state.value.settings.remember }
        compose.runOnUiThread { vm.searchEntry.remember("account"); vm.store.save(null) }
        until { vm.account.value == null && !vm.searchEntry.state.value.settings.remember }
        assertEquals(listOf("guest"), vm.searchEntry.state.value.recent)
        val old = activity; compose.runOnUiThread { old.recreate() }; until { currentActivity(); activity !== old }
        assertFalse(vm.searchEntry.state.value.settings.suggestions)
        compose.runOnUiThread { vm.searchEntry.settings(SearchAssistanceSettings()) }
        compose.openGlobalSearch(); compose.onNodeWithText("guest", useUnmergedTree = true).assertExists()
    }
    @Test fun filtersResetAndRemovalKeepTheQueryAndVisibilityPreference() {
        submit()
        compose.runOnUiThread { vm.date = "week"; vm.refresh() }; until { !vm.browse.value.loading }
        compose.onNodeWithTag("search-filter-date").assertIsDisplayed().performClick()
        until { !vm.browse.value.loading && vm.date.isBlank() }; assertEquals("mixed", vm.query)
        compose.runOnUiThread { vm.sort = "views"; vm.durationFilter = "short"; vm.setSearchType(SearchType.VIDEOS) }
        until { !vm.browse.value.loading }
        compose.onNodeWithContentDescription("Search filters").performClick()
        assertTrue(compose.onAllNodesWithText("View count").fetchSemanticsNodes().isNotEmpty()); assertTrue(compose.onAllNodesWithText("Under 4 minutes").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("search-reset-filters").performClick(); until { !vm.browse.value.loading }
        compose.onNodeWithContentDescription("Close search filters").performClick()
        assertEquals(SearchType.ALL, vm.searchType.value); assertEquals("mixed", vm.query)
        assertEquals(SearchVisibility(), vm.searchVisibility.value)
    }
    @Test fun mixedCardsRemainDistinctInThemesThinModeNarrowLargeTextAndLandscape() {
        submit(); screenshot("results-dark")
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("related-channel-$channel"))
        compose.onNodeWithTag("related-avatar-$channel", useUnmergedTree = true).assertExists()
        screenshot("results-channel")
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("playlist-card-PLlive"))
        compose.onNodeWithTag("search-playlist-cover-PLlive", useUnmergedTree = true).assertExists(); screenshot("results-playlist")
        compose.runOnUiThread { vm.store.guestPreferences(vm.preferences.value.copy(darkMode = "light")); vm.refreshSharedSettings() }; screenshot("results-light")
        compose.runOnUiThread { vm.store.guestPreferences(vm.preferences.value.copy(thinMode = true)); vm.refreshSharedSettings() }
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("related-channel-$channel"))
        compose.onNodeWithTag("search-channel-placeholder-$channel", useUnmergedTree = true).assertExists(); screenshot("results-thin")
        val font = shell("settings get system font_scale"); val density = shell("wm density")
        try {
            shell("settings put system font_scale 1.6"); shell("wm density 672")
            until { currentActivity(); activity.resources.configuration.fontScale >= 1.5f && activity.resources.configuration.screenWidthDp <= 320 }
            compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("playlist-card-PLlive"))
            compose.onNodeWithContentDescription("Search filters").assertIsDisplayed(); screenshot("results-narrow-large-text")
            compose.runOnUiThread { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            until { currentActivity(); activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("playlist-card-PLlive"))
            screenshot("results-landscape-large-text")
        } finally {
            shell(if (font == "null") "settings delete system font_scale" else "settings put system font_scale $font")
            val override = Regex("Override density: (\\d+)").find(density)?.groupValues?.get(1)
            shell(if (override == null) "wm density reset" else "wm density $override")
            currentActivity(); compose.runOnUiThread { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
