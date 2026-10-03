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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
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
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val sponsorEditor by vm.sponsorSettingsChannel.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(vm.tab) }
    var route by rememberSaveable { mutableStateOf(vm.route) }
    var watch by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var addVideo by remember { mutableStateOf<Video?>(null) }
    var search by rememberSaveable { mutableStateOf(vm.query) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun navigate(selected: String, path: String = "") { tab = selected; route = path; watch = false; vm.navigate(selected, path) }
    fun play(video: Video) { watch = true; vm.play(video.id) }
    LaunchedEffect(shared.value) { if (shared.value) { watch = true; shared.value = false } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(watch, playback.playWhenReady, playback.error, playback.details, prefs, vm.store.pip) { activity.updatePip(watch) }
    DisposableEffect(fullscreen, pip) {
        activity.requestedOrientation = if (fullscreen && !pip) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.setFullscreen(fullscreen && !pip)
        onDispose { activity.setFullscreen(false) }
    }
    LaunchedEffect(pip, watch, playback.mediaId) { if (pip || !watch) { dialog = ""; vm.closeDeArrow() }; if (pip) vm.sponsorSettingsChannel.value = null }
    LaunchedEffect(account) { dialog = "" }
    LaunchedEffect(dialog) { if (dialog == "settings") vm.refreshSharedSettings() }
    BackHandler(fullscreen || watch || route.isNotBlank()) { when { fullscreen -> fullscreen = false; watch -> watch = false; else -> navigate(tab) } }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light) {
        Surface(Modifier.fillMaxSize()) {
            if (pip || fullscreen) {
                VideoPlayer(vm, playback, controller, Modifier.fillMaxSize(), fullscreen = fullscreen,
                    controls = !pip, settingsOpen = dialog == "player" || sponsorEditor != null, onFullscreen = { fullscreen = !fullscreen },
                    onSettings = { dialog = "player" })
            } else Scaffold(
                topBar = { TopAppBar(title = {
                    if (watch) Text("Now playing", style = MaterialTheme.typography.titleMedium)
                    else if (route.isNotEmpty()) Text(channel?.name ?: playlist?.title ?: state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Mobivious", fontWeight = FontWeight.Bold) }
                }, navigationIcon = { if (watch || route.isNotEmpty()) IconButton(onClick = { if (watch) watch = false else navigate(tab) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = {
                    IconButton(onClick = { dialog = "settings" }) { Icon(Icons.Default.Settings, "App settings") }
                    IconButton(onClick = { dialog = if (account == null) "login" else "account" }) { Icon(if (account == null) Icons.Default.AccountCircle else Icons.Default.VerifiedUser, "Account") }
                }) },
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = { Column {
                    if (!watch && playback.details != null) MiniPlayer(vm, playback, { watch = true }, vm::togglePlay, vm::closePlayer)
                    NavigationBar { listOf("Home" to Icons.Default.Home, "Search" to Icons.Default.Search, "Subscriptions" to Icons.Default.Subscriptions, "Library" to Icons.Default.VideoLibrary).forEach { (name, icon) ->
                        NavigationBarItem(selected = tab == name && !watch, onClick = { navigate(name) }, icon = { Icon(icon, name) }, label = { Text(name, fontSize = 11.sp) })
                    } }
                } }
            ) { padding ->
                if (watch) WatchScreen(vm, playback, controller, Modifier.padding(padding), { fullscreen = true }, { dialog = "player" }, dialog == "player" || sponsorEditor != null, { addVideo = it }, { id -> navigate("Home", "channel:$id") }, ::play)
                else Column(Modifier.padding(padding).fillMaxSize()) {
                    if(offline) Text("Offline · showing saved results", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    if (route.isEmpty() && tab == "Search") {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(search, { search = it }, label = { Text("Search videos or paste a link") }, singleLine = true, modifier = Modifier.weight(1f), trailingIcon = { IconButton(onClick = {
                                val link = VideoLinks.parse(search, vm.store.server)
                                if (link != null) { watch = true; vm.play(link.id, link.seconds) } else { vm.query = search; vm.refresh() }
                            }) { Icon(Icons.Default.Search, "Search") } })
                            IconButton(onClick = { dialog = "filters" }) { Icon(Icons.Default.Tune, "Search filters") }
                        }
                    }
                    if (route.isEmpty() && tab == "Home") Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("popular" to "Popular", "trending" to "Trending").forEach { (key, name) -> FilterChip(selected = vm.discovery == key, onClick = { vm.discovery = key; vm.refresh() }, label = { Text(name) }) }
                        Spacer(Modifier.weight(1f)); IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                    if ((tab == "Library" || tab == "Subscriptions") && account == null) EmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", "Sign in") { dialog = "login" }
                    else if (tab == "Library" && route.isEmpty()) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Your library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        item { Card(onClick = { navigate("Library", "history") }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null); Spacer(Modifier.width(16.dp)); Text("Watch history", Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } } }
                        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { dialog = "create" }) { Icon(Icons.Default.Add, null); Text("New") } } }
                        items(playlists, key = { it.id }) { list -> Card(onClick = { navigate("Library", "playlist:${list.id}") }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Column { Text(list.title, fontWeight = FontWeight.SemiBold); Text("${list.count} videos · ${list.privacy}", style = MaterialTheme.typography.bodySmall) } } } }
                        if (playlists.isEmpty()) item { Text("Save videos to a playlist from the watch screen.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::refresh) }
                    }
                    else LazyColumn(Modifier.fillMaxSize().testTag("browse-video-list"), contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (channel != null) item { Column(Modifier.padding(16.dp)) { Text(channel!!.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("${channel!!.subscribers} subscribers", style = MaterialTheme.typography.bodyMedium); if (channel!!.description.isNotBlank()) Text(channel!!.description, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)); Button(onClick = { if (account == null) dialog = "login" else vm.toggleSubscribe(channel!!.id) }) { Text(if (subscriptions.any { it.id == channel!!.id }) "Subscribed" else "Subscribe") } } }
                        if (channel != null) item { TextButton(onClick = { vm.openSponsorBlock(channel!!.id) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Channel SponsorBlock settings") } }
                        if (playlist != null) item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("${playlist!!.count} videos", Modifier.weight(1f)); TextButton(onClick = { dialog = "edit" }) { Text("Edit playlist") }; IconButton(onClick = { dialog = "deletePlaylist" }) { Icon(Icons.Default.DeleteOutline, "Delete playlist") } } }
                        if (route == "history") item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Recently watched", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { dialog = "clearHistory" }) { Text("Clear") } } }
                        if (tab == "Subscriptions" && subscriptions.isNotEmpty()) item { androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(subscriptions) { c -> AssistChip(onClick = { navigate("Subscriptions", "channel:${c.id}") }, label = { Text(c.name) }) } } }
                        items(state.videos, key = { it.id + it.indexId }) { video -> VideoCard(vm, video, vm.store.server, { play(video) }, { id -> navigate(tab, "channel:$id") }, if (playlist != null || route == "history") ({ vm.action {
                            if (playlist != null) vm.api.removeFromPlaylist(playlist!!.id, video.indexId) else vm.api.removeHistory(video.id)
                            vm.refresh()
                        } }) else null) }
                        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::refresh); if (state.videos.isEmpty()) TextButton(onClick = { dialog = "server" }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Configure server") } }
                        if (!state.loading && state.error == null && state.videos.isEmpty()) item { EmptyState(if (tab == "Search") "Find something to watch" else "Nothing here yet", if (tab == "Search") "Search by title, channel, or paste a video link." else "Refresh to check for videos.", "Refresh", vm::refresh) }
                        if (!state.end && !state.loading && state.videos.isNotEmpty()) item { TextButton(onClick = vm::more, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
                    }
                }
            }
            when(dialog) {
                "login" -> LoginDialog(vm, { dialog = "" }, { dialog = "server" })
                "server" -> ServerDialog(vm, { dialog = "" })
                "account" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(account?.username.orEmpty()) }, text = { Text("Signed in to ${vm.store.server}") }, confirmButton = { TextButton(onClick = { vm.logout(); dialog = "" }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Close") } })
                "settings" -> SettingsDialog(vm, prefs, account != null, { dialog = "" }, { dialog = "server" }, { vm.openSponsorBlock() })
                "filters" -> FiltersDialog(vm, { dialog = "" })
                "player" -> PlayerSettings(vm, playback, { dialog = "" }, activity::enterPip, activity.supportsPip(), { dialog = ""; vm.openSponsorBlock() })
                "create", "edit" -> PlaylistDialog(if(dialog == "edit") playlist else null, { dialog = "" }) { title, privacy, description -> val editing = if (dialog == "edit") playlist else null; vm.action { if (editing != null) vm.api.editPlaylist(editing.id, title, privacy, description) else vm.api.createPlaylist(title, privacy); vm.refreshAccount(); vm.refresh() }; dialog = "" }
                "deletePlaylist", "clearHistory" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(if (dialog == "clearHistory") "Clear watch history?" else "Delete playlist?") }, text = { Text("This also changes your account on the website.") }, confirmButton = { TextButton(onClick = { val clear = dialog == "clearHistory"; val id = playlist?.id; vm.action { if (clear) vm.api.clearHistory() else if (id != null) vm.api.deletePlaylist(id); if (clear) vm.refresh() else { vm.refreshAccount(); navigate("Library") } }; dialog = "" }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
            }
            val dearrow by vm.dearrowContribution.collectAsStateWithLifecycle()
            if (sponsorEditor != null && !pip) SponsorBlockSheet(vm, sponsorEditor!!, { vm.sponsorSettingsChannel.value = null; dialog = "login" }) { vm.sponsorSettingsChannel.value = null }
            if (dearrow.open && !pip && !fullscreen) DeArrowContributionSheet(vm)
            addVideo?.let { video -> AlertDialog(onDismissRequest = { addVideo = null }, title = { Text("Save to playlist") }, text = { Column { if (account == null) TextButton(onClick = { addVideo = null; dialog = "login" }) { Text("Sign in") } else if (playlists.isEmpty()) TextButton(onClick = { addVideo = null; dialog = "create" }) { Text("Create your first playlist") }; playlists.forEach { list -> TextButton(onClick = { vm.action { vm.api.addToPlaylist(list.id, video.id); vm.refreshAccount(); vm.message.value = "Saved to ${list.title}" }; addVideo = null }) { Text(list.title) } } } }, confirmButton = { TextButton(onClick = { addVideo = null }) { Text("Cancel") } }) }
        }
    }
}

@Composable private fun EmptyState(title: String, detail: String, action: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.PlayCircleOutline, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleLarge); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant); FilledTonalButton(onClick = onClick) { Text(action) } } }
@Composable private fun ErrorCard(message: String, retry: () -> Unit) { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp)) { Text(message); TextButton(onClick = retry) { Text("Retry") } } } }
private fun resolved(base: String, path: String) = base.toHttpUrlOrNull()?.resolve(path)?.toString() ?: path
private fun time(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
private fun count(value: Long): String = when { value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0); value >= 1000 -> "%.1fK".format(value / 1000.0); else -> "$value" }
@Composable private fun VideoCard(vm: AppViewModel, video: Video, server: String, play: () -> Unit, channel: (String) -> Unit, remove: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = play)) {
            AsyncImage(resolved(server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(if(video.live) "LIVE" else time(video.duration), Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .8f)).padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { DeArrowTitle(vm, video, MaterialTheme.typography.titleMedium, Modifier.clickable(onClick = play), maxLines = 2); Text(listOf(video.author, if(video.views > 0) "${count(video.views)} views" else "", video.published).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).clickable(enabled = video.channelId.isNotEmpty()) { channel(video.channelId) }) }
            if(remove != null) IconButton(onClick = remove) { Icon(Icons.Default.RemoveCircleOutline, "Remove ${video.title}") }
        }
    }
}
@Composable private fun MiniPlayer(vm: AppViewModel, playback: PlaybackState, open: () -> Unit, toggle: () -> Unit, close: () -> Unit) { Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) { Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = open).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f).padding(12.dp)) { playback.details?.video?.let { DeArrowTitle(vm, it, MaterialTheme.typography.labelLarge, maxLines = 1) }; Text(playback.details?.video?.author.orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = toggle) { Icon(if(playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if(playback.playing) "Pause" else "Play") }; IconButton(onClick = close) { Icon(Icons.Default.Close, "Close player") } } } }
@Composable private fun WatchScreen(vm: AppViewModel, playback: PlaybackState, controller: androidx.media3.session.MediaController?, modifier: Modifier, fullscreen: () -> Unit, settings: () -> Unit, settingsOpen: Boolean, add: (Video) -> Unit, channel: (String) -> Unit, play: (Video) -> Unit) {
    val comments by vm.comments.collectAsStateWithLifecycle(); val error by vm.commentError.collectAsStateWithLifecycle(); val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var description by remember { mutableStateOf(false) }; var showingComments by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier.fillMaxSize()) {
        VideoPlayer(vm, playback, controller, Modifier.fillMaxWidth().padding(horizontal = 8.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(16.dp)),
            settingsOpen = settingsOpen, onFullscreen = fullscreen, onSettings = settings)
        LazyColumn(Modifier.testTag("watch-details-list")) {
            playback.details?.let { details ->
                item { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeArrowTitle(vm, details.video, MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${count(details.video.views)} views · ${details.video.published}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(details.video.author, Modifier.weight(1f).clickable { channel(details.video.channelId) }, fontWeight = FontWeight.SemiBold); FilledTonalButton(onClick = { if(vm.account.value == null) vm.message.value = "Sign in from the account button to subscribe." else vm.toggleSubscribe(details.video.channelId) }) { Text(if(subscriptions.any { it.id == details.video.channelId }) "Subscribed" else "Subscribe") } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { add(details.video) }, label = { Text("Save") }, leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) })
                        AssistChip(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${vm.store.server}/watch?v=${details.video.id}&t=${playback.position / 1000}"), "Share video")) }, label = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                    }
                    TextButton(onClick = { vm.openDeArrow(details.video.id) }) { Text("Suggest / vote on titles") }
                    TextButton(onClick = { vm.openSponsorBlock(details.video.channelId) }) { Text("Channel SponsorBlock settings") }
                    if (account == null) Text("Sign in to suggest titles and vote.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { description = !description }) { Text(if(description) "Hide description" else "Show description") }
                    if(description) Text(details.description, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showingComments = !showingComments; if(showingComments) vm.loadComments() }) { Text(if(showingComments) "Hide comments" else "Comments") }
                } }
                if(showingComments) {
                    items(comments.items) { c -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text("${c.author} · ${c.published}", style = MaterialTheme.typography.labelMedium); Text(c.text, Modifier.padding(vertical = 6.dp)); Text("${c.likes} likes", style = MaterialTheme.typography.bodySmall) } }
                    if(error != null) item { ErrorCard(error!!) { vm.loadComments() } }
                    if(comments.continuation.isNotEmpty()) item { TextButton(onClick = { vm.loadComments(true) }) { Text("More comments") } }
                }
                item { Text("Up next", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
                items(details.recommendations, key = { it.id }) { VideoCard(vm, it, vm.store.server, { play(it) }, channel) }
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
@Composable private fun ServerDialog(vm: AppViewModel, dismiss: () -> Unit) { var address by remember { mutableStateOf(vm.store.server) }; var error by remember { mutableStateOf<String?>(null) }; AlertDialog(onDismissRequest = dismiss, title = { Text("Invidious server") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Your app and website share the same server and account. Changing servers signs you out."); OutlinedTextField(address, { address = it }, label = { Text("HTTPS address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)); if(error != null) Text(error!!, color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton(onClick = { try { val value = InvidiousApi.normalizeServer(address, net.wingress.mobivious.BuildConfig.DEBUG); vm.switchServer(value); dismiss() } catch(e: Exception) { error = e.message } }) { Text("Connect") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) }
@Composable private fun SettingsDialog(vm: AppViewModel, prefs: AccountPreferences, signedIn: Boolean, dismiss: () -> Unit, server: () -> Unit, sponsorBlock: () -> Unit) {
    var background by remember { mutableStateOf(vm.store.background) }; var pip by remember { mutableStateOf(vm.store.pip) }; var history by remember { mutableStateOf(prefs.watchHistory) }; var positions by remember { mutableStateOf(prefs.savePosition) }; var region by remember { mutableStateOf(vm.region) }
    var enabled by remember { mutableStateOf(prefs.dearrowEnabled) }; var original by remember { mutableStateOf(prefs.dearrowShowOriginal) }
    var before by remember { mutableStateOf(prefs) }; var edited by remember { mutableStateOf(false) }
    LaunchedEffect(prefs) { if (!edited) { before = prefs; history = prefs.watchHistory; positions = prefs.savePosition; enabled = prefs.dearrowEnabled; original = prefs.dearrowShowOriginal } }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Settings") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Playback", style = MaterialTheme.typography.titleMedium); ToggleRow("Background playback", background) { background = it; vm.store.background = it }; ToggleRow("Picture in picture", pip) { pip = it; vm.store.pip = it }
        TextButton(onClick = sponsorBlock, enabled = !busy) { Text("SponsorBlock") }
        if(signedIn) { HorizontalDivider(Modifier.padding(vertical = 12.dp)); Text("Shared account settings", style = MaterialTheme.typography.titleMedium); ToggleRow("Watch history", history) { history = it; edited = true }; ToggleRow("Remember playback position", positions) { positions = it; edited = true } }
        HorizontalDivider(Modifier.padding(vertical = 12.dp)); Text("DeArrow", style = MaterialTheme.typography.titleMedium)
        ToggleRow("Replace video titles with DeArrow", enabled) { enabled = it; edited = true }
        ToggleRow("Show original titles using the DeArrow icon", original) { original = it; edited = true }
        Text(if (signedIn) "These DeArrow settings are shared with the website." else "These DeArrow settings are saved on this device for this instance.", style = MaterialTheme.typography.bodySmall)
        if (signedIn) DeArrowIdentitySettings(vm)
        TextButton(onClick = { uri.openUri("https://dearrow.ajay.app/") }) { Text("Community title data: DeArrow / SponsorBlock") }
        TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by-nc-sa/4.0/") }) { Text("CC BY-NC-SA 4.0") }
        OutlinedTextField(region, { region = it.uppercase().take(2) }, label = { Text("Trending region (e.g. ID)") }, singleLine = true)
        TextButton(onClick = server) { Text("Server: ${vm.store.server.toHttpUrlOrNull()?.host}") }
        Text("Mobivious ${net.wingress.mobivious.BuildConfig.VERSION_NAME}\nPowered by Invidious & Companion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    } }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val context = vm.api.context(); val baseline = before; val value = baseline.copy(watchHistory = history, savePosition = positions, dearrowEnabled = enabled, dearrowShowOriginal = original)
        busy = true; error = null
        scope.launch { try { vm.savePreferences(value, baseline, context); if(region.length == 2) { vm.region = region; vm.store.region = region }; dismiss() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Could not save settings." } finally { busy = false } }
    }) { Text(if (busy) "Saving…" else "Save") } }, dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Close") } })
}
@Composable private fun ToggleRow(label: String, value: Boolean, update: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, update) } }
@Composable private fun FiltersDialog(vm: AppViewModel, dismiss: () -> Unit) { var sort by remember { mutableStateOf(vm.sort) }; var date by remember { mutableStateOf(vm.date) }; var duration by remember { mutableStateOf(vm.durationFilter) }; AlertDialog(onDismissRequest = dismiss, title = { Text("Search filters") }, text = { Column { Choice("Sort", listOf("relevance", "rating", "upload_date", "view_count"), sort) { sort = it }; Choice("Uploaded", listOf("", "hour", "today", "week", "month", "year"), date) { date = it }; Choice("Duration", listOf("", "short", "long"), duration) { duration = it } } }, confirmButton = { TextButton(onClick = { vm.sort = sort; vm.date = date; vm.durationFilter = duration; vm.refresh(); dismiss() }) { Text("Apply") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) }
@Composable private fun Choice(label: String, values: List<String>, selected: String, update: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { TextButton(onClick = { expanded = true }) { Text("$label: ${selected.ifBlank { "Any" }.replace('_', ' ')}") }; DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem(text = { Text(value.ifBlank { "Any" }.replace('_', ' ')) }, onClick = { update(value); expanded = false }) } } } }
@Composable private fun PlaylistDialog(playlist: Playlist?, dismiss: () -> Unit, save: (String, String, String) -> Unit) { var title by remember { mutableStateOf(playlist?.title ?: "") }; var privacy by remember { mutableStateOf(playlist?.privacy ?: "private") }; var description by remember { mutableStateOf(playlist?.description ?: "") }; AlertDialog(onDismissRequest = dismiss, title = { Text(if(playlist == null) "New playlist" else "Edit playlist") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it.take(150) }, label = { Text("Title") }, singleLine = true); Choice("Privacy", listOf("private", "unlisted", "public"), privacy) { privacy = it }; if(playlist != null) OutlinedTextField(description, { description = it }, label = { Text("Description") }) } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { save(title.trim(), privacy, description) }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) }
