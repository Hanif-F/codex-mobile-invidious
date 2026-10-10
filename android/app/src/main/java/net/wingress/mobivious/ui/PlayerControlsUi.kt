package net.wingress.mobivious.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

/** White, lightweight chrome. Video decoding surfaces are never sampled for glass. */
@Composable
internal fun PlayerControlsLayout(playback: PlaybackState, positionMs: Long, fullscreen: Boolean,
    settingsEnabled: Boolean, onPlay: () -> Unit, onSettings: () -> Unit,
    onFullscreen: () -> Unit, onChapters: () -> Unit, modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null, onChat: (() -> Unit)? = null, chatOpen: Boolean = false, onRetry: (() -> Unit)? = null,
    showControls: Boolean = true, notice: (@Composable () -> Unit)? = null,
    timeline: @Composable (modifier: Modifier, trackOffset: Dp) -> Unit) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(48.dp) }
    var footerHeight by remember { mutableStateOf(96.dp) }
    var noticeHeight by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Two 48dp rows keep chapter/fullscreen targets separate from full-width seeking.
        // Very shallow pictures share the upper row with the header controls.
        val sharedHeader = maxHeight < 144.dp
        val occupiedTop = headerHeight + if (notice == null) 0.dp else noticeHeight
        fun centerFits(size: Dp) = !sharedHeader && maxHeight / 2 - size / 2 >= occupiedTop + 1.dp &&
            maxHeight / 2 + size / 2 <= maxHeight - footerHeight - 1.dp
        val centerSize = if (centerFits(64.dp)) 64.dp else 48.dp
        val centered = centerFits(centerSize)
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            AnimatedVisibility(showControls && !sharedHeader, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
                PlayerControlHeader(playback, fullscreen, settingsEnabled, onSettings,
                    onCollapse = onCollapse, onChat = onChat, chatOpen = chatOpen,
                    compactPlay = if (centered) null else onPlay,
                    modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                        .playerEdgeContrast(top = true)
                        .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier)
                        .padding(horizontal = 4.dp))
            }
            if (notice != null) Box(Modifier.fillMaxWidth()
                .onSizeChanged { noticeHeight = with(density) { it.height.toDp() } }
                .padding(horizontal = 8.dp, vertical = 4.dp)) { notice() }
        }
        AnimatedVisibility(showControls, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                .playerEdgeContrast(top = false)
                .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) else Modifier)
                .padding(horizontal = 8.dp)) {
            PlayerControlFooter(playback, positionMs, fullscreen, onFullscreen, onChapters, onRetry,
                header = if (sharedHeader) FooterHeader(settingsEnabled, onSettings, onPlay, onCollapse, onChat, chatOpen) else null,
                timeline = timeline)
        }
        AnimatedVisibility(showControls && centered && !playback.loading && playback.error == null,
            enter = fadeIn(tween(160)), exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.testTag("player-transport")) { PlayerPlayControl(playback, Modifier.size(centerSize), play = onPlay) }
        }
    }
}

@Composable
private fun Modifier.playerEdgeContrast(top: Boolean): Modifier {
    if (LocalReduceTransparency.current) return background(Color(0xFF171B24))
    val edge = Color.Black.copy(alpha = .18f)
    return background(Brush.verticalGradient(if (top) listOf(edge, Color.Transparent) else listOf(Color.Transparent, edge)))
}

internal data class FooterHeader(val settingsEnabled: Boolean, val onSettings: () -> Unit,
    val onPlay: () -> Unit, val onCollapse: (() -> Unit)?, val onChat: (() -> Unit)?, val chatOpen: Boolean)

@Composable
internal fun PlayerControlFooter(playback: PlaybackState, positionMs: Long, fullscreen: Boolean,
    onFullscreen: () -> Unit, onChapters: () -> Unit, onRetry: (() -> Unit)? = null,
    header: FooterHeader? = null,
    timeline: @Composable (Modifier, Dp) -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val timeStyle = MaterialTheme.typography.labelSmall
    val portrait = !fullscreen && LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val completeTime = playbackTimeLabel(playback, positionMs)
    fun textWidth(text: String): Dp = with(density) {
        measurer.measure(AnnotatedString(text), timeStyle, maxLines = 1, softWrap = false).size.width.toDp()
    } + 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("player-footer")) {
        val hasChapters = playback.chapters.isNotEmpty()
        val hasChat = header?.onChat != null && playback.clip == null && playback.details?.chatAvailable == true && !playback.live
        val hasPlay = header != null && !playback.loading && playback.error == null
        val headerWidth = if (header == null) 0.dp else 52.dp + (if (hasChat) 52.dp else 0.dp) + (if (hasPlay) 52.dp else 0.dp)
        val collapse = header?.onCollapse?.takeIf { maxWidth >= headerWidth + 52.dp + 56.dp + (if (hasChapters) 76.dp else 28.dp) }
        val reserved = 56.dp + headerWidth + (if (collapse != null) 52.dp else 0.dp) + if (hasChapters) 52.dp else 0.dp
        val compactTime = textWidth(completeTime) > maxWidth - reserved
        val timeWidth = textWidth(if (compactTime) playerTime(positionMs) else completeTime)
            .coerceAtMost((maxWidth - reserved).coerceAtLeast(24.dp))
        val chapterSpace = maxWidth - timeWidth - reserved + if (hasChapters) 48.dp else 0.dp
        val compactChapter = compactTime || chapterSpace < 80.dp
        val chapterWidth = if (compactChapter) 48.dp else minOf(chapterSpace, 240.dp).coerceAtLeast(48.dp)
        // Bring the visible controls together in portrait, keeping their 48dp hit areas separate.
        val labelHeight = with(density) { measurer.measure(AnnotatedString("Mg"), timeStyle).size.height.toDp() }
        val contentOffset = if (portrait && header == null) minOf(12.dp, (48.dp - labelHeight) / 2).coerceAtLeast(0.dp) else 0.dp
        val trackOffset = if (portrait && header == null) (-12).dp else 0.dp
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (header != null) Modifier.testTag("player-header") else Modifier), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (collapse != null) PlayerIconButton(Icons.Default.KeyboardArrowDown,
                    if (fullscreen) "Return to watch page" else "Minimize player", onClick = collapse)
                if (hasPlay) PlayerPlayControl(playback, Modifier.size(48.dp), play = header.onPlay)
                when {
                    playback.error != null -> {
                        Text(playback.error, Modifier.weight(1f), color = Color.White, style = timeStyle,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = Color.White) }
                    }
                    playback.loading -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Loading video" },
                            color = Color.White, strokeWidth = 2.dp)
                        Text("Loading video", color = Color.White, style = timeStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else -> {
                        PlayerTime(playback, positionMs, Modifier.width(timeWidth).offset(y = contentOffset), compactTime)
                        if (hasChapters) PlayerChapterTitle(playback.chapters, positionMs,
                            Modifier.width(chapterWidth), compact = compactChapter, contentOffset = contentOffset, open = onChapters)
                        Spacer(Modifier.weight(1f))
                    }
                }
                if (hasChat) PlayerIconButton(Icons.AutoMirrored.Filled.Chat, if (header.chatOpen) "Hide chat replay" else "Show chat replay",
                    Modifier.testTag("player-chat").semantics { selected = header.chatOpen }, onClick = header.onChat)
                if (header != null) PlayerIconButton(Icons.Default.Settings, "Player settings", enabled = header.settingsEnabled, onClick = header.onSettings)
                PlayerIconButton(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    if (fullscreen) "Exit full screen" else "Full screen", contentOffset = contentOffset, onClick = onFullscreen)
            }
            if (!playback.loading && playback.error == null) timeline(Modifier.fillMaxWidth().height(48.dp), trackOffset)
        }
    }
}

