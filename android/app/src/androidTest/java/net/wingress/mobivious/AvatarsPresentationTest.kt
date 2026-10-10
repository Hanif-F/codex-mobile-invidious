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
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Layout/semantics checks independent of playback, accounts and upstream images. */
@RunWith(AndroidJUnit4::class)
class AvatarsPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val channel = Channel("UC" + "a".repeat(22), "A long creator name that wraps across a narrow screen", subscribers = "42")
    private val server = "https://instance.test"

    @Test fun darkLargeFontHeaderAndAuthorKeepOneNavigationAction() {
        var opened = 0
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Surface(Modifier.width(320.dp)) {
                        Column {
                            ChannelHeader(channel, server, false, false) {}
                            ChannelAuthor(server, "", channel.name, true, size = 32.dp, tag = "author", onClick = { opened++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("channel-header-avatar", useUnmergedTree = true).assertWidthIsEqualTo(72.dp)
        compose.onNodeWithTag("author", useUnmergedTree = true).assertWidthIsEqualTo(32.dp)
        compose.onAllNodesWithText(channel.name).filter(hasClickAction()).assertCountEquals(1)
        compose.onAllNodesWithText(channel.name).filter(hasClickAction())[0].performClick()
        assertEquals(1, opened)
    }

    @Test fun lightThinModeSuppressesHeaderSubscriptionCommentAndHeartImages() {
        val comment = Comment("Viewer", "Body", "today", 3, id = "thin", authorId = channel.id,
            avatar = "https://yt3.ggpht.com/author=s48", heart = CreatorHeart("Studio", "https://yt3.ggpht.com/heart=s48"))
        compose.setContent { MaterialTheme(colorScheme = lightColorScheme()) {
            Column {
                ChannelHeader(channel, server, true, false) {}
                SubscriptionChannelChip(channel, server, true) {}
                CommentRow(comment, server, "abcdefghijk", {}, {}, thinMode = true)
            }
        } }
        compose.onNodeWithTag("channel-header-avatar", true).assertDoesNotExist()
        compose.onNodeWithTag("subscription-avatar-${channel.id}", true).assertDoesNotExist()
        compose.onNodeWithTag("comment-avatar-thin", true).assertDoesNotExist()
        compose.onNodeWithTag("comment-heart-avatar-thin", true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Hearted by Studio").assertExists()
    }

    @Test fun subscriptionCommentsAndCompactAuthorUseTheirIntendedSizes() {
        compose.setContent { MaterialTheme {
            Column(Modifier.width(640.dp)) {
                SubscriptionChannelChip(channel, server, false) {}
                ChannelAuthor(server, "", "Compact creator", true, tag = "compact")
                CommentRow(Comment("Viewer", "Body", "today", 0, id = "comment"), server, "abcdefghijk", {}, {})
            }
        } }
        compose.onNodeWithTag("subscription-avatar-${channel.id}", true).assertWidthIsEqualTo(24.dp)
        compose.onNodeWithTag("compact", true).assertWidthIsEqualTo(24.dp)
        compose.onNodeWithTag("comment-avatar-comment", true).assertWidthIsEqualTo(40.dp)
        compose.onNodeWithText("Viewer").assert(hasClickAction().not())
    }

    @Test fun narrowWatchIdentityAtLargeFontKeepsSubscribeBelowTheAuthor() {
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Surface(Modifier.width(320.dp)) {
                    WatchChannelIdentity(Video("abcdefghijk", "Title", channel.name, channel.id), server, false, true, {}, {})
                }
            }
        } }
        compose.onNodeWithTag("watch-channel-avatar", true).assertWidthIsEqualTo(40.dp)
        compose.onNodeWithText(channel.name).assertIsDisplayed()
        compose.onNodeWithText("Subscribed").assertIsDisplayed()
        val name = compose.onNodeWithText(channel.name).fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Subscribed").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(button.top >= name.bottom)
    }
}
