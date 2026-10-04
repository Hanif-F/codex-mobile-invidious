package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.onNodeWithText("Verified channel").assertIsDisplayed()
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
