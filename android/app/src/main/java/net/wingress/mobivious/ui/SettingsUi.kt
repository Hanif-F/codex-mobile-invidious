package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.wingress.mobivious.BuildConfig
import net.wingress.mobivious.data.*

internal val LocalSettingsTopInset = staticCompositionLocalOf { 0.dp }
private val LocalPreferenceFeedback = staticCompositionLocalOf<@Composable (String) -> Unit> { {} }
private val settingsPreferenceKeys = mapOf(
    "Color mode" to "dark_mode", "Video list density" to "ui_density", "Hide thumbnails" to "thin_mode",
    "Autoplay opened videos" to "autoplay", "Next recommendation" to "continue", "Autoplay next video" to "continue_autoplay",
    "Loop videos by default" to "video_loop", "Audio only by default" to "listen", "Proxy video streams" to "local",
    "Default speed" to "speed", "Seek interval" to "seek_seconds", "Preferred video codec" to "video_codec", "Default quality" to "quality_dash",
    "Preferred audio track" to "preferred_audio",
    "Caption priority 1" to "captions", "Caption priority 2" to "captions", "Caption priority 3" to "captions",
    "Default homepage" to "default_home", "Trending region" to "region", "Show related videos" to "related_videos",
    "Expand descriptions by default" to "extend_desc", "Show YouTube comments" to "comments",
    "Show members-only videos" to "show_member_videos", "Videos per page" to "max_results", "Feed sorting" to "sort",
    "Latest video from each channel" to "latest_only", "Unwatched videos only" to "unseen_only",
    "Pending notifications only" to "notifications_only", "Watch history" to "watch_history",
    "Remember playback position" to "save_player_pos", "Default playlist" to "default_playlist",
    "Replace video titles with DeArrow" to "dearrow_enabled", "Show original titles using the DeArrow icon" to "dearrow_show_original",
    "Enable AI Channel Filter" to "ai_filter_enabled"
)

@Composable
internal fun settingsPadding(horizontal: Dp = AppTokens.inset) = PaddingValues(start = horizontal, end = horizontal, top = LocalSettingsTopInset.current + 16.dp, bottom = 32.dp)

