package net.wingress.mobivious

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Tests the redesigned navigation and real service/surface boundaries on a device. */
@RunWith(AndroidJUnit4::class)
class LiquidRedesignSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private var densityOverride: String? = null
    private var originalFontScale = "1.0"
    private var originalHardwareIme = "null"
    private var originalHandwriting = "null"
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun currentActivity() = compose.runOnUiThread {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().firstOrNull()?.let { activity = it }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        shell("mkdir -p /data/local/tmp/mobivious-liquid")
        shell("screencap -p /data/local/tmp/mobivious-liquid/$name.png")
    }
    private fun back() {
        compose.waitForIdle()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun preferences(value: AccountPreferences) {
        compose.runOnUiThread { vm.store.guestDeArrow(value); vm.refreshSharedSettings() }
        until { vm.preferences.value == LocalPreferences.normalize(value) }
    }
    @Before fun launch() {
        densityOverride = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        originalFontScale = shell("settings get system font_scale").takeIf { it.toFloatOrNull() != null } ?: "1.0"
        originalHardwareIme = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        originalHandwriting = shell("settings get secure stylus_handwriting_enabled")
        shell("settings put secure stylus_handwriting_enabled 0")
        command("reset")
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
            Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences()); vm.store.reduceTransparency(false)
            vm.store.clearVisibilitySnapshot(vm.api.context()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() && activity.window.decorView.hasWindowFocus() }
    }
    @After fun close() {
        shell("wm density ${densityOverride ?: "reset"}"); shell("settings put system font_scale $originalFontScale")
        shell(if (originalHardwareIme == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $originalHardwareIme")
        shell(if (originalHandwriting == "null") "settings delete secure stylus_handwriting_enabled" else "settings put secure stylus_handwriting_enabled $originalHandwriting")
        currentActivity()
        if (::activity.isInitialized) compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.store.guestDeArrow(AccountPreferences()); vm.store.reduceTransparency(false)
            activity.finishAndRemoveTask()
        }
    }
    @Test fun discoverFeedsAndBottomSearchPreserveTheirOrigin() {
        command("watched", """{"indicatorVideos":true}""")
        compose.runOnUiThread { vm.refresh() }
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 4 }
        compose.onNodeWithTag("navigation-Discover").assertIsSelected()
        compose.onNodeWithTag("discover-Popular").assertIsSelected()
        screenshot("discover-light")
        compose.onNodeWithTag("browse-video-list").performScrollToIndex(2)
        compose.waitForIdle()
        val popular = vm.browse.value.position
        compose.onNodeWithTag("discover-Trending").performClick()
        until { vm.tab == "Trending" && !vm.browse.value.loading }
        compose.onNodeWithTag("trending-region").assertIsDisplayed()
        screenshot("trending-light")
        compose.onNodeWithTag("discover-Popular").performClick()
        until { vm.tab == "Popular" && !vm.browse.value.loading }
        assertEquals(popular, vm.browse.value.position)
        compose.onNodeWithTag("global-search").performClick()
        until { vm.tab == "Search" }
        compose.onNodeWithTag("navigation-Discover").assertDoesNotExist()
        compose.onNodeWithTag("main-search").performTextInput("fixture")
        until { androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(activity.window.decorView.rootWindowInsets).isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) }
        screenshot("search-ime")
        compose.onNodeWithTag("main-search").performImeAction()
        until { vm.searchInput.value.submitted == "fixture" && !vm.browse.value.loading }
        screenshot("search-results")
        compose.onNodeWithContentDescription("Close search").performClick()
        until { vm.tab == "Popular" }
        assertEquals(popular, vm.browse.value.position)
    }
    @Test fun youOwnsGuestSettingsAndOpaqueAccessibilityMode() {
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("you-guest").assertExists()
        screenshot("you-guest")
        compose.onNodeWithTag("global-settings").performClick()
        screenshot("settings-root")
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Reduce transparency").performScrollTo().performClick()
        until { vm.store.reduceTransparency.value }
        assertTrue(SessionStore(activity).reduceTransparency.value)
        screenshot("appearance-opaque")
        back(); back()
        compose.onNodeWithTag("navigation-Discover").performClick()
        preferences(AccountPreferences(darkMode = "dark", uiDensity = "compact"))
        screenshot("discover-dark-compact")
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("you-downloads").performClick()
        compose.onNodeWithTag("downloads-page").assertExists()
        screenshot("downloads-dark")
    }
    @Test fun watchPreservesFourAspectRatiosAndPlaybackThroughMiniSearchAndSettings() {
        for ((id, ratio) in listOf("portrait001" to 9f/16f, "square00001" to 1f, "landscape01" to 16f/9f, "ultrawide01" to 8f/3f)) {
            compose.runOnUiThread { vm.openLink(VideoLink(id)); activity.sharedVideo.value = true }
            until { vm.playback.value.mediaId == id && vm.playback.value.geometry.ratio != null && vm.playback.value.playing }
            compose.onNodeWithTag("watch-content").assertExists()
            compose.onNodeWithTag("navigation-Discover").assertDoesNotExist()
            assertEquals(ratio, vm.playback.value.geometry.ratio!!, .01f)
            screenshot("watch-$id")
        }
        val token = vm.queue.value.token
        back()
        compose.onNodeWithTag("mini-player-preview").assertIsDisplayed()
        assertTrue(vm.playback.value.playing)
        screenshot("mini-player")
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("main-search").performClick()
        until { androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(activity.window.decorView.rootWindowInsets).isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) }
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        compose.onNodeWithTag("player-surface").assertDoesNotExist()
        screenshot("mini-search-ime")
        assertEquals(token, vm.queue.value.token)
        assertTrue(vm.playback.value.playing)
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("global-settings").performClick()
        assertTrue(vm.playback.value.playing)
        assertEquals(token, vm.queue.value.token)
    }
    @Test fun wideSidebarAndLargeTextRemainReachable() {
        command("watched", """{"indicatorVideos":true}""")
        compose.runOnUiThread { vm.refresh() }
        until { !vm.browse.value.loading && vm.browse.value.videos.size == 4 }
        shell("wm density 200")
        until { activity.resources.configuration.screenWidthDp >= 840 }
        currentActivity()
        compose.onNodeWithTag("navigation-Discover").assertIsDisplayed()
        compose.onNodeWithTag("global-search").assertIsDisplayed()
        screenshot("discover-wide")
        compose.runOnUiThread { vm.openLink(VideoLink("portrait001")); activity.sharedVideo.value = true }
        until { vm.playback.value.playing && vm.playback.value.geometry.ratio != null }
        compose.onNodeWithTag("watch-more").assertIsDisplayed()
        screenshot("watch-wide-portrait")
        back()
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("global-settings").assertIsDisplayed()
        screenshot("you-wide")
        shell("wm density 640")
        shell("settings put system font_scale 1.5")
        until { activity.resources.configuration.screenWidthDp <= 360 }
        currentActivity()
        compose.onNodeWithTag("navigation-Discover").performClick()
        screenshot("discover-narrow-large-text")
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("global-settings").performClick()
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        screenshot("appearance-narrow-large-text")
    }
    @Test fun systemReducedMotionKeepsNavigationAndMinimizationUsable() {
        val scale = shell("settings get global animator_duration_scale")
        try {
            shell("settings put global animator_duration_scale 0")
            compose.onNodeWithTag("discover-Trending").performClick()
            until { vm.tab == "Trending" && !vm.browse.value.loading }
            compose.onNodeWithTag("navigation-You").performClick()
            compose.onNodeWithTag("global-settings").performClick()
            back()
            compose.onNodeWithTag("navigation-Discover").performClick()
            compose.runOnUiThread { vm.openLink(VideoLink("landscape01")); activity.sharedVideo.value = true }
            until { vm.playback.value.playing }
            until { compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isNotEmpty() }
            back()
            until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mini-player-preview").performClick()
            compose.onNodeWithTag("watch-content").assertIsDisplayed()
            assertTrue(vm.playback.value.playing)
        } finally {
            shell(if (scale == "null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $scale")
        }
    }

    @Test fun guestTitleContributionReturnsToWatchAfterSignInCancellation() {
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.playback.value.playing && compose.onAllNodesWithTag("watch-more").fetchSemanticsNodes().isNotEmpty() }
        val token = vm.queue.value.token
        compose.onNodeWithTag("watch-more").performClick()
        compose.onNodeWithText("DeArrow Title").performClick()
        compose.onNodeWithTag("sign-in-screen").assertIsDisplayed()
        back()
        until { compose.onAllNodesWithTag("watch-more").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(token, vm.queue.value.token)
        assertTrue(vm.playback.value.playing)
    }

}
