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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.SubscriptionChannelsState
import net.wingress.mobivious.data.SubscriptionSort
import net.wingress.mobivious.data.DisplayFormats

@Composable
internal fun SubscriptionChannelsScreen(state: SubscriptionChannelsState, server: String, thinMode: Boolean,
    listState: LazyListState, search: (String) -> Unit, refresh: () -> Unit, open: (String) -> Unit,
    sort: (SubscriptionSort) -> Unit = {}, header: @Composable () -> Unit = {}) {
    val matches = remember(state) { state.matches }
    BrowseGlassSurface(Modifier.fillMaxSize().testTag("subscription-channel-screen"), toolbar = {
        header()
        Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(horizontal = Liquid.inset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchField(state.query, search, "Search channels", "subscription-channel-search", Modifier.weight(1f), glass = true) {}
                IconButton(onClick = refresh, enabled = !state.loading, modifier = Modifier.size(48.dp).browseGlass(androidx.compose.foundation.shape.CircleShape)) {
                    Icon(Icons.Default.Refresh, "Refresh channels")
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                BrowseSortMenu(SubscriptionSort.entries, state.effectiveSort, { it.label }, Modifier.testTag("subscription-sort"),
                    optionTag = { "subscription-sort-option-${it.key}" }, available = { state.unavailableReason == null || it == SubscriptionSort.ALPHABETICAL }, select = sort)
                Text(DisplayFormats.inventory(matches.size.toLong(), "channel"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("subscription-channel-count"))
                if (state.query.isNotEmpty()) TextButton(onClick = { search("") }) { Text("Clear search") }
            }
        }
    }) { contentModifier, topInset ->
        Box(contentModifier, contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 800.dp).fillMaxSize().testTag("subscription-channel-list"), state = listState,
                contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset, top = topInset, bottom = LocalContentBottomInset.current)) {
                val help = state.unavailableReason ?: if (state.effectiveSort == SubscriptionSort.RELEVANCE)
                    "Based on the last 90 days of viewing, boosted by unwatched uploads from the last 7 days." else null
                help?.let { item(key = "sort-help") { Text(it, Modifier.padding(vertical = 12.dp).testTag("subscription-sort-help"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("subscription-channels-loading")) }
                state.error?.let { error -> item {
                    Card(Modifier.fillMaxWidth().padding(16.dp).testTag("subscription-channels-error"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) { Text(error); LibraryActionButton("Retry", onClick = refresh) }
                    }
                } }
                items(matches, key = { it.id }) { channel ->
                    ListItem(headlineContent = { Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = state.stats[channel.id]?.let { data -> ({
                            Column {
                                val upload = relativePublication(data.latestUpload).takeIf(String::isNotBlank)
                                Text(upload?.let { "Last upload: $it" } ?: "Last upload unknown")
                                val total = state.effectiveSort == SubscriptionSort.MOST_WATCHED
                                val count = if (total) data.allTimeWatched else data.recentWatched
                                Text("${DisplayFormats.inventory(count.toLong(), "video")} watched ${if (total) "all time" else "in the last 90 days"}")
                            }
                        }) },
                        leadingContent = if (thinMode) null else ({ ChannelAvatar(server, channel.image, channel.name, 40.dp, "subscription-channel-avatar-${channel.id}") }),
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("subscription-channel-${channel.id}")
                            .clickable(role = Role.Button, onClickLabel = "Open ${channel.name}'s channel") { open(channel.id) })
                    HorizontalDivider(Modifier.padding(start = if (thinMode) 16.dp else 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                }
                if (state.loaded && !state.loading && state.error == null && matches.isEmpty()) item {
                    Text(if (state.channels.isEmpty()) "No subscribed channels" else "No matching channels",
                        Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
