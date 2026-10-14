package net.wingress.mobivious.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
internal fun BrowseAppSurface(modifier: Modifier = Modifier, enabled: Boolean = true,
    toolbar: @Composable () -> Unit, content: @Composable (Modifier, Dp) -> Unit) {
    if (!enabled) { content(modifier, 0.dp); return }
    Column(modifier.background(MaterialTheme.colorScheme.surface).semantics { testTagsAsResourceId = true }) {
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,tonalElevation=0.dp,shadowElevation=1.dp) {
            Column(Modifier.fillMaxWidth().testTag("app-toolbar")) { toolbar() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize(),0.dp) }
    }
}

/** A feed-only viewport: the toolbar stays fixed above the pull indicator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedRefreshContainer(enabled: Boolean, refreshing: Boolean, loading: Boolean,
    label: String, refresh: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    Box(modifier.clipToBounds()
        .pullToRefresh(isRefreshing = enabled && refreshing, state = state, enabled = enabled && !loading, onRefresh = refresh)
        .then(if (enabled) Modifier.testTag("feed-refresh-container").semantics {
            if (refreshing) stateDescription = "Refreshing $label"
            customActions = listOf(CustomAccessibilityAction("Refresh $label") {
                if (loading) false else { refresh(); true }
            })
        } else Modifier)) {
        content()
        if (enabled) PullToRefreshDefaults.Indicator(state = state, isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter).testTag("feed-refresh-indicator").semantics {
                progressBarRangeInfo = if (refreshing) ProgressBarRangeInfo.Indeterminate else
                    ProgressBarRangeInfo(state.distanceFraction.coerceIn(0f, 1f), 0f..1f)
            })
    }
}

@Composable
internal fun BrowsePageTitle(title: String, back: (() -> Unit)? = null, statusInset: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (statusInset) Modifier.statusBarsPadding() else Modifier)
        .padding(horizontal = AppTokens.inset, vertical = 8.dp)) {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        val titleContent: @Composable (Modifier) -> Unit = { modifier ->
            Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            titleContent(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
        } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            back?.let { AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = it) }
            titleContent(Modifier.weight(1f)); actions()
        }
    }
}

@Composable
internal fun BrowsePanelToolbar(title: String, closeLabel: String, close: () -> Unit,
    detail: String? = null, back: (() -> Unit)? = null, enabled: Boolean = true) {
    AppForeground {

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).appControl()
        .heightIn(min = 56.dp).padding(start = if (back == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        back?.let { AppInsetIconButton(onClick = it, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to SponsorBlock settings") } }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        AppInsetIconButton(onClick = close, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, closeLabel) }
    }

    }
}

@Composable
internal fun <T> BrowseTabs(options: List<T>, selected: T?, label: (T) -> String,
    tag: (T) -> String, modifier: Modifier = Modifier, enabled: Boolean = true, available: (T) -> Boolean = { true }, select: (T) -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> AppFilterChip(selected==option,{select(option)}, {Text(label(option))},
            enabled=enabled && available(option), modifier=Modifier.heightIn(min=48.dp).testTag(tag(option))) }
    }
}

@Composable
internal fun <T> BrowseSortMenu(options: List<T>, selected: T, label: (T) -> String,
    modifier: Modifier = Modifier, optionTag: (T) -> String = { "" }, available: (T) -> Boolean = { true }, select: (T) -> Unit) {
    AppForeground {

    val source = remember { MutableInteractionSource() }

    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = modifier.heightIn(min = 48.dp).appControl(), interactionSource = source,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Sort: ${label(selected)}", Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 8.dp))
        }
        AppDropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, enabled = available(option),
                leadingIcon = { if (option == selected) Icon(Icons.Default.Check, null) },
                modifier = Modifier.testTag(optionTag(option)).semantics { this.selected = option == selected },
                onClick = { expanded = false; select(option) }) }
        }
    }

    }
}
