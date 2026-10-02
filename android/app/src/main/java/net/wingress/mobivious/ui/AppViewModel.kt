package net.wingress.mobivious.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.wingress.mobivious.MobiviousApplication
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.PlaybackRules
import net.wingress.mobivious.player.PlaybackService
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class BrowseState(val title: String = "For you", val videos: List<Video> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val page: Int = 1, val continuation: String = "", val end: Boolean = false)
data class PlaybackState(val details: VideoDetails? = null, val loading: Boolean = false, val error: String? = null,
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0, val buffering: Boolean = false)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MobiviousApplication
    val store = app.store
    val api = app.api
    val account = store.account
    val offline = app.offline
    val browse = MutableStateFlow(BrowseState())
    val playback = MutableStateFlow(PlaybackState())
    val controller = MutableStateFlow<MediaController?>(null)
    val channel = MutableStateFlow<Channel?>(null)
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlist = MutableStateFlow<Playlist?>(null)
    val subscriptions = MutableStateFlow<List<Channel>>(emptyList())
    val comments = MutableStateFlow(Page<Comment>(emptyList()))
    val commentError = MutableStateFlow<String?>(null)
    val preferences = MutableStateFlow(AccountPreferences())
    val message = MutableStateFlow<String?>(null)
    var tab = "Home"
    var route = ""
    var query = ""
    var discovery = "popular"
    var region = store.region
    var sort = "relevance"
    var date = ""
    var durationFilter = ""
    private var browseJob: Job? = null
    private var videoJob: Job? = null
    private var commentJob: Job? = null
    private var browseGeneration = 0
    private var requestedVideo: String? = null
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    init {
        future.addListener({
            runCatching {
                controller.value = future.get().apply {
                    addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) { updatePlayback() }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { playback.value = playback.value.copy(error = "Playback failed. Retry to refresh the stream.", buffering = false) }
                    })
                    setPlaybackSpeed(store.defaultSpeed)
                    trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, store.maxHeight).build()
                    currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }?.let { id ->
                        requestedVideo = id
                        viewModelScope.launch { runCatching { api.video(id) }.onSuccess { playback.value = playback.value.copy(details = it) } }
                    }
                }
            }.onFailure { message.value = "Unable to connect to the player." }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch { while (isActive) { delay(500); updatePlayback() } }
        viewModelScope.launch { account.collect { if (it != null) refreshAccount() else { subscriptions.value = emptyList(); playlists.value = emptyList(); preferences.value = AccountPreferences() } } }
        refresh()
    }
    private fun updatePlayback() {
        val p = controller.value ?: return
        playback.value = playback.value.copy(playing = p.isPlaying, position = p.currentPosition.coerceAtLeast(0), duration = p.duration.coerceAtLeast(0), buffering = p.playbackState == Player.STATE_BUFFERING)
    }
    fun refreshAccount() { action { preferences.value = api.preferences(); subscriptions.value = api.subscriptions(); playlists.value = api.playlists() } }
    fun navigate(tab: String, route: String = "") { this.tab = tab; this.route = route; channel.value = null; playlist.value = null; refresh() }
    fun refresh() = load(false)
    fun more() = load(true)
    private fun load(more: Boolean) {
        if (more && (browse.value.loading || browse.value.end)) return
        browseJob?.cancel()
        val generation = ++browseGeneration
        val old = if (more) browse.value else BrowseState()
        val page = if (more) old.page + 1 else 1
        val selectedTab = tab; val selectedRoute = route; val selectedQuery = query
        browse.value = old.copy(loading = true, error = null, title = if (selectedRoute == "history") "History" else selectedTab)
        browseJob = viewModelScope.launch {
            try {
                var continuation = ""
                val videos = when {
                    selectedRoute.startsWith("channel:") -> {
                        val id = selectedRoute.substringAfter(':')
                        if (!more) channel.value = api.channel(id)
                        val response = api.channelVideos(id, if (more) old.continuation else "")
                        continuation = response.continuation; response.items
                    }
                    selectedRoute.startsWith("playlist:") -> api.playlist(selectedRoute.substringAfter(':'), page).let { playlist.value = it.first; it.second }
                    selectedRoute == "history" -> api.history(page)
                    selectedTab == "Search" -> if (selectedQuery.isBlank()) emptyList() else api.search(selectedQuery, page, sort, date, durationFilter)
                    selectedTab == "Subscriptions" -> if (account.value == null) emptyList() else api.feed(page)
                    selectedTab == "Library" -> { if (account.value != null) playlists.value = api.playlists(); emptyList() }
                    else -> api.discovery(discovery, region)
                }
                if (generation == browseGeneration) browse.value = browse.value.copy(videos = (old.videos + videos).distinctBy { it.id + it.indexId }, loading = false, page = page,
                    continuation = continuation, end = videos.isEmpty() || selectedTab == "Home" && selectedRoute.isEmpty() || selectedRoute.startsWith("channel:") && continuation.isEmpty())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (generation == browseGeneration) browse.value = browse.value.copy(loading = false, error = friendly(e))
            }
        }
    }
    fun play(id: String, explicit: Long? = null) {
        if (id == playback.value.details?.video?.id && explicit == null) { controller.value?.play(); return }
        requestedVideo = id
        videoJob?.cancel(); commentJob?.cancel(); comments.value = Page(emptyList()); commentError.value = null
        playback.value = playback.value.copy(loading = true, error = null)
        videoJob = viewModelScope.launch {
            try {
                val details = api.video(id)
                val prefs = if (account.value != null) runCatching { api.preferences() }.getOrDefault(preferences.value) else AccountPreferences(false, false)
                preferences.value = prefs
                val saved = if (prefs.savePosition && account.value != null) runCatching { api.position(id) }.getOrElse { store.position(id) } else 0
                val start = PlaybackRules.resume(saved, details.video.duration, explicit)
                val base = store.server.toHttpUrlOrNull()!!
                fun resolve(url: String) = base.resolve(url)?.takeIf { it.isHttps || net.wingress.mobivious.BuildConfig.DEBUG && it.host in listOf("10.0.2.2", "127.0.0.1", "localhost") }?.toString().orEmpty()
                val raw = details.hls.ifBlank { details.dash }.ifBlank { details.fallback }
                val stream = resolve(raw).ifBlank { if (details.dash.isNotBlank() || !details.video.live) api.url("api/manifest/dash/id/$id", mapOf("local" to "true")).toString() else "" }
                require(stream.isNotBlank()) { "No playable stream is available." }
                val uri = stream.toHttpUrlOrNull()!!.newBuilder().setQueryParameter("local", "true").build().toString()
                val metadata = MediaMetadata.Builder().setTitle(details.video.title).setArtist(details.video.author)
                    .setArtworkUri(Uri.parse(resolve(details.video.thumbnail))).setExtras(Bundle().apply { putBoolean("history", prefs.watchHistory); putBoolean("savePosition", prefs.savePosition) }).build()
                val item = MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata)
                    .setMimeType(if (details.hls.isNotBlank()) MimeTypes.APPLICATION_M3U8 else if (details.dash.isNotBlank() || details.fallback.isBlank()) MimeTypes.APPLICATION_MPD else MimeTypes.VIDEO_MP4)
                    .setSubtitleConfigurations(details.captions.map { caption -> MediaItem.SubtitleConfiguration.Builder(Uri.parse(resolve(caption.url))).setMimeType(MimeTypes.TEXT_VTT).setLanguage(caption.language).setLabel(caption.label).build() }).build()
                val p = controller.value ?: future.awaitController()
                p.pause()
                p.setMediaItem(item, start * 1000); p.prepare(); p.play()
                playback.value = PlaybackState(details = details)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { playback.value = playback.value.copy(loading = false, error = friendly(e)) }
        }
    }
    fun retryPlayback() { val id = requestedVideo ?: return; val at = if (playback.value.details?.video?.id == id) playback.value.position / 1000 else null; playback.value = playback.value.copy(details = null); play(id, at) }
    fun closePlayer() { videoJob?.cancel(); controller.value?.stop(); controller.value?.clearMediaItems(); playback.value = PlaybackState() }
    fun togglePlay() { controller.value?.let { if (it.playWhenReady) it.pause() else it.play() } }
    fun audioOnly(value: Boolean) { controller.value?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, value).build() } }
    fun quality(height: Int) { store.maxHeight = height; controller.value?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setMaxVideoSize(Int.MAX_VALUE, height).build() } }
    fun speed(value: Float) { store.defaultSpeed = value; controller.value?.setPlaybackSpeed(value) }
    fun captions(language: String?) { controller.value?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, language == null).setPreferredTextLanguage(language).build() } }
    fun loadComments(more: Boolean = false) {
        val id = playback.value.details?.video?.id ?: return
        commentJob?.cancel()
        commentJob = viewModelScope.launch { try { val next = api.comments(id, if (more) comments.value.continuation else ""); comments.value = next.copy(items = if (more) comments.value.items + next.items else next.items); commentError.value = null } catch (e: CancellationException) { throw e } catch (e: Exception) { commentError.value = friendly(e) } }
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message.value = friendly(e) } } }
    suspend fun login(username: String, password: String) { store.save(api.login(username, password)); refresh() }
    fun logout() = action { try { api.logout() } finally { closePlayer(); store.save(null); store.clearPositions(); app.cache.clear(); navigate("Home") } }
    fun switchServer(value: String) { val address = InvidiousApi.normalizeServer(value, net.wingress.mobivious.BuildConfig.DEBUG); closePlayer(); store.save(null); store.clearPositions(); app.cache.clear(); store.server = address; navigate("Home") }
    fun savePreferences(value: AccountPreferences) = action { api.preferences(value); preferences.value = value; if (!value.savePosition) store.clearPositions(); message.value = "Account settings saved" }
    fun toggleSubscribe(id: String) = action { api.subscribe(id, subscriptions.value.none { it.id == id }); subscriptions.value = api.subscriptions() }
    override fun onCleared() { MediaController.releaseFuture(future) }
    private fun friendly(e: Exception) = if (e is ApiException || e is IllegalArgumentException) e.message ?: "Request failed." else "Cannot reach this instance. Check the address and connection, then retry."
}
private suspend fun com.google.common.util.concurrent.ListenableFuture<MediaController>.awaitController(): MediaController = withContext(Dispatchers.IO) { get() }
