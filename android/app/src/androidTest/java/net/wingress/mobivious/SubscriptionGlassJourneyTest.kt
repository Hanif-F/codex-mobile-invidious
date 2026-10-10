package net.wingress.mobivious

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Full journey against disposable local accounts, with actual backdrop and playback rendering. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubscriptionGlassJourneyTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val owner = "UC" + "a".repeat(22)
    private var density: String? = null
    private var fontScale = "1.0"
    private var animator = "1.0"
    private var hardwareIme = "null"
    private var handwriting = "null"
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun command(path: String, body: JSONObject = JSONObject()) {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toString().toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun currentActivity() { compose.runOnUiThread {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
            .firstOrNull()?.let { activity = it }
    } }
    private fun capture(name: String) {
        compose.waitForIdle()
        shell("mkdir -p /data/local/tmp/mobivious-subscriptions-glass")
        shell("screencap -p /data/local/tmp/mobivious-subscriptions-glass/$name.png")
    }
    private fun feed() {
        compose.runOnUiThread { vm.navigate("Subscriptions") }
        until { vm.route.isEmpty() && vm.tab == "Subscriptions" && !vm.browse.value.loading }
    }
    @Before fun launch() {
        density = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        fontScale = shell("settings get system font_scale").takeIf { it.toFloatOrNull() != null } ?: "1.0"
        animator = shell("settings get global animator_duration_scale").takeIf { it.toFloatOrNull() != null } ?: "1.0"
        hardwareIme = shell("settings get secure show_ime_with_hard_keyboard")
        handwriting = shell("settings get secure stylus_handwriting_enabled")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        shell("settings put secure stylus_handwriting_enabled 0")
        command("reset")
        command("watched", JSONObject().put("indicatorVideos", true))
        command("community")
        command("home-subscriptions", JSONObject().put("subscriptionChannels", JSONArray((0..25).map { index ->
            JSONObject().put("authorId", if (index == 0) owner else "UC" + "%022d".format(index))
                .put("author", if (index == 0) "Mobivious Studio" else "Creator %02d".format(index))
                .put("authorThumbnails", JSONArray().put(JSONObject().put("url", "/ggpht/creator=s88")))
        })))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences(autoplay = false)); vm.store.reduceTransparency(false); vm.refreshSharedSettings()
            vm.action { vm.login("GlassViewer", "fixture-password") }
        }
        until { vm.account.value != null && vm.subscriptionChannels.value.loaded && vm.dearrowIdentity.value != null }
        feed()
    }
    @After fun close() {
        shell("wm density ${density ?: "reset"}"); shell("settings put system font_scale $fontScale")
        shell("settings put global animator_duration_scale $animator")
        shell(if (hardwareIme == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $hardwareIme")
        shell(if (handwriting == "null") "settings delete secure stylus_handwriting_enabled" else "settings put secure stylus_handwriting_enabled $handwriting")
        currentActivity()
        if (::activity.isInitialized) compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.store.guestDeArrow(AccountPreferences()); vm.store.reduceTransparency(false)
            activity.finishAndRemoveTask()
        }
    }

    @Test fun feedDirectoryAndCreatorSheetsRetainPlaybackAndTheirOrigin() {
        capture("subscriptions-light")
        compose.runOnUiThread { vm.playVideo(vm.browse.value.videos.first()) }
        until { vm.playback.value.details != null && vm.playback.value.position > 0 }
        compose.onNodeWithTag("mini-player-preview").assertExists()
        val token = vm.queue.value.token
        compose.onNodeWithTag("subscription-search").performClick()
        until { androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(activity.window.decorView.rootWindowInsets)
            .isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) }
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        compose.onNodeWithTag("player-surface").assertDoesNotExist()
        compose.onNodeWithTag("subscription-channels-button").assertIsDisplayed()
        capture("subscriptions-search-ime")
        compose.onNodeWithTag("subscription-search-submit").performClick()
        until { compose.onAllNodesWithTag("mini-player-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("browse-video-list").performScrollToIndex(2)
        compose.onNodeWithTag("browse-video-list").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 120f) }
        compose.onNodeWithTag("subscription-channels-button").assertIsDisplayed()
        capture("subscriptions-scrolled-mini")
        compose.onNodeWithTag("subscription-channels-button").performClick()
        until { vm.route == "subscription-channels" && !vm.subscriptionChannels.value.loading }
        capture("channels-light")
        compose.onNodeWithTag("subscription-channel-list").performScrollToIndex(15)
        compose.onNodeWithTag("subscription-channel-search").assertIsDisplayed()
        capture("channels-scrolled")
        compose.onNodeWithTag("subscription-channel-search").performTextInput("Mobivious")
        compose.onNodeWithTag("subscription-channel-search-submit").performClick()
        compose.onNodeWithTag("subscription-channel-$owner").performClick()
        until { vm.channel.value != null && !vm.browse.value.loading }
        compose.onNodeWithTag("channel-banner").assertExists()
        capture("creator-light")
        compose.revealInBrowse(hasTestTag("channel-description-open"))
        compose.onNodeWithTag("channel-description-open").performClick()
        compose.onNodeWithTag("channel-description-sheet").assertIsDisplayed()
        capture("creator-description")
        compose.onNodeWithContentDescription("Close channel description").performClick()
        compose.revealInBrowse(hasTestTag("channel-actions-$owner"))
        compose.onNodeWithTag("channel-actions-$owner").performClick()
        compose.onNodeWithText("RSS", substring = false).performClick()
        until { vm.rss.value.url != null }
        capture("creator-rss")
        compose.onNodeWithContentDescription("Close feed").performClick()
        compose.onNodeWithTag("channel-actions-$owner").performClick()
        compose.onNodeWithText("Channel SponsorBlock settings", substring = false).performClick()
        compose.onNodeWithTag("sponsorblock-sheet").assertIsDisplayed()
        capture("creator-sponsorblock")
        compose.onNodeWithContentDescription("Close SponsorBlock settings").performClick()
        compose.navigateBack()
        until { vm.route == "subscription-channels" }
        compose.onNodeWithTag("subscription-channel-search").assertTextContains("Mobivious")
        assertEquals(token, vm.queue.value.token)
        assertTrue(vm.playback.value.playing)
    }

    @Test fun darkOpaqueFeedAndExportsRetainAccessibleControls() {
        compose.runOnUiThread { vm.preferences.value = vm.preferences.value.copy(darkMode = "dark") }
        capture("subscriptions-dark")
        compose.runOnUiThread { vm.store.reduceTransparency(true) }
        capture("subscriptions-dark-opaque")
        compose.onNodeWithTag("subscription-actions").performClick()
        compose.onNodeWithText("Export OPML").performClick()
        until { vm.rss.value.xml != null }
        compose.onNodeWithText("YouTube feeds").performClick()
        until { vm.rss.value.xml?.contains("youtube.com/feeds/") == true }
        compose.onNodeWithText("Save file").assertIsDisplayed()
        capture("subscriptions-opml-dark-opaque")
        compose.onNodeWithContentDescription("Close feed").assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("subscription-channels-button").performClick()
        until { vm.route == "subscription-channels" && !vm.subscriptionChannels.value.loading }
        capture("channels-dark-opaque")
        compose.onNodeWithTag("subscription-channel-search").performTextInput("Mobivious")
        compose.onNodeWithTag("subscription-channel-search-submit").performClick()
        compose.onNodeWithTag("subscription-channel-$owner").performClick()
        until { vm.channel.value != null && !vm.browse.value.loading }
        capture("creator-dark-opaque")
    }

    @Test fun wideFeedsUseTwoColumnsAndReducedMotionPreservesNavigation() {
        shell("wm density 240")
        until { currentActivity(); activity.resources.configuration.screenWidthDp >= 840 }
        feed()
        until { compose.onAllNodesWithTag("navigation-Subscriptions").fetchSemanticsNodes().isNotEmpty() }
        val first = compose.onNodeWithTag("video-card-testvideo01").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("video-card-testvideo02").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f); assertTrue(second.left >= first.right)
        capture("subscriptions-wide")
        compose.onNodeWithTag("subscription-channels-button").performClick()
        until { vm.route == "subscription-channels" && !vm.subscriptionChannels.value.loading }
        val directory = compose.onNodeWithTag("subscription-channel-list").fetchSemanticsNode().boundsInRoot
        val maxWidth = 800 * activity.resources.displayMetrics.density
        assertTrue(directory.width <= maxWidth + 1)
        capture("channels-wide")
        shell("settings put global animator_duration_scale 0")
        compose.navigateBack()
        until { vm.route.isEmpty() && vm.tab == "Subscriptions" }
        compose.onNodeWithTag("subscription-channels-button").assertIsDisplayed()
        shell("wm density 640"); shell("settings put system font_scale 1.5")
        until { currentActivity(); activity.resources.configuration.screenWidthDp <= 360 && activity.resources.configuration.fontScale >= 1.49f }
        feed()
        capture("subscriptions-narrow-large")
        compose.runOnUiThread { vm.preferences.value = vm.preferences.value.copy(thinMode = true) }
        compose.onNodeWithTag("subscription-channels-button").performClick()
        until { vm.route == "subscription-channels" && !vm.subscriptionChannels.value.loading }
        capture("channels-narrow-large-thin")
    }
}
