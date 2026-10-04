package net.wingress.mobivious.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionChannelsState(val context: ApiContext? = null, val channels: List<Channel> = emptyList(),
    val loaded: Boolean = false, val loading: Boolean = false, val error: String? = null, val query: String = "") {
    val matches: List<Channel>
        get() = channels.filter { it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { channel: Channel -> channel.name }.thenBy { it.id })
}

/** Session-only channel directory. Reads cannot replace newer results or cross account/instance changes. */
class SubscriptionsController(private val scope: CoroutineScope, private val context: () -> ApiContext,
    private val fetch: suspend (ApiContext) -> List<Channel>, private val errorMessage: (Exception) -> String) {
    private val mutableState = MutableStateFlow(SubscriptionChannelsState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var revision = 0L

    fun reset() {
        val current = context()
        if (mutableState.value.context == current) return
        job?.cancel(); revision++
        mutableState.value = SubscriptionChannelsState(context = current, loaded = current.account == null)
    }

    fun search(query: String) {
        reset()
        mutableState.value = mutableState.value.copy(query = query)
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
                val channels = fetch(current)
                if (current == context() && ticket == revision) mutableState.value = mutableState.value.copy(
                    channels = channels.distinctBy { it.id }, loaded = true, loading = false, error = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current == context() && ticket == revision) mutableState.value = mutableState.value.copy(
                    loading = false, error = errorMessage(e))
            }
        }
    }
}
