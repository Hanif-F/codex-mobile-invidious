package net.wingress.mobivious.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.SponsorBlockPlayback
import net.wingress.mobivious.player.PlaybackService
import net.wingress.mobivious.player.StreamCatalog
import net.wingress.mobivious.player.AudioSection
import net.wingress.mobivious.data.PreferenceRules

private fun playerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
internal fun speedLabel(speed: Float) = "${speed.toString().removeSuffix(".0")}×"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayer(
    vm: AppViewModel, playback: PlaybackState, controller: MediaController?, modifier: Modifier,
    fullscreen: Boolean = false, controls: Boolean = true, settingsOpen: Boolean = false,
    onFullscreen: () -> Unit, onSettings: () -> Unit,
) {
    val current by rememberUpdatedState(playback)
    val pendingSeek by vm.pendingSeek.collectAsStateWithLifecycle()
    val sponsorState by vm.sponsorBlock.collectAsStateWithLifecycle()
    val sponsor = sponsorState.takeIf { it.mediaId == playback.mediaId } ?: SponsorBlockPlayback()
    val compactPlay = !fullscreen && !settingsOpen && sponsor.active != null
    var visible by remember(playback.mediaId, controls) { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrub by remember(playback.mediaId, controls) { mutableStateOf<Float?>(null) }
    var feedback by remember(playback.mediaId, controls) { mutableStateOf<String?>(null) }
    var feedbackGeneration by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var exploring by remember { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    fun interact() { interaction++; visible = true }
    fun toggleControls() { vm.cancelAccumulatedSeek(); visible = !visible; interaction++ }
    fun seek(offset: Long) {
        if (current.seekable && !current.loading && current.error == null) {
            vm.seekBy(offset)
            feedback = if (offset < 0) "−10 seconds" else "+10 seconds"
            feedbackGeneration++
            interact()
        }
    }
    LaunchedEffect(playback.playWhenReady, playback.playerState, playback.error, playback.loading, controls) {
        if (playback.error != null) vm.cancelAccumulatedSeek(false)
        if (!playback.playWhenReady || playback.playerState == Player.STATE_ENDED || playback.error != null || playback.loading) visible = true
    }
    LaunchedEffect(settingsOpen, controls) { if (settingsOpen || !controls) vm.cancelAccumulatedSeek() }
    LaunchedEffect(pendingSeek) { if (pendingSeek != null) interact() }
    LaunchedEffect(visible, interaction, playback.playWhenReady, playback.playerState, playback.error, playback.loading, scrub != null, settingsOpen, exploring, focused, controls, pendingSeek) {
        if (controls && visible && playback.playWhenReady && playback.playerState != Player.STATE_ENDED && playback.error == null &&
            !playback.loading && scrub == null && !settingsOpen && !exploring && !focused && pendingSeek == null) {
            delay(3000)
            visible = false
        }
    }
    LaunchedEffect(feedbackGeneration) { if (feedback != null) { delay(800); feedback = null } }
    // A new pointer-input key cancels any pending single/double tap on media changes or PiP entry.
    Box(modifier.background(Color.Black).testTag("player-surface")) {
        PlaybackVideoSurface(playback, controller, Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().testTag("player-gestures")
        .pointerInput(playback.mediaId, controls) {
            if (controls) coroutineScope {
                var single: Job? = null
                var firstTime = Long.MIN_VALUE
                var firstZone = 0
                fun zone(x: Float) = when { x < size.width / 3f -> -1; x > size.width * 2 / 3f -> 1; else -> 0 }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    up.consume()
                    if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                        up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                    val direction = zone(up.position.x)
                    if (vm.pendingSeek.value != null) {
                        single?.cancel(); firstTime = Long.MIN_VALUE
                        if (direction == 0) toggleControls() else { vm.accumulateSeek(direction); interact() }
                    } else if (single?.isActive == true && direction == firstZone &&
                        down.uptimeMillis - firstTime in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis) {
                        single?.cancel(); firstTime = Long.MIN_VALUE
                        if (direction == 0) toggleControls() else { vm.accumulateSeek(direction); interact() }
                    } else {
                        if (single?.isActive == true) { single?.cancel(); toggleControls() }
                        firstTime = up.uptimeMillis; firstZone = direction
                        single = launch { delay(viewConfiguration.doubleTapTimeoutMillis); toggleControls() }
                    }
                }
            }
        }
        .onKeyEvent { event ->
            if (controls && !settingsOpen && sponsor.active != null && event.key == Key.Enter && event.type == KeyEventType.KeyUp &&
                !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed && !event.isShiftPressed) {
                vm.sponsorCommand(PlaybackService.SPONSOR_SKIP, sponsor.active.id); true
            } else false
        }.focusable(enabled = controls).semantics {
            if (controls) {
                onClick(label = if (visible) "Hide player controls" else "Show player controls") { toggleControls(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Back 10 seconds") { if (current.seekable) { seek(-10_000); true } else false },
                    CustomAccessibilityAction("Forward 10 seconds") { if (current.seekable) { seek(10_000); true } else false },
                ) + if (sponsor.active != null) listOf(CustomAccessibilityAction("Skip ${sponsor.active.category.label}") {
                    vm.sponsorCommand(PlaybackService.SPONSOR_SKIP, sponsor.active.id); true
                }) else emptyList()
            }
        })
        if (controls) {
            AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200)), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("player-controls").onFocusChanged { focused = it.hasFocus }.focusGroup()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))) {
                    if (!playback.loading && playback.error == null) {
                        IconButton(
                            onClick = { vm.togglePlay(); interact() }, enabled = playback.canPlay,
                            modifier = Modifier.align(Alignment.Center).offset(y = if (fullscreen) 0.dp else if (compactPlay) (-12).dp else (-24).dp)
                                .size(if (compactPlay) 48.dp else 64.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)),
                        ) {
                            val ended = playback.playerState == Player.STATE_ENDED
                            Icon(if (ended) Icons.Default.Replay else if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (ended) "Replay" else if (playback.playWhenReady) "Pause" else "Play", Modifier.size(if (compactPlay) 32.dp else 40.dp), tint = Color.White)
                        }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)) else Modifier)
                        .padding(horizontal = 12.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            Slider(
                                value = scrub ?: playback.position.coerceAtMost(playback.duration).toFloat(),
                                onValueChange = { vm.cancelAccumulatedSeek(); scrub = it; interact() },
                                onValueChangeFinished = { scrub?.let { vm.seekTo(it.toLong()) }; scrub = null; interact() },
                                valueRange = 0f..playback.duration.coerceAtLeast(1).toFloat(),
                                enabled = playback.seekable && playback.duration > 0 && !playback.loading && playback.error == null,
                                thumb = {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(
                                        if (playback.seekable && !playback.loading && playback.error == null) Color.White else Color.Transparent))
                                },
                                track = { slider ->
                                    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                                        val end = playback.duration.coerceAtLeast(1).toFloat()
                                        fun line(fraction: Float, color: Color) {
                                            if (fraction > 0f) drawLine(color, Offset(0f, size.height / 2), Offset(size.width * fraction.coerceIn(0f, 1f), size.height / 2), size.height, StrokeCap.Round)
                                        }
                                        line(1f, Color.White.copy(alpha = .22f))
                                        line(playback.bufferedPosition / end, Color.White.copy(alpha = .45f))
                                        line(slider.value / end, Color.White)
                                        sponsor.segments.forEach { segment ->
                                            drawLine(sponsorColor(sponsor.settings.colors[segment.category] ?: segment.category.color),
                                                Offset(size.width * segment.start / end, size.height / 2),
                                                Offset(size.width * minOf(segment.end, playback.duration) / end, size.height / 2), size.height, StrokeCap.Butt)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("player-timeline").semantics {
                                    contentDescription = "Playback position"
                                    stateDescription = sponsor.segments.filter { it.start <= (scrub?.toLong() ?: playback.position) && (scrub?.toLong() ?: playback.position) < it.end }
                                        .map { it.category.label }.distinct().joinToString(", ")
                                },
                            )
                            if (scrub != null) {
                                val labels = sponsor.segments.filter { it.start <= scrub!!.toLong() && scrub!!.toLong() < it.end }.map { it.category.label }.distinct()
                                if (labels.isNotEmpty()) Text(labels.joinToString(", "), Modifier.align(Alignment.TopCenter).offset(y = (-20).dp).background(Color.Black.copy(alpha = .85f)).padding(4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${playerTime(scrub?.toLong() ?: playback.position)} / ${if (playback.live) "LIVE" else if (playback.duration > 0) playerTime(playback.duration) else "—"}",
                                Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { vm.cancelAccumulatedSeek(); interact(); vm.queueOpen.value = true }, enabled = controller != null) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Playback queue", tint = Color.White) }
                            IconButton(onClick = { vm.cancelAccumulatedSeek(); interact(); onSettings() }, enabled = controller != null) { Icon(Icons.Default.Settings, "Player settings", tint = Color.White) }
                            IconButton(onClick = { interact(); onFullscreen() }) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                if (fullscreen) "Exit full screen" else "Full screen", tint = Color.White) }
                        }
                    }
                }
            }
            if (playback.loading || playback.buffering) CircularProgressIndicator(
                Modifier.align(Alignment.Center).then(if (!playback.loading) Modifier.offset(y = (-52).dp) else Modifier).size(28.dp)
                    .semantics { contentDescription = if (playback.loading) "Loading video" else "Buffering" }, color = Color.White, strokeWidth = 2.dp)
            playback.error?.let { error ->
                Column(Modifier.align(Alignment.Center).padding(start = 16.dp, end = 16.dp, bottom = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { interact(); vm.retryPlayback() }) { Text("Retry", color = Color.White) }
                }
            }
            feedback?.let { Text(it, Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .8f), CircleShape).padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }, color = Color.White) }
            pendingSeek?.let { pending ->
                Column(Modifier.align(if (pending.offset < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxWidth(1f / 3f).background(Color.Black.copy(alpha = .65f), CircleShape).padding(vertical = 20.dp)
                    .testTag("pending-seek").semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (pending.offset < 0) Icons.Default.FastRewind else Icons.Default.FastForward, null, tint = Color.White)
                    Text("${if (pending.offset < 0) "−" else "+"}${kotlin.math.abs(pending.offset) / 1000} seconds", color = Color.White,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            if (!settingsOpen && playback.error == null && !playback.loading) {
                Column(Modifier.align(Alignment.TopStart)
                    .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)) else Modifier)
                    .padding(8.dp).widthIn(max = 460.dp)) {
                    sponsor.active?.let { active ->
                        Row(Modifier.background(Color.Black.copy(alpha = .85f)).testTag("sponsorblock-prompt"), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text("${active.category.label} · ${sponsor.remaining}s", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                if (sponsor.notice.isNotBlank()) Text(sponsor.notice, Modifier.testTag("sponsorblock-notice").semantics { liveRegion = LiveRegionMode.Polite },
                                    color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { vm.sponsorCommand(PlaybackService.SPONSOR_SKIP, active.id) }) { Text("Skip", color = Color.White) }
                            IconButton(onClick = { vm.sponsorCommand(PlaybackService.SPONSOR_DISMISS, active.id) }) { Icon(Icons.Default.Close, "Dismiss SponsorBlock segment", tint = Color.White) }
                        }
                    }
                    if (sponsor.active == null && sponsor.notice.isNotBlank()) Text(sponsor.notice, Modifier.background(Color.Black.copy(alpha = .85f)).padding(8.dp).testTag("sponsorblock-notice")
                        .semantics { liveRegion = LiveRegionMode.Polite }, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private data class PlayerTrack(val group: TrackGroup, val index: Int, val label: String, val selected: Boolean)

private fun PlaybackState.trackChoices(type: Int): List<PlayerTrack> = tracks.groups.filter { it.type == type }.flatMap { group ->
    (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
        val format = group.getTrackFormat(index)
        val language = format.language?.takeUnless { it == "und" }?.let { Locale.forLanguageTag(it).getDisplayName(Locale.getDefault()) }
        PlayerTrack(group.mediaTrackGroup, index, format.label?.takeIf { it.isNotBlank() } ?: language ?: "${if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Caption"} track ${index + 1}", group.isTrackSelected(index))
    }
}.let { choices ->
    choices.mapIndexed { index, track -> if (choices.count { it.label == track.label } > 1) track.copy(label = "${track.label} (${index + 1})") else track }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSettings(vm: AppViewModel, playback: PlaybackState, dismiss: () -> Unit, pip: () -> Unit, supportsPip: Boolean, sponsorBlock: () -> Unit) {
    var page by rememberSaveable(playback.mediaId) { mutableStateOf("Player settings") }
    val formats = playback.details?.formats.orEmpty()
    val video = StreamCatalog.choices(playback.tracks, C.TRACK_TYPE_VIDEO, formats)
    val audio = StreamCatalog.choices(playback.tracks, C.TRACK_TYPE_AUDIO, formats)
    val captions = playback.trackChoices(C.TRACK_TYPE_TEXT)
    val selection = playback.selection
    val audioOnly = selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true
    val audioAuto = selection?.overrides?.values?.none { it.type == C.TRACK_TYPE_AUDIO } != false && selection?.preferredAudioLanguages.isNullOrEmpty()
    val selectedCaption = if (selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true) null else captions.firstOrNull { it.selected }
    val qualityAuto = selection?.overrides?.values?.none { it.type == C.TRACK_TYPE_VIDEO } != false
    val selectedVideo = video.firstOrNull { it.explicitlySelected(selection) }
    val available = !playback.loading && playback.canSelectTracks
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .85f }
    // Handle system Back in the sheet's dialog before the watch screen's handler.
    fun back() { if (page == "Player settings") dismiss() else page = "Player settings" }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState, sheetMaxWidth = 640.dp) {
        BackHandler { back() }
        Column(Modifier.fillMaxWidth().heightIn(max = sheetHeight)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != "Player settings") IconButton(onClick = { page = "Player settings" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to player settings") }
                Text(page, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "Close player settings") }
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("player-settings-list"), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (page == "Player settings") {
                    item { SettingRow("Refresh buffer", enabled = playback.canRefresh && !playback.loading) { dismiss(); vm.refreshBuffer() } }
                    item { SettingRow("SponsorBlock") { sponsorBlock() } }
                    item { SettingRow("Quality", if (video.isEmpty()) "Unavailable" else if (qualityAuto) "Auto" else selectedVideo?.primary ?: "Auto", available && !audioOnly && video.isNotEmpty()) { page = "Quality" } }
                    item { SettingRow("Audio", if (audio.isEmpty()) "Unavailable" else if (audioAuto) "Auto · ${audio.firstOrNull { it.selected }?.primary ?: "Default"}" else audio.firstOrNull { it.explicitlySelected(selection) }?.primary ?: "Auto", available && audio.isNotEmpty()) { page = "Audio" } }
                    item { SettingRow("Captions", if (captions.isEmpty()) "Unavailable" else selectedCaption?.label ?: "Off", available && captions.isNotEmpty()) { page = "Captions" } }
                    item { SettingRow("Playback speed", speedLabel(playback.speed), !playback.loading && playback.canSetSpeed) { page = "Playback speed" } }
                    item {
                        Row(Modifier.fillMaxWidth().clickable(enabled = available) { vm.audioOnly(!audioOnly) }.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Audio only", Modifier.weight(1f), color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                            Switch(checked = audioOnly, onCheckedChange = vm::audioOnly, enabled = available)
                        }
                    }
                    item { SettingRow("Picture in picture", if (supportsPip) "" else "Unavailable on this device", supportsPip && playback.details != null && playback.error == null && !playback.loading) { dismiss(); pip() } }
                } else when (page) {
                    "Quality" -> {
                        item { SettingChoice("Auto", qualityAuto, available && !audioOnly) { vm.autoQuality(); page = "Player settings" } }
                        items(video) { track -> SettingChoice(track.primary, track.explicitlySelected(selection), available && !audioOnly, track.secondary) {
                            vm.selectTrack(track.group, track.index); page = "Player settings"
                        } }
                    }
                    "Playback speed" -> items(PreferenceRules.speeds) { speed ->
                        SettingChoice(speedLabel(speed), playback.speed == speed, playback.canSetSpeed) { vm.speed(speed); page = "Player settings" }
                    }
                    "Audio" -> {
                        item { SettingChoice("Auto", audioAuto, available) { vm.autoAudio(); page = "Player settings" } }
                        AudioSection.entries.forEach { section ->
                            val choices = audio.filter { it.section == section }
                            if (choices.isNotEmpty()) {
                                item { Text(section.title, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall) }
                                items(choices) { track -> SettingChoice(track.primary, !audioAuto && track.explicitlySelected(selection), available, track.secondary) { vm.selectTrack(track.group, track.index); page = "Player settings" } }
                            }
                        }
                    }
                    "Captions" -> {
                        item { SettingChoice("Off", selectedCaption == null, available) { vm.captions(null); page = "Player settings" } }
                        items(captions) { track -> SettingChoice(track.label, track == selectedCaption, available) { vm.selectTrack(track.group, track.index); page = "Player settings" } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String = "", enabled: Boolean = true, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = click).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
        if (value.isNotEmpty()) Text(value, Modifier.padding(start = 16.dp).widthIn(max = 200.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, enabled: Boolean, detail: String = "", click: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = click)
        .semantics { this.selected = selected }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (detail.isNotBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}
