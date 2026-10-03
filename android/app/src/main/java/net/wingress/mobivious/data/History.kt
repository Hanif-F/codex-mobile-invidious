package net.wingress.mobivious.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

data class HistoryMetadata(val released: LocalDate?, val watched: LocalDate?)
data class HistoryPage(val entries: List<Video>, val total: Int? = null, val hasMore: Boolean,
    val today: LocalDate? = null, val timezone: String = "UTC", val organized: Boolean = true)
enum class HistoryGroup(val label: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), WEEK("Last 7 days"), MONTH("Last 30 days"), OLDER("Older")
}

object History {
    fun date(value: String): LocalDate? = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(value))
        runCatching { LocalDate.parse(value) }.getOrNull() else null
    fun group(watched: LocalDate?, today: LocalDate?): HistoryGroup = when {
        watched == null || today == null -> HistoryGroup.OLDER
        else -> when (ChronoUnit.DAYS.between(watched, today)) {
            0L -> HistoryGroup.TODAY
            1L -> HistoryGroup.YESTERDAY
            in 2L..6L -> HistoryGroup.WEEK
            in 7L..29L -> HistoryGroup.MONTH
            else -> HistoryGroup.OLDER
        }
    }
    private fun entry(json: JSONObject): Video = ApiParser.video(json).copy(published = "",
        title = json.text("title").ifBlank { "Unavailable video" }, unavailable = json.text("title").isBlank(),
        author = json.text("channel_name").ifBlank { json.text("channel_id").ifBlank { "Unknown channel" } },
        history = HistoryMetadata(date(json.text("release_date")), date(json.text("latest_watched"))))
    fun parse(body: String): HistoryPage {
        if (body.trimStart().startsWith("[")) {
            val raw = JSONArray(body)
            val entries = (0 until raw.length()).mapNotNull { i -> when (val item = raw.opt(i)) {
                is JSONObject -> entry(item).takeIf { it.id.isNotBlank() }
                is String -> entry(JSONObject().put("video_id", item)).takeIf { it.id.isNotBlank() }
                else -> null
            } }
            return HistoryPage(entries, hasMore = entries.isNotEmpty(), organized = false)
        }
        val json = JSONObject(body)
        val today = date(json.getString("today")) ?: error("Invalid history calendar date")
        return HistoryPage(json.getJSONArray("entries").objects().map(::entry), json.getInt("total"),
            json.getBoolean("hasMore"), today, json.getString("timezone"))
    }
}

data class SearchInput(val draft: String = "", val submitted: String = "")
data class SearchPage(val items: List<Video>, val hasMore: Boolean) {
    companion object {
        fun parse(body: String): SearchPage {
            val raw = JSONArray(body)
            return SearchPage(ApiParser.videos(raw), raw.length() >= 20)
        }
    }
}
