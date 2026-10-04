package net.wingress.mobivious.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.wingress.mobivious.MainActivity
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Teal = Color(0xFF006B61)
private val dark = darkColorScheme(primary = Color(0xFF72DFD0), onPrimary = Color(0xFF003731), secondary = Color(0xFFB4CCC5), background = Color(0xFF101817), surface = Color(0xFF101817), surfaceContainer = Color(0xFF1C2523))
private val light = lightColorScheme(primary = Teal, onPrimary = Color.White, secondary = Color(0xFF4B635D), secondaryContainer = Color(0xFFCBE8DE), onSecondaryContainer = Color(0xFF153B32), background = Color(0xFFF6FAF7), surface = Color(0xFFF6FAF7), surfaceContainer = Color(0xFFE8F0EB))

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MobiviousApp(vm: AppViewModel, activity: MainActivity, pip: Boolean, shared: MutableState<Boolean>) {
    val state by vm.browse.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val channelTab by vm.channelTab.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val sponsorEditor by vm.sponsorSettingsChannel.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val searchVisibility by vm.searchVisibility.collectAsStateWithLifecycle()
    val search by vm.searchInput.collectAsStateWithLifecycle()
    val scopedSearch by vm.scopedSearch.collectAsStateWithLifecycle()
    val browseReset by vm.browseReset.collectAsStateWithLifecycle()
    val browseList = rememberLazyListState()
    LaunchedEffect(browseReset) { browseList.scrollToItem(0) }
    val visibleVideos = ContentVisibility.filter(state.videos, vm.contentSurface(), prefs.showMemberVideos, searchVisibility, blocked.takeIf { it.context == vm.api.context() }?.ids.orEmpty())
    var tab by rememberSaveable { mutableStateOf(vm.tab) }
    var route by rememberSaveable { mutableStateOf(vm.route) }
    var watch by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var settingsPage by rememberSaveable { mutableStateOf("") }
    val selectedTab by vm.navigation.collectAsStateWithLifecycle()
    LaunchedEffect(selectedTab) { tab = selectedTab.first; route = selectedTab.second; watch = false }
    val saveSheet by vm.saveSheet.collectAsStateWithLifecycle()
    val queueOpen by vm.queueOpen.collectAsStateWithLifecycle()
    val blockUndo by vm.blockUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun navigate(selected: String, path: String = "") { vm.cancelAccumulatedSeek(); tab = selected; route = path; watch = false; vm.navigate(selected, path) }
    fun play(video: Video, source: Playlist? = null) { watch = true; vm.playVideo(video, source) }
    LaunchedEffect(shared.value) { if (shared.value) { watch = true; settingsPage = ""; shared.value = false } }
    LaunchedEffect(message, blockUndo) { message?.let {
        val undo = blockUndo.takeIf { _ -> it == "Channel blocked" }
        val result = snackbar.showSnackbar(it, actionLabel = undo?.let { "Undo" }, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed && undo != null) vm.undoBlock(undo)
        if (vm.message.value == it) vm.message.value = null
    } }
    LaunchedEffect(watch, playback.playWhenReady, playback.error, playback.details, prefs, vm.store.pip, settingsPage) { activity.updatePip(watch && settingsPage.isEmpty()) }
    DisposableEffect(fullscreen, pip) {
        activity.requestedOrientation = if (fullscreen && !pip) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.setFullscreen(fullscreen && !pip)
        onDispose { activity.setFullscreen(false) }
    }
    LaunchedEffect(pip, watch, playback.mediaId) { if (pip || !watch) { vm.cancelAccumulatedSeek(); dialog = ""; vm.closeDeArrow() }; if (pip) vm.sponsorSettingsChannel.value = null }
    LaunchedEffect(account) { dialog = "" }
    LaunchedEffect(settingsPage) { if (settingsPage.isNotEmpty()) vm.cancelAccumulatedSeek(); if (settingsPage == "Settings") vm.refreshSharedSettings() }
    BackHandler(settingsPage.isEmpty() && (fullscreen || watch || route.isNotBlank())) { when { fullscreen -> fullscreen = false; watch -> { vm.cancelAccumulatedSeek(); watch = false }; else -> navigate(tab) } }
    MaterialTheme(colorScheme = if (prefs.darkMode == "dark" || prefs.darkMode.isBlank() && isSystemInDarkTheme()) dark else light) {
        Surface(Modifier.fillMaxSize()) {
            if (pip || fullscreen) {
                VideoPlayer(vm, playback, controller, Modifier.fillMaxSize(), fullscreen = fullscreen,
                    controls = !pip, settingsOpen = dialog == "player" || sponsorEditor != null, onFullscreen = { fullscreen = !fullscreen },
                    onSettings = { dialog = "player" })
            } else if (settingsPage.isNotEmpty()) SettingsScreen(vm, settingsPage, { settingsPage = it }, { settingsPage = when (settingsPage) { "Settings" -> ""; "Blocked channels" -> "Browsing"; else -> "Settings" } }, { dialog = "login" }, { id -> settingsPage = ""; navigate("Home", "channel:$id") })
            else Scaffold(
                topBar = { TopAppBar(title = {
                    if (watch) Text("Now playing", style = MaterialTheme.typography.titleMedium)
                    else if (route.isNotEmpty()) Text(channel?.name ?: playlist?.title ?: state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Mobivious", fontWeight = FontWeight.Bold) }
                }, navigationIcon = { if (watch || route.isNotEmpty()) IconButton(onClick = { vm.cancelAccumulatedSeek(); if (watch) watch = false else navigate(tab) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = {
                    IconButton(onClick = { vm.cancelAccumulatedSeek(); settingsPage = "Settings" }) { Icon(Icons.Default.Settings, "App settings") }
                    IconButton(onClick = { dialog = if (account == null) "login" else "account" }) { Icon(if (account == null) Icons.Default.AccountCircle else Icons.Default.VerifiedUser, "Account") }
                }) },
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = { Column {
                    if (!watch && playback.details != null) MiniPlayer(vm, playback, { watch = true }, vm::togglePlay, vm::closePlayer)
                    NavigationBar { PreferenceRules.navigation(prefs.feedMenu).map { name -> name to when(name) { "Home" -> Icons.Default.Home; "Search" -> Icons.Default.Search; "Subscriptions" -> Icons.Default.Subscriptions; else -> Icons.Default.VideoLibrary } }.forEach { (name, icon) ->
                        NavigationBarItem(selected = tab == name && !watch, onClick = { navigate(name) }, icon = { Icon(icon, name) }, label = { Text(name, fontSize = 11.sp) })
                    } }
                } }
            ) { padding ->
                if (watch) WatchScreen(vm, playback, controller, Modifier.padding(padding), { fullscreen = true }, { dialog = "player" }, dialog == "player" || sponsorEditor != null, vm::openSave, { id -> navigate("Home", "channel:$id") }, { play(it) }, { dialog = "login" })
                else Column(Modifier.padding(padding).fillMaxSize()) {
                    if(offline) Text("Offline · showing saved results", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    if (account != null && blocked.error != null && vm.contentSurface() in listOf(ContentSurface.DISCOVERY, ContentSurface.SEARCH)) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(blocked.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { settingsPage = "Blocked channels" }) { Text("Manage / retry blocked channels") }
                        }
                    }
                    if (route.isEmpty() && tab == "Search") {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SearchField(search.draft, { vm.editSearch(it, false) }, "Search videos or paste a link", "main-search", Modifier.weight(1f)) {
                                val link = VideoLinks.parse(search.draft, vm.store.server)
                                if (link != null) { watch = vm.openLink(link) } else vm.submitSearch(false)
                            }
                            IconButton(onClick = { dialog = "filters" }) { Icon(Icons.Default.Tune, "Search filters") }
                        }
                    }
                    if (route == "history" && account != null || route.isEmpty() && tab == "Subscriptions" && account != null) {
                        val historyScreen = route == "history"
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SearchField(scopedSearch.draft, { vm.editSearch(it, true) }, if (historyScreen) "Search history by title or channel" else "Search subscriptions",
                                if (historyScreen) "history-search" else "subscription-search", Modifier.weight(1f), enabled = !historyScreen || state.history?.organized != false) { vm.submitSearch(true) }
                            if (!historyScreen && scopedSearch.submitted.isNotBlank()) IconButton(onClick = { dialog = "visibilityFilters" }) { Icon(Icons.Default.Tune, "Search visibility") }
                        }
                        if (scopedSearch.submitted.isNotBlank() || scopedSearch.draft.isNotBlank()) TextButton(onClick = vm::clearScopedSearch, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Clear search") }
                        if (historyScreen && state.history?.organized == false) Text("Update this server to enable organized history and history search.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    if (route.isEmpty() && tab == "Home") Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (prefs.feedMenu.filter { it in listOf("Popular", "Trending") } + listOf("Popular", "Trending")).distinct().forEach { name -> val key = name.lowercase(); FilterChip(selected = vm.discovery == key, onClick = { vm.discovery = key; vm.refresh() }, label = { Text(name) }) }
                        Spacer(Modifier.weight(1f)); IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                    if (route.isEmpty() && (tab == "Library" || tab == "Subscriptions") && account == null) EmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", "Sign in") { dialog = "login" }
                    else if (tab == "Library" && route.isEmpty()) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Your library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        item { Card(onClick = { navigate("Library", "history") }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null); Spacer(Modifier.width(16.dp)); Text("Watch history", Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } } }
                        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { dialog = "create" }) { Icon(Icons.Default.Add, null); Text("New") } } }
                        items(playlists, key = { it.id }) { list -> Card(onClick = { navigate("Library", "playlist:${list.id}") }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Column { Text(list.title, fontWeight = FontWeight.SemiBold); Text("${list.count} videos · ${list.privacy}", style = MaterialTheme.typography.bodySmall) } } } }
                        if (playlists.isEmpty()) item { Text("Save videos from their actions menu or the watch screen.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::refresh) }
                    }
                    else LazyColumn(Modifier.fillMaxSize().testTag("browse-video-list"), state = browseList, contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (channel != null) item { Column(Modifier.padding(16.dp)) { Text(channel!!.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("${channel!!.subscribers} subscribers", style = MaterialTheme.typography.bodyMedium); if (channel!!.description.isNotBlank()) Text(channel!!.description, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)); Button(onClick = { if (account == null) dialog = "login" else vm.toggleSubscribe(channel!!.id) }) { Text(if (subscriptions.any { it.id == channel!!.id }) "Subscribed" else "Subscribe") } } }
                        channel?.let { info -> item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                SearchField(scopedSearch.draft, { vm.editSearch(it, true) }, "Search this channel", "channel-search", Modifier.fillMaxWidth()) { vm.submitSearch(true) }
                                if (scopedSearch.submitted.isNotBlank()) Text("Results in ${info.name}", style = MaterialTheme.typography.labelMedium)
                                if (scopedSearch.submitted.isNotBlank() || scopedSearch.draft.isNotBlank()) TextButton(onClick = vm::clearScopedSearch) { Text("Clear search") }
                            }
                        } }
                        channel?.let { info -> item {
                            Row(Modifier.padding(horizontal = 16.dp).testTag("channel-tabs"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                info.contentTabs.forEach { contentTab ->
                                    FilterChip(selected = scopedSearch.submitted.isBlank() && channelTab == contentTab, onClick = { vm.selectChannelTab(contentTab) },
                                        label = { Text(contentTab.label) }, modifier = Modifier.testTag("channel-tab-${contentTab.path}"))
                                }
                            }
                        } }
                        channel?.let { info -> item { ChannelBlockingButton(vm, info.id, info.name, { dialog = "login" }) } }
                        if (channel != null) item { TextButton(onClick = { vm.openSponsorBlock(channel!!.id) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Channel SponsorBlock settings") } }
                        playlist?.let { list -> item {
                            val owned = list.id.startsWith("IV") && playlists.any { it.id == list.id }
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (list.id.startsWith("RD")) "Mix" else "${list.count} videos", Modifier.weight(1f))
                                TextButton(onClick = { watch = true; vm.play("", source = list.id) }) { Text("Play") }
                                if (owned) {
                                    TextButton(onClick = { dialog = "edit" }) { Text("Edit playlist") }
                                    IconButton(onClick = { dialog = "deletePlaylist" }) { Icon(Icons.Default.DeleteOutline, "Delete playlist") }
                                }
                            }
                        } }
                        if (route == "history") item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(state.history?.total?.let { "$it videos" } ?: "Recently watched", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { dialog = "clearHistory" }) { Text("Clear") } } }
                        if (tab == "Subscriptions" && subscriptions.isNotEmpty()) item { androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(subscriptions) { c -> AssistChip(onClick = { navigate("Subscriptions", "channel:${c.id}") }, label = { Text(c.name) }) } } }
                        val groups = if (route == "history" && state.history?.organized == true) visibleVideos.groupBy { History.group(it.history?.watched, state.history?.today) } else mapOf(null to visibleVideos)
                        groups.forEach { (group, videos) ->
                            if (group != null) item(key = "history-group-$group") { Text(group.label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("history-group-${group.name}"), style = MaterialTheme.typography.titleLarge) }
                            items(videos, key = { it.id + it.indexId + (it.playlistIndex?.toString() ?: "") }) { video -> VideoCard(vm, video, vm.store.server, { play(video, playlist) }, { id -> navigate(tab, "channel:$id") }, { dialog = "login" }, if (playlist?.let { list -> list.id.startsWith("IV") && playlists.any { it.id == list.id } } == true || route == "history") ({
                                val list = playlist
                                if (list != null) vm.removePlaylistVideo(list, video)
                                else vm.removeHistory(video.id)
                            }) else null, audioPlay = { watch = true; vm.playVideo(video, playlist, audio = true) }, removalLabel = if (playlist != null) "Remove from playlist" else "Remove from history") }
                        }
                        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::refresh); if (state.videos.isEmpty()) TextButton(onClick = { dialog = ""; settingsPage = "Server" }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Configure server") } }
                        if (!state.loading && visibleVideos.isEmpty() && state.videos.isNotEmpty()) item {
                            Column(Modifier.padding(16.dp)) {
                                Text("Videos hidden by your visibility settings")
                                Text("Change the filters or load another page to find visible videos.", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { if (vm.contentSurface() == ContentSurface.SEARCH) dialog = if (tab == "Subscriptions") "visibilityFilters" else "filters" else settingsPage = "Browsing" }) { Text("Visibility controls") }
                            }
                        }
                        if (!state.loading && state.error == null && state.videos.isEmpty()) item { EmptyState(
                            if (scopedSearch.submitted.isNotBlank()) "No matches" else if (route == "history") "Your history is empty" else if (tab == "Search") "Find something to watch" else "Nothing here yet",
                            if (scopedSearch.submitted.isNotBlank()) "Try another title or channel, or clear the search." else if (route == "history") "Videos you watch with history enabled will appear here." else if (tab == "Search") "Search by title, channel, or paste a video link." else "Refresh to check for videos.", "Refresh", vm::refresh) }
                        if (!state.end && !state.loading) item { TextButton(onClick = vm::more, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
                    }
                }
            }
            when(dialog) {
                "login" -> LoginDialog(vm, { dialog = "" }, { dialog = ""; settingsPage = "Server" })
                "account" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(account?.username.orEmpty()) }, text = { Text("Signed in to ${vm.store.server}") }, confirmButton = { TextButton(onClick = { vm.logout(); dialog = "" }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Close") } })
                "filters" -> FiltersDialog(vm, { dialog = "" })
                "visibilityFilters" -> FiltersDialog(vm, { dialog = "" }, visibilityOnly = true)
                "player" -> PlayerSettings(vm, playback, { dialog = "" }, activity::enterPip, activity.supportsPip(), { dialog = ""; vm.openSponsorBlock() })
                "create", "edit" -> PlaylistDialog(if(dialog == "edit") playlist else null, { dialog = "" }) { title, privacy, description -> val editing = if (dialog == "edit") playlist else null; vm.action { if (editing != null) vm.api.editPlaylist(editing.id, title, privacy, description) else vm.api.createPlaylist(title, privacy); vm.refreshAccount(); vm.refresh() }; dialog = "" }
                "deletePlaylist", "clearHistory" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(if (dialog == "clearHistory") "Clear watch history?" else "Delete playlist?") }, text = { Text("This also changes your account on the website.") }, confirmButton = { TextButton(onClick = { val clear = dialog == "clearHistory"; val id = playlist?.id; if (clear) vm.clearHistory() else vm.action { if (id != null) vm.api.deletePlaylist(id); vm.refreshAccount(); navigate("Library") }; dialog = "" }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
            }
            val dearrow by vm.dearrowContribution.collectAsStateWithLifecycle()
            if (sponsorEditor != null && !pip) SponsorBlockSheet(vm, sponsorEditor!!, { vm.sponsorSettingsChannel.value = null; dialog = "login" }, dismiss = { vm.sponsorSettingsChannel.value = null })
            if (dearrow.open && !pip && !fullscreen) DeArrowContributionSheet(vm)
            if (saveSheet.video != null && dialog != "login" && !pip) SavePlaylistSheet(vm) { dialog = "login" }
            if (queueOpen && saveSheet.video == null && !pip) PlaybackQueueSheet(vm, { vm.queueOpen.value = false; dialog = "login" }, { id -> vm.queueOpen.value = false; navigate("Home", "channel:$id") })
        }
    }
}

@Composable private fun EmptyState(title: String, detail: String, action: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.PlayCircleOutline, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleLarge); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant); FilledTonalButton(onClick = onClick) { Text(action) } } }
@Composable private fun ErrorCard(message: String, retry: () -> Unit) { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp)) { Text(message); TextButton(onClick = retry) { Text("Retry") } } } }
private fun resolved(base: String, path: String) = base.toHttpUrlOrNull()?.resolve(path)?.toString() ?: path
private fun time(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
private fun count(value: Long): String = when { value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0); value >= 1000 -> "%.1fK".format(value / 1000.0); else -> "$value" }
@Composable internal fun VideoCard(vm: AppViewModel, video: Video, server: String, play: () -> Unit, channel: (String) -> Unit, signIn: () -> Unit, remove: (() -> Unit)? = null, audioPlay: (() -> Unit)? = null, removalLabel: String = "Remove") {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = ApiContext(server, account)
    val indicator = WatchedIndicators.forVideo(video, watched.takeIf { it.context == context } ?: WatchedState())
    val compact = prefs.uiDensity == "compact"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp)
        .testTag("video-card-${video.id}").semantics { if (indicator.description.isNotEmpty()) stateDescription = indicator.description }) {
        if (!prefs.thinMode) Box(Modifier.fillMaxWidth().aspectRatio(if (compact) 2.4f else 16f/9f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = play)) {
            AsyncImage(resolved(server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (indicator.watched) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
                Text("Watched", Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = .8f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("video-watched-${video.id}"), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Text(if(video.live) "LIVE" else if (video.history != null && video.duration <= 0) "Unknown duration" else time(video.duration), Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .8f)).padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp)
            indicator.bar?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                    .testTag("video-progress-${video.id}").semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(indicator.percent?.div(100f) ?: 1f, 0f..1f)
                    }, color = MaterialTheme.colorScheme.primary, trackColor = Color.Black.copy(alpha = .3f), gapSize = 0.dp, drawStopIndicator = {})
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (video.membersOnly) MembersBadge(video.id)
                DeArrowTitle(vm, video, MaterialTheme.typography.titleMedium, Modifier.clickable(onClick = play), maxLines = if (compact) 1 else 2)
                if (prefs.thinMode) {
                    if (indicator.watched) Text("Watched", Modifier.testTag("video-watched-${video.id}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    indicator.percent?.let { Text("Progress: $it%", Modifier.testTag("video-progress-${video.id}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                }
                Text(listOf(video.author, if(video.views > 0) "${count(video.views)} views" else "", video.published).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).clickable(enabled = video.channelId.isNotEmpty()) { channel(video.channelId) })
                video.history?.let { history ->
                    Text("Released: ${history.released ?: "Unknown date"}", style = MaterialTheme.typography.bodySmall)
                    Text("Watched: ${history.watched ?: "Unknown watch date"}", style = MaterialTheme.typography.bodySmall)
                    if (video.unavailable) Text("Video ID: ${video.id}", style = MaterialTheme.typography.bodySmall)
                    if (video.duration <= 0) Text("Duration: Unknown", style = MaterialTheme.typography.bodySmall)
                    else if (prefs.thinMode) Text("Duration: ${time(video.duration)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            VideoActionsMenu(vm, video, signIn, channel, audioPlay ?: { vm.playVideo(video, audio = true) }, remove, removalLabel)
            if(remove != null) IconButton(onClick = remove) { Icon(Icons.Default.RemoveCircleOutline, "Remove ${video.title}") }
        }
    }
}
@Composable private fun MiniPlayer(vm: AppViewModel, playback: PlaybackState, open: () -> Unit, toggle: () -> Unit, close: () -> Unit) { Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) { Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = open).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f).padding(12.dp)) { playback.details?.video?.let { DeArrowTitle(vm, it, MaterialTheme.typography.labelLarge, maxLines = 1) }; Text(playback.details?.video?.author.orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = toggle) { Icon(if(playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if(playback.playing) "Pause" else "Play") }; IconButton(onClick = close) { Icon(Icons.Default.Close, "Close player") } } } }
@Composable private fun WatchScreen(vm: AppViewModel, playback: PlaybackState, controller: androidx.media3.session.MediaController?, modifier: Modifier, fullscreen: () -> Unit, settings: () -> Unit, settingsOpen: Boolean, add: (Video) -> Unit, channel: (String) -> Unit, play: (Video) -> Unit, signIn: () -> Unit) {
    val comments by vm.comments.collectAsStateWithLifecycle(); val error by vm.commentError.collectAsStateWithLifecycle(); val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    var description by remember(playback.details?.video?.id, prefs.extendDescription) { mutableStateOf(prefs.extendDescription) }; var showingComments by remember(playback.details?.video?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier.fillMaxSize()) {
        VideoPlayer(vm, playback, controller, Modifier.fillMaxWidth().padding(horizontal = 8.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(16.dp)),
            settingsOpen = settingsOpen, onFullscreen = fullscreen, onSettings = settings)
        LazyColumn(Modifier.testTag("watch-details-list")) {
            playback.details?.let { details ->
                item { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (details.video.membersOnly) MembersBadge(details.video.id)
                    DeArrowTitle(vm, details.video, MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${count(details.video.views)} views · ${details.video.published}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(details.video.author, Modifier.weight(1f).clickable { channel(details.video.channelId) }, fontWeight = FontWeight.SemiBold); FilledTonalButton(onClick = { if(vm.account.value == null) vm.message.value = "Sign in from the account button to subscribe." else vm.toggleSubscribe(details.video.channelId) }) { Text(if(subscriptions.any { it.id == details.video.channelId }) "Subscribed" else "Subscribe") } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { add(details.video) }, label = { Text("Save") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                        AssistChip(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${vm.store.server}/watch?v=${details.video.id}&t=${playback.position / 1000}"), "Share video")) }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                    }
                    TextButton(onClick = { vm.queueOpen.value = true }) { Text("Playback queue") }
                    ChannelBlockingButton(vm, details.video.channelId, details.video.author, signIn)
                    blocked.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { vm.openDeArrow(details.video.id) }) { Text("Suggest / vote on titles") }
                    TextButton(onClick = { vm.openSponsorBlock(details.video.channelId) }) { Text("Channel SponsorBlock settings") }
                    if (account == null) Text("Sign in to suggest titles and vote.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { description = !description }) { Text(if(description) "Hide description" else "Show description") }
                    if(description) Text(details.description, style = MaterialTheme.typography.bodyMedium)
                    if (prefs.showYoutubeComments) TextButton(onClick = { showingComments = !showingComments; if(showingComments) vm.loadComments() }) { Text(if(showingComments) "Hide comments" else "Comments") }
                } }
                if(showingComments && prefs.showYoutubeComments) {
                    items(comments.items) { c -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text("${c.author} · ${c.published}", style = MaterialTheme.typography.labelMedium); Text(c.text, Modifier.padding(vertical = 6.dp)); Text("${c.likes} likes", style = MaterialTheme.typography.bodySmall) } }
                    if(error != null) item { ErrorCard(error!!) { vm.loadComments() } }
                    if(comments.continuation.isNotEmpty()) item { TextButton(onClick = { vm.loadComments(true) }) { Text("More comments") } }
                }
                if (prefs.relatedVideos) {
                    item { Text("Up next", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
                    items(ContentVisibility.filter(details.recommendations, ContentSurface.RECOMMENDATIONS, prefs.showMemberVideos, blocked = blocked.takeIf { it.context == vm.api.context() }?.ids.orEmpty()), key = { it.id }) { VideoCard(vm, it, vm.store.server, { play(it) }, channel, signIn) }
                }
            }
        }
    }
}

@Composable private fun LoginDialog(vm: AppViewModel, dismiss: () -> Unit, server: () -> Unit) {
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if(!busy) dismiss() }, title = { Text("Welcome back") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Use your Invidious account on ${vm.store.server.toHttpUrlOrNull()?.host.orEmpty()}.")
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, enabled = !busy)
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if(error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = server, enabled = !busy) { Text("Change server") }
    } }, confirmButton = { TextButton(enabled = !busy && username.isNotBlank() && password.isNotEmpty(), onClick = { busy = true; error = null; scope.launch { try { vm.login(username.trim(), password); password = ""; dismiss() } catch(e: Exception) { error = e.message ?: "Unable to sign in." } finally { busy = false } } }) { Text("Sign in") } }, dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}
