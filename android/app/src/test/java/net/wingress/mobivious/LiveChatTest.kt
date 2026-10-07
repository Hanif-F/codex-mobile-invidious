package net.wingress.mobivious

import kotlinx.coroutines.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LiveChatTest {
    private val ctx = ApiContext("https://example.test", null)
    private fun message(id: String, offset: Long = 0, text: String = id) = ChatMessage(id, offset, "Viewer", "UCviewer", "@viewer", text)
    private class Fixture(val initial: ApiContext = ApiContext("https://example.test", null)) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var context = initial; var clock = 0L; var offset = 0; var failure = false
        val calls = mutableListOf<Pair<Long, String>>()
        val saves = mutableListOf<Int>(); var reads = 0
        var fetch: suspend (Long, String) -> ChatChunk = { _, _ -> ChatChunk(emptyList()) }
        var read: suspend () -> Int = { reads++; offset }
        var write: suspend (Int) -> Unit = { if (failure) throw IllegalStateException("Save failed"); saves.add(it) }
        val controller = ChatReplayController(scope, { context }, { _, at, token, _ -> calls.add(at to token); fetch(at, token) },
            { _, _ -> read() }, { _, value, _ -> write(value) }, { it.message.orEmpty() }, { clock })
        fun open(position: Long = 0) { controller.bind("abcdefghijk", "one", true); controller.update(position, 120_000); controller.present(true); controller.open() }
        fun tick(position: Long = 0) { clock += 1_000; controller.update(position, 120_000) }
        override fun close() = scope.cancel()
    }
    @Test fun availabilityIsOptionalAndExcludesLiveAndUpcoming() {
        fun details(extra: String) = ApiParser.details(JSONObject("""{"videoId":"abcdefghijk",$extra}"""))
        assertFalse(details("\"title\":\"Video\"").chatAvailable)
        assertTrue(details("\"liveChatReplay\":true").chatAvailable)
        assertFalse(details("\"liveChatReplay\":true,\"liveNow\":true").chatAvailable)
        assertFalse(details("\"liveChatReplay\":true,\"isUpcoming\":true").chatAvailable)
        assertFalse(details("\"liveChatReplay\":\"true\"").chatAvailable)
    }
    @Test fun chunkParsingValidatesEnvelopeAndRetainsKindsAndUnicode() {
        val chunk = ChatChunk.parse(JSONObject("""{"messages":[{"id":"a","offsetMs":1500,"text":"Hello 日本語 😀","author":"Viewer","authorChannelId":"UCviewer","authorHandle":"@viewer","kind":"paid","amount":"$5"},{"id":"bad","offsetMs":-1,"text":"Bad"},{"id":"fraction","offsetMs":1.5,"text":"Bad"},{"id":"wrong","offsetMs":"1","text":"Bad"}],"removedIds":["gone",42],"continuation":"+/&%="}"""))
        assertEquals(1, chunk.messages.size); assertEquals("paid", chunk.messages.single().kind)
        assertEquals("$5", chunk.messages.single().amount); assertEquals("Hello 日本語 😀", chunk.messages.single().text)
        assertEquals(listOf("gone"), chunk.removedIds); assertEquals("+/&%=", chunk.continuation)
        assertTrue(runCatching { ChatChunk.parse(JSONObject("{}")) }.isFailure)
        assertTrue(runCatching { ChatChunk.parse(JSONObject("""{"messages":[],"removedIds":[],"continuation":12}""")) }.isFailure)
    }
    @Test fun appearanceBoundsInvalidGeometryAndPreservesNativeDefaults() {
        val a = ChatAppearance.parse(JSONObject("""{"x":9,"y":-1,"width":2,"height":-1,"fontScale":999,"opacity":-9,"belowFraction":9,"besideFraction":0}"""))
        assertEquals(0f, a.x); assertEquals(0f, a.y); assertEquals(1f, a.width); assertEquals(.1f, a.height)
        assertEquals(300, a.fontScale); assertEquals(0, a.opacity); assertEquals(.7f, a.belowFraction); assertEquals(.1f, a.besideFraction)
        assertEquals(ChatAppearance(), ChatAppearance.parse(JSONObject()))
        assertEquals(ChatAppearance(), ChatAppearance.parse(ChatAppearance().json()))
        assertTrue(ChatAppearance(x = Float.NaN).bounded().x.isFinite())
        val full = ChatAppearance(x = Float.NaN, y = Float.NaN, width = 1f, height = 1f).bounded()
        assertEquals(0f, full.x); assertEquals(0f, full.y)
    }
    @Test fun besideWidthAllowsTenPercentAndSurvivesStorageWithoutChangingOtherDefaults() {
        for (width in listOf(.1f, .15f, .25f, .35f, .7f)) {
            val value = ChatAppearance(besideFraction = width)
            assertEquals(value, value.bounded())
            assertEquals(value, ChatAppearance.parse(value.json()))
        }
        assertEquals(.1f, ChatAppearance(besideFraction = -.5f).bounded().besideFraction)
        assertEquals(.7f, ChatAppearance(besideFraction = 2f).bounded().besideFraction)
        for (width in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(.35f, ChatAppearance(besideFraction = width).bounded().besideFraction)
        }
        assertEquals(.6f, ChatAppearance().belowFraction)
        assertEquals(.3f, ChatAppearance(belowFraction = .1f).bounded().belowFraction)
    }
    @Test fun channelIdsAreHiddenForNewSettingsAndExplicitOlderChoicesArePreserved() {
        assertTrue(ChatAppearance().hideUserIds)
        assertTrue(ChatAppearance.parse(JSONObject()).hideUserIds)
        assertTrue(ChatAppearance.parse(JSONObject("""{"fontScale":150}""")).hideUserIds)
        for (hidden in listOf(false, true)) {
            assertEquals(hidden, ChatAppearance.parse(JSONObject().put("hideUserIds", hidden)).hideUserIds)
            val value = ChatAppearance(hideUserIds = hidden, besideFraction = .15f)
            assertEquals(value, ChatAppearance.parse(value.json()))
        }
    }
    @Test fun filtersMatchIdsHandlesWordsAndSafeRegexCaseInsensitively() {
        assertFalse(ChatFilters(ChatPreferences(users = "ucVIEWER")).accepts(message("a")))
        assertFalse(ChatFilters(ChatPreferences(users = "VIEWER")).accepts(message("a")))
        assertFalse(ChatFilters(ChatPreferences(users = "@VIEWER")).accepts(message("a")))
        assertTrue(ChatFilters(ChatPreferences(users = "other")).accepts(message("a")))
        assertFalse(ChatFilters(ChatPreferences(users = "other\u00a0@VIEWER")).accepts(message("a")))
        assertFalse(ChatFilters(ChatPreferences(words = "spam\u3000noise")).accepts(message("a", text = "SPAM HERE")))
        val filters = ChatFilters(ChatPreferences(words = "spam /h[ae]llo/"))
        assertFalse(filters.accepts(message("a", text = "SPAM HERE")))
        assertFalse(filters.accepts(message("a", text = "Hallo friend")))
        assertTrue(filters.accepts(message("a", text = "Good day")))
        assertTrue(ChatFilters(ChatPreferences(words = "/(?=bad)/ valid")).invalid.isNotEmpty())
        assertTrue(runCatching { ChatFilters.validate(ChatPreferences(words = "/(a)\\1/")) }.isFailure)
        assertTrue(runCatching { ChatFilters.validate(ChatPreferences(users = "あ".repeat(400))) }.isFailure)
        assertTrue(runCatching { ChatFilters.validate(ChatPreferences(words = "a".repeat(129))) }.isFailure)
        val pathological = ChatFilters(ChatPreferences(words = "/(a+)+$/"))
        assertTrue(pathological.accepts(message("a", text = "a".repeat(10000) + "!")))
    }
    @Test fun importedUnsupportedPatternsAreSkippedAndDoNotRewritePreferences() {
        Fixture().use { f ->
            val p = ChatPreferences(words = "/(?=hello)/ spam")
            f.controller.configure(p); f.fetch = { _, _ -> ChatChunk(listOf(message("a", text = "Hello"), message("b", text = "spam"))) }
            f.open(); assertEquals(listOf("a"), f.controller.state.value.messages.map { it.id })
            assertNotNull(f.controller.state.value.filterWarning); assertEquals(p, AccountPreferences.parse(AccountPreferences(chat = p).json()).chat)
        }
    }
    @Test fun unchangedUnsupportedImportsCanStaySavedWhileOtherPreferencesChange() {
        val imported = ChatPreferences(words = "/(?=hello)/ spam", users = "あ".repeat(400))
        ChatFilters.validateChanges(imported.copy(timestamps = false), imported)
        assertTrue(runCatching { ChatFilters.validateChanges(imported.copy(words = "/(?=bad)/"), imported) }.isFailure)
        ChatFilters.validateChanges(imported.copy(words = "valid"), imported)
    }
    @Test fun chatFieldsNeverLeakIntoGeneralPreferencePatches() {
        val old = AccountPreferences(); val changed = old.copy(chat = ChatPreferences(false, "@viewer", "spam"), speed = 2f)
        assertEquals("{\"speed\":2}", changed.changesFrom(old).toString())
        assertEquals(ChatPreferences(false, "@viewer", "spam"), old.merge(changed.chat.json()).chat)
        assertTrue(AccountPermissions.groups.getValue("Manage chat replay settings").contains("GET;PUT:chat_timing/*"))
    }
    @Test fun requestsStartOnDemandAndUsePlayerPositionRatherThanWallTime() {
        Fixture().use { f ->
            f.fetch = { _, _ -> ChatChunk(listOf(message("a", 0), message("b", 10_000))) }
            f.controller.bind("abcdefghijk", "one", true); f.controller.present(true); assertEquals(0, f.reads); assertTrue(f.calls.isEmpty())
            f.controller.open(); assertEquals(1, f.reads); assertEquals(listOf("a"), f.controller.state.value.messages.map { it.id })
            f.tick(); assertEquals(listOf("a"), f.controller.state.value.messages.map { it.id })
            f.tick(10_000); assertEquals(listOf("a", "b"), f.controller.state.value.messages.map { it.id })
            f.tick(1_000); assertEquals(listOf("a"), f.controller.state.value.messages.map { it.id }); assertEquals(1, f.calls.size)
        }
    }
    @Test fun offsetsDelayOrAdvanceChatAndClampPublicRequests() {
        Fixture().use { f ->
            f.offset = 10_000; f.fetch = { _, _ -> ChatChunk(listOf(message("a", 0), message("b", 5_000))) }
            f.open(); assertTrue(f.controller.state.value.messages.isEmpty()); assertEquals(0L, f.calls.single().first)
            f.tick(15_000); assertEquals(listOf("a", "b"), f.controller.state.value.messages.map { it.id })
            f.controller.timing(-10_000); assertEquals(-10_000, f.saves.single())
            f.controller.timing(0); assertEquals(listOf(-10_000, 0), f.saves)
            assertTrue(runCatching { f.controller.timing(3_600_001) }.isFailure)
        }
    }
    @Test fun continuationOverlapReplacementsRemovalsAndEndAreApplied() {
        Fixture().use { f ->
            f.fetch = { _, token -> if (token.isEmpty()) ChatChunk(listOf(message("a"), message("b", 1_000)), continuation = "next")
                else ChatChunk(listOf(message("b", 1_000, "Replacement"), message("c", 2_000)), listOf("a")) }
            f.open(); f.tick(2_000)
            assertEquals(listOf("b", "c"), f.controller.state.value.messages.map { it.id })
            assertEquals("Replacement", f.controller.state.value.messages.first().text)
            f.tick(3_000); assertEquals(listOf("", "next"), f.calls.map { it.second })
        }
    }
    @Test fun prefetchStopsAheadAndSparseChunksContinue() {
        Fixture().use { f ->
            f.fetch = { _, token -> when (token) { "" -> ChatChunk(emptyList(), continuation = "sparse")
                "sparse" -> ChatChunk(listOf(message("future", 60_000)), continuation = "later")
                else -> ChatChunk(emptyList()) } }
            f.open(); f.tick(); assertEquals(2, f.calls.size); f.tick(); assertEquals(2, f.calls.size)
            f.controller.update(31_000, 120_000) // A seek outside cached coverage must request at its target.
            assertEquals(3, f.calls.size)
        }
    }
    @Test fun errorsKeepRowsAndRetryTheSameCursorAndUnavailableDoesNotRetry() {
        Fixture().use { f ->
            var fail = true
            f.fetch = { _, token -> if (token.isEmpty()) ChatChunk(listOf(message("a")), continuation = "next")
                else if (fail) throw IllegalStateException("Offline") else ChatChunk(listOf(message("b"))) }
            f.open(); f.tick(); assertEquals("Offline", f.controller.state.value.error); assertEquals("a", f.controller.state.value.messages.single().id)
            f.tick(); assertEquals(2, f.calls.size); fail = false; f.controller.retry()
            assertEquals(listOf("", "next", "next"), f.calls.map { it.second }); assertNull(f.controller.state.value.error)
        }
        Fixture().use { f -> f.fetch = { _, _ -> throw ApiException(404, "Unavailable") }; f.open(); f.tick(); f.controller.retry()
            assertTrue(f.controller.state.value.unavailable); assertEquals(1, f.calls.size) }
    }
    @Test fun repeatedCursorsAndCyclesStopPagination() {
        Fixture().use { f ->
            f.fetch = { _, token -> ChatChunk(emptyList(), continuation = if (token == "b") "a" else if (token == "a") "b" else "a") }
            f.open(); repeat(5) { f.tick() }
            assertEquals(listOf("", "a", "b"), f.calls.map { it.second }); assertNotNull(f.controller.state.value.error)
            f.tick(70_000); assertEquals(70_000L, f.calls.last().first)
        }
    }
    @Test fun suspendAndCloseStopRequestsButReopenReusesCachedRows() {
        Fixture().use { f ->
            f.fetch = { _, _ -> ChatChunk(listOf(message("a")), continuation = "next") }
            f.open(); f.controller.present(false); f.tick(); assertEquals(1, f.calls.size); assertTrue(f.controller.state.value.open)
            f.controller.present(true); assertEquals(2, f.calls.size)
            f.controller.close(); f.tick(); assertEquals(2, f.calls.size); assertFalse(f.controller.state.value.open)
        }
    }
    @Test fun eachOccurrenceStartsClosedEvenForTheSameVideoAndTimingRemainsPerVideo() {
        Fixture().use { f -> f.open(); f.controller.timing(1234); f.controller.bind("abcdefghijk", "two", true)
            assertFalse(f.controller.state.value.open); assertTrue(f.controller.state.value.messages.isEmpty()); assertEquals(listOf(1234), f.saves) }
    }
    @Test fun readingPositionFreezesUntilReturningToPlaybackAndWindowIsBounded() {
        Fixture().use { f ->
            f.fetch = { _, _ -> ChatChunk((0 until 240).map { message("m$it", it.toLong()) }) }
            f.open(130); assertEquals(120, f.controller.state.value.messages.size)
            f.controller.position(CommentPosition(4, 12), false); val rows = f.controller.state.value.messages
            f.tick(200); assertEquals(rows, f.controller.state.value.messages); assertEquals(CommentPosition(4, 12), f.controller.state.value.position)
            f.controller.position(CommentPosition(119, 0), true); assertFalse(f.controller.state.value.following)
            f.controller.follow(); assertEquals("m200", f.controller.state.value.messages.last().id); assertEquals(120, f.controller.state.value.messages.size)
            f.controller.position(CommentPosition(), false); f.controller.older(); assertEquals("m80", f.controller.state.value.messages.last().id)
        }
    }
    @Test fun recreationAndPanelRestorationPreserveManualReadingPosition() {
        Fixture().use { f ->
            f.fetch = { _, _ -> ChatChunk((0 until 240).map { message("m$it", it.toLong()) }) }
            f.open(200); f.controller.position(CommentPosition(4, 12), false)
            val rows = f.controller.state.value.messages
            f.controller.present(false); f.tick(210); f.controller.present(true)
            assertFalse(f.controller.state.value.following); assertEquals(CommentPosition(4, 12), f.controller.state.value.position)
            assertEquals(rows, f.controller.state.value.messages); assertEquals(1, f.calls.size)
            f.controller.close(); f.controller.open(); assertFalse(f.controller.state.value.following)
        }
    }
    @Test fun replacementsKeepAManuallyScrolledWindowInTimestampOrder() {
        Fixture().use { f ->
            f.fetch = { _, token -> if (token.isEmpty()) ChatChunk(listOf(message("a"), message("b", 1000)), continuation = "next")
                else ChatChunk(listOf(message("a", 2000, "Replacement"))) }
            f.open(2000); f.controller.position(CommentPosition(0, 7), false); f.tick(2000)
            assertEquals(listOf("b", "a"), f.controller.state.value.messages.map { it.id })
            assertFalse(f.controller.state.value.following); assertEquals(CommentPosition(0, 7), f.controller.state.value.position)
        }
    }
    @Test fun oldestSeekSegmentsAreEvictedAndReloaded() {
        Fixture().use { f ->
            f.fetch = { at, _ -> ChatChunk(listOf(message("m$at", at)), continuation = "next") }
            f.open(); repeat(25) { f.clock += 1_000; f.controller.update((it + 1) * 30_000L, 1_000_000, true) }
            val before = f.calls.size; f.clock += 1_000; f.controller.update(0, 1_000_000, true)
            assertEquals(before + 1, f.calls.size); assertEquals(0L, f.calls.last().first)
        }
    }
    @Test fun continuationThrottleAndCancellationPreventEarlyOrHiddenRequests() = runBlocking {
        Fixture().use { f ->
            f.fetch = { _, _ -> ChatChunk(emptyList(), continuation = "next") }
            f.open(); f.controller.update(0, 120_000)
            delay(30); assertEquals(1, f.calls.size); assertTrue(f.controller.state.value.loading)
            f.controller.present(false); delay(30); assertEquals(1, f.calls.size); assertFalse(f.controller.state.value.loading)
        }
    }
    @Test fun timingReadFromBeforeRecreationCannotReplaceTheNewRead() = runBlocking {
        Fixture().use { f ->
            val gate = CompletableDeferred<Unit>(); var reads = 0
            f.read = { if (reads++ == 0) withContext(NonCancellable) { gate.await(); 999 } else 123 }
            f.open(); f.controller.present(false); f.controller.present(true)
            assertEquals(123, f.controller.state.value.timingOffsetMs)
            gate.complete(Unit); yield(); assertEquals(123, f.controller.state.value.timingOffsetMs)
        }
    }
    @Test fun cachePruningRequiresRefetchOutsideRetainedCoverage() {
        Fixture().use { f ->
            f.fetch = { at, _ -> if (at == 0L) ChatChunk((0 until 5100).map { message("m$it", it * 10L) }) else ChatChunk(listOf(message("late", at))) }
            f.open(); f.tick(51_000)
            assertEquals(2, f.calls.size); assertEquals(51_000L, f.calls.last().first); assertEquals("late", f.controller.state.value.messages.single().id)
        }
    }
    @Test fun lateSeekAndOccurrenceResponsesCannotPublish() = runBlocking {
        Fixture().use { f ->
            val gate = CompletableDeferred<Unit>()
            f.fetch = { at, _ -> if (at == 0L) withContext(NonCancellable) { gate.await(); ChatChunk(listOf(message("old"))) }
                else ChatChunk(listOf(message("new", at))) }
            f.open(); f.tick(70_000); assertEquals("new", f.controller.state.value.messages.single().id)
            gate.complete(Unit); yield(); assertEquals("new", f.controller.state.value.messages.single().id)
            f.controller.bind("bbbbbbbbbbb", "two", true); assertTrue(f.controller.state.value.messages.isEmpty())
        }
    }
    @Test fun lateTimingReadCannotOverwriteManualEditAndFailedWritesRetry() = runBlocking {
        Fixture().use { f ->
            val gate = CompletableDeferred<Unit>(); f.read = { withContext(NonCancellable) { gate.await(); 999 } }
            f.open(); f.failure = true; f.controller.timing(123); assertEquals("Save failed", f.controller.state.value.timingError)
            gate.complete(Unit); yield(); assertEquals(123, f.controller.state.value.timingOffsetMs)
            f.failure = false; f.controller.retryTiming(); assertEquals(listOf(123), f.saves); assertNull(f.controller.state.value.timingError)
        }
    }
    @Test fun failedTimingReadsRetryWithoutBecomingWrites() {
        Fixture().use { f ->
            var fail = true; f.read = { if (fail) throw IllegalStateException("Read failed") else 456 }
            f.open(); assertEquals("Read failed", f.controller.state.value.timingError); fail = false; f.controller.retryTiming()
            assertEquals(456, f.controller.state.value.timingOffsetMs); assertTrue(f.saves.isEmpty())
        }
    }
    @Test fun accountAndInstanceChangesInvalidateEvenUncancellableResponses() = runBlocking {
        Fixture().use { f ->
            val gate = CompletableDeferred<Unit>(); f.fetch = { _, _ -> withContext(NonCancellable) { gate.await(); ChatChunk(listOf(message("old"))) } }
            f.open(); f.context = ApiContext("https://other.test", null, 1); f.controller.bind("abcdefghijk", "one", true)
            gate.complete(Unit); yield(); assertFalse(f.controller.state.value.open); assertTrue(f.controller.state.value.messages.isEmpty())
        }
    }
    @Test fun publicApiEncodesCursorsWithoutSendingAccountCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("private-token", "Viewer", Long.MAX_VALUE, address) })
            server.enqueue(MockResponse().setBody("""{"messages":[],"removedIds":[],"continuation":null}"""))
            api.chatReplay("abcdefghijk", 1234, "+/=&%", api.context())
            val request = server.takeRequest(); assertEquals("/api/v1/live_chat/abcdefghijk", request.requestUrl!!.encodedPath)
            assertEquals("+/=&%", request.requestUrl!!.queryParameter("continuation")); assertEquals("1234", request.requestUrl!!.queryParameter("offset_ms"))
            assertNull(request.getHeader("Authorization"))
        }
    }
    @Test fun authenticatedApisUseDedicatedSparseWritesAndExplainOldScopes() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val address = server.url("/").toString().trimEnd('/')
            val account = Account("private-token", "Viewer", Long.MAX_VALUE, address)
            var expired = false; val api = InvidiousApi({ address }, { account }, { expired = true })
            server.enqueue(MockResponse().setBody("""{"chat_show_timestamps":false,"unrelated":"keep"}"""))
            assertFalse(api.chatPreferences(JSONObject().put("chat_show_timestamps", false), api.context()).chat.timestamps)
            val settings = server.takeRequest(); assertEquals("PATCH", settings.method); assertEquals("Bearer private-token", settings.getHeader("Authorization"))
            assertEquals(1, JSONObject(settings.body.readUtf8()).length())
            server.enqueue(MockResponse().setBody("""{"offsetMs":-1000}""")); assertEquals(-1000, api.chatTiming("abcdefghijk", api.context()))
            assertEquals("GET", server.takeRequest().method)
            server.enqueue(MockResponse().setBody("""{"offsetMs":123}""")); api.chatTiming("abcdefghijk", 123, api.context())
            val timing = server.takeRequest(); assertEquals("PUT", timing.method); assertEquals(123, JSONObject(timing.body.readUtf8()).getInt("offsetMs"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Invalid scope"}"""))
            try { api.chatTiming("abcdefghijk", api.context()); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains("Sign out and sign in")) }
            assertFalse(expired)
            for ((status, error) in listOf(401 to "Request must be authenticated", 403 to "Token is expired")) {
                server.enqueue(MockResponse().setResponseCode(status).setBody(JSONObject().put("error", error).toString()))
                try { api.chatTiming("abcdefghijk", api.context()); fail() } catch (e: ApiException) { assertTrue(e.message!!.contains("Playback can continue")) }
                assertFalse(expired)
            }
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Request must be authenticated"}"""))
            try { api.preferences(api.context()); fail() } catch (_: ApiException) { }
            assertTrue(expired)
        }
    }
    @Test fun apiRejectsResponsesAfterContextChanges() = runBlocking {
        MockWebServer().use { server ->
            server.start(); var address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { null })
            server.enqueue(MockResponse().setBody("""{"messages":[],"removedIds":[],"continuation":null}""").setBodyDelay(200, TimeUnit.MILLISECONDS))
            val request = async(Dispatchers.IO) { api.chatReplay("abcdefghijk", 0, "", api.context()) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); address = "https://other.test"
            try { request.await(); fail() } catch (_: CancellationException) { }
        }
    }
}
