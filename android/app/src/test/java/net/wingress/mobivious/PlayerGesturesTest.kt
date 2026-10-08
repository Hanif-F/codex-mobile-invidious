package net.wingress.mobivious

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Test

class PlayerGesturesTest {
    @Test fun directionsAreLockedToTheDominantAxisAndUnsupportedSwipesHaveNoAction() {
        assertEquals(PlayerDragAxis.VERTICAL, PlayerGestureRules.axis(PlayerPresentation.WATCH, Offset(10f, 20f)))
        assertEquals(PlayerDragAxis.VERTICAL, PlayerGestureRules.axis(PlayerPresentation.FULLSCREEN, Offset(-10f, 20f)))
        assertEquals(PlayerDragAxis.VERTICAL, PlayerGestureRules.axis(PlayerPresentation.MINI, Offset(10f, -20f)))
        for (x in listOf(-20f, 20f)) assertEquals(PlayerDragAxis.HORIZONTAL, PlayerGestureRules.axis(PlayerPresentation.MINI, Offset(x, 10f)))
        assertNull(PlayerGestureRules.axis(PlayerPresentation.WATCH, Offset(0f, -100f)))
        assertNull(PlayerGestureRules.axis(PlayerPresentation.MINI, Offset(0f, 100f)))
        assertNull(PlayerGestureRules.axis(PlayerPresentation.FULLSCREEN, Offset(100f, 10f)))
        assertNull(PlayerGestureRules.axis(PlayerPresentation.CLOSED, Offset(0f, 100f)))
    }

    @Test fun verticalDistanceAndDirectionalFlingThresholdsAreIndependentOfViewport() {
        for ((mode, sign) in listOf(PlayerPresentation.WATCH to 1f, PlayerPresentation.MINI to -1f)) {
            fun completes(distance: Float, speed: Float) = PlayerGestureRules.completes(mode, PlayerDragAxis.VERTICAL, distance * sign, speed * sign, 390f)
            assertFalse(completes(63f, 0f)); assertTrue(completes(64f, 0f))
            assertFalse(completes(23f, 2_000f)); assertTrue(completes(24f, 1_000f))
            assertFalse(completes(24f, 999f)); assertFalse(completes(24f, -2_000f))
            assertFalse(completes(-100f, 0f))
        }
    }

    @Test fun fullscreenRequires120DpRegardlessOfSpeedAndRejectsTopEdgeStarts() {
        for (width in listOf(320f, 600f)) for (speed in listOf(-2_000f, 0f, 2_000f)) {
            fun completes(distance: Float) = PlayerGestureRules.completes(PlayerPresentation.FULLSCREEN, PlayerDragAxis.VERTICAL, distance, speed, width)
            assertFalse(completes(-120f)); assertFalse(completes(24f)); assertFalse(completes(119f)); assertTrue(completes(120f))
        }
        for (edge in listOf(32f, 48f)) {
            assertFalse(PlayerGestureRules.acceptsStart(PlayerPresentation.FULLSCREEN, edge - 1f, edge))
            assertTrue(PlayerGestureRules.acceptsStart(PlayerPresentation.FULLSCREEN, edge, edge))
            assertTrue(PlayerGestureRules.acceptsStart(PlayerPresentation.WATCH, 0f, edge))
            assertTrue(PlayerGestureRules.acceptsStart(PlayerPresentation.MINI, 0f, edge))
        }
    }

    @Test fun sidewaysDismissalWorksInEitherDirectionAndRequires35PercentOrADirectionalFling() {
        for (width in listOf(320f, 390f, 600f)) for (sign in listOf(-1f, 1f)) {
            fun completes(distance: Float, speed: Float) = PlayerGestureRules.completes(PlayerPresentation.MINI,
                PlayerDragAxis.HORIZONTAL, distance * sign, speed * sign, width)
            assertFalse(completes(width * .35f - 1, 0f)); assertTrue(completes(width * .35f, 0f))
            assertTrue(completes(24f, 1_000f)); assertFalse(completes(24f, -1_000f))
            assertFalse(completes(23f, 2_000f))
        }
    }

    @Test fun fullscreenCollapsesToWatchBeforeWatchCollapsesToMini() {
        assertEquals(PlayerPresentation.WATCH, PlayerGestureRules.target(PlayerPresentation.FULLSCREEN, PlayerDragAxis.VERTICAL))
        assertEquals(PlayerPresentation.MINI, PlayerGestureRules.target(PlayerPresentation.WATCH, PlayerDragAxis.VERTICAL))
        assertEquals(PlayerPresentation.WATCH, PlayerGestureRules.target(PlayerPresentation.MINI, PlayerDragAxis.VERTICAL))
        assertEquals(PlayerPresentation.CLOSED, PlayerGestureRules.target(PlayerPresentation.MINI, PlayerDragAxis.HORIZONTAL))
    }

