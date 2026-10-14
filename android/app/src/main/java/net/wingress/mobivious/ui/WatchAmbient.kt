package net.wingress.mobivious.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal val LocalWatchAmbient = staticCompositionLocalOf<WatchAmbientState?> { null }

internal fun ambientPictureBounds(slot: Rect, ratio: Float?): Rect {
    if (slot.isEmpty) return slot
    val aspect = ratio?.takeIf { it.isFinite() && it > 0f } ?: 16f / 9f
    val width = minOf(slot.width, slot.height * aspect)
    val height = width / aspect
    return Rect(slot.center - Offset(width / 2, height / 2), Size(width, height))
}

/** Bound the lighting, not the foreground: supporting text keeps its contrast even over white footage. */
internal fun ambientIllumination(color: Color, surface: Color, dark: Boolean): Color {
    fun adjusted(amount: Float) = if (dark) Color(color.red * amount, color.green * amount,
        color.blue * amount, color.alpha) else lerp(Color.White, color, amount).copy(alpha = color.alpha)
    fun safe(candidate: Color): Boolean {
        val luminance = candidate.copy(alpha = if (dark) .55f else .30f).compositeOver(surface).luminance()
        return if (dark) luminance <= .075f else luminance >= .70f
    }
    if (safe(color)) return color
    var low = 0f; var high = 1f
    repeat(10) {
        val middle = (low + high) / 2f
        if (safe(adjusted(middle))) low = middle else high = middle
    }
    return adjusted(low)
}

private class AmbientFrameSampler {
    private val gate = AmbientCaptureGate()
    private val handler = Handler(Looper.getMainLooper())
    private var bitmap: Bitmap? = null
    private val pixels = IntArray(32 * 18)

    fun invalidate() = gate.invalidate()
    fun close() {
        gate.close()
        if (!gate.inFlight) { bitmap?.recycle(); bitmap = null }
    }

    suspend fun sample(source: SurfaceView): AmbientPalette? {
        if (!source.isAttachedToWindow || !source.holder.surface.isValid || source.width == 0 || source.height == 0) return null
        val token = gate.begin(SystemClock.uptimeMillis()) ?: return null
        val destination = bitmap ?: createBitmap(32, 18, Bitmap.Config.ARGB_8888).also { bitmap = it }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { gate.invalidate() }
            fun finish(result: Int) {
                val accepted = gate.finish(token, SystemClock.uptimeMillis(), result == PixelCopy.SUCCESS)
                val palette = if (accepted && continuation.isActive) {
                    destination.getPixels(pixels, 0, 32, 0, 0, 32, 18)
                    AmbientPalette.fromPixels(pixels, 32, 18)
                } else null
                if (gate.closed) { bitmap?.recycle(); bitmap = null }
                if (continuation.isActive) continuation.resume(palette)
            }
            try { PixelCopy.request(source, destination, { finish(it) }, handler) }
            catch (_: IllegalArgumentException) { finish(PixelCopy.ERROR_SOURCE_INVALID) }
        }
    }
}

internal class WatchAmbientState {
    private val sampler = AmbientFrameSampler()
    var surface by mutableStateOf<SurfaceView?>(null); private set
    var frameReady by mutableStateOf(false); private set
    var refresh by mutableIntStateOf(0); private set
    var enabled by mutableStateOf(false)
    var palette by mutableStateOf<AmbientPalette?>(null); private set
    val available by derivedStateOf { enabled && palette != null }
    val colors = List(9) { Animatable(Color.Transparent) }

    fun attach(source: SurfaceView?) {
        if (surface === source) return
        sampler.invalidate(); surface = source; frameReady = false
    }
    fun renderedFrame() { frameReady = true; refresh++ }
    fun seek() { refresh++ }
    fun resetFrame() { sampler.invalidate(); frameReady = false; palette = null }
    fun clear() { sampler.invalidate(); palette = null }
    fun close() = sampler.close()
    suspend fun sample(source: SurfaceView): Boolean {
        val next = sampler.sample(source) ?: return false
        show(next)
        return true
    }
    /** Only spatial colors become UI state; frame bitmaps stay in the sampler. */
    fun show(palette: AmbientPalette) { this.palette = palette }
}

