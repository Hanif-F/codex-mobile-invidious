package net.wingress.mobivious.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.ContentLink
import net.wingress.mobivious.data.ContentLinks
import net.wingress.mobivious.player.Projection
import net.wingress.mobivious.player.ViewingMode

@Composable
internal fun AnnotationOverlays(vm: AppViewModel, playback: PlaybackState, modifier: Modifier = Modifier) {
    val state by vm.annotations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val active = state.takeIf { it.binding?.occurrence == vm.queue.value.currentKey && it.binding?.context == vm.api.context() }
        ?.active(playback.position + (playback.clip?.startMs ?: 0)).orEmpty()
    if (active.isEmpty()) return
    BoxWithConstraints(modifier.testTag("annotation-overlays")) {
        val ratio = playback.displayGeometry.layoutRatio
        val h = minOf(maxHeight, maxWidth / ratio)
        val w = h * ratio
        Box(Modifier.size(w, h).align(Alignment.Center)) {
            active.forEach { a ->
                val bg = Color(a.background or (0xff shl 24)).copy(alpha = a.opacity)
                if (a.style == "speech") Canvas(Modifier.matchParentSize()) {
                    val centerX = (a.x + a.width / 2) / 100 * size.width
                    val bottom = (a.y + a.height) / 100 * size.height
                    val tail = Path().apply {
                        moveTo(centerX - 8.dp.toPx(), bottom - 1)
                        lineTo((a.tailX ?: (a.x + a.width / 2)) / 100 * size.width,
                            (a.tailY ?: (a.y + a.height + 5)).coerceIn(0f, 100f) / 100 * size.height)
                        lineTo(centerX + 8.dp.toPx(), bottom - 1); close()
                    }
                    drawPath(tail, bg)
                }
                Box(Modifier.offset(w * (a.x / 100), h * (a.y / 100))
                    .size(w * (a.width / 100), h * (a.height / 100))
                    .then(if (a.style == "highlight") Modifier.border(2.dp, bg, RoundedCornerShape(2.dp))
                        else if (a.style == "title") Modifier else Modifier.background(bg, RoundedCornerShape(3.dp)))
                    .testTag("annotation-${a.id}")
                    .then(if (a.url != null) Modifier.clickable(onClickLabel = "Open annotation link") {
                        // A queued tap must still belong to this exact video occurrence.
                        if (vm.annotations.value.binding != state.binding || state.binding?.context != vm.api.context()) return@clickable
                        val target = ContentLinks.resolve(a.url, vm.store.server, playback.mediaId)
                        if (target is ContentLink.Seek) vm.seekTo((target.milliseconds - (playback.clip?.startMs ?: 0)).coerceAtLeast(0))
                        else if (target == null || !vm.openContent(target)) openExternalContent(context, a.url) { }
                    } else Modifier)) {
                    Text(a.text, Modifier.fillMaxSize().padding(4.dp), color = Color(a.foreground or (0xff shl 24)),
                        fontSize = (h.value * a.textSize / 100).coerceIn(8f, 48f).sp,
                        lineHeight = (h.value * a.textSize / 100 * 1.15f).coerceIn(9f, 56f).sp)
                    IconButton(onClick = { if (vm.annotations.value.binding == state.binding && state.binding?.context == vm.api.context()) vm.dismissAnnotation(a.id) }, modifier = Modifier.size(24.dp).align(Alignment.TopEnd).offset(8.dp, (-8).dp)) {
                        Icon(Icons.Default.Close, "Dismiss annotation ${a.text.ifBlank { "highlight" }}", Modifier.size(18.dp).background(Color.Black.copy(alpha = .65f), CircleShape).padding(2.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AnnotationSettings(vm: AppViewModel, playback: PlaybackState) {
    val state by vm.annotations.collectAsStateWithLifecycle()
    val view by vm.viewing.collectAsStateWithLifecycle()
    if (playback.downloadId != null) { Text("Archived annotations are available during online playback.", Modifier.padding(16.dp)); return }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Show annotations", Modifier.weight(1f))
        Switch(state.enabled, vm::setAnnotations, Modifier.testTag("annotations-toggle"))
    }
    Text(if (view.annotations == null) "Using playback defaults and link options" else "Override for this video occurrence",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { vm.setAnnotations(null) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Use playback default") }
    if (state.enabled) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        Text(when {
            state.error != null -> state.error!!
            state.loaded && state.items.isEmpty() -> "No archived annotations found."
            state.loaded -> "${state.items.size} archived annotations"
            else -> "Annotations load while the video is visible."
        }, Modifier.padding(16.dp).testTag("annotations-status"))
        if (state.error != null) TextButton(onClick = vm::retryAnnotations) { Text("Retry annotations") }
    }
}

@Composable
internal fun ViewingSettings(vm: AppViewModel, playback: PlaybackState) {
    val state by vm.viewing.collectAsStateWithLifecycle()
    ViewingMode.entries.forEach { mode -> SettingChoice(mode.label, mode == state.mode, true) { vm.setViewingMode(mode) } }
    Text("Viewing mode applies to this video occurrence. Automatic follows video metadata and your playback default.",
        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    state.error?.let { Text(it, Modifier.padding(16.dp).testTag("viewing-error"), color = MaterialTheme.colorScheme.error) }
    val motionAvailable = ProjectionVideoView.motionAvailable(LocalContext.current)
    val enabled = playback.projection != Projection.FLAT
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (motionAvailable) "Device motion" else "Device motion unavailable", Modifier.weight(1f))
        Switch(state.motion, vm::setViewingMotion, Modifier.testTag("viewing-motion"), enabled = motionAvailable && enabled)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        AppIconButton(Icons.Default.KeyboardArrowLeft, "Look left", enabled = enabled) { vm.panViewing(-15f, 0f) }
        AppIconButton(Icons.Default.KeyboardArrowUp, "Look up", enabled = enabled) { vm.panViewing(0f, 15f) }
        AppIconButton(Icons.Default.KeyboardArrowDown, "Look down", enabled = enabled) { vm.panViewing(0f, -15f) }
        AppIconButton(Icons.Default.KeyboardArrowRight, "Look right", enabled = enabled) { vm.panViewing(15f, 0f) }
    }
    TextButton(onClick = vm::recenterViewing, enabled = enabled, modifier = Modifier.testTag("viewing-recenter")) { Text("Recenter") }
}
