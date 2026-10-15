package net.wingress.mobivious.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.wingress.mobivious.data.*

enum class SeekPreviewKind { DRAG, TAP }
data class SeekPreviewBinding(val context: ApiContext, val videoId: String, val occurrence: String,
    val track: StoryboardTrack, val durationMs: Long, val clipStartMs: Long = 0)
data class SeekPreviewState<T>(val kind: SeekPreviewKind? = null, val targetMs: Long = 0,
    val frame: StoryboardFrame? = null, val sheet: T? = null)

/** Input changes never cancel a sheet needed by another frame. Only interaction/context end does. */
class SeekPreviewController<T>(private val scope: CoroutineScope,
    private val loadFrames: suspend (StoryboardTrack, ApiContext) -> List<StoryboardFrame>,
    private val cachedSheet: suspend (String) -> T?, private val downloadSheet: suspend (String) -> T?,
    private val fits: (T, StoryboardFrame) -> Boolean, private val now: () -> Long) {
    companion object { const val TAP_DOWNLOAD_INTERVAL = 250L }
    private val mutable = MutableStateFlow(SeekPreviewState<T>())
    val state = mutable.asStateFlow()
    private var binding: SeekPreviewBinding? = null
    private var frames: List<StoryboardFrame>? = null
    private var indexJob: Job? = null
    private var selectionJob: Job? = null
    private var downloadJob: Job? = null
    private var generation = 0L
    private var revision = 0L
    private var lastTapDownload: Long? = null
    private var tapOwner: Triple<ApiContext, String, String>? = null
    private var lastSheet: Pair<String, T>? = null
    private var retryIndex = false
    private val failed = mutableSetOf<String>()

    fun bind(value: SeekPreviewBinding?) {
        if (binding == value) return
        cancel(); binding = value; frames = null
        // Temporarily hiding the player must not reset a tap burst's download budget.
        if (value != null) {
            val owner = Triple(value.context, value.videoId, value.occurrence)
            if (owner != tapOwner) { tapOwner = owner; lastTapDownload = null }
        }
    }
    fun begin(kind: SeekPreviewKind, targetMs: Long) {
        if (binding == null) return
        if (mutable.value.kind != kind) { cancel(); mutable.value = SeekPreviewState(kind) }
        update(targetMs)
    }
    fun update(targetMs: Long) {
        val bound = binding ?: return
        if (mutable.value.kind == null) return
        mutable.value = mutable.value.copy(targetMs = PlaybackRules.seek(targetMs, bound.durationMs))
        if (frames == null) {
            if (indexJob?.isActive == true) return
            val token = generation
            indexJob = scope.launch {
                val loaded = try { loadFrames(bound.track, bound.context) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) {
                        if (generation == token) retryIndex = true
                        emptyList()
                    }
                if (generation == token) { frames = loaded; select() }
            }
        } else select()
    }
    fun finish() = cancel()
    fun cancel() {
        generation++; revision++
        indexJob?.cancel(); indexJob = null
        selectionJob?.cancel(); selectionJob = null
        downloadJob?.cancel(); downloadJob = null
        if (retryIndex) { frames = null; retryIndex = false }
        failed.clear(); lastSheet = null; mutable.value = SeekPreviewState()
    }
    private fun selectedFrame(): StoryboardFrame? {
        val bound = binding ?: return null
        return StoryboardRules.frame(frames.orEmpty(), StoryboardRules.sourcePosition(mutable.value.targetMs, bound.durationMs, bound.clipStartMs))
    }
    private fun select() {
        selectionJob?.cancel()
        val token = generation; val version = ++revision
        val frame = selectedFrame() ?: return
        selectionJob = scope.launch {
            val cached = lastSheet?.takeIf { it.first == frame.url }?.second ?:
                try { cachedSheet(frame.url) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (generation != token || revision != version || mutable.value.kind == null) return@launch
            if (cached != null) {
                if (fits(cached, frame)) mutable.value = mutable.value.copy(frame = frame, sheet = cached)
                return@launch
            }
            if (frame.url in failed || downloadJob?.isActive == true) return@launch
            if (mutable.value.kind == SeekPreviewKind.TAP) {
                val wait = lastTapDownload?.let { (it + TAP_DOWNLOAD_INTERVAL - now()).coerceAtLeast(0) } ?: 0
                if (wait > 0) { delay(wait); if (generation == token && revision == version) select(); return@launch }
                lastTapDownload = now()
            }
            downloadJob = scope.launch {
                val sheet = try { downloadSheet(frame.url) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if (generation != token) return@launch
                if (sheet == null) failed.add(frame.url)
                else lastSheet = frame.url to sheet
                val current = selectedFrame()
                if (sheet != null && current?.url == frame.url && fits(sheet, current))
                    mutable.value = mutable.value.copy(frame = current, sheet = sheet)
                downloadJob = null
                // Recheck the newest target, including cache hits while another sheet was loading.
                select()
            }
        }
    }
}
