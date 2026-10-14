package net.wingress.mobivious.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
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
    val accent by vm.store.accentPreset.collectAsStateWithLifecycle()
    val preferenceEdits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val sponsorEditor by vm.sponsorSettingsChannel.collectAsStateWithLifecycle()
    val spoilerEditor by vm.spoilerSettingsChannel.collectAsStateWithLifecycle()
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
    val subscriptionFeed = route.isEmpty() && tab == "Subscriptions"
    val channelPage = route.startsWith("channel:")
    val libraryPage = tab == "You" && route.isEmpty() || route in listOf("playlists", "history", "clips", "downloads") || route.startsWith("playlist:")
    val discoveryPage = route.isEmpty() && PreferenceRules.isDiscovery(tab)
    val searchPage = route.isEmpty() && tab == "Search"
    val titledBrowsePage = subscriptionFeed || channelPage || route == "subscription-channels" || libraryPage || discoveryPage || searchPage || route == "sign-in" || route.startsWith("post:")
    val librarySection by vm.playlistLibrarySection.collectAsStateWithLifecycle()
    val libraryTitle = when {
        route.startsWith("playlist:") -> if (playlist?.mix == true) "Mix" else "Playlist"
        route == "playlists" -> "Playlists"
        route == "history" -> "History"
        route == "clips" -> "My Clips"
        route == "downloads" -> "Downloads"
        else -> "You"
    }
    val destination = AppDestination.from(tab) ?: AppDestination.from(vm.searchOriginTab)
    val browseReset by vm.browseReset.collectAsStateWithLifecycle()
    val browseList = key(selectedTab, if (route == "playlists") librarySection else null) {
        remember {
            val position=vm.browse.value.position
            androidx.compose.foundation.lazy.LazyListState(position.index,position.offset)
        }
    }
    val latestBrowseList by rememberUpdatedState(browseList)
    val subscriptionList = key(vm.api.context()) { rememberLazyListState() }
    var handledBrowseReset by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(browseReset) {
        if (browseReset > handledBrowseReset) { browseList.scrollToItem(0); handledBrowseReset = browseReset }
    }
    val visibleVideos = remember(state.videos,aiState,prefs,blocked,searchVisibility,selectedTab) { vm.visibleVideos(state.videos, aiState=aiState) }
    var channelDescriptionOpen by rememberSaveable(vm.store.server, channel?.id, route) { mutableStateOf(false) }
    val presentation = rememberPlayerPresentation()
    val watch = presentation.watch
    val fullscreen = presentation.fullscreen
    val queue by vm.queue.collectAsStateWithLifecycle()
    val chapters = playback.chapters
    val queuePanelOpen by vm.queueExpanded.collectAsStateWithLifecycle()
    val chapterPanel = key(vm.store.server, queue.token, queue.currentKey) { rememberChapterPanelState() }
    val watchList = key(queue.currentKey) { rememberLazyListState() }
    val watchResize = key(queue.currentKey) { rememberWatchPlayerResizeState() }
    val watchDefaults = queue.effective(prefs)
    var watchDescription by rememberSaveable(queue.currentKey, watchDefaults.extendDescription) { mutableStateOf(watchDefaults.extendDescription) }
    fun browsePlayer() = presentation.present(if (vm.playback.value.details != null) PlayerPresentation.MINI else PlayerPresentation.CLOSED, animate = false)
    fun restorePlayer() {
        vm.cancelAccumulatedSeek()
        // A delayed browse return must not resurrect a queue explicitly closed by the user.
        presentation.present(if (vm.playback.value.details != null) PlayerPresentation.WATCH else PlayerPresentation.CLOSED)
    }
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
        vm.cancelAccumulatedSeek(); vm.closeComments(); vm.closeChat(); vm.queueExpanded.value = false; dialog = ""
        chapterPanel.show(chapters, playback.position)
    }
    fun toggleChat() { if (vm.chatReplay.value.open) vm.closeChat() else { chapterPanel.close(); vm.queueExpanded.value = false; dialog = ""; vm.openChat() } }
    fun backWatch() { if (vm.queueExpanded.value) vm.queueExpanded.value = false else if (chat.open) vm.closeChat() else if (chapterPanel.open) chapterPanel.close() else if (!vm.backComments()) collapsePlayer() }
    LaunchedEffect(chapters, playback.loading) { if (!playback.loading && chapters.isEmpty()) chapterPanel.close() }
    LaunchedEffect(dialog) { if (dialog.isNotEmpty()) chapterPanel.close() }
    var dialogContext by remember { mutableStateOf(deviceContext) }
    LaunchedEffect(deviceContext) { if(dialogContext!=deviceContext) {dialog="";dialogContext=deviceContext} }
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
    LaunchedEffect(searchOpen,selectedTab) { if (searchOpen && tab=="Search" && route.isEmpty()) { searchFocus.requestFocus(); keyboard?.show() } }
    val restoredBrowse by vm.restoredBrowse.collectAsStateWithLifecycle()
    val authenticationFinished by vm.authenticationFinished.collectAsStateWithLifecycle()
    var handledRestore by rememberSaveable { mutableLongStateOf(0L) }
    var handledAuth by rememberSaveable { mutableLongStateOf(0L) }
    var observedNavigation by remember { mutableStateOf(selectedTab) }
    LaunchedEffect(browseList,selectedTab, librarySection, restoredBrowse, authenticationFinished, shared.value) {
        // StateFlow collection can lag the shared-video event by one frame. Consume
        // the event only after its destination is visible so a late tab update
        // cannot immediately minimize the player we just opened.
        if (selectedTab != vm.navigation.value || route == "playlists" && librarySection != vm.playlistLibrarySection.value) return@LaunchedEffect
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
            if (selectedTab==vm.navigation.value && vm.restoredBrowse.value == handledRestore && (route != "playlists" || librarySection == vm.playlistLibrarySection.value)) vm.browsePosition(position)
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
        originIndex = latestBrowseList.firstVisibleItemIndex; originOffset = latestBrowseList.firstVisibleItemScrollOffset
        originWatch = presentation.watch; authSettings = settingsPage; settingsPage = ""
        if (watch) { authWatchId = playback.details?.video?.id.orEmpty(); authWatchPosition = playback.position / 1000 }
        vm.openSignIn()
    }
    fun submitGlobalSearch() {
        if (searchDraft.isBlank()) return
        searchOpen = false; keyboard?.hide(); vm.cancelAccumulatedSeek()
        if (vm.tab != "Search" || vm.route.isNotEmpty() || presentation.watch) {
            originIndex = latestBrowseList.firstVisibleItemIndex; originOffset = latestBrowseList.firstVisibleItemScrollOffset; originWatch = presentation.watch
        }
        val link = ContentLinks.parse(searchDraft, vm.store.server)
        if (link != null) { if (vm.openContent(link)) openPlayer() else browsePlayer() }
        else { browsePlayer(); vm.openGlobalSearch(searchDraft) }
    }
    fun openGlobalSearch() {
        vm.browsePosition(CommentPosition(latestBrowseList.firstVisibleItemIndex,latestBrowseList.firstVisibleItemScrollOffset))
        originWatch = presentation.watch
        originIndex = latestBrowseList.firstVisibleItemIndex; originOffset = latestBrowseList.firstVisibleItemScrollOffset
        authWatchId = ""; authSettings = ""; vm.cancelAccumulatedSeek(); browsePlayer()
        searchDraft = vm.searchInput.value.submitted; vm.beginGlobalSearch(); searchOpen = true
    }
    fun selectDestination(selected: AppDestination) {
        vm.browsePosition(CommentPosition(latestBrowseList.firstVisibleItemIndex,latestBrowseList.firstVisibleItemScrollOffset))
        authWatchId = ""; authSettings = ""; vm.cancelAccumulatedSeek(); browsePlayer()
        searchOpen = false; keyboard?.hide(); vm.selectTab(selected.label)
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
    LaunchedEffect(pip, watch, playback.mediaId) { if (pip || !watch) { vm.queueExpanded.value = false; chapterPanel.close(); vm.closeComments(); vm.cancelAccumulatedSeek(); dialog = ""; vm.closeDeArrow() }; if (pip) vm.sponsorSettingsChannel.value = null }
    LaunchedEffect(account) { dialog = "" }
    LaunchedEffect(watch, pip, settingsPage) { if (watch || pip || settingsPage.isNotEmpty()) {
        channelDescriptionOpen = false; vm.closePostComments()
    } }
    LaunchedEffect(settingsPage) { if (settingsPage.isNotEmpty()) vm.cancelAccumulatedSeek(); if (settingsPage == "Settings") vm.refreshSharedSettings() }
    BackHandler(settingsPage.isEmpty() && (fullscreen || watch || presentation.target == PlayerPresentation.WATCH || route.isNotBlank() || vm.hasBrowseBack)) { when { fullscreen -> { if (chat.open) vm.closeChat() else if (chapterPanel.open) chapterPanel.close() else collapsePlayer() }; watch || presentation.target == PlayerPresentation.WATCH -> backWatch(); else -> { vm.cancelAccumulatedSeek(); vm.backBrowse() } } }
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

    AppTheme(darkTheme,accent) {
      Box(Modifier.fillMaxSize()) {
        AppBackground(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize()) {
          PlayerPresentationHost(vm, playback, controller, presentation, pip, settingsPage.isNotEmpty() && !pip || clipEditor.open || keyboardVisible && !pip && !watch,
            downloadDialog.video != null || queuePanelOpen || fullscreen && chapterPanel.open || dialog.isNotEmpty() || sponsorEditor != null || saveSheet.video != null || contribution.open || rss.open || searchOpen || accountBusy || channelDescriptionOpen || postComments.open || linkResolution.link != null || clipEditor.open,
            queue.currentKey, ::closePlayer, ::collapsePlayer, ::restorePlayer, ::toggleFullscreen, { chapterPanel.close(); dialog = "player" }, ::openChapters, ::toggleChat) { openChatSettings ->
            if (settingsPage.isNotEmpty() && !pip) SettingsScreen(vm, settingsPage, { page -> when(page) {
                "Chat settings" -> dialog="chatPreferences"
                "Search filters" -> dialog="filters"
                "Trending" -> { settingsPage=""; vm.selectTab("Trending") }
                "Channels" -> { settingsPage=""; navigate("Subscriptions","subscription-channels") }
                else -> settingsPage=page
            } }, { settingsPage = settingsParent(settingsPage) }, { signIn() }, { id -> settingsPage = ""; navigate("Popular", "channel:$id") })
            else BoxWithConstraints(Modifier.fillMaxSize().semantics { testTagsAsResourceId=true }) {
              val wideLayout = AppWindowWidth.from(maxWidth.value)!=AppWindowWidth.COMPACT
              val shellStart = if (wideLayout && !watch) 112.dp else 0.dp
              val imeVisible = keyboardVisible
              val feedColumns = if (maxWidth < 600.dp || prefs.thinMode || prefs.uiDensity=="compact") 1 else
                  ((minOf(maxWidth-shellStart,1200.dp).value)/(280f*maxOf(1f,LocalDensity.current.fontScale/1.3f))).toInt().coerceAtLeast(1)
              val edgeColor = MaterialTheme.colorScheme.background
              Scaffold(
                modifier = Modifier.padding(start = if(imeVisible) 0.dp else shellStart).imePadding().hiddenPlayerContent(pip || fullscreen && !presentation.active),
                containerColor = Color.Transparent,
                topBar = {
                    if (!watch && !fullscreen && !titledBrowsePage) AppTopBar(title = when {
                            route == "sign-in" -> "Sign in"
                            route == "subscription-channels" -> "Channels"
                            route.isNotEmpty() -> channel?.name ?: playlist?.title ?: state.title
                            else -> destination?.label ?: "Search"
                        }, navigation = if (route.isNotEmpty() || vm.hasBrowseBack && tab != "Search") ({
                            AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", enabled = !accountBusy) { vm.cancelAccumulatedSeek(); vm.backBrowse() }
                        }) else null, ) {
                        AppIconButton(Icons.Default.Search,"Search",Modifier.testTag("global-search"),onClick=::openGlobalSearch)
                    }
                },
                snackbarHost = { AppSnackbarHost(snackbar) },
                bottomBar = {
                    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                        .hiddenPlayerContent(watch || presentation.active || pip || fullscreen || imeVisible)) {
                        if (!imeVisible && presentation.mode != PlayerPresentation.CLOSED && playback.details != null)
                            MiniPlayer(vm,playback,presentation,::restorePlayer,vm::togglePlay,::closePlayer,
                                gesturesEnabled=!pip && dialog.isEmpty() && sponsorEditor==null && !searchOpen && !accountBusy && saveSheet.video==null && !contribution.open && !rss.open && !channelDescriptionOpen && !postComments.open && linkResolution.link==null)
                        if (!wideLayout && !imeVisible) AppNavigation(destination,false,enabled=!accountBusy,
                            modifier=Modifier.playerAnchor { presentation.navigationBounds=it },select=::selectDestination)
                        else if (wideLayout && !imeVisible) Spacer(Modifier.navigationBarsPadding())
                    }

                }
            ) { padding ->
              CompositionLocalProvider(LocalContentBottomInset provides 20.dp) {
                val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
                Box(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom).semantics { testTagsAsResourceId=true }) {
                  Box(Modifier.fillMaxSize()) {
                if (route == "sign-in") CompositionLocalProvider(LocalContentBottomInset provides 20.dp) {
                    // The shared viewport ends above navigation and the IME.
                    SignInScreen(vm, Modifier.hiddenPlayerContent(watch || presentation.active || pip)) { vm.backBrowse() }
                }
                else BrowseAppSurface(Modifier.fillMaxSize().hiddenPlayerContent(watch || presentation.active || pip),
                    enabled = subscriptionFeed || channelPage || libraryPage || discoveryPage || searchPage, toolbar = {
                        if (libraryPage) LibraryToolbar(libraryTitle, back = if (route.isEmpty()) null else ({ vm.cancelAccumulatedSeek(); vm.backBrowse() }), actions = {
                                AppIconButton(Icons.Default.Search,"Search",Modifier.testTag("global-search"),onClick=::openGlobalSearch)
                                if (route.isEmpty()) AppIconButton(Icons.Default.Settings, "Settings", Modifier.testTag("global-settings"), enabled = !accountBusy) { vm.settingsOpened(); settingsPage = "Settings" }
                                else if (route == "playlists" && account != null) AppIconButton(Icons.Default.Add, "New playlist", Modifier.testTag("playlist-create"), prominent = true) { dialog = "create" }
                                if (route == "history" && account != null) OverflowMenu("History actions", vm.api.context(), Modifier.testTag("history-actions")) { close ->
                                    DropdownMenuItem(text = { Text("Clear watch history") }, onClick = { close(); dialog = "clearHistory" })
                                }
                            }, controls = {
                                if (route == "playlists" && account != null) BrowseTabs(listOf("owned", "subscribed"), librarySection,
                                    { if (it == "owned") "My playlists" else "Subscribed" }, { "playlist-section-$it" }, select = vm::selectPlaylistLibrary)
                                if (route == "history" && account != null) {
                                    AppSurface(Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SearchField(scopedSearch.draft, { vm.editSearch(it, true) }, "Search history by title or channel", "history-search", Modifier.weight(1f), enabled = state.history?.organized != false) { vm.submitSearch(true) }
                                        if (scopedSearch.draft.isNotBlank() || scopedSearch.submitted.isNotBlank()) AppInsetIconButton(vm::clearScopedSearch) { Icon(Icons.Default.Close, "Clear search") }
                                    } }
                                }
                            })
                        else if (discoveryPage) LibraryToolbar(tab, actions = {
                            AppIconButton(Icons.Default.Search,"Search",Modifier.testTag("global-search"),onClick=::openGlobalSearch)
                        }, controls = {
                            if (tab == "Trending") key(deviceContext) { TrendingControls(vm, prefs.region) }
                        })
                        else if (searchPage) LibraryToolbar("Search", back={ searchOpen=false; keyboard?.hide(); if(vm.hasBrowseBack) vm.backBrowse() else vm.selectTab("Popular") }, backLabel="Close search", actions = {
                            AppIconButton(Icons.Default.Tune, "Search filters") { dialog = "filters" }
                        }, controls = {
                            SearchField(searchDraft, {searchDraft=it}, "Search or paste a link", "main-search", Modifier.fillMaxWidth().focusRequester(searchFocus)) { submitGlobalSearch() }
                            BrowseTabs(SearchType.entries, searchType, { it.label }, { "search-type-${it.apiValue}" }, select = vm::setSearchType)
                            val filterLabels=listOfNotNull(
                                vm.sort.takeIf {it!="relevance" && searchType!=SearchType.CHANNELS}?.let {"Sort: $it"},
                                vm.date.takeIf {it.isNotBlank() && searchType.videoFilters}?.let {"Uploaded: $it"},
                                vm.durationFilter.takeIf {it.isNotBlank() && searchType.videoFilters}?.let {"Duration: $it"},
                                searchVisibility.showMembers?.let {if(it) "Members shown" else "Members hidden"},
                                "Blocked included".takeIf {searchVisibility.includeBlocked})
                            if(filterLabels.isNotEmpty()) Text(filterLabels.joinToString(" · "),Modifier.testTag("search-filter-summary"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)

                        })
                        else if (subscriptionFeed) SubscriptionFeedToolbar {
                            AppIconButton(Icons.Default.Search,"Search",Modifier.testTag("global-search"),onClick=::openGlobalSearch)
                            if(account!=null) OverflowMenu("Subscription actions", vm.api.context(), Modifier.testTag("subscription-actions")) { close ->
                                DropdownMenuItem(text = { Text("RSS") }, onClick = { close(); vm.openSubscriptionRss() })
                                DropdownMenuItem(text = { Text("Export OPML") }, onClick = { close(); vm.openOpml() })
                            }
                        }
                        else if (channelPage) ChannelBrowseToolbar(channel, channelTab, channelVideoSort, channelPlaylistSort,
                            scopedSearch.draft, scopedSearch.submitted, state.loading, { vm.cancelAccumulatedSeek(); vm.backBrowse() }, vm::refresh,
                            { vm.editSearch(it, true) }, { vm.submitSearch(true) }, vm::clearScopedSearch, vm::selectChannelTab,
                            vm::setChannelVideoSort, vm::setChannelPlaylistSort) { AppIconButton(Icons.Default.Search,"Search",Modifier.testTag("global-search"),onClick=::openGlobalSearch) }
                    }) { browseModifier, browseTopInset ->
                key(selectedTab, deviceContext) {
                FeedRefreshContainer(discoveryPage || subscriptionFeed && account != null,
                    state.refreshing, state.loading, tab, vm::refreshFeed, browseModifier) {
                Column(Modifier.fillMaxSize().padding(top = if (titledBrowsePage) 0.dp else padding.calculateTopPadding())
                    .then(if (libraryPage) Modifier.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 800.dp) else Modifier.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max=1200.dp)).fillMaxSize()) {
                    if(offline && !titledBrowsePage) Text("Offline · showing saved results", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    if (route == "downloads") DownloadedScreen(vm, browseList, browseTopInset) { id -> openPlayer(); vm.playDownload(id) }
                    else if (route.startsWith("post:")) PostDetailScreen(vm, postDetail, browseList,
                        { id -> navigate(tab, "channel:$id") }, ::openRichLink, { play(it) },
                        { list -> browsePlayer(); vm.openPlaylist(list) }, { signIn() }, { vm.backBrowse() })
                    else if (route == "subscription-channels") {
                        if (account == null) BrowseAppSurface(Modifier.fillMaxSize(), toolbar = {
                            BrowsePageTitle("Channels", back = { vm.backBrowse() })
                        }) { guestModifier, top -> Column(guestModifier.padding(top = top)) {
                            EmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", "Sign in") { signIn() }
                        } }
                        else SubscriptionChannelsScreen(subscriptionChannels.takeIf { it.context == vm.api.context() } ?: SubscriptionChannelsState(), vm.store.server, prefs.thinMode,
                            subscriptionList, { query -> vm.searchSubscriptionChannels(query); scope.launch { subscriptionList.scrollToItem(0) } }, vm::refreshSubscriptions,
                            { id -> navigate("Subscriptions", "channel:$id") },
                            { sort -> vm.sortSubscriptionChannels(sort, deviceContext); scope.launch { subscriptionList.scrollToItem(0) } },
                            header = { BrowsePageTitle("Channels", back = { vm.cancelAccumulatedSeek(); vm.backBrowse() }) },
                            requestedSort = preferenceEdits["profile:subscriptionSort"]?.let { SubscriptionSort.saved(org.json.JSONObject(it.requested).getString("value")) },
                            saveFeedback = { preferenceEdits["profile:subscriptionSort"]?.let { edit ->
                                PreferenceSaveFeedback(edit.status,edit.error,{vm.preferenceSaves.retry(edit.key)},{vm.preferenceSaves.revert(edit.key)})
                            } })
                    }
                    else if (route in listOf("clips", "history", "playlists") && account == null) LazyColumn(Modifier.fillMaxSize(), state = browseList,
                        contentPadding = PaddingValues(top = browseTopInset, bottom = LocalContentBottomInset.current)) {
                        item { LibraryEmptyState(if (route == "clips") "My Clips" else if (route == "history") "Your watch history" else "Your playlists",
                            "Sign in with your Invidious account to keep this part of your library together.",
                            if (route == "clips") Icons.Default.ContentCut else if (route == "history") Icons.Default.History else Icons.Default.VideoLibrary, "Sign in") { signIn() } }
                    }
                    else if (tab == "You" && route.isEmpty()) YouDashboard(account, vm.store.server, browseList, browseTopInset,
                        { page -> if(page=="server") {vm.settingsOpened();settingsPage="Server"} else navigate("You", page) }, { signIn() })
                    else if (route == "playlists") PlaylistLibrary(vm, playlists, librarySection, state, browseList, browseTopInset,
                        { list -> browsePlayer(); vm.openPlaylist(list) }, { dialog = "create" }, { signIn() })
                    else if (subscriptionFeed && account == null) LazyColumn(Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = browseTopInset, bottom = LocalContentBottomInset.current)) {
                        item { LibraryEmptyState("Your videos, together", "Sign in with your Invidious account to see subscriptions, playlists, and history.", Icons.Default.Subscriptions, "Sign in") { signIn() } }
                    }
                    else LazyColumn(Modifier.fillMaxSize().testTag("browse-video-list"), state = browseList, contentPadding = PaddingValues(top = browseTopInset, bottom = LocalContentBottomInset.current)) {
                        if (account != null && blocked.error != null && vm.contentSurface() in listOf(ContentSurface.DISCOVERY, ContentSurface.SEARCH)) item {
                            Column(Modifier.padding(horizontal = AppTokens.inset, vertical = 8.dp)) {
                                Text(blocked.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                ActionRow("Manage / retry blocked channels") { settingsPage = "Blocked channels" }
                            }
                        }
                        if (offline && titledBrowsePage) item { Text("Offline · showing saved results", Modifier.padding(horizontal = AppTokens.inset, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
                        if (discoveryPage) item { Text(if (tab == "Trending") "Trending in ${prefs.region}" else "Popular across your instance",
                            Modifier.padding(horizontal = AppTokens.inset, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (subscriptionFeed) {
                            if (subscriptions.isNotEmpty() && scopedSearch.submitted.isBlank()) item(key = "subscription-shortcuts") {
                                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = AppTokens.inset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(subscriptions, key = { it.id }) { c -> SubscriptionChannelChip(c, vm.store.server, prefs.thinMode) { navigate("Subscriptions", "channel:${c.id}") } }
                                }
                            }
                            item(key = "subscription-controls") { SubscriptionFeedControls(account != null, scopedSearch.draft, scopedSearch.submitted,
                                { vm.editSearch(it, true) }, { vm.submitSearch(true) }, vm::clearScopedSearch,
                                { navigate("Subscriptions", "subscription-channels") }, { dialog = "visibilityFilters" }) }
                            item(key = "subscription-heading") { SectionHeading(if (scopedSearch.submitted.isBlank()) "Latest uploads" else "Search results", Modifier.padding(horizontal = AppTokens.inset),
                                detail = if (scopedSearch.submitted.isBlank()) "From the channels you follow" else null) }
                        }
                        if (route == "clips") item {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Most recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!state.loading && state.clips.isEmpty() && state.error == null) Text("Save a moment worth sharing. Open a video and choose More → Create clip.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (channelTab == ChannelTab.CLIPS && scopedSearch.submitted.isBlank() && !state.loading && state.clips.isEmpty() && state.error == null) item {
                            Text("No clips yet. Clips made on videos from this channel will appear here.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (route.startsWith("hashtag:")) item { Text(state.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
                        channel?.let { info -> item {
                            ChannelHeader(info, vm.store.server, prefs.thinMode, subscriptions.any { it.id == info.id }, actions = {
                                OverflowMenu("Actions for channel ${info.name}", listOf(info.id, vm.api.context()), Modifier.testTag("channel-actions-${info.id}")) { close ->
                                    DropdownMenuItem(text = { Text("RSS") }, onClick = { close(); vm.openChannelRss(info) })
                                    BlockChannelMenuItem(vm, info.id, info.name, { signIn() }, Modifier.testTag("channel-block-${info.id}"), close)
                                    DropdownMenuItem(text = { Text("Channel SponsorBlock settings") }, onClick = { close(); vm.openSponsorBlock(info.id) })
                                    DropdownMenuItem(text = { Text(channelSpoilerSettingsPage) }, onClick = { close(); vm.openChannelSpoilers(info.id) })
                                }
                            }, readDescription = { channelDescriptionOpen = true },
                                subscriptionBusy = info.id in subscriptionChannels.busy,
                                subscriptionKnown = account == null || subscriptionChannels.context == vm.api.context() && subscriptionChannels.loaded,
                                subscriptionChecking = subscriptionChannels.loading,
                                subscriptionError = subscriptionChannels.actionErrors[info.id] ?: subscriptionChannels.error.takeIf { !subscriptionChannels.loaded }) {
                                if (account == null) { vm.prepareChannelSubscription(info.id); signIn() } else vm.toggleSubscribe(info.id)
                            }
                            Box(Modifier.padding(horizontal = 16.dp)) { ChannelBlockingError(vm, info.id) }
                            ChannelBrowseControls(info, channelTab, channelVideoSort, channelPlaylistSort, scopedSearch.draft, scopedSearch.submitted,
                                { vm.editSearch(it, true) }, { vm.submitSearch(true) }, vm::clearScopedSearch, vm::selectChannelTab,
                                vm::setChannelVideoSort, vm::setChannelPlaylistSort)
                        } }
                        playlist?.let { list -> item {
                            PlaylistHeader(vm, list, { openPlayer(); vm.play("", source = list.id, seed = list.seedVideoId) },
                                { dialog = "edit" }, { dialog = "deletePlaylist" }, { signIn() })
                        } }
                        val generalSearch = tab == "Search" && route.isEmpty()
                        if (generalSearch) itemsIndexed(vm.visibleSearchResults(state.searchResults), key = { index,result -> "search:$index:${result.key}" },contentType={_,result -> result::class}) { _,result ->
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
                        if (route == "history" && state.history?.organized == false) item { Text("Update this server to enable organized history and history search.", Modifier.padding(AppTokens.inset), style = MaterialTheme.typography.bodySmall) }
                        if (route == "history") item { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(state.history?.total?.let { DisplayFormats.inventory(it.toLong(), "video") } ?: "Recently watched", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge) } }
                        val groups = if (generalSearch) emptyMap() else if (route == "history" && state.history?.organized == true) visibleVideos.groupBy { History.group(it.history?.watched, state.history?.today) } else mapOf(null to visibleVideos)
                        groups.forEach { (group, videos) ->
                            if (group != null) item(key = "history-group-$group") { Text(group.label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("history-group-${group.name}"), style = MaterialTheme.typography.titleLarge) }
                            val rows = videos.chunked(if (route.isEmpty() && PreferenceRules.isDiscovery(tab) || subscriptionFeed || channelPage && channelTab?.videoTab == true) feedColumns else 1)
                            itemsIndexed(rows, key={ index,row -> "row:$index:"+row.joinToString { it.id+":"+it.indexId+":"+(it.playlistIndex?.toString() ?: "") } },contentType={_,_->"video-row"}) { _,row ->
                              Row(Modifier.fillMaxWidth()) {
                                row.forEach { video -> Box(Modifier.weight(1f)) { VideoCard(vm, video, vm.store.server, { play(video, playlist) }, { id -> navigate(tab, "channel:$id") }, { signIn() }, if (playlist?.let { list -> list.owned } == true || route == "history") ({
                                val list = playlist
                                if (list != null) vm.removePlaylistVideo(list, video)
                                else vm.removeHistory(video.id)
                            }) else null, audioPlay = { openPlayer(); vm.playVideo(video, playlist, audio = true) }, removalLabel = if (playlist != null) "Remove from playlist" else "Remove from history",
                                avatarOwner = channel?.id.takeIf { route.startsWith("channel:") && channelTab?.videoTab == true }, libraryRow = libraryPage) } }
                                repeat(((if(route.isEmpty() && PreferenceRules.isDiscovery(tab) || subscriptionFeed || channelPage && channelTab?.videoTab==true) feedColumns else 1)-row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                              }
                            }
                        }
                        if (state.loading && !state.refreshing) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        if (state.error != null) item { ErrorCard(state.error!!, vm::retryBrowse); if (state.videos.isEmpty() && state.searchResults.isEmpty()) LibraryActionButton("Configure server", Modifier.padding(horizontal = 16.dp)) { dialog = ""; settingsPage = "Server" } }
                        if (!state.loading && (if (generalSearch) vm.visibleSearchResults(state.searchResults).isEmpty() && state.searchResults.isNotEmpty() else visibleVideos.isEmpty() && state.videos.isNotEmpty())) item {
                            Column(Modifier.padding(16.dp)) {
                                Text(if (generalSearch) "Results hidden by your visibility settings" else "Videos hidden by your visibility settings")
                                Text("Change the filters or load another page to find visible videos.", style = MaterialTheme.typography.bodySmall)
                                LibraryActionButton("Visibility controls") { if (vm.contentSurface() == ContentSurface.SEARCH) dialog = if (tab == "Subscriptions") "visibilityFilters" else "filters" else settingsPage = "Browsing" }
                            }
                        }
                        if (!state.loading && state.error == null && state.videos.isEmpty() && state.lists.isEmpty() && state.posts.isEmpty() && state.channels.isEmpty() &&
                            state.clips.isEmpty() && route != "clips" && !(channelTab == ChannelTab.CLIPS && scopedSearch.submitted.isBlank())) item { EmptyState(
                            if (scopedSearch.submitted.isNotBlank() || generalSearch && search.submitted.isNotBlank()) "No matches" else if (route == "history") "Your history is empty" else if (tab == "Search") "Find something to watch" else "Nothing here yet",
                            if (scopedSearch.submitted.isNotBlank() || generalSearch && search.submitted.isNotBlank()) "Try another query or change the filters." else if (route == "history") "Videos you watch with history enabled will appear here." else if (tab == "Search") "Search for videos, channels and playlists, or paste a link." else "Refresh to check for videos.", if (tab == "Search" && search.submitted.isBlank()) "" else "Refresh", if (discoveryPage || subscriptionFeed) vm::refreshFeed else vm::refresh) }
                        if (!state.end && !state.loading && state.error == null) item { LibraryActionButton("Load more", Modifier.fillMaxWidth(), onClick = vm::more) }
                    }
                }
                }
                }
                }
                }
                }
              }
            }
              if (wideLayout && !watch && !fullscreen && !pip && !imeVisible) AppNavigation(destination,true,enabled=!accountBusy,
                  modifier=Modifier.align(Alignment.TopStart).fillMaxHeight(),select=::selectDestination)
              if (presentation.showsWatch && !pip) {
                  // Watch owns the whole window, independently of changing browse/mini chrome allocations.
                  Box(Modifier.fillMaxSize().graphicsLayer { alpha = presentation.watchAlpha }
                      .background(MaterialTheme.colorScheme.surface).hiddenPlayerContent(!watch || presentation.active)) {
                      WatchScreen(vm, playback, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                          presentation, watchList, watchResize, chapterPanel, watchDescription, { watchDescription = it }, ::closePlayer, vm::openSave,
                          { id -> navigate("Popular", "channel:$id") }, { play(it) }, { signIn() }, openChatSettings)
                  }
              }
            }
          }
            if (fullscreen && !pip && chapterPanel.open && chapters.isNotEmpty()) ChaptersSheet(chapters, playback, chapterPanel, vm::seekTo)
            val contextChange by vm.preferenceContextChange.collectAsStateWithLifecycle()
            if(contextChange!=null) AlertDialog(tonalElevation=3.dp,onDismissRequest={vm.preferenceContextChange.value=null},
                title={Text("Discard unconfirmed preferences?")},text={Text("Pending and unsaved changes will be discarded when you change account or server.")},
                confirmButton={TextButton(vm::continuePreferenceContextChange) {Text("Continue")}},
                dismissButton={TextButton({vm.preferenceContextChange.value=null}) {Text("Stay")}},
                containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)
            LinkResolutionDialog(linkResolution, vm::retryLinkResolution,
                { url -> openExternalContent(context, url) { vm.message.value = "No app could open this link." } }, vm::dismissLinkResolution)
            RssSheet(vm)
            if (channelDescriptionOpen && !pip && channel != null && route == "channel:${channel!!.id}")
                ChannelDescriptionSheet(channel!!, vm.store.server, ::openRichLink) { channelDescriptionOpen = false }
            if (postComments.open && !watch && !pip && settingsPage.isEmpty() && postComments.context == vm.api.context() &&
                (postComments.target as? CommentTarget.Post)?.let(vm::isPostCommentsSource) == true)
                PostCommentsSheet(vm, postComments, { id -> vm.closePostComments(); navigate(tab, "channel:$id") }, ::openRichLink)
            when(dialog) {
                "chatPreferences" -> ChatSettings(vm) { dialog="" }
                "filters" -> FiltersDialog(vm, { dialog = "" })
                "visibilityFilters" -> FiltersDialog(vm, { dialog = "" }, visibilityOnly = true)
                "player" -> PlayerSettings(vm, playback, { dialog = "" }, activity::enterPip, activity.supportsPip())
                "create", "edit" -> PlaylistDialog(if(dialog == "edit") playlist else null, { dialog = "" }) { title, privacy, description -> val editing=if(dialog=="edit") playlist else null; val requestContext=vm.api.context(); if(editing!=null) vm.api.editPlaylist(editing.id,title,privacy,description) else vm.api.createPlaylist(title,privacy); if(vm.api.context()!=requestContext) throw kotlinx.coroutines.CancellationException("Account or instance changed"); vm.refreshAccount();vm.refresh();dialog="" }
                "deletePlaylist", "clearHistory" -> LibraryConfirmation(if (dialog == "clearHistory") "Clear watch history?" else "Delete playlist?", { dialog = "" }, {
                    val clear = dialog == "clearHistory"; val id = playlist?.id
                    if (clear) vm.clearHistory() else vm.action { if (id != null) vm.api.deletePlaylist(id); vm.refreshAccount(); navigate("You") }
                    dialog = ""
                }) { Text("This also changes your account on the website.") }
            }
            val dearrow by vm.dearrowContribution.collectAsStateWithLifecycle()
            if (sponsorEditor != null && !pip) SponsorBlockSheet(vm, sponsorEditor!!, dismiss = { vm.sponsorSettingsChannel.value = null })
            if (spoilerEditor != null && !pip) key(vm.api.context()) { ChannelSpoilerSettings(vm, spoilerEditor!!, dismiss = { vm.spoilerSettingsChannel.value = null }) }
            if (dearrow.open && !pip && !fullscreen) DeArrowContributionSheet(vm)
            if (!pip) DownloadExportConsent()
            if (downloadDialog.video != null && !pip) DownloadDialog(vm)
            if (saveSheet.video != null && route != "sign-in" && !pip) SavePlaylistSheet(vm) { signIn() }
            if (!pip) { ClipDialogs(vm); ClipEditor(vm) }
          }
        }
      }
    }

}

@Composable private fun EmptyState(title: String, detail: String, action: String, onClick: () -> Unit) {
    LibraryEmptyState(title, detail, Icons.Default.PlayCircleOutline, action.takeIf(String::isNotBlank), onClick)
}
@Composable private fun ErrorCard(message: String, retry: () -> Unit) { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp)) { Text(message); Box { LibraryActionButton("Retry", onClick = retry) } } } }
internal fun resolved(base: String, path: String) = base.toHttpUrlOrNull()?.resolve(path)?.toString() ?: path
internal fun time(seconds: Long): String = DisplayFormats.clock(seconds)
@Composable internal fun VideoCard(vm: AppViewModel, video: Video, server: String, play: () -> Unit, channel: (String) -> Unit, signIn: () -> Unit, remove: (() -> Unit)? = null, audioPlay: (() -> Unit)? = null, removalLabel: String = "Remove", avatarOwner: String? = null,
    removeFromPlaylist: (() -> Unit)? = null, removeFromPlaylistEnabled: Boolean = true, aiGroup: AiPageGroup = vm.aiPageGroup(), libraryRow: Boolean = false) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = ApiContext(server, account, vm.store.contextGeneration)
    val indicator = WatchedIndicators.forVideo(video, watched.takeIf { it.context == context } ?: WatchedState())
    val compact = prefs.uiDensity == "compact" || libraryRow
    val aiState by vm.aiFilter.state.collectAsStateWithLifecycle()
    val warning = vm.aiDecision(video, aiGroup, aiState).warning
    val duration = if (video.live) "LIVE" else DisplayFormats.duration(video.duration)
    val warningCompact = compact || prefs.thinMode
    val fontScale = LocalDensity.current.fontScale
    val badgeInset = with(LocalDensity.current) { 12.sp.toDp() } + 22.dp
    val thumbnail: @Composable (Modifier) -> Unit = { thumbnailModifier ->
        if (!prefs.thinMode || warning != null) BoxWithConstraints(thumbnailModifier) {
          val naturalHeight = if (prefs.thinMode) 64.dp else maxWidth * 9f / 16f
          val imageHeight = if (warning == null) naturalHeight else maxOf(naturalHeight,
              (if (warningCompact) 96.dp else 144.dp) * fontScale + if (indicator.watched) badgeInset else 0.dp)
          Box(Modifier.fillMaxWidth().height(imageHeight).clip(AppTokens.media).background(MaterialTheme.colorScheme.surfaceContainer)) {
            ChannelSpoilerArtwork(vm, video, Modifier.fillMaxSize(), play) { revealed ->
            if (warning != null && !revealed) AiThumbnail(warning, Modifier.fillMaxSize(), compact = warningCompact,
                topInset = if (indicator.watched) badgeInset else 0.dp, bottomInset = if (duration.isNotBlank()) badgeInset else 4.dp)
            else AsyncImage(resolved(server, video.thumbnail.ifBlank { "/vi/${video.id}/mqdefault.jpg" }), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (indicator.watched) {
                Text("Watched", Modifier.align(Alignment.TopStart).padding(8.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("video-watched-${video.id}"), color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = PlayerTextShadow))
            }
            if (duration.isNotBlank()) Text(duration, Modifier.align(Alignment.BottomEnd).padding(8.dp).padding(horizontal = 6.dp, vertical = 3.dp),
                color = Color.White, fontSize = 12.sp, style = MaterialTheme.typography.labelSmall.copy(shadow = PlayerTextShadow))
            indicator.bar?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                    .testTag("video-progress-${video.id}").semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(indicator.percent?.div(100f) ?: 1f, 0f..1f)
                    }, color = MaterialTheme.colorScheme.primary, trackColor = Color.Black.copy(alpha = .3f), gapSize = 0.dp, drawStopIndicator = {})
            }
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
            if(remove != null && !libraryRow) AppInsetIconButton(onClick = remove) { Icon(Icons.Default.RemoveCircleOutline, "Remove ${maskedVideoTitle(vm, video)}") }
        }
    }
    val cardModifier = Modifier.fillMaxWidth().padding(horizontal = AppTokens.inset, vertical = if (compact) 8.dp else 12.dp)
        .testTag("video-card-${video.id}").semantics { if (indicator.description.isNotEmpty()) stateDescription = indicator.description }
    if (compact && (!libraryRow || LocalDensity.current.fontScale <= 1.3f)) Row(cardModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        thumbnail(Modifier.width(if (libraryRow) if (prefs.uiDensity == "compact") 80.dp else 112.dp else if (LocalDensity.current.fontScale > 1.3f) 96.dp else 128.dp))
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
    var transportWidth by remember(density.density) { mutableFloatStateOf(48*density.density) }
    val enabled = gesturesEnabled && !exploring && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val dismissOffset = if (presentation.target == PlayerPresentation.CLOSED) presentation.dismissDirection * width * presentation.fraction else 0f
    val drag = presentation.dragHandler(density.density,
        kotlin.math.abs(presentation.miniBounds.top - presentation.watchBounds.top).coerceAtLeast(160 * density.density),
        width, vm::cancelAccumulatedSeek, close)
    val currentDrag by rememberUpdatedState(drag)
    Box(Modifier.fillMaxWidth().clipToBounds().hiddenPlayerContent(presentation.mode != PlayerPresentation.MINI || presentation.active)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, if (presentation.allocatesMini) placeable.height else 0) {
                placeable.place(0, if (presentation.allocatesMini) 0 else -placeable.height)
            }
        }) {
        AppSurface(modifier = Modifier.graphicsLayer {
            translationX = dismissOffset
            alpha = presentation.miniAlpha
        }, shape = RoundedCornerShape(0.dp), role = AppSurfaceRole.MINI_PLAYER) {
            LinearProgressIndicator(progress={ if(playback.duration>0) (playback.position.toFloat()/playback.duration).coerceIn(0f,1f) else 0f },modifier=Modifier.fillMaxWidth().height(2.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("mini-player")
                .onGloballyPositioned { coordinates ->
                    origin = coordinates.positionInRoot()
                    width = coordinates.size.width.toFloat()
                }
                .pointerInput(playback.mediaId, occurrence.currentKey, presentation.mode, enabled, layoutDirection, windowSize) {
                    if (!enabled || presentation.mode != PlayerPresentation.MINI) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Buttons keep their entire 48dp hit areas; title/background drags can cancel a click.
                        val onButtons = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr)
                            down.position.x >= size.width - transportWidth - 48.dp.toPx() else down.position.x <= transportWidth + 48.dp.toPx()
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
                Box(Modifier.padding(start = 8.dp).size(96.dp, 54.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .playerAnchor { presentation.miniBounds = it.translate(Offset(-dismissOffset, 0f)) }
                    .clickable(enabled = presentation.mode == PlayerPresentation.MINI && !presentation.active,
                        onClickLabel = "Open player", onClick = open).testTag("mini-player-preview"))
                Column(Modifier.weight(1f).clickable(enabled = presentation.mode == PlayerPresentation.MINI && !presentation.active,
                    onClickLabel = "Open player", onClick = open).padding(horizontal = 8.dp)) {
                    if (playback.clip != null) DeArrowTitle(vm, playback.details?.video ?: playback.clip.video, MaterialTheme.typography.labelLarge, maxLines = 1, originalTitle = playback.clip.title, useDeArrow = false, playbackOccurrence = occurrence.currentKey)
                    else playback.details?.video?.let { DeArrowTitle(vm, it, MaterialTheme.typography.labelLarge, maxLines = 1, playbackOccurrence = occurrence.currentKey, offline = playback.downloadId != null) }
                    when {
                        playback.error != null -> Text("Playback failed",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        playback.loading || playback.buffering -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                            Text(if(playback.loading) "Loading" else "Buffering",style=MaterialTheme.typography.bodySmall)
                        }
                        else -> Text(playback.details?.video?.author.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
                val ended = playback.playerState == Player.STATE_ENDED
                Box(Modifier.onGloballyPositioned { transportWidth=it.size.width.toFloat() }) {
                if(playback.error!=null) TextButton(vm::retryPlayback,
                    Modifier.heightIn(min=48.dp).testTag("mini-player-retry"),enabled=presentation.mode==PlayerPresentation.MINI && !presentation.active && !playback.loading) {Text("Retry")}
                else AppInsetIconButton(onClick = toggle, enabled = playback.canPlay && presentation.mode == PlayerPresentation.MINI && !presentation.active, modifier = Modifier.size(48.dp)) {
                    Icon(if (ended) Icons.Default.Replay else if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (ended) "Replay" else if (playback.playWhenReady) "Pause" else "Play")
                }
                }
                AppInsetIconButton(onClick = close, enabled = presentation.mode == PlayerPresentation.MINI && !presentation.active, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Close player") }
            }
        }
    }
}
private enum class WatchPane { PHONE, PRIMARY, SUPPORTING }

@Composable private fun WatchScreen(vm: AppViewModel, playback: PlaybackState, modifier: Modifier,
    presentation: PlayerPresentationState, detailsList: androidx.compose.foundation.lazy.LazyListState,
    resize: WatchPlayerResizeState, chapterPanel: ChapterPanelState,
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
    val queueOpen by vm.queueExpanded.collectAsStateWithLifecycle()
    val drawerOpen = commentsOpen || chapterPanel.open || chatDocked || queueOpen

    BoxWithConstraints(modifier.fillMaxSize().testTag("watch-content")
        .pointerInput(Unit) {
            // Consume otherwise unhandled touches so the revealed browse layer cannot receive watch-page taps.
            awaitEachGesture { awaitFirstDown().consume(); waitForUpOrCancellation()?.consume() }
        }) {
        val sideBySide = maxWidth >= 840.dp && maxWidth * .62f >= 480.dp && maxWidth * .38f >= 320.dp
        val playerWidth = (if (sideBySide) maxWidth * .62f - 16.dp else maxWidth - 16.dp).value
        val expandedHeight = playback.geometry.embeddedHeight(playerWidth, maxHeight.value, false).let { if(sideBySide) minOf(it,maxHeight.value*.6f) else it }
        val compactHeight = minOf(expandedHeight,playback.geometry.embeddedHeight(playerWidth, maxHeight.value, false, 1f))
        val scrollConnection = rememberWatchPlayerScrollConnection(detailsList, resize, expandedHeight > compactHeight && !drawerOpen)
        val playerHeight = if(sideBySide) (expandedHeight+(compactHeight-expandedHeight)*resize.progress).dp else if (chatDocked) maxHeight * (1f - chatAppearance.belowFraction) else playback.geometry.embeddedHeight(playerWidth, maxHeight.value, drawerOpen, resize.progress).dp
        val supportingList = key(queue.token,queue.currentKey) { rememberLazyListState() }
        val detailsContent: @Composable ColumnScope.(WatchPane) -> Unit = { pane ->
            val tools=pane!=WatchPane.PRIMARY
            val information=pane!=WatchPane.SUPPORTING
            if (tools && queueOpen && sideBySide) Column(Modifier.weight(1f).fillMaxWidth()) {
                LibraryToolbar("Playback queue", statusInset = false, actions = { AppIconButton(Icons.Default.Close, "Close playback queue") { vm.queueExpanded.value = false } })
                PlaybackQueuePanel(vm, signIn, channel, fullHeight = true)
            }
            else if (tools && chapterPanel.open) ChaptersPanel(chapters, playback.position, chapterPanel,
                playback.seekable && !playback.loading && playback.error == null, chapterPanel::close, vm::seekTo,
                Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp))
            else if (tools && chatDocked) ChatReplayPanel(vm, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), chatSettings)
            else if (tools && commentsOpen) CommentsDrawer(vm, comments, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), channel, ::openWatchLink)
            else if (information && playback.clip != null) LazyColumn(Modifier.weight(1f).testTag("clip-details-list"), state = detailsList) {
                item { ClipDetails(vm, playback.clip, closePlayer, channel) }
            }
            else LazyColumn(Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)
                .then(if(information) Modifier.nestedScroll(scrollConnection) else Modifier)
                .testTag(if(information) "watch-details-list" else "watch-supporting-list"), state = if(information) detailsList else supportingList) {
                if(information) playback.details?.let { details ->
                    item(key = "watch:metadata") {
                      WatchContent(Modifier.fillMaxWidth().testTag("watch-metadata")) {
                      Column(Modifier.padding(horizontal = AppTokens.inset, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (details.video.membersOnly) MembersBadge(details.video.id)
                        val titleStyle = MaterialTheme.typography.titleLarge
                        DeArrowTitle(vm, details.video, titleStyle, playbackOccurrence = queue.currentKey, offline = playback.downloadId != null)
                        VideoNotices(details)
                        VideoMetadataLine(details.video, likes = details.likes)
                        if (playback.downloadId != null) DownloadChannelIdentity(details.video)
                        else WatchChannelIdentity(details.video, vm.store.server, prefs.thinMode,
                            subscriptions.any { it.id == details.video.channelId }, subscribe = {
                                if (vm.account.value == null) { vm.prepareChannelSubscription(details.video.channelId); signIn() }
                                else vm.toggleSubscribe(details.video.channelId)
                            }, channel = channel, verified = details.authorVerified == true, subscribers = details.subscribers,
                                subscriptionBusy = details.video.channelId in subscriptionChannels.busy,
                                subscriptionKnown = account == null || subscriptionChannels.context == vm.api.context() && subscriptionChannels.loaded,
                                subscriptionChecking = subscriptionChannels.loading,
                                subscriptionError = subscriptionChannels.actionErrors[details.video.channelId] ?: subscriptionChannels.error.takeIf { !subscriptionChannels.loaded })
                        if (playback.downloadId == null) WatchPrimaryActions(vm, details, { add(details.video) }, signIn)
                        blocked.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (prefs.showYoutubeComments && playback.downloadId == null) CommentsEntry(comments) { chapterPanel.close(); vm.queueExpanded.value = false; vm.openComments() }
                        WatchDisclosureRow("Description", Icons.AutoMirrored.Filled.Notes,
                            modifier = Modifier.testTag("watch-description-toggle"), expanded = description,
                            actionLabel = if (description) "Hide description" else "Show description") { describe(!description) }
                        VideoDescription(details, queue.currentKey.orEmpty(), vm.store.server, description, ::openWatchLink)
                        if (queue.current?.linkPlayback?.invalidEnd == true) Text("Invalid end boundary ignored.", style = MaterialTheme.typography.bodySmall)
                    } } }
                }
                if (information && queue.hasExplicitQueue) item(key = "watch:queue") {
                    ActionRow("Playback queue", Modifier.padding(horizontal = AppTokens.inset).testTag("playback-queue-header"),
                        detail = queue.source?.title, icon = Icons.AutoMirrored.Filled.PlaylistPlay, actionLabel = "Open playback queue") {
                        chapterPanel.close(); vm.closeComments(); vm.closeChat(); vm.queueExpanded.value = true
                    }
                }
                if(pane!=WatchPane.PRIMARY && playback.clip==null) playback.details?.let { details ->
                    if (prefs.relatedVideos) {
                        item(key = "watch:up-next") { Text("Up next", Modifier.padding(horizontal = AppTokens.inset, vertical = 20.dp)
                            .testTag("watch-up-next"), style = MaterialTheme.typography.titleMedium) }
                        itemsIndexed(vm.visibleVideos(details.recommendations, ContentSurface.RECOMMENDATIONS, AiPageGroup.RECOMMENDATIONS, aiState), key = { index,video -> "recommendation:$index:${video.id}" },contentType={_,_->"video"}) { _,video -> VideoCard(vm, video, vm.store.server, { play(video) }, channel, signIn, aiGroup = AiPageGroup.RECOMMENDATIONS) }
                    }
                }
            }
        }
        if (queueOpen && !sideBySide) LibraryPanel("Playback queue", { vm.queueExpanded.value = false }, closeLabel = "Close playback queue") { body, _ ->
            Box(body) { PlaybackQueuePanel(vm, signIn, channel, fullHeight = true) }
        }
        if (sideBySide) Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(.62f).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().padding(horizontal=8.dp).height(playerHeight).playerAnchor { presentation.watchBounds = it })
                detailsContent(WatchPane.PRIMARY)
            }
            Column(Modifier.weight(.38f).fillMaxHeight()) { detailsContent(WatchPane.SUPPORTING) }
        } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(playerHeight)
                .playerAnchor { presentation.watchBounds = it })
            detailsContent(WatchPane.PHONE)
        }
    }

}


