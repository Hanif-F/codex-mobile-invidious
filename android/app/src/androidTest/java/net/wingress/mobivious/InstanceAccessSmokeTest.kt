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
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.Matcher
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Only the disposable localhost fixture emits synthetic verification events. */
@RunWith(AndroidJUnit4::class)
class InstanceAccessSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private val app get() = activity.application as MobiviousApplication
    private fun until(condition: () -> Boolean) = compose.waitUntil(60_000, condition)
    private fun command(path: String, data: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(data.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun open() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            vm.switchServer("http://127.0.0.1:18080"); vm.store.save(null)
            vm.store.verificationPersistence.write(InstanceAccess.origin(vm.store.server.toHttpUrl()), null)
            vm.access.contextChanged(); vm.refresh()
        }
        until { vm.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        command("reset")
        if (::activity.isInitialized) {
            runBlocking { app.downloads.records.value.filter { it.assets.any { a -> a.managed } }.forEach { app.downloads.delete(it.id) } }
            compose.runOnUiThread { vm.access.contextChanged(); vm.closePlayer(); vm.store.save(null); activity.finishAndRemoveTask() }
        }
    }
    private fun denied() = runBlocking { assertTrue(runCatching { vm.api.discovery("popular") }.exceptionOrNull() is InstanceAccessException) }
    private fun verify() {
        until { vm.access.issue.value != null }
        compose.onNodeWithTag("instance-access-action").performClick()
        completePage()
        until { vm.access.issue.value == null && vm.access.pass(vm.api.context()) != null }
        compose.onNodeWithText("Done").performClick()
    }
    private fun completePage() {
        compose.onNodeWithTag("instance-verification").assertExists()
        onView(isAssignableFrom(WebView::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription() = "Complete the local fixture's synthetic verification page"
            override fun perform(controller: UiController, view: View) {
                (view as WebView).evaluateJavascript("(function ready(){var b=document.getElementById('complete');if(b)b.click();else setTimeout(ready,100);})();", null)
            }
        })
    }
    @Test fun verificationUsesTheNativePageAndEncryptedInstancePass() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        runBlocking { assertTrue(vm.api.discovery("popular").isNotEmpty()) }
        val token = app.access.pass(vm.api.context())!!.token
        val stored = activity.getSharedPreferences("mobivious", 0).all.filterKeys { it.startsWith("verification.") }.values
        assertTrue(stored.isNotEmpty()); assertTrue(stored.none { it.toString().contains(token) })
        val restored = SessionStore(activity)
        assertEquals(token, restored.verificationPersistence.read(InstanceAccess.origin(vm.store.server.toHttpUrl()))?.token)
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        assertEquals(token, app.access.pass(vm.api.context())?.token)
    }
    @Test fun requiredLoginUsesNativeSignInWithRegistrationDisabledAndThenVerification() {
        command("accounts", """{"registrationEnabled":false}""")
        command("access", """{"accessEnabled":true,"accessLogin":true}""")
        denied(); until { vm.access.issue.value?.reason == AccessReason.LOGIN }
        compose.onNodeWithTag("instance-access-action").performClick()
        compose.onNodeWithTag("account-username").performTextInput("Fixture")
        compose.onNodeWithTag("account-password").performTextInput("fixture-password")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.account.value != null }
        denied(); verify()
        runBlocking { assertTrue(vm.api.discovery("popular").isNotEmpty()) }
        assertTrue(runBlocking { vm.api.registration(vm.api.context()) }.loginRequired)
    }
    @Test fun cancelledAndRecreatedVerificationCanBeRetried() {
        command("access", """{"accessEnabled":true}"""); denied()
        compose.onNodeWithTag("instance-access-action").performClick()
        compose.onNodeWithTag("instance-verification-cancel").performClick()
        compose.runOnUiThread { vm.access.prompt() }
        compose.onNodeWithTag("instance-access-action").performClick()
        val old = activity
        compose.runOnUiThread { activity.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        completePage(); until { vm.access.issue.value == null }
    }
    @Test fun unavailableVerificationAndPolicyChangesRemainRetryableWithoutSigningOut() {
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        command("access", """{"accessPolicy":2}"""); denied(); verify()
        command("access", """{"accessUnavailable":true}"""); denied()
        assertEquals(AccessReason.UNAVAILABLE, vm.access.issue.value?.reason); assertNotNull(vm.account.value)
        command("access", """{"accessUnavailable":false}""")
        runBlocking { assertTrue(vm.api.discovery("popular").isNotEmpty()) }
    }
    @Test fun playbackAccessExpiryPausesWithoutAutomaticRecoveryAndRetryKeepsPosition() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.playback.value.playerState == Player.STATE_READY }
        compose.runOnUiThread { vm.controller.value?.pause(); vm.controller.value?.seekTo(4000) }
        until { vm.playback.value.position >= 3500 }
        command("access", """{"accessExpiry":1}""")
        compose.runOnUiThread { vm.refreshBuffer() }
        until { vm.access.issue.value?.reason == AccessReason.VERIFICATION }
        assertFalse(vm.playback.value.playing)
        verify()
        compose.runOnUiThread { vm.refreshBuffer() }
        until { vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.error == null }
        assertTrue(vm.playback.value.position >= 3500); assertFalse(vm.playback.value.playing)
    }
    @Test fun protectedDownloadsRetryWithoutRepeatingCompleteAssetsAndPlayOffline() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        command("downloads", """{"downloadFailKey":"aes"}""")
        val id = runBlocking { val catalog = app.api.downloads("testvideo01"); app.downloads.create(app.store.server, catalog, DownloadSelection(video = "v360", audio = "aes")) }
        until { app.downloads.find(id)?.assets?.any { it.status == DownloadStatus.FAILED } == true && app.downloads.find(id)?.active == false }
        val completed = app.downloads.find(id)!!.assets.filter { it.status == DownloadStatus.COMPLETE }.map { it.filename }
        assertTrue(completed.isNotEmpty()); assertTrue(app.downloads.find(id)!!.assets.all { it.managed && it.transferId == 0L })
        command("downloads", """{"downloadFailKey":""}""")
        runBlocking { app.downloads.retry(id) }
        until { app.downloads.find(id)?.playable == true }
        assertTrue(app.downloads.find(id)!!.assets.filter { it.status == DownloadStatus.COMPLETE }.map { it.filename }.containsAll(completed))
        command("access", """{"accessLogin":true,"accessExpiry":1}""")
        compose.runOnUiThread { vm.playDownload(id) }
        until { vm.playback.value.downloadId == id && vm.playback.value.playerState == Player.STATE_READY }
    }
    @Test fun requiredSignInDuringPlaybackRetainsThePausedPlayerUntilExplicitRetry() {
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.playback.value.playerState == Player.STATE_READY }
        compose.runOnUiThread { vm.controller.value?.pause(); vm.controller.value?.seekTo(4000); vm.controller.value?.setPlaybackSpeed(1.5f) }
        until { vm.playback.value.position >= 3500 }
        val occurrence = vm.queue.value.currentKey
        command("access", """{"accessLogin":true}"""); denied()
        compose.onNodeWithTag("instance-access-action").performClick()
        compose.onNodeWithTag("account-username").performTextInput("Fixture")
        compose.onNodeWithTag("account-password").performTextInput("fixture-password")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()
        until { vm.account.value != null && vm.queue.value.context == vm.api.context() }
        assertEquals(occurrence, vm.queue.value.currentKey)
        assertFalse(vm.playback.value.playing)
        compose.runOnUiThread { vm.retryPlayback() }
        until { vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.error == null }
        assertTrue(vm.playback.value.position >= 3500); assertEquals(1.5f, vm.playback.value.speed, .01f)
    }
    @Test fun protectedTransferOwnershipChangesStopWorkAndCancellationStaysRetryable() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        command("downloads", """{"downloadSlow":true}""")
        val id = runBlocking { val catalog = app.api.downloads("testvideo01"); app.downloads.create(app.store.server, catalog, DownloadSelection(video = "v360", audio = "aes")) }
        until { app.downloads.find(id)?.assets?.any { it.status == DownloadStatus.RUNNING } == true }
        compose.runOnUiThread { vm.store.save(Account("another-token", "Another", Long.MAX_VALUE, vm.store.server)) }
        until { app.downloads.find(id)?.active == false }
        assertTrue(app.downloads.find(id)!!.assets.any { it.status == DownloadStatus.FAILED })
        runBlocking { app.downloads.cancel(id) }
        assertTrue(app.downloads.find(id)!!.assets.all { it.status in listOf(DownloadStatus.COMPLETE, DownloadStatus.CANCELLED) })
        command("downloads", """{"downloadSlow":false}""")
        compose.runOnUiThread { vm.store.save(null) }
        runBlocking { app.downloads.retry(id) }
        until { app.downloads.find(id)?.playable == true }
    }
    @Test fun backgroundAndPipAccessExpiryPauseUntilVerificationAndExplicitRetry() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        val background = vm.store.background; val pip = vm.store.pip
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            compose.runOnUiThread { vm.store.background = true; vm.store.pip = false; vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
            until { vm.playback.value.playerState == Player.STATE_READY }
            for (inPip in listOf(false, true)) {
                compose.runOnUiThread {
                    vm.controller.value?.play()
                    if (inPip) { vm.store.pip = true; activity.enterPip() }
                    else { activity.updatePip(false); activity.moveTaskToBack(true) }
                }
                if (inPip) {
                    until { activity.isInPictureInPictureMode }
                    Thread.sleep(1500) // Finish the system PiP enter animation before reopening the task.
                }
                command("access", """{"accessExpiry":1}""")
                compose.runOnUiThread { vm.refreshBuffer() }
                until { vm.access.issue.value?.reason == AccessReason.VERIFICATION && !vm.playback.value.playing }
                assertFalse(vm.playback.value.recovering)
                instrumentation.uiAutomation.executeShellCommand("am start -n ${instrumentation.targetContext.packageName}/net.wingress.mobivious.MainActivity --activity-clear-top")
                    .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
                until { !activity.isInPictureInPictureMode && activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
                verify()
                compose.runOnUiThread { vm.refreshBuffer() }
                until { vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.error == null }
            }
        } finally { compose.runOnUiThread { vm.store.background = background; vm.store.pip = pip } }
    }
    @Test fun rendererFailureOffersAFreshPageWithoutCrashingTheApp() {
        command("access", """{"accessEnabled":true}"""); denied()
        compose.onNodeWithTag("instance-access-action").performClick()
        compose.onNodeWithTag("instance-verification").assertExists()
        onView(isAssignableFrom(WebView::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription() = "Terminate the local verification renderer"
            override fun perform(controller: UiController, view: View) { assertTrue((view as WebView).webViewRenderProcess!!.terminate()) }
        })
        until { compose.onAllNodesWithText("Verification stopped. Retry to load a fresh page.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("instance-verification-retry").performClick()
        completePage(); until { vm.access.issue.value == null }
    }
    @Test fun subframeCompletionsAreIgnoredAndRetryReplacesTheVisiblePage() {
        command("access", """{"accessEnabled":true}"""); denied()
        compose.onNodeWithTag("instance-access-action").performClick()
        compose.onNodeWithTag("instance-verification").assertExists()
        onView(isAssignableFrom(WebView::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription() = "Send a fixture pass from an untrusted subframe"
            override fun perform(controller: UiController, view: View) {
                (view as WebView).evaluateJavascript("""var frame=document.createElement('iframe');frame.src='/verify?client=native';frame.onload=function(){frame.contentWindow.mobiviousVerification.postMessage(JSON.stringify({verificationToken:'fixture-pass-1',expiresAt:Math.floor(Date.now()/1000)+3600,enabled:true}));};document.body.appendChild(frame);""", null)
                controller.loopMainThreadForAtLeast(500)
            }
        })
        compose.waitForIdle(); assertNull(vm.access.pass(vm.api.context()))
        compose.onNodeWithTag("instance-verification-retry").performClick()
        completePage(); until { vm.access.issue.value == null }
    }
}
