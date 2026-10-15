package net.wingress.mobivious.player

import net.wingress.mobivious.data.AccountPreferences
import net.wingress.mobivious.data.AudioPreferences

/** Upstream audio IDs distinguish languages/variants; rendition IDs alone do not. */
data class AudioTrackKey(val identity: String, val language: String, val stable: Boolean, val bitrate: Long,
    val codec: String, val label: String?, val legacyId: String?) {
    companion object {
        fun of(choice: StreamChoice) = AudioTrackKey(choice.audioTrackId, choice.language, choice.stable, choice.bitrate,
            choice.codec, choice.key.label.takeIf { choice.audioTrackId.isEmpty() },
            choice.key.id.takeIf { choice.audioTrackId.isEmpty() && choice.language.isEmpty() })
    }
}

/** Defaults and manual choices belong to an occurrence, including refreshed manifests. */
data class AudioSelection(val occurrence: String = "", val preference: String = "original", val enabled: Boolean = false,
    val manual: AudioTrackKey? = null) {
    fun auto() = copy(manual = null)
    fun select(choice: StreamChoice) = copy(manual = AudioTrackKey.of(choice))
    fun fixed(choices: List<StreamChoice>): StreamChoice? {
        if (!enabled) return null
        if (manual != null) {
            choices.filter { AudioTrackKey.of(it) == manual }.singleOrNull()?.let { return it }
            return preferred(choices, "original")
        }
        return preferred(choices, preference)
    }

    companion object {
        fun open(occurrence: String, preferences: AccountPreferences, enabled: Boolean) =
            AudioSelection(occurrence, AudioPreferences.normalize(preferences.preferredAudio), enabled)

        private fun preferred(choices: List<StreamChoice>, preference: String): StreamChoice? {
            val original = choices.filter { it.original }
            val regular = original.filterNot { it.stable }
            val candidates = when (preference) {
                "original" -> regular
                "original_stable" -> choices.filter { entry -> entry.stable && (entry.original ||
                    entry.language.isNotEmpty() && original.any { it.language == entry.language }) }
                else -> {
                    val tag = StreamCatalog.audioLanguage(preference)
                    val available = choices.filterNot { it.stable }
                    available.filter { it.language == tag }.ifEmpty {
                        if ('-' !in tag) available.filter { it.language.substringBefore('-') == tag } else emptyList()
                    }
                }
            }.ifEmpty { regular }
            // Without reliable original identity, leave Media3's playable default alone.
            return candidates.sortedWith(compareByDescending<StreamChoice> { it.bitrate }.thenBy { it.originalIndex }).firstOrNull()
        }
    }
}
