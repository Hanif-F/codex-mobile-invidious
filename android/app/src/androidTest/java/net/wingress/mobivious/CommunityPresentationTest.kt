package net.wingress.mobivious

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
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
class CommunityPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "UC" + "a".repeat(22)

    @Test fun postActionsSeparateLikesAndShareAboveAFullWidthCommentsButton() {
        var dark by mutableStateOf(false)
        var count by mutableStateOf<Long?>(1234)
        var opened = 0
        var shared = 0
        val post = CommunityPost(Comment("Creator", "Short post", "today", 7137, id = "Ugpost1"), owner)
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (dark) 2f else 1f)) {
                Surface(Modifier.width(320.dp)) {
                    Column(Modifier.padding(16.dp)) { PostActions(post.copy(commentCount = count), { opened++ }, { shared++ }) }
                }
            }
        } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark = isDark }
            val likes = compose.onNodeWithContentDescription(DisplayFormats.audience(7137, "like")).assert(hasClickAction().not()).fetchSemanticsNode().boundsInRoot
            val share = compose.onNodeWithContentDescription("Share post").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val row = compose.onNodeWithTag("post-actions-Ugpost1").fetchSemanticsNode().boundsInRoot
            val shareBounds = share.fetchSemanticsNode().boundsInRoot
            assertTrue(shareBounds.left >= likes.right)
            assertEquals(row.right, shareBounds.right, 1f)
            assertEquals(likes.center.y, shareBounds.center.y, 1f)
            for (value in listOf(1234L, 0L, null)) {
                compose.runOnIdle { count = value }
                val expected = value?.let { "Comments (${DisplayFormats.compact(it)})" } ?: "Comments"
                val button = compose.onNodeWithTag("post-comments-Ugpost1").assertTextEquals(expected).assertHeightIsAtLeast(48.dp)
                val bounds = button.fetchSemanticsNode().boundsInRoot
                assertEquals(row.left, bounds.left, 1f); assertEquals(row.width, bounds.width, 1f)
                assertTrue(bounds.top >= row.bottom)
            }
            compose.onNodeWithTag("post-comments-Ugpost1").performClick()
            share.performClick()
            compose.onNodeWithText("Copy link").assertDoesNotExist()
            compose.onNodeWithText("Share").assertDoesNotExist()
        }
        assertEquals(2, opened); assertEquals(2, shared)
    }

    @Test fun invalidPostIdentifiersDisableOnlyTheUnavailableActions() {
        var post by mutableStateOf(CommunityPost(Comment("Creator", "Body", "today", 0, id = "bad/id"), owner))
        compose.setContent { MaterialTheme { PostActions(post, {}, {}) } }
        compose.onNodeWithContentDescription("Share post").assertIsNotEnabled()
        compose.onNodeWithText("Comments").assertIsNotEnabled()
        compose.runOnIdle { post = post.copy(comment = post.comment.copy(id = "Ugpost1"), channelId = "") }
        compose.onNodeWithContentDescription("Share post").assertIsEnabled()
        compose.onNodeWithText("Comments").assertIsNotEnabled()
    }

    @Test fun postExpansionRestoresAndKeepsRichLinksInsideTheCard() {
        val restoration = StateRestorationTester(compose)
        val body = (1..12).joinToString("<br>") { "<b>Long post line $it</b>" } + "<br><a href=\"/post/Ugpost2\">Another post</a>"
        var opened = ""
        restoration.setContent { MaterialTheme {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                NativeRichText("Fallback", body, "Ugpost1", "https://instance.test", "", { opened = it }, 6, "post-body-Ugpost1",
                    expansionControl = { expanded, toggle -> PostExpansionButton("Ugpost1", expanded, toggle) })
            }
        } }
        fun layout(): TextLayoutResult {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("post-body-Ugpost1").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }
        assertEquals(6, layout().lineCount)
        compose.onNodeWithText("Read more").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(layout().lineCount > 6)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Show less").assertExists()
        val expanded = layout()
        assertTrue(expanded.layoutInput.text.spanStyles.isNotEmpty())
        val index = expanded.layoutInput.text.text.indexOf("Another post")
        compose.onNodeWithTag("post-body-Ugpost1").performTouchInput { click(expanded.getBoundingBox(index).center) }
        assertEquals("/post/Ugpost2", opened)
        compose.onNodeWithText("Show less").performScrollTo().performClick()
        assertEquals(6, layout().lineCount)
    }

    @Test fun shortAndStandalonePostBodiesHaveNoExpansionControl() {
        var standalone by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            Column(Modifier.width(320.dp)) {
                NativeRichText(if (standalone) (1..12).joinToString("\n") { "Line $it" } else "Short post", "", "Ugpost1",
                    "https://instance.test", "", {}, if (standalone) Int.MAX_VALUE else 6,
                    expansionControl = { expanded, toggle -> PostExpansionButton("Ugpost1", expanded, toggle) })
            }
        } }
        compose.onNodeWithTag("post-expand-Ugpost1").assertDoesNotExist()
        compose.runOnIdle { standalone = true }
        compose.onNodeWithTag("post-expand-Ugpost1").assertDoesNotExist()
    }

    @Test fun postShareChooserContainsThePostUrlAndChannelOwner() {
        val post = CommunityPost(Comment("Creator", "Body", "today", 0, id = "Ugpost1"), owner)
        val chooser = postShareIntent("https://instance.test", post)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share post", chooser.getStringExtra(Intent.EXTRA_TITLE))
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action); assertEquals("text/plain", send.type)
        assertEquals("https://instance.test/post/Ugpost1?ucid=$owner", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun channelIdentityAndRichDescriptionLinksWorkAtNarrowLargeFontSizes() {
        var opened = ""
        val channel = Channel(owner, "A long channel name", "Readable fallback", "42", verified = true, pronouns = "they/them",
            descriptionHtml = "<b>Formatted description</b><br><a href=\"/post/Ugpost1\">Open post</a>")
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                var description by remember { mutableStateOf(false) }
                Surface(Modifier.width(320.dp)) { ChannelHeader(channel, "https://instance.test", true, false, readDescription = { description = true }) {} }
                if (description) ChannelDescriptionSheet(channel, "https://instance.test", { opened = it }) { description = false }
            }
        } }
        compose.onNodeWithText("they/them").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verified channel").assertIsDisplayed()
        compose.onNodeWithTag("channel-header-avatar", true).assertDoesNotExist()
        compose.onNodeWithTag("channel-description-open").performClick()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("channel-description-text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(layout.layoutInput.text.spanStyles.isNotEmpty())
        val index = layout.layoutInput.text.text.indexOf("Open post")
        compose.onNodeWithTag("channel-description-text").performTouchInput { click(layout.getBoundingBox(index).center) }
        assertEquals("/post/Ugpost1", opened)
        compose.onNodeWithContentDescription("Close channel description").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun galleryRemainsBrowsableWhenImagesFailAndViewerHasAnAccessibleCloseButton() {
        var dark by mutableStateOf(false)
        var width by mutableStateOf(320.dp)
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Surface(Modifier.width(width).height(500.dp)) {
                PostGallery(listOf(PostImage("invalid", 1280, 720), PostImage("invalid2", 1280, 720)), "https://instance.test", "gallery")
            }
        } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark = isDark; width = if (isDark) 390.dp else 320.dp }
            compose.onNodeWithTag("gallery").performTouchInput { swipeLeft() }
            compose.onNodeWithText("2 / 2").assertIsDisplayed()
            compose.onNodeWithTag("gallery").performTouchInput { click(center) }
            compose.onNodeWithTag("post-image-viewer").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close image").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("post-image-viewer").assertDoesNotExist()
        }
    }

    @Test fun postCommentPanelKeepsCloseSortingAndReadOnlyMetadataAccessible() {
        var dark by mutableStateOf(false)
        var thin by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        var selected = CommentSort.TOP
        val state = CommentsState(CommentTarget.Post("Ugpost1", owner), ApiContext("https://instance.test", null), open = true,
            feed = CommentFeed(CommentPage(listOf(Comment("Post reader", "Post comment text", "today", 12, id = "post-comment")), count = 1), loaded = true))
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                CommentsPanel(state, thin, Modifier.width(320.dp).height(500.dp), {}, {}, { selected = it }, { _, _ -> }, {}, { _, _ -> }, {}, {})
            }
        } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark = isDark; thin = isDark; fontScale = if (isDark) 2f else 1f }
            compose.onNodeWithContentDescription("Close comments").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            compose.onNodeWithTag("comments-sort-new").assertIsDisplayed().performClick()
            assertEquals(CommentSort.NEWEST, selected)
            compose.onNodeWithText("Post comment text").assertIsDisplayed()
            compose.onNodeWithContentDescription("12 likes").assert(hasClickAction().not())
            if (thin) compose.onNodeWithTag("comment-avatar-post-comment", true).assertDoesNotExist()
        }
    }
}
