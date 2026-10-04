package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Uses only the disposable localhost API fixture, like AppSmokeTest. */
@RunWith(AndroidJUnit4::class)
class PlaylistRssSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private fun until(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun signIn() {
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Viewer", Long.MAX_VALUE, "http://127.0.0.1:18080")) }
        until { vm.playlists.value.any { it.owned } }
    }
    @Before fun launch() {
        command("reset"); command("playlist-rss")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread { vm.store.save(null); vm.switchServer("http://127.0.0.1:18080"); vm.store.guestDeArrow(AccountPreferences()); vm.navigate("Home") }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
    }
    @After fun close() { if (::activity.isInitialized) compose.runOnUiThread { vm.closePlayer(); activity.finishAndRemoveTask() } }

    @Test fun libraryGroupsAndReadOnlySubscriptionsFollowOwnerUpdates() {
        signIn()
        val source = Playlist("IVother", "Live owner playlist", 2)
        compose.runOnUiThread { vm.subscribePlaylist(source, true) }
        until { vm.playlists.value.any { it.id == source.id && it.saved } }
        compose.runOnUiThread { vm.navigate("Library") }
        until { !vm.browse.value.loading }
        compose.onNodeWithText("My playlists (1)").assertExists()
        compose.onNodeWithText("Subscribed playlists (1)").assertExists()
        compose.onNodeWithTag("playlist-card-IVother").performClick()
        until { vm.playlist.value?.id == source.id && !vm.browse.value.loading }
        compose.onNodeWithTag("playlist-actions-IVother").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-edit").assertDoesNotExist()
        compose.onNodeWithTag("playlist-delete").assertDoesNotExist()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        command("playlist-rss", """{"sourceTitle":"Owner updated playlist"}""")
        compose.runOnUiThread { vm.refresh() }
        until { vm.playlist.value?.title == "Owner updated playlist" && !vm.browse.value.loading }
        compose.runOnUiThread { vm.play("testvideo01", source = source.id, index = 0) }
        until { vm.queue.value.source?.id == source.id }
        val queueToken = vm.queue.value.token
        compose.runOnUiThread { vm.subscribePlaylist(vm.playlist.value!!, false) }
        until { vm.playlists.value.none { it.id == source.id } }
        assertEquals(queueToken, vm.queue.value.token)
        compose.runOnUiThread { vm.openPlaylist(vm.playlists.value.single { it.owned }) }
        until { vm.playlist.value?.owned == true && !vm.browse.value.loading }
        compose.onNodeWithTag("playlist-actions-${vm.playlist.value!!.id}").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-edit").assertExists()
    }

    @Test fun nestedPlaylistSubscriptionDoesNotOpenThePlaylist() {
        signIn()
        val source = Playlist("IVother", "Live owner playlist", 2)
        compose.runOnUiThread { vm.subscribePlaylist(source, true) }
        until { vm.playlists.value.any { it.id == source.id && it.saved } }
        compose.runOnUiThread { vm.navigate("Library") }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("library-playlist-list").performScrollToNode(hasTestTag("playlist-subscribe-IVother"))
        compose.onNodeWithTag("playlist-subscribe-IVother").performClick()
        until { vm.playlists.value.none { it.id == source.id } }
        assertNull(vm.playlist.value)
        assertEquals("Library", vm.tab)
        assertEquals("", vm.route)
    }

    @Test fun ownedPlaylistDeletionStillRequiresConfirmation() {
        signIn()
        val list = vm.playlists.value.single { it.owned }
        compose.runOnUiThread { vm.openPlaylist(list) }
        until { !vm.browse.value.loading && vm.playlist.value?.id == list.id }
        compose.onNodeWithTag("playlist-actions-${list.id}").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-delete").performClick()
        compose.onNodeWithText("Delete playlist?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(vm.playlists.value.any { it.id == list.id })
        compose.onNodeWithTag("playlist-actions-${list.id}").performClick()
        compose.onNodeWithTag("playlist-delete").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        until { vm.playlists.value.none { it.id == list.id } && vm.route.isEmpty() }
        assertEquals("Library", vm.tab)
    }

    @Test fun signInRetainsSubscriptionIntentAndFailureCanRetry() {
        val source = Playlist("RDopaque", "Fixture mix", -1, seedVideoId = "testvideo01")
        compose.runOnUiThread { vm.preparePlaylistSubscription(source) }
        signIn()
        until { vm.playlists.value.any { it.id == source.id && it.seedVideoId == "testvideo01" } }
        command("playlist-rss", """{"failSubscribe":true}""")
        val other = Playlist("PLlive", "Live owner playlist", 2)
        compose.runOnUiThread { vm.subscribePlaylist(other, true); vm.subscribePlaylist(other, true) }
        until { vm.playlistErrors.value.containsKey(other.id) && vm.playlistBusy.value.isEmpty() }
        compose.runOnUiThread { vm.subscribePlaylist(other, true) }
        until { vm.playlists.value.any { it.id == other.id } }
        assertEquals(1, vm.playlists.value.count { it.id == other.id })
    }

    @Test fun typedDiscoveryAndChannelPaginationKeepMixSeeds() {
        compose.runOnUiThread { vm.navigate("Search"); vm.setPlaylistSearch(true); vm.editSearch("music", false); vm.submitSearch(false) }
        until { !vm.browse.value.loading && vm.browse.value.lists.isNotEmpty() }
        assertTrue(vm.browse.value.videos.isEmpty())
        val mix = vm.browse.value.lists.single { it.mix }
        assertEquals("testvideo01", mix.seedVideoId)
        compose.runOnUiThread { vm.openPlaylist(mix) }
        until { vm.playlist.value?.mix == true && !vm.browse.value.loading }
        assertEquals("testvideo01", vm.playlist.value?.seedVideoId)
        compose.runOnUiThread { vm.navigate("Channel", "channel:UC" + "a".repeat(22)) }
        until { vm.channel.value != null && !vm.browse.value.loading }
        compose.runOnUiThread { vm.selectChannelTab(ChannelTab.PLAYLISTS) }
        until { vm.browse.value.lists.isNotEmpty() && !vm.browse.value.loading }
        compose.runOnUiThread { vm.more() }
        until { vm.browse.value.end && !vm.browse.value.loading }
        assertEquals(listOf("PLlive", "RDopaque"), vm.browse.value.lists.map { it.id })
        compose.runOnUiThread { vm.setChannelPlaylistSort("oldest") }
        until { !vm.browse.value.loading && vm.browse.value.lists.size == 1 }
        compose.runOnUiThread { vm.editSearch("channel query", true); vm.submitSearch(true) }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        compose.runOnUiThread { vm.clearScopedSearch() }
        until { !vm.browse.value.loading && vm.browse.value.lists.isNotEmpty() }
        assertEquals(ChannelTab.PLAYLISTS, vm.channelTab.value)
    }

    @Test fun rssSheetsOfferCorrectLinksFilesAndClearOnAccountChange() {
        signIn()
        compose.runOnUiThread { vm.navigate("Subscriptions") }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("subscription-actions").performClick()
        compose.onNodeWithText("RSS").performClick()
        until { vm.rss.value.url != null }
        assertTrue(vm.rss.value.url!!.contains("fixture-rss-secret"))
        assertFalse(vm.rss.value.url!!.contains("fixture-token"))
        compose.onNodeWithText("Copy").assertExists()
        compose.runOnUiThread { vm.dismissRss(); vm.openPlaylistRss(vm.playlists.value.single { it.owned }) }
        until { vm.rss.value.xml != null }
        assertNull(vm.rss.value.url)
        compose.onNodeWithText("Save file").assertExists()
        compose.onNodeWithContentDescription("Close feed").performClick()
        compose.onNodeWithTag("subscription-actions").performClick()
        compose.onNodeWithText("Export OPML").performClick()
        until { vm.rss.value.xml != null }
        compose.onNodeWithText("YouTube feeds").performClick()
        until { vm.rss.value.xml?.contains("youtube.com/feeds/") == true }
        compose.runOnUiThread { vm.store.save(null) }
        until { !vm.rss.value.open && vm.playlists.value.isEmpty() }
    }
}
