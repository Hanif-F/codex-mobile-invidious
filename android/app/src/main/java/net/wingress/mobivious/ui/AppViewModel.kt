package net.wingress.mobivious.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.wingress.mobivious.MobiviousApplication
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackRules
import net.wingress.mobivious.player.PlaybackService
import net.wingress.mobivious.player.SeekAccumulator
import net.wingress.mobivious.player.PendingSeek
import net.wingress.mobivious.player.StreamCatalog
import net.wingress.mobivious.player.StreamKey
import net.wingress.mobivious.player.VideoSelection
import net.wingress.mobivious.player.VideoGeometry
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class BrowseState(val title: String = "For you", val videos: List<Video> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val page: Int = 1, val continuation: String = "", val end: Boolean = false,
    val history: HistoryPage? = null, val clips: List<Clip> = emptyList(), val lists: List<Playlist> = emptyList(),
    val posts: List<CommunityPost> = emptyList(), val channels: List<Channel> = emptyList(),
    val position: CommentPosition = CommentPosition(), val retryMore: Boolean = false)
data class PostDetailState(val link: PostLink? = null, val context: ApiContext? = null,
    val post: CommunityPost? = null, val loading: Boolean = false, val error: String? = null)
data class LinkResolutionState(val link: ChannelLink? = null, val context: ApiContext? = null,
    val loading: Boolean = false, val error: String? = null)
data class PlaybackState(val details: VideoDetails? = null, val loading: Boolean = false, val error: String? = null,
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0, val buffering: Boolean = false,
    val mediaId: String = "", val playWhenReady: Boolean = false, val playerState: Int = Player.STATE_IDLE,
    val bufferedPosition: Long = 0, val seekable: Boolean = false, val live: Boolean = false,
    val speed: Float = 1f, val tracks: Tracks = Tracks.EMPTY, val selection: TrackSelectionParameters? = null,
    val canPlay: Boolean = false, val canSetSpeed: Boolean = false, val canSelectTracks: Boolean = false,
    val canRefresh: Boolean = false, val videoSelection: VideoSelection = VideoSelection(),
    val geometry: VideoGeometry = VideoGeometry(), val clip: Clip? = null)
