package net.wingress.mobivious

import android.content.Intent
import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Local, disposable accounts and statistics; never contacts a production instance. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubscriptionSortingSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val address = "http://127.0.0.1:18080"
    private val favorite = "UC" + "a".repeat(22)
    private val alpha = "UC" + "b".repeat(22)
    private val beta = "UC" + "c".repeat(22)
    private val dormant = "UC" + "d".repeat(22)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun fixture() = JSONObject(URL("$address/test/state").readText())
    private fun command(data: JSONObject = JSONObject(), path: String = "home-subscriptions") {
        (URL("$address/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(data.toString().toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun stats(): JSONObject {
        val now = System.currentTimeMillis() / 1000
        fun row(upload: Long?, total: Int, recent: Int, score: Double) = JSONObject()
            .put("latestUpload", upload ?: JSONObject.NULL).put("allTimeWatched", total).put("recentWatched", recent).put("relevance", score)
        return JSONObject().put(favorite, row(null, 6, 6, 4.0)).put(alpha, row(now - 2 * 86400, 8, 0, 0.0))
            .put(beta, row(now - 86400, 1, 1, 1.5)).put(dormant, row(null, 0, 0, 0.0))
    }
    private fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    @Before fun launch() {
        command(path = "reset")
        command(JSONObject().put("subscriptionChannels", JSONArray(listOf(
            JSONObject().put("authorId", alpha).put("author", "Alpha Studio"),
            JSONObject().put("authorId", beta).put("author", "Beta Studio"),
            JSONObject().put("authorId", favorite).put("author", "Zulu Studio"),
            JSONObject().put("authorId", dormant).put("author", "Dormant"))))
            .put("subscriptionStats", stats()))
        launchActivity()
        compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.switchServer(address)
            activity.model.store.guestDeArrow(AccountPreferences()); activity.model.refreshSharedSettings()
            activity.model.action { activity.model.login("SortingViewer", "fixture-password") }
        }
        until { activity.model.account.value != null && activity.model.subscriptionChannels.value.loaded && activity.model.dearrowIdentity.value != null }
        compose.runOnUiThread { activity.model.sortSubscriptionChannels(SubscriptionSort.RELEVANCE) }
        directory()
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            activity.model.closePlayer(); activity.model.store.save(null); activity.finishAndRemoveTask()
        }
    }
    private fun directory() {
        compose.runOnUiThread { activity.model.navigate("Subscriptions", "subscription-channels") }
        until { !activity.model.subscriptionChannels.value.loading && activity.model.subscriptionChannels.value.loaded }
    }
    private fun choose(sort: SubscriptionSort) {
        compose.onNodeWithTag("subscription-sort").performClick()
        compose.onNodeWithTag("subscription-sort-option-${sort.key}").performClick()
        compose.onNodeWithTag("subscription-sort").assertTextContains("Sort: ${sort.label}")
    }
    private fun order(vararg ids: String) = assertEquals(ids.toList(), activity.model.subscriptionChannels.value.matches.map { it.id })
    private fun requests() = fixture().getJSONArray("subscriptionRequests").length()

    @Test fun relevanceDefaultAndAllFourSortsFilterLocallyWithCorrectDetails() {
        compose.onNodeWithTag("subscription-sort").assertTextContains("Sort: Relevance")
        order(favorite, beta, alpha, dormant)
        compose.onNodeWithText("6 videos watched in the last 90 days").assertExists()
        compose.onAllNodesWithText("Last upload unknown").assertCountEquals(2)
        val before = requests()
        choose(SubscriptionSort.LATEST); order(beta, alpha, dormant, favorite)
        choose(SubscriptionSort.MOST_WATCHED); order(alpha, favorite, beta, dormant)
        compose.onNodeWithText("8 videos watched all time").assertExists()
        choose(SubscriptionSort.ALPHABETICAL); order(alpha, beta, dormant, favorite)
        choose(SubscriptionSort.RELEVANCE)
        compose.onNodeWithTag("subscription-channel-search").performTextInput("sTuDiO")
        order(favorite, beta, alpha)
        choose(SubscriptionSort.LATEST); order(beta, alpha, favorite)
        compose.onNodeWithTag("subscription-channel-search").assertTextContains("sTuDiO")
        assertEquals(before, requests())
    }

    @Test fun choiceSurvivesNewActivityAndIsScopedToInstance() {
        choose(SubscriptionSort.MOST_WATCHED)
        compose.onNodeWithTag("subscription-channel-$favorite").performClick()
        until { activity.model.channel.value != null && !activity.model.browse.value.loading }
        compose.navigateBack()
        until { activity.model.route == "subscription-channels" && !activity.model.subscriptionChannels.value.loading }
        until { compose.onAllNodesWithTag("subscription-sort").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("subscription-sort").assertTextContains("Sort: Most watched")
        val current = activity.model.api.context()
        assertEquals(SubscriptionSort.RELEVANCE, activity.model.store.subscriptionSort(current.copy(server = "https://other.test")))
        compose.runOnUiThread { activity.finishAndRemoveTask() }
        launchActivity()
        until { activity.model.subscriptionChannels.value.loaded }
        directory()
        compose.onNodeWithTag("subscription-sort").assertTextContains("Sort: Most watched")
        assertEquals(SubscriptionSort.MOST_WATCHED, activity.model.store.subscriptionSort(activity.model.api.context()))
    }

    @Test fun legacyAndPermissionFallbackKeepChoiceWhileFailuresRetainRows() {
        choose(SubscriptionSort.MOST_WATCHED)
        for (mode in listOf("legacy", "permission")) {
            command(JSONObject().put("subscriptionStatsMode", mode))
            compose.onNodeWithContentDescription("Refresh channels").performClick()
            until { !activity.model.subscriptionChannels.value.loading && activity.model.subscriptionChannels.value.unavailableReason != null }
            compose.onNodeWithTag("subscription-sort").assertTextContains("Sort: A–Z")
            compose.onNodeWithTag("subscription-sort-help").assertTextContains("Showing A–Z.", substring = true)
            compose.onNodeWithTag("subscription-sort").performClick()
            compose.onNodeWithTag("subscription-sort-option-relevance").assertIsNotEnabled()
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitForIdle()
            assertEquals(SubscriptionSort.MOST_WATCHED, activity.model.subscriptionChannels.value.sort)
            assertEquals(SubscriptionSort.MOST_WATCHED, activity.model.store.subscriptionSort(activity.model.api.context()))
        }
        command(JSONObject().put("subscriptionStatsMode", "full"))
        compose.onNodeWithContentDescription("Refresh channels").performClick()
        until { !activity.model.subscriptionChannels.value.loading && activity.model.subscriptionChannels.value.unavailableReason == null }
        compose.onNodeWithTag("subscription-sort").assertTextContains("Sort: Most watched")
        compose.onNodeWithTag("subscription-channel-search").performTextInput("studio")
        command(JSONObject().put("failSubscriptionRead", true))
        compose.onNodeWithContentDescription("Refresh channels").performClick()
        until { activity.model.subscriptionChannels.value.error != null }
        order(alpha, favorite, beta)
        compose.onNodeWithTag("subscription-sort").assertTextContains("Sort: Most watched")
        command(JSONObject().put("failSubscriptionRead", false))
        compose.onNodeWithText("Retry").performClick()
        until { !activity.model.subscriptionChannels.value.loading && activity.model.subscriptionChannels.value.error == null }
        compose.onNodeWithTag("subscription-channel-search").assertTextContains("studio")
    }

    @Test fun repeatWatchesAndMemberPreferenceRefreshStatsAndLateReadCannotSurviveSignOut() {
        val app = activity.application as MobiviousApplication
        val context = activity.model.api.context()
        for ((id, score) in listOf(alpha to 5.0, beta to 7.0)) {
            val data = stats(); data.getJSONObject(id).put("relevance", score)
            command(JSONObject().put("subscriptionStats", data))
            val before = requests()
            compose.runOnUiThread { activity.model.action { app.watched.recordWatched(context, "aaaaaaaaaaa") } }
            until { requests() > before && !activity.model.subscriptionChannels.value.loading && activity.model.subscriptionChannels.value.stats[id]?.relevance == score }
            assertEquals(id, activity.model.subscriptionChannels.value.matches.first().id)
        }
        val before = requests()
        compose.runOnUiThread { activity.model.preferences.value = activity.model.preferences.value.copy(showMemberVideos = !activity.model.preferences.value.showMemberVideos) }
        until { requests() > before && !activity.model.subscriptionChannels.value.loading }
        command(JSONObject().put("subscriptionDelayNext", 1000))
        compose.onNodeWithContentDescription("Refresh channels").performClick()
        until { activity.model.subscriptionChannels.value.loading }
        compose.runOnUiThread { activity.model.store.save(null) }
        until { fixture().getJSONArray("subscriptionRequests").let { rows -> (0 until rows.length()).all { rows.getJSONObject(it).getBoolean("completed") } } }
        assertTrue(activity.model.subscriptionChannels.value.channels.isEmpty())
        assertTrue(activity.model.subscriptionChannels.value.stats.isEmpty())
        compose.onNodeWithText("Sign in", substring = false).assertExists()
    }
}
