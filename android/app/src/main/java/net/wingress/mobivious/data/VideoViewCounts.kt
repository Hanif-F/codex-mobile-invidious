package net.wingress.mobivious.data

import java.util.Locale
import org.json.JSONObject

enum class ViewCountType(val wire: String) {
    VIEWS("views"), WATCHING("watching");
    companion object { fun parse(value: String) = entries.firstOrNull { it.wire == value } }
}

/** Counts are presentation metadata supplied by the response, never a reason to fetch a video. */
object VideoViewCounts {
    private val legacy = Regex("^((?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?\\s*[KMBT]?)(?:\\s+(views?|watching))?$", RegexOption.IGNORE_CASE)

    private fun missing(json: JSONObject): Boolean {
        val rawType = json.text("viewCountType")
        return rawType.isNotBlank() && ViewCountType.parse(rawType) == null ||
            json.text("viewCountPrecision") == "unknown" && ViewCountType.parse(rawType) == null
    }

    fun value(json: JSONObject): Long? = json.nonNegativeLong("viewCount").takeUnless {
        missing(json) || json.optBoolean("liveNow") && ViewCountType.parse(json.text("viewCountType")) == null
    }

    fun text(json: JSONObject): String = if (missing(json)) "" else (json.opt("viewCountText") as? String)?.trim().orEmpty()

    fun label(video: Video, exact: Boolean = false, locale: Locale = Locale.getDefault()): String {
        if (video.viewCountPrecision == CountPrecision.UNKNOWN && video.viewCountType == null) return ""
        val value = video.views?.takeIf { it >= 0 && (!video.live || video.viewCountType != null) }
        if (exact && video.viewCountPrecision != CountPrecision.EXACT) return ""
        if (value != null) {
            val watching = video.viewCountType == ViewCountType.WATCHING
            return if (exact) DisplayFormats.inventory(value, if (watching) "watching" else "view", if (watching) "watching" else "views", locale)
                else DisplayFormats.audience(value, if (watching) "watching" else "view", if (watching) "watching" else "views", locale)
        }
        if (exact) return ""
        val text = video.viewCountText.trim()
        if (text.equals("No views", ignoreCase = true)) return text.takeUnless { video.viewCountType == ViewCountType.WATCHING }.orEmpty()
        val match = legacy.matchEntire(text) ?: return ""
        val noun = match.groupValues[2].lowercase(Locale.ROOT)
        if (noun.isNotEmpty()) {
            val type = if (noun == "watching") ViewCountType.WATCHING else ViewCountType.VIEWS
            return text.takeIf { video.viewCountType == null || video.viewCountType == type }.orEmpty()
        }
        // Old recommendations used bare "0" for missing counts. Bare live counts have no reliable unit.
        val number = match.groupValues[1].trim().trimEnd('K', 'M', 'B', 'T', 'k', 'm', 'b', 't').trim()
            .replace(",", "").toBigDecimalOrNull()
        if (number?.signum() != 1 || video.live && video.viewCountType == null) return ""
        val single = text.replace(",", "").toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ONE) == 0
        return "$text ${if (video.viewCountType == ViewCountType.WATCHING) "watching" else if (single) "view" else "views"}"
    }

    /** Preserve count metadata across existing clip and MediaSession handoffs, without new storage. */
    fun write(video: Video, json: JSONObject): JSONObject = json.apply {
        video.views?.let { put("viewCount", it) }
        if (video.viewCountPrecision != CountPrecision.UNVERIFIED) put("viewCountPrecision", video.viewCountPrecision.name.lowercase(Locale.ROOT))
        video.viewCountType?.let { put("viewCountType", it.wire) }
        if (video.viewCountText.isNotBlank()) put("viewCountText", video.viewCountText)
    }
}
