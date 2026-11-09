package net.wingress.mobivious

import android.content.Intent
import android.view.View
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
import net.wingress.mobivious.ui.ChannelHeader
import net.wingress.mobivious.ui.CommunityPostCard
import net.wingress.mobivious.ui.InstanceAccessPrompt
import net.wingress.mobivious.ui.InstanceImage
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.Matcher
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun verifiedBrowseRequests(path: String): Int {
        val requests = fixture().getJSONArray("accessRequests")
        return (0 until requests.length()).count {
            val request = requests.getJSONObject(it)
            request.getString("path").substringBefore('?') == path && request.getBoolean("verification")
        }
    }
    private fun recreate() {
        val old = activity
        compose.runOnUiThread { activity.recreate() }
        until { var ready = false; compose.runOnUiThread { ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity && it !== old } }; ready }
        compose.runOnUiThread { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
    }
    private fun returnToForeground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("am start -n ${instrumentation.targetContext.packageName}/net.wingress.mobivious.MainActivity --activity-clear-top")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        until { !activity.isInPictureInPictureMode && activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
    }
    private fun verify(duplicate: Boolean = false) {
        until { vm.access.issue.value != null }
        compose.onNodeWithTag("instance-access-action").assertDoesNotExist()
        completePage(duplicate)
        until { vm.access.issue.value == null && vm.access.pass(vm.api.context()) != null }
        until { !vm.browse.value.loading }
        compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        compose.onNodeWithText("Access restored").assertDoesNotExist()
    }
    private fun completePage(duplicate: Boolean = false) {
        compose.onNodeWithTag("instance-verification").assertExists()
        onView(isAssignableFrom(WebView::class.java)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription() = "Complete the local fixture's synthetic verification page"
            override fun perform(controller: UiController, view: View) {
                val repeat = if (duplicate) "window.addEventListener('invidious-verification-complete',function again(e){window.removeEventListener('invidious-verification-complete',again);window.dispatchEvent(new CustomEvent('invidious-verification-complete',{detail:e.detail}));});" else ""
                (view as WebView).evaluateJavascript("(function ready(){var b=document.getElementById('complete');if(b){${repeat}b.click();}else setTimeout(ready,100);})();", null)
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
    @Test fun automaticVerificationCoalescesDenialsAndRetriesBrowsingOnce() {
        command("access", """{"accessEnabled":true}""")
        compose.runOnUiThread { vm.refresh() }
        until { !vm.browse.value.loading && vm.browse.value.error != null }
        denied(); denied()
        val before = verifiedBrowseRequests("/api/v1/popular")
        compose.onAllNodesWithTag("instance-verification").assertCountEquals(1)
        verify(duplicate = true)
        until { vm.browse.value.error == null && vm.browse.value.videos.isNotEmpty() }
        assertEquals(before + 1, verifiedBrowseRequests("/api/v1/popular"))
    }
    @Test fun verificationWaitsForTheCurrentBrowseRequestBeforeRetrying() {
        command("discovery-search", """{"discoveryDelayNext":4000}""")
        compose.runOnUiThread { vm.refresh() }
        until { vm.browse.value.loading && fixture().getInt("discoveryDelayNext") == 0 }
        command("access", """{"accessEnabled":true}"""); denied()
        val before = verifiedBrowseRequests("/api/v1/popular")
        verify()
        until { !vm.browse.value.loading && verifiedBrowseRequests("/api/v1/popular") == before + 1 }
        assertNull(vm.browse.value.error)
    }
    @Test fun automaticVerificationRetriesTheFailedSearchPageWithoutLosingEarlierResults() {
        command("discovery-search", """{"searchMixed":true}""")
        compose.runOnUiThread { vm.openGlobalSearch("mixed") }
        until { !vm.browse.value.loading && vm.browse.value.searchResults.isNotEmpty() }
        val first = vm.browse.value.searchResults
        command("access", """{"accessEnabled":true}""")
        compose.runOnUiThread { vm.more() }
        until { !vm.browse.value.loading && vm.browse.value.retryMore }
        val before = verifiedBrowseRequests("/api/v1/search")
        verify()
        until { vm.browse.value.page == 2 && !vm.browse.value.loading }
        assertNull(vm.browse.value.error)
        assertTrue(vm.browse.value.searchResults.containsAll(first))
        assertTrue(vm.browse.value.searchResults.size > first.size)
        assertEquals(before + 1, verifiedBrowseRequests("/api/v1/search"))
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
    @Test fun browseLoginCardOpensNativeSignInAndReloadsPopular() {
        command("access", """{"accessLogin":true}""")
        compose.runOnUiThread { vm.navigate("Popular") }
        until { vm.browse.value.errorReason == AccessReason.LOGIN }
        compose.onNodeWithText("Later").performClick()

        val inCard = hasAnyAncestor(hasTestTag("browse-error-card"))
        val signIn = compose.onNode(hasText("Sign in") and inCard)
        compose.revealInBrowse(hasTestTag("browse-error-card"))
        compose.onNode(hasText("Sign in to continue.") and inCard).assertIsDisplayed()
        compose.onNode(hasText("Retry") and inCard).assertDoesNotExist()
        compose.onNodeWithText("Configure server").assertExists()
        signIn.assertIsDisplayed().assertHasClickAction()
        captureWatchScreenshot("browse-login-required")

        signIn.performClick()
        compose.onNodeWithTag("account-username").assertIsDisplayed()
        compose.navigateBack()
        compose.revealInBrowse(hasTestTag("browse-error-card"))
        signIn.assertIsDisplayed().performClick()
        compose.onNodeWithTag("account-username").performTextInput("Fixture")
        compose.onNodeWithTag("account-password").performTextInput("fixture-password")
        compose.onNodeWithTag("account-auth-submit").performScrollTo().performClick()

        until { vm.account.value != null && vm.tab == "Popular" && vm.route.isEmpty() &&
            !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
        assertEquals(1, vm.browse.value.page)
        compose.onNodeWithTag("browse-error-card").assertDoesNotExist()
    }
    @Test fun cancelledAndRecreatedVerificationCanBeRetried() {
        command("access", """{"accessEnabled":true}"""); denied()
        compose.onNodeWithTag("instance-verification-cancel").performClick()
        denied(); recreate(); denied()
        compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        compose.runOnUiThread { vm.retryBrowse() }
        compose.onNodeWithTag("instance-verification").assertExists()
        recreate()
        compose.onNodeWithTag("instance-verification").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        denied(); compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        compose.runOnUiThread { vm.retryBrowse() }
        verify()
    }
    @Test fun accountAndServerChangesDiscardVerificationAndRejectStaleCompletions() {
        for (changeAccount in listOf(true, false)) {
            command("access", """{"accessEnabled":true}"""); denied()
            compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
            until { !vm.queue.value.loading && vm.queue.value.error != null }
            compose.onNodeWithTag("instance-verification").assertExists()
            val previous = vm.api.context()
            command("access", """{"accessEnabled":false}""")
            compose.runOnUiThread {
                if (changeAccount) vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server))
                else vm.switchServer("http://localhost:18080")
                vm.access.report(previous, AccessReason.VERIFICATION)
                vm.retryBrowseAfterVerification(previous)
            }
            until { vm.access.issue.value == null }
            until { vm.queue.value.currentKey == null }
            compose.onNodeWithTag("instance-verification").assertDoesNotExist()
            runBlocking {
                try { vm.access.complete(previous, "stale-pass", Long.MAX_VALUE, true); fail("Stale completion accepted") }
                catch (_: kotlinx.coroutines.CancellationException) { }
            }
            assertNull(vm.access.pass(vm.api.context()))
        }
    }
    @Test fun verificationCompletingInBackgroundKeepsPlaybackPausedWhenBackgroundIsDisabled() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.playback.value.playerState == Player.STATE_READY }
        val background = vm.store.background
        try {
            command("access", """{"accessExpiry":1}""")
            compose.runOnUiThread { vm.controller.value?.play(); vm.refreshBuffer() }
            until { vm.queue.value.error != null && vm.access.issue.value != null }
            compose.runOnUiThread { vm.store.background = false; activity.updatePip(false); activity.moveTaskToBack(true) }
            until { !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
            val expiry = System.currentTimeMillis() / 1000 + 3600
            command("access", """{"accessExpiry":$expiry}""")
            runBlocking { vm.access.complete(vm.api.context(), "fixture-pass-1", expiry, true) }
            until { !vm.queue.value.loading && vm.queue.value.error == null && vm.playback.value.playerState == Player.STATE_READY }
            assertFalse(vm.playback.value.playWhenReady)
            returnToForeground()
            assertFalse(vm.playback.value.playWhenReady)
        } finally { compose.runOnUiThread { vm.store.background = background } }
    }
    @Test fun unavailableVerificationAndPolicyChangesRemainRetryableWithoutSigningOut() {
        compose.runOnUiThread { vm.store.save(Account("fixture-token", "Fixture", Long.MAX_VALUE, vm.store.server)) }
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        command("access", """{"accessPolicy":2}"""); denied(); verify()
        command("access", """{"accessUnavailable":true}"""); denied()
        assertEquals(AccessReason.UNAVAILABLE, vm.access.issue.value?.reason); assertNotNull(vm.account.value)
        compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        compose.onNodeWithTag("instance-access-action").assertExists()
        command("access", """{"accessUnavailable":false}""")
        runBlocking { assertTrue(vm.api.discovery("popular").isNotEmpty()) }
    }
    @Test fun playbackVerificationAutomaticallyRetriesAndKeepsPositionAndOptions() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.playback.value.playerState == Player.STATE_READY }
        val occurrence = vm.queue.value.currentKey
        for (playing in listOf(false, true)) {
            compose.runOnUiThread {
                vm.controller.value?.pause(); vm.controller.value?.seekTo(4000)
                vm.controller.value?.setPlaybackSpeed(1.5f); vm.audioOnly(true)
                if (playing) vm.controller.value?.play()
            }
            until { vm.playback.value.position >= 3500 && vm.playback.value.playWhenReady == playing }
            command("access", """{"accessExpiry":1}""")
            compose.runOnUiThread { vm.refreshBuffer() }
            until { vm.access.issue.value?.reason == AccessReason.VERIFICATION && vm.queue.value.error != null }
            assertFalse(vm.playback.value.playing)
            verify(duplicate = true)
            until { vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.error == null && !vm.queue.value.loading }
            assertEquals(occurrence, vm.queue.value.currentKey)
            assertTrue(vm.playback.value.position >= 3500)
            assertEquals(playing, vm.playback.value.playWhenReady)
            assertEquals(1.5f, vm.playback.value.speed, .01f)
            compose.runOnUiThread {
                assertTrue(vm.controller.value!!.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO))
            }
        }
    }
    @Test fun initialMetadataVerificationRetriesOnceAndPreservesAutoplayIntent() {
        for (autoplay in listOf(true, false)) {
            command("access", """{"accessEnabled":true,"accessExpiry":1}""")
            compose.runOnUiThread {
                vm.openLink(VideoLinks.parse("http://127.0.0.1:18080/watch?v=testvideo01&autoplay=${if (autoplay) 1 else 0}", vm.store.server)!!)
                activity.sharedVideo.value = true
            }
            until { !vm.queue.value.loading && vm.queue.value.error != null }
            val token = vm.queue.value.token; val occurrence = vm.queue.value.currentKey
            val before = verifiedBrowseRequests("/api/v1/videos/testvideo01")
            verify(duplicate = true)
            until { !vm.queue.value.loading && vm.queue.value.error == null && vm.playback.value.playerState == Player.STATE_READY }
            assertEquals(token, vm.queue.value.token); assertEquals(occurrence, vm.queue.value.currentKey)
            assertEquals(autoplay, vm.playback.value.playWhenReady)
            assertEquals(before + 1, verifiedBrowseRequests("/api/v1/videos/testvideo01"))
        }
    }
    @Test fun closingOrReplacingBlockedPlaybackDiscardsItsPendingRecovery() {
        command("access", """{"accessEnabled":true}""")
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.queue.value.error != null && !vm.queue.value.loading }
        compose.onNodeWithTag("instance-verification-cancel").performClick()
        compose.runOnUiThread { vm.closePlayer(); vm.retryBrowse() }
        verify()
        assertNull(vm.queue.value.currentKey)
        command("access", """{"accessExpiry":1}""")
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
        until { vm.queue.value.error != null && !vm.queue.value.loading }
        compose.onNodeWithTag("instance-verification-cancel").performClick()
        compose.runOnUiThread { vm.openLink(VideoLink("testvideo02")); vm.retryBrowse() }
        until { vm.queue.value.current?.video?.id == "testvideo02" && vm.queue.value.error != null && !vm.queue.value.loading }
        verify()
        until { vm.playback.value.playerState == Player.STATE_READY && vm.queue.value.error == null && !vm.queue.value.loading }
        assertEquals("testvideo02", vm.queue.value.current?.video?.id)
    }
    @Test fun visibleImagesAndBannersRecoverWhileAnOldDenialArrivesAfterVerification() {
        // A prior instrumentation process may have successfully cached these fixture images.
        val runId = System.nanoTime()
        command("access", """{"accessEnabled":true,"accessDelayPath":"/media/thumbnail.jpg","accessDelayNext":1}""")
        val client = vm.access.client()
        val pending = CompletableFuture.supplyAsync {
            client.newCall(okhttp3.Request.Builder().url("http://127.0.0.1:18080/media/thumbnail.jpg?delayed=$runId").build()).execute().use { it.code }
        }
        try {
            until { fixture().getJSONArray("accessDelayedDenials").length() == 1 }
            val failures = AtomicInteger(); val successes = AtomicInteger()
            val request = coil.request.ImageRequest.Builder(activity).data("http://127.0.0.1:18080/media/thumbnail.jpg?visible=$runId")
                .listener(onError = { _, _ -> failures.incrementAndGet() }, onSuccess = { _, _ -> successes.incrementAndGet() }).build()
            val channel = Channel("UC${"a".repeat(22)}", "Recovery author", image = "/ggpht/studio=s88?recovery=$runId",
                banner = "/ggpht/banner=w1060-h175?banner=$runId")
            val post = CommunityPost(Comment("Recovery author", "Recovery post", "", null, id = "Ugrecovery"), channel.id,
                attachment = PostAttachment.Images(listOf(PostImage("/ggpht/post=w1280?recovery-post=$runId", 1280, 720))))
            compose.runOnUiThread {
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            InstanceImage(request, "Recovery thumbnail", Modifier.size(96.dp).testTag("recovery-thumbnail"))
                            ChannelHeader(channel, vm.store.server, false, false, subscribe = {})
                            CommunityPostCard(vm, post, comments = {}, channel = {}, link = {}, play = {}, playlist = {}, signIn = {})
                        }
                        InstanceAccessPrompt(vm, true, {})
                    }
                }
            }
            until { failures.get() > 0 && vm.access.issue.value != null }
            // The banner's own fallback removes its painter after a denial.
            until { compose.onAllNodesWithTag("channel-banner").fetchSemanticsNodes().isEmpty() }
            until { compose.onAllNodesWithText("Image unavailable").fetchSemanticsNodes().isNotEmpty() }
            verify()
            until { successes.get() == 1 }
            compose.onNodeWithTag("channel-banner").assertExists()
            until { compose.onAllNodesWithText("Image unavailable").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("recovery-thumbnail").captureToImage()
            captureWatchScreenshot("verification-image-recovery")
            // Offscreen images defer Coil's size resolution until drawn; reveal the recovered post.
            compose.onNodeWithTag("post-media-Ugrecovery").performScrollTo()
            until {
                val requests = fixture().getJSONArray("accessRequests")
                (0 until requests.length()).any { requests.getJSONObject(it).let { row ->
                    row.getString("path").contains("?banner") && row.getBoolean("verification")
                } } && (0 until requests.length()).any { requests.getJSONObject(it).let { row ->
                    row.getString("path").contains("?recovery-post") && row.getBoolean("verification")
                } }
            }
            command("access", """{"accessReleaseDenials":true}""")
            assertEquals(200, pending.get(10, TimeUnit.SECONDS))
            assertNull(vm.access.issue.value); assertNotNull(vm.access.pass(vm.api.context()))
            compose.onNodeWithTag("instance-verification").assertDoesNotExist()
        } finally {
            command("access", """{"accessReleaseDenials":true}""")
        }
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
    @Test fun backgroundAndPipAccessExpiryResumeAfterForegroundVerification() {
        command("access", """{"accessEnabled":true}"""); denied(); verify()
        val background = vm.store.background; val pip = vm.store.pip
        try {
            compose.runOnUiThread { vm.store.background = true; vm.store.pip = false; vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
            until { vm.playback.value.playerState == Player.STATE_READY }
            val occurrence = vm.queue.value.currentKey
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
                assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("Verify access").isNullOrEmpty())
                returnToForeground()
                verify()
                assertEquals(occurrence, vm.queue.value.currentKey)
                until { vm.playback.value.playerState == Player.STATE_READY && vm.playback.value.error == null && vm.playback.value.playWhenReady }
            }
        } finally { compose.runOnUiThread { vm.store.background = background; vm.store.pip = pip } }
    }
    @Test fun cancelledVerificationStaysDismissedAcrossBackgroundAndPipUntilExplicitRetry() {
        val background = vm.store.background; val pip = vm.store.pip
        try {
            compose.runOnUiThread { vm.store.background = true; vm.openLink(VideoLink("testvideo01")); activity.sharedVideo.value = true }
            until { vm.playback.value.playerState == Player.STATE_READY }
            compose.runOnUiThread { vm.controller.value?.pause() }
            command("access", """{"accessEnabled":true}"""); denied()
            compose.onNodeWithTag("instance-verification-cancel").performClick()
            for (inPip in listOf(false, true)) {
                compose.runOnUiThread {
                    vm.store.pip = inPip
                    if (inPip) activity.enterPip()
                    else { activity.updatePip(false); activity.moveTaskToBack(true) }
                }
                until { if (inPip) activity.isInPictureInPictureMode else !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
                if (inPip) Thread.sleep(1500)
                denied(); returnToForeground()
                compose.onNodeWithTag("instance-verification").assertDoesNotExist()
            }
            compose.runOnUiThread { vm.retryBrowse() }
            verify()
        } finally { compose.runOnUiThread { vm.store.background = background; vm.store.pip = pip } }
    }
    @Test fun rendererFailureOffersAFreshPageWithoutCrashingTheApp() {
        command("access", """{"accessEnabled":true}"""); denied()
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
