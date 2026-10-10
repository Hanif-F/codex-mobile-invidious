package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import net.wingress.mobivious.data.ChapterRules

internal fun playerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return net.wingress.mobivious.data.DisplayFormats.clock(seconds)
}
internal fun speedLabel(speed: Float) = "${speed.toString().removeSuffix(".0")}×"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VideoPlayer(
    vm: AppViewModel, playback: PlaybackState, controller: MediaController?, modifier: Modifier,
    fullscreen: Boolean = false, controls: Boolean = true, settingsOpen: Boolean = false,
    presentation: PlayerPresentation = PlayerPresentation.WATCH, drag: PlayerDragHandler? = null,
    gesturesEnabled: Boolean = true, chromeVisible: Boolean = true, gestureKey: Any? = null,
    onCollapse: (() -> Unit)? = null, onRestore: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null, surfaceAlpha: Float = 1f,
    onFullscreen: () -> Unit, onSettings: () -> Unit, onChapters: () -> Unit,
    onChat: (() -> Unit)? = null,
) {
    val activity = androidx.activity.compose.LocalActivity.current as? net.wingress.mobivious.MainActivity
    val current by rememberUpdatedState(playback)
    val currentDrag by rememberUpdatedState(drag)
    val pendingSeek by vm.pendingSeek.collectAsStateWithLifecycle()
    val sponsorState by vm.sponsorBlock.collectAsStateWithLifecycle()
    val replay by vm.chatReplay.collectAsStateWithLifecycle()
    val sponsor = sponsorState.takeIf { it.mediaId == playback.mediaId } ?: SponsorBlockPlayback()
    val chapters = playback.chapters
    var visible by remember(playback.mediaId, controls) { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrub by remember(playback.mediaId, gestureKey, controls) { mutableStateOf<Float?>(null) }
    var feedback by remember(playback.mediaId, controls) { mutableStateOf<String?>(null) }
    var feedbackGeneration by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    var inputOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val fullscreenTopEdge by rememberUpdatedState(maxOf(with(density) { 32.dp.toPx() },
        WindowInsets.statusBarsIgnoringVisibility.getTop(density).toFloat(), WindowInsets.displayCutout.getTop(density).toFloat()))
    val exploring = rememberPlayerTouchExploration()
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
    Box(modifier.graphicsLayer { alpha = surfaceAlpha }.background(Color.Black).testTag("player-surface")
        .onGloballyPositioned {
            inputOrigin = it.positionInRoot()
            val bounds = it.boundsInWindow()
            activity?.updatePipSource(android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
        }) {
        if (playback.videoEnabled || presentation != PlayerPresentation.MINI) PlaybackVideoSurface(playback, controller, Modifier.fillMaxSize())
        else playback.details?.video?.let { video ->
            coil.compose.AsyncImage(resolved(vm.store.server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), null,
                Modifier.fillMaxSize().testTag("mini-player-artwork"), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().testTag("player-gestures")
        .pointerInput(playback.mediaId, controls, presentation, gesturesEnabled, exploring, gestureKey) {
            if (!gesturesEnabled) return@pointerInput
            if (controls || presentation == PlayerPresentation.MINI) coroutineScope {
                var single: Job? = null
                var firstTime = Long.MIN_VALUE
                var firstZone = 0
                fun zone(x: Float) = when { x < size.width / 3f -> -1; x > size.width * 2 / 3f -> 1; else -> 0 }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val up = playerGesture(down, presentation, if (exploring) null else currentDrag,
                        position = { it + inputOrigin }, fullscreenTopEdge = fullscreenTopEdge) {
                        single?.cancel(); firstTime = Long.MIN_VALUE; vm.cancelAccumulatedSeek()
                    } ?: return@awaitEachGesture
                    if (presentation == PlayerPresentation.MINI) { onRestore?.invoke(); return@awaitEachGesture }
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
            if (gesturesEnabled && chromeVisible && presentation == PlayerPresentation.MINI && event.type == KeyEventType.KeyUp &&
                (event.key == Key.Enter || event.key == Key.Spacebar)) { onRestore?.invoke(); true }
            else if (controls && chromeVisible && !settingsOpen && sponsor.active != null && event.key == Key.Enter && event.type == KeyEventType.KeyUp &&
                !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed && !event.isShiftPressed) {
                vm.sponsorCommand(PlaybackService.SPONSOR_SKIP, sponsor.active.id); true
            } else false
        }.focusable(enabled = gesturesEnabled && chromeVisible && (controls || presentation == PlayerPresentation.MINI)).semantics {
            if (controls && chromeVisible && !settingsOpen) {
                onClick(label = if (visible) "Hide player controls" else "Show player controls") { toggleControls(); true }
                customActions = listOf(
                    CustomAccessibilityAction("Back 10 seconds") { if (current.seekable) { seek(-10_000); true } else false },
                    CustomAccessibilityAction("Forward 10 seconds") { if (current.seekable) { seek(10_000); true } else false },
                ) + listOfNotNull(onCollapse?.let { collapse -> CustomAccessibilityAction(if (fullscreen) "Exit full screen" else "Minimize player") { collapse(); true } }) + if (sponsor.active != null) listOf(CustomAccessibilityAction("Skip ${sponsor.active.category.label}") {
                    vm.sponsorCommand(PlaybackService.SPONSOR_SKIP, sponsor.active.id); true
                }) else emptyList()
            } else if (presentation == PlayerPresentation.MINI && gesturesEnabled && chromeVisible) {
                role = Role.Button
                onClick(label = "Open player") { onRestore?.invoke(); true }
                customActions = listOf(CustomAccessibilityAction("Restore player") { onRestore?.invoke(); true },
                    CustomAccessibilityAction("Dismiss player") { onDismiss?.invoke(); true })
            }
        })
        if (controls) {
            val timelinePosition = scrub?.toLong() ?: playback.position
            val sponsorLabels = sponsor.segments.filter { it.start <= timelinePosition && timelinePosition < it.end }
                .map { it.category.label }.distinct()
            val notice: (@Composable () -> Unit)? = if (!settingsOpen && playback.error == null && !playback.loading &&
                (sponsor.active != null || sponsor.notice.isNotBlank())) ({
                PlayerSponsorPrompt(sponsor, { id -> vm.sponsorCommand(PlaybackService.SPONSOR_SKIP, id) },
                    { id -> vm.sponsorCommand(PlaybackService.SPONSOR_DISMISS, id) })
            }) else null
            PlayerControlsLayout(playback, timelinePosition, fullscreen, controller != null,
                onPlay = { vm.togglePlay(); interact() }, onSeek = ::seek,
                onSettings = { vm.cancelAccumulatedSeek(); interact(); onSettings() },
                onFullscreen = { interact(); onFullscreen() },
                onChapters = { vm.cancelAccumulatedSeek(); interact(); onChapters() },
                onCollapse = onCollapse?.let { collapse -> { vm.cancelAccumulatedSeek(); collapse() } },
                onChat = onChat, chatOpen = replay.open, onRetry = { interact(); vm.retryPlayback() }, showControls = visible && chromeVisible,
                notice = if (chromeVisible) notice else null,
                modifier = Modifier.then(if (visible && chromeVisible) Modifier.testTag("player-controls") else Modifier)
                    .onFocusChanged { focused = it.hasFocus }.focusGroup(),
                timeline = { timelineModifier, trackOffset ->
                    Box(timelineModifier) {
                            Slider(
                                value = scrub ?: playback.position.coerceAtMost(playback.duration).toFloat(),
                                onValueChange = { vm.cancelAccumulatedSeek(); scrub = it; interact() },
                                onValueChangeFinished = { scrub?.let { vm.seekTo(it.toLong()) }; scrub = null; interact() },
                                valueRange = 0f..playback.duration.coerceAtLeast(1).toFloat(),
                                enabled = playback.seekable && playback.duration > 0 && !playback.loading && playback.error == null,
                                thumb = {
                                    Box(Modifier.offset(y = trackOffset).size(12.dp).clip(CircleShape).background(
                                        if (playback.seekable && !playback.loading && playback.error == null) Color.White else Color.Transparent))
                                },
                                track = { slider ->
                                    Canvas(Modifier.fillMaxWidth().offset(y = trackOffset).height(3.dp)) {
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
                                        // Separators stay visible over played, buffered and SponsorBlock ranges.
                                        chapters.filter { it.startMs > 0 }.forEach { chapter ->
                                            val x = size.width * chapter.startMs / end
                                            drawLine(Color.Black.copy(alpha = .8f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize().testTag("player-timeline").semantics {
                                    contentDescription = "Playback position"
                                    stateDescription = (listOfNotNull(ChapterRules.current(chapters, timelinePosition)?.title) + sponsorLabels).joinToString(", ")
                                },
                            )
                            if (scrub != null) PlayerSeekSponsorLabels(sponsorLabels,
                                Modifier.align(Alignment.TopCenter).offset(y = (-24).dp))
                    }
                })
            feedback?.let { Text(it, Modifier.align(Alignment.Center).mediaGlass().padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }, color = Color.White) }
            pendingSeek?.let { pending ->
                Column(Modifier.align(if (pending.offset < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxWidth(1f / 3f).mediaGlass(Liquid.card).padding(vertical = 20.dp)
                    .testTag("pending-seek").semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (pending.offset < 0) Icons.Default.FastRewind else Icons.Default.FastForward, null, tint = Color.White)
                    Text("${if (pending.offset < 0) "−" else "+"}${kotlin.math.abs(pending.offset) / 1000} seconds", color = Color.White,
                        style = MaterialTheme.typography.labelLarge)
                }
            }

        }
    }
}

@Composable
internal fun PlayerSponsorPrompt(sponsor: SponsorBlockPlayback, skip: (String) -> Unit, dismiss: (String) -> Unit) {
    val active = sponsor.active
    Row(Modifier.fillMaxWidth().mediaGlass(Liquid.card).testTag("sponsorblock-prompt"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 8.dp)) {
            if (active != null) Text("${active.category.label} · ${sponsor.remaining}s", color = Color.White,
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sponsor.notice.isNotBlank()) Text(sponsor.notice, Modifier.testTag("sponsorblock-notice")
                .semantics { liveRegion = LiveRegionMode.Polite }, color = Color.White,
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active != null) {
            TextButton(onClick = { skip(active.id) }) { Text("Skip", color = Color.White) }
            IconButton(onClick = { dismiss(active.id) }) { Icon(Icons.Default.Close, "Dismiss SponsorBlock segment", tint = Color.White) }
        }
    }
}

@Composable
internal fun PlayerSeekSponsorLabels(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    Text(labels.joinToString(", "), modifier.mediaGlass().padding(horizontal = 10.dp, vertical = 4.dp).testTag("player-seek-sponsor-labels"),
        color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
internal fun PlayerSettings(vm: AppViewModel, playback: PlaybackState, dismiss: () -> Unit, pip: () -> Unit, supportsPip: Boolean) {
    var page by rememberSaveable(playback.mediaId) { mutableStateOf("Player settings") }
    val formats = playback.details?.formats.orEmpty()
    val video = StreamCatalog.choices(playback.tracks, C.TRACK_TYPE_VIDEO, formats)
    val audio = StreamCatalog.choices(playback.tracks, C.TRACK_TYPE_AUDIO, formats)
    val captions = playback.trackChoices(C.TRACK_TYPE_TEXT)
    val selection = playback.selection
    val audioOnly = selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true
    val audioAuto = selection?.overrides?.values?.none { it.type == C.TRACK_TYPE_AUDIO } != false && selection?.preferredAudioLanguages.isNullOrEmpty()
    val selectedCaption = if (selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true) null else captions.firstOrNull { it.selected }
    val qualityAuto = playback.videoSelection.mode == net.wingress.mobivious.player.VideoSelectionMode.AUTO
    val selectedVideo = if (qualityAuto) null else playback.videoSelection.fixed(video)
    val available = !playback.loading && playback.canSelectTracks
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .85f }
    // Handle system Back in the sheet's dialog before the watch screen's handler.
    fun back() { if (page == "Player settings") dismiss() else page = "Player settings" }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState, sheetMaxWidth = 640.dp) {
        BackHandler { back() }
        WatchGlassScene(Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().heightIn(max = sheetHeight)) {
            WatchPanelToolbar(page, "Close player settings", dismiss, Icons.Default.Settings,
                backLabel = "Back to player settings", back = if (page != "Player settings") ({ page = "Player settings" }) else null)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("player-settings-list"), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (page == "Player settings") {
                    item { SettingRow("Quality", if (video.isEmpty()) "Unavailable" else if (qualityAuto) "Auto" else selectedVideo?.let(StreamCatalog::qualityText) ?: "Unavailable", available && !audioOnly && video.isNotEmpty()) { page = "Quality" } }
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
                    item { SettingRow("Refresh buffer", enabled = playback.canRefresh && !playback.loading) { dismiss(); vm.refreshBuffer() } }
                } else when (page) {
                    "Quality" -> {
                        item { SettingChoice("Auto", qualityAuto, available && !audioOnly) { vm.autoQuality(); page = "Player settings" } }
                        items(StreamCatalog.qualityMenu(video)) { track -> SettingChoice(track.primary, !qualityAuto && track == selectedVideo, available && !audioOnly, track.secondary) {
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
        } }
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
