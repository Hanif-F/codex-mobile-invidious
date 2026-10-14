package net.wingress.mobivious

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
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
    private val portrait = mutableStateOf(true)
    private val controlsVisible = mutableStateOf(true)
    private val backdrop = mutableStateOf(Color(0xFF535862))
    private var chapterClicks = 0
    private var settingsClicks = 0
    private var playClicks = 0
    private var retries = 0
    private lateinit var inputModeManager: InputModeManager

    @Composable private fun Content() {
        inputModeManager = LocalInputModeManager.current
        MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.floatValue),
                LocalLayoutDirection provides direction.value,
                LocalConfiguration provides Configuration(LocalConfiguration.current).apply {
                    orientation = if (portrait.value) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                }) {
                Box(Modifier.width(width.intValue.dp).height(height.intValue.dp).testTag("controls-surface").background(backdrop.value)) {
                    PlayerControlsDim(controlsVisible.value, Modifier.matchParentSize())
                    PlayerControlsLayout(playback.value, position.longValue, fullscreen.value, true,
                        onPlay = {
                            playClicks++
                            playback.value = playback.value.copy(playWhenReady = !playback.value.playWhenReady)
                        }, onSettings = { settingsClicks++ },
                        onFullscreen = { fullscreen.value = !fullscreen.value }, onChapters = { chapterClicks++ },
                        onCollapse = {}, onChat = {}, onRetry = { retries++ }, showControls = controlsVisible.value, timeline = { modifier, trackOffset ->
                            Box(modifier.testTag("test-timeline")) {
                                Box(Modifier.align(Alignment.Center).fillMaxWidth().offset(y = trackOffset).height(3.dp).testTag("test-seek-track"))
                            }
                        })
                }
            }
        }
    }

    @Test fun portraitTransportStaysCenteredAcrossPhoneSizesLargeTextRtlAndTinyPictures() {
        compose.setContent { Content() }
        for ((wide, tall, scale, rtl, full) in listOf(
            Layout(320, 180, 1f, false, false), Layout(360, 203, 1f, false, false),
            Layout(390, 220, 1f, false, false), Layout(240, 160, 2f, true, false),
            Layout(320, 96, 1f, false, false), Layout(320, 400, 2f, true, true),
            Layout(600, 340, 1f, false, true))) {
            compose.runOnIdle {
                width.intValue = wide; height.intValue = tall; fontScale.floatValue = scale
                direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                fullscreen.value = full
            }
            val surface = compose.onNodeWithTag("controls-surface").getUnclippedBoundsInRoot()
            val button = compose.onNodeWithTag("player-play-pause").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
            compose.onAllNodesWithTag("player-play-pause").assertCountEquals(1)
            assertEquals(surface.middleX, button.middleX, .5f)
            assertEquals(surface.middleY, button.middleY, .5f)
            compose.onNodeWithTag("player-transport").assertExists()
            val tiny = compose.onAllNodesWithTag("player-more-controls").fetchSemanticsNodes().isNotEmpty()
            if (tiny) {
                val more = compose.onNodeWithTag("player-more-controls").getUnclippedBoundsInRoot()
                assertSeparate(button, more)
                compose.onNodeWithTag("player-more-controls").performClick()
                compose.onNodeWithTag("player-controls-popup").assertIsDisplayed()
                compose.onNodeWithTag("test-timeline").assertIsDisplayed().assertWidthIsAtLeast(64.dp)
                compose.onNodeWithText("Chapters").assertIsDisplayed().assertContentDescriptionEquals("Current chapter: $title")
                compose.onNodeWithContentDescription("Full screen").assertIsDisplayed()
                compose.onNodeWithContentDescription("Player settings").assertIsDisplayed()
                compose.onNodeWithContentDescription("Close player controls").performClick()
            } else {
                for (control in listOf(compose.onNodeWithContentDescription("Player settings"),
                    compose.onNodeWithContentDescription(if (full) "Exit full screen" else "Full screen"),
                    compose.onNodeWithTag("player-chapter-title"), compose.onNodeWithTag("test-timeline"))) {
                    control.assertIsDisplayed()
                    assertSeparate(button, control.getUnclippedBoundsInRoot())
                }
                compose.onNodeWithTag("player-chapter-title").assertContentDescriptionEquals("Current chapter: $title")
                compose.onNodeWithTag("test-timeline").assertWidthIsAtLeast(64.dp)
                compose.onNodeWithTag("player-time").assertIsDisplayed()
            }
            captureWatchScreenshot("portrait-centered-$wide-$tall-${if (rtl) "rtl" else "ltr"}")
        }
    }

    private fun assertSeparate(a: DpRect, b: DpRect) {
        assertTrue("Controls overlap: $a and $b", a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
    }

    @Test fun tinyPopupDispatchesChapterSettingsChatAndFullscreenActions() {
        height.intValue = 96
        playback.value = playback.value.copy(details = playback.value.details!!.copy(liveChatReplay = true))
        var chatClicks = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.size(320.dp, 96.dp)) {
                PlayerControlsLayout(playback.value, 5_000, false, true, onPlay = {},
                    onSettings = { settingsClicks++ }, onFullscreen = { fullscreen.value = true },
                    onChapters = { chapterClicks++ }, onChat = { chatClicks++ }, onCollapse = {},
                    timeline = { modifier, _ -> Box(modifier.testTag("popup-timeline")) })
            }
        } }
        for (action in listOf("Chapters", "Player settings", "Show chat replay", "Full screen")) {
            compose.onNodeWithContentDescription("More player controls").performClick()
            compose.onNodeWithTag("popup-timeline").assertIsDisplayed()
            if (action == "Chapters") compose.onNodeWithText(action).performClick()
            else compose.onNodeWithContentDescription(action).performClick()
            compose.onNodeWithTag("player-controls-popup").assertDoesNotExist()
        }
        assertEquals(1, chapterClicks); assertEquals(1, settingsClicks); assertEquals(1, chatClicks)
        assertTrue(fullscreen.value)
    }

    @Test fun landscapeRetainsItsCompactHeaderTransportFallback() {
        portrait.value = false; height.intValue = 96
        compose.setContent { Content() }
        compose.onNodeWithTag("player-transport").assertDoesNotExist()
        compose.onNodeWithTag("player-play-pause").assertIsDisplayed()
        compose.onNodeWithTag("test-timeline").assertIsDisplayed()
    }

    @Test fun noSeekButtonsAndChatStaysToTheLeftOfSettings() {
        playback.value = playback.value.copy(details = playback.value.details!!.copy(liveChatReplay = true))
        compose.setContent { Content() }
        compose.onNodeWithContentDescription("Back 10 seconds").assertDoesNotExist()
        compose.onNodeWithContentDescription("Forward 10 seconds").assertDoesNotExist()
        for (tall in listOf(180, 220)) {
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

    @Test fun oneTwentyFivePercentDimCoversThePictureWithoutLocalControlBackings() {
        height.intValue = 320
        playback.value = playback.value.copy(details = playback.value.details!!.copy(liveChatReplay = true))
        compose.setContent { Content() }
        for (background in listOf(Color(0xFF535862), Color.White, Color(0xFFF3E4C9), Color(0xFF142536))) {
            compose.runOnIdle { backdrop.value = background; controlsVisible.value = true }
            compose.mainClock.advanceTimeBy(200)
            fun assertDim(color: Color) {
                assertEquals(background.red * .75f, color.red, .012f)
                assertEquals(background.green * .75f, color.green, .012f)
                assertEquals(background.blue * .75f, color.blue, .012f)
            }
            for (control in listOf(
                compose.onNodeWithTag("player-play-pause"),
                compose.onNodeWithContentDescription("Player settings"),
                compose.onNodeWithContentDescription("Minimize player"),
                compose.onNodeWithContentDescription("Full screen"),
                compose.onNodeWithContentDescription("Show chat replay"),
            )) {
                control.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                val pixels = control.captureToImage().toPixelMap()
                val color = pixels[(pixels.width * .1f).toInt(), pixels.height / 2]
                assertDim(color)
            }
            val pixels = compose.onNodeWithTag("controls-surface").captureToImage().toPixelMap()
            for (y in listOf(1, pixels.height / 2, pixels.height - 2)) assertDim(pixels[1, y])
            compose.runOnIdle { controlsVisible.value = false }
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithTag("player-play-pause").assertDoesNotExist()
            val clear = compose.onNodeWithTag("controls-surface").captureToImage().toPixelMap()[1, 1]
            assertEquals(background.red, clear.red, .01f)
            assertEquals(background.green, clear.green, .01f)
            assertEquals(background.blue, clear.blue, .01f)
        }
        captureWatchScreenshot("player-background-icons-only")
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
        height.intValue = 320
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
