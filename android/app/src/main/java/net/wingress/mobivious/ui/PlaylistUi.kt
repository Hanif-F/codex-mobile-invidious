package net.wingress.mobivious.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.wingress.mobivious.data.Playlist
import net.wingress.mobivious.data.Avatars
import net.wingress.mobivious.data.ContentVisibility

@Composable
internal fun PlaylistSubscriptionButton(vm: AppViewModel, list: Playlist, signIn: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val busy by vm.playlistBusy.collectAsStateWithLifecycle()
    val errors by vm.playlistErrors.collectAsStateWithLifecycle()
    val owned = list.owned || lists.any { it.id == list.id && it.owned }
    val subscribed = lists.any { it.id == list.id && it.saved && !it.owned }
    if (!owned) Column {
        TextButton(enabled = list.id !in busy, onClick = {
            if (account == null) { vm.preparePlaylistSubscription(list); signIn() }
            else vm.subscribePlaylist(list, !subscribed)
        }, modifier = Modifier.testTag("playlist-subscribe-${list.id}")) {
            Text(if (list.id in busy) "Saving…" else if (subscribed) "Unsubscribe" else "Subscribe")
        }
        errors[list.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun PlaylistCard(vm: AppViewModel, list: Playlist, open: () -> Unit, signIn: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val library by vm.playlists.collectAsStateWithLifecycle()
    val owned = list.owned || library.any { it.id == list.id && it.owned }
    val subscribed = library.any { it.id == list.id && it.saved && !it.owned }
    Card(onClick = open, modifier = Modifier.fillMaxWidth().testTag("playlist-card-${list.id}")) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!prefs.thinMode && list.thumbnail.isNotBlank()) AsyncImage(resolved(vm.store.server, list.thumbnail), null,
                    Modifier.size(if (prefs.uiDensity == "compact") 80.dp else 112.dp, if (prefs.uiDensity == "compact") 45.dp else 63.dp), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(list.title, fontWeight = FontWeight.SemiBold)
                    Text(if (owned) "My playlist · ${list.privacy}" else list.sourceLabel + if (subscribed) " · Subscribed" else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (list.author.isNotBlank()) PlaylistAuthor(vm, list, prefs.thinMode)
                    if (!list.mix && list.count >= 0) Text("${list.count} videos", style = MaterialTheme.typography.bodySmall)
                }
            }
            PlaylistSubscriptionButton(vm, list, signIn)
        }
    }
}

@Composable
internal fun PlaylistHeader(vm: AppViewModel, list: Playlist, play: () -> Unit, edit: () -> Unit, delete: () -> Unit, signIn: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val library by vm.playlists.collectAsStateWithLifecycle()
    val owned = list.owned || library.any { it.id == list.id && it.owned }
    val subscribed = library.any { it.id == list.id && it.saved && !it.owned }
    Column(Modifier.padding(horizontal = 16.dp).testTag("playlist-header")) {
        Text(if (owned) "My playlist · ${list.privacy}" else list.sourceLabel + if (subscribed) " · Subscribed" else "",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (list.author.isNotBlank()) PlaylistAuthor(vm, list, prefs.thinMode)
        if (!list.mix && list.count >= 0) Text("${list.count} videos")
        if (list.description.isNotBlank()) Text(list.description, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = play) { Text("Play") }
            PlaylistSubscriptionButton(vm, list, signIn)
            TextButton(onClick = { vm.openPlaylistRss(list.copy(owned = owned)) }) { Text("RSS") }
            if (owned) {
                TextButton(onClick = edit, modifier = Modifier.testTag("playlist-edit")) { Text("Edit playlist") }
                TextButton(onClick = delete, modifier = Modifier.testTag("playlist-delete")) { Text("Delete playlist") }
            }
        }
    }
}

@Composable
private fun PlaylistAuthor(vm: AppViewModel, list: Playlist, thinMode: Boolean) {
    val valid = ContentVisibility.validChannel(list.channelId)
    ChannelAuthor(vm.store.server, list.authorAvatar, list.author, valid && Avatars.show(thinMode, list.channelId),
        modifier = Modifier.fillMaxWidth(), tag = "playlist-avatar-${list.id}",
        onClick = if (valid) ({ vm.navigate(vm.tab, "channel:${list.channelId}") }) else null)
}
