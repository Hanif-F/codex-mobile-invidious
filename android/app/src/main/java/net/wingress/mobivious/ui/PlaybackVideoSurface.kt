package net.wingress.mobivious.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import android.view.SurfaceView
import android.view.accessibility.CaptioningManager
import android.widget.TextView
import android.graphics.Color as AndroidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat

internal val PlaybackState.videoEnabled: Boolean
    get() = C.TRACK_TYPE_VIDEO !in selection?.disabledTrackTypes.orEmpty()

internal fun PlaybackState.keepVideoScreenOn(visible: Boolean): Boolean =
    visible && videoEnabled && mediaId.isNotBlank() && details?.video?.id == mediaId &&
        playWhenReady && error == null &&
        (playerState == Player.STATE_READY || playerState == Player.STATE_BUFFERING)

/** Native captions and loading/error views follow the same unbacked-content policy. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerView.removeBlackUiBackgrounds() {
    setShutterBackgroundColor(AndroidColor.BLACK)
    val preferred = context.getSystemService(CaptioningManager::class.java)?.userStyle
        ?.let(CaptionStyleCompat::createFromCaptionStyle) ?: CaptionStyleCompat.DEFAULT
    subtitleView?.apply {
        setStyle(CaptionStyleCompat(preferred.foregroundColor, AndroidColor.TRANSPARENT,
            AndroidColor.TRANSPARENT,
            if (preferred.edgeType == CaptionStyleCompat.EDGE_TYPE_NONE) CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else preferred.edgeType,
            if (preferred.edgeType == CaptionStyleCompat.EDGE_TYPE_NONE) AndroidColor.BLACK else preferred.edgeColor,
            preferred.typeface))
        // Embedded cue windows and background spans must not reintroduce text boxes.
        setApplyEmbeddedStyles(false)
    }
    findViewById<TextView>(androidx.media3.ui.R.id.exo_error_message)?.apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setShadowLayer(3f, 0f, 1f, AndroidColor.BLACK)
    }
}

/** Every visible player uses the service's controller; changing layouts only changes its surface. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlaybackVideoSurface(playback: PlaybackState, controller: MediaController?, modifier: Modifier,
    onVideoSurfaceChanged: (SurfaceView?) -> Unit = {}) {
    val surfaceChanged by rememberUpdatedState(onVideoSurfaceChanged)
    // PiP stays STARTED even though the activity is no longer RESUMED.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val keepAwake = controller != null && playback.keepVideoScreenOn(lifecycleState.isAtLeast(Lifecycle.State.STARTED))
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                removeBlackUiBackgrounds()
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setEnableComposeSurfaceSyncWorkaround(true)
            }
        },
        update = {
            it.player = controller
            it.keepScreenOn = keepAwake
            surfaceChanged(it.videoSurfaceView as? SurfaceView)
        },
        onRelease = {
            surfaceChanged(null)
            it.keepScreenOn = false
            // PlayerView clears only its own surface, preserving a newly attached player view.
            it.player = null
        },
        modifier = modifier,
    )
}
