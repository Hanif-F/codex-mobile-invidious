package net.wingress.mobivious

import android.content.Intent
import android.app.Activity
import android.app.Instrumentation
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.URL

/** Uses only the disposable localhost API fixture, like AppSmokeTest. */
@RunWith(AndroidJUnit4::class)
class PlaylistRssSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
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
    @Test fun allSubscriptionExportsOpenSaveAndShareTheSelectedBodyAndMimeType() {
        signIn()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intents = CopyOnWriteArrayList<Intent>()
        val target = File(activity.cacheDir, "rss/test-save-result").apply { parentFile!!.mkdirs(); writeText("untouched") }
        val uri = FileProvider.getUriForFile(activity, "${BuildConfig.APPLICATION_ID}.rss", target)
        var saveResult = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        var switchAccountOnSave = false
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action !in listOf(Intent.ACTION_VIEW, Intent.ACTION_CHOOSER, Intent.ACTION_CREATE_DOCUMENT)) return null
                intents.add(Intent(intent))
                if (switchAccountOnSave && intent.action == Intent.ACTION_CREATE_DOCUMENT) vm.store.save(null)
                return if (intent.action == Intent.ACTION_CREATE_DOCUMENT) saveResult else Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.runOnUiThread { vm.openSubscriptionExport() }
            until { vm.rss.value.body != null }
            for (format in SubscriptionExportFormat.entries) {
                compose.onNodeWithTag("feed-format-${format.apiValue}").performScrollTo().performClick()
                until { !vm.rss.value.loading && vm.rss.value.exportFormat == format && vm.rss.value.body != null }
                val selected = vm.rss.value
                assertEquals(format.filename, selected.filename)
                if (format == SubscriptionExportFormat.NEWPIPE_JSON) assertTrue(JSONObject(selected.body!!).has("subscriptions"))
                else assertTrue(selected.body!!.startsWith("<opml"))
                assertFalse(selected.body!!.contains("fixture-token"))
                val count = intents.size
                compose.onNodeWithText("Open", substring = false).performScrollTo().performClick()
                until { intents.size > count }
                val opened = intents.last()
                assertEquals(Intent.ACTION_VIEW, opened.action); assertEquals(format.mimeType, opened.type)
                assertTrue(opened.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(selected.body, activity.contentResolver.openInputStream(opened.data!!)!!.bufferedReader().use { it.readText() })
                val shareCount = intents.size
                compose.onNodeWithText("Share", substring = false).performScrollTo().performClick()
                until { intents.size > shareCount }
                @Suppress("DEPRECATION") val send = intents.last().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, send.action); assertEquals(format.mimeType, send.type)
                @Suppress("DEPRECATION") val shared = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
                assertEquals(format.filename, shared.lastPathSegment)
                assertEquals(selected.body, activity.contentResolver.openInputStream(shared)!!.bufferedReader().use { it.readText() })
                target.writeText("untouched")
                saveResult = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                val cancelCount = intents.size
                compose.onNodeWithText("Save file").performScrollTo().performClick()
                until { intents.size > cancelCount }
                compose.waitForIdle(); assertEquals("untouched", target.readText())
                saveResult = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
                compose.onNodeWithText("Save file").performScrollTo().performClick()
                until { target.readText() == selected.body }
                assertEquals(format.mimeType, intents.last().type)
                assertEquals(format.filename, intents.last().getStringExtra(Intent.EXTRA_TITLE))
            }
            captureWatchScreenshot("subscription-export-sheet")
            target.writeText("untouched")
            switchAccountOnSave = true
            compose.onNodeWithText("Save file").performScrollTo().performClick()
            until { !vm.rss.value.open }
            assertEquals("untouched", target.readText())
        } finally { instrumentation.removeMonitor(monitor); target.delete() }
    }

    @Test fun subscriptionExportRetryLegacyServersAndLateFormatsNeverReplaceTheCurrentSelection() {
        signIn()
        command("playlist-rss", """{"exportFailNext":true}""")
        compose.runOnUiThread { vm.openSubscriptionExport(SubscriptionExportFormat.NEWPIPE_JSON) }
        until { vm.rss.value.error != null }
        compose.onNodeWithText("Retry").performClick()
        until { vm.rss.value.body != null && vm.rss.value.error == null }
        command("playlist-rss", """{"exportLegacy":true}""")
        compose.runOnUiThread { vm.openSubscriptionExport(SubscriptionExportFormat.FREETUBE_OPML) }
        until { vm.rss.value.error?.contains("subscription export update") == true }
        compose.onNodeWithTag("feed-format-rss").performScrollTo().performClick()
        until { vm.rss.value.body != null && vm.rss.value.error == null }
        command("playlist-rss", """{"exportLegacy":false,"exportDelayNext":1000,"exportFailNext":true}""")
        compose.runOnUiThread { vm.openSubscriptionExport(SubscriptionExportFormat.NEWPIPE_JSON) }
        until {
            val requests = JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONArray("exportRequests")
            (0 until requests.length()).any { !requests.getJSONObject(it).getBoolean("completed") }
        }
        compose.onNodeWithTag("feed-format-opml").performScrollTo().performClick()
        until { vm.rss.value.exportFormat == SubscriptionExportFormat.FREETUBE_OPML && vm.rss.value.body != null }
        until {
            val requests = JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONArray("exportRequests")
            (0 until requests.length()).all { requests.getJSONObject(it).getBoolean("completed") }
        }
        assertEquals(SubscriptionExportFormat.FREETUBE_OPML, vm.rss.value.exportFormat)
        assertNull(vm.rss.value.error); assertTrue(vm.rss.value.body!!.startsWith("<opml"))
        val old = activity
        compose.runOnUiThread { old.recreate() }
        until { old.isDestroyed }
        compose.runOnUiThread {
            activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
        }
        assertEquals(SubscriptionExportFormat.FREETUBE_OPML, vm.rss.value.exportFormat)
        assertNotNull(vm.rss.value.body)
        command("playlist-rss", """{"exportDelayNext":1000}""")
        compose.runOnUiThread { vm.openSubscriptionExport(SubscriptionExportFormat.NEWPIPE_JSON); vm.dismissRss() }
        assertFalse(vm.rss.value.open)
        compose.runOnUiThread { vm.openSubscriptionExport(); vm.store.save(null) }
        until { !vm.rss.value.open }
        assertNull(vm.rss.value.body)
        signIn()
        compose.runOnUiThread { vm.openSubscriptionExport(); vm.switchServer("http://localhost:18080") }
        until { !vm.rss.value.open }
        assertNull(vm.rss.value.body)
    }
    @Before fun launch() {
        command("reset"); command("playlist-rss")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread { vm.store.save(null); vm.switchServer("http://127.0.0.1:18080"); vm.store.guestPreferences(AccountPreferences()); vm.navigate("Popular") }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
    }
    @After fun close() { if (::activity.isInitialized) compose.runOnUiThread { vm.closePlayer(); activity.finishAndRemoveTask() } }

    @Test fun pendingSubscriptionAtSignInKeepsOwnedAndSubscribedPlaylists() {
        val source = Playlist("PLlive", "Live owner playlist", 2)
        compose.runOnUiThread { vm.preparePlaylistSubscription(source) }
        signIn()
        until { source.id !in vm.playlistBusy.value && vm.playlists.value.any { it.owned } &&
            vm.playlists.value.any { it.id == source.id && it.saved } }
        compose.runOnUiThread { vm.navigate("You") }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("you-playlists").performClick()
        until { vm.route == "playlists" && !vm.browse.value.loading }
        compose.onNodeWithText("My playlists (1)").assertExists()
        compose.onNodeWithTag("playlist-section-subscribed").performClick()
        compose.onNodeWithText("Subscribed playlists (1)").assertExists()
    }

    @Test fun libraryGroupsAndReadOnlySubscriptionsFollowOwnerUpdates() {
        signIn()
        val source = Playlist("IVother", "Live owner playlist", 2)
        compose.runOnUiThread { vm.subscribePlaylist(source, true) }
        until { vm.playlists.value.any { it.id == source.id && it.saved } }
        compose.runOnUiThread { vm.navigate("You") }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("you-playlists").performClick()
        until { vm.route == "playlists" && !vm.browse.value.loading }
        compose.onNodeWithText("My playlists (1)").assertExists()
        compose.onNodeWithTag("playlist-section-subscribed").performClick()
        compose.onNodeWithText("Subscribed playlists (1)").assertExists()
        compose.onNodeWithTag("playlist-library-list").performScrollToNode(hasTestTag("playlist-card-IVother"))
        compose.onNodeWithTag("playlist-card-IVother").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
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
        compose.runOnUiThread { vm.navigate("You") }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("you-playlists").performClick()
        until { vm.route == "playlists" && !vm.browse.value.loading }
        compose.onNodeWithTag("playlist-section-subscribed").performClick()
        compose.onNodeWithTag("playlist-library-list").performScrollToNode(hasTestTag("playlist-subscribe-IVother"))
        compose.onNodeWithTag("playlist-subscribe-IVother").performClick()
        compose.onNodeWithTag("subscription-unsubscribe").performClick()
        until { vm.playlists.value.none { it.id == source.id } }
        assertNull(vm.playlist.value)
        assertEquals("You", vm.tab)
        assertEquals("playlists", vm.route)
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
        assertEquals("You", vm.tab)
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
        compose.runOnUiThread { vm.navigate("Search"); vm.setSearchType(SearchType.PLAYLISTS); vm.editSearch("music", false); vm.submitSearch(false) }
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
        until { vm.rss.value.body != null }
        assertNull(vm.rss.value.url)
        compose.onNodeWithText("Save file").assertExists()
        compose.onNodeWithContentDescription("Close feed").performClick()
        compose.onNodeWithTag("subscription-actions").performClick()
        compose.onNodeWithText("Export subscriptions").performClick()
        until { vm.rss.value.body != null }
        compose.onNodeWithText("YouTube OPML").performClick()
        until { vm.rss.value.body?.contains("youtube.com/feeds/") == true }
        compose.runOnUiThread { vm.store.save(null) }
        until { !vm.rss.value.open && vm.playlists.value.isEmpty() }
    }
}
