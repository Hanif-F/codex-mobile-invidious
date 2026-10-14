package net.wingress.mobivious
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.AccentPreset
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class OpaqueSurfacesPresentationTest {
 @get:Rule val compose=createComposeRule()
 @Test fun ordinarySurfacesRemainOpaqueOverChangingScenery() {
  var scenery by mutableStateOf(Color.Black)
  var dark by mutableStateOf(false)
  compose.setContent { AppTheme(dark,AccentPreset.BLUE) {
   Box(Modifier.size(320.dp,240.dp).background(scenery)) {
    AppSurface(Modifier.padding(16.dp).fillMaxSize().testTag("opaque-surface")) {Text("Readable controls",Modifier.padding(24.dp))}
   }
  } }
  for(night in listOf(false,true)) for(color in listOf(Color.Black,Color.White,Color.Magenta,Color.Green)) {
   compose.runOnIdle {dark=night;scenery=color}
   val image=compose.onNodeWithTag("opaque-surface").captureToImage().toPixelMap()
   val expected=appColorScheme(night).surfaceContainerHigh
   val actual=image[image.width/2,image.height/2]
   assertEquals(expected.red,actual.red,.01f);assertEquals(expected.green,actual.green,.01f);assertEquals(expected.blue,actual.blue,.01f)
  }
 }
}