data class DeArrowContributionState(val open: Boolean = false, val videoId: String = "", val context: ApiContext? = null,
    val titles: List<DeArrowSubmission> = emptyList(), val busy: Boolean = false, val loaded: Boolean = false,
    val draft: String = "", val review: Boolean = false, val acknowledgements: Set<Int> = emptySet(), val status: String? = null)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MobiviousApplication
    val store = app.store
    val api = app.api
    val account = store.account
    val accountBusy = MutableStateFlow(false)
    val offline = app.offline
    val watched = app.watched.state
    val blocked = app.blocked.state
    val searchVisibility = MutableStateFlow(store.searchVisibility(api.context()))
    val browse = MutableStateFlow(BrowseState())
    val linkResolution = MutableStateFlow(LinkResolutionState())
    private var linkResolutionJob: Job? = null
    val searchInput = MutableStateFlow(SearchInput())
    val scopedSearch = MutableStateFlow(SearchInput())
    val browseReset = MutableStateFlow(0L)
    val playback = MutableStateFlow(PlaybackState())
    val controller = MutableStateFlow<MediaController?>(null)
    val pendingSeek = MutableStateFlow<PendingSeek?>(null)
    private val seekAccumulator = SeekAccumulator()
    private var seekJob: Job? = null
    private var seekContext: ApiContext? = null
    val channel = MutableStateFlow<Channel?>(null)
    val channelTab = MutableStateFlow<ChannelTab?>(null)
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlist = MutableStateFlow<Playlist?>(null)
    val playlistBusy = MutableStateFlow<Set<String>>(emptySet())
    val playlistErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val rss = MutableStateFlow(RssState())
    val playlistSearch = MutableStateFlow(false)
    val channelPlaylistSort = MutableStateFlow("last")
    val channelVideoSort = MutableStateFlow(ChannelSort.NEWEST)
    val postDetail = MutableStateFlow(PostDetailState())
    val clipResolution = MutableStateFlow(ClipResolutionState())
    val clipEditor = MutableStateFlow(ClipEditorState())
    val clipOpened = MutableStateFlow(0L)
    val clipDelete = MutableStateFlow<Clip?>(null)
    val clipDeleteBusy = MutableStateFlow(false)
    val clipDeleteError = MutableStateFlow<String?>(null)
    private var clipJob: Job? = null
    private var clipEditorJob: Job? = null
    private var clipDeleteJob: Job? = null
    private var pendingClipEditor: Pair<String, String>? = null
    val postNavigation = MutableStateFlow(0L)
    private val postCommentController = CommentsController(viewModelScope, api::context, api::comments, ::friendly)
    val postComments = postCommentController.state
    private var pendingPlaylistSubscription: Pair<String, Playlist>? = null
    private var playlistRevision = 0L
    private var playlistSeed: String? = null
    private var playlistLink: VideoLink? = null
    private var rssJob: Job? = null
    private val subscriptionsController = SubscriptionsController(viewModelScope, api::context, api::subscriptionDirectory, ::friendly,
        store::subscriptionSort, store::subscriptionSort)
    val subscriptionChannels = subscriptionsController.state
    private var subscriptionChannelParent = false
    private var subscriptionFeedSearch = SearchInput()
    private val commentController = CommentsController(viewModelScope, api::context,
        { video, sort, continuation, context -> api.comments(video, sort, continuation, context) }, ::friendly)
    val comments = commentController.state
    val preferences = MutableStateFlow(store.initialPreferences())
    val chatAppearance = MutableStateFlow(store.chatAppearance())
    private val chatController = ChatReplayController(viewModelScope, api::context, api::chatReplay,
        { id, ctx -> if (ctx.account == null) store.chatTiming(id, ctx) else api.chatTiming(id, ctx) },
        { id, offset, ctx -> if (ctx.account == null) store.chatTiming(id, offset, ctx) else api.chatTiming(id, offset, ctx) }, ::friendly)
    val chatReplay = chatController.state
    private var chatAppearanceContext = api.context()
    val sponsorBlock = MutableStateFlow(SponsorBlockPlayback())
    val sponsorSettingsChannel = MutableStateFlow<String?>(null)
    fun openSponsorBlock(channelId: String = "") { sponsorSettingsChannel.value = channelId; refreshSharedSettings() }
    val dearrowTitles = app.dearrowTitles
    val dearrowIdentity = MutableStateFlow<DeArrowIdentity?>(null)
    val dearrowIdentityError = MutableStateFlow<String?>(null)
    val dearrowContribution = MutableStateFlow(DeArrowContributionState())
    val message = MutableStateFlow<String?>(store.storageError.value)
    val blockUndo = MutableStateFlow<BlockUndo?>(null)
    val saveSheet = MutableStateFlow(PlaylistSaveState())
    val queue = app.playbackQueue
    val queueExpanded = MutableStateFlow(queue.value.hasExplicitQueue)
    var tab = PreferenceRules.destination(preferences.value.defaultHome, account.value != null).first
    val navigation = MutableStateFlow(tab to "")
    var route = ""
    var launchIntentConsumed = false
    var query: String
        get() = searchInput.value.submitted
        set(value) { searchInput.value = SearchInput(value, value) }
    val discovery = MutableStateFlow(PreferenceRules.destination(preferences.value.defaultHome, account.value != null).second)
    var region = preferences.value.region
    var sort = "relevance"
    var date = ""
    var durationFilter = ""
    private var browseJob: Job? = null
    private var contributionJob: Job? = null
    private var preferenceGeneration = 0L
    private val preferenceWrites = Mutex()
    private val playerDefaultWrites = Mutex()
    private var preferencesContext: ApiContext? = null
    private var identityGeneration = 0L
    private var browseGeneration = 0
    private var requestedVideo: String? = null
    private var homeAppliedContext: ApiContext? = null
    private var navigationRevision = 0L
    private data class BrowseReturn(val tab: String, val route: String, val browse: BrowseState,
        val channel: Channel?, val channelTab: ChannelTab?, val playlist: Playlist?, val seed: String?,
        val playlistSort: String, val videoSort: ChannelSort, val postDetail: PostDetailState, val postComments: CommentsState,
        val scopedSearch: SearchInput, val discovery: String, val context: ApiContext,
        val channelSearchOrigin: BrowseReturn?, val subscriptionParent: Boolean, val playlistLink: VideoLink?,
        val preferences: AccountPreferences)
    private fun captureBrowse() = BrowseReturn(tab, route, browse.value, channel.value, channelTab.value,
        playlist.value, playlistSeed, channelPlaylistSort.value, channelVideoSort.value, postDetail.value,
        postComments.value, scopedSearch.value, discovery.value, api.context(), channelSearchOrigin, subscriptionChannelParent, playlistLink, preferences.value)
    private val browseReturns = mutableListOf<BrowseReturn>()
    private val tabBrowse = mutableMapOf<String, BrowseReturn>()
    private var channelSearchOrigin: BrowseReturn? = null
    private var searchReturn: BrowseReturn? = null
    private var signInReturn: BrowseReturn? = null
    fun clearNavigationReturns() { signInReturn = null; searchReturn = null; browseReturns.clear(); channelSearchOrigin = null }
    fun selectTab(name: String) {
        if (name !in PreferenceRules.tabs) return
        if (route.isEmpty() && tab in PreferenceRules.tabs) tabBrowse[tab] = captureBrowse()
        clearNavigationReturns()
        val saved = tabBrowse[name]?.takeIf { it.context == api.context() }
        if (saved != null) restoreBrowse(saved) else navigate(name)
    }
    fun settingsOpened() { navigationRevision++; homeAppliedContext = api.context() }
    private fun browsingChanged(before: AccountPreferences, after: AccountPreferences): Boolean =
        listOf(before.region, before.maxResults, before.feedSort, before.latestOnly, before.unseenOnly,
            before.notificationsOnly, before.showMemberVideos) !=
        listOf(after.region, after.maxResults, after.feedSort, after.latestOnly, after.unseenOnly,
            after.notificationsOnly, after.showMemberVideos)
    private fun currentBrowseAffected(before: AccountPreferences, after: AccountPreferences,
        selectedTab: String = tab, selectedRoute: String = route): Boolean = when {
        selectedRoute == "history" -> before.maxResults != after.maxResults
        selectedRoute.startsWith("playlist:") -> before.showMemberVideos != after.showMemberVideos
        selectedRoute.isNotEmpty() -> false
        selectedTab == "Trending" -> before.region != after.region || before.showMemberVideos != after.showMemberVideos
        selectedTab == "Popular" -> before.showMemberVideos != after.showMemberVideos
        selectedTab == "Subscriptions" -> browsingChanged(before.copy(region = after.region), after)
        else -> false
    }
    private fun invalidateSavedTabs(before: AccountPreferences, after: AccountPreferences) {
        tabBrowse.keys.removeAll { currentBrowseAffected(before, after, it, "") }
    }
    val restoredBrowse = MutableStateFlow(0L)
    val authenticationFinished = MutableStateFlow(0L)
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (command.customAction == PlaybackService.SPONSOR_STATE) receiveSponsorState(args)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).buildAsync()
    init {
        viewModelScope.launch { store.storageError.collect { it?.let { error -> message.value = error } } }
        future.addListener({
            runCatching {
                controller.value = future.get().apply {
                    addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) { updatePlayback() }
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) { updatePlayback() }
                        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { cancelAccumulatedSeek(); chatController.update(newPosition.positionMs, controller.value?.duration?.coerceAtLeast(0) ?: 0, true) }
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (pendingSeek.value?.mediaId != mediaItem?.mediaId) cancelAccumulatedSeek(false)
                        }
                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { if (playWhenReady) cancelAccumulatedSeek(false) }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { cancelAccumulatedSeek(false); playback.value = playback.value.copy(error = "Playback failed. Retry to refresh the stream.", buffering = false) }
                        override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                            if (error == null) playback.value = playback.value.copy(error = null)
                        }
                    })
                    if (currentMediaItem == null) setPlaybackSpeed(preferences.value.speed)
                    val stateContext = api.context()
                    val state = sendCustomCommand(SessionCommand(PlaybackService.SPONSOR_STATE, Bundle.EMPTY), Bundle.EMPTY)
                    state.addListener({ if (api.context() == stateContext && sponsorBlock.value.token.isEmpty()) runCatching { receiveSponsorState(state.get().extras) } }, ContextCompat.getMainExecutor(application))
                }
            }.onFailure { message.value = "Unable to connect to the player." }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch { while (isActive) { delay(500); updatePlayback() } }
        viewModelScope.launch { var previous = queue.value; queue.collect { state ->
            if (state.token != previous.token || state.hasExplicitQueue != previous.hasExplicitQueue)
                queueExpanded.value = state.hasExplicitQueue
            if (previous.source?.id == state.source?.id && previous.source?.count != state.source?.count && state.source != null) {
                if (route == "playlist:${state.source.id}") refresh()
                if (account.value != null) refreshAccount()
            }
            previous = state
            if (clipEditor.value.open && clipEditor.value.occurrence != state.currentKey) closeClipEditor()
            requestedVideo = state.current?.video?.id
            val old = playback.value.details?.video?.id
            playback.value = playback.value.copy(details = state.details, loading = state.loading, error = state.error, videoSelection = state.videoSelection, clip = state.current?.clip)
            if (old != state.details?.video?.id) {
                playback.value = playback.value.copy(geometry = VideoGeometry())
                cancelAccumulatedSeek(false)
                contributionJob?.cancel(); dearrowContribution.value = DeArrowContributionState()
            }
            syncComments(); syncChat(); state.details?.video?.id?.let(::ensureDeArrow); syncDeArrowMetadata(); syncSponsorSettings()
        } }
        viewModelScope.launch { app.playbackContext.collect { changedContext ->
            val it = changedContext?.account
            clipJob?.cancel(); clipEditorJob?.cancel(); clipDeleteJob?.cancel(); clipResolution.value = ClipResolutionState(); clipEditor.value = ClipEditorState()
            clipDelete.value = null; clipDeleteBusy.value = false; clipDeleteError.value = null
            if (pendingClipEditor?.first != api.context().server) pendingClipEditor = null
            chatController.bind("", "", false)
            commentController.bind(null, false)
            subscriptionsController.reset(); subscriptionChannelParent = false; subscriptionFeedSearch = SearchInput()
            dismissLinkResolution(); browseJob?.cancel(); browseGeneration++; browse.value = BrowseState()
            blockUndo.value = null
            playlistRevision++; playlistBusy.value = emptySet(); playlistErrors.value = emptyMap()
            playlist.value = null; playlistSeed = null; playlistLink = null; playlists.value = emptyList()
            postDetail.value = PostDetailState(); postCommentController.bind(null); browseReturns.clear(); tabBrowse.clear(); channelSearchOrigin = null
            rssJob?.cancel(); rss.value = RssState()
            val pendingList = pendingPlaylistSubscription
            if (it != null && pendingList?.first == api.context().server) {
                pendingPlaylistSubscription = null
                subscribePlaylist(pendingList.second, true)
            } else if (pendingList?.first != api.context().server) pendingPlaylistSubscription = null
            val pendingSave = saveSheet.value
            if (pendingSave.video != null) {
                if (pendingSave.context?.server == api.context().server) { saveSheet.value = PlaylistSaveState(pendingSave.video, api.context()); if (it != null) loadSaveLists() }
                else saveSheet.value = PlaylistSaveState()
            }
            app.blocked.reset(); searchVisibility.value = store.searchVisibility(api.context())
            searchInput.value = SearchInput(); scopedSearch.value = SearchInput()
            cancelAccumulatedSeek(false)
            sponsorSettingsChannel.value = null
            sponsorBlock.value = SponsorBlockPlayback()
            preferenceGeneration++; identityGeneration++
            dearrowTitles.clear(); contributionJob?.cancel(); dearrowContribution.value = DeArrowContributionState()
            dearrowIdentity.value = null; dearrowIdentityError.value = null
            preferencesContext = if (it == null) api.context() else null
            preferences.value = store.initialPreferences()
            region = preferences.value.region
            chatAppearanceContext = api.context(); chatAppearance.value = store.chatAppearance(chatAppearanceContext)
            if (it != null) refreshAccount() else playlists.value = emptyList()
            refresh()
        } }
        viewModelScope.launch { combine(preferences, dearrowTitles.titles) { prefs, _ -> prefs }.collect {
            if (it.dearrowEnabled) playback.value.details?.video?.id?.let(::ensureDeArrow)
            else dearrowTitles.clear()
            syncDeArrowMetadata()
        } }
        viewModelScope.launch { preferences.collect { syncComments(); syncChat(); syncSponsorSettings(); syncHistorySettings(); if (preferencesContext == api.context()) queueCommand(PlaybackService.QUEUE_SETTINGS) { putString("settings", it.json().toString()) } } }
        viewModelScope.launch { watched.map { it.context to it.historyRevision }.distinctUntilChanged().collect { (context, revision) ->
            if (revision > 0 && context == api.context() && subscriptionChannels.value.loaded) refreshSubscriptions()
        } }
        viewModelScope.launch { preferences.map { it.showMemberVideos }.distinctUntilChanged().drop(1).collect {
            if (preferencesContext == api.context() && api.context().account != null && subscriptionChannels.value.loaded) refreshSubscriptions()
        } }
        refresh()
    }
    private fun receiveSponsorState(args: Bundle) {
        runCatching { SponsorBlockPlayback.parse(JSONObject(args.getString("state") ?: "{}")) }.onSuccess {
            sponsorBlock.value = it
            syncSponsorSettings()
        }
    }
    private fun syncSponsorSettings() {
        val state = sponsorBlock.value
        if (preferencesContext != api.context()) return
        val video = playback.value.details?.video ?: return
        if (state.mediaId != video.id || state.token.isEmpty()) return
        val settings = preferences.value.sponsorBlock.effective(video.channelId)
        if (state.settings != settings) sponsorCommand(PlaybackService.SPONSOR_CONFIGURE, settings = settings)
    }
    fun sponsorCommand(action: String, segment: String? = null, settings: SponsorBlockSettings? = null) {
        if (action == PlaybackService.SPONSOR_SKIP) cancelAccumulatedSeek()
        val state = sponsorBlock.value
        controller.value?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle().apply {
            putString("mediaId", state.mediaId); putString("token", state.token)
            putString("segment", segment); settings?.let { putString("settings", it.json().toString()) }
        })
    }
    private fun updatePlayback() {
        val p = controller.value ?: return
        val mediaId = p.currentMediaItem?.mediaId.orEmpty()
        val size = p.videoSize
        val decoded = VideoGeometry(mediaId, size.width, size.height, size.pixelWidthHeightRatio)
        val geometry = if (decoded.ratio != null) decoded else playback.value.geometry.takeIf { it.mediaId == mediaId } ?: decoded
        playback.value = playback.value.copy(
            playing = p.isPlaying, playWhenReady = p.playWhenReady, playerState = p.playbackState,
            mediaId = p.currentMediaItem?.mediaId.orEmpty(), position = p.currentPosition.coerceAtLeast(0),
            duration = p.duration.coerceAtLeast(0), bufferedPosition = p.bufferedPosition.coerceAtLeast(0),
            buffering = p.playbackState == Player.STATE_BUFFERING, live = p.isCurrentMediaItemLive,
            seekable = p.isCurrentMediaItemSeekable && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            speed = p.playbackParameters.speed, tracks = p.currentTracks, selection = p.trackSelectionParameters,
            canPlay = p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            canSetSpeed = p.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH),
            canSelectTracks = p.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS),
            canRefresh = p.mediaItemCount > 0 && p.isCommandAvailable(Player.COMMAND_STOP) && p.isCommandAvailable(Player.COMMAND_PREPARE), geometry = geometry)
        syncChat()
        if (p.currentMediaItem?.mediaId == chatReplay.value.videoId) chatController.update(playback.value.position, playback.value.duration)
        val state = queue.value
        // Account changes replace the service occurrence before its controller timeline arrives.
        // Wait for that occurrence so the editor captures its actual position and playing intent.
        if (pendingClipEditor != null && account.value != null && state.context == api.context() &&
            pendingClipEditor == (api.context().server to state.details?.video?.id) && !state.loading && state.current?.clip == null &&
            p.playbackState == Player.STATE_READY && p.mediaMetadata.extras?.getString("occurrence") == state.currentKey) {
            pendingClipEditor = null
            openClipEditor()
        }
    }
    fun refreshAccount() {
        refreshSharedSettings()
        refreshSubscriptions()
        refreshPlaylists()
    }
    private fun refreshPlaylists() {
        val context = api.context(); val revision = playlistRevision
        action { val lists = api.playlists(context); if (api.context() == context && revision == playlistRevision) playlists.value = lists }
    }
    fun refreshSubscriptions() = subscriptionsController.refresh()
    fun searchSubscriptionChannels(query: String) = subscriptionsController.search(query)
    fun sortSubscriptionChannels(sort: SubscriptionSort, context: ApiContext = api.context()) {
        if (context != api.context()) return
        try { subscriptionsController.sort(sort) } catch (_: CancellationException) { } catch (e: Exception) { message.value = friendly(e) }
    }
    fun refreshSharedSettings() {
        val context = api.context()
        refreshBlockedChannels()
        refreshWatched()
        if (context.account == null) { preferencesContext = context; preferences.value = store.guestDeArrow(); syncSponsorSettings(); syncHistorySettings(); refreshWatched(); return }
        val prefsGeneration = ++preferenceGeneration
        val identityVersion = ++identityGeneration
        val navigationAtRequest = navigationRevision
        action { val value = api.preferences(context); if (api.context() == context && prefsGeneration == preferenceGeneration) {
            val refreshCurrent = currentBrowseAffected(preferences.value, value)
            invalidateSavedTabs(preferences.value, value)
            preferencesContext = context; preferences.value = value; region = value.region; syncSponsorSettings(); syncHistorySettings(); refreshWatched()
            val applyHome = homeAppliedContext != context && navigationRevision == navigationAtRequest && route != "sign-in" && signInReturn == null
            homeAppliedContext = context
            if (applyHome) openDefaultHome()
            else if (refreshCurrent) refresh()
        } }
        viewModelScope.launch {
            try { val identity = api.dearrowIdentity(context); if (api.context() == context && identityVersion == identityGeneration) { dearrowIdentity.value = identity; dearrowIdentityError.value = null } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (api.context() == context && identityVersion == identityGeneration) { dearrowIdentity.value = null; dearrowIdentityError.value = friendly(e) } }
        }
    }
    fun ensureDeArrow(id: String) { if (preferences.value.dearrowEnabled) dearrowTitles.ensure(id) }
    fun refreshBlockedChannels() { viewModelScope.launch { app.blocked.refresh() } }
    fun toggleBlocked(id: String, name: String, context: ApiContext = api.context()) {
        if (context != api.context()) return
        val block = id !in blocked.value.ids
        action {
            app.blocked.setBlocked(context, id, name, block)
            if (api.context() == context) { message.value = if (block) "Channel blocked" else "Channel unblocked"
                blockUndo.value = if (block) BlockUndo(id, name, context, SystemClock.elapsedRealtime() + 5000) else null
                refreshBlockedChannels() }
        }
    }
    fun saveSearchVisibility(value: SearchVisibility, context: ApiContext = api.context()): Boolean {
        return try { store.saveSearchVisibility(context, value); searchVisibility.value = value; true }
        catch (_: CancellationException) { false } catch (e: Exception) { message.value = friendly(e); false }
    }
    fun contentSurface(): ContentSurface = when {
        route == "subscription-channels" -> ContentSurface.SUBSCRIPTIONS
        route == "history" -> ContentSurface.HISTORY
        route.startsWith("channel:") -> ContentSurface.CHANNEL
        route.startsWith("playlist:") -> ContentSurface.PLAYLIST
        tab == "Search" -> ContentSurface.SEARCH
        tab == "Subscriptions" && scopedSearch.value.submitted.isNotBlank() -> ContentSurface.SEARCH
        tab == "Subscriptions" -> ContentSurface.SUBSCRIPTIONS
        else -> ContentSurface.DISCOVERY
    }
    fun visibleVideos(videos: List<Video>, surface: ContentSurface = contentSurface()): List<Video> =
        ContentVisibility.filter(videos, surface, preferences.value.showMemberVideos, searchVisibility.value,
            blocked.value.takeIf { it.context == api.context() }?.ids.orEmpty())
    fun visiblePlaylists(lists: List<Playlist>): List<Playlist> = if (contentSurface() == ContentSurface.SEARCH && !searchVisibility.value.includeBlocked)
        lists.filter { it.channelId !in blocked.value.takeIf { state -> state.context == api.context() }?.ids.orEmpty() } else lists
    fun displayTitle(video: Video): String = if (preferences.value.dearrowEnabled) dearrowTitles.titles.value[video.id] ?: video.title else video.title
    private fun syncDeArrowMetadata() {
        if (queue.value.current?.clip != null) return
        val video = playback.value.details?.video ?: return
        val p = controller.value ?: return
        val title = displayTitle(video)
        if (p.currentMediaItem?.mediaId == video.id && p.mediaMetadata.title?.toString() != title) {
            p.sendCustomCommand(SessionCommand(PlaybackService.SET_DISPLAY_TITLE, Bundle.EMPTY), Bundle().apply { putString("mediaId", video.id); putString("title", title) })
        }
    }
    fun navigate(tab: String, route: String = "", loadContent: Boolean = true, rememberOrigin: Boolean = false) {
        if (this.route.isEmpty() && this.tab in PreferenceRules.tabs) tabBrowse[this.tab] = captureBrowse()
        navigationRevision++
        dismissClipResolution(); closeClipEditor()
        dismissLinkResolution()
        if ((this.tab != tab || this.route != route) && (rememberOrigin || route.isNotEmpty() && route != "sign-in"))
            browseReturns += captureBrowse()
        channelSearchOrigin = null
        if (this.tab == "Subscriptions" && this.route.isEmpty() && route == "subscription-channels") subscriptionFeedSearch = scopedSearch.value
        val returnToFeed = this.route == "subscription-channels" && tab == "Subscriptions" && route.isEmpty()
        subscriptionChannelParent = tab == "Subscriptions" && route.startsWith("channel:") &&
            (this.route == "subscription-channels" || this.tab == "Subscriptions" && this.route.startsWith("channel:") && subscriptionChannelParent)
        postCommentController.bind(null)
        postDetail.value = PostDetailState()
        this.tab = tab
        if (PreferenceRules.isDiscovery(tab)) discovery.value = tab.lowercase()
        navigation.value = tab to route; this.route = route
        scopedSearch.value = if (returnToFeed) subscriptionFeedSearch else SearchInput()
        channel.value = null; channelTab.value = null; playlist.value = null; playlistSeed = null; playlistLink = null; channelPlaylistSort.value = "last"; channelVideoSort.value = ChannelSort.NEWEST; if (loadContent) refresh()
    }
    private fun restoreBrowse(saved: BrowseReturn, afterSignIn: Boolean = false) {
        navigationRevision++
        dismissLinkResolution()
        if (saved.context.server != api.context().server || !afterSignIn && saved.context != api.context()) { navigate("Popular"); return }
        val refreshSaved = currentBrowseAffected(saved.preferences, preferences.value, saved.tab, saved.route)
        browseJob?.cancel(); browseGeneration++
        tab = saved.tab; route = saved.route; channel.value = saved.channel; channelTab.value = saved.channelTab
        playlist.value = saved.playlist; playlistSeed = saved.seed; playlistLink = saved.playlistLink; channelPlaylistSort.value = saved.playlistSort
        channelVideoSort.value = saved.videoSort; postDetail.value = saved.postDetail.copy(context = api.context(), loading = false)
        postCommentController.restore(saved.postComments)
        channelSearchOrigin = saved.channelSearchOrigin; subscriptionChannelParent = saved.subscriptionParent
        scopedSearch.value = saved.scopedSearch; discovery.value = saved.discovery
        browse.value = saved.browse.copy(loading = false)
        navigation.value = tab to route; restoredBrowse.value++
        if (route == "subscription-channels") refreshSubscriptions()
        if (saved.browse.loading && (route == "clips" || route.startsWith("channel:") && channelTab.value == ChannelTab.CLIPS)) reloadClipPages(saved.browse.page)
        else if (refreshSaved || saved.browse.loading || afterSignIn && saved.tab in listOf("You", "Subscriptions")) load(false, refreshChannel = false)
    }
    fun openGlobalSearch(text: String) {
        if (text.isBlank()) return
        if (tab != "Search" || route.isNotEmpty()) searchReturn = captureBrowse()
        searchInput.value = SearchInput(text, text.trim())
        navigate("Search")
    }
    fun backBrowse() {
        val saved = searchReturn
        if (tab == "Search" && route.isEmpty() && saved != null) { searchReturn = null; restoreBrowse(saved) }
        else if (route == "sign-in" && signInReturn != null) { val origin = signInReturn!!; signInReturn = null; restoreBrowse(origin) }
        else if (browseReturns.isNotEmpty()) restoreBrowse(browseReturns.removeAt(browseReturns.lastIndex))
        else navigate(tab, if (subscriptionChannelParent && route.startsWith("channel:")) "subscription-channels" else "")
    }
    val hasBrowseBack: Boolean get() = browseReturns.isNotEmpty() || tab == "Search" && searchReturn != null || route == "sign-in" && signInReturn != null
    fun browsePosition(position: CommentPosition) {
        if (!browse.value.loading && browse.value.position != position) browse.value = browse.value.copy(position = position)
    }
    fun openPost(link: PostLink) {
        if (!PostLinks.validId(link.id)) return
        navigate(tab, "post:${link.id}", loadContent = false)
        postDetail.value = PostDetailState(link, api.context())
        postNavigation.value++
        load(false)
    }
    fun dismissLinkResolution() { linkResolutionJob?.cancel(); linkResolutionJob = null; linkResolution.value = LinkResolutionState() }
    fun retryLinkResolution() { linkResolution.value.link?.let(::openChannelLink) }
    private fun openChannelLink(link: ChannelLink) {
        dismissLinkResolution(); postNavigation.value++
        if (link.id != null) {
            navigate(tab, "channel:${link.id}", loadContent = false, rememberOrigin = true)
            channelTab.value = link.tab; refresh(); return
        }
        val context = api.context()
        linkResolution.value = LinkResolutionState(link, context, loading = true)
        linkResolutionJob = viewModelScope.launch {
            try {
                val id = api.resolveChannel(link, context)
                if (api.context() == context && linkResolution.value.link == link) {
                    navigate(tab, "channel:$id", loadContent = false, rememberOrigin = true)
                    channelTab.value = link.tab; refresh()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (api.context() == context && linkResolution.value.link == link)
                linkResolution.value = LinkResolutionState(link, context, error = friendly(e)) }
        }
    }
    fun openContent(target: ContentLink): Boolean {
        when (target) {
            is ContentLink.Video -> { dismissClipResolution(); dismissLinkResolution(); return openLink(target.link) }
            is ContentLink.Clip -> { openClipLink(target.link); return true }
            is ContentLink.Channel -> openChannelLink(target.link)
            is ContentLink.Post -> openPost(target.link)
            is ContentLink.Hashtag -> { postNavigation.value++; navigate(tab, "hashtag:${target.tag}", rememberOrigin = true) }
            is ContentLink.Seek -> seekTo(target.milliseconds)
            is ContentLink.External -> return false
        }
        return false
    }
    fun openPostComments() {
        postDetail.value.post?.let(::openPostComments)
    }
    fun isPostCommentsSource(target: CommentTarget.Post): Boolean = when {
        route == "post:${target.id}" -> postDetail.value.context == api.context() &&
            postDetail.value.post?.let { it.id == target.id && it.channelId == target.channelId } == true
        route == "channel:${target.channelId}" -> channel.value?.id == target.channelId &&
            channelTab.value == ChannelTab.POSTS && scopedSearch.value.submitted.isBlank()
        else -> false
    }
    fun openPostComments(post: CommunityPost) {
        if (!PostLinks.validId(post.id) || !ContentVisibility.validChannel(post.channelId)) return
        val target = CommentTarget.Post(post.id, post.channelId)
        if (!isPostCommentsSource(target)) return
        val current = if (route.startsWith("post:")) postDetail.value.post == post else post in browse.value.posts
        if (!current) return
        postCommentController.bind(CommentTarget.Post(post.id, post.channelId)); postCommentController.open()
    }
    fun closePostComments() = postCommentController.close()
    fun backPostComments() = postCommentController.back()
    fun sortPostComments(sort: CommentSort) = postCommentController.sort(sort)
    fun openPostReplies(comment: Comment) = postCommentController.replies(comment)
    fun loadPostComments(more: Boolean, key: String?) = postCommentController.load(more, key)
    fun postCommentPosition(key: String?, position: CommentPosition) = postCommentController.position(key, position)
    fun setChannelVideoSort(value: ChannelSort) {
        if (channelVideoSort.value == value || channelTab.value?.videoTab != true || channel.value?.ageGated == true) return
        channelVideoSort.value = value; load(false, refreshChannel = false)
    }
    fun openSignIn() { if (route != "sign-in") signInReturn = captureBrowse(); navigate("You", "sign-in") }
    fun openPlaylist(list: Playlist) {
        navigate("You", "playlist:${list.id}", loadContent = false, rememberOrigin = true)
        playlistSeed = list.seedVideoId
        playlist.value = list.copy(owned = list.owned || playlists.value.any { it.id == list.id && it.owned }, saved = listsSubscribed(list.id))
        refresh()
    }
    fun listsSubscribed(id: String) = playlists.value.any { it.id == id && it.saved && !it.owned }
    fun preparePlaylistSubscription(list: Playlist) { pendingPlaylistSubscription = api.context().server to list }
    fun subscribePlaylist(list: Playlist, subscribe: Boolean = !listsSubscribed(list.id)) {
        if (list.owned || list.id in playlistBusy.value) return
        val context = api.context()
        if (context.account == null) { preparePlaylistSubscription(list); return }
        playlistRevision++
        playlistBusy.value += list.id; playlistErrors.value -= list.id
        viewModelScope.launch {
            try {
                val result = api.subscribePlaylist(list, subscribe, context)
                if (context == api.context()) {
                    playlistRevision++
                    playlists.value = playlists.value.filterNot { it.id == list.id } + listOfNotNull(result)
                    if (playlist.value?.id == list.id) playlist.value = (result ?: playlist.value!!).copy(saved = subscribe)
                    message.value = if (subscribe) "Playlist subscribed" else "Playlist unsubscribed"
                    // An initial account read may have been fenced out by this mutation.
                    // Reload after confirmation so owned and subscribed lists are both retained.
                    refreshPlaylists()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (context == api.context()) playlistErrors.value += list.id to friendly(e) }
            finally { if (context == api.context()) playlistBusy.value -= list.id }
        }
    }
    fun setPlaylistSearch(value: Boolean) { if (playlistSearch.value != value) { playlistSearch.value = value; refresh() } }
    fun setChannelPlaylistSort(value: String) { if (value in listOf("last", "newest", "oldest")) { channelPlaylistSort.value = value; load(false, refreshChannel = false) } }
    fun openChannelRss(info: Channel) {
        rssJob?.cancel()
        val link = runCatching { RssLinks.channel(api.context().server, info.id) }
        rss.value = RssState(true, "${info.name} RSS", api.context(), url = link.getOrNull(), error = link.exceptionOrNull()?.message)
    }
    fun openPlaylistRss(list: Playlist) {
        rssJob?.cancel()
        val privateFile = list.owned && list.privacy == "private"
        val link = runCatching { if (privateFile) null else RssLinks.playlist(api.context().server, list) }
        rss.value = RssState(true, "${list.title} RSS", api.context(), playlist = list,
            url = link.getOrNull(), error = link.exceptionOrNull()?.message, filename = "playlist.atom")
        if (privateFile) retryRss()
    }
    fun openSubscriptionRss() { rss.value = RssState(true, "Subscriptions RSS", api.context(), privateLink = true); retryRss() }
    fun openOpml(format: String = "rss") { rss.value = RssState(true, "Subscription OPML", api.context(), opml = true, format = format, filename = "subscriptions.opml"); retryRss() }
    fun dismissRss() { rssJob?.cancel(); rss.value = RssState() }
    fun retryRss() {
        val initial = rss.value; val context = initial.context ?: return
        if (initial.playlist?.let { !it.owned || it.privacy != "private" } == true) { openPlaylistRss(initial.playlist); return }
        if (!initial.privateLink && !initial.opml && initial.playlist == null) return
        rssJob?.cancel(); rss.value = initial.copy(loading = true, error = null)
        rssJob = viewModelScope.launch {
            try {
                val loaded = when {
                    initial.opml -> initial.copy(xml = api.subscriptionOpml(initial.format, context))
                    initial.playlist != null -> initial.copy(xml = api.playlistAtom(initial.playlist.id, context))
                    else -> initial.copy(url = api.subscriptionFeedLink(context))
                }
                if (context == api.context() && rss.value.open) rss.value = loaded.copy(loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (context == api.context() && rss.value.open) rss.value = initial.copy(loading = false, error = friendly(e)) }
        }
    }
    fun editSearch(value: String, scoped: Boolean) {
        val input = if (scoped) scopedSearch else searchInput
        input.value = input.value.copy(draft = value)
    }
    fun submitSearch(scoped: Boolean) {
        val input = if (scoped) scopedSearch else searchInput
        postCommentController.bind(null)
        if (scoped && route.startsWith("channel:") && input.value.submitted.isBlank() && input.value.draft.isNotBlank())
            channelSearchOrigin = captureBrowse().copy(scopedSearch = SearchInput(), channelSearchOrigin = null)
        input.value = input.value.copy(submitted = input.value.draft.trim())
        if (scoped && input.value.submitted.isBlank()) clearScopedSearch() else refresh()
    }
    fun clearScopedSearch() {
        val saved = channelSearchOrigin; channelSearchOrigin = null
        if (saved != null && saved.route == route && saved.context == api.context()) restoreBrowse(saved)
        else { scopedSearch.value = SearchInput(); refresh() }
    }
    fun openDefaultHome() { val target = PreferenceRules.destination(preferences.value.defaultHome, account.value != null); discovery.value = target.second; navigate(target.first) }
    fun selectDiscovery(value: String) {
        if (value !in listOf("popular", "trending")) return
        selectTab(if (value == "trending") "Trending" else "Popular")
    }
    fun refresh() { dearrowTitles.clear(); load(false); playback.value.details?.video?.id?.let(::ensureDeArrow); refreshWatched() }
    private fun refreshWatched() {
        if (preferencesContext == api.context()) app.watched.configure(api.context(), preferences.value.savePosition)
        viewModelScope.launch { app.watched.refresh() }
    }
    fun removeHistory(id: String) {
        val context = api.context()
        action { app.watched.removeHistory(context, id); if (api.context() == context) refresh() }
    }
    fun clearHistory() {
        val context = api.context()
        action { app.watched.clearHistory(context); if (api.context() == context) refresh() }
    }
    fun more() = load(true)
    fun retryBrowse() = load(browse.value.retryMore, refreshChannel = false)
    fun selectChannelTab(value: ChannelTab) {
        val info = channel.value ?: return
        if (!route.startsWith("channel:") || value !in info.contentTabs || value == channelTab.value && scopedSearch.value.submitted.isBlank()) return
        postCommentController.bind(null)
        if (value == channelTab.value && channelSearchOrigin != null) { clearScopedSearch(); return }
        channelSearchOrigin = null; scopedSearch.value = SearchInput()
        channelTab.value = value
        dearrowTitles.clear()
        load(false, refreshChannel = false)
    }
    private fun load(more: Boolean, refreshChannel: Boolean = true) {
        if (route == "subscription-channels") {
            browseJob?.cancel(); browseGeneration++
            browse.value = BrowseState(title = "Subscribed channels", end = true)
            val state = subscriptionChannels.value
            if (!state.loading) refreshSubscriptions()
            return
        }
        if (more && (browse.value.loading || browse.value.end)) return
        browseJob?.cancel()
        val generation = ++browseGeneration
        val old = if (more) browse.value else BrowseState()
        if (!more) browseReset.value++
        val page = if (more) old.page + 1 else 1
        val selectedTab = tab; val selectedRoute = route; val selectedQuery = query
        val selectedDiscovery = discovery.value; val selectedRegion = region
        val listSearch = playlistSearch.value; val listSort = channelPlaylistSort.value; val videoSort = channelVideoSort.value; val revision = playlistRevision
        val scopedQuery = scopedSearch.value.submitted
        val context = api.context()
        val selectedChannel = channel.value; val selectedChannelTab = channelTab.value
        val selectedPostLink = postDetail.value.link ?: selectedRoute.takeIf { it.startsWith("post:") }?.let { PostLink(it.substringAfter(':')) }
        browse.value = old.copy(loading = true, error = null, title = if (selectedRoute == "history") "History" else if (selectedRoute.startsWith("post:")) "Post" else if (selectedRoute.startsWith("hashtag:")) "#${selectedRoute.substringAfter(':')}" else selectedTab)
        browseJob = viewModelScope.launch {
            try {
                var continuation = ""
                var hasMore: Boolean? = null
                var history: HistoryPage? = null
                var lists = emptyList<Playlist>()
                var posts = emptyList<CommunityPost>()
                var channels = emptyList<Channel>()
                var clips = emptyList<Clip>()
                val videos = when {
                    selectedRoute == "clips" -> {
                        if (context.account != null) clips = api.clips(page = page, context = context)
                        hasMore = clips.size == 30; emptyList()
                    }
                    selectedRoute.startsWith("hashtag:") -> {
                        val response = api.hashtag(selectedRoute.substringAfter(':'), page, context)
                        hasMore = response.size >= 60; response
                    }
                    selectedRoute.startsWith("post:") && selectedPostLink != null -> {
                        postDetail.value = PostDetailState(selectedPostLink, context, postDetail.value.post, loading = true)
                        val post = api.post(selectedPostLink, context)
                        if (generation != browseGeneration || context != api.context()) throw CancellationException("Post request superseded")
                        postDetail.value = PostDetailState(selectedPostLink, context, post)
                        postCommentController.bind(CommentTarget.Post(post.id, post.channelId))
                        hasMore = false; emptyList()
                    }
                    selectedRoute.startsWith("channel:") -> {
                        val id = selectedRoute.substringAfter(':')
                        val info = if (selectedChannel == null || !more && refreshChannel) api.channel(id, context) else selectedChannel
                        if (generation != browseGeneration || context != api.context()) throw CancellationException("Channel request superseded")
                        val contentTab = if (selectedChannelTab == ChannelTab.CLIPS) ChannelTab.CLIPS else info.preferredTab(selectedChannelTab)
                        channel.value = info; channelTab.value = contentTab
                        if (scopedQuery.isNotBlank()) {
                            val response = api.channelSearch(id, scopedQuery, page, context)
                            hasMore = response.hasMore
                            response.items
                        } else {
                            val token = if (more) old.continuation else ""
                            val resultVideos = when {
                                contentTab.playlistTab -> {
                                    val response = api.channelPlaylistPage(id, contentTab, token, listSort, context)
                                    lists = response.items; continuation = response.continuation; emptyList()
                                }
                                contentTab == ChannelTab.CLIPS -> {
                                    clips = api.clips(id, page, context); emptyList()
                                }
                                contentTab == ChannelTab.POSTS -> {
                                    val response = api.channelPosts(id, token, context)
                                    posts = response.items; continuation = response.continuation; emptyList()
                                }
                                contentTab == ChannelTab.CHANNELS -> {
                                    val response = api.channelRelated(id, token, context)
                                    channels = response.items; continuation = response.continuation; emptyList()
                                }
                                else -> {
                                    val response = api.channelVideoPage(id, contentTab, token, if (info.ageGated) ChannelSort.NEWEST else videoSort, context)
                                    continuation = response.continuation; response.items
                                }
                            }
                            if (more && continuation == token) continuation = ""
                            hasMore = if (contentTab == ChannelTab.CLIPS) clips.size == 30 else continuation.isNotBlank()
                            resultVideos
                        }
                    }
                    selectedRoute.startsWith("playlist:") -> {
                        val id = selectedRoute.substringAfter(':'); val mix = id.startsWith("RD")
                        val result = api.queuePage(id, if (more) (old.videos.mapNotNull { it.playlistIndex }.maxOrNull() ?: -1) + 1 else 0,
                            if (mix && more) old.videos.lastOrNull()?.id else if (mix) playlistSeed ?: playlists.value.find { it.id == id }?.seedVideoId else null, context)
                        if (generation == browseGeneration && context == api.context()) {
                            val metadata = result.playlist ?: Playlist(id, result.source.title, result.source.count)
                            playlist.value = metadata.copy(owned = metadata.owned || playlists.value.any { it.id == id && it.owned }, saved = if (revision == playlistRevision) metadata.saved || listsSubscribed(id) else listsSubscribed(id), seedVideoId = playlistSeed ?: metadata.seedVideoId)
                            if (playlistSeed == null) playlistSeed = metadata.seedVideoId
                            if (revision == playlistRevision && playlists.value.any { it.id == id }) playlists.value = playlists.value.map { if (it.id == id) playlist.value!! else it }
                        }
                        hasMore = if (mix) result.videos.isNotEmpty() && !ContentVisibility.exhausted(old.videos, result.videos)
                            else (result.videos.mapNotNull { it.playlistIndex }.maxOrNull() ?: -1) + 1 < result.source.count && result.videos.isNotEmpty()
                        result.videos
                    }
                    selectedRoute == "history" -> {
                        val response = api.history(page, scopedQuery, context)
                        if (generation != browseGeneration || context != api.context()) throw CancellationException("History request superseded")
                        if (more && response.organized && old.history?.today != null && response.today != old.history.today) {
                            load(false); return@launch
                        }
                        if (!response.organized) scopedSearch.value = SearchInput()
                        history = response; hasMore = response.hasMore
                        response.entries
                    }
                    selectedTab == "Search" -> if (selectedQuery.isBlank()) emptyList() else if (listSearch) {
                        lists = api.searchPlaylists(selectedQuery, page, sort, context)
                        hasMore = lists.isNotEmpty() && (!more || lists.any { incoming -> old.lists.none { it.id == incoming.id } })
                        emptyList()
                    } else api.search(selectedQuery, page, sort, date, durationFilter)
                    selectedTab == "Subscriptions" -> if (context.account == null) emptyList() else if (scopedQuery.isNotBlank()) {
                        val response = api.subscriptionSearch(scopedQuery, page, context)
                        hasMore = response.hasMore; response.items
                    } else api.feed(page, preferences.value.notificationsOnly)
                    selectedRoute == "sign-in" -> emptyList()
                    selectedTab == "You" -> { if (context.account != null) { val loaded = api.playlists(context); if (context == api.context() && revision == playlistRevision) playlists.value = loaded }; emptyList() }
                    else -> api.discovery(selectedDiscovery, selectedRegion)
                }
                if (generation == browseGeneration && context == api.context()) browse.value = browse.value.copy(videos = ContentVisibility.merge(old.videos, videos), loading = false, page = page,
                    continuation = continuation, history = history, clips = (old.clips + clips).distinctBy { it.id }, lists = (old.lists + lists).distinctBy { it.id },
                    posts = (old.posts + posts).distinctBy { it.key }, channels = (old.channels + channels).distinctBy { it.id }, retryMore = false, end = if (hasMore != null) !hasMore || !selectedRoute.startsWith("channel:") && more && videos.isNotEmpty() && ContentVisibility.exhausted(old.videos, videos) else videos.isEmpty() || more && ContentVisibility.exhausted(old.videos, videos) ||
                        PreferenceRules.isDiscovery(selectedTab) && selectedRoute.isEmpty() || selectedRoute.startsWith("channel:") && continuation.isBlank() ||
                        selectedTab == "Subscriptions" && selectedRoute.isEmpty() && (preferences.value.latestOnly || preferences.value.notificationsOnly))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (generation == browseGeneration && context == api.context()) {
                    browse.value = browse.value.copy(loading = false, error = friendly(e), retryMore = more)
                    if (selectedRoute.startsWith("post:")) postDetail.value = postDetail.value.copy(loading = false, error = friendly(e))
                }
            }
        }
    }
    fun queueCommand(command: String, values: Bundle.() -> Unit = {}) {
        val context = api.context()
        val args = Bundle().apply {
            putString("server", context.server); putString("account", context.account?.username); putLong("generation", context.generation); putString("token", queue.value.token); values()
        }
        viewModelScope.launch {
            val p = controller.value ?: future.awaitController()
            if (context != api.context()) return@launch
            val result = p.sendCustomCommand(SessionCommand(command, Bundle.EMPTY), args)
            result.addListener({ runCatching { if (result.get().resultCode != SessionResult.RESULT_SUCCESS) message.value = "Playback request expired. Try again." } }, ContextCompat.getMainExecutor(getApplication()))
        }
    }
    fun play(id: String, explicit: Long? = null, source: String? = null, index: Int? = null, audio: Boolean = false, seed: String? = null) {
        dismissClipResolution(); closeClipEditor()
        navigationRevision++
        cancelAccumulatedSeek(false)
        val linked = playlistLink?.takeIf { source != null && it.playlistId == source && route == "playlist:$source" }
        if (linked != null) playlistLink = null
        queueCommand(PlaybackService.QUEUE_START) {
            putString("id", id); (seed ?: playlist.value?.takeIf { it.id == source }?.seedVideoId)?.let { putString("seed", it) }
            source?.let { putString("source", it) }; (index ?: linked?.index)?.let { putInt("index", it) }
            explicit?.let { putLong("seconds", it) }; putBoolean("audio", audio)
            linked?.let { putString("linkPlayback", it.playback.copy(startMs = it.startMs).json().toString()) }
        }
    }
    fun playVideo(video: Video, source: Playlist? = null, audio: Boolean = false) = play(video.id, source = source?.id, index = video.playlistIndex, audio = audio)
    fun openLink(link: VideoLink): Boolean {
        dismissClipResolution(); closeClipEditor()
        if (link.id.isEmpty()) {
            openPlaylist(Playlist(link.playlistId!!, "Playlist", 0, seedVideoId = link.seedVideoId))
            playlistLink = link
            return false
        }
        navigationRevision++
        cancelAccumulatedSeek(false)
        queueCommand(PlaybackService.QUEUE_START) {
            putString("id", link.id); link.playlistId?.let { putString("source", it) }; link.index?.let { putInt("index", it) }
            link.seedVideoId?.let { putString("seed", it) }
            putString("linkPlayback", link.playback.copy(startMs = link.startMs).json().toString())
        }
        return true
    }
    fun dismissClipResolution() { clipJob?.cancel(); clipResolution.value = ClipResolutionState() }
    fun openClipLink(link: ClipLink) {
        dismissClipResolution()
        if (ClipRules.nativeId(link.id) && link.server.trimEnd('/') != store.server.trimEnd('/')) {
            clipResolution.value = ClipResolutionState(link, foreign = true); return
        }
        val context = api.context()
        clipResolution.value = ClipResolutionState(link, loading = true)
        clipJob = viewModelScope.launch {
            try {
                val clip = api.clip(link.id, context)
                if (context == api.context() && clipResolution.value.link == link) {
                    clipResolution.value = ClipResolutionState(); watchClip(clip)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (context == api.context() && clipResolution.value.link == link)
                clipResolution.value = ClipResolutionState(link, error = friendly(e)) }
        }
    }
    fun watchClip(clip: Clip) {
        if (clip.server.trimEnd('/') != store.server.trimEnd('/')) return
        navigationRevision++
        closeClipEditor(); dismissClipResolution(); closeComments(); closeChat(); cancelAccumulatedSeek(false)
        queueCommand(PlaybackService.QUEUE_START) { putString("id", clip.video.id); putString("clip", clip.json().toString()) }
        clipOpened.value++
    }
    fun watchFullClip() { cancelAccumulatedSeek(false); queueCommand(PlaybackService.CLIP_FULL_VIDEO) }
    fun requestClipSignIn() { playback.value.details?.video?.id?.let { pendingClipEditor = store.server to it } }
    fun openClipEditor() {
        val details = playback.value.details ?: return
        if (account.value == null || queue.value.current?.clip != null || !ClipRules.eligible(details)) return
        val context = api.context(); val occurrence = queue.value.currentKey.orEmpty()
        val old = clipEditor.value
        val range = ClipRules.defaultRange(controller.value?.currentPosition ?: playback.value.position, details.video.duration * 1000)
        val wasPlaying = controller.value?.playWhenReady ?: playback.value.playWhenReady
        controller.value?.pause(); cancelAccumulatedSeek(false); closeComments(); closeChat()
        clipEditor.value = if (old.context == context && old.occurrence == occurrence && old.published == null)
            old.copy(open = true, wasPlaying = wasPlaying, error = null)
        else ClipEditorState(true, details, context, occurrence, wasPlaying, startMs = range.first, endMs = range.last,
            startText = ClipRules.timestamp(range.first, range.last >= 3_600_000), endText = ClipRules.timestamp(range.last))
        clipEditorJob?.cancel()
        val track = details.storyboards.filter { it.width > 0 }.minByOrNull { kotlin.math.abs(it.width - 160) }
        if (track != null && clipEditor.value.frames.isEmpty()) {
            clipEditor.value = clipEditor.value.copy(loadingFrames = true)
            clipEditorJob = viewModelScope.launch {
                val frames = try { api.storyboard(track, context) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                if (clipEditor.value.open && context == api.context() && clipEditor.value.occurrence == occurrence)
                    clipEditor.value = clipEditor.value.copy(frames = frames, loadingFrames = false)
            }
        }
    }
    fun closeClipEditor() {
        val state = clipEditor.value
        if (!state.open) return
        clipEditorJob?.cancel()
        clipEditor.value = state.copy(open = false, busy = false, loadingFrames = false)
        if (state.wasPlaying && state.context == api.context() && state.occurrence == queue.value.currentKey) controller.value?.play()
    }
    fun clipTitle(value: String) { if (!clipEditor.value.busy) clipEditor.value = clipEditor.value.copy(title = value, error = null) }
    fun clipTimes(start: Long, end: Long) {
        val state = clipEditor.value
        if (state.busy) return
        clipEditor.value = state.copy(startMs = start, endMs = end, startText = ClipRules.timestamp(start, end >= 3_600_000),
            endText = ClipRules.timestamp(end), error = null)
    }
    fun clipTimeText(value: String, start: Boolean) {
        val state = clipEditor.value
        if (state.busy) return
        val parsed = ClipRules.parseTimestamp(value)
        clipEditor.value = if (start) state.copy(startText = value, startMs = parsed ?: state.startMs, error = null)
            else state.copy(endText = value, endMs = parsed ?: state.endMs, error = null)
    }
    fun publishClip() {
        val state = clipEditor.value; val context = state.context ?: return
        val details = state.details ?: return
        if (!state.open || state.busy || context != api.context()) return
        state.validation?.let { clipEditor.value = state.copy(error = it); return }
        clipEditor.value = state.copy(busy = true, error = null)
        clipEditorJob?.cancel()
        clipEditorJob = viewModelScope.launch {
            try {
                val clip = api.createClip(details.video.id, state.title, state.startMs, state.endMs, context)
                if (context == api.context() && clipEditor.value.occurrence == state.occurrence) {
                    clipEditor.value = clipEditor.value.copy(busy = false, published = clip)
                    invalidateClipLists()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (context == api.context() && clipEditor.value.occurrence == state.occurrence)
                clipEditor.value = clipEditor.value.copy(busy = false, error = friendly(e)) }
        }
    }
    private fun invalidateClipLists(deleted: String? = null) {
        for (index in browseReturns.indices) {
            val saved = browseReturns[index]
            if (saved.route == "clips" || saved.channelTab == ChannelTab.CLIPS)
                browseReturns[index] = saved.copy(browse = saved.browse.copy(loading = true, clips = saved.browse.clips.filterNot { it.id == deleted }))
        }
        if (deleted != null) browse.value = browse.value.copy(clips = browse.value.clips.filterNot { it.id == deleted })
        if (route == "clips" || route.startsWith("channel:") && channelTab.value == ChannelTab.CLIPS) reloadClipPages(browse.value.page)
    }
    private fun reloadClipPages(pages: Int) {
        val selectedRoute = route
        val channelId = selectedRoute.takeIf { it.startsWith("channel:") }?.substringAfter(':')
        if (selectedRoute != "clips" && (channelId == null || channelTab.value != ChannelTab.CLIPS)) return
        val context = api.context()
        if (channelId == null && context.account == null) return
        browseJob?.cancel(); val generation = ++browseGeneration
        browse.value = browse.value.copy(loading = true, error = null)
        browseJob = viewModelScope.launch {
            try {
                val clips = mutableListOf<Clip>(); var page = 1; var count = 0
                for (number in 1..pages.coerceAtLeast(1)) {
                    val response = api.clips(channelId, number, context)
                    clips += response; page = number; count = response.size
                    if (count < 30) break
                }
                if (generation == browseGeneration && context == api.context() && route == selectedRoute)
                    browse.value = browse.value.copy(clips = clips.distinctBy { it.id }, page = page, end = count < 30, loading = false, error = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == browseGeneration && context == api.context())
                browse.value = browse.value.copy(loading = false, error = friendly(e), retryMore = false) }
        }
    }
    fun confirmDeleteClip(clip: Clip) { if (clip.owned(api.context())) { clipDeleteError.value = null; clipDelete.value = clip } }
    fun deleteClip() {
        val clip = clipDelete.value ?: return
        val context = api.context()
        if (clipDeleteBusy.value || !clip.owned(context)) return
        clipDeleteBusy.value = true
        clipDeleteError.value = null
        clipDeleteJob = viewModelScope.launch {
            try {
                api.deleteClip(clip.id, context)
                if (context == api.context()) {
                    if (queue.value.current?.clip?.id == clip.id) closePlayer()
                    clipDelete.value = null; clipDeleteBusy.value = false
                    invalidateClipLists(clip.id); message.value = "Clip deleted"
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (context == api.context()) { clipDeleteBusy.value = false; clipDeleteError.value = friendly(e) } }
        }
    }
    fun insertQueue(video: Video, next: Boolean) {
        queueCommand(PlaybackService.QUEUE_INSERT) {
            putBoolean("next", next); putString("video", JSONObject().put("videoId", video.id).put("title", video.title).put("author", video.author)
                .put("authorId", video.channelId).put("lengthSeconds", video.duration).put("isMember", video.membersOnly).toString())
        }
        message.value = if (next) "Will play next" else "Added to queue"
    }
    fun removePlaylistVideo(list: Playlist, video: Video) {
        if (!list.owned) return
        val occurrence = queue.value.items.firstOrNull { it.video.indexId == video.indexId }
        if (queue.value.source?.id == list.id && occurrence != null) queueCommand(PlaybackService.QUEUE_DELETE_SOURCE) { putString("key", occurrence.key) }
        else action { val context = api.context(); api.removeFromPlaylist(list.id, video.indexId, context); if (context == api.context()) { refresh(); refreshAccount(); message.value = "Removed from playlist" } }
    }
    fun selectQueue(key: String) { cancelAccumulatedSeek(false); val state = queue.value; val index = state.items.indexOfFirst { it.key == key }; val prefix = if (state.source?.mix == false && (state.items.mapNotNull { it.sourceIndex }.minOrNull() ?: 0) > 0) 1 else 0; if (index >= 0) controller.value?.seekTo(index + prefix, C.TIME_UNSET) }
    fun nextQueue(direction: Int) { cancelAccumulatedSeek(false); if (direction > 0) controller.value?.seekToNextMediaItem() else controller.value?.seekToPreviousMediaItem() }
    fun repeatQueue(value: QueueRepeat) { controller.value?.repeatMode = when(value) { QueueRepeat.ONE -> Player.REPEAT_MODE_ONE; QueueRepeat.ALL -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF } }
    fun retryPlayback() { cancelAccumulatedSeek(); queueCommand(PlaybackService.QUEUE_RETRY) }
    fun closePlayer() { chatController.bind("", "", false); commentController.bind(null, false); cancelAccumulatedSeek(false); queueCommand(PlaybackService.QUEUE_CLOSE); contributionJob?.cancel(); dearrowContribution.value = DeArrowContributionState(); queueExpanded.value = false }
    fun undoBlock(value: BlockUndo) {
        if (value != blockUndo.value || value.context != api.context() || SystemClock.elapsedRealtime() > value.expires) return
        blockUndo.value = null
        action { app.blocked.setBlocked(value.context, value.id, value.name, false); if (api.context() == value.context) { message.value = "Channel unblocked"; refreshBlockedChannels() } }
    }
    fun openSave(video: Video) { saveSheet.value = PlaylistSaveState(video, api.context()); if (account.value != null) loadSaveLists() }
    fun dismissSave() { if (!saveSheet.value.busy) saveSheet.value = PlaylistSaveState() }
    fun editSave(title: String, privacy: String) { if (!saveSheet.value.busy) saveSheet.value = saveSheet.value.copy(title = title.take(150), privacy = privacy) }
    fun loadSaveLists() {
        val state = saveSheet.value; val context = state.context ?: return
        if (state.loading || state.busy || context.account == null) return
        saveSheet.value = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try { val lists = api.playlists(context).filter { it.owned }; if (saveSheet.value.video == state.video && context == api.context()) saveSheet.value = saveSheet.value.copy(lists = lists.sortedBy { it.id != preferences.value.defaultPlaylist }, loading = false) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { if (saveSheet.value.video == state.video && context == api.context()) saveSheet.value = saveSheet.value.copy(loading = false, error = friendly(e)) }
        }
    }
    fun saveVideo(list: Playlist? = null) {
        val state = saveSheet.value; val context = state.context ?: return; val video = state.video ?: return
        if (state.busy || state.loading || context != api.context() || context.account == null || list == null && state.created == null && state.title.isBlank()) return
        saveSheet.value = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val target = list ?: state.created ?: api.createPlaylist(state.title.trim(), state.privacy, context).also {
                    if (context == api.context()) {
                        saveSheet.value = saveSheet.value.copy(created = it, lists = saveSheet.value.lists + it)
                        playlists.value = playlists.value.filterNot { list -> list.id == it.id } + it
                        refreshAccount()
                    }
                }
                if (context != api.context()) throw CancellationException()
                api.addToPlaylist(target.id, video.id, context)
                if (context == api.context()) { saveSheet.value = PlaylistSaveState(); message.value = "Saved to ${target.title}"; refreshAccount(); if (route == "playlist:${target.id}") refresh() }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (context == api.context() && saveSheet.value.video == video) saveSheet.value = saveSheet.value.copy(busy = false,
                    error = if (saveSheet.value.created != null) "Playlist created. Saving failed: ${friendly(e)} Retry saving below." else friendly(e))
            }
        }
    }
    fun togglePlay() { cancelAccumulatedSeek(false); controller.value?.let {
        if (it.playbackState == Player.STATE_ENDED) { val start = queue.value.current?.linkPlayback?.startMs; if (start != null) it.seekTo(start) else it.seekToDefaultPosition(); it.play() }
        else if (it.playWhenReady) it.pause() else it.play()
    } }
    fun seekTo(position: Long) { controller.value?.let { p ->
        cancelAccumulatedSeek()
        if (p.isCurrentMediaItemSeekable && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            p.seekTo(PlaybackRules.seek(position, p.duration)); updatePlayback()
        }
    } }
    fun seekBy(offset: Long) { controller.value?.let { seekTo(it.currentPosition + offset) } }
    fun accumulateSeek(direction: Int) {
        val p = controller.value ?: return
        val state = playback.value
        if (!state.seekable || !state.canPlay || state.loading || state.error != null) return
        val id = p.currentMediaItem?.mediaId ?: return
        val first = seekAccumulator.pending == null
        val value = seekAccumulator.tap(direction, id, p.currentPosition, p.playWhenReady, SystemClock.elapsedRealtime())
        seekContext = api.context()
        pendingSeek.value = value
        if (first) p.pause()
        // A synchronous external discontinuity can cancel the batch during pause.
        if (seekAccumulator.pending != value) return
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            delay(SeekAccumulator.DELAY)
            if (api.context() != seekContext || p.currentMediaItem?.mediaId != id || p.playerError != null || playback.value.error != null) { cancelAccumulatedSeek(false); return@launch }
            val finished = seekAccumulator.finish(id, SystemClock.elapsedRealtime()) ?: return@launch
            pendingSeek.value = null
            seekContext = null
            if (p.isCurrentMediaItemSeekable && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                p.seekTo(finished.target(p.duration))
                p.playWhenReady = finished.resume
            } else p.playWhenReady = finished.resume
            updatePlayback()
        }
    }
    fun cancelAccumulatedSeek(restore: Boolean = true) {
        seekJob?.cancel(); seekJob = null
        val value = seekAccumulator.cancel()
        pendingSeek.value = null
        val context = seekContext; seekContext = null
        val p = controller.value
        if (restore && value != null && p != null && p.currentMediaItem?.mediaId == value.mediaId && api.context() == context && p.playerError == null)
            p.playWhenReady = value.resume
    }
    fun refreshBuffer() {
        cancelAccumulatedSeek()
        val p = controller.value ?: return
        if (!playback.value.canRefresh || playback.value.loading) return
        val position = p.currentPosition
        val live = p.isCurrentMediaItemLive
        val playing = p.playWhenReady
        val speed = p.playbackParameters
        val tracks = p.trackSelectionParameters
        playback.value = playback.value.copy(error = null)
        // stop releases buffered media without replacing the item or its history owner.
        p.stop()
        if (live) p.seekToDefaultPosition() else p.seekTo(position)
        p.trackSelectionParameters = tracks
        p.playbackParameters = speed
        p.prepare()
        p.playWhenReady = playing
        updatePlayback()
    }
    fun audioOnly(value: Boolean) { controller.value?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, value).build() } }
    fun autoQuality() { queueCommand(PlaybackService.VIDEO_AUTO) { putString("occurrence", queue.value.currentKey) } }
    // Resolution callers select one current-video representation; defaults are edited in Settings.
    fun quality(height: Int) { if (height == Int.MAX_VALUE) autoQuality() else controller.value?.let { p ->
        StreamCatalog.defaultVideo(StreamCatalog.choices(p.currentTracks, C.TRACK_TYPE_VIDEO), "${height}p",
            if (queue.value.videoSelection.dash) queue.value.videoSelection.codec else "auto")?.let { selectTrack(it.group, it.index) }
    } }
    fun speed(value: Float) { if (!value.isFinite() || value !in .25f..2f) return; controller.value?.setPlaybackSpeed(value); updatePlayerDefault { it.copy(speed = value) } }
    private fun updatePlayerDefault(change: (AccountPreferences) -> AccountPreferences) {
        val context = api.context()
        action { playerDefaultWrites.withLock { val before = preferences.value; savePreferences(change(before), before, context) } }
    }
    private fun syncHistorySettings() {
        if (preferencesContext != api.context()) return
        val prefs = preferences.value
        app.watched.configure(api.context(), prefs.savePosition)
        val id = controller.value?.currentMediaItem?.mediaId ?: return
        controller.value?.sendCustomCommand(SessionCommand(PlaybackService.SET_HISTORY_SETTINGS, Bundle.EMPTY), Bundle().apply {
            putString("mediaId", id); putBoolean("history", account.value != null && prefs.watchHistory); putBoolean("savePosition", prefs.savePosition)
        })
    }
    fun captions(language: String?) { controller.value?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, language == null).setPreferredTextLanguage(language).build() } }
    fun selectTrack(group: TrackGroup, index: Int) { controller.value?.let { p ->
        val current = p.currentTracks.groups.firstOrNull { it.mediaTrackGroup == group } ?: return
        if (index !in 0 until current.length || !current.isTrackSupported(index) || !p.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return
        if (group.type == C.TRACK_TYPE_VIDEO) {
            queueCommand(PlaybackService.VIDEO_SELECT) { putString("occurrence", queue.value.currentKey); putBundle("format", group.getFormat(index).toBundle()) }
            return
        }
        val builder = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group, index))
        if (group.type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(group.getFormat(index).language)
        else if (group.type == C.TRACK_TYPE_TEXT) builder.setPreferredTextLanguage(group.getFormat(index).language)
        p.trackSelectionParameters = builder.build()
    } }
    fun autoAudio() { controller.value?.let { p -> p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(null).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build() } }
    private fun syncComments() = commentController.bind(queue.value.current?.video?.id ?: playback.value.details?.video?.id, queue.value.effective(preferences.value).showYoutubeComments)
    fun openComments() { closeChat(); syncComments(); commentController.open() }
    private fun syncChat() {
        if (queue.value.current?.clip != null) { chatController.bind("", "", false); return }
        if (chatAppearanceContext != api.context()) { chatAppearanceContext = api.context(); chatAppearance.value = store.chatAppearance(chatAppearanceContext) }
        val q = queue.value
        val details = q.details
        if (details != null) chatController.bind(details.video.id, q.currentKey.orEmpty(), details.chatAvailable && !playback.value.live)
        else if (q.currentKey != chatReplay.value.occurrence || chatReplay.value.context != api.context()) chatController.bind("", q.currentKey.orEmpty(), false)
        chatController.configure(preferences.value.chat)
    }
    fun openChat() { closeComments(); syncChat(); chatController.open() }
    fun closeChat() = chatController.close()
    fun presentChat(visible: Boolean) = chatController.present(visible)
    fun retryChat() = chatController.retry()
    fun followChat() = chatController.follow()
    fun olderChat() = chatController.older()
    fun chatPosition(position: CommentPosition, following: Boolean, occurrence: String, context: ApiContext?) {
        if (chatReplay.value.occurrence == occurrence && chatReplay.value.context == context) chatController.position(position, following)
    }
    fun chatTiming(value: Int) = chatController.timing(value)
    fun retryChatTiming() = chatController.retryTiming()
    fun setChatAppearance(value: ChatAppearance, context: ApiContext = api.context()): Boolean {
        return try { store.chatAppearance(value.bounded(), context); chatAppearance.value = value.bounded(); true }
        catch (e: CancellationException) { false }
        catch (e: Exception) { message.value = friendly(e); false }
    }
    fun setBackground(value: Boolean, context: ApiContext): Boolean = try { store.background(value, context); true }
        catch (_: CancellationException) { false } catch (e: Exception) { message.value = friendly(e); false }
    fun setPip(value: Boolean, context: ApiContext): Boolean = try { store.pip(value, context); true }
        catch (_: CancellationException) { false } catch (e: Exception) { message.value = friendly(e); false }
    suspend fun saveChatPreferences(value: ChatPreferences, before: ChatPreferences, context: ApiContext) = preferenceWrites.withLock {
        ChatFilters.validateChanges(value, before)
        if (api.context() != context) throw CancellationException("Account or instance changed")
        val patch = JSONObject(); val old = before.json(); val changed = value.json()
        changed.keys().forEach { if (old.get(it) != changed.get(it)) patch.put(it, changed.get(it)) }
        if (patch.length() == 0) return@withLock
        preferenceGeneration++
        val saved = if (context.account == null) LocalPreferences.normalize(preferences.value.merge(patch)).also { store.guestDeArrow(it, context) }
            else api.chatPreferences(patch, context)
        if (api.context() == context) { preferenceGeneration++; preferencesContext = context; preferences.value = saved }
    }
    fun closeComments() = commentController.close()
    fun backComments() = commentController.back()
    fun sortComments(sort: CommentSort) = commentController.sort(sort)
    fun openReplies(comment: Comment) = commentController.replies(comment)
    fun loadComments(more: Boolean = false, threadKey: String? = null) = commentController.load(more, threadKey)
    fun commentPosition(threadKey: String?, position: CommentPosition) = commentController.position(threadKey, position)
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message.value = friendly(e) } } }
    private fun acceptAuthentication(value: Account, context: ApiContext, resumeIntent: Boolean = true, replaceCurrent: Boolean = false) {
        if (context != api.context()) throw CancellationException("Account or instance changed")
        homeAppliedContext = null
        if (replaceCurrent) store.replaceAccount(context, value) else store.save(value)
        val accepted = store.account.value
        homeAppliedContext = api.context()
        val origin = signInReturn; signInReturn = null
        // Run after the account collector resets account-scoped state.
        viewModelScope.launch { yield()
            if (account.value != accepted) return@launch
            if (replaceCurrent) return@launch
            if (resumeIntent && origin != null) restoreBrowse(origin, afterSignIn = true)
            else navigate("You")
            authenticationFinished.value++
        }
    }
    private suspend fun <T> accountOperation(block: suspend () -> T): T = viewModelScope.async {
        accountBusy.value = true
        try { block() } finally { accountBusy.value = false }
    }.await()
    suspend fun login(username: String, password: String) = accountOperation {
        val context = api.context(); acceptAuthentication(api.login(username, password, context), context)
    }
    suspend fun register(username: String, password: String, confirmation: String, answer: String, token: String) = accountOperation {
        val context = api.context(); acceptAuthentication(api.register(username, password, confirmation, answer, token, context), context)
    }
    suspend fun changeCredentials(kind: String, fields: JSONObject, context: ApiContext) = accountOperation {
        acceptAuthentication(api.changeCredentials(kind, fields, context), context, resumeIntent = false, replaceCurrent = true)
    }
    suspend fun createAccountToken(password: String, scopes: List<String>, expires: Long?, context: ApiContext) = accountOperation {
        api.createToken(password, scopes, expires, context)
    }
    fun clearAccount(context: ApiContext) {
        if (context != api.context()) return
        signInReturn = null; searchReturn = null
        closePlayer(); store.save(null)
        tabBrowse.clear(); app.cache.clear(); navigate("You")
    }
    suspend fun deleteAccount(password: String, context: ApiContext) = accountOperation {
        api.deleteAccount(password, context)
        try { store.deleteProfile(context) } finally { clearAccount(context) }
    }
    suspend fun revokeSession(session: AccountSession, context: ApiContext) = accountOperation {
        api.revokeSession(session.id, context); if (session.current) clearAccount(context)
    }
    fun logout() = action { accountOperation { val context = api.context(); try { api.logout() } finally { clearAccount(context) } } }
    fun switchServer(value: String) { dismissRss(); pendingPlaylistSubscription = null; sponsorSettingsChannel.value = null; val address = InvidiousApi.normalizeServer(value, net.wingress.mobivious.BuildConfig.DEBUG); if (address == store.server) return; pendingClipEditor = null; clipJob?.cancel(); clipEditorJob?.cancel(); clipDeleteJob?.cancel(); clipEditor.value = ClipEditorState(); dismissClipResolution(); clearNavigationReturns(); postCommentController.bind(null); closePlayer(); saveSheet.value = PlaylistSaveState(); blockUndo.value = null; store.save(null); app.cache.clear(); dearrowTitles.clear(); dearrowIdentity.value = null; dearrowIdentityError.value = null; store.server = address; subscriptionsController.reset(); subscriptionChannelParent = false; subscriptionFeedSearch = SearchInput(); searchVisibility.value = store.searchVisibility(api.context()); preferences.value = store.guestDeArrow(); region = preferences.value.region; homeAppliedContext = null; openDefaultHome() }
    suspend fun savePreferences(value: AccountPreferences, before: AccountPreferences, context: ApiContext): Unit = preferenceWrites.withLock {
        if (api.context() != context) throw CancellationException("Account or instance changed")
        preferenceGeneration++
        val changes = value.changesFrom(before, includeChannelNames = context.account == null)
        val saved = if (context.account == null) {
            LocalPreferences.normalize(preferences.value.merge(changes)).also { store.guestDeArrow(it, context) }
        } else if (changes.length() > 0) try { api.preferences(changes, context) }
            catch (e: ApiException) {
                if (e.status in listOf(404, 405) || e.status == 400 && (e.message?.contains("preference", true) == true || e.message?.contains("Only boolean") == true))
                    throw ApiException(400, "This server needs the Mobivious settings API update before these shared settings can be saved.")
                throw e
            } else preferences.value
        if (api.context() == context) {
            val refreshCurrent = currentBrowseAffected(preferences.value, saved)
            invalidateSavedTabs(preferences.value, saved)
            preferenceGeneration++; preferencesContext = context; preferences.value = saved; region = saved.region
            syncSponsorSettings(); syncHistorySettings(); if (!saved.savePosition) store.clearPositions()
            if (refreshCurrent) refresh()
            message.value = if (context.account == null) "Settings saved" else "Account settings saved"
        }
    }
    suspend fun importDeArrowIdentity(privateId: String, context: ApiContext) {
        require(DeArrowRules.validPrivateId(privateId)) { "Enter a private user ID of 30–256 letters, numbers, underscores or hyphens." }
        api.importDeArrowIdentity(privateId.trim(), context)
        if (api.context() != context) return
        val identityVersion = ++identityGeneration
        message.value = "Private identity settings saved"
        try { val identity = api.dearrowIdentity(context); if (api.context() == context && identityVersion == identityGeneration) { dearrowIdentity.value = identity; dearrowIdentityError.value = null } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (api.context() == context && identityVersion == identityGeneration) dearrowIdentityError.value = "Identity settings were saved, but the status could not be refreshed." }
    }
    fun openDeArrow(id: String) {
        if (account.value == null) { message.value = "Sign in to suggest titles and vote."; return }
        val context = api.context()
        val old = dearrowContribution.value
        dearrowContribution.value = if (old.videoId == id && old.context == context) old.copy(open = true) else DeArrowContributionState(open = true, videoId = id, context = context)
        if (!dearrowContribution.value.loaded && !dearrowContribution.value.busy) refreshDeArrow()
    }
    fun closeDeArrow() { dearrowContribution.value = dearrowContribution.value.copy(open = false) }
    fun editDeArrowDraft(value: String) { dearrowContribution.value = dearrowContribution.value.copy(draft = value, review = false, acknowledgements = emptySet()) }
    fun reviewDeArrow(review: Boolean) { if (!review || DeArrowRules.validTitle(dearrowContribution.value.draft)) dearrowContribution.value = dearrowContribution.value.copy(review = review, acknowledgements = emptySet()) }
    fun acknowledgeDeArrow(index: Int, checked: Boolean) { val state = dearrowContribution.value; if (index !in 0..3 || state.busy || !state.review) return; dearrowContribution.value = state.copy(acknowledgements = if (checked) state.acknowledgements + index else state.acknowledgements - index) }
    private fun sameContribution(state: DeArrowContributionState) = api.context() == state.context && dearrowContribution.value.context == state.context && dearrowContribution.value.videoId == state.videoId
    fun refreshDeArrow() {
        val state = dearrowContribution.value
        val context = state.context ?: return
        if (state.busy) return
        dearrowContribution.value = state.copy(busy = true, status = "Loading submissions…")
        val identityVersion = ++identityGeneration
        contributionJob = viewModelScope.launch {
            try {
                val identity = api.dearrowIdentity(context)
                if (!sameContribution(state)) return@launch
                if (identityVersion == identityGeneration) { dearrowIdentity.value = identity; dearrowIdentityError.value = null }
                if (!identity.ready) { dearrowContribution.value = dearrowContribution.value.copy(status = "The instance administrator must configure DeArrow contribution storage."); return@launch }
                val titles = api.dearrowSubmissions(state.videoId, context)
                if (sameContribution(state)) dearrowContribution.value = dearrowContribution.value.copy(titles = titles, loaded = true, status = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (sameContribution(state)) dearrowContribution.value = dearrowContribution.value.copy(status = friendly(e)) }
            finally { if (sameContribution(state)) dearrowContribution.value = dearrowContribution.value.copy(busy = false) }
        }
    }
    fun contributeDeArrow(action: String, item: DeArrowSubmission? = null, original: Boolean = false) {
        val state = dearrowContribution.value
        val context = state.context ?: return
        if (state.busy || dearrowIdentity.value?.ready != true || !sameContribution(state)) return
        if (action == "submit" && (!DeArrowRules.validTitle(state.draft) || state.acknowledgements.size != 4)) return
        if (action == "downvote" && !DeArrowRules.canDownvote(item)) return
        val fields = org.json.JSONObject().put("action", action)
        if (action == "submit") fields.put("title", state.draft.trim()).put("confirmed", true)
        else fields.put("original", original).put("uuid", item?.uuid.orEmpty())
        dearrowContribution.value = state.copy(busy = true, status = "Sending…")
        contributionJob = viewModelScope.launch {
            try {
                api.contributeDeArrow(state.videoId, fields, context)
                if (!sameContribution(state)) return@launch
                val identityVersion = ++identityGeneration
                dearrowTitles.invalidate(state.videoId); ensureDeArrow(state.videoId)
                dearrowContribution.value = dearrowContribution.value.copy(status = "Accepted by DeArrow. Rankings may take time to update.",
                    draft = if (action == "submit") "" else state.draft, review = false, acknowledgements = emptySet())
                try {
                    val titles = api.dearrowSubmissions(state.videoId, context)
                    val identity = api.dearrowIdentity(context)
                    if (sameContribution(state)) { dearrowContribution.value = dearrowContribution.value.copy(titles = titles, loaded = true); if (identityVersion == identityGeneration) dearrowIdentity.value = identity }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (sameContribution(state)) dearrowContribution.value = dearrowContribution.value.copy(status = "Your action was accepted, but the list could not be refreshed.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (sameContribution(state)) dearrowContribution.value = dearrowContribution.value.copy(status = if (e is ApiException) friendly(e) else "DeArrow did not confirm this action. Refresh submissions before trying again.") }
            finally { if (sameContribution(state)) dearrowContribution.value = dearrowContribution.value.copy(busy = false) }
        }
    }
    fun toggleSubscribe(id: String) {
        val context = api.context()
        val subscribe = subscriptionChannels.value.channels.none { it.id == id }
        viewModelScope.launch {
            try { api.subscribe(id, subscribe, context); if (context == api.context()) refreshSubscriptions() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (context == api.context()) message.value = friendly(e) }
        }
    }
    override fun onCleared() { cancelAccumulatedSeek(); MediaController.releaseFuture(future) }
    private fun friendly(e: Exception) = if (e is ApiException || e is IllegalArgumentException) e.message ?: "Request failed." else "Cannot reach this instance. Check the address and connection, then retry."
}
private suspend fun ListenableFuture<MediaController>.awaitController(): MediaController = suspendCancellableCoroutine { continuation ->
    // Cancelling one queued action must not cancel the controller shared by the whole ViewModel.
    addListener({
        try { continuation.resume(get()) }
        catch (e: Exception) { continuation.resumeWithException(e) }
    }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
}
