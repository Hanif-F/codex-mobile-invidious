package net.wingress.mobivious.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.json.JSONObject

enum class CountPrecision {
    EXACT, APPROXIMATE, UNKNOWN, UNVERIFIED;
    companion object {
        fun parse(value: String) = when (value) {
            "exact" -> EXACT
            "approximate" -> APPROXIMATE
            "unknown" -> UNKNOWN
            else -> UNVERIFIED
        }
    }
}

/** Display rounding must never change the source value or imply additional precision. */
object DisplayFormats {
    private val thousand = BigDecimal(1000)
    private val suffixes = listOf("", "K", "M", "B", "T")
    private val numericText = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")
    private val compactText = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([KMBT])$", RegexOption.IGNORE_CASE)

    fun compact(value: Long?, locale: Locale = Locale.getDefault()): String =
        value?.takeIf { it >= 0 }?.let { compact(BigDecimal.valueOf(it), locale) }.orEmpty()

    private fun compact(value: BigDecimal, locale: Locale): String {
        var scaled = value
        var unit = 0
        while (scaled >= thousand && unit < suffixes.lastIndex) { scaled = scaled.movePointLeft(3); unit++ }
        var rounded = scaled.setScale(if (unit == 0) 0 else 1, RoundingMode.HALF_UP)
        if (rounded >= thousand && unit < suffixes.lastIndex) {
            rounded = rounded.movePointLeft(3).setScale(1, RoundingMode.HALF_UP); unit++
        }
        return number(rounded, if (unit == 0) 0 else 1, locale) + suffixes[unit]
    }

    fun grouped(value: Long?, locale: Locale = Locale.getDefault()): String =
        value?.takeIf { it >= 0 }?.let { NumberFormat.getIntegerInstance(locale).format(it) }.orEmpty()

    fun audience(value: Long?, singular: String, plural: String = singular + "s", locale: Locale = Locale.getDefault()): String =
        label(compact(value, locale), value, singular, plural)

    fun inventory(value: Long?, singular: String, plural: String = singular + "s", locale: Locale = Locale.getDefault()): String =
        label(grouped(value, locale), value, singular, plural)

    private fun label(text: String, value: Long?, singular: String, plural: String) =
        if (text.isBlank()) "" else "$text ${if (value == 1L) singular else plural}"

    fun subscribers(raw: String, locale: Locale = Locale.getDefault()): String {
        val text = raw.trim().replace(Regex("\\s+subscribers?$", RegexOption.IGNORE_CASE), "")
        if (text.isBlank() || text.lowercase(Locale.ROOT) in listOf("-", "—", "n/a", "null", "nan", "undefined")) return ""
        val short = compactText.matchEntire(text)
        if (short != null) {
            val scale = suffixes.indexOf(short.groupValues[2].uppercase(Locale.ROOT))
            return compact(short.groupValues[1].toBigDecimal() * thousand.pow(scale), locale) + " subscribers"
        }
        if (numericText.matches(text)) {
            val value = runCatching { text.replace(",", "").toBigDecimal().longValueExact() }.getOrNull()
            return audience(value, "subscriber", locale = locale)
        }
        // Preserve unfamiliar localized upstream text rather than guessing its value.
        return if (text.startsWith('-')) "" else "$text subscribers"
    }

    private fun number(value: BigDecimal, decimals: Int, locale: Locale) = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = decimals; minimumFractionDigits = 0; roundingMode = RoundingMode.HALF_UP
    }.format(value)

    fun bytes(value: Long?, locale: Locale = Locale.getDefault()): String {
        if (value == null || value < 0) return ""
        val units = listOf("B", "kB", "MB", "GB", "TB", "PB", "EB")
        var scaled = BigDecimal.valueOf(value); var unit = 0
        while (scaled >= thousand && unit < units.lastIndex) { scaled = scaled.movePointLeft(3); unit++ }
        var rounded = scaled.setScale(if (unit == 0) 0 else 1, RoundingMode.HALF_UP)
        if (rounded >= thousand && unit < units.lastIndex) { rounded = rounded.movePointLeft(3); unit++ }
        return "${number(rounded, if (unit == 0) 0 else 1, locale)} ${units[unit]}"
    }

    fun bitrate(value: Long, locale: Locale = Locale.getDefault()): String = when {
        value >= 1_000_000 -> "${number(BigDecimal.valueOf(value).movePointLeft(6), 2, locale)} Mbps"
        value >= 1000 -> "${number(BigDecimal.valueOf(value).movePointLeft(3), 1, locale)} kbps"
        value > 0 -> "${grouped(value, locale)} bps"
        else -> ""
    }

    fun duration(seconds: Long): String = if (seconds <= 0) "" else clock(seconds)
    fun clock(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return if (safe >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", safe / 3600, safe / 60 % 60, safe % 60)
        else String.format(Locale.ROOT, "%d:%02d", safe / 60, safe % 60)
    }

    fun publication(seconds: Long?, now: Long = System.currentTimeMillis() / 1000): Long? =
        seconds?.takeIf { it >= 1_104_537_600 && it <= now + 300 }

    fun date(value: LocalDate?, locale: Locale = Locale.getDefault()): String = value?.let {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(it)
    }.orEmpty()

    fun timestamp(seconds: Long?, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault(), dateOnly: Boolean = false): String =
        seconds?.takeIf { it in 1..253_402_300_799L }?.let {
            runCatching {
                val format = if (dateOnly) DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                format.withLocale(locale).withZone(zone).format(Instant.ofEpochSecond(it))
            }.getOrNull()
        }.orEmpty()
}

/** Accept API integers and integer strings, without truncating fractions or overflowing. */
fun JSONObject.nonNegativeLong(key: String): Long? = when (val value = opt(key)) {
    is Number, is String -> runCatching { value.toString().toBigDecimal().longValueExact() }.getOrNull()?.takeIf { it >= 0 }
    else -> null
}
