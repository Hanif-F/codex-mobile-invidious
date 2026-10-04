package net.wingress.mobivious.player

import net.wingress.mobivious.data.AccountPreferences

enum class VideoSelectionMode { AUTO, PRESET, MANUAL }

/** Captured defaults belong to one occurrence, independently of later account updates. */
data class VideoSelection(val occurrence: String = "", val quality: String = "auto", val codec: String = "auto",
    val dash: Boolean = false, val mode: VideoSelectionMode = VideoSelectionMode.AUTO,
    val manual: StreamKey? = null, val revision: Long = 0) {
    fun auto() = copy(mode = VideoSelectionMode.AUTO, manual = null, revision = revision + 1)
    fun select(key: StreamKey) = copy(mode = VideoSelectionMode.MANUAL, manual = key, revision = revision + 1)
    private fun defaults() = copy(mode = defaultMode(quality), manual = null, revision = revision + 1)
    fun resolve(choices: List<StreamChoice>): VideoSelection =
        if (mode == VideoSelectionMode.MANUAL && choices.isNotEmpty() && StreamCatalog.findVideo(choices, manual) == null) defaults() else this
    fun fixed(choices: List<StreamChoice>): StreamChoice? = when (mode) {
        VideoSelectionMode.AUTO -> null
        VideoSelectionMode.PRESET -> StreamCatalog.defaultVideo(choices, quality, if (dash) codec else "auto")
        VideoSelectionMode.MANUAL -> StreamCatalog.findVideo(choices, manual)
            ?: StreamCatalog.defaultVideo(choices, quality, if (dash) codec else "auto")
    }
    fun adaptive(choices: List<StreamChoice>) = StreamCatalog.automaticVideo(choices, if (dash) codec else "auto")
    companion object {
        private fun defaultMode(quality: String) = if (quality == "auto") VideoSelectionMode.AUTO else VideoSelectionMode.PRESET
        fun open(occurrence: String, preferences: AccountPreferences, dash: Boolean, revision: Long = 0) = VideoSelection(
            occurrence, preferences.qualityDash, preferences.videoCodec, dash, defaultMode(preferences.qualityDash), revision = revision)
    }
}
