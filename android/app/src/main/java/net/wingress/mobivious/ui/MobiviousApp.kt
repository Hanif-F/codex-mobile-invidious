package net.wingress.mobivious.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.wingress.mobivious.MainActivity
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MobiviousApp(vm: AppViewModel, activity: MainActivity, pip: Boolean, shared: MutableState<Boolean>) {
    val downloadDialog by vm.downloadDialog.collectAsStateWithLifecycle()
    val state by vm.browse.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val accountBusy by vm.accountBusy.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val channelTab by vm.channelTab.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val searchType by vm.searchType.collectAsStateWithLifecycle()
    val channelPlaylistSort by vm.channelPlaylistSort.collectAsStateWithLifecycle()
    val channelVideoSort by vm.channelVideoSort.collectAsStateWithLifecycle()
    val postDetail by vm.postDetail.collectAsStateWithLifecycle()
    val postNavigation by vm.postNavigation.collectAsStateWithLifecycle()
    val postComments by vm.postComments.collectAsStateWithLifecycle()
    val chat by vm.chatReplay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val linkResolution by vm.linkResolution.collectAsStateWithLifecycle()
    val clipEditor by vm.clipEditor.collectAsStateWithLifecycle()
    val clipOpened by vm.clipOpened.collectAsStateWithLifecycle()
    val subscriptionChannels by vm.subscriptionChannels.collectAsStateWithLifecycle()
    val deviceContext = vm.api.context()
    val subscriptions = subscriptionChannels.takeIf { it.context == vm.api.context() }?.channels.orEmpty()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val reduceTransparency by vm.store.reduceTransparency.collectAsStateWithLifecycle()
    val sponsorEditor by vm.sponsorSettingsChannel.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val searchVisibility by vm.searchVisibility.collectAsStateWithLifecycle()
    val aiState by vm.aiFilter.state.collectAsStateWithLifecycle()
    val search by vm.searchInput.collectAsStateWithLifecycle()
    val scopedSearch by vm.scopedSearch.collectAsStateWithLifecycle()
    val selectedTab by vm.navigation.collectAsStateWithLifecycle()
    val tab = selectedTab.first
    val route = selectedTab.second
    val destination = ShellDestination.from(tab)
    var lastDiscover by rememberSaveable { mutableStateOf("Popular") }
    LaunchedEffect(tab) { if (PreferenceRules.isDiscovery(tab)) lastDiscover = tab }
    val browseReset by vm.browseReset.collectAsStateWithLifecycle()
    val browseList = key(selectedTab) {
        val position = state.position
        rememberLazyListState(position.index, position.offset)
    }
    val subscriptionList = key(vm.api.context()) { rememberLazyListState() }
    var handledBrowseReset by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(browseReset) {
        if (browseReset > handledBrowseReset) { browseList.scrollToItem(0); handledBrowseReset = browseReset }
    }
    val visibleVideos = vm.visibleVideos(state.videos, aiState = aiState)
    var channelDescriptionOpen by rememberSaveable(vm.store.server, channel?.id, route) { mutableStateOf(false) }
    val presentation = rememberPlayerPresentation()
    val watch = presentation.watch
    val fullscreen = presentation.fullscreen
    val queue by vm.queue.collectAsStateWithLifecycle()
    val chapters = playback.chapters
    val chapterPanel = key(vm.store.server, queue.token, queue.currentKey) { rememberChapterPanelState() }
    val watchList = key(queue.currentKey) { rememberLazyListState() }
    val watchResize = key(queue.currentKey) { rememberWatchPlayerResizeState() }
    val watchDefaults = queue.effective(prefs)
    var watchDescription by rememberSaveable(queue.currentKey, watchDefaults.extendDescription) { mutableStateOf(watchDefaults.extendDescription) }
    fun browsePlayer() = presentation.present(if (playback.details != null) PlayerPresentation.MINI else PlayerPresentation.CLOSED, animate = false)
    fun restorePlayer() { vm.cancelAccumulatedSeek(); presentation.present(PlayerPresentation.WATCH) }
    // Loading another occurrence cancels gesture motion. New playback must enter
    // watch directly, so that cancellation cannot strand it in the mini-player.
    fun openPlayer() { vm.cancelAccumulatedSeek(); presentation.present(PlayerPresentation.WATCH, animate = false) }
    fun collapsePlayer() {
        vm.cancelAccumulatedSeek()
        if (presentation.fullscreen) presentation.present(PlayerPresentation.WATCH)
        else { vm.closeComments(); presentation.present(PlayerPresentation.MINI) }
    }
    fun closePlayer() { presentation.present(PlayerPresentation.CLOSED, animate = false); vm.closePlayer() }
    fun toggleFullscreen() {
        vm.cancelAccumulatedSeek()
        presentation.present(if (presentation.fullscreen) PlayerPresentation.WATCH else PlayerPresentation.FULLSCREEN)
    }
    var handledClipOpened by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(clipOpened) { if (clipOpened > handledClipOpened) { handledClipOpened = clipOpened; openPlayer() } }
    var previousQueueToken by remember { mutableStateOf(queue.token) }
    LaunchedEffect(queue.token, playback.details?.video?.id) {
        if (queue.token.isEmpty() && previousQueueToken.isNotEmpty()) presentation.present(PlayerPresentation.CLOSED, animate = false)
        else if (playback.details != null && presentation.mode == PlayerPresentation.CLOSED && queue.token.isNotEmpty())
            presentation.present(PlayerPresentation.MINI, animate = false)
        previousQueueToken = queue.token
    }
    var dialog by remember { mutableStateOf("") }
    fun openChapters() {
        vm.cancelAccumulatedSeek(); vm.closeComments(); vm.closeChat(); dialog = ""
        chapterPanel.show(chapters, playback.position)
    }
    fun toggleChat() { if (vm.chatReplay.value.open) vm.closeChat() else { chapterPanel.close(); dialog = ""; vm.openChat() } }
    fun backWatch() { if (chat.open) vm.closeChat() else if (chapterPanel.open) chapterPanel.close() else if (!vm.backComments()) collapsePlayer() }
    LaunchedEffect(chapters, playback.loading) { if (!playback.loading && chapters.isEmpty()) chapterPanel.close() }
    LaunchedEffect(dialog) { if (dialog.isNotEmpty()) chapterPanel.close() }
    var settingsPage by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchDraft by rememberSaveable { mutableStateOf("") }
    var originIndex by rememberSaveable { mutableIntStateOf(0) }
    var originOffset by rememberSaveable { mutableIntStateOf(0) }
    var originWatch by rememberSaveable { mutableStateOf(false) }
    var authSettings by rememberSaveable { mutableStateOf("") }
    var authWatchId by rememberSaveable { mutableStateOf("") }
    var authWatchPosition by rememberSaveable { mutableLongStateOf(0L) }
    var navigationServer by rememberSaveable { mutableStateOf(vm.store.server) }
    var handledPostNavigation by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(postNavigation) {
        if (postNavigation > handledPostNavigation) {
            handledPostNavigation = postNavigation
            searchOpen = false; settingsPage = ""; browsePlayer()
        }
    }
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
    var observedNavigation by remember { mutableStateOf(selectedTab) }
    LaunchedEffect(selectedTab, restoredBrowse, authenticationFinished, shared.value) {
        // StateFlow collection can lag the shared-video event by one frame. Consume
        // the event only after its destination is visible so a late tab update
        // cannot immediately minimize the player we just opened.
        if (selectedTab != vm.navigation.value) return@LaunchedEffect
        val openingVideo = shared.value
        if (selectedTab != observedNavigation) { if (!openingVideo) browsePlayer(); observedNavigation = selectedTab }
        if (restoredBrowse > handledRestore) {
            val position = vm.browse.value.position
            browseList.scrollToItem(position.index, position.offset); handledRestore = restoredBrowse
            if (openingVideo) openPlayer() else if (originWatch) restorePlayer() else browsePlayer(); originWatch = false
            if (account == null) {
                authWatchId = ""
                if (authSettings.isNotEmpty()) { settingsPage = authSettings; authSettings = "" }
            }
        }
        if (authenticationFinished > handledAuth) {
            handledAuth = authenticationFinished; settingsPage = authSettings; authSettings = ""
            if (authWatchId.isNotEmpty()) { vm.openLink(VideoLink(authWatchId, authWatchPosition)); openPlayer(); authWatchId = "" }
        }
        if (openingVideo) { openPlayer(); settingsPage = ""; shared.value = false }
    }
    LaunchedEffect(browseList, selectedTab, browseReset) {
        snapshotFlow { CommentPosition(browseList.firstVisibleItemIndex, browseList.firstVisibleItemScrollOffset) }.collect { position ->
            if (vm.restoredBrowse.value == handledRestore) vm.browsePosition(position)
        }
    }
    val saveSheet by vm.saveSheet.collectAsStateWithLifecycle()
    val contribution by vm.dearrowContribution.collectAsStateWithLifecycle()
    val rss by vm.rss.collectAsStateWithLifecycle()
    val blockUndo by vm.blockUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun navigate(selected: String, path: String = "") { searchOpen = false; keyboard?.hide(); vm.cancelAccumulatedSeek(); browsePlayer(); vm.navigate(selected, path) }
    fun openRichLink(raw: String) {
        val target = ContentLinks.resolve(raw, vm.store.server) ?: return
        channelDescriptionOpen = false; vm.closePostComments()
        if (target is ContentLink.External) openExternalContent(context, target.url) { vm.message.value = "No app could open this link." }
        else if (vm.openContent(target)) openPlayer() else browsePlayer()
    }
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
        val link = ContentLinks.parse(searchDraft, vm.store.server)
        if (link != null) { if (vm.openContent(link)) openPlayer() else browsePlayer() }
        else { browsePlayer(); vm.openGlobalSearch(searchDraft) }
    }
    fun selectDestination(selected: ShellDestination) {
        vm.browsePosition(CommentPosition(browseList.firstVisibleItemIndex, browseList.firstVisibleItemScrollOffset))
        originWatch = watch; authWatchId = ""; authSettings = ""; vm.cancelAccumulatedSeek(); browsePlayer()
        if (selected == ShellDestination.SEARCH) {
            originIndex = browseList.firstVisibleItemIndex; originOffset = browseList.firstVisibleItemScrollOffset
            searchDraft = search.submitted; vm.beginGlobalSearch(); searchOpen = true
        } else {
            searchOpen = false; keyboard?.hide()
            vm.selectTab(when (selected) { ShellDestination.DISCOVER -> lastDiscover; ShellDestination.SUBSCRIPTIONS -> "Subscriptions"; else -> "You" })
        }
    }
    var dockCompact by remember { mutableStateOf(false) }
    val exploring = rememberPlayerTouchExploration()
    LaunchedEffect(browseList, exploring, selectedTab) {
        dockCompact = false
        var previous = CommentPosition(browseList.firstVisibleItemIndex, browseList.firstVisibleItemScrollOffset)
        snapshotFlow { CommentPosition(browseList.firstVisibleItemIndex, browseList.firstVisibleItemScrollOffset) }.collect { next ->
            if (!exploring) {
                val direction = if (next.index != previous.index) (next.index - previous.index) * 100 else next.offset - previous.offset
                if (next.index == 0 && next.offset < 64 || direction < -8) dockCompact = false
                else if (direction > 12) dockCompact = true
            }
            previous = next
        }
    }
    fun play(video: Video, source: Playlist? = null) { openPlayer(); vm.playVideo(video, source) }
    LaunchedEffect(message, blockUndo) { message?.let {
        val undo = blockUndo.takeIf { _ -> it == "Channel blocked" }
        val result = snackbar.showSnackbar(it, actionLabel = undo?.let { "Undo" }, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed && undo != null) vm.undoBlock(undo)
        if (vm.message.value == it) vm.message.value = null
    } }
    LaunchedEffect(watch, playback.playWhenReady, playback.error, playback.details, playback.geometry, prefs, vm.store.pip, settingsPage, clipEditor.open) { activity.updatePip(watch && settingsPage.isEmpty() && !clipEditor.open) }
    DisposableEffect(fullscreen, pip, playback.geometry.orientation) {
        activity.requestedOrientation = if (fullscreen && !pip) when (playback.geometry.orientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            -1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.setFullscreen(fullscreen && !pip)
        onDispose { activity.setFullscreen(false); activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    LaunchedEffect(pip, watch, playback.mediaId) { if (pip || !watch) { chapterPanel.close(); vm.closeComments(); vm.cancelAccumulatedSeek(); dialog = ""; vm.closeDeArrow() }; if (pip) vm.sponsorSettingsChannel.value = null }
    LaunchedEffect(account) { dialog = "" }
    LaunchedEffect(watch, pip, settingsPage) { if (watch || pip || settingsPage.isNotEmpty()) {
        channelDescriptionOpen = false; vm.closePostComments()
    } }
    LaunchedEffect(settingsPage) { if (settingsPage.isNotEmpty()) vm.cancelAccumulatedSeek(); if (settingsPage == "Settings") vm.refreshSharedSettings() }
    BackHandler(settingsPage.isEmpty() && (fullscreen || watch || route.isNotBlank() || vm.hasBrowseBack)) { when { fullscreen -> { if (chat.open) vm.closeChat() else if (chapterPanel.open) chapterPanel.close() else collapsePlayer() }; watch -> backWatch(); else -> { vm.cancelAccumulatedSeek(); vm.backBrowse() } } }
    BackHandler(searchOpen) { searchOpen = false; keyboard?.hide() }
    BackHandler(accountBusy) { }
    val keyboardVisible = WindowInsets.isImeVisible
    val darkTheme = prefs.darkMode == "dark" || prefs.darkMode.isBlank() && isSystemInDarkTheme()
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = if (darkTheme) Liquid.dark else Liquid.light, typography = Liquid.typography, shapes = Liquid.shapes) {
      val backdrop = rememberLayerBackdrop()
      CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalReduceTransparency provides reduceTransparency) {
        Surface(Modifier.fillMaxSize()) {
          PlayerPresentationHost(vm, playback, controller, presentation, pip, settingsPage.isNotEmpty() && !pip || clipEditor.open || keyboardVisible && !pip && !watch,
            downloadDialog.video != null || fullscreen && chapterPanel.open || dialog.isNotEmpty() || sponsorEditor != null || saveSheet.video != null || contribution.open || rss.open || searchOpen || accountBusy || channelDescriptionOpen || postComments.open || linkResolution.link != null || clipEditor.open,
            queue.currentKey, ::closePlayer, ::collapsePlayer, ::restorePlayer, ::toggleFullscreen, { chapterPanel.close(); dialog = "player" }, ::openChapters, ::toggleChat) { openChatSettings ->
            if (settingsPage.isNotEmpty() && !pip) SettingsScreen(vm, settingsPage, { settingsPage = it }, { settingsPage = settingsParent(settingsPage) }, { signIn() }, { id -> settingsPage = ""; navigate("Popular", "channel:$id") })
            else BoxWithConstraints(Modifier.fillMaxSize()) {
              val wideLayout = maxWidth >= 840.dp
              val shellStart = if (wideLayout && !watch) 204.dp else 0.dp
              val imeVisible = keyboardVisible
              val edgeColor = MaterialTheme.colorScheme.background
              Scaffold(
                modifier = Modifier.padding(start = shellStart).hiddenPlayerContent(pip || fullscreen && !presentation.active),
                containerColor = Color.Transparent,
                topBar = {
                    if (!watch && !fullscreen) LiquidTopBar(
                        title = when {
                            route == "sign-in" -> "Sign in"
                            route == "subscription-channels" -> "Channels"
                            route.isNotEmpty() -> channel?.name ?: playlist?.title ?: state.title
                            else -> destination.label
                        },
                        large = route.isEmpty(),
                        navigation = if (route.isNotEmpty() || vm.hasBrowseBack && tab != "Search") ({
                            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", enabled = !accountBusy) { vm.cancelAccumulatedSeek(); vm.backBrowse() }
                        }) else null,
                    ) {
                        if (account != null && route.isEmpty() && tab == "Subscriptions") {
                            OverflowMenu("Subscription actions", vm.api.context(), Modifier.testTag("subscription-actions")) { close ->
                                DropdownMenuItem(text = { Text("RSS") }, onClick = { close(); vm.openSubscriptionRss() })
                                DropdownMenuItem(text = { Text("Export OPML") }, onClick = { close(); vm.openOpml() })
                            }
                        }
                        if (route == "history") OverflowMenu("History actions", vm.api.context(), Modifier.testTag("history-actions")) { close ->
                            DropdownMenuItem(text = { Text("Clear watch history") }, onClick = { close(); dialog = "clearHistory" })
                        }
                        if (tab == "You" && route.isEmpty()) GlassIconButton(Icons.Default.Settings, "Settings",
                            Modifier.testTag("global-settings"), enabled = !accountBusy) { vm.settingsOpened(); settingsPage = "Settings" }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column(Modifier.drawBehind {
                        if (!watch) drawRect(Brush.verticalGradient(listOf(Color.Transparent, edgeColor.copy(alpha = .75f)),
                            startY = -24.dp.toPx(), endY = size.height), topLeft = Offset(0f, -24.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height + 24.dp.toPx()))
                    }.navigationBarsPadding().imePadding()
                        .graphicsLayer { alpha = if (watch) presentation.miniAlpha else 1f }
                        .hiddenPlayerContent(watch || presentation.active || pip || fullscreen)) {
                        if (presentation.mode != PlayerPresentation.CLOSED && playback.details != null && (!imeVisible || watch))
                            MiniPlayer(vm, playback, presentation, ::restorePlayer, vm::togglePlay, ::closePlayer,
                                gesturesEnabled = !pip && dialog.isEmpty() && sponsorEditor == null && !searchOpen && !accountBusy && saveSheet.video == null && !contribution.open && !rss.open && !channelDescriptionOpen && !postComments.open && linkResolution.link == null)
                        if (!watch || presentation.active) {
                            if (tab == "Search" && route.isEmpty()) Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).liquidGlass().padding(start = 4.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                SearchField(searchDraft, { searchDraft = it }, "Search or paste a link", "main-search",
                                    Modifier.weight(1f).focusRequester(searchFocus)) { submitGlobalSearch() }
                                IconButton(onClick = { searchOpen = false; keyboard?.hide(); if (vm.hasBrowseBack) vm.backBrowse() else vm.selectTab(lastDiscover) }) {
                                    Icon(Icons.Default.Close, "Close search")
                                }
                            }
                            else if (!wideLayout) FloatingNavigation(destination, dockCompact, !accountBusy,
                                Modifier.playerAnchor { presentation.navigationBounds = it }, ::selectDestination)
                        }
                    }
                }
            ) { padding ->
              CompositionLocalProvider(LocalContentBottomInset provides (padding.calculateBottomPadding() + 20.dp)) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                  // Content controls must not sample the layer that records them.
                  // Only the sibling floating chrome uses the shell's live backdrop.
                  CompositionLocalProvider(LocalGlassBackdrop provides null) {
                if (route == "sign-in") SignInScreen(vm, Modifier.padding(padding).hiddenPlayerContent(watch || presentation.active || pip))
                else Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().hiddenPlayerContent(watch || presentation.active || pip)) {
                    if(offline) Text("Offline · showing saved results", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    if (account != null && blocked.error != null && vm.contentSurface() in listOf(ContentSurface.DISCOVERY, ContentSurface.SEARCH)) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(blocked.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { settingsPage = "Blocked channels" }) { Text("Manage / retry blocked channels") }
                        }
                    }
                    if (route.isEmpty() && tab == "Search") Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { dialog = "filters" }) { Icon(Icons.Default.Tune, "Search filters") }
                        SearchType.entries.forEach { type ->
                            FilterChip(selected = searchType == type, onClick = { vm.setSearchType(type) }, label = { Text(type.label) },
                                modifier = Modifier.testTag("search-type-${type.apiValue}"))
                        }
                    }
                    if (route.isEmpty() && tab == "Subscriptions") Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your latest uploads", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (route.isEmpty() && PreferenceRules.isDiscovery(tab)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Liquid.inset, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LiquidSegments(listOf("Popular", "Trending"), tab, { it }, Modifier.weight(1f).testTag("discover-feeds"),
                                tag = { "discover-$it" }, select = vm::selectTab)
                            GlassIconButton(Icons.Default.Refresh, "Refresh $tab", Modifier.testTag("feed-refresh"), onClick = vm::refresh)
                        }
                        Text(if (tab == "Trending") "Trending in ${prefs.region}" else "Popular across your instance",
                            Modifier.padding(horizontal = Liquid.inset, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (tab == "Trending") key(deviceContext) { TrendingControls(vm, prefs.region) }
                    }
                    if (route == "downloads") DownloadedScreen(vm, browseList) { id -> openPlayer(); vm.playDownload(id) }
                    else if (route.startsWith("post:")) PostDetailScreen(vm, postDetail, browseList,
                        { id -> navigate(tab, "channel:$id") }, ::openRichLink, { play(it) },
                        { list -> browsePlayer(); vm.openPlaylist(list) }, { signIn() })
                    else if (route == "subscription-channels") {
                        if (account == null) EmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", "Sign in") { signIn() }
                        else SubscriptionChannelsScreen(subscriptionChannels.takeIf { it.context == vm.api.context() } ?: SubscriptionChannelsState(), vm.store.server, prefs.thinMode,
                            subscriptionList, { query -> vm.searchSubscriptionChannels(query); scope.launch { subscriptionList.scrollToItem(0) } }, vm::refreshSubscriptions,
                            { id -> navigate("Subscriptions", "channel:$id") },
                            { sort -> vm.sortSubscriptionChannels(sort, deviceContext); scope.launch { subscriptionList.scrollToItem(0) } })
                    }
                    else if (route == "clips" && account == null) EmptyState("My Clips", "Sign in to create and find your clips.", "Sign in") { signIn() }
                    else if (route.isEmpty() && tab == "You" && account == null) GuestYouScreen(vm.store.server, browseList, { navigate("You", "downloads") }) { signIn() }
                    else if (route.isEmpty() && tab == "Subscriptions" && account == null) EmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", "Sign in") { signIn() }
                    else if (tab == "You" && route.isEmpty()) LazyColumn(Modifier.fillMaxSize().testTag("you-library-list"), state = browseList, contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset, top = 8.dp, bottom = LocalContentBottomInset.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { YouIdentity(account, vm.store.server) }
                        item { DownloadShortcut { navigate("You", "downloads") } }
                        item { LibraryShortcuts({ navigate("You", "history") }, { vm.navigate("You", "clips", rememberOrigin = true) }) }
                        item {
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                Text("My playlists (${playlists.count { it.owned }})", style = MaterialTheme.typography.titleLarge)
                                FilledTonalButton(onClick = { dialog = "create" }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New playlist") }
                            }
                        }
                        items(playlists.filter { it.owned }, key = { "owned:${it.id}" }) { list -> PlaylistCard(vm, list, { browsePlayer(); vm.openPlaylist(list) }, { signIn() }) }
                        if (playlists.none { it.owned }) item { Text("Create a playlist to save your videos.") }
                        item { Text("Subscribed playlists (${playlists.count { !it.owned && it.saved }})", style = MaterialTheme.typography.titleLarge) }
                        items(playlists.filter { !it.owned && it.saved }, key = { "subscribed:${it.id}" }) { list -> PlaylistCard(vm, list, { browsePlayer(); vm.openPlaylist(list) }, { signIn() }) }
                        if (playlists.none { !it.owned && it.saved }) item { Text("Subscribe to playlists or mixes from search, a channel, or a shared link.") }
                        if (state.loading) item { CircularProgressIndicator() }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::refresh) }
                    }
                    else LazyColumn(Modifier.fillMaxSize().testTag("browse-video-list"), state = browseList, contentPadding = PaddingValues(bottom = LocalContentBottomInset.current)) {
                        if (route == "clips") item {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Most recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!state.loading && state.clips.isEmpty() && state.error == null) Text("Save a moment worth sharing. Open a video and choose More → Create clip.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (channelTab == ChannelTab.CLIPS && scopedSearch.submitted.isBlank() && !state.loading && state.clips.isEmpty() && state.error == null) item {
                            Text("No clips yet. Clips made on videos from this channel will appear here.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (route.startsWith("hashtag:")) item { Text(state.title, Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium) }
                        channel?.let { info -> item {
                            ChannelHeader(info, vm.store.server, prefs.thinMode, subscriptions.any { it.id == info.id }, actions = {
                                OverflowMenu("Actions for channel ${info.name}", listOf(info.id, vm.api.context()), Modifier.testTag("channel-actions-${info.id}")) { close ->
                                    DropdownMenuItem(text = { Text("RSS") }, onClick = { close(); vm.openChannelRss(info) })
                                    BlockChannelMenuItem(vm, info.id, info.name, { signIn() }, Modifier.testTag("channel-block-${info.id}"), close)
                                    DropdownMenuItem(text = { Text("Channel SponsorBlock settings") }, onClick = { close(); vm.openSponsorBlock(info.id) })
                                }
                            }, readDescription = { channelDescriptionOpen = true }) {
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
                        if (channelTab?.videoTab == true && channel?.ageGated == false && scopedSearch.submitted.isBlank()) item {
                            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChannelSort.entries.forEach { sort -> FilterChip(selected = channelVideoSort == sort,
                                    onClick = { vm.setChannelVideoSort(sort) }, label = { Text(sort.label) }, modifier = Modifier.testTag("channel-sort-${sort.apiValue}")) }
                            }
                        }
                        if (channelTab == ChannelTab.PLAYLISTS && scopedSearch.submitted.isBlank()) item {
                            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("last" to "Last added", "newest" to "Newest", "oldest" to "Oldest").forEach { (key, label) ->
                                    FilterChip(selected = channelPlaylistSort == key, onClick = { vm.setChannelPlaylistSort(key) }, label = { Text(label) })
                                }
                            }
                        }
                        playlist?.let { list -> item {
                            PlaylistHeader(vm, list, { openPlayer(); vm.play("", source = list.id, seed = list.seedVideoId) },
                                { dialog = "edit" }, { dialog = "deletePlaylist" }, { signIn() })
                        } }
                        val generalSearch = tab == "Search" && route.isEmpty()
                        if (generalSearch) items(vm.visibleSearchResults(state.searchResults), key = { "search:${it.key}" }) { result ->
                            when (result) {
                                is SearchResult.VideoItem -> VideoCard(vm, result.video, vm.store.server, { play(result.video) },
                                    { id -> navigate(tab, "channel:$id") }, { signIn() },
                                    audioPlay = { openPlayer(); vm.playVideo(result.video, audio = true) })
                                is SearchResult.ChannelItem -> RelatedChannelCard(result.channel, vm.store.server, prefs.thinMode) {
                                    navigate(tab, "channel:${result.channel.id}")
                                }
                                is SearchResult.PlaylistItem -> PlaylistCard(vm, result.playlist,
                                    { browsePlayer(); vm.openPlaylist(result.playlist) }, { signIn() })
                            }
                        }
                        if (!generalSearch) items(vm.visiblePlaylists(state.lists), key = { "result:${it.id}" }) { list ->
                            PlaylistCard(vm, list, { browsePlayer(); vm.openPlaylist(list) }, { signIn() })
                        }
                        items(state.posts, key = { "post:${it.key}" }) { post ->
                            CommunityPostCard(vm, post, comments = { vm.openPostComments(post) },
                                channel = { id -> navigate(tab, "channel:$id") }, link = ::openRichLink, play = { play(it) },
                                playlist = { list -> browsePlayer(); vm.openPlaylist(list) }, signIn = { signIn() })
                        }
                        items(state.clips, key = { "clip:${it.id}" }) { clip ->
                            ClipCard(vm, clip, { vm.watchClip(clip); openPlayer() })
                        }
                        if (!generalSearch) items(state.channels, key = { "channel:${it.id}" }) { related ->
                            RelatedChannelCard(related, vm.store.server, prefs.thinMode) { navigate(tab, "channel:${related.id}") }
                        }
                        if (route == "history") item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(state.history?.total?.let { DisplayFormats.inventory(it.toLong(), "video") } ?: "Recently watched", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge) } }
                        if (route.isEmpty() && tab == "Subscriptions" && subscriptions.isNotEmpty()) item {
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(subscriptions, key = { it.id }) { c -> SubscriptionChannelChip(c, vm.store.server, prefs.thinMode) { navigate("Subscriptions", "channel:${c.id}") } }
                            }
                        }
                        val groups = if (generalSearch) emptyMap() else if (route == "history" && state.history?.organized == true) visibleVideos.groupBy { History.group(it.history?.watched, state.history?.today) } else mapOf(null to visibleVideos)
                        groups.forEach { (group, videos) ->
                            if (group != null) item(key = "history-group-$group") { Text(group.label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("history-group-${group.name}"), style = MaterialTheme.typography.titleLarge) }
                            val rows = videos.chunked(if (wideLayout && route.isEmpty() && PreferenceRules.isDiscovery(tab) && prefs.uiDensity != "compact") 2 else 1)
                            items(rows, key = { row -> row.joinToString { it.id + it.indexId + (it.playlistIndex?.toString() ?: "") } }) { row ->
                              Row(Modifier.fillMaxWidth()) {
                                row.forEach { video -> Box(Modifier.weight(1f)) { VideoCard(vm, video, vm.store.server, { play(video, playlist) }, { id -> navigate(tab, "channel:$id") }, { signIn() }, if (playlist?.let { list -> list.owned } == true || route == "history") ({
                                val list = playlist
                                if (list != null) vm.removePlaylistVideo(list, video)
                                else vm.removeHistory(video.id)
                            }) else null, audioPlay = { openPlayer(); vm.playVideo(video, playlist, audio = true) }, removalLabel = if (playlist != null) "Remove from playlist" else "Remove from history",
                                avatarOwner = channel?.id.takeIf { route.startsWith("channel:") && channelTab?.videoTab == true }) } }
                                if (row.size == 1 && wideLayout && route.isEmpty() && PreferenceRules.isDiscovery(tab) && prefs.uiDensity != "compact") Spacer(Modifier.weight(1f))
                              }
                            }
                        }
                        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::retryBrowse); if (state.videos.isEmpty() && state.searchResults.isEmpty()) OutlinedButton(onClick = { dialog = ""; settingsPage = "Server" }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Configure server") } }
                        if (!state.loading && (if (generalSearch) vm.visibleSearchResults(state.searchResults).isEmpty() && state.searchResults.isNotEmpty() else visibleVideos.isEmpty() && state.videos.isNotEmpty())) item {
                            Column(Modifier.padding(16.dp)) {
                                Text(if (generalSearch) "Results hidden by your visibility settings" else "Videos hidden by your visibility settings")
                                Text("Change the filters or load another page to find visible videos.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { if (vm.contentSurface() == ContentSurface.SEARCH) dialog = if (tab == "Subscriptions") "visibilityFilters" else "filters" else settingsPage = "Browsing" }) { Text("Visibility controls") }
                            }
                        }
                        if (!state.loading && state.error == null && state.videos.isEmpty() && state.lists.isEmpty() && state.posts.isEmpty() && state.channels.isEmpty() &&
                            state.clips.isEmpty() && route != "clips" && !(channelTab == ChannelTab.CLIPS && scopedSearch.submitted.isBlank())) item { EmptyState(
                            if (scopedSearch.submitted.isNotBlank() || generalSearch && search.submitted.isNotBlank()) "No matches" else if (route == "history") "Your history is empty" else if (tab == "Search") "Find something to watch" else "Nothing here yet",
                            if (scopedSearch.submitted.isNotBlank() || generalSearch && search.submitted.isNotBlank()) "Try another query or change the filters." else if (route == "history") "Videos you watch with history enabled will appear here." else if (tab == "Search") "Search for videos, channels and playlists, or paste a link." else "Refresh to check for videos.", if (tab == "Search" && search.submitted.isBlank()) "" else "Refresh", vm::refresh) }
                        if (!state.end && !state.loading && state.error == null) item { OutlinedButton(onClick = vm::more, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
                    }
                }
                if (presentation.showsWatch && !pip) {
                    val miniPadding = with(LocalDensity.current) { if (presentation.allocatesMini) presentation.miniHeight.toDp() else 0.dp }
                    val layoutDirection = LocalLayoutDirection.current
                    val watchPadding = PaddingValues(start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(), end = padding.calculateEndPadding(layoutDirection),
                        bottom = (padding.calculateBottomPadding() - miniPadding).coerceAtLeast(0.dp))
                    WatchScreen(vm, playback, Modifier.padding(watchPadding).graphicsLayer { alpha = presentation.watchAlpha }
                        .hiddenPlayerContent(!watch || presentation.active),
                        presentation, watchList, watchResize, chapterPanel, ::openChapters, watchDescription, { watchDescription = it }, ::closePlayer, vm::openSave,
                        { id -> navigate("Popular", "channel:$id") }, { play(it) }, { signIn() }, openChatSettings)
                }
                }
                }
              }
            }
              if (wideLayout && !watch && !fullscreen && !pip) FloatingSidebar(destination, !accountBusy,
                  Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 14.dp), ::selectDestination)
            }
          }
            if (fullscreen && !pip && chapterPanel.open && chapters.isNotEmpty()) ChaptersSheet(chapters, playback, chapterPanel, vm::seekTo)
            LinkResolutionDialog(linkResolution, vm::retryLinkResolution,
                { url -> openExternalContent(context, url) { vm.message.value = "No app could open this link." } }, vm::dismissLinkResolution)
            RssSheet(vm)
            if (channelDescriptionOpen && !pip && channel != null && route == "channel:${channel!!.id}")
                ChannelDescriptionSheet(channel!!, vm.store.server, ::openRichLink) { channelDescriptionOpen = false }
            if (postComments.open && !watch && !pip && settingsPage.isEmpty() && postComments.context == vm.api.context() &&
                (postComments.target as? CommentTarget.Post)?.let(vm::isPostCommentsSource) == true)
                PostCommentsSheet(vm, postComments, { id -> vm.closePostComments(); navigate(tab, "channel:$id") }, ::openRichLink)
            when(dialog) {
                "filters" -> FiltersDialog(vm, { dialog = "" })
                "visibilityFilters" -> FiltersDialog(vm, { dialog = "" }, visibilityOnly = true)
                "player" -> PlayerSettings(vm, playback, { dialog = "" }, activity::enterPip, activity.supportsPip())
                "create", "edit" -> PlaylistDialog(if(dialog == "edit") playlist else null, { dialog = "" }) { title, privacy, description -> val editing = if (dialog == "edit") playlist else null; vm.action { if (editing != null) vm.api.editPlaylist(editing.id, title, privacy, description) else vm.api.createPlaylist(title, privacy); vm.refreshAccount(); vm.refresh() }; dialog = "" }
                "deletePlaylist", "clearHistory" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(if (dialog == "clearHistory") "Clear watch history?" else "Delete playlist?") }, text = { Text("This also changes your account on the website.") }, confirmButton = { TextButton(onClick = { val clear = dialog == "clearHistory"; val id = playlist?.id; if (clear) vm.clearHistory() else vm.action { if (id != null) vm.api.deletePlaylist(id); vm.refreshAccount(); navigate("You") }; dialog = "" }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
            }
            val dearrow by vm.dearrowContribution.collectAsStateWithLifecycle()
            if (sponsorEditor != null && !pip) SponsorBlockSheet(vm, sponsorEditor!!, { vm.sponsorSettingsChannel.value = null; signIn() }, dismiss = { vm.sponsorSettingsChannel.value = null })
            if (dearrow.open && !pip && !fullscreen) DeArrowContributionSheet(vm)
            if (!pip) DownloadExportConsent()
            if (downloadDialog.video != null && !pip) DownloadDialog(vm)
            if (saveSheet.video != null && route != "sign-in" && !pip) SavePlaylistSheet(vm) { signIn() }
            if (!pip) { ClipDialogs(vm); ClipEditor(vm) }
        }
      }
    }
}

