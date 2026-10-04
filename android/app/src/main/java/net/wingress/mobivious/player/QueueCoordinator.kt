package net.wingress.mobivious.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.wingress.mobivious.MobiviousApplication
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** All player loading and queue work belongs to the service, on its main scope. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class QueueCoordinator(private val app: MobiviousApplication, private val player: ExoPlayer,
    private val scope: CoroutineScope, private val beforeChange: () -> Unit) {
    private val state get() = app.playbackQueue.value
    private var job: Job? = null
    private var prefs = app.store.guestDeArrow()
    private var prefsContext: ApiContext? = null
    private var settingsVersion = 0L
    private var selection: Selection? = null
    private data class Selection(val video: StreamKey?, val audio: StreamKey?, val text: StreamKey?,
        val parameters: TrackSelectionParameters, val speed: Float)
    private var pendingSelection: Selection? = null
    private var selectionPending = false
    private var decoderError = false
    private val playlistWrites = Mutex()
    private val mixContinuations = mutableSetOf<String>()
    var changed: () -> Unit = {}
    fun update(value: PlaybackQueueSnapshot) { app.playbackQueue.value = value; changed() }
    fun settings(value: AccountPreferences) {
        prefs = value; prefsContext = app.api.context(); settingsVersion++
        if (value.dearrowEnabled) state.current?.video?.id?.let { app.dearrowTitles.ensure(it) }
        syncDisplayTitle()
    }
    fun syncDisplayTitle() {
        val item = player.currentMediaItem ?: return
        val details = state.details ?: return
        if (state.context != app.api.context() || item.mediaMetadata.extras?.getString("occurrence") != state.currentKey) return
        val title = if (prefs.dearrowEnabled) app.dearrowTitles.titles.value[item.mediaId] ?: details.video.title else details.video.title
        if (item.mediaMetadata.title?.toString() == title) return
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { putBoolean("dearrowMetadataOnly", true) }
        player.replaceMediaItem(0, item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(title).setExtras(extras).build()).build())
    }
    fun playerError(message: String?) {
        if (message != null) { decoderError = true; update(state.copy(loading = false, error = message)) }
        else if (decoderError) { decoderError = false; update(state.copy(error = null)) }
    }
    private fun valid(token: String, context: ApiContext) = state.token == token && state.context == context && app.api.context() == context
    private fun check(token: String, context: ApiContext) { if (!valid(token, context)) throw CancellationException("Playback context changed") }
    private fun launch(sourceOnly: Boolean = false, block: suspend (String, ApiContext) -> Unit) {
        job?.cancel()
        val token = state.token; val context = state.context ?: return
        job = scope.launch {
            try { check(token, context); block(token, context) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid(token, context)) {
                if (sourceOnly) update(state.copy(sourceLoading = false, sourceError = e.message ?: "Queue could not load. Retry."))
                else { player.pause(); update(state.copy(loading = false, sourceLoading = false, error = e.message ?: "Playback failed. Retry.")) }
            } }
        }
    }
    fun start(id: String, source: String? = null, index: Int? = null, seconds: Long? = null,
        audio: Boolean = false, paused: Boolean = false) {
        beforeChange(); job?.cancel(); selection = null; decoderError = false; mixContinuations.clear()
        player.pause()
        val context = app.api.context()
        if (prefsContext != context) { prefs = app.store.guestDeArrow(); prefsContext = context }
        val token = UUID.randomUUID().toString()
        val seed = id.takeIf { it.isNotEmpty() }?.let { QueueOccurrence.local(Video(it, it)).copy(sourceIndex = index) }
        update(PlaybackQueueSnapshot(token, context, source?.let { QueueSource(it) }, items = listOfNotNull(seed), currentKey = seed?.key, loading = true,
            sourceComplete = source == null, explicitQueue = source != null))
        launch { _, _ ->
            val version = settingsVersion
            val fetched = if (context.account == null) app.store.guestDeArrow() else try { app.api.preferences(context) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { prefs }
            check(token, context)
            if (version == settingsVersion) prefs = fetched
            update(state.copy(repeat = if (prefs.videoLoop) QueueRepeat.ONE else QueueRepeat.OFF))
            var entry: QueueOccurrence? = null
            if (source != null) {
                try {
                    readSource(token, context, index ?: 0, id.takeIf { it.isNotBlank() }, initial = true)
                    entry = if (id.isEmpty()) state.items.firstOrNull { QueueRules.eligible(it, prefs.showMemberVideos) }
                        else state.items.firstOrNull { (index == null || it.sourceIndex == index) && it.video.id == id }
                            ?: state.items.firstOrNull { it.video.id == id }
                    val sourceMatch = state.items.firstOrNull { it.key != seed?.key && (index == null || it.sourceIndex == index) && (id.isEmpty() || it.video.id == id) }
                        ?: state.items.firstOrNull { it.key != seed?.key && it.video.id == id }
                    if (id.isNotEmpty() && sourceMatch != null) { entry = sourceMatch; update(state.copy(items = QueueRules.resolveSeed(state.items, seed?.key, sourceMatch.key))) }
                    while (entry == null && id.isEmpty() && !state.sourceComplete) {
                        val before = state.items.size
                        readSource(token, context, (state.items.mapNotNull { it.sourceIndex }.maxOrNull() ?: -1) + 1,
                            if (state.source?.mix == true) state.items.lastOrNull()?.video?.id else null)
                        entry = state.items.firstOrNull { QueueRules.eligible(it, prefs.showMemberVideos) }
                        if (state.items.size == before) break
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    update(state.copy(sourceLoading = false, sourceError = e.message ?: "Queue could not load. Retry."))
                    if (id.isEmpty()) throw e
                }
            }
            if (entry == null && id.isNotEmpty()) {
                entry = seed
            }
            require(entry != null) { "No playable videos in this queue." }
            load(entry, token, context, seconds, playing = !paused && prefs.autoplay, audio = audio, fresh = true)
        }
    }
    private suspend fun readSource(token: String, context: ApiContext, index: Int = 0, continuation: String? = null, initial: Boolean = false) {
        val source = state.source ?: return
        if (source.mix && continuation != null && continuation in mixContinuations) throw IllegalStateException("The mix returned no new successor. Retry the queue.")
        update(state.copy(sourceLoading = true, sourceError = null))
        try {
            playlistWrites.withLock {
                val page = app.api.queuePage(source.id, index, continuation, context)
                check(token, context)
                if (source.mix && continuation != null) mixContinuations.add(continuation)
                val merged = QueueRules.mergePage(state, page, index, if (initial) null else continuation)
                val complete = !source.mix && (page.videos.isEmpty() || (merged.mapNotNull { it.sourceIndex }.maxOrNull() ?: -1) + 1 >= page.source.count || merged.size == state.items.size)
                update(state.copy(source = page.source, items = merged, sourceLoading = false, sourceComplete = complete))
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (valid(token, context)) update(state.copy(sourceLoading = false, sourceError = e.message ?: "Queue could not load. Retry."))
            throw e
        }
    }
    private fun capture(): Selection {
        fun selected(type: Int) = player.currentTracks.groups.filter { it.type == type }.firstNotNullOfOrNull { group ->
            player.trackSelectionParameters.overrides[group.mediaTrackGroup]?.trackIndices?.singleOrNull()?.let { StreamKey.of(group.getTrackFormat(it)) }
        }
        return Selection(selected(C.TRACK_TYPE_VIDEO), selected(C.TRACK_TYPE_AUDIO), selected(C.TRACK_TYPE_TEXT), player.trackSelectionParameters, player.playbackParameters.speed)
    }
    private suspend fun load(entry: QueueOccurrence, token: String, context: ApiContext, seconds: Long? = null,
        playing: Boolean = true, audio: Boolean = false, fresh: Boolean = false) {
        check(token, context)
        if (!fresh) selection = capture()
        beforeChange(); player.pause()
        update(state.copy(currentKey = entry.key, details = null, loading = true, error = null))
        val details = app.api.video(entry.video.id, prefs.local)
        check(token, context)
        val saved = if (!prefs.savePosition) 0 else if (context.account == null) app.store.position(entry.video.id, context)
            else try { app.api.position(entry.video.id, context) } catch (e: CancellationException) { throw e } catch (_: Exception) { app.store.position(entry.video.id, context) }
        check(token, context)
        val base = context.server.toHttpUrlOrNull()!!
        fun resolve(url: String) = base.resolve(url)?.takeIf { it.isHttps || net.wingress.mobivious.BuildConfig.DEBUG && it.host in listOf("10.0.2.2", "127.0.0.1", "localhost") }?.toString().orEmpty()
        val raw = details.hls.ifBlank { details.dash }.ifBlank { details.fallback }
        val stream = resolve(raw).ifBlank { if (details.dash.isNotBlank() || !details.video.live) app.api.url("api/manifest/dash/id/${entry.video.id}", mapOf("local" to prefs.local.toString())).toString() else "" }
        require(stream.isNotBlank()) { "No playable stream is available." }
        val uri = stream.toHttpUrlOrNull()!!.newBuilder().setQueryParameter("local", prefs.local.toString()).build().toString()
        val metadata = MediaMetadata.Builder().setTitle(details.video.title).setArtist(details.video.author)
            .setArtworkUri(Uri.parse(resolve(details.video.thumbnail))).setExtras(Bundle().apply {
                putString("occurrence", entry.key); putBoolean("history", context.account != null && prefs.watchHistory); putBoolean("savePosition", prefs.savePosition)
                putString("channelId", details.video.channelId); putBoolean("liveNow", details.video.live)
                putString("sponsorblock", prefs.sponsorBlock.effective(details.video.channelId, context.account != null).json().toString())
            }).build()
        val caption = PreferenceRules.caption(prefs.captions, details.captions)
        val item = MediaItem.Builder().setMediaId(entry.video.id).setUri(uri).setMediaMetadata(metadata)
            .setMimeType(if (details.hls.isNotBlank()) MimeTypes.APPLICATION_M3U8 else if (details.dash.isNotBlank() || details.fallback.isBlank()) MimeTypes.APPLICATION_MPD else MimeTypes.VIDEO_MP4)
            .setSubtitleConfigurations(details.captions.map { track -> MediaItem.SubtitleConfiguration.Builder(Uri.parse(resolve(track.url)))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(track.language).setLabel(track.label).setSelectionFlags(if (track == caption) C.SELECTION_FLAG_DEFAULT else 0).build() }).build()
        val snapshot = selection
        player.trackSelectionParameters = (snapshot?.parameters ?: player.trackSelectionParameters).buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT).clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, if (fresh) audio || prefs.listen else snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) ?: prefs.listen)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setPreferredAudioLanguage(snapshot?.parameters?.preferredAudioLanguages?.firstOrNull())
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: (caption == null))
            .setPreferredTextLanguage(snapshot?.parameters?.preferredTextLanguages?.firstOrNull() ?: caption?.language).build()
        player.setPlaybackSpeed(snapshot?.speed ?: prefs.speed)
        pendingSelection = snapshot; selectionPending = true
        player.setMediaItem(item, PlaybackRules.resume(saved, details.video.duration, seconds) * 1000)
        player.prepare(); player.playWhenReady = playing
        update(state.copy(items = state.items.map { if (it.key == entry.key) it.copy(video = details.video.copy(indexId = it.video.indexId, playlistIndex = it.sourceIndex)) else it }, details = details, loading = false, error = null))
        if (prefs.dearrowEnabled) app.dearrowTitles.ensure(entry.video.id)
        syncDisplayTitle()
    }
    fun applySelection() {
        if (!selectionPending || player.playbackState != Player.STATE_READY || player.currentTracks.groups.isEmpty()) return
        selectionPending = false
        val snapshot = pendingSelection; pendingSelection = null
        val builder = player.trackSelectionParameters.buildUpon()
        for (type in listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
            val choices = StreamCatalog.choices(player.currentTracks, type)
            val key = when (type) { C.TRACK_TYPE_VIDEO -> snapshot?.video; C.TRACK_TYPE_AUDIO -> snapshot?.audio; else -> snapshot?.text }
            val choice = if (snapshot != null) StreamCatalog.find(choices, key) else if (type == C.TRACK_TYPE_VIDEO) StreamCatalog.defaultVideo(choices, prefs.qualityDash) else null
            if (choice != null) builder.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
            else if (key != null && type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(null)
        }
        player.trackSelectionParameters = builder.build()
    }
    fun select(key: String, seconds: Long? = null) {
        val entry = state.items.firstOrNull { it.key == key && !it.removed && !it.video.unavailable } ?: return
        launch { token, context -> load(entry, token, context, seconds, playing = true) }
    }
    fun advance(direction: Int = 1, automatic: Boolean = false) {
        if (state.loading) return
        launch { token, context ->
            if (automatic && state.repeat == QueueRepeat.ONE && state.current?.removed != true) {
                val current = state.current ?: return@launch
                load(current, token, context, 0, QueueRules.automaticStart(prefs)); return@launch
            }
            fun successor() = QueueRules.successor(state, direction, prefs.showMemberVideos,
                if (state.explicitQueue) emptySet() else app.blocked.state.value.takeIf { it.context == context }?.ids.orEmpty())
            var next = successor()
            while (direction < 0 && QueueRules.missingPreviousIndex(state, next) != null) {
                val before = state.items.size
                readSource(token, context, (QueueRules.missingPreviousIndex(state, next)!! - 99).coerceAtLeast(0))
                next = successor()
                if (state.items.size == before) throw IllegalStateException("Playlist returned no items for a missing page. Retry the queue.")
            }
            while (direction > 0 && state.source != null && (QueueRules.missingIndex(state, next) != null || !state.sourceComplete && (next == null || next.tail && state.source?.mix == false))) {
                val index = QueueRules.missingIndex(state, next) ?: (state.items.mapNotNull { it.sourceIndex }.maxOrNull() ?: -1) + 1
                val before = state.items.size
                readSource(token, context, index, if (state.source!!.mix) state.items.lastOrNull { it.sourceIndex != null }?.video?.id else null)
                next = successor()
                if (state.items.size == before) {
                    if (QueueRules.missingIndex(state, next) != null) throw IllegalStateException("Playlist returned no items for a missing page. Retry the queue.")
                    break
                }
            }
            if (next == null && direction < 0 && state.repeat == QueueRepeat.ALL && state.source?.mix != true) {
                if (state.source != null && !state.sourceComplete) readSource(token, context, (state.source!!.count - 1).coerceAtLeast(0))
                next = state.items.lastOrNull { QueueRules.eligible(it, prefs.showMemberVideos) }
            }
            if (next == null && direction > 0 && state.repeat == QueueRepeat.ALL && state.source?.mix != true) {
                if ((state.items.mapNotNull { it.sourceIndex }.minOrNull() ?: 0) > 0) readSource(token, context, 0)
                next = state.items.firstOrNull { QueueRules.eligible(it, prefs.showMemberVideos) }
            }
            if (next == null && direction > 0 && !state.explicitQueue && prefs.continueNext && prefs.relatedVideos) {
                val recommendation = ContentVisibility.filter(state.details?.recommendations.orEmpty(), ContentSurface.RECOMMENDATIONS,
                    prefs.showMemberVideos, blocked = app.blocked.state.value.takeIf { it.context == context }?.ids.orEmpty())
                    .firstOrNull { !it.unavailable && it.id != state.current?.video?.id }
                if (recommendation != null) { next = QueueOccurrence.local(recommendation); update(state.copy(items = state.items + next)) }
            }
            if (next != null) load(next, token, context,
                seconds = if (automatic && player.playbackState == Player.STATE_ENDED && next.video.id == state.current?.video?.id) 0 else null,
                playing = !automatic || QueueRules.automaticStart(prefs))
        }
    }
    fun insert(video: Video, next: Boolean) {
        if (state.context != app.api.context() || state.token.isEmpty()) {
            start(video.id, paused = true)
            update(state.copy(explicitQueue = true)); return
        }
        update(QueueRules.insert(state, video, next))
    }
    fun remove(key: String) { update(QueueRules.remove(state, key)) }
    fun removeFromPlaylist(key: String) {
        val entry = state.items.firstOrNull { it.key == key } ?: return
        val source = state.source ?: return
        val token = state.token; val context = state.context ?: return
        scope.launch {
            playlistWrites.withLock {
                if (!valid(token, context) || state.items.firstOrNull { it.key == key }?.removed != false) return@withLock
                try {
                    app.api.removeFromPlaylist(source.id, entry.video.indexId, context); check(token, context)
                    update(state.copy(source = state.source?.copy(count = (state.source!!.count - 1).coerceAtLeast(0)), items = state.items.map {
                        when { it.key == key -> it.copy(removed = true); it.sourceIndex != null && it.sourceIndex > (entry.sourceIndex ?: Int.MAX_VALUE) -> it.copy(sourceIndex = it.sourceIndex - 1); else -> it }
                    }))
                } catch (e: CancellationException) { throw e } catch (e: Exception) { if (valid(token, context)) update(state.copy(sourceError = "Could not remove playlist item: ${e.message}")) }
            }
        }
    }
    fun repeat(value: QueueRepeat) { if (value != QueueRepeat.ALL || state.source?.mix != true) update(state.copy(repeat = value)) }
    fun retry() {
        val entry = state.current ?: return
        val seconds = if (player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") == entry.key) player.currentPosition / 1000 else null
        val playing = player.playWhenReady
        launch { token, context -> load(entry, token, context, seconds, playing) }
    }
    fun more() { launch(sourceOnly = true) { token, context ->
        mixContinuations.clear()
        val next = QueueRules.successor(state, 1, prefs.showMemberVideos)
        readSource(token, context, QueueRules.missingIndex(state, next) ?: (state.items.mapNotNull { it.sourceIndex }.maxOrNull() ?: -1) + 1,
            if (state.source?.mix == true) state.items.lastOrNull { it.sourceIndex != null }?.video?.id else null)
        if (player.playbackState == Player.STATE_ENDED && state.current != null) advance(automatic = true)
    } }
    fun close() { beforeChange(); job?.cancel(); selectionPending = false; update(PlaybackQueueSnapshot()); player.stop(); player.clearMediaItems() }
}
