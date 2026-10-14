package net.wingress.mobivious

import androidx.compose.material3.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VisualSurfaceRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun artworkAvailabilityPreservesAnOpenControlMenu() {
        var artwork by mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme(colorScheme = AppTokens.light) {
                AppBackground(Modifier.fillMaxSize()) {
                    Box {
                        OverflowMenu("Artwork actions", "stable-artwork") { close ->
                            DropdownMenuItem(text = { Text("Retained choice") }, onClick = close)
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Artwork actions").performClick()
        for (source in listOf("file:///missing-ui-regression.png", null)) {
            compose.runOnIdle { artwork = source }
            compose.waitForIdle()
            compose.onNodeWithText("Retained choice").assertIsDisplayed()
        }
    }

    @Test fun roundedActionsLeaveTheContrastingContainerVisibleAtTheirCorners() {
        var dark by mutableStateOf(false)
        compose.setContent { MaterialTheme(colorScheme = if (dark) AppTokens.dark else AppTokens.light) {
            Box(Modifier.size(240.dp, 120.dp).background(Color.Magenta).testTag("container"), contentAlignment = Alignment.Center) {
                Box {
                    Box { LibraryActionButton("Retry", Modifier.width(160.dp).testTag("retry"), onClick = {}) }
                }
            }
        } }
        for (appearance in listOf(false, true)) {
            compose.runOnIdle { dark = appearance }
            val container = compose.onNodeWithTag("container")
            val outer = container.fetchSemanticsNode().boundsInRoot
            val control = compose.onNodeWithTag("retry").fetchSemanticsNode().boundsInRoot
            val image = container.captureToImage().toPixelMap()
            for (x in listOf(control.left + 2, control.right - 3)) for (y in listOf(control.top + 2, control.bottom - 3)) {
                val color = image[(x - outer.left).toInt(), (y - outer.top).toInt()]
                assertTrue("A backing rectangle covers the container in dark=$appearance: $color",
                    color.red > .7f && color.blue > .7f && color.green < .2f)
            }
        }
    }

    @Test fun wrappedToolbarTitleMasksScrollingGlyphsAndReservesMeasuredInsets() {
        var dark by mutableStateOf(false)
        var underlay by mutableStateOf(Color.Magenta)
        compose.setContent { MaterialTheme(colorScheme = if (dark) AppTokens.dark else AppTokens.light, typography = AppTokens.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                BrowseAppSurface(Modifier.width(320.dp).height(700.dp),
                    toolbar = { LibraryToolbar("History & library", back = {}, statusInset = false) }) { body, top ->
                    Box(body.background(underlay)) {
                        Text("MMMM Background letters WWWW", Modifier.padding(top = 70.dp), style = MaterialTheme.typography.displayLarge)
                        Text("First reachable row", Modifier.padding(top = top).testTag("first-row"))
                    }
                }
            }
        } }
        for (appearance in listOf(false, true)) {
            compose.runOnIdle { dark = appearance; underlay = Color.Magenta }
            compose.waitForIdle()
            Thread.sleep(600)
            val header = compose.onNodeWithTag("app-toolbar")
            val bounds = header.fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithText("History & library")
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            val before = title.captureToImage().toPixelMap()
            assertTrue("Wrapped title must fit the measured toolbar", titleBounds.bottom <= bounds.bottom)
            assertTrue("Body must reserve the complete measured toolbar", compose.onNodeWithTag("first-row").fetchSemanticsNode().boundsInRoot.top >= bounds.bottom)
            val pixels = header.captureToImage().toPixelMap()
            val background = pixels[pixels.width - 3, (titleBounds.center.y - bounds.top).toInt()]
            val foreground = (if (appearance) AppTokens.dark else AppTokens.light).onSurface
            assertTrue("Title contrast must remain >= 4.5", colorContrast(foreground, background) >= 4.5f)
            val buttonBefore = compose.onNodeWithTag("library-toolbar-back").captureToImage().toPixelMap()
            compose.runOnIdle { underlay = Color.Cyan }
            Thread.sleep(700)
            val after = title.captureToImage().toPixelMap()
            for (y in 0 until before.height step 3) for (x in 0 until before.width step 3) {
                assertEquals("Scrolled glyphs/color leak behind the title", before[x, y], after[x, y])
            }
            val buttonAfter = compose.onNodeWithTag("library-toolbar-back").captureToImage().toPixelMap()
            val sampleX = buttonBefore.width / 2
            val sampleY = (buttonBefore.height * .2f).toInt()
            val a = buttonBefore[sampleX, sampleY]; val b = buttonAfter[sampleX, sampleY]
            captureWatchScreenshot("overhaul-title-diagnostic-${if (appearance) "dark" else "light"}")
            assertEquals("Opaque chrome must ignore the scrolling artwork", a, b)
            captureWatchScreenshot("overhaul-wrapped-toolbar-${if (appearance) "dark" else "light"}")
        }
    }

    @Test fun launcherSymbolsStayInsideTheSafeRegionForEveryMask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName) as AdaptiveIconDrawable
        val out = File(context.cacheDir, "visual-fixes-icons").apply { mkdirs() }
        for ((name, foreground) in listOf("color" to icon.foreground, "themed" to requireNotNull(icon.monochrome))) {
            val symbol = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            foreground.setBounds(-108, -108, 540, 540)
            foreground.draw(Canvas(symbol))
            var painted = 0
            for (x in 0 until 432) for (y in 0 until 432) if (android.graphics.Color.alpha(symbol.getPixel(x, y)) > 16) {
                painted++
                assertTrue("$name foreground leaves the safe region", (x - 216) * (x - 216) + (y - 216) * (y - 216) < 162 * 162)
            }
            assertTrue("Missing $name symbol", painted > 1000)
            for (mask in listOf("circle", "square", "rounded-square")) {
                val image = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image)
                canvas.clipPath(Path().apply { when (mask) {
                    "circle" -> addCircle(216f, 216f, 216f, Path.Direction.CW)
                    "square" -> addRect(0f, 0f, 432f, 432f, Path.Direction.CW)
                    else -> addRoundRect(0f, 0f, 432f, 432f, 70f, 70f, Path.Direction.CW)
                } })
                if (name == "color") { icon.background.setBounds(-108, -108, 540, 540); icon.background.draw(canvas) }
                else canvas.drawColor(android.graphics.Color.rgb(50, 60, 80))
                // Reset bounds for each independent diagnostic render.
                foreground.setBounds(-108, -108, 540, 540)
                foreground.draw(canvas)
                assertEquals("Missing foreground in $name $mask", symbol.getPixel(216, 216), image.getPixel(216, 216))
                File(out, "$name-$mask.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun multipartToolbarPlacesControlsBelowTheSafeTitle() {
        compose.setContent { MaterialTheme(colorScheme = AppTokens.light, typography = AppTokens.typography) {
            BrowseAppSurface(Modifier.width(320.dp).height(700.dp), toolbar = {
                BrowsePageTitle("Channel", back = {})
                Box(Modifier.fillMaxWidth().height(56.dp).testTag("channel-search"))
                Box(Modifier.fillMaxWidth().height(48.dp).testTag("channel-tabs"))
            }) { body, top -> Box(body.padding(top = top).testTag("list-content")) }
        } }
        val title = compose.onNodeWithText("Channel").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("channel-search").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("channel-tabs").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("app-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("Search covers the title", search.top >= title.bottom)
        assertTrue("Tabs cover search", tabs.top >= search.bottom)
        assertTrue("Toolbar backdrop excludes controls", header.bottom >= tabs.bottom)
    }
}
