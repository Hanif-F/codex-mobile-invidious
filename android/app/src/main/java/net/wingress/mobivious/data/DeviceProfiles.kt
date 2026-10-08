package net.wingress.mobivious.data

import java.io.IOException
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Persistent ownership is independent of bearer tokens and request generations. */
data class DeviceProfile(val server: String, val owner: String) {
    val prefix: String get() = "device.v2." + MessageDigest.getInstance("SHA-256")
        .digest(JSONArray().put(server).put(owner).toString().toByteArray()).joinToString("") { "%02x".format(it) } + "."
    companion object {
        fun validId(value: String?) = value?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        fun server(value: String) = value.trim().trimEnd('/').toHttpUrl().toString().trimEnd('/')
        fun of(context: ApiContext) = DeviceProfile(server(context.server), context.account?.let {
            validId(it.profileId)?.let { id -> "account:$id" } ?: "legacy:${it.username}"
        } ?: "guest")
    }
}

/** A checked, atomic edit; implementations must leave their readable state intact on failure. */
interface ProfilePersistence {
    fun values(): Map<String, Any?>
    fun edit(values: Map<String, Any>, remove: Set<String> = emptySet()): Boolean
}

class DeviceProfiles(private val persistence: ProfilePersistence) {
    fun key(context: ApiContext, name: String) = DeviceProfile.of(context).prefix + name
    fun value(context: ApiContext, name: String): Any? = persistence.values()[key(context, name)]
    @Synchronized fun write(context: ApiContext, name: String, value: Any?) {
        checked(if (value == null) emptyMap() else mapOf(key(context, name) to value), if (value == null) setOf(key(context, name)) else emptySet())
    }
    private fun checked(values: Map<String, Any>, remove: Set<String> = emptySet()) {
        if (!persistence.edit(values, remove)) throw IOException("Could not save data on this device. Please try again.")
    }
    @Synchronized fun transfer(from: ApiContext, to: ApiContext, extra: Map<String, Any> = emptyMap()) {
        val old = DeviceProfile.of(from).prefix; val next = DeviceProfile.of(to).prefix
        val all = persistence.values(); val values = extra.toMutableMap(); val remove = mutableSetOf<String>()
        if (old != next) all.forEach { (key, value) -> if (key.startsWith(old)) {
            val target = next + key.removePrefix(old)
            if (!all.containsKey(target) && value != null) values[target] = value
            remove.add(key)
        } }
        checked(values, remove)
    }
    @Synchronized fun purge(context: ApiContext) {
        val prefix = DeviceProfile.of(context).prefix
        checked(emptyMap(), persistence.values().keys.filter { it.startsWith(prefix) }.toSet())
    }
    /** Unowned saves become guest data; already-owned saves retain their legacy owner. */
    @Synchronized fun migrate(server: String, savedAccount: Account?) {
        val all = persistence.values()
        if (all["device.profiles.version"] == 2) return
        val values = mutableMapOf<String, Any>("device.profiles.version" to 2)
        val remove = mutableSetOf<String>()
        val guest = ApiContext(server, null)
        fun put(context: ApiContext, name: String, value: Any?) {
            val key = key(context, name)
            if (!all.containsKey(key) && !values.containsKey(key) && value != null) values[key] = value
        }
        fun json(raw: Any?) = (raw as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
        fun owner(raw: String): ApiContext? = runCatching {
            val a = JSONArray(raw); val address = a.getString(0)
            DeviceProfile.server(address)
            ApiContext(address, if (a.isNull(1)) null else Account("", a.getString(1), Long.MAX_VALUE, address))
        }.getOrNull()
        for (name in listOf("background", "pip")) { put(guest, name, all[name] as? Boolean); if (name in all) remove.add(name) }
        // Consolidate the obsolete scalar defaults into the selected instance's guest preferences.
        fun legacy(name: String): Any? = all["$name.$server"] ?: all.entries.firstOrNull { (key, _) ->
            key.startsWith("$name.") && runCatching { DeviceProfile.server(key.removePrefix("$name.")) == DeviceProfile.server(server) }.getOrDefault(false)
        }?.value
        val base = json(legacy("preferences")) ?: JSONObject().apply {
            (all["speed"] as? Number)?.takeIf { it.toDouble().isFinite() }?.let { put("speed", it) }
            (all["height"] as? Int)?.takeIf { it != Int.MAX_VALUE }?.let { put("quality_dash", "${it}p") }
            (all["region"] as? String)?.let { put("region", it) }
            (legacy("dearrow.enabled") as? Boolean)?.let { put("dearrow_enabled", it) }
            (legacy("dearrow.original") as? Boolean)?.let { put("dearrow_show_original", it) }
            json(legacy("sponsorblock"))?.let { sb -> sb.keys().forEach { put(it, sb.get(it)) } }
        }
        put(guest, "preferences", LocalPreferences.read(base).json().toString())
        listOf("speed", "height", "region").filter { it in all }.forEach(remove::add)
        all.forEach { (name, raw) ->
            when {
                name.startsWith("preferences.") -> {
                    runCatching { ApiContext(name.removePrefix("preferences."), null) }.getOrNull()?.let { ctx ->
                        runCatching { put(ctx, "preferences", json(raw)?.let { LocalPreferences.read(it).json().toString() }) }
                    }; remove.add(name)
                }
                name.startsWith("chat.appearance.") -> {
                    runCatching { put(ApiContext(name.removePrefix("chat.appearance."), null), "chat.appearance", json(raw)?.let { appearance(it).json().toString() }) }; remove.add(name)
                }
                name.startsWith("subscriptions.sort.") -> {
                    runCatching { put(ApiContext(name.removePrefix("subscriptions.sort."), null), "subscriptions.sort", (raw as? String)?.takeIf { v -> SubscriptionSort.entries.any { it.key == v } }) }; remove.add(name)
                }
                name.startsWith("chat.timing.") -> {
                    val suffix = name.removePrefix("chat.timing."); val address = suffix.substringBeforeLast('.'); val id = suffix.substringAfterLast('.')
                    if (SponsorBlockRules.validVideo(id)) runCatching { put(ApiContext(address, null), "chat.timing.$id", (raw as? Int)?.takeIf { it in -3_600_000..3_600_000 }) }
                    remove.add(name)
                }
                name.startsWith("playback.positions.") -> {
                    owner(name.removePrefix("playback.positions."))?.let { put(it, "positions", JSONObject(positions(json(raw) ?: JSONObject())).toString()) }; remove.add(name)
                }
                name.startsWith("visibility.search.") -> {
                    owner(name.removePrefix("visibility.search."))?.let { ctx -> put(ctx, "search", json(raw)?.let { SearchVisibility.parse(it).json().toString() }) }; remove.add(name)
                }
                name.startsWith("visibility.blocks.") -> {
                    owner(name.removePrefix("visibility.blocks."))?.let { ctx -> put(ctx, "blocks", (raw as? String)?.let { runCatching { JSONArray(BlockedChannel.parse(JSONArray(it)).map { b -> b.json() }).toString() }.getOrNull() }) }; remove.add(name)
                }
            }
        }
        // Old standalone SponsorBlock/DeArrow saves on other instances also stay guests.
        all.keys.filter { it.startsWith("sponsorblock.") || it.startsWith("dearrow.enabled.") || it.startsWith("dearrow.original.") }.forEach { name ->
            val address = name.removePrefix("sponsorblock.").removePrefix("dearrow.enabled.").removePrefix("dearrow.original.")
            runCatching {
                val ctx = ApiContext(address, null)
                if (key(ctx, "preferences") !in values && key(ctx, "preferences") !in all) {
                    val j = json(all["sponsorblock.$address"]) ?: JSONObject()
                    (all["dearrow.enabled.$address"] as? Boolean)?.let { j.put("dearrow_enabled", it) }
                    (all["dearrow.original.$address"] as? Boolean)?.let { j.put("dearrow_show_original", it) }
                    put(ctx, "preferences", LocalPreferences.read(j).json().toString())
                }
            }; remove.add(name)
        }
        val oldPositions = all.filterKeys { it.startsWith("position.") }
        // A corrupt encrypted session cannot establish ownership of old unscoped progress.
        // Keep that source untouched instead of exposing possibly personal progress to guests.
        if (oldPositions.isNotEmpty() && (savedAccount != null || "session" !in all)) {
            val ctx = ApiContext(savedAccount?.server ?: server, savedAccount)
            val target = key(ctx, "positions")
            val j = json(all[target] ?: values[target]) ?: JSONObject()
            oldPositions.forEach { (key, value) ->
                val id = key.removePrefix("position.")
                if (!j.has(id) && SponsorBlockRules.validVideo(id) && value is Long && value in 1..Int.MAX_VALUE.toLong()) j.put(id, value)
            }
            values[target] = j.toString(); remove.addAll(oldPositions.keys)
        }
        checked(values, remove)
    }
    companion object {
        fun appearance(j: JSONObject) = ChatAppearance.parse(LocalPreferences.typed(j, ChatAppearance().json()))
        fun positions(j: JSONObject): Map<String, Long> = j.keys().asSequence().mapNotNull { id ->
            val n = j.opt(id) as? Number
            if (SponsorBlockRules.validVideo(id) && n != null && n.toDouble().isFinite() && n.toDouble() == n.toLong().toDouble() && n.toLong() in 1..Int.MAX_VALUE.toLong()) id to n.toLong() else null
        }.toMap()
    }
}

/** Guest saves contain only supported local settings, with strict primitive types. */
object LocalPreferences {
    fun typed(raw: JSONObject, defaults: JSONObject): JSONObject {
        val clean = JSONObject()
        defaults.keys().forEach { name ->
            val value = raw.opt(name); val expected = defaults.opt(name)
            if (value is Boolean && expected is Boolean || value is String && expected is String ||
                value is Number && expected is Number && value.toDouble().isFinite() && (expected !is Int || value.toDouble() == value.toLong().toDouble()) ||
                value is JSONArray && expected is JSONArray && (0 until value.length()).all { value.opt(it) is String } || value is JSONObject && expected is JSONObject)
                clean.put(name, value)
        }
        return clean
    }
    fun read(raw: JSONObject): AccountPreferences {
        val clean = typed(raw, AccountPreferences().json())
        val p = AccountPreferences.parse(clean)
        val allowedHomes = listOf("", "Popular", "Trending")
        return p.copy(watchHistory = false, defaultPlaylist = "", latestOnly = false, unseenOnly = false, notificationsOnly = false,
            maxResults = p.maxResults.takeIf { it in 1..1500 } ?: 40,
            feedSort = p.feedSort.takeIf { it in PreferenceRules.feedSorts } ?: "published",
            qualityDash = p.qualityDash.takeIf { it in PreferenceRules.qualities } ?: "auto",
            region = p.region.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US",
            defaultHome = p.defaultHome.takeIf { it in allowedHomes } ?: "Popular",
            feedMenu = p.feedMenu.filter { it in allowedHomes }.take(4).ifEmpty { listOf("Popular", "Trending") },
            captions = p.captions.take(3).map { it.take(128) }.let { it + List(3 - it.size) { "" } },
            comments = p.comments.filter { it in listOf("", "youtube", "reddit") }.take(2),
            chat = p.chat.copy(users = p.chat.users.takeIf { runCatching { ChatFilters.validate(p.chat.copy(words = "")) }.isSuccess } ?: "",
                words = p.chat.words.takeIf { runCatching { ChatFilters.validate(p.chat.copy(users = "")) }.isSuccess } ?: ""),
            sponsorBlock = p.sponsorBlock.copy(channels = p.sponsorBlock.channels.entries.take(1000).associate { (id, v) -> id to v.copy(name = v.name.take(200)) }))
    }
    fun normalize(value: AccountPreferences) = read(value.json())
}
