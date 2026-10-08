package net.wingress.mobivious.data

import java.net.URI
import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject

enum class SponsorBlockMode(val wire: String, val label: String) {
    AUTO("auto", "Auto skip"), MANUAL("manual", "Manual skip"), MARKER("marker", "Show in seek bar only"), DISABLED("disabled", "Disabled");
    companion object { fun parse(value: String) = entries.firstOrNull { it.wire == value } }
}

enum class SponsorBlockCategory(val wire: String, val label: String, val color: String) {
    SPONSOR("sponsor", "Sponsor", "#4caf50"), SELFPROMO("selfpromo", "Self-Promotion", "#ffeb3b"),
    INTERACTION("interaction", "Interaction", "#e91e63"), INTRO("intro", "Intro", "#00bcd4"),
    OUTRO("outro", "Outro", "#2196f3"), PREVIEW("preview", "Recap", "#3f51b5"),
    MUSIC_OFFTOPIC("music_offtopic", "Music Offtopic", "#ff9800"), FILLER("filler", "Filler", "#9c27b0");
    companion object { fun parse(value: String) = entries.firstOrNull { it.wire == value } }
}

data class SponsorBlockChannel(val name: String, val enabled: Boolean? = null,
    val modes: Map<SponsorBlockCategory, SponsorBlockMode> = emptyMap()) {
    fun json(includeName: Boolean = true) = JSONObject().apply {
        if (includeName) put("name", name)
        put("enabled", enabled ?: JSONObject.NULL)
        put("modes", JSONObject().apply { modes.forEach { (category, mode) -> put(category.wire, mode.wire) } })
    }
}

data class SponsorBlockSettings(val enabled: Boolean = false,
    val modes: Map<SponsorBlockCategory, SponsorBlockMode> = SponsorBlockCategory.entries.associateWith { SponsorBlockMode.MANUAL },
    val colors: Map<SponsorBlockCategory, String> = SponsorBlockCategory.entries.associateWith { it.color },
    val channels: Map<String, SponsorBlockChannel> = emptyMap()) {
    fun effective(channelId: String): SponsorBlockSettings {
        val channel = channels[channelId]
        return copy(enabled = channel?.enabled ?: enabled, modes = modes + channel?.modes.orEmpty(), channels = emptyMap())
    }
    val usable: Boolean get() = enabled && modes.values.any { it != SponsorBlockMode.DISABLED }
    fun json() = JSONObject().apply {
        put("sponsorblock_enabled", enabled)
        put("sponsorblock_modes", JSONObject().apply { modes.forEach { (k, v) -> put(k.wire, v.wire) } })
        put("sponsorblock_colors", JSONObject().apply { colors.forEach { (k, v) -> put(k.wire, v) } })
        put("sponsorblock_channel_overrides", JSONObject().apply { channels.forEach { (k, v) -> put(k, v.json()) } })
    }
    fun patch(before: SponsorBlockSettings, output: JSONObject, includeChannelNames: Boolean = false) {
        if (enabled != before.enabled) output.put("sponsorblock_enabled", enabled)
        val modesPatch = JSONObject(); val colorsPatch = JSONObject(); val channelsPatch = JSONObject()
        SponsorBlockCategory.entries.forEach { category ->
            if (modes[category] != before.modes[category]) modesPatch.put(category.wire, (modes[category] ?: SponsorBlockMode.MANUAL).wire)
            if (colors[category] != before.colors[category]) colorsPatch.put(category.wire, colors[category] ?: category.color)
        }
        (channels.keys + before.channels.keys).forEach { id ->
            if (channels[id] != before.channels[id]) channelsPatch.put(id, channels[id]?.json(includeChannelNames) ?: JSONObject.NULL)
        }
        if (modesPatch.length() > 0) output.put("sponsorblock_modes", modesPatch)
        if (colorsPatch.length() > 0) output.put("sponsorblock_colors", colorsPatch)
        if (channelsPatch.length() > 0) output.put("sponsorblock_channel_overrides", channelsPatch)
    }
    companion object {
        fun parse(json: JSONObject): SponsorBlockSettings {
            val modeJson = json.optJSONObject("sponsorblock_modes")
            val colorJson = json.optJSONObject("sponsorblock_colors")
            val channels = mutableMapOf<String, SponsorBlockChannel>()
            json.optJSONObject("sponsorblock_channel_overrides")?.let { overrides ->
                overrides.keys().forEach { id ->
                    val raw = overrides.optJSONObject(id)
                    if (SponsorBlockRules.channelId(id) == id && raw != null) {
                        val modes = SponsorBlockCategory.entries.mapNotNull { category ->
                            SponsorBlockMode.parse(raw.optJSONObject("modes")?.text(category.wire).orEmpty())?.let { category to it }
                        }.toMap()
                        val enabled = raw.opt("enabled") as? Boolean
                        if (enabled != null || modes.isNotEmpty()) channels[id] = SponsorBlockChannel(raw.text("name", id), enabled, modes)
                    }
                }
            }
            return SponsorBlockSettings(json.opt("sponsorblock_enabled") == true,
                SponsorBlockCategory.entries.associateWith { SponsorBlockMode.parse(modeJson?.text(it.wire).orEmpty()) ?: SponsorBlockMode.MANUAL },
                SponsorBlockCategory.entries.associateWith { colorJson?.text(it.wire)?.takeIf(SponsorBlockRules::validColor) ?: it.color }, channels)
        }
    }
}

