package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.*
import net.wingress.mobivious.ui.*
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MetadataPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactChannelAndVerifiedWatchDetailsRemainReadableAtLargeFont() {
        val details = ApiParser.details(JSONObject("""{"videoId":"abcdefghijk","title":"Video","author":"Studio","viewCount":5600001,"viewCountPrecision":"exact","likeCount":0,"likeCountPrecision":"exact","published":1704060000}"""))
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Surface(Modifier.width(320.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        ChannelHeader(Channel("", "Studio", subscribers = "5600000"), "", true, false) {}
                        VideoMetadataLine(details.video, likes = details.likes)
                        VideoStatistics(details)
                    }
                }
            }
        } }
        compose.onNodeWithText(DisplayFormats.subscribers("5600000")).assertIsDisplayed()
        compose.onNodeWithTag("video-metadata-abcdefghijk").assertTextContains(DisplayFormats.audience(5600001, "view"), substring = true)
        compose.onNodeWithTag("video-exact-views").performScrollTo().assertTextEquals(DisplayFormats.inventory(5600001, "view"))
        compose.onNodeWithTag("video-exact-likes").performScrollTo().assertTextEquals("0 likes")
        compose.onNodeWithTag("video-published-date").performScrollTo().assertIsDisplayed()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "metadata-presentation.png")
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun roundedAndLegacyCountsNeverGainExactTotalsAndEpochDatesStayHidden() {
        var precision by mutableStateOf("approximate")
        compose.setContent { MaterialTheme {
            val json = JSONObject("""{"videoId":"short","viewCount":5600000,"likeCount":1200,"published":0,"publishedText":"56 years ago"}""")
            if (precision.isNotEmpty()) { json.put("viewCountPrecision", precision); json.put("likeCountPrecision", precision) }
            val details = ApiParser.details(json)
            Column { VideoMetadataLine(details.video, likes = details.likes); VideoStatistics(details) }
        } }
        for (value in listOf("approximate", "")) {
            compose.runOnIdle { precision = value }
            compose.onNodeWithTag("video-metadata-short").assertTextEquals("${DisplayFormats.audience(5600000, "view")} · ${DisplayFormats.audience(1200, "like")}")
            compose.onNodeWithTag("video-exact-views").assertDoesNotExist()
            compose.onNodeWithTag("video-exact-likes").assertDoesNotExist()
            compose.onNodeWithText("56 years ago", substring = true).assertDoesNotExist()
        }
        compose.runOnIdle { precision = "unknown" }
        compose.onNodeWithTag("video-metadata-short").assertDoesNotExist()
        compose.onNodeWithTag("video-statistics").assertDoesNotExist()
    }
}
