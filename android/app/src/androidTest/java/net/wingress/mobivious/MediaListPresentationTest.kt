package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.AccentPreset
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaListPresentationTest {
    @get:Rule val compose = createComposeRule()
    private var width by mutableIntStateOf(390)
    private var compact by mutableStateOf(false)
    private var fontScale by mutableFloatStateOf(1f)
    private var rtl by mutableStateOf(false)
    private var thin by mutableStateOf(false)
    private var actions = 0

    private fun content() = compose.setContent { AppTheme(true, AccentPreset.BLUE) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale),
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            MediaListLayout(compact, Modifier.width(width.dp).padding(16.dp).testTag("row"),
                artwork = if (thin) null else ({ modifier -> Box(modifier.aspectRatio(16f / 9f).testTag("artwork")) })) { modifier ->
                Row(modifier.testTag("metadata")) {
                    Text("A long video title and creator", Modifier.weight(1f))
                    TextButton(onClick = { actions++ }, Modifier.heightIn(min = 48.dp).testTag("actions")) { Text("More") }
                }
            }
        }
    } }

    @Test fun phoneAndTabletRowsUseLargerArtworkInBothDensitiesAndDirections() {
        content()
        for (rowWidth in listOf(390, 800)) for (isCompact in listOf(false, true)) for (isRtl in listOf(false, true)) {
            compose.runOnIdle { width = rowWidth; compact = isCompact; rtl = isRtl }
            compose.onNodeWithTag("artwork").assertWidthIsEqualTo(if (isCompact) 144.dp else 160.dp)
                .assertHeightIsEqualTo(if (isCompact) 81.dp else 90.dp)
            val artwork = compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot
            val metadata = compose.onNodeWithTag("metadata").fetchSemanticsNode().boundsInRoot
            assertEquals(artwork.top, metadata.top, 1f)
            assertTrue(if (isRtl) metadata.right < artwork.left else metadata.left > artwork.right)
            compose.onNodeWithTag("actions").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(8, actions)
    }

    @Test fun narrowAndLargeTextRowsStackAndThinRowsReturnTheArtworkSpace() {
        content()
        for ((rowWidth, scale) in listOf(320 to 1f, 390 to 2f)) {
            compose.runOnIdle { width = rowWidth; fontScale = scale }
            compose.onNodeWithTag("artwork").assertWidthIsEqualTo((rowWidth - 32).dp)
            val artwork = compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot
            val metadata = compose.onNodeWithTag("metadata").fetchSemanticsNode().boundsInRoot
            assertTrue(metadata.top > artwork.bottom)
            compose.onNodeWithTag("actions").assertIsDisplayed().performClick()
        }
        compose.runOnIdle { thin = true }
        compose.onNodeWithTag("artwork").assertDoesNotExist()
        compose.onNodeWithTag("metadata").assertWidthIsEqualTo(358.dp)
    }
}
