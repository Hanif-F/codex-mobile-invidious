package net.wingress.mobivious.player

import net.wingress.mobivious.data.*

/** Playback-session history only; settings and data are owned by the service. */
class SponsorBlockEngine {
    var settings = SponsorBlockSettings()
    var segments: List<SponsorBlockSegment> = emptyList()
    private val skipped = mutableSetOf<String>()
    private val dismissed = mutableSetOf<String>()
    data class Decision(val visible: List<SponsorBlockSegment>, val active: SponsorBlockSegment? = null,
        val seek: Long? = null, val categories: Set<SponsorBlockCategory> = emptySet())
    fun reset() { segments = emptyList(); skipped.clear(); dismissed.clear(); settings = SponsorBlockSettings() }
    fun dismiss(id: String) { dismissed.add(id) }
    fun observePosition(time: Long) { segments.forEach { if (time < it.start || time >= it.end) dismissed.remove(it.id) } }
    fun evaluate(time: Long, duration: Long): Decision {
        observePosition(time)
        if (!settings.usable || duration <= 0) return Decision(emptyList())
        val visible = segments.filter { settings.modes[it.category] != SponsorBlockMode.DISABLED && it.start < duration }
        if (time >= duration) return Decision(visible)
        val current = visible.filter { it.start <= time && time < it.end }
        val autos = current.filter { settings.modes[it.category] == SponsorBlockMode.AUTO && it.id !in skipped }
        if (autos.isNotEmpty()) {
            var end = autos.maxOf { it.end }
            val merged = autos.toMutableSet()
            do {
                val oldEnd = end
                visible.filter { settings.modes[it.category] == SponsorBlockMode.AUTO && it.id !in skipped &&
                    it.start <= end && it.start < duration && it.end > time }.forEach { merged.add(it); end = maxOf(end, it.end) }
            } while (end != oldEnd)
            skipped.addAll(merged.map { it.id })
            return Decision(visible, seek = end.coerceAtMost(duration), categories = merged.map { it.category }.toSet())
        }
        val manual = current.filter { (settings.modes[it.category] == SponsorBlockMode.MANUAL ||
            settings.modes[it.category] == SponsorBlockMode.AUTO && it.id in skipped) && it.id !in dismissed }
            .minWithOrNull(compareBy<SponsorBlockSegment> { it.end }.thenBy { it.start })
        return Decision(visible, active = manual)
    }
}
