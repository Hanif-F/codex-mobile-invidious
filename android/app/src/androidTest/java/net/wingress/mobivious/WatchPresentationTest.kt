package net.wingress.mobivious

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

    @Test fun expandedWatchUsesFlatNeutralSurfacesInBothThemes() {
        var dark by mutableStateOf(true)
        compose.setContent { MaterialTheme(colorScheme = appColorScheme(dark)) {
            WatchContent(Modifier.size(320.dp, 180.dp).testTag("watch-flat")) {}
        } }
        for (night in listOf(true, false)) {
            compose.runOnIdle { dark = night }
            val expected = appColorScheme(night).surface
            val pixels = compose.onNodeWithTag("watch-flat").captureToImage().toPixelMap()
            for (x in listOf(1, pixels.width / 2, pixels.width - 2)) {
                for (y in listOf(1, pixels.height / 2, pixels.height - 2)) {
                    assertEquals(expected.red, pixels[x, y].red, .01f)
                    assertEquals(expected.green, pixels[x, y].green, .01f)
                    assertEquals(expected.blue, pixels[x, y].blue, .01f)
                }
            }
        }
    }

    @Test fun moreMenuSurvivesThemeAndWidthChanges() {
        var dark by mutableStateOf(true)
        var width by mutableIntStateOf(320)
        compose.setContent { MaterialTheme(colorScheme = appColorScheme(dark), typography = AppTokens.typography) {
            WatchContent(Modifier.width(width.dp)) {
                var open by remember { mutableStateOf(false) }
                WatchActionRow(true, {}, {}, {}, { open = true }, moreMenu = {
                    AppDropdownMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text("Keep this menu open") }, onClick = { open = false })
                    }
                })
            }
        } }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Keep this menu open").assertIsDisplayed()
        compose.runOnIdle { dark = false; width = 240 }
        compose.onNodeWithText("Keep this menu open").assertIsDisplayed().performClick()
        compose.onNodeWithText("Keep this menu open").assertDoesNotExist()
    }

    @Test fun actionsKeepVisibleLabelsAndDisabledDownloads() {
        val width = mutableIntStateOf(320)
        val enabled = mutableStateOf(true)
        val clicks = IntArray(4)
        compose.setContent { MaterialTheme(colorScheme = appColorScheme(false), typography = AppTokens.typography) {
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
            org.junit.Assert.assertTrue(label.top >= icon.bottom)
            assertEquals((icon.right - icon.left).value, (icon.bottom - icon.top).value, .5f)
            assertEquals((icon.left.value + icon.right.value) / 2, (label.left.value + label.right.value) / 2, .5f)
            assertEquals(1, clicks[index])
        }
        compose.runOnIdle { width.intValue = 240; enabled.value = false }
        compose.onNodeWithContentDescription("Download").assertIsNotEnabled().assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks[1])
        compose.onNodeWithContentDescription("More").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
    }

    @Test fun circlesStayInOneRowAndLargeLabelsWrapWithoutClippingInRtl() {
        var width by mutableIntStateOf(240)
        var scale by mutableFloatStateOf(2f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        compose.setContent { MaterialTheme(typography = AppTokens.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale),
                LocalLayoutDirection provides direction) {
                WatchContent(Modifier.width(width.dp)) { WatchActionRow(true, {}, {}, {}, {}) }
            }
        } }
        for ((wide, font, rtl) in listOf(Triple(192, 2f, false), Triple(240, 2f, false), Triple(320, 1f, false), Triple(600, 2f, true))) {
            compose.runOnIdle { width = wide; scale = font; direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr }
            val row = compose.onNodeWithTag("watch-actions").getUnclippedBoundsInRoot()
            val buttons = listOf("Save", "Download", "Share", "More").map { label ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label).assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertLabelGlyphsFit(layouts.single(), label)
                if (wide == 240 && label == "Download") org.junit.Assert.assertTrue(layouts.single().lineCount > 1)
                val circle = compose.onNodeWithContentDescription(label).assertIsDisplayed()
                    .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
                assertEquals((circle.right - circle.left).value, (circle.bottom - circle.top).value, .5f)
                org.junit.Assert.assertTrue(circle.left >= row.left && circle.right <= row.right)
                org.junit.Assert.assertTrue(compose.onNodeWithText(label).getUnclippedBoundsInRoot().top >= circle.bottom)
                circle
            }
            buttons.forEach { assertEquals(buttons.first().top.value, it.top.value, .5f) }
            buttons.zipWithNext().forEach { (left, right) ->
                org.junit.Assert.assertTrue(if (rtl) right.right <= left.left else left.right <= right.left)
            }
            captureWatchScreenshot("watch-action-circles-$wide-${if (rtl) "rtl" else "ltr"}")
        }
    }

    @Test fun disclosureKeepsExpandedSemanticsAndWrapsWithLargeTextInOpaqueMode() {
        val expanded = mutableStateOf(false)
        compose.setContent { MaterialTheme(colorScheme = appColorScheme(true), typography = AppTokens.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
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
