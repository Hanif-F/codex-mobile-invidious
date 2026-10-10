package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Uses the disposable API/media fixture through adb reverse; no YouTube account is required. */
@RunWith(AndroidJUnit4::class)
class CommentsSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private fun until(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun requests(): List<JSONObject> = fixture().getJSONArray("commentRequests").let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.store.save(null)
            activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, darkMode = "light"))
            activity.model.refreshSharedSettings(); activity.model.navigate("Popular")
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.details != null && activity.model.playback.value.canPlay }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread { activity.model.closePlayer(); activity.finishAndRemoveTask() }
    }
    private fun open(wait: Boolean = true) {
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("comments-entry"))
        compose.onNodeWithTag("comments-entry").performClick()
        if (wait) until { activity.model.comments.value.feed.loaded }
    }
    private fun more() {
        val list = if (activity.model.comments.value.threadKey == null) "comments-list" else "comment-replies-list"
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag("comments-load-more"))
        compose.onNodeWithTag("comments-load-more").performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /data/local/tmp/mobivious-comments-screenshots").let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        automation.executeShellCommand("screencap -p /data/local/tmp/mobivious-comments-screenshots/$name.png").let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
    }
    @Test fun entryHidesSpoilersAndFetchesOnlyWhenOpenedAndReusesLoadedComments() {
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("comments-entry"))
        compose.onNodeWithTag("comments-entry").assertExists()
        compose.onNodeWithText("Fixture creator").assertDoesNotExist()
        compose.onNodeWithTag("comments-drawer").assertDoesNotExist()
        assertTrue(requests().isEmpty())
        screenshot("entry-no-spoilers")
        open()
        compose.onNodeWithTag("player-surface").assertIsDisplayed()
        compose.onNodeWithTag("comments-drawer").assertIsDisplayed()
        compose.onNodeWithText("Fixture creator").assertExists()
        compose.onAllNodesWithContentDescription("3 likes", useUnmergedTree = true).assertAll(hasClickAction().not())
        compose.onNodeWithContentDescription("Verified author").assertExists()
        compose.onNodeWithText("Pinned").assertExists()
        compose.onNodeWithText("Creator", substring = false).assertExists()
        compose.onNodeWithText("Member").assertExists()
        compose.onNodeWithContentDescription("Hearted by Mobivious Studio").assertExists()
        screenshot("comments-light")
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, darkMode = "dark"))
            activity.model.refreshSharedSettings()
        }
        until { activity.model.preferences.value.darkMode == "dark" }
        screenshot("comments-dark")
        compose.onNodeWithContentDescription("Close comments").performClick()
        compose.onNodeWithText("Fixture creator").assertDoesNotExist()
        compose.onNodeWithTag("comments-entry").assertExists()
        open(); assertEquals(1, requests().size)
        assertTrue(requests().all { it.getString("source") == "youtube" && !it.getBoolean("authorized") })
    }
    @Test fun replyPagesAreSeparateAndBackRestoresTheMainScrollPosition() {
        open()
        compose.onNodeWithTag("comments-list").performScrollToIndex(3)
        until { activity.model.comments.value.feed.position.index > 0 }
        val before = activity.model.comments.value.feed.position
        compose.runOnUiThread { activity.model.openReplies(activity.model.comments.value.feed.page.items.first()) }
        until { activity.model.comments.value.thread?.feed?.loaded == true }
        compose.onNodeWithTag("comment-parent").assertExists()
        compose.onNodeWithText("First reply").assertExists()
        more(); until { activity.model.comments.value.thread?.feed?.page?.items?.size == 3 }
        assertEquals(listOf("reply1", "reply2", "reply3"), activity.model.comments.value.thread!!.feed.page.items.map { it.id })
        screenshot("replies")
        compose.onNodeWithContentDescription("Back to comments").performClick()
        until { activity.model.comments.value.threadKey == null }
        compose.waitForIdle()
        assertEquals(before, activity.model.comments.value.feed.position)
        compose.navigateBack()
        compose.onNodeWithTag("comments-drawer").assertDoesNotExist()
        compose.onNodeWithTag("watch-details-list").assertExists()
    }
    @Test fun paginationRetryKeepsRowsCursorAndTotalAndSortingResetsTheList() {
        open(); command("comments", """{"commentFailNext":true}"""); more()
        until { activity.model.comments.value.feed.error != null }
        assertEquals(14, activity.model.comments.value.feed.page.items.size)
        compose.onNodeWithTag("comments-list").performScrollToNode(hasTestTag("comments-retry"))
        compose.onNodeWithTag("comments-retry").performClick()
        until { activity.model.comments.value.feed.page.items.size == 15 }
        assertEquals(1234L, activity.model.comments.value.feed.page.count)
        assertEquals(15, activity.model.comments.value.feed.page.items.map { it.id }.distinct().size)
        compose.onNodeWithTag("comments-sort-new").performClick()
        until { activity.model.comments.value.feed.loaded && activity.model.comments.value.sort == CommentSort.NEWEST }
        compose.onNodeWithText("Newest viewer").assertIsDisplayed()
        compose.onNodeWithText("Fixture creator").assertDoesNotExist()
        assertEquals(0, activity.model.comments.value.feed.position.index)
        assertEquals(listOf("", "comments+/page=2%&", "comments+/page=2%&", ""), requests().map { it.getString("continuation") })
    }
    @Test fun initialRetryEmptyStateAndDisabledPreferenceAreExplicit() {
        command("comments", """{"commentFailNext":true}"""); open(false)
        until { activity.model.comments.value.feed.error != null }
        compose.onNodeWithText("Comments unavailable").assertExists()
        compose.onNodeWithTag("comments-retry").performClick()
        until { activity.model.comments.value.feed.loaded }
        command("comments", """{"commentEmpty":true}""")
        compose.onNodeWithTag("comments-sort-new").performClick()
        until { activity.model.comments.value.feed.loaded }
        compose.onNodeWithText("No comments to show.", substring = true).assertExists()
        assertEquals(0L, activity.model.comments.value.feed.page.count)
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(comments = listOf("", "")))
            activity.model.refreshSharedSettings()
        }
        until { !activity.model.preferences.value.showYoutubeComments }
        compose.onNodeWithTag("comments-drawer").assertDoesNotExist()
        compose.onNodeWithTag("comments-entry").assertDoesNotExist()
        assertNull(activity.model.comments.value.videoId)
    }
    @Test fun delayedSortAndReplyResponsesCannotReplaceAnotherVideo() {
        command("comments", """{"commentDelayNext":1500}"""); open(false)
        until { requests().any { !it.getBoolean("completed") } }
        compose.onNodeWithTag("comments-sort-new").performClick()
        until { activity.model.comments.value.feed.loaded }
        until { requests().all { it.getBoolean("completed") } }
        assertEquals("newest", activity.model.comments.value.feed.page.items.first().id)
        compose.onNodeWithTag("comments-sort-top").performClick()
        until { activity.model.comments.value.feed.loaded }
        command("comments", """{"commentDelayNext":1500}""")
        compose.onNodeWithTag("comment-replies-parent").performClick()
        until { requests().any { !it.getBoolean("completed") } }
        compose.runOnUiThread { activity.model.playVideo(Video("testvideo02", "Next video")) }
        until { activity.model.playback.value.details?.video?.id == "testvideo02" }
        until { requests().all { it.getBoolean("completed") } }
        assertFalse(activity.model.comments.value.open)
        assertTrue(activity.model.comments.value.threads.isEmpty())
        assertFalse(activity.model.comments.value.feed.loaded)
    }
    @Test fun richTimestampLinksSeekWithoutRestartingAndLongTextExpands() {
        open()
        fun clickTimestamp() {
            val body = compose.onNodeWithTag("comment-body-parent")
            val layouts = mutableListOf<TextLayoutResult>()
            body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val bounds = layout.getBoundingBox(layout.layoutInput.text.text.indexOf("0:30"))
            body.performTouchInput { click(Offset(bounds.center.x, bounds.center.y)) }
        }
        compose.runOnUiThread { activity.model.controller.value!!.pause(); activity.model.seekTo(5_000) }
        until { !activity.model.playback.value.playWhenReady }
        clickTimestamp(); until { activity.model.playback.value.position == 30_000L }
        assertFalse(activity.model.playback.value.playWhenReady)
        compose.runOnUiThread { activity.model.controller.value!!.play() }
        until { activity.model.playback.value.playWhenReady }
        clickTimestamp(); until { activity.model.playback.value.position in 30_000L..32_000L }
        assertTrue(activity.model.playback.value.playWhenReady)
        assertEquals("testvideo01", activity.model.playback.value.details?.video?.id)
        compose.onNodeWithTag("comments-list").performScrollToNode(hasText("Read more"))
        compose.onNodeWithText("Read more").performClick()
        compose.onNodeWithText("Show less").assertExists()
        screenshot("long-comment")
        compose.onNodeWithText("Show less").performClick()
    }
}
