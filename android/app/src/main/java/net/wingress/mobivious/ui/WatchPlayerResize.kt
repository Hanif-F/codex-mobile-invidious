package net.wingress.mobivious.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import net.wingress.mobivious.player.WatchPlayerScroll

@Stable
internal class WatchPlayerResizeState(initialDistance: Float = 0f) {
    var distance by mutableFloatStateOf(initialDistance.coerceIn(0f, WatchPlayerScroll.RESIZE_DISTANCE))
        private set
    val progress: Float get() = distance / WatchPlayerScroll.RESIZE_DISTANCE

    fun consume(delta: Float, topDistance: Float?): Float {
        val consumed = WatchPlayerScroll.consume(delta, distance, topDistance)
        distance = (distance + consumed).coerceIn(0f, WatchPlayerScroll.RESIZE_DISTANCE)
        return consumed
    }

    companion object {
        val saver = Saver<WatchPlayerResizeState, Float>(save = { it.distance }, restore = { WatchPlayerResizeState(it) })
    }
}

@Composable
internal fun rememberWatchPlayerResizeState(): WatchPlayerResizeState =
    rememberSaveable(saver = WatchPlayerResizeState.saver) { WatchPlayerResizeState() }

/** Consume only the resize part of a drag/fling; the list receives all remaining scrolling. */
@Composable
internal fun rememberWatchPlayerScrollConnection(
    list: LazyListState, resize: WatchPlayerResizeState, enabled: Boolean,
): NestedScrollConnection {
    val density = LocalDensity.current.density
    return remember(list, resize, density, enabled) {
        object : NestedScrollConnection {
            private fun consume(available: Offset): Offset {
                if (!enabled) return Offset.Zero
                val top = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset / density else null
                return Offset(0f, -resize.consume(-available.y / density, top) * density)
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = consume(available)
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = consume(available)
        }
    }
}
