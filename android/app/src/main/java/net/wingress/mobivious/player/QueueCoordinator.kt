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
    private val trackSelector: CodecAwareTrackSelector, private val scope: CoroutineScope, private val beforeChange: () -> Unit) {
    private val state get() = app.playbackQueue.value
    private var job: Job? = null
    private var prefs = app.store.initialPreferences()
    private var prefsContext: ApiContext? = null
    private var settingsVersion = 0L
    private var selection: Selection? = null
    private data class Selection(val audio: StreamKey?, val text: StreamKey?,
        val parameters: TrackSelectionParameters, val speed: Float)
    private var pendingSelection: Selection? = null
    private var selectionPending = false
    private var decoderError = false
    private var requestedPlayWhenReady = false
    private var baseRepeat = QueueRepeat.OFF
    private var enforcingBound = false
    private val playlistWrites = Mutex()
    private val mixContinuations = mutableSetOf<String>()
    var changed: () -> Unit = {}
    fun update(value: PlaybackQueueSnapshot) { app.playbackQueue.value = value; changed() }
    fun settings(value: AccountPreferences) {
        prefs = value; prefsContext = app.api.context(); settingsVersion++
        app.aiFilter.configure(value.aiFilter)
        if (state.current?.downloadId == null) {
            app.aiFilter.ensure(state.items.filter { it.downloadId == null }.map { it.video.channelId }, value.aiFilter.active(AiPageGroup.OTHER))
            app.aiFilter.ensure(state.details?.recommendations.orEmpty().map { it.channelId }, value.aiFilter.active(AiPageGroup.RECOMMENDATIONS))
        }
        if (state.current?.downloadId == null && value.dearrowEnabled) state.current?.video?.id?.let { app.dearrowTitles.ensure(it) }
        syncDisplayTitle()
    }
    fun syncDisplayTitle() {
        val item = player.currentMediaItem ?: return
        val details = state.details ?: return
        if (state.current?.downloadId != null) return
        if (state.context != app.api.context() || item.mediaMetadata.extras?.getString("occurrence") != state.currentKey) return
        val title = state.current?.clip?.title ?: if (prefs.dearrowEnabled) app.dearrowTitles.titles.value[item.mediaId] ?: details.video.title else details.video.title
        if (item.mediaMetadata.title?.toString() == title) return
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply { putBoolean("dearrowMetadataOnly", true) }
        player.replaceMediaItem(0, item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(title).setExtras(extras).build()).build())
    }
    fun playerError(message: String?) {
        if (message != null) { requestedPlayWhenReady = player.playWhenReady; decoderError = true; update(state.copy(loading = false, error = message)) }
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
    fun startDownload(id: String) {
        val record = app.downloads.find(id) ?: return
        beforeChange(); job?.cancel(); selection = null; decoderError = false
        player.pause()
        prefs = app.store.initialPreferences(); prefsContext = app.api.context()
        baseRepeat = QueueRepeat.OFF; mixContinuations.clear()
        val entry = QueueOccurrence.local(record.details.video).copy(downloadId = id)
        val context = app.api.context()
        update(PlaybackQueueSnapshot(token = UUID.randomUUID().toString(), context = context, items = listOf(entry), currentKey = entry.key, loading = true))
        launch { token, owner -> load(entry, token, owner, playing = true, fresh = true) }
    }
    private suspend fun loadDownload(entry: QueueOccurrence, playing: Boolean, positionMs: Long?, fresh: Boolean) {
        app.downloads.reconcile()
        val record = app.downloads.find(entry.downloadId!!) ?: error("This download was deleted.")
        val snapshot = if (fresh) null else capture()
        beforeChange(); player.pause()
        val details = app.downloads.localDetails(record)
        update(state.copy(currentKey = entry.key, details = details, loading = true, error = null,
            videoSelection = VideoSelection(occurrence = entry.key)))
        trackSelector.configure(state.videoSelection)
        player.trackSelectionParameters = (snapshot?.parameters ?: player.trackSelectionParameters).buildUpon()
            .clearOverrides().setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, record.assets.none { it.choice.kind == DownloadKind.VIDEO } || snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, record.assets.none { it.choice.kind == DownloadKind.AUDIO })
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: (PreferenceRules.caption(prefs.captions, details.captions) == null))
            .setPreferredTextLanguage(snapshot?.parameters?.preferredTextLanguages?.firstOrNull() ?: PreferenceRules.caption(prefs.captions, details.captions)?.language).build()
        player.setPlaybackSpeed(snapshot?.speed ?: prefs.speed)
        pendingSelection = snapshot; selectionPending = true
        player.setMediaSource(DownloadPlayback.source(app, app.downloads, record, entry.key), positionMs ?: record.positionMs)
        player.prepare(); player.playWhenReady = playing; requestedPlayWhenReady = playing
        update(state.copy(details = details, loading = false, error = null))
    }
    fun start(id: String, source: String? = null, index: Int? = null, seconds: Long? = null,
        audio: Boolean = false, paused: Boolean = false, sourceSeed: String? = null, linkPlayback: LinkPlayback? = null, clip: Clip? = null) {
        beforeChange(); job?.cancel(); selection = null; decoderError = false; mixContinuations.clear()
        player.pause()
        val context = app.api.context()
        if (prefsContext != context) { prefs = app.store.initialPreferences(); prefsContext = context }
        requestedPlayWhenReady = !paused && (clip != null || (linkPlayback?.options?.autoplay ?: prefs.autoplay))
        val token = UUID.randomUUID().toString()
        val seed = id.takeIf { it.isNotEmpty() }?.let { QueueOccurrence.local(clip?.video ?: Video(it, it)).copy(sourceIndex = index, linkPlayback = linkPlayback, clip = clip) }
        update(PlaybackQueueSnapshot(token, context, source?.let { QueueSource(it, seedVideoId = sourceSeed) }, items = listOfNotNull(seed), currentKey = seed?.key, loading = true,
            sourceComplete = source == null, explicitQueue = source != null, carriedOptions = (linkPlayback?.options?.carried() ?: PlaybackLinkOptions()).let { if (audio) it.copy(listen = true) else it }))
        launch { _, _ ->
            val version = settingsVersion
            val fetched = if (context.account == null) app.store.initialPreferences() else try { app.api.preferences(context) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { prefs }
            check(token, context)
            if (version == settingsVersion) prefs = fetched
            baseRepeat = if (clip != null || prefs.videoLoop) QueueRepeat.ONE else QueueRepeat.OFF
            update(state.copy(repeat = baseRepeat))
            var entry: QueueOccurrence? = null
            if (source != null) {
                try {
                    readSource(token, context, index ?: 0, id.takeIf { it.isNotBlank() } ?: sourceSeed, initial = true)
                    entry = if (id.isEmpty()) state.items.firstOrNull { (index == null || (it.sourceIndex ?: -1) >= index) && QueueRules.eligible(it, prefs.showMemberVideos) }
                        else state.items.firstOrNull { (index == null || it.sourceIndex == index) && it.video.id == id }
                            ?: state.items.firstOrNull { it.video.id == id }
                    val sourceMatch = state.items.firstOrNull { it.key != seed?.key && (index == null || it.sourceIndex == index) && (id.isEmpty() || it.video.id == id) }
                        ?: state.items.firstOrNull { it.key != seed?.key && it.video.id == id }
                    if (id.isNotEmpty() && sourceMatch != null) {
                        entry = sourceMatch.copy(linkPlayback = linkPlayback)
                        update(state.copy(items = QueueRules.resolveSeed(state.items, seed?.key, sourceMatch.key).map { if (it.key == sourceMatch.key) entry else it }))
                    }
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
            if (id.isEmpty() && linkPlayback != null) {
                entry = entry.copy(linkPlayback = linkPlayback)
                update(state.copy(items = state.items.map { if (it.key == entry.key) entry else it }))
            }
            load(entry, token, context, seconds, playing = !paused && (clip != null || (linkPlayback?.options?.autoplay ?: prefs.autoplay)), audio = audio, fresh = true)
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
                update(state.copy(source = page.source.copy(seedVideoId = source.seedVideoId ?: page.source.seedVideoId), items = merged, sourceLoading = false, sourceComplete = complete))
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
        return Selection(selected(C.TRACK_TYPE_AUDIO), selected(C.TRACK_TYPE_TEXT), player.trackSelectionParameters, player.playbackParameters.speed)
    }
    private suspend fun load(entry: QueueOccurrence, token: String, context: ApiContext, seconds: Long? = null,
        playing: Boolean = true, audio: Boolean = false, fresh: Boolean = false, positionMs: Long? = null) {
        check(token, context)
        if (entry.downloadId != null) { loadDownload(entry, playing, positionMs, fresh); check(token, context); return }
        val sameOccurrence = !fresh && state.videoSelection.occurrence == entry.key
        if (!fresh && player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") == state.currentKey) {
            val captured = capture()
            selection = captured.takeIf { sameOccurrence }
            if (!sameOccurrence) update(state.copy(carriedOptions = state.carriedOptions.copy(
                listen = (C.TRACK_TYPE_VIDEO in captured.parameters.disabledTrackTypes).takeIf { state.carriedOptions.listen != null || it != prefs.listen },
                speed = state.carriedOptions.speed?.let { captured.speed })))
        }
        else selection = null
        requestedPlayWhenReady = playing
        beforeChange(); player.pause()
        update(state.copy(currentKey = entry.key, details = null, loading = true, error = null))
        if (!fresh && !sameOccurrence) {
            val version = settingsVersion
            val fetched = if (context.account == null) app.store.initialPreferences() else try { app.api.preferences(context) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { prefs }
            check(token, context)
            if (version == settingsVersion) prefs = fetched
        }
        val prefs = state.effective(this.prefs)
        app.aiFilter.configure(prefs.aiFilter)
        val details = app.api.video(entry.video.id, prefs.local, entry.linkPlayback?.options?.region, context)
        app.aiFilter.ensure(details.recommendations.map { it.channelId }, prefs.aiFilter.active(AiPageGroup.RECOMMENDATIONS))
        entry.clip?.let { clip ->
            require(!details.video.live && details.upcoming != true && details.video.duration in 1..Long.MAX_VALUE / 1000 &&
                clip.endMs <= details.video.duration * 1000 && clip.startMs >= 0 && clip.endMs > clip.startMs) {
                "The source video no longer contains this clip. Try again later."
            }
        }
        val bounded = entry.linkPlayback?.bounded(details.video.duration.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000) ?: 0)
        val repeat = bounded?.options?.loop?.let { if (it) QueueRepeat.ONE else QueueRepeat.OFF } ?: baseRepeat
        update(state.copy(details = details, repeat = repeat, items = state.items.map { if (it.key == entry.key) it.copy(linkPlayback = bounded) else it }))
        check(token, context)
        val saved = if (entry.clip != null || !prefs.savePosition) 0 else if (context.account == null) app.store.position(entry.video.id, context)
            else try { app.api.position(entry.video.id, context) } catch (e: CancellationException) { throw e } catch (_: Exception) { app.store.position(entry.video.id, context) }
        check(token, context)
        val base = context.server.toHttpUrlOrNull()!!
        fun resolve(url: String) = base.resolve(url)?.takeIf { it.isHttps || net.wingress.mobivious.BuildConfig.DEBUG && it.host in listOf("10.0.2.2", "127.0.0.1", "localhost") }?.toString().orEmpty()
        val raw = details.hls.ifBlank { details.dash }.ifBlank { details.fallback }
        val stream = resolve(raw).ifBlank { if (details.dash.isNotBlank() || !details.video.live) app.api.url("api/manifest/dash/id/${entry.video.id}", mapOf("local" to prefs.local.toString())).toString() else "" }
        require(details.upcoming != true || details.video.live) { details.notice.ifBlank { "This video is upcoming. Retry when it starts." } }
        require(stream.isNotBlank()) { "No playable stream is available." }
        val uri = stream.toHttpUrlOrNull()!!.newBuilder().setQueryParameter("local", prefs.local.toString()).apply { entry.linkPlayback?.options?.region?.let { setQueryParameter("region", it) } }.build().toString()
        val metadata = MediaMetadata.Builder().setTitle(entry.clip?.title ?: details.video.title).setArtist(details.video.author)
            .setArtworkUri(Uri.parse(resolve(details.video.thumbnail))).setExtras(Bundle().apply {
                putString("occurrence", entry.key); putBoolean("clip", entry.clip != null); putBoolean("history", entry.clip == null && context.account != null && prefs.watchHistory); putBoolean("savePosition", entry.clip == null && prefs.savePosition)
                putString("channelId", details.video.channelId); putBoolean("liveNow", details.video.live)
                putString("sponsorblock", prefs.sponsorBlock.effective(details.video.channelId).json().toString())
            }).build()
        val caption = PreferenceRules.caption(prefs.captions, details.captions)
        val item = MediaItem.Builder().setMediaId(entry.video.id).setUri(uri).setMediaMetadata(metadata)
            .setMimeType(if (details.hls.isNotBlank()) MimeTypes.APPLICATION_M3U8 else if (details.dash.isNotBlank() || details.fallback.isBlank()) MimeTypes.APPLICATION_MPD else MimeTypes.VIDEO_MP4)
            .setSubtitleConfigurations(details.captions.map { track ->
                val captionUrl = resolve(track.url).toHttpUrlOrNull()?.newBuilder()?.apply { entry.linkPlayback?.options?.region?.let { setQueryParameter("region", it) } }?.build()?.toString().orEmpty()
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(captionUrl))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(track.language).setLabel(track.label).setSelectionFlags(if (track == caption) C.SELECTION_FLAG_DEFAULT else 0).build() }).build()
        val clippedItem = entry.clip?.let { clip -> item.buildUpon().setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build() } ?: item
        val snapshot = selection
        val videoPolicy = if (sameOccurrence) state.videoSelection else VideoSelection.open(entry.key, prefs,
            item.localConfiguration?.mimeType == MimeTypes.APPLICATION_MPD, state.videoSelection.revision + 1)
        configureVideo(videoPolicy)
        player.trackSelectionParameters = (snapshot?.parameters ?: player.trackSelectionParameters).buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT).clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, if (fresh) audio || prefs.listen else snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) ?: prefs.listen)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setPreferredAudioLanguage(snapshot?.parameters?.preferredAudioLanguages?.firstOrNull())
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, snapshot?.parameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: (caption == null))
            .setPreferredTextLanguage(snapshot?.parameters?.preferredTextLanguages?.firstOrNull() ?: caption?.language).build()
        player.setPlaybackSpeed(snapshot?.speed ?: prefs.speed)
        pendingSelection = snapshot; selectionPending = true
        val start = if (positionMs != null) positionMs else if (seconds != null) PlaybackRules.resume(saved, details.video.duration, seconds) * 1000
            else if (fresh || !sameOccurrence) bounded?.startMs ?: PlaybackRules.resume(saved, details.video.duration, null) * 1000
            else PlaybackRules.resume(saved, details.video.duration, null) * 1000
        player.setMediaItem(clippedItem, if (entry.clip != null) (positionMs ?: 0).coerceIn(0, entry.clip.durationMs)
            else LinkPlaybackRules.seek(start, details.video.duration.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000) ?: 0, bounded?.endMs))
        player.prepare(); player.playWhenReady = playing
        update(state.copy(items = state.items.map { if (it.key == entry.key) it.copy(video = details.video.copy(indexId = it.video.indexId, playlistIndex = it.sourceIndex)) else it }, details = details, loading = false, error = null))
        if (prefs.dearrowEnabled) app.dearrowTitles.ensure(entry.video.id)
        syncDisplayTitle()
    }
    fun applySelection() {
        applyVideoSelection()
        if (!selectionPending || state.loading || player.playbackState != Player.STATE_READY || player.currentTracks.groups.isEmpty() ||
            player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") != state.currentKey) return
        selectionPending = false
        val snapshot = pendingSelection; pendingSelection = null
        val builder = player.trackSelectionParameters.buildUpon()
        for (type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
            val choices = StreamCatalog.choices(player.currentTracks, type)
            val key = if (type == C.TRACK_TYPE_AUDIO) snapshot?.audio else snapshot?.text
            val choice = StreamCatalog.find(choices, key)
            if (choice != null) builder.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
            else if (key != null && type == C.TRACK_TYPE_AUDIO) builder.setPreferredAudioLanguage(null)
        }
        player.trackSelectionParameters = builder.build()
    }
    private fun configureVideo(value: VideoSelection) {
        trackSelector.configure(value)
        update(state.copy(videoSelection = value))
    }
    private fun videoChoices() = StreamCatalog.choices(player.currentTracks, C.TRACK_TYPE_VIDEO, state.details?.formats.orEmpty())
    private fun applyVideoSelection() {
        if (state.loading || player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") != state.videoSelection.occurrence) return
        val choices = videoChoices()
        if (choices.isEmpty()) return
        val policy = state.videoSelection.resolve(choices)
        if (policy != state.videoSelection) configureVideo(policy)
        val choice = policy.fixed(choices)
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (choice != null) builder.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
        val parameters = builder.build()
        if (parameters != player.trackSelectionParameters) player.trackSelectionParameters = parameters
    }
    fun autoVideo(occurrence: String): Boolean {
        if (state.loading || occurrence != state.currentKey || occurrence != state.videoSelection.occurrence) return false
        configureVideo(state.videoSelection.auto())
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE).build()
        applyVideoSelection()
        return true
    }
    fun manualVideo(occurrence: String, key: StreamKey): Boolean {
        if (state.loading || occurrence != state.currentKey || occurrence != state.videoSelection.occurrence) return false
        val choice = StreamCatalog.findVideo(videoChoices(), key) ?: return false
        configureVideo(state.videoSelection.select(choice.key))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(choice.group, choice.index)).build()
        return true
    }
    fun select(key: String, positionMs: Long? = null) {
        val entry = state.items.firstOrNull { it.key == key && !it.removed && !it.video.unavailable } ?: return
        launch { token, context -> load(entry, token, context, playing = true, positionMs = positionMs ?: entry.linkPlayback?.startMs) }
    }
    fun advance(direction: Int = 1, automatic: Boolean = false) {
        if (state.loading) return
        if (automatic && state.current?.clip != null) {
            if (state.repeat == QueueRepeat.ONE) { player.seekTo(0); player.playWhenReady = true }
            else player.pause()
            return
        }
        launch { token, context ->
            if (automatic && state.repeat == QueueRepeat.ONE && state.current?.removed != true) {
                val current = state.current ?: return@launch
                load(current, token, context, playing = QueueRules.automaticStart(state.effective(prefs)), positionMs = current.linkPlayback?.startMs ?: 0); return@launch
            }
            if (!state.explicitQueue && state.current?.downloadId == null) {
                app.aiFilter.prepare((state.items.map { it.video } + state.details?.recommendations.orEmpty()).map { it.channelId }, prefs.aiFilter, AiPageGroup.RECOMMENDATIONS)
                check(token, context)
            }
            fun aiHidden(video: Video) = AiFilter.decide(prefs.aiFilter, AiPageGroup.RECOMMENDATIONS,
                app.aiFilter.state.value.takeIf { it.context == context }?.matches?.get(video.channelId).orEmpty()).hidden
            fun successor() = QueueRules.successor(state, direction, prefs.showMemberVideos,
                if (state.explicitQueue) emptySet() else app.blocked.state.value.takeIf { it.context == context }?.ids.orEmpty() +
                    state.items.map { it.video }.filter(::aiHidden).map { it.channelId })
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
            if (next == null && direction > 0 && !state.explicitQueue && state.effective(prefs).continueNext && state.effective(prefs).relatedVideos) {
                val recommendation = ContentVisibility.filter(state.details?.recommendations.orEmpty(), ContentSurface.RECOMMENDATIONS,
                    prefs.showMemberVideos, blocked = app.blocked.state.value.takeIf { it.context == context }?.ids.orEmpty())
                    .firstOrNull { !it.unavailable && it.id != state.current?.video?.id && !aiHidden(it) }
                if (recommendation != null) { next = QueueOccurrence.local(recommendation); update(state.copy(items = state.items + next)) }
            }
            if (next != null) load(next, token, context,
                seconds = if (automatic && player.playbackState == Player.STATE_ENDED && next.video.id == state.current?.video?.id) 0 else null,
                playing = !automatic || QueueRules.automaticStart(state.effective(prefs)))
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
                    require(source.owned || app.api.playlists(context).any { it.id == source.id && it.owned }) { "Only My playlists can be edited." }
                    app.api.removeFromPlaylist(source.id, entry.video.indexId, context); check(token, context)
                    update(state.copy(source = state.source?.copy(count = (state.source!!.count - 1).coerceAtLeast(0)), items = state.items.map {
                        when { it.key == key -> it.copy(removed = true); it.sourceIndex != null && it.sourceIndex > (entry.sourceIndex ?: Int.MAX_VALUE) -> it.copy(sourceIndex = it.sourceIndex - 1); else -> it }
                    }))
                } catch (e: CancellationException) { throw e } catch (e: Exception) { if (valid(token, context)) update(state.copy(sourceError = "Could not remove playlist item: ${e.message}")) }
            }
        }
    }
    fun repeat(value: QueueRepeat) {
        if (value == QueueRepeat.ALL && state.source?.mix == true) return
        baseRepeat = value
        update(state.copy(repeat = value, items = state.items.map { item ->
            if (item.key == state.currentKey && item.linkPlayback != null) item.copy(linkPlayback = item.linkPlayback.copy(options = item.linkPlayback.options.copy(loop = null))) else item
        }))
    }
    fun syncModeOverrides() {
        if (state.loading || player.playbackState != Player.STATE_READY || player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") != state.currentKey) return
        val modes = state.carriedOptions.copy(
            listen = state.carriedOptions.listen?.let { C.TRACK_TYPE_VIDEO in player.trackSelectionParameters.disabledTrackTypes },
            speed = state.carriedOptions.speed?.let { player.playbackParameters.speed })
        if (modes != state.carriedOptions) update(state.copy(carriedOptions = modes))
    }
    fun fullVideo() {
        val entry = state.current ?: return
        val clip = entry.clip ?: return
        val position = clip.startMs + player.currentPosition.coerceIn(0, clip.durationMs)
        val playing = player.playWhenReady
        val full = entry.copy(clip = null)
        baseRepeat = if (prefs.videoLoop) QueueRepeat.ONE else QueueRepeat.OFF
        update(state.copy(items = state.items.map { if (it.key == entry.key) full else it }, repeat = baseRepeat))
        launch { token, context -> load(full, token, context, playing = playing, positionMs = position) }
    }
    fun clampSeek(position: Long): Long = state.current?.clip?.let { position.coerceIn(0, it.durationMs) }
        ?: LinkPlaybackRules.seek(position, player.duration, state.current?.linkPlayback?.endMs)
    fun enforceEnd(): Boolean {
        if (state.loading || player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") != state.currentKey) return false
        val original = state.current?.linkPlayback ?: return false
        val bounded = original.bounded(player.duration)
        if (bounded != original) update(state.copy(items = state.items.map { if (it.key == state.currentKey) it.copy(linkPlayback = bounded) else it }))
        val end = bounded.endMs ?: return false
        if (player.currentPosition < end) return false
        if (enforcingBound) return true
        enforcingBound = true
        try {
            val playing = player.playWhenReady
            if (state.repeat == QueueRepeat.ONE && playing) {
                player.seekTo((bounded.startMs ?: 0).coerceAtMost(end - 1)); player.playWhenReady = true
            } else {
                player.pause()
                if (player.currentPosition != end) player.seekTo(end)
            }
        } finally { enforcingBound = false }
        return true
    }
    fun retry() {
        val entry = state.current ?: return
        val position = if (player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") == entry.key) player.currentPosition else null
        val playing = if (state.error != null) requestedPlayWhenReady else player.playWhenReady
        launch { token, context -> load(entry, token, context, playing = playing, positionMs = position) }
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
