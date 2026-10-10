package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.wingress.mobivious.data.Playlist
import net.wingress.mobivious.data.DisplayFormats
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
        SubscriptionControl(subscribed, modifier = Modifier.testTag("playlist-subscribe-${list.id}"), busy = list.id in busy,
            error = errors[list.id], resetKey = listOf(vm.api.context(), list.id), onChange = {
            if (account == null) { vm.preparePlaylistSubscription(list); signIn() }
            else vm.subscribePlaylist(list, !subscribed)
        })
    }
}

@Composable
internal fun PlaylistCard(vm: AppViewModel, list: Playlist, open: () -> Unit, signIn: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val library by vm.playlists.collectAsStateWithLifecycle()
    val owned = list.owned || library.any { it.id == list.id && it.owned }
    val subscribed = library.any { it.id == list.id && it.saved && !it.owned }
    BrowseArtworkSurface(null) {
        Column(Modifier.fillMaxWidth().clickable(onClick = open).testTag("playlist-card-${list.id}").padding(horizontal = Liquid.inset, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!prefs.thinMode && list.thumbnail.isNotBlank()) AsyncImage(resolved(vm.store.server, list.thumbnail), null,
                    Modifier.size(if (prefs.uiDensity == "compact") 80.dp else 112.dp, if (prefs.uiDensity == "compact") 45.dp else 63.dp).clip(Liquid.media), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(list.title, fontWeight = FontWeight.SemiBold)
                    Text(if (owned) "My playlist · ${list.privacy}" else list.sourceLabel + if (subscribed) " · Subscribed" else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (list.author.isNotBlank()) PlaylistAuthor(vm, list, prefs.thinMode)
                    if (!list.mix && list.count >= 0) Text(DisplayFormats.inventory(list.count.toLong(), "video"), style = MaterialTheme.typography.bodySmall)
                }
            }
            PlaylistSubscriptionButton(vm, list, signIn)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
    }
}

@Composable
internal fun PlaylistHeader(vm: AppViewModel, list: Playlist, play: () -> Unit, edit: () -> Unit, delete: () -> Unit, signIn: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val library by vm.playlists.collectAsStateWithLifecycle()
    val owned = list.owned || library.any { it.id == list.id && it.owned }
    val subscribed = library.any { it.id == list.id && it.saved && !it.owned }
    WatchGlassScene(Modifier.fillMaxWidth(), artwork = list.thumbnail.takeIf { !prefs.thinMode && it.isNotBlank() }?.let { resolved(vm.store.server, it) }) {
    Column(Modifier.padding(horizontal = Liquid.inset, vertical = 20.dp).testTag("playlist-header"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!prefs.thinMode && list.thumbnail.isNotBlank()) AsyncImage(resolved(vm.store.server, list.thumbnail), null,
            Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(16f / 9f).clip(Liquid.media), contentScale = ContentScale.Crop)
        Text(list.title, style = MaterialTheme.typography.headlineMedium)
        Text(if (owned) "My playlist · ${list.privacy}" else list.sourceLabel + if (subscribed) " · Subscribed" else "",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (list.author.isNotBlank()) PlaylistAuthor(vm, list, prefs.thinMode)
        if (!list.mix && list.count >= 0) Text(DisplayFormats.inventory(list.count.toLong(), "video"))
        if (list.description.isNotBlank()) Text(list.description, style = MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton("Play", icon = Icons.Default.PlayArrow, prominent = true, onClick = play)
            PlaylistSubscriptionButton(vm, list, signIn)
            OverflowMenu("Actions for playlist ${list.title}", listOf(list, vm.api.context(), owned), Modifier.size(48.dp).browseGlass(CircleShape).testTag("playlist-actions-${list.id}")) { close ->
                DropdownMenuItem(text = { Text("RSS") }, onClick = { close(); vm.openPlaylistRss(list.copy(owned = owned)) })
                if (owned) {
                    DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { close(); edit() }, modifier = Modifier.testTag("playlist-edit"))
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { close(); delete() }, modifier = Modifier.testTag("playlist-delete"))
                }
            }
        }
    }
    }
}

@Composable
internal fun PlaylistDialog(playlist: Playlist?, dismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var title by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.title ?: "") }
    var privacy by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.privacy ?: "private") }
    var description by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.description ?: "") }
    LibraryPanel(if (playlist == null) "New playlist" else "Edit playlist", dismiss, actions = {
        LibraryActionButton("Save", Modifier.testTag("playlist-form-save"), enabled = title.isNotBlank(), prominent = true) { save(title.trim(), privacy, description) }
    }) { modifier, top ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(start = Liquid.inset, end = Liquid.inset, top = top, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            OutlinedTextField(title, { title = it.take(150) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("playlist-form-title"))
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            BrowseArtworkSurface(null) { BrowseTabs(listOf("private", "unlisted", "public"), privacy, { it.replaceFirstChar(Char::uppercase) }, { "playlist-privacy-$it" }, select = { privacy = it }) }
            if (playlist != null) OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = dismiss) { Text("Cancel") }
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
