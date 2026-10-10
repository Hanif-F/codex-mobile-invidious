package net.wingress.mobivious.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

/** Insets and measured chrome determine whether transport fits at the picture's actual center. */
@Composable
internal fun PlayerControlsLayout(playback: PlaybackState, positionMs: Long, fullscreen: Boolean,
    settingsEnabled: Boolean, onPlay: () -> Unit, onSeek: (Long) -> Unit, onSettings: () -> Unit,
    onFullscreen: () -> Unit, onChapters: () -> Unit, modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null, onChat: (() -> Unit)? = null, chatOpen: Boolean = false, onRetry: (() -> Unit)? = null,
    showControls: Boolean = true, notice: (@Composable () -> Unit)? = null,
    timeline: @Composable (modifier: Modifier, trackOffset: Dp) -> Unit) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(64.dp) }
    var railHeight by remember { mutableStateOf(64.dp) }
    var noticeHeight by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val promptHeight = if (notice == null) 0.dp else noticeHeight
        val singleRow = maxHeight < headerHeight + promptHeight + railHeight + 8.dp
        val occupiedTop = promptHeight + if (singleRow) 0.dp else headerHeight
        val centered = maxHeight / 2 - 32.dp >= occupiedTop + 8.dp &&
            maxHeight / 2 + 32.dp <= maxHeight - railHeight - 8.dp
        val compact = !centered
        val railChat = maxWidth >= 400.dp && onChat != null && playback.clip == null && playback.details?.chatAvailable == true && !playback.live
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            AnimatedVisibility(showControls && !singleRow, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
                PlayerControlHeader(playback, positionMs, fullscreen, settingsEnabled,
                    onSettings, onFullscreen, onChapters, onCollapse = onCollapse, onChat = onChat, chatOpen = chatOpen,
                    modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                        .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier)
                        .padding(8.dp))
            }
            if (notice != null) Box(Modifier.fillMaxWidth()
                .onSizeChanged { noticeHeight = with(density) { it.height.toDp() } }
                .then(if (fullscreen && singleRow) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier)
                .padding(horizontal = 8.dp).padding(bottom = 4.dp)) { notice() }
        }
        AnimatedVisibility(showControls, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { railHeight = with(density) { it.height.toDp() } }
                .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) else Modifier)
                .padding(8.dp)) {
            Row(Modifier.fillMaxWidth().mediaGlass().padding(horizontal = 8.dp).testTag("player-footer"),
                verticalAlignment = Alignment.CenterVertically) {
                if (singleRow && onCollapse != null) IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, if (fullscreen) "Return to watch page" else "Minimize player", tint = Color.White)
                }
                if (compact) PlayerPlayControl(playback, Modifier.size(48.dp), glass = false, play = onPlay)
                val labelHeight = with(density) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
                Box(Modifier.weight(1f).height((labelHeight + 32.dp).coerceAtLeast(48.dp))) {
                    when {
                        playback.error != null -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Text(playback.error, Modifier.weight(1f).padding(horizontal = 6.dp), color = Color.White,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = Color.White) }
                        }
                        playback.loading -> Row(Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Loading video" },
                                color = Color.White, strokeWidth = 2.dp)
                            Text("Loading video", color = Color.White, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        else -> {
                            timeline(Modifier.fillMaxSize(), labelHeight / 2)
                            PlayerTime(playback, positionMs, Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp))
                        }
                    }
                }
                if (singleRow) {
                    if (railChat)
                        IconButton(onClick = onChat, modifier = Modifier.testTag("player-chat").semantics { selected = chatOpen }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, if (chatOpen) "Hide chat replay" else "Show chat replay", tint = Color.White)
                        }
                    IconButton(onClick = onSettings, enabled = settingsEnabled) { Icon(Icons.Default.Settings, "Player settings",
                        tint = Color.White.copy(alpha = if (settingsEnabled) 1f else .38f)) }
                    IconButton(onClick = onFullscreen) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        if (fullscreen) "Exit full screen" else "Full screen", tint = Color.White) }
                }
            }
        }
        AnimatedVisibility(showControls && centered, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.Center)) {
            PlayerTransport(playback, onPlay, onSeek)
        }
    }
}

