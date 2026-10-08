package net.wingress.mobivious.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

internal enum class PlayerPresentation { CLOSED, MINI, WATCH, FULLSCREEN }
internal enum class PlayerDragAxis { VERTICAL, HORIZONTAL }

@Composable
internal fun rememberPlayerTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var exploring by remember(manager) { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return exploring
}

/** Distances and velocity are expressed in dp and dp/second, independently of display density. */
internal object PlayerGestureRules {
    fun acceptsStart(mode: PlayerPresentation, y: Float, fullscreenTopEdge: Float): Boolean =
        mode != PlayerPresentation.FULLSCREEN || y >= fullscreenTopEdge

    fun axis(mode: PlayerPresentation, displacement: Offset): PlayerDragAxis? = when {
        abs(displacement.y) >= abs(displacement.x) -> when {
            mode == PlayerPresentation.MINI && displacement.y < 0 -> PlayerDragAxis.VERTICAL
            mode in listOf(PlayerPresentation.WATCH, PlayerPresentation.FULLSCREEN) && displacement.y > 0 -> PlayerDragAxis.VERTICAL
            else -> null
        }
        mode == PlayerPresentation.MINI -> PlayerDragAxis.HORIZONTAL
        else -> null
    }

    fun target(mode: PlayerPresentation, axis: PlayerDragAxis): PlayerPresentation = when {
        axis == PlayerDragAxis.HORIZONTAL -> PlayerPresentation.CLOSED
        mode == PlayerPresentation.WATCH -> PlayerPresentation.MINI
        else -> PlayerPresentation.WATCH
    }

    fun completes(mode: PlayerPresentation, axis: PlayerDragAxis, distance: Float, velocity: Float, width: Float): Boolean {
        if (mode == PlayerPresentation.FULLSCREEN) return axis == PlayerDragAxis.VERTICAL && distance >= 120f
        val direction = if (axis == PlayerDragAxis.HORIZONTAL) if (distance < 0) -1 else 1
            else if (mode == PlayerPresentation.MINI) -1 else 1
        val directedDistance = distance * direction
        val threshold = if (axis == PlayerDragAxis.HORIZONTAL) width * .35f else 64f
        return directedDistance >= threshold || directedDistance >= 24f && velocity * direction >= 1_000f
    }
}

internal interface PlayerDragHandler {
    fun start(axis: PlayerDragAxis)
    fun move(displacement: Offset)
    fun end(velocity: Offset)
    fun cancel()
}

/** Returns an up only for a tap. A claimed drag never falls through to a tap or double-tap. */
internal suspend fun AwaitPointerEventScope.playerGesture(
    down: PointerInputChange,
    mode: PlayerPresentation,
    drag: PlayerDragHandler?,
    position: (Offset) -> Offset = { it },
    fullscreenTopEdge: Float = 0f,
    claim: () -> Unit = {},
): PointerInputChange? {
    val start = position(down.position)
    // Leave the entire top-edge gesture unconsumed so Android can reveal its bars.
    if (!PlayerGestureRules.acceptsStart(mode, start.y, fullscreenTopEdge)) return null
    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, start) }
    var axis: PlayerDragAxis? = null
    var finished = false
    try {
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.any { it.id != down.id && it.pressed }) return null
            val change = event.changes.firstOrNull { it.id == down.id } ?: return null
            if (change.isConsumed) return null
            val point = position(change.position)
            velocity.addPosition(change.uptimeMillis, point)
            val displacement = point - start
            if (!change.pressed) {
                if (axis != null) {
                    change.consume()
                    drag?.move(displacement)
                    val speed = velocity.calculateVelocity()
                    drag?.end(Offset(speed.x, speed.y))
                    finished = true
                    return null
                }
                if (displacement.getDistance() > viewConfiguration.touchSlop ||
                    change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) return null
                change.consume()
                return change
            }
            if (axis == null && displacement.getDistance() > viewConfiguration.touchSlop) {
                if (drag == null) return null
                axis = PlayerGestureRules.axis(mode, displacement) ?: return null
                claim()
                drag.start(axis)
            }
            if (axis != null) {
                if (change.positionChange() != Offset.Zero) change.consume()
                drag?.move(displacement)
            }
        }
    } finally {
        if (axis != null && !finished) drag?.cancel()
    }
}
