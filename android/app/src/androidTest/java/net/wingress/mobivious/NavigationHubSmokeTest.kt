package net.wingress.mobivious

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import java.net.HttpURLConnection
import java.net.URL

/** Navigation and presentation acceptance against the disposable local instance. */
@RunWith(AndroidJUnit4::class)
class NavigationHubSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Compose idle does not cover Android's density/rotation window animation.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(750, 5_000)
        shell("mkdir -p /data/local/tmp/mobivious-navigation-screenshots")
        shell("screencap -p /data/local/tmp/mobivious-navigation-screenshots/$name.png")
    }
    private fun currentActivity() {
        compose.runOnUiThread {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()?.let { activity = it }
        }
    }
    private fun back() {
        compose.waitForIdle()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun tab(name: String) {
        if (name in listOf("Popular", "Trending")) compose.selectDiscoveryFeed(name) else compose.onNodeWithTag("navigation-$name").performClick()
        until { vm.tab == name && vm.route.isEmpty() && !vm.browse.value.loading }
        compose.waitForIdle()
    }
    private fun accountSettings() {
        compose.openAppSettings()
        compose.onNodeWithText("Account", substring = false).performClick()
    }
    private fun login() {
        compose.runOnUiThread { vm.action { vm.login("Fixture", "fixture-password") } }
        until { vm.account.value != null && vm.tab == "You" && !vm.browse.value.loading && !vm.accountBusy.value && vm.dearrowIdentity.value != null && vm.preferences.value.savePosition }
        compose.waitForIdle()
    }
    @Before fun launch() {
        command("reset")
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
            Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() && activity.window.decorView.hasWindowFocus() }
    }
    @After fun close() {
        if (::activity.isInitialized) {
            currentActivity()
            compose.runOnUiThread { vm.closePlayer(); vm.store.save(null); vm.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask() }
        }
    }

    @Test fun feedsKeepIndependentScrollAndSettingsSearchAndChannelReturnToTheirOrigin() {
        command("watched", """{"indicatorVideos":true}""")
        compose.runOnUiThread { vm.refresh() }
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 4 }
        compose.onNodeWithTag("browse-video-list").performScrollToIndex(2)
        compose.waitForIdle()
        val popular = vm.browse.value
        assertTrue(popular.position.index > 0)
        tab("Trending")
        compose.onNodeWithTag("browse-video-list").performScrollToIndex(1)
        compose.waitForIdle()
        val trending = vm.browse.value
        tab("Popular")
        assertEquals(popular.videos, vm.browse.value.videos)
        assertEquals(popular.position, vm.browse.value.position)
        tab("Trending")
        assertEquals(trending.position, vm.browse.value.position)
        compose.openAppSettings()
        back(); tab("Trending")
        assertEquals(trending.position, vm.browse.value.position)
        compose.openGlobalSearch()
        compose.onNodeWithTag("main-search").performTextInput("fixture")
        compose.onNodeWithTag("main-search").performImeAction()
        until { vm.tab == "Search" && !vm.browse.value.loading }
        back()
        assertEquals("Trending", vm.tab)
        assertEquals(trending.position, vm.browse.value.position)
        compose.runOnUiThread { vm.navigate("Trending", "channel:" + "UC" + "a".repeat(22)) }
        until { vm.channel.value != null && !vm.browse.value.loading }
        back()
        assertEquals("Trending", vm.tab)
        assertEquals(trending.position, vm.browse.value.position)
        screenshot("trending-restored")
    }

    @Test fun libraryHubOwnsHistoryClipsAndPlaylistsAndAccountManagementLivesInSettings() {
        login()
        compose.onNodeWithTag("you-identity").assertIsDisplayed()
        compose.onNodeWithText("Change username").assertDoesNotExist()
        compose.onNodeWithText("Sessions & API tokens").assertDoesNotExist()
        compose.onNodeWithTag("you-history").assertIsDisplayed()
        compose.onNodeWithTag("you-clips").assertIsDisplayed()
        screenshot("you-light")
        compose.onNodeWithTag("you-history").performClick()
        until { vm.route == "history" && !vm.browse.value.loading }
        back()
        compose.onNodeWithTag("you-identity").assertIsDisplayed()
        compose.onNodeWithTag("you-clips").performClick()
        until { vm.route == "clips" && !vm.browse.value.loading }
        back()
        compose.onNodeWithTag("you-playlists").performClick()
        until { vm.route == "playlists" && !vm.browse.value.loading }
        compose.onNodeWithTag("playlist-section-subscribed").performClick()
        compose.onNodeWithText("Subscribed playlists (0)").assertIsDisplayed()
        back()
        compose.waitForIdle()
        val position = vm.browse.value.position
        tab("Popular"); tab("You")
        assertEquals(position, vm.browse.value.position)
        accountSettings()
        compose.onNodeWithText("Change username").assertIsDisplayed()
        compose.onNodeWithText("Change password").assertIsDisplayed()
        compose.onNodeWithText("Sessions & API tokens").assertIsDisplayed()
        screenshot("account-settings")
        back(); screenshot("settings-root"); back()
        assertEquals(position, vm.browse.value.position)
    }

    @Test fun guestLibraryDestinationsReturnFromAuthenticationAndKeepDownloadsPublic() {
        tab("You")
        for (route in listOf("playlists", "history", "clips")) {
            compose.onNodeWithTag("you-$route").performScrollTo().performClick()
            until { vm.route == route && !vm.browse.value.loading }
            compose.onNodeWithText("Sign in", substring = false).performClick()
            compose.onNodeWithTag("sign-in-screen").assertExists()
            back()
            assertEquals(route, vm.route)
            back()
            compose.onNodeWithTag("you-guest").assertExists()
        }
        compose.onNodeWithTag("you-downloads").performScrollTo().performClick()
        compose.onNodeWithTag("downloads-page").assertExists()
        assertNull(vm.account.value)
        back()
        compose.onNodeWithTag("global-settings").assertIsDisplayed()
    }

    @Test fun guestAuthenticationBackReturnsToYouOrTheSettingsAccountPage() {
        tab("You")
        compose.onNodeWithTag("you-guest").assertExists()
        compose.onNodeWithTag("you-sign-in").performClick()
        compose.onNodeWithTag("account-username").assertExists()
        back()
        compose.onNodeWithTag("you-guest").assertExists()
        accountSettings()
        compose.onNodeWithText("Sign in", substring = false).performClick()
        compose.onNodeWithTag("sign-in-screen").assertExists()
        back()
        compose.onNodeWithContentDescription("Back from Account").assertExists()
        compose.onNodeWithText("Sign in to manage your account").assertIsDisplayed()
    }

    @Test fun feedPreferencesAndAccountInstanceChangesInvalidateSavedTabs() {
        tab("Trending")
        val requests = fixture().getJSONArray("discoveryRequests").length()
        tab("Popular")
        compose.runOnUiThread {
            val before = vm.preferences.value
            vm.action { vm.savePreferences(before.copy(region = "ID"), before, vm.api.context()) }
        }
        until { vm.preferences.value.region == "ID" && !vm.browse.value.loading }
        tab("Trending")
        assertTrue(fixture().getJSONArray("discoveryRequests").length() > requests)
        compose.onNodeWithText("Trending in ID").assertExists()
        login()
        compose.runOnUiThread { vm.logout() }
        until { vm.account.value == null && vm.tab == "You" && !vm.accountBusy.value }
        compose.onNodeWithTag("you-guest").assertExists()
        compose.runOnUiThread { vm.switchServer("http://localhost:18080") }
        until { !vm.browse.value.loading && vm.tab == "Popular" }
        assertEquals("US", vm.preferences.value.region)
        tab("Trending")
        compose.onNodeWithText("Trending in US").assertExists()
    }

    @Test fun settingsAndTabSwitchesKeepServicePlaybackActive() {
        login()
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")) }
        until { vm.playback.value.playing }
        tab("Trending"); tab("You")
        val token = vm.queue.value.token
        compose.openAppSettings()
        compose.onNodeWithText("Account", substring = false).performClick()
        assertTrue(vm.playback.value.playing)
        assertEquals(token, vm.queue.value.token)
        back(); back()
        assertTrue(vm.playback.value.playing)
        compose.onNodeWithTag("you-library-list").assertExists()
    }

    @Test fun regionChangesDuringSearchOrChildNavigationReloadTheReturningFeed() {
        tab("Trending")
        for ((region, child) in listOf("ID" to false, "JP" to true)) {
            val requests = fixture().getJSONArray("discoveryRequests").length()
            compose.runOnUiThread {
                if (child) vm.navigate("Trending", "channel:" + "UC" + "a".repeat(22))
                else vm.openGlobalSearch("fixture")
            }
            until { !vm.browse.value.loading }
            compose.runOnUiThread {
                val before = vm.preferences.value
                vm.action { vm.savePreferences(before.copy(region = region), before, vm.api.context()) }
            }
            until { vm.preferences.value.region == region }
            back()
            until { vm.tab == "Trending" && vm.route.isEmpty() && !vm.browse.value.loading }
            assertTrue(fixture().getJSONArray("discoveryRequests").length() > requests)
            compose.onNodeWithText("Trending in $region").assertExists()
        }
    }

    @Test fun playlistSegmentsKeepTheirOwnPositionsAcrossDetailsAndGlobalSearch() {
        login()
        compose.runOnUiThread { vm.action {
            repeat(12) { vm.api.createPlaylist("Saved playlist ${it + 1}", "private") }
            vm.refreshAccount()
        } }
        until { vm.playlists.value.count { it.owned } == 12 }
        compose.onNodeWithTag("you-playlists").performClick()
        until { vm.route == "playlists" && !vm.browse.value.loading }
        // An interior row keeps this check independent of metadata changing the maximum scroll range.
        compose.onNodeWithTag("playlist-library-list").performScrollToIndex(2)
        until { vm.browse.value.position.index > 0 }
        compose.waitForIdle()
        val position = vm.browse.value.position
        compose.onNodeWithTag("playlist-section-subscribed").performClick()
        compose.onNodeWithText("Subscribed playlists (0)").assertIsDisplayed()
        compose.onNodeWithTag("playlist-section-owned").performClick()
        compose.waitForIdle()
        assertEquals("Playlist segment restores its settled position", position, vm.browse.value.position)
        compose.openGlobalSearch()
        compose.onNodeWithContentDescription("Close search").performClick()
        until { vm.route == "playlists" && vm.browse.value.position == position }
        assertEquals("owned", vm.playlistLibrarySection.value)
        val playlist = vm.playlists.value.first { it.owned }
        compose.runOnUiThread { vm.openPlaylist(playlist) }
        until { vm.playlist.value?.id == playlist.id && !vm.browse.value.loading }
        back()
        until { vm.route == "playlists" && !vm.browse.value.loading }
        compose.waitForIdle()
        assertEquals("Playlist detail restores the library position", position, vm.browse.value.position)
        screenshot("playlists-scrolled-restored")
    }

    @Test fun libraryAndFeedPresentationAdaptToThemesLargeTextNarrowAndLandscapeScreens() {
        tab("You"); screenshot("you-guest")
        login()
        compose.runOnUiThread {
            val before = vm.preferences.value
            vm.action { vm.savePreferences(before.copy(darkMode = "dark"), before, vm.api.context()) }
        }
        until { vm.preferences.value.darkMode == "dark" }
        until { compose.onAllNodesWithText("Account settings saved").fetchSemanticsNodes().isEmpty() }
        screenshot("you-dark")
        val font = shell("settings get system font_scale").ifBlank { "1.0" }
        val density = shell("wm density")
        try {
            shell("settings put system font_scale 1.0")
            shell("wm density 672")
            until { currentActivity(); activity.resources.configuration.screenWidthDp <= 320 }
            compose.onNodeWithTag("global-settings").assertIsDisplayed()
            compose.onNodeWithTag("navigation-Subscriptions").assertContentDescriptionEquals("Subscriptions")
            screenshot("you-narrow")
            shell("settings put system font_scale 1.6")
            shell("wm density 600")
            until { currentActivity(); activity.resources.configuration.fontScale >= 1.5f && activity.resources.configuration.screenWidthDp <= 360 }
            compose.onNodeWithTag("you-library-list").performScrollToNode(hasTestTag("you-history"))
            compose.onNodeWithTag("you-history").assertIsDisplayed()
            screenshot("you-narrow-large-text")
            compose.onNodeWithTag("you-library-list").performScrollToNode(hasTestTag("you-clips"))
            compose.onNodeWithTag("you-clips").assertIsDisplayed()
            compose.runOnUiThread { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            until { currentActivity(); activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            screenshot("you-landscape-large-text")
            tab("Trending")
            compose.onNodeWithTag("feed-refresh").assertIsDisplayed()
            compose.onNodeWithTag("discover-Trending").assertIsSelected()
            screenshot("trending-landscape-large-text")
        } finally {
            shell(if (font == "null") "settings delete system font_scale" else "settings put system font_scale $font")
            val override = Regex("Override density: (\\d+)").find(density)?.groupValues?.get(1)
            shell(if (override == null) "wm density reset" else "wm density $override")
            currentActivity()
            compose.runOnUiThread { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
