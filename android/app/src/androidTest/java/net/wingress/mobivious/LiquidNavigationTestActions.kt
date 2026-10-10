package net.wingress.mobivious

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Follow the product's navigation topology instead of assuming every tab is always visible. */
internal fun ComposeTestRule.selectDiscoveryFeed(feed: String) {
    if (onAllNodesWithContentDescription("Close search").fetchSemanticsNodes().isNotEmpty())
        onNodeWithContentDescription("Close search").performClick()
    if (onAllNodesWithTag("discover-$feed").fetchSemanticsNodes().isEmpty())
        onNodeWithTag("navigation-Discover").performClick()
    onNodeWithTag("discover-$feed").performClick()
}

internal fun ComposeTestRule.openAppSettings() {
    if (onAllNodesWithTag("navigation-You").fetchSemanticsNodes().isEmpty() &&
        onAllNodesWithTag("watch-content").fetchSemanticsNodes().isNotEmpty()) {
        navigateBack()
        waitUntil(5_000) { onAllNodesWithTag("navigation-You").fetchSemanticsNodes().isNotEmpty() }
    }
    if (onAllNodesWithContentDescription("Close search").fetchSemanticsNodes().isNotEmpty())
        onNodeWithContentDescription("Close search").performClick()
    if (onAllNodesWithTag("global-settings").fetchSemanticsNodes().isEmpty())
        onNodeWithTag("navigation-You").performClick()
    onNodeWithTag("global-settings").performClick()
}

internal fun ComposeTestRule.openGlobalSearch() {
    if (onAllNodesWithTag("main-search").fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag("main-search").performClick()
    else onNodeWithTag("global-search").performClick()
}

internal fun ComposeTestRule.navigateBack() {
    when {
        onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() -> onNodeWithContentDescription("Back").performClick()
        onAllNodesWithContentDescription("Return to watch page").fetchSemanticsNodes().isNotEmpty() -> onNodeWithContentDescription("Return to watch page").performClick()
        else -> {
            if (onAllNodesWithContentDescription("Minimize player").fetchSemanticsNodes().isEmpty())
                onNodeWithTag("player-gestures").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            onNodeWithContentDescription("Minimize player").performClick()
        }
    }
}

/** Floating chrome is outside the list viewport; bring a touch target clear of that chrome. */
internal fun ComposeTestRule.revealInBrowse(matcher: SemanticsMatcher) {
    val list = onNodeWithTag("browse-video-list")
    list.performScrollToNode(matcher)
    val target = onNode(matcher).fetchSemanticsNode().boundsInRoot
    val chrome = onAllNodes(hasTestTag("mini-player-preview") or hasTestTag("navigation-Discover") or hasTestTag("global-search"))
        .fetchSemanticsNodes().minOfOrNull { it.boundsInRoot.top }
    if (chrome != null && target.bottom > chrome - 48f)
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, target.bottom - chrome + 48f) }
    waitForIdle()
}
