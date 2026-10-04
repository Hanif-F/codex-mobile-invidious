package net.wingress.mobivious.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackService

@Composable
internal fun PlaybackQueuePanel(vm: AppViewModel, signIn: () -> Unit, channel: (String) -> Unit) {
    val state by vm.queue.collectAsStateWithLifecycle()
    val expanded by vm.queueExpanded.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val titles by vm.dearrowTitles.titles.collectAsStateWithLifecycle()
    val current = state.current?.video
    val currentTitle = current?.let { if (prefs.dearrowEnabled) titles[it.id] ?: it.title else it.title }
    val owned = state.source?.owned == true || lists.any { it.id == state.source?.id && it.owned }
    PlaybackQueueContent(state, expanded, prefs.showMemberVideos, currentTitle,
        toggle = { vm.queueExpanded.value = !vm.queueExpanded.value }, previous = { vm.nextQueue(-1) }, next = { vm.nextQueue(1) },
        repeat = vm::repeatQueue, retry = vm::retryPlayback, more = { vm.queueCommand(PlaybackService.QUEUE_MORE) }) { entry ->
        val indicator = WatchedIndicators.forVideo(entry.video,
            watched.takeIf { it.context == ApiContext(vm.store.server, account) } ?: WatchedState())
        val eligible = QueueRules.eligible(entry, prefs.showMemberVideos)
        QueueVideoRow(entry, entry.key == state.currentKey, vm.store.server, prefs.thinMode, indicator, eligible,
            hiddenMember = entry.video.membersOnly && !prefs.showMemberVideos,
            play = { vm.selectQueue(entry.key) }, channel = channel,
            title = { modifier -> DeArrowTitle(vm, entry.video, MaterialTheme.typography.titleSmall, modifier, maxLines = 2,
                fontWeight = if (entry.key == state.currentKey) FontWeight.Bold else FontWeight.Medium) }) {
            VideoActionsMenu(vm, entry.video, signIn, channel,
                audio = { vm.audioOnly(true); if (eligible) vm.selectQueue(entry.key) },
                remove = if (!entry.removed) ({ vm.queueCommand(PlaybackService.QUEUE_REMOVE) { putString("key", entry.key) } }) else null,
                removalLabel = "Remove from queue",
                removeFromPlaylist = if (owned && entry.video.indexId.isNotEmpty() && !entry.removed) ({
                    vm.queueCommand(PlaybackService.QUEUE_DELETE_SOURCE) { putString("key", entry.key) }
                }) else null, removeFromPlaylistEnabled = !state.loading && !state.sourceLoading)
        }
    }
}

/** The viewport scrolls independently; following playback never moves the watch page. */
@Composable
internal fun PlaybackQueueContent(state: PlaybackQueueSnapshot, expanded: Boolean, showMembers: Boolean,
    currentTitle: String? = state.current?.video?.title, toggle: () -> Unit, previous: () -> Unit, next: () -> Unit,
    repeat: (QueueRepeat) -> Unit, retry: () -> Unit, more: () -> Unit,
    row: @Composable (QueueOccurrence) -> Unit) {
    if (!state.hasExplicitQueue) return
    val visible = state.items.filter { (!it.removed || it.key == state.currentKey) &&
        (showMembers || !it.video.membersOnly || it.key == state.currentKey) }
    val list = key(state.token) { rememberLazyListState() }
    LaunchedEffect(state.token, state.currentKey, expanded) {
        if (expanded) visible.indexOfFirst { it.key == state.currentKey }.takeIf { it >= 0 }?.let { list.scrollToItem(it) }
    }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("playback-queue"),
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            ActionRow(state.source?.title?.ifBlank { "Playback queue" } ?: "Playback queue",
                detail = currentTitle?.let { "Now playing · $it" }, icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                trailingIcon = Icons.Default.ExpandMore,
                trailingRotation = animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "queue-chevron").value,
                actionLabel = if (expanded) "Collapse playback queue" else "Expand playback queue",
                modifier = Modifier.testTag("playback-queue-header").semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                onClick = toggle)
            AnimatedVisibility(expanded, enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))) {
              Column {
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(previous, enabled = !state.loading && (state.currentIndex > 0 ||
                            (state.current?.sourceIndex ?: 0) > 0 || state.repeat == QueueRepeat.ALL)) {
                            Icon(Icons.Default.SkipPrevious, "Previous video")
                        }
                        IconButton(next, enabled = !state.loading && (QueueRules.successor(state, 1, showMembers) != null ||
                            !state.sourceComplete || state.repeat == QueueRepeat.ALL)) {
                            Icon(Icons.Default.SkipNext, "Next video")
                        }
                        Text("Repeat", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueRepeat.entries.forEach { value ->
                            FilterChip(selected = state.repeat == value, onClick = { repeat(value) },
                                enabled = value != QueueRepeat.ALL || state.source?.mix != true,
                                label = { Text(value.label) }, modifier = Modifier.testTag("queue-repeat-${value.name}"))
                        }
                    }
                }
              }
            }
            // Status and recovery remain accessible even if the user collapsed the list.
            if (state.loading || state.sourceLoading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("queue-loading"))
            if (state.error != null || state.sourceError != null) Column(Modifier.padding(12.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); OutlinedButton(retry) { Text("Retry playback") } }
                state.sourceError?.let { Text(it, color = MaterialTheme.colorScheme.error); OutlinedButton(more) { Text("Retry queue") } }
            }
            AnimatedVisibility(expanded, enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))) {
              Column {
                if (visible.isEmpty() && !state.loading && !state.sourceLoading) Text("Your queue is empty. Add a video using its actions menu.", Modifier.padding(12.dp))
                if (visible.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 288.dp).testTag("playback-queue-items"), state = list) {
                    items(visible, key = { it.key }) { entry -> row(entry) }
                }
                if (!state.sourceComplete && !state.sourceLoading) OutlinedButton(more, enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Load more queue items") }
              }
            }
        }
    }
}

