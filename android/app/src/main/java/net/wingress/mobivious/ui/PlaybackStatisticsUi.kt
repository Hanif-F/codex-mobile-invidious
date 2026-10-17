package net.wingress.mobivious.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.media3.common.MediaLibraryInfo
import net.wingress.mobivious.BuildConfig
import net.wingress.mobivious.player.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlaybackStatisticsOverlay(ui: StatisticsUi, snapshot: StatisticsSnapshot, statistics: PlaybackStatistics,
    modifier: Modifier = Modifier, playWhenReady: Boolean = false, onPlay: (() -> Unit)? = null,
    writeClipboard: (Context, String) -> Unit = { context, text ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Playback diagnostics", text))
    }) {
    val context = LocalContext.current
    var status by remember(snapshot.videoId) { mutableStateOf("") }
    var fallback by remember(snapshot.videoId) { mutableStateOf<String?>(null) }
    BackHandler { if (statistics.ui.value.expanded) statistics.expand(false) else statistics.close() }
    fun copy() {
        statistics.refresh?.invoke()
        val text = fallback ?: statistics.snapshot.value.json(BuildConfig.VERSION_NAME, MediaLibraryInfo.VERSION, ui.viewport)
        try {
            writeClipboard(context, text)
            fallback = null; status = "Diagnostics copied"
        } catch (_: Exception) {
            fallback = text; status = "Copy failed. Select the JSON below or retry Copy diagnostics."
        }
    }
    BoxWithConstraints(modifier) {
        // Keep the timeline/fullscreen footer and the ordinary settings button accessible.
        val top = if (maxHeight >= 320.dp) 48.dp else 0.dp
        val bottom = if (maxHeight >= 192.dp) 96.dp else if (maxHeight >= 144.dp) 56.dp else 0.dp
        val panelWidth = if (ui.expanded && maxHeight >= 192.dp) maxWidth - 16.dp else maxWidth - 72.dp
        Surface(Modifier.padding(start = 8.dp, top = top, bottom = bottom)
            .width(panelWidth.coerceAtLeast(48.dp).coerceAtMost(520.dp)).heightIn(max = (maxHeight - top - bottom).coerceAtLeast(48.dp))
            .testTag("playback-statistics").semantics { paneTitle = "Playback statistics" }
            .pointerInput(Unit) {
                // Child buttons/scrolling receive events first. Blank panel space still belongs to the panel.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.small) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Statistics", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    if (onPlay != null) IconButton(onClick = onPlay, Modifier.size(48.dp)) {
                        Icon(if (playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playWhenReady) "Pause playback" else "Resume playback")
                    }
                    if (ui.expanded) IconButton(onClick = ::copy, Modifier.size(48.dp).testTag("copy-playback-diagnostics")) {
                        Icon(Icons.Default.ContentCopy, "Copy diagnostics")
                    }
                    IconButton(onClick = { statistics.expand(!ui.expanded) }, Modifier.size(48.dp)) {
                        Icon(if (ui.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            if (ui.expanded) "Collapse playback statistics" else "Playback statistics details")
                    }
                    IconButton(onClick = statistics::close, Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Close playback statistics") }
                }
                if (status.isNotEmpty()) Text(status, Modifier.padding(horizontal = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .testTag("playback-statistics-content").padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val keys = if (ui.expanded) StatisticKey.entries else StatisticKey.entries.take(4)
                    val large = LocalDensity.current.fontScale > 1.3f
                    if (!ui.expanded && !large) keys.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key -> StatisticCell(key, snapshot.metric(key, ui.viewport), Modifier.weight(1f)) }
                        }
                    } else keys.forEach { key -> StatisticCell(key, snapshot.metric(key, ui.viewport), Modifier.fillMaxWidth()) }
                    if (ui.expanded) {
                        StatisticsGraph("Buffer ahead (s)", snapshot.samples, false)
                        StatisticsGraph("Last media transfer (Mbps)", snapshot.samples, true)
                        Text("Graphs cover the last 60 seconds while visible. Buffering counts exclude startup, seeks and pauses. " +
                            "Media load and decoder counters belong to this source. Load bytes update when Media3 reports a load, including failed loads; " +
                            "a progressive transfer may finish only at the end of the video. Transfer speed describes a completed media load, not internet capacity. " +
                            "FPS and bitrate describe active stream metadata. Unavailable values are not estimated.",
                            style = MaterialTheme.typography.bodySmall)
                        fallback?.let { text -> SelectionContainer { Text(text, Modifier.testTag("diagnostics-copy-fallback"),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.sp,
                                textDirection = TextDirection.Ltr, textAlign = TextAlign.Left)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsGraph(label: String, samples: List<StatisticsSample>, bandwidth: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(40.dp).testTag(if (bandwidth) "statistics-bandwidth-graph" else "statistics-buffer-graph")
            .semantics { contentDescription = "$label over the last 60 seconds" }) {
            val end = samples.lastOrNull()?.at ?: return@Canvas
            val values = samples.map { if (bandwidth) it.bandwidth else it.buffer }
            val maximum = maxOf(1.0, values.filterNotNull().maxOrNull() ?: 0.0)
            var previous: Offset? = null
            samples.forEachIndexed { index, sample ->
                val value = values[index]
                val point = value?.let { Offset((1f - (end - sample.at) / 60_000f) * size.width,
                    size.height - (it / maximum * (size.height - 2)).toFloat() - 1) }
                if (point != null && previous != null) drawLine(color, previous, point, 2.dp.toPx())
                previous = point
            }
        }
    }
}

@Composable
private fun StatisticCell(key: StatisticKey, metric: PlaybackStatistic, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.testTag("statistic-${key.name}")) {
        Text(key.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(metric.value ?: metric.reason, Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.sp, textDirection = TextDirection.Ltr,
                textAlign = if (rtl) TextAlign.Right else TextAlign.Left)) }
    }
}