@Composable private fun FiltersDialog(vm: AppViewModel, dismiss: () -> Unit, visibilityOnly: Boolean = false) {
    val context = remember { vm.api.context() }
    val searchType by vm.searchType.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val savedVisibility by vm.searchVisibility.collectAsStateWithLifecycle()
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val browse by vm.browse.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(vm.sort) }
    var date by remember { mutableStateOf(vm.date) }
    var duration by remember { mutableStateOf(vm.durationFilter) }
    val visibility=remember(savedVisibility,edits) {vm.requestedSearchVisibility()}
    val edit=edits["profile:searchVisibility"]
    fun refresh() { if(vm.api.context()==context) vm.refresh() }
    LibraryPanel("Search filters", dismiss, closeLabel = "Close search filters") { body, top ->
        Column(body.verticalScroll(rememberScrollState()).padding(start = AppTokens.inset, end = AppTokens.inset,
            top = top + 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!visibilityOnly) {
                if (searchType != SearchType.CHANNELS) Choice("Sort", listOf("relevance", "views"), sort) { if(it!=sort) {sort=it;vm.sort=it;refresh()} }
                if (searchType.videoFilters) Choice("Uploaded", listOf("", "hour", "today", "week", "month", "year"), date) { if(it!=date) {date=it;vm.date=it;refresh()} }
                if (searchType.videoFilters) Choice("Duration", listOf("", "short", "long"), duration) { if(it!=duration) {duration=it;vm.durationFilter=it;refresh()} }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("search-show-members")
                .toggleable(visibility.showMembers ?: prefs.showMemberVideos,role=androidx.compose.ui.semantics.Role.Checkbox) {
                    vm.saveSearchVisibility(visibility.copy(showMembers=it),context)
                },verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.showMembers ?: prefs.showMemberVideos,null)
                Text("Show members-only videos",Modifier.weight(1f))
            }
            Text(if (visibility.showMembers == null) "Using browsing default" else "Search override on this device", style = MaterialTheme.typography.bodySmall)
            ActionRow("Use browsing default", trailingIcon = Icons.Default.RestartAlt,
                modifier = Modifier.testTag("search-members-reset")) { vm.saveSearchVisibility(visibility.copy(showMembers=null),context) }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("search-include-blocked")
                .toggleable(visibility.includeBlocked,role=androidx.compose.ui.semantics.Role.Checkbox) {
                    vm.saveSearchVisibility(visibility.copy(includeBlocked=it),context)
                },verticalAlignment = Alignment.CenterVertically) {
                Checkbox(visibility.includeBlocked,null)
                Text("Include blocked channels",Modifier.weight(1f))
            }
            PreferenceSaveFeedback(edit?.status,edit?.error,{vm.preferenceSaves.retry("profile:searchVisibility")},{vm.preferenceSaves.revert("profile:searchVisibility")})
            Text(if(browse.loading) "Updating results…" else "Filters apply immediately",
                Modifier.semantics { liveRegion=androidx.compose.ui.semantics.LiveRegionMode.Polite },style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun Choice(label: String, values: List<String>, selected: String, update: (String) -> Unit) {
    DropdownChoiceRow(label, values.map { it to it.ifBlank { "Any" }.replace('_', ' ') }, selected, change = update)
}