@Composable private fun EmptyState(title: String, detail: String, action: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.PlayCircleOutline, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleLarge); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant); if (action.isNotBlank()) FilledTonalButton(onClick = onClick) { Text(action) } } }
@Composable private fun ErrorCard(message: String, retry: () -> Unit) { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp)) { Text(message); OutlinedButton(onClick = retry) { Text("Retry") } } } }
internal fun resolved(base: String, path: String) = base.toHttpUrlOrNull()?.resolve(path)?.toString() ?: path
internal fun time(seconds: Long): String = DisplayFormats.clock(seconds)
@Composable internal fun VideoCard(vm: AppViewModel, video: Video, server: String, play: () -> Unit, channel: (String) -> Unit, signIn: () -> Unit, remove: (() -> Unit)? = null, audioPlay: (() -> Unit)? = null, removalLabel: String = "Remove", avatarOwner: String? = null,
    removeFromPlaylist: (() -> Unit)? = null, removeFromPlaylistEnabled: Boolean = true, aiGroup: AiPageGroup = vm.aiPageGroup()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = ApiContext(server, account, vm.store.contextGeneration)
    val indicator = WatchedIndicators.forVideo(video, watched.takeIf { it.context == context } ?: WatchedState())
    val compact = prefs.uiDensity == "compact"
    val aiState by vm.aiFilter.state.collectAsStateWithLifecycle()
    val warning = vm.aiDecision(video, aiGroup, aiState).warning
    val thumbnail: @Composable (Modifier) -> Unit = { thumbnailModifier ->
        if (!prefs.thinMode || warning != null) Box(thumbnailModifier.then(if (prefs.thinMode) Modifier.height(64.dp) else Modifier.aspectRatio(16f/9f)).clip(Liquid.media).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = play)) {
            if (warning != null) AiThumbnail(warning, Modifier.fillMaxSize(), compact = prefs.thinMode)
            else AsyncImage(resolved(server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (indicator.watched) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
                Text("Watched", Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = .8f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("video-watched-${video.id}"), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            val duration = if (video.live) "LIVE" else DisplayFormats.duration(video.duration)
            if (duration.isNotBlank()) Text(duration, Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .8f)).padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp)
            indicator.bar?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                    .testTag("video-progress-${video.id}").semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(indicator.percent?.div(100f) ?: 1f, 0f..1f)
                    }, color = MaterialTheme.colorScheme.primary, trackColor = Color.Black.copy(alpha = .3f), gapSize = 0.dp, drawStopIndicator = {})
            }
        }
    }
    val metadata: @Composable (Modifier) -> Unit = { metadataModifier ->

        Row(metadataModifier, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (video.membersOnly) MembersBadge(video.id)
                DeArrowTitle(vm, video, MaterialTheme.typography.titleMedium, Modifier.clickable(onClick = play), maxLines = 2)
                if (prefs.thinMode) {
                    if (indicator.watched) Text("Watched", Modifier.testTag("video-watched-${video.id}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    indicator.percent?.let { Text("Progress: $it%", Modifier.testTag("video-progress-${video.id}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                }
                if (video.author.isNotBlank() || video.channelId.isNotBlank()) ChannelAuthor(server, video.authorAvatar,
                    video.author.ifBlank { "Unknown channel" }, Avatars.show(prefs.thinMode, video.channelId, avatarOwner),
                    size = 24.dp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    tag = "video-avatar-${video.id}", onClick = if (ContentVisibility.validChannel(video.channelId)) ({ channel(video.channelId) }) else null)
                VideoMetadataLine(video, Modifier.padding(top = 4.dp))
                video.history?.let { history ->
                    Text("Released: ${DisplayFormats.date(history.released).ifBlank { "Unknown date" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Watched: ${DisplayFormats.date(history.watched).ifBlank { "Unknown watch date" }}", style = MaterialTheme.typography.bodySmall)
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
    val cardModifier = Modifier.fillMaxWidth().padding(horizontal = Liquid.inset, vertical = if (compact) 8.dp else 12.dp)
        .testTag("video-card-${video.id}").semantics { if (indicator.description.isNotEmpty()) stateDescription = indicator.description }
    if (compact) Row(cardModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        thumbnail(Modifier.width(if (LocalDensity.current.fontScale > 1.3f) 96.dp else 128.dp))
        metadata(Modifier.weight(1f))
    } else Column(cardModifier) {
        thumbnail(Modifier.fillMaxWidth())
        metadata(Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun MiniPlayer(vm: AppViewModel, playback: PlaybackState, presentation: PlayerPresentationState,
    open: () -> Unit, toggle: () -> Unit, close: () -> Unit, gesturesEnabled: Boolean) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val occurrence by vm.queue.collectAsStateWithLifecycle()
    val exploring = rememberPlayerTouchExploration()
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var width by remember { mutableFloatStateOf(1f) }
    val enabled = gesturesEnabled && !exploring && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val dismissOffset = if (presentation.target == PlayerPresentation.CLOSED) presentation.dismissDirection * width * presentation.fraction else 0f
    val drag = presentation.dragHandler(density.density,
        kotlin.math.abs(presentation.miniBounds.top - presentation.watchBounds.top).coerceAtLeast(160 * density.density),
        width, vm::cancelAccumulatedSeek, close)
    val currentDrag by rememberUpdatedState(drag)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clipToBounds().hiddenPlayerContent(presentation.mode != PlayerPresentation.MINI || presentation.active)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, if (presentation.allocatesMini) placeable.height else 0) {
                placeable.place(0, if (presentation.allocatesMini) 0 else -placeable.height)
            }
        }) {
        Surface(modifier = Modifier.graphicsLayer {
            translationX = dismissOffset
            alpha = presentation.miniAlpha
        }.liquidGlass(Liquid.card), color = Color.Transparent, tonalElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("mini-player")
                .onGloballyPositioned { coordinates ->
                    origin = coordinates.positionInRoot()
                    width = coordinates.size.width.toFloat()
                    presentation.miniHeight = coordinates.size.height.toFloat()
                }
                .pointerInput(playback.mediaId, occurrence.currentKey, presentation.mode, enabled, layoutDirection, windowSize) {
                    if (!enabled || presentation.mode != PlayerPresentation.MINI) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Buttons keep their entire 48dp hit areas; title/background drags can cancel a click.
                        val onButtons = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr)
                            down.position.x >= size.width - 96.dp.toPx() else down.position.x <= 96.dp.toPx()
                        if (onButtons || presentation.active) return@awaitEachGesture
                        playerGesture(down, PlayerPresentation.MINI, currentDrag, position = { it + origin })
                    }
                }
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("Restore player") { open(); true },
                        CustomAccessibilityAction("Dismiss player") { close(); true })
                }
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // The live preview is rendered above this slot by PlayerPresentationHost.
                Box(Modifier.padding(start = 8.dp).size(96.dp, 54.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                    .playerAnchor { presentation.miniBounds = it.translate(Offset(-dismissOffset, 0f)) }
                    .clickable(enabled = presentation.mode == PlayerPresentation.MINI && !presentation.active,
                        onClickLabel = "Open player", onClick = open).testTag("mini-player-preview"))
                Column(Modifier.weight(1f).clickable(enabled = presentation.mode == PlayerPresentation.MINI && !presentation.active,
                    onClickLabel = "Open player", onClick = open).padding(horizontal = 8.dp)) {
                    if (playback.clip != null) Text(playback.clip.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else playback.details?.video?.let { DeArrowTitle(vm, it, MaterialTheme.typography.labelLarge, maxLines = 1) }
                    Text(playback.details?.video?.author.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                val ended = playback.playerState == Player.STATE_ENDED
                IconButton(onClick = toggle, enabled = playback.canPlay && presentation.mode == PlayerPresentation.MINI && !presentation.active, modifier = Modifier.size(48.dp)) {
                    Icon(if (ended) Icons.Default.Replay else if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (ended) "Replay" else if (playback.playWhenReady) "Pause" else "Play")
                }
                IconButton(onClick = close, enabled = presentation.mode == PlayerPresentation.MINI && !presentation.active, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Close player") }
            }
        }
    }
}
@Composable private fun WatchScreen(vm: AppViewModel, playback: PlaybackState, modifier: Modifier,
    presentation: PlayerPresentationState, detailsList: androidx.compose.foundation.lazy.LazyListState,
    resize: WatchPlayerResizeState, chapterPanel: ChapterPanelState, openChapters: () -> Unit,
    description: Boolean, describe: (Boolean) -> Unit, closePlayer: () -> Unit,
    add: (Video) -> Unit, channel: (String) -> Unit, play: (Video) -> Unit, signIn: () -> Unit, chatSettings: () -> Unit) {
    val chat by vm.chatReplay.collectAsStateWithLifecycle()
    val chatAppearance by vm.chatAppearance.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle(); val subscriptionChannels by vm.subscriptionChannels.collectAsStateWithLifecycle()
    val subscriptions = subscriptionChannels.takeIf { it.context == vm.api.context() }?.channels.orEmpty()
    val account by vm.account.collectAsStateWithLifecycle()
    val savedPrefs by vm.preferences.collectAsStateWithLifecycle()
    val aiState by vm.aiFilter.state.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = queue.effective(savedPrefs)
    fun openWatchLink(raw: String) {
        when (val target = ContentLinks.resolve(raw, vm.store.server, playback.details?.video?.id.orEmpty())) {
            is ContentLink.Seek -> vm.seekTo(target.milliseconds)
            is ContentLink.External -> openExternalContent(context, target.url) { vm.message.value = "No app could open this link." }
            null -> Unit
            else -> { vm.closeComments(); if (!vm.openContent(target)) presentation.present(PlayerPresentation.MINI, animate = false) }
        }
    }
    val chapters = playback.chapters
    val commentsOpen = comments.open && prefs.showYoutubeComments && comments.videoId == playback.details?.video?.id
    val chatDocked = chat.open && chat.available && !chatAppearance.overlay
    val drawerOpen = commentsOpen || chapterPanel.open || chatDocked
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("watch-content")
        .pointerInput(Unit) {
            // Consume otherwise unhandled touches so the revealed browse layer cannot receive watch-page taps.
            awaitEachGesture { awaitFirstDown().consume(); waitForUpOrCancellation()?.consume() }
        }) {
        val sideBySide = maxWidth >= 840.dp
        val playerWidth = (if (sideBySide) maxWidth * .62f - 16.dp else maxWidth - 16.dp).value
        val expandedHeight = playback.geometry.embeddedHeight(playerWidth, maxHeight.value, false)
        val compactHeight = playback.geometry.embeddedHeight(playerWidth, maxHeight.value, false, 1f)
        val scrollConnection = rememberWatchPlayerScrollConnection(detailsList, resize, expandedHeight > compactHeight && !drawerOpen)
        val playerHeight = if (chatDocked) maxHeight * (1f - chatAppearance.belowFraction) else playback.geometry.embeddedHeight(playerWidth, maxHeight.value, drawerOpen, resize.progress).dp
        val detailsContent: @Composable ColumnScope.() -> Unit = {
            if (chapterPanel.open) ChaptersPanel(chapters, playback.position, chapterPanel,
                playback.seekable && !playback.loading && playback.error == null, chapterPanel::close, vm::seekTo,
                Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp))
            else if (chatDocked) ChatReplayPanel(vm, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), chatSettings)
            else if (commentsOpen) CommentsDrawer(vm, comments, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), channel, ::openWatchLink)
            else if (playback.clip != null) LazyColumn(Modifier.weight(1f).testTag("clip-details-list"), state = detailsList) {
                item { ClipDetails(vm, playback.clip, closePlayer, channel) }
            }
            else LazyColumn(Modifier.weight(1f).nestedScroll(scrollConnection).testTag("watch-details-list"), state = detailsList) {
                playback.details?.let { details ->
                    item(key = "watch:metadata") {
                      WatchGlassScene(Modifier.fillMaxWidth(), artwork = if (prefs.thinMode || playback.downloadId != null) null else
                          resolved(vm.store.server, details.video.thumbnail.ifBlank { "/vi/${details.video.id}/mqdefault.jpg" })) {
                      Column(Modifier.padding(horizontal = Liquid.inset, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (details.video.membersOnly) MembersBadge(details.video.id)
                        val titleStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp)
                        if (playback.downloadId != null) Text(details.video.title, style = titleStyle, fontWeight = FontWeight.SemiBold)
                        else DeArrowTitle(vm, details.video, titleStyle, fontWeight = FontWeight.SemiBold)
                        VideoNotices(details)
                        VideoMetadataLine(details.video, likes = details.likes)
                        if (playback.downloadId != null) DownloadChannelIdentity(details.video)
                        else WatchChannelIdentity(details.video, vm.store.server, prefs.thinMode,
                            subscriptions.any { it.id == details.video.channelId }, subscribe = {
                                if (vm.account.value == null) signIn()
                                else vm.toggleSubscribe(details.video.channelId)
                            }, channel = channel, verified = details.authorVerified == true, subscribers = details.subscribers)
                        if (playback.downloadId == null) WatchPrimaryActions(vm, details, { add(details.video) }, signIn)
                        blocked.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (prefs.showYoutubeComments && playback.downloadId == null) CommentsEntry(comments) { chapterPanel.close(); vm.openComments() }
                        ChatReplayEntry(chat) { chapterPanel.close(); vm.openChat() }
                        ChaptersEntry(chapters, playback.position, openChapters)
                        WatchDisclosureRow("Description", Icons.AutoMirrored.Filled.Notes,
                            modifier = Modifier.testTag("watch-description-toggle"), expanded = description,
                            actionLabel = if (description) "Hide description" else "Show description") { describe(!description) }
                        VideoDescription(details, queue.currentKey.orEmpty(), vm.store.server, description, ::openWatchLink)
                        if (queue.current?.linkPlayback?.invalidEnd == true) Text("Invalid end boundary ignored.", style = MaterialTheme.typography.bodySmall)
                    } } }
                }
                if (queue.hasExplicitQueue) item(key = "watch:queue") { PlaybackQueuePanel(vm, signIn, channel) }
                playback.details?.let { details ->
                    if (prefs.relatedVideos) {
                        item(key = "watch:up-next") { Text("Up next", Modifier.padding(horizontal = Liquid.inset, vertical = 20.dp)
                            .testTag("watch-up-next"), style = MaterialTheme.typography.titleMedium) }
                        items(vm.visibleVideos(details.recommendations, ContentSurface.RECOMMENDATIONS, AiPageGroup.RECOMMENDATIONS, aiState), key = { it.id }) { VideoCard(vm, it, vm.store.server, { play(it) }, channel, signIn, aiGroup = AiPageGroup.RECOMMENDATIONS) }
                    }
                }
            }
        }
        if (sideBySide) Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(.62f).fillMaxHeight().padding(8.dp).playerAnchor { presentation.watchBounds = it })
            Column(Modifier.weight(.38f).fillMaxHeight(), content = detailsContent)
        } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(playerHeight)
                .playerAnchor { presentation.watchBounds = it })
            detailsContent()
        }
    }
}