    private class Frames : MonotonicFrameClock {
        private var now = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(1); now += 16_666_667L
            return onFrame(now)
        }
    }

    @Test fun fullscreenWaitsForReleaseAndOnlyCompletedSwipesAnimate() = runBlocking(Frames()) {
        val state = PlayerPresentationState(PlayerPresentation.FULLSCREEN, this)
        val drag = state.dragHandler(3f, 900f, 1170f, {}, { fail("Fullscreen must never close playback") })
        suspend fun settled() { coroutineContext[Job]!!.children.toList().forEach { it.join() } }
        fun fixed() {
            assertEquals(PlayerPresentation.FULLSCREEN, state.mode)
            assertEquals(0f, state.fraction, 0f); assertNull(state.target); assertFalse(state.active)
        }
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 357f)); fixed()
        drag.end(Offset(0f, 6_000f)); settled(); assertTrue(state.fullscreen); assertFalse(state.dragging)
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 450f)); fixed()
        drag.move(Offset(0f, 90f)); drag.end(Offset.Zero); settled(); assertTrue(state.fullscreen)
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 450f)); drag.cancel(); settled()
        assertTrue(state.fullscreen); assertFalse(state.active)
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 360f)); fixed(); drag.end(Offset.Zero)
        assertEquals(PlayerPresentation.FULLSCREEN, state.mode)
        assertEquals(PlayerPresentation.WATCH, state.target); assertEquals(0f, state.fraction, 0f)
        settled(); assertEquals(PlayerPresentation.WATCH, state.mode); assertFalse(state.active)
    }

    @Test fun interruptedFullscreenGestureCannotFinishLater() = runBlocking(Frames()) {
        val state = PlayerPresentationState(PlayerPresentation.FULLSCREEN, this)
        val drag = state.dragHandler(1f, 400f, 390f, {}, { fail() })
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 150f)); state.cancelMotion()
        drag.move(Offset(0f, 200f)); drag.end(Offset.Zero)
        assertTrue(state.fullscreen); assertFalse(state.active); assertFalse(state.dragging)
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 150f)); drag.end(Offset.Zero)
        state.cancelMotion()
        coroutineContext[Job]!!.children.toList().forEach { it.join() }
        assertTrue(state.fullscreen); assertFalse(state.active)
    }

    @Test fun completedAndCanceledDragsSettleWithoutClosingOrLosingPresentation() = runBlocking(Frames()) {
        val state = PlayerPresentationState(PlayerPresentation.WATCH, this)
        var closes = 0
        val drag = state.dragHandler(3f, 900f, 1170f, {}, { closes++ })
        suspend fun settled() { coroutineContext[Job]!!.children.toList().forEach { it.join() } }
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 90f)); drag.end(Offset.Zero); settled()
        assertEquals(PlayerPresentation.WATCH, state.mode); assertFalse(state.active)
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 192f)); drag.end(Offset.Zero); settled()
        assertEquals(PlayerPresentation.MINI, state.mode)
        drag.start(PlayerDragAxis.HORIZONTAL); drag.move(Offset(-500f, 0f)); drag.cancel(); settled()
        assertEquals(PlayerPresentation.MINI, state.mode); assertEquals(0, closes)
        drag.start(PlayerDragAxis.HORIZONTAL); drag.move(Offset(500f, 0f)); drag.end(Offset.Zero); settled()
        assertEquals(PlayerPresentation.CLOSED, state.mode); assertEquals(1, closes)
    }

    @Test fun interruptionAndNavigationCancelAnInFlightDismissal() = runBlocking(Frames()) {
        val state = PlayerPresentationState(PlayerPresentation.MINI, this)
        var closes = 0
        val drag = state.dragHandler(1f, 400f, 390f, {}, { closes++ })
        drag.start(PlayerDragAxis.HORIZONTAL); drag.move(Offset(200f, 0f)); drag.end(Offset.Zero)
        state.present(PlayerPresentation.WATCH, animate = false)
        coroutineContext[Job]!!.children.toList().forEach { it.join() }
        assertEquals(PlayerPresentation.WATCH, state.mode); assertFalse(state.active); assertEquals(0, closes)
        drag.start(PlayerDragAxis.VERTICAL); drag.move(Offset(0f, 100f)); state.cancelMotion()
        assertEquals(PlayerPresentation.WATCH, state.mode); assertFalse(state.active)
    }
}
