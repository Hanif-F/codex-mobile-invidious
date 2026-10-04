package net.wingress.mobivious

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.AvatarImageLoader
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL

/** Uses the localhost API/media fixture; all images are local and requests are logged. */
@RunWith(AndroidJUnit4::class)
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class AvatarsSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity
    private val server = "http://127.0.0.1:18080"
    private fun until(condition: () -> Boolean) = compose.waitUntil(20_000, condition)
    private fun state() = JSONObject(URL("$server/test/state").readText())
    private fun command(path: String, body: String = "{}") {
        (URL("$server/test/$path").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; outputStream.use { it.write(body.toByteArray()) }
            inputStream.close(); disconnect()
        }
    }
    @Before fun launch() {
        command("reset")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        AvatarImageLoader.get(instrumentation.targetContext).apply { memoryCache?.clear(); diskCache?.clear() }
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        compose.runOnUiThread {
            activity.model.store.save(null); activity.model.switchServer(server)
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false))
            activity.model.refreshSharedSettings(); activity.model.navigate("Home")
        }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        if (::activity.isInitialized) compose.runOnUiThread { activity.model.closePlayer(); activity.finishAndRemoveTask() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/mobivious-avatar-screenshots",
            "screencap -p /data/local/tmp/mobivious-avatar-screenshots/$name.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    @Test fun browsingChannelHeaderAndWatchReuseImagesWithoutExtraMetadataLookups() {
        compose.onNodeWithTag("video-avatar-testvideo01", true).assertExists()
        screenshot("home")
        compose.onNodeWithText("Mobivious Studio").performClick()
        until { activity.model.channel.value != null && !activity.model.browse.value.loading }
        compose.onNodeWithTag("channel-header-avatar", true).assertExists()
        compose.onNodeWithTag("video-avatar-testvideo01", true).assertDoesNotExist()
        val channels = state().getJSONArray("channelRequests")
        assertEquals(2, channels.length()) // Existing metadata + selected Videos page only.
        screenshot("channel")
        compose.onNodeWithContentDescription("Back").performClick()
        until { activity.model.route.isEmpty() && !activity.model.browse.value.loading }
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.details != null }
        compose.onNodeWithTag("watch-channel-avatar", true).assertExists()
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("video-avatar-testvideo02"))
        compose.onNodeWithTag("video-avatar-testvideo02", true).assertExists()
        val images = state().getJSONArray("avatarRequests")
        assertTrue(images.length() > 0)
        (0 until images.length()).forEach { i ->
            assertEquals("/ggpht/studio=s176", images.getJSONObject(i).getString("path"))
            assertFalse(images.getJSONObject(i).getBoolean("authorized")); assertFalse(images.getJSONObject(i).getBoolean("cookie"))
        }
        // Allow the first image to finish before checking subsequent appearances.
        runBlocking {
            val request = coil.request.ImageRequest.Builder(activity).data("$server/ggpht/studio=s176").size(176).build()
            assertTrue(AvatarImageLoader.get(activity).execute(request) is coil.request.SuccessResult)
        }
        val before = state().getJSONArray("avatarRequests").length()
        compose.runOnUiThread { activity.model.insertQueue(activity.model.playback.value.details!!.recommendations.first(), false) }
        until { activity.model.queue.value.hasExplicitQueue && activity.model.queueExpanded.value }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("playback-queue"))
        compose.onNodeWithTag("playback-queue-items").performScrollToNode(hasTestTag("queue-occurrence-${activity.model.queue.value.currentKey}"))
        compose.onAllNodesWithTag("video-avatar-testvideo01", true).assertCountEquals(1)
        compose.waitForIdle()
        assertEquals(before, state().getJSONArray("avatarRequests").length())
        screenshot("queue")
    }

    @Test fun commentsRepliesAndCreatorHeartsDisplayAvatars() {
        compose.onNodeWithText("A quiet moment · playback fixture").performClick()
        until { activity.model.playback.value.details != null }
        compose.onNodeWithTag("watch-details-list").performScrollToNode(hasTestTag("comments-entry"))
        compose.onNodeWithTag("comments-entry").performClick()
        until { activity.model.comments.value.feed.loaded }
        compose.onNodeWithTag("comment-avatar-parent", true).assertExists()
        compose.onNodeWithTag("comment-heart-avatar-parent", true).assertExists()
        screenshot("comments")
        compose.onNodeWithTag("comment-replies-parent").performClick()
        until { activity.model.comments.value.thread?.feed?.loaded == true }
        compose.onNodeWithTag("comment-avatar-reply1", true).assertExists()
        screenshot("replies")
    }

    @Test fun subscriptionsHistoryAndPlaylistCreatorsUseExistingPayloads() {
        compose.runOnUiThread { activity.model.store.save(Account("fixture-token", "Viewer", Long.MAX_VALUE, server)) }
        until { activity.model.subscriptionChannels.value.channels.isNotEmpty() }
        compose.runOnUiThread { activity.model.navigate("Subscriptions") }
        until { !activity.model.browse.value.loading }
        compose.onNodeWithTag("subscription-avatar-UC${"a".repeat(22)}", true).assertExists()
        screenshot("subscriptions")
        command("watched", """{"watched":["testvideo01"]}""")
        compose.runOnUiThread { activity.model.navigate("Library", "history") }
        until { !activity.model.browse.value.loading && activity.model.browse.value.videos.isNotEmpty() }
        compose.onNodeWithTag("video-avatar-testvideo01", true).assertExists()
        screenshot("history")
        command("playlist-rss")
        compose.runOnUiThread { activity.model.subscribePlaylist(Playlist("PLlive", "Fixture playlist", 2), true) }
        until { activity.model.playlists.value.any { it.id == "PLlive" && it.saved } }
        compose.runOnUiThread { activity.model.navigate("Library") }
        until { !activity.model.browse.value.loading }
        compose.onNodeWithTag("library-playlist-list").performScrollToNode(hasTestTag("playlist-card-PLlive"))
        compose.onNodeWithTag("playlist-avatar-PLlive", true).assertExists()
        screenshot("playlist-card")
        compose.onNodeWithTag("playlist-card-PLlive").performClick()
        until { !activity.model.browse.value.loading && activity.model.playlist.value?.author == "Source owner" }
        compose.onNodeWithTag("playlist-avatar-PLlive", true).assertExists()
        compose.onNodeWithText("Source owner").assertHasClickAction()
        screenshot("playlist")
    }

    @Test fun thinModeMakesNoAvatarRequestsAndFailuresKeepAuthorsUsable() {
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false, thinMode = true))
            activity.model.refreshSharedSettings()
        }
        until { activity.model.preferences.value.thinMode }
        command("avatars")
        compose.onNodeWithTag("video-avatar-testvideo01", true).assertDoesNotExist()
        compose.onNodeWithText("Mobivious Studio").performClick()
        until { activity.model.channel.value != null && !activity.model.browse.value.loading }
        compose.onNodeWithTag("channel-header-avatar", true).assertDoesNotExist()
        assertEquals(0, state().getJSONArray("avatarRequests").length())
        AvatarImageLoader.get(activity).apply { memoryCache?.clear(); diskCache?.clear() }
        command("avatars", """{"fail":true}""")
        compose.runOnUiThread {
            activity.model.store.guestDeArrow(AccountPreferences(autoplay = false))
            activity.model.refreshSharedSettings()
        }
        until { !activity.model.preferences.value.thinMode && state().getJSONArray("avatarRequests").length() > 0 }
        compose.onNodeWithTag("channel-header-avatar", true).assertExists()
        compose.onNodeWithText("Mobivious Studio").assertExists()
        screenshot("failed-avatar")
    }
}
