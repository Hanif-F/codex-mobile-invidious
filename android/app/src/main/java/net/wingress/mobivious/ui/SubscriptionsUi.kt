package net.wingress.mobivious.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.SubscriptionChannelsState
import net.wingress.mobivious.data.SubscriptionSort

@Composable
internal fun SubscriptionChannelsScreen(state: SubscriptionChannelsState, server: String, thinMode: Boolean,
    listState: LazyListState, search: (String) -> Unit, refresh: () -> Unit, open: (String) -> Unit,
    sort: (SubscriptionSort) -> Unit = {}) {
    val matches = remember(state) { state.matches }
    var sortMenu by remember(state.context) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("subscription-channel-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(state.query, search, "Search channels", "subscription-channel-search", Modifier.weight(1f)) {}
            IconButton(onClick = refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh channels") }
        }
        Box(Modifier.padding(horizontal = 16.dp)) {
            OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("subscription-sort")) {
                Text("Sort: ${state.effectiveSort.label}")
                Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SubscriptionSort.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.label) },
                        enabled = state.unavailableReason == null || choice == SubscriptionSort.ALPHABETICAL,
                        leadingIcon = { if (choice == state.effectiveSort) Icon(Icons.Default.Check, null) },
                        modifier = Modifier.testTag("subscription-sort-option-${choice.key}").semantics { selected = choice == state.effectiveSort },
                        onClick = { sortMenu = false; sort(choice) })
                }
            }
        }
        val help = state.unavailableReason ?: if (state.effectiveSort == SubscriptionSort.RELEVANCE)
            "Based on the last 90 days of viewing, boosted by unwatched uploads from the last 7 days." else null
        help?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("subscription-sort-help"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    supportingContent = state.stats[channel.id]?.let { data -> ({
                        Column {
                            val upload = data.latestUpload?.let { DateUtils.getRelativeTimeSpanString(it * 1000, System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString() }
                            Text(upload?.let { "Last upload: $it" } ?: "Last upload unknown")
                            val total = state.effectiveSort == SubscriptionSort.MOST_WATCHED
                            val count = if (total) data.allTimeWatched else data.recentWatched
                            Text("$count ${if (count == 1) "video" else "videos"} watched ${if (total) "all time" else "in the last 90 days"}")
                        }
                    }) },
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
