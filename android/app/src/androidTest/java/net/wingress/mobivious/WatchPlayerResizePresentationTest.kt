package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.player.VideoGeometry
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real nested scrolling and saveable state, without a decoder or network fixture. */
@RunWith(AndroidJUnit4::class)
class WatchPlayerResizePresentationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var resize: WatchPlayerResizeState
    private lateinit var list: LazyListState
    private var density = 1f
    private val occurrence = mutableStateOf("first")
    private val visible = mutableStateOf(true)
    private val comments = mutableStateOf(false)
    private val windowHeight = mutableStateOf(700.dp)

    @Composable private fun Content(short: Boolean = false, wide: Boolean = false) {
        MaterialTheme {
            resize = key(occurrence.value) { rememberWatchPlayerResizeState() }
            list = key(occurrence.value) { rememberLazyListState() }
            density = LocalDensity.current.density
            if (visible.value) BoxWithConstraints(Modifier.width(400.dp).height(windowHeight.value)) {
                val geometry = if (wide) VideoGeometry("wide", 16, 9) else VideoGeometry("portrait", 9, 16)
                val expanded = geometry.embeddedHeight(maxWidth.value, maxHeight.value, false)
                val compact = geometry.embeddedHeight(maxWidth.value, maxHeight.value, false, 1f)
                val connection = rememberWatchPlayerScrollConnection(list, resize, expanded - compact, !comments.value)
                val playerHeight = geometry.embeddedHeight(maxWidth.value, maxHeight.value, comments.value, resize.progress).dp
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(playerHeight)
                        .testTag("sized-player"))
                    if (comments.value) Text("Comments", Modifier.weight(1f))
                    else LazyColumn(Modifier.weight(1f).nestedScroll(connection).testTag("details"), state = list) {
                        item { Text("Metadata", Modifier.height(if (short) 300.dp else 360.dp).testTag("metadata")) }
                        if (!short) items(20) { Text("Recommendation $it", Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    private fun height(): Float = compose.onNodeWithTag("sized-player").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
    private fun metadataTop(): Float = compose.onNodeWithTag("metadata").getUnclippedBoundsInRoot().top.value
    private fun drag(delta: Float) {
        compose.onNodeWithTag("details").performTouchInput {
            down(center); moveBy(Offset(0f, -delta * density), delayMillis = 400)
            advanceEventTime(200); up()
        }
        compose.waitForIdle()
    }

    @Test fun playerTracksAnActiveDragAndOnlyExpandsNearTheTop() {
        compose.setContent { Content() }
        val expanded = height()
        drag(20f)
        assertEquals(expanded, height(), 1f)
        compose.onNodeWithTag("details").performTouchInput {
            down(center); moveBy(Offset(0f, -60f * density), delayMillis = 400)
        }
        compose.waitForIdle()
        val partial = height()
        assertTrue(partial < expanded)
        assertTrue(resize.progress > 0f && resize.progress < 1f)
        val metadataBeforeStep = metadataTop()
        compose.onNodeWithTag("details").performTouchInput { moveBy(Offset(0f, -24f * density), delayMillis = 400) }
        compose.waitForIdle()
        assertEquals(24f, partial - height(), 1f)
        assertEquals(24f, metadataBeforeStep - metadataTop(), 1f)
        val paused = height()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(paused, height(), .01f)
        val metadata = metadataTop()
        compose.onNodeWithTag("details").performTouchInput { moveBy(Offset(0f, 12f * density), delayMillis = 400) }
        compose.waitForIdle()
        assertEquals(12f, height() - paused, 1f)
        assertEquals(12f, metadataTop() - metadata, 1f)
        compose.onNodeWithTag("details").performTouchInput { advanceEventTime(200); up() }
        drag(320f)
        assertEquals(1f, resize.progress, .001f)
        compose.runOnIdle { runBlocking { list.scrollToItem(4) } }
        drag(-40f)
        assertEquals(1f, resize.progress, .001f)
        compose.runOnIdle { runBlocking { list.scrollToItem(0) } }
        drag(-60f)
        assertTrue(resize.progress > 0f && resize.progress < 1f)
        drag(-240f)
        assertEquals(expanded, height(), 1f)
    }

    @Test fun shortPageStaysCompactAfterItsListStopsScrollingAndFlingCanExpandIt() {
        compose.setContent { Content(short = true) }
        drag(320f)
        assertEquals(1f, resize.progress, .001f)
        assertFalse(list.canScrollForward)
        val compact = height()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(compact, height(), .01f)
        compose.onNodeWithTag("details").performTouchInput { swipeDown(durationMillis = 100) }
        compose.waitForIdle()
        assertEquals(0f, resize.progress, .001f)
        assertTrue(height() > compact)
    }

    @Test fun flingContinuesIntoTheListAfterThePlayerReachesItsHeightLimit() {
        compose.setContent { Content() }
        compose.onNodeWithTag("details").performTouchInput { swipeUp(durationMillis = 150) }
        compose.waitForIdle()
        assertEquals(1f, resize.progress, .001f)
        assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset / density > 24f)
        compose.runOnIdle { runBlocking { list.scrollToItem(0, (24f * density).toInt()) } }
        compose.onNodeWithTag("details").performTouchInput { swipeDown(durationMillis = 150) }
        compose.waitForIdle()
        assertEquals(0f, resize.progress, .001f)
    }

    @Test fun commentsMinimizationAndRecreationKeepProgressButANewOccurrenceResetsIt() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        drag(72f)
        val progress = resize.progress
        val partial = height()
        compose.runOnIdle { comments.value = true }
        val compact = height()
        assertTrue(compact < partial)
        compose.runOnIdle { comments.value = false; visible.value = false }
        compose.onNodeWithTag("sized-player").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        assertEquals(partial, height(), 1f)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(progress, resize.progress, .001f)
        assertEquals(partial, height(), 1f)
        compose.runOnIdle { windowHeight.value = 600.dp }
        assertEquals(progress, resize.progress, .001f)
        assertTrue(height() < partial)
        val resizedWindow = height()
        compose.runOnIdle { occurrence.value = "next" }
        compose.waitForIdle()
        assertEquals(0f, resize.progress, .001f)
        assertTrue(height() > resizedWindow)
    }

    @Test fun smallLandscapePlayerDoesNotConsumeScrollingForAnInvisibleResize() {
        compose.setContent { Content(wide = true) }
        val natural = height()
        drag(200f)
        assertEquals(0f, resize.progress, .001f)
        assertEquals(natural, height(), .01f)
        assertTrue(list.firstVisibleItemScrollOffset > 24 || list.firstVisibleItemIndex > 0)
    }
}
