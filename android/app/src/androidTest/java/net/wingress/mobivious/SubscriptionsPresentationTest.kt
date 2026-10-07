package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.data.SubscriptionChannelsState
import net.wingress.mobivious.data.SubscriptionSort
import net.wingress.mobivious.data.SubscriptionStats
import net.wingress.mobivious.ui.SubscriptionChannelsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionsPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sortingAndDetailsFitNarrowScreensWithLargeTextAndProduceScreenshots() {
        var dark by mutableStateOf(false)
        var width by mutableStateOf(320.dp)
        var fontScale by mutableStateOf(1f)
        var thin by mutableStateOf(false)
        var state by mutableStateOf(SubscriptionChannelsState(loaded = true,
            channels = listOf(Channel("a", "A creator with a long channel name that wraps"), Channel("b", "Unknown upload")),
            stats = mapOf("a" to SubscriptionStats(System.currentTimeMillis() / 1000 - 2 * 86400, 18, 6, 4.0),
                "b" to SubscriptionStats(null, 1, 1, 0.0))))
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Surface(Modifier.width(width).height(600.dp)) {
                    SubscriptionChannelsScreen(state, "https://instance.test", thin, rememberLazyListState(),
                        { state = state.copy(query = it) }, {}, {}, { state = state.copy(sort = it) })
                }
            }
        } }
        fun capture(name: String) {
            compose.waitForIdle()
            // Connected-test cleanup removes app storage; preserve review artifacts outside it.
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            listOf("mkdir -p /data/local/tmp/mobivious-subscription-sorting", "screencap -p /data/local/tmp/mobivious-subscription-sorting/$name").forEach {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream -> stream.readBytes() }
            }
        }
        for (size in listOf(320.dp, 390.dp)) for (isDark in listOf(false, true)) {
            compose.runOnIdle { width = size; dark = isDark; fontScale = if (isDark) 1.4f else 1f; thin = isDark; state = state.copy(sort = SubscriptionSort.RELEVANCE) }
            compose.onNodeWithTag("subscription-sort").assertIsDisplayed()
            compose.onNodeWithText("6 videos watched in the last 90 days").assertIsDisplayed()
            compose.onNodeWithTag("subscription-sort-help").assertIsDisplayed()
            val screen = compose.onNodeWithTag("subscription-channel-screen").fetchSemanticsNode().boundsInRoot
            val button = compose.onNodeWithTag("subscription-sort").fetchSemanticsNode().boundsInRoot
            assertTrue(button.left >= screen.left && button.right <= screen.right)
            capture("${size.value.toInt()}-${if (isDark) "dark-large-thin" else "light"}-relevance.png")
            compose.onNodeWithTag("subscription-sort").performClick()
            compose.onNodeWithTag("subscription-sort-option-most_watched").assertIsEnabled().performClick()
            compose.onNodeWithText("18 videos watched all time").assertIsDisplayed()
            compose.onNodeWithTag("subscription-sort-help").assertDoesNotExist()
            capture("${size.value.toInt()}-${if (isDark) "dark-large-thin" else "light"}-most-watched.png")
            if (thin) compose.onNodeWithTag("subscription-channel-avatar-a", true).assertDoesNotExist()
            else compose.onNodeWithTag("subscription-channel-avatar-a", true).assertExists()
        }
    }

    @Test fun narrowLightAndDarkDirectoriesKeepSearchPinnedAndRowsAccessible() {
        var dark by mutableStateOf(false)
        var width by mutableStateOf(320.dp)
        var fontScale by mutableStateOf(1f)
        var opened = ""
        val state = SubscriptionChannelsState(loaded = true, channels = (0 until 40).map {
            Channel("channel-$it", "Channel %02d with a long name".format(it))
        })
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Surface(Modifier.width(width).height(500.dp)) {
                    SubscriptionChannelsScreen(state, "https://instance.test", false, rememberLazyListState(), {}, {}, { opened = it })
                }
            }
        } }
        for (isDark in listOf(false, true)) for (size in listOf(320.dp, 390.dp)) {
            compose.runOnIdle { dark = isDark; width = size; fontScale = if (isDark) 1.4f else 1f }
            compose.onNodeWithTag("subscription-channel-list").performScrollToNode(hasTestTag("subscription-channel-channel-39"))
            compose.onNodeWithTag("subscription-channel-search").assertIsDisplayed()
            compose.onNodeWithContentDescription("Refresh channels").assertIsDisplayed()
            compose.onNodeWithTag("subscription-channel-channel-39").assertHasClickAction().performClick()
            assertEquals("channel-39", opened)
            compose.onNodeWithTag("subscription-channel-avatar-channel-39", true).assertWidthIsEqualTo(40.dp)
        }
    }

    @Test fun liveNameSearchAndClearWorkInThinModeAndEmptyStatesStayDistinct() {
        var state by mutableStateOf(SubscriptionChannelsState(loaded = true,
            channels = listOf(Channel("a", "Alpha"), Channel("b", "Beta"))))
        compose.setContent { MaterialTheme { Surface(Modifier.width(320.dp).height(500.dp)) {
            SubscriptionChannelsScreen(state, "https://instance.test", true, rememberLazyListState(),
                { state = state.copy(query = it) }, {}, {})
        } } }
        compose.onNodeWithTag("subscription-channel-search").performTextInput("bEt")
        compose.onNodeWithTag("subscription-channel-a").assertDoesNotExist()
        compose.onNodeWithTag("subscription-channel-b").assertIsDisplayed()
        compose.onNodeWithTag("subscription-channel-avatar-b", true).assertDoesNotExist()
        compose.onNodeWithTag("subscription-channel-search").performTextReplacement("missing")
        compose.onNodeWithText("No matching channels").assertIsDisplayed()
        compose.onNodeWithText("Clear search").performClick()
        compose.onNodeWithTag("subscription-channel-a").assertExists()
        compose.runOnIdle { state = state.copy(channels = emptyList()) }
        compose.onNodeWithText("No subscribed channels").assertIsDisplayed()
        compose.onNodeWithText("No matching channels").assertDoesNotExist()
    }
}
