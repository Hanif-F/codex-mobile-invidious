package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL
import java.time.LocalDate

/** Uses only the disposable fixture; no production account or upstream search. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SearchHistorySmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private fun until(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun requests() = fixture().getJSONArray("searchRequests").objects()
    private fun command(path: String = "search-history", body: String) {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("reset", "{}")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        command(body = """{"searchTest":true}""")
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread { vm.closePlayer(); activity.finishAndRemoveTask() }
    }
    private fun signIn() {
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        until { vm.subscriptionChannels.value.channels.isNotEmpty() }
    }
    private fun openHistory() {
        signIn(); compose.runOnUiThread { vm.navigate("You", "history") }
        until { !vm.browse.value.loading && vm.browse.value.history != null }
    }
    private fun submit(tag: String, text: String) {
        if (tag == "main-search") compose.onNodeWithTag("global-search").performClick()
        if (tag == "channel-search") compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.onNodeWithTag(tag).performImeAction()
        until { !vm.browse.value.loading }
    }
    private fun choose(tab: ChannelTab) {
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("channel-tab-${tab.path}"))
        compose.onNodeWithTag("channel-tab-${tab.path}").performClick()
        until { !vm.browse.value.loading && vm.channelTab.value == tab }
    }
    private fun seedHistory() {
        command(body = """{"max_results":2,"historyEntries":[
          {"video_id":"testvideo01","title":"Saved today","channel_name":"Archive Studio","channel_id":"UCaaaaaaaaaaaaaaaaaaaaaa","release_date":"2024-02-29","latest_watched":"2026-10-04","length_seconds":120},
          {"video_id":"testvideo02","title":"Saved yesterday","channel_name":"Archive Studio","latest_watched":"2026-10-03","length_seconds":120},
          {"video_id":"weekvideo01","title":"Saved week","channel_name":"Archive Studio","latest_watched":"2026-10-01"},
          {"video_id":"monthvid001","title":"Saved month","channel_name":"Archive Studio","latest_watched":"2026-09-20"},
          {"video_id":"oldvideo001","title":"Needle café","channel_name":"Archive Studio","latest_watched":"2026-08-01"},
          {"video_id":"unknownvid1","title":null,"channel_name":null,"latest_watched":null}
        ]}""")
    }

    @Test fun iconImeAndPhysicalEnterEachSubmitExactlyOnce() {
        compose.runOnUiThread { vm.navigate("Search") }
        until { !vm.browse.value.loading }
        submit("main-search", "first")
        until { requests().size == 1 }
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("main-search").performTextReplacement("second")
        compose.onNodeWithTag("main-search-submit").performClick()
        until { !vm.browse.value.loading && requests().size == 2 }
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("main-search").performTextReplacement("many")
        compose.onNodeWithTag("main-search").performClick()
        compose.onNodeWithTag("main-search").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        until { !vm.browse.value.loading && requests().size == 3 }
        compose.waitForIdle()
        assertEquals(listOf("first", "second", "many"), requests().map { it.getString("q") })
        assertEquals(20, vm.browse.value.videos.size)
    }

    @Test fun imeSubmissionPreservesPastedVideoTimestamps() {
        compose.runOnUiThread { vm.navigate("Search") }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("main-search").performTextReplacement("https://youtu.be/testvideo01?t=25")
        compose.onNodeWithTag("main-search").performImeAction()
        until { vm.playback.value.details != null && vm.playback.value.position >= 25_000 }
        assertEquals("testvideo01", vm.playback.value.mediaId)
        assertTrue(requests().isEmpty())
    }

    @Test fun channelSearchPaginationAndClearRestoreSelectedStreams() {
        compose.runOnUiThread { vm.navigate("Popular", "channel:UCaaaaaaaaaaaaaaaaaaaaaa") }
        until { !vm.browse.value.loading && vm.channel.value != null }
        choose(ChannelTab.STREAMS)
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("channel-search"))
        compose.onNodeWithTag("channel-search").performTextReplacement("many")
        assertTrue(requests().isEmpty()); assertEquals("streamvid01", vm.browse.value.videos.single().id)
        compose.onNodeWithTag("channel-search").performImeAction()
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 20 }
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").performClick()
        until { !vm.browse.value.loading && vm.browse.value.end }
        assertEquals(21, vm.browse.value.videos.size)
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Clear search"))
        compose.onNodeWithText("Clear search").performClick()
        until { !vm.browse.value.loading && vm.scopedSearch.value.submitted.isEmpty() }
        assertEquals(ChannelTab.STREAMS, vm.channelTab.value)
        assertEquals("streamvid01", vm.browse.value.videos.single().id)
        assertTrue(requests().all { !it.getBoolean("authorized") })
    }

    @Test fun subscriptionSearchLoadsBeyondHiddenPagesAndIgnoresFeedOnlyFilters() {
        signIn()
        compose.runOnUiThread { vm.preferences.value = vm.preferences.value.copy(latestOnly = true, notificationsOnly = true); vm.navigate("Subscriptions") }
        until { !vm.browse.value.loading }
        submit("subscription-search", "hidden")
        assertEquals(20, vm.browse.value.videos.size); assertFalse(vm.browse.value.end)
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").performClick()
        until { !vm.browse.value.loading && vm.browse.value.end }
        assertTrue(vm.visibleVideos(vm.browse.value.videos).any { it.id == "testvideo01" })
        assertTrue(requests().all { it.getString("scope") == "subscriptions" && it.getBoolean("authorized") })
        compose.onNodeWithText("Clear search").performClick()
        until { !vm.browse.value.loading && vm.scopedSearch.value.submitted.isEmpty() }
    }

    @Test fun historySearchCoversLaterPagesAndDateGroupsKeepUnknownEntries() {
        seedHistory(); openHistory()
        assertEquals(6, vm.browse.value.history!!.total)
        // Lazy lists can retain the first video's key when date headings arrive; bring the heading into view.
        compose.onNodeWithTag("browse-video-list").performScrollToIndex(0)
        compose.onNodeWithTag("history-group-TODAY").assertExists()
        val released = "Released: ${net.wingress.mobivious.data.DisplayFormats.date(java.time.LocalDate.of(2024, 2, 29))}"
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText(released))
        compose.onNodeWithText(released).assertExists()
        submit("history-search", "needle CAFÉ")
        assertEquals("oldvideo001", vm.browse.value.videos.single().id)
        assertEquals(1, vm.browse.value.history!!.total)
        submit("history-search", "missing")
        compose.onNodeWithText("No matches").assertExists()
        compose.onNodeWithText("Clear search").performClick()
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 2 }
        repeat(2) {
            compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Load more"))
            compose.onNodeWithText("Load more").performClick()
            until { !vm.browse.value.loading }
        }
        assertEquals(6, vm.browse.value.videos.size)
        assertTrue(vm.browse.value.videos.last().unavailable)
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("history-group-OLDER"))
        compose.onNodeWithTag("history-group-OLDER").assertExists()
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Unknown watch date", substring = true))
        compose.onNodeWithText("Watched: Unknown watch date").assertExists()
    }

    @Test fun historyRemovalPreservesProgressAndClearRequiresConfirmation() {
        seedHistory(); command("watched", """{"positions":{"testvideo01":40}}"""); openHistory()
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasContentDescription("Remove Saved today"))
        compose.onNodeWithContentDescription("Remove Saved today").performClick()
        until { !vm.browse.value.loading && vm.browse.value.history?.total == 5 }
        assertEquals(40, fixture().getJSONObject("positions").getInt("testvideo01"))
        compose.onNodeWithTag("history-actions").performClick()
        compose.onNodeWithText("Clear watch history").performClick(); compose.onNodeWithText("Cancel").performClick()
        assertEquals(5, fixture().getJSONArray("watched").length())
        compose.onNodeWithTag("history-actions").performClick()
        compose.onNodeWithText("Clear watch history").performClick(); compose.onNodeWithText("Delete").performClick()
        until { !vm.browse.value.loading && vm.browse.value.history?.total == 0 }
        assertEquals(0, fixture().getJSONObject("positions").length())
        compose.onNodeWithText("Your history is empty").assertExists()
    }

    @Test fun oldHistoryServerRetainsViewingAndExplainsDisabledSearch() {
        seedHistory(); command(body = """{"historyLegacy":true}"""); openHistory()
        assertFalse(vm.browse.value.history!!.organized)
        compose.onNodeWithTag("history-search").assertIsNotEnabled()
        compose.onNodeWithText("Update this server to enable organized history and history search.").assertExists()
        assertTrue(vm.browse.value.videos.isNotEmpty())
    }

    @Test fun delayedSearchCannotReplaceNewChannelTabOrAccount() {
        compose.runOnUiThread { vm.navigate("Popular", "channel:UCaaaaaaaaaaaaaaaaaaaaaa") }
        until { !vm.browse.value.loading && vm.channel.value != null }
        command(body = """{"searchDelayNext":1200}""")
        compose.runOnUiThread { vm.editSearch("second", true); vm.submitSearch(true) }
        until { requests().any { !it.getBoolean("completed") } }
        choose(ChannelTab.STREAMS)
        until { requests().all { it.getBoolean("completed") } }
        assertEquals("streamvid01", vm.browse.value.videos.single().id)
        signIn(); compose.runOnUiThread { vm.navigate("Subscriptions") }
        until { !vm.browse.value.loading }
        command(body = """{"searchDelayNext":1200}""")
        compose.runOnUiThread { vm.editSearch("second", true); vm.submitSearch(true) }
        until { requests().any { !it.getBoolean("completed") } }
        compose.runOnUiThread { vm.store.save(null) }
        until { requests().all { it.getBoolean("completed") } && !vm.browse.value.loading }
        assertTrue(vm.scopedSearch.value.submitted.isEmpty()); assertTrue(vm.browse.value.videos.isEmpty())
    }

    @Test fun historyPaginationReloadsWhenServerCalendarDayChanges() {
        seedHistory(); openHistory()
        command(body = """{"historyToday":"2026-10-05"}""")
        compose.runOnUiThread { vm.more() }
        until { !vm.browse.value.loading && vm.browse.value.history?.today == LocalDate.of(2026, 10, 5) }
        assertEquals(1, vm.browse.value.page); assertEquals(2, vm.browse.value.videos.size)
        assertEquals(HistoryGroup.YESTERDAY, History.group(vm.browse.value.videos.first().history!!.watched, vm.browse.value.history!!.today))
    }
}
