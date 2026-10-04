package net.wingress.mobivious

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import net.wingress.mobivious.data.Video
import net.wingress.mobivious.data.VideoDetails
import net.wingress.mobivious.ui.PlaybackState
import net.wingress.mobivious.ui.keepVideoScreenOn
import net.wingress.mobivious.ui.videoEnabled
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackVisibilityTest {
    private val video = Video("testvideo01", "Visible video")
    private val active = PlaybackState(
        details = VideoDetails(video, "", "", "", "", emptyList(), emptyList()),
        mediaId = video.id, playWhenReady = true, playing = true, playerState = Player.STATE_READY,
    )

    @Test fun visiblePlaybackAndBufferingKeepAwakeEvenWithoutAdvancingFrames() {
        assertTrue(active.keepVideoScreenOn(visible = true))
        assertTrue(active.copy(playing = false, buffering = true, playerState = Player.STATE_BUFFERING).keepVideoScreenOn(visible = true))
    }

    @Test fun pauseIdleCompletionAndErrorsAllowScreenTimeout() {
        assertFalse(active.copy(playing = false, playWhenReady = false).keepVideoScreenOn(visible = true))
        assertFalse(active.copy(playWhenReady = false, playerState = Player.STATE_BUFFERING).keepVideoScreenOn(visible = true))
        assertFalse(active.copy(playerState = Player.STATE_IDLE).keepVideoScreenOn(visible = true))
        assertFalse(active.copy(playerState = Player.STATE_ENDED).keepVideoScreenOn(visible = true))
        assertFalse(active.copy(error = "Playback failed").keepVideoScreenOn(visible = true))
    }

    @Test fun audioOnlyModeAllowsTimeoutAndReturningToVideoRestoresWake() {
        val selection = TrackSelectionParameters.Builder().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        val audio = active.copy(selection = selection)
        assertFalse(audio.videoEnabled)
        assertFalse(audio.keepVideoScreenOn(visible = true))
        val videoAgain = audio.copy(selection = selection.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build())
        assertTrue(videoAgain.videoEnabled)
        assertTrue(videoAgain.keepVideoScreenOn(visible = true))
    }

    @Test fun hiddenPlaybackAllowsTimeoutAndVisibilityRestoresWake() {
        assertFalse(active.keepVideoScreenOn(visible = false))
        assertTrue(active.keepVideoScreenOn(visible = true))
    }

    @Test fun closedMissingAndStaleMediaCannotHoldTheScreenAwake() {
        assertFalse(active.copy(details = null).keepVideoScreenOn(visible = true))
        assertFalse(active.copy(mediaId = "").keepVideoScreenOn(visible = true))
        assertFalse(active.copy(mediaId = "testvideo02").keepVideoScreenOn(visible = true))
        assertFalse(PlaybackState().keepVideoScreenOn(visible = true))
    }
}
