package net.wingress.mobivious

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelDescriptionPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val description = (1..40).joinToString("\n\n") { "Paragraph $it: a complete channel description with several lines of text." } +
        "\n\nFinal line: https://example.test/channel"

    private fun content(text: String, fontScale: Float = 1f, width: Int = 390, dark: Boolean = false) {
        val channel = Channel("creator", "Creator with a long channel name", text, "42")
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                var open by remember { mutableStateOf(false) }
                LazyColumn(Modifier.width(width.dp).testTag("channel-page")) {
                    item { ChannelHeader(channel, "", true, false, readDescription = { open = true }) {} }
                }
                if (open) ChannelDescriptionSheet(channel) { open = false }
            }
        } }
    }

    private fun open() {
        compose.onNodeWithTag("channel-page").performScrollToNode(hasTestTag("channel-description-open"))
        compose.onNodeWithTag("channel-description-open").assertIsDisplayed().performClick()
        compose.onNodeWithTag("channel-description-sheet").assertIsDisplayed()
    }

    private fun readToTheEnd() {
        compose.onNodeWithTag("channel-description-text").assertTextEquals(description)
        compose.onNodeWithTag("channel-description-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        val range = compose.onNodeWithTag("channel-description-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        assertEquals(range.maxValue(), range.value(), 1f)
        val body = compose.onNodeWithTag("channel-description-text").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("channel-description-scroll").getUnclippedBoundsInRoot()
        assertTrue("The final line must fit inside the sheet's viewport", body.bottom <= viewport.bottom + 1.dp)
        compose.onNodeWithContentDescription("Close channel description").assertIsDisplayed()
    }

    @Test fun completeTextScrollsToTheFinalLineAndCloseAndBackKeepTheChannelPage() {
        content(description)
        open(); readToTheEnd()
        compose.onNodeWithContentDescription("Close channel description").performClick()
        compose.onNodeWithTag("channel-description-sheet").assertDoesNotExist()
        open()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.onNodeWithTag("channel-description-sheet").assertDoesNotExist()
        compose.onNodeWithTag("channel-page").assertIsDisplayed()
    }

    @Test fun narrowDarkLargeFontSheetKeepsItsCloseButtonAndAllTextAccessible() {
        content(description, fontScale = 2f, width = 320, dark = true)
        open(); readToTheEnd()
        compose.onNodeWithContentDescription("Close channel description").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
    }

    @Test fun blankDescriptionsHaveNoReadButton() {
        content(" \n ")
        compose.onNodeWithTag("channel-description-open").assertDoesNotExist()
        compose.onNodeWithTag("channel-description-sheet").assertDoesNotExist()
    }

    @Test fun shortDescriptionsAlsoOpenAndTheSheetCanBeSwipedAway() {
        content("A short channel description.")
        open()
        compose.onNodeWithTag("channel-description-text").assertTextEquals("A short channel description.")
        compose.onNodeWithTag("channel-description-sheet").performTouchInput { swipeDown(durationMillis = 100) }
        compose.onNodeWithTag("channel-description-sheet").assertDoesNotExist()
    }
}