private fun playbackTimeLabel(playback: PlaybackState, positionMs: Long) =
    "${playerTime(positionMs)} / ${if (playback.live) "LIVE" else if (playback.duration > 0) playerTime(playback.duration) else "—"}"

@Composable
internal fun PlayerTime(playback: PlaybackState, positionMs: Long, modifier: Modifier = Modifier, compact: Boolean = false) {
    Text(if (compact) playerTime(positionMs) else playbackTimeLabel(playback, positionMs),
        modifier.testTag("player-time").semantics { contentDescription = playbackTimeLabel(playback, positionMs) },
        color = Color.White, style = MaterialTheme.typography.labelSmall.copy(shadow = PlayerTextShadow),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

internal val PlayerTextShadow = Shadow(Color.Black.copy(alpha = .8f), Offset(0f, 1f), 3f)

@Composable
internal fun PlayerControlHeader(playback: PlaybackState, fullscreen: Boolean, settingsEnabled: Boolean,
    onSettings: () -> Unit, modifier: Modifier = Modifier, onCollapse: (() -> Unit)? = null,
    onChat: (() -> Unit)? = null, chatOpen: Boolean = false, compactPlay: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().testTag("player-header"), verticalAlignment = Alignment.CenterVertically) {
        if (onCollapse != null) PlayerIconButton(Icons.Default.KeyboardArrowDown,
            if (fullscreen) "Return to watch page" else "Minimize player", onClick = onCollapse)
        if (compactPlay != null) PlayerPlayControl(playback, Modifier.size(48.dp), play = compactPlay)
        Spacer(Modifier.weight(1f))
        if (onChat != null && playback.clip == null && playback.details?.chatAvailable == true && !playback.live)
            PlayerIconButton(Icons.AutoMirrored.Filled.Chat, if (chatOpen) "Hide chat replay" else "Show chat replay",
                Modifier.testTag("player-chat").semantics { selected = chatOpen }, onClick = onChat)
        PlayerIconButton(Icons.Default.Settings, "Player settings", enabled = settingsEnabled, onClick = onSettings)
    }
}

@Composable
private fun PlayerIconButton(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, contentOffset: Dp = 0.dp, onClick: () -> Unit) {
    IconButton(onClick, modifier.size(48.dp), enabled = enabled) {
        Box(Modifier.offset(y = contentOffset)) { PlayerIcon(icon, description, enabled = enabled) }
    }
}

@Composable
private fun PlayerIcon(icon: ImageVector, description: String, size: Dp = 24.dp, enabled: Boolean = true) {
    Box(Modifier.size(size)) {
        Icon(icon, null, Modifier.fillMaxSize().offset(y = 1.dp).blur(2.dp), tint = Color.Black.copy(alpha = .65f))
        Icon(icon, description, Modifier.fillMaxSize(), tint = Color.White.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable
internal fun PlayerPlayControl(playback: PlaybackState, modifier: Modifier = Modifier, play: () -> Unit) {
    if (playback.loading || playback.error != null) return
    val ended = playback.playerState == Player.STATE_ENDED
    val icon = if (ended) Icons.Default.Replay else if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow
    val description = if (ended) "Replay" else if (playback.playWhenReady) "Pause" else "Play"
    IconButton(onClick = play, enabled = playback.canPlay, modifier = modifier.testTag("player-play-pause")
        .then(if (LocalReduceTransparency.current) Modifier.background(Color(0xFF171B24), CircleShape) else Modifier)) {
        PlayerIcon(icon, description, size = 36.dp, enabled = playback.canPlay)
        if (playback.buffering) CircularProgressIndicator(Modifier.size(44.dp).semantics { contentDescription = "Buffering" },
            color = Color.White, strokeWidth = 2.dp)
    }
}
