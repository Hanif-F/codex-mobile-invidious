package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.wingress.mobivious.data.AccentPreset
import net.wingress.mobivious.player.*
import net.wingress.mobivious.ui.AppTheme
import net.wingress.mobivious.ui.PlaybackStatisticsOverlay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackStatisticsPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailsRemainScrollableWithPinnedActionsLargeTextRtlAndBothThemes() {
        val statistics = PlaybackStatistics().apply { toggle(); expand(true) }
        val snapshot = StatisticsSnapshot("testvideo01", "DASH", metrics = StatisticKey.entries.associateWith {
            PlaybackStatistic(if (it == StatisticKey.codecs) "av01.0.05M.08 / mp4a.40.2" else "1920 × 1080 / 1200000")
        }, samples = listOf(StatisticsSample(0, 2.0, 3.0), StatisticsSample(1000, 1.0, 4.0)))
        var night by mutableStateOf(false)
        compose.setContent {
            val state by statistics.ui.collectAsState()
            AppTheme(night, AccentPreset.BLUE) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                    LocalLayoutDirection provides if (night) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.width(320.dp).height(500.dp)) { PlaybackStatisticsOverlay(state, snapshot, statistics, Modifier.fillMaxSize()) }
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night = dark }
            compose.onNodeWithContentDescription("Copy diagnostics").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithContentDescription("Close playback statistics").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("statistic-resolution").performScrollTo()
            compose.onNode(hasText("1920 × 1080 / 1200000") and hasAnyAncestor(hasTestTag("statistic-resolution")))
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>(); action(layouts)
                    layouts.forEach {
                        assertLabelGlyphsFit(it, "statistics resolution")
                        assertTrue("Resolution must retain width-before-height order in RTL", it.getBoundingBox(0).left < it.getBoundingBox(7).left)
                    }
                }
            compose.onNodeWithTag("statistic-codecs").performScrollTo()
            compose.onNodeWithText("av01.0.05M.08 / mp4a.40.2").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>(); action(layouts)
                layouts.forEach { assertLabelGlyphsFit(it, "statistics codecs") }
            }
            compose.onNodeWithTag("statistics-buffer-graph").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("statistics-bandwidth-graph").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Copy diagnostics").assertIsDisplayed()
            captureWatchScreenshot("statistics-large-${if (dark) "dark-rtl" else "light"}")
        }
        compose.onNodeWithContentDescription("Collapse playback statistics").performClick()
        assertFalse(statistics.ui.value.expanded)
        compose.onNodeWithContentDescription("Playback statistics details").assertIsDisplayed()
        compose.onNodeWithTag("statistic-codecs").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close playback statistics").performClick()
        assertFalse(statistics.ui.value.enabled)
    }

    @Test fun failedClipboardRetainsSelectableSnapshotAndRetryCopiesThatSnapshot() {
        val statistics = PlaybackStatistics().apply { toggle(); expand(true); publish(StatisticsSnapshot("testvideo01", "DASH")) }
        var attempts = 0
        var copied: String? = null
        compose.setContent {
            val state by statistics.ui.collectAsState()
            val snapshot by statistics.snapshot.collectAsState()
            AppTheme(false, AccentPreset.BLUE) {
                Box(Modifier.width(320.dp).height(700.dp)) {
                    PlaybackStatisticsOverlay(state, snapshot, statistics, Modifier.fillMaxSize(), writeClipboard = { _, text ->
                        attempts++
                        if (attempts == 1) throw SecurityException("Clipboard unavailable")
                        copied = text
                    })
                }
            }
        }
        compose.onNodeWithContentDescription("Copy diagnostics").performClick()
        compose.onNodeWithTag("diagnostics-copy-fallback").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { statistics.publish(StatisticsSnapshot("testvideo01", "HLS")) }
        compose.onNodeWithContentDescription("Copy diagnostics").performClick()
        assertTrue(copied!!.contains("testvideo01"))
        assertFalse(copied.contains("HLS"))
        compose.onNodeWithTag("diagnostics-copy-fallback").assertDoesNotExist()
        compose.onNodeWithText("Diagnostics copied").assertExists()
    }
}