@Composable
internal fun SettingsScreen(vm: AppViewModel, page: String, navigate: (String) -> Unit, back: () -> Unit, signIn: () -> Unit, openChannel: (String) -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val preferenceEdits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val context = vm.api.context()
    val accountBusy by vm.accountBusy.collectAsStateWithLifecycle()
    var previousContext by remember { mutableStateOf(context) }
    LaunchedEffect(context) {
        if (previousContext != context && page in accountSettingsPages && page != "Account") navigate("Account")
        previousContext = context
    }
    if (page == "SponsorBlock") {
        key(context) { SponsorBlockSheet(vm, "", back, fullScreen = true) }
        return
    }
    if (page == channelSpoilerSettingsPage) {
        key(context) { ChannelSpoilerSettings(vm, "", back, fullScreen = true) }
        return
    }
    BackHandler { if (!accountBusy) back() }
    BrowseAppSurface(Modifier.fillMaxSize().imePadding(), toolbar = {
        LibraryToolbar(page, back = back, backLabel = "Back from $page", backEnabled = !accountBusy)
    }) { body, top ->
        Box(body, contentAlignment = Alignment.TopCenter) {
          CompositionLocalProvider(LocalSettingsTopInset provides top) {
            val content = Modifier.widthIn(max = if (page in accountSettingsPages || page == "Server") 600.dp else 800.dp).fillMaxSize()
            key(page, context) {
                when (page) {
                    "Settings" -> LazyColumn(content.testTag("settings-root"), contentPadding = settingsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (preferenceEdits.isNotEmpty()) item { AppSection(Modifier.testTag("preference-save-status")) {
                            Text("Preference changes", style = MaterialTheme.typography.titleMedium)
                            preferenceEdits.values.groupBy { it.page }.forEach { (affected, fields) ->
                                ActionRow(affected, modifier=Modifier.testTag("preference-status-$affected"), detail=if(fields.any { it.status in listOf(PreferenceSaveStatus.FAILED,PreferenceSaveStatus.INVALID) })
                                    "Changes need attention" else "Saving…") { navigate(affected) }
                                fields.filter { it.status in listOf(PreferenceSaveStatus.FAILED,PreferenceSaveStatus.INVALID) }.forEach { edit ->
                                    PreferenceSaveFeedback(edit.status,edit.error,{vm.preferenceSaves.retry(edit.key)},{vm.preferenceSaves.revert(edit.key)})
                                }
                            }
                        } }
                        item { Text(account?.let { "Signed in as ${it.username}" } ?: "Preferences for this device and instance", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item { AppSection {
                            SectionHeading("Your account")
                            SettingsLink("Account", "Sign-in, credentials, sessions and API tokens", Icons.Default.AccountCircle, navigate)
                        } }
                        item { AppSection {
                            SectionHeading("Preferences")
                            SettingsLink("Playback", "Speed, quality, audio and captions", Icons.Default.PlayCircle, navigate)
                            SettingsLink("Appearance", "Color mode, accents and video list layout", Icons.Default.Palette, navigate)
                            SettingsLink("Browsing", "Homepage, region and video pages", Icons.Default.Explore, navigate)
                            SettingsLink("Subscriptions", "Feed size, sorting and filters", Icons.Default.Subscriptions, navigate)
                            SettingsLink("History & library", "Watch history, resume and default playlist", Icons.Default.History, navigate)
                            SettingsLink(channelSpoilerSettingsPage, "Hide selected channels’ video titles and thumbnails", Icons.Default.VisibilityOff, navigate)
                        } }
                        item { AppSection {
                            SectionHeading("Community tools")
                            SettingsLink("SponsorBlock", "Segment skipping, colors and channel overrides", Icons.Default.SkipNext, navigate)
                            SettingsLink("DeArrow", "Community titles and contribution identity", Icons.Default.Title, navigate)
                            SettingsLink("AI channel filter", "AiSList warnings and video filtering", Icons.Default.FilterAlt, navigate)
                        } }
                        item { AppSection {
                            SectionHeading("Connection & app")
                            SettingsLink("Server", vm.store.server, Icons.Default.Dns, navigate)
                            SettingsLink("About", "Version and community data", Icons.Default.Info, navigate)
                        } }
                    }
                    "Server" -> ServerSettingsScreen(vm, content, back)
                    "About" -> AboutSettingsScreen(content)
                    "Blocked channels" -> BlockedChannelsScreen(vm, content, signIn, openChannel)
                    in accountSettingsPages -> AccountSettingsContent(vm, page, content, navigate, signIn)
                    else -> PreferenceSettingsScreen(vm, page, prefs, content, signIn, navigate)
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
private fun PreferenceSettingsScreen(vm: AppViewModel, page: String, prefs: AccountPreferences, modifier: Modifier, signIn: () -> Unit, navigate: (String) -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val context = remember { vm.api.context() }
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val background = vm.localPreferenceValue("profile:background",vm.store.background) as Boolean
    val pip = vm.localPreferenceValue("profile:pip",vm.store.pip) as Boolean
    val scope = rememberCoroutineScope()
    val aiState by vm.aiFilter.state.collectAsStateWithLifecycle()
    val searchAssistance by vm.searchEntry.state.collectAsStateWithLifecycle()
    LaunchedEffect(page, context) { if (page == "AI channel filter") vm.aiFilter.refreshStatus() }
    DisposableEffect(page, context) { onDispose { vm.leavePreferencePage(page) } }
    val value = remember(prefs, edits) { vm.preferenceValues() }
    fun update(next: AccountPreferences) { vm.requestPreferences(next, value, page, context) }
    CompositionLocalProvider(LocalPreferenceFeedback provides { label ->
        val key = when(label) {
            "Background playback" -> "profile:background"; "Picture in picture" -> "profile:pip"
            else -> settingsPreferenceKeys[label]
        }
        val edit = edits[key]
        if (edit != null) PreferenceSaveFeedback(edit.status, edit.error,
            { vm.preferenceSaves.retry(edit.key) }, { vm.preferenceSaves.revert(edit.key) })
    }) {
    Box(modifier.fillMaxSize().testTag("settings-category")) {
        LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(start = AppTokens.inset, end = AppTokens.inset,
            top = LocalSettingsTopInset.current + 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page != CaptionAppearance.PAGE) item { AppSection { Text(if (account != null) "Preferences are shared with your Invidious account on the website." else "Preferences are saved on this device for this instance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            when (page) {
                "Caption appearance" -> item { AppSection { CaptionAppearanceEditor(vm) } }
                "AI channel filter" -> {
                    item { AppSection {
                        Column(Modifier.testTag("ai-filter-master")) {
                            SettingsToggle("Enable AI Channel Filter", "Pause filtering and warnings without clearing your choices", value.aiFilter.enabled) { update(value.copy(aiFilter = value.aiFilter.copy(enabled = it))) }
                        }
                        if (!value.aiFilter.enabled) Text("Filtering is paused. Edit the actions, then enable the filter to apply them.", style = MaterialTheme.typography.bodySmall)
                        Text("AiSList is a community classification of channels. Blocklist means high confidence; Warnlist means moderate confidence. Classifications may be incorrect. Invidious does not perform automatic AI detection.", style = MaterialTheme.typography.bodySmall)
                        aiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-status-error")) }
                    } }
                    AiPageGroup.entries.forEach { group -> item { AppSection {
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
                                    value.aiFilter.saved(kind, group).wire) { update(value.copy(aiFilter = value.aiFilter.withAction(kind, group, AiAction.parse(it, group)))) }
                                val actionEdit=edits["ai_${kind.wire}_${group.wire}_action"]
                                PreferenceSaveFeedback(actionEdit?.status,actionEdit?.error,
                                    {actionEdit?.let {vm.preferenceSaves.retry(it.key)}},{actionEdit?.let {vm.preferenceSaves.revert(it.key)}})
                            }
                        }
                    } } }
                    item { AppSection { AiListDetails(aiState) { scope.launch { vm.aiFilter.refreshStatus() } } } }
                }
                "Playback" -> {
                    item { AppSection {
                        SettingsHeading("Playback defaults")
                        SettingsToggle("Autoplay opened videos", "Start playing when you open a video", value.autoplay) { update(value.copy(autoplay = it)) }
                        SettingsToggle("Next recommendation", "Select the next eligible recommendation when a standalone video ends", value.continueNext) { update(value.copy(continueNext = it)) }
                        SettingsToggle("Autoplay next video", "Start successors automatically; opened-video autoplay also enables this", value.continueAutoplay) { update(value.copy(continueAutoplay = it)) }
                        SettingsToggle("Loop videos by default", "Start playback sessions with Repeat One", value.videoLoop) { update(value.copy(videoLoop = it)) }
                        SettingsToggle("Audio only by default", "Disable video tracks when opening a video", value.listen) { update(value.copy(listen = it)) }
                        SettingsToggle("Proxy video streams", "Route streams through your Invidious instance", value.local) { update(value.copy(local = it)) }
                        SettingsChoice("Default speed", PreferenceRules.speeds.map { it.toString() to "${it}×" }, value.speed.toString()) { update(value.copy(speed = it.toFloat())) }
                        SettingsChoice("Seek interval", PreferenceRules.seekIntervals.map { it.toString() to "$it seconds" }, value.seekSeconds.toString()) { update(value.copy(seekSeconds = it.toInt())) }
                        SettingsChoice("Preferred video codec", PreferenceRules.videoCodecs, value.videoCodec) { update(value.copy(videoCodec = it)) }
                        SettingsChoice("Default quality", PreferenceRules.qualities.map { it to PreferenceRules.qualityLabel(it) }, value.qualityDash) { update(value.copy(qualityDash = it)) }
                        SettingsChoice("Preferred audio track", AudioPreferences.choices, value.preferredAudio) { update(value.copy(preferredAudio = it)) }
                        Text("Use original audio if your choice is unavailable. General languages can match regional tracks; a regional choice requires an exact match.", style = MaterialTheme.typography.bodySmall)
                        Text("Seek interval applies to the next seek, in video seconds at any playback speed. Other playback defaults apply to the next video. DASH playback prefers the selected codec when available. A resolution selects the best available stream at or below it, then prefers the codec at that resolution.", style = MaterialTheme.typography.bodySmall)
                    } }
                    item { AppSection {
                        SettingsHeading("On this device")
                        SettingsToggle("Background playback", "Keep playing when the app is in the background", background) { vm.setBackground(it, context) }
                        SettingsToggle("Picture in picture", "Allow the floating player when leaving a video", pip) { vm.setPip(it, context) }
                        Text("These two options save immediately for this account or guest on this device.", style = MaterialTheme.typography.bodySmall)
                    } }
                    item { AppSection {
                        SettingsHeading("Caption language priority")
                        Text("Use the first available language. Set all priorities to None to start with captions off.", style = MaterialTheme.typography.bodySmall)
                        repeat(3) { index ->
                            SettingsChoice("Caption priority ${index + 1}", CaptionLanguages.values.map { it to it.ifBlank { "None" } }, value.captions.getOrElse(index) { "" }) { language ->
                                val languages = MutableList(3) { value.captions.getOrElse(it) { "" } }; languages[index] = language
                                update(value.copy(captions = languages))
                            }
                        }
                        ActionRow("Caption appearance", detail = "Size, colors, opacity, fonts and edges") { navigate("Caption appearance") }
                    } }
                }
                "Appearance" -> item { AppSection {
                    SettingsChoice("Color mode", listOf("" to "System", "light" to "Light", "dark" to "Dark"), value.darkMode) { update(value.copy(darkMode = it)) }
                    val savedAccent by vm.store.accentPreset.collectAsStateWithLifecycle()
                    val accent = AccentPreset.saved(vm.localPreferenceValue("device:accent",savedAccent.id) as String)
                    Text("Accent", style = MaterialTheme.typography.titleMedium)
                    AccentPreset.entries.forEach { preset ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(accent == preset, role = Role.RadioButton, onClick = { vm.requestLocalPreference("device:accent","Appearance",savedAccent.id,preset.id) })
                            .testTag("accent-${preset.id}"), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.padding(12.dp).size(24.dp).background(androidx.compose.ui.graphics.Color(preset.light[0]), androidx.compose.foundation.shape.CircleShape))
                            Text(preset.label, Modifier.weight(1f))
                            RadioButton(accent == preset, null)
                        }
                    }
                    edits["device:accent"]?.let { edit -> PreferenceSaveFeedback(edit.status,edit.error,{vm.preferenceSaves.retry(edit.key)},{vm.preferenceSaves.revert(edit.key)}) }
                    SettingsChoice("Video list density", listOf("balanced" to "Comfortable", "compact" to "Compact"), value.uiDensity) { update(value.copy(uiDensity = it)) }
                    SettingsToggle("Hide thumbnails", "Use a text-only video list", value.thinMode) { update(value.copy(thinMode = it)) }
                } }
                "Browsing" -> {
                    item { AppSection {
                        SettingsHeading("Search on this device")
                        SettingsToggle("Save recent searches", "Keep recent queries for this profile and instance. Turning this off hides them and pauses saving.", searchAssistance.settings.remember) {
                            vm.searchEntry.settings(searchAssistance.settings.copy(remember = it))
                        }
                        SettingsToggle("Live search suggestions", "Request suggestions from your Invidious instance while typing", searchAssistance.settings.suggestions) {
                            vm.searchEntry.settings(searchAssistance.settings.copy(suggestions = it))
                        }
                        Text("Clearing recent searches also removes hidden saved queries.", style = MaterialTheme.typography.bodySmall)
                        key(context) { ClearRecentSearches(vm.searchEntry, searchAssistance.recent.isNotEmpty()) }
                    } }
                    item { AppSection {
                        SettingsToggle("Show members-only videos", "Include members-only content in video lists. This does not grant membership access.", value.showMemberVideos) { update(value.copy(showMemberVideos = it)) }
                        SettingsLink("Blocked channels", "Manage channels hidden from discovery and recommendations", Icons.Default.Block, navigate)
                    } }
                    item { AppSection {
                        val homes = PreferenceRules.homes.filter { account != null || it !in listOf("Subscriptions", "Playlists") }
                        SettingsChoice("Default homepage", homes.map { it to PreferenceRules.homeLabel(it) }, value.defaultHome) { update(value.copy(defaultHome = it)) }
                        Text("Choose where the app opens. Popular, Trending, Subscriptions and You have independent destinations. Signing in returns you to what you were doing.", style = MaterialTheme.typography.bodySmall)
                        RegionChoice(value.region, tag = "settings-region") { update(value.copy(region = it)) }
                        LocalPreferenceFeedback.current("Trending region")
                    } }
                    item { AppSection {
                        SettingsHeading("Video pages")
                        SettingsToggle("Show related videos", "Display recommendations below the video", value.relatedVideos) { update(value.copy(relatedVideos = it)) }
                        SettingsToggle("Expand descriptions by default", "Show the full description when opening a video", value.extendDescription) { update(value.copy(extendDescription = it)) }
                        SettingsToggle("Show YouTube comments", "Show a comments entry on video pages; text stays hidden until opened", value.showYoutubeComments) { enabled ->
                            val others = value.comments.filter { it != "youtube" && it.isNotBlank() }
                            update(value.copy(comments = ((if (enabled) listOf("youtube") else emptyList()) + others).take(2).let { it + List((2 - it.size).coerceAtLeast(0)) { "" } }))
                        }
                    } }
                }
                "Subscriptions" -> {
                    if (account == null) item { AppSection { LibraryEmptyState("Your subscription feed", "Sign in to change your subscription feed.", Icons.Default.Subscriptions, "Sign in", signIn) } }
                    else item { AppSection {
                        SettingsChoice("Videos per page", (listOf(10, 20, 30, 40, 60, 100, 200, 500, 1000, 1500) + value.maxResults).distinct().sorted().map { it.toString() to it.toString() }, value.maxResults.toString()) { update(value.copy(maxResults = it.toInt())) }
                        SettingsChoice("Feed sorting", PreferenceRules.feedSorts.zip(listOf("Newest first", "Oldest first", "Title A–Z", "Title Z–A", "Channel A–Z", "Channel Z–A")), value.feedSort) { update(value.copy(feedSort = it)) }
                        SettingsToggle("Latest video from each channel", "Show one video per subscribed channel", value.latestOnly) { update(value.copy(latestOnly = it)) }
                        SettingsToggle("Unwatched videos only", "Hide videos already in your watch history", value.unseenOnly) { update(value.copy(unseenOnly = it)) }
                        SettingsToggle("Pending notifications only", "Show pending account uploads in this feed", value.notificationsOnly) { update(value.copy(notificationsOnly = it)) }
                        Text("This filters the feed; it does not enable Android alerts. Page size also applies to watch history.", style = MaterialTheme.typography.bodySmall)
                    } }
                }
                "History & library" -> item { AppSection {
                    if (account != null) SettingsToggle("Watch history", "Record played videos in your shared account history", value.watchHistory) { update(value.copy(watchHistory = it)) }
                    else Text("Sign in to keep a shared watch history.", style = MaterialTheme.typography.bodyMedium)
                    SettingsToggle("Remember playback position", if (account != null) "Resume across Android and the website" else "Resume videos on this device", value.savePosition) { update(value.copy(savePosition = it)) }
                    if (account != null) {
                        SettingsChoice("Default playlist", listOf("" to "None") + playlists.filter { it.owned }.map { it.id to it.title }, value.defaultPlaylist) { update(value.copy(defaultPlaylist = it)) }
                        Text("The default playlist appears first when saving a video.", style = MaterialTheme.typography.bodySmall)
                    }
                } }
                "DeArrow" -> {
                    item { AppSection {
                        SettingsToggle("Replace video titles with DeArrow", "Use community titles throughout browsing and playback", value.dearrowEnabled) { update(value.copy(dearrowEnabled = it)) }
                        SettingsToggle("Show original titles using the DeArrow icon", "Keep original titles available on each video", value.dearrowShowOriginal) { update(value.copy(dearrowShowOriginal = it)) }
                    } }
                    if (account != null) item { AppSection { DeArrowIdentitySettings(vm) } }
                    item { AppSection { CommunityDataLinks() } }
                }
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
private fun SettingsToggle(label: String, detail: String, value: Boolean, enabled: Boolean = true, update: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).appItemFeedback(interaction)
        .toggleable(value, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = update).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(label, style = MaterialTheme.typography.bodyLarge); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(value, null, enabled = enabled)
    }
    LocalPreferenceFeedback.current(label)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsChoice(label: String, choices: List<Pair<String, String>>, selected: String, enabled: Boolean = true, update: (String) -> Unit) {
    Column {
        DropdownChoiceRow(label, choices, selected, enabled = enabled, change = update)
        LocalPreferenceFeedback.current(label)
    }
}

@Composable
private fun ServerSettingsScreen(vm: AppViewModel, modifier: Modifier, back: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(vm.store.server) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var candidate by rememberSaveable {mutableStateOf<String?>(null)}
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = settingsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppSection {
            Text("Your app and website share the same server and account. Changing servers signs you out.")
            OutlinedTextField(address, { address = it; error = null }, label = { Text("HTTPS address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LibraryActionButton("Connect", prominent=true) {
                try {
                    val normalized=InvidiousApi.normalizeServer(address,BuildConfig.ALLOW_FIXTURE_HTTP)
                    if(normalized==vm.store.server) back() else candidate=normalized
                } catch(e: Exception) {error=e.message}
            }
        } }
    }
    if(candidate!=null) AlertDialog(tonalElevation=3.dp,onDismissRequest={candidate=null},title={Text("Change server?")},
        text={Text("Connect to $candidate? This signs you out and closes playback. Unconfirmed preference changes will be discarded. Downloads stay on this device.")},
        confirmButton={TextButton({
            val target=candidate ?: return@TextButton
            try { vm.preferenceSaves.discard();vm.switchServer(target);candidate=null;back() }
            catch(e: Exception) {candidate=null;error=e.message}
        }) {Text("Connect")}},dismissButton={TextButton({candidate=null}) {Text("Stay")}},
        containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)

}

@Composable
private fun AboutSettingsScreen(modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = settingsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppSection { Text("Mobivious ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall); Text("Powered by Invidious & Companion", Modifier.padding(top = 8.dp)) } }
        item { AppSection { CommunityDataLinks() } }
        item { AppSection {
            val uri = LocalUriHandler.current
            Text("Open source", style = MaterialTheme.typography.titleMedium)
            Text("Mobivious is open source.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uri.openUri("https://github.com/Hanif-F/codex-mobile-invidious") }) { Text("App source & license") }
        } }
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
