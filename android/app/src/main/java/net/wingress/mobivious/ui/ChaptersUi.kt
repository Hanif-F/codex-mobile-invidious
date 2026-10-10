package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.ChapterRules
import net.wingress.mobivious.data.VideoChapter

internal val PlaybackState.chapters: List<VideoChapter>
    get() = details?.takeIf { clip == null && it.video.id == mediaId }?.chapters?.let {
        ChapterRules.available(it, duration, live)
    }.orEmpty()

/** Owned above watch/fullscreen so moving the list never resets its position or open state. */
@Stable
internal class ChapterPanelState(initialOpen: Boolean = false, val list: LazyListState = LazyListState(), initialReveal: Int? = null) {
    var open by mutableStateOf(initialOpen); private set
    var revealIndex by mutableStateOf(initialReveal); private set

    fun show(chapters: List<VideoChapter>, positionMs: Long) {
        if (chapters.size < 2 || open) return
        revealIndex = chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
        open = true
    }
    fun close() { open = false; revealIndex = null }
    fun revealed() { revealIndex = null }

    companion object {
        val saver = Saver<ChapterPanelState, List<Int>>(
            save = { listOf(if (it.open) 1 else 0, it.list.firstVisibleItemIndex, it.list.firstVisibleItemScrollOffset, it.revealIndex ?: -1) },
            restore = { ChapterPanelState(it[0] == 1, LazyListState(it[1], it[2]), it[3].takeIf { index -> index >= 0 }) },
        )
    }
}

@Composable
internal fun rememberChapterPanelState(): ChapterPanelState = rememberSaveable(saver = ChapterPanelState.saver) { ChapterPanelState() }

/** The footer chapter action keeps a full accessible title even in its compact chevron form. */
@Composable
internal fun PlayerChapterTitle(chapters: List<VideoChapter>, positionMs: Long, modifier: Modifier = Modifier,
    compact: Boolean = false, contentOffset: Dp = 0.dp, open: () -> Unit) {
    if (chapters.isEmpty()) return
    val title = ChapterRules.current(chapters, positionMs)?.title ?: "Chapters"
    Row(modifier.heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClickLabel = "Open chapters", onClick = open)
        .semantics { contentDescription = "Current chapter: $title" }
        .testTag("player-chapter-title").padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (!compact) Text(title,
            Modifier.weight(1f).offset(y = contentOffset).testTag("player-current-chapter"), color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(shadow = PlayerTextShadow),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp).offset(y = contentOffset), tint = Color.White.copy(alpha = .8f))
    }
}

@Composable
internal fun ChaptersPanel(chapters: List<VideoChapter>, positionMs: Long, state: ChapterPanelState,
    enabled: Boolean, close: () -> Unit, seek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val current = ChapterRules.current(chapters, positionMs)
    LaunchedEffect(state.revealIndex, chapters) {
        state.revealIndex?.let { index ->
            if (chapters.isNotEmpty()) state.list.scrollToItem(index.coerceAtMost(chapters.lastIndex))
            state.revealed()
        }
    }
    WatchPanelSurface(modifier.testTag("chapters-panel")) {
            WatchPanelToolbar("Chapters", "Close chapters", close, Icons.Default.FormatListNumbered,
                detail = "${chapters.size} chapters")
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("chapters-list"), state = state.list,
                contentPadding = PaddingValues(bottom = 24.dp)) {
                items(chapters, key = VideoChapter::startMs) { chapter ->
                    val active = chapter == current
                    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Seek to ${playerTime(chapter.startMs)}") { seek(chapter.startMs) }
                        .semantics { selected = active; if (active) stateDescription = "Current chapter" }
                        .testTag("chapter-${chapter.startMs}").padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(chapter.title, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge)
                        Text(playerTime(chapter.startMs), color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersSheet(chapters: List<VideoChapter>, playback: PlaybackState, state: ChapterPanelState, seek: (Long) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .85f }
    ModalBottomSheet(onDismissRequest = state::close, sheetState = sheetState, sheetMaxWidth = 640.dp) {
        BackHandler { state.close() }
        ChaptersPanel(chapters, playback.position, state, playback.seekable && !playback.loading && playback.error == null,
            state::close, seek, Modifier.fillMaxWidth().height(height))
    }
}
