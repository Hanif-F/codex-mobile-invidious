package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
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
class VideoInformationPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val details = VideoDetails(Video("abcdefghijk", "Video", "Studio"), "Plain", "", "", "", emptyList(), emptyList(),
        likes = 0, authorVerified = true, subscribers = "12.3K", upcoming = true, premiereTimestamp = 1800000000,
        listed = false, genre = "Music", license = "", familyFriendly = false, allowedRegions = listOf("ID", "US"),
        music = listOf(MusicCredit("Song", "Artist", "Album", "Music license")))

    @Test fun metadataAndCreditsFitNarrowDarkLargeFontLayouts() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).testTag("info-scroll")) {
                    VideoNotices(details)
                    WatchChannelIdentity(details.video, "", true, false, {}, {}, verified = true, subscribers = details.subscribers)
                    VideoInformation(details, "occurrence", "https://instance.test") {}
                }
            }
        } }
        compose.onNodeWithText("Unlisted").assertExists()
        compose.onNodeWithContentDescription("Verified channel").assertExists()
        compose.onNodeWithText("12.3K subscribers").assertExists()
        compose.onNodeWithTag("video-information-toggle").assertDoesNotExist()
        compose.onNodeWithText("Video details").assertDoesNotExist()
        compose.onNodeWithText("License: Standard YouTube license").assertExists()
        compose.onNodeWithText("Family friendly: No").assertExists()
        compose.onNodeWithText("Song").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Artist · Album").assertExists()
        compose.onNodeWithTag("video-regions-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("video-regions").assertTextContains("(ID)", substring = true)
    }

    @Test fun descriptionOwnsMetadataVisibilityAndGenreLinksStillWork() {
        val expanded = mutableStateOf(false)
        val clicked = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                VideoDescription(details.copy(genreUrl = "/hashtag/music"), "occurrence", "https://instance.test", expanded.value) { clicked += it }
            }
        } }
        compose.onNodeWithTag("watch-description-text").assertDoesNotExist()
        compose.onNodeWithTag("video-information").assertDoesNotExist()
        compose.runOnIdle { expanded.value = true }
        compose.onNodeWithTag("watch-description-text").assertTextContains("Plain")
        val description = compose.onNodeWithTag("watch-description-text").getUnclippedBoundsInRoot()
        val information = compose.onNodeWithTag("video-information").getUnclippedBoundsInRoot()
        assertTrue(information.top >= description.bottom)
        compose.onNodeWithText("Genre: Music").performClick()
        assertEquals(listOf("/hashtag/music"), clicked)
        compose.onNodeWithTag("video-regions-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("video-regions").assertExists()
        compose.runOnIdle { expanded.value = false }
        compose.onNodeWithTag("watch-description-text").assertDoesNotExist()
        compose.onNodeWithTag("video-information").assertDoesNotExist()
        compose.runOnIdle { expanded.value = true }
        compose.onNodeWithTag("video-license").assertExists()
    }

    @Test fun emptyDescriptionsStillShowMetadataAndMissingMetadataAddsNoSection() {
        val current = mutableStateOf(details.copy(description = ""))
        compose.setContent { MaterialTheme {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                VideoDescription(current.value, "occurrence", "https://instance.test", true) {}
            }
        } }
        compose.onNodeWithTag("video-license").assertExists()
        compose.onNodeWithTag("video-information-toggle").assertDoesNotExist()
        compose.runOnIdle { current.value = details.copy(genre = "", license = null, familyFriendly = null, allowedRegions = null, music = emptyList()) }
        compose.onNodeWithTag("watch-description-text").assertTextContains("Plain")
        compose.onNodeWithTag("video-information").assertDoesNotExist()
    }

    @Test fun richDescriptionPreservesFormattingAndNativeLinkTargets() {
        val clicked = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            Column(Modifier.width(390.dp)) {
                NativeRichText("Fallback", "<b>Bold</b> <i>italic</i><br><a href=\"/watch?v=abcdefghijk&t=12.345\">Timestamp</a> <a href=\"/@creator\">Creator</a> #music",
                    "description", "https://instance.test", "abcdefghijk", { clicked += it }, tag = "description")
            }
        } }
        compose.onNodeWithTag("description").assertTextContains("Bold italic", substring = true)
        val text = compose.onNodeWithTag("description").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(3, links.size)
        for (entry in links) {
            val annotation = entry.item as androidx.compose.ui.text.LinkAnnotation.Clickable
            annotation.linkInteractionListener?.onClick(annotation)
        }
        assertTrue(clicked.any { ContentLinks.resolve(it, "https://instance.test", "abcdefghijk") == ContentLink.Seek(12345) })
        assertTrue(clicked.any { ContentLinks.resolve(it, "https://instance.test") is ContentLink.Channel })
        assertTrue(clicked.any { ContentLinks.resolve(it, "https://instance.test") == ContentLink.Hashtag("music") })
    }

    @Test fun plainFallbackLinksTimestampsAndHashtagsWithoutExecutingHtml() {
        compose.setContent { MaterialTheme { Column {
            NativeRichText("Intro 0:00 and 1:02:03 #日本語 https://example.com", "", "plain", "https://instance.test", "abcdefghijk", {}, tag = "plain")
            NativeRichText("Fallback", "<script>alert('bad')</script><b>Safe</b><a href=\"javascript:alert(1)\">Unsafe target</a>", "safe", "https://instance.test", "abcdefghijk", {}, tag = "safe")
        } } }
        val plain = compose.onNodeWithTag("plain").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(4, plain.getLinkAnnotations(0, plain.length).size)
        val safe = compose.onNodeWithTag("safe").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(safe.getLinkAnnotations(0, safe.length).isEmpty()); assertFalse(safe.text.contains("alert"))
    }
}
