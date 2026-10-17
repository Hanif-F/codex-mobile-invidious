package net.wingress.mobivious.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import net.wingress.mobivious.MobiviousApplication

/** Observes the real service player. Never prepares, seeks, selects tracks or fetches metadata. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackStatisticsCollector(private val app: MobiviousApplication, private val player: ExoPlayer, scope: CoroutineScope) : AnalyticsListener {
    private val statistics = app.playbackStatistics
    private var accumulator = StatisticsAccumulator(SystemClock::elapsedRealtime)
    private var resetAt = SystemClock.elapsedRealtime()
    private var decoder: DecoderCounters? = null
    private var rendered = 0L
    private var dropped = 0L
    private var seeking = false
    private val job: Job
    init {
        player.addAnalyticsListener(this)
        statistics.refresh = { capture(false) }
        job = scope.launch {
            statistics.ui.map { it.enabled && it.visible }.distinctUntilChanged().collectLatest { visible ->
                observe(visible)
                if (visible) while (isActive) { capture(true); delay(1000) }
            }
        }
    }
    fun reset() {
        accumulator = StatisticsAccumulator(SystemClock::elapsedRealtime)
        resetAt = SystemClock.elapsedRealtime(); decoder = null; rendered = 0; dropped = 0; seeking = false
        if (app.playbackQueue.value.token.isEmpty()) statistics.resetSession()
        else statistics.publish(StatisticsSnapshot(videoId = app.playbackQueue.value.current?.video?.id.orEmpty()))
    }
    fun release() { job.cancel(); player.removeAnalyticsListener(this); statistics.refresh = null; statistics.resetSession() }
    private fun matches() = app.playbackQueue.value.let { q ->
        !q.loading && q.context == app.api.context() && q.currentKey != null &&
            player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") == q.currentKey
    }
    private fun relevant(event: AnalyticsListener.EventTime): Boolean {
        if (event.realtimeMs < resetAt || !matches()) return false
        if (event.windowIndex !in 0 until event.timeline.windowCount) return false
        val item = event.timeline.getWindow(event.windowIndex, Timeline.Window()).mediaItem
        return item.mediaMetadata.extras?.getString("occurrence") == app.playbackQueue.value.currentKey &&
            item.localConfiguration?.uri == player.currentMediaItem?.localConfiguration?.uri
    }
    private fun media(event: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData): Boolean =
        relevant(event) && data.dataType in listOf(C.DATA_TYPE_MEDIA, C.DATA_TYPE_MEDIA_INITIALIZATION) &&
            data.trackType != C.TRACK_TYPE_TEXT && player.currentMediaItem?.localConfiguration?.subtitleConfigurations.orEmpty()
                .none { it.uri == info.dataSpec.uri || it.uri == info.uri } && app.playbackQueue.value.current?.downloadId == null

    override fun onLoadStarted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (media(eventTime, loadEventInfo, mediaLoadData)) accumulator.start(loadEventInfo.loadTaskId)
    }
    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (media(eventTime, loadEventInfo, mediaLoadData)) accumulator.load(loadEventInfo.loadTaskId,
            loadEventInfo.bytesLoaded, loadEventInfo.loadDurationMs, completed = true)
    }
    override fun onLoadCanceled(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (media(eventTime, loadEventInfo, mediaLoadData)) accumulator.load(loadEventInfo.loadTaskId,
            loadEventInfo.bytesLoaded, loadEventInfo.loadDurationMs, canceled = true)
    }
    override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData,
        error: java.io.IOException, wasCanceled: Boolean) {
        if (media(eventTime, loadEventInfo, mediaLoadData)) accumulator.load(loadEventInfo.loadTaskId,
            loadEventInfo.bytesLoaded, loadEventInfo.loadDurationMs, error = true,
            timeout = generateSequence(error as Throwable) { it.cause }.take(8).any { it is java.net.SocketTimeoutException }, terminal = wasCanceled)
    }
    override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        if (relevant(eventTime)) decoder = decoderCounters
    }
    override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        if (decoder === decoderCounters) {
            decoderCounters.ensureUpdated()
            rendered += decoderCounters.renderedOutputBufferCount; dropped += decoderCounters.droppedBufferCount
            decoder = null
        }
    }
    override fun onPositionDiscontinuity(eventTime: AnalyticsListener.EventTime, oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            seeking = true; observe(); seeking = false
        }
    }
    override fun onEvents(player: Player, events: AnalyticsListener.Events) { observe() }
    private fun observe(visible: Boolean = statistics.ui.value.let { it.enabled && it.visible }) {
        accumulator.observe(visible && matches(), player.isPlaying, player.playbackState == Player.STATE_BUFFERING,
            player.playWhenReady, player.playerError != null, seeking)
    }
    private fun codec(format: Format?): String? = (format?.codecs ?: format?.sampleMimeType)
        ?.takeIf { it.matches(Regex("[A-Za-z0-9.,_ /-]{1,120}")) }
    private fun bitrate(format: Format?): String? = format?.bitrate?.takeIf { it > 0 }?.toString()
    private fun capture(chart: Boolean) {
        observe()
        val q = app.playbackQueue.value
        if (q.currentKey == null || q.context != app.api.context()) return
        val bound = matches()
        val offline = q.current?.downloadId != null
        val audioOnly = C.TRACK_TYPE_VIDEO in player.trackSelectionParameters.disabledTrackTypes
        val mode = if (offline) "offline" else when (player.currentMediaItem?.localConfiguration?.mimeType) {
            MimeTypes.APPLICATION_MPD -> "DASH"
            MimeTypes.APPLICATION_M3U8 -> "HLS"
            else -> "progressive"
        }
        val video = player.videoFormat.takeIf { bound && !audioOnly }
        val audio = player.audioFormat.takeIf { bound }
        val ready = bound && player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING, Player.STATE_ENDED)
        val size = player.videoSize
        val buffer = if (ready) StatisticsAccumulator.buffer(player.currentPosition, player.bufferedPosition) else null
        val metrics = linkedMapOf<StatisticKey, PlaybackStatistic>()
        fun put(key: StatisticKey, value: String?, waiting: Boolean = false) {
            metrics[key] = PlaybackStatistic(value, if (waiting) "Waiting for data" else "Unavailable")
        }
        put(StatisticKey.identity, "${q.current?.video?.id.orEmpty()} / ${if (audioOnly) "audio-only / " else ""}$mode")
        put(StatisticKey.resolution, if (video != null && size.width > 0 && size.height > 0) "${size.width} × ${size.height}" else null, !audioOnly && !ready)
        put(StatisticKey.quality, video?.let { "${it.width} × ${it.height}" }?.takeIf { video.width > 0 && video.height > 0 }, !audioOnly && !ready)
        put(StatisticKey.codecs, if (video != null || audio != null) "${codec(video) ?: "Unavailable"} / ${codec(audio) ?: "Unavailable"}" else null, !ready)
        put(StatisticKey.fps, video?.frameRate?.takeIf { it > 0 && it.isFinite() }?.let { statNumber(it.toDouble()) }, !audioOnly && !ready)
        put(StatisticKey.bitrate, if (video != null || audio != null) "${bitrate(video) ?: "Unavailable"} / ${bitrate(audio) ?: "Unavailable"}" else null, !ready)
        if (decoder == null && video != null && rendered + dropped == 0L) decoder = player.videoDecoderCounters
        decoder?.ensureUpdated()
        put(StatisticKey.frames, if (!audioOnly && (decoder != null || rendered + dropped > 0))
            StatisticsAccumulator.frames(rendered + (decoder?.renderedOutputBufferCount ?: 0), dropped + (decoder?.droppedBufferCount ?: 0)) else null, !audioOnly && !ready)
        put(StatisticKey.buffer, buffer?.let(::statNumber), !ready)
        put(StatisticKey.bandwidth, accumulator.bandwidth?.takeUnless { offline }?.let(::statNumber), !offline && accumulator.bandwidth == null)
        put(StatisticKey.bytes, accumulator.bytes.toString().takeUnless { offline })
        put(StatisticKey.requests, "${accumulator.requests} / ${accumulator.errors} / ${accumulator.timeouts} / ${accumulator.cancellations}".takeUnless { offline })
        put(StatisticKey.state, if (bound) "${statNumber(player.currentPosition / 1000.0)} / ${statNumber(player.playbackParameters.speed.toDouble())}× / ${statNumber(player.volume * 100.0)}% / ${player.volume == 0f} / ${player.playerError?.errorCode ?: 0}" else null, !bound)
        put(StatisticKey.stalls, "${accumulator.stalls} / ${statNumber(accumulator.stallSeconds)}")
        val window = if (bound && !player.currentTimeline.isEmpty) player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()) else null
        put(StatisticKey.live, window?.takeIf { it.isLive && it.durationMs >= 0 }?.let {
            statNumber((it.durationMs - player.currentPosition).coerceAtLeast(0) / 1000.0)
        })
        val samples = if (chart && statistics.ui.value.let { it.enabled && it.visible }) accumulator.sample(buffer, accumulator.bandwidth) else accumulator.samples()
        statistics.publish(StatisticsSnapshot(q.current?.video?.id.orEmpty(), if (audioOnly) "audio-only / $mode" else mode,
            accumulator.observationSeconds, metrics, samples))
    }
}
