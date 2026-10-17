package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.StateRestorationTester
import net.wingress.mobivious.data.DownloadChoice
import net.wingress.mobivious.data.DownloadKind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.AccentPreset
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppControlPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/mobivious-ui-audit", "screencap -p /data/local/tmp/mobivious-ui-audit/$name.png"))
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    @Test fun subscriptionStateAndExplicitActionRemainClearWithLargeText() {
        var dark by mutableStateOf(false)
        var busy by mutableStateOf(false)
        var known by mutableStateOf(true)
        var subscribed by mutableStateOf(true)
        var changes = 0
        compose.setContent { AppTheme(dark, AccentPreset.BLUE) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Surface(Modifier.width(320.dp).height(850.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        SubscriptionControl(subscribed, { changes++ }, busy = busy, known = known, checking = !known,
                            error = if (dark) "Could not update the subscription. Your current subscription is unchanged." else null)
                    }
                }
            }
        } }
        compose.onNodeWithText("Subscribed").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, changes)
        compose.onNodeWithTag("subscription-unsubscribe").assertIsDisplayed().performClick()
        assertEquals(1, changes)
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Unsubscribing…").assertIsNotEnabled()
        capture("subscription-large-pending")
        compose.runOnIdle { busy = false; dark = true }
        compose.onNodeWithText("Subscribed").assertIsDisplayed()
        capture("subscription-large-dark-error")
        compose.runOnIdle { known = false; subscribed = false }
        compose.onNodeWithText("Checking…").assertIsNotEnabled()
        compose.onNodeWithText("Subscribe", substring = false).assertDoesNotExist()
    }

    @Test fun settingsToolbarAndSegmentedControlsFitNarrowLargeTextAndKeepRowsReachable() {
        var selected by mutableStateOf("Popular")
        var backed = false
        compose.setContent { AppTheme(true, AccentPreset.BLUE) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                BrowseAppSurface(Modifier.width(320.dp).height(850.dp), toolbar = {
                    LibraryToolbar("History & library", back = { backed = true }, statusInset = false, backLabel = "Back from History & library", controls = {
                            AppSegments(listOf("Popular", "Trending"), selected, { it }, Modifier.fillMaxWidth(), tag = { "feed-$it" }) { selected = it }
                        })
                }) { modifier, top -> LazyColumn(modifier.testTag("rows"), contentPadding = PaddingValues(top = top, bottom = 32.dp)) {
                    items(30) { Text("Preference $it", Modifier.fillMaxWidth().padding(20.dp).testTag("preference-$it")) }
                } }
            }
        } }
        val header = compose.onNodeWithTag("app-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithTag("preference-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top >= header.bottom)
        compose.onNodeWithTag("feed-Trending").assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.onNodeWithTag("rows").performScrollToIndex(29)
        compose.onNodeWithTag("preference-29").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back from History & library").assertIsDisplayed().performClick()
        assertTrue(backed)
        capture("settings-toolbar-large-dark")
    }
    @Test fun actionMenusKeepLabelsTargetsAndDisabledActionsAtLargeTextInBothDirections() {
        var dark by mutableStateOf(false)
        var rtl by mutableStateOf(false)
        var clicks = 0
        compose.setContent { AppTheme(dark, AccentPreset.BLUE) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.width(320.dp)) {
                    OverflowMenu("Actions", "entity") { close ->
                        ActionMenuItem("Play audio only", Icons.Default.Headphones, onClick = { clicks++; close() })
                        ActionMenuItem("Download", Icons.Default.Download, enabled = false, onClick = { clicks++ })
                        ActionMenuDivider()
                        ActionMenuItem("Remove from playlist", Icons.Default.Delete, destructive = true, onClick = { clicks++; close() })
                    }
                }
            }
        } }
        compose.onNodeWithContentDescription("Actions").performClick()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night; rtl = night }
            for (label in listOf("Play audio only", "Download", "Remove from playlist"))
                compose.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("Download").assertIsNotEnabled().performClick()
            assertEquals(0, clicks)
            capture("action-menu-large-${if (night) "dark-rtl" else "light"}")
        }
        compose.onNodeWithText("Play audio only").performClick()
        assertEquals(1, clicks)
        compose.onNodeWithText("Remove from playlist").assertDoesNotExist()
    }

    @Test fun playerMoreAndDownloadLabelsRemainReadableAndRestoreAtNarrowLargeText() {
        val restoration = StateRestorationTester(compose)
        var rtl by mutableStateOf(false)
        val choice = DownloadChoice("av1", DownloadKind.VIDEO, "/download",
            """{"type":"video/mp4; codecs=\"av01.0.05M.08\"","size":"1920x1080","fps":60,"bitrate":1500000,"clen":"12000000"}""")
        restoration.setContent { AppTheme(rtl, AccentPreset.BLUE) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Surface(Modifier.width(320.dp).height(700.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PlayerSettingsMore(expanded, { expanded = it }) {
                        Text("Annotations")
                        Text("Audio only")
                        Text("Picture in picture")
                        Text("Refresh buffer")
                    }
                    DownloadChoiceText(choice)
                }
                }
            }
        } }
        compose.onNodeWithText("Annotations").assertDoesNotExist()
        compose.onNodeWithTag("player-settings-more").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Annotations").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("player-settings-more").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        for (direction in listOf(false, true)) {
            compose.runOnIdle { rtl = direction }
            for (label in listOf("1080p · 60 fps · AV1", downloadChoiceLabel(choice).secondary)) {
                val text = compose.onNodeWithText(label).performScrollTo()
                text.assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); action(layouts)
                    layouts.forEach {
                        assertLabelGlyphsFit(it, "download label")
                        if (label == downloadChoiceLabel(choice).secondary) assertTrue("Bitrate starts before its unit", it.getBoundingBox(0).left < it.getBoundingBox(label.indexOf("Mbps")).left)
                    }
                }
            }
            capture("player-more-download-large-${if (direction) "rtl" else "ltr"}")
        }
        compose.onNodeWithTag("player-settings-more").performScrollTo().performClick()
        compose.onNodeWithText("Annotations").assertDoesNotExist()
    }

}
