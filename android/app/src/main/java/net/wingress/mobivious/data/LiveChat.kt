package net.wingress.mobivious.data

import com.google.re2j.Pattern
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.util.Locale

data class ChatMessage(val id: String, val offsetMs: Long, val author: String, val authorChannelId: String,
    val authorHandle: String, val text: String, val kind: String = "text", val amount: String = "")
data class ChatChunk(val messages: List<ChatMessage>, val removedIds: List<String> = emptyList(), val continuation: String = "") {
    companion object {
        fun parse(j: JSONObject): ChatChunk {
            val rows = j.getJSONArray("messages")
            val removed = j.getJSONArray("removedIds")
            require(j.has("continuation") && (j.isNull("continuation") || j.opt("continuation") is String)) { "Invalid chat continuation" }
            val messages = rows.objects().mapNotNull { m ->
                val id = m.opt("id") as? String ?: return@mapNotNull null
                val text = m.opt("text") as? String ?: return@mapNotNull null
                val raw = m.opt("offsetMs") as? Number ?: return@mapNotNull null
                val offset = raw.toLong()
                if (id.isBlank() || text.isEmpty() || offset < 0 || raw.toDouble() != offset.toDouble()) return@mapNotNull null
                ChatMessage(id, offset, m.text("author"), m.text("authorChannelId"), m.text("authorHandle"), text,
                    m.text("kind", "text"), m.text("amount"))
            }
            return ChatChunk(messages, (0 until removed.length()).mapNotNull { removed.opt(it) as? String }, j.text("continuation"))
        }
    }
}

data class ChatPreferences(val timestamps: Boolean = true, val users: String = "", val words: String = "") {
    fun json() = JSONObject().put("chat_show_timestamps", timestamps).put("chat_user_blacklist", users).put("chat_word_blacklist", words)
    companion object { fun parse(j: JSONObject) = ChatPreferences(j.optBoolean("chat_show_timestamps", true), j.text("chat_user_blacklist"), j.text("chat_word_blacklist")) }
}

data class ChatAppearance(val overlay: Boolean = false, val hideUserIds: Boolean = true, val fontScale: Int = 100,
    val opacity: Int = 75, val belowFraction: Float = .60f, val besideFraction: Float = .35f,
    val x: Float = .56f, val y: Float = .05f, val width: Float = .40f, val height: Float = .75f) {
    fun bounded(): ChatAppearance {
        fun Float.safe(default: Float, min: Float, max: Float) = if (isFinite()) coerceIn(min, max) else default.coerceIn(min, max)
        val w = width.safe(.4f, .1f, 1f); val h = height.safe(.75f, .1f, 1f)
        return copy(fontScale = fontScale.coerceIn(25, 300), opacity = opacity.coerceIn(0, 100),
            belowFraction = belowFraction.safe(.6f, .3f, .7f), besideFraction = besideFraction.safe(.35f, BESIDE_FRACTION_RANGE.start, BESIDE_FRACTION_RANGE.endInclusive),
            width = w, height = h, x = x.safe(.56f, 0f, 1f - w), y = y.safe(.05f, 0f, 1f - h))
    }
    fun json() = JSONObject().put("overlay", overlay).put("hideUserIds", hideUserIds).put("fontScale", fontScale).put("opacity", opacity)
        .put("belowFraction", belowFraction).put("besideFraction", besideFraction).put("x", x).put("y", y).put("width", width).put("height", height)
    companion object {
        val BESIDE_FRACTION_RANGE = .1f.. .7f
        fun parse(j: JSONObject) = ChatAppearance(j.optBoolean("overlay"), j.optBoolean("hideUserIds", true), j.optInt("fontScale", 100),
            j.optInt("opacity", 75), j.optDouble("belowFraction", .6).toFloat(), j.optDouble("besideFraction", .35).toFloat(),
            j.optDouble("x", .56).toFloat(), j.optDouble("y", .05).toFloat(), j.optDouble("width", .4).toFloat(), j.optDouble("height", .75).toFloat()).bounded()
    }
}

