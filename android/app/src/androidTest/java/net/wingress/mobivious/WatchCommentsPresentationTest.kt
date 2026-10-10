package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchCommentsPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun floatingGlassLeavesFirstCommentReadableAndKeepsSortAndRepliesWorking() {
        val dark = mutableStateOf(false)
        val opaque = mutableStateOf(false)
        val scale = mutableFloatStateOf(1f)
        val sort = mutableStateOf(CommentSort.TOP)
        var replies = 0
        val items = (0..19).map { Comment("Studio $it", "A readable comment.", "Today", 12,
            id = "$it", replyCount = 2, replyContinuation = "replies:$it") }
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) Liquid.dark else Liquid.light, typography = Liquid.typography) {
                CompositionLocalProvider(LocalReduceTransparency provides opaque.value,
                    LocalDensity provides Density(LocalDensity.current.density, scale.floatValue)) {
                    CommentsPanel(CommentsState(target = CommentTarget.Video("abcdefghijk"), open = true,
                        sort = sort.value, feed = CommentFeed(CommentPage(items, count = 20), loaded = true)),
                        false, Modifier.width(320.dp).height(700.dp), {}, {}, { sort.value = it }, { _, _ -> },
                        { replies++ }, { _, _ -> }, {}, {}, watchStyle = true)
                }
            }
        }
        val toolbar = compose.onNodeWithTag("comments-toolbar").getUnclippedBoundsInRoot()
        val body = compose.onNodeWithTag("comment-body-0").getUnclippedBoundsInRoot()
        val avatar = compose.onNodeWithTag("comment-avatar-0", true).getUnclippedBoundsInRoot()
        assertTrue(body.top >= toolbar.bottom)
        assertTrue(avatar.right <= body.left)
        compose.onNodeWithTag("comments-sort-top").assertIsSelected()
        compose.onNodeWithTag("comment-replies-0").performClick()
        assertEquals(1, replies)
        captureWatchScreenshot("watch-comments-floating-light")
        compose.onNodeWithTag("comments-list").performScrollToIndex(8)
        assertEquals(toolbar, compose.onNodeWithTag("comments-toolbar").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("comments-sort-new").performClick().assertIsSelected()
        compose.onNodeWithTag("comment-body-0").assertIsDisplayed()
        compose.runOnIdle { dark.value = true; opaque.value = true; scale.floatValue = 2f }
        compose.onNodeWithContentDescription("Close comments").assertIsDisplayed()
        compose.onNodeWithTag("comments-sort-new").assertIsDisplayed().assertIsSelected()
        val largeToolbar = compose.onNodeWithTag("comments-toolbar").getUnclippedBoundsInRoot()
        assertTrue(compose.onNodeWithTag("comment-body-0").getUnclippedBoundsInRoot().top >= largeToolbar.bottom)
        captureWatchScreenshot("watch-comments-floating-dark-opaque-large-text")
    }
}
