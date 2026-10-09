package net.wingress.mobivious.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

enum class AiListKind(val wire: String, val label: String) {
    BLOCKLIST("blocklist", "High confidence — Blocklist"), WARNLIST("warnlist", "Moderate confidence — Warnlist")
}
enum class AiPageGroup(val wire: String, val label: String) {
    FEEDS("feeds", "Popular and Trending"), SEARCH("search", "Search and hashtags"),
    RECOMMENDATIONS("recommendations", "Watch recommendations"), OTHER("other_pages", "Library, channels and queues");
    companion object {
        fun browsing(tab: String, route: String): AiPageGroup = when {
            route.startsWith("hashtag:") -> SEARCH
            route.isNotEmpty() -> OTHER
            tab == "Search" -> SEARCH
            PreferenceRules.isDiscovery(tab) -> FEEDS
            else -> OTHER
        }
    }
}
enum class AiAction(val wire: String, val label: String) {
    OFF("off", "Off"), HIDE("hide", "Hide videos"), REPLACE("replace_thumbnail", "Replace thumbnails");
    companion object {
        fun parse(value: Any?, group: AiPageGroup) = entries.firstOrNull { it.wire == value && (group != AiPageGroup.OTHER || it != HIDE) } ?: OFF
    }
}
data class AiFilterSettings(val enabled: Boolean = false, val actions: Map<AiPageGroup, Map<AiListKind, AiAction>> = AiPageGroup.entries.associateWith { AiListKind.entries.associateWith { AiAction.OFF } }) {
    fun saved(kind: AiListKind, group: AiPageGroup) = actions[group]?.get(kind) ?: AiAction.OFF
    fun active(group: AiPageGroup) = if (!enabled) emptySet() else AiListKind.entries.filter { saved(it, group) != AiAction.OFF }.toSet()
    fun withAction(kind: AiListKind, group: AiPageGroup, action: AiAction) = copy(actions = actions +
        (group to (actions[group].orEmpty() + (kind to AiAction.parse(action.wire, group)))))
    fun json() = JSONObject().put("ai_filter_enabled", enabled).apply {
        AiPageGroup.entries.forEach { group -> AiListKind.entries.forEach { kind -> put(key(kind, group), saved(kind, group).wire) } }
    }
    companion object {
        fun key(kind: AiListKind, group: AiPageGroup) = "ai_${kind.wire}_${group.wire}_action"
        fun parse(j: JSONObject): AiFilterSettings {
            val actions = AiPageGroup.entries.associateWith { group -> AiListKind.entries.associateWith { kind ->
                val key = key(kind, group)
                if (j.has(key)) AiAction.parse(j.opt(key), group)
                else if (j.opt("ai_${kind.wire}_${group.wire}") == true) {
                    if (group == AiPageGroup.OTHER) AiAction.REPLACE
                    else AiAction.entries.firstOrNull { it != AiAction.OFF && it.wire == j.opt("ai_${kind.wire}_action") } ?: AiAction.HIDE
                } else AiAction.OFF
            } }
            val enabled = j.opt("ai_filter_enabled") as? Boolean ?: actions.values.any { values -> values.values.any { it != AiAction.OFF } }
            return AiFilterSettings(enabled, actions)
        }
    }
}
data class AiDecision(val hidden: Boolean = false, val warning: AiListKind? = null)
object AiFilter {
    fun decide(settings: AiFilterSettings, group: AiPageGroup, matches: Set<AiListKind>): AiDecision {
        val active = settings.active(group).intersect(matches)
        if (group != AiPageGroup.OTHER && active.any { settings.saved(it, group) == AiAction.HIDE }) return AiDecision(hidden = true)
        return AiDecision(warning = AiListKind.entries.firstOrNull { it in active && settings.saved(it, group) == AiAction.REPLACE })
    }
}
data class AiListStatus(val available: Boolean, val stale: Boolean, val channelCount: Int, val updatedAt: String?)
data class AiChannelMatch(val matches: Set<AiListKind>, val resolved: Boolean)
data class AiResponse(val lists: Map<AiListKind, AiListStatus>, val channels: Map<String, AiChannelMatch> = emptyMap()) {
    companion object {
        fun parse(j: JSONObject): AiResponse {
            val lists = j.getJSONObject("lists")
            return AiResponse(AiListKind.entries.associateWith { kind ->
                val status = lists.getJSONObject(kind.wire)
                AiListStatus(status.getBoolean("available"), status.getBoolean("stale"), status.getInt("channelCount"), status.opt("updatedAt") as? String)
            }, j.optJSONObject("channels")?.let { channels -> channels.keys().asSequence().filter(ContentVisibility::validChannel).associateWith { id ->
                val channel = channels.getJSONObject(id)
                val matches = channel.getJSONArray("matches")
                AiChannelMatch(AiListKind.entries.filter { kind -> (0 until matches.length()).any { matches.opt(it) == kind.wire } }.toSet(), channel.getBoolean("resolved"))
            } }.orEmpty())
        }
    }
}
data class AiFilterState(val context: ApiContext? = null, val matches: Map<String, Set<AiListKind>> = emptyMap(),
    val lists: Map<AiListKind, AiListStatus> = emptyMap(), val loading: Boolean = false, val error: String? = null)

