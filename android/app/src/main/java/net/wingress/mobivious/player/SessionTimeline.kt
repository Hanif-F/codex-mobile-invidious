package net.wingress.mobivious.player

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ForwardingTimeline

/** Media3's session snapshot requires epoch timing to belong to a live window. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class SessionTimeline(timeline: Timeline) : ForwardingTimeline(timeline) {
    override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
        super.getWindow(windowIndex, window, defaultPositionProjectionUs)
        // HLS VOD may expose a window start time even though it has no live configuration.
        // Preserve the manifest, periods and seek geometry, and all actual live-window timing.
        if (window.liveConfiguration == null) {
            window.presentationStartTimeMs = C.TIME_UNSET
            window.windowStartTimeMs = C.TIME_UNSET
            window.elapsedRealtimeEpochOffsetMs = C.TIME_UNSET
        }
        return window
    }
}