@Composable private fun FiltersDialog(vm: AppViewModel, dismiss: () -> Unit, visibilityOnly: Boolean = false) {
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(vm.sort) }
    var date by remember { mutableStateOf(vm.date) }
    var duration by remember { mutableStateOf(vm.durationFilter) }
    var visibility by remember(vm.api.context()) { mutableStateOf(vm.searchVisibility.value) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Search filters") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!visibilityOnly) {
                Choice("Sort", listOf("relevance", "views"), sort) { sort = it }
                Choice("Uploaded", listOf("", "hour", "today", "week", "month", "year"), date) { date = it }
                Choice("Duration", listOf("", "short", "long"), duration) { duration = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.showMembers ?: prefs.showMemberVideos, { visibility = visibility.copy(showMembers = it) }, modifier = Modifier.testTag("search-show-members"))
                Text("Show members-only videos", Modifier.weight(1f))
            }
            Text(if (visibility.showMembers == null) "Using browsing default" else "Search override saved on this device", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { visibility = visibility.copy(showMembers = null) }, modifier = Modifier.testTag("search-members-reset")) { Text("Use browsing default") }
            if (account != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.includeBlocked, { visibility = visibility.copy(includeBlocked = it) }, modifier = Modifier.testTag("search-include-blocked"))
                Text("Include blocked channels", Modifier.weight(1f))
            }
        }
    }, confirmButton = { TextButton(onClick = {
        vm.sort = sort; vm.date = date; vm.durationFilter = duration; vm.saveSearchVisibility(visibility); vm.refresh(); dismiss()
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@Composable private fun Choice(label: String, values: List<String>, selected: String, update: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { TextButton(onClick = { expanded = true }) { Text("$label: ${selected.ifBlank { "Any" }.replace('_', ' ')}") }; DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem(text = { Text(value.ifBlank { "Any" }.replace('_', ' ')) }, onClick = { update(value); expanded = false }) } } } }
@Composable private fun PlaylistDialog(playlist: Playlist?, dismiss: () -> Unit, save: (String, String, String) -> Unit) { var title by remember { mutableStateOf(playlist?.title ?: "") }; var privacy by remember { mutableStateOf(playlist?.privacy ?: "private") }; var description by remember { mutableStateOf(playlist?.description ?: "") }; AlertDialog(onDismissRequest = dismiss, title = { Text(if(playlist == null) "New playlist" else "Edit playlist") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it.take(150) }, label = { Text("Title") }, singleLine = true); Choice("Privacy", listOf("private", "unlisted", "public"), privacy) { privacy = it }; if(playlist != null) OutlinedTextField(description, { description = it }, label = { Text("Description") }) } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { save(title.trim(), privacy, description) }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) }
