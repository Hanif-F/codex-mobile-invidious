package net.wingress.mobivious

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.SponsorBlockCategory
import net.wingress.mobivious.data.SponsorBlockPlayback
import net.wingress.mobivious.data.SponsorBlockSegment
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerFeedbackPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val segment = SponsorBlockSegment("manual", SponsorBlockCategory.SPONSOR, 10_000, 20_000)
    private val sponsor = mutableStateOf(SponsorBlockPlayback(active = segment, remaining = 8))
    private val controls = mutableStateOf(true)
    private val fullscreen = mutableStateOf(false)
    private val width = mutableIntStateOf(390)
    private val height = mutableIntStateOf(220)
    private val fontScale = mutableFloatStateOf(1f)
    private val dark = mutableStateOf(false)
    private val opaque = mutableStateOf(false)
    private val backdrop = mutableStateOf(Color(0xFFECEFF1))
    private val seek = mutableStateOf<Long?>(null)
    private var skipped = ""
    private var dismissed = ""
    private var fullscreenClicks = 0
    private lateinit var inputModeManager: InputModeManager

    @Composable private fun Content() {
        inputModeManager = LocalInputModeManager.current
        MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.floatValue),
                LocalReduceTransparency provides opaque.value) {
                Box(Modifier.width(width.intValue.dp).height(height.intValue.dp)
                    .background(backdrop.value).testTag("feedback-surface")) {
                    val offset = seek.value
                    if (offset != null) PlayerSeekFeedback(offset,
                        Modifier.align(if (offset < 0) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                            .fillMaxWidth(1f / 3f).padding(horizontal = 4.dp))
                    else PlayerControlsLayout(
                        PlaybackState(mediaId = "abcdefghijk", duration = 120_000, canPlay = true, playerState = Player.STATE_READY),
                        12_000, fullscreen.value, true, onPlay = {}, onSettings = {},
                        onFullscreen = { fullscreenClicks++ }, onChapters = {}, onCollapse = {},
                        showControls = controls.value,
                        notice = if (sponsor.value.active == null && sponsor.value.notice.isBlank()) null else ({
                            PlayerSponsorPrompt(sponsor.value, { skipped = it }, { dismissed = it })
                        }),
                        timeline = { modifier, _ -> Box(modifier.testTag("feedback-timeline")) })
                }
            }
        }
    }

    @Test fun compactActionsStayAtBottomLeftAndClearTheTimelineAndOtherControls() {
        compose.setContent { Content() }
        for ((wide, tall, scale, full) in listOf(
            Layout(390, 220, 1f, false), Layout(320, 180, 1f, false),
            Layout(240, 160, 2f, false), Layout(600, 340, 1f, true))) {
            compose.runOnIdle {
                width.intValue = wide; height.intValue = tall; fontScale.floatValue = scale; fullscreen.value = full
                controls.value = true
            }
            compose.waitForIdle()
            val surface = compose.onNodeWithTag("feedback-surface").getUnclippedBoundsInRoot()
            val pill = compose.onNodeWithTag("sponsorblock-actions").assertIsDisplayed().getUnclippedBoundsInRoot()
            val skip = compose.onNodeWithTag("sponsorblock-skip").assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp).getUnclippedBoundsInRoot()
            val close = compose.onNodeWithContentDescription("Dismiss SponsorBlock segment")
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).getUnclippedBoundsInRoot()
            val footer = compose.onNodeWithTag("player-footer").getUnclippedBoundsInRoot()
            val settings = compose.onNodeWithContentDescription("Player settings").getUnclippedBoundsInRoot()
            val play = compose.onNodeWithTag("player-play-pause").getUnclippedBoundsInRoot()
            assertEquals(surface.left.value + 8, pill.left.value, .5f)
            assertTrue(pill.right - pill.left < (surface.right - surface.left) * .75f)
            assertTrue(pill.top >= surface.top && pill.bottom <= footer.top - 8.dp)
            assertTrue(skip.right <= close.left)
            for (other in listOf(settings, play)) assertTrue("SponsorBlock must not cover another control",
                pill.right <= other.left || pill.left >= other.right || pill.bottom <= other.top || pill.top >= other.bottom)
            compose.onNodeWithText("Sponsor · 8s").assertDoesNotExist()
            compose.onNodeWithContentDescription("Full screen").takeIf { !full }?.performClick()
            captureWatchScreenshot("player-feedback-${wide}-${tall}-${if (full) "full" else "watch"}")
            compose.runOnIdle { controls.value = false }
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            val hiddenPill = compose.onNodeWithTag("sponsorblock-actions").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(hiddenPill.bottom > pill.bottom)
            assertTrue(hiddenPill.bottom <= surface.bottom - 8.dp)
        }
        assertEquals(3, fullscreenClicks)
    }

    @Test fun skipAndCloseRemainAccessibleAndConfirmationIsCompact() {
        compose.setContent { Content() }
        compose.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        compose.onNodeWithTag("sponsorblock-skip").assertContentDescriptionEquals("Skip Sponsor")
            .performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
            .performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(segment.id, skipped) }
        compose.onNodeWithContentDescription("Dismiss SponsorBlock segment").performClick()
        compose.runOnIdle {
            assertEquals(segment.id, dismissed)
            sponsor.value = sponsor.value.copy(notice = "Skipped Intro, Sponsor, Outro ".repeat(8))
        }
        val notice = compose.onNodeWithTag("sponsorblock-notice").assertIsDisplayed()
        val noticeBounds = notice.getUnclippedBoundsInRoot()
        assertTrue(noticeBounds.right - noticeBounds.left <= 280.dp)
        assertEquals(LiveRegionMode.Polite, notice.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        val layouts = mutableListOf<TextLayoutResult>()
        notice.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().isLineEllipsized(0))
        assertTrue(notice.getUnclippedBoundsInRoot().bottom <
            compose.onNodeWithTag("sponsorblock-actions").getUnclippedBoundsInRoot().top)
        captureWatchScreenshot("player-feedback-confirmation-and-actions")
        compose.runOnIdle { height.intValue = 160 }
        compose.onNodeWithTag("sponsorblock-skip").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("sponsorblock-notice").assertDoesNotExist()
        compose.runOnIdle { sponsor.value = sponsor.value.copy(active = null) }
        compose.onNodeWithTag("sponsorblock-actions").assertDoesNotExist()
        compose.onNodeWithTag("sponsorblock-notice").assertIsDisplayed()
        compose.runOnIdle { sponsor.value = SponsorBlockPlayback() }
        compose.onNodeWithTag("sponsorblock-prompt").assertDoesNotExist()
    }

    @Test fun seekHasNoBackdropAndSponsorHonorsReduceTransparency() {
        compose.setContent { Content() }
        for ((background, night, reduce) in listOf(
            Triple(Color.White, false, false), Triple(Color.Black, true, false), Triple(Color.White, true, true))) {
            compose.runOnIdle {
                backdrop.value = background; dark.value = night; opaque.value = reduce
                sponsor.value = SponsorBlockPlayback(active = segment)
            }
            val plate = compose.onNodeWithTag("sponsorblock-actions").captureToImage().toPixelMap()
            val expected = if (reduce) Color(0xFF181A1D).red else if (background == Color.White) .4f else 0f
            assertEquals(expected, plate[plate.width / 2, plate.height / 2].red, .03f)
            captureWatchScreenshot("player-feedback-pill-${if (night) "dark" else "light"}-${if (reduce) "opaque" else "transparent"}")
            for (offset in listOf(-10_000L, 30_000L)) {
                compose.runOnIdle { seek.value = offset }
                compose.onNodeWithTag("player-seek-feedback").assertTextEquals(if (offset < 0) "−10 seconds" else "+30 seconds")
                val pixels = compose.onNodeWithTag("player-seek-feedback").captureToImage().toPixelMap()
                assertEquals(background.red, pixels[2, pixels.height / 2].red, .01f)
                captureWatchScreenshot("player-feedback-${if (offset < 0) "back" else "forward"}-${if (night) "dark" else "light"}-${if (reduce) "opaque" else "transparent"}")
            }
            compose.runOnIdle { seek.value = null }
        }
    }

    private data class Layout(val width: Int, val height: Int, val scale: Float, val fullscreen: Boolean)
}
