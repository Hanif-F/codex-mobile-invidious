package net.wingress.mobivious.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.ViewTreeObserver
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.floor

internal class GlassWindowSource(val window: Window)
internal val LocalGlassWindowSource = staticCompositionLocalOf<GlassWindowSource?> { null }
internal val LocalGlassWindowCapture = staticCompositionLocalOf<GlassWindowCapture?> { null }

/** Resolve before entering a popup/dialog. A popup nested in a dialog inherits its window. */
@Composable
internal fun rememberGlassWindowSource(): GlassWindowSource? {
    val view = LocalView.current
    val inherited = LocalGlassWindowSource.current
    val activity = LocalActivity.current
    return remember(view, inherited, activity) {
        var parent = view.parent
        var dialogWindow: Window? = null
        while (parent != null) {
            if (parent is DialogWindowProvider) { dialogWindow = parent.window; break }
            parent = parent.parent
        }
        val window = dialogWindow ?: inherited?.window ?: activity?.window
        window?.let(::GlassWindowSource)
    }
}

internal data class GlassCaptureRegion(val crop: android.graphics.Rect, val origin: IntOffset)

/** PixelCopy's Window rectangle is relative to DecorView, rather than the overlay window. */
internal fun glassCaptureRegion(overlay: Rect, sourceOrigin: IntOffset, sourceWidth: Int, sourceHeight: Int,
    padding: Float): GlassCaptureRegion? {
    if (overlay.isEmpty || sourceWidth <= 0 || sourceHeight <= 0) return null
    val crop = android.graphics.Rect(
        floor(overlay.left - sourceOrigin.x - padding).toInt().coerceAtLeast(0),
        floor(overlay.top - sourceOrigin.y - padding).toInt().coerceAtLeast(0),
        ceil(overlay.right - sourceOrigin.x + padding).toInt().coerceAtMost(sourceWidth),
        ceil(overlay.bottom - sourceOrigin.y + padding).toInt().coerceAtMost(sourceHeight))
    if (crop.isEmpty) return null
    return GlassCaptureRegion(crop, sourceOrigin + IntOffset(crop.left, crop.top))
}

internal data class GlassWindowSnapshot(val bitmap: Bitmap, val origin: IntOffset)

/** Two reusable native-resolution crops. No decoding surface or other window is retained. */
internal class GlassWindowCapture(private val source: GlassWindowSource) {
    val changed = Channel<Unit>(Channel.CONFLATED)
    var snapshot by mutableStateOf<GlassWindowSnapshot?>(null); private set
    var captures = 0; private set
    var region: GlassCaptureRegion? = null; private set
    private val handler = Handler(Looper.getMainLooper())
    private val buffers = mutableListOf<Bitmap>()
    private var generation = 0L
    private var inFlight = false
    private var closed = false

    fun invalidate() { if (!closed) changed.trySend(Unit) }
    fun position(next: GlassCaptureRegion?) {
        if (region == next) return
        generation++
        region = next
        snapshot = null
        invalidate()
    }
    fun suspendCapture() {
        generation++
        snapshot = null
    }
    fun close() {
        closed = true
        suspendCapture()
        changed.close()
        if (!inFlight) release()
    }
    private fun release() { buffers.forEach(Bitmap::recycle); buffers.clear() }

    suspend fun capture() {
        val target = region ?: return
        if (closed || inFlight || !source.window.decorView.isAttachedToWindow) return
        val token = generation
        val front = snapshot?.bitmap
        // Dispose obsolete-size crops only when no copy is writing into them.
        buffers.removeAll { bitmap ->
            if (bitmap !== front && (bitmap.width != target.crop.width() || bitmap.height != target.crop.height())) {
                bitmap.recycle(); true
            } else false
        }
        val destination = buffers.firstOrNull { it !== front && it.width == target.crop.width() && it.height == target.crop.height() }
            ?: createBitmap(target.crop.width(), target.crop.height(), Bitmap.Config.ARGB_8888).also(buffers::add)
        inFlight = true
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { generation++ }
            fun finish(result: Int) {
                inFlight = false
                if (!closed && token == generation && continuation.isActive && result == PixelCopy.SUCCESS) {
                    snapshot = GlassWindowSnapshot(destination, target.origin)
                    captures++
                }
                if (!closed && token != generation) invalidate()
                if (closed) release()
                if (continuation.isActive) continuation.resume(Unit)
            }
            // Window PixelCopy may offset its rectangle by surface insets.
            try { PixelCopy.request(source.window, android.graphics.Rect(target.crop), destination, { finish(it) }, handler) }
            catch (_: IllegalArgumentException) { finish(PixelCopy.ERROR_SOURCE_INVALID) }
        }
    }
}

