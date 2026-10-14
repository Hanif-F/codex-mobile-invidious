package net.wingress.mobivious

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Rect
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Test

class AmbientPaletteTest {
    @Test fun lightFollowsTheFittedPictureInsteadOfLetterboxBarsInTallWideLayouts() {
        val picture = ambientPictureBounds(Rect(0f, 0f, 1_000f, 2_000f), 16f / 9f)
        assertEquals(1_000f, picture.width, .01f)
        assertEquals(562.5f, picture.height, .01f)
        assertEquals(1_000f, picture.center.y, .01f)
        val portrait = ambientPictureBounds(Rect(0f, 0f, 1_000f, 500f), 9f / 16f)
        assertEquals(281.25f, portrait.width, .01f)
        assertEquals(500f, portrait.height, .01f)
        assertEquals(500f, portrait.center.x, .01f)
    }

    @Test fun spatialSamplingPreservesAllNineRegionsIncludingUnevenDimensions() {
        val colors = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(),
            0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt())
        val width = 32; val height = 18
        val pixels = IntArray(width * height)
        for (zone in colors.indices) for (y in zone / 3 * height / 3 until (zone / 3 + 1) * height / 3)
            for (x in zone % 3 * width / 3 until (zone % 3 + 1) * width / 3) pixels[y * width + x] = colors[zone]
        assertEquals(colors, AmbientPalette.fromPixels(pixels, width, height).colors)
    }

    @Test fun averagingDoesNotInventColorInDarkOrGrayscaleFootage() {
        for (value in listOf(0, 16, 128, 255)) {
            val color = (255 shl 24) or (value shl 16) or (value shl 8) or value
            val palette = AmbientPalette.fromPixels(IntArray(576) { color }, 32, 18)
            assertTrue(palette.colors.all { kotlin.math.abs((it and 255) - value) <= 1 })
            assertTrue(palette.colors.all { (it and 255) == ((it ushr 8) and 255) && (it and 255) == ((it ushr 16) and 255) })
        }
    }

    @Test fun vividIlluminationBoundsSupportingTextContrastInBothThemes() {
        for (color in listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan)) {
            for (dark in listOf(false, true)) {
                val scheme = if (dark) appColorScheme(true) else appColorScheme(false)
                val background = ambientIllumination(color, scheme.surface, dark)
                    .copy(alpha = if (dark) .55f else .30f).compositeOver(scheme.surface)
                val foreground = scheme.onSurfaceVariant.luminance()
                val actual = background.luminance()
                val contrast = (maxOf(foreground, actual) + .05f) / (minOf(foreground, actual) + .05f)
                assertTrue("dark=$dark color=$color contrast=$contrast", contrast >= 4.5f)
            }
        }
    }

    @Test fun onlyOneCopyCanOwnTheBitmapAndRequestsAreLimitedToFourPerSecond() {
        val gate = AmbientCaptureGate()
        val token = gate.begin(0)!!
        assertNull(gate.begin(500))
        assertTrue(gate.finish(token, 10, true))
        assertNull(gate.begin(249))
        assertNotNull(gate.begin(250))
    }

    @Test fun transientFailuresBackOffWithoutPublishingAPalette() {
        val gate = AmbientCaptureGate()
        assertFalse(gate.finish(gate.begin(0)!!, 20, false))
        assertNull(gate.begin(1_019))
        assertNotNull(gate.begin(1_020))
    }

    @Test fun canceledAndReplacedSourcesCannotPublishOrReuseAnInFlightBitmap() {
        val gate = AmbientCaptureGate()
        val old = gate.begin(0)!!
        gate.invalidate()
        assertNull(gate.begin(500))
        assertFalse(gate.finish(old, 501, true))
        val fresh = gate.begin(502)!!
        assertTrue(gate.finish(fresh, 503, true))
    }

    @Test fun closingKeepsThePendingCopyOwnedUntilItsCallbackAndRejectsFutureCopies() {
        val gate = AmbientCaptureGate()
        val token = gate.begin(0)!!
        gate.close()
        assertTrue(gate.inFlight)
        assertFalse(gate.finish(token, 500, true))
        assertFalse(gate.inFlight)
        assertNull(gate.begin(1_000))
    }
}
