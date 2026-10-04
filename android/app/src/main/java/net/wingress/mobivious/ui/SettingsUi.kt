package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.wingress.mobivious.BuildConfig
import net.wingress.mobivious.data.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(vm: AppViewModel, page: String, navigate: (String) -> Unit, back: () -> Unit, signIn: () -> Unit, openChannel: (String) -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val context = vm.api.context()
    var saving by remember(page, context) { mutableStateOf(false) }
    if (page == "SponsorBlock") {
        key(context) { SponsorBlockSheet(vm, "", signIn, back, fullScreen = true) }
        return
    }
    BackHandler { if (!saving) back() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(page) }, navigationIcon = {
            IconButton(onClick = back, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back from $page") }
        })
    }) { padding ->
        key(page, context) {
            when (page) {
                "Settings" -> LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("settings-root"), contentPadding = PaddingValues(vertical = 12.dp)) {
                    item { Text(account?.let { "Signed in as ${it.username}" } ?: "Preferences for this device and instance", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { SettingsLink("Playback", "Speed, quality, audio and captions", Icons.Default.PlayCircle, navigate) }
                    item { SettingsLink("Appearance", "Color mode and video list layout", Icons.Default.Palette, navigate) }
                    item { SettingsLink("Browsing", "Homepage, navigation and video pages", Icons.Default.Explore, navigate) }
                    item { SettingsLink("Subscriptions", "Feed size, sorting and filters", Icons.Default.Subscriptions, navigate) }
                    item { SettingsLink("History & library", "Watch history, resume and default playlist", Icons.Default.History, navigate) }
                    item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
                    item { SettingsLink("SponsorBlock", "Segment skipping, colors and channel overrides", Icons.Default.SkipNext, navigate) }
                    item { SettingsLink("DeArrow", "Community titles and contribution identity", Icons.Default.Title, navigate) }
                    item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
                    item { SettingsLink("Server", vm.store.server, Icons.Default.Dns, navigate) }
                    item { SettingsLink("About", "Version and community data", Icons.Default.Info, navigate) }
                }
                "Server" -> ServerSettingsScreen(vm, Modifier.padding(padding), back)
                "About" -> AboutSettingsScreen(Modifier.padding(padding))
                "Blocked channels" -> BlockedChannelsScreen(vm, Modifier.padding(padding), signIn, openChannel)
                else -> PreferenceSettingsScreen(vm, page, prefs, Modifier.padding(padding), back, signIn, navigate) { saving = it }
            }
        }
    }
}

@Composable
private fun SettingsLink(title: String, detail: String, icon: ImageVector, navigate: (String) -> Unit) {
    ActionRow(title, modifier = Modifier.padding(horizontal = 4.dp), detail = detail, icon = icon) { navigate(title) }
}

