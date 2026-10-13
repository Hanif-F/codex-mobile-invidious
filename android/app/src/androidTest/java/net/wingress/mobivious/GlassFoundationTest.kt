package net.wingress.mobivious

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassFoundationTest {
    @get:Rule val compose = createComposeRule()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun nativeCaptionsIgnoreBlackCueWindowsAndBackgroundSpans() {
        val background = Color(0xFF6F9EBA)
        compose.setContent {
            Box(Modifier.size(320.dp, 180.dp).background(background).testTag("native-caption")) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                    androidx.media3.ui.PlayerView(context).apply {
                        removeBlackUiBackgrounds()
                        useController = false
                        // This test supplies a colored picture instead of a decoder frame.
                        videoSurfaceView?.visibility = android.view.View.GONE
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter).visibility = android.view.View.GONE
                        subtitleView!!.apply {
                            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                            val text = android.text.SpannableString("CAPTION          CAPTION").apply {
                                setSpan(android.text.style.BackgroundColorSpan(android.graphics.Color.BLACK),
                                    0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(text)
                                .setWindowColor(android.graphics.Color.BLACK).build()))
                        }
                    }
                }, modifier = Modifier.matchParentSize())
            }
        }
        captureWatchScreenshot("no-black-background-native-caption")
        val pixels = compose.onNodeWithTag("native-caption").captureToImage().toPixelMap()
        var glyphs = 0
        for (y in pixels.height / 2 until pixels.height) {
            // The wide gap between the two words must expose the picture, even
            // when the source caption explicitly requests black windows/spans.
            assertEquals(background.red, pixels[pixels.width / 2, y].red, .01f)
            assertEquals(background.green, pixels[pixels.width / 2, y].green, .01f)
            for (x in 0 until pixels.width) if (pixels[x, y].red > .9f) glyphs++
        }
        assertTrue("The caption must still be visible", glyphs > 100)
    }

    @Test fun labelsIconsAndMenuSlotsNeverPaintTheirOwnBackgrounds() {
        var dark by mutableStateOf(true)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light, typography = Liquid.typography) {
                Column(Modifier.fillMaxSize().background(Color(0xFF8099AB))) {
                    // Before a scene has statistics, the old helpers painted particularly
                    // obvious rectangles/discs. Compare every pixel with unbacked content.
                    GlassForeground {
                        Text("Popular\nTrending", Modifier.testTag("plain-label"))
                        GlassLabel("Popular\nTrending", Modifier.testTag("glass-label"))
                        Icon(Icons.Default.Check, null, Modifier.size(32.dp).testTag("plain-icon"))
                        GlassIcon(Icons.Default.Check, null, Modifier.size(32.dp).testTag("glass-icon"))
                        DropdownMenuItem(text = { Text("Subscriptions") }, onClick = {},
                            leadingIcon = { Icon(Icons.Default.Check, null) },
                            trailingIcon = { Icon(Icons.Default.Check, null) },
                            modifier = Modifier.width(280.dp).testTag("plain-menu"))
                        GlassMenuItem(text = { GlassLabel("Subscriptions") }, onClick = {},
                            leadingIcon = { GlassIcon(Icons.Default.Check, null) },
                            trailingIcon = { GlassIcon(Icons.Default.Check, null) },
                            modifier = Modifier.width(280.dp).testTag("glass-menu"))
                    }
                }
            }
        }
        for (appearance in listOf(true, false)) {
            compose.runOnIdle { dark = appearance }
            for (kind in listOf("label", "icon", "menu")) {
                val plain = compose.onNodeWithTag("plain-$kind").captureToImage().toPixelMap()
                val glass = compose.onNodeWithTag("glass-$kind").captureToImage().toPixelMap()
                assertEquals(plain.width, glass.width)
                assertEquals(plain.height, glass.height)
                for (y in 0 until plain.height) for (x in 0 until plain.width) {
                    assertEquals("Extra paint behind $kind in dark=$appearance at $x,$y", plain[x, y], glass[x, y])
                }
            }
        }
        captureWatchScreenshot("no-black-background-content")
    }

    @Test fun generatedEnvironmentsKeepReadableLabelsWithoutAdditionalBacking() {
        var dark by mutableStateOf(true)
        var actual = GlassContentColors.light
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light, typography = Liquid.typography) {
                GlassBackgroundScene(Modifier.size(320.dp, 640.dp)) {
                    GlassSurface(Modifier.padding(24.dp).fillMaxWidth().height(100.dp)) {
                        actual = LocalGlassContentColors.current
                        GlassLabel("Your library", Modifier.padding(20.dp).testTag("generated-label"), color = actual.secondary)
                    }
                }
            }
        }
        for (appearance in listOf(true, false)) {
            compose.runOnIdle { dark = appearance }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("generated-label").captureToImage().toPixelMap()
            assertTrue("Designed lighting must keep its quiet labels readable",
                glassContrast(actual.secondary, pixels[pixels.width / 2, pixels.height - 2]) >= 4.5f)
        }
    }

    @Test fun navigationLabelsRemainReadableOverNarrowArtworkBands() {
        var stripe by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
        lateinit var scene: GlassScene
        compose.setContent { MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
            scene = rememberGlassScene()
            Box(Modifier.size(320.dp, 640.dp)) {
                Box(Modifier.matchParentSize().recordGlassScene(scene).background(Color.Black)
                    .then(Modifier.drawBehind {
                        drawRect(Color.White, androidx.compose.ui.geometry.Offset(0f, stripe.top),
                            androidx.compose.ui.geometry.Size(size.width, stripe.height))
                    }))
                CompositionLocalProvider(LocalGlassScene provides scene, LocalGlassBackdrop provides scene.backdrop) {
                    FloatingNavigation(ShellDestination.DISCOVER, false, true,
                        Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {}
                }
            }
        } }
        val label = compose.onNodeWithText("Subscriptions", useUnmergedTree = true)
        val bounds = label.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { stripe = bounds.translate(androidx.compose.ui.geometry.Offset(0f, -scene.bounds.top)) }
        compose.waitForIdle()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        label.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color
        val pixels = label.captureToImage().toPixelMap()
        assertTrue("A narrow artwork band must not defeat label contrast",
            glassContrast(foreground, pixels[pixels.width / 2, pixels.height - 2]) >= 4.5f)
        captureWatchScreenshot("overhaul-navigation-artwork-band")
    }

    @Test fun sceneDownsamplesChangesAndStopsWhenSettled() {
        var background by mutableStateOf(Color.Black)
        lateinit var scene: GlassScene
        var actual by mutableStateOf(GlassContentColors.light)
        compose.setContent {
            MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
                CompositionLocalProvider(LocalAdaptiveControlColors provides true) {
                scene = rememberGlassScene()
                Box(Modifier.size(300.dp, 220.dp)) {
                    Box(Modifier.matchParentSize().recordGlassScene(scene).background(background))
                    CompositionLocalProvider(LocalGlassBackdrop provides scene.backdrop, LocalGlassScene provides scene) {
                        GlassSurface(Modifier.padding(24.dp).fillMaxWidth().height(80.dp).testTag("material")) {
                            actual = LocalGlassContentColors.current
                            Text("Adaptive secondary label", color = actual.secondary, modifier = Modifier.padding(20.dp))
                        }
                    }
                }
                }
            }
        }
        compose.waitUntil(5_000) { scene.captures > 0 }
        assertEquals(GlassScene.WIDTH * GlassScene.HEIGHT, scene.samples!!.size)
        val darkPixels = compose.onNodeWithTag("material").captureToImage().toPixelMap()
        val darkInterior = darkPixels[darkPixels.width / 2, darkPixels.height - 20]
        assertTrue(glassContrast(actual.secondary, darkInterior) >= 4.5f)
        compose.runOnIdle { background = Color.White }
        compose.waitUntil(5_000) { actual.primary == GlassContentColors.dark.primary }
        val brightPixels = compose.onNodeWithTag("material").captureToImage().toPixelMap()
        val brightInterior = brightPixels[brightPixels.width / 2, brightPixels.height - 20]
        assertTrue("Interior must transmit its changing source", brightInterior.red - darkInterior.red > .5f)
        assertTrue(glassContrast(actual.secondary, brightInterior) >= 4.5f)
        // Drain any redraw generated by the polarity switch before measuring the idle scene.
        Thread.sleep(600)
        val settled = scene.captures
        Thread.sleep(600)
        assertEquals("A settled scene must not poll", settled, scene.captures)
        captureWatchScreenshot("overhaul-foundation-bright-adaptive")
    }

    @Test fun nestedPageRecordingPreservesForegroundsAndCornerTransparency() {
        var opaque by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
                CompositionLocalProvider(LocalReduceTransparency provides opaque) {
                val shell = rememberGlassScene()
                GlassBackgroundScene(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().recordGlassScene(shell)) {
                        BrowseGlassSurface(toolbar = { LibraryToolbar("Settings", statusInset = false) }) { body, top ->
                            Column(body.padding(top = top).padding(20.dp)) {
                                Text("Reading foreground", Modifier.testTag("reading"))
                                GlassSection { Text("Section foreground", Modifier.testTag("section-label")) }
                            }
                        }
                    }
                }
                }
            }
        }
        for (reduced in listOf(false, true)) {
            compose.runOnIdle { opaque = reduced }
            compose.waitForIdle()
            Thread.sleep(700)
            for (tag in listOf("reading", "section-label")) {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                var brightest = 0f
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) brightest = maxOf(brightest, pixels[x, y].red)
                assertTrue("$tag foreground was tinted by its recording: $brightest", brightest > .90f)
            }
        }
        captureWatchScreenshot("overhaul-foundation-nested-page")
    }
    @Test fun softwareAndReducedTransparencyUseReadablePlatesWithoutReadback() {
        var software by mutableStateOf(true)
        var background by mutableStateOf(Color.Black)
        lateinit var scene: GlassScene
        var actual = GlassContentColors.light
        compose.setContent {
            val hardwareView = androidx.compose.ui.platform.LocalView.current
            val softwareView = remember { android.view.View(hardwareView.context) }
            MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalView provides if (software) softwareView else hardwareView,
                    LocalReduceTransparency provides !software) {
                    scene = rememberGlassScene()
                    Box(Modifier.size(300.dp, 220.dp)) {
                        Box(Modifier.matchParentSize().recordGlassScene(scene).background(background))
                        CompositionLocalProvider(LocalGlassScene provides scene, LocalGlassBackdrop provides scene.backdrop) {
                            GlassSurface(Modifier.padding(24.dp).fillMaxWidth().height(80.dp).testTag("fallback")) {
                                actual = LocalGlassContentColors.current
                                GlassLabel("Readable fallback", Modifier.align(androidx.compose.ui.Alignment.Center), color = actual.secondary)
                            }
                        }
                    }
                }
            }
        }
        for (isSoftware in listOf(true, false)) {
            compose.runOnIdle { software = isSoftware; background = Color.Black }
            val black = compose.onNodeWithTag("fallback").captureToImage().toPixelMap()
            compose.runOnIdle { background = Color.White }
            val white = compose.onNodeWithTag("fallback").captureToImage().toPixelMap()
            val x = black.width / 2; val y = black.height / 5
            assertEquals("Fallback must be independent of the recording", black[x, y], white[x, y])
            assertTrue(glassContrast(actual.secondary, white[x, y]) >= 4.5f)
            assertEquals("Fallback does not perform a readback", 0, scene.captures)
            captureWatchScreenshot("overhaul-fallback-${if (isSoftware) "software-policy" else "reduce-transparency"}")
        }
    }

    @Test fun pressCancellationAndRapidRepetitionKeepOneSemanticAction() {
        var clicks = 0
        compose.setContent {
            MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
                GlassBackgroundScene(Modifier.size(300.dp, 220.dp)) {
                    GlassIconButton(androidx.compose.material.icons.Icons.Default.Check, "Confirm",
                        Modifier.align(androidx.compose.ui.Alignment.Center).testTag("press-control")) { clicks++ }
                }
            }
        }
        val control = compose.onNodeWithTag("press-control")
        control.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        control.performTouchInput { down(center); advanceEventTime(80); moveTo(androidx.compose.ui.geometry.Offset(-80f, -80f)); up() }
        compose.runOnIdle { assertEquals("Canceled presses must not click", 0, clicks) }
        repeat(4) { control.performClick() }
        compose.runOnIdle { assertEquals(4, clicks) }
        control.performTouchInput { down(center); advanceEventTime(120) }
        captureWatchScreenshot("overhaul-control-pressed")
        control.performTouchInput { cancel() }
        compose.waitForIdle()
        control.assertHasClickAction()
        compose.runOnIdle { assertEquals("Cancellation must remain reversible", 4, clicks) }
    }

}
