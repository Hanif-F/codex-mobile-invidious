package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.CaptionAppearance
import net.wingress.mobivious.ui.CaptionPreview
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CaptionAppearancePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitColorsAndWindowsRenderAndResetRemovesTheBoxes() {
        var style by mutableStateOf(CaptionAppearance(size = 200, textColor = "#FF00FF", backgroundColor = "#0000FF",
            backgroundOpacity = 100, windowColor = "#FFFF00", windowOpacity = 100, edge = "none"))
        compose.setContent { Box(Modifier.width(320.dp)) { CaptionPreview(style) } }
        fun count(red: Boolean, green: Boolean, blue: Boolean): Int {
            val pixels = compose.onNodeWithTag("caption-preview").captureToImage().toPixelMap()
            var found = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val p = pixels[x, y]
                if ((if (red) p.red > .8f else p.red < .2f) && (if (green) p.green > .8f else p.green < .2f) &&
                    (if (blue) p.blue > .8f else p.blue < .2f)) found++
            }
            return found
        }
        assertTrue(count(true, false, true) > 100)
        assertTrue(count(false, false, true) > 100)
        assertTrue(count(true, true, false) > 100)
        compose.runOnIdle { style = CaptionAppearance() }
        assertEquals(0, count(true, true, false)); assertEquals(0, count(false, false, true))
        assertTrue(count(true, true, true) > 50)
    }

    @Test fun transparentTextAndLargeRtlMultilinePreviewRemainUsable() {
        var style by mutableStateOf(CaptionAppearance(size = 400, textColor = "#FF00FF", edge = "none"))
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.width(360.dp)) { CaptionPreview(style) }
        } }
        captureWatchScreenshot("caption-large-rtl")
        val visible = compose.onNodeWithTag("caption-preview").captureToImage().toPixelMap()
        var colored = 0
        for (y in 0 until visible.height) for (x in 0 until visible.width) if (visible[x,y].red > .8f && visible[x,y].blue > .8f) colored++
        assertTrue(colored > 100)
        compose.runOnIdle { style = style.copy(textOpacity = 0) }
        val hidden = compose.onNodeWithTag("caption-preview").captureToImage().toPixelMap()
        for (y in 0 until hidden.height step 4) for (x in 0 until hidden.width step 4)
            assertTrue("Transparent text must leave the preview picture visible", hidden[x,y].red < .8f)
    }
}
