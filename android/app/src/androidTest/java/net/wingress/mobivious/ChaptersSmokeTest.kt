package net.wingress.mobivious

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.media3.common.Player
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Real service-owned playback, using only the localhost fixture and generated media. */
@RunWith(AndroidJUnit4::class)
class ChaptersSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val base = "http://127.0.0.1:18080"
    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)
    private fun until(block: () -> Boolean) = compose.waitUntil(30_000, block)
    private fun command(name: String, body: String = "{}") {
        (URL("$base/test/$name").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }
            inputStream.close(); disconnect()
        }
    }
    private fun fixture() = JSONObject(URL("$base/test/state").readText())
    private fun metadataRequests() = fixture().getJSONArray("videoDetailRequests").length()
    private fun images(): Int {
        val state = fixture()
        return state.getJSONArray("avatarRequests").length() + (0 until state.getJSONArray("mediaPaths").length())
            .count { state.getJSONArray("mediaPaths").getString(it).endsWith(".jpg") }
    }
    private fun incoming(path: String) = ui {
        activity.startActivity(Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, base + path))
    }
    private fun ready() = until {
        !activity.model.queue.value.loading && activity.model.playback.value.playerState == Player.STATE_READY &&
            activity.model.queue.value.details != null && activity.model.playback.value.details == activity.model.queue.value.details &&
            compose.onAllNodesWithTag("player-play-pause").fetchSemanticsNodes().isNotEmpty()
    }
    private fun reacquire() = until {
        var resumed: MainActivity? = null
        ui { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
        if (resumed != null) activity = resumed!!
        resumed != null && activity.model.controller.value != null
    }
    private fun chapters() {
        compose.onNodeWithTag("player-chapter-title").performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("chapters-panel").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("chapters-panel").assertIsDisplayed()
    }

    @Before fun launch() {
        command("reset"); command("chapters")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Configure localhost BEFORE constructing the Activity/ViewModel, so startup cannot browse the live instance.
        instrumentation.runOnMainSync {
            val app = instrumentation.targetContext.applicationContext as MobiviousApplication
            app.store.save(null); app.store.server = base; app.cache.clear()
            app.store.guestDeArrow(AccountPreferences(autoplay = false, continueAutoplay = false, thinMode = true))
        }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    }
    @After fun close() { if (::activity.isInitialized) ui { activity.model.closePlayer(); activity.finishAndRemoveTask() } }

    @Test fun selectingAndScrubbingPreservePlaybackAndMakeNoPreviewOrMetadataRequests() {
        incoming("/watch?v=testvideo01&autoplay=0"); ready()
        val occurrence = activity.model.queue.value.currentKey
        val metadata = metadataRequests(); val imageCount = images()
        chapters()
        compose.onNodeWithTag("chapter-30000").performClick()
        until { activity.model.playback.value.position == 30000L }
        assertFalse(activity.model.playback.value.playWhenReady)
        compose.onNodeWithTag("chapter-30000").assertIsSelected()
        compose.onNodeWithTag("chapters-panel").assertExists()
        ui { activity.model.togglePlay() }
        until { activity.model.playback.value.playing }
        compose.onNodeWithTag("chapter-60000").performClick()
        until { activity.model.playback.value.position in 60000L..65000L && activity.model.playback.value.playing }
        assertTrue(activity.model.playback.value.playWhenReady)
        ui { activity.model.togglePlay() }; until { !activity.model.playback.value.playWhenReady }
        compose.onNodeWithTag("player-timeline").performTouchInput { down(center); moveTo(center.copy(x = width * .4f)); up() }
        until { activity.model.playback.value.position in 44000L..52000L }
        compose.onNodeWithTag("player-current-chapter", useUnmergedTree = true).assertTextEquals("日本語 & details")
        assertEquals(occurrence, activity.model.queue.value.currentKey)
        assertEquals(metadata, metadataRequests()); assertEquals(imageCount, images())
        assertEquals(0, fixture().getJSONArray("chapterAssetRequests").length())
    }

    @Test fun inlineFooterAndCenteredPlaybackStayInPlaceAcrossFullscreen() {
        incoming("/watch?v=testvideo01&autoplay=0"); ready()
        fun checkControls(fullscreen: Boolean) {
            val surface = compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot()
            val button = compose.onNodeWithTag("player-play-pause").getUnclippedBoundsInRoot()
            assertEquals((surface.left + surface.right).value / 2, (button.left + button.right).value / 2, .5f)
            assertEquals((surface.top + surface.bottom).value / 2, (button.top + button.bottom).value / 2, .5f)
            val timeline = compose.onNodeWithTag("player-timeline").getUnclippedBoundsInRoot()
            val time = compose.onNodeWithTag("player-time").getUnclippedBoundsInRoot()
            val chapter = compose.onNodeWithTag("player-chapter-title").getUnclippedBoundsInRoot()
            val settings = compose.onNodeWithContentDescription("Player settings").getUnclippedBoundsInRoot()
            val expand = compose.onNodeWithContentDescription(if (fullscreen) "Exit full screen" else "Full screen").getUnclippedBoundsInRoot()
            val rail = compose.onNodeWithTag("player-footer").getUnclippedBoundsInRoot()
            assertTrue("Time $time must fit in rail $rail", time.top >= rail.top && time.bottom <= rail.bottom)
            assertTrue("Seek target $timeline must fit in rail $rail", timeline.top >= rail.top && timeline.bottom <= rail.bottom)
            assertTrue(chapter.bottom < timeline.top)
            assertTrue(chapter.right <= settings.left && settings.right <= expand.left)
        }
        checkControls(false)
        compose.onNodeWithContentDescription("Full screen").performClick(); reacquire()
        // Fullscreen chrome changes before Android finishes rotating the window.
        until {
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                compose.onNodeWithTag("player-surface").getUnclippedBoundsInRoot().let { it.right - it.left > it.bottom - it.top }
        }
        checkControls(true)
        chapters()
    }

    @Test fun fullscreenUsesChapterSheetAndBackClosesItBeforeLeavingFullscreen() {
        incoming("/watch?v=testvideo01&autoplay=0"); ready()
        compose.onNodeWithContentDescription("Full screen").performClick(); reacquire()
        chapters()
        compose.onNodeWithTag("chapter-30000").performClick()
        until { activity.model.playback.value.position == 30000L }
        compose.onNodeWithTag("chapters-panel").assertExists()
        pressBack()
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Exit full screen").assertExists()
        pressBack(); reacquire()
        compose.onNodeWithContentDescription("Full screen").assertExists()
    }

    @Test fun chapterDrawerSurvivesRecreationAndWatchFullscreenTransitions() {
        incoming("/watch?v=testvideo01&autoplay=0"); ready(); chapters()
        val requests = metadataRequests()
        compose.onNodeWithTag("chapter-30000").performClick()
        until { activity.model.playback.value.position == 30000L }
        ui { activity.recreate() }; reacquire()
        compose.onNodeWithTag("chapters-panel").assertExists()
        compose.onNodeWithTag("chapter-30000").assertIsSelected()
        compose.onNodeWithContentDescription("Full screen").performClick(); reacquire()
        compose.onNodeWithTag("chapters-panel").assertExists()
        // Sheet is modal; fullscreen Back is handled by the sheet, then the player.
        pressBack(); pressBack(); reacquire()
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
        assertEquals(requests, metadataRequests())
    }

    @Test fun commentsAndChaptersReplaceOneAnotherAndWatchBackOnlyClosesTheDrawer() {
        incoming("/watch?v=testvideo01&autoplay=0"); ready()
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("comments-entry"))
        compose.onNodeWithTag("comments-entry").performClick()
        compose.onNodeWithTag("comments-drawer").assertExists()
        chapters()
        compose.onNodeWithTag("comments-drawer").assertDoesNotExist()
        pressBack()
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
        compose.onNodeWithTag("watch-content").assertExists()
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("comments-entry"))
        compose.onNodeWithTag("comments-entry").performClick()
        compose.onNodeWithTag("comments-drawer").assertExists()
    }

    @Test fun repeatedVideoQueueOccurrencesAndMinimizationDismissChapters() {
        incoming("/watch?v=testvideo01&list=PLfixture&index=0&autoplay=0"); ready(); chapters()
        val first = activity.model.queue.value.currentKey
        val next = activity.model.queue.value.items[2].key
        ui { activity.model.selectQueue(next) }
        until { activity.model.queue.value.currentKey == next }
        ready()
        assertEquals("testvideo01", activity.model.playback.value.details!!.video.id)
        assertNotEquals(first, activity.model.queue.value.currentKey)
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
        chapters()
        compose.onNodeWithContentDescription("Minimize player").performClick()
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
    }

    @Test fun liveDescriptionsNeverExposeChapters() {
        command("sponsorblock", """{"liveNow":true}""")
        incoming("/watch?v=testvideo01&autoplay=0"); ready()
        assertTrue(activity.model.playback.value.details!!.video.live)
        captureWatchScreenshot("watch-glass-live")
        compose.onNodeWithTag("player-chapter-title").assertDoesNotExist()
        compose.onNodeWithTag("chapters-entry").assertDoesNotExist()
        assertEquals(0, fixture().getJSONArray("chapterAssetRequests").length())
    }

    @Test fun pipDismissesTheDrawerAndReturningDoesNotReopenIt() {
        incoming("/watch?v=testvideo01&autoplay=1"); ready(); chapters()
        Assume.assumeTrue(activity.supportsPip())
        ui { activity.enterPip() }
        until { activity.isInPictureInPictureMode }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        // PiP can detach the entire Compose hierarchy; verify the drawer after returning.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        automation.waitForIdle(500, 5_000)
        val bounds = android.graphics.Rect()
        until { automation.windows.firstOrNull { it.root?.packageName?.toString() == instrumentation.targetContext.packageName }
            ?.let { it.getBoundsInScreen(bounds); !bounds.isEmpty } == true }
        captureWatchScreenshot("watch-glass-pip")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "input tap ${bounds.centerX()} ${bounds.centerY()} ")).use { it.readBytes() }
        captureWatchScreenshot("watch-glass-pip-menu")
        until {
            fun expand(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                val label = "${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()}".lowercase()
                if (label.contains("full screen") || label.contains("fullscreen") || label.contains("expand") ||
                    node.viewIdResourceName?.endsWith("expand_button") == true)
                    return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                return (0 until node.childCount).any { index -> node.getChild(index)?.let(::expand) == true }
            }
            automation.windows.any { window -> window.root?.takeIf { it.packageName?.toString() == "com.android.systemui" }?.let(::expand) == true }
        }
        reacquire(); until { !activity.isInPictureInPictureMode }
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
        assertTrue(activity.model.playback.value.playWhenReady)
        info.flags = originalFlags; automation.serviceInfo = info
    }
}