@Composable private fun FiltersDialog(vm: AppViewModel, dismiss: () -> Unit, visibilityOnly: Boolean = false) {
    val context = remember { vm.api.context() }
    val searchType by vm.searchType.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(vm.sort) }
    var date by remember { mutableStateOf(vm.date) }
    var duration by remember { mutableStateOf(vm.durationFilter) }
    var visibility by remember { mutableStateOf(vm.searchVisibility.value) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Search filters") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!visibilityOnly) {
                if (searchType != SearchType.CHANNELS) Choice("Sort", listOf("relevance", "views"), sort) { sort = it }
                if (searchType.videoFilters) Choice("Uploaded", listOf("", "hour", "today", "week", "month", "year"), date) { date = it }
                if (searchType.videoFilters) Choice("Duration", listOf("", "short", "long"), duration) { duration = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.showMembers ?: prefs.showMemberVideos, { visibility = visibility.copy(showMembers = it) }, modifier = Modifier.testTag("search-show-members"))
                Text("Show members-only videos", Modifier.weight(1f))
            }
            Text(if (visibility.showMembers == null) "Using browsing default" else "Search override saved on this device", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { visibility = visibility.copy(showMembers = null) }, modifier = Modifier.testTag("search-members-reset")) { Text("Use browsing default") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.includeBlocked, { visibility = visibility.copy(includeBlocked = it) }, modifier = Modifier.testTag("search-include-blocked"))
                Text("Include blocked channels", Modifier.weight(1f))
            }
        }
    }, confirmButton = { TextButton(onClick = {
        if (vm.saveSearchVisibility(visibility, context)) {
            vm.sort = sort; vm.date = date; vm.durationFilter = duration; vm.refresh(); dismiss()
        }
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@Composable private fun Choice(label: String, values: List<String>, selected: String, update: (String) -> Unit) {
    DropdownChoiceRow(label, values.map { it to it.ifBlank { "Any" }.replace('_', ' ') }, selected, change = update)
}
@Composable private fun PlaylistDialog(playlist: Playlist?, dismiss: () -> Unit, save: (String, String, String) -> Unit) { var title by remember { mutableStateOf(playlist?.title ?: "") }; var privacy by remember { mutableStateOf(playlist?.privacy ?: "private") }; var description by remember { mutableStateOf(playlist?.description ?: "") }; AlertDialog(onDismissRequest = dismiss, title = { Text(if(playlist == null) "New playlist" else "Edit playlist") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it.take(150) }, label = { Text("Title") }, singleLine = true); Choice("Privacy", listOf("private", "unlisted", "public"), privacy) { privacy = it }; if(playlist != null) OutlinedTextField(description, { description = it }, label = { Text("Description") }) } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { save(title.trim(), privacy, description) }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) }
