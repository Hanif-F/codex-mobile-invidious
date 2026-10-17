package net.wingress.mobivious.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Locale

internal enum class StatisticKey(val label: String) {
    resolution("Decoded resolution"), frames("Dropped / total frames"), buffer("Buffer ahead (s)"),
    bandwidth("Last media transfer (Mbps)"), identity("Video ID / playback mode"), viewport("Viewport / density"),
    quality("Active quality"), codecs("Video / audio codecs"), fps("Nominal FPS"), bitrate("Video / audio bitrate (bps)"),
    bytes("Loaded media (bytes)"), requests("Media loads / errors / timeouts / cancellations"),
    state("Position (s) / speed / volume / muted / error code"), stalls("Buffering count / seconds observed"),
    live("Behind available live edge (s)")
}

internal data class PlaybackStatistic(val value: String? = null, val reason: String = "Unavailable")
internal data class StatisticsSample(val at: Long, val buffer: Double?, val bandwidth: Double?)
internal data class StatisticsViewport(val width: Int = 0, val height: Int = 0, val density: Float = 1f) {
    val text get() = if (width > 0 && height > 0) "$width × $height / ${statNumber(density.toDouble())}" else null
}
internal data class StatisticsUi(val enabled: Boolean = false, val expanded: Boolean = false,
    val visible: Boolean = false, val viewport: StatisticsViewport = StatisticsViewport())

internal data class StatisticsSnapshot(val videoId: String = "", val mode: String = "", val observationSeconds: Double = 0.0,
    val metrics: Map<StatisticKey, PlaybackStatistic> = emptyMap(), val samples: List<StatisticsSample> = emptyList()) {
    fun metric(key: StatisticKey, viewport: StatisticsViewport = StatisticsViewport()): PlaybackStatistic =
        if (key == StatisticKey.viewport) PlaybackStatistic(viewport.text) else metrics[key] ?: PlaybackStatistic(reason = "Waiting for data")

    // A deliberate allowlist: no Format, MediaItem, URI or Throwable is serializable here.
    fun json(appVersion: String, playerVersion: String, viewport: StatisticsViewport): String {
        val values = JSONObject(); val reasons = JSONObject()
        StatisticKey.entries.forEach { key ->
            val metric = metric(key, viewport)
            values.put(key.name, metric.value ?: JSONObject.NULL)
            if (metric.value == null) reasons.put(key.name, metric.reason)
        }
        return JSONObject().put("version", 1).put("platform", "Android").put("videoId", videoId)
            .put("appVersion", appVersion).put("playerVersion", playerVersion).put("mode", mode)
            .put("observationSeconds", observationSeconds).put("metrics", values).put("availabilityReasons", reasons).toString(2)
    }
}

/** Main-thread bridge shared by the service and activity; no controller owns the decoder. */
internal class PlaybackStatistics {
    private val mutableUi = MutableStateFlow(StatisticsUi())
    val ui = mutableUi.asStateFlow()
    private val mutableSnapshot = MutableStateFlow(StatisticsSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()
    var refresh: (() -> Unit)? = null
    fun publish(value: StatisticsSnapshot) { mutableSnapshot.value = value }
    fun toggle() { mutableUi.value = mutableUi.value.copy(enabled = !ui.value.enabled, expanded = false) }
    fun expand(value: Boolean) { mutableUi.value = mutableUi.value.copy(expanded = value) }
    fun close() { mutableUi.value = mutableUi.value.copy(enabled = false, expanded = false) }
    fun visible(value: Boolean) { mutableUi.value = mutableUi.value.copy(visible = value) }
    fun viewport(width: Int, height: Int, density: Float) {
        mutableUi.value = mutableUi.value.copy(viewport = StatisticsViewport(width, height, density))
    }
    fun resetSession() { close(); publish(StatisticsSnapshot()) }
}

internal fun statNumber(value: Double): String = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

/** Pure bookkeeping. Load bytes are cumulative per task, including retry callbacks. */
internal class StatisticsAccumulator(private val now: () -> Long) {
    private val loads = mutableMapOf<Long, Long>()
    var bytes = 0L; private set
    var requests = 0; private set
    var errors = 0; private set
    var timeouts = 0; private set
    var cancellations = 0; private set
    var bandwidth: Double? = null; private set
    private var observedMs = 0L
    private var tick: Long? = null
    private var hasPlayed = false
    private var stalledAt: Long? = null
    private var stallMs = 0L
    var stalls = 0; private set
    private val history = ArrayDeque<StatisticsSample>()
    val observationSeconds get() = (observedMs + (tick?.let { now() - it } ?: 0)) / 1000.0
    val stallSeconds get() = (stallMs + (stalledAt?.let { now() - it } ?: 0)) / 1000.0

    fun start(id: Long) { if (id !in loads) { loads[id] = 0; requests++ } }
    fun load(id: Long, cumulativeBytes: Long, durationMs: Long, completed: Boolean = false,
        error: Boolean = false, timeout: Boolean = false, canceled: Boolean = false, terminal: Boolean = false) {
        val previous = loads[id] ?: return // Includes stale completions after reset.
        val total = cumulativeBytes.coerceAtLeast(previous)
        bytes += total - previous; loads[id] = total
        if (error) errors++
        if (timeout) timeouts++
        if (canceled) cancellations++
        if (completed && total > 0 && durationMs > 0) bandwidth = total * .008 / durationMs
        if (completed || canceled || terminal) loads.remove(id)
    }
    fun observe(visible: Boolean, playing: Boolean, buffering: Boolean, intent: Boolean, failed: Boolean = false, seeking: Boolean = false) {
        val time = now()
        tick?.let { observedMs += time - it }
        val wasVisible = tick != null
        tick = if (visible) time else null
        if (wasVisible && !visible && history.isNotEmpty()) history.addLast(StatisticsSample(time, null, null))
        val stalled = visible && !seeking && !failed && intent && buffering && hasPlayed
        if (!stalled) endStall(time)
        if (!visible || !intent || seeking || failed) hasPlayed = false
        if (visible && playing) hasPlayed = true
        if (stalled && stalledAt == null) { stalls++; stalledAt = time }
        trim(time)
    }
    private fun endStall(time: Long) { stalledAt?.let { stallMs += time - it }; stalledAt = null }
    fun sample(buffer: Double?, speed: Double?): List<StatisticsSample> {
        val time = now()
        history.addLast(StatisticsSample(time, buffer, speed)); trim(time)
        return history.toList()
    }
    fun samples(): List<StatisticsSample> { trim(now()); return history.toList() }
    private fun trim(time: Long) {
        while (history.isNotEmpty() && (history.size > 60 || time - history.first().at >= 60_000)) history.removeFirst()
    }
    companion object {
        fun buffer(position: Long, bufferedPosition: Long): Double? =
            if (position < 0 || bufferedPosition < 0) null else (bufferedPosition - position).coerceAtLeast(0) / 1000.0
        fun frames(rendered: Long, dropped: Long): String =
            "$dropped / ${rendered + dropped} (${statNumber(if (rendered + dropped == 0L) 0.0 else 100.0 * dropped / (rendered + dropped))}%)"
    }
}
