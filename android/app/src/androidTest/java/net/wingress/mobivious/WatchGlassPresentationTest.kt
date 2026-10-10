package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
class WatchGlassPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actionsKeepCircularButtonsWithVisibleLabelsAndDisabledDownloads() {
        val width = mutableIntStateOf(320)
        val enabled = mutableStateOf(true)
        val clicks = IntArray(4)
        compose.setContent { MaterialTheme(colorScheme = Liquid.light, typography = Liquid.typography) {
            WatchGlassScene(Modifier.width(width.intValue.dp)) {
                WatchActionRow(enabled.value, { clicks[0]++ }, { clicks[1]++ }, { clicks[2]++ }, { clicks[3]++ })
            }
        } }
        for ((index, name) in listOf("Save", "Download", "Share", "More").withIndex()) {
            compose.onNodeWithContentDescription(name).assertIsDisplayed().assertWidthIsEqualTo(56.dp)
                .assertHeightIsEqualTo(56.dp).performClick()
            compose.onNodeWithText(name).assertIsDisplayed()
            val icon = compose.onNodeWithContentDescription(name).getUnclippedBoundsInRoot()
            val label = compose.onNodeWithText(name).getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue(label.top >= icon.bottom)
            assertEquals(1, clicks[index])
        }
        compose.runOnIdle { width.intValue = 240; enabled.value = false }
        compose.onNodeWithContentDescription("Download").assertIsNotEnabled().assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp).performClick()
        assertEquals(1, clicks[1])
        compose.onNodeWithContentDescription("More").assertIsDisplayed().assertWidthIsEqualTo(48.dp)
    }

    @Test fun narrowLargeTextKeepsActionLabelsWhole() {
        compose.setContent { MaterialTheme(typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                WatchGlassScene(Modifier.width(240.dp)) { WatchActionRow(true, {}, {}, {}, {}) }
            }
        } }
        for (label in listOf("Save", "Download", "Share", "More")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            org.junit.Assert.assertFalse(layouts.single().hasVisualOverflow)
        }
    }

    @Test fun disclosureKeepsExpandedSemanticsAndWrapsWithLargeTextInOpaqueMode() {
        val expanded = mutableStateOf(false)
        compose.setContent { MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
            CompositionLocalProvider(LocalReduceTransparency provides true,
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                WatchGlassScene(Modifier.width(320.dp)) {
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
            WatchGlassScene(Modifier.width(320.dp)) { WatchActionRow(true, { saved++ }, {}, {}, {}) }
        } }
        compose.onNodeWithContentDescription("Save").performTouchInput { longClick() }
        compose.onAllNodesWithText("Save").assertCountEquals(2)
        assertEquals(0, saved)
    }
}
