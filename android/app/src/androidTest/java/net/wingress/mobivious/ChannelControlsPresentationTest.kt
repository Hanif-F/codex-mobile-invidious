package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelControlsPresentationTest {
    @get:Rule val compose = createComposeRule()
    private var width by mutableIntStateOf(390)
    private var fontScale by mutableFloatStateOf(1f)
    private var rtl by mutableStateOf(false)
    private var selected by mutableStateOf(ChannelSort.NEWEST)
    private var submissions = 0
    private val channel = Channel("creator", "Creator", tabs = listOf("videos", "streams", "playlists"))

    @Composable private fun Controls() {
        AppTheme(false, AccentPreset.BLUE) {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                var draft by rememberSaveable { mutableStateOf("") }
                var submitted by rememberSaveable { mutableStateOf("") }
                val focus = LocalFocusManager.current
                Column(Modifier.width(width.dp)) {
                    ChannelBrowseControls(channel, ChannelTab.VIDEOS, selected, "last", draft, submitted,
                        { draft = it }, { submitted = draft.trim(); submissions++ }, { draft = ""; submitted = "" }, {}, { selected = it }, {})
                    TextButton(onClick = { focus.clearFocus() }) { Text("Other action") }
                }
            }
        }
    }

    @Test fun emptySearchCollapsesOnBlurButDraftAndSubmittedQueriesRemain() {
        compose.setContent { Controls() }
        compose.onNodeWithTag("channel-search").assertDoesNotExist()
        compose.onNodeWithTag("channel-search-expand").assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("channel-search").assertIsFocused().assertWidthIsAtLeast(160.dp)
        compose.onNodeWithText("Other action").performClick()
        compose.onNodeWithTag("channel-search").assertDoesNotExist()
        assertEquals(0, submissions)

        compose.onNodeWithTag("channel-search-expand").performClick()
        compose.onNodeWithTag("channel-search").performTextInput("studio")
        compose.onNodeWithText("Other action").performClick()
        compose.onNodeWithTag("channel-search").assertTextContains("studio").assertIsNotFocused()
        compose.onNodeWithTag("channel-search-submit").performClick()
        assertEquals(1, submissions)
        compose.onNodeWithTag("channel-search").assertIsDisplayed()
        compose.onNodeWithTag("channel-sort").assertDoesNotExist()
        compose.onNodeWithTag("channel-search").performTextReplacement("")
        compose.onNodeWithText("Other action").performClick()
        compose.onNodeWithTag("channel-search").assertExists()
        compose.onNodeWithTag("channel-search").performImeAction()
        compose.onNodeWithTag("channel-search").assertDoesNotExist()
        assertEquals(2, submissions)
    }

    @Test fun sortingAdaptsWhileFocusedAndKeepsTheQueryAndSelection() {
        compose.setContent { Controls() }
        compose.onNodeWithTag("channel-sort-pills").assertExists()
        compose.onNodeWithTag("channel-sort-oldest").performClick().assertIsSelected()
        compose.onNodeWithTag("channel-search-expand").performClick()
        compose.onNodeWithTag("channel-search").performTextInput("draft")
        compose.onNodeWithTag("channel-sort-pills").assertDoesNotExist()
        compose.onNodeWithTag("channel-sort").assertIsDisplayed()
        compose.runOnIdle { width = 700 }
        compose.onNodeWithTag("channel-search").assertIsFocused().assertTextContains("draft")
        compose.onNodeWithTag("channel-sort-oldest").assertIsSelected()
        compose.runOnIdle { width = 320; fontScale = 2f; rtl = true }
        compose.onNodeWithTag("channel-search").assertWidthIsAtLeast(160.dp).assertIsFocused().assertTextContains("draft")
        compose.onNodeWithTag("channel-sort").assertWidthIsEqualTo(48.dp).performClick()
        compose.onNodeWithTag("channel-sort-popular").performClick()
        assertEquals(ChannelSort.POPULAR, selected)
        compose.onNodeWithTag("channel-search").assertTextContains("draft")
    }

    @Test fun restoredDraftStaysExpandedAndClearReturnsToTheSearchIcon() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Controls() }
        compose.onNodeWithTag("channel-search-expand").performClick()
        compose.onNodeWithTag("channel-search").performTextInput("retained draft")
        compose.onNodeWithText("Other action").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("channel-search").assertTextContains("retained draft")
        assertEquals(0, submissions)
        compose.onNodeWithText("Clear search").performClick()
        compose.onNodeWithTag("channel-search-expand").assertIsDisplayed()
        compose.onNodeWithTag("channel-search").assertDoesNotExist()
    }

    @Test fun sortPillsUseSmallerLabelsThanPageTabsAndKeepTheirTouchTargets() {
        compose.setContent { Controls() }
        compose.onNodeWithTag("channel-sort-newest").assertHeightIsEqualTo(32.dp).assertWidthIsAtLeast(48.dp).assertIsSelected()
        val sortLabel = compose.onNodeWithTag("channel-sort-label-newest", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tabLabel = compose.onNode(hasText("Videos") and hasAnyAncestor(hasTestTag("channel-tabs")),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Sorting labels should be smaller than page labels", sortLabel.height < tabLabel.height)
        // Native chips draw at 32dp but accept taps in a 48dp target around them.
        compose.onNodeWithTag("channel-sort-oldest").performTouchInput { click(Offset(center.x, -7f)) }.assertIsSelected()
        compose.onNodeWithTag("channel-sort-newest").performClick().assertIsSelected()
        compose.onNodeWithTag("channel-sort-oldest").performTouchInput { click(Offset(center.x, height + 7f)) }.assertIsSelected()
    }
}
