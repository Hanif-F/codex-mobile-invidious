package net.wingress.mobivious

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchAssistanceTest {
    private val guest = ApiContext("https://example.test", null)
    private class Memory : SearchAssistanceStore {
        val recents = mutableMapOf<DeviceProfile, List<String>>()
        val options = mutableMapOf<DeviceProfile, SearchAssistanceSettings>()
        var fail = false
        override fun searchAssistance(context: ApiContext) = options[DeviceProfile.of(context)] ?: SearchAssistanceSettings()
        override fun recentSearches(context: ApiContext) = recents[DeviceProfile.of(context)].orEmpty()
        override fun saveSearchAssistance(context: ApiContext, value: SearchAssistanceSettings) {
            check(!fail); options[DeviceProfile.of(context)] = value
        }
        override fun saveRecentSearches(context: ApiContext, value: List<String>) { check(!fail); recents[DeviceProfile.of(context)] = value }
    }
    @Test fun recentsTrimBoundDeduplicateAndExcludeLinks() {
        val values = RecentSearches.normalize(listOf(" Cafe ", "CAFE", "https://youtu.be/abcdefghijk", "youtube.com/watch?v=x") + (1..25).map { "query $it" })
        assertEquals(20, values.size); assertEquals("Cafe", values.first()); assertEquals("query 19", values.last())
        assertEquals(listOf("safe"), RecentSearches.parse("""[4,null,"safe","SAFE","www.youtube.com"]"""))
        assertTrue(RecentSearches.parse("broken").isEmpty())
        assertEquals(SearchAssistanceSettings(), SearchAssistanceSettings.parse("""{"remember":"false"}"""))
    }
    @Test fun entryCancelSubmitAndProfilePersistenceAreIndependent() = runTest {
        var owner = guest; val memory = Memory()
        val controller = SearchEntryController(this, { owner }, memory, { "ID" }, { _, _, _ -> emptyList() }, { fail(it) })
        controller.bind(owner); controller.remember("first"); controller.remember("second"); controller.remember("FIRST")
        controller.open(SearchEntryMode.NEW); assertEquals("", controller.state.value.draft)
        controller.update("draft"); controller.cancel(); assertNull(controller.submit())
        controller.open(SearchEntryMode.EDIT, "second"); assertEquals("second", controller.state.value.draft)
        assertEquals(SearchSubmission("second", SearchEntryMode.EDIT), controller.submit()); assertNull(controller.submit())
        controller.settings(SearchAssistanceSettings(remember = false)); controller.remember("ignored")
        assertTrue(controller.state.value.matchingRecent.isEmpty())
        controller.settings(SearchAssistanceSettings()); assertEquals(listOf("FIRST", "second"), controller.state.value.recent)
        owner = guest.copy(account = Account("token", "Alice", Long.MAX_VALUE, guest.server, "a".repeat(64)))
        controller.bind(owner); assertTrue(controller.state.value.recent.isEmpty()); controller.remember("alice")
        owner = guest.copy(generation = 4); controller.bind(owner)
        assertEquals(listOf("FIRST", "second"), controller.state.value.recent)
        controller.remove("first"); assertEquals(listOf("second"), controller.state.value.recent)
        controller.clear(); assertTrue(memory.recentSearches(owner).isEmpty())
    }
    @Test fun debounceCancellationAndOlderResponsesCannotReplaceTheDraft() = runTest {
        val calls = mutableListOf<String>(); var owner = guest
        val controller = SearchEntryController(this, { owner }, Memory(), { "ID" }, { q, region, _ ->
            calls += "$q:$region"
            // Simulates even an uncooperative transport that finishes after cancellation.
            withContext(NonCancellable) { delay(if (q == "old") 1000 else 10) }
            listOf(q, q, "")
        }, { fail(it) })
        controller.bind(owner); controller.open(SearchEntryMode.NEW)
        controller.update("o"); advanceTimeBy(200); controller.update("old"); advanceTimeBy(251)
        assertEquals(listOf("old:ID"), calls)
        controller.update("new"); advanceTimeBy(300)
        assertEquals(listOf("new"), controller.state.value.suggestions)
        advanceUntilIdle(); assertEquals("new", controller.state.value.draft); assertEquals(listOf("new"), controller.state.value.suggestions)
        controller.update("late"); advanceTimeBy(251); owner = guest.copy(generation = 1); controller.bind(owner)
        advanceUntilIdle(); assertFalse(controller.state.value.open); assertTrue(controller.state.value.suggestions.isEmpty())
        controller.open(SearchEntryMode.NEW); controller.update("https://youtu.be/abcdefghijk"); advanceUntilIdle()
        controller.update(""); advanceUntilIdle(); assertEquals(3, calls.size)
        controller.cancel()
    }
    @Test fun failuresAndOptOutNeverPreventManualSubmission() = runTest {
        val memory = Memory(); val errors = mutableListOf<String>(); var calls = 0
        val controller = SearchEntryController(this, { guest }, memory, { "US" }, { _, _, _ -> calls++; error("offline") }, errors::add)
        controller.bind(guest); controller.open(SearchEntryMode.NEW); controller.update("manual"); advanceUntilIdle()
        assertTrue(controller.state.value.failed); assertFalse(controller.state.value.loading)
        memory.fail = true; controller.remember("manual"); assertEquals(1, errors.size)
        assertEquals("manual", controller.submit()!!.text)
        memory.fail = false; controller.settings(SearchAssistanceSettings(suggestions = false))
        controller.open(SearchEntryMode.NEW); controller.update("offline"); advanceUntilIdle(); assertEquals(1, calls)
        assertEquals("offline", controller.submit()!!.text)
    }
    @Test fun suggestionsUseTheInstanceRegionAndNoAuthorization() = runBlocking {
        MockWebServer().use { server ->
            val address = server.url("/").toString()
            val api = InvidiousApi({ address }, { Account("secret", "Viewer", Long.MAX_VALUE, address) })
            server.enqueue(MockResponse().setBody("""{"query":"café & games","suggestions":["first",4,"second","first",""]}"""))
            assertEquals(listOf("first", "second"), api.searchSuggestions("café & games", "ID"))
            val request = server.takeRequest()
            assertEquals("/api/v1/search/suggestions", request.requestUrl!!.encodedPath)
            assertEquals("café & games", request.requestUrl!!.queryParameter("q")); assertEquals("ID", request.requestUrl!!.queryParameter("region"))
            assertNull(request.getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(404))
            try { api.searchSuggestions("query", "US"); fail() } catch (e: ApiException) { assertEquals(404, e.status) }
        }
    }
}