@Composable
private fun PreferenceSettingsScreen(vm: AppViewModel, page: String, prefs: AccountPreferences, modifier: Modifier, back: () -> Unit, signIn: () -> Unit, navigate: (String) -> Unit, onBusy: (Boolean) -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val context = remember { vm.api.context() }
    var baseline by rememberSaveable { mutableStateOf(prefs.json().toString()) }
    var draft by rememberSaveable { mutableStateOf(baseline) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var background by rememberSaveable { mutableStateOf(vm.store.background) }
    var pip by rememberSaveable { mutableStateOf(vm.store.pip) }
    val scope = rememberCoroutineScope()
    val value = AccountPreferences.parse(JSONObject(draft))
    val edited = draft != baseline
    LaunchedEffect(prefs) { if (!edited && !busy) { baseline = prefs.json().toString(); draft = baseline } }
    fun update(next: AccountPreferences) { draft = next.json().toString(); error = null }
    val accountOnly = page == "Subscriptions"
    val valid = (!accountOnly || account != null) && (page != "Browsing" || Regex("[A-Z]{2}").matches(value.region)) && (page != "Subscriptions" || value.maxResults in 1..1500)
    Column(modifier.fillMaxSize().imePadding().testTag("settings-category")) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("settings-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(if (account != null) "Preferences are shared with your Invidious account on the website." else "Preferences are saved on this device for this instance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when (page) {
                "Playback" -> {
                    item {
                        SettingsHeading("Playback defaults")
                        SettingsToggle("Autoplay opened videos", "Start playing when you open a video", value.autoplay, !busy) { update(value.copy(autoplay = it)) }
                        SettingsToggle("Next recommendation", "Select the next eligible recommendation when a standalone video ends", value.continueNext, !busy) { update(value.copy(continueNext = it)) }
                        SettingsToggle("Autoplay next video", "Start successors automatically; opened-video autoplay also enables this", value.continueAutoplay, !busy) { update(value.copy(continueAutoplay = it)) }
                        SettingsToggle("Loop videos by default", "Start playback sessions with Repeat One", value.videoLoop, !busy) { update(value.copy(videoLoop = it)) }
                        SettingsToggle("Audio only by default", "Disable video tracks when opening a video", value.listen, !busy) { update(value.copy(listen = it)) }
                        SettingsToggle("Proxy video streams", "Route streams through your Invidious instance", value.local, !busy) { update(value.copy(local = it)) }
                        SettingsChoice("Default speed", PreferenceRules.speeds.map { it.toString() to "${it}×" }, value.speed.toString(), !busy) { update(value.copy(speed = it.toFloat())) }
                        SettingsChoice("Default quality", PreferenceRules.qualities.map { it to PreferenceRules.qualityLabel(it) }, value.qualityDash, !busy) { update(value.copy(qualityDash = it)) }
                        Text("Speed, quality and audio defaults apply to the next video. A resolution selects the best available stream at or below it.", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        SettingsHeading("On this device")
                        SettingsToggle("Background playback", "Keep playing when the app is in the background", background, !busy) { background = it; vm.store.background = it }
                        SettingsToggle("Picture in picture", "Allow the floating player when leaving a video", pip, !busy) { pip = it; vm.store.pip = it }
                        Text("These two options save immediately on this device.", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        SettingsHeading("Caption language priority")
                        Text("Use the first available language. Set all priorities to None to start with captions off.", style = MaterialTheme.typography.bodySmall)
                        repeat(3) { index ->
                            SettingsChoice("Caption priority ${index + 1}", CaptionLanguages.values.map { it to it.ifBlank { "None" } }, value.captions.getOrElse(index) { "" }, !busy) { language ->
                                val languages = MutableList(3) { value.captions.getOrElse(it) { "" } }; languages[index] = language
                                update(value.copy(captions = languages))
                            }
                        }
                    }
                }
                "Appearance" -> item {
                    SettingsChoice("Color mode", listOf("" to "System", "light" to "Light", "dark" to "Dark"), value.darkMode, !busy) { update(value.copy(darkMode = it)) }
                    SettingsChoice("Video list density", listOf("balanced" to "Comfortable", "compact" to "Compact"), value.uiDensity, !busy) { update(value.copy(uiDensity = it)) }
                    SettingsToggle("Hide thumbnails", "Use a text-only video list", value.thinMode, !busy) { update(value.copy(thinMode = it)) }
                }
                "Browsing" -> {
                    item {
                        SettingsToggle("Show members-only videos", "Include members-only content in video lists. This does not grant membership access.", value.showMemberVideos, !busy) { update(value.copy(showMemberVideos = it)) }
                        SettingsLink("Blocked channels", "Manage channels hidden from discovery and recommendations", Icons.Default.Block, navigate)
                    }
                    item {
                        val homes = PreferenceRules.homes.filter { account != null || it !in listOf("Subscriptions", "Playlists") }
                        SettingsChoice("Default homepage", homes.map { it to it.ifBlank { "Search" } }, value.defaultHome, !busy) { update(value.copy(defaultHome = it)) }
                        Text("The homepage opens on launch or sign-in. Search and Library remain reachable in navigation.", style = MaterialTheme.typography.bodySmall)
                        SettingsHeading("Feed navigation order")
                        repeat(if (account == null) 2 else 4) { index ->
                            SettingsChoice("Feed priority ${index + 1}", homes.map { it to it.ifBlank { "Search" } }, value.feedMenu.getOrElse(index) { "" }, !busy) { home ->
                                val menu = value.feedMenu.toMutableList(); while (menu.size <= index) menu.add(""); menu[index] = home
                                update(value.copy(feedMenu = menu))
                            }
                        }
                        OutlinedTextField(value.region, { update(value.copy(region = it.uppercase().take(2))) }, label = { Text("Trending region (e.g. ID)") }, singleLine = true, enabled = !busy, isError = !Regex("[A-Z]{2}").matches(value.region), modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        SettingsHeading("Video pages")
                        SettingsToggle("Show related videos", "Display recommendations below the video", value.relatedVideos, !busy) { update(value.copy(relatedVideos = it)) }
                        SettingsToggle("Expand descriptions by default", "Show the full description when opening a video", value.extendDescription, !busy) { update(value.copy(extendDescription = it)) }
                        SettingsToggle("Show YouTube comments", "Show a comments entry on video pages; text stays hidden until opened", value.showYoutubeComments, !busy) { enabled ->
                            val others = value.comments.filter { it != "youtube" && it.isNotBlank() }
                            update(value.copy(comments = ((if (enabled) listOf("youtube") else emptyList()) + others).take(2).let { it + List((2 - it.size).coerceAtLeast(0)) { "" } }))
                        }
                    }
                }
                "Subscriptions" -> {
                    if (account == null) item { Text("Sign in to change your subscription feed."); Button(onClick = signIn) { Text("Sign in") } }
                    else item {
                        SettingsChoice("Videos per page", (listOf(10, 20, 30, 40, 60, 100, 200, 500, 1000, 1500) + value.maxResults).distinct().sorted().map { it.toString() to it.toString() }, value.maxResults.toString(), !busy) { update(value.copy(maxResults = it.toInt())) }
                        SettingsChoice("Feed sorting", PreferenceRules.feedSorts.zip(listOf("Newest first", "Oldest first", "Title A–Z", "Title Z–A", "Channel A–Z", "Channel Z–A")), value.feedSort, !busy) { update(value.copy(feedSort = it)) }
                        SettingsToggle("Latest video from each channel", "Show one video per subscribed channel", value.latestOnly, !busy) { update(value.copy(latestOnly = it)) }
                        SettingsToggle("Unwatched videos only", "Hide videos already in your watch history", value.unseenOnly, !busy) { update(value.copy(unseenOnly = it)) }
                        SettingsToggle("Pending notifications only", "Show pending account uploads in this feed", value.notificationsOnly, !busy) { update(value.copy(notificationsOnly = it)) }
                        Text("This filters the feed; it does not enable Android alerts. Page size also applies to watch history.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                "History & library" -> item {
                    if (account != null) SettingsToggle("Watch history", "Record played videos in your shared account history", value.watchHistory, !busy) { update(value.copy(watchHistory = it)) }
                    else Text("Sign in to keep a shared watch history.", style = MaterialTheme.typography.bodyMedium)
                    SettingsToggle("Remember playback position", if (account != null) "Resume across Android and the website" else "Resume videos on this device", value.savePosition, !busy) { update(value.copy(savePosition = it)) }
                    if (account != null) {
                        SettingsChoice("Default playlist", listOf("" to "None") + playlists.filter { it.owned }.map { it.id to it.title }, value.defaultPlaylist, !busy) { update(value.copy(defaultPlaylist = it)) }
                        Text("The default playlist appears first when saving a video.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                "DeArrow" -> {
                    item {
                        SettingsToggle("Replace video titles with DeArrow", "Use community titles throughout browsing and playback", value.dearrowEnabled, !busy) { update(value.copy(dearrowEnabled = it)) }
                        SettingsToggle("Show original titles using the DeArrow icon", "Keep original titles available on each video", value.dearrowShowOriginal, !busy) { update(value.copy(dearrowShowOriginal = it)) }
                    }
                    if (account != null) item { DeArrowIdentitySettings(vm) }
                    item { CommunityDataLinks() }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("settings-save-error")) } }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = back, enabled = !busy) { Text("Cancel") }
            Button(onClick = {
                busy = true; onBusy(true); error = null
                scope.launch {
                    try { vm.savePreferences(value, AccountPreferences.parse(JSONObject(baseline)), context); back() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Could not save settings. Your changes are still here." }
                    finally { busy = false; onBusy(false) }
                }
            }, enabled = !busy && valid, modifier = Modifier.testTag("settings-save")) { Text(if (busy) "Saving…" else "Save") }
        }
    }
}

@Composable
private fun SettingsHeading(title: String) { Text(title, Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }

@Composable
private fun SettingsToggle(label: String, detail: String, value: Boolean, enabled: Boolean, update: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, enabled = enabled, role = Role.Switch, onValueChange = update).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(label, style = MaterialTheme.typography.bodyLarge); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(value, null, enabled = enabled)
    }
}

@Composable
private fun SettingsChoice(label: String, choices: List<Pair<String, String>>, selected: String, enabled: Boolean, update: (String) -> Unit) {
    DropdownChoiceRow(label, choices, selected, enabled = enabled, change = update)
}

@Composable
private fun ServerSettingsScreen(vm: AppViewModel, modifier: Modifier, back: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(vm.store.server) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your app and website share the same server and account. Changing servers signs you out.") }
        item { OutlinedTextField(address, { address = it; error = null }, label = { Text("HTTPS address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), isError = error != null) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Button(onClick = { try { vm.switchServer(InvidiousApi.normalizeServer(address, BuildConfig.DEBUG)); back() } catch (e: Exception) { error = e.message } }) { Text("Connect") } }
    }
}

@Composable
private fun AboutSettingsScreen(modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Mobivious ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall); Text("Powered by Invidious & Companion", Modifier.padding(top = 8.dp)) }
        item { CommunityDataLinks() }
    }
}

@Composable
private fun CommunityDataLinks() {
    val uri = LocalUriHandler.current
    Column {
        TextButton(onClick = { uri.openUri("https://dearrow.ajay.app/") }) { Text("Community title data: DeArrow / SponsorBlock") }
        TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by-nc-sa/4.0/") }) { Text("CC BY-NC-SA 4.0") }
    }
}
