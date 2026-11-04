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

    @Test fun edgeThenBackendTurnstileUsesSeparateDialogsUntilBothPass() {
        block("""{"edgeEnabled":true,"accessEnabled":true,"edgeRotateProbe":1}""")
        compose.onNodeWithText("Cloudflare verification").assertExists()
        solveEdge()
        until { vm.access.issue.value?.reason == AccessReason.VERIFICATION }
        compose.onNodeWithText("Instance verification").assertExists()
        compose.onNodeWithText("Cloudflare verification").assertDoesNotExist()
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

    @Test fun lateRotationOpensTheSeparateBackendDialog() = remainingBackendAfterRotation(cancel = false)

    @Test fun cancelledCloudflareStaysDismissedWhenRotationRevealsBackendVerification() = remainingBackendAfterRotation(cancel = true)

    private fun remainingBackendAfterRotation(cancel: Boolean) = runBlocking {
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
            if (cancel) compose.onNodeWithTag("instance-verification-cancel").performClick()
            val before = state().getJSONArray("edgeRequests").length()
            command("""{"edgeReleaseRotations":true}"""); assertEquals(200, pending.await())
            until { vm.access.issue.value?.reason == AccessReason.VERIFICATION }
            if (cancel) {
                compose.onNodeWithTag("instance-verification").assertDoesNotExist()
                compose.runOnUiThread { vm.retryBrowse() }
            }
            until {
                val requests = state().getJSONArray("edgeRequests")
                (before until requests.length()).any { index -> requests.getJSONObject(index).let {
                    it.getBoolean("accepted") && it.getString("path").startsWith("/verify?")
                } }
            }
            compose.onNodeWithTag("instance-verification").assertExists()
            compose.onNodeWithText("Instance verification").assertExists()
            assertEquals(AccessReason.VERIFICATION, vm.access.issue.value?.reason)
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

    @Test fun aNewBrowserCookieRecoversAfterARejectedCandidateWithoutReloadingTheDialog() {
        block("""{"edgeEnabled":true,"edgeRejectNative":true}"""); solveEdge()
        val rejection = "Cloudflare did not accept verification for native requests. Retry verification."
        until { compose.onAllNodesWithText(rejection).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cloudflare verification").assertExists()
        val before = state().getJSONArray("edgeRequests").length()
        android.os.SystemClock.sleep(800)
        assertEquals("A rejected cookie must not be probed repeatedly", before, state().getJSONArray("edgeRequests").length())
        command("""{"edgeRejectNative":false,"edgePolicy":2}""")
        onView(isAssignableFrom(WebView::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription() = "Issue a newer disposable browser cookie without recreating the page"
            override fun perform(controller: UiController, view: View) {
                (view as WebView).evaluateJavascript("fetch('/test/edge-complete',{method:'POST',body:'{}'});", null)
            }
        })
        recovered()
        assertEquals("fixture-edge-2", vm.access.clearance(vm.api.context())?.cookie?.value)
        assertNull(vm.browse.value.error)
    }

    private fun recoverInitialAccountSettings(hidden: Boolean, backend: Boolean = false, failedRead: Boolean = false) {
        val originalActivity = activity; val originalModel = vm
        val video = vm.browse.value.videos.first()
        command(JSONObject().put("watched", org.json.JSONArray(listOf(video.id))).put("seedPlaylist", true).toString(), "watched")
        val imageMarker = "verified-history=$hidden-$backend-$failedRead"
        val thumbnail = video.thumbnail + (if ('?' in video.thumbnail) "&" else "?") + imageMarker
        val history = JSONObject().put("video_id", video.id).put("title", video.title).put("channel_name", video.author)
            .put("channel_id", video.channelId).put("length_seconds", video.duration).put("latest_watched", "2026-10-04")
            .put("videoThumbnails", org.json.JSONArray().put(JSONObject().put("url", thumbnail)))
        command(JSONObject().put("historyEntries", org.json.JSONArray().put(history)).toString(), "search-history")
        val rules = if (hidden) mapOf(video.channelId to ChannelSpoilerRule(video.author, true, true)) else emptyMap()
        command(JSONObject().put("default_home", "Trending").put("channel_spoiler_settings", ChannelSpoilerRules.json(rules)).toString(), "preferences")
        command(JSONObject().put("edgeEnabled", !backend).put("accessEnabled", backend).toString())
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", 9999999999, vm.store.server)) }
        until { vm.access.issue.value != null && vm.spoilerSettingsError.value != null }
        assertFalse(vm.channelSpoilers.state.value.ready)
        compose.runOnUiThread { vm.navigate("You", "history") }
        until { !vm.browse.value.loading }
        if (failedRead) command("""{"failRead":true}""", "spoilers")
        if (backend) clickFixture("complete") else solveEdge()
        until { vm.access.issue.value == null }
        if (failedRead) {
            until { vm.spoilerSettingsError.value != null && !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
            assertFalse(vm.channelSpoilers.state.value.ready)
            compose.onNodeWithTag("channel-settings-retry").assertExists()
            compose.onNodeWithText(video.title).assertDoesNotExist()
            command("""{"failRead":false}""", "spoilers")
            compose.onNodeWithTag("channel-settings-retry").performClick()
        }
        until { vm.channelSpoilers.state.value.ready && !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        until { vm.blocked.value.error == null && vm.subscriptionChannels.value.loaded && vm.playlists.value.isNotEmpty() }
        assertEquals("history", vm.route); assertEquals("You", vm.tab)
        assertEquals("Trending", vm.preferences.value.defaultHome)
        assertNull(vm.spoilerSettingsError.value); assertNull(vm.browse.value.error)
        assertEquals(rules, vm.channelSpoilers.state.value.rules)
        compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        if (!hidden && !backend) until {
            val requests = state().getJSONArray("edgeRequests")
            (0 until requests.length()).any { index -> requests.getJSONObject(index).let {
                it.getBoolean("accepted") && it.getString("path").contains(imageMarker)
            } }
        }
        captureWatchScreenshot(if (hidden) "verified-account-history-masked" else "verified-account-history")
        compose.runOnUiThread { vm.navigate("Popular") }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        if (hidden) {
            compose.onNodeWithText(video.title).assertDoesNotExist()
            compose.onNodeWithTag("spoiler-thumbnail-${video.id}").assertExists()
        } else {
            compose.onNodeWithText(video.title).assertExists()
            compose.onNodeWithTag("spoiler-thumbnail-${video.id}").assertDoesNotExist()
            until {
                val requests = state().getJSONArray("edgeRequests")
                (0 until requests.length()).any { index -> requests.getJSONObject(index).let {
                    it.getBoolean("accepted") && it.getString("path").startsWith("/media/thumbnail.jpg")
                } }
            }
        }
        compose.runOnUiThread {
            assertSame(originalActivity, ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single())
            assertSame(originalModel, vm)
        }
        captureWatchScreenshot(if (hidden) "verified-account-popular-masked" else "verified-account-popular")
    }

    @Test fun challengedInitialAccountSettingsRestoreHistoryAndPopularWithoutReopening() = recoverInitialAccountSettings(hidden = false)

    @Test fun challengedInitialAccountSettingsPreserveIntentionalSpoilerRules() = recoverInitialAccountSettings(hidden = true)

    @Test fun backendVerificationAlsoRestoresInitialAccountSettings() = recoverInitialAccountSettings(hidden = true, backend = true)

    @Test fun failedAccountSettingsRecoveryOffersRetryOnTheAffectedHistory() = recoverInitialAccountSettings(hidden = false, failedRead = true)

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

    @Test fun choosingRequiredSignInDoesNotSuppressALaterCloudflareChallenge() {
        command("""{"accessLogin":true}""")
        compose.runOnUiThread { vm.refresh() }
        until { vm.access.issue.value?.reason == AccessReason.LOGIN }
        compose.onNodeWithTag("instance-access-action").performClick()
        until { vm.route == "sign-in" }
        command("""{"edgeEnabled":true}""")
        compose.runOnUiThread { vm.action { vm.api.registration(vm.api.context()) } }
        until { vm.access.issue.value?.reason == AccessReason.CLOUDFLARE }
        compose.onNodeWithText("Cloudflare verification").assertExists()
        command("""{"accessLogin":false}""")
        solveEdge(); recovered()
        assertEquals("sign-in", vm.route)
        assertNull(vm.account.value)
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
