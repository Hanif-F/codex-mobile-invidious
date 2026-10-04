package net.wingress.mobivious.data

import org.json.JSONObject

data class Registration(val loginEnabled: Boolean, val enabled: Boolean, val captchaImage: String = "", val captchaToken: String = "") {
    companion object {
        fun parse(json: JSONObject): Registration {
            val captcha = json.optJSONObject("captcha")
            return Registration(json.optBoolean("loginEnabled", true), json.optBoolean("registrationEnabled"),
                captcha?.text("image").orEmpty(), captcha?.text("token").orEmpty())
        }
    }
}

data class AccountSession(val id: String, val type: String, val issuedAt: Long, val expiresAt: Long?, val current: Boolean) {
    companion object {
        fun parse(json: JSONObject) = AccountSession(json.getString("id"), json.getString("type"), json.getLong("issuedAt"),
            if (json.isNull("expiresAt")) null else json.getLong("expiresAt"), json.getBoolean("current"))
    }
}

/** Readable groups are translated into the same scopes accepted by the web API. */
object AccountPermissions {
    val groups = linkedMapOf(
        "Read preferences" to listOf("GET:preferences"),
        "Change preferences" to listOf("PATCH:preferences"),
        "Read subscriptions and feed" to listOf("GET:feed", "GET:subscriptions", "GET:subscriptions/search"),
        "Manage subscriptions" to listOf("POST;DELETE:subscriptions/*"),
        "Read history and progress" to listOf("GET:history", "GET:playback", "GET:playback/*"),
        "Manage history and progress" to listOf("POST;DELETE:history/*", "DELETE:history", "PUT;DELETE:playback/*", "DELETE:playback"),
        "Read playlists" to listOf("GET:playlists", "GET:playlists/*"),
        "Manage playlists" to listOf("POST:playlists", "PATCH;DELETE:playlists/*", "POST:playlists/*", "PUT;DELETE:saved_playlists/*"),
        "Manage blocked channels" to listOf("GET:blocked_channels", "POST;DELETE:blocked_channels/*"),
        "RSS and subscription exports" to listOf("GET:feed/rss", "GET:subscriptions/export"),
        "DeArrow contributions" to listOf("GET;POST:dearrow/*", "PUT:dearrow/identity"),
        "Manage API tokens" to listOf("GET:tokens", "POST:tokens/register", "POST:tokens/unregister"),
        "Read notifications" to listOf("GET:notifications")
    )
    val expiries = linkedMapOf("1 day" to 1L, "7 days" to 7L, "30 days" to 30L, "1 year" to 365L, "Never" to 0L)
    fun scopes(selected: Set<String>, advanced: String): List<String> =
        (selected.flatMap { groups[it].orEmpty() } + advanced.split(Regex("[\\s,]+"))).filter { it.isNotBlank() }.distinct()
    fun valid(scopes: List<String>) = scopes.size in 1..64 && scopes.all {
        it.length <= 256 && it.matches(Regex("(?:(?:GET|POST|PUT|HEAD|DELETE|PATCH|OPTIONS)(?:;(?:GET|POST|PUT|HEAD|DELETE|PATCH|OPTIONS))*)?:[a-z0-9_.*\\/-]+"))
    }
}
