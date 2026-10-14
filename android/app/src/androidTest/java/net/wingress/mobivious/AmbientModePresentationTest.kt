package net.wingress.mobivious

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
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
class AmbientModePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun videoColorsReachLightingWhileControlsRemainOpaque() {
        var dark by mutableStateOf(true)
        var opaque by mutableStateOf(false)
        lateinit var state: WatchAmbientState
        var foreground = Color.White

        compose.setContent {
            state = remember { WatchAmbientState().apply { enabled = true; show(AmbientPalette(List(9) { 0xFFFF0000.toInt() })) } }
            DisposableEffect(state) { onDispose { state.close() } }
            WatchAmbientAnimation(state)
            MaterialTheme(colorScheme = if (dark) appColorScheme(true) else appColorScheme(false), typography = AppTokens.typography) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalWatchAmbient provides state, LocalReduceTransparency provides opaque) {
                    WatchAmbientScene(Modifier.width(320.dp).height(640.dp).testTag("ambient-scene"),
                        Rect(8 * density, 0f, 312 * density, 180 * density)) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(200.dp))
                            AppSurface(Modifier.fillMaxWidth().height(72.dp).testTag("ambient-controls")) {
                                foreground = MaterialTheme.colorScheme.onSurfaceVariant
                                Text("Material", Modifier.align(Alignment.Center).testTag("ambient-label"), color = foreground)
                            }
                        }
                    }
                }
            }
        }
        for (isDark in listOf(true, false)) {
            compose.runOnIdle { dark = isDark }
            fun sample(tag: String): Color {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                return pixels[(pixels.width * .15f).toInt(), (pixels.height * .50f).toInt()]
            }
            compose.runOnIdle { state.show(AmbientPalette(List(9) { 0xFFFF0000.toInt() })) }
            compose.mainClock.advanceTimeBy(800)
            val redBackground = sample("watch-ambient-background")
            val redControls = sample("ambient-controls")
            captureWatchScreenshot("ambient-${if (isDark) "dark" else "light"}-red")
            compose.runOnIdle { state.show(AmbientPalette(List(9) { 0xFF0000FF.toInt() })) }
            compose.mainClock.advanceTimeBy(800)
            val blueBackground = sample("watch-ambient-background")
            val blueControls = sample("ambient-controls")
            assertTrue("Background follows video colors", difference(redBackground, blueBackground) > .06f)
            assertTrue("Controls remain independent of video colors", difference(redControls,blueControls)<.01f)
            captureWatchScreenshot("ambient-${if (isDark) "dark" else "light"}-blue")
            for (source in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF00FF00.toInt())) {
                compose.runOnIdle { state.show(AmbientPalette(List(9) { source })) }
                compose.mainClock.advanceTimeBy(800)
                val label = compose.onNodeWithTag("ambient-label").captureToImage().toPixelMap()
                val background = label[label.width / 2, 1]
                val contrast = (maxOf(foreground.luminance(), background.luminance()) + .05f) /
                    (minOf(foreground.luminance(), background.luminance()) + .05f)
                assertTrue("Control contrast $contrast dark=$isDark source=$source", contrast >= 4.5f)
            }
        }
        compose.runOnIdle { opaque = true }
        compose.onNodeWithTag("watch-ambient-background").assertDoesNotExist()
        compose.runOnIdle { opaque = false; state.enabled = false }
        compose.onNodeWithTag("watch-ambient-background").assertDoesNotExist()
        for (night in listOf(true, false)) {
            compose.runOnIdle { dark = night }
            val expected = if (night) appColorScheme(true).surface else appColorScheme(false).surface
            val pixels = compose.onNodeWithTag("ambient-scene").captureToImage().toPixelMap()
            for (y in listOf(1, pixels.height / 2, pixels.height - 2)) {
                assertEquals(expected.red, pixels[1, y].red, .01f)
                assertEquals(expected.green, pixels[1, y].green, .01f)
                assertEquals(expected.blue, pixels[1, y].blue, .01f)
            }
        }
    }

    private fun difference(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) +
        kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
    @Test fun disabledAmbientDoesNotAnimateARetainedPalette() {
        compose.mainClock.autoAdvance = false
        lateinit var state: WatchAmbientState
        compose.setContent {
            state = remember { WatchAmbientState().apply { show(AmbientPalette(List(9) { 0xFFFF0000.toInt() })) } }
            DisposableEffect(state) { onDispose { state.close() } }
            WatchAmbientAnimation(state)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {
            state.colors.forEach { assertEquals(Color.Transparent,it.value); assertFalse(it.isRunning) }
            state.enabled = true
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { state.colors.forEach { assertEquals(1f,it.value.red,.01f); assertEquals(1f,it.value.alpha,.01f) } }
    }

}
