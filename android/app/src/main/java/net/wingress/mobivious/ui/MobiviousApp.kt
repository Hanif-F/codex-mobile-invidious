package net.wingress.mobivious.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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
    val accountBusy by vm.accountBusy.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val channelTab by vm.channelTab.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistSearch by vm.playlistSearch.collectAsStateWithLifecycle()
    val channelPlaylistSort by vm.channelPlaylistSort.collectAsStateWithLifecycle()
    val subscriptionChannels by vm.subscriptionChannels.collectAsStateWithLifecycle()
    val subscriptions = subscriptionChannels.takeIf { it.context == vm.api.context() }?.channels.orEmpty()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
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
    val subscriptionList = key(vm.api.context()) { rememberLazyListState() }
    LaunchedEffect(browseReset) { browseList.scrollToItem(0) }
    val visibleVideos = ContentVisibility.filter(state.videos, vm.contentSurface(), prefs.showMemberVideos, searchVisibility, blocked.takeIf { it.context == vm.api.context() }?.ids.orEmpty())
    var tab by rememberSaveable { mutableStateOf(vm.tab) }
    var route by rememberSaveable { mutableStateOf(vm.route) }
    var watch by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var settingsPage by rememberSaveable { mutableStateOf("") }
    var accountPage by rememberSaveable(account?.username, vm.store.server) { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchDraft by rememberSaveable { mutableStateOf("") }
    var originIndex by rememberSaveable { mutableIntStateOf(0) }
    var originOffset by rememberSaveable { mutableIntStateOf(0) }
    var originWatch by rememberSaveable { mutableStateOf(false) }
    var authSettings by rememberSaveable { mutableStateOf("") }
    var authWatchId by rememberSaveable { mutableStateOf("") }
    var authWatchPosition by rememberSaveable { mutableLongStateOf(0L) }
    var navigationServer by rememberSaveable { mutableStateOf(vm.store.server) }
    LaunchedEffect(vm.store.server) {
        if (navigationServer != vm.store.server) {
            authWatchId = ""; authSettings = ""; originWatch = false; vm.clearNavigationReturns(); navigationServer = vm.store.server
        }
    }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchOpen) { if (searchOpen) { searchFocus.requestFocus(); keyboard?.show() } }
    val restoredBrowse by vm.restoredBrowse.collectAsStateWithLifecycle()
    val authenticationFinished by vm.authenticationFinished.collectAsStateWithLifecycle()
    var handledRestore by rememberSaveable { mutableLongStateOf(0L) }
    var handledAuth by rememberSaveable { mutableLongStateOf(0L) }
    val selectedTab by vm.navigation.collectAsStateWithLifecycle()
    LaunchedEffect(selectedTab, restoredBrowse, authenticationFinished) {
        tab = selectedTab.first; route = selectedTab.second; watch = false
        if (restoredBrowse > handledRestore) {
            handledRestore = restoredBrowse; browseList.scrollToItem(originIndex, originOffset)
            watch = originWatch; originWatch = false
            if (account == null) { authWatchId = ""; authSettings = "" }
        }
        if (authenticationFinished > handledAuth) {
            handledAuth = authenticationFinished; settingsPage = authSettings; authSettings = ""
            if (authWatchId.isNotEmpty()) { vm.openLink(VideoLink(authWatchId, authWatchPosition)); watch = true; authWatchId = "" }
        }
    }
    val saveSheet by vm.saveSheet.collectAsStateWithLifecycle()
    val blockUndo by vm.blockUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun navigate(selected: String, path: String = "") { searchOpen = false; vm.cancelAccumulatedSeek(); tab = selected; route = path; watch = false; vm.navigate(selected, path) }
    fun signIn() {
        dialog = ""; searchOpen = false
        originIndex = browseList.firstVisibleItemIndex; originOffset = browseList.firstVisibleItemScrollOffset
        originWatch = watch; authSettings = settingsPage; settingsPage = ""
        if (watch) { authWatchId = playback.details?.video?.id.orEmpty(); authWatchPosition = playback.position / 1000 }
        vm.openSignIn()
    }
    fun submitGlobalSearch() {
        if (searchDraft.isBlank()) return
        searchOpen = false; keyboard?.hide(); vm.cancelAccumulatedSeek()
        if (tab != "Search" || route.isNotEmpty() || watch) {
            originIndex = browseList.firstVisibleItemIndex; originOffset = browseList.firstVisibleItemScrollOffset; originWatch = watch
        }
        val link = VideoLinks.parse(searchDraft, vm.store.server)
        if (link != null) watch = vm.openLink(link) else { watch = false; vm.openGlobalSearch(searchDraft) }
    }
    fun play(video: Video, source: Playlist? = null) { watch = true; vm.playVideo(video, source) }
    LaunchedEffect(shared.value) { if (shared.value) { watch = true; settingsPage = ""; shared.value = false } }
    LaunchedEffect(message, blockUndo) { message?.let {
        val undo = blockUndo.takeIf { _ -> it == "Channel blocked" }
        val result = snackbar.showSnackbar(it, actionLabel = undo?.let { "Undo" }, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed && undo != null) vm.undoBlock(undo)
        if (vm.message.value == it) vm.message.value = null
    } }
    LaunchedEffect(watch, playback.playWhenReady, playback.error, playback.details, playback.geometry, prefs, vm.store.pip, settingsPage) { activity.updatePip(watch && settingsPage.isEmpty()) }
    DisposableEffect(fullscreen, pip, playback.geometry.orientation) {
        activity.requestedOrientation = if (fullscreen && !pip) when (playback.geometry.orientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            -1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.setFullscreen(fullscreen && !pip)
        onDispose { activity.setFullscreen(false); activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    LaunchedEffect(pip, watch, playback.mediaId) { if (pip || !watch) { vm.closeComments(); vm.cancelAccumulatedSeek(); dialog = ""; vm.closeDeArrow() }; if (pip) vm.sponsorSettingsChannel.value = null }
    LaunchedEffect(account) { dialog = "" }
    LaunchedEffect(settingsPage) { if (settingsPage.isNotEmpty()) vm.cancelAccumulatedSeek(); if (settingsPage == "Settings") vm.refreshSharedSettings() }
    BackHandler(settingsPage.isEmpty() && (fullscreen || watch || route.isNotBlank() || vm.hasBrowseBack)) { when { fullscreen -> fullscreen = false; watch -> { if (!vm.backComments()) { vm.cancelAccumulatedSeek(); watch = false } }; else -> { vm.cancelAccumulatedSeek(); vm.backBrowse() } } }
    BackHandler(searchOpen) { searchOpen = false; keyboard?.hide() }
    BackHandler(accountBusy) { }
    MaterialTheme(colorScheme = if (prefs.darkMode == "dark" || prefs.darkMode.isBlank() && isSystemInDarkTheme()) dark else light) {
        Surface(Modifier.fillMaxSize()) {
            if (pip || fullscreen) {
                VideoPlayer(vm, playback, controller, Modifier.fillMaxSize(), fullscreen = fullscreen,
                    controls = !pip, settingsOpen = dialog == "player" || sponsorEditor != null, onFullscreen = { fullscreen = !fullscreen },
                    onSettings = { dialog = "player" })
            } else if (settingsPage.isNotEmpty()) SettingsScreen(vm, settingsPage, { settingsPage = it }, { settingsPage = when (settingsPage) { "Settings" -> ""; "Blocked channels" -> "Browsing"; else -> "Settings" } }, { signIn() }, { id -> settingsPage = ""; navigate("Home", "channel:$id") })
            else Scaffold(
                topBar = { TopAppBar(title = {
                    if (searchOpen) SearchField(searchDraft, { searchDraft = it }, "Search or paste a link", "main-search",
                        Modifier.fillMaxWidth().focusRequester(searchFocus)) { submitGlobalSearch() }
                    else if (watch) Text("Now playing", style = MaterialTheme.typography.titleMedium)
                    else if (tab == "Search" && route.isEmpty()) Text(search.submitted.ifBlank { "Search" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else if (route == "subscription-channels") Text("Subscribed channels", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else if (route.isNotEmpty()) Text(channel?.name ?: playlist?.title ?: state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Mobivious", fontWeight = FontWeight.Bold) }
                }, navigationIcon = { if (searchOpen || watch || route.isNotEmpty() || vm.hasBrowseBack) IconButton(enabled = !accountBusy, onClick = { vm.cancelAccumulatedSeek(); if (searchOpen) { searchOpen = false; keyboard?.hide() } else if (watch) { if (!vm.backComments()) watch = false } else vm.backBrowse() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = {
                    if (!watch && account != null && route.isEmpty() && tab == "Subscriptions") {
                        OverflowMenu("Subscription actions", vm.api.context(), Modifier.testTag("subscription-actions")) { close ->
                            DropdownMenuItem(text = { Text("RSS") }, onClick = { close(); vm.openSubscriptionRss() })
                            DropdownMenuItem(text = { Text("Export OPML") }, onClick = { close(); vm.openOpml() })
                        }
                    }
                    if (!watch && route == "history") {
                        OverflowMenu("History actions", vm.api.context(), Modifier.testTag("history-actions")) { close ->
                            DropdownMenuItem(text = { Text("Clear watch history") }, onClick = { close(); dialog = "clearHistory" })
                        }
                    }
                    if (!searchOpen) IconButton(enabled = !accountBusy, onClick = { searchDraft = search.submitted; searchOpen = true }, modifier = Modifier.testTag("global-search")) { Icon(Icons.Default.Search, "Search") }
                }) },
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = { Column {
                    if (!watch && playback.details != null) MiniPlayer(vm, playback, controller, { watch = true }, vm::togglePlay, vm::closePlayer)
                    NavigationBar { PreferenceRules.navigation(prefs.feedMenu).map { name -> name to when(name) { "Home" -> Icons.Default.Home; "Account" -> Icons.Default.AccountCircle; "Subscriptions" -> Icons.Default.Subscriptions; else -> Icons.Default.VideoLibrary } }.forEach { (name, icon) ->
                        NavigationBarItem(enabled = !accountBusy, selected = tab == name && !watch, onClick = {
                            vm.clearNavigationReturns(); originWatch = false; authWatchId = ""; authSettings = ""; navigate(name)
                        }, icon = { Icon(icon, name) }, label = { Text(name, fontSize = 11.sp) })
                    } }
                } }
            ) { padding ->
                if (watch) WatchScreen(vm, playback, controller, Modifier.padding(padding), { fullscreen = true }, { dialog = "player" }, dialog == "player" || sponsorEditor != null, vm::openSave, { id -> navigate("Home", "channel:$id") }, { play(it) }, { signIn() })
                else if (tab == "Account" && route.isEmpty()) AccountScreen(vm, Modifier.padding(padding), accountPage, { accountPage = it }, backEnabled = !searchOpen) { settingsPage = "Settings" }
                else Column(Modifier.padding(padding).fillMaxSize()) {
                    if(offline) Text("Offline · showing saved results", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    if (account != null && blocked.error != null && vm.contentSurface() in listOf(ContentSurface.DISCOVERY, ContentSurface.SEARCH)) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(blocked.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { settingsPage = "Blocked channels" }) { Text("Manage / retry blocked channels") }
                        }
                    }
                    if (route.isEmpty() && tab == "Search") Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { dialog = "filters" }) { Icon(Icons.Default.Tune, "Search filters") }
                        FilterChip(selected = !playlistSearch, onClick = { vm.setPlaylistSearch(false) }, label = { Text("Videos") })
                        FilterChip(selected = playlistSearch, onClick = { vm.setPlaylistSearch(true) }, label = { Text("Playlists & mixes") })
                    }
                    if (route.isEmpty() && tab == "Subscriptions") Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Subscriptions", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = { navigate("Subscriptions", "subscription-channels") }, modifier = Modifier.testTag("subscription-channels-button")) {
                            Icon(Icons.Default.Subscriptions, null); Spacer(Modifier.width(8.dp)); Text("Channels")
                        }
                    }
                    if (route == "history" && account != null || route.isEmpty() && tab == "Subscriptions" && account != null) {
                        val historyScreen = route == "history"
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SearchField(scopedSearch.draft, { vm.editSearch(it, true) }, if (historyScreen) "Search history by title or channel" else "Search subscriptions",
                                if (historyScreen) "history-search" else "subscription-search", Modifier.weight(1f), enabled = !historyScreen || state.history?.organized != false) { vm.submitSearch(true) }
                            if (!historyScreen && scopedSearch.submitted.isNotBlank()) IconButton(onClick = { dialog = "visibilityFilters" }) { Icon(Icons.Default.Tune, "Search visibility") }
                        }
                        if (scopedSearch.submitted.isNotBlank() || scopedSearch.draft.isNotBlank()) OutlinedButton(onClick = vm::clearScopedSearch, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Clear search") }
                        if (historyScreen && state.history?.organized == false) Text("Update this server to enable organized history and history search.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    if (route.isEmpty() && tab == "Home") Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (prefs.feedMenu.filter { it in listOf("Popular", "Trending") } + listOf("Popular", "Trending")).distinct().forEach { name -> val key = name.lowercase(); FilterChip(selected = discovery == key, onClick = { vm.selectDiscovery(key) }, label = { Text(name) }, modifier = Modifier.testTag("discovery-$key")) }
                        Spacer(Modifier.weight(1f)); IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                    if (route == "subscription-channels") {
                        if (account == null) EmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", "Sign in") { signIn() }
                        else SubscriptionChannelsScreen(subscriptionChannels.takeIf { it.context == vm.api.context() } ?: SubscriptionChannelsState(), vm.store.server, prefs.thinMode,
                            subscriptionList, { query -> vm.searchSubscriptionChannels(query); scope.launch { subscriptionList.scrollToItem(0) } }, vm::refreshSubscriptions,
                            { id -> navigate("Subscriptions", "channel:$id") })
                    }
                    else if (route.isEmpty() && (tab == "Library" || tab == "Subscriptions") && account == null) EmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", "Sign in") { signIn() }
                    else if (tab == "Library" && route.isEmpty()) LazyColumn(Modifier.fillMaxSize().testTag("library-playlist-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Your library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        item { Card(onClick = { navigate("Library", "history") }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null); Spacer(Modifier.width(16.dp)); Text("Watch history", Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } } }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("My playlists (${playlists.count { it.owned }})", style = MaterialTheme.typography.titleLarge)
                                FilledTonalButton(onClick = { dialog = "create" }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New playlist") }
                            }
                        }
                        items(playlists.filter { it.owned }, key = { "owned:${it.id}" }) { list -> PlaylistCard(vm, list, { watch = false; vm.openPlaylist(list) }, { signIn() }) }
                        if (playlists.none { it.owned }) item { Text("Create a playlist to save your videos.") }
                        item { Text("Subscribed playlists (${playlists.count { !it.owned && it.saved }})", style = MaterialTheme.typography.titleLarge) }
                        items(playlists.filter { !it.owned && it.saved }, key = { "subscribed:${it.id}" }) { list -> PlaylistCard(vm, list, { watch = false; vm.openPlaylist(list) }, { signIn() }) }
                        if (playlists.none { !it.owned && it.saved }) item { Text("Subscribe to playlists or mixes from search, a channel, or a shared link.") }
                        if (state.loading) item { CircularProgressIndicator() }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::refresh) }
                    }
                    else LazyColumn(Modifier.fillMaxSize().testTag("browse-video-list"), state = browseList, contentPadding = PaddingValues(bottom = 12.dp)) {
                        channel?.let { info -> item {
                            ChannelHeader(info, vm.store.server, prefs.thinMode, subscriptions.any { it.id == info.id }, actions = {
                                OverflowMenu("Actions for channel ${info.name}", listOf(info.id, vm.api.context()), Modifier.testTag("channel-actions-${info.id}")) { close ->
                                    DropdownMenuItem(text = { Text("RSS") }, onClick = { close(); vm.openChannelRss(info) })
                                    BlockChannelMenuItem(vm, info.id, info.name, { signIn() }, Modifier.testTag("channel-block-${info.id}"), close)
                                    DropdownMenuItem(text = { Text("Channel SponsorBlock settings") }, onClick = { close(); vm.openSponsorBlock(info.id) })
                                }
                            }) {
                                if (account == null) signIn() else vm.toggleSubscribe(info.id)
                            }
                            Box(Modifier.padding(horizontal = 16.dp)) { ChannelBlockingError(vm, info.id) }
                        } }
                        channel?.let { info -> item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                SearchField(scopedSearch.draft, { vm.editSearch(it, true) }, "Search this channel", "channel-search", Modifier.fillMaxWidth()) { vm.submitSearch(true) }
                                if (scopedSearch.submitted.isNotBlank()) Text("Results in ${info.name}", style = MaterialTheme.typography.labelMedium)
                                if (scopedSearch.submitted.isNotBlank() || scopedSearch.draft.isNotBlank()) OutlinedButton(onClick = vm::clearScopedSearch) { Text("Clear search") }
                            }
                        } }
                        channel?.let { info -> item {
                            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()).testTag("channel-tabs"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                info.contentTabs.forEach { contentTab ->
                                    FilterChip(selected = scopedSearch.submitted.isBlank() && channelTab == contentTab, onClick = { vm.selectChannelTab(contentTab) },
                                        label = { Text(contentTab.label) }, modifier = Modifier.testTag("channel-tab-${contentTab.path}"))
                                }
                            }
                        } }
                        if (channelTab == ChannelTab.PLAYLISTS && scopedSearch.submitted.isBlank()) item {
                            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("last" to "Last added", "newest" to "Newest", "oldest" to "Oldest").forEach { (key, label) ->
                                    FilterChip(selected = channelPlaylistSort == key, onClick = { vm.setChannelPlaylistSort(key) }, label = { Text(label) })
                                }
                            }
                        }
                        playlist?.let { list -> item {
                            PlaylistHeader(vm, list, { watch = true; vm.play("", source = list.id, seed = list.seedVideoId) },
                                { dialog = "edit" }, { dialog = "deletePlaylist" }, { signIn() })
                        } }
                        items(vm.visiblePlaylists(state.lists), key = { "result:${it.id}" }) { list ->
                            PlaylistCard(vm, list, { watch = false; vm.openPlaylist(list) }, { signIn() })
                        }
                        if (route == "history") item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(state.history?.total?.let { "$it videos" } ?: "Recently watched", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge) } }
                        if (route.isEmpty() && tab == "Subscriptions" && subscriptions.isNotEmpty()) item {
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(subscriptions, key = { it.id }) { c -> SubscriptionChannelChip(c, vm.store.server, prefs.thinMode) { navigate("Subscriptions", "channel:${c.id}") } }
                            }
                        }
                        val groups = if (route == "history" && state.history?.organized == true) visibleVideos.groupBy { History.group(it.history?.watched, state.history?.today) } else mapOf(null to visibleVideos)
                        groups.forEach { (group, videos) ->
                            if (group != null) item(key = "history-group-$group") { Text(group.label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("history-group-${group.name}"), style = MaterialTheme.typography.titleLarge) }
                            items(videos, key = { it.id + it.indexId + (it.playlistIndex?.toString() ?: "") }) { video -> VideoCard(vm, video, vm.store.server, { play(video, playlist) }, { id -> navigate(tab, "channel:$id") }, { signIn() }, if (playlist?.let { list -> list.owned } == true || route == "history") ({
                                val list = playlist
                                if (list != null) vm.removePlaylistVideo(list, video)
                                else vm.removeHistory(video.id)
                            }) else null, audioPlay = { watch = true; vm.playVideo(video, playlist, audio = true) }, removalLabel = if (playlist != null) "Remove from playlist" else "Remove from history",
                                avatarOwner = channel?.id.takeIf { route.startsWith("channel:") && channelTab in listOf(ChannelTab.VIDEOS, ChannelTab.STREAMS) }) }
                        }
                        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::refresh); if (state.videos.isEmpty()) OutlinedButton(onClick = { dialog = ""; settingsPage = "Server" }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Configure server") } }
                        if (!state.loading && visibleVideos.isEmpty() && state.videos.isNotEmpty()) item {
                            Column(Modifier.padding(16.dp)) {
                                Text("Videos hidden by your visibility settings")
                                Text("Change the filters or load another page to find visible videos.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { if (vm.contentSurface() == ContentSurface.SEARCH) dialog = if (tab == "Subscriptions") "visibilityFilters" else "filters" else settingsPage = "Browsing" }) { Text("Visibility controls") }
                            }
                        }
                        if (!state.loading && state.error == null && state.videos.isEmpty() && state.lists.isEmpty()) item { EmptyState(
                            if (scopedSearch.submitted.isNotBlank()) "No matches" else if (route == "history") "Your history is empty" else if (tab == "Search") "Find something to watch" else "Nothing here yet",
                            if (scopedSearch.submitted.isNotBlank()) "Try another title or channel, or clear the search." else if (route == "history") "Videos you watch with history enabled will appear here." else if (tab == "Search") "Search by title, channel, or paste a video link." else "Refresh to check for videos.", "Refresh", vm::refresh) }
                        if (!state.end && !state.loading) item { OutlinedButton(onClick = vm::more, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
                    }
                }
            }
            RssSheet(vm)
            when(dialog) {
                "filters" -> FiltersDialog(vm, { dialog = "" })
                "visibilityFilters" -> FiltersDialog(vm, { dialog = "" }, visibilityOnly = true)
                "player" -> PlayerSettings(vm, playback, { dialog = "" }, activity::enterPip, activity.supportsPip(), { dialog = ""; vm.openSponsorBlock() })
                "create", "edit" -> PlaylistDialog(if(dialog == "edit") playlist else null, { dialog = "" }) { title, privacy, description -> val editing = if (dialog == "edit") playlist else null; vm.action { if (editing != null) vm.api.editPlaylist(editing.id, title, privacy, description) else vm.api.createPlaylist(title, privacy); vm.refreshAccount(); vm.refresh() }; dialog = "" }
                "deletePlaylist", "clearHistory" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(if (dialog == "clearHistory") "Clear watch history?" else "Delete playlist?") }, text = { Text("This also changes your account on the website.") }, confirmButton = { TextButton(onClick = { val clear = dialog == "clearHistory"; val id = playlist?.id; if (clear) vm.clearHistory() else vm.action { if (id != null) vm.api.deletePlaylist(id); vm.refreshAccount(); navigate("Library") }; dialog = "" }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
            }
            val dearrow by vm.dearrowContribution.collectAsStateWithLifecycle()
            if (sponsorEditor != null && !pip) SponsorBlockSheet(vm, sponsorEditor!!, { vm.sponsorSettingsChannel.value = null; signIn() }, dismiss = { vm.sponsorSettingsChannel.value = null })
            if (dearrow.open && !pip && !fullscreen) DeArrowContributionSheet(vm)
            if (saveSheet.video != null && tab != "Account" && !pip) SavePlaylistSheet(vm) { signIn() }
        }
    }
}

@Composable private fun EmptyState(title: String, detail: String, action: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.PlayCircleOutline, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleLarge); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant); FilledTonalButton(onClick = onClick) { Text(action) } } }
@Composable private fun ErrorCard(message: String, retry: () -> Unit) { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp)) { Text(message); OutlinedButton(onClick = retry) { Text("Retry") } } } }
internal fun resolved(base: String, path: String) = base.toHttpUrlOrNull()?.resolve(path)?.toString() ?: path
internal fun time(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
private fun count(value: Long): String = when { value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0); value >= 1000 -> "%.1fK".format(value / 1000.0); else -> "$value" }
@Composable internal fun VideoCard(vm: AppViewModel, video: Video, server: String, play: () -> Unit, channel: (String) -> Unit, signIn: () -> Unit, remove: (() -> Unit)? = null, audioPlay: (() -> Unit)? = null, removalLabel: String = "Remove", avatarOwner: String? = null,
    removeFromPlaylist: (() -> Unit)? = null, removeFromPlaylistEnabled: Boolean = true) {
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
                if (video.author.isNotBlank() || video.channelId.isNotBlank()) ChannelAuthor(server, video.authorAvatar,
                    video.author.ifBlank { "Unknown channel" }, Avatars.show(prefs.thinMode, video.channelId, avatarOwner),
                    size = if (compact) 24.dp else 32.dp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    tag = "video-avatar-${video.id}", onClick = if (ContentVisibility.validChannel(video.channelId)) ({ channel(video.channelId) }) else null)
                Text(listOf(if(video.views > 0) "${count(video.views)} views" else "", video.published).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                video.history?.let { history ->
                    Text("Released: ${history.released ?: "Unknown date"}", style = MaterialTheme.typography.bodySmall)
                    Text("Watched: ${history.watched ?: "Unknown watch date"}", style = MaterialTheme.typography.bodySmall)
                    if (video.unavailable) Text("Video ID: ${video.id}", style = MaterialTheme.typography.bodySmall)
                    if (video.duration <= 0) Text("Duration: Unknown", style = MaterialTheme.typography.bodySmall)
                    else if (prefs.thinMode) Text("Duration: ${time(video.duration)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            VideoActionsMenu(vm, video, signIn, channel, audioPlay ?: { vm.playVideo(video, audio = true) }, remove, removalLabel,
                removeFromPlaylist, removeFromPlaylistEnabled)
            if(remove != null) IconButton(onClick = remove) { Icon(Icons.Default.RemoveCircleOutline, "Remove ${video.title}") }
        }
    }
}
@Composable
private fun MiniPlayer(vm: AppViewModel, playback: PlaybackState, controller: MediaController?, open: () -> Unit, toggle: () -> Unit, close: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("mini-player")
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                .clickable(onClickLabel = "Open player", onClick = open).testTag("mini-player-preview")) {
                if (playback.videoEnabled) PlaybackVideoSurface(playback, controller, Modifier.fillMaxSize())
                else playback.details?.video?.let { video ->
                    AsyncImage(resolved(vm.store.server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), null,
                        Modifier.fillMaxSize().testTag("mini-player-artwork"), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f).clickable(onClickLabel = "Open player", onClick = open).padding(horizontal = 8.dp)) {
                playback.details?.video?.let { DeArrowTitle(vm, it, MaterialTheme.typography.labelLarge, maxLines = 1) }
                Text(playback.details?.video?.author.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            val ended = playback.playerState == Player.STATE_ENDED
            IconButton(onClick = toggle, enabled = playback.canPlay, modifier = Modifier.size(48.dp)) {
                Icon(if (ended) Icons.Default.Replay else if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (ended) "Replay" else if (playback.playWhenReady) "Pause" else "Play")
            }
            IconButton(onClick = close, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Close player") }
        }
    }
}
@Composable private fun WatchScreen(vm: AppViewModel, playback: PlaybackState, controller: androidx.media3.session.MediaController?, modifier: Modifier, fullscreen: () -> Unit, settings: () -> Unit, settingsOpen: Boolean, add: (Video) -> Unit, channel: (String) -> Unit, play: (Video) -> Unit, signIn: () -> Unit) {
    val comments by vm.comments.collectAsStateWithLifecycle(); val subscriptionChannels by vm.subscriptionChannels.collectAsStateWithLifecycle()
    val subscriptions = subscriptionChannels.takeIf { it.context == vm.api.context() }?.channels.orEmpty()
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    var description by remember(playback.details?.video?.id, prefs.extendDescription) { mutableStateOf(prefs.extendDescription) }
    val context = LocalContext.current
    val drawerOpen = comments.open && prefs.showYoutubeComments && comments.videoId == playback.details?.video?.id
    val detailsList = rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxSize().testTag("watch-content")) {
        val playerHeight = playback.geometry.embeddedHeight((maxWidth - 16.dp).value, maxHeight.value, drawerOpen).dp
        Column(Modifier.fillMaxSize()) {
            VideoPlayer(vm, playback, controller, Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(playerHeight).clip(RoundedCornerShape(16.dp)),
                settingsOpen = settingsOpen, onFullscreen = fullscreen, onSettings = settings)
            if (drawerOpen) CommentsDrawer(vm, comments, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), channel) { raw ->
                when (val target = CommentLinks.resolve(raw, vm.store.server, playback.details?.video?.id.orEmpty())) {
                    is CommentLink.Seek -> vm.seekTo(target.seconds.coerceAtMost(Long.MAX_VALUE / 1000) * 1000)
                    is CommentLink.Video -> { vm.closeComments(); vm.openLink(target.link) }
                    is CommentLink.Channel -> { vm.closeComments(); channel(target.id) }
                    is CommentLink.External -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target.url))) }
                        .onFailure { vm.message.value = "No app could open this link." }
                    null -> Unit
                }
            }
            else LazyColumn(Modifier.weight(1f).testTag("watch-details-list"), state = detailsList) {
                playback.details?.let { details ->
                    item(key = "watch:metadata") { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (details.video.membersOnly) MembersBadge(details.video.id)
                        DeArrowTitle(vm, details.video, MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${count(details.video.views)} views · ${details.video.published}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WatchChannelIdentity(details.video, vm.store.server, prefs.thinMode,
                            subscriptions.any { it.id == details.video.channelId }, subscribe = {
                                if (vm.account.value == null) signIn()
                                else vm.toggleSubscribe(details.video.channelId)
                            }, channel = channel)
                        FlowRow(Modifier.fillMaxWidth().testTag("watch-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { add(details.video) }, label = { Text("Save") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                            AssistChip(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${vm.store.server}/watch?v=${details.video.id}&t=${playback.position / 1000}"), "Share video")) }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                            AssistChip(onClick = { vm.openDeArrow(details.video.id) }, label = { Text("DeArrow Title") }, leadingIcon = { Icon(Icons.Default.Title, null) })
                        }
                        if (account == null) Text("Sign in to suggest titles and vote.", style = MaterialTheme.typography.bodySmall)
                        blocked.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (prefs.showYoutubeComments) CommentsEntry(comments, vm::openComments)
                        ActionRow(if (description) "Hide description" else "Show description",
                            modifier = Modifier.testTag("watch-description-toggle").semantics { stateDescription = if (description) "Expanded" else "Collapsed" },
                            trailingIcon = if (description) Icons.Default.ExpandLess else Icons.Default.ExpandMore) { description = !description }
                        if(description) Text(details.description, style = MaterialTheme.typography.bodyMedium)
                    } }
                }
                if (queue.hasExplicitQueue) item(key = "watch:queue") { PlaybackQueuePanel(vm, signIn, channel) }
                playback.details?.let { details ->
                    if (prefs.relatedVideos) {
                        item(key = "watch:up-next") { Text("Up next", Modifier.padding(16.dp).testTag("watch-up-next"), style = MaterialTheme.typography.titleLarge) }
                        items(ContentVisibility.filter(details.recommendations, ContentSurface.RECOMMENDATIONS, prefs.showMemberVideos, blocked = blocked.takeIf { it.context == vm.api.context() }?.ids.orEmpty()), key = { it.id }) { VideoCard(vm, it, vm.store.server, { play(it) }, channel, signIn) }
                    }
                }
            }
        }
    }
}

