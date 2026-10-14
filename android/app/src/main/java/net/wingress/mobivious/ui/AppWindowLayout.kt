package net.wingress.mobivious.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** A separating opaque hinge uses the larger contiguous pane, including in split-screen. */
internal fun usableWindowPane(window: Rect, hinge: Rect?, keyboardHeight: Int = 0): Rect {
    if (hinge == null || hinge.isEmpty || !window.overlaps(hinge)) return window
    val visible=Rect(window.left,window.top,window.right,maxOf(window.top,window.bottom-keyboardHeight.coerceAtLeast(0)))
    if (visible.isEmpty || !visible.overlaps(hinge)) return visible
    val cut = hinge.intersect(visible)
    val panes = if (cut.height >= visible.height * .9f) listOf(
        Rect(visible.left,visible.top,cut.left,visible.bottom), Rect(cut.right,visible.top,visible.right,visible.bottom)
    ) else if (cut.width >= visible.width * .9f) listOf(
        Rect(visible.left,visible.top,visible.right,cut.top), Rect(visible.left,cut.bottom,visible.right,visible.bottom)
    ) else return visible
    return panes.filterNot { it.isEmpty }.maxByOrNull { it.width * it.height } ?: visible
}

private tailrec fun Context.windowActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.windowActivity() else null
    else -> null
}

@Composable
internal fun rememberUsableWindowPane(): Rect {
    val activity = LocalContext.current.windowActivity()
    val size = LocalWindowInfo.current.containerSize
    val hinge by produceState<Rect?>(null, activity) {
        if (activity != null) WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { layout ->
            layout.displayFeatures.filterIsInstance<FoldingFeature>()
                .firstOrNull { it.occlusionType == FoldingFeature.OcclusionType.FULL }?.bounds?.let {
                    Rect(it.left.toFloat(),it.top.toFloat(),it.right.toFloat(),it.bottom.toFloat())
                }
        }.collect { value = it }
    }
    return usableWindowPane(Rect(0f,0f,size.width.toFloat(),size.height.toFloat()), hinge, WindowInsets.ime.getBottom(LocalDensity.current))
}

@Composable
internal fun AppUsablePane(bounds: Rect, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val size = LocalWindowInfo.current.containerSize
    val safe = WindowInsets.safeDrawing
    val consumed = WindowInsets(
        left=minOf(safe.getLeft(density,direction),bounds.left.coerceAtLeast(0f).roundToInt()),
        top=minOf(safe.getTop(density),bounds.top.coerceAtLeast(0f).roundToInt()),
        right=minOf(safe.getRight(density,direction),(size.width-bounds.right).coerceAtLeast(0f).roundToInt()),
        bottom=minOf(maxOf(safe.getBottom(density),WindowInsets.ime.getBottom(density)),(size.height-bounds.bottom).coerceAtLeast(0f).roundToInt()))
    Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.AbsoluteAlignment.TopLeft) {
        Box(Modifier.absoluteOffset { IntOffset(bounds.left.roundToInt(),bounds.top.roundToInt()) }
            .requiredSize(with(density) {bounds.width.toDp()},with(density) {bounds.height.toDp()})
            .consumeWindowInsets(consumed)) {
            CompositionLocalProvider(LocalPageInset provides if(with(density) {bounds.width.toDp()}<600.dp) 16.dp else 24.dp,content=content)
        }
    }
}

internal val LocalPageInset = staticCompositionLocalOf { 16.dp }
