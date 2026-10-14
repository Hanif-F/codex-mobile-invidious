package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.TrendingCategory
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Real gestures and browse requests against the disposable localhost API. */
@RunWith(AndroidJUnit4::class)
class FeedRefreshJourneyTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(body: String = "{}", path: String = "home-subscriptions") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun pull() {
        compose.onNodeWithTag("browse-video-list").performScrollToIndex(0)
        compose.onNodeWithTag("browse-video-list").performTouchInput { swipeDown(durationMillis = 500) }
    }
    private fun tab(name: String) {
        compose.selectFeed(name)
        until { vm.tab == name && vm.route.isEmpty() && !vm.browse.value.loading }
    }
    private fun login() {
        compose.runOnUiThread { vm.action { vm.login("RefreshViewer", "fixture-password") } }
        until { vm.account.value != null && vm.subscriptionChannels.value.loaded && vm.dearrowIdentity.value != null }
    }
    @Before fun launch() {
        command(path = "reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestPreferences(AccountPreferences()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.store.guestPreferences(AccountPreferences()); activity.finishAndRemoveTask()
        }
    }

    @Test fun rootToolbarsKeepSearchAndYouOwnsSettings() {
        for (name in listOf("Popular", "Trending")) {
            tab(name)
            compose.onNodeWithTag("global-search").assertIsDisplayed()
            compose.onNodeWithTag("global-settings").assertDoesNotExist()
            compose.onNodeWithTag("feed-refresh").assertDoesNotExist()
            compose.onNodeWithTag("feed-refresh-container").assertIsDisplayed()
        }
        tab("You")
        compose.onNodeWithTag("global-settings").performClick()
        compose.onNodeWithTag("settings-root").assertExists()
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        tab("Subscriptions")
        compose.onNodeWithTag("feed-refresh-container").assertDoesNotExist()
        compose.runOnUiThread { vm.refreshFeed() }
        assertEquals(0, fixture().getJSONArray("feedRequests").length())
        login(); tab("Subscriptions")
        compose.onNodeWithTag("global-search").assertIsDisplayed()
        compose.onNodeWithTag("subscription-actions").assertIsDisplayed()
        compose.onNodeWithTag("global-settings").assertDoesNotExist()
        compose.onNodeWithTag("subscription-refresh").assertDoesNotExist()
        compose.onNodeWithTag("subscription-channels-shortcut").assertDoesNotExist()
        compose.onAllNodesWithTag("subscription-channels-button").assertCountEquals(1)
        compose.onNodeWithTag("subscription-channels-button").assertIsDisplayed()
    }

    @Test fun refreshRetainsRowsReplacesPageOneAndRecoversFromFailureAndEmptyResults() {
        val before = vm.browse.value.videos
        val requests = fixture().getJSONArray("discoveryRequests").length()
        command("""{"indicatorVideos":true}""", "watched")
        command("""{"discoveryDelayNext":1800}""")
        pull()
        until { vm.browse.value.refreshing }
        assertEquals(before, vm.browse.value.videos)
        compose.runOnUiThread { vm.refreshFeed(); vm.refreshFeed() }
        compose.onNodeWithTag("browse-video-list").performTouchInput { swipeDown(durationMillis = 300) }
        until { !vm.browse.value.loading }
        assertFalse(vm.browse.value.refreshing)
        assertEquals(requests + 1, fixture().getJSONArray("discoveryRequests").length())
        assertEquals(4, vm.browse.value.videos.size)
        command("""{"indicatorVideos":false}""", "watched")
        pull(); until { !vm.browse.value.loading }
        assertEquals(1, vm.browse.value.videos.size)
        val retained = vm.browse.value.videos
        command("""{"discoveryFailNext":true}""")
        pull(); until { vm.browse.value.error != null }
        assertFalse(vm.browse.value.refreshing)
        assertEquals(retained, vm.browse.value.videos)
        compose.runOnUiThread { vm.retryBrowse() }
        until { !vm.browse.value.loading && vm.browse.value.error == null }
        command("""{"discoveryEmpty":true}""")
        pull(); until { !vm.browse.value.loading && vm.browse.value.videos.isEmpty() }
        compose.onNodeWithText("Nothing here yet").assertIsDisplayed()
        assertFalse(vm.browse.value.refreshing)
        command("""{"discoveryEmpty":false}""")
        pull(); until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
    }

    @Test fun navigatingAwayCancelsRefreshAndLateResponsesCannotReplaceTheOtherFeed() {
        command("""{"discoveryDistinct":true,"discoveryDelayNext":2200}""")
        pull(); until { vm.browse.value.refreshing }
        tab("Trending")
        until {
            val events = fixture().getJSONArray("discoveryRequests")
            (0 until events.length()).all { events.getJSONObject(it).getBoolean("completed") }
        }
        assertFalse(vm.browse.value.refreshing)
        assertEquals("trending discovery fixture", vm.browse.value.videos.single().title)
        tab("Popular")
        assertFalse(vm.browse.value.refreshing)
        assertEquals("popular discovery fixture", vm.browse.value.videos.single().title)
    }

    @Test fun refreshKeepsTrendingFiltersAndSubscriptionSearchAndSeparatesPagination() {
        command("""{"discoveryCategories":true}""", "discovery-search")
        tab("Trending")
        compose.runOnUiThread {
            vm.selectTrendingCategory(TrendingCategory.GAMING); vm.selectTrendingRegion("ID"); vm.flushPreferenceEdits()
        }
        until { vm.trendingCategory.value == TrendingCategory.GAMING && vm.preferences.value.region == "ID" && !vm.browse.value.loading }
        pull(); until { !vm.browse.value.loading }
        val events = fixture().getJSONArray("discoveryRequests")
        val last = events.getJSONObject(events.length() - 1)
        assertEquals("gaming", last.getString("type")); assertEquals("ID", last.getString("region"))
        login(); tab("Subscriptions")
        val feedRows = vm.browse.value.videos
        command("""{"feedDelayNext":1400,"feedFailNext":true}""")
        pull(); until { vm.browse.value.refreshing }
        assertEquals(feedRows, vm.browse.value.videos)
        until { vm.browse.value.error != null }
        assertFalse(vm.browse.value.refreshing)
        assertEquals(feedRows, vm.browse.value.videos)
        compose.runOnUiThread { vm.retryBrowse() }
        until { !vm.browse.value.loading && vm.browse.value.error == null }
        compose.onNodeWithTag("subscription-search").performTextInput("many")
        compose.onNodeWithTag("subscription-search-submit").performClick()
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 20 }
        command("""{"searchDelayNext":1200}""", "search-history")
        compose.runOnUiThread { vm.more() }
        until { vm.browse.value.loading }
        assertFalse(vm.browse.value.refreshing)
        until { !vm.browse.value.loading && vm.browse.value.page == 2 }
        val loaded = vm.browse.value.videos
        command("""{"searchDelayNext":1800,"searchFailNext":true}""", "search-history")
        pull(); until { vm.browse.value.refreshing }
        assertEquals(loaded, vm.browse.value.videos)
        until { vm.browse.value.error != null }
        assertFalse(vm.browse.value.refreshing)
        assertEquals(loaded, vm.browse.value.videos)
        compose.runOnUiThread { vm.retryBrowse() }
        until { !vm.browse.value.loading && vm.browse.value.error == null }
        assertEquals(1, vm.browse.value.page); assertEquals(20, vm.browse.value.videos.size)
        assertEquals("many", vm.scopedSearch.value.submitted)
        val searches = fixture().getJSONArray("searchRequests")
        val refreshed = searches.getJSONObject(searches.length() - 1)
        assertEquals("subscriptions", refreshed.getString("scope"))
        assertEquals("many", refreshed.getString("q")); assertEquals(1, refreshed.getInt("page"))
    }
}
