package net.wingress.mobivious.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object DeArrowRules {
    fun validTitle(value: String): Boolean = value.trim().let { it.isNotEmpty() && it.codePointCount(0, it.length) <= 110 && '\n' !in it && '\r' !in it }
    fun validPrivateId(value: String) = value.trim().isEmpty() || Regex("^[A-Za-z0-9_-]{30,256}$").matches(value.trim())
    fun canDownvote(item: DeArrowSubmission?) = item != null && !item.locked
}

/** Screen-lifetime lookups: the instance owns ranking/trust rules and longer-lived caching. */
class DeArrowTitles(private val scope: CoroutineScope, private val server: () -> String,
    private val fetch: suspend (String) -> String?) {
    private val lock = Any()
    private val slots = Semaphore(4)
    private var serial = 0L
    private val pending = mutableMapOf<String, Pair<Long, Job>>()
    private val values = MutableStateFlow<Map<String, String?>>(emptyMap())
    val titles = values.asStateFlow()
    fun ensure(id: String) {
        if (!Regex("^[A-Za-z0-9_-]{11}$").matches(id)) return
        val job = synchronized(lock) {
            if (id in values.value || id in pending) return
            val instance = server()
            val ticket = ++serial
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val title = slots.withPermit { fetch(id) }?.trim()?.takeIf { it.isNotEmpty() }
                    synchronized(lock) { if (pending[id]?.first == ticket && server() == instance) values.value = values.value + (id to title) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { synchronized(lock) { if (pending[id]?.first == ticket && server() == instance) values.value = values.value + (id to null) } }
                finally { synchronized(lock) { if (pending[id]?.first == ticket) pending.remove(id) } }
            }.also { pending[id] = ticket to it }
        }
        job.start()
    }
    fun invalidate(id: String) = synchronized(lock) {
        pending.remove(id)?.second?.cancel()
        values.value = values.value - id
    }
    fun clear() = synchronized(lock) {
        pending.values.forEach { it.second.cancel() }
        pending.clear()
        values.value = emptyMap()
    }
}