/** Copies pixels across windows; each overlay records/draws its own GPU layer. */
@Composable
internal fun GlassOverlayScene(source: GlassWindowSource?, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    if (source == null || LocalGlassMaterialProfile.current == GlassMaterialProfile.DeferredPlayer) {
        GlassBackgroundScene(modifier, content)
        return
    }
    val view = LocalView.current
    val enabled = !LocalReduceTransparency.current && view.isHardwareAccelerated
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val capture = remember(source) { GlassWindowCapture(source) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlay by remember { mutableStateOf(Rect.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Record the crop without painting a rectangle behind the shaped material.
    val scene = rememberGlassScene(samplingEnabled = enabled, sourceDraw = {
        capture.snapshot?.let {
            drawImage(it.bitmap.asImageBitmap(), topLeft = Offset(it.origin.x.toFloat(), it.origin.y.toFloat()) - origin)
        }
    })
    val padding = with(LocalDensity.current) { 24.dp.toPx() }
    fun reposition() {
        coordinates?.takeIf { it.isAttached }?.let {
            origin = it.localToScreen(Offset.Zero)
            overlay = Rect(origin, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }
        val decor = source.window.decorView
        val location = IntArray(2)
        decor.getLocationOnScreen(location)
        capture.position(glassCaptureRegion(overlay, IntOffset(location[0], location[1]), decor.width, decor.height, padding))
    }
    DisposableEffect(capture) { onDispose { capture.close() } }
    DisposableEffect(capture, enabled, overlay, padding) {
        if (!enabled) {
            capture.suspendCapture()
            onDispose { }
        } else {
            val decor = source.window.decorView
            val observer = decor.viewTreeObserver
            val overlayObserver = view.viewTreeObserver
            val draw = ViewTreeObserver.OnDrawListener { capture.invalidate() }
            val layout = ViewTreeObserver.OnGlobalLayoutListener { reposition() }
            observer.addOnDrawListener(draw)
            observer.addOnGlobalLayoutListener(layout)
            overlayObserver.addOnGlobalLayoutListener(layout)
            reposition()
            capture.invalidate()
            onDispose {
                if (observer.isAlive) {
                    observer.removeOnDrawListener(draw)
                    observer.removeOnGlobalLayoutListener(layout)
                }
                if (overlayObserver.isAlive) overlayObserver.removeOnGlobalLayoutListener(layout)
            }
        }
    }
    LaunchedEffect(capture, enabled, lifecycle) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            capture.invalidate()
            try {
                for (ignored in capture.changed) {
                    delay((250L - (SystemClock.uptimeMillis() - last)).coerceAtLeast(0))
                    while (capture.changed.tryReceive().isSuccess) { }
                    withFrameNanos { }
                    last = SystemClock.uptimeMillis()
                    capture.capture()
                }
            } finally { capture.suspendCapture() }
        }
    }
    val snapshot = capture.snapshot
    Box(modifier.onGloballyPositioned {
        coordinates = it
        reposition()
    }) {
        Canvas(Modifier.matchParentSize().recordGlassScene(scene)) { }
        CompositionLocalProvider(LocalGlassScene provides scene,
            LocalGlassBackdrop provides if (enabled && snapshot != null) scene.backdrop else null,
            LocalGlassWindowSource provides source, LocalGlassWindowCapture provides capture,
            LocalInsideGlassSection provides false) { content() }
    }
}
