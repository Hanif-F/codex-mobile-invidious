package net.wingress.mobivious

import android.content.Intent
import android.view.View
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.media3.common.Player
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.hamcrest.Matcher
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Synthetic localhost challenges exercise cookie handoff, never automate a real provider. */
@RunWith(AndroidJUnit4::class)
class CloudflareAccessSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val app get() = activity.application as MobiviousApplication
    private fun until(condition: () -> Boolean) = compose.waitUntil(60_000, condition)
    private fun command(data: String, path: String = "access") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(data.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun state() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    @Before fun open() {
        command("{}", "reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val restoring = InstrumentationRegistry.getArguments().getString("cloudflareRestart") == "true"
        if (restoring) {
            val saved = SessionStore(instrumentation.targetContext).cloudflarePersistence.read(InstanceAccess.origin("http://127.0.0.1:18080".toHttpUrl()))
            assertNotNull("Seed clearance before force-stopping the app", saved)
            command(JSONObject().put("edgeEnabled", true).put("edgeUserAgent", saved!!.userAgent).toString())
        }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.switchServer("http://127.0.0.1:18080"); vm.store.save(null)
            val origin = InstanceAccess.origin(vm.store.server.toHttpUrl())
            vm.store.verificationPersistence.write(origin, null)
            if (!restoring) vm.store.cloudflarePersistence.write(origin, null)
            vm.access.contextChanged(); vm.refresh()
        }
        until { vm.browse.value.videos.isNotEmpty() && !vm.browse.value.loading }
    }
    @After fun close() {
        command("{}", "reset")
        if (::activity.isInitialized) {
            runBlocking { app.downloads.records.value.filter { it.assets.any { asset -> asset.managed } }.forEach { app.downloads.delete(it.id) } }
            compose.runOnUiThread {
                if (InstrumentationRegistry.getArguments().getString("cloudflareKeepClearance") != "true")
                    vm.store.cloudflarePersistence.write(InstanceAccess.origin(vm.store.server.toHttpUrl()), null)
                vm.access.contextChanged(); vm.closePlayer(); vm.store.save(null); activity.finishAndRemoveTask()
            }
        }
    }
    private fun clickFixture(id: String) {
        compose.onNodeWithTag("instance-verification").assertExists()
        onView(isAssignableFrom(WebView::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription() = "Click the disposable fixture's $id button"
            override fun perform(controller: UiController, view: View) {
                (view as WebView).evaluateJavascript("(function ready(){var b=document.getElementById('$id');if(b)b.click();else setTimeout(ready,100);})();", null)
            }
        })
    }
    private fun block(data: String = """{"edgeEnabled":true}""") {
        command(data)
        compose.runOnUiThread { vm.refresh() }
        until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
    }
    private fun solveEdge() = clickFixture("edge-complete")
    private fun recovered() {
        until { vm.access.issue.value == null && vm.access.clearance(vm.api.context()) != null }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("instance-verification").assertDoesNotExist()
    }
    private fun recreate() {
        val old = activity
        compose.runOnUiThread { activity.recreate() }
        until {
            var ready = false
            compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }
            ready
        }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }

    @Test fun edgeOnlyRestoresBrowsingAndEncryptedClearanceSurvivesStoreRecreation() {
        block(); solveEdge(); recovered()
        assertNull(vm.browse.value.error)
        val edge = vm.access.clearance(vm.api.context())!!
        val values = activity.getSharedPreferences("mobivious", 0).all.filterKeys { it.startsWith("cloudflare.") }.values
        assertTrue(values.isNotEmpty()); assertTrue(values.none { it.toString().contains(edge.cookie.value) })
        val restored = SessionStore(activity)
        assertEquals(edge, restored.cloudflarePersistence.read(InstanceAccess.origin(vm.store.server.toHttpUrl())))
        val freshAccess = InstanceAccess(restored::positionContext, restored.verificationPersistence, restored.cloudflarePersistence, { edge.userAgent })
        assertEquals(edge, freshAccess.clearance(restored.positionContext()))
        assertTrue(runBlocking { InvidiousApi({ restored.server }, { null }, access = freshAccess).discovery("popular").isNotEmpty() })
        val requests = state().getJSONArray("edgeRequests")
        assertTrue((0 until requests.length()).map { requests.getJSONObject(it) }.any { it.getString("path").startsWith("/api/v1/popular") && it.getBoolean("accepted") })
        recreate(); compose.onNodeWithTag("instance-verification").assertDoesNotExist()
    }

    @Test fun edgeThenBackendTurnstileKeepsTheSameDialogUntilBothPass() {
        block("""{"edgeEnabled":true,"accessEnabled":true,"edgeRotateProbe":1}"""); solveEdge()
        until { vm.access.clearance(vm.api.context()) != null }
        compose.onNodeWithTag("instance-verification").assertExists()
        assertNull(vm.access.pass(vm.api.context()))
        clickFixture("complete"); recovered()
        assertNotNull(vm.access.pass(vm.api.context()))
        assertEquals("fixture-edge-2", vm.access.clearance(vm.api.context())?.cookie?.value)
        assertTrue(runBlocking { vm.api.discovery("popular").isNotEmpty() })
    }

    @Test fun lateCookieRotationRestoresBrowsingAndImagesWithoutRelaunching() = runBlocking {
        block(); solveEdge(); recovered()
        val originalActivity = activity; val originalModel = vm
        command("""{"edgeRotatePath":"/api/v1/edge-rotation-fixture","edgeRotateNext":1}""")
        val pending = async(Dispatchers.IO) {
            vm.access.client().newCall(Request.Builder().url(vm.store.server + "/api/v1/edge-rotation-fixture").build()).execute().use { it.code }
        }
        try {
            until { state().getJSONArray("edgeDelayedRotations").length() == 1 }
            compose.runOnUiThread { vm.refresh() }
            until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE && !vm.browse.value.loading }
            val before = state().getJSONArray("edgeRequests").length()
            compose.onNodeWithTag("instance-verification").assertExists()
            command("""{"edgeReleaseRotations":true}""")
            assertEquals(200, pending.await()); recovered()
            until { vm.browse.value.error == null && vm.browse.value.videos.isNotEmpty() }
            until {
                val requests = state().getJSONArray("edgeRequests")
                (before until requests.length()).any { index -> requests.getJSONObject(index).let {
                    it.getBoolean("accepted") && it.getString("path").startsWith("/media/thumbnail.jpg")
                } }
            }
            assertEquals("fixture-edge-2", vm.access.clearance(vm.api.context())?.cookie?.value)
            compose.runOnUiThread {
                assertSame(originalActivity, ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single())
                assertSame(originalModel, vm)
            }
        } finally { command("""{"edgeReleaseRotations":true}""") }
    }

    @Test fun aDelayedEdgeChallengeCannotUndoCompletedVerification() = runBlocking {
        command("""{"edgeEnabled":true,"accessDelayPath":"/api/v1/popular","accessDelayNext":1}""")
        val pending = async(Dispatchers.IO) {
            vm.access.client().newCall(Request.Builder().url(vm.store.server + "/api/v1/popular").build()).execute().use { it.code }
        }
        try {
            until { state().getJSONArray("accessDelayedDenials").length() == 1 }
            compose.runOnUiThread { vm.refresh() }
            until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
            solveEdge(); recovered()
            val accepted = vm.access.clearance(vm.api.context())
            command("""{"accessReleaseDenials":true}""")
            assertEquals(200, pending.await())
            assertEquals(accepted, vm.access.clearance(vm.api.context())); assertNull(vm.access.issue.value)
            assertNull(vm.browse.value.error)
            compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        } finally { command("""{"accessReleaseDenials":true}""") }
    }

    @Test fun lateRotationLoadsTheRemainingBackendChallengeInTheOpenDialog() = runBlocking {
        block(); solveEdge(); recovered()
        command("""{"edgeRotatePath":"/api/v1/edge-rotation-fixture","edgeRotateNext":1}""")
        val pending = async(Dispatchers.IO) {
            vm.access.client().newCall(Request.Builder().url(vm.store.server + "/api/v1/edge-rotation-fixture").build()).execute().use { it.code }
        }
        try {
            until { state().getJSONArray("edgeDelayedRotations").length() == 1 }
            command("""{"accessEnabled":true}""")
            compose.runOnUiThread { vm.refresh() }
            until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
            val before = state().getJSONArray("edgeRequests").length()
            command("""{"edgeReleaseRotations":true}"""); assertEquals(200, pending.await())
            until {
                val requests = state().getJSONArray("edgeRequests")
                (before until requests.length()).any { index -> requests.getJSONObject(index).let {
                    it.getBoolean("accepted") && it.getString("path").startsWith("/verify?")
                } }
            }
            compose.onNodeWithTag("instance-verification").assertExists()
            assertNull(vm.access.pass(vm.api.context())); assertNotNull(vm.access.issue.value)
            clickFixture("complete"); recovered()
            assertNotNull(vm.access.pass(vm.api.context())); assertNull(vm.browse.value.error)
        } finally { command("""{"edgeReleaseRotations":true}""") }
    }

    @Test fun rejectedNativeClearanceStaysOpenUntilExplicitRetry() {
        block("""{"edgeEnabled":true,"edgeRejectNative":true}"""); solveEdge()
        until { compose.onAllNodesWithText("Cloudflare did not accept verification for native requests. Retry verification.").fetchSemanticsNodes().isNotEmpty() }
        assertNull(vm.access.clearance(vm.api.context()))
        compose.onNodeWithTag("instance-verification").assertExists()
        command("""{"edgeRejectNative":false}""")
        compose.onNodeWithTag("instance-verification-retry").performClick(); solveEdge(); recovered()
    }

    @Test fun cancellationSurvivesRecreationAndServerChangeDiscardsThePrompt() {
        block(); compose.onNodeWithTag("instance-verification-cancel").performClick(); recreate()
        compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        compose.runOnUiThread { vm.retryBrowse() }
        until { compose.onAllNodesWithTag("instance-verification").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("instance-verification").assertExists()
        compose.onNodeWithTag("instance-verification-cancel").performClick()
        compose.runOnUiThread { vm.switchServer("http://localhost:18080") }
        until { vm.api.context().server.contains("localhost") }
        assertNull(vm.store.cloudflarePersistence.read(InstanceAccess.origin("http://127.0.0.1:18080".toHttpUrl())))
    }

    @Test fun edgeVerificationIsAvailableOnNativeSignInAndDoesNotReplayLogin() {
        compose.runOnUiThread { vm.openSignIn() }
        until { vm.route == "sign-in" }
        compose.waitForIdle()
        compose.onNodeWithTag("account-username").performTextInput("Fixture")
        compose.onNodeWithTag("account-password").performTextInput("fixture-password")
        until { compose.onAllNodes(hasTestTag("account-auth-submit") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        command("""{"edgeEnabled":true}""")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
        solveEdge(); recovered(); assertNull(vm.account.value)
        compose.onNodeWithTag("account-password").performTextClearance()
        compose.onNodeWithTag("account-password").performTextInput("fixture-password")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.account.value != null }
    }


    @Test fun clearanceRestoresAfterProcessRestart() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("cloudflareRestart") == "true")
        assertNotNull(vm.access.clearance(vm.api.context()))
        assertNull(vm.access.issue.value)
        compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        assertTrue(runBlocking { vm.api.discovery("popular").isNotEmpty() })
    }

    @Test fun registrationBootstrapCanVerifyAndThenRefreshWithoutReplayingSignup() {
        command("""{"edgeEnabled":true}""")
        compose.runOnUiThread { vm.openSignIn() }
        until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
        solveEdge(); recovered()
        compose.onNodeWithTag("authentication-true").performClick()
        compose.onNodeWithTag("account-username").performTextInput("Fixture")
        compose.onNodeWithTag("account-password").performTextInput("an uncommon signup password")
        compose.onNodeWithTag("account-confirm-password").performTextInput("an uncommon signup password")
        until { compose.onAllNodes(hasTestTag("account-auth-submit") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        assertNull(vm.account.value)
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.account.value != null }
    }

    @Test fun playbackRecoveryKeepsPausedIntentAndManagedDownloadsUseClearance() {
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.playback.value.playerState == Player.STATE_READY }
        compose.runOnUiThread { vm.controller.value?.pause(); vm.controller.value?.seekTo(4000); vm.controller.value?.setPlaybackSpeed(1.5f) }
        until { vm.playback.value.position >= 3500 && !vm.playback.value.playWhenReady }
        val occurrence = vm.queue.value.currentKey
        command("""{"edgeEnabled":true}""")
        compose.runOnUiThread { vm.refreshBuffer() }
        until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
        solveEdge(); recovered()
        until { vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.error == null && !vm.queue.value.loading }
        assertEquals(occurrence, vm.queue.value.currentKey); assertFalse(vm.playback.value.playWhenReady)
        assertTrue(vm.playback.value.position >= 3500); assertEquals(1.5f, vm.playback.value.speed, .01f)
        val id = runBlocking { app.downloads.create(vm.store.server, app.api.downloads("testvideo01"), DownloadSelection(video = "v360", audio = "aes")) }
        until { app.downloads.find(id)?.playable == true }
        assertTrue(app.downloads.find(id)!!.assets.all { it.managed && it.transferId == 0L })
    }
}