/** Browser-compatible token format, with linear-time native regex matching. */
class ChatFilters(preferences: ChatPreferences) {
    private val users = if (preferences.users.toByteArray(Charsets.UTF_8).size <= 1024)
        tokens(preferences.users).filter { it.length <= 128 }.map { it.lowercase(Locale.ROOT).removePrefix("@") }.toSet() else emptySet()
    private val words = mutableListOf<(String) -> Boolean>()
    val invalid = mutableListOf<String>()
    init {
        if (preferences.users.toByteArray(Charsets.UTF_8).size > 1024 || tokens(preferences.users).any { it.length > 128 }) invalid.add("user filter exceeds native limits")
        val wordTokens = if (preferences.words.toByteArray(Charsets.UTF_8).size <= 1024) tokens(preferences.words)
            else { invalid.add("word filter exceeds native limits"); emptyList() }
        wordTokens.forEach { token ->
            try {
                require(token.length <= 128)
                if (token.startsWith('/') && token.endsWith('/') && token.length >= 2) {
                    val pattern = Pattern.compile(token.substring(1, token.lastIndex), Pattern.CASE_INSENSITIVE)
                    words.add { text -> pattern.matcher(text).find() }
                } else {
                    val literal = token.lowercase(Locale.ROOT)
                    words.add { text -> text.lowercase(Locale.ROOT).contains(literal) }
                }
            } catch (_: RuntimeException) { invalid.add(token) }
        }
    }
    fun accepts(m: ChatMessage) = m.authorChannelId.lowercase(Locale.ROOT) !in users &&
        (m.authorHandle.isBlank() || m.authorHandle.lowercase(Locale.ROOT).removePrefix("@") !in users) && words.none { it(m.text) }
    companion object {
        private fun tokens(raw: String) = raw.trim().split(Regex("[\\s\\p{Z}]+")).filter(String::isNotBlank)
        fun validate(p: ChatPreferences) {
            require(p.users.toByteArray(Charsets.UTF_8).size <= 1024 && p.words.toByteArray(Charsets.UTF_8).size <= 1024) { "Filters must be at most 1,024 UTF-8 bytes." }
            require((tokens(p.users) + tokens(p.words)).all { it.length <= 128 }) { "Filter tokens must be at most 128 characters." }
            require(ChatFilters(p).invalid.isEmpty()) { "Invalid or unsupported regex. Lookaround and backreferences are unsupported." }
        }
        fun validateChanges(value: ChatPreferences, before: ChatPreferences) = validate(value.copy(
            users = if (value.users == before.users) "" else value.users,
            words = if (value.words == before.words) "" else value.words))
    }
}

data class ChatReplayState(val videoId: String = "", val occurrence: String = "", val context: ApiContext? = null,
    val available: Boolean = false, val open: Boolean = false, val visible: Boolean = false,
    val messages: List<ChatMessage> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false,
    val unavailable: Boolean = false, val error: String? = null, val following: Boolean = true,
    val position: CommentPosition = CommentPosition(), val windowRevision: Long = 0,
    val timingOffsetMs: Int = 0, val timingLoading: Boolean = false, val timingError: String? = null,
    val timingSaving: Boolean = false, val filterWarning: String? = null)