/** Shared by browsing and the playback service. Requests outlive a caller's bounded wait. */
class AiFilterRepository(private val scope: CoroutineScope, private val context: () -> ApiContext,
    private val fetchStatus: suspend (ApiContext) -> AiResponse,
    private val fetchChannels: suspend (List<String>, Set<AiListKind>, ApiContext) -> AiResponse,
    private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val matched: Boolean, val resolved: Boolean, val expires: Long)
    private val lock = Any()
    private val slots = Semaphore(4)
    private val cache = linkedMapOf<Pair<String, AiListKind>, Entry>()
    private val pending = mutableMapOf<Pair<String, AiListKind>, Job>()
    private val retries = mutableSetOf<Job>()
    private val revisions = mutableMapOf<AiListKind, Long>()
    private var serial = 0L
    private var epoch = 0L
    private var settings = AiFilterSettings()
    private val mutableState = MutableStateFlow(AiFilterState())
    val state = mutableState.asStateFlow()

    fun reset(value: ApiContext = context(), force: Boolean = false) = synchronized(lock) {
        if (!force && mutableState.value.context == value) return@synchronized
        epoch++; pending.values.toSet().forEach { it.cancel() }; retries.toList().forEach { it.cancel() }
        pending.clear(); retries.clear(); cache.clear(); revisions.clear()
        mutableState.value = AiFilterState(context = value)
    }
    fun configure(value: AiFilterSettings) = synchronized(lock) {
        reset()
        if (settings == value) return@synchronized
        settings = value
        retries.toList().forEach { it.cancel() }; retries.clear()
        if (!value.enabled) { epoch++; pending.values.toSet().forEach { it.cancel() }; pending.clear(); mutableState.value = mutableState.value.copy(loading = false) }
    }
    private fun valid(owner: ApiContext, version: Long) = owner == context() && mutableState.value.context == owner && epoch == version
    private fun publish() {
        val matches = cache.entries.filter { it.value.matched }.groupBy({ it.key.first }, { it.key.second }).mapValues { it.value.toSet() }
        mutableState.value = mutableState.value.copy(matches = matches)
    }
    private fun accept(response: AiResponse, ids: List<String>, kinds: Set<AiListKind>, revision: Long) {
        val accepted = mutableSetOf<AiListKind>()
        val statuses = mutableState.value.lists.toMutableMap()
        response.lists.forEach { (kind, status) ->
            val old = statuses[kind]
            if (old?.updatedAt != status.updatedAt && revision < (revisions[kind] ?: 0)) return@forEach
            if (old != null && old.updatedAt != status.updatedAt) cache.keys.removeAll { it.second == kind }
            statuses[kind] = status; revisions[kind] = maxOf(revisions[kind] ?: 0, revision); accepted += kind
        }
        ids.forEach { id -> response.channels[id]?.let { result -> kinds.intersect(accepted).forEach { kind ->
            // Partial matches are useful, but a missing match is never cached as confirmed clean.
            if (response.lists[kind]?.available == true) cache[id to kind] = Entry(kind in result.matches, result.resolved, clock() + 300_000)
        } } }
        val overflow = cache.keys.map { it.first }.distinct().dropLast(10_000).toSet()
        cache.keys.removeAll { it.first in overflow }
        mutableState.value = mutableState.value.copy(lists = statuses, error = null)
        publish()
    }
    suspend fun refreshStatus() {
        val owner: ApiContext; val version: Long; val revision: Long
        synchronized(lock) { reset(); owner = context(); version = epoch; revision = ++serial; mutableState.value = mutableState.value.copy(loading = true) }
        try {
            val response = fetchStatus(owner)
            synchronized(lock) { if (valid(owner, version)) accept(response, emptyList(), emptySet(), revision) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { synchronized(lock) { if (valid(owner, version)) mutableState.value = mutableState.value.copy(error = error(e)) } }
        finally { synchronized(lock) { if (valid(owner, version)) mutableState.value = mutableState.value.copy(loading = false) } }
    }
    private fun error(e: Exception) = if (e is ApiException && e.status in listOf(404, 405)) "Update this server to enable the AI channel filter." else e.message ?: "AI list data could not be loaded. Unresolved videos remain visible."

    fun ensure(ids: List<String>, kinds: Set<AiListKind>, retry: Boolean = true): List<Job> = synchronized(lock) {
        reset()
        if (!settings.enabled || kinds.isEmpty()) return@synchronized emptyList()
        val wanted = ids.filter(ContentVisibility::validChannel).distinct()
        val missing = wanted.associateWith { id -> kinds.filter { kind -> (id to kind) !in pending && cache[id to kind]?.let { !it.resolved || it.expires <= clock() } != false }.toSet() }
        missing.entries.filter { it.value.isNotEmpty() }.groupBy({ it.value }, { it.key }).forEach { (requested, channels) -> channels.chunked(100).forEach { batch ->
            val owner = context(); val version = epoch; val revision = ++serial
            lateinit var task: Job
            task = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val response = slots.withPermit { fetchChannels(batch, requested, owner) }
                    synchronized(lock) {
                        if (!valid(owner, version)) return@synchronized
                        accept(response, batch, requested, revision)
                        val unresolved = batch.filter { response.channels[it]?.resolved == false }
                        if (retry && unresolved.isNotEmpty() && requested.all { response.lists[it]?.available == true }) {
                            lateinit var later: Job
                            later = scope.launch(start = CoroutineStart.LAZY) {
                                try { delay(5_000); synchronized(lock) { if (valid(owner, version) && settings.enabled) ensure(unresolved, requested, retry = false) } }
                                finally { synchronized(lock) { retries.remove(later) } }
                            }
                            retries += later; later.start()
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { synchronized(lock) { if (valid(owner, version)) mutableState.value = mutableState.value.copy(error = error(e)) } }
                finally { synchronized(lock) { batch.forEach { id -> requested.forEach { kind -> if (pending[id to kind] === task) pending.remove(id to kind) } } } }
            }
            batch.forEach { id -> requested.forEach { kind -> pending[id to kind] = task } }; task.start()
        } }
        wanted.flatMap { id -> kinds.mapNotNull { pending[id to it] } }.distinct()
    }
    suspend fun prepare(ids: List<String>, value: AiFilterSettings, group: AiPageGroup) {
        configure(value)
        val kinds = value.active(group)
        val jobs = ensure(ids, kinds)
        withTimeoutOrNull(2_000) { jobs.joinAll() }
    }
}