@Composable
internal fun QueueVideoRow(entry: QueueOccurrence, current: Boolean, server: String, thinMode: Boolean,
    indicator: VideoIndicator, eligible: Boolean, hiddenMember: Boolean = false,
    play: () -> Unit, channel: (String) -> Unit,
    title: @Composable (Modifier) -> Unit = { modifier -> Text(entry.video.title, modifier, maxLines = 2,
        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium) },
    actions: @Composable () -> Unit) {
    val video = entry.video
    Row(Modifier.fillMaxWidth().background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .testTag("queue-occurrence-${entry.key}")
        .semantics {
            selected = current
            stateDescription = listOfNotNull(if (current) "Now playing" else null, if (entry.removed) "Removed from queue" else null,
                if (video.unavailable) "Unavailable video" else null, if (hiddenMember) "Hidden by members-only setting" else null,
                indicator.description.takeIf { it.isNotEmpty() }).joinToString(". ")
        }
        .clickable(enabled = eligible, role = Role.Button, onClickLabel = "Play ${video.title}", onClick = play)
        .heightIn(min = 96.dp).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!thinMode) Box(Modifier.size(80.dp, 45.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)) {
            AsyncImage(resolved(server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            indicator.bar?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                    .testTag("video-progress-${video.id}").semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(indicator.percent?.div(100f) ?: 1f, 0f..1f)
                    }, gapSize = 0.dp, drawStopIndicator = {})
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (current) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Now playing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            title(Modifier.fillMaxWidth())
            if (entry.removed) Text("Removed from queue", style = MaterialTheme.typography.labelSmall)
            if (video.unavailable) Text("Unavailable video", style = MaterialTheme.typography.labelSmall)
            else if (hiddenMember) Text("Hidden by members-only setting", style = MaterialTheme.typography.labelSmall)
            if (video.membersOnly) MembersBadge(video.id)
            if (video.author.isNotBlank() || video.channelId.isNotBlank()) ChannelAuthor(server, video.authorAvatar,
                video.author.ifBlank { "Unknown channel" }, Avatars.show(thinMode, video.channelId),
                tag = "video-avatar-${video.id}", onClick = if (ContentVisibility.validChannel(video.channelId)) ({ channel(video.channelId) }) else null, maxLines = 1)
            Text(if (video.live) "LIVE" else time(video.duration), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (indicator.watched) Text("Watched", Modifier.testTag("video-watched-${video.id}"), style = MaterialTheme.typography.labelSmall)
            if (thinMode) indicator.percent?.let { Text("Progress: $it%", Modifier.testTag("video-progress-${video.id}"), style = MaterialTheme.typography.labelSmall) }
        }
        actions()
    }
}
