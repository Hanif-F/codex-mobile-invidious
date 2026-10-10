package net.wingress.mobivious

import androidx.compose.foundation.layout.*
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
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassControlPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/mobivious-glass-audit", "screencap -p /data/local/tmp/mobivious-glass-audit/$name.png"))
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    @Test fun subscriptionStateAndExplicitActionRemainClearWithLargeText() {
        var dark by mutableStateOf(false)
        var busy by mutableStateOf(false)
        var known by mutableStateOf(true)
        var subscribed by mutableStateOf(true)
        var changes = 0
        compose.setContent { MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light, typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalReduceTransparency provides dark) {
                LibraryControlScene(Modifier.width(320.dp).height(850.dp)) {
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
        compose.setContent { MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalReduceTransparency provides true) {
                BrowseGlassSurface(Modifier.width(320.dp).height(850.dp), softEdge = true, toolbar = {
                    LibraryToolbar("History & library", back = { backed = true }, statusInset = false,
                        backLabel = "Back from History & library", controls = {
                            LiquidSegments(listOf("Popular", "Trending"), selected, { it }, Modifier.fillMaxWidth(), tag = { "feed-$it" }) { selected = it }
                        })
                }) { modifier, top -> LazyColumn(modifier.testTag("rows"), contentPadding = PaddingValues(top = top, bottom = 32.dp)) {
                    items(30) { Text("Preference $it", Modifier.fillMaxWidth().padding(20.dp).testTag("preference-$it")) }
                } }
            }
        } }
        val header = compose.onNodeWithTag("browse-glass-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithTag("preference-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top >= header.bottom)
        compose.onNodeWithTag("feed-Trending").assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.onNodeWithTag("rows").performScrollToIndex(29)
        compose.onNodeWithTag("preference-29").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back from History & library").assertIsDisplayed().performClick()
        assertTrue(backed)
        capture("settings-toolbar-large-dark")
    }
}
