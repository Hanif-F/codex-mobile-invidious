package net.wingress.mobivious.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout

internal val PlaybackState.videoEnabled: Boolean
    get() = C.TRACK_TYPE_VIDEO !in selection?.disabledTrackTypes.orEmpty()

internal fun PlaybackState.keepVideoScreenOn(visible: Boolean): Boolean =
    visible && videoEnabled && mediaId.isNotBlank() && details?.video?.id == mediaId &&
        playWhenReady && error == null &&
        (playerState == Player.STATE_READY || playerState == Player.STATE_BUFFERING)

/** Every visible player uses the service's controller; changing layouts only changes its surface. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlaybackVideoSurface(playback: PlaybackState, controller: MediaController?, modifier: Modifier) {
    // PiP stays STARTED even though the activity is no longer RESUMED.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val keepAwake = controller != null && playback.keepVideoScreenOn(lifecycleState.isAtLeast(Lifecycle.State.STARTED))
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            }
        },
        update = {
            it.player = controller
            it.keepScreenOn = keepAwake
        },
        onRelease = {
            it.keepScreenOn = false
            // PlayerView clears only its own surface, preserving a newly attached player view.
            it.player = null
        },
        modifier = modifier,
    )
}
