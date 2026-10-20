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
internal class WatchPlayerResizeState(initialProgress: Float = 0f) {
    var progress by mutableFloatStateOf(initialProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
        private set

    fun consume(delta: Float, topDistance: Float?, resizeRange: Float): Float {
        if (!resizeRange.isFinite() || resizeRange <= 0f) return 0f
        val consumed = WatchPlayerScroll.consume(delta, progress * resizeRange, topDistance, resizeRange)
        progress = (progress + consumed / resizeRange).coerceIn(0f, 1f)
        return consumed
    }

    companion object {
        // Older activities saved a distance on a fixed 96 dp scale. New saves wrap the
        // normalized progress so restoration can distinguish the two representations.
        val saver = Saver<WatchPlayerResizeState, Any>(save = { listOf(it.progress) }, restore = {
            when (it) {
                is Float -> WatchPlayerResizeState(it / 96f)
                is List<*> -> (it.singleOrNull() as? Float)?.let(::WatchPlayerResizeState)
                else -> null
            }
        })
    }
}

@Composable
internal fun rememberWatchPlayerResizeState(): WatchPlayerResizeState =
    rememberSaveable(saver = WatchPlayerResizeState.saver) { WatchPlayerResizeState() }

/** Consume only the resize part of a drag/fling; the list receives all remaining scrolling. */
@Composable
internal fun rememberWatchPlayerScrollConnection(
    list: LazyListState, resize: WatchPlayerResizeState, resizeRange: Float, enabled: Boolean,
): NestedScrollConnection {
    val density = LocalDensity.current.density
    return remember(list, resize, density, resizeRange, enabled) {
        object : NestedScrollConnection {
            private fun consume(available: Offset): Offset {
                if (!enabled) return Offset.Zero
                val top = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset / density else null
                return Offset(0f, -resize.consume(-available.y / density, top, resizeRange) * density)
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = consume(available)
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = consume(available)
        }
    }
}
