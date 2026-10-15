package net.wingress.mobivious.data

import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SearchAssistanceSettings(val remember: Boolean = true, val suggestions: Boolean = true) {
    fun json() = JSONObject().put("remember", remember).put("suggestions", suggestions)
    companion object {
        fun parse(raw: String?) = runCatching {
            val json = JSONObject(raw ?: "{}")
            SearchAssistanceSettings(json.opt("remember") as? Boolean ?: true, json.opt("suggestions") as? Boolean ?: true)
        }.getOrDefault(SearchAssistanceSettings())
    }
}

interface SearchAssistanceStore {
    fun searchAssistance(context: ApiContext): SearchAssistanceSettings
    fun saveSearchAssistance(context: ApiContext, value: SearchAssistanceSettings)
    fun recentSearches(context: ApiContext): List<String>
    fun saveRecentSearches(context: ApiContext, value: List<String>)
}

object RecentSearches {
    fun urlLike(value: String) = value.trim().let {
        it.contains(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")) || it.startsWith("www.", true) ||
            it.startsWith("youtu.be/", true) || it.startsWith("youtube.com/", true) || it.startsWith("m.youtube.com/", true)
    }
    fun normalize(values: List<String>) = values.map(String::trim).filter { it.isNotBlank() && !urlLike(it) }
        .distinctBy { it.lowercase(Locale.ROOT) }.take(20)
    fun parse(raw: String?) = runCatching {
        val array = JSONArray(raw ?: "[]")
        normalize((0 until array.length()).mapNotNull { array.opt(it) as? String })
    }.getOrDefault(emptyList())
}

enum class SearchEntryMode { NEW, EDIT }
data class SearchSubmission(val text: String, val mode: SearchEntryMode)
data class SearchEntryState(
    val open: Boolean = false, val mode: SearchEntryMode = SearchEntryMode.NEW, val draft: String = "",
    val session: Long = 0, val recent: List<String> = emptyList(), val suggestions: List<String> = emptyList(),
    val loading: Boolean = false, val failed: Boolean = false,
    val settings: SearchAssistanceSettings = SearchAssistanceSettings()
) {
    val matchingRecent get() = if (!settings.remember) emptyList() else recent.filter { it.contains(draft.trim(), true) }
}

/** Editor state never changes loaded results. Every async response belongs to one draft and profile. */
class SearchEntryController(
    private val scope: CoroutineScope,
    private val context: () -> ApiContext,
    private val store: SearchAssistanceStore,
    private val region: () -> String,
    private val lookup: suspend (String, String, ApiContext) -> List<String>,
    private val reportError: (String) -> Unit
) {
    val state = MutableStateFlow(SearchEntryState())
    private var owner: ApiContext? = null
    private var revision = 0L
    private var job: Job? = null

    fun bind(value: ApiContext) {
        job?.cancel(); revision++; owner = value
        state.value = SearchEntryState(session = revision, settings = store.searchAssistance(value), recent = store.recentSearches(value))
    }
    fun open(mode: SearchEntryMode, query: String = "") {
        if (owner != context()) bind(context())
        job?.cancel(); revision++
        state.value = state.value.copy(open = true, mode = mode, draft = if (mode == SearchEntryMode.NEW) "" else query,
            session = revision, suggestions = emptyList(), loading = false, failed = false)
        update(state.value.draft)
    }
    fun cancel() {
        job?.cancel(); revision++
        state.value = state.value.copy(open = false, draft = "", suggestions = emptyList(), loading = false, failed = false)
    }
    fun update(text: String) {
        job?.cancel(); val ticket = ++revision
        state.value = state.value.copy(draft = text, suggestions = emptyList(), loading = false, failed = false)
        val query = text.trim(); val origin = owner; val selectedRegion = region()
        if (!state.value.open || !state.value.settings.suggestions || query.isBlank() || RecentSearches.urlLike(query)) return
        job = scope.launch {
            delay(250)
            if (ticket != revision || origin != context()) return@launch
            state.value = state.value.copy(loading = true)
            try {
                val values = lookup(query, selectedRegion, origin).map(String::trim).filter(String::isNotBlank)
                    .distinctBy { it.lowercase(Locale.ROOT) }.take(8)
                if (ticket == revision && origin == context() && selectedRegion == region())
                    state.value = state.value.copy(suggestions = values, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (ticket == revision && origin == context()) state.value = state.value.copy(loading = false, failed = true)
            }
        }
    }
    fun submit(): SearchSubmission? {
        val current = state.value
        if (!current.open || current.draft.isBlank() || owner != context()) return null
        val submission = SearchSubmission(current.draft.trim(), current.mode)
        cancel()
        return submission
    }
    private fun write(action: (ApiContext) -> Unit): Boolean = try {
        val origin = owner
        if (origin == null || origin != context()) false else { action(origin); true }
    } catch (_: CancellationException) { false }
    catch (_: Exception) { reportError("Could not save search data on this device. Please try again."); false }

    fun remember(text: String) {
        if (!state.value.settings.remember || RecentSearches.urlLike(text)) return
        val values = RecentSearches.normalize(listOf(text) + state.value.recent)
        if (write { store.saveRecentSearches(it, values) }) state.value = state.value.copy(recent = values)
    }
    fun remove(text: String) {
        val values = state.value.recent.filterNot { it.equals(text, true) }
        if (write { store.saveRecentSearches(it, values) }) state.value = state.value.copy(recent = values)
    }
    fun clear() {
        if (write { store.saveRecentSearches(it, emptyList()) }) state.value = state.value.copy(recent = emptyList())
    }
    fun settings(value: SearchAssistanceSettings) {
        if (write { store.saveSearchAssistance(it, value) }) {
            state.value = state.value.copy(settings = value)
            update(state.value.draft)
        }
    }
}
