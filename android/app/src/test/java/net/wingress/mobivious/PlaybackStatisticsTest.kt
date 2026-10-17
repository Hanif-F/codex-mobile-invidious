package net.wingress.mobivious

import net.wingress.mobivious.player.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackStatisticsTest {
    private var time = 0L
    private fun accumulator() = StatisticsAccumulator { time }

    @Test fun loadBytesUseCumulativeDeltasAndCompletedTransferTime() {
        val stats = accumulator()
        stats.start(1); stats.start(1)
        stats.load(1, 100, 20, error = true)
        stats.load(1, 100, 30, error = true, timeout = true)
        stats.load(1, 250, 100, completed = true)
        stats.load(1, 250, 100, completed = true)
        assertEquals(250, stats.bytes)
        assertEquals(1, stats.requests); assertEquals(2, stats.errors); assertEquals(1, stats.timeouts)
        assertEquals(.02, stats.bandwidth!!, .00001)
        stats.start(2); stats.load(2, 50, 20, canceled = true)
        assertEquals(300, stats.bytes); assertEquals(1, stats.cancellations)
    }

    @Test fun unknownAndTerminalTasksCannotAddBytesOrInvalidSpeeds() {
        val stats = accumulator()
        stats.load(9, 1_000_000, 1, completed = true)
        assertEquals(0, stats.bytes); assertNull(stats.bandwidth)
        stats.start(1); stats.load(1, 100, 0, error = true, terminal = true)
        stats.load(1, 500, 10, completed = true)
        assertEquals(100, stats.bytes); assertNull(stats.bandwidth)
        stats.start(2); stats.load(2, 0, 10, completed = true)
        assertNull(stats.bandwidth)
    }

    @Test fun observationExcludesHiddenTimeAndClosesAnActiveStall() {
        val stats = accumulator()
        stats.observe(true, true, false, true)
        time = 1000; stats.observe(true, false, true, true)
        time = 1500; stats.observe(false, false, true, true)
        time = 9500
        assertEquals(1.5, stats.observationSeconds, 0.0)
        assertEquals(.5, stats.stallSeconds, 0.0); assertEquals(1, stats.stalls)
        stats.observe(true, false, true, true) // Resuming during a hidden stall isn't another observed stall.
        time = 10_000; stats.observe(true, true, false, true)
        assertEquals(2.0, stats.observationSeconds, 0.0); assertEquals(1, stats.stalls)
    }

    @Test fun startupPauseSeekAndErrorDoNotCountAsStalls() {
        val stats = accumulator()
        stats.observe(true, false, true, true)
        assertEquals(0, stats.stalls)
        stats.observe(true, true, false, true)
        stats.observe(true, false, true, true, seeking = true)
        stats.observe(true, false, true, true)
        assertEquals(0, stats.stalls)
        stats.observe(true, true, false, true)
        stats.observe(true, false, true, false)
        stats.observe(true, false, true, true)
        assertEquals(0, stats.stalls)
        stats.observe(true, true, false, true)
        stats.observe(true, false, true, true, failed = true)
        stats.observe(true, false, true, true)
        assertEquals(0, stats.stalls)
        stats.observe(true, true, false, true)
        stats.observe(true, false, true, true)
        time = 1000; stats.observe(true, false, true, true)
        assertEquals(1, stats.stalls); assertEquals(1.0, stats.stallSeconds, 0.0)
    }

    @Test fun graphHistoryHasGapsAndIsBoundedByAgeAndCount() {
        val stats = accumulator()
        stats.observe(true, true, false, true)
        repeat(65) { time += 1000; stats.sample(2.0, 3.0) }
        assertEquals(60, stats.samples().size)
        stats.observe(false, false, false, false)
        assertNull(stats.samples().last().buffer)
        time += 30_000; stats.observe(true, true, false, true); stats.sample(1.0, null)
        assertTrue(stats.samples().all { time - it.at < 60_000 })
        assertTrue(stats.samples().any { it.buffer == null && it.bandwidth == null })
        time += 60_000; assertTrue(stats.samples().isEmpty())
    }

    @Test fun buffersAndFramesHandleUnknownZeroAndBackwardsValues() {
        assertNull(StatisticsAccumulator.buffer(0, -1))
        assertEquals(0.0, StatisticsAccumulator.buffer(5000, 2000)!!, 0.0)
        assertEquals(2.5, StatisticsAccumulator.buffer(1000, 3500)!!, 0.0)
        assertEquals("0 / 0 (0%)", StatisticsAccumulator.frames(0, 0))
        assertEquals("2 / 10 (20%)", StatisticsAccumulator.frames(8, 2))
    }

    @Test fun exportedSnapshotHasAllowlistedFieldsAndExplicitAvailability() {
        val stats = StatisticsSnapshot("testvideo01", "offline", 2.5, mapOf(
            StatisticKey.resolution to PlaybackStatistic("1920 × 1080"),
            StatisticKey.bandwidth to PlaybackStatistic(reason = "Unavailable")))
        val json = JSONObject(stats.json("0.12.3", "1.9.3", StatisticsViewport(640, 360, 2f)))
        assertEquals(setOf("version", "platform", "videoId", "appVersion", "playerVersion", "mode", "observationSeconds", "metrics", "availabilityReasons"), json.keys().asSequence().toSet())
        assertEquals(1, json.getInt("version"))
        assertEquals("640 × 360 / 2", json.getJSONObject("metrics").getString("viewport"))
        assertTrue(json.getJSONObject("metrics").isNull("bandwidth"))
        assertEquals("Unavailable", json.getJSONObject("availabilityReasons").getString("bandwidth"))
        assertFalse(json.toString().contains("uri", true)); assertFalse(json.toString().contains("token", true))
    }

    @Test fun closingTheSessionResetsVisibilityButNewSourceMeasurementsKeepItEnabled() {
        val stats = PlaybackStatistics()
        stats.toggle(); stats.expand(true); stats.visible(true)
        stats.publish(StatisticsSnapshot("first"))
        stats.publish(StatisticsSnapshot("second"))
        assertTrue(stats.ui.value.enabled); assertTrue(stats.ui.value.expanded)
        stats.visible(false); assertTrue(stats.ui.value.enabled)
        stats.visible(true); assertTrue(stats.ui.value.expanded)
        stats.resetSession()
        assertFalse(stats.ui.value.enabled); assertFalse(stats.ui.value.expanded)
        assertEquals("", stats.snapshot.value.videoId)
        assertEquals(0L, accumulator().bytes)
    }
}