@Composable
internal fun PlayerTime(playback: PlaybackState, positionMs: Long, modifier: Modifier = Modifier) {
    Text("${playerTime(positionMs)} / ${if (playback.live) "LIVE" else if (playback.duration > 0) playerTime(playback.duration) else "—"}",
        modifier.testTag("player-time"), color = Color.White, style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun PlayerControlHeader(playback: PlaybackState, positionMs: Long, fullscreen: Boolean, settingsEnabled: Boolean,
    onSettings: () -> Unit, onFullscreen: () -> Unit, onChapters: () -> Unit, modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null, onChat: (() -> Unit)? = null, chatOpen: Boolean = false) {
    Row(modifier.fillMaxWidth().testTag("player-header"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onCollapse != null) WatchIconButton(Icons.Default.KeyboardArrowDown,
            if (fullscreen) "Return to watch page" else "Minimize player", media = true, onClick = onCollapse)
        if (playback.chapters.isNotEmpty()) Box(Modifier.weight(1f)) {
            PlayerChapterTitle(playback.chapters, positionMs, Modifier.widthIn(max = 320.dp).fillMaxWidth().mediaGlass(), onChapters)
        }
        else Spacer(Modifier.weight(1f))
        if (onChat != null && playback.clip == null && playback.details?.chatAvailable == true && !playback.live)
            WatchIconButton(Icons.AutoMirrored.Filled.Chat, if (chatOpen) "Hide chat replay" else "Show chat replay",
                Modifier.testTag("player-chat").semantics { selected = chatOpen }, media = true, onClick = onChat)
        WatchIconButton(Icons.Default.Settings, "Player settings", enabled = settingsEnabled, media = true, onClick = onSettings)
        WatchIconButton(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            if (fullscreen) "Exit full screen" else "Full screen", media = true, onClick = onFullscreen)
    }
}

@Composable
internal fun PlayerTransport(playback: PlaybackState, play: () -> Unit, seek: (Long) -> Unit) {
    if (playback.loading || playback.error != null) return
    val seekable = playback.seekable && playback.duration > 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.testTag("player-transport")) {
        WatchIconButton(Icons.Default.Replay10, "Back 10 seconds", Modifier.testTag("player-back-10"),
            enabled = seekable, media = true) { seek(-10_000) }
        PlayerPlayControl(playback, Modifier.size(64.dp), play = play)
        WatchIconButton(Icons.Default.Forward10, "Forward 10 seconds", Modifier.testTag("player-forward-10"),
            enabled = seekable, media = true) { seek(10_000) }
    }
}

@Composable
internal fun PlayerPlayControl(playback: PlaybackState, modifier: Modifier = Modifier, glass: Boolean = true, play: () -> Unit) {
    if (playback.loading || playback.error != null) return
    val ended = playback.playerState == Player.STATE_ENDED
    val icon = if (ended) Icons.Default.Replay else if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow
    val description = if (ended) "Replay" else if (playback.playWhenReady) "Pause" else "Play"
    if (glass) WatchIconButton(icon, description, modifier.testTag("player-play-pause"),
        enabled = playback.canPlay, media = true, size = 64.dp, busy = playback.buffering, onClick = play)
    else IconButton(onClick = play, enabled = playback.canPlay, modifier = modifier.testTag("player-play-pause")) {
        Icon(icon, description, Modifier.size(28.dp), tint = Color.White.copy(alpha = if (playback.canPlay) 1f else .38f))
        if (playback.buffering) CircularProgressIndicator(Modifier.size(38.dp).semantics {
            contentDescription = "Buffering"
        }, color = Color.White, strokeWidth = 2.dp)
    }
}
