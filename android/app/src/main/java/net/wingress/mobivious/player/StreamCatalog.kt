package net.wingress.mobivious.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionParameters
import net.wingress.mobivious.data.StreamFormat
import java.util.Locale
import kotlin.math.roundToInt

enum class AudioSection(val title: String) { ORIGINAL("Original Audio"), STABLE("Stable Volume"), DUBBED("Dubbed Audio"), OTHER("Audio tracks") }

/** Representation identity survives a manifest reload without retaining stale TrackGroups. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
data class StreamKey(val id: String?, val language: String?, val label: String?, val codec: String?,
    val width: Int, val height: Int, val fps: Float, val bitrate: Int, val roles: Int) {
    companion object {
        fun of(f: Format) = StreamKey(f.id, f.language, f.label, f.codecs ?: f.sampleMimeType,
            f.width, f.height, f.frameRate, f.bitrate, f.roleFlags)
    }
}
data class StreamChoice(val group: TrackGroup, val index: Int, val key: StreamKey, val primary: String,
    val secondary: String, val selected: Boolean, val height: Int, val fps: Float, val bitrate: Long,
    val section: AudioSection = AudioSection.OTHER, val identity: String = "", val codec: String = "") {
    fun explicitlySelected(parameters: TrackSelectionParameters?) = parameters?.overrides?.get(group)?.trackIndices?.contains(index) == true
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object StreamCatalog {
    private val qualifier = Regex("\\[\\s*\\d+(?:\\.\\d+)?k\\s*]|\\b(?:stable volume|drc|original audio|original)\\b", RegexOption.IGNORE_CASE)
    private fun normalized(name: String) = name.replace(qualifier, " ").replace(Regex("[·|]"), " ").trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    private fun language(f: StreamFormat) = f.audio?.id?.substringBefore('.')?.lowercase(Locale.ROOT).orEmpty()
    private fun stable(f: StreamFormat) = f.drc == true || Regex("stable volume|\\bdrc\\b", RegexOption.IGNORE_CASE).containsMatchIn(f.audio?.name.orEmpty())

    /** Only use unambiguous metadata; itags alone are not audio identities. */
    fun metadata(format: Format, formats: List<StreamFormat>): StreamFormat? {
        val audio = format.sampleMimeType?.startsWith("audio/") == true
        val candidates = formats.filter { it.mimeType.startsWith(if (audio) "audio/" else "video/") }
        val ids = candidates.filter { it.id.isNotBlank() && it.id == format.id }
        val pool = ids.ifEmpty { candidates }
        val label = format.label.orEmpty()
        val name = normalized(label)
        val lang = format.language?.lowercase(Locale.ROOT)?.takeUnless { it == "und" }.orEmpty()
        val drcHint = Regex("stable volume|\\bdrc\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)
        val matching = pool.filter { f ->
            if (!audio) (format.height <= 0 || f.height == format.height || f.width == format.width) &&
                (format.bitrate <= 0 || f.bitrate == format.bitrate.toLong())
            else (lang.isEmpty() || language(f).isEmpty() || language(f) == lang) &&
                (name.isEmpty() || f.audio?.name.isNullOrBlank() || normalized(f.audio.name) == name) &&
                (label.isEmpty() || stable(f) == drcHint) &&
                (format.bitrate <= 0 || f.bitrate <= 0 || f.bitrate == format.bitrate.toLong())
        }
        // A unique representation ID is sufficient when the manifest omits labels/details.
        return matching.singleOrNull() ?: ids.singleOrNull()?.takeIf { !audio ||
            (lang.isEmpty() || language(it).isEmpty() || language(it) == lang) && (label.isEmpty() || stable(it) == drcHint) }
    }

    fun choices(tracks: Tracks, type: Int, formats: List<StreamFormat> = emptyList()): List<StreamChoice> {
        val raw = tracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                val f = group.getTrackFormat(index)
                val meta = metadata(f, formats)
                val height = f.height.takeIf { it > 0 } ?: meta?.height ?: 0
                val fps = f.frameRate.takeIf { it > 0 && it.isFinite() } ?: meta?.fps ?: 0f
                val bitrate = f.bitrate.toLong().takeIf { it > 0 } ?: meta?.bitrate ?: 0
                val codec = codecLabel(f.codecs ?: meta?.codec.orEmpty(), f.sampleMimeType ?: meta?.mimeType.orEmpty())
                val displayLanguage = f.language?.takeUnless { it == "und" || it.isBlank() }?.let { Locale.forLanguageTag(it).getDisplayName(Locale.getDefault()) }
                val name = meta?.audio?.name?.takeIf { it.isNotBlank() } ?: f.label?.takeIf { it.isNotBlank() } ?: displayLanguage
                    ?: "${if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Caption"} track ${index + 1}"
                val isStable = meta?.let(::stable) == true || Regex("stable volume|\\bdrc\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)
                val original = if (meta?.audio?.default != null) meta.audio.default == true else
                    f.roleFlags and C.ROLE_FLAG_MAIN != 0 || Regex("\\boriginal\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)
                val knownDub = meta?.audio?.default == false || f.roleFlags and C.ROLE_FLAG_ALTERNATE != 0
                val section = when { isStable -> AudioSection.STABLE; original -> AudioSection.ORIGINAL; knownDub -> AudioSection.DUBBED; else -> AudioSection.OTHER }
                val primary = if (type == C.TRACK_TYPE_VIDEO) {
                    if (height > 0) "${height}p${if (fps > 0) " · ${decimal(fps.toDouble(), 2)} FPS" else ""}" else "Video track ${index + 1}"
                } else if (type == C.TRACK_TYPE_AUDIO) name.replace(Regex("\\[\\s*\\d+(?:\\.\\d+)?k\\s*]"), "").trim() else name
                val secondary = if (type == C.TRACK_TYPE_TEXT) "" else listOf(codec, bitrateLabel(bitrate), bytesLabel(meta?.bytes ?: 0)).filter { it.isNotEmpty() }.joinToString(" · ")
                StreamChoice(group.mediaTrackGroup, index, StreamKey.of(f), primary, secondary, group.isTrackSelected(index),
                    height, fps, bitrate, section, meta?.audio?.id ?: f.language ?: name, codec)
            }
        }
        val ordered = if (type == C.TRACK_TYPE_VIDEO) rank(raw) else if (type == C.TRACK_TYPE_AUDIO)
            raw.sortedWith(compareBy<StreamChoice> { it.section.ordinal }.thenBy { it.identity }.thenByDescending { it.bitrate }) else raw
        return ordered.map { entry ->
            val peers = ordered.filter { if (type == C.TRACK_TYPE_VIDEO) it.height == entry.height && it.fps.roundToInt() == entry.fps.roundToInt() && it.codec == entry.codec
                else type == C.TRACK_TYPE_AUDIO && it.section == entry.section && it.identity == entry.identity && it.codec == entry.codec }
            val rates = peers.map { it.bitrate }.filter { it > 0 }.distinct().sortedDescending()
            val tier = if (rates.size < 2) "" else when (entry.bitrate) { rates.first() -> "High Bitrate"; rates.last() -> "Low Bitrate"; else -> "Medium Bitrate" }
            val label = entry.primary + if (tier.isEmpty()) "" else " · $tier"
            // Keep unknown/unlabeled representations individually accessible.
            entry.copy(primary = if (ordered.count { it.primary == entry.primary && it.secondary == entry.secondary } > 1 && tier.isEmpty())
                "$label (${ordered.indexOf(entry) + 1})" else label)
        }
    }
    fun rank(choices: List<StreamChoice>) = choices.sortedWith(compareByDescending<StreamChoice> { it.height }.thenByDescending { it.fps }.thenByDescending { it.bitrate })
    fun defaultVideo(choices: List<StreamChoice>, preference: String): StreamChoice? {
        val sorted = rank(choices)
        return when (preference) { "auto" -> null; "best" -> sorted.firstOrNull(); "worst" -> sorted.lastOrNull()
            else -> preference.removeSuffix("p").toIntOrNull()?.let { height -> sorted.firstOrNull { it.height <= height } ?: sorted.lastOrNull() } }
    }
    fun find(choices: List<StreamChoice>, key: StreamKey?) = key?.let { wanted -> choices.filter { it.key == wanted }.singleOrNull() }
    private fun decimal(value: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", value).trimEnd('0').trimEnd('.')
    fun bitrateLabel(value: Long) = when { value >= 1_000_000 -> "${decimal(value / 1_000_000.0, 2)} Mbps"; value >= 1000 -> "${decimal(value / 1000.0, 1)} kbps"; value > 0 -> "$value bps"; else -> "" }
    fun bytesLabel(value: Long) = when { value >= 1_000_000_000 -> "${decimal(value / 1_000_000_000.0, 1)} GB"; value >= 1_000_000 -> "${decimal(value / 1_000_000.0, 1)} MB"; value >= 1000 -> "${decimal(value / 1000.0, 1)} kB"; value > 0 -> "$value B"; else -> "" }
    private fun codecLabel(codec: String, mime: String): String = when {
        codec.startsWith("avc") || mime == "video/avc" -> "H.264"; codec.startsWith("hev") || codec.startsWith("hvc") || mime == "video/hevc" -> "HEVC"
        codec.startsWith("vp09") || mime == "video/x-vnd.on2.vp9" -> "VP9"; codec.startsWith("av01") || mime == "video/av01" -> "AV1"
        codec.startsWith("mp4a") || mime == "audio/mp4a-latm" -> "AAC"; codec.contains("opus") || mime == "audio/opus" -> "Opus"
        else -> codec.ifBlank { mime.substringAfter('/', "") }
    }
}