/** UI-session replay: never owns playback or changes its play/pause state. */
class ChatReplayController(private val scope: CoroutineScope, private val context: () -> ApiContext,
    private val fetch: suspend (String, Long, String, ApiContext) -> ChatChunk,
    private val readTiming: suspend (String, ApiContext) -> Int,
    private val writeTiming: suspend (String, Int, ApiContext) -> Unit,
    private val errorMessage: (Exception) -> String,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    val state = MutableStateFlow(ChatReplayState())
    private class Segment(val requestOffset: Long) {
        var start = (requestOffset - 15_000).coerceAtLeast(0)
        var trimmed = false
        var end = requestOffset; var started = false; var complete = false; var failed = false
        var continuation = ""; val cursors = mutableSetOf<String>(); val messages = linkedMapOf<String, ChatMessage>()
    }
    private val segments = mutableListOf<Segment>()
    private val removed = linkedSetOf<String>()
    private var active: Segment? = null
    private var request: Job? = null
    private var timingRead: Job? = null
    private var timingWrite: Job? = null
    private var generation = 0L
    private var timingRevision = 0L
    private var timingLoaded = false
    private var timingDirty = false
    private var playbackPosition = 0L
    private var duration = 0L
    private var nextRequestAt = 0L
    private var filters = ChatFilters(ChatPreferences())
    private var preferences = ChatPreferences()
    private var presentationVisible = false

    fun bind(videoId: String, occurrence: String, available: Boolean) {
        val ctx = context()
        if (state.value.videoId == videoId && state.value.occurrence == occurrence && state.value.context == ctx) {
            if (state.value.available != available) { state.value = state.value.copy(available = available); if (!available) close() }
            return
        }
        cancelRequest(); timingRead?.cancel(); timingWrite?.cancel(); timingRevision++
        segments.clear(); removed.clear(); active = null; timingLoaded = false; timingDirty = false; nextRequestAt = 0
        state.value = ChatReplayState(videoId = videoId, occurrence = occurrence, available = available, context = ctx,
            filterWarning = if (filters.invalid.isEmpty()) null else "Unsupported imported filters skipped: ${filters.invalid.joinToString()}")
    }
    fun configure(p: ChatPreferences) {
        if (p == preferences) return
        preferences = p; filters = ChatFilters(p)
        state.value = state.value.copy(filterWarning = if (filters.invalid.isEmpty()) null else "Unsupported imported filters skipped: ${filters.invalid.joinToString()}")
        rebuild(true)
    }
    fun open() {
        if (!state.value.available || state.value.context != context()) return
        state.value = state.value.copy(open = true, visible = presentationVisible)
        if (timingLoaded) selectPosition(false)
        resume()
    }
    fun close() { cancelRequest(); timingRead?.cancel(); state.value = state.value.copy(open = false, visible = false, timingLoading = false) }
    fun present(visible: Boolean) {
        presentationVisible = visible
        val showing = visible && state.value.open && state.value.available
        if (showing == state.value.visible) return
        state.value = state.value.copy(visible = showing)
        if (showing) { selectPosition(false); resume() }
        else { cancelRequest(); timingRead?.cancel(); state.value = state.value.copy(timingLoading = false) }
    }
    private fun resume() {
        if (!state.value.visible) return
        if (!timingLoaded) loadTiming() else { if (active == null) selectPosition(); pump() }
    }
    private fun loadTiming() {
        if (state.value.timingLoading) return
        val before = state.value; val revision = timingRevision; val version = generation
        state.value = before.copy(timingLoading = true, timingError = null)
        timingRead = scope.launch {
            try {
                val offset = readTiming(before.videoId, before.context!!).coerceIn(-3_600_000, 3_600_000)
                if (same(before) && revision == timingRevision && version == generation) {
                    timingLoaded = true; state.value = state.value.copy(timingOffsetMs = offset, timingLoading = false)
                    selectPosition(); pump()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (same(before) && revision == timingRevision && version == generation) {
                timingLoaded = true; state.value = state.value.copy(timingLoading = false, timingError = errorMessage(e))
                selectPosition(); pump()
            } }
        }
    }
    fun timing(offset: Int) {
        require(offset in -3_600_000..3_600_000)
        timingRevision++; timingRead?.cancel(); timingWrite?.cancel(); timingLoaded = true; timingDirty = true
        state.value = state.value.copy(timingOffsetMs = offset, timingLoading = false, timingError = null)
        selectPosition(); saveTiming()
    }
    private fun saveTiming() {
        val before = state.value; val revision = timingRevision
        state.value = before.copy(timingSaving = true, timingError = null)
        timingWrite = scope.launch {
            try {
                writeTiming(before.videoId, before.timingOffsetMs, before.context!!)
                if (same(before) && revision == timingRevision) { timingDirty = false; state.value = state.value.copy(timingSaving = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (same(before) && revision == timingRevision) state.value = state.value.copy(timingSaving = false, timingError = errorMessage(e)) }
        }
    }
    fun retryTiming() { if (state.value.timingError != null) { if (!timingDirty) { timingLoaded = false; loadTiming() } else saveTiming() } }
    fun update(position: Long, durationMs: Long, discontinuity: Boolean = false) {
        val jumped = discontinuity || position < playbackPosition || position - playbackPosition > 2_000
        playbackPosition = position.coerceAtLeast(0); duration = durationMs.coerceAtLeast(0)
        if (!state.value.visible || !timingLoaded || state.value.context != context()) return
        if (active == null || jumped || active?.let { it.trimmed && requestOffset() > it.end } == true) selectPosition()
        rebuild(false); pump()
    }
    private fun due() = playbackPosition - state.value.timingOffsetMs
    private fun requestOffset() = due().coerceAtLeast(0).let { if (duration > 0) minOf(it, duration) else it }
    private fun selectPosition(force: Boolean = true) {
        if (!state.value.visible || !timingLoaded) return
        val at = requestOffset()
        val chosen = segments.lastOrNull { it.started && at >= it.start && (it.complete && !it.trimmed || at <= it.end) }
            ?: Segment(at).also { segments.add(it) }
        val switched = chosen !== active
        if (switched) {
            cancelRequest(); active = chosen; segments.remove(chosen); segments.add(chosen)
            trim(); state.value = state.value.copy(error = null, unavailable = false, loaded = chosen.started)
        }
        rebuild(force || switched); pump()
    }
    private fun cancelRequest() { generation++; request?.cancel(); request = null; state.value = state.value.copy(loading = false) }
    private fun same(before: ChatReplayState) = before.videoId == state.value.videoId && before.occurrence == state.value.occurrence && before.context == context() && before.context == state.value.context
    private fun rows() = active?.messages?.values.orEmpty().filter { it.offsetMs <= due() && filters.accepts(it) }.sortedBy { it.offsetMs }
    private fun rebuild(force: Boolean) {
        val current = state.value
        val rows = rows()
        val visible = if (force || current.following) rows.takeLast(120) else current.messages
            .mapNotNull { old -> rows.firstOrNull { it.id == old.id } }.sortedBy { it.offsetMs }
        state.value = current.copy(messages = visible, following = if (force) true else current.following,
            position = if (force) CommentPosition() else current.position, windowRevision = current.windowRevision + if (force) 1 else 0)
    }
    fun position(position: CommentPosition, following: Boolean) {
        val atPlayback = state.value.messages.lastOrNull()?.id == rows().lastOrNull()?.id
        state.value = state.value.copy(position = position, following = following && atPlayback)
    }
    fun follow() = rebuild(true)
    fun older() {
        if (state.value.following) return
        val rows = rows(); val first = state.value.messages.firstOrNull()?.id ?: return
        val index = rows.indexOfFirst { it.id == first }
        if (index > 0) {
            val earlier = rows.subList((index - 120).coerceAtLeast(0), index)
            state.value = state.value.copy(messages = earlier, position = CommentPosition(earlier.lastIndex, 0), windowRevision = state.value.windowRevision + 1)
        }
    }
    fun retry() { active?.failed = false; state.value = state.value.copy(error = null); pump() }
    private fun pump() {
        val segment = active ?: return
        if (!state.value.visible || !timingLoaded || segment.complete || segment.failed || state.value.unavailable || state.value.loading) return
        if (segment.started && segment.end > due() + 30_000) return
        val before = state.value; val version = generation
        val token = segment.continuation
        state.value = before.copy(loading = true)
        request = scope.launch {
            delay((nextRequestAt - now()).coerceAtLeast(0))
            try {
                if (version != generation || !same(before) || !state.value.visible) return@launch
                nextRequestAt = now() + 800
                val result = fetch(before.videoId, if (segment.started) maxOf(segment.requestOffset, segment.end) else segment.requestOffset, token, before.context!!)
                if (version != generation || !same(before) || segment !== active) return@launch
                result.removedIds.forEach { id -> removed.add(id); segments.forEach { it.messages.remove(id) } }
                while (removed.size > 5_000) removed.remove(removed.first())
                result.messages.filter { it.id !in removed }.forEach { message ->
                    segments.forEach { if (message.id in it.messages) it.messages[message.id] = message }
                    segment.messages[message.id] = message; segment.end = maxOf(segment.end, message.offsetMs)
                }
                segment.started = true
                val repeated = result.continuation.isNotEmpty() && (result.continuation == token || result.continuation in segment.cursors)
                if (token.isNotEmpty()) segment.cursors.add(token)
                if (segment.cursors.size > 5_000) { segment.failed = true; throw IllegalStateException("Chat pagination limit reached. Seek or reopen this video to continue.") }
                segment.continuation = if (repeated) token else result.continuation
                segment.failed = repeated
                segment.complete = result.continuation.isEmpty()
                state.value = state.value.copy(loading = false, loaded = true, error = if (repeated) "Chat pagination stopped because the server repeated a cursor." else null)
                trim(); rebuild(false)
                // The next playback tick pumps sparse chunks too; never chain concurrent jobs.
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version == generation && same(before)) {
                    segment.failed = true
                    state.value = state.value.copy(loading = false, unavailable = e is ApiException && e.status == 404, error = errorMessage(e))
                }
            }
        }
    }
    private fun trim() {
        while (segments.size > 24 || segments.sumOf { it.messages.size } > 5_000 && segments.size > 1) segments.removeAt(0)
        active?.let { segment ->
            if (segment.messages.size > 5_000) {
                val keep = segment.messages.values.sortedBy { kotlin.math.abs(it.offsetMs - requestOffset()) }.take(5_000).map { it.id }.toSet()
                segment.messages.keys.retainAll(keep)
                segment.trimmed = true
                segment.start = segment.messages.values.minOf { it.offsetMs }
                segment.end = segment.messages.values.maxOf { it.offsetMs }
            }
        }
    }
}
