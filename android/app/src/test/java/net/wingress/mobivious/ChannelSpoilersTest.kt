package net.wingress.mobivious

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.wingress.mobivious.data.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSpoilersTest {
    private val channel = "UC" + "a".repeat(22)
    private val other = "UC" + "b".repeat(22)
    private val now = 1_791_980_000L
    private val rule = ChannelSpoilerRule("Channel", true, true, 7)
    private fun video(id: String = "testvideo01", published: Long? = null) = Video(id, "Original", channelId = channel, publicationTime = published)

    @Test fun normalizationMatchesWebsiteAndKeepsInvalidDelaysOut() {
        for (timer in listOf("", ",\"reveal_after_days\":null")) {
            val prefs = AccountPreferences.parse(JSONObject("""{"channel_spoiler_settings":{"$channel":{"hide_title":true$timer}}}"""))
            assertNull(prefs.channelSpoilers[channel]!!.revealAfterDays)
            assertTrue(prefs.json().getJSONObject("channel_spoiler_settings").getJSONObject(channel).has("reveal_after_days"))
        }
        for (timer in listOf("0", "-1", "1.5", "\"7\"", "2147483648", "true")) {
            assertTrue(ChannelSpoilerRules.parse(JSONObject("""{"$channel":{"hide_title":true,"reveal_after_days":$timer}}""")).isEmpty())
        }
        val emoji = "😀".repeat(201)
        val parsed = ChannelSpoilerRules.parse(JSONObject().put(channel, rule.json().put("name", emoji)).put("invalid", rule.json()).put(other, rule.copy(hideTitle = false, hideThumbnail = false).json()))
        assertEquals("😀".repeat(200), parsed[channel]!!.name); assertEquals(1, parsed.size)
        assertNull(ChannelSpoilerRules.days("0")); assertNull(ChannelSpoilerRules.days("2147483648")); assertEquals(7, ChannelSpoilerRules.days(" 7 "))
    }
    @Test fun sparseChangesAndDeletionPreserveOtherChannelsAndGuestNames() {
        val before = AccountPreferences(channelSpoilers = mapOf(channel to rule, other to rule.copy(name = "Other")))
        val next = before.copy(channelSpoilers = before.channelSpoilers + (channel to rule.copy(hideThumbnail = false)))
        val delta = next.changesFrom(before)
        val map = delta.getJSONObject("channel_spoiler_settings")
        assertEquals(setOf(channel), map.keys().asSequence().toSet()); assertFalse(map.getJSONObject(channel).has("name"))
        assertEquals("Channel", next.changesFrom(before, true).getJSONObject("channel_spoiler_settings").getJSONObject(channel).getString("name"))
        assertEquals(before.channelSpoilers[other], before.merge(delta).channelSpoilers[other])
        val removed = before.copy(channelSpoilers = mapOf(other to rule)).changesFrom(before)
        assertTrue(removed.getJSONObject("channel_spoiler_settings").isNull(channel))
        assertFalse(before.merge(removed).channelSpoilers.containsKey(channel))
        assertEquals(before.channelSpoilers, LocalPreferences.read(before.json()).channelSpoilers)
    }
    @Test fun exactExpiryUnknownAndFutureDatesRemainCorrect() {
        val uploaded = now - 7 * 86_400
        assertTrue(rule.active(uploaded, now - 1)); assertFalse(rule.active(uploaded, now))
        assertTrue(rule.active(null, now)); assertTrue(rule.active(now + 86_400, now))
        assertTrue(rule.copy(revealAfterDays = null).active(uploaded, now))
        assertFalse(rule.copy(hideTitle = false, hideThumbnail = false).active(null, now))
        assertEquals(1_791_005_400L, ChannelSpoilerRules.publication("2026-10-03T12:30:00+07:00"))
        assertEquals(1_790_985_600L, ChannelSpoilerRules.publication("2026-10-03"))
        for (value in listOf(null, "", "unknown", 0L, "1970-01-01")) assertNull(ChannelSpoilerRules.publication(value))
        assertNull(ChannelSpoilerRules.publication(JSONObject("""{"publishedIsKnown":false,"published":1791005400}""")))
        assertEquals(ChannelSpoilerRules.publication("2026-10-03T12:30:00+07:00"), ApiParser.video(JSONObject("""{"published":"2026-10-03T12:30:00+07:00"}""")).publicationTime)
    }
    @Test fun pendingRulesNeverExposeTitlesAndRevealModesAreExplicit() {
        assertEquals("Loading channel settings…", ChannelSpoilerState().title(video(), "Replacement", SpoilerTitleMode.ORIGINAL))
        val state = ChannelSpoilerState(ready = true, rules = mapOf(channel to rule), now = now)
        assertTrue(state.title(video(), "Replacement").contains("upload date unknown"))
        assertEquals("Replacement", state.title(video(), "Replacement", SpoilerTitleMode.DEARROW))
        assertTrue(state.title(video(), null, SpoilerTitleMode.DEARROW).startsWith("Title hidden"))
        assertEquals("Original", state.title(video(), "Replacement", SpoilerTitleMode.ORIGINAL))
        assertEquals(SpoilerTitleMode.DEARROW, SpoilerTitleMode.HIDDEN.next(true))
        assertEquals(SpoilerTitleMode.ORIGINAL, SpoilerTitleMode.HIDDEN.next(false))
        assertEquals(SpoilerTitleMode.HIDDEN, SpoilerTitleMode.ORIGINAL.next(true))
        assertEquals("Replacement", state.title(video(published = now - 8 * 86_400), "Replacement"))
        assertTrue(state.decision(video(published = 0)).unknown)
        assertTrue(state.decision(video().copy(history = HistoryMetadata(java.time.LocalDate.of(1970, 1, 1), null))).unknown)
        assertFalse(state.decision(video().copy(history = HistoryMetadata(java.time.LocalDate.of(2020, 1, 1), null))).title)
    }
    @Test fun lookupsBatchDeduplicateRetainMissesAndSkipKnownOrInactiveRules() = runTest {
        val context = ApiContext("https://instance.test", null)
        val calls = mutableListOf<List<String>>()
        val gate = CompletableDeferred<Unit>()
        val repository = ChannelSpoilerRepository(backgroundScope, { context }, { ids, _ -> calls += ids; gate.await(); mapOf("testvideo01" to now - 8 * 86_400) }, { now })
        repository.reset(context, AccountPreferences(channelSpoilers = mapOf(channel to rule)))
        repository.ensure(listOf(video(), video("testvideo02"), video("testvideo03", now), video("testvideo04").copy(channelId = other)))
        advanceTimeBy(31); runCurrent()
        repository.ensure(listOf(video())); gate.complete(Unit); runCurrent()
        assertEquals(listOf(listOf("testvideo01", "testvideo02")), calls)
        assertFalse(repository.state.value.decision(video()).title)
        assertTrue(repository.state.value.decision(video("testvideo02")).unknown)
        repository.ensure(listOf(video("testvideo02"))); advanceTimeBy(100); runCurrent(); assertEquals(1, calls.size)
        repository.bindPlayback("first"); repository.revealTitle("first", SpoilerTitleMode.ORIGINAL)
        repository.revealThumbnail("first")
        assertTrue(repository.state.value.playback.thumbnail)
        repository.bindPlayback("second")
        repository.revealTitle("first", SpoilerTitleMode.ORIGINAL)
        assertEquals(SpoilerTitleMode.HIDDEN, repository.state.value.playback.title)
        repository.revealTitle("second", SpoilerTitleMode.ORIGINAL)
        assertFalse(repository.state.value.playback.thumbnail)
        repository.configure(AccountPreferences()); repository.ensure(listOf(video())); advanceTimeBy(100); assertEquals(1, calls.size)
        repository.configure(AccountPreferences(dearrowEnabled = true))
        assertTrue(repository.state.value.dearrowEnabled)
    }
    @Test fun accountChangeRejectsLateLookupAndReveals() = runTest {
        var context = ApiContext("https://one.test", null)
        val repository = ChannelSpoilerRepository(backgroundScope, { context }, { ids, _ -> withContext(NonCancellable) { delay(100); ids.associateWith { now - 8 * 86_400 } } }, { now })
        repository.reset(context, AccountPreferences(channelSpoilers = mapOf(channel to rule))); repository.ensure(listOf(video()))
        advanceTimeBy(31); runCurrent()
        context = ApiContext("https://two.test", null); repository.reset(context, AccountPreferences())
        advanceTimeBy(200); runCurrent(); assertTrue(repository.state.value.dates.isEmpty())
        assertTrue(repository.state.value.rules.isEmpty()); assertEquals(context, repository.state.value.context)
    }
    @Test fun publicLookupUsesBoundedCacheEndpointWithoutAuthentication() = runTest {
        MockWebServer().use { server ->
            val address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { Account("secret", "Account", Long.MAX_VALUE, address) })
            server.enqueue(MockResponse().setBody("""{"publications":{"testvideo01":1791005400,"testvideo02":null}}"""))
            val dates = api.spoilerPublications(listOf("testvideo01", "testvideo02"), api.context())
            val request = server.takeRequest()
            assertEquals("/api/v1/spoilers/publications", request.requestUrl!!.encodedPath)
            assertEquals("testvideo01,testvideo02", request.requestUrl!!.queryParameter("ids")); assertNull(request.getHeader("Authorization"))
            assertEquals(1_791_005_400L, dates["testvideo01"]); assertNull(dates["testvideo02"])
        }
    }
}
