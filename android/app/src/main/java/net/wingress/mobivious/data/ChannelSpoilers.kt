package net.wingress.mobivious.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class ChannelSpoilerRule(val name: String, val hideTitle: Boolean = false,
    val hideThumbnail: Boolean = false, val revealAfterDays: Int? = null) {
    val enabled get() = hideTitle || hideThumbnail
    fun active(published: Long?, now: Long) = enabled && (revealAfterDays == null || published == null ||
        now < published + revealAfterDays.toLong() * 86_400)
    fun json(includeName: Boolean = true) = JSONObject().apply {
        if (includeName) put("name", name)
        put("hide_title", hideTitle); put("hide_thumbnail", hideThumbnail)
        put("reveal_after_days", revealAfterDays ?: JSONObject.NULL)
    }
}

object ChannelSpoilerRules {
    fun parse(raw: JSONObject?): Map<String, ChannelSpoilerRule> = buildMap {
        raw?.keys()?.asSequence()?.take(1000)?.forEach { id ->
            if (!ContentVisibility.validChannel(id)) return@forEach
            val fields = raw.optJSONObject(id) ?: return@forEach
            val title = fields.opt("hide_title") == true; val thumbnail = fields.opt("hide_thumbnail") == true
            if (!title && !thumbnail) return@forEach
            val days = if (fields.isNull("reveal_after_days")) null else {
                val value = fields.opt("reveal_after_days")
                if (value !is Int && value !is Long) return@forEach
                (value as Number).toLong().takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: return@forEach
            }
            val name = (fields.opt("name") as? String) ?: id
            put(id, ChannelSpoilerRule(name.takeCodePoints(200), title, thumbnail, days))
        }
    }
    fun json(rules: Map<String, ChannelSpoilerRule>) = JSONObject().apply { rules.forEach { (id, rule) -> put(id, rule.json()) } }
    fun patch(before: Map<String, ChannelSpoilerRule>, next: Map<String, ChannelSpoilerRule>, includeNames: Boolean) = JSONObject().apply {
        (before.keys + next.keys).forEach { id ->
            if (before[id] != next[id]) put(id, next[id]?.takeIf { it.enabled }?.json(includeNames) ?: JSONObject.NULL)
        }
    }
    fun days(text: String): Int? = text.trim().takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()?.takeIf { it > 0 }
    fun publication(value: Any?): Long? = runCatching {
        val seconds = when (value) {
            is Int, is Long -> (value as Number).toLong()
            is String -> if (value.contains('T')) Instant.parse(value).epochSecond
                else LocalDate.parse(value).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            else -> return null
        }
        seconds.takeIf { it in 1_104_537_600..253_402_300_799L }
    }.getOrNull()
    fun publication(json: JSONObject): Long? = if (json.opt("publishedIsKnown") == false) null
        else publication(json.opt("sourcePublished").takeUnless { it == JSONObject.NULL } ?: json.opt("published"))
    fun publication(video: Video): Long? = publication(video.publicationTime) ?:
        publication(video.history?.released?.atStartOfDay()?.toEpochSecond(ZoneOffset.UTC))
    private fun String.takeCodePoints(count: Int) = substring(0, offsetByCodePoints(0, minOf(count, codePointCount(0, length))))
}

enum class SpoilerTitleMode { HIDDEN, DEARROW, ORIGINAL;
    fun next(hasReplacement: Boolean) = when (this) {
        HIDDEN -> if (hasReplacement) DEARROW else ORIGINAL
        DEARROW -> ORIGINAL
        ORIGINAL -> HIDDEN
    }
}
data class SpoilerDecision(val title: Boolean = false, val thumbnail: Boolean = false, val unknown: Boolean = false,
    val pending: Boolean = false) {
    val titleLabel get() = if (pending) "Loading channel settings…" else "Title hidden" + if (unknown) " · upload date unknown" else ""
    val thumbnailLabel get() = if (pending) "Loading channel settings…" else "Thumbnail hidden" + if (unknown) " · upload date unknown" else ""
}
data class PlaybackSpoilerReveal(val occurrence: String = "", val title: SpoilerTitleMode = SpoilerTitleMode.HIDDEN,
    val thumbnail: Boolean = false)
