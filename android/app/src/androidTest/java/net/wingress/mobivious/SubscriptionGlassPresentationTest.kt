package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionGlassPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/mobivious-subscriptions-glass", "screencap -p /data/local/tmp/mobivious-subscriptions-glass/$name.png"))
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    @Test fun narrowLargeTextFeedReflowsAndKeepsItsContentBelowChrome() {
        var dark by mutableStateOf(false)
        var opaque by mutableStateOf(false)
        var query by mutableStateOf("")
        var opened = 0
        var submitted = 0
        compose.setContent { MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light, typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalReduceTransparency provides opaque) {
                BrowseGlassSurface(Modifier.width(320.dp).height(850.dp), toolbar = {
                    SubscriptionFeedToolbar(true, query, "", false, { query = it }, { submitted++ }, { query = "" }, {}, { opened++ }, {}) {}
                }) { modifier, top ->
                    LazyColumn(modifier.testTag("feed"), contentPadding = PaddingValues(top = top)) {
                        items(20) { Text("Upload $it", Modifier.fillMaxWidth().padding(20.dp).testTag("upload-$it")) }
                    }
                }
            }
        } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark = isDark; opaque = isDark }
            val header = compose.onNodeWithTag("browse-glass-toolbar").fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithTag("upload-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(first.top >= header.bottom)
            compose.onNodeWithTag("subscription-channels-button").assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("subscription-search").performTextReplacement("studio")
            compose.onNodeWithTag("subscription-search-submit").performClick()
            compose.onNodeWithText("Clear search").performClick()
            compose.onNodeWithTag("feed").performScrollToIndex(19)
            compose.onNodeWithTag("subscription-channels-button").assertIsDisplayed()
            capture(if (isDark) "feed-narrow-dark-opaque-large" else "feed-narrow-light-large")
            compose.onNodeWithTag("feed").performScrollToIndex(0)
        }
        assertEquals(2, opened); assertEquals(2, submitted); assertEquals("", query)
    }

    @Test fun directoryLastRowAndCountStayAccessibleWithLargeTextAndOpaqueGlass() {
        val channels = (0..39).map { Channel("channel-$it", "Channel $it with a long creator name") }
        var query by mutableStateOf("")
        var opened = ""
        compose.setContent { MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalReduceTransparency provides true) {
                Surface(Modifier.width(320.dp).height(850.dp)) {
                    SubscriptionChannelsScreen(SubscriptionChannelsState(loaded = true, channels = channels, query = query), "", true,
                        rememberLazyListState(), { query = it }, {}, { opened = it },
                        header = { BrowsePageTitle("Channels", back = {}) })
                }
            }
        } }
        compose.onNodeWithTag("subscription-channel-count").assertTextEquals("40 channels")
        compose.onNodeWithTag("subscription-channel-list").performScrollToNode(hasTestTag("subscription-channel-channel-39"))
        val row = compose.onNodeWithTag("subscription-channel-channel-39").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("browse-glass-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(row.top >= header.bottom)
        compose.onNodeWithTag("subscription-channel-channel-39").performClick()
        assertEquals("channel-39", opened)
        compose.onNodeWithTag("subscription-channel-search").performTextInput("39")
        compose.onNodeWithTag("subscription-channel-count").assertTextEquals("1 channel")
        compose.onNodeWithTag("subscription-channel-search-submit").performClick()
        capture("directory-narrow-dark-opaque-large-thin")
    }

    @Test fun channelTabsScrollAndSortMenusKeepTheirSelectedSemantics() {
        val channel = Channel("creator", "Creator", tabs = listOf("videos", "streams", "playlists", "posts", "channels"))
        var tab by mutableStateOf(ChannelTab.VIDEOS)
        var sort by mutableStateOf(ChannelSort.NEWEST)
        compose.setContent { MaterialTheme(colorScheme = Liquid.light, typography = Liquid.typography) {
            BrowseGlassSurface(Modifier.width(320.dp).height(800.dp), toolbar = {
                ChannelBrowseToolbar(channel, tab, sort, "last", "", "", false, {}, {}, {}, {}, {}, { tab = it }, { sort = it }, {})
            }) { modifier, top -> LazyColumn(modifier, contentPadding = PaddingValues(top = top)) {
                item { ChannelHeader(channel, "", false, false) {} }
            } }
        } }
        compose.onNodeWithTag("channel-tab-streams").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("channel-sort").performClick()
        compose.onNodeWithTag("channel-sort-oldest").performClick()
        assertEquals(ChannelSort.OLDEST, sort)
        compose.onNodeWithTag("channel-tab-channels").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("channel-sort").assertDoesNotExist()
        capture("channel-narrow-tabs")
    }
}