@Composable private fun FiltersDialog(vm: AppViewModel, dismiss: () -> Unit, visibilityOnly: Boolean = false) {
    val playlistSearch by vm.playlistSearch.collectAsStateWithLifecycle()
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
                if (!playlistSearch) Choice("Uploaded", listOf("", "hour", "today", "week", "month", "year"), date) { date = it }
                if (!playlistSearch) Choice("Duration", listOf("", "short", "long"), duration) { duration = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.showMembers ?: prefs.showMemberVideos, { visibility = visibility.copy(showMembers = it) }, modifier = Modifier.testTag("search-show-members"))
                Text("Show members-only videos", Modifier.weight(1f))
            }
            Text(if (visibility.showMembers == null) "Using browsing default" else "Search override saved on this device", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { visibility = visibility.copy(showMembers = null) }, modifier = Modifier.testTag("search-members-reset")) { Text("Use browsing default") }
            if (account != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.includeBlocked, { visibility = visibility.copy(includeBlocked = it) }, modifier = Modifier.testTag("search-include-blocked"))
                Text("Include blocked channels", Modifier.weight(1f))
            }
        }
    }, confirmButton = { TextButton(onClick = {
        vm.sort = sort; vm.date = date; vm.durationFilter = duration; vm.saveSearchVisibility(visibility); vm.refresh(); dismiss()
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@Composable private fun Choice(label: String, values: List<String>, selected: String, update: (String) -> Unit) {
    DropdownChoiceRow(label, values.map { it to it.ifBlank { "Any" }.replace('_', ' ') }, selected, change = update)
}
@Composable private fun PlaylistDialog(playlist: Playlist?, dismiss: () -> Unit, save: (String, String, String) -> Unit) { var title by remember { mutableStateOf(playlist?.title ?: "") }; var privacy by remember { mutableStateOf(playlist?.privacy ?: "private") }; var description by remember { mutableStateOf(playlist?.description ?: "") }; AlertDialog(onDismissRequest = dismiss, title = { Text(if(playlist == null) "New playlist" else "Edit playlist") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it.take(150) }, label = { Text("Title") }, singleLine = true); Choice("Privacy", listOf("private", "unlisted", "public"), privacy) { privacy = it }; if(playlist != null) OutlinedTextField(description, { description = it }, label = { Text("Description") }) } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { save(title.trim(), privacy, description) }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) }
