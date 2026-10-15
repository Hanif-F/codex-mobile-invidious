package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** A single dispatch path consumes physical Enter before an IME can submit it twice. */
@Composable
internal fun SearchField(value: String, update: (String) -> Unit, label: String, tag: String, modifier: Modifier=Modifier, enabled: Boolean=true, submit: () -> Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val search={ keyboard?.hide();focus.clearFocus();submit() }
    val c=MaterialTheme.colorScheme
    OutlinedTextField(value,update,placeholder={Text(label)},singleLine=true,enabled=enabled,
        shape=MaterialTheme.shapes.medium,
        colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=c.surfaceContainerHighest,
            unfocusedContainerColor=c.surfaceContainerHighest,unfocusedBorderColor=c.outline,focusedBorderColor=c.primary),
        modifier=modifier.testTag(tag).onPreviewKeyEvent { event ->
            if(enabled && (event.key==Key.Enter || event.key==Key.NumPadEnter)) {
                if(event.type==KeyEventType.KeyUp) search()
                true
            } else false
        },keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={search()}),
        trailingIcon={ Row {
            if(value.isNotEmpty()) IconButton({update("")},Modifier.size(48.dp).testTag("$tag-clear"),enabled=enabled) { Icon(Icons.Default.Close,"Clear search input") }
            IconButton(search,Modifier.size(48.dp).testTag("$tag-submit"),enabled=enabled) {Icon(Icons.Default.Search,"Search")}
        } })
}

