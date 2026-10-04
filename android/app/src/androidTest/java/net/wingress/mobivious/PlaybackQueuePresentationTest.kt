package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
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

/** Exercises the actual queue viewport and rows without a player or upstream requests. */
@RunWith(AndroidJUnit4::class)
class PlaybackQueuePresentationTest {
    @get:Rule val compose = createComposeRule()
    private val entries = (0..19).map { QueueOccurrence.source("PLqueue", Video("testvideo01", "Occurrence $it", "Creator", duration = 120), it) }
    private val state = mutableStateOf(PlaybackQueueSnapshot(token = "queue", source = QueueSource("PLqueue", "Playlist", 20),
        items = entries, currentKey = entries[0].key, explicitQueue = true))
    private val expanded = mutableStateOf(true)
    private var selected: String? = null
    private var removed: String? = null
    private var retried = 0
    private var more = 0
    private lateinit var outer: LazyListState

    private fun content(width: Int = 390, fontScale: Float = 1f, dark: Boolean = false, thin: Boolean = true) {
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                outer = rememberLazyListState()
                Surface(Modifier.width(width.dp).fillMaxHeight()) {
                    LazyColumn(Modifier.testTag("outer"), state = outer) {
                        item { Text("Video metadata", Modifier.height(48.dp)) }
                        item { PlaybackQueueContent(state.value, expanded.value, false,
                            toggle = { expanded.value = !expanded.value }, previous = {}, next = {}, repeat = {},
                            retry = { retried++ }, more = { more++ }) { entry ->
                            QueueVideoRow(entry, entry.key == state.value.currentKey, "", thin,
                                VideoIndicator(true, 25, .25f), QueueRules.eligible(entry, false),
                                hiddenMember = entry.video.membersOnly, play = { selected = entry.key }, channel = {}) {
                                OverflowMenu("Queue item actions", entry.key, Modifier.testTag("actions-${entry.key}")) { close ->
                                    DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { close(); removed = entry.key })
                                }
                            }
                        } }
                        item { Text("Up next", Modifier.testTag("up-next")) }
                    }
                }
            }
        } }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/mobivious-queue-screenshots",
            "screencap -p /data/local/tmp/mobivious-queue-screenshots/$name.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    @Test fun implicitPlaybackNeverShowsAQueueRegardlessOfItemCount() {
        state.value = state.value.copy(explicitQueue = false)
        content()
        compose.onNodeWithTag("playback-queue").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(items = listOf(entries[0]), explicitQueue = true) }
        compose.onNodeWithTag("playback-queue-items").assertExists()
        compose.runOnIdle { state.value = state.value.copy(token = "") }
        compose.onNodeWithTag("playback-queue").assertDoesNotExist()
    }

    @Test fun followingDuplicateOccurrencesOnlyScrollsTheBoundedInnerList() {
        content()
        compose.onNodeWithTag("playback-queue-items").assertHeightIsEqualTo(288.dp)
        val outerPosition = compose.runOnIdle { outer.firstVisibleItemIndex to outer.firstVisibleItemScrollOffset }
        compose.runOnIdle { state.value = state.value.copy(currentKey = entries[18].key) }
        compose.onNodeWithTag("queue-occurrence-${entries[18].key}").assertIsDisplayed().assertIsSelected()
        assertEquals(outerPosition, compose.runOnIdle { outer.firstVisibleItemIndex to outer.firstVisibleItemScrollOffset })
        compose.onNodeWithTag("queue-occurrence-${entries[19].key}").performClick()
        assertEquals(entries[19].key, selected)
        compose.onNodeWithTag("outer").performScrollToNode(hasTestTag("up-next"))
        compose.onNodeWithTag("up-next").assertIsDisplayed()
    }

    @Test fun collapsedQueueStaysCollapsedOnAdvanceAndReopensAtCurrentOccurrence() {
        content()
        compose.onNodeWithTag("playback-queue-header").performClick()
        compose.runOnIdle { state.value = state.value.copy(currentKey = entries[15].key) }
        compose.onNodeWithTag("playback-queue-items").assertDoesNotExist()
        compose.onNodeWithTag("playback-queue-header").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithTag("playback-queue-header").performClick()
        compose.onNodeWithTag("queue-occurrence-${entries[15].key}").assertIsDisplayed().assertIsSelected()
    }

    @Test fun loadingAndRecoveryRemainVisibleWithoutDetailsAndWhileCollapsed() {
        expanded.value = false
        state.value = state.value.copy(details = null, loading = true, error = "Playback error", sourceError = "Queue error")
        content()
        compose.onNodeWithTag("queue-loading").assertIsDisplayed()
        compose.onNodeWithText("Retry playback").performClick()
        compose.onNodeWithText("Retry queue").performClick()
        assertEquals(1, retried); assertEquals(1, more)
        compose.runOnIdle { state.value = state.value.copy(loading = false, error = null, sourceError = null, sourceComplete = false); expanded.value = true }
        compose.onNodeWithTag("outer").performScrollToNode(hasText("Load more queue items"))
        compose.onNodeWithText("Load more queue items").performClick()
        assertEquals(2, more)
    }

    private fun layout(width: Int, fontScale: Float, dark: Boolean) {
        val entry = entries[0].copy(video = entries[0].video.copy(title = "A long video title that wraps across this compact queue row",
            author = "A long creator name that must fit beside the item actions"), removed = true)
        state.value = state.value.copy(items = listOf(entry), currentKey = entry.key, source = QueueSource("RDqueue", "Mix"))
        content(width, fontScale, dark, thin = false)
        compose.onNodeWithTag("outer").performScrollToNode(hasTestTag("playback-queue-items"))
        compose.onNodeWithTag("queue-occurrence-${entry.key}").assertIsSelected()
        compose.onNodeWithText("Removed from queue").assertExists()
        compose.onNodeWithTag("actions-${entry.key}").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Remove from queue").performClick()
        assertEquals(entry.key, removed); assertNull(selected)
        compose.onNodeWithTag("outer").performScrollToNode(hasTestTag("queue-repeat-ALL"))
        compose.onNodeWithTag("queue-repeat-ALL").assertIsNotEnabled()
        screenshot("presentation-${width}-${if (dark) "dark" else "light"}")
    }
    @Test fun narrowDarkLargeFontLayoutKeepsActionsAccessible() = layout(320, 2f, true)
    @Test fun light390dpLayoutKeepsActionsAccessible() = layout(390, 1.3f, false)
}
