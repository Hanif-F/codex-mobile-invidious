package net.wingress.mobivious.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.motionEventSpy
import android.view.MotionEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
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
import net.wingress.mobivious.player.Projection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun VideoPlayer(
    vm: AppViewModel, playback: PlaybackState, controller: MediaController?, modifier: Modifier,
    fullscreen: Boolean = false, controls: Boolean = true, settingsOpen: Boolean = false,
    presentation: PlayerPresentation = PlayerPresentation.WATCH, drag: PlayerDragHandler? = null,
    gesturesEnabled: Boolean = true, chromeVisible: Boolean = true, gestureKey: Any? = null,
    onCollapse: (() -> Unit)? = null, onRestore: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null, surfaceAlpha: Float = 1f,
    onFullscreen: () -> Unit, onSettings: () -> Unit, onChapters: () -> Unit,
    onChat: (() -> Unit)? = null,
    pictureOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val activity = androidx.activity.compose.LocalActivity.current as? net.wingress.mobivious.MainActivity
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val viewing by vm.viewing.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val statisticsUi by vm.playbackStatistics.ui.collectAsStateWithLifecycle()
    val statisticsSnapshot by vm.playbackStatistics.snapshot.collectAsStateWithLifecycle()
    val statisticsVisible = controls && chromeVisible && gesturesEnabled && !settingsOpen &&
        presentation in listOf(PlayerPresentation.WATCH, PlayerPresentation.FULLSCREEN) && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(statisticsVisible) { vm.playbackStatistics.visible(statisticsVisible) }
    DisposableEffect(vm) { onDispose { vm.playbackStatistics.visible(false) } }
    val immersive = playback.projection != Projection.FLAT
    LaunchedEffect(controls, presentation, lifecycle, playback.videoEnabled, playback.details, viewing.annotations) {
        vm.configurePlayerTools(controls && presentation != PlayerPresentation.MINI && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(vm) { onDispose { vm.configurePlayerTools(false) } }
    val seekSeconds = PreferenceRules.seekSeconds(preferences.seekSeconds)
    val spoilerQueue by vm.queue.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(playback)
    val currentDrag by rememberUpdatedState(drag)
    val pendingSeek by vm.pendingSeek.collectAsStateWithLifecycle()
    val scrubSession by vm.scrubSeek.collectAsStateWithLifecycle()
    val preview by vm.seekPreviews.state.collectAsStateWithLifecycle()
    val spoilers by vm.channelSpoilers.state.collectAsStateWithLifecycle()
    val sponsorState by vm.sponsorBlock.collectAsStateWithLifecycle()
    val replay by vm.chatReplay.collectAsStateWithLifecycle()
    val sponsor = sponsorState.takeIf { it.mediaId == playback.mediaId } ?: SponsorBlockPlayback()
    val chapters = playback.chapters
    var visible by remember(playback.mediaId, controls) { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    val scrub = scrubSession?.targetMs?.toFloat()
    val timelineInteractions = remember { MutableInteractionSource() }
    val timelinePointerActive = remember { mutableStateOf(false) }
    var feedback by remember(playback.mediaId, controls) { mutableStateOf<Long?>(null) }
    var feedbackGeneration by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    var overflowOpen by remember(playback.mediaId, controls) { mutableStateOf(false) }
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
            feedback = offset
            feedbackGeneration++
            interact()
        }
    }
    DisposableEffect(vm, gestureKey, controls) {
        onDispose {
            // A pending tap batch already survives Activity recreation. Losing a pointer cancels
            // only dragging; its eventual tap commit still belongs to the retained ViewModel.
            if (activity?.isChangingConfigurations == true) { vm.cancelScrub(); vm.seekPreviews.cancel() }
            else vm.cancelAccumulatedSeek()
        }
    }
    LaunchedEffect(timelineInteractions) {
        timelineInteractions.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> timelinePointerActive.value = true
                is DragInteraction.Stop -> { timelinePointerActive.value = false; vm.finishScrub() }
                is DragInteraction.Cancel -> { timelinePointerActive.value = false; vm.cancelAccumulatedSeek() }
            }
        }
    }
    LaunchedEffect(controls, chromeVisible, gesturesEnabled, settingsOpen, presentation, playback.details,
        playback.mediaId, playback.duration, playback.seekable, playback.live, playback.loading, playback.error,
        playback.downloadId, playback.videoEnabled, spoilerQueue.currentKey, spoilerQueue.context, spoilers) {
        vm.configureSeekPreview(controls && chromeVisible && gesturesEnabled && !settingsOpen &&
            presentation in listOf(PlayerPresentation.WATCH, PlayerPresentation.FULLSCREEN))
    }
    LaunchedEffect(playback.playWhenReady, playback.playerState, playback.error, playback.loading, controls) {
        if (playback.error != null) vm.cancelAccumulatedSeek(false)
        if (!playback.playWhenReady || playback.playerState == Player.STATE_ENDED || playback.error != null || playback.loading) visible = true
    }
    LaunchedEffect(settingsOpen, controls, gesturesEnabled, chromeVisible) {
        if (settingsOpen || !controls) vm.cancelAccumulatedSeek()
        else if (!gesturesEnabled || !chromeVisible) vm.cancelScrub()
    }
    LaunchedEffect(pendingSeek) { if (pendingSeek != null) interact() }
    LaunchedEffect(visible, interaction, playback.playWhenReady, playback.playerState, playback.error, playback.loading, scrub != null, settingsOpen, overflowOpen, exploring, focused, controls, pendingSeek) {
        if (controls && visible && playback.playWhenReady && playback.playerState != Player.STATE_ENDED && playback.error == null &&
            !playback.loading && scrub == null && !settingsOpen && !overflowOpen && !exploring && !focused && pendingSeek == null) {
            delay(3000)
            visible = false
        }
    }
    LaunchedEffect(feedbackGeneration) { if (feedback != null) { delay(800); feedback = null } }
    // A new pointer-input key cancels any pending single/double tap on media changes or PiP entry.
    Box(modifier.graphicsLayer { alpha = surfaceAlpha }.background(Color.Black).testTag("player-surface")
        .onGloballyPositioned {
            inputOrigin = it.positionInRoot()
            vm.playbackStatistics.viewport(it.size.width, it.size.height, density.density)
            val bounds = it.boundsInWindow()
            activity?.updatePipSource(android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
        }) {
        if (playback.videoEnabled || presentation != PlayerPresentation.MINI) {
            if (immersive && playback.videoEnabled) ProjectedVideoSurface(playback, controller, Modifier.fillMaxSize(),
                rememberCaptionAppearance(vm), viewing, controls && presentation != PlayerPresentation.MINI,
                failure = { if (vm.queue.value.currentKey == viewing.occurrence && vm.api.context() == viewing.context) vm.viewingFailed() }, pan = vm::panViewing)
            else PlaybackVideoSurface(playback, controller, Modifier.fillMaxSize(), rememberCaptionAppearance(vm))
        }
        else playback.details?.video?.let { video ->
            ChannelSpoilerArtwork(vm, video, Modifier.fillMaxSize(), playbackOccurrence = spoilerQueue.currentKey, offline = playback.downloadId != null) {
            coil.compose.AsyncImage(resolved(vm.store.server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), null,
                Modifier.fillMaxSize().testTag("mini-player-artwork"), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
        if (controls && chromeVisible) PlayerSeekPreview(preview, Modifier.matchParentSize())
        PlayerControlsDim(controls && visible && chromeVisible, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().testTag("player-gestures")
        .pointerInput(playback.mediaId, controls, presentation, gesturesEnabled, exploring, gestureKey, immersive) {
            if (!gesturesEnabled) return@pointerInput
            if (controls || presentation == PlayerPresentation.MINI) coroutineScope {
                var single: Job? = null
                var firstTime = Long.MIN_VALUE
                var firstZone = 0
                fun zone(x: Float) = when { x < size.width / 3f -> -1; x > size.width * 2 / 3f -> 1; else -> 0 }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val claim = { single?.cancel(); firstTime = Long.MIN_VALUE; vm.cancelAccumulatedSeek() }
                    val up = (if (immersive && presentation != PlayerPresentation.MINI && !exploring) {
                        if (!PlayerGestureRules.acceptsStart(presentation, down.position.y + inputOrigin.y, fullscreenTopEdge)) null
                        else viewingGesture(down, vm::panViewing, claim)
                    } else playerGesture(down, presentation, if (exploring) null else currentDrag,
                        position = { it + inputOrigin }, fullscreenTopEdge = fullscreenTopEdge) {
                        single?.cancel(); firstTime = Long.MIN_VALUE; vm.cancelAccumulatedSeek()
                    }) ?: return@awaitEachGesture
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
                    CustomAccessibilityAction("Back $seekSeconds seconds") { if (current.seekable) { seek(-seekSeconds * 1000L); true } else false },
                    CustomAccessibilityAction("Forward $seekSeconds seconds") { if (current.seekable) { seek(seekSeconds * 1000L); true } else false },
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
        // Picture overlays stay above the decoder and gestures, below playback controls.
        // An overlay covering the footer must not intercept fullscreen or seeking taps.
        if (controls && presentation != PlayerPresentation.MINI) AnnotationOverlays(vm, playback, Modifier.matchParentSize())
        pictureOverlay()
        if (controls) {
            val timelinePosition = scrubSession?.targetMs ?: pendingSeek?.target(playback.duration) ?: playback.position
            val sponsorLabels = sponsor.segments.filter { it.start <= timelinePosition && timelinePosition < it.end }
                .map { it.category.label }.distinct()
            val notice: (@Composable () -> Unit)? = if (!settingsOpen && playback.error == null && !playback.loading &&
                (sponsor.active != null || sponsor.notice.isNotBlank())) ({
                PlayerSponsorPrompt(sponsor, { id -> vm.sponsorCommand(PlaybackService.SPONSOR_SKIP, id) },
                    { id -> vm.sponsorCommand(PlaybackService.SPONSOR_DISMISS, id) })
            }) else null
            PlayerControlsLayout(playback, timelinePosition, fullscreen, controller != null,
                onPlay = { vm.togglePlay(); interact() },
                onSettings = { vm.cancelAccumulatedSeek(); interact(); onSettings() },
                onFullscreen = { interact(); onFullscreen() },
                onChapters = { vm.cancelAccumulatedSeek(); interact(); onChapters() },
                onCollapse = onCollapse?.let { collapse -> { vm.cancelAccumulatedSeek(); collapse() } },
                onChat = onChat, chatOpen = replay.open, onRetry = { interact(); vm.retryPlayback() }, showControls = visible && chromeVisible,
                notice = if (chromeVisible) notice else null,
                onOverflowChanged = { overflowOpen = it },
                modifier = Modifier.then(if (visible && chromeVisible) Modifier.testTag("player-controls") else Modifier)
                    .onFocusChanged { focused = it.hasFocus }.focusGroup(),
                timeline = { timelineModifier, trackOffset ->
                    Box(timelineModifier) {
                            Slider(
                                value = timelinePosition.coerceAtMost(playback.duration).toFloat(),
                                onValueChange = { vm.updateScrub(it.toLong()); interact() },
                                onValueChangeFinished = {
                                    // Drag completion also runs on cancellation. Its Stop/Cancel interaction
                                    // owns finishing; only native releases or non-pointer actions commit here.
                                    if (!timelinePointerActive.value) vm.finishScrub()
                                    interact()
                                },
                                interactionSource = timelineInteractions,
                                valueRange = 0f..playback.duration.coerceAtLeast(1).toFloat(),
                                enabled = playback.seekable && playback.duration > 0 && !playback.loading && playback.error == null,
                                thumb = {
                                    Box(Modifier.offset(y = trackOffset).size(12.dp).clip(CircleShape).background(
                                        if (playback.seekable && !playback.loading && playback.error == null) Color.White else Color.Transparent))
                                },
                                track = { slider ->
                                    Canvas(Modifier.fillMaxWidth().offset(y = trackOffset).height(3.dp).testTag("player-seek-track")) {
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
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp).testTag("player-timeline")
                                    .motionEventSpy { event ->
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> timelinePointerActive.value = true
                                            MotionEvent.ACTION_UP -> timelinePointerActive.value = false
                                            MotionEvent.ACTION_CANCEL -> {
                                                timelinePointerActive.value = false
                                                vm.cancelAccumulatedSeek()
                                            }
                                        }
                                    }.semantics {
                                    contentDescription = "Playback position"
                                    stateDescription = (listOfNotNull(ChapterRules.current(chapters, timelinePosition)?.title) + sponsorLabels).joinToString(", ")
                                },
                            )
                            if (scrub != null || pendingSeek != null) PlayerSeekSponsorLabels(sponsorLabels,
                                Modifier.align(Alignment.TopCenter).offset(y = (-24).dp))
                    }
                })
            (pendingSeek?.offset ?: feedback)?.let { offset ->
                PlayerSeekFeedback(offset, Modifier.align(if (offset < 0) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                    .fillMaxWidth(1f / 3f).padding(horizontal = 4.dp)
                    .then(if (pendingSeek != null) Modifier.testTag("pending-seek") else Modifier))
            }
            if (statisticsVisible && statisticsUi.enabled) {
                PlaybackStatisticsOverlay(statisticsUi, statisticsSnapshot, vm.playbackStatistics,
                    Modifier.matchParentSize(), playback.playWhenReady, vm::togglePlay)
            }
        }
    }
}

@Composable
internal fun PlayerSponsorPrompt(sponsor: SponsorBlockPlayback, skip: (String) -> Unit, dismiss: (String) -> Unit) {
    val active = sponsor.active
    if (active == null && sponsor.notice.isBlank()) return
    BoxWithConstraints {
        val noticeStyle = MaterialTheme.typography.labelSmall
        val noticeHeight = with(LocalDensity.current) { noticeStyle.lineHeight.toDp() } + 12.dp
        val showNotice = sponsor.notice.isNotBlank() && (active == null || maxHeight >= 48.dp + 4.dp + noticeHeight)
        Column(Modifier.testTag("sponsorblock-prompt"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Preserve the actions if a very short picture cannot fit the passive confirmation too.
            if (showNotice) {
                Text(sponsor.notice, Modifier.widthIn(max = 280.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp).testTag("sponsorblock-notice")
                    .semantics { liveRegion = LiveRegionMode.Polite }, color = Color.White,
                    style = noticeStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (active != null) {
                Row(Modifier.testTag("sponsorblock-actions"),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { skip(active.id) }, modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("sponsorblock-skip").semantics { contentDescription = "Skip ${active.category.label}" }) {
                        Text("Skip", color = Color.White, maxLines = 1)
                    }
                    AppInsetIconButton(onClick = { dismiss(active.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, "Dismiss SponsorBlock segment", Modifier.size(20.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerSeekSponsorLabels(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    Text(labels.joinToString(", "), modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("player-seek-sponsor-labels"),
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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlayerSettings(vm: AppViewModel, playback: PlaybackState, dismiss: () -> Unit, pip: () -> Unit, supportsPip: Boolean) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    val viewing by vm.viewing.collectAsStateWithLifecycle()
    val statistics by vm.playbackStatistics.ui.collectAsStateWithLifecycle()
    var page by rememberSaveable(queue.context, queue.currentKey, playback.mediaId) { mutableStateOf("Player settings") }
    var moreExpanded by rememberSaveable(queue.context, queue.currentKey, playback.mediaId) { mutableStateOf(false) }
    val formats = playback.details?.formats.orEmpty()
    val video = StreamCatalog.choices(playback.tracks, C.TRACK_TYPE_VIDEO, formats)
    val audio = StreamCatalog.choices(playback.tracks, C.TRACK_TYPE_AUDIO, formats)
    val captions = playback.trackChoices(C.TRACK_TYPE_TEXT)
    val selection = playback.selection
    val audioOnly = selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true
    val selectedAudio = audio.firstOrNull { it.selected && it.explicitlySelected(selection) }
        ?: audio.firstOrNull { it.selected }
    val selectedCaption = if (selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true) null else captions.firstOrNull { it.selected }
    val captionFailure by vm.captionFailure.collectAsStateWithLifecycle()
    val qualityAuto = playback.videoSelection.mode == net.wingress.mobivious.player.VideoSelectionMode.AUTO
    val selectedVideo = if (qualityAuto) null else playback.videoSelection.fixed(video)
    val available = !playback.loading && playback.canSelectTracks
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Handle system Back in the sheet's dialog before the watch screen's handler.
    fun back() { when (page) { "Player settings" -> dismiss(); "Caption appearance" -> page = "Captions"; else -> page = "Player settings" } }
    AppModalSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        BackHandler { back() }
        PanelSurface(Modifier.fillMaxWidth()) {
            WatchPanelToolbar(page, "Close player settings", dismiss,
                backLabel = if (page == "Caption appearance") "Back to captions" else "Back to player settings", back = if (page != "Player settings") ({ back() }) else null)
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .testTag("player-settings-list").padding(vertical = 8.dp)) {
                if (page == "Player settings") {
                    SettingRow("Quality", if (video.isEmpty()) "Unavailable" else if (qualityAuto) "Auto" else selectedVideo?.let(StreamCatalog::qualityText) ?: "Unavailable",
                        available && !audioOnly && video.isNotEmpty(), icon = Icons.Default.HighQuality) { page = "Quality" }
                    SettingRow("Audio", selectedAudio?.primary ?: "Unavailable", available && audio.isNotEmpty(), icon = Icons.Default.Audiotrack) { page = "Audio" }
                    SettingRow("Captions", if (captions.isEmpty()) "Unavailable" else selectedCaption?.label ?: "Off", icon = Icons.Default.ClosedCaption) { page = "Captions" }
                    SettingRow("Playback speed", speedLabel(playback.speed), !playback.loading && playback.canSetSpeed, icon = Icons.Default.Speed) { page = "Playback speed" }
                    if (playback.details?.projectionType in listOf("EQUIRECTANGULAR", "MESH")) {
                        SettingRow("Viewing mode", viewing.mode.label, !audioOnly, icon = Icons.Default.ViewInAr) { page = "Viewing mode" }
                    }
                    PlayerSettingsMore(moreExpanded, { moreExpanded = it }) {
                        SettingRow("Annotations", if (annotations.enabled) "On" else "Off", icon = Icons.AutoMirrored.Filled.Comment) { page = "Annotations" }
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .toggleable(audioOnly, enabled = available, role = Role.Switch, onValueChange = vm::audioOnly)
                            .padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (available) 1f else .38f)
                            Icon(Icons.Default.Headphones, null, Modifier.size(24.dp), tint = color)
                            Text("Audio only", Modifier.weight(1f), color = color)
                            Switch(checked = audioOnly, onCheckedChange = null, enabled = available)
                        }
                        SettingRow("Picture in picture", if (supportsPip) "" else "Unavailable on this device",
                            supportsPip && playback.details != null && playback.error == null && !playback.loading,
                            icon = Icons.Default.PictureInPictureAlt, opensPage = false) { dismiss(); pip() }
                        SettingRow("Refresh buffer", enabled = playback.canRefresh && !playback.loading,
                            icon = Icons.Default.Refresh, opensPage = false) { dismiss(); vm.refreshBuffer() }
                        SettingRow("Playback statistics", if (statistics.enabled) "On" else "Off",
                            icon = Icons.Default.QueryStats, opensPage = false) { vm.playbackStatistics.toggle(); dismiss() }
                    }
                } else when (page) {
                    "Annotations" -> AnnotationSettings(vm, playback)
                    "Viewing mode" -> ViewingSettings(vm, playback)
                    "Quality" -> {
                        run { SettingChoice("Auto", qualityAuto, available && !audioOnly) { vm.autoQuality(); page = "Player settings" } }
                        StreamCatalog.qualityMenu(video).forEach { track -> SettingChoice(track.primary, !qualityAuto && track == selectedVideo, available && !audioOnly, track.secondary) {
                            vm.selectTrack(track.group, track.index); page = "Player settings"
                        } }
                    }
                    "Playback speed" -> PreferenceRules.speeds.forEach { speed ->
                        SettingChoice(speedLabel(speed), playback.speed == speed, playback.canSetSpeed) { vm.speed(speed); page = "Player settings" }
                    }
                    "Audio" -> {
                        AudioSection.entries.forEach { section ->
                            val choices = audio.filter { it.section == section }
                            if (choices.isNotEmpty()) {
                                run { Text(section.title, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                choices.forEach { track -> SettingChoice(track.primary, track == selectedAudio, available, track.secondary) { vm.selectTrack(track.group, track.index); page = "Player settings" } }
                            }
                        }
                    }
                    "Captions" -> {
                        run { SettingRow("Caption appearance", icon = Icons.Default.TextFormat) { page = "Caption appearance" } }
                        captionFailure?.takeIf { failure ->
                            val selected = selectedCaption?.let { it.group.getFormat(it.index) }
                            failure.occurrence == vm.queue.value.currentKey && selection?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) != true &&
                                if (selected == null) failure.language in selection?.preferredTextLanguages.orEmpty()
                                else failure.language == selected.language && failure.label == selected.label
                        }?.let { failure ->
                            Column(Modifier.padding(16.dp).testTag("caption-load-error")) {
                                Text(failure.message, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { vm.retryPlayback() }, enabled = !playback.loading) { Text("Retry captions") }
                            }
                        }
                        if (captions.isEmpty()) Text("No caption tracks available for this video.", Modifier.padding(16.dp))
                        run { SettingChoice("Off", selectedCaption == null, available) { vm.captions(null); page = "Player settings" } }
                        captions.forEach { track -> SettingChoice(track.label, track == selectedCaption, available) { vm.selectTrack(track.group, track.index); page = "Player settings" } }
                    }
                    "Caption appearance" -> Box(Modifier.padding(horizontal = 16.dp)) { CaptionAppearanceEditor(vm) }
                }
            }
        }
    }
}

@Composable
internal fun PlayerSettingsMore(expanded: Boolean, change: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingRow("More", icon = Icons.Default.MoreHoriz, opensPage = false,
        trailingIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        modifier = Modifier.testTag("player-settings-more").semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) { change(!expanded) }
    if (expanded) Column(content = content)
}

@Composable
private fun SettingRow(label: String, value: String = "", enabled: Boolean = true,
    icon: ImageVector, opensPage: Boolean = true, trailingIcon: ImageVector? = null,
    modifier: Modifier = Modifier, click: () -> Unit) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    val detailColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button, onClick = click)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        if (stacked) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = color)
            if (value.isNotEmpty()) Text(value, color = detailColor, style = MaterialTheme.typography.bodyMedium)
        } else Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = color)
            if (value.isNotEmpty()) Text(value, Modifier.padding(start = 16.dp).widthIn(max = 160.dp),
                color = detailColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val trailing = trailingIcon ?: if (opensPage) Icons.Default.ChevronRight else null
        if (trailing != null) Icon(trailing, null, Modifier.size(24.dp), tint = detailColor)
    }
}

@Composable
internal fun SettingChoice(label: String, selected: Boolean, enabled: Boolean, detail: String = "", click: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = click)
        .semantics { this.selected = selected }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (detail.isNotBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}
