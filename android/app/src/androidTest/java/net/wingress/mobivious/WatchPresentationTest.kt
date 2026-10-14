package net.wingress.mobivious

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedWatchFallbackIsFlatBeforeAmbientArrivesAndAfterItIsDisabled() {
        var dark by mutableStateOf(true)
        var ambient by mutableStateOf<WatchAmbientState?>(null)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) AppTokens.dark else AppTokens.light) {
                AppBackground {
                    CompositionLocalProvider(LocalWatchAmbient provides ambient) {
                        WatchContent(Modifier.size(320.dp, 180.dp).testTag("watch-flat-fallback")) {}
                    }
                }
            }
        }
        for (night in listOf(true, false)) {
            compose.runOnIdle { dark = night; ambient = null }
            for (afterAmbient in listOf(false, true)) {
                if (afterAmbient) {
                    compose.runOnIdle { ambient = WatchAmbientState() }
                    compose.runOnIdle { ambient = null }
                }
                val expected = if (night) AppTokens.dark.surface else AppTokens.light.surface
                val pixels = compose.onNodeWithTag("watch-flat-fallback").captureToImage().toPixelMap()
                for (x in listOf(1, pixels.width / 2, pixels.width - 2)) {
                    for (y in listOf(1, pixels.height / 2, pixels.height - 2)) {
                        assertEquals(expected.red, pixels[x, y].red, .01f)
                        assertEquals(expected.green, pixels[x, y].green, .01f)
                        assertEquals(expected.blue, pixels[x, y].blue, .01f)
                    }
                }
            }
        }
    }

    @Test fun arrivingAmbientPalettePreservesAnOpenMenu() {
        val ambient = mutableStateOf<WatchAmbientState?>(null)
        compose.setContent { MaterialTheme(colorScheme = AppTokens.dark, typography = AppTokens.typography) {
            AppBackground {
                CompositionLocalProvider(LocalWatchAmbient provides ambient.value) {
                    WatchContent(Modifier.width(320.dp)) {
                        var open by remember { mutableStateOf(false) }
                        WatchActionRow(true, {}, {}, {}, { open = true }, moreMenu = {
                            AppDropdownMenu(open, { open = false }) {
                                DropdownMenuItem(text = { Text("Keep this menu open") }, onClick = { open = false })
                            }
                        })
                    }
                }
            }
        } }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Keep this menu open").assertIsDisplayed()
        compose.runOnIdle { ambient.value = WatchAmbientState() }
        compose.onNodeWithText("Keep this menu open").assertIsDisplayed()
        compose.runOnIdle { ambient.value = null }
        compose.onNodeWithText("Keep this menu open").assertIsDisplayed().performClick()
        compose.onNodeWithText("Keep this menu open").assertDoesNotExist()
    }

    @Test fun actionsKeepVisibleLabelsAndDisabledDownloads() {
        val width = mutableIntStateOf(320)
        val enabled = mutableStateOf(true)
        val clicks = IntArray(4)
        compose.setContent { MaterialTheme(colorScheme = AppTokens.light, typography = AppTokens.typography) {
            WatchContent(Modifier.width(width.intValue.dp)) {
                WatchActionRow(enabled.value, { clicks[0]++ }, { clicks[1]++ }, { clicks[2]++ }, { clicks[3]++ })
            }
        } }
        for ((index, name) in listOf("Save", "Download", "Share", "More").withIndex()) {
            compose.onNodeWithContentDescription(name).assertIsDisplayed().assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText(name).assertIsDisplayed()
            val icon = compose.onNodeWithContentDescription(name).getUnclippedBoundsInRoot()
            val label = compose.onNodeWithText(name).getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue(label.top >= icon.top && label.bottom <= icon.bottom)
            assertEquals(1, clicks[index])
        }
        compose.runOnIdle { width.intValue = 240; enabled.value = false }
        compose.onNodeWithContentDescription("Download").assertIsNotEnabled().assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks[1])
        compose.onNodeWithContentDescription("More").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
    }

    @Test fun narrowLargeTextKeepsActionLabelsWhole() {
        compose.setContent { MaterialTheme(typography = AppTokens.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                WatchContent(Modifier.width(240.dp)) { WatchActionRow(true, {}, {}, {}, {}) }
            }
        } }
        for (label in listOf("Save", "Download", "Share", "More")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertLabelGlyphsFit(layouts.single(),label)
        }
    }

    @Test fun disclosureKeepsExpandedSemanticsAndWrapsWithLargeTextInOpaqueMode() {
        val expanded = mutableStateOf(false)
        compose.setContent { MaterialTheme(colorScheme = AppTokens.dark, typography = AppTokens.typography) {
            CompositionLocalProvider(LocalReduceTransparency provides true,
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                WatchContent(Modifier.width(320.dp)) {
                    WatchDisclosureRow("Description", Icons.AutoMirrored.Filled.Notes, Modifier.testTag("description"),
                        detail = "A detailed description that wraps across multiple lines", expanded = expanded.value) {
                        expanded.value = !expanded.value
                    }
                }
            }
        } }
        compose.onNodeWithTag("description").assertHeightIsAtLeast(56.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed")).performClick()
        compose.onNodeWithTag("description")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Description").assertIsDisplayed()
    }

    @Test fun longPressShowsTheActionTooltipWithoutActivatingTheButton() {
        var saved = 0
        compose.setContent { MaterialTheme {
            WatchContent(Modifier.width(320.dp)) { WatchActionRow(true, { saved++ }, {}, {}, {}) }
        } }
        compose.onNodeWithContentDescription("Save").performTouchInput { longClick() }
        compose.onAllNodesWithText("Save").assertCountEquals(2)
        assertEquals(0, saved)
    }
}
