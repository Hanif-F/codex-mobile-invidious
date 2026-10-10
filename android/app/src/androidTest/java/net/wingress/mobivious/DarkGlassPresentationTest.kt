package net.wingress.mobivious

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import net.wingress.mobivious.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Hardware-rendered backdrops exercise the real material independently of artwork/network loading. */
@RunWith(AndroidJUnit4::class)
class DarkGlassPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialsRemainVisibleInBothAppearancesAndOpaqueMode() {
        var dark by mutableStateOf(false)
        var opaque by mutableStateOf(false)
        var prominent by mutableStateOf(false)
        val backgrounds = listOf(Color.Black, Color.White, Color(0xFFFF3377), Color(0xFF00DD99), Color.Black)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light, typography = Liquid.typography) {
                CompositionLocalProvider(LocalReduceTransparency provides opaque) {
                    Column(Modifier.fillMaxSize().testTag("glass-matrix").background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Glass over dark, bright, colored and detailed content", color = MaterialTheme.colorScheme.onSurface)
                        backgrounds.forEachIndexed { index, background ->
                            val backdrop = rememberLayerBackdrop()
                            Box(Modifier.fillMaxWidth().height(84.dp)) {
                                Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(background).drawBehind {
                                    if (index == backgrounds.lastIndex) {
                                        val cell = 12.dp.toPx()
                                        for (x in 0..(size.width / cell).toInt()) for (y in 0..(size.height / cell).toInt()) {
                                            if ((x + y) % 2 == 0) drawRect(Color.White, Offset(x * cell, y * cell), Size(cell, cell))
                                        }
                                    }
                                })
                                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                                    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (material in listOf("Shell", "Browse", "Watch")) {
                                            Box(Modifier.weight(1f).fillMaxHeight().then(when (material) {
                                                "Shell" -> Modifier.liquidGlass(prominent = prominent)
                                                "Browse" -> Modifier.browseGlass(prominent = prominent)
                                                else -> Modifier.watchGlass(Liquid.pill, prominent)
                                            }).testTag("glass-$index-$material"), contentAlignment = Alignment.Center) {
                                                Text(material, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for ((isDark, isOpaque, isProminent) in listOf(Triple(false, false, false), Triple(true, false, false),
            Triple(true, true, false), Triple(true, false, true), Triple(true, true, true))) {
            compose.runOnIdle { dark = isDark; opaque = isOpaque; prominent = isProminent }
            // A semantics assertion alone can observe the previous frame on the emulator's render thread.
            val surface = if (isDark) Liquid.dark.surface else Liquid.light.surface
            compose.waitUntil(5_000) {
                val pixels = compose.onNodeWithTag("glass-matrix").captureToImage().toPixelMap()
                val actual = pixels[pixels.width - 2, pixels.height / 2]
                kotlin.math.abs(actual.red - surface.red) < .005f
            }
            compose.onNodeWithTag("glass-1-Shell").assertIsDisplayed()
            captureWatchScreenshot("glass-matrix-${if (isDark) "dark" else "light"}-${if (isOpaque) "opaque" else "transparent"}${if (isProminent) "-prominent" else ""}")
            if (isDark) for (index in backgrounds.indices) for (material in listOf("Shell", "Browse", "Watch")) {
                val pixels = compose.onNodeWithTag("glass-$index-$material").captureToImage().toPixelMap()
                // Sample beside the glyphs at text height, including the upper reflection.
                for (x in listOf(.18f, .82f)) for (y in listOf(.35f, .5f, .65f)) {
                    val background = pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
                    assertContrast(Liquid.dark.onSurfaceVariant, background, 4.5f,
                        "$material backdrop $index opaque=$isOpaque prominent=$isProminent")
                }
            }
        }
    }

    @Test fun neutralPalettePreservesTextActionAndStatusContrast() {
        val colors = Liquid.dark
        for (surface in listOf(colors.background, colors.surfaceContainerLowest, colors.surfaceContainerLow,
            colors.surfaceContainer, colors.surfaceContainerHigh, colors.surfaceContainerHighest)) {
            assertContrast(colors.onSurface, surface, 4.5f, "Reading text")
            assertContrast(colors.onSurfaceVariant, surface, 4.5f, "Secondary text")
            assertContrast(colors.primary, surface, 4.5f, "Actions and links")
        }
        assertContrast(colors.onPrimary, colors.primary, 4.5f, "Primary button")
        assertContrast(colors.onPrimaryContainer, colors.primaryContainer, 4.5f, "Prominent control")
        assertContrast(colors.inverseOnSurface, colors.inverseSurface, 4.5f, "Inverse surface")
        assertContrast(colors.onError, colors.error, 4.5f, "Error")
        assertContrast(colors.onErrorContainer, colors.errorContainer, 4.5f, "Error container")
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float, context: String) {
        val a = foreground.luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("$context: contrast $ratio must be >= $minimum", ratio >= minimum)
    }
}
