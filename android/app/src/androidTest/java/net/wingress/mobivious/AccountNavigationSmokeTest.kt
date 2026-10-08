package net.wingress.mobivious

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/** Account mutations and real media only target the disposable localhost fixture. */
@RunWith(AndroidJUnit4::class)
class AccountNavigationSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private fun until(condition: () -> Boolean) = compose.waitUntil(40_000, condition)
    private fun back() {
        compose.waitForIdle()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() && vm.queue.value.token.isEmpty() && activity.window.decorView.hasWindowFocus() }
    }
    @After fun close() { if (::activity.isInitialized) compose.runOnUiThread { vm.closePlayer(); vm.store.save(null); activity.finishAndRemoveTask() } }
    private fun restoreWatch() {
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("mini-player-preview").performClick()
        until { compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun signInWithDelayedPreferences() {
        command("preferences", """{"default_home":"Subscriptions","delayNextMillis":1200}""")
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        until {
            val requests = JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONArray("preferencesRequests")
            (0 until requests.length()).any { !requests.getJSONObject(it).getBoolean("completed") }
        }
    }

    @Test fun delayedAccountPreferencesKeepThePageOpenedWhileTheyLoad() {
        signInWithDelayedPreferences()
        compose.runOnUiThread { vm.navigate("You", "history") }
        until { !vm.browse.value.loading && vm.browse.value.history != null }
        until { vm.preferences.value.defaultHome == "Subscriptions" }
        assertEquals("You" to "history", vm.navigation.value)
        compose.onNodeWithTag("history-search").assertExists()
    }

    @Test fun delayedAccountPreferencesApplyDefaultHomeWithoutNavigation() {
        signInWithDelayedPreferences()
        until { vm.preferences.value.defaultHome == "Subscriptions" && vm.tab == "Subscriptions" && !vm.browse.value.loading }
        assertEquals("Subscriptions" to "", vm.navigation.value)
    }

    @Test fun delayedAccountPreferencesKeepExplicitDiscoveryAndPlayback() {
        repeat(5) { attempt ->
            if (attempt > 0) {
                compose.runOnUiThread { vm.closePlayer(); vm.store.save(null); vm.navigate("Popular") }
                until { vm.queue.value.token.isEmpty() && !vm.browse.value.loading }
            }
            signInWithDelayedPreferences()
            compose.runOnUiThread { vm.selectDiscovery("trending"); vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
            until { vm.preferences.value.defaultHome == "Subscriptions" && vm.playback.value.playing }
            assertEquals("Trending" to "", vm.navigation.value)
            assertEquals("trending", vm.discovery.value)
            // Service playback can become ready before Compose collects its state.
            // Await the watch UI itself; never expand the mini-player to make this pass.
            until { compose.onAllNodesWithTag("watch-details-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("watch-details-list").assertIsDisplayed()
        }
    }

    @Test fun fourTabsGuestSettingsAndSearchCancellationAndReturn() {
        listOf("Popular", "Trending", "Subscriptions", "You").forEach { compose.onNodeWithTag("navigation-$it").assertExists() }
        compose.onNodeWithTag("main-search").assertDoesNotExist()
        val original = vm.browse.value
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("main-search").performTextInput("fixture")
        assertEquals("Popular", vm.tab); assertEquals(original.videos, vm.browse.value.videos)
        back(); compose.onNodeWithTag("main-search").assertDoesNotExist()
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("main-search").performTextReplacement("fixture")
        compose.onNodeWithTag("main-search").performImeAction()
        until { vm.tab == "Search" && !vm.browse.value.loading }
        compose.onNodeWithTag("main-search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search filters").assertExists()
        compose.onNodeWithTag("global-search").performClick()
        compose.onNodeWithTag("main-search").assertTextContains("fixture")
        back(); assertEquals("Search", vm.tab)
        back(); until { vm.tab == "Popular" }; assertEquals(original.videos, vm.browse.value.videos)
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithTag("global-settings").performClick()
        compose.onNodeWithTag("settings-root").assertExists()
        back(); compose.onNodeWithTag("you-guest").assertExists()
    }

    @Test fun contextualSignupReturnsToLibraryAndCredentialChangesRetainSignIn() {
        compose.onNodeWithTag("navigation-You").performClick()
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Create account").performClick()
        compose.onNodeWithTag("account-username").performTextInput("NewViewer")
        compose.onNodeWithTag("account-password").performTextInput("an uncommon signup password")
        compose.onNodeWithTag("account-confirm-password").performTextInput("an uncommon signup password")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.account.value != null && vm.tab == "You" }
        compose.onNodeWithTag("global-settings").performClick()
        compose.onNodeWithText("Account", substring = false).performClick()
        compose.onNodeWithText("Change username").performClick()
        compose.onNodeWithTag("account-current-password").performTextInput("wrong")
        compose.onNodeWithTag("account-new-username").performTextReplacement("RenamedViewer")
        compose.onNodeWithTag("account-change-submit").performClick()
        until { !vm.accountBusy.value && compose.onAllNodesWithTag("account-error").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("NewViewer", vm.account.value?.username)
        compose.onNodeWithTag("account-current-password").performTextReplacement("an uncommon signup password")
        compose.onNodeWithTag("account-change-submit").performClick()
        until { vm.account.value?.username == "RenamedViewer" }
        compose.onNodeWithText("Signed in as RenamedViewer").assertExists()
    }

    @Test fun sessionsTokenCreationAndDeletionConfirmation() {
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)); vm.navigate("You") }
        compose.onNodeWithTag("global-settings").performClick()
        compose.onNodeWithText("Account", substring = false).performClick()
        compose.onNodeWithText("Sessions & API tokens").performClick()
        until { compose.onAllNodesWithText("This session").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Browser session").assertExists()
        compose.onNodeWithText("Revoke", substring = false).performClick()
        compose.onAllNodesWithText("Revoke", substring = false).onLast().performClick()
        until { compose.onAllNodesWithText("Browser session").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Create API token").performClick()
        compose.onNodeWithContentDescription("Back from Create API token").assertExists()
        compose.onNodeWithTag("account-create-token").assertExists()
        compose.onNodeWithTag("account-token-permission-Read preferences").performClick()
        compose.onNodeWithTag("account-token-password").performScrollTo().performTextInput("an uncommon signup password")
        compose.onNodeWithTag("account-create-token").performScrollTo().performClick()
        until { compose.onAllNodesWithTag("account-created-token").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Done").performClick()
        back(); compose.onNodeWithText("Delete account").performClick()
        compose.onNodeWithTag("account-current-password").performTextInput("an uncommon signup password")
        compose.onNodeWithTag("account-change-submit").performClick()
        assertNotNull(vm.account.value)
        compose.onNodeWithText("Cancel").performClick(); assertNotNull(vm.account.value)
        compose.onNodeWithTag("account-change-submit").performClick()
        compose.onNodeWithText("Permanently delete").performClick()
        until { vm.account.value == null }
    }

    @Test fun realPortraitAndOtherShapesUpdateSizingFullscreenAndPipWithoutRestart() {
        for ((id, expected) in listOf("portrait001" to 9f / 16f, "square00001" to 1f, "landscape01" to 16f / 9f, "ultrawide01" to 8f / 3f)) {
            compose.runOnUiThread { vm.openLink(VideoLink(id)); activity.sharedVideo.value = true }
            until { vm.playback.value.mediaId == id && vm.playback.value.geometry.ratio != null && vm.playback.value.playing }
            restoreWatch()
            assertEquals(expected, vm.playback.value.geometry.ratio!!, .01f)
            val player = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
            val content = compose.onNodeWithTag("watch-content").getUnclippedBoundsInRoot()
            val contentHeight = (content.bottom - content.top).value
            assertEquals(minOf((player.right - player.left).value / expected, contentHeight * .7f), (player.bottom - player.top).value, 1f)
            if (expected < .99f) {
                compose.runOnUiThread { vm.openComments() }
                until { vm.comments.value.open }
                compose.waitForIdle()
                val commentPlayer = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
                assertEquals(contentHeight * .4f, (commentPlayer.bottom - commentPlayer.top).value, 1f)
                compose.runOnUiThread { vm.closeComments() }
            }
            val mediaId = vm.playback.value.mediaId
            if (compose.onAllNodesWithContentDescription("Full screen").fetchSemanticsNodes().isEmpty())
                compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
            compose.onNodeWithContentDescription("Full screen").performClick()
            until { activity.requestedOrientation == if (expected < .99f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else if (expected > 1.01f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            assertEquals(mediaId, vm.playback.value.mediaId)
            back()
            compose.runOnUiThread { activity.updatePip(true) }
            if (activity.supportsPip()) {
                compose.runOnUiThread { activity.enterPip() }
                until {
                    val surface = activity.window.decorView
                    activity.isInPictureInPictureMode && surface.height > 0 &&
                        kotlin.math.abs(surface.width.toFloat() / surface.height - expected.coerceIn(1f / 2.39f, 2.39f)) < .15f
                }
                compose.runOnUiThread {
                    val surface = activity.window.decorView
                    assertEquals(expected.coerceIn(1f / 2.39f, 2.39f), surface.width.toFloat() / surface.height, .15f)
                }
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                automation.waitForIdle(500, 5_000)
                val descriptor = automation.executeShellCommand("am start --windowingMode 1 -n ${activity.packageName}/net.wingress.mobivious.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-new-task")
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
                compose.runOnUiThread { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
                until { !activity.isInPictureInPictureMode }
            }
        }
    }

    @Test fun decodedQueueShapeChangesAndActivityRecreationKeepTheServicePlayer() {
        compose.runOnUiThread { vm.openLink(VideoLink("portrait001")); activity.sharedVideo.value = true }
        until { vm.playback.value.mediaId == "portrait001" && vm.playback.value.geometry.ratio != null && vm.playback.value.playing }
        compose.runOnUiThread { vm.insertQueue(Video("landscape01", "Landscape fixture"), true); vm.seekTo(4000) }
        until { vm.queue.value.items.size >= 2 && vm.playback.value.position >= 3500 }
        val token = vm.queue.value.token
        val old = activity
        compose.runOnUiThread { old.recreate() }
        until {
            var resumed = false
            compose.runOnUiThread { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }
            resumed
        }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        until { vm.playback.value.geometry.ratio != null }
        assertEquals(token, vm.queue.value.token)
        assertTrue(vm.playback.value.position >= 3500)
        assertEquals(9f / 16f, vm.playback.value.geometry.ratio!!, .01f)
        if (compose.onAllNodesWithContentDescription("Full screen").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("player-surface").performTouchInput { click(Offset(width * .5f, height * .15f)) }
        compose.onNodeWithContentDescription("Full screen").performClick()
        until { activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }
        compose.runOnUiThread { vm.nextQueue(1) }
        until { vm.playback.value.mediaId == "landscape01" && vm.playback.value.geometry.ratio == 16f / 9f && activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
        assertEquals(token, vm.queue.value.token)
        back(); until { activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}
