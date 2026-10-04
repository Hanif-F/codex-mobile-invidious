package net.wingress.mobivious.data

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class AccountPreferences(
    val watchHistory: Boolean = true, val savePosition: Boolean = false,
    val dearrowEnabled: Boolean = false, val dearrowShowOriginal: Boolean = true,
    val sponsorBlock: SponsorBlockSettings = SponsorBlockSettings(),
    val autoplay: Boolean = true, val listen: Boolean = false, val local: Boolean = true,
    val continueNext: Boolean = false, val continueAutoplay: Boolean = true, val videoLoop: Boolean = false,
    val speed: Float = 1f, val qualityDash: String = "auto", val videoCodec: String = "auto", val captions: List<String> = listOf("", "", ""),
    val darkMode: String = "", val uiDensity: String = "balanced", val thinMode: Boolean = false,
    val defaultHome: String = "Popular", val feedMenu: List<String> = listOf("Popular", "Trending", "Subscriptions", "Playlists"),
    val region: String = "US", val relatedVideos: Boolean = true, val extendDescription: Boolean = false,
    val comments: List<String> = listOf("youtube", ""), val maxResults: Int = 40,
    val feedSort: String = "published", val latestOnly: Boolean = false, val unseenOnly: Boolean = false,
    val notificationsOnly: Boolean = false, val defaultPlaylist: String = "", val showMemberVideos: Boolean = false
) {
    fun json() = sponsorBlock.json().apply {
        put("watch_history", watchHistory); put("save_player_pos", savePosition)
        put("dearrow_enabled", dearrowEnabled); put("dearrow_show_original", dearrowShowOriginal)
        put("autoplay", autoplay); put("listen", listen); put("local", local)
        put("continue", continueNext); put("continue_autoplay", continueAutoplay); put("video_loop", videoLoop)
        put("speed", speed); put("quality_dash", qualityDash); put("video_codec", PreferenceRules.videoCodec(videoCodec)); put("captions", JSONArray(captions))
        put("dark_mode", darkMode); put("ui_density", uiDensity); put("thin_mode", thinMode)
        put("default_home", defaultHome); put("feed_menu", JSONArray(feedMenu)); put("region", region)
        put("related_videos", relatedVideos); put("extend_desc", extendDescription); put("comments", JSONArray(comments))
        put("max_results", maxResults); put("sort", feedSort); put("latest_only", latestOnly)
        put("unseen_only", unseenOnly); put("notifications_only", notificationsOnly)
        put("default_playlist", defaultPlaylist.ifBlank { null } ?: JSONObject.NULL)
        put("show_member_videos", showMemberVideos)
    }
    fun changesFrom(before: AccountPreferences) = JSONObject().apply {
        val old = before.json(); val current = json()
        current.keys().forEach { key ->
            if (!key.startsWith("sponsorblock_") && current.get(key).toString() != old.get(key).toString()) put(key, current.get(key))
        }
        sponsorBlock.patch(before.sponsorBlock, this)
    }
    fun merge(changes: JSONObject): AccountPreferences {
        val current = json()
        changes.keys().forEach { key ->
            if (key in listOf("sponsorblock_modes", "sponsorblock_colors")) {
                val map = current.getJSONObject(key); val delta = changes.getJSONObject(key)
                delta.keys().forEach { map.put(it, delta.get(it)) }
            } else current.put(key, changes.get(key))
        }
        return parse(current)
    }
    val maxHeight: Int get() = when (qualityDash) { "worst" -> 144; else -> qualityDash.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE }
    val showYoutubeComments: Boolean get() = "youtube" in comments

    companion object {
        fun parse(j: JSONObject): AccountPreferences {
            fun strings(key: String, fallback: List<String>): List<String> = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it, "") } } ?: fallback
            val defaults = AccountPreferences()
            val rawSpeed = j.optDouble("speed", 1.0).toFloat()
            return AccountPreferences(
                watchHistory = j.optBoolean("watch_history", true), savePosition = j.optBoolean("save_player_pos", false),
                dearrowEnabled = j.optBoolean("dearrow_enabled", false), dearrowShowOriginal = j.optBoolean("dearrow_show_original", true), sponsorBlock = SponsorBlockSettings.parse(j),
                autoplay = j.optBoolean("autoplay", true), listen = j.optBoolean("listen", false), local = j.optBoolean("local", true),
                continueNext = j.optBoolean("continue", false), continueAutoplay = j.optBoolean("continue_autoplay", true), videoLoop = j.optBoolean("video_loop", false),
                speed = rawSpeed.takeIf { it.isFinite() && it in .25f..2f } ?: 1f, qualityDash = j.text("quality_dash", "auto"), videoCodec = PreferenceRules.videoCodec(j.opt("video_codec")),
                captions = strings("captions", defaults.captions), darkMode = j.text("dark_mode").takeIf { it in listOf("", "light", "dark") } ?: "",
                uiDensity = j.text("ui_density", "balanced").takeIf { it == "compact" } ?: "balanced", thinMode = j.optBoolean("thin_mode"),
                defaultHome = j.text("default_home", "Popular"), feedMenu = strings("feed_menu", defaults.feedMenu), region = j.text("region", "US"),
                relatedVideos = j.optBoolean("related_videos", true), extendDescription = j.optBoolean("extend_desc"), comments = strings("comments", defaults.comments),
                maxResults = j.optInt("max_results", 40).coerceIn(0, 1500), feedSort = j.text("sort", "published"),
                latestOnly = j.optBoolean("latest_only"), unseenOnly = j.optBoolean("unseen_only"), notificationsOnly = j.optBoolean("notifications_only"), defaultPlaylist = j.text("default_playlist"), showMemberVideos = j.optBoolean("show_member_videos"))
        }
    }
}

object PreferenceRules {
    val videoCodecs = listOf("auto" to "Auto", "av1" to "AV1", "h264" to "H.264")
    fun videoCodec(value: Any?) = (value as? String)?.takeIf { codec -> videoCodecs.any { it.first == codec } } ?: "auto"
    val homes = listOf("", "Popular", "Trending", "Subscriptions", "Playlists")
    val qualities = listOf("auto", "best", "4320p", "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p", "worst")
    fun qualityLabel(value: String) = when (value) { "auto" -> "Auto"; "best" -> "Best"; "worst" -> "Worst"; else -> value }
    val speeds = listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val feedSorts = listOf("published", "published - reverse", "alphabetically", "alphabetically - reverse", "channel name", "channel name - reverse")
    fun destination(home: String, signedIn: Boolean): Pair<String, String> = when (home) {
        "" -> "Search" to "popular"
        "Trending" -> "Home" to "trending"
        "Subscriptions" -> (if (signedIn) "Subscriptions" else "Home") to "popular"
        "Playlists" -> (if (signedIn) "Library" else "Home") to "popular"
        else -> "Home" to "popular"
    }
    fun navigation(@Suppress("UNUSED_PARAMETER") menu: List<String>): List<String> = listOf("Home", "Subscriptions", "Library", "Account")
    // The web stores English language names; also accept tags from older/native preferences.
    fun caption(preferred: List<String>, available: List<Caption>): Caption? = preferred.asSequence().filter { it.isNotBlank() }.mapNotNull { choice ->
        available.firstOrNull { c -> c.language.equals(choice, true) || c.label.equals(choice, true) ||
            Locale.forLanguageTag(c.language).getDisplayLanguage(Locale.ENGLISH).equals(choice, true) ||
            Locale.forLanguageTag(c.language).getDisplayName(Locale.ENGLISH).equals(choice, true) }
    }.firstOrNull()
}
