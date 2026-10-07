package net.wingress.mobivious.ui

import net.wingress.mobivious.data.*

data class ClipResolutionState(val link: ClipLink? = null, val loading: Boolean = false, val error: String? = null, val foreign: Boolean = false)
data class ClipEditorState(val open: Boolean = false, val details: VideoDetails? = null, val context: ApiContext? = null,
    val occurrence: String = "", val wasPlaying: Boolean = false, val title: String = "", val startMs: Long = 0, val endMs: Long = 0,
    val startText: String = "", val endText: String = "", val frames: List<StoryboardFrame> = emptyList(),
    val loadingFrames: Boolean = false, val busy: Boolean = false, val error: String? = null, val published: Clip? = null) {
    val validation: String? get() = if (ClipRules.parseTimestamp(startText) == null || ClipRules.parseTimestamp(endText) == null)
        "Use M:SS.s or H:MM:SS.s for clip times."
        else ClipRules.error(title, startMs, endMs, (details?.video?.duration ?: 0) * 1000)
    val validRange get() = ClipRules.parseTimestamp(startText) != null && ClipRules.parseTimestamp(endText) != null &&
        startMs >= 0 && endMs <= (details?.video?.duration ?: 0) * 1000 && endMs - startMs in 5_000..120_000
}
