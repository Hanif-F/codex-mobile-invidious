package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.*

@Composable
internal fun VideoActionsMenu(vm: AppViewModel, video: Video, signIn: () -> Unit, channel: (String) -> Unit,
    audio: () -> Unit, remove: (() -> Unit)? = null, removalLabel: String = "Remove",
    removeFromPlaylist: (() -> Unit)? = null, removeFromPlaylistEnabled: Boolean = true) {
    val account by vm.account.collectAsStateWithLifecycle()
    OverflowMenu("Actions for ${video.title}", listOf(video, ApiContext(vm.store.server, account)), Modifier.testTag("video-actions-${video.id}")) { close ->
        DropdownMenuItem(text = { Text("Save to playlist") }, onClick = { close(); vm.openSave(video) })
        DropdownMenuItem(text = { Text("Download") }, enabled = !video.unavailable && !video.live, onClick = { close(); vm.openDownload(video) })
        DropdownMenuItem(text = { Text("Audio mode") }, enabled = !video.unavailable, onClick = { close(); audio() })
        DropdownMenuItem(text = { Text("Play next") }, enabled = !video.unavailable, onClick = { close(); vm.insertQueue(video, true) })
        DropdownMenuItem(text = { Text("Add to queue") }, enabled = !video.unavailable, onClick = { close(); vm.insertQueue(video, false) })
        if (ContentVisibility.validChannel(video.channelId)) {
            DropdownMenuItem(text = { Text("Open channel") }, onClick = { close(); channel(video.channelId) })
            BlockChannelMenuItem(vm, video.channelId, video.author, signIn, close = close)
        }
        if (remove != null) DropdownMenuItem(text = { Text(removalLabel) }, onClick = { close(); remove() })
        if (removeFromPlaylist != null) DropdownMenuItem(text = { Text("Remove from playlist") }, enabled = removeFromPlaylistEnabled,
            onClick = { close(); removeFromPlaylist() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavePlaylistSheet(vm: AppViewModel, signIn: () -> Unit) {
    val state by vm.saveSheet.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val enabled = !state.busy && !state.loading
    LibraryPanel("Save to playlist", vm::dismissSave, enabled = !state.busy) { modifier, top ->
        LazyColumn(modifier.testTag("save-playlist-sheet"), contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset, top = top, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(state.video?.title.orEmpty(), style = MaterialTheme.typography.titleMedium) }
            if (account == null) item { Text("Sign in to save this video."); LibraryActionButton("Sign in", prominent = true, onClick = signIn) }
            else {
                if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("save-playlist-error")) } }
                if (state.created != null) item { LibraryActionButton("Retry saving to ${state.created!!.title}", enabled = enabled, onClick = { vm.saveVideo(state.created) }) }
                if (state.lists.isEmpty() && !state.loading) item { Text("No personal playlists yet. Create one below.") }
                items(state.lists, key = { it.id }) { list ->
                    ActionRow(list.title, modifier = Modifier.testTag("save-playlist-${list.id}"),
                        detail = if (list.id == prefs.defaultPlaylist) "Default playlist · Save video here" else "Save video here",
                        icon = Icons.Default.VideoLibrary, trailingIcon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        enabled = enabled, actionLabel = "Save to ${list.title}") { vm.saveVideo(list) }
                }
                item { LibraryActionButton("Reload playlists", enabled = enabled, onClick = vm::loadSaveLists); HorizontalDivider() }
                item {
                    Text("Create and save", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(state.title, { vm.editSave(it, state.privacy) }, label = { Text("Playlist title") }, singleLine = true, enabled = enabled && state.created == null,
                        modifier = Modifier.fillMaxWidth().testTag("save-playlist-title"))
                    LibraryControlScene(Modifier.padding(vertical = 8.dp)) {
                        BrowseTabs(listOf("private", "unlisted", "public"), state.privacy,
                            { it.replaceFirstChar { c -> c.uppercase() } }, { "save-privacy-$it" },
                            enabled = enabled && state.created == null, select = { vm.editSave(state.title, it) })
                    }
                    BrowseArtworkSurface(null) { LibraryActionButton("Create and save", Modifier.testTag("create-and-save"), enabled = enabled && state.title.isNotBlank() && state.created == null,
                        prominent = true) { vm.saveVideo() } }
                }
            }
            item { TextButton(onClick = vm::dismissSave, enabled = !state.busy) { Text("Cancel") } }
        }
    }
}
