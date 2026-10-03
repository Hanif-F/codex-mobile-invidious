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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class BrowseState(val title: String = "For you", val videos: List<Video> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val page: Int = 1, val continuation: String = "", val end: Boolean = false,
    val history: HistoryPage? = null)
data class PlaybackState(val details: VideoDetails? = null, val loading: Boolean = false, val error: String? = null,
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0, val buffering: Boolean = false,
    val mediaId: String = "", val playWhenReady: Boolean = false, val playerState: Int = Player.STATE_IDLE,
    val bufferedPosition: Long = 0, val seekable: Boolean = false, val live: Boolean = false,
    val speed: Float = 1f, val tracks: Tracks = Tracks.EMPTY, val selection: TrackSelectionParameters? = null,
    val canPlay: Boolean = false, val canSetSpeed: Boolean = false, val canSelectTracks: Boolean = false,
    val canRefresh: Boolean = false)
data class DeArrowContributionState(val open: Boolean = false, val videoId: String = "", val context: ApiContext? = null,
    val titles: List<DeArrowSubmission> = emptyList(), val busy: Boolean = false, val loaded: Boolean = false,
    val draft: String = "", val review: Boolean = false, val acknowledgements: Set<Int> = emptySet(), val status: String? = null)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MobiviousApplication
    val store = app.store
    val api = app.api
    val account = store.account
    val offline = app.offline
    val watched = app.watched.state
    val blocked = app.blocked.state
    val searchVisibility = MutableStateFlow(store.searchVisibility(api.context()))
    val browse = MutableStateFlow(BrowseState())
    val searchInput = MutableStateFlow(SearchInput())
    val scopedSearch = MutableStateFlow(SearchInput())
    val browseReset = MutableStateFlow(0L)
    val playback = MutableStateFlow(PlaybackState())
    val controller = MutableStateFlow<MediaController?>(null)
    val pendingSeek = MutableStateFlow<PendingSeek?>(null)
    private val seekAccumulator = SeekAccumulator()
    private var seekJob: Job? = null
    private var seekContext: ApiContext? = null
    private data class SelectionSnapshot(val video: StreamKey?, val audio: StreamKey?, val text: StreamKey?,
        val parameters: TrackSelectionParameters, val speed: Float, val playing: Boolean)
    private data class SelectionRequest(val mediaId: String, val quality: String, val snapshot: SelectionSnapshot?)
    private var selectionRequest: SelectionRequest? = null
    val channel = MutableStateFlow<Channel?>(null)
    val channelTab = MutableStateFlow<ChannelTab?>(null)
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlist = MutableStateFlow<Playlist?>(null)
    val subscriptions = MutableStateFlow<List<Channel>>(emptyList())
    val comments = MutableStateFlow(Page<Comment>(emptyList()))
    val commentError = MutableStateFlow<String?>(null)
    val preferences = MutableStateFlow(store.guestDeArrow())
    val sponsorBlock = MutableStateFlow(SponsorBlockPlayback())
    val sponsorSettingsChannel = MutableStateFlow<String?>(null)
    fun openSponsorBlock(channelId: String = "") { sponsorSettingsChannel.value = channelId; refreshSharedSettings() }
    val dearrowTitles = DeArrowTitles(viewModelScope, { store.server }) { api.dearrowTitle(it) }
    val dearrowIdentity = MutableStateFlow<DeArrowIdentity?>(null)
    val dearrowIdentityError = MutableStateFlow<String?>(null)
    val dearrowContribution = MutableStateFlow(DeArrowContributionState())
    val message = MutableStateFlow<String?>(null)
    var tab = PreferenceRules.destination(preferences.value.defaultHome, account.value != null).first
    val navigation = MutableStateFlow(tab to "")
    var route = ""
    var query: String
        get() = searchInput.value.submitted
        set(value) { searchInput.value = SearchInput(value, value) }
    var discovery = PreferenceRules.destination(preferences.value.defaultHome, account.value != null).second
    var region = preferences.value.region
    var sort = "relevance"
    var date = ""
    var durationFilter = ""
    private var browseJob: Job? = null
    private var videoJob: Job? = null
    private var commentJob: Job? = null
    private var contributionJob: Job? = null
    private var preferenceGeneration = 0L
    private val preferenceWrites = Mutex()
    private val playerDefaultWrites = Mutex()
    private var preferencesContext: ApiContext? = null
    private var identityGeneration = 0L
    private var browseGeneration = 0
    private var requestedVideo: String? = null
    private var homeAppliedContext: ApiContext? = null
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (command.customAction == PlaybackService.SPONSOR_STATE) receiveSponsorState(args)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).buildAsync()
    init {
        future.addListener({
            runCatching {
                controller.value = future.get().apply {
                    addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) { applyRequestedSelection(); updatePlayback() }
                        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { cancelAccumulatedSeek() }
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (pendingSeek.value?.mediaId != mediaItem?.mediaId) cancelAccumulatedSeek(false)
                        }
                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { if (playWhenReady) cancelAccumulatedSeek(false) }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { cancelAccumulatedSeek(false); playback.value = playback.value.copy(error = "Playback failed. Retry to refresh the stream.", buffering = false) }
                        override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                            if (error == null) playback.value = playback.value.copy(error = null)
                        }
                    })
                    if (currentMediaItem == null) setPlaybackSpeed(store.defaultSpeed)
                    currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }?.let { id ->
                        requestedVideo = id
                        val restoreContext = api.context()
                        viewModelScope.launch { runCatching { api.video(id) }.onSuccess {
                            if (api.context() == restoreContext && requestedVideo == id && controller.value?.currentMediaItem?.mediaId == id) {
                                playback.value = playback.value.copy(details = it); ensureDeArrow(id); syncDeArrowMetadata(); syncSponsorSettings()
                            }
                        } }
                    }
                    val stateContext = api.context()
                    val state = sendCustomCommand(SessionCommand(PlaybackService.SPONSOR_STATE, Bundle.EMPTY), Bundle.EMPTY)
                    state.addListener({ if (api.context() == stateContext && sponsorBlock.value.token.isEmpty()) runCatching { receiveSponsorState(state.get().extras) } }, ContextCompat.getMainExecutor(application))
                }
            }.onFailure { message.value = "Unable to connect to the player." }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch { while (isActive) { delay(500); updatePlayback() } }
        viewModelScope.launch { account.collect {
            browseJob?.cancel(); browseGeneration++; browse.value = BrowseState()
            app.blocked.reset(); searchVisibility.value = store.searchVisibility(api.context())
            searchInput.value = SearchInput(); scopedSearch.value = SearchInput()
            cancelAccumulatedSeek(false)
            sponsorSettingsChannel.value = null
            sponsorBlock.value = SponsorBlockPlayback()
            preferenceGeneration++; identityGeneration++
            dearrowTitles.clear(); contributionJob?.cancel(); dearrowContribution.value = DeArrowContributionState()
            dearrowIdentity.value = null; dearrowIdentityError.value = null
            preferencesContext = if (it == null) api.context() else null
            preferences.value = if (it == null) store.guestDeArrow() else AccountPreferences()
            if (it != null) refreshAccount() else { subscriptions.value = emptyList(); playlists.value = emptyList() }
            refresh()
        } }
        viewModelScope.launch { combine(preferences, dearrowTitles.titles) { prefs, _ -> prefs }.collect {
            if (it.dearrowEnabled) playback.value.details?.video?.id?.let(::ensureDeArrow)
            else dearrowTitles.clear()
            syncDeArrowMetadata()
        } }
        viewModelScope.launch { preferences.collect { syncSponsorSettings(); syncHistorySettings() } }
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
        val settings = preferences.value.sponsorBlock.effective(video.channelId, account.value != null)
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
            canRefresh = p.mediaItemCount > 0 && p.isCommandAvailable(Player.COMMAND_STOP) && p.isCommandAvailable(Player.COMMAND_PREPARE))
    }
    fun refreshAccount() {
        refreshSharedSettings()
        val context = api.context()
        action { val channels = api.subscriptions(); val lists = api.playlists(); if (api.context() == context) { subscriptions.value = channels; playlists.value = lists } }
    }
    fun refreshSharedSettings() {
        val context = api.context()
        refreshBlockedChannels()
        refreshWatched()
        if (context.account == null) { preferencesContext = context; preferences.value = store.guestDeArrow(); syncSponsorSettings(); syncHistorySettings(); refreshWatched(); return }
        val prefsGeneration = ++preferenceGeneration
        val identityVersion = ++identityGeneration
        action { val value = api.preferences(context); if (api.context() == context && prefsGeneration == preferenceGeneration) {
            val membersChanged = value.showMemberVideos != preferences.value.showMemberVideos
            preferencesContext = context; preferences.value = value; region = value.region; syncSponsorSettings(); syncHistorySettings(); refreshWatched()
            if (homeAppliedContext != context) { homeAppliedContext = context; openDefaultHome() }
            else if (tab == "Subscriptions" || route == "history" || membersChanged && route.startsWith("playlist:")) refresh()
        } }
        viewModelScope.launch {
            try { val identity = api.dearrowIdentity(context); if (api.context() == context && identityVersion == identityGeneration) { dearrowIdentity.value = identity; dearrowIdentityError.value = null } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (api.context() == context && identityVersion == identityGeneration) { dearrowIdentity.value = null; dearrowIdentityError.value = friendly(e) } }
        }
    }
    fun ensureDeArrow(id: String) { if (preferences.value.dearrowEnabled) dearrowTitles.ensure(id) }
    fun refreshBlockedChannels() { viewModelScope.launch { app.blocked.refresh() } }
    fun toggleBlocked(id: String, name: String) {
        val context = api.context()
        val block = id !in blocked.value.ids
        action {
            app.blocked.setBlocked(context, id, name, block)
            if (api.context() == context) { message.value = if (block) "Channel blocked" else "Channel unblocked"; refreshBlockedChannels() }
        }
    }
    fun saveSearchVisibility(value: SearchVisibility) {
        store.saveSearchVisibility(api.context(), value); searchVisibility.value = value
    }
    fun contentSurface(): ContentSurface = when {
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
    fun displayTitle(video: Video): String = if (preferences.value.dearrowEnabled) dearrowTitles.titles.value[video.id] ?: video.title else video.title
    private fun syncDeArrowMetadata() {
        val video = playback.value.details?.video ?: return
        val p = controller.value ?: return
        val title = displayTitle(video)
        if (p.currentMediaItem?.mediaId == video.id && p.mediaMetadata.title?.toString() != title) {
            p.sendCustomCommand(SessionCommand(PlaybackService.SET_DISPLAY_TITLE, Bundle.EMPTY), Bundle().apply { putString("mediaId", video.id); putString("title", title) })
        }
    }
    fun navigate(tab: String, route: String = "") { this.tab = tab; navigation.value = tab to route; this.route = route; scopedSearch.value = SearchInput(); channel.value = null; channelTab.value = null; playlist.value = null; refresh() }
    fun editSearch(value: String, scoped: Boolean) {
        val input = if (scoped) scopedSearch else searchInput
        input.value = input.value.copy(draft = value)
    }
    fun submitSearch(scoped: Boolean) {
        val input = if (scoped) scopedSearch else searchInput
        input.value = input.value.copy(submitted = input.value.draft.trim())
        refresh()
    }
    fun clearScopedSearch() { scopedSearch.value = SearchInput(); refresh() }
    fun openDefaultHome() { val target = PreferenceRules.destination(preferences.value.defaultHome, account.value != null); discovery = target.second; navigate(target.first) }
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
    fun selectChannelTab(value: ChannelTab) {
        val info = channel.value ?: return
        if (!route.startsWith("channel:") || value !in info.contentTabs || value == channelTab.value && scopedSearch.value.submitted.isBlank()) return
        scopedSearch.value = SearchInput()
        channelTab.value = value
        dearrowTitles.clear()
        load(false, refreshChannel = false)
    }
    private fun load(more: Boolean, refreshChannel: Boolean = true) {
        if (more && (browse.value.loading || browse.value.end)) return
        browseJob?.cancel()
        val generation = ++browseGeneration
        val old = if (more) browse.value else BrowseState()
        if (!more) browseReset.value++
        val page = if (more) old.page + 1 else 1
        val selectedTab = tab; val selectedRoute = route; val selectedQuery = query
        val scopedQuery = scopedSearch.value.submitted
        val context = api.context()
        val selectedChannel = channel.value; val selectedChannelTab = channelTab.value
        browse.value = old.copy(loading = true, error = null, title = if (selectedRoute == "history") "History" else selectedTab)
        browseJob = viewModelScope.launch {
            try {
                var continuation = ""
                var hasMore: Boolean? = null
                var history: HistoryPage? = null
                val videos = when {
                    selectedRoute.startsWith("channel:") -> {
                        val id = selectedRoute.substringAfter(':')
                        val info = if (selectedChannel == null || !more && refreshChannel) api.channel(id) else selectedChannel
                        if (generation != browseGeneration || context != api.context()) throw CancellationException("Channel request superseded")
                        val contentTab = info.preferredTab(selectedChannelTab)
                        channel.value = info; channelTab.value = contentTab
                        if (scopedQuery.isNotBlank()) {
                            val response = api.channelSearch(id, scopedQuery, page, context)
                            hasMore = response.hasMore
                            response.items
                        } else {
                            val token = if (more) old.continuation else ""
                            val response = when (contentTab) {
                                ChannelTab.VIDEOS -> api.channelVideos(id, token)
                                ChannelTab.STREAMS -> api.channelStreams(id, token)
                            }
                            continuation = response.continuation; response.items
                        }
                    }
                    selectedRoute.startsWith("playlist:") -> api.playlist(selectedRoute.substringAfter(':'), page).let { if (generation == browseGeneration && context == api.context()) playlist.value = it.first; it.second }
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
                    selectedTab == "Search" -> if (selectedQuery.isBlank()) emptyList() else api.search(selectedQuery, page, sort, date, durationFilter)
                    selectedTab == "Subscriptions" -> if (context.account == null) emptyList() else if (scopedQuery.isNotBlank()) {
                        val response = api.subscriptionSearch(scopedQuery, page, context)
                        hasMore = response.hasMore; response.items
                    } else api.feed(page, preferences.value.notificationsOnly)
                    selectedTab == "Library" -> { if (account.value != null) playlists.value = api.playlists(); emptyList() }
                    else -> api.discovery(discovery, region)
                }
                if (generation == browseGeneration && context == api.context()) browse.value = browse.value.copy(videos = ContentVisibility.merge(old.videos, videos), loading = false, page = page,
                    continuation = continuation, history = history, end = if (hasMore != null) !hasMore || more && videos.isNotEmpty() && ContentVisibility.exhausted(old.videos, videos) else videos.isEmpty() || more && ContentVisibility.exhausted(old.videos, videos) ||
                        selectedTab == "Home" && selectedRoute.isEmpty() || selectedRoute.startsWith("channel:") && continuation.isBlank() ||
                        selectedTab == "Subscriptions" && selectedRoute.isEmpty() && (preferences.value.latestOnly || preferences.value.notificationsOnly))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (generation == browseGeneration && context == api.context()) browse.value = browse.value.copy(loading = false, error = friendly(e))
            }
        }
    }
    fun play(id: String, explicit: Long? = null) = loadVideo(id, explicit)
    private fun loadVideo(id: String, explicit: Long? = null, snapshot: SelectionSnapshot? = null) {
        cancelAccumulatedSeek(false)
        selectionRequest = null
        if (id == playback.value.details?.video?.id && explicit == null) { controller.value?.play(); return }
        requestedVideo = id
        if (id != dearrowContribution.value.videoId) { contributionJob?.cancel(); dearrowContribution.value = DeArrowContributionState() }
        videoJob?.cancel(); commentJob?.cancel(); comments.value = Page(emptyList()); commentError.value = null
        playback.value = playback.value.copy(loading = true, error = null)
        videoJob = viewModelScope.launch {
            try {
                val context = api.context()
                val prefsGeneration = preferenceGeneration
                val fetched = if (context.account != null) try { api.preferences(context) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { preferences.value }
                    else store.guestDeArrow().copy(watchHistory = false)
                val prefs = if (prefsGeneration != preferenceGeneration && preferencesContext == context) preferences.value else fetched
                val details = api.video(id, prefs.local)
                if (api.context() != context) throw CancellationException("Account or instance changed")
                if (prefsGeneration == preferenceGeneration) { preferencesContext = context; preferences.value = prefs }
                app.watched.configure(context, prefs.savePosition)
                val saved = if (prefs.savePosition && account.value != null) try { api.position(id, context) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { store.position(id, context) }
                    else if (prefs.savePosition) store.position(id, context) else 0
                if (api.context() != context) throw CancellationException("Account or instance changed")
                val start = PlaybackRules.resume(saved, details.video.duration, explicit)
                val base = store.server.toHttpUrlOrNull()!!
                fun resolve(url: String) = base.resolve(url)?.takeIf { it.isHttps || net.wingress.mobivious.BuildConfig.DEBUG && it.host in listOf("10.0.2.2", "127.0.0.1", "localhost") }?.toString().orEmpty()
                val raw = details.hls.ifBlank { details.dash }.ifBlank { details.fallback }
                val stream = resolve(raw).ifBlank { if (details.dash.isNotBlank() || !details.video.live) api.url("api/manifest/dash/id/$id", mapOf("local" to prefs.local.toString())).toString() else "" }
                require(stream.isNotBlank()) { "No playable stream is available." }
                val uri = stream.toHttpUrlOrNull()!!.newBuilder().setQueryParameter("local", prefs.local.toString()).build().toString()
                val metadata = MediaMetadata.Builder().setTitle(details.video.title).setArtist(details.video.author)
                    .setArtworkUri(Uri.parse(resolve(details.video.thumbnail))).setExtras(Bundle().apply {
                        putBoolean("history", prefs.watchHistory); putBoolean("savePosition", prefs.savePosition)
                        putString("channelId", details.video.channelId); putBoolean("liveNow", details.video.live)
                        putString("sponsorblock", prefs.sponsorBlock.effective(details.video.channelId, account.value != null).json().toString())
                    }).build()
                val caption = PreferenceRules.caption(prefs.captions, details.captions)
                val item = MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata)
                    .setMimeType(if (details.hls.isNotBlank()) MimeTypes.APPLICATION_M3U8 else if (details.dash.isNotBlank() || details.fallback.isBlank()) MimeTypes.APPLICATION_MPD else MimeTypes.VIDEO_MP4)
                    .setSubtitleConfigurations(details.captions.map { track -> MediaItem.SubtitleConfiguration.Builder(Uri.parse(resolve(track.url))).setMimeType(MimeTypes.TEXT_VTT).setLanguage(track.language).setLabel(track.label)
                        .setSelectionFlags(if (track == caption) C.SELECTION_FLAG_DEFAULT else 0).build() }).build()
                val p = controller.value ?: future.awaitController()
                p.pause()
                p.trackSelectionParameters = (snapshot?.parameters ?: p.trackSelectionParameters).buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT).clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) ?: prefs.listen)
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    .setPreferredAudioLanguage(snapshot?.parameters?.preferredAudioLanguages?.firstOrNull())
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: (caption == null))
                    .setPreferredTextLanguage(snapshot?.parameters?.preferredTextLanguages?.firstOrNull() ?: caption?.language).build()
                p.setPlaybackSpeed(snapshot?.speed ?: prefs.speed)
                p.setMediaItem(item, start * 1000)
                selectionRequest = SelectionRequest(id, prefs.qualityDash, snapshot)
                p.prepare(); p.playWhenReady = snapshot?.playing ?: prefs.autoplay
                playback.value = PlaybackState(details = details)
                ensureDeArrow(id); syncDeArrowMetadata()
                updatePlayback()
                syncSponsorSettings()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { playback.value = playback.value.copy(loading = false, error = friendly(e)) }
        }
    }
    private fun selectionSnapshot(): SelectionSnapshot? = controller.value?.let { p ->
        fun selected(type: Int): StreamKey? = p.currentTracks.groups.filter { it.type == type }.firstNotNullOfOrNull { group ->
            p.trackSelectionParameters.overrides[group.mediaTrackGroup]?.trackIndices?.singleOrNull()?.let { StreamKey.of(group.getTrackFormat(it)) }
        }
        SelectionSnapshot(selected(C.TRACK_TYPE_VIDEO), selected(C.TRACK_TYPE_AUDIO), selected(C.TRACK_TYPE_TEXT), p.trackSelectionParameters, p.playbackParameters.speed, p.playWhenReady)
    }
    private fun applyRequestedSelection() {
        val request = selectionRequest ?: return
        val p = controller.value ?: return
        if (p.currentMediaItem?.mediaId != request.mediaId || p.playbackState != Player.STATE_READY || p.currentTracks.groups.isEmpty()) return
        selectionRequest = null // Clear before setting parameters, which itself emits player events.
        val builder = p.trackSelectionParameters.buildUpon()
        for (type in listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
            val choices = StreamCatalog.choices(p.currentTracks, type)
            val key = when (type) { C.TRACK_TYPE_VIDEO -> request.snapshot?.video; C.TRACK_TYPE_AUDIO -> request.snapshot?.audio; else -> request.snapshot?.text }
            val choice = if (request.snapshot != null) StreamCatalog.find(choices, key)
                else if (type == C.TRACK_TYPE_VIDEO) StreamCatalog.defaultVideo(choices, request.quality) else null
            if (choice != null) builder.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
            else if (key != null && type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(null)
        }
        p.trackSelectionParameters = builder.build()
    }
    fun retryPlayback() {
        cancelAccumulatedSeek()
        val id = requestedVideo ?: return
        val at = if (controller.value?.currentMediaItem?.mediaId == id) controller.value!!.currentPosition / 1000 else null
        val snapshot = if (controller.value?.currentMediaItem?.mediaId == id) selectionSnapshot() else null
        playback.value = playback.value.copy(details = null)
        loadVideo(id, at, snapshot)
    }
    fun closePlayer() { cancelAccumulatedSeek(false); selectionRequest = null; videoJob?.cancel(); contributionJob?.cancel(); dearrowContribution.value = DeArrowContributionState(); controller.value?.stop(); controller.value?.clearMediaItems(); playback.value = PlaybackState() }
    fun togglePlay() { cancelAccumulatedSeek(false); controller.value?.let {
        if (it.playbackState == Player.STATE_ENDED) { it.seekToDefaultPosition(); it.play() }
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
    fun autoQuality() { selectionRequest = null; controller.value?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE).build() } }
    // Resolution callers select one current-video representation; defaults are edited in Settings.
    fun quality(height: Int) { if (height == Int.MAX_VALUE) autoQuality() else controller.value?.let { p ->
        StreamCatalog.defaultVideo(StreamCatalog.choices(p.currentTracks, C.TRACK_TYPE_VIDEO), "${height}p")?.let { selectTrack(it.group, it.index) }
    } }
    fun speed(value: Float) { store.defaultSpeed = value; controller.value?.setPlaybackSpeed(value); updatePlayerDefault { it.copy(speed = value) } }
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
        if (group.type == C.TRACK_TYPE_VIDEO) selectionRequest = null
        val builder = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group, index))
        if (group.type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(group.getFormat(index).language)
        else if (group.type == C.TRACK_TYPE_TEXT) builder.setPreferredTextLanguage(group.getFormat(index).language)
        p.trackSelectionParameters = builder.build()
    } }
    fun autoAudio() { controller.value?.let { p -> p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(null).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build() } }
    fun loadComments(more: Boolean = false) {
        val id = playback.value.details?.video?.id ?: return
        commentJob?.cancel()
        commentJob = viewModelScope.launch { try { val next = api.comments(id, if (more) comments.value.continuation else ""); comments.value = next.copy(items = if (more) comments.value.items + next.items else next.items); commentError.value = null } catch (e: CancellationException) { throw e } catch (e: Exception) { commentError.value = friendly(e) } }
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message.value = friendly(e) } } }
    suspend fun login(username: String, password: String) { store.save(api.login(username, password)); refresh() }
    fun logout() = action { val context = api.context(); try { api.logout() } finally { closePlayer(); store.clearPositions(context); store.clearVisibilitySnapshot(context); store.save(null); app.cache.clear(); navigate("Home") } }
    fun switchServer(value: String) { sponsorSettingsChannel.value = null; val address = InvidiousApi.normalizeServer(value, net.wingress.mobivious.BuildConfig.DEBUG); if (address == store.server) return; closePlayer(); store.save(null); store.clearPositions(); app.cache.clear(); dearrowTitles.clear(); dearrowIdentity.value = null; dearrowIdentityError.value = null; store.server = address; searchVisibility.value = store.searchVisibility(api.context()); preferences.value = store.guestDeArrow(); region = preferences.value.region; homeAppliedContext = null; openDefaultHome() }
    suspend fun savePreferences(value: AccountPreferences, before: AccountPreferences, context: ApiContext): Unit = preferenceWrites.withLock {
        if (api.context() != context) throw CancellationException("Account or instance changed")
        preferenceGeneration++
        val changes = value.changesFrom(before)
        val saved = if (context.account == null) {
            preferences.value.merge(changes).let { it.copy(watchHistory = false, sponsorBlock = it.sponsorBlock.copy(channels = emptyMap())) }.also { store.guestDeArrow(it) }
        } else if (changes.length() > 0) try { api.preferences(changes, context) }
            catch (e: ApiException) {
                if (e.status in listOf(404, 405) || e.status == 400 && (e.message?.contains("preference", true) == true || e.message?.contains("Only boolean") == true))
                    throw ApiException(400, "This server needs the Mobivious settings API update before these shared settings can be saved.")
                throw e
            } else preferences.value
        if (api.context() == context) {
            preferenceGeneration++; preferencesContext = context; preferences.value = saved; region = saved.region
            syncSponsorSettings(); syncHistorySettings(); if (!saved.savePosition) store.clearPositions()
            if (changes.keys().asSequence().any { it in listOf("region", "max_results", "sort", "latest_only", "unseen_only", "notifications_only", "show_member_videos") }) refresh()
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
    fun toggleSubscribe(id: String) = action { api.subscribe(id, subscriptions.value.none { it.id == id }); subscriptions.value = api.subscriptions() }
    override fun onCleared() { cancelAccumulatedSeek(); MediaController.releaseFuture(future) }
    private fun friendly(e: Exception) = if (e is ApiException || e is IllegalArgumentException) e.message ?: "Request failed." else "Cannot reach this instance. Check the address and connection, then retry."
}
private suspend fun com.google.common.util.concurrent.ListenableFuture<MediaController>.awaitController(): MediaController = withContext(Dispatchers.IO) { get() }
