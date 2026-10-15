package net.wingress.mobivious.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.wingress.mobivious.data.*

@Composable
internal fun SearchChannelCard(channel: Channel, server: String, thinMode: Boolean, open: () -> Unit) {
    OutlinedCard(onClick = open, enabled = ContentVisibility.validChannel(channel.id), modifier = Modifier.fillMaxWidth().padding(horizontal = AppTokens.inset, vertical = 12.dp)
        .testTag("related-channel-${channel.id}").semantics { stateDescription = "Channel" }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!thinMode) ChannelAvatar(server, channel.image, channel.name, 64.dp, "related-avatar-${channel.id}")
            else Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .testTag("search-channel-placeholder-${channel.id}"), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, Modifier.size(32.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (channel.verified) Icon(Icons.Default.Verified, "Verified channel", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (channel.handle.isNotBlank()) Text(channel.handle, style = MaterialTheme.typography.bodyMedium)
                val stats = listOfNotNull(DisplayFormats.subscribers(channel.subscribers).takeIf(String::isNotBlank), channel.videoCount?.let { DisplayFormats.inventory(it, "video") })
                if (stats.isNotEmpty()) Text(stats.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (channel.description.isNotBlank()) Text(channel.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun SearchPlaylistCard(vm: AppViewModel, list: Playlist, open: () -> Unit, signIn: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val largeText = LocalDensity.current.fontScale > 1.3f
    ElevatedCard(onClick = open, modifier = Modifier.fillMaxWidth().padding(horizontal = AppTokens.inset, vertical = 12.dp)
        .testTag("playlist-card-${list.id}").semantics { stateDescription = if (list.mix) "Mix" else "Playlist" },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val cover: @Composable (Modifier) -> Unit = { modifier ->
                val c = MaterialTheme.colorScheme
                Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth(.8f).height(4.dp).clip(MaterialTheme.shapes.small).background(c.outlineVariant))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth(.9f).height(4.dp).clip(MaterialTheme.shapes.small).background(c.secondaryContainer))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().then(if (prefs.thinMode) Modifier.height(64.dp) else Modifier.aspectRatio(16f / 9f)).clip(AppTokens.media).background(c.surfaceContainerHighest)
                        .testTag("search-playlist-cover-${list.id}")) {
                        if (!prefs.thinMode && list.thumbnail.isNotBlank()) AsyncImage(resolved(vm.store.server, list.thumbnail), null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Surface(Modifier.align(Alignment.BottomEnd), color = c.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(if (list.mix) Icons.Default.Shuffle else Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(24.dp))
                                if (!list.mix && list.count >= 0) Text(list.count.toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            val metadata: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(list.sourceLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (list.author.isNotBlank()) PlaylistAuthor(vm, list, prefs.thinMode)
                    if (!list.mix && list.count >= 0) Text(DisplayFormats.inventory(list.count.toLong(), "video"), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (largeText) { cover(if (prefs.thinMode) Modifier.width(88.dp) else Modifier.fillMaxWidth()); metadata(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                cover(Modifier.width(if (prefs.thinMode) 88.dp else 136.dp)); metadata(Modifier.weight(1f))
            }
            PlaylistSubscriptionButton(vm, list, signIn)
        }
    }
}
