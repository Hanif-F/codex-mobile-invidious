package net.wingress.mobivious

import net.wingress.mobivious.ui.WatchPlayerResizeState
import org.junit.Assert.*
import org.junit.Test

class WatchPlayerScrollTest {
    @Test fun first24DpScrollTheListAndTheNext96DpResizeContinuously() {
        val state = WatchPlayerResizeState()
        assertEquals(0f, state.consume(12f, 0f), .001f)
        assertEquals(0f, state.consume(12f, 12f), .001f)
        assertEquals(24f, state.consume(24f, 24f), .001f)
        assertEquals(.25f, state.progress, .001f)
        state.consume(24f, 24f)
        assertEquals(.5f, state.progress, .001f)
        // Only the remaining resize distance is consumed; the rest goes to browsing.
        assertEquals(48f, state.consume(200f, 24f), .001f)
        assertEquals(1f, state.progress, .001f)
        assertEquals(0f, state.consume(200f, null), .001f)
    }

    @Test fun crossingTheTopRegionInOneScrollLeavesTheCorrectRemainderForTheList() {
        val state = WatchPlayerResizeState()
        assertEquals(48f, state.consume(72f, 0f), .001f)
        assertEquals(.5f, state.progress, .001f)
        assertEquals(-48f, state.consume(-72f, 48f), .001f)
        assertEquals(0f, state.progress, .001f)
    }

    @Test fun upwardBrowsingOnlyExpandsWhenReturningNearTheTop() {
        val state = WatchPlayerResizeState(96f)
        assertEquals(0f, state.consume(-100f, null), .001f)
        assertEquals(0f, state.consume(-40f, 100f), .001f)
        assertEquals(1f, state.progress, .001f)
        assertEquals(-24f, state.consume(-60f, 60f), .001f)
        assertEquals(.75f, state.progress, .001f)
        assertEquals(-48f, state.consume(-48f, 24f), .001f)
        assertEquals(.25f, state.progress, .001f)
        assertEquals(-24f, state.consume(-100f, 0f), .001f)
        assertEquals(0f, state.progress, .001f)
    }

    @Test fun viewportClampingOnAShortPageCannotRestartOrReverseResizing() {
        val state = WatchPlayerResizeState()
        state.consume(72f, 0f)
        // A larger viewport can clamp the details list back to zero without any user scrolling.
        repeat(3) { assertEquals(16f, state.consume(16f, 0f), .001f) }
        assertEquals(1f, state.progress, .001f)
        repeat(3) { assertEquals(0f, state.consume(0f, 0f), .001f) }
        assertEquals(1f, state.progress, .001f)
        state.consume(-48f, 0f)
        assertEquals(.5f, state.progress, .001f)
    }

    @Test fun largeFlingDeltasRespectTheLimitsAndRestoredProgressCanReverse() {
        val state = WatchPlayerResizeState()
        assertEquals(96f, state.consume(10_000f, 0f), .001f)
        assertEquals(-96f, state.consume(-10_000f, 0f), .001f)
        val restored = WatchPlayerResizeState.saver.restore(48f)!!
        assertEquals(.5f, restored.progress, .001f)
        restored.consume(-24f, 24f)
        assertEquals(.25f, restored.progress, .001f)
    }
}
