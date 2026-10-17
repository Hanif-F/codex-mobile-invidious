package net.wingress.mobivious.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.net.ssl.SSLException

/** Only terminal player failures reach this policy, after Media3's own load retries. */
internal object BufferRecoveryPolicy {
    fun eligible(error: PlaybackException, streamed: Boolean): Boolean {
        if (!streamed) return false
        val causes = causes(error)
        if (causes.any { it is SecurityException || it is SSLException }) return false
        if (causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().any { it.responseCode in listOf(401, 407) }) return false
        return error.errorCode in setOf(
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
        )
    }

    private fun causes(error: Throwable) = generateSequence(error) { it.cause }.take(8).toList()

    // Deliberately never include exception messages, URIs, headers or response bodies.
    fun diagnostic(videoId: String, error: PlaybackException, stage: String?): String {
        val causes = causes(error)
        val status = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        return "video=$videoId code=${error.errorCode} cause=${causes.joinToString(">") { it.javaClass.simpleName }} http=${status ?: "unknown"} stage=${stage ?: "unknown"}"
    }
}

/** Main-thread recovery budget. Clearing an error or reaching READY does not replenish it. */
internal class BufferRecovery(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val refresh: () -> Unit,
    private val status: (Boolean) -> Unit,
    private val outcome: (String, Int) -> Unit,
) {
    var attempts = 0
        private set
    var recovering = false
        private set
    private var pending: Job? = null
    private var generation = 0L
    private var lastFailure: Any? = null
    private var handledLastFailure = false
    private var stableSince: Long? = null
    private var lastSample: Pair<Long, Long>? = null

    fun failed(failure: Any): Boolean {
        interrupted()
        if (lastFailure === failure) return handledLastFailure
        lastFailure = failure
        if (pending != null) return true.also { handledLastFailure = it }
        if (attempts == 3) {
            setRecovering(false)
            outcome("exhausted", attempts)
            return false.also { handledLastFailure = it }
        }
        handledLastFailure = true
        attempts++
        val ticket = generation
        setRecovering(true)
        outcome("scheduled", attempts)
        pending = scope.launch {
            delay(longArrayOf(1000, 2000, 4000)[attempts - 1])
            pending = null
            if (ticket == generation) {
                outcome("refresh", attempts)
                refresh()
            }
        }
        return true
    }

    fun ready() {
        pending?.cancel(); pending = null
        if (recovering) outcome("ready", attempts)
        setRecovering(false)
    }

    fun interrupted() { stableSince = null; lastSample = null }

    fun sample(playing: Boolean, positionMs: Long) {
        val time = now()
        val previous = lastSample
        if (!playing || previous == null || positionMs <= previous.second) stableSince = null
        else if (stableSince == null) stableSince = previous.first
        lastSample = if (playing) time to positionMs else null
        if (attempts > 0 && stableSince?.let { time - it >= 30_000 } == true) {
            attempts = 0
            lastFailure = null
            outcome("stable", 0)
        }
    }

    fun reset() {
        generation++
        pending?.cancel(); pending = null
        if (recovering) outcome("cancelled", attempts)
        attempts = 0; lastFailure = null; handledLastFailure = false
        interrupted()
        setRecovering(false)
    }

    private fun setRecovering(value: Boolean) {
        if (recovering == value) return
        recovering = value
        status(value)
    }
}
