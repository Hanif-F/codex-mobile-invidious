package net.wingress.mobivious

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.ui.PreferenceSaveStatus

/** Follow the product's navigation topology instead of assuming every tab is always visible. */
internal fun ComposeTestRule.selectFeed(feed: String): SemanticsNodeInteraction {
    if(onAllNodesWithContentDescription("Close search").fetchSemanticsNodes().isNotEmpty())
        onNodeWithContentDescription("Close search").performClick()
    waitUntil(10_000) { onAllNodesWithTag("navigation-$feed").fetchSemanticsNodes().isNotEmpty() }
    return onNodeWithTag("navigation-$feed").performClick()
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

/** Leave a preference page after its automatic writes complete; entity editors keep submit buttons. */
internal fun ComposeTestRule.finishAutomaticPreferencePage() {
    var activity: MainActivity?=null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
        activity?.model?.flushPreferenceEdits()
    }
    val model=checkNotNull(activity).model
    waitUntil(30_000) {model.preferenceSaves.edits.value.values.none {it.status in listOf(PreferenceSaveStatus.PENDING,PreferenceSaveStatus.DEBOUNCING)}}
    if(model.preferenceSaves.edits.value.values.any {it.status in listOf(PreferenceSaveStatus.FAILED,PreferenceSaveStatus.INVALID)}) return
    val description=onAllNodes(hasContentDescription("Back from ",substring=true)).fetchSemanticsNodes()
        .flatMap {it.config[SemanticsProperties.ContentDescription]}.first {it!="Back from Settings"}
    onNodeWithContentDescription(description).performClick()
}

internal fun ComposeTestRule.flushChatPreferenceFields() {
    onNodeWithTag("chat-words").performImeAction()
    onNodeWithTag("chat-timing").performImeAction()
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

/** Bring a list target inside the viewport between the toolbar and navigation. */
internal fun ComposeTestRule.revealInBrowse(matcher: SemanticsMatcher) {
    val list = onNodeWithTag("browse-video-list")
    list.performScrollToNode(matcher)
    val target = onNode(matcher).fetchSemanticsNode().boundsInRoot
    val chrome = onAllNodes(hasTestTag("mini-player-preview") or hasTestTag("navigation-Popular"))
        .fetchSemanticsNodes().minOfOrNull { it.boundsInRoot.top }
    if (chrome != null && target.bottom > chrome - 48f)
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, target.bottom - chrome + 48f) }
    val toolbar = onAllNodesWithTag("app-toolbar").fetchSemanticsNodes().maxOfOrNull { it.boundsInRoot.bottom }
    val revealed = onNode(matcher).fetchSemanticsNode().boundsInRoot
    if (toolbar != null && revealed.top < toolbar + 24f)
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, revealed.top - toolbar - 24f) }
    waitForIdle()
}

/** Paragraph width can retain its available constraint after wrap-content sizing.
 * Check every rendered character instead of treating that unused width as clipping. */
internal fun assertLabelGlyphsFit(layout: androidx.compose.ui.text.TextLayoutResult, label: String) {
    for (index in layout.layoutInput.text.indices) {
        if (layout.layoutInput.text[index].isWhitespace()) continue
        val box = layout.getBoundingBox(index)
        org.junit.Assert.assertTrue("$label character $index clips: $box in ${layout.size}",
            box.left >= -1f && box.top >= -1f && box.right <= layout.size.width + 1f && box.bottom <= layout.size.height + 1f)
        org.junit.Assert.assertFalse("$label is ellipsized",layout.isLineEllipsized(layout.getLineForOffset(index)))
    }
}
