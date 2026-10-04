package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.Comment
import net.wingress.mobivious.data.CreatorHeart
import net.wingress.mobivious.ui.CommentRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native rich-text/layout checks, independent of the playback fixture. */
@RunWith(AndroidJUnit4::class)
class CommentsPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val comment = Comment("A long creator name that wraps", "Plain fallback", "today", 1234, id = "rich",
        html = "<b>Bold</b> <i>italic</i> <s>strike</s><br><a href=\"https://example.com/path\">Source link</a>",
        creator = true, verified = true, member = true, pinned = true, edited = true, heart = CreatorHeart("Studio", ""))
    private fun content(dark: Boolean, fontScale: Float, width: Int) {
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.width(width.dp)) {
                        CommentRow(comment, "https://instance.test", "abcdefghijk", {}, {})
                    }
                }
            }
        }
    }
    @Test fun lightThemeRichContentAndMetadataAreDistinct() {
        content(false, 1f, 360)
        compose.onNodeWithText("Bold italic strike\nSource link", substring = true).assertExists()
        compose.onNodeWithText("today · Edited").assertExists()
        compose.onNodeWithText("Pinned").assertExists()
        compose.onNodeWithText("Creator").assertExists()
        compose.onNodeWithText("Member").assertExists()
        compose.onNodeWithContentDescription("1234 likes").assert(hasClickAction().not())
    }
    @Test fun darkThemeLargeFontsAndNarrowLayoutKeepMetadataReadable() {
        content(true, 2f, 320)
        compose.onNodeWithText("A long creator name that wraps").assertIsDisplayed()
        compose.onNodeWithText("Creator").assertIsDisplayed()
        compose.onNodeWithText("Member").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verified author").assertIsDisplayed()
    }
    @Test fun wideLayoutRetainsRichTextAndReadOnlyLikeSemantics() {
        content(false, 1.3f, 640)
        compose.onNodeWithTag("comment-body-rich").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hearted by Studio").assertIsDisplayed()
        compose.onNodeWithContentDescription("1234 likes").assert(hasClickAction().not())
    }
}
