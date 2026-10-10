package net.wingress.mobivious.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class SubscriptionSort(val key: String, val label: String) {
    ALPHABETICAL("alphabetical", "A–Z"), LATEST("latest", "Latest upload"),
    MOST_WATCHED("most_watched", "Most watched"), RELEVANCE("relevance", "Relevance");

    companion object {
        fun saved(key: String?) = entries.find { it.key == key } ?: RELEVANCE
    }
}

data class SubscriptionStats(val latestUpload: Long?, val allTimeWatched: Int, val recentWatched: Int, val relevance: Double) {
    companion object {
        fun parse(json: JSONObject): SubscriptionStats {
            fun integer(name: String, max: Long): Long {
                val number = json.get(name) as? Number ?: error("Invalid $name")
                val value = number.toDouble()
                require(value.isFinite() && value >= 0 && value < Long.MAX_VALUE.toDouble() && value <= max.toDouble() && value == number.toLong().toDouble())
                return number.toLong()
            }
            val latest = if (json.has("latestUpload") && json.isNull("latestUpload")) null else integer("latestUpload", Long.MAX_VALUE / 1000)
            val total = integer("allTimeWatched", Int.MAX_VALUE.toLong()).toInt()
            val recent = integer("recentWatched", total.toLong()).toInt()
            val relevance = (json.get("relevance") as? Number)?.toDouble() ?: error("Invalid relevance")
            require(relevance.isFinite() && relevance >= 0)
            return SubscriptionStats(latest, total, recent, relevance)
        }
    }
}

data class SubscriptionDirectory(val channels: List<Channel>, val stats: Map<String, SubscriptionStats> = emptyMap(),
    val unavailableReason: String? = null) {
    companion object {
        const val LEGACY = "This instance doesn't provide channel sorting statistics. Showing A–Z."
        const val HISTORY_PERMISSION = "Your token needs history access for channel sorting. Showing A–Z."
        fun parse(body: String): SubscriptionDirectory {
            try {
                val rows = JSONArray(body).objects()
                val channels = rows.map(ApiParser::channel).distinctBy { it.id }
                val stats = rows.mapNotNull { row ->
                    if (!row.has("subscriptionStats")) null else row.getString("authorId") to SubscriptionStats.parse(row.getJSONObject("subscriptionStats"))
                }.toMap()
                val missing = channels.any { it.id !in stats }
                return SubscriptionDirectory(channels, if (missing) emptyMap() else stats, if (missing) LEGACY else null)
            } catch (e: Exception) {
                throw IllegalArgumentException("The instance returned invalid channel sorting statistics. Try again.", e)
            }
        }
    }
}

data class SubscriptionChannelsState(val context: ApiContext? = null, val channels: List<Channel> = emptyList(),
    val loaded: Boolean = false, val loading: Boolean = false, val error: String? = null, val query: String = "",
    val stats: Map<String, SubscriptionStats> = emptyMap(), val sort: SubscriptionSort = SubscriptionSort.RELEVANCE,
    val unavailableReason: String? = null, val busy: Set<String> = emptySet(),
    val actionErrors: Map<String, String> = emptyMap()) {
    val effectiveSort: SubscriptionSort get() = if (unavailableReason == null) sort else SubscriptionSort.ALPHABETICAL
    val matches: List<Channel>
        get() {
            val name = compareBy<Channel> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id }
            val comparator = when (effectiveSort) {
                SubscriptionSort.ALPHABETICAL -> name
                SubscriptionSort.LATEST -> compareByDescending<Channel> { stats[it.id]?.latestUpload ?: Long.MIN_VALUE }.then(name)
                SubscriptionSort.MOST_WATCHED -> compareByDescending<Channel> { stats[it.id]?.allTimeWatched ?: 0 }.then(name)
                SubscriptionSort.RELEVANCE -> compareByDescending<Channel> { stats[it.id]?.relevance ?: 0.0 }.then(name)
            }
            return channels.filter { it.name.contains(query.trim(), ignoreCase = true) }.sortedWith(comparator)
        }
}

/** Session-only channel directory. Reads cannot replace newer results or cross account/instance changes. */
class SubscriptionsController(private val scope: CoroutineScope, private val context: () -> ApiContext,
    private val fetch: suspend (ApiContext) -> SubscriptionDirectory, private val errorMessage: (Exception) -> String,
    private val readSort: (ApiContext) -> SubscriptionSort = { SubscriptionSort.RELEVANCE },
    private val saveSort: (ApiContext, SubscriptionSort) -> Unit = { _, _ -> },
    private val write: suspend (String, Boolean, ApiContext) -> Unit = { _, _, _ -> }) {
    private val mutableState = MutableStateFlow(SubscriptionChannelsState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    private val writes = mutableMapOf<String, Job>()

    fun reset() {
        val current = context()
        if (mutableState.value.context == current) return
        job?.cancel(); writes.values.forEach { it.cancel() }; writes.clear(); revision++
        mutableState.value = SubscriptionChannelsState(context = current, loaded = current.account == null, sort = readSort(current))
    }

    fun search(query: String) {
        reset()
        mutableState.value = mutableState.value.copy(query = query)
    }

    fun sort(value: SubscriptionSort) {
        reset()
        if (mutableState.value.unavailableReason != null && value != SubscriptionSort.ALPHABETICAL) return
        saveSort(context(), value)
        mutableState.value = mutableState.value.copy(sort = value)
    }

    fun refresh() {
        reset()
        val current = context()
        if (current.account == null) return
        job?.cancel()
        val ticket = ++revision
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val directory = fetch(current)
                if (current == context() && ticket == revision) mutableState.value = mutableState.value.copy(
                    channels = directory.channels.distinctBy { it.id }, stats = directory.stats, unavailableReason = directory.unavailableReason,
                    loaded = true, loading = false, error = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current == context() && ticket == revision) mutableState.value = mutableState.value.copy(
                    loading = false, error = errorMessage(e))
            }
        }
    }

    /** Membership changes only after confirmation; repeated taps cannot send a second write. */
    fun toggle(channel: Channel) {
        reset()
        val current = context()
        val state = mutableState.value
        if (current.account == null || channel.id in state.busy) return
        if (!state.loaded) { refresh(); return }
        val subscribe = state.channels.none { it.id == channel.id }
        mutableState.value = state.copy(busy = state.busy + channel.id, actionErrors = state.actionErrors - channel.id)
        writes[channel.id] = scope.launch {
            try {
                write(channel.id, subscribe, current)
                if (current != context()) return@launch
                // A read started before this write must never replace its confirmed result.
                job?.cancel(); revision++
                val latest = mutableState.value
                mutableState.value = latest.copy(channels = if (subscribe) latest.channels.filterNot { it.id == channel.id } + channel
                    else latest.channels.filterNot { it.id == channel.id }, loading = false)
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current == context()) mutableState.value = mutableState.value.copy(
                    actionErrors = mutableState.value.actionErrors + (channel.id to errorMessage(e)))
            } finally {
                if (current == context()) mutableState.value = mutableState.value.copy(busy = mutableState.value.busy - channel.id)
            }
        }
    }
}
