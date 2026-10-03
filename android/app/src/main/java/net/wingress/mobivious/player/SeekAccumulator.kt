package net.wingress.mobivious.player

data class PendingSeek(val mediaId: String, val start: Long, val offset: Long, val resume: Boolean, val deadline: Long) {
    fun target(duration: Long) = PlaybackRules.seek(start + offset, duration)
}

/** A tap changes the requested delta, never the player's position. Uses monotonic milliseconds. */
class SeekAccumulator {
    companion object { const val STEP = 10_000L; const val DELAY = 600L }
    var pending: PendingSeek? = null
        private set
    fun tap(direction: Int, mediaId: String, position: Long, playing: Boolean, now: Long): PendingSeek {
        require(direction == -1 || direction == 1)
        val old = pending?.takeIf { it.mediaId == mediaId }
        val step = direction * STEP
        // Bound arithmetic even for a very long tap sequence with unknown duration.
        val offset = if (old == null || (old.offset > 0) != (step > 0)) step
            else (old.offset + step).coerceIn(-Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong())
        return PendingSeek(mediaId, old?.start ?: position, offset, old?.resume ?: playing, now + DELAY).also { pending = it }
    }
    fun finish(mediaId: String, now: Long): PendingSeek? {
        val value = pending ?: return null
        if (value.mediaId != mediaId) { cancel(); return null }
        if (now < value.deadline) return null
        pending = null
        return value
    }
    fun cancel(): PendingSeek? = pending.also { pending = null }
}
