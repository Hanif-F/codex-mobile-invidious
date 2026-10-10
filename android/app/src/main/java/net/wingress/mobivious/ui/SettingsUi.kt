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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.wingress.mobivious.BuildConfig
import net.wingress.mobivious.data.*
import org.json.JSONObject

internal val LocalSettingsTopInset = staticCompositionLocalOf { 0.dp }

@Composable
internal fun settingsPadding(horizontal: Dp = Liquid.inset) = PaddingValues(start = horizontal, end = horizontal, top = LocalSettingsTopInset.current + 16.dp, bottom = 32.dp)

@Composable
internal fun SettingsScreen(vm: AppViewModel, page: String, navigate: (String) -> Unit, back: () -> Unit, signIn: () -> Unit, openChannel: (String) -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val context = vm.api.context()
    val accountBusy by vm.accountBusy.collectAsStateWithLifecycle()
    var previousContext by remember { mutableStateOf(context) }
    LaunchedEffect(context) {
        if (previousContext != context && page in accountSettingsPages && page != "Account") navigate("Account")
        previousContext = context
    }
    var saving by remember(page, context) { mutableStateOf(false) }
    if (page == "SponsorBlock") {
        key(context) { SponsorBlockSheet(vm, "", signIn, back, fullScreen = true) }
        return
    }
    BackHandler { if (!saving && !accountBusy) back() }
    BrowseGlassSurface(Modifier.fillMaxSize().imePadding(), softEdge = true, toolbar = {
        LibraryToolbar(page, large = page == "Settings", back = back,
            backLabel = "Back from $page", backEnabled = !saving && !accountBusy)
    }) { body, top ->
        Box(body, contentAlignment = Alignment.TopCenter) {
          CompositionLocalProvider(LocalSettingsTopInset provides top) {
            val content = Modifier.widthIn(max = 800.dp).fillMaxSize()
            key(page, context) {
                when (page) {
                    "Settings" -> LazyColumn(content.testTag("settings-root"), contentPadding = settingsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Text(account?.let { "Signed in as ${it.username}" } ?: "Preferences for this device and instance", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item { SectionHeading("Your account") }
                        item { SettingsLink("Account", "Sign-in, credentials, sessions and API tokens", Icons.Default.AccountCircle, navigate) }
                        item { SectionHeading("Preferences") }
                        item { SettingsLink("Playback", "Speed, quality, audio and captions", Icons.Default.PlayCircle, navigate) }
                        item { SettingsLink("Appearance", "Color mode and video list layout", Icons.Default.Palette, navigate) }
                        item { SettingsLink("Browsing", "Homepage, region and video pages", Icons.Default.Explore, navigate) }
                        item { SettingsLink("Subscriptions", "Feed size, sorting and filters", Icons.Default.Subscriptions, navigate) }
                        item { SettingsLink("History & library", "Watch history, resume and default playlist", Icons.Default.History, navigate) }
                        item { SectionHeading("Community tools") }
                        item { SettingsLink("SponsorBlock", "Segment skipping, colors and channel overrides", Icons.Default.SkipNext, navigate) }
                        item { SettingsLink("DeArrow", "Community titles and contribution identity", Icons.Default.Title, navigate) }
                        item { SettingsLink("AI channel filter", "AiSList warnings and video filtering", Icons.Default.FilterAlt, navigate) }
                        item { SectionHeading("Connection & app") }
                        item { SettingsLink("Server", vm.store.server, Icons.Default.Dns, navigate) }
                        item { SettingsLink("About", "Version and community data", Icons.Default.Info, navigate) }
                    }
                    "Server" -> ServerSettingsScreen(vm, content, back)
                    "About" -> AboutSettingsScreen(content)
                    "Blocked channels" -> BlockedChannelsScreen(vm, content, signIn, openChannel)
                    in accountSettingsPages -> AccountSettingsContent(vm, page, content, navigate, signIn)
                    else -> PreferenceSettingsScreen(vm, page, prefs, content, back, signIn, navigate) { saving = it }
                }
            }
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
    val aiState by vm.aiFilter.state.collectAsStateWithLifecycle()
    LaunchedEffect(page, context) { if (page == "AI channel filter") vm.aiFilter.refreshStatus() }
    val value = AccountPreferences.parse(JSONObject(draft))
    val edited = draft != baseline
    LaunchedEffect(prefs) { if (draft == baseline && !busy) { baseline = prefs.json().toString(); draft = baseline } }
    fun update(next: AccountPreferences) { draft = next.json().toString(); error = null }
    val accountOnly = page == "Subscriptions"
    val valid = (!accountOnly || account != null) && (page != "Browsing" || Regex("[A-Z]{2}").matches(value.region)) && (page != "Subscriptions" || value.maxResults in 1..1500)
    val density = LocalDensity.current
    var footerHeight by remember { mutableStateOf(100.dp) }
    Box(modifier.fillMaxSize().testTag("settings-category")) {
        LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset,
            top = LocalSettingsTopInset.current + 16.dp, bottom = footerHeight + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(if (account != null) "Preferences are shared with your Invidious account on the website." else "Preferences are saved on this device for this instance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when (page) {
                "AI channel filter" -> {
                    item {
                        Column(Modifier.testTag("ai-filter-master")) {
                            SettingsToggle("Enable AI Channel Filter", "Pause filtering and warnings without clearing your choices", value.aiFilter.enabled, !busy) { update(value.copy(aiFilter = value.aiFilter.copy(enabled = it))) }
                        }
                        if (!value.aiFilter.enabled) Text("Filtering is paused. Edit the actions, then enable the filter and Save to apply them.", style = MaterialTheme.typography.bodySmall)
                        Text("AiSList is a community classification of channels. Blocklist means high confidence; Warnlist means moderate confidence. Classifications may be incorrect. Invidious does not perform automatic AI detection.", style = MaterialTheme.typography.bodySmall)
                        aiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-status-error")) }
                    }
                    AiPageGroup.entries.forEach { group -> item {
                        SettingsHeading(group.label)
                        Text(when (group) {
                            AiPageGroup.FEEDS -> "Videos in Popular and Trending."
                            AiPageGroup.SEARCH -> "Global search and hashtag results."
                            AiPageGroup.RECOMMENDATIONS -> "Hidden recommendations cannot become automatic playback targets."
                            AiPageGroup.OTHER -> "Subscriptions, history, channel videos and scoped searches, playlists, mixes, clips and queues. These pages never hide AI matches. Downloads are unchanged."
                        }, style = MaterialTheme.typography.bodySmall)
                        AiListKind.entries.forEach { kind ->
                            Box(Modifier.testTag("ai-choice-${kind.wire}-${group.wire}")) {
                                SettingsChoice(kind.label, AiAction.entries.filter { group != AiPageGroup.OTHER || it != AiAction.HIDE }.map { it.wire to it.label },
                                    value.aiFilter.saved(kind, group).wire, !busy) { update(value.copy(aiFilter = value.aiFilter.withAction(kind, group, AiAction.parse(it, group)))) }
                            }
                        }
                    } }
                    item { AiListDetails(aiState) { scope.launch { vm.aiFilter.refreshStatus() } } }
                }
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
                        SettingsChoice("Preferred video codec", PreferenceRules.videoCodecs, value.videoCodec, !busy) { update(value.copy(videoCodec = it)) }
                        SettingsChoice("Default quality", PreferenceRules.qualities.map { it to PreferenceRules.qualityLabel(it) }, value.qualityDash, !busy) { update(value.copy(qualityDash = it)) }
                        Text("Playback defaults apply to the next video. DASH playback prefers the selected codec when available. A resolution selects the best available stream at or below it, then prefers the codec at that resolution.", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        SettingsHeading("On this device")
                        SettingsToggle("Background playback", "Keep playing when the app is in the background", background, !busy) { if (vm.setBackground(it, context)) background = it }
                        SettingsToggle("Picture in picture", "Allow the floating player when leaving a video", pip, !busy) { if (vm.setPip(it, context)) pip = it }
                        Text("These two options save immediately for this account or guest on this device.", style = MaterialTheme.typography.bodySmall)
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
                    val reduceTransparency by vm.store.reduceTransparency.collectAsStateWithLifecycle()
                    SettingsHeading("Accessibility")
                    SettingsToggle("Reduce transparency", "Use solid controls for stronger contrast. Saves on this device immediately.", reduceTransparency, !busy) { vm.store.reduceTransparency(it) }
                }
                "Browsing" -> {
                    item {
                        SettingsToggle("Show members-only videos", "Include members-only content in video lists. This does not grant membership access.", value.showMemberVideos, !busy) { update(value.copy(showMemberVideos = it)) }
                        SettingsLink("Blocked channels", "Manage channels hidden from discovery and recommendations", Icons.Default.Block, navigate)
                    }
                    item {
                        val homes = PreferenceRules.homes.filter { account != null || it !in listOf("Subscriptions", "Playlists") }
                        SettingsChoice("Default homepage", homes.map { it to PreferenceRules.homeLabel(it) }, value.defaultHome, !busy) { update(value.copy(defaultHome = it)) }
                        Text("Choose where the app opens. Popular and Trending live in Discover; Subscriptions and You remain in navigation. Signing in returns you to what you were doing.", style = MaterialTheme.typography.bodySmall)
                        RegionChoice(value.region, !busy, "settings-region") { update(value.copy(region = it)) }
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
                    if (account == null) item { LibraryEmptyState("Your subscription feed", "Sign in to change your subscription feed.", Icons.Default.Subscriptions, "Sign in", signIn) }
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
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface)))
            .navigationBarsPadding().padding(horizontal = Liquid.inset, vertical = 12.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("settings-save-error")) }
            LibraryControlScene {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = back, enabled = !busy) { Text("Cancel") }
                    LibraryActionButton(if (busy) "Saving…" else "Save", Modifier.testTag("settings-save"), enabled = !busy && valid, prominent = true, onClick = {
                        busy = true; onBusy(true); error = null
                        scope.launch {
                            try { vm.savePreferences(value, AccountPreferences.parse(JSONObject(baseline)), context); back() }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Could not save settings. Your changes are still here." }
                            finally { busy = false; onBusy(false) }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun SettingsHeading(title: String) { Text(title, Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }

@Composable
private fun AiListDetails(state: AiFilterState, refresh: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("ai-list-details")) { Text("List status and technical details") }
        if (expanded) {
            AiListKind.entries.forEach { kind ->
                Text(kind.label, style = MaterialTheme.typography.titleSmall)
                val list = state.lists[kind]
                Text(if (list?.available != true) "List unavailable" else "${list.channelCount} channels · Downloaded ${list.updatedAt.orEmpty()}${if (list.stale) " · Stale; using the last successful copy" else ""}",
                    Modifier.testTag("ai-status-${kind.wire}"), style = MaterialTheme.typography.bodySmall)
            }
            Text("Lists refresh on the server every six hours. Matching uses exact channel IDs or normalized handles. Missing handles use the instance’s shared channel cache; unresolved videos stay visible. No account identity or viewing history is sent to AiSList.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = refresh, enabled = !state.loading) { Text(if (state.loading) "Refreshing…" else "Refresh status") }
            TextButton(onClick = { uri.openUri("https://aisloplist.com/") }) { Text("Community classification data: AiSList") }
            TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by-nc/4.0/") }) { Text("CC BY-NC 4.0") }
            TextButton(onClick = { uri.openUri("https://github.com/Override92/AiSList") }) { Text("AiSList source") }
        }
    }
}

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
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = settingsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your app and website share the same server and account. Changing servers signs you out.") }
        item { OutlinedTextField(address, { address = it; error = null }, label = { Text("HTTPS address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), isError = error != null) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { LibraryControlScene { LibraryActionButton("Connect", prominent = true) { try { vm.switchServer(InvidiousApi.normalizeServer(address, BuildConfig.DEBUG)); back() } catch (e: Exception) { error = e.message } } } }
    }
}

@Composable
private fun AboutSettingsScreen(modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = settingsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Mobivious ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall); Text("Powered by Invidious & Companion", Modifier.padding(top = 8.dp)) }
        item { CommunityDataLinks() }
        item {
            val uri = LocalUriHandler.current
            Text("Open source", style = MaterialTheme.typography.titleMedium)
            Text("Glass rendering uses Backdrop 1.0.6 by Kyant, licensed under Apache 2.0.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uri.openUri("https://github.com/Kyant0/AndroidLiquidGlass") }) { Text("Backdrop source & license") }
        }
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
