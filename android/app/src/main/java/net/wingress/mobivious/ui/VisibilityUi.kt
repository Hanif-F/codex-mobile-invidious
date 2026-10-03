package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.*

@Composable
internal fun MembersBadge(id: String) {
    Text("Members only", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("video-members-$id"))
}

@Composable
internal fun ChannelBlockingButton(vm: AppViewModel, id: String, name: String, signIn: () -> Unit) {
    val state by vm.blocked.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    if (!ContentVisibility.validChannel(id)) return
    Column {
        TextButton(onClick = { if (account == null) signIn() else vm.toggleBlocked(id, name) }, enabled = id !in state.busy,
            modifier = Modifier.testTag("channel-block-$id")) {
            Text(if (id in state.busy) "Saving…" else if (id in state.ids) "Unblock channel" else "Block channel")
        }
        state.actionErrors[id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun VideoBlockingMenu(vm: AppViewModel, video: Video, signIn: () -> Unit) {
    val state by vm.blocked.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var expanded by remember(video.id, vm.api.context()) { mutableStateOf(false) }
    if (!ContentVisibility.validChannel(video.channelId)) return
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("video-actions-${video.id}")) {
            Icon(Icons.Default.MoreVert, "Actions for ${video.title}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(if (video.channelId in state.ids) "Unblock channel" else "Block channel") },
                enabled = video.channelId !in state.busy, onClick = {
                    expanded = false
                    if (account == null) signIn() else vm.toggleBlocked(video.channelId, video.author)
                })
        }
    }
}

@Composable
internal fun BlockedChannelsScreen(vm: AppViewModel, modifier: Modifier, signIn: () -> Unit, openChannel: (String) -> Unit) {
    val state by vm.blocked.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    LaunchedEffect(vm.api.context()) { vm.refreshBlockedChannels() }
    LazyColumn(modifier.fillMaxSize().testTag("blocked-channel-manager"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Blocked channels are hidden from discovery, search and recommendations. Subscriptions, history, playlists and direct links remain accessible.") }
        if (account == null) item { Text("Sign in to share blocked channels with the website."); Button(onClick = signIn) { Text("Sign in") } }
        else {
            item { TextButton(onClick = vm::refreshBlockedChannels, enabled = !state.loading) { Text("Refresh blocked channels") } }
            if (state.loading) item { CircularProgressIndicator() }
            state.error?.let { error -> item {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("blocked-refresh-error"))
                if (!state.loaded) Text("Your blocked channels could not be loaded. Some blocked content may be visible.")
                TextButton(onClick = vm::refreshBlockedChannels) { Text("Retry") }
            } }
            if (state.loaded && state.channels.isEmpty() && !state.loading) item { Text("No blocked channels") }
            items(state.channels, key = { it.id }) { channel ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { openChannel(channel.id) }, modifier = Modifier.weight(1f)) { Text(channel.name) }
                        TextButton(onClick = { vm.toggleBlocked(channel.id, channel.name) }, enabled = channel.id !in state.busy,
                            modifier = Modifier.testTag("unblock-${channel.id}")) { Text(if (channel.id in state.busy) "Saving…" else "Unblock") }
                    }
                    state.actionErrors[channel.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("blocked-action-error-${channel.id}")) }
                }
            }
        }
    }
}
