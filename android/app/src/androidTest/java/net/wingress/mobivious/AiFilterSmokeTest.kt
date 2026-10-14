package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AiFilterSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val first = "UC" + "a".repeat(22)
    private val member = "UC" + "b".repeat(22)
    private fun ui(action: () -> Unit) = compose.runOnUiThread(action)
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("reset"); command("visibility", """{"visibilityVideos":true,"seedPlaylist":true}""")
        command("ai", """{"aiMatches":{"$first":["blocklist"],"$member":["warnlist"]}}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestPreferences(AccountPreferences()); vm.saveSearchVisibility(SearchVisibility())
            vm.aiFilter.reset(force = true); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 3 }
    }
    @After fun close() {
        if (::activity.isInitialized) ui {
            vm.closePlayer(); vm.store.background = true; vm.store.pip = true; vm.store.save(null)
            vm.store.guestPreferences(AccountPreferences()); vm.refreshSharedSettings(); activity.finishAndRemoveTask()
        }
    }
    private fun save(value: AccountPreferences) {
        ui { vm.action { vm.savePreferences(value, vm.preferences.value, vm.api.context()) } }
        until { vm.preferences.value == value }
    }
    private fun ai(group: AiPageGroup, action: AiAction) = AiFilterSettings(true).withAction(AiListKind.BLOCKLIST, group, action)
    private fun navigate(tab: String, route: String = "") {
        ui { vm.query = "fixture"; vm.navigate(tab, route) }
        until { !vm.browse.value.loading }
    }
    private fun settings() {
        compose.openAppSettings()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasText("AI channel filter", substring = false))
        compose.onNodeWithText("AI channel filter", substring = false).performClick()
    }
    private fun choose(group: AiPageGroup, kind: AiListKind, label: String) {
        val tag = "ai-choice-${kind.wire}-${group.wire}"
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(tag))).performClick()
        compose.onNodeWithText(label, substring = false).performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/mobivious-ai-screenshots", "screencap -p /data/local/tmp/mobivious-ai-screenshots/$name.png").forEach {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream -> stream.readBytes() }
        }
    }
    private fun assertWarningFits() {
        val container = compose.onNodeWithTag("ai-thumbnail-blocklist", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (text in listOf("Likely AI-generated", "AiSList Blocklist")) {
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            // A centered paragraph can retain wider layout constraints than its measured glyph bounds.
            for (line in 0 until layout.lineCount) assertTrue("Warning line is clipped: $text",
                layout.getLineRight(line) - layout.getLineLeft(line) <= bounds.width + 1f)
            assertFalse("Warning text is clipped vertically: $text", layout.didOverflowHeight)
            assertTrue(bounds.top >= container.top && bounds.bottom <= container.bottom)
            val duration = compose.onNode(hasText("2:00") and hasAnyAncestor(hasTestTag("video-card-testvideo01")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Duration covers warning text", bounds.bottom <= duration.top)
        }
    }
    @Test fun guestCanEditWhilePausedSaveEnableAndPauseWithoutLosingChoices() {
        assertEquals(0, fixture().getJSONArray("aiRequests").length())
        settings(); choose(AiPageGroup.FEEDS, AiListKind.BLOCKLIST, "Hide videos")
        compose.finishAutomaticPreferencePage()
        until { vm.preferences.value.aiFilter.saved(AiListKind.BLOCKLIST, AiPageGroup.FEEDS) == AiAction.HIDE }
        assertFalse(vm.preferences.value.aiFilter.enabled)
        assertEquals(0, fixture().getJSONArray("aiRequests").length())
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        settings()
        compose.onNodeWithTag("settings-list").performScrollToIndex(0)
        compose.onNodeWithText("Enable AI Channel Filter").performClick()
        screenshot("settings-enabled")
        compose.finishAutomaticPreferencePage()
        until { vm.preferences.value.aiFilter.enabled }
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        // Settings is opened from You; feed-only actions request classifications when a feed is shown.
        navigate("Popular")
        until { vm.aiFilter.state.value.matches[first]?.contains(AiListKind.BLOCKLIST) == true }
        compose.onNodeWithTag("video-card-testvideo01").assertDoesNotExist()
        save(vm.preferences.value.copy(aiFilter = vm.preferences.value.aiFilter.copy(enabled = false)))
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        assertEquals(AiAction.HIDE, vm.store.guestPreferences().aiFilter.saved(AiListKind.BLOCKLIST, AiPageGroup.FEEDS))
    }
    @Test fun fullyHiddenSearchPageStillLoadsTheNextUnfilteredPage() {
        command("visibility", """{"hiddenFirstPage":true}""")
        command("ai", """{"aiMatches":{"$member":["blocklist"]}}""")
        save(vm.preferences.value.copy(showMemberVideos = true, aiFilter = ai(AiPageGroup.SEARCH, AiAction.HIDE)))
        navigate("Search")
        assertEquals(1, vm.browse.value.videos.size)
        compose.onNodeWithTag("video-card-membervid01").assertDoesNotExist()
        compose.onNodeWithText("Results hidden by your visibility settings").assertIsDisplayed()
        compose.onNodeWithText("Load more").performClick()
        until { vm.browse.value.page == 2 && !vm.browse.value.loading }
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
    }
    @Test fun warningsRenderAcrossThemesAndTextOnlyWithoutFetchingOriginalImages() {
        val settings = ai(AiPageGroup.FEEDS, AiAction.REPLACE).withAction(AiListKind.WARNLIST, AiPageGroup.FEEDS, AiAction.REPLACE)
        save(vm.preferences.value.copy(showMemberVideos = true, aiFilter = settings, darkMode = "light"))
        until { vm.aiFilter.state.value.matches.size == 2 }
        command("ai", """{"aiImages":"warnings","clearRequests":true}""")
        ui { vm.refresh() }; until { !vm.browse.value.loading && vm.browse.value.videos.first().thumbnail.contains("warnings") }
        compose.onNodeWithTag("ai-thumbnail-blocklist", useUnmergedTree = true).assertExists(); screenshot("warnings-light"); assertWarningFits()
        save(vm.preferences.value.copy(darkMode = "dark", uiDensity = "compact")); screenshot("warnings-dark-compact"); assertWarningFits()
        save(vm.preferences.value.copy(thinMode = true)); screenshot("warnings-text-only"); assertWarningFits()
        compose.onNodeWithTag("ai-thumbnail-warnlist", useUnmergedTree = true).assertExists()
        val paths = fixture().getJSONArray("allRequests").objects().map { it.getString("path") }
        assertFalse(paths.any { it.contains("ai=warnings-testvideo01") || it.contains("ai=warnings-membervid01") })
        assertTrue(fixture().getJSONArray("aiRequests").objects().none { it.getBoolean("authorized") })
    }
    @Test fun accountLibraryScopedSearchClipsAndQueueOnlyReplaceAndPreserveOccurrences() {
        ui { vm.action { vm.login("AiViewer", "fixture-password") } }
        until { vm.account.value != null && vm.tab == "You" && !vm.browse.value.loading }
        val settings = ai(AiPageGroup.OTHER, AiAction.REPLACE).withAction(AiListKind.BLOCKLIST, AiPageGroup.SEARCH, AiAction.HIDE)
        save(vm.preferences.value.copy(showMemberVideos = true, aiFilter = settings))
        assertEquals("replace_thumbnail", fixture().getJSONObject("preferences").getString("ai_blocklist_other_pages_action"))
        navigate("Subscriptions")
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        navigate("Popular", "channel:$first")
        ui { vm.scopedSearch.value = SearchInput("fixture", "fixture"); vm.refresh() }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        compose.onAllNodesWithTag("ai-thumbnail-blocklist", useUnmergedTree = true).assertCountEquals(1)
        command("clips", """{"count":1}"""); navigate("You", "clips")
        until { vm.browse.value.clips.isNotEmpty() }
        compose.onNodeWithTag("ai-thumbnail-blocklist", useUnmergedTree = true).assertExists(); screenshot("clips-warning")
        command("queue")
        ui { vm.play("testvideo01", source = "IVqueue", index = 0) }
        until { !vm.queue.value.loading && vm.queue.value.items.size == 3 }
        assertEquals(listOf("A", "B", "C"), vm.queue.value.items.map { it.video.indexId })
        compose.onNodeWithTag("mini-player-preview").performClick()
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("playback-queue-header"))
        compose.onNodeWithTag("playback-queue-header").performClick()
        compose.onAllNodes(hasTestTag("ai-thumbnail-blocklist") and hasAnyAncestor(hasTestTag("playback-queue")), useUnmergedTree = true).assertCountEquals(3)
        screenshot("queue-warnings")
        ui { vm.selectQueue(vm.queue.value.items[1].key) }
        until { !vm.queue.value.loading && vm.queue.value.current?.video?.indexId == "B" }
        assertEquals(3, vm.queue.value.items.size)
    }
    @Test fun backgroundAutoplaySkipsHiddenRecommendationsAndDirectPlaybackStillWorks() {
        val settings = ai(AiPageGroup.RECOMMENDATIONS, AiAction.HIDE).withAction(AiListKind.WARNLIST, AiPageGroup.RECOMMENDATIONS, AiAction.HIDE)
        save(vm.preferences.value.copy(showMemberVideos = true, continueNext = true, aiFilter = settings))
        ui { vm.store.pip = false; vm.store.background = true; vm.play("testvideo01") }
        until { var ready = false; ui { ready = vm.controller.value?.playbackState == Player.STATE_READY }; !vm.queue.value.loading && ready }
        until { vm.aiFilter.state.value.matches.size == 2 }
        ui { vm.controller.value!!.seekTo(vm.controller.value!!.duration - 500); vm.controller.value!!.play(); activity.moveTaskToBack(true) }
        until { vm.queue.value.current?.video?.id == "othervideo1" && !vm.queue.value.loading }
        assertTrue(vm.queue.value.items.none { it.video.id == "testvideo02" || it.video.id == "membervid01" })
        ui { vm.play("testvideo02") }
        until { vm.queue.value.current?.video?.id == "testvideo02" && !vm.queue.value.loading }
    }
    @Test fun unavailableServerExplainsCompatibilityAndKeepsVideoCardsAccessible() {
        command("ai", """{"aiMissing":true}""")
        save(vm.preferences.value.copy(aiFilter = ai(AiPageGroup.FEEDS, AiAction.HIDE)))
        until { vm.aiFilter.state.value.error != null }
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        settings()
        compose.onNodeWithTag("ai-status-error").assertTextContains("Update this server", substring = true)
    }
    @Test fun failedAccountSaveRetainsDraftAndListStatusExplainsStaleData() {
        ui { vm.action { vm.login("AiViewer", "fixture-password") } }
        until { vm.account.value != null && vm.tab == "You" && !vm.browse.value.loading }
        command("ai", """{"aiStale":["warnlist"]}""")
        command("sponsorblock", """{"failPreferences":true}""")
        settings(); choose(AiPageGroup.OTHER,AiListKind.WARNLIST,"Replace thumbnails")
        until {vm.preferenceSaves.edits.value.values.any {it.error!=null}}
        compose.onNodeWithText("Retry").assertExists()
        assertEquals(AiAction.OFF, vm.preferences.value.aiFilter.saved(AiListKind.WARNLIST, AiPageGroup.OTHER))
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("ai-list-details"))
        compose.onNodeWithTag("ai-list-details").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("ai-status-warnlist"))
        compose.onNodeWithTag("ai-status-warnlist").assertTextContains("Stale", substring = true)
        screenshot("list-status-stale")
        command("sponsorblock", """{"failPreferences":false}""")
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.finishAutomaticPreferencePage()
        until { vm.preferences.value.aiFilter.saved(AiListKind.WARNLIST, AiPageGroup.OTHER) == AiAction.REPLACE }
        assertFalse(vm.preferences.value.aiFilter.enabled)
    }
}
