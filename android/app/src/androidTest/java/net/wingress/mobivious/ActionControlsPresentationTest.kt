package net.wingress.mobivious

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native interaction/accessibility regressions independent of playback and network fixtures. */
@RunWith(AndroidJUnit4::class)
class ActionControlsPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nestedOverflowClosesBeforeActionAndBackDoesNotActivateParent() {
        var opened = 0
        var edited = 0
        compose.setContent { MaterialTheme {
            Card(onClick = { opened++ }) {
                OverflowMenu("Playlist actions", "playlist", Modifier.testTag("actions")) { close ->
                    DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { close(); edited++ })
                }
            }
        } }
        compose.onNodeWithTag("actions").performClick()
        compose.onNodeWithText("Edit playlist").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.onNodeWithText("Edit playlist").assertDoesNotExist()
        assertEquals(0, opened)
        assertEquals(0, edited)
        compose.onNodeWithTag("actions").performClick()
        compose.onNodeWithText("Edit playlist").performClick()
        compose.onNodeWithText("Edit playlist").assertDoesNotExist()
        assertEquals(0, opened)
        assertEquals(1, edited)
    }

    @Test fun openMenuResetsAcrossEntityAndAccountChanges() {
        val identity = mutableStateOf("playlist-a" to "account-a")
        compose.setContent { MaterialTheme {
            OverflowMenu("Playlist actions", identity.value, Modifier.testTag("actions")) { close ->
                DropdownMenuItem(text = { Text("Edit playlist") }, onClick = close)
            }
        } }
        compose.onNodeWithTag("actions").performClick()
        compose.runOnIdle { identity.value = "playlist-b" to "account-a" }
        compose.onNodeWithText("Edit playlist").assertDoesNotExist()
        compose.onNodeWithTag("actions").performClick()
        compose.runOnIdle { identity.value = "playlist-b" to "account-b" }
        compose.onNodeWithText("Edit playlist").assertDoesNotExist()
    }

    @Test fun selectorKeepsLabelAndValueAndCannotOpenWhileDisabled() {
        val selected = mutableStateOf("inherit")
        val enabled = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            DropdownChoiceRow("SponsorBlock", listOf("inherit" to "Use global", "true" to "Enabled"),
                selected.value, modifier = Modifier.testTag("choice"), enabled = enabled.value) { selected.value = it }
        } }
        compose.onNodeWithTag("choice").assert(hasText("SponsorBlock") and hasText("Use global")).performClick()
        compose.onNode(hasText("Use global") and isSelected()).assertExists()
        compose.onNodeWithText("Enabled").performClick()
        compose.onNodeWithTag("choice").assert(hasText("SponsorBlock") and hasText("Enabled"))
        assertEquals("true", selected.value)
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("choice").assertIsNotEnabled()
        compose.onNodeWithTag("choice").performClick()
        compose.onNodeWithText("Use global").assertDoesNotExist()
    }

    private fun largeFontLayout(dark: Boolean, width: Int) {
        val channel = Channel("UC" + "a".repeat(22), "A creator with a long name", subscribers = "42")
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Surface(Modifier.width(width.dp)) {
                    Column {
                        ChannelHeader(channel, "https://instance.test", false, false, actions = {
                            OverflowMenu("Channel actions", channel.id, Modifier.testTag("channel-actions")) {}
                        }) {}
                        DropdownChoiceRow("Default quality", listOf("auto" to "Auto"), "auto", modifier = Modifier.testTag("quality")) {}
                        ActionRow("Channel SponsorBlock settings", Modifier.testTag("channel-settings")) {}
                    }
                }
            }
        } }
        compose.onNodeWithText("Subscribe").assertIsDisplayed()
        compose.onNodeWithTag("channel-actions").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("quality").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("channel-settings").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test fun darkNarrowLayoutKeepsControlsAccessibleAtDoubleFontSize() = largeFontLayout(true, 320)
    @Test fun lightWideLayoutKeepsControlsAccessibleAtDoubleFontSize() = largeFontLayout(false, 640)
}
