package net.wingress.mobivious

import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import net.wingress.mobivious.data.AccentPreset
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppWindowPresentationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun usablePaneKeepsPhysicalBoundsWhenLayoutDirectionChanges() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        val expected=Rect(100f,200f,600f,700f)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AppUsablePane(expected) { Box(Modifier.fillMaxSize().testTag("usable-pane")) }
            }
        }
        for(value in listOf(LayoutDirection.Ltr,LayoutDirection.Rtl)) {
            compose.runOnIdle {direction=value}
            val actual=compose.onNodeWithTag("usable-pane").fetchSemanticsNode().boundsInRoot
            assertEquals(expected.left,actual.left,1f);assertEquals(expected.top,actual.top,1f)
            assertEquals(expected.right,actual.right,1f);assertEquals(expected.bottom,actual.bottom,1f)
        }
    }
    @Test fun nativeControlsDrawIntoASoftwareCanvasInBothAppearances() {
        var night by mutableStateOf(false)
        lateinit var view: View
        compose.setContent { AppTheme(night,AccentPreset.BLUE) {
            view=LocalView.current
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("Software rendering")
                    AppSection {Text("Opaque native controls"); Switch(true,{})}
                    AppFilterChip(false,{}, {Text("Choice")})
                    OutlinedTextField("Retained text",{},label={Text("Field")})
                }
            }
        } }
        for(value in listOf(false,true)) {
            compose.runOnIdle {night=value}
            compose.runOnIdle {
                val bitmap=createBitmap(view.width,view.height)
                val canvas=Canvas(bitmap)
                assertFalse(canvas.isHardwareAccelerated)
                view.draw(canvas)
                assertEquals(appColorScheme(value).surface.toArgb(),bitmap.getPixel(1,1))
                bitmap.recycle()
            }
            compose.onNodeWithText("Retained text").assertExists()
        }
    }
}
