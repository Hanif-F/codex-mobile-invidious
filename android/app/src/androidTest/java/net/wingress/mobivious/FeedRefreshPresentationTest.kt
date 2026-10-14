package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.ui.FeedRefreshContainer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class FeedRefreshPresentationTest {
    @get:Rule val compose = createComposeRule()
    private var refreshing by mutableStateOf(false)
    private var loading by mutableStateOf(false)
    private var enabled by mutableStateOf(true)
    private var requests = 0

    private fun content() = compose.setContent {
        MaterialTheme {
            val list = rememberLazyListState()
            FeedRefreshContainer(enabled, refreshing, loading, "Popular", {
                requests++; refreshing = true; loading = true
            }, Modifier.width(320.dp).height(500.dp)) {
                LazyColumn(Modifier.fillMaxSize().testTag("refresh-test-list"), state = list, overscrollEffect = null) {
                    items(40) { Text("Video $it", Modifier.fillMaxWidth().height(80.dp)) }
                }
            }
        }
    }

    @Test fun partialPullShowsProgressAndReleasingBelowThresholdCancels() {
        content()
        compose.onNodeWithTag("refresh-test-list").performTouchInput {
            down(Offset(centerX, height * .1f))
            moveTo(Offset(centerX, height * .3f), 500)
        }
        val progress = compose.onNodeWithTag("feed-refresh-indicator").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("The indicator must follow a partial pull", progress > 0f && progress < 1f)
        compose.onNodeWithTag("refresh-test-list").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0, requests)
        assertEquals(0f, compose.onNodeWithTag("feed-refresh-indicator").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, .01f)
    }

    @Test fun thresholdPullRefreshesOnceAndStopsWhenTheRequestFinishes() {
        content()
        compose.onNodeWithTag("refresh-test-list").performTouchInput { swipeDown(durationMillis = 600) }
        compose.onNodeWithTag("feed-refresh-container").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "Refreshing Popular"))
        assertEquals(1, requests)
        compose.onNodeWithTag("refresh-test-list").performTouchInput { swipeDown(durationMillis = 600) }
        assertEquals(1, requests)
        compose.runOnIdle { refreshing = false; loading = false }
        compose.waitForIdle()
        assertEquals(0f, compose.onNodeWithTag("feed-refresh-indicator").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, .01f)
    }

    @Test fun scrollingTowardTheTopDoesNotRefreshUntilThereIsATopPull() {
        content()
        compose.onNodeWithTag("refresh-test-list").performScrollToIndex(20)
        compose.onNodeWithTag("refresh-test-list").performTouchInput { swipeDown(durationMillis = 600) }
        assertEquals(0, requests)
        compose.onNodeWithTag("refresh-test-list").performScrollToIndex(0)
        assertEquals(0, requests)
        compose.onNodeWithTag("refresh-test-list").performTouchInput { swipeDown(durationMillis = 600) }
        assertEquals(1, requests)
    }

    @Test fun accessibilityRefreshSharesTheLoadingGuardAndDisabledFeedsHaveNoAction() {
        content()
        compose.onNodeWithTag("feed-refresh-container").performCustomAccessibilityActionWithLabel("Refresh Popular")
        compose.onNodeWithTag("refresh-test-list").performTouchInput { swipeDown(durationMillis = 600) }
        assertEquals(1, requests)
        compose.runOnIdle { enabled = false; refreshing = false; loading = false }
        compose.onNodeWithTag("feed-refresh-container").assertDoesNotExist()
        compose.onNodeWithTag("refresh-test-list").performTouchInput { swipeDown(durationMillis = 600) }
        assertEquals(1, requests)
    }
}
