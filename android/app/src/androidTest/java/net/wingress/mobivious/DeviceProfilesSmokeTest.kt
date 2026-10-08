package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.URL
import kotlinx.coroutines.CancellationException
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DeviceProfilesSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val channel = "UC" + "a".repeat(22)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            val vm = activity.model
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            for (id in listOf('a', 'b', 'c')) {
                vm.store.save(Account("fixture-token", "ProfileFixture", Long.MAX_VALUE, vm.store.server, id.toString().repeat(64)))
                vm.store.deleteProfile(vm.api.context())
            }
            vm.store.save(null)
            vm.store.clearVisibilitySnapshot(vm.api.context()); vm.saveSearchVisibility(SearchVisibility())
            vm.store.guestDeArrow(AccountPreferences()); vm.store.background = true; vm.store.pip = true
            vm.store.chatAppearance(ChatAppearance()); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { activity.model.browse.value.videos.isNotEmpty() && !activity.model.browse.value.loading }
        command("reset")
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread {
            val vm = activity.model
            vm.closePlayer(); vm.store.save(null); vm.store.clearVisibilitySnapshot(vm.api.context())
            vm.store.background = true; vm.store.pip = true; vm.store.chatAppearance(ChatAppearance())
            vm.store.guestDeArrow(AccountPreferences()); activity.finishAndRemoveTask()
        }
    }
    private fun login(name: String, id: Char) {
        command("accounts", JSONObject().put("accountProfileId", id.toString().repeat(64)).toString())
        compose.runOnUiThread { activity.model.action { activity.model.login(name, "fixture-password") } }
        until { activity.model.account.value?.profileId == id.toString().repeat(64) && !activity.model.accountBusy.value && activity.model.tab == "You" }
    }
    private fun logout() {
        compose.runOnUiThread { activity.model.logout() }
        until { activity.model.account.value == null && !activity.model.accountBusy.value }
    }
    @Test fun guestAccountTransitionsRestoreSettingsAndNeverRewriteGuestSpeed() {
        command("preferences", """{"speed":1.25}""")
        compose.runOnUiThread {
            activity.model.store.pip = false
            activity.model.setChatAppearance(ChatAppearance(fontScale = 145))
            activity.model.store.guestDeArrow(AccountPreferences(speed = .75f, savePosition = true))
            activity.model.refreshSharedSettings()
        }
        login("ProfileAlice", 'a')
        until { activity.model.preferences.value.speed == 1.25f }
        assertTrue(activity.model.store.pip); assertEquals(100, activity.model.chatAppearance.value.fontScale)
        compose.runOnUiThread { activity.model.store.background = false; activity.model.setChatAppearance(ChatAppearance(fontScale = 175)) }
        logout()
        until { activity.model.preferences.value.speed == .75f && activity.model.chatAppearance.value.fontScale == 145 }
        assertFalse(activity.model.store.pip)
        login("ProfileBob", 'b')
        assertTrue(activity.model.store.background); assertTrue(activity.model.store.pip); assertEquals(100, activity.model.chatAppearance.value.fontScale)
        logout(); login("ProfileAlice", 'a')
        until { activity.model.chatAppearance.value.fontScale == 175 }
        assertFalse(activity.model.store.background); assertTrue(activity.model.store.pip)
        assertEquals(.75f, activity.model.store.guestDeArrow().speed)
    }
    @Test fun existingSessionDiscoversStableIdentityAndLateGuestSavesAreRejected() {
        val vm = activity.model
        command("preferences", """{"save_player_pos":true}""")
        compose.runOnUiThread {
            vm.store.save(Account("fixture-token", "LegacyProfile", Long.MAX_VALUE, vm.store.server))
            vm.store.background = false; vm.store.position("abcdefghijk", 67)
        }
        until { vm.account.value != null && !vm.accountBusy.value }
        val old = vm.api.context()
        command("accounts", JSONObject().put("accountProfileId", "c".repeat(64)).toString())
        compose.runOnUiThread { vm.refreshSharedSettings() }
        until { vm.account.value?.profileId == "c".repeat(64) }
        assertFalse(vm.store.background); assertEquals(67L, vm.store.position("abcdefghijk"))
        compose.runOnUiThread {
            try { vm.store.pip(false, old); fail("Old session write survived profile resolution") } catch (_: CancellationException) { }
        }
        assertTrue(vm.store.pip)
    }
    @Test fun guestCanBlockFromVideoMenuAndManageItWithoutSigningIn() {
        compose.onNodeWithTag("video-actions-testvideo01").performClick()
        compose.onNodeWithText("Block channel", substring = false).performClick()
        until { channel in activity.model.blocked.value.ids && activity.model.blocked.value.busy.isEmpty() }
        assertNull(activity.model.account.value)
        compose.onNodeWithTag("video-card-testvideo01").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        until { channel !in activity.model.blocked.value.ids && !activity.model.browse.value.loading }
        compose.onNodeWithTag("video-card-testvideo01").assertExists()
        compose.onNodeWithTag("video-actions-testvideo01").performClick()
        compose.onNodeWithText("Block channel", substring = false).performClick()
        until { channel in activity.model.blocked.value.ids && activity.model.blocked.value.busy.isEmpty() }
        compose.onNodeWithTag("navigation-You").performClick(); compose.onNodeWithTag("global-settings").performClick()
        compose.onNodeWithText("Browsing").performClick(); compose.onNodeWithText("Blocked channels").performClick()
        compose.onNodeWithTag("unblock-$channel").performClick()
        until { channel !in activity.model.blocked.value.ids }
        assertTrue(fixture().getJSONObject("blockedChannels").length() == 0)
        val events = fixture().getJSONArray("events")
        assertFalse((0 until events.length()).any { events.getJSONObject(it).getString("path").startsWith("/api/v1/auth/") })
    }
    @Test fun guestCanCreateAndResetChannelSponsorBlockOverrides() {
        command("sponsorblock", """{"sponsorSegments":[{"id":"guest-segment","category":"sponsor","start":10,"end":20}]}""")
        compose.onNodeWithTag("navigation-You").performClick(); compose.onNodeWithTag("global-settings").performClick()
        compose.onNodeWithText("SponsorBlock").performScrollTo().performClick()
        compose.onNodeWithText("Channel SponsorBlock settings").performClick()
        compose.onNodeWithText("Channel ID or /channel/UC… URL").performTextInput(channel)
        compose.onNodeWithText("Edit channel settings").performClick()
        until { compose.onAllNodesWithTag("sponsor-channel-enabled").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sponsor-channel-enabled").performClick()
        compose.onNode(hasText("Enabled") and hasAnyAncestor(isPopup())).performClick()
        compose.onNode(hasText("Save") and hasAnyAncestor(hasTestTag("sponsorblock-sheet"))).performClick()
        until { activity.model.preferences.value.sponsorBlock.channels[channel]?.enabled == true }
        assertTrue(SessionStore(activity).guestDeArrow().sponsorBlock.effective(channel).enabled)
        compose.runOnUiThread { activity.model.play("testvideo01", 0) }
        until { activity.model.sponsorBlock.value.segments.any { it.id == "guest-segment" } }
        assertFalse(fixture().getBoolean("sponsorAuthorized"))
        compose.onNodeWithTag("sponsor-configured-channel-$channel").performClick()
        compose.onNodeWithText("Reset to global").performClick()
        until { channel !in activity.model.preferences.value.sponsorBlock.channels }
        assertFalse(SessionStore(activity).guestDeArrow().sponsorBlock.channels.containsKey(channel))
        until { activity.model.sponsorBlock.value.segments.isEmpty() }
        assertEquals(0, fixture().getJSONArray("preferencesRequests").length())
        assertNull(activity.model.account.value)
    }
}
