package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.AccentPreset
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PanelPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowLargeTextHeaderKeepsNavigationActionsAndRowsReachable() {
        var dark by mutableStateOf(false)
        var rtl by mutableStateOf(false)
        var back = 0
        var close = 0
        var saved = 0
        compose.setContent { AppTheme(dark, AccentPreset.BLUE) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PanelScaffold(Modifier.width(320.dp).height(650.dp), toolbar = {
                    PanelHeader("Edit playlist and description", "Close editor", { close++ }, back = { back++ },
                        backLabel = "Back to playlists", actions = {
                            LibraryActionButton("Save playlist", Modifier.testTag("panel-save"), prominent = true) { saved++ }
                        })
                }) { body, _ -> LazyColumn(body.testTag("panel-rows")) {
                    items(30) { Text("Playlist item $it", Modifier.fillMaxWidth().padding(16.dp).testTag("panel-row-$it")) }
                } }
            }
        } }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night; rtl = night }
            compose.onNodeWithTag("panel-rows").performScrollToIndex(0)
            val header = compose.onNodeWithTag("panel-header").getUnclippedBoundsInRoot()
            assertTrue(compose.onNodeWithTag("panel-row-0").assertIsDisplayed().getUnclippedBoundsInRoot().top >= header.bottom)
            compose.onNodeWithTag("panel-save").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithContentDescription("Back to playlists").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Close editor").assertIsDisplayed().performClick()
            compose.onNodeWithTag("panel-rows").performScrollToIndex(29)
            compose.onNodeWithTag("panel-row-29").assertIsDisplayed()
            assertEquals(header, compose.onNodeWithTag("panel-header").getUnclippedBoundsInRoot())
            captureWatchScreenshot("drawer-header-large-${if (night) "dark-rtl" else "light"}")
        }
        assertEquals(2, back); assertEquals(2, close); assertEquals(2, saved)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun modalSheetKeepsHeaderPinnedAndLastOptionReachable() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var open by mutableStateOf(true)
        var selected = -1
        var windowHeight = 0
        var bottomInset = 0
        compose.setContent { AppTheme(dark, AccentPreset.BLUE) {
            windowHeight = LocalWindowInfo.current.containerSize.height
            bottomInset = WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(LocalDensity.current)
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                if (open) AppModalSheet({ open = false }) {
                    PanelScaffold(Modifier.fillMaxSize(), toolbar = {
                        PanelHeader("Player settings", "Close test settings", { open = false })
                    }) { body, _ -> LazyColumn(body.testTag("sheet-options")) {
                        items(30) { index -> TextButton({ selected = index }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("sheet-option-$index")) {
                            Text("Playback option $index")
                        } }
                    } }
                }
            }
        } }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night; scale = if (night) 2f else 1f }
            compose.onNodeWithTag("sheet-options").performScrollToIndex(0)
            val body = compose.onNodeWithTag("modal-panel-body").fetchSemanticsNode().boundsInRoot
            val handle = compose.onNodeWithTag("modal-panel-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("The sheet leaves space above it", handle.top > windowHeight * .1f)
            assertEquals("The sheet remains anchored to the window bottom", (windowHeight - bottomInset).toFloat(), body.bottom, 1f)
            assertTrue("The total height includes the handle and insets", windowHeight - handle.top <= windowHeight * .85f + 1f)
            val header = compose.onNodeWithTag("panel-header").getUnclippedBoundsInRoot()
            assertTrue(compose.onNodeWithTag("sheet-option-0").getUnclippedBoundsInRoot().top >= header.bottom)
            compose.onNodeWithTag("sheet-options").performScrollToIndex(29)
            compose.onNodeWithTag("sheet-option-29").assertIsDisplayed().performClick()
            assertEquals(29, selected)
            assertEquals(header, compose.onNodeWithTag("panel-header").getUnclippedBoundsInRoot())
            compose.onNodeWithContentDescription("Close test settings").assertIsDisplayed()
            captureWatchScreenshot("drawer-modal-${if (night) "dark-large" else "light"}")
        }
        compose.onNodeWithContentDescription("Close test settings").performClick()
        compose.onNodeWithTag("modal-panel").assertDoesNotExist()
    }

    @Test fun libraryPanelKeepsBusyDismissalAndActionSemantics() {
        var busy by mutableStateOf(true)
        var open by mutableStateOf(true)
        var submitted = false
        compose.setContent { AppTheme(false, AccentPreset.BLUE) {
            if (open) LibraryPanel("Download", { open = false }, enabled = !busy, closeLabel = "Close download test",
                actions = { LibraryActionButton("Download", Modifier.testTag("panel-download"), enabled = !busy, prominent = true) { submitted = true } }) { body, _ ->
                Text("Download choices", body)
            }
        } }
        compose.onNodeWithContentDescription("Close download test").assertIsNotEnabled()
        compose.onNodeWithTag("panel-download").assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag("panel-download").assertIsDisplayed().performClick()
        assertTrue(submitted)
        compose.onNodeWithContentDescription("Close download test").performClick()
        compose.onNodeWithText("Download choices").assertDoesNotExist()
    }

}