/** An opaque editor above the current page; closing it leaves that page untouched. */
@Composable
internal fun SearchEntryScreen(state: SearchEntryState, controller: SearchEntryController, close: () -> Unit, submit: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.session) { withFrameNanos { }; focus.requestFocus(); keyboard?.show() }
    BackHandler { close() }
    Surface(Modifier.fillMaxSize().testTag("search-editor").semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Close search", onClick = close)
                    SearchField(state.draft, controller::update, "Search or paste a link", "main-search",
                        Modifier.weight(1f).focusRequester(focus), submit = submit)
                }
            }
            val recent = state.matchingRecent
            val suggestions = state.suggestions.filterNot { suggestion -> recent.any { it.equals(suggestion, true) } }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max = 800.dp).align(Alignment.CenterHorizontally)
                .testTag("search-suggestions"), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (recent.isNotEmpty()) {
                    item { Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        ClearRecentSearches(controller, state.recent.isNotEmpty())
                    } }
                    itemsIndexed(recent, key = { _, value -> "recent:$value" }) { index, text ->
                        SearchSuggestionRow(text, true, "search-recent-$index", { controller.update(text); submit() },
                            { controller.update(text); focus.requestFocus(); keyboard?.show() }, { controller.remove(text) })
                    }
                }
                if (suggestions.isNotEmpty()) {
                    item { Text("Suggestions", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall) }
                    itemsIndexed(suggestions, key = { _, value -> "suggestion:$value" }) { index, text ->
                        SearchSuggestionRow(text, false, "search-suggestion-$index", { controller.update(text); submit() },
                            { controller.update(text); focus.requestFocus(); keyboard?.show() })
                    }
                }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search-suggestions-loading")) }
                if (state.failed) item { Text("Live suggestions unavailable. You can still search.",
                    Modifier.padding(16.dp).testTag("search-suggestions-error"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.draft.isBlank() && recent.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Find something to watch", style = MaterialTheme.typography.titleLarge)
                        Text("Search for videos, channels and playlists, or paste a link.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(text: String, recent: Boolean, tag: String, submit: () -> Unit, insert: () -> Unit, remove: (() -> Unit)? = null) {
    ListItem(headlineContent = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(if (recent) Icons.Default.History else Icons.Default.Search, null) },
        trailingContent = { Row {
            IconButton(insert, Modifier.size(48.dp).testTag("$tag-insert")) { Icon(Icons.AutoMirrored.Filled.Reply, "Edit $text") }
            if (remove != null) IconButton(remove, Modifier.size(48.dp).testTag("$tag-remove")) { Icon(Icons.Default.Close, "Remove $text from recent searches") }
        } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).clickable(onClickLabel = "Search for $text", onClick = submit))
}

@Composable
internal fun ClearRecentSearches(controller: SearchEntryController, enabled: Boolean) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    TextButton({ confirm = true }, enabled = enabled, modifier = Modifier.testTag("search-clear-recent")) { Text("Clear all") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear recent searches?") },
        text = { Text("This removes searches saved on this device for this profile and instance.") },
        confirmButton = { TextButton({ controller.clear(); confirm = false }, Modifier.testTag("search-confirm-clear")) { Text("Clear") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

internal fun searchSortLabel(value: String) = if (value in listOf("views", "view_count")) "View count" else "Relevance"
internal fun searchDateLabel(value: String) = when(value) {
    "hour" -> "Last hour"; "today" -> "Today"; "week" -> "This week"; "month" -> "This month"; "year" -> "This year"; else -> "Any time"
}
internal fun searchDurationLabel(value: String) = when(value) { "short" -> "Under 4 minutes"; "long" -> "Over 20 minutes"; else -> "Any duration" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchResultsToolbar(vm: AppViewModel, query: String, type: SearchType, visibility: SearchVisibility,
    close: () -> Unit, edit: () -> Unit, newSearch: () -> Unit, filters: () -> Unit) {
    val browse by vm.browse.collectAsStateWithLifecycle()
    Column(Modifier.statusBarsPadding().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Close search", onClick = close)
            Surface(onClick = if (query.isBlank()) newSearch else edit, modifier = Modifier.weight(1f).testTag("search-query"),
                color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.extraLarge) {
                Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Search, null)
                    Text(query.ifBlank { "Search or paste a link" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AppIconButton(Icons.Default.Add, "New search", Modifier.testTag("global-search"), onClick = newSearch)
        }
        if (query.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                BrowseTabs(SearchType.entries, type, { it.label }, { "search-type-${it.apiValue}" }, Modifier.weight(1f), select = vm::setSearchType)
                AppIconButton(Icons.Default.Tune, "Search filters", enabled = !browse.refreshing, onClick = filters)
            }
            val hasResultFilters = vm.sort != "relevance" && type != SearchType.CHANNELS || type.videoFilters && (vm.date.isNotBlank() || vm.durationFilter.isNotBlank())
            if (hasResultFilters || visibility.showMembers != null || visibility.includeBlocked) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search-filter-summary"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.sort != "relevance" && type != SearchType.CHANNELS) RemovableSearchFilter("Sort: ${searchSortLabel(vm.sort)}", "sort") { vm.sort = "relevance"; vm.refresh() }
                    if (type.videoFilters && vm.date.isNotBlank()) RemovableSearchFilter(searchDateLabel(vm.date), "date") { vm.date = ""; vm.refresh() }
                    if (type.videoFilters && vm.durationFilter.isNotBlank()) RemovableSearchFilter(searchDurationLabel(vm.durationFilter), "duration") { vm.durationFilter = ""; vm.refresh() }
                    if (visibility.showMembers != null || visibility.includeBlocked) TextButton(filters) { Text(listOfNotNull(
                        visibility.showMembers?.let { if (it) "Members shown" else "Members hidden" },
                        "Blocked included".takeIf { visibility.includeBlocked }).joinToString(" · ")) }
                }
            }
        }
    }
}

@Composable
private fun RemovableSearchFilter(label: String, tag: String, remove: () -> Unit) {
    InputChip(selected = true, onClick = remove, label = { Text(label) }, trailingIcon = { Icon(Icons.Default.Close, "Remove $label", Modifier.size(18.dp)) },
        modifier = Modifier.heightIn(min = 48.dp).testTag("search-filter-$tag"))
}
