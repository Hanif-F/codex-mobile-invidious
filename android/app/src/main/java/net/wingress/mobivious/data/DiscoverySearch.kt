package net.wingress.mobivious.data

import java.util.Locale
import org.json.JSONArray

enum class TrendingCategory(val apiValue: String, val label: String) {
    LIVESTREAMS("livestreams", "Livestreams"), GAMING("gaming", "Gaming");
    companion object {
        fun saved(value: String?) = entries.firstOrNull { it.apiValue == value } ?: LIVESTREAMS
    }
}

/** Matches the web fork's I18n::CONTENT_REGIONS; names follow the device locale. */
object ContentRegions {
    val codes = ("AE AR AT AU AZ BA BD BE BG BH BO BR BY CA CH CL CO CR CY CZ DE DK DO DZ EC EE " +
        "EG ES FI FR GB GE GH GR GT HK HN HR HU ID IE IL IN IQ IS IT JM JO JP KE KR KW KZ LB LI LK LT LU LV LY MA ME MK MT MX " +
        "MY NG NI NL NO NP NZ OM PA PE PG PH PK PL PR PT PY QA RO RS RU SA SE SG SI SK SN SV TH TN TR TW TZ UA UG US UY VE VN YE ZA ZW").split(' ')
    fun name(code: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }.getOrDefault(code).ifBlank { code }
    fun label(code: String, locale: Locale = Locale.getDefault()) = "${name(code, locale)} ($code)"
    fun choices(query: String, locale: Locale = Locale.getDefault()): List<String> = codes
        .filter { it.contains(query.trim(), true) || name(it, locale).contains(query.trim(), true) }
        .sortedBy { name(it, locale) }
}

enum class SearchType(val apiValue: String, val label: String) {
    ALL("all", "All"), VIDEOS("video", "Videos"), CHANNELS("channel", "Channels"), PLAYLISTS("playlist", "Playlists & mixes");
    val videoFilters get() = this == ALL || this == VIDEOS
}

sealed interface SearchResult {
    val key: String
    val channelId: String
    data class VideoItem(val video: Video) : SearchResult {
        override val key get() = "video:${video.id}"
        override val channelId get() = video.channelId
    }
    data class ChannelItem(val channel: Channel) : SearchResult {
        override val key get() = "channel:${channel.id}"
        override val channelId get() = channel.id
    }
    data class PlaylistItem(val playlist: Playlist) : SearchResult {
        override val key get() = "playlist:${playlist.id}"
        override val channelId get() = playlist.channelId
    }
}

data class GeneralSearchPage(val items: List<SearchResult>, val sourceKeys: Set<String>) {
    fun exhausted(previous: Set<String>) = sourceKeys.isEmpty() || sourceKeys.all { it in previous }
    companion object {
        fun parse(array: JSONArray, type: SearchType = SearchType.ALL): GeneralSearchPage {
            val keys = mutableSetOf<String>()
            for (index in 0 until array.length()) if (array.optJSONObject(index) == null) keys += "unknown:${array.opt(index)}"
            val results = array.objects().mapNotNull { json ->
                // Older instances/fixtures may omit type; never interpret an explicit unknown type as a video.
                val kind = json.text("type").ifBlank { when {
                    json.text("videoId").isNotBlank() -> "video"
                    json.text("playlistId", json.text("mixId")).isNotBlank() -> "playlist"
                    json.text("authorId").isNotBlank() -> "channel"
                    else -> "unknown"
                } }
                val result = when (kind) {
                    "video" -> json.text("videoId").takeIf { it.isNotBlank() }?.let { SearchResult.VideoItem(ApiParser.video(json)) }
                    "channel" -> json.text("authorId").takeIf(ContentVisibility::validChannel)?.let { SearchResult.ChannelItem(ApiParser.channel(json)) }
                    "playlist", "mix" -> json.text("playlistId", json.text("mixId")).takeIf { it.isNotBlank() }?.let { SearchResult.PlaylistItem(ApiParser.playlist(json)) }
                    else -> null
                }
                keys += result?.key ?: "unknown:$json"
                result?.takeIf { type == SearchType.ALL || when (it) {
                    is SearchResult.VideoItem -> type == SearchType.VIDEOS
                    is SearchResult.ChannelItem -> type == SearchType.CHANNELS
                    is SearchResult.PlaylistItem -> type == SearchType.PLAYLISTS
                } }
            }.distinctBy { it.key }
            return GeneralSearchPage(results, keys)
        }
    }
}

object SearchResults {
    fun merge(old: List<SearchResult>, next: List<SearchResult>) = (old + next).distinctBy { it.key }
    fun visible(items: List<SearchResult>, showMembers: Boolean, visibility: SearchVisibility, blocked: Set<String>) =
        items.filter { (visibility.includeBlocked || it.channelId !in blocked) &&
            (it !is SearchResult.VideoItem || (visibility.showMembers ?: showMembers) || !it.video.membersOnly) }
}
