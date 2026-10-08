package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
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
class VisibilitySmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val first = "UC" + "a".repeat(22)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(body: String, path: String = "visibility") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("{}", "reset")
        command("""{"visibilityVideos":true,"seedPlaylist":true}""")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null)
            activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(AccountPreferences())
            activity.model.saveSearchVisibility(SearchVisibility())
            activity.model.refreshSharedSettings(); activity.model.navigate("Home")
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.size == 3 }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.clearVisibilitySnapshot(activity.model.api.context())
            activity.model.store.save(null); activity.model.store.guestDeArrow(AccountPreferences())
            activity.model.saveSearchVisibility(SearchVisibility()); activity.finishAndRemoveTask()
        }
    }
    private fun login() {
        compose.runOnUiThread { activity.model.action { activity.model.login("VisibilityViewer", "fixture-password") } }
        until { activity.model.account.value != null && activity.model.blocked.value.loaded && !activity.model.blocked.value.loading && activity.model.tab == "Account" }
        navigate("Home")
    }
    private fun navigate(tab: String, route: String = "") {
        compose.runOnUiThread { activity.model.query = "fixture"; activity.model.navigate(tab, route) }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
    }
    private fun manager() {
        compose.onNodeWithTag("navigation-Account").performClick(); compose.onNodeWithTag("account-settings").performClick()
        compose.onNodeWithText("Browsing", substring = false).performClick()
        compose.onNodeWithText("Blocked channels", substring = false).performClick()
        compose.onNodeWithTag("blocked-channel-manager").assertIsDisplayed()
    }
    private fun save(next: AccountPreferences) {
        compose.runOnUiThread { val vm = activity.model; vm.action { vm.savePreferences(next, vm.preferences.value, vm.api.context()) } }
        until { activity.model.preferences.value == next }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/mobivious-visibility-screenshots",
            "screencap -p /data/local/tmp/mobivious-visibility-screenshots/$name.png").forEach {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream -> stream.readBytes() }
        }
    }

    @Test fun guestBrowsingSettingAndBadgesCoverCompactTextOnlyLayouts() {
        compose.onNodeWithTag("video-card-membervid01").assertDoesNotExist()
        compose.onNodeWithTag("navigation-Account").performClick(); compose.onNodeWithTag("account-settings").performClick()
        compose.onNodeWithText("Browsing", substring = false).performClick()
        compose.onNodeWithText("Show members-only videos", substring = false).performClick()
        compose.onNodeWithTag("settings-save").performClick()
        until { activity.model.preferences.value.showMemberVideos }
        compose.onNodeWithContentDescription("Back from Settings").performClick()
        compose.onNodeWithTag("navigation-Home").performClick()
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("video-card-membervid01"))
        compose.onNodeWithTag("video-members-membervid01", useUnmergedTree = true).assertIsDisplayed()
        save(activity.model.preferences.value.copy(thinMode = true, uiDensity = "compact", darkMode = "dark"))
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("video-card-membervid01"))
        compose.onNodeWithTag("video-members-membervid01", useUnmergedTree = true).assertIsDisplayed()
        screenshot("members-compact-dark")
        assertTrue(activity.model.store.guestDeArrow().showMemberVideos)
    }
    @Test fun hiddenSearchPagesRemainReachableAndSearchOverrideCanReset() {
        command("""{"hiddenFirstPage":true}""")
        navigate("Search")
        compose.onNodeWithTag("video-card-membervid01").assertDoesNotExist()
        compose.onNodeWithText("Videos hidden by your visibility settings").assertIsDisplayed()
        compose.onNodeWithText("Load more").performClick()
        until { activity.model.browse.value.page == 2 && !activity.model.browse.value.loading }
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        compose.onNodeWithContentDescription("Search filters").performClick()
        compose.onNodeWithTag("search-show-members").performClick()
        compose.onNodeWithText("Apply").performClick()
        until { activity.model.searchVisibility.value.showMembers == true && !activity.model.browse.value.loading }
        compose.onNodeWithTag("video-card-membervid01").assertExists()
        assertFalse(activity.model.preferences.value.showMemberVideos)
        compose.onNodeWithContentDescription("Search filters").performClick()
        compose.onNodeWithTag("search-members-reset").performClick()
        compose.onNodeWithText("Apply").performClick()
        until { activity.model.searchVisibility.value.showMembers == null && !activity.model.browse.value.loading }
        compose.onNodeWithTag("video-card-membervid01").assertDoesNotExist()
        assertNull(activity.model.store.searchVisibility(activity.model.api.context()).showMembers)
        assertFalse(fixture().getJSONArray("visibilityReads").getJSONObject(0).getBoolean("authorized"))
    }
    @Test fun cardBlockSearchOverrideAndChannelHeaderPreserveLibraryAccess() {
        login()
        compose.onNodeWithTag("video-actions-testvideo01").performClick()
        compose.onNodeWithText("Block channel", substring = false).performClick()
        until { first in activity.model.blocked.value.ids && activity.model.blocked.value.busy.isEmpty() }
        compose.onNodeWithTag("video-card-testvideo01").assertDoesNotExist()
        navigate("Search")
        compose.onNodeWithTag("video-card-testvideo01").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search filters").performClick()
        compose.onNodeWithTag("search-include-blocked").performClick()
        compose.onNodeWithText("Apply").performClick()
        until { activity.model.searchVisibility.value.includeBlocked && !activity.model.browse.value.loading }
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        navigate("Subscriptions")
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        navigate("Library", "playlist:IVfixture")
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        compose.onNodeWithTag("video-card-membervid01").assertDoesNotExist()
        navigate("Home", "channel:$first")
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        compose.onNodeWithTag("channel-actions-$first").performScrollTo().performClick()
        compose.onNodeWithTag("channel-block-$first").performClick()
        until { first !in activity.model.blocked.value.ids }
    }
    @Test fun managerRetainsFailedChangesAndRefreshesWebsiteState() {
        login()
        command("""{"blockedChannels":{"$first":"Mobivious Studio"}}""")
        manager()
        until { first in activity.model.blocked.value.ids && !activity.model.blocked.value.loading }
        command("""{"failBlockedWrite":true}""")
        compose.onNodeWithTag("unblock-$first").performClick()
        until { activity.model.blocked.value.actionErrors[first] != null }
        compose.onNodeWithTag("blocked-action-error-$first").assertIsDisplayed()
        compose.onNodeWithTag("unblock-$first").assertExists()
        command("""{"failBlockedRead":true}""")
        compose.onNodeWithText("Refresh blocked channels").performClick()
        until { activity.model.blocked.value.error != null }
        compose.onNodeWithTag("blocked-refresh-error").assertIsDisplayed()
        assertEquals(first, activity.model.store.blockedSnapshot(activity.model.api.context())!!.single().id)
        screenshot("blocked-manager-failure")
        command("""{"failBlockedRead":false,"failBlockedWrite":false}""")
        compose.onNodeWithTag("unblock-$first").performClick()
        until { first !in activity.model.blocked.value.ids && !activity.model.blocked.value.loading }
        compose.onNodeWithText("No blocked channels").assertIsDisplayed()
    }
    @Test fun directMemberPlaybackChannelBlockingAndRecommendationsKeepPlayerRunning() {
        login()
        command("""{"memberCurrent":true}""")
        compose.onNodeWithText("A quiet moment · playback fixture", substring = false).performClick()
        until { activity.model.playback.value.details != null && activity.model.playback.value.duration > 0 }
        compose.onNodeWithTag("video-members-testvideo01", useUnmergedTree = true).assertExists()
        assertFalse(activity.model.preferences.value.showMemberVideos)
        val media = activity.model.playback.value.mediaId
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("watch-actions"))
        compose.onNodeWithTag("watch-actions-menu").assertDoesNotExist()
        compose.onNodeWithContentDescription("Video actions").assertDoesNotExist()
        compose.onNodeWithTag("playback-queue").assertDoesNotExist()
        compose.onAllNodesWithText("Mobivious Studio", substring = false)[0].performClick()
        until { activity.model.channel.value != null && !activity.model.browse.value.loading }
        command("""{"failBlockedWrite":true}""")
        compose.onNodeWithTag("channel-actions-$first").performClick()
        compose.onNodeWithTag("channel-block-$first").performClick()
        until { activity.model.blocked.value.actionErrors[first] != null }
        compose.onNodeWithTag("channel-block-$first").assertDoesNotExist()
        compose.onNodeWithTag("channel-block-error-$first").performScrollTo().assertIsDisplayed()
        assertEquals(media, activity.model.playback.value.mediaId)
        command("""{"failBlockedWrite":false}""")
        compose.onNodeWithTag("channel-actions-$first").performScrollTo().performClick()
        compose.onNodeWithTag("channel-block-$first").performClick()
        until { first in activity.model.blocked.value.ids }
        assertEquals(media, activity.model.playback.value.mediaId)
        compose.onNodeWithTag("mini-player-preview").performClick()
        compose.onNodeWithTag("watch-details-list").assertExists()
        assertTrue(activity.model.visibleVideos(activity.model.playback.value.details!!.recommendations, ContentSurface.RECOMMENDATIONS).none { it.channelId == first || it.membersOnly })
        save(activity.model.preferences.value.copy(showMemberVideos = true))
        assertTrue(fixture().getJSONObject("preferences").getBoolean("show_member_videos"))
        assertEquals(media, activity.model.playback.value.mediaId)
    }
    @Test fun savedSearchOverridesAndSnapshotsAreOwnedByEachAccountAndInstance() {
        val store = activity.model.store
        val guest = activity.model.api.context()
        store.saveSearchVisibility(guest, SearchVisibility(false))
        compose.runOnUiThread { store.save(Account("fixture-token", "Alice", Long.MAX_VALUE, guest.server)) }
        until { activity.model.blocked.value.loaded && !activity.model.blocked.value.loading }
        val alice = activity.model.api.context()
        val bob = alice.copy(account = alice.account!!.copy(username = "Bob"))
        val another = alice.copy(server = "https://another.instance")
        store.saveSearchVisibility(alice, SearchVisibility(true, true))
        store.saveBlockedSnapshot(alice, listOf(BlockedChannel(first, "Mobivious Studio")))
        assertEquals(SearchVisibility(false), store.searchVisibility(guest))
        assertEquals(SearchVisibility(true, true), store.searchVisibility(alice))
        assertEquals(SearchVisibility(), store.searchVisibility(bob))
        assertEquals(SearchVisibility(), store.searchVisibility(another))
        assertNull(store.blockedSnapshot(guest)); assertNull(store.blockedSnapshot(bob)); assertNull(store.blockedSnapshot(another))
        assertEquals(first, SessionStore(activity).blockedSnapshot(alice)!!.single().id)
        store.clearVisibilitySnapshot(alice)
    }
}
