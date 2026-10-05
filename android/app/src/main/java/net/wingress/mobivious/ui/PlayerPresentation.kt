package net.wingress.mobivious.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal class PlayerPresentationState(initial: PlayerPresentation, private val scope: CoroutineScope) {
    var mode by mutableStateOf(initial); private set
    var target by mutableStateOf<PlayerPresentation?>(null); private set
    var dragging by mutableStateOf(false); private set
    private val motion = Animatable(0f)
    private var dragFraction by mutableStateOf<Float?>(null)
    private var job: Job? = null
    private var axis = PlayerDragAxis.VERTICAL
    private var distance = 0f
    private var travel = 1f
    private var width = 1f
    var dismissDirection by mutableFloatStateOf(1f); private set
    val fraction get() = dragFraction ?: motion.value
    val active get() = target != null
    val watch get() = mode == PlayerPresentation.WATCH || mode == PlayerPresentation.FULLSCREEN
    val fullscreen get() = mode == PlayerPresentation.FULLSCREEN
    val watchAlpha get() = blend({ if (it == PlayerPresentation.WATCH) 1f else 0f })
    val miniAlpha get() = blend({ if (it == PlayerPresentation.MINI) 1f else 0f })
    val showsWatch get() = mode == PlayerPresentation.WATCH || target == PlayerPresentation.WATCH || target == PlayerPresentation.MINI
    val allocatesMini get() = mode == PlayerPresentation.MINI || target == PlayerPresentation.MINI

    var watchBounds by mutableStateOf(Rect.Zero)
    var miniBounds by mutableStateOf(Rect.Zero)
    var navigationBounds by mutableStateOf(Rect.Zero)
    var miniHeight by mutableFloatStateOf(0f)
    private fun blend(value: (PlayerPresentation) -> Float): Float = value(mode) + ((target?.let(value) ?: value(mode)) - value(mode)) * fraction

    fun cancelMotion() {
        job?.cancel(); job = null; target = null; dragging = false; dragFraction = null; distance = 0f
    }

    fun present(next: PlayerPresentation, animate: Boolean = true, closed: () -> Unit = {}) {
        if (next == mode && !active) return
        cancelMotion()
        if (!animate || next == PlayerPresentation.FULLSCREEN || mode == PlayerPresentation.CLOSED) {
            mode = next
            if (next == PlayerPresentation.CLOSED) closed()
        } else {
            target = next
            dragFraction = 0f
            settle(true, closed)
        }
    }

    fun dragHandler(density: Float, verticalTravel: Float, rowWidth: Float, claimed: () -> Unit, closed: () -> Unit): PlayerDragHandler = object : PlayerDragHandler {
        override fun start(axis: PlayerDragAxis) {
            cancelMotion(); claimed()
            this@PlayerPresentationState.axis = axis
            travel = (if (axis == PlayerDragAxis.VERTICAL) verticalTravel else rowWidth).coerceAtLeast(1f)
            width = rowWidth / density
            target = PlayerGestureRules.target(mode, axis)
            dragFraction = 0f; dragging = true
        }
        override fun move(displacement: Offset) {
            distance = if (axis == PlayerDragAxis.VERTICAL) displacement.y / density else displacement.x / density
            dismissDirection = if (distance < 0) -1f else 1f
            val directed = if (axis == PlayerDragAxis.HORIZONTAL) abs(distance) else distance * if (mode == PlayerPresentation.MINI) -1 else 1
            dragFraction = (directed * density / travel).coerceIn(0f, 1f)
        }
        override fun end(velocity: Offset) {
            val speed = (if (axis == PlayerDragAxis.VERTICAL) velocity.y else velocity.x) / density
            dragging = false
            settle(PlayerGestureRules.completes(mode, axis, distance, speed, width), closed)
        }
        override fun cancel() { if (dragging) { dragging = false; settle(false, closed) } }
    }

    private fun settle(complete: Boolean, closed: () -> Unit) {
        val start = fraction
        job = scope.launch {
            motion.snapTo(start); dragFraction = null
            motion.animateTo(if (complete) 1f else 0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            val next = target
            if (complete && next != null) mode = next
            target = null; dragFraction = null; job = null
            if (complete && next == PlayerPresentation.CLOSED) closed()
        }
    }
}

@Composable
internal fun rememberPlayerPresentation(): PlayerPresentationState {
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = Saver(
        save = { it.mode.name }, restore = { PlayerPresentationState(PlayerPresentation.valueOf(it), scope) },
    )) { PlayerPresentationState(PlayerPresentation.CLOSED, scope) }
}

/** Measured slots own chrome only. This host owns the sole live surface throughout a transition. */
@Composable
internal fun PlayerPresentationHost(
    vm: AppViewModel, playback: PlaybackState, controller: MediaController?, state: PlayerPresentationState,
    pip: Boolean, hidden: Boolean, modal: Boolean, occurrence: String?,
    close: () -> Unit, collapse: () -> Unit, restore: () -> Unit, fullscreen: () -> Unit, settings: () -> Unit,
    chapters: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(windowSize, playback.mediaId, occurrence, pip, hidden, modal, lifecycleState) { state.cancelMotion() }
    DisposableEffect(state) { onDispose { state.cancelMotion() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        content()
        val full = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        fun bounds(mode: PlayerPresentation): Rect = when (mode) {
            PlayerPresentation.FULLSCREEN -> full
            PlayerPresentation.WATCH -> state.watchBounds.takeUnless { it.isEmpty } ?: full
            else -> state.miniBounds.takeUnless { it.isEmpty } ?: Rect(8 * density.density,
                state.navigationBounds.top - 72 * density.density, 120 * density.density, state.navigationBounds.top - 9 * density.density)
        }
        val anchorReady = state.mode == PlayerPresentation.FULLSCREEN ||
            (if (state.mode == PlayerPresentation.WATCH) state.watchBounds else state.miniBounds).isEmpty.not()
        if (!hidden && (pip || state.mode != PlayerPresentation.CLOSED && anchorReady)) {
            val from = bounds(state.mode)
            val destination = bounds(state.target ?: state.mode)
            val dismissing = state.target == PlayerPresentation.CLOSED
            val rect = if (pip) full else if (dismissing) from.translate(Offset(state.dismissDirection * full.width * state.fraction, 0f))
                else lerp(from, destination, state.fraction)
            val drag = state.dragHandler(density.density, abs(bounds(PlayerPresentation.MINI).top - bounds(PlayerPresentation.WATCH).top)
                .coerceAtLeast(160 * density.density), full.width, vm::cancelAccumulatedSeek, close)
            VideoPlayer(vm, playback, controller,
                Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                    .clip(RoundedCornerShape(if (pip || state.fullscreen) 0.dp else (8 + 8 * state.watchAlpha).dp)),
                fullscreen = state.fullscreen, controls = !pip && state.watch, settingsOpen = modal,
                presentation = state.mode, drag = drag,
                gesturesEnabled = !pip && !modal && (!state.active || state.dragging) && lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                chromeVisible = !state.active, gestureKey = windowSize to occurrence,
                onCollapse = collapse, onRestore = restore, onDismiss = close,
                onFullscreen = fullscreen, onSettings = settings, onChapters = chapters,
                surfaceAlpha = if (dismissing) 1f - state.fraction else 1f)
        }
    }
}

internal fun Modifier.playerAnchor(update: (Rect) -> Unit): Modifier = onGloballyPositioned {
    // Unclipped coordinates keep the anchor stable while a dismissed row moves outside the window.
    update(Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())))
}

internal fun Modifier.hiddenPlayerContent(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { hideFromAccessibility() } else this
