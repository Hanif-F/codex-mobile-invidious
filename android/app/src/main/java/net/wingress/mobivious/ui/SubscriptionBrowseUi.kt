package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.data.ChannelSort
import net.wingress.mobivious.data.ChannelTab

@Composable
internal fun SubscriptionFeedToolbar(signedIn: Boolean, query: String, submitted: String, loading: Boolean,
    edit: (String) -> Unit, submit: () -> Unit, clear: () -> Unit, refresh: () -> Unit,
    channels: () -> Unit, visibility: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    BrowsePageTitle("Subscriptions", large = true, actions = {
        if (signedIn) {
            IconButton(onClick = refresh, enabled = !loading, modifier = Modifier.size(48.dp).browseGlass(CircleShape).testTag("subscription-refresh")) {
                Icon(Icons.Default.Refresh, "Refresh subscriptions")
            }
            actions()
        }
    })
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Liquid.inset)) {
        val stackSearch = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        val channelsButton: @Composable () -> Unit = {
            TextButton(onClick = channels, modifier = Modifier.heightIn(min = 48.dp).browseGlass(prominent = true).testTag("subscription-channels-button"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Icon(Icons.Default.Subscriptions, null); Spacer(Modifier.width(8.dp)); Text("Channels")
            }
        }
        if (!signedIn) channelsButton()
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stackSearch) {
                SearchField(query, edit, "Search subscriptions", "subscription-search", Modifier.fillMaxWidth(), glass = true, submit = submit)
                channelsButton()
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchField(query, edit, "Search subscriptions", "subscription-search", Modifier.weight(1f), glass = true, submit = submit)
                channelsButton()
            }
            if (query.isNotBlank() || submitted.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = clear) { Text("Clear search") }
                if (submitted.isNotBlank()) IconButton(onClick = visibility, modifier = Modifier.size(48.dp).browseGlass(CircleShape)) {
                    Icon(Icons.Default.Tune, "Search visibility")
                }
            }
        }
    }
}

@Composable
internal fun ChannelBrowseToolbar(channel: Channel?, tab: ChannelTab?, videoSort: ChannelSort, playlistSort: String,
    query: String, submitted: String, loading: Boolean, back: () -> Unit, refresh: () -> Unit,
    edit: (String) -> Unit, submit: () -> Unit, clear: () -> Unit, selectTab: (ChannelTab) -> Unit,
    sortVideo: (ChannelSort) -> Unit, sortPlaylist: (String) -> Unit) {
    BrowsePageTitle(channel?.name ?: "Channel", back = back, actions = {
        IconButton(onClick = refresh, enabled = !loading, modifier = Modifier.size(48.dp).browseGlass(CircleShape)) { Icon(Icons.Default.Refresh, "Refresh channel") }
    })
    if (channel != null) Column(Modifier.fillMaxWidth().padding(horizontal = Liquid.inset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(query, edit, "Search this channel", "channel-search", Modifier.fillMaxWidth(), glass = true, submit = submit)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compactRow = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.3f
            val tabs: @Composable (Modifier) -> Unit = { modifier ->
                BrowseTabs(channel.contentTabs, tab.takeIf { submitted.isBlank() }, { it.label }, { "channel-tab-${it.path}" },
                    modifier.testTag("channel-tabs"), selectTab)
            }
            val sorting: @Composable () -> Unit = {
                if (submitted.isBlank()) {
                    if (tab?.videoTab == true && !channel.ageGated) BrowseSortMenu(ChannelSort.entries, videoSort, { it.label },
                        Modifier.testTag("channel-sort"), optionTag = { "channel-sort-${it.apiValue}" }, select = sortVideo)
                    if (tab == ChannelTab.PLAYLISTS) BrowseSortMenu(listOf("last", "newest", "oldest"), playlistSort,
                        { when (it) { "last" -> "Last added"; "newest" -> "Newest"; else -> "Oldest" } },
                        Modifier.testTag("channel-playlist-sort"), select = sortPlaylist)
                }
            }
            if (compactRow) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs(Modifier.weight(1f)); sorting()
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tabs(Modifier.fillMaxWidth()); sorting() }
        }
        if (query.isNotBlank() || submitted.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = clear) { Text("Clear search") }
            if (submitted.isNotBlank()) Text("Results in ${channel.name}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
