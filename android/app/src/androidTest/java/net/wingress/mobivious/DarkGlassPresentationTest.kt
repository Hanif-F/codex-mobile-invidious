package net.wingress.mobivious

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
        val foregrounds = java.util.concurrent.ConcurrentHashMap<String, GlassContentColors>()
        val scenes = java.util.concurrent.ConcurrentHashMap<Int, GlassScene>()
        val backgrounds = listOf(Color.Black, Color.White, Color(0xFFFF3377), Color(0xFF00DD99), Color.Black)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light, typography = Liquid.typography) {
                CompositionLocalProvider(LocalReduceTransparency provides opaque, LocalAdaptiveControlColors provides true) {
                    Column(Modifier.fillMaxSize().testTag("glass-matrix").background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Adaptive text, clear interiors and directional depth", color = MaterialTheme.colorScheme.onSurface)
                        backgrounds.forEachIndexed { index, background ->
                            val scene = rememberGlassScene(background)
                            SideEffect { scenes[index] = scene }
                            Box(Modifier.fillMaxWidth().height(84.dp)) {
                                Box(Modifier.matchParentSize().recordGlassScene(scene).background(background).drawBehind {
                                    if (index == backgrounds.lastIndex) {
                                        val cell = 12.dp.toPx()
                                        for (x in 0..(size.width / cell).toInt()) for (y in 0..(size.height / cell).toInt()) {
                                            if ((x + y) % 2 == 0) drawRect(Color.White, Offset(x * cell, y * cell), Size(cell, cell))
                                        }
                                    }
                                })
                                CompositionLocalProvider(LocalGlassBackdrop provides scene.backdrop, LocalGlassScene provides scene) {
                                    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (material in listOf("Shell", "Browse", "Watch", "Section")) {
                                            val role = if (material == "Section") GlassMaterialRole.Section else GlassMaterialRole.Control
                                            var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                                            val colors = rememberGlassContentColors(bounds, role, prominent)
                                            SideEffect { foregrounds["$index-$material"] = colors }
                                            Box(Modifier.weight(1f).fillMaxHeight()
                                                .onGloballyPositioned { bounds = it.boundsInRoot() }
                                                .then(when (material) {
                                                    "Shell" -> Modifier.liquidGlass(prominent = prominent)
                                                    "Browse" -> Modifier.browseGlass(prominent = prominent)
                                                    "Watch" -> Modifier.watchGlass(Liquid.pill, prominent)
                                                    else -> Modifier.glassMaterial(Liquid.card, role = role)
                                                }).testTag("glass-$index-$material"), contentAlignment = Alignment.Center) {
                                                GlassForeground(colors) {
                                                    GlassLabel(material, Modifier.testTag("label-$index-$material"),
                                                        color = colors.secondary, style = MaterialTheme.typography.labelMedium)
                                                    GlassIcon(Icons.Default.Check, "Selected $material",
                                                        Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(18.dp)
                                                            .testTag("icon-$index-$material"), tint = colors.primary)
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
        }
        for ((isDark, isOpaque, isProminent) in listOf(Triple(false, false, false), Triple(true, false, false),
            Triple(false, true, false), Triple(true, true, false), Triple(true, false, true), Triple(false, false, true))) {
            compose.runOnIdle { dark = isDark; opaque = isOpaque; prominent = isProminent }
            compose.waitUntil(5_000) { isOpaque || scenes.values.all { it.captures > 0 } }
            val surface = if (isDark) Liquid.dark.surface else Liquid.light.surface
            compose.waitUntil(5_000) {
                val pixels = compose.onNodeWithTag("glass-matrix").captureToImage().toPixelMap()
                kotlin.math.abs(pixels[pixels.width - 2, pixels.height / 2].red - surface.red) < .005f
            }
            compose.waitForIdle()
            captureWatchScreenshot("glass-matrix-${if (isDark) "dark" else "light"}-${if (isOpaque) "opaque" else "transparent"}${if (isProminent) "-prominent" else ""}")
            for (index in backgrounds.indices) for (material in listOf("Shell", "Browse", "Watch", "Section")) {
                val colors = foregrounds.getValue("$index-$material")
                val pixels = compose.onNodeWithTag("label-$index-$material").captureToImage().toPixelMap()
                // Read the continuous glass material next to actual glyphs.
                for (y in listOf(1, pixels.height - 2)) {
                    assertContrast(colors.secondary, pixels[pixels.width / 2, y], 4.5f,
                        "$material backdrop $index dark=$isDark opaque=$isOpaque prominent=$isProminent colors=$colors stats=${scenes[index]?.contrastAt(scenes[index]!!.bounds)}")
                }
                val icon = compose.onNodeWithTag("icon-$index-$material").captureToImage().toPixelMap()
                assertContrast(colors.primary, icon[icon.width / 2, 2], 3f, "Actual meaningful icon $index-$material")
            }
            for (material in listOf("Shell", "Browse", "Watch", "Section")) {
                fun interior(index: Int): Color {
                    val pixels = compose.onNodeWithTag("glass-$index-$material").captureToImage().toPixelMap()
                    return pixels[pixels.width / 2, (pixels.height * .2f).toInt()]
                }
                val transmission = kotlin.math.abs(interior(1).luminance() - interior(0).luminance())
                assertTrue("$material transmission=$transmission opaque=$isOpaque",
                    if (isOpaque) transmission < .025f else transmission > .35f)
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