data class ChannelSpoilerState(val context: ApiContext? = null, val ready: Boolean = false,
    val rules: Map<String, ChannelSpoilerRule> = emptyMap(), val dates: Map<String, Long?> = emptyMap(),
    val now: Long = System.currentTimeMillis() / 1000, val revision: Long = 0,
    val playback: PlaybackSpoilerReveal = PlaybackSpoilerReveal(), val dearrowEnabled: Boolean = false) {
    fun decision(video: Video, cached: Boolean = true): SpoilerDecision {
        if (!ready) return SpoilerDecision(true, true, pending = true)
        val rule = rules[video.channelId] ?: return SpoilerDecision()
        val published = ChannelSpoilerRules.publication(video) ?: if (cached) ChannelSpoilerRules.publication(dates[video.id]) else null
        if (!rule.active(published, now)) return SpoilerDecision()
        return SpoilerDecision(rule.hideTitle, rule.hideThumbnail, published == null)
    }
    fun title(video: Video, replacement: String? = null, reveal: SpoilerTitleMode = SpoilerTitleMode.HIDDEN,
        original: String = video.title, cached: Boolean = true): String {
        val decision = decision(video, cached)
        return if (decision.title) when (if (decision.pending) SpoilerTitleMode.HIDDEN else reveal) {
            SpoilerTitleMode.HIDDEN -> decision.titleLabel
            SpoilerTitleMode.DEARROW -> replacement ?: decision.titleLabel
            SpoilerTitleMode.ORIGINAL -> original
        } else replacement ?: original
    }
}

/** Main-dispatcher owner shared by Compose and the playback service. Lookup failures are misses. */
class ChannelSpoilerRepository(private val scope: CoroutineScope, private val context: () -> ApiContext,
    private val fetch: suspend (List<String>, ApiContext) -> Map<String, Long?>,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 }) {
    private val mutable = MutableStateFlow(ChannelSpoilerState())
    val state = mutable.asStateFlow()
    private val queued = linkedSetOf<String>()
    private val inFlight = mutableSetOf<String>()
    private var work: Job? = null
    private var generation = 0L
    fun reset(owner: ApiContext = context(), preferences: AccountPreferences? = null) {
        generation++; work?.cancel(); work = null; queued.clear(); inFlight.clear()
        mutable.value = ChannelSpoilerState(owner, preferences != null, preferences?.channelSpoilers.orEmpty(),
            now = clock(), revision = mutable.value.revision + 1, dearrowEnabled = preferences?.dearrowEnabled == true)
    }
    fun configure(preferences: AccountPreferences, owner: ApiContext = context()) {
        if (owner != context()) return
        val old = mutable.value
        if (old.context != owner) { reset(owner, preferences); return }
        if (old.ready && old.rules == preferences.channelSpoilers && old.dearrowEnabled == preferences.dearrowEnabled) return
        mutable.value = old.copy(ready = true, rules = preferences.channelSpoilers, now = clock(),
            revision = old.revision + 1, playback = PlaybackSpoilerReveal(old.playback.occurrence), dearrowEnabled = preferences.dearrowEnabled)
    }
    fun reload() {
        generation++; work?.cancel(); work = null; queued.clear(); inFlight.clear()
        mutable.value = mutable.value.copy(dates = emptyMap(), now = clock(), revision = mutable.value.revision + 1)
    }
    fun ensure(videos: List<Video>) {
        val snapshot = mutable.value
        if (!snapshot.ready || snapshot.context != context()) return
        videos.forEach { video ->
            val rule = snapshot.rules[video.channelId]
            if (rule?.revealAfterDays != null && ChannelSpoilerRules.publication(video) == null &&
                video.id !in snapshot.dates && video.id !in inFlight && video.id.matches(Regex("[A-Za-z0-9_-]{11}"))) queued.add(video.id)
        }
        if (queued.isEmpty() || work != null) return
        val owner = context(); val ticket = generation
        work = scope.launch {
            try {
                delay(30) // Coalesce visible rows and queue entries into one cache read.
                while (queued.isNotEmpty()) {
                    val ids = queued.take(100); queued.removeAll(ids.toSet())
                    inFlight.addAll(ids)
                    val dates = try { fetch(ids, owner) } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { emptyMap() }
                    if (generation != ticket || owner != context()) return@launch
                    mutable.value = mutable.value.copy(dates = mutable.value.dates + ids.associateWith { dates[it] })
                    inFlight.removeAll(ids.toSet())
                }
            } finally { if (generation == ticket) work = null }
        }
    }
    fun revealTitle(occurrence: String, mode: SpoilerTitleMode) {
        if (!mutable.value.ready || mutable.value.context != context() || occurrence.isEmpty() || mutable.value.playback.occurrence != occurrence) return
        val current = mutable.value.playback
        mutable.value = mutable.value.copy(playback = current.copy(title = mode))
    }
    fun revealThumbnail(occurrence: String) {
        if (!mutable.value.ready || mutable.value.context != context() || occurrence.isEmpty() || mutable.value.playback.occurrence != occurrence) return
        val current = mutable.value.playback
        mutable.value = mutable.value.copy(playback = current.copy(thumbnail = true))
    }
    fun bindPlayback(occurrence: String?) {
        if (mutable.value.context != context() || mutable.value.playback.occurrence == occurrence.orEmpty()) return
        mutable.value = mutable.value.copy(playback = PlaybackSpoilerReveal(occurrence.orEmpty()))
    }
}
