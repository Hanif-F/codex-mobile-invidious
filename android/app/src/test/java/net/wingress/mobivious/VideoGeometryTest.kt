package net.wingress.mobivious

import net.wingress.mobivious.player.VideoGeometry
import org.junit.Assert.*
import org.junit.Test

class VideoGeometryTest {
    @Test fun portraitVideosUseMoreWatchSpaceAndRespectCommentsCap() {
        val portrait = VideoGeometry("short", 1080, 1920)
        assertEquals(490f, portrait.embeddedHeight(400f, 700f, false), .01f)
        assertEquals(280f, portrait.embeddedHeight(400f, 700f, true), .01f)
        assertEquals(225f, VideoGeometry("wide", 1920, 1080).embeddedHeight(400f, 700f, false), .01f)
        assertEquals(1, portrait.orientation)
    }
    @Test fun squareAnamorphicUnknownAndInvalidDimensionsRemainUsable() {
        assertEquals(0, VideoGeometry("square", 800, 800).orientation)
        assertEquals(16f / 9f, VideoGeometry("anamorphic", 720, 576, 64f / 45f).layoutRatio, .001f)
        for (geometry in listOf(VideoGeometry(), VideoGeometry("zero", 800, 0), VideoGeometry("nan", 800, 800, Float.NaN))) {
            assertNull(geometry.ratio); assertEquals(16f / 9f, geometry.layoutRatio, .001f); assertEquals(0, geometry.orientation)
        }
        assertEquals(-1, VideoGeometry("wide", 2560, 1080).orientation)
    }
    @Test fun pipUsesTheActualRatioWithinPlatformLimits() {
        assertEquals(9f / 16f, VideoGeometry("short", 1080, 1920).pipRatio, .0001f)
        assertEquals(1f, VideoGeometry("square", 500, 500).pipRatio, .0001f)
        assertEquals(2.39f, VideoGeometry("extreme", 10000, 100).pipRatio, .0001f)
        assertEquals(1f / 2.39f, VideoGeometry("extreme", 100, 10000).pipRatio, .0001f)
        for (geometry in listOf(VideoGeometry("tall", 100, 10000), VideoGeometry("wide", 10000, 100), VideoGeometry("portrait", 9, 16), VideoGeometry("near-boundary", 41841, 100000))) {
            val (n, d) = geometry.pipFraction
            val ratio = n.toDouble() / d
            assertTrue(ratio >= 1.0 / 2.39 && ratio <= 2.39)
            assertEquals(geometry.pipRatio.toDouble(), ratio, .00001)
        }
    }
}
