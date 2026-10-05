package net.wingress.mobivious

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.AccountPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Uses the disposable localhost fixture; never changes a production account. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class HomeSubscriptionsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val channelId = "UC" + "a".repeat(22)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun command(body: String = "{}", path: String = "home-subscriptions") {
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
            val vm = activity.model
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences()); vm.refreshSharedSettings(); vm.navigate("Home")
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null)
            activity.model.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask()
        }
    }
    private fun login() {
        compose.runOnUiThread { activity.model.action { activity.model.login("DirectoryViewer", "fixture-password") } }
        until { activity.model.account.value != null && activity.model.subscriptionChannels.value.loaded && activity.model.dearrowIdentity.value != null }
    }
    private fun subscriptions() {
        compose.onNodeWithTag("navigation-Subscriptions").performClick()
        until { activity.model.route.isEmpty() && activity.model.tab == "Subscriptions" && !activity.model.browse.value.loading }
    }
    private fun directory() {
        compose.onNodeWithTag("subscription-channels-button").performClick()
        until { activity.model.route == "subscription-channels" && !activity.model.subscriptionChannels.value.loading }
    }
    private fun back() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
    }
    private fun recreate() {
        val old = activity
        compose.runOnUiThread { old.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/mobivious-home-subscriptions-screenshots",
            "screencap -p /data/local/tmp/mobivious-home-subscriptions-screenshots/$name.png").forEach {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream -> stream.readBytes() }
        }
    }

    @Test fun homeHighlightChangesDuringRequestsAndSurvivesNavigationAndRecreation() {
        command("""{"discoveryDistinct":true}""")
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(defaultHome = "Trending"))
            activity.model.refreshSharedSettings(); activity.model.openDefaultHome()
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.firstOrNull()?.title == "trending discovery fixture" }
        compose.onNodeWithTag("discovery-trending").assertIsSelected()
        command("""{"discoveryDelayNext":1800}""")
        compose.onNodeWithTag("discovery-popular").performClick()
        compose.onNodeWithTag("discovery-popular").assertIsSelected()
        compose.onNodeWithTag("discovery-trending").assertIsNotSelected()
        until { fixture().getJSONArray("discoveryRequests").let { array -> (0 until array.length()).any { !array.getJSONObject(it).getBoolean("completed") } } }
        assertTrue(activity.model.browse.value.loading)
        compose.onNodeWithTag("discovery-trending").performClick()
        until { !activity.model.browse.value.loading }
        compose.onNodeWithTag("discovery-trending").assertIsSelected()
        until { fixture().getJSONArray("discoveryRequests").let { array -> (0 until array.length()).all { array.getJSONObject(it).getBoolean("completed") } } }
        assertEquals("trending discovery fixture", activity.model.browse.value.videos.single().title)
        compose.onNodeWithTag("discovery-popular").performClick()
        until { !activity.model.browse.value.loading }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithTag("navigation-Home").performClick()
        compose.onNodeWithTag("discovery-popular").assertIsSelected()
        recreate(); compose.onNodeWithTag("discovery-popular").assertIsSelected()
        screenshot("home-selection")
    }

    @Test fun directoryIsAccessibleAfterFeedScrollAndRestoresSearchAndChannelPositionOnBack() {
        val channels = JSONArray((0 until 35).reversed().map { index -> JSONObject().put("author", "Channel %02d".format(index))
            .put("authorId", if (index == 25) channelId else "UC" + "%022d".format(index)) })
        command(JSONObject().put("subscriptionChannels", channels).toString())
        command("""{"indicatorVideos":true}""", "watched")
        login(); subscriptions()
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Live indicator fixture"))
        compose.onNodeWithTag("subscription-channels-button").assertIsDisplayed()
        directory()
        compose.onNodeWithText("Channel 00").assertIsDisplayed()
        val requests = fixture().getJSONArray("subscriptionRequests").length()
        compose.onNodeWithTag("subscription-channel-search").performTextInput("cHaNnEl")
        assertEquals(35, activity.model.subscriptionChannels.value.matches.size)
        assertEquals(requests, fixture().getJSONArray("subscriptionRequests").length())
        compose.onNodeWithTag("subscription-channel-search-submit").performClick()
        val row = "subscription-channel-$channelId"
        compose.onNodeWithTag("subscription-channel-list").performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag("subscription-channel-search").assertIsDisplayed()
        val position = compose.onNodeWithTag(row).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(row).performClick()
        until { activity.model.channel.value?.id == channelId && !activity.model.browse.value.loading }
        back()
        compose.onNodeWithTag("subscription-channel-search").assertTextContains("cHaNnEl")
        compose.onNodeWithTag(row).assertIsDisplayed()
        assertEquals(position, compose.onNodeWithTag(row).fetchSemanticsNode().boundsInRoot.top, 1f)
        recreate(); compose.onNodeWithTag(row).assertIsDisplayed()
        compose.onNodeWithTag("subscription-channel-search").performTextReplacement("absent")
        compose.onNodeWithText("No matching channels").assertIsDisplayed()
        compose.onNodeWithText("Clear search").performClick()
        compose.onNodeWithText("Channel 00").assertIsDisplayed()
        screenshot("channel-directory")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("subscription-channels-button").assertIsDisplayed()
        compose.onNodeWithTag("subscription-search").assertExists()
    }

    @Test fun directoryKeepsChannelsOnRefreshFailureRetriesAndReflectsUnsubscribe() {
        login(); subscriptions(); directory()
        command("""{"failSubscriptionRead":true}""")
        compose.onNodeWithContentDescription("Refresh channels").performClick()
        until { activity.model.subscriptionChannels.value.error != null }
        compose.onNodeWithTag("subscription-channel-$channelId").assertExists()
        compose.onNodeWithText("Fixture subscriptions temporarily unavailable").assertExists()
        command("""{"failSubscriptionRead":false}""")
        compose.onNodeWithText("Retry").performClick()
        until { !activity.model.subscriptionChannels.value.loading && activity.model.subscriptionChannels.value.error == null }
        compose.onNodeWithTag("subscription-channel-$channelId").performClick()
        until { activity.model.channel.value != null && !activity.model.browse.value.loading }
        compose.onNodeWithText("Subscribed", substring = false).performClick()
        until { activity.model.subscriptionChannels.value.channels.isEmpty() && !activity.model.subscriptionChannels.value.loading }
        back(); compose.onNodeWithText("No subscribed channels").assertIsDisplayed()
    }

    @Test fun directoryGuestPromptAndLateReadsCannotExposeFormerAccountChannels() {
        subscriptions(); directory()
        compose.onNodeWithText("Sign in", substring = false).assertIsDisplayed()
        assertEquals(0, fixture().getJSONArray("subscriptionRequests").length())
        login(); subscriptions(); directory()
        compose.onNodeWithTag("subscription-channel-search").performTextInput("Studio")
        command("""{"subscriptionDelayNext":1000}""")
        compose.onNodeWithContentDescription("Refresh channels").performClick()
        until { activity.model.subscriptionChannels.value.loading }
        compose.runOnUiThread { activity.model.store.save(null) }
        until { activity.model.subscriptionChannels.value.context?.account == null }
        until { fixture().getJSONArray("subscriptionRequests").let { a -> (0 until a.length()).all { a.getJSONObject(it).getBoolean("completed") } } }
        assertTrue(activity.model.subscriptionChannels.value.channels.isEmpty())
        assertEquals("", activity.model.subscriptionChannels.value.query)
        compose.onNodeWithText("Sign in", substring = false).assertIsDisplayed()
    }

    @Test fun guestWatchActionsHaveNoOverflowOrImplicitQueue() {
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.details != null }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("watch-actions"))
        compose.onNodeWithTag("watch-actions-menu").assertDoesNotExist()
        compose.onNodeWithContentDescription("Video actions").assertDoesNotExist()
        compose.onNodeWithTag("channel-block-$channelId").assertDoesNotExist()
        compose.onNodeWithText("Playback queue").assertDoesNotExist()
        compose.onNodeWithContentDescription("Playback queue").assertDoesNotExist()
        compose.onNodeWithTag("playback-queue").assertDoesNotExist()
        screenshot("watch-actions")
    }
}
