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
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.data.SubscriptionChannelsState
import net.wingress.mobivious.ui.SubscriptionChannelsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionsPresentationTest {
    @get:Rule val compose = createComposeRule()

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
