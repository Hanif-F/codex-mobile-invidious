package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.Video
import net.wingress.mobivious.data.VideoDetails
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the same control composables as VideoPlayer, without a decoder or network. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerControlsPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val title = "A long chapter title 日本語 ".repeat(12).trim()
    private val playback = mutableStateOf(PlaybackState(
        details = VideoDetails(Video("abcdefghijk", "Video", "Studio", duration = 120),
            "0:00 $title\n0:30 Second chapter", "", "", "", emptyList(), emptyList()),
        mediaId = "abcdefghijk", position = 5000, duration = 120000, canPlay = true, playerState = Player.STATE_READY))
    private val position = mutableLongStateOf(5000)
    private val fullscreen = mutableStateOf(false)
    private val width = mutableIntStateOf(320)
    private val height = mutableIntStateOf(180)
    private val fontScale = mutableFloatStateOf(1f)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private var chapterClicks = 0
    private var settingsClicks = 0
    private var playClicks = 0

    @Composable private fun Content() {
        MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.floatValue),
                LocalLayoutDirection provides direction.value) {
                Box(Modifier.width(width.intValue.dp).height(height.intValue.dp).testTag("controls-surface")) {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                        PlayerControlFooter(playback.value, position.longValue, fullscreen.value, true,
                            { settingsClicks++ }, { fullscreen.value = !fullscreen.value }, { chapterClicks++ })
                    }
                    PlayerPlaybackButton(playback.value, height.intValue < 180) {
                        playClicks++
                        playback.value = playback.value.copy(playWhenReady = !playback.value.playWhenReady)
                    }
                }
            }
        }
    }

    @Test fun footerStaysOneRowWithLongTitlesAndCenteredPlaybackAcrossLayouts() {
        compose.setContent { Content() }
        for ((wide, tall, scale, rtl, full) in listOf(
            Layout(320, 180, 1f, false, false), Layout(320, 160, 2f, false, false),
            Layout(390, 220, 1f, false, false), Layout(600, 340, 1f, false, true),
            Layout(320, 400, 2f, true, true))) {
            compose.runOnIdle {
                width.intValue = wide; height.intValue = tall; fontScale.floatValue = scale
                direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                fullscreen.value = full
            }
            val time = compose.onNodeWithTag("player-time").getUnclippedBoundsInRoot()
            val chapter = compose.onNodeWithTag("player-chapter-title").getUnclippedBoundsInRoot()
            val settings = compose.onNodeWithContentDescription("Player settings").getUnclippedBoundsInRoot()
            val expand = compose.onNodeWithContentDescription(if (full) "Exit full screen" else "Full screen").getUnclippedBoundsInRoot()
            if (rtl) {
                assertTrue(time.left >= chapter.right)
                assertTrue(chapter.left >= settings.right)
                assertTrue(settings.left >= expand.right)
            } else {
                assertTrue(time.right <= chapter.left)
                assertTrue(chapter.right <= settings.left)
                assertTrue(settings.right <= expand.left)
            }
            assertEquals(time.middleY, chapter.middleY, .5f)
            assertEquals(chapter.middleY, settings.middleY, .5f)
            assertTrue(chapter.right - chapter.left >= 48.dp)
            val surface = compose.onNodeWithTag("controls-surface").getUnclippedBoundsInRoot()
            val button = compose.onNodeWithTag("player-play-pause").getUnclippedBoundsInRoot()
            assertEquals(surface.middleX, button.middleX, .5f)
            assertEquals(surface.middleY, button.middleY, .5f)
            assertEquals(if (tall < 180) 48f else 64f, (button.right - button.left).value, .5f)
            val textLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("player-current-chapter", useUnmergedTree = true)
                .assertTextEquals(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            assertEquals(1, textLayouts.single().lineCount)
            assertTrue(textLayouts.single().isLineEllipsized(0))
        }
    }

    @Test fun chapterTracksScrubbingAndSupportsClickAndKeyboardWithoutDisplacingSettings() {
        compose.setContent { Content() }
        compose.onNodeWithTag("player-time").assertTextEquals("0:05 / 2:00")
        compose.onNodeWithTag("player-chapter-title").performClick()
        assertEquals(1, chapterClicks)
        compose.runOnIdle { position.longValue = 35000 }
        compose.onNodeWithTag("player-time").assertTextEquals("0:35 / 2:00")
        compose.onNodeWithTag("player-current-chapter", useUnmergedTree = true).assertTextEquals("Second chapter")
        compose.onNodeWithTag("player-chapter-title").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        assertEquals(2, chapterClicks)
        compose.onNodeWithContentDescription("Player settings").performClick()
        assertEquals(1, settingsClicks)
        compose.onNodeWithContentDescription("Full screen").performClick()
        compose.onNodeWithContentDescription("Exit full screen").assertExists()
    }

    @Test fun playbackKeepsPlayPauseReplayDisabledAndLoadingStates() {
        compose.setContent { Content() }
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(2, playClicks)
        compose.runOnIdle { playback.value = playback.value.copy(playerState = Player.STATE_ENDED) }
        compose.onNodeWithContentDescription("Replay").assertIsEnabled()
        compose.runOnIdle { playback.value = playback.value.copy(canPlay = false) }
        compose.onNodeWithContentDescription("Replay").assertIsNotEnabled()
        compose.runOnIdle { playback.value = playback.value.copy(loading = true) }
        compose.onNodeWithTag("player-play-pause").assertDoesNotExist()
        compose.runOnIdle { playback.value = playback.value.copy(loading = false, error = "Playback failed") }
        compose.onNodeWithTag("player-play-pause").assertDoesNotExist()
    }

    @Test fun videosWithoutChaptersKeepTimeAndIconsAndLiveDurationLabel() {
        compose.setContent { Content() }
        compose.runOnIdle { playback.value = playback.value.copy(details = playback.value.details!!.copy(description = "No chapters")) }
        compose.onNodeWithTag("player-chapter-title").assertDoesNotExist()
        compose.onNodeWithTag("player-time").assertTextEquals("0:05 / 2:00")
        compose.onNodeWithContentDescription("Player settings").assertIsDisplayed()
        compose.runOnIdle { playback.value = playback.value.copy(live = true) }
        compose.onNodeWithTag("player-time").assertTextEquals("0:05 / LIVE")
        compose.runOnIdle { playback.value = playback.value.copy(live = false, duration = 0) }
        compose.onNodeWithTag("player-time").assertTextEquals("0:05 / —")
    }

    private data class Layout(val width: Int, val height: Int, val fontScale: Float, val rtl: Boolean, val fullscreen: Boolean)
    private val DpRect.middleX get() = (left.value + right.value) / 2
    private val DpRect.middleY get() = (top.value + bottom.value) / 2
}
