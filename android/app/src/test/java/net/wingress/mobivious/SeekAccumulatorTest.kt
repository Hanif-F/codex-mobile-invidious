package net.wingress.mobivious

import net.wingress.mobivious.player.SeekAccumulator
import org.junit.Assert.*
import org.junit.Test

class SeekAccumulatorTest {
    @Test fun repeatedTapsKeepFrozenPositionAndExtendDeadline() {
        val a = SeekAccumulator()
        a.tap(1, "video", 40_000, true, 0)
        a.tap(1, "video", 40_020, false, 200)
        val third = a.tap(1, "video", 40_030, false, 400)
        assertEquals(30_000L, third.offset); assertEquals(40_000L, third.start); assertTrue(third.resume)
        assertNull(a.finish("video", 999))
        assertEquals(70_000L, a.finish("video", 1000)?.target(120_000))
        assertNull(a.finish("video", 1001))
    }
    @Test fun oppositeDirectionReplacesRatherThanSubtractsAndRetainsAnchor() {
        val a = SeekAccumulator()
        repeat(3) { a.tap(1, "video", 50_000, true, it * 100L) }
        assertEquals(-10_000L, a.tap(-1, "video", 50_010, false, 300).offset)
        assertEquals(-20_000L, a.tap(-1, "video", 50_010, false, 400).offset)
        val reversed = a.tap(1, "video", 50_010, false, 500)
        assertEquals(10_000L, reversed.offset); assertEquals(50_000L, reversed.start)
    }
    @Test fun pausedPlaybackStaysPausedAndTargetsAreBounded() {
        val a = SeekAccumulator()
        assertEquals(0L, a.tap(-1, "video", 2000, false, 0).target(120_000))
        assertFalse(a.finish("video", 600)!!.resume)
        assertEquals(120_000L, a.tap(1, "video", 115_000, false, 1000).target(120_000))
    }
    @Test fun cancellationAndMediaReplacementCannotCommitStaleSeeks() {
        val a = SeekAccumulator()
        a.tap(1, "old", 40_000, true, 0)
        assertTrue(a.cancel()!!.resume); assertNull(a.finish("old", 600))
        a.tap(1, "old", 40_000, true, 1000)
        assertNull(a.finish("new", 1600)); assertNull(a.pending)
        val new = a.tap(-1, "new", 80_000, false, 2000)
        assertEquals(80_000L, new.start); assertFalse(new.resume)
    }
}
