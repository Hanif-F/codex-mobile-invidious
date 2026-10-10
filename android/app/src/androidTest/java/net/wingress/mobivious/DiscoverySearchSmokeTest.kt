package net.wingress.mobivious

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** All requests and accounts belong to the disposable localhost fixture. */
@RunWith(AndroidJUnit4::class)
class DiscoverySearchSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val channel = "UC" + "a".repeat(22)
    private fun until(condition: () -> Boolean) = compose.waitUntil(25_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun requests() = fixture().getJSONArray("searchRequests").objects()
    private fun discoveries() = fixture().getJSONArray("discoveryRequests").objects()
    private fun command(body: String = "{}", path: String = "discovery-search") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command(path = "reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            (activity.application as MobiviousApplication).cache.clear()
            vm.store.guestDeArrow(AccountPreferences()); vm.store.trendingCategory(vm.api.context(), TrendingCategory.LIVESTREAMS)
            vm.trendingCategory.value = TrendingCategory.LIVESTREAMS
            vm.saveSearchVisibility(SearchVisibility()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        // Remove guest blocks left by other scenarios using the app's real repository.
        compose.runOnUiThread { vm.blocked.value.channels.forEach { vm.toggleBlocked(it.id, it.name) } }
        until { vm.blocked.value.ids.isEmpty() }
        command("""{"searchMixed":true,"discoveryCategories":true}""")
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            vm.closePlayer(); vm.store.clearVisibilitySnapshot(vm.api.context()); vm.store.save(null)
            vm.store.clearVisibilitySnapshot(vm.api.context()); activity.finishAndRemoveTask()
        }
    }
    private fun back() { compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun recreate() {
        val old = activity; compose.runOnUiThread { old.recreate() }
        until {
            var ready = false
            compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }
            ready
        }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/mobivious-discovery-search", "screencap -p /data/local/tmp/mobivious-discovery-search/$name.png").forEach {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream -> stream.readBytes() }
        }
    }
    private fun search(text: String = "mixed") {
        compose.openGlobalSearch()
        compose.onNodeWithTag("main-search").performTextReplacement(text)
        compose.onNodeWithTag("main-search").performImeAction()
        until { vm.tab == "Search" && !vm.browse.value.loading && vm.query == text }
    }
    private fun country(tag: String, query: String, code: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("region-search").performTextReplacement(query)
        compose.onNodeWithTag("region-$code").performClick()
    }

    @Test fun trendingControlsSaveGuestRegionCategoryAndRestoreAcrossTabsAndRecreation() {
        compose.selectDiscoveryFeed("Trending")
        until { !vm.browse.value.loading && vm.browse.value.videos.firstOrNull()?.title == "trending livestreams US" }
        compose.onNodeWithTag("trending-livestreams").assertIsSelected()
        compose.onNodeWithTag("trending-gaming").performClick()
        until { !vm.browse.value.loading && vm.browse.value.videos.firstOrNull()?.title == "trending gaming US" }
        country("trending-region", "Indonesia", "ID")
        until { vm.preferences.value.region == "ID" && !vm.browse.value.loading && !vm.trendingRegionBusy.value }
        assertEquals("trending gaming ID", vm.browse.value.videos.single().title)
        assertEquals("ID", vm.store.guestDeArrow().region)
        assertEquals(TrendingCategory.GAMING, vm.store.trendingCategory(vm.api.context()))
        screenshot("trending-gaming-id")
        compose.selectDiscoveryFeed("Popular")
        until { !vm.browse.value.loading && vm.tab == "Popular" }
        compose.runOnUiThread { vm.refresh() }; until { !vm.browse.value.loading }
        val popular = discoveries().last { it.getString("kind") == "popular" }
        assertTrue(popular.isNull("region")); assertTrue(popular.isNull("type"))
        compose.selectDiscoveryFeed("Trending"); until { !vm.browse.value.loading }
        recreate(); compose.onNodeWithTag("trending-gaming").assertIsSelected()
        assertEquals("trending gaming ID", vm.browse.value.videos.single().title)
    }

    @Test fun settingsCountryPickerKeepsDraftUntilSaveAndCancelDiscardsIt() {
        fun open() {
            compose.openAppSettings(); compose.onNodeWithText("Browsing").performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-region"))
        }
        open(); country("settings-region", "Japan", "JP")
        assertEquals("US", vm.preferences.value.region); assertEquals("US", vm.store.guestDeArrow().region)
        compose.onNodeWithText("Cancel").performClick(); back()
        open(); country("settings-region", "JP", "JP"); compose.onNodeWithTag("settings-save").performClick()
        until { vm.preferences.value.region == "JP" && compose.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isNotEmpty() }
        back(); compose.selectDiscoveryFeed("Trending"); until { !vm.browse.value.loading }
        assertEquals("trending livestreams JP", vm.browse.value.videos.single().title)
        compose.onNodeWithTag("trending-region").performClick()
        compose.onNodeWithTag("region-search").performTextReplacement("Indonesia")
        compose.onNodeWithTag("region-ID").assertExists(); screenshot("country-picker")
        compose.onNodeWithContentDescription("Cancel").performClick()
    }

    @Test fun failedAccountRegionSaveRetainsPreviousSelectionAndCanRetry() {
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        until { vm.dearrowIdentity.value != null }
        compose.runOnUiThread { vm.navigate("Trending") }; until { !vm.browse.value.loading }
        command("""{"failPreferences":true}""")
        country("trending-region", "ID", "ID")
        until { !vm.trendingRegionBusy.value && vm.trendingRegionError.value != null }
        assertEquals("US", vm.preferences.value.region); assertEquals("trending livestreams US", vm.browse.value.videos.single().title)
        compose.onNodeWithTag("trending-region-error").assertExists()
        command("""{"failPreferences":false}"""); country("trending-region", "ID", "ID")
        until { vm.preferences.value.region == "ID" && !vm.browse.value.loading && !vm.trendingRegionBusy.value }
        assertEquals("ID", fixture().getJSONObject("preferences").getString("region"))
        assertEquals("preserved", fixture().getJSONObject("preferences").getString("unrelated_setting"))
    }

    @Test fun mixedSearchPreservesOrderChannelBackScrollRecreationAndRetry() {
        search()
        assertEquals(SearchType.ALL, vm.searchType.value)
        assertEquals(listOf("video:testvideo01", "channel:$channel", "playlist:PLlive"), vm.browse.value.searchResults.map { it.key })
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        compose.onNodeWithTag("related-channel-$channel").assertExists()
        compose.onNodeWithTag("playlist-card-PLlive").assertExists()
        screenshot("mixed-search")
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("related-channel-$channel"))
        compose.waitForIdle(); val position = vm.browse.value.position
        compose.onNodeWithTag("related-channel-$channel").performClick()
        until { vm.route == "channel:$channel" && vm.channel.value != null && !vm.browse.value.loading }
        back(); until { vm.route.isEmpty() && vm.tab == "Search" }
        assertEquals("mixed", vm.query); assertEquals(position, vm.browse.value.position)
        recreate(); assertEquals(SearchType.ALL, vm.searchType.value); assertEquals(3, vm.browse.value.searchResults.size)
        command("""{"searchFailNext":true}""")
        compose.runOnUiThread { vm.more() }; until { !vm.browse.value.loading && vm.browse.value.error != null }
        assertEquals(3, vm.browse.value.searchResults.size); assertEquals(1, vm.browse.value.page)
        compose.runOnUiThread { vm.retryBrowse() }; until { !vm.browse.value.loading && vm.browse.value.page == 2 }
        assertEquals(listOf("video:testvideo01", "channel:$channel", "playlist:PLlive", "channel:UC" + "c".repeat(22),
            "video:testvideo02", "playlist:RDopaque"), vm.browse.value.searchResults.map { it.key })
        assertEquals("testvideo01", vm.browse.value.lists.single { it.mix }.seedVideoId)
        compose.runOnUiThread { vm.more() }; until { !vm.browse.value.loading && vm.browse.value.end }
        assertTrue(requests().all { !it.getBoolean("authorized") })
    }

    @Test fun searchTypesUseCorrectFiltersAndKeepVideoChoices() {
        search()
        compose.runOnUiThread { vm.sort = "views"; vm.date = "week"; vm.durationFilter = "short"; vm.setSearchType(SearchType.CHANNELS) }
        until { !vm.browse.value.loading && vm.searchType.value == SearchType.CHANNELS }
        assertTrue(vm.browse.value.searchResults.single() is SearchResult.ChannelItem)
        val channels = requests().last(); assertEquals("channel", channels.getString("type")); assertEquals("relevance", channels.getString("sort"))
        assertTrue(channels.isNull("date")); assertTrue(channels.isNull("duration"))
        compose.onNodeWithContentDescription("Search filters").performClick()
        compose.onNodeWithText("Uploaded").assertDoesNotExist(); compose.onNodeWithText("Duration").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cancel").performClick(); screenshot("channel-search")
        compose.onNodeWithTag("search-type-video").performClick(); until { !vm.browse.value.loading && vm.searchType.value == SearchType.VIDEOS }
        val videos = requests().last(); assertEquals("week", videos.getJSONArray("date").getString(0)); assertEquals("short", videos.getJSONArray("duration").getString(0))
        assertEquals("views", videos.getString("sort"))
        compose.runOnUiThread { vm.setSearchType(SearchType.PLAYLISTS) }; until { !vm.browse.value.loading }
        assertTrue(vm.browse.value.searchResults.single() is SearchResult.PlaylistItem)
        assertTrue(requests().last().isNull("date"))
        search("second"); assertEquals(SearchType.PLAYLISTS, vm.searchType.value)
    }

    @Test fun hiddenMixedPageStillLoadsChannelsAndVisibilityOverrideIncludesBlockedResults() {
        compose.runOnUiThread { vm.toggleBlocked(channel, "Blocked creator") }; until { channel in vm.blocked.value.ids }
        search("hidden-mixed")
        assertTrue(vm.visibleSearchResults(vm.browse.value.searchResults).isEmpty()); assertFalse(vm.browse.value.end)
        compose.onNodeWithText("Results hidden by your visibility settings").assertExists()
        compose.onNodeWithText("Load more").performClick(); until { !vm.browse.value.loading && vm.browse.value.page == 2 }
        compose.onNodeWithTag("related-channel-UC" + "c".repeat(22)).assertExists()
        compose.runOnUiThread { vm.saveSearchVisibility(SearchVisibility(true, true)) }
        assertEquals(4, vm.visibleSearchResults(vm.browse.value.searchResults).size)
        compose.runOnUiThread { vm.toggleBlocked(channel, "Blocked creator") }; until { vm.blocked.value.ids.isEmpty() }
    }

    @Test fun unsupportedOnlyAndRepeatedPagesHaveCorrectEndBehavior() {
        search("unsupported"); assertTrue(vm.browse.value.searchResults.isEmpty()); assertFalse(vm.browse.value.end)
        compose.onNodeWithText("Load more").performClick(); until { !vm.browse.value.loading && vm.browse.value.page == 2 }
        compose.onNodeWithTag("related-channel-$channel").assertExists()
        search("repeat"); compose.runOnUiThread { vm.more() }; until { !vm.browse.value.loading && vm.browse.value.end }
        assertEquals(2, vm.browse.value.searchResults.size)
    }

    @Test fun delayedRequestsCannotReplaceNewSearchModeCategoryOrNavigation() {
        search(); command("""{"searchDelayNext":1200}""")
        compose.runOnUiThread { vm.refresh() }; until { requests().any { !it.getBoolean("completed") } }
        compose.onNodeWithTag("search-type-channel").performClick(); until { !vm.browse.value.loading && vm.searchType.value == SearchType.CHANNELS }
        until { requests().all { it.getBoolean("completed") } }
        assertTrue(vm.browse.value.searchResults.single() is SearchResult.ChannelItem)
        compose.runOnUiThread { vm.navigate("Trending") }; until { !vm.browse.value.loading }
        command("""{"discoveryDelayNext":1200}""")
        compose.runOnUiThread { vm.refresh() }; until { discoveries().any { !it.getBoolean("completed") } }
        compose.onNodeWithTag("trending-gaming").performClick(); until { !vm.browse.value.loading }
        until { discoveries().all { it.getBoolean("completed") } }
        assertEquals("trending gaming US", vm.browse.value.videos.single().title)
        search("mixed")
        command("""{"searchDelayNext":1200}""")
        compose.runOnUiThread { vm.refresh() }; until { requests().any { !it.getBoolean("completed") } }
        compose.runOnUiThread { vm.navigate("Popular") }; until { !vm.browse.value.loading }
        until { requests().all { it.getBoolean("completed") } }
        assertEquals("Popular", vm.tab); assertTrue(vm.browse.value.searchResults.isEmpty())
    }

    @Test fun controlsAndMixedCardsRemainReachableOnCompactDisplays() {
        compose.selectDiscoveryFeed("Trending"); until { !vm.browse.value.loading }
        compose.onNodeWithTag("trending-gaming").performClick(); until { !vm.browse.value.loading }
        screenshot("layout-trending")
        compose.onNodeWithTag("trending-region").performClick()
        compose.onNodeWithTag("region-search").performTextReplacement("ID")
        screenshot("layout-picker"); compose.onNodeWithTag("region-ID").performClick()
        until { !vm.trendingRegionBusy.value && !vm.browse.value.loading }
        search()
        for (tag in listOf("video-card-testvideo01", "related-channel-$channel", "playlist-card-PLlive")) {
            compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        compose.onNodeWithTag("search-type-playlist").performScrollTo().performClick(); until { !vm.browse.value.loading }
        compose.onNodeWithTag("search-type-playlist").assertIsSelected()
        screenshot("layout-playlists")
        compose.onNodeWithTag("search-type-channel").performScrollTo().performClick(); until { !vm.browse.value.loading }
        compose.onNodeWithTag("related-channel-$channel").assertIsDisplayed(); screenshot("layout-channels")
    }
}
