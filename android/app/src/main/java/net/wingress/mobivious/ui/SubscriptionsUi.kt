package net.wingress.mobivious.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.SubscriptionChannelsState

@Composable
internal fun SubscriptionChannelsScreen(state: SubscriptionChannelsState, server: String, thinMode: Boolean,
    listState: LazyListState, search: (String) -> Unit, refresh: () -> Unit, open: (String) -> Unit) {
    val matches = remember(state.channels, state.query) { state.matches }
    Column(Modifier.fillMaxSize().testTag("subscription-channel-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(state.query, search, "Search channels", "subscription-channel-search", Modifier.weight(1f)) {}
            IconButton(onClick = refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh channels") }
        }
        if (state.query.isNotEmpty()) TextButton(onClick = { search("") }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Clear search") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("subscription-channels-loading"))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("subscription-channel-list"), state = listState,
            contentPadding = PaddingValues(vertical = 8.dp)) {
            state.error?.let { error -> item {
                Card(Modifier.fillMaxWidth().padding(16.dp).testTag("subscription-channels-error"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) { Text(error); OutlinedButton(onClick = refresh) { Text("Retry") } }
                }
            } }
            items(matches, key = { it.id }) { channel ->
                ListItem(headlineContent = { Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = if (thinMode) null else ({ ChannelAvatar(server, channel.image, channel.name, 40.dp, "subscription-channel-avatar-${channel.id}") }),
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("subscription-channel-${channel.id}")
                        .clickable(role = Role.Button, onClickLabel = "Open ${channel.name}'s channel") { open(channel.id) })
            }
            if (state.loaded && !state.loading && state.error == null && matches.isEmpty()) item {
                Text(if (state.channels.isEmpty()) "No subscribed channels" else "No matching channels",
                    Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