data class SponsorBlockSegment(val id: String, val category: SponsorBlockCategory, val start: Long, val end: Long) {
    fun json() = JSONObject().put("id", id).put("category", category.wire).put("start", start / 1000.0).put("end", end / 1000.0)
}

object SponsorBlockRules {
    fun validColor(value: String) = Regex("^#[0-9a-fA-F]{6}$").matches(value)
    fun validVideo(id: String) = Regex("^[A-Za-z0-9_-]{11}$").matches(id)
    fun channelId(input: String): String? = runCatching {
        val value = input.trim()
        val id = if (value.startsWith("UC")) value else Regex("^/channel/(UC[A-Za-z0-9_-]{22})/?$").matchEntire(URI(value).path.orEmpty())?.groupValues?.get(1)
        id?.takeIf { Regex("^UC[A-Za-z0-9_-]{22}$").matches(it) }
    }.getOrNull()
    fun segments(json: JSONObject): List<SponsorBlockSegment> = json.optJSONArray("segments")?.objects()?.mapNotNull { raw ->
        val category = SponsorBlockCategory.parse(raw.text("category")) ?: return@mapNotNull null
        val start = raw.opt("start") as? Number ?: return@mapNotNull null
        val end = raw.opt("end") as? Number ?: return@mapNotNull null
        val a = start.toDouble(); val b = end.toDouble(); val id = raw.opt("id") as? String
        if (id.isNullOrBlank() || !a.isFinite() || !b.isFinite() || a < 0 || b <= a || b >= Long.MAX_VALUE / 1000.0) null
        else SponsorBlockSegment(id, category, (a * 1000).roundToLong(), (b * 1000).roundToLong()).takeIf { it.end > it.start }
    }?.distinctBy { it.id }?.sortedBy { it.start }.orEmpty()
}

data class SponsorBlockPlayback(val mediaId: String = "", val token: String = "",
    val settings: SponsorBlockSettings = SponsorBlockSettings(), val segments: List<SponsorBlockSegment> = emptyList(),
    val active: SponsorBlockSegment? = null, val remaining: Long = 0, val notice: String = "") {
    fun json() = JSONObject().put("mediaId", mediaId).put("token", token).put("settings", settings.json())
        .put("segments", JSONArray().apply { segments.forEach { put(it.json()) } }).put("active", active?.id ?: "")
        .put("remaining", remaining).put("notice", notice)
    companion object {
        fun parse(json: JSONObject): SponsorBlockPlayback {
            val segments = SponsorBlockRules.segments(json)
            return SponsorBlockPlayback(json.text("mediaId"), json.text("token"), SponsorBlockSettings.parse(json.optJSONObject("settings") ?: JSONObject()),
                segments, segments.firstOrNull { it.id == json.text("active") }, json.optLong("remaining"), json.text("notice"))
        }
    }
}
