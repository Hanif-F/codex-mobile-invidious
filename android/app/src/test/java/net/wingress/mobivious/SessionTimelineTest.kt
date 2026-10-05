package net.wingress.mobivious

import androidx.media3.common.*
import net.wingress.mobivious.player.SessionTimeline
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SessionTimelineTest {
    private fun timeline(live: Boolean) = object : Timeline() {
        override fun getWindowCount() = 1
        override fun getPeriodCount() = 1
        override fun getIndexOfPeriod(uid: Any) = if (uid == "period") 0 else C.INDEX_UNSET
        override fun getUidOfPeriod(index: Int): Any = "period"
        override fun getPeriod(index: Int, period: Period, setIds: Boolean): Period =
            period.set("period", "period", 0, 120_000_000, 0)
        override fun getWindow(index: Int, window: Window, projection: Long): Window = window.set(
            "window", MediaItem.Builder().setMediaId("fixture").build(), "manifest", 1000, 2000, 3000,
            true, live, if (live) MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(5000).build() else null,
            0, 120_000_000, 0, 0, 0)
    }

    @Test fun hlsVodCanBecomeASessionPlaylistWithoutInventingLiveState() {
        val wrapped = SessionTimeline(timeline(false))
        val state = SimpleBasePlayer.State.Builder().setPlaylist(wrapped, Tracks.EMPTY, MediaMetadata.EMPTY).build()
        val item = state.playlist.single()
        assertNull(item.liveConfiguration)
        assertEquals(C.TIME_UNSET, item.windowStartTimeMs)
        assertEquals("manifest", item.manifest)
        assertTrue(item.isSeekable)
        assertEquals(120_000_000L, item.durationUs)
        assertEquals("period", wrapped.getUidOfPeriod(0))
    }

    @Test fun liveWindowTimingAndConfigurationStayIntact() {
        val wrapped = SessionTimeline(timeline(true))
        val item = SimpleBasePlayer.State.Builder().setPlaylist(wrapped, Tracks.EMPTY, MediaMetadata.EMPTY).build().playlist.single()
        assertEquals(1000L, item.presentationStartTimeMs)
        assertEquals(2000L, item.windowStartTimeMs)
        assertEquals(3000L, item.elapsedRealtimeEpochOffsetMs)
        assertEquals(5000L, item.liveConfiguration!!.targetOffsetMs)
        assertTrue(item.isDynamic)
    }
}
