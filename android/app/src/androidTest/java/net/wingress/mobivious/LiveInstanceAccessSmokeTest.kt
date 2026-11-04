package net.wingress.mobivious

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Opt-in real-provider smoke. It creates only an instance pass, never changes server policy/accounts. */
@RunWith(AndroidJUnit4::class)
class LiveInstanceAccessSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    @Test fun signedInPopularAndHistoryLoadWithConfirmedSettings() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveSignedInAccess") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val vm = activity.model
        val account = vm.account.value
        assertNotNull("Sign in on the emulator before selecting this opt-in check", account)
        assertEquals("https://invidious.wingress.net", vm.store.server)
        try {
            compose.runOnUiThread { vm.refreshSharedSettings(); vm.navigate("Popular") }
            compose.waitUntil(240_000) { vm.channelSpoilers.state.value.ready && !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
            assertNull(vm.browse.value.error); assertNull(vm.spoilerSettingsError.value)
            assertNull(vm.access.issue.value)
            compose.onNodeWithTag("instance-verification").assertDoesNotExist()
            captureWatchScreenshot("live-signed-in-popular")
            compose.runOnUiThread { vm.navigate("You", "history") }
            compose.waitUntil(60_000) { !vm.browse.value.loading }
            assertNull(vm.browse.value.error); assertNotNull(vm.browse.value.history)
            val total = requireNotNull(vm.browse.value.history?.total) { "Expected organized live History" }
            compose.onNodeWithText(DisplayFormats.inventory(total.toLong(), "video")).assertExists()
            assertTrue(vm.browse.value.videos.all { !vm.channelSpoilers.state.value.decision(it).pending })
            assertTrue("The live check changed the signed-in session", vm.account.value?.token == account?.token)
            captureWatchScreenshot("live-signed-in-history")
        } finally {
            compose.runOnUiThread { vm.closePlayer(); vm.navigate("Popular") }
        }
    }

    @Test fun realCloudflareClearanceRestoresNativeBrowsingAndPlayback() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveCloudflareAccess") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val vm = activity.model
        val before = vm.store.server
        try {
            compose.runOnUiThread { vm.store.save(null); vm.switchServer("https://invidious.wingress.net"); vm.refresh() }
            // Allow the visitor to interact with a real managed widget in the visible emulator.
            compose.waitUntil(240_000) {
                vm.access.clearance(vm.api.context()) != null
            }
            assertNotNull(runBlocking { vm.access.discover(vm.api.context()) })
            val liveVideos = try { runBlocking { vm.api.discovery("popular") } }
                catch (e: InstanceAccessException) { throw AssertionError("Native Cloudflare clearance accepted; live ${e.reason} prevents browsing/playback without account credentials.", e) }
            assertTrue(liveVideos.isNotEmpty())
            compose.waitUntil(60_000) { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
            compose.onNodeWithTag("instance-verification").assertDoesNotExist()
            val video = liveVideos.first { !it.unavailable && !it.membersOnly && !it.live }
            compose.runOnUiThread { vm.openLink(VideoLink(video.id)); activity.sharedVideo.value = true }
            compose.waitUntil(90_000) { vm.playback.value.playerState == androidx.media3.common.Player.STATE_READY && vm.playback.value.playing }
            val start = vm.playback.value.position
            compose.waitUntil(30_000) { vm.playback.value.position > start + 1000 }
            assertNull(vm.access.issue.value); assertNull(vm.account.value)
        } finally {
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(activity.filesDir, "access-live-cloudflare.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.runOnUiThread {
                vm.closePlayer(); vm.access.contextChanged()
                if (InstrumentationRegistry.getArguments().getString("liveKeepInstance") != "true") { vm.switchServer(before); activity.finishAndRemoveTask() }
            }
        }
    }

    @Test fun realProviderCompletesWithoutAccountOrPolicyChanges() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveInstanceAccess") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val vm = activity.model
        val before = vm.store.server
        try {
            compose.runOnUiThread {
                vm.store.save(null); vm.switchServer("https://invidious.wingress.net")
                vm.store.verificationPersistence.write(InstanceAccess.origin(vm.store.server.toHttpUrl()), null)
            }
            compose.waitUntil(60_000) { !vm.browse.value.loading }
            val status = runBlocking { vm.access.discover(vm.api.context()) }
            assertNotNull(status)
            Assume.assumeTrue("Live verification must be enabled and required", status!!.enabled && status.required)
            if (status.enabled && status.required) {
                compose.runOnUiThread { vm.access.contextChanged(); vm.access.report(vm.api.context(), AccessReason.VERIFICATION) }
                compose.onNodeWithTag("instance-access-action").assertDoesNotExist()
                compose.waitUntil(120_000) { vm.access.pass(vm.api.context()) != null }
                assertTrue(runBlocking { vm.access.discover(vm.api.context()) }!!.verified)
                compose.waitUntil(60_000) { !vm.browse.value.loading }
                compose.onNodeWithTag("instance-verification").assertDoesNotExist()
                compose.onNodeWithText("Access restored").assertDoesNotExist()
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    File(activity.filesDir, "access-live-complete.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            assertNull(vm.account.value)
        } finally {
            compose.runOnUiThread {
                vm.access.contextChanged()
                if (InstrumentationRegistry.getArguments().getString("liveKeepInstance") != "true") vm.switchServer(before)
                activity.finishAndRemoveTask()
            }
        }
    }
}
