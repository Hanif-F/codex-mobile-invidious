package net.wingress.mobivious

import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import net.wingress.mobivious.player.SponsorBlockEngine
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SponsorBlockTest {
    private val sponsor = SponsorBlockCategory.SPONSOR
    private val intro = SponsorBlockCategory.INTRO
    private val channelId = "UC" + "a".repeat(22)
    private fun segment(id: String, start: Long, end: Long, category: SponsorBlockCategory = sponsor) = SponsorBlockSegment(id, category, start, end)
    private fun engine(mode: SponsorBlockMode, vararg ranges: SponsorBlockSegment) = SponsorBlockEngine().apply {
        settings = SponsorBlockSettings(enabled = true).let { it.copy(modes = it.modes + (sponsor to mode)) }; segments = ranges.toList()
    }

    @Test fun defaultsAndMalformedPreferenceFallbacks() {
        val prefs = ApiParser.preferences(JSONObject("{}"))
        assertFalse(prefs.sponsorBlock.enabled)
        assertTrue(prefs.sponsorBlock.modes.values.all { it == SponsorBlockMode.MANUAL })
        assertEquals(8, prefs.sponsorBlock.colors.size)
        val parsed = SponsorBlockSettings.parse(JSONObject("""{"sponsorblock_enabled":"true","sponsorblock_modes":{"sponsor":"wrong","intro":"auto","unknown":"auto"},"sponsorblock_colors":{"sponsor":"red","intro":"#123ABC"}}"""))
        assertFalse(parsed.enabled); assertEquals(SponsorBlockMode.MANUAL, parsed.modes[sponsor])
        assertEquals(SponsorBlockMode.AUTO, parsed.modes[intro]); assertEquals(sponsor.color, parsed.colors[sponsor]); assertEquals("#123ABC", parsed.colors[intro])
    }
    @Test fun rangesFilterBadEntriesDeduplicateAndSort() {
        val parsed = SponsorBlockRules.segments(JSONObject("""{"segments":[
            {"id":"late","category":"intro","start":3,"end":4},
            {"id":"early","category":"sponsor","start":0.5,"end":1},
            {"id":"early","category":"sponsor","start":2,"end":3},
            {"id":"bad","category":"unknown","start":0,"end":1},
            {"id":"reverse","category":"sponsor","start":5,"end":2},
            {"id":"negative","category":"sponsor","start":-1,"end":2},
            {"id":"text","category":"sponsor","start":"0","end":1},
            {"category":"sponsor","start":0,"end":1},
            {"id":"huge","category":"sponsor","start":0,"end":1e100}] }"""))
        assertEquals(listOf("early", "late"), parsed.map { it.id }); assertEquals(500L, parsed.first().start)
        assertTrue(SponsorBlockRules.segments(JSONObject()).isEmpty())
    }
    @Test fun channelInputAcceptsOnlyCanonicalPaths() {
        listOf(channelId, "/channel/$channelId", "https://youtube.com/channel/$channelId/").forEach { assertEquals(channelId, SponsorBlockRules.channelId(it)) }
        listOf("UCfixture", "https://youtube.com/@handle", "https://example.com/other", "../$channelId").forEach { assertNull(SponsorBlockRules.channelId(it)) }
    }
    @Test fun inheritancePreservesFalseAndGlobalColors() {
        val global = SponsorBlockSettings(enabled = true, channels = mapOf(channelId to SponsorBlockChannel("Channel", false, mapOf(intro to SponsorBlockMode.AUTO))))
        val effective = global.effective(channelId)
        assertFalse(effective.enabled); assertEquals(SponsorBlockMode.AUTO, effective.modes[intro]); assertEquals(SponsorBlockMode.MANUAL, effective.modes[sponsor])
        assertEquals(global.colors, effective.colors); assertFalse(global.effective(channelId).enabled)
        val inherited = global.copy(channels = mapOf(channelId to SponsorBlockChannel("Channel", null, mapOf(intro to SponsorBlockMode.MARKER))))
        assertTrue(inherited.effective(channelId).enabled)
        assertEquals(global, SponsorBlockSettings.parse(global.json()))
    }
    @Test fun sparseCategoryAndChannelPatch() {
        val old = AccountPreferences(sponsorBlock = SponsorBlockSettings(channels = mapOf(channelId to SponsorBlockChannel("Channel", false))))
        assertEquals(0, old.changesFrom(old).length())
        val changed = old.copy(sponsorBlock = old.sponsorBlock.copy(modes = old.sponsorBlock.modes + (intro to SponsorBlockMode.AUTO),
            colors = old.sponsorBlock.colors + (sponsor to "#123456"), channels = emptyMap())).changesFrom(old)
        assertEquals(3, changed.length()); assertEquals(1, changed.getJSONObject("sponsorblock_modes").length())
        assertTrue(changed.getJSONObject("sponsorblock_channel_overrides").isNull(channelId)); assertFalse(changed.has("dearrow_enabled"))
        val add = old.copy(sponsorBlock = old.sponsorBlock.copy(channels = mapOf(channelId to SponsorBlockChannel("New name", true)))).changesFrom(old)
        assertFalse(add.getJSONObject("sponsorblock_channel_overrides").getJSONObject(channelId).has("name"))
    }
    @Test fun autoMergesTouchingAndOverlappingRangesAndReplaysManually() {
        val engine = engine(SponsorBlockMode.AUTO, segment("a", 1000, 2000), segment("b", 1500, 3000), segment("c", 3000, 4000))
        assertEquals(4000L, engine.evaluate(1100, 10_000).seek)
        val replay = engine.evaluate(1100, 10_000)
        assertNull(replay.seek); assertEquals("a", replay.active?.id)
        assertNull(engine.evaluate(3500, 10_000).seek); assertEquals("c", engine.evaluate(3500, 10_000).active?.id)
    }
    @Test fun manualOverlapOffersEarliestEndAndDismissResetsAfterLeaving() {
        val engine = engine(SponsorBlockMode.MANUAL, segment("a", 1000, 3000), segment("b", 1000, 2000, intro))
        assertEquals("b", engine.evaluate(1500, 10_000).active?.id)
        engine.dismiss("b"); assertEquals("a", engine.evaluate(1500, 10_000).active?.id)
        engine.dismiss("a"); assertNull(engine.evaluate(1500, 10_000).active)
        engine.settings = engine.settings.copy(enabled = false)
        engine.evaluate(4000, 10_000)
        engine.settings = engine.settings.copy(enabled = true)
        assertEquals("b", engine.evaluate(1500, 10_000).active?.id)
    }
    @Test fun markerAndDisabledModesNeverSeekOrPrompt() {
        val engine = engine(SponsorBlockMode.MARKER, segment("a", 0, 5000))
        assertEquals(1, engine.evaluate(1000, 10_000).visible.size); assertNull(engine.evaluate(1000, 10_000).active)
        engine.settings = engine.settings.copy(modes = engine.settings.modes + (sponsor to SponsorBlockMode.DISABLED))
        assertTrue(engine.evaluate(1000, 10_000).visible.isEmpty()); assertNull(engine.evaluate(1000, 10_000).seek)
        engine.settings = engine.settings.copy(modes = SponsorBlockCategory.entries.associateWith { SponsorBlockMode.DISABLED })
        assertFalse(engine.settings.usable)
    }
    @Test fun clampsDurationAndWaitsForKnownDuration() {
        val engine = engine(SponsorBlockMode.AUTO, segment("a", 0, 30_000), segment("b", 40_000, 50_000))
        assertNull(engine.evaluate(1000, 0).seek); assertEquals(10_000L, engine.evaluate(1000, 10_000).seek)
        assertNull(engine.evaluate(10_000, 10_000).active)
    }
    @Test fun sessionResetAndSettingsUpdatesHaveDistinctReplaySemantics() {
        val engine = engine(SponsorBlockMode.AUTO, segment("a", 1000, 2000))
        engine.evaluate(1100, 10_000); engine.settings = engine.settings.copy(colors = engine.settings.colors + (sponsor to "#123456"))
        assertNull(engine.evaluate(1100, 10_000).seek)
        val settings = engine.settings; val segments = engine.segments; engine.reset(); engine.settings = settings; engine.segments = segments
        assertEquals(2000L, engine.evaluate(1100, 10_000).seek)
    }
    @Test fun playbackStateRoundTripRetainsActiveAndNotice() {
        val segment = segment("a", 1000, 2000)
        val state = SponsorBlockPlayback("abcdefghijk", "session", SponsorBlockSettings(enabled = true), listOf(segment), segment, 1, "Skipped Intro")
        assertEquals(state, SponsorBlockPlayback.parse(state.json()))
    }
    @Test fun publicSegmentRequestHasNoCredentialsAndPropagatesFailure() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val account = Account("private-token", "user", Long.MAX_VALUE, server.url("/").toString().trimEnd('/'))
            val api = InvidiousApi({ account.server }, { account })
            server.enqueue(MockResponse().setBody("""{"segments":[{"id":"a","category":"sponsor","start":1,"end":2}]}"""))
            assertEquals(1, api.sponsorBlock("abcdefghijk", api.context()).size)
            val request = server.takeRequest(); assertEquals("/api/v1/sponsorblock/abcdefghijk", request.path)
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            try { api.sponsorBlock("abcdefghijk", api.context()); fail("Failure hidden") } catch (e: ApiException) { assertEquals(503, e.status) }
        }
    }
    @Test fun staleAccountContextPreventsRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start(); var address = server.url("/").toString().trimEnd('/')
            val api = InvidiousApi({ address }, { null }); val old = api.context(); address = "https://other.test"
            try { api.sponsorBlock("abcdefghijk", old); fail("Stale request accepted") } catch (_: kotlinx.coroutines.CancellationException) { }
            assertEquals(0, server.requestCount)
        }
    }
}
