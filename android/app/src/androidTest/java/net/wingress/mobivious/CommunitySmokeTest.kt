package net.wingress.mobivious

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import androidx.compose.ui.test.*
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

/** Uses only the disposable localhost API/media fixture. */
@RunWith(AndroidJUnit4::class)
class CommunitySmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val owner = "UC" + "a".repeat(22)
    private fun ui(action: () -> Unit) = compose.runOnUiThread(action)
    private fun until(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun fixture() = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
    private fun events(key: String) = fixture().getJSONArray(key).let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun back() { InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    @Before fun launch() {
        command("reset"); command("community")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false)); activity.model.refreshSharedSettings()
            activity.model.navigate("Home", "channel:$owner")
        }
        until { !activity.model.browse.value.loading && activity.model.channel.value != null }
    }
    @After fun close() {
        if (::activity.isInitialized) ui { activity.model.closePlayer(); activity.finishAndRemoveTask() }
    }
    private fun choose(tab: ChannelTab) {
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("channel-tabs"))
        compose.onNodeWithTag("channel-tab-${tab.path}").performScrollTo().performClick()
        until { !activity.model.browse.value.loading && activity.model.channelTab.value == tab }
    }
    private fun more() {
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Load more"))
        compose.onNodeWithText("Load more").performClick()
        until { !activity.model.browse.value.loading }
    }
    private fun post(id: String) {
        ui { activity.model.openPost(PostLink(id, owner)) }
        until { !activity.model.postDetail.value.loading && activity.model.postDetail.value.post?.id == id }
    }
    private fun comments() {
        compose.onNodeWithTag("post-detail").performScrollToNode(hasTestTag("post-comments-open"))
        compose.onNodeWithTag("post-comments-open").performClick()
        until { activity.model.postComments.value.feed.loaded }
    }

    @Test fun specializedTabsSortAndRestoreAfterSearchAndChildNavigation() {
        compose.onNodeWithText("they/them").assertExists()
        compose.onNodeWithText("Verified channel").assertExists()
        compose.onNodeWithTag("channel-banner").assertExists()
        choose(ChannelTab.SHORTS)
        compose.onNodeWithTag("channel-sort-popular").performScrollTo().performClick()
        until { !activity.model.browse.value.loading && activity.model.channelVideoSort.value == ChannelSort.POPULAR }
        more(); assertEquals(2, activity.model.browse.value.videos.size)
        choose(ChannelTab.STREAMS)
        assertEquals(ChannelSort.POPULAR, activity.model.channelVideoSort.value)
        for (tab in listOf(ChannelTab.PODCASTS, ChannelTab.RELEASES, ChannelTab.COURSES)) {
            choose(tab); assertEquals("PLlive", activity.model.browse.value.lists.single().id)
            more(); assertEquals(listOf("PLlive", "RDopaque"), activity.model.browse.value.lists.map { it.id })
        }
        val original = activity.model.browse.value
        ui { activity.model.editSearch("fixture", true); activity.model.submitSearch(true) }
        until { !activity.model.browse.value.loading && activity.model.scopedSearch.value.submitted == "fixture" }
        ui { activity.model.clearScopedSearch() }
        until { activity.model.scopedSearch.value.submitted.isBlank() }
        assertEquals(original.lists, activity.model.browse.value.lists)
        ui { activity.model.openGlobalSearch("fixture") }
        until { !activity.model.browse.value.loading }
        back(); until { activity.model.route == "channel:$owner" }
        assertEquals(ChannelTab.COURSES, activity.model.channelTab.value)
        assertEquals(original.lists, activity.model.browse.value.lists)
        ui { activity.model.openPlaylist(original.lists.first()) }
        until { !activity.model.browse.value.loading }
        back(); until { activity.model.route == "channel:$owner" }
        assertEquals(ChannelTab.COURSES, activity.model.channelTab.value)
        assertEquals(original.lists, activity.model.browse.value.lists)
        choose(ChannelTab.CHANNELS)
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("related-channel-UC${"c".repeat(22)}"))
        compose.onNodeWithTag("related-channel-UC${"c".repeat(22)}").performClick()
        until { !activity.model.browse.value.loading && activity.model.channel.value?.id != owner }
        back(); until { activity.model.channel.value?.id == owner }
        assertEquals(ChannelTab.CHANNELS, activity.model.channelTab.value)
        assertEquals(1, activity.model.browse.value.channels.size)
        assertTrue(events("channelRequests").filter { it.optString("tab") in listOf("shorts", "streams") && it.optString("sort") == "popular" }.isNotEmpty())
    }

    @Test fun postsPaginationRetainsRowsAndRetriesTheSameCursor() {
        choose(ChannelTab.POSTS); assertEquals(8, activity.model.browse.value.posts.size)
        command("channel", """{"channelFailNext":true}""")
        more(); assertNotNull(activity.model.browse.value.error)
        assertEquals(8, activity.model.browse.value.posts.size)
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasText("Retry"))
        compose.onNodeWithText("Retry").performClick()
        until { !activity.model.browse.value.loading && activity.model.browse.value.error == null }
        assertEquals(9, activity.model.browse.value.posts.size); assertTrue(activity.model.browse.value.end)
        val requests = events("channelRequests").filter { it.getString("tab") == "posts" }
        assertEquals(listOf(null, "posts+/page=2%&", "posts+/page=2%&"), requests.map { if (it.isNull("continuation")) null else it.getString("continuation") })
        compose.onNodeWithTag("browse-video-list").performScrollToNode(hasTestTag("post-open-Ugpost3"))
        val original = activity.model.browse.value.position
        compose.onNodeWithTag("post-open-Ugpost3").performClick()
        until { activity.model.postDetail.value.post?.id == "Ugpost3" }
        back(); until { activity.model.route == "channel:$owner" }
        assertEquals(ChannelTab.POSTS, activity.model.channelTab.value); assertEquals(9, activity.model.browse.value.posts.size)
        until { activity.model.browse.value.position == original }
    }

    @Test fun postMediaPollsQuizzesAndUnknownAttachmentsRemainReadable() {
        post("Ugpost3")
        compose.onNodeWithTag("post-media-Ugpost3").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithTag("post-media-Ugpost3").performTouchInput { click(center) }
        compose.onNodeWithTag("post-image-viewer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close image").performClick()
        post("Ugpost6")
        compose.onNodeWithTag("post-detail").performScrollToNode(hasText("42 votes"))
        compose.onNodeWithText("42 votes").assert(hasClickAction().not())
        compose.onNodeWithText("Second choice").assertExists()
        post("Ugpost7")
        compose.onNodeWithText("Correct answer").assertExists()
        post("Ugpost8")
        compose.onNodeWithText("Attachment unavailable").assertExists()
        compose.onNodeWithTag("post-body-Ugpost8").assertExists()
    }

    @Test fun postCommentsSheetSortsRepliesRetriesAndIgnoresVideoCommentVisibility() {
        ui { activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, comments = emptyList())); activity.model.refreshSharedSettings() }
        post("Ugpost1"); comments()
        compose.onNodeWithTag("post-comments-sheet").assertIsDisplayed()
        compose.onNodeWithTag("comments-list").performScrollToNode(hasTestTag("comment-replies-parent"))
        compose.onNodeWithTag("comment-replies-parent").performClick()
        until { activity.model.postComments.value.thread?.feed?.loaded == true }
        command("comments", """{"commentFailNext":true}""")
        compose.onNodeWithTag("comment-replies-list").performScrollToNode(hasTestTag("comments-load-more"))
        compose.onNodeWithTag("comments-load-more").performClick()
        until { activity.model.postComments.value.thread?.feed?.error != null }
        compose.onNodeWithTag("comments-retry").performClick()
        until { activity.model.postComments.value.thread?.feed?.page?.items?.size == 3 }
        back(); until { activity.model.postComments.value.threadKey == null }
        assertTrue(activity.model.postComments.value.open)
        compose.onNodeWithTag("comments-sort-new").performClick()
        until { activity.model.postComments.value.sort == CommentSort.NEWEST && activity.model.postComments.value.feed.loaded }
        assertEquals("newest", activity.model.postComments.value.feed.page.items.first().id)
        val count = events("commentRequests").size
        compose.onNodeWithContentDescription("Close comments").performClick()
        comments(); assertEquals(count, events("commentRequests").size)
        assertTrue(events("commentRequests").all { it.getBoolean("isPost") && !it.getBoolean("authorized") && it.getString("ucid") == owner })
    }

    @Test fun delayedPostsAndCommentsCannotOverwriteAnotherRouteOrInstance() {
        command("community", """{"postDelayNext":1200}""")
        ui { activity.model.openPost(PostLink("Ugpost1")) }
        until { events("postRequests").any { !it.getBoolean("completed") } }
        post("Ugpost2")
        until { events("postRequests").all { it.getBoolean("completed") } }
        assertEquals("Ugpost2", activity.model.postDetail.value.post!!.id)
        command("comments", """{"commentDelayNext":1200}""")
        ui { activity.model.openPostComments() }
        until { events("commentRequests").any { !it.getBoolean("completed") } }
        post("Ugpost1"); comments()
        until { events("commentRequests").all { it.getBoolean("completed") } }
        assertEquals(CommentTarget.Post("Ugpost1", owner), activity.model.postComments.value.target)
        ui { activity.model.switchServer("https://another.test") }
        assertFalse(activity.model.postComments.value.open); assertNull(activity.model.postDetail.value.post)
    }

    @Test fun sharedPostIntentRecreationAndPlaybackKeepTheirIndependentState() {
        ui { activity.model.play("testvideo01"); activity.sharedVideo.value = true }
        until { activity.model.playback.value.details != null && activity.model.playback.value.canPlay }
        ui { activity.model.openComments() }
        until { activity.model.comments.value.feed.loaded }
        val token = activity.model.queue.value.token
        ui { activity.startActivity(Intent(activity, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND; type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "https://youtube.com/post/Ugpost1")
        }) }
        until { activity.model.postDetail.value.post?.id == "Ugpost1" }
        compose.onNodeWithTag("mini-player-preview").performClick()
        compose.onNodeWithTag("watch-content").assertIsDisplayed()
        ui { activity.startActivity(Intent(activity, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND; type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "https://youtube.com/post/Ugpost1")
        }) }
        until { !activity.model.postDetail.value.loading }
        compose.onNodeWithTag("post-detail").assertIsDisplayed()
        comments()
        val model = activity.model
        val reads = events("postRequests").size
        val old = activity
        ui { old.recreate() }
        until { old.isDestroyed }
        ui { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        compose.onNodeWithTag("post-comments-sheet").assertIsDisplayed()
        assertEquals(reads, events("postRequests").size); assertSame(model, activity.model)
        assertEquals(token, activity.model.queue.value.token)
        assertEquals("testvideo01", activity.model.comments.value.videoId)
        assertTrue(activity.model.comments.value.feed.loaded)
    }

    @Test fun recreationAfterLeavingSharedPostDoesNotReplayItsLaunchIntent() {
        ui { activity.startActivity(Intent(activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW; data = android.net.Uri.parse("https://youtube.com/post/Ugpost1")
        }) }
        until { activity.model.postDetail.value.post?.id == "Ugpost1" }
        ui { activity.model.clearNavigationReturns(); activity.model.navigate("Home") }
        until { activity.model.route.isEmpty() && !activity.model.browse.value.loading }
        val reads = events("postRequests").size
        val model = activity.model
        val old = activity
        ui { old.recreate() }
        until { old.isDestroyed }
        ui { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single() }
        compose.onNodeWithTag("browse-video-list").assertExists()
        assertSame(model, activity.model)
        assertTrue(activity.model.route.isEmpty())
        assertNull(activity.model.postDetail.value.post)
        assertEquals(reads, events("postRequests").size)
    }
}
