package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.AccountPreferences
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class RemainingGlassJourneyTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private var hardwareIme = "null"
    private val vm get() = activity.model
    private val channelId = "UC" + "a".repeat(22)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/mobivious-glass-audit", "screencap -p /data/local/tmp/mobivious-glass-audit/$name.png"))
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
    @Before fun launch() {
        hardwareIme = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestDeArrow(AccountPreferences()); vm.store.reduceTransparency(false); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
      shell(if (hardwareIme == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $hardwareIme")
      if (::activity.isInitialized) compose.runOnUiThread {
        vm.closePlayer(); vm.store.save(null); vm.store.guestDeArrow(AccountPreferences()); vm.store.reduceTransparency(false); activity.finishAndRemoveTask()
    } }
    private fun settings() { compose.onNodeWithTag("navigation-You").performClick(); compose.openAppSettings() }
    private fun page(name: String) {
        if (compose.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("settings-root").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performScrollTo().performClick()
    }
    private fun back(name: String) { compose.onNodeWithContentDescription("Back from $name").performClick() }
    private fun login() {
        compose.runOnUiThread { vm.action { vm.login("Fixture", "transient-password") } }
        until { vm.account.value != null && vm.subscriptionChannels.value.loaded && !vm.accountBusy.value }
    }

    @Test fun settingsFormsMenusAndAuthenticationStayReachable() {
        capture("discover-light")
        settings(); capture("settings-light")
        page("Playback")
        compose.onNodeWithText("Preferred video codec").performScrollTo().performClick()
        capture("settings-codec-menu")
        compose.onNode(hasText("AV1") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("settings-save").assertIsDisplayed()
        capture("playback-settings")
        back("Playback")
        page("Appearance")
        compose.onNodeWithText("Reduce transparency").performScrollTo().performClick()
        capture("appearance-opaque")
        compose.onNodeWithText("Color mode").performScrollTo().performClick()
        compose.onNode(hasText("Dark") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("settings-save").performClick()
        until { compose.onAllNodesWithTag("settings-root").fetchSemanticsNodes().isNotEmpty() }
        capture("settings-dark-opaque")
        page("Server")
        compose.onNodeWithText("HTTPS address").performClick(); capture("server-keyboard")
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        if (compose.onAllNodesWithContentDescription("Back from Server").fetchSemanticsNodes().isNotEmpty()) back("Server")
        page("Account"); compose.onNodeWithText("Sign in", substring = false).performClick()
        compose.onNodeWithTag("account-username").performTextInput("PreviewViewer")
        compose.onNodeWithContentDescription("Show Password").assertExists()
        capture("authentication-keyboard")
        compose.onNodeWithTag("account-confirm-password").assertDoesNotExist()
    }

    @Test fun accountManagementUsesQuietFormsAndExplicitConfirmations() {
        login(); settings(); page("Account"); capture("account")
        page("Sessions & API tokens")
        until { compose.onAllNodesWithText("This session").fetchSemanticsNodes().isNotEmpty() }
        capture("sessions")
        compose.onNodeWithText("Create API token").performClick()
        compose.onNodeWithText("Token expiry").performScrollTo().performClick()
        capture("token-expiry-menu")
        compose.onNode(hasText("30 days") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("account-token-password").performScrollTo().performClick()
        capture("token-keyboard")
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        if (compose.onAllNodesWithContentDescription("Back from Create API token").fetchSemanticsNodes().isNotEmpty()) back("Create API token")
        if (compose.onAllNodesWithContentDescription("Back from Sessions & API tokens").fetchSemanticsNodes().isNotEmpty()) back("Sessions & API tokens")
        page("Delete account")
        compose.onNodeWithTag("account-current-password").performTextInput("transient-password")
        compose.onNodeWithTag("account-change-submit").performScrollTo().performClick()
        capture("delete-account-confirmation")
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(vm.account.value)
    }

    @Test fun subscriptionMenuPendingFailureAndRetryNeverMisrepresentMembership() {
        login()
        compose.runOnUiThread { vm.navigate("Popular", "channel:$channelId") }
        until { vm.channel.value != null && !vm.browse.value.loading }
        compose.onNodeWithText("Subscribed", substring = false).performScrollTo().performClick()
        capture("subscription-menu")
        assertTrue(vm.subscriptionChannels.value.channels.any { it.id == channelId })
        command("home-subscriptions", """{"subscriptionWriteDelayNext":1800,"failSubscriptionWrite":true}""")
        compose.onNodeWithTag("subscription-unsubscribe").performClick()
        until { channelId in vm.subscriptionChannels.value.busy }
        compose.onNodeWithText("Unsubscribing…").assertIsNotEnabled()
        compose.runOnUiThread { vm.toggleSubscribe(channelId); vm.toggleSubscribe(channelId) }
        until { channelId !in vm.subscriptionChannels.value.busy }
        assertTrue(vm.subscriptionChannels.value.channels.any { it.id == channelId })
        compose.onNodeWithText("Fixture subscription update failed; retry").assertIsDisplayed()
        capture("subscription-failed")
        val events = JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONArray("events")
        assertEquals(1, (0 until events.length()).count { events.getJSONObject(it).optString("action") == "channel-unsubscribe" })
        command("home-subscriptions", """{"failSubscriptionWrite":false,"failSubscriptionRead":true}""")
        compose.onNodeWithText("Subscribed", substring = false).performClick()
        compose.onNodeWithTag("subscription-unsubscribe").performClick()
        until { channelId !in vm.subscriptionChannels.value.busy && vm.subscriptionChannels.value.channels.none { it.id == channelId } }
        compose.onNodeWithText("Subscribe", substring = false).assertIsDisplayed()
        capture("subscription-confirmed")
    }

    @Test fun guestSubscribeCompletesAfterSignInAndReturnsToTheCreator() {
        command("home-subscriptions", """{"subscriptionChannels":[]}""")
        compose.runOnUiThread { vm.navigate("Popular", "channel:$channelId") }
        until { vm.channel.value != null && !vm.browse.value.loading }
        compose.onNodeWithText("Subscribe", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("account-username").performTextInput("Fixture")
        compose.onNodeWithTag("account-password").performTextInput("transient-password")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.route == "channel:$channelId" && channelId !in vm.subscriptionChannels.value.busy && vm.subscriptionChannels.value.channels.any { it.id == channelId } }
        compose.onNodeWithText("Subscribed", substring = false).assertIsDisplayed()
        capture("subscription-after-sign-in")
    }

    @Test fun canceledSubscribeSignInDoesNotChangeALaterAccount() {
        command("home-subscriptions", """{"subscriptionChannels":[]}""")
        compose.runOnUiThread { vm.navigate("Popular", "channel:$channelId") }
        until { vm.channel.value != null && !vm.browse.value.loading }
        compose.onNodeWithText("Subscribe", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back", substring = false).performClick()
        until { vm.route == "channel:$channelId" }
        login()
        assertTrue(vm.subscriptionChannels.value.channels.isEmpty())
        val events = JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONArray("events")
        assertEquals(0, (0 until events.length()).count { events.getJSONObject(it).optString("action") == "channel-subscribe" })
    }
}
