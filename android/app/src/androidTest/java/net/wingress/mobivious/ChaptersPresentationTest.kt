package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
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
@OptIn(ExperimentalTestApi::class)
class ChaptersPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val chapters = (0..29).map { VideoChapter(it * 10000L, "Chapter $it · 日本語 & title") }
    private val position = mutableLongStateOf(180000)
    private val occurrence = mutableStateOf("instance:first-occurrence")
    private lateinit var panel: ChapterPanelState
    private var selected: Long? = null

    @Composable private fun Content() {
        panel = key(occurrence.value) { rememberChapterPanelState() }
        Column(Modifier.width(320.dp).height(400.dp)) {
            if (panel.open) ChaptersPanel(chapters, position.longValue, panel, true, panel::close, { selected = it; position.longValue = it }, Modifier.weight(1f))
            else ChaptersEntry(chapters, position.longValue) { panel.show(chapters, position.longValue) }
        }
    }

    @Test fun openingRevealsCurrentChapterAndSeekingKeepsThePanelOpen() {
        compose.setContent { MaterialTheme { Content() } }
        compose.onNodeWithTag("chapters-entry").performClick()
        compose.onNodeWithTag("chapter-180000").assertIsDisplayed().assertIsSelected().performClick()
        assertEquals(180000L, selected)
        compose.onNodeWithTag("chapters-panel").assertExists()
        compose.onNodeWithTag("chapters-list").performScrollToIndex(5)
        val index = compose.runOnIdle { panel.list.firstVisibleItemIndex }
        compose.runOnIdle { position.longValue = 250000 }
        assertEquals(index, compose.runOnIdle { panel.list.firstVisibleItemIndex })
        compose.onNodeWithContentDescription("Close chapters").performClick()
        compose.onNodeWithTag("chapters-entry").performClick()
        compose.onNodeWithTag("chapter-250000").assertIsDisplayed().assertIsSelected()
    }

    @Test fun restorationKeepsOpenStateAndScrollButNewOccurrencesResetIt() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { Content() } }
        compose.onNodeWithTag("chapters-entry").performClick()
        compose.onNodeWithTag("chapters-list").performScrollToIndex(3)
        val scroll = compose.runOnIdle { panel.list.firstVisibleItemIndex to panel.list.firstVisibleItemScrollOffset }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("chapters-panel").assertExists()
        assertEquals(scroll, compose.runOnIdle { panel.list.firstVisibleItemIndex to panel.list.firstVisibleItemScrollOffset })
        // Distinct queue occurrences can contain the very same video and chapters.
        compose.runOnIdle { occurrence.value = "instance:second-occurrence" }
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
        compose.runOnIdle { panel.show(chapters, position.longValue); occurrence.value = "another-instance:second-occurrence" }
        compose.onNodeWithTag("chapters-panel").assertDoesNotExist()
    }

    @Test fun chapterAndSponsorLabelsStaySeparateWithAccessibleLongRtlTitles() {
        val longTitle = "عنوان طويل 日本語 ".repeat(15)
        val entries = listOf(VideoChapter(0, longTitle), VideoChapter(10000, "Second"))
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                val state = rememberChapterPanelState()
                Column(Modifier.width(320.dp).height(600.dp)) {
                    PlayerChapterTitle(entries, 5000, listOf("Sponsor", "Intro")) { state.show(entries, 5000) }
                    ChaptersPanel(entries, 5000, state, true, state::close, { selected = it }, Modifier.weight(1f))
                }
            }
        } }
        compose.onNodeWithTag("player-current-chapter").assertTextEquals(longTitle)
        compose.onNodeWithTag("player-seek-sponsor-labels").assertTextEquals("Sponsor, Intro")
        compose.onNodeWithTag("chapter-0").assertIsSelected().assert(hasClickAction())
        compose.onNodeWithTag("chapters-list").performScrollToIndex(1)
        compose.onNodeWithTag("chapter-10000").assertIsDisplayed().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("chapter-10000").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        assertEquals(10000L, selected)
    }

    @Test fun unavailableChaptersHideTheEntryAndLoadingDisablesSeekActions() {
        compose.setContent { MaterialTheme {
            val state = rememberChapterPanelState()
            Column(Modifier.height(300.dp)) {
                ChaptersEntry(emptyList(), 0) { fail("No chapter action should exist") }
                ChaptersPanel(chapters, 0, state, false, state::close, { fail("Disabled seek") }, Modifier.weight(1f))
            }
        } }
        compose.onNodeWithTag("chapters-entry").assertDoesNotExist()
        compose.onNodeWithTag("chapter-0").assertIsNotEnabled()
    }
}