@Composable
internal fun rememberAmbientAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Read the setting directly: ValueAnimator's cached scale can lag its ContentObserver callback.
    fun enabledNow() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f &&
        !context.getSystemService(PowerManager::class.java).isPowerSaveMode
    var enabled by remember(context) { mutableStateOf(enabledNow()) }
    DisposableEffect(context, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = enabledNow() }
        }
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = enabledNow()
        }
        val power = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { enabled = enabledNow() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        context.registerReceiver(power, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(resume)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer); context.unregisterReceiver(power)
            lifecycle.removeObserver(resume)
        }
    }
    return enabled
}

/** Effects are isolated so animation and sampled palettes never recompose the watch list. */
@Composable
internal fun WatchAmbientEffects(state: WatchAmbientState, controller: MediaController?, playback: PlaybackState,
    enabled: Boolean, sampling: Boolean) {
    SideEffect { state.enabled = enabled }
    DisposableEffect(state) { onDispose { state.close() } }
    DisposableEffect(state, controller) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = state.renderedFrame()
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = state.seek()
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = state.resetFrame()
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(state, enabled) { if (!enabled) state.clear() }
    // A paused decoder can render before a new media-keyed observer is attached.
    LaunchedEffect(state, playback.playerState, state.surface) {
        if (playback.playerState == Player.STATE_READY && state.surface != null) state.renderedFrame()
    }
    LaunchedEffect(state, enabled, sampling, state.surface, state.frameReady, state.refresh,
        playback.playing, playback.loading, playback.playerState) {
        val source = state.surface
        if (enabled && sampling && source != null && state.frameReady && !playback.loading &&
            playback.playerState == Player.STATE_READY) {
            // Let a paused seek's replacement buffer reach the surface before copying it.
            delay(100)
            do {
                val success = state.sample(source)
                if (success && !playback.playing) break
                delay(250)
            } while (true)
        }
    }
    WatchAmbientAnimation(state, enabled)
}

@Composable
internal fun WatchAmbientAnimation(state: WatchAmbientState, enabled: Boolean = state.enabled) {
    if (!enabled) return
    val palette = state.palette ?: return
    LaunchedEffect(state, palette) {
        coroutineScope {
            state.colors.forEachIndexed { index, color -> launch {
                color.animateTo(Color(palette.colors[index]), tween(600))
            } }
        }
    }
}


@Composable
internal fun WatchAmbientScene(modifier: Modifier, playerBounds: Rect, content: @Composable BoxScope.() -> Unit) {
    val state = LocalWatchAmbient.current
    val active = state?.available == true && !LocalReduceTransparency.current && LocalView.current.isHardwareAccelerated
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.red < .3f
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        Box(Modifier.matchParentSize().clipToBounds().background(surface)) {
            if (active) Canvas(Modifier.matchParentSize().testTag("watch-ambient-background")
                .graphicsLayer { alpha = if (dark) .55f else .30f }) {
                val picture = playerBounds.translate(-origin)
                val wide = size.width >= 840.dp.toPx()
                val radius = maxOf(picture.width * .85f, 280.dp.toPx())
                state.colors.forEachIndexed { index, animation ->
                    val column = index % 3; val row = index / 3
                    val center = if (wide) Offset(picture.right + (column + .1f) * 110.dp.toPx(),
                        picture.top + picture.height * (row + .5f) / 3f)
                    else Offset(picture.left + picture.width * (column + .5f) / 3f,
                        picture.bottom + (row + .1f) * 110.dp.toPx())
                    val color = ambientIllumination(animation.value, surface, dark)
                    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * .4f), Color.Transparent),
                        center = center, radius = radius), radius = radius, center = center)
                }
            }
        }
        content()
    }
}
