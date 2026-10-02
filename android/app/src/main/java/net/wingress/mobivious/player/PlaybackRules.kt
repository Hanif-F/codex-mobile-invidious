package net.wingress.mobivious.player

object PlaybackRules {
    fun resume(saved: Long, duration: Long, explicit: Long?): Long {
        val start = explicit ?: saved.takeIf { it > 0 && (duration <= 0 || it < duration - 20) } ?: 0
        return if (duration > 0) start.coerceIn(0, duration) else start.coerceAtLeast(0)
    }
    fun save(position: Long, duration: Long, ended: Boolean): Long = if (ended || duration > 0 && position >= duration - 15) 0 else position.coerceAtLeast(0)
}
