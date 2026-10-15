package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.data.ChannelSort
import net.wingress.mobivious.data.ChannelTab

@Composable
internal fun SubscriptionFeedToolbar(actions: @Composable RowScope.() -> Unit) {
    BrowsePageTitle("Subscriptions", actions = actions)
}

@Composable
internal fun SubscriptionFeedControls(signedIn: Boolean, query: String, submitted: String,
    edit: (String) -> Unit, submit: () -> Unit, clear: () -> Unit, channels: () -> Unit, visibility: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = AppTokens.inset)) {
        val stackSearch = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        val channelsButton: @Composable () -> Unit = {
            LibraryActionButton("Channels", Modifier.testTag("subscription-channels-button"), Icons.Default.Subscriptions, prominent = true, onClick = channels)
        }
        if (!signedIn) channelsButton()
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stackSearch) {
                SearchField(query, edit, "Search subscriptions", "subscription-search", Modifier.fillMaxWidth(), submit = submit)
                channelsButton()
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchField(query, edit, "Search subscriptions", "subscription-search", Modifier.weight(1f), submit = submit)
                channelsButton()
            }
            if (query.isNotBlank() || submitted.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = clear) { Text("Clear search") }
                if (submitted.isNotBlank()) AppIconButton(Icons.Default.Tune, "Search visibility", onClick = visibility)
            }
        }
    }
}

@Composable
internal fun ChannelBrowseToolbar(channel: Channel?, tab: ChannelTab?, videoSort: ChannelSort, playlistSort: String,
    query: String, submitted: String, loading: Boolean, back: () -> Unit, refresh: () -> Unit,
    edit: (String) -> Unit, submit: () -> Unit, clear: () -> Unit, selectTab: (ChannelTab) -> Unit,
    sortVideo: (ChannelSort) -> Unit, sortPlaylist: (String) -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    BrowsePageTitle(channel?.name ?: "Channel", back = back, actions = {
        actions()
        AppIconButton(Icons.Default.Refresh, "Refresh channel", enabled = !loading, onClick = refresh)
    })
}

@Composable
internal fun ChannelBrowseControls(channel: Channel?, tab: ChannelTab?, videoSort: ChannelSort, playlistSort: String,
    query: String, submitted: String, edit: (String) -> Unit, submit: () -> Unit, clear: () -> Unit,
    selectTab: (ChannelTab) -> Unit, sortVideo: (ChannelSort) -> Unit, sortPlaylist: (String) -> Unit) {
    if (channel != null) Column(Modifier.fillMaxWidth().padding(horizontal = AppTokens.inset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var expanded by rememberSaveable(channel.id) { mutableStateOf(false) }
        var searchFocused by remember(channel.id) { mutableStateOf(false) }
        var focusPending by remember(channel.id) { mutableStateOf(false) }
        var focusRequest by remember(channel.id) { mutableIntStateOf(0) }
        val focusRequester = remember(channel.id) { FocusRequester() }
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val showSearch = expanded || query.isNotBlank() || submitted.isNotBlank()
        fun leaveSearch() { focus.clearFocus(); keyboard?.hide() }
        LaunchedEffect(channel.id, focusRequest) {
            if (focusRequest > 0) {
                withFrameNanos { }
                focusRequester.requestFocus()
                keyboard?.show()
                focusPending = false
            }
        }
        LaunchedEffect(query, submitted, searchFocused, focusPending) {
            if (!searchFocused && !focusPending && query.isBlank() && submitted.isBlank()) expanded = false
        }
        BrowseTabs(channel.contentTabs, tab.takeIf { submitted.isBlank() }, { it.label }, { "channel-tab-${it.path}" },
            Modifier.fillMaxWidth().testTag("channel-tabs"), select = { leaveSearch(); selectTab(it) })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val options = when {
                submitted.isNotBlank() -> emptyList()
                tab?.videoTab == true && !channel.ageGated -> ChannelSort.entries.map { it.apiValue to it.label }
                tab == ChannelTab.PLAYLISTS -> listOf("last" to "Last added", "newest" to "Newest", "oldest" to "Oldest")
                else -> emptyList()
            }
            val selected = if (tab == ChannelTab.PLAYLISTS) playlistSort else videoSort.apiValue
            val tag = if (tab == ChannelTab.PLAYLISTS) "channel-playlist-sort" else "channel-sort"
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val style = MaterialTheme.typography.labelLarge
            val pillStyle = MaterialTheme.typography.labelMedium
            fun textWidth(text: String) = with(density) { measurer.measure(text, style).size.width.toDp() }
            val searchWidth = if (showSearch) 160.dp else 48.dp
            val pillsWidth = options.fold(0.dp) { width, option -> width + maxOf(48.dp,
                with(density) { measurer.measure(option.second, pillStyle).size.width.toDp() } + 32.dp) } +
                (8 * (options.size - 1).coerceAtLeast(0)).dp
            val pillsFit = pillsWidth + 8.dp + searchWidth <= maxWidth
            val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
            val iconOnly = textWidth("Sort: $selectedLabel") + 64.dp + 8.dp + searchWidth > maxWidth
            Row(Modifier.fillMaxWidth().testTag("channel-search-sort-row"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isNotEmpty()) ChannelSortControls(options, selected, tag, pillsFit, iconOnly) { value ->
                    leaveSearch()
                    if (tab == ChannelTab.PLAYLISTS) sortPlaylist(value)
                    else sortVideo(ChannelSort.entries.first { it.apiValue == value })
                }
                if (showSearch) SearchField(query, edit, "Search this channel", "channel-search",
                    Modifier.weight(1f).onFocusChanged {
                        searchFocused = it.hasFocus
                        if (it.hasFocus) expanded = true
                    }.focusRequester(focusRequester), submit = submit)
                else {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        AppIconButton(Icons.Default.Search, "Search this channel", Modifier.testTag("channel-search-expand")) {
                            expanded = true
                            focusPending = true
                            focusRequest++
                        }
                    }
                }
            }
        }
        if (query.isNotBlank() || submitted.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { leaveSearch(); expanded = false; clear() }) { Text("Clear search") }
            if (submitted.isNotBlank()) Text("Results in ${channel.name}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChannelSortControls(options: List<Pair<String, String>>, selected: String, tag: String,
    pills: Boolean, iconOnly: Boolean, select: (String) -> Unit) {
    var expanded by remember(tag) { mutableStateOf(false) }
    // Keep an open dropdown alive if losing search focus makes room for pills.
    if (pills && !expanded) Row(Modifier.testTag("$tag-pills").selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = MaterialTheme.colorScheme
        options.forEach { (value, label) -> FilterChip(value == selected, { select(value) },
            label = { Text(label, Modifier.testTag("$tag-label-$value"), style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("$tag-$value"), shape = CircleShape,
            border = BorderStroke(1.dp, if (value == selected) colors.onPrimaryContainer else colors.outline),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer)) }
    } else Box {
        val label = options.firstOrNull { it.first == selected }?.second.orEmpty()
        if (iconOnly) AppIconButton(Icons.AutoMirrored.Filled.Sort, "Sort: $label", Modifier.testTag(tag)) { expanded = true }
        else TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp).testTag(tag).appControl(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Sort: $label")
            Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 8.dp))
        }
        AppDropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) },
                leadingIcon = { if (value == selected) Icon(Icons.Default.Check, null) },
                modifier = Modifier.testTag("$tag-$value").semantics { this.selected = value == selected },
                onClick = { expanded = false; select(value) }) }
        }
    }
}
