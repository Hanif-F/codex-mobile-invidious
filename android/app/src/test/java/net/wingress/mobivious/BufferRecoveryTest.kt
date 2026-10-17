package net.wingress.mobivious

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import net.wingress.mobivious.player.BufferRecovery
import net.wingress.mobivious.player.BufferRecoveryPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class BufferRecoveryTest {
    @Test fun threeDelayedRefreshesThenManualRecoveryAndDuplicateEvents() = runTest {
        var refreshed = 0
        val statuses = mutableListOf<Boolean>()
        val recovery = BufferRecovery(backgroundScope, { testScheduler.currentTime }, { refreshed++ }, statuses::add, { _, _ -> })
        for ((index, delay) in listOf(1000L, 2000L, 4000L).withIndex()) {
            val error = Any()
            assertTrue(recovery.failed(error)); assertTrue(recovery.failed(error))
            assertTrue(recovery.failed(Any())) // Another notification cannot create a second timer.
            runCurrent(); advanceTimeBy(delay - 1); runCurrent()
            assertEquals(index, refreshed)
            advanceTimeBy(1); runCurrent()
            assertEquals(index + 1, refreshed)
        }
        assertFalse(recovery.failed(Any())); assertFalse(recovery.recovering)
        advanceTimeBy(60_000); runCurrent(); assertEquals(3, refreshed)
        recovery.reset()
        assertTrue(recovery.failed(Any())); advanceTimeBy(1000); runCurrent()
        assertEquals(4, refreshed)
        assertEquals(listOf(true, false, true), statuses)
    }

    @Test fun cancellationInvalidatesPendingWorkAndReadyDoesNotResetBudget() = runTest {
        var refreshed = 0
        val recovery = BufferRecovery(backgroundScope, { testScheduler.currentTime }, { refreshed++ }, {}, { _, _ -> })
        recovery.failed(Any()); runCurrent(); advanceTimeBy(500); recovery.reset()
        advanceTimeBy(5000); runCurrent(); assertEquals(0, refreshed)
        assertEquals(0, recovery.attempts)
        recovery.failed(Any()); runCurrent(); advanceTimeBy(1000); runCurrent()
        recovery.ready(); assertFalse(recovery.recovering); assertEquals(1, recovery.attempts)
        recovery.failed(Any()); runCurrent(); advanceTimeBy(1999); runCurrent(); assertEquals(1, refreshed)
        advanceTimeBy(1); runCurrent(); assertEquals(2, refreshed)
    }

    @Test fun onlyThirtySecondsOfUninterruptedAdvancingPlaybackReplenishesBudget() = runTest {
        var now = 0L
        val recovery = BufferRecovery(backgroundScope, { now }, {}, {}, { _, _ -> })
        recovery.failed(Any()); recovery.ready()
        recovery.sample(true, 0)
        now = 29_000; recovery.sample(true, 29_000); assertEquals(1, recovery.attempts)
        recovery.sample(false, 29_000)
        now = 60_000; recovery.sample(true, 29_000)
        now = 89_000; recovery.sample(true, 58_000); assertEquals(1, recovery.attempts)
        recovery.interrupted() // A seek cannot count as continuous progress.
        recovery.sample(true, 100_000)
        now = 118_000; recovery.sample(true, 129_000); assertEquals(1, recovery.attempts)
        now = 119_000; recovery.sample(true, 130_000); assertEquals(0, recovery.attempts)
        recovery.failed(Any()); recovery.ready()
        now = 150_000; recovery.sample(true, 130_000)
        now = 180_000; recovery.sample(true, 130_000); assertEquals(1, recovery.attempts)
    }

    @Test fun policyIncludesRecoverableSourceAndDecoderFailuresButExcludesPermanentAndOfflineErrors() {
        val recoverable = listOf(1002, 2000, 2001, 2002, 2003, 2004, 2008, 3001, 3002, 4001, 4003)
        for (code in recoverable) {
            val error = PlaybackException("private message", IOException("private URL"), code)
            assertTrue("code=$code", BufferRecoveryPolicy.eligible(error, true))
            assertFalse(BufferRecoveryPolicy.eligible(error, false))
        }
        for (code in listOf(1000, 1001, 2005, 2006, 2007, 3003, 3004, 4002, 4004, 4005, 6000, 6002, 6006))
            assertFalse("code=$code", BufferRecoveryPolicy.eligible(PlaybackException("", null, code), true))
        for (cause in listOf(SecurityException("secret"), SSLHandshakeException("secret")))
            assertFalse(BufferRecoveryPolicy.eligible(PlaybackException("", IOException(cause), 2001), true))
    }

    @Test fun diagnosticsContainClassesAndCodesWithoutMessagesOrSignedUrls() {
        val error = PlaybackException("https://private.example/?token=secret", IOException("Authorization: secret"), 2001)
        val diagnostic = BufferRecoveryPolicy.diagnostic("testvideo01", error, "manifest")
        assertTrue(diagnostic.contains("video=testvideo01")); assertTrue(diagnostic.contains("code=2001"))
        assertTrue(diagnostic.contains("IOException")); assertTrue(diagnostic.contains("stage=manifest"))
        for (secret in listOf("https", "token", "secret", "Authorization")) assertFalse(diagnostic.contains(secret))
    }
}
