package net.wingress.mobivious

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GlassRefractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sharpStripesBendAcrossControlSectionAndCircularGlass() {
        lateinit var scene: GlassScene
        var density = 1f
        var section by mutableStateOf(false)
        var circle by mutableStateOf(false)
        var dark by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalAdaptiveControlColors provides true) {
            density = LocalDensity.current.density
            MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light) {
                scene = rememberGlassScene()
                Box(Modifier.size(320.dp, 260.dp)) {
                    Canvas(Modifier.matchParentSize().recordGlassScene(scene)) {
                        val stripe = 18.dp.toPx()
                        for (x in 0..(size.width / stripe).toInt()) {
                            drawRect(if (x % 2 == 0) Color(0xFF333333) else Color(0xFFDDDDDD),
                                Offset(x * stripe, 0f), Size(stripe, size.height))
                        }
                    }
                    CompositionLocalProvider(LocalGlassScene provides scene, LocalGlassBackdrop provides scene.backdrop) {
                        GlassSurface(Modifier.align(Alignment.Center).size(if (circle) 180.dp else 272.dp, 180.dp)
                            .testTag("lens"), shape = if (circle) CircleShape else Liquid.card,
                            role = if (section) GlassMaterialRole.Section else GlassMaterialRole.Control) { }
                    }
                }
            }
            }
        }
        for ((isSection, isCircle, isDark) in listOf(Triple(false, false, true), Triple(true, false, true),
            Triple(false, true, true), Triple(false, false, false))) {
            compose.runOnIdle { section = isSection; circle = isCircle; dark = isDark }
            compose.waitUntil(5_000) { scene.captures > 0 }
            compose.waitForIdle()
            val node = compose.onNodeWithTag("lens")
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val pixels = node.captureToImage().toPixelMap()
            val y = pixels.height / 2
            val transitions = (8 until pixels.width - 8).filter { x -> abs(pixels[x, y].red - pixels[x - 1, y].red) > .08f }
            assertTrue("Transmitted stripes must retain sharp transitions: section=$isSection circle=$isCircle dark=$isDark",
                transitions.size >= 4)
            // The unmodified source has transitions at multiples of 18dp. At least
            // one transmitted transition must move away from that source position.
            val stripe = 18f * density
            val moved = transitions.any { x ->
                val position = bounds.left - scene.bounds.left + x
                val nearest = kotlin.math.round(position / stripe) * stripe
                abs(position - nearest) > 2f
            }
            assertTrue("Sharp background features must actually refract", moved)
        }
        captureWatchScreenshot("refraction-sharp-stripes")
    }

    @Test fun refractionLeavesForegroundGlyphsUndistortedAndPlayerProfileFrozen() {
        var deferred by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = Liquid.dark) {
                GlassBackgroundScene(Modifier.size(320.dp, 240.dp)) {
                    CompositionLocalProvider(LocalGlassMaterialProfile provides
                        if (deferred) GlassMaterialProfile.DeferredPlayer else GlassMaterialProfile.Refractive) {
                        GlassSurface(Modifier.padding(16.dp).fillMaxWidth().height(100.dp)) {
                            GlassLabel("Sharp foreground", Modifier.align(Alignment.Center).testTag("glyphs"))
                        }
                    }
                }
            }
        }
        val clear = compose.onNodeWithTag("glyphs").captureToImage().toPixelMap()
        compose.runOnIdle { deferred = true }
        val player = compose.onNodeWithTag("glyphs").captureToImage().toPixelMap()
        assertEquals(clear.width, player.width)
        assertEquals(clear.height, player.height)
        var glyphs = 0
        for (y in 0 until clear.height) for (x in 0 until clear.width) {
            val clearInk = clear[x, y].red > .94f
            val playerInk = player[x, y].red > .94f
            assertEquals("Foreground moved at $x,$y", clearInk, playerInk)
            if (clearInk) glyphs++
        }
        assertTrue(glyphs > 100)
    }

    @Test fun dialogCopiesActualOriginatingPixelsRefreshesAndStopsWhenDismissed() {
        var open by mutableStateOf(true)
        var color by mutableStateOf(Color(0xFF1040E0))
        var width by mutableStateOf(240.dp)
        var capture: GlassWindowCapture? = null
        compose.setContent {
            CompositionLocalProvider(LocalAdaptiveControlColors provides true) {
            MaterialTheme(colorScheme = Liquid.dark) {
                val source = rememberGlassWindowSource()
                Box(Modifier.fillMaxSize().background(color))
                if (open) Dialog({ open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    GlassOverlayScene(source, Modifier.size(width, 200.dp)) {
                        capture = LocalGlassWindowCapture.current
                        GlassSurface(Modifier.fillMaxSize().testTag("dialog-lens"), Liquid.card,
                            role = GlassMaterialRole.Section) { GlassLabel("Actual page", Modifier.align(Alignment.Center)) }
                    }
                }
            }
            }
        }
        compose.waitUntil(5_000) { capture?.captures?.let { it > 0 } == true }
        fun colorAtCenter(): Color = compose.onNodeWithTag("dialog-lens").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 4] }
        val blue = colorAtCenter()
        assertTrue("Dialog must transmit the real blue page", blue.blue > blue.red + .1f)
        val first = capture!!.captures
        compose.runOnIdle { color = Color(0xFFE04010) }
        compose.waitUntil(5_000) { capture!!.captures > first }
        val red = colorAtCenter()
        assertTrue("Dialog must refresh from the real red page", red.red > red.blue + .1f)
        val transparentCorner = compose.onNodeWithTag("dialog-lens").captureToImage().toPixelMap()[0, 0]
        // The elevation shadow contributes a few alpha levels; a painted crop is opaque.
        assertTrue("The crop must never paint a rectangle outside rounded glass: $transparentCorner", transparentCorner.alpha < .03f)
        val bitmap = capture!!.snapshot!!.bitmap
        assertTrue("Only a cropped region is captured", bitmap.width < compose.activity.window.decorView.width)
        compose.runOnIdle { width = 180.dp }
        compose.waitUntil(5_000) {
            capture!!.snapshot?.bitmap?.let { it.width == capture!!.region!!.crop.width() && it.width < bitmap.width } == true
        }
        Thread.sleep(650)
        val settled = capture!!.captures
        Thread.sleep(650)
        assertEquals("No idle capture loop", settled, capture!!.captures)
        assertNotNull("The resized dialog must retain its source", capture!!.snapshot)
        val resized = colorAtCenter()
        assertTrue("Resizing must preserve real page transmission: $resized", resized.red > resized.blue + .1f)
        captureWatchScreenshot("refraction-dialog-real-page")
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        assertNull(capture!!.snapshot)
        assertTrue(bitmap.isRecycled)
    }

    @Test fun reducedTransparencyAndDeferredPlayerSkipWindowReadback() {
        var reduced by mutableStateOf(true)
        var deferred by mutableStateOf(false)
        var capture: GlassWindowCapture? = null
        compose.setContent {
            MaterialTheme(colorScheme = Liquid.dark) {
                val source = rememberGlassWindowSource()
                Box(Modifier.fillMaxSize().background(Color.Blue))
                CompositionLocalProvider(LocalReduceTransparency provides reduced,
                    LocalGlassMaterialProfile provides if (deferred) GlassMaterialProfile.DeferredPlayer else GlassMaterialProfile.Refractive) {
                    Dialog({}) {
                        GlassOverlayScene(source, Modifier.size(180.dp, 120.dp)) {
                            capture = LocalGlassWindowCapture.current
                            GlassSurface(Modifier.fillMaxSize().testTag("fallback")) { }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val opaque = capture!!
        assertEquals(0, opaque.captures)
        assertNull(opaque.snapshot)
        compose.runOnIdle { reduced = false; deferred = true }
        compose.waitForIdle()
        assertNull("Deferred player keeps its original independent backdrop", capture)
        compose.runOnIdle { deferred = false }
        compose.waitUntil(5_000) { capture?.captures?.let { it > 0 } == true }
        val active = capture!!
        compose.runOnIdle { reduced = true }
        compose.waitForIdle()
        val stopped = active.captures
        Thread.sleep(650)
        assertEquals(stopped, active.captures)
        assertNull(active.snapshot)
    }

    @Test fun captureGeometryIncludesInsetsAndClipsAtSourceEdges() {
        val region = glassCaptureRegion(Rect(90f, 180f, 250f, 320f), IntOffset(100, 200), 300, 400, 24f)!!
        assertEquals(android.graphics.Rect(0, 0, 174, 144), region.crop)
        assertEquals(IntOffset(100, 200), region.origin)
        assertNull(glassCaptureRegion(Rect(500f, 700f, 600f, 800f), IntOffset(100, 200), 300, 400, 0f))
    }
}
