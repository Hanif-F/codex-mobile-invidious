package net.wingress.mobivious

import androidx.compose.runtime.saveable.SaverScope
import net.wingress.mobivious.ui.WatchPlayerResizeState
import org.junit.Assert.*
import org.junit.Test

class WatchPlayerScrollTest {
    @Test fun first24DpScrollTheListThenEveryConsumedDpChangesHeightByOneDp() {
        for (range in listOf(48f, 96f, 210f, 300f)) {
            val state = WatchPlayerResizeState()
            assertEquals(0f, state.consume(12f, 0f, range), .001f)
            assertEquals(0f, state.consume(12f, 12f, range), .001f)
            var resized = 0f
            repeat(2) {
                val before = state.progress * range
                val consumed = state.consume(20f, 24f, range)
                assertEquals(20f, consumed, .001f)
                assertEquals(consumed, state.progress * range - before, .001f)
                resized += consumed
            }
            assertEquals(range - resized, state.consume(1000f, 24f, range), .001f)
            assertEquals(1f, state.progress, .001f)
            assertEquals(0f, state.consume(1000f, null, range), .001f)
        }
    }

    @Test fun crossingTheTopRegionLeavesTheCorrectRemainderForTheList() {
        val state = WatchPlayerResizeState()
        assertEquals(48f, state.consume(72f, 0f, 210f), .001f)
        assertEquals(48f / 210f, state.progress, .001f)
        assertEquals(-48f, state.consume(-72f, 48f, 210f), .001f)
        assertEquals(0f, state.progress, .001f)
    }

    @Test fun upwardBrowsingOnlyExpandsWhenReturningNearTheTop() {
        val state = WatchPlayerResizeState(1f)
        assertEquals(0f, state.consume(-100f, null, 210f), .001f)
        assertEquals(0f, state.consume(-40f, 100f, 210f), .001f)
        assertEquals(-24f, state.consume(-60f, 60f, 210f), .001f)
        assertEquals(186f / 210f, state.progress, .001f)
        assertEquals(-48f, state.consume(-48f, 24f, 210f), .001f)
        assertEquals(-138f, state.consume(-1000f, 0f, 210f), .001f)
        assertEquals(0f, state.progress, .001f)
    }

    @Test fun viewportClampingCannotRestartOrReverseResizing() {
        val state = WatchPlayerResizeState()
        state.consume(72f, 0f, 210f)
        repeat(3) { assertEquals(54f, state.consume(54f, 0f, 210f), .001f) }
        assertEquals(1f, state.progress, .001f)
        repeat(3) { assertEquals(0f, state.consume(0f, 0f, 210f), .001f) }
        assertEquals(-105f, state.consume(-105f, 0f, 210f), .001f)
        assertEquals(.5f, state.progress, .001f)
    }

    @Test fun flingLimitsAndReversalsUseTheCurrentHeightRange() {
        val state = WatchPlayerResizeState()
        assertEquals(210f, state.consume(10_000f, 0f, 210f), .001f)
        assertEquals(-210f, state.consume(-10_000f, 0f, 210f), .001f)
        state.consume(105f, 24f, 210f)
        assertEquals(.5f, state.progress, .001f)
        assertEquals(-25f, state.consume(-25f, 24f, 100f), .001f)
        assertEquals(.25f, state.progress, .001f)
        assertEquals(0f, state.consume(20f, 24f, 0f), .001f)
        assertEquals(.25f, state.progress, .001f)
        assertEquals(30f, state.consume(30f, 24f, 120f), .001f)
        assertEquals(.5f, state.progress, .001f)
    }

    @Test fun normalizedProgressAndLegacyActivityStateRestore() {
        val state = WatchPlayerResizeState(.5f)
        val saved = with(WatchPlayerResizeState.saver) { SaverScope { true }.save(state) }!!
        val restored = WatchPlayerResizeState.saver.restore(saved)!!
        assertEquals(.5f, restored.progress, .001f)
        assertEquals(-30f, restored.consume(-30f, 24f, 120f), .001f)
        assertEquals(.25f, restored.progress, .001f)
        assertEquals(.5f, WatchPlayerResizeState.saver.restore(48f)!!.progress, .001f)
        assertEquals(0f, WatchPlayerResizeState(Float.NaN).progress, .001f)
        assertEquals(0f, state.consume(Float.NaN, 0f, 210f), .001f)
        assertEquals(0f, state.consume(10f, 0f, Float.NaN), .001f)
    }
}
