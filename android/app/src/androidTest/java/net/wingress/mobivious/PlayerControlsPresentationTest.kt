package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
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
        mediaId = "abcdefghijk", position = 5000, duration = 120000, seekable = true, canPlay = true, playerState = Player.STATE_READY))
    private val position = mutableLongStateOf(5000)
    private val fullscreen = mutableStateOf(false)
    private val width = mutableIntStateOf(320)
    private val height = mutableIntStateOf(180)
    private val fontScale = mutableFloatStateOf(1f)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private var chapterClicks = 0
    private var settingsClicks = 0
    private var playClicks = 0
    private var retries = 0
    private lateinit var inputModeManager: InputModeManager

    @Composable private fun Content() {
        inputModeManager = LocalInputModeManager.current
        MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.floatValue),
                LocalLayoutDirection provides direction.value) {
                Box(Modifier.width(width.intValue.dp).height(height.intValue.dp).testTag("controls-surface").background(Color(0xFF535862))) {
                    PlayerControlsLayout(playback.value, position.longValue, fullscreen.value, true,
                        onPlay = {
                            playClicks++
                            playback.value = playback.value.copy(playWhenReady = !playback.value.playWhenReady)
                        }, onSettings = { settingsClicks++ },
                        onFullscreen = { fullscreen.value = !fullscreen.value }, onChapters = { chapterClicks++ },
                        onCollapse = {}, onChat = {}, onRetry = { retries++ }, timeline = { modifier, trackOffset ->
                            Box(modifier.testTag("test-timeline")) {
                                Box(Modifier.align(Alignment.Center).fillMaxWidth().offset(y = trackOffset).height(3.dp).testTag("test-seek-track"))
                            }
                        })
                }
            }
        }
    }

    @Test fun measuredChromeKeepsTransportCenteredOrInHeaderAboveFullWidthSeeking() {
        compose.setContent { Content() }
        for ((wide, tall, scale, rtl, full) in listOf(
            Layout(320, 220, 1f, false, false), Layout(320, 160, 2f, false, false),
            Layout(390, 220, 1f, false, false), Layout(600, 340, 1f, false, true),
            Layout(320, 400, 2f, true, true), Layout(320, 96, 1f, false, false))) {
            compose.runOnIdle {
                width.intValue = wide; height.intValue = tall; fontScale.floatValue = scale
                direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                fullscreen.value = full
            }
            compose.waitForIdle()
            val surface = compose.onNodeWithTag("controls-surface").getUnclippedBoundsInRoot()
            val button = compose.onNodeWithTag("player-play-pause").getUnclippedBoundsInRoot()
            val rail = compose.onNodeWithTag("player-footer").getUnclippedBoundsInRoot()
            val settings = compose.onNodeWithContentDescription("Player settings").getUnclippedBoundsInRoot()
            val expand = compose.onNodeWithContentDescription(if (full) "Exit full screen" else "Full screen").getUnclippedBoundsInRoot()
            if (tall >= 144) assertTrue(settings.bottom <= rail.top)
            else assertTrue(settings.top >= rail.top && settings.bottom <= rail.bottom)
            assertTrue(expand.top >= rail.top && expand.bottom <= rail.bottom)
            assertTrue(button.top >= surface.top && button.bottom <= surface.bottom)
            val centered = compose.onAllNodesWithTag("player-transport").fetchSemanticsNodes().isNotEmpty()
            if (centered) {
                assertEquals(surface.middleX, button.middleX, .5f)
                assertEquals(surface.middleY, button.middleY, .5f)
                assertTrue(button.right - button.left >= 48.dp)
                val header = compose.onNodeWithTag("player-header").getUnclippedBoundsInRoot()
                assertTrue(header.bottom < button.top)
                assertTrue(button.bottom < rail.top)

            } else {
                assertEquals(48f, (button.right - button.left).value, .5f)
                val header = compose.onNodeWithTag("player-header").getUnclippedBoundsInRoot()
                assertTrue(button.top >= header.top && button.bottom <= header.bottom)
                compose.onNodeWithTag("player-back-10").assertDoesNotExist()
                compose.onNodeWithTag("player-forward-10").assertDoesNotExist()
            }
            run {
                val chapter = compose.onNodeWithTag("player-chapter-title").getUnclippedBoundsInRoot()
                assertTrue(chapter.right - chapter.left >= 48.dp)
                assertTrue(chapter.top >= rail.top && chapter.bottom <= rail.bottom)
                val track = compose.onNodeWithTag("test-timeline").getUnclippedBoundsInRoot()
                assertTrue(track.right - track.left >= 64.dp)
                assertTrue(chapter.bottom <= track.top)
                assertTrue(expand.bottom <= track.top)
                assertEquals(rail.left.value, track.left.value, .5f)
                assertEquals(rail.right.value, track.right.value, .5f)
                val line = compose.onNodeWithTag("test-seek-track").getUnclippedBoundsInRoot()
                val time = compose.onNodeWithTag("player-time").getUnclippedBoundsInRoot()
                val gap = line.middleY - time.middleY
                if (!full && tall >= 144) assertTrue("Portrait spacing: $gap", gap in 23.5f..32.5f)
                else assertEquals(48f, gap, .5f)
                compose.onNodeWithTag("player-chapter-title").assertContentDescriptionEquals("Current chapter: $title")
                if (compose.onAllNodesWithTag("player-current-chapter", true).fetchSemanticsNodes().isNotEmpty()) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose.onNodeWithTag("player-current-chapter", true).assertTextEquals(title)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(1, layouts.single().lineCount)
                    assertTrue(layouts.single().isLineEllipsized(0))
                }
            }
            compose.onNodeWithTag("player-time").assertIsDisplayed()
            if (rtl) captureWatchScreenshot("watch-controls-rtl-large-text")
        }
    }

    @Test fun noSeekButtonsAndChatStaysToTheLeftOfSettings() {
        playback.value = playback.value.copy(details = playback.value.details!!.copy(liveChatReplay = true))
        compose.setContent { Content() }
        compose.onNodeWithContentDescription("Back 10 seconds").assertDoesNotExist()
        compose.onNodeWithContentDescription("Forward 10 seconds").assertDoesNotExist()
        for (tall in listOf(180, 96)) {
            compose.runOnIdle { height.intValue = tall }
            val chat = compose.onNodeWithTag("player-chat").assertIsDisplayed().getUnclippedBoundsInRoot()
            val settings = compose.onNodeWithContentDescription("Player settings").getUnclippedBoundsInRoot()
            assertTrue(chat.right <= settings.left)
            assertEquals(chat.middleY, settings.middleY, .5f)
            compose.onNodeWithTag("player-chapter-title").assertIsDisplayed()
            compose.onNodeWithContentDescription("Full screen").assertIsDisplayed()
        }
        captureWatchScreenshot("watch-controls-chat-shallow")
        compose.runOnIdle { playback.value = playback.value.copy(live = true) }
        compose.onNodeWithTag("player-chat").assertDoesNotExist()
    }

    @Test fun multiHourTimestampCompactsAtLargeFontsWithoutLosingChapterOrSeeking() {
        width.intValue = 240
        fontScale.floatValue = 2f
        playback.value = playback.value.copy(duration = 14_672_000)
        compose.setContent { Content() }
        compose.onNodeWithTag("player-time").assertTextEquals("0:05").assertContentDescriptionEquals("0:05 / 4:04:32")
        compose.onNodeWithTag("player-chapter-title").assertIsDisplayed().performClick()
        assertEquals(1, chapterClicks)
        compose.onNodeWithTag("test-timeline").assertWidthIsAtLeast(64.dp)
        compose.onNodeWithContentDescription("Full screen").assertIsDisplayed()
        captureWatchScreenshot("watch-controls-multi-hour-large-text")
    }

    @Test fun loadingAndRetryRemainInsideTheRailInShallowVideo() {
        height.intValue = 96
        playback.value = playback.value.copy(loading = true)
        compose.setContent { Content() }
        compose.onNodeWithContentDescription("Loading video").assertIsDisplayed()
        compose.onNodeWithTag("player-play-pause").assertDoesNotExist()
        compose.onNodeWithTag("test-timeline").assertDoesNotExist()
        captureWatchScreenshot("watch-controls-loading-shallow")
        compose.runOnIdle { playback.value = playback.value.copy(loading = false, error = "Playback could not start") }
        val rail = compose.onNodeWithTag("player-footer").getUnclippedBoundsInRoot()
        val retry = compose.onNodeWithText("Retry").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(retry.top >= rail.top && retry.bottom <= rail.bottom)
        captureWatchScreenshot("watch-controls-error-shallow")
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test fun chapterTracksScrubbingAndSupportsClickAndKeyboardWithoutDisplacingSettings() {
        compose.setContent { Content() }
        compose.onNodeWithTag("player-time").assertTextEquals("0:05 / 2:00")
        compose.onNodeWithTag("player-chapter-title").performClick()
        assertEquals(1, chapterClicks)
        compose.runOnIdle { position.longValue = 35000 }
        compose.onNodeWithTag("player-time").assertTextEquals("0:35 / 2:00")
        compose.onNodeWithTag("player-current-chapter", useUnmergedTree = true).assertTextEquals("Second chapter")
        compose.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        compose.onNodeWithTag("player-chapter-title").performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
            .assertIsFocused()
            .performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.waitForIdle()
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
