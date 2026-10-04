package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackService

@Composable
internal fun VideoActionsMenu(vm: AppViewModel, video: Video, signIn: () -> Unit, channel: (String) -> Unit,
    audio: () -> Unit, remove: (() -> Unit)? = null, removalLabel: String = "Remove") {
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var expanded by remember(video, vm.api.context()) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("video-actions-${video.id}")) { Icon(Icons.Default.MoreVert, "Actions for ${video.title}") }
        DropdownMenu(expanded, { expanded = false }) {
            fun close(action: () -> Unit) { expanded = false; action() }
            DropdownMenuItem(text = { Text("Save to playlist") }, onClick = { close { vm.openSave(video) } })
            DropdownMenuItem(text = { Text("Audio mode") }, enabled = !video.unavailable, onClick = { close(audio) })
            DropdownMenuItem(text = { Text("Play next") }, enabled = !video.unavailable, onClick = { close { vm.insertQueue(video, true) } })
            DropdownMenuItem(text = { Text("Add to queue") }, enabled = !video.unavailable, onClick = { close { vm.insertQueue(video, false) } })
            if (ContentVisibility.validChannel(video.channelId)) {
                DropdownMenuItem(text = { Text("Open channel") }, onClick = { close { channel(video.channelId) } })
                DropdownMenuItem(text = { Text(if (video.channelId in blocked.ids) "Unblock channel" else "Block channel") }, enabled = video.channelId !in blocked.busy,
                    onClick = { close { if (account == null) signIn() else vm.toggleBlocked(video.channelId, video.author) } })
            }
            if (remove != null) DropdownMenuItem(text = { Text(removalLabel) }, onClick = { close(remove) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavePlaylistSheet(vm: AppViewModel, signIn: () -> Unit) {
    val state by vm.saveSheet.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val enabled = !state.busy && !state.loading
    ModalBottomSheet(onDismissRequest = vm::dismissSave, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().testTag("save-playlist-sheet"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Save to playlist", style = MaterialTheme.typography.titleLarge); Text(state.video?.title.orEmpty()) }
            if (account == null) item { Text("Sign in to save this video."); Button(onClick = signIn) { Text("Sign in") } }
            else {
                if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("save-playlist-error")) } }
                if (state.created != null) item { Button(onClick = { vm.saveVideo(state.created) }, enabled = enabled) { Text("Retry saving to ${state.created!!.title}") } }
                if (state.lists.isEmpty() && !state.loading) item { Text("No personal playlists yet. Create one below.") }
                items(state.lists, key = { it.id }) { list ->
                    TextButton(onClick = { vm.saveVideo(list) }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("save-playlist-${list.id}")) {
                        Text(list.title + if (list.id == prefs.defaultPlaylist) " (Default)" else "")
                    }
                }
                item { TextButton(onClick = vm::loadSaveLists, enabled = enabled) { Text("Reload playlists") }; HorizontalDivider() }
                item {
                    Text("Create and save", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(state.title, { vm.editSave(it, state.privacy) }, label = { Text("Playlist title") }, singleLine = true, enabled = enabled && state.created == null,
                        modifier = Modifier.fillMaxWidth().testTag("save-playlist-title"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("private", "unlisted", "public").forEach { privacy -> FilterChip(selected = state.privacy == privacy, onClick = { vm.editSave(state.title, privacy) },
                            enabled = enabled && state.created == null, label = { Text(privacy.replaceFirstChar { it.uppercase() }) }) }
                    }
                    Button(onClick = { vm.saveVideo() }, enabled = enabled && state.title.isNotBlank() && state.created == null,
                        modifier = Modifier.testTag("create-and-save")) { Text("Create and save") }
                }
            }
            item { TextButton(onClick = vm::dismissSave, enabled = !state.busy) { Text("Cancel") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackQueueSheet(vm: AppViewModel, signIn: () -> Unit, channel: (String) -> Unit) {
    val state by vm.queue.collectAsStateWithLifecycle()
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val owned = state.source?.id?.let { id -> lists.any { it.id == id && it.owned } } == true
    ModalBottomSheet(onDismissRequest = { vm.queueOpen.value = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().testTag("playback-queue"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(state.source?.title?.ifBlank { "Playback queue" } ?: "Playback queue", style = MaterialTheme.typography.titleLarge)
                    Row {
                        TextButton(onClick = { vm.nextQueue(-1) }, enabled = !state.loading && (state.currentIndex > 0 || (state.current?.sourceIndex ?: 0) > 0 || state.repeat == QueueRepeat.ALL)) { Text("Previous") }
                        TextButton(onClick = { vm.nextQueue(1) }, enabled = !state.loading && (QueueRules.successor(state, 1, prefs.showMemberVideos) != null || !state.sourceComplete || state.repeat == QueueRepeat.ALL)) { Text("Next") }
                    }
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { QueueRepeat.entries.forEach { value ->
                        FilterChip(selected = state.repeat == value, onClick = { vm.repeatQueue(value) }, enabled = value != QueueRepeat.ALL || state.source?.mix != true,
                            label = { Text(value.label) }, modifier = Modifier.testTag("queue-repeat-${value.name}"))
                    } }
                    if (state.loading || state.sourceLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::retryPlayback) { Text("Retry playback") } }
                    state.sourceError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { vm.queueCommand(PlaybackService.QUEUE_MORE) }) { Text("Retry queue") } }
                    if (state.items.isEmpty()) Text("Your queue is empty. Add a video using its actions menu.")
                }
            }
            items(state.items.filter { (!it.removed || it.key == state.currentKey) && (prefs.showMemberVideos || !it.video.membersOnly || it.key == state.currentKey) }, key = { it.key }) { entry ->
                Column(Modifier.testTag("queue-occurrence-${entry.key}")) {
                    if (entry.key == state.currentKey) Text(if (entry.removed) "Now playing · removed from queue" else "Now playing", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
                    if (entry.video.unavailable) Text("Unavailable video", Modifier.padding(horizontal = 16.dp))
                    else if (entry.video.membersOnly && !prefs.showMemberVideos) Text("Hidden by members-only setting", Modifier.padding(horizontal = 16.dp))
                    VideoCard(vm, entry.video, vm.store.server, { if (QueueRules.eligible(entry, prefs.showMemberVideos)) vm.selectQueue(entry.key) }, channel, signIn,
                        remove = if (!entry.removed) ({ vm.queueCommand(PlaybackService.QUEUE_REMOVE) { putString("key", entry.key) } }) else null,
                        audioPlay = { vm.audioOnly(true); vm.selectQueue(entry.key) }, removalLabel = "Remove from queue")
                    if (owned && entry.video.indexId.isNotEmpty() && !entry.removed) TextButton(onClick = {
                        vm.queueCommand(PlaybackService.QUEUE_DELETE_SOURCE) { putString("key", entry.key) }
                    }, enabled = !state.loading && !state.sourceLoading, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Remove from playlist") }
                }
            }
            if (!state.sourceComplete && !state.sourceLoading) item { TextButton(onClick = { vm.queueCommand(PlaybackService.QUEUE_MORE) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Load more queue items") } }
        }
    }
}
