package net.wingress.mobivious

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFoundationTest {
    @get:Rule val compose = createComposeRule()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun nativeCaptionsIgnoreBlackCueWindowsAndBackgroundSpans() {
        val background = Color(0xFF6F9EBA)
        compose.setContent {
            Box(Modifier.size(320.dp, 180.dp).background(background).testTag("native-caption")) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                    androidx.media3.ui.PlayerView(context).apply {
                        removeBlackUiBackgrounds()
                        useController = false
                        // This test supplies a colored picture instead of a decoder frame.
                        videoSurfaceView?.visibility = android.view.View.GONE
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter).visibility = android.view.View.GONE
                        subtitleView!!.apply {
                            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                            val text = android.text.SpannableString("CAPTION          CAPTION").apply {
                                setSpan(android.text.style.BackgroundColorSpan(android.graphics.Color.BLACK),
                                    0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(text)
                                .setWindowColor(android.graphics.Color.BLACK).build()))
                        }
                    }
                }, modifier = Modifier.matchParentSize())
            }
        }
        captureWatchScreenshot("no-black-background-native-caption")
        val pixels = compose.onNodeWithTag("native-caption").captureToImage().toPixelMap()
        var glyphs = 0
        for (y in pixels.height / 2 until pixels.height) {
            // The wide gap between the two words must expose the picture, even
            // when the source caption explicitly requests black windows/spans.
            assertEquals(background.red, pixels[pixels.width / 2, y].red, .01f)
            assertEquals(background.green, pixels[pixels.width / 2, y].green, .01f)
            for (x in 0 until pixels.width) if (pixels[x, y].red > .9f) glyphs++
        }
        assertTrue("The caption must still be visible", glyphs > 100)
    }

    @Test fun pressCancellationAndRapidRepetitionKeepOneSemanticAction() {
        var clicks = 0
        compose.setContent {
            MaterialTheme(colorScheme = AppTokens.dark, typography = AppTokens.typography) {
                AppBackground(Modifier.size(300.dp, 220.dp)) {
                    AppIconButton(androidx.compose.material.icons.Icons.Default.Check, "Confirm",
                        Modifier.align(androidx.compose.ui.Alignment.Center).testTag("press-control")) { clicks++ }
                }
            }
        }
        val control = compose.onNodeWithTag("press-control")
        control.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        control.performTouchInput { down(center); advanceEventTime(80); moveTo(androidx.compose.ui.geometry.Offset(-80f, -80f)); up() }
        compose.runOnIdle { assertEquals("Canceled presses must not click", 0, clicks) }
        repeat(4) { control.performClick() }
        compose.runOnIdle { assertEquals(4, clicks) }
        control.performTouchInput { down(center); advanceEventTime(120) }
        captureWatchScreenshot("overhaul-control-pressed")
        control.performTouchInput { cancel() }
        compose.waitForIdle()
        control.assertHasClickAction()
        compose.runOnIdle { assertEquals("Cancellation must remain reversible", 4, clicks) }
    }

}
