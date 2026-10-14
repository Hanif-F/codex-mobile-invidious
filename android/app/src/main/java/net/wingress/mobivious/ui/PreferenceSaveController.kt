package net.wingress.mobivious.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PreferenceSaveStatus { DEBOUNCING, PENDING, FAILED, INVALID }
internal data class PreferenceEdit<C>(
    val context: C, val key: String, val page: String, val confirmed: String,
    val requested: String, val revision: Long, val status: PreferenceSaveStatus,
    val error: String? = null
)

/**
 * ViewModel-owned intents. Only the sink acquires the shared preference-write mutex.
 * Calls and state publication are confined to the ViewModel dispatcher.
 */
internal class PreferenceSaveController<C : Any>(
    private val scope: CoroutineScope,
    private val sink: suspend (PreferenceEdit<C>) -> Unit,
    private val debounceMillis: Long = 500
) {
    private val mutable = MutableStateFlow<Map<String, PreferenceEdit<C>>>(emptyMap())
    val edits = mutable.asStateFlow()
    private var context: C? = null
    private var generation = 0L
    private var revision = 0L
    private var worker: Job? = null
    private val timers = mutableMapOf<String, Job>()
    private var wake = Channel<Unit>(Channel.CONFLATED)

    fun bind(value: C) {
        if (context == value) return
        generation++; context = value
        worker?.cancel(); timers.values.forEach(Job::cancel); timers.clear()
        wake.close(); wake = Channel(Channel.CONFLATED); mutable.value = emptyMap()
        val boundGeneration = generation
        val signals = wake
        worker = scope.launch {
            for (signal in signals) {
                while (boundGeneration == generation) {
                    val edit = mutable.value.values.firstOrNull { it.status == PreferenceSaveStatus.PENDING } ?: break
                    try {
                        sink(edit)
                        if (boundGeneration != generation) break
                        val latest = mutable.value[edit.key]
                        if (latest?.revision == edit.revision) mutable.value = mutable.value - edit.key
                        else if (latest != null) publish(latest.copy(confirmed = edit.requested))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (boundGeneration != generation) break
                        val latest = mutable.value[edit.key]
                        if (latest?.revision == edit.revision) publish(latest.copy(
                            status = PreferenceSaveStatus.FAILED, error = e.message ?: "Could not save. Try again."))
                    }
                }
            }
        }
    }
    private fun publish(edit: PreferenceEdit<C>) { mutable.value = mutable.value + (edit.key to edit) }

    fun request(key: String, page: String, confirmed: String, requested: String,
        validationError: String? = null, debounce: Boolean = false) {
        val bound = checkNotNull(context) { "Bind the preference context before editing" }
        timers.remove(key)?.cancel()
        val old = mutable.value[key]
        // An unchanged request must still supersede an in-flight older value. The sink
        // may be committing that value; issue the newer intent rather than dropping it.
        if (old == null && requested == confirmed && validationError == null) return
        val edit = PreferenceEdit(bound,key,page,old?.confirmed ?: confirmed,requested,++revision,
            when { validationError != null -> PreferenceSaveStatus.INVALID
                debounce -> PreferenceSaveStatus.DEBOUNCING
                else -> PreferenceSaveStatus.PENDING },validationError)
        publish(edit)
        if (edit.status == PreferenceSaveStatus.DEBOUNCING) {
            val boundGeneration = generation
            timers[key] = scope.launch {
                delay(debounceMillis)
                if (generation == boundGeneration && mutable.value[key]?.revision == edit.revision) flush(key)
            }
        } else if (edit.status == PreferenceSaveStatus.PENDING) wake.trySend(Unit)
    }
    fun flush(key: String? = null) {
        mutable.value.values.filter { (key == null || it.key == key) && it.status == PreferenceSaveStatus.DEBOUNCING }
            .forEach { edit -> timers.remove(edit.key)?.cancel(); publish(edit.copy(status = PreferenceSaveStatus.PENDING)) }
        wake.trySend(Unit)
    }
    fun retry(key: String) {
        val edit = mutable.value[key] ?: return
        if (edit.status != PreferenceSaveStatus.FAILED) return
        publish(edit.copy(revision=++revision,status=PreferenceSaveStatus.PENDING,error=null)); wake.trySend(Unit)
    }
    fun revert(key: String) {
        val edit = mutable.value[key] ?: return
        if (edit.status !in listOf(PreferenceSaveStatus.FAILED, PreferenceSaveStatus.INVALID, PreferenceSaveStatus.DEBOUNCING)) return
        timers.remove(key)?.cancel(); mutable.value = mutable.value - key
    }
    fun hasUnconfirmed() = mutable.value.isNotEmpty()
    fun discard() { val current=context ?: return;context=null;bind(current) }
    fun discardKeys(predicate: (String) -> Boolean) {
        mutable.value.keys.filter(predicate).forEach { key -> timers.remove(key)?.cancel();mutable.value=mutable.value-key }
    }
}
