package ai.sotuvchi.app.core.format

import ai.sotuvchi.app.core.l10n.AppLanguage
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.l10n.Localizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Money is whole UZS sums as integers. No decimals, no tiyin. */
object Money {
    private const val NBSP = " "

    /** "450 000 so'm" in Uzbek, "450 000 сум" in Russian, "450 000 UZS" in English. */
    fun format(amount: Int, language: AppLanguage = Localizer.language): String =
        "${grouped(amount, NBSP)}$NBSP${currencyLabel(language)}"

    /** Compact form for charts and tiles: "3.2M", "450K". */
    fun compact(amount: Int): String {
        val value = abs(amount.toDouble())
        val sign = if (amount < 0) "-" else ""
        return when {
            value >= 1_000_000_000 -> sign + trimmed(value / 1_000_000_000) + "B"
            value >= 1_000_000 -> sign + trimmed(value / 1_000_000) + "M"
            value >= 1_000 -> sign + trimmed(value / 1_000) + "K"
            else -> sign + value.toInt().toString()
        }
    }

    /** Digits grouped by three with a space: 1 250 000. */
    fun grouped(amount: Int, separator: String = " "): String {
        val digits = abs(amount.toLong()).toString()
        val groups = ArrayDeque<String>()
        var end = digits.length
        while (end > 0) {
            val start = maxOf(end - 3, 0)
            groups.addFirst(digits.substring(start, end))
            end = start
        }
        return (if (amount < 0) "-" else "") + groups.joinToString(separator)
    }

    val currencyLabel: String get() = currencyLabel(Localizer.language)

    fun currencyLabel(language: AppLanguage): String = when (language) {
        AppLanguage.UZ -> "so'm"
        AppLanguage.RU -> "сум"
        AppLanguage.EN -> "UZS"
    }

    private fun trimmed(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
        else String.format(Locale.US, "%.1f", rounded)
    }
}

object DateText {
    /** Day-based filters on the server use Tashkent days (UTC+5). */
    val tashkent: ZoneId = ZoneId.of("Asia/Tashkent")

    private val locale: Locale get() = Localizer.language.locale
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun at(instant: Instant, zone: ZoneId = this.zone): ZonedDateTime = instant.atZone(zone)

    /** "5 min ago", "2 h ago", "3 d ago". */
    fun relative(date: Instant, now: Instant = Instant.now()): String {
        val seconds = abs(Duration.between(date, now).seconds)
        if (seconds < 60) return L("just now")
        val minutes = seconds / 60
        if (minutes < 60) return L("{count} min ago", mapOf("count" to minutes))
        val hours = minutes / 60
        if (hours < 24) return L("{count} h ago", mapOf("count" to hours))
        return L("{count} d ago", mapOf("count" to hours / 24))
    }

    /** "15:30". */
    fun time(date: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(at(date))

    private fun dayMonth(date: ZonedDateTime, withYear: Boolean, wide: Boolean = false): String {
        val month = if (wide) "MMMM" else "MMM"
        val pattern = if (withYear) "d $month yyyy" else "d $month"
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    private fun sameDay(a: ZonedDateTime, b: ZonedDateTime) = a.toLocalDate() == b.toLocalDate()

    /** "Today, 15:30", "Yesterday, 09:10" or "17 Oct, 15:30". */
    fun dayTime(date: Instant, now: Instant = Instant.now()): String {
        val d = at(date)
        val n = at(now)
        if (sameDay(d, n)) return "${L("Today")}, ${time(date)}"
        if (sameDay(d, n.minusDays(1))) return "${L("Yesterday")}, ${time(date)}"
        return "${dayMonth(d, withYear = false)}, ${time(date)}"
    }

    /** "18 Nov", or "18 Nov 2027" outside the current year. */
    fun shortDate(date: Instant, now: Instant = Instant.now()): String {
        val d = at(date)
        return dayMonth(d, withYear = d.year != at(now).year)
    }

    /** Chat day separator: "Today", "Yesterday" or "17 October". */
    fun day(date: Instant, now: Instant = Instant.now()): String {
        val d = at(date)
        val n = at(now)
        if (sameDay(d, n)) return L("Today")
        if (sameDay(d, n.minusDays(1))) return L("Yesterday")
        return dayMonth(d, withYear = d.year != n.year, wide = true)
    }

    /** Short list timestamp: time today, weekday this week, else date. */
    fun listStamp(date: Instant, now: Instant = Instant.now()): String {
        val d = at(date)
        val n = at(now)
        if (sameDay(d, n)) return time(date)
        val days = ChronoUnit.DAYS.between(d.toLocalDate(), n.toLocalDate())
        if (days in 0..6) return DateTimeFormatter.ofPattern("EEE", locale).format(d)
        return dayMonth(d, withYear = false)
    }

    /** Parses the server's "YYYY-MM-DD" day keys as Tashkent days. */
    fun parseDay(value: String): LocalDate? {
        val parts = value.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        return runCatching { LocalDate.of(parts[0], parts[1], parts[2]) }.getOrNull()
    }

    /** "17 Oct" for chart axes. */
    fun shortDay(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMM", locale).format(date)

    /** The Tashkent calendar day an instant falls on, as the server's "YYYY-MM-DD" key. */
    fun tashkentDay(instant: Instant): LocalDate = instant.atZone(tashkent).toLocalDate()
}

/** Countdown helpers shared by sign-in and the chat reply window. */
object Countdown {
    /** "0:42" or "12:05". */
    fun clock(seconds: Int): String {
        val value = maxOf(seconds, 0)
        return String.format(Locale.US, "%d:%02d", value / 60, value % 60)
    }

    /** "5 h 20 min", "20 min". */
    fun hoursMinutes(duration: Duration): String {
        val totalMinutes = maxOf(duration.toMinutes(), 0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) L("{hours} h {minutes} min", mapOf("hours" to hours, "minutes" to minutes))
        else L("{minutes} min", mapOf("minutes" to maxOf(minutes, 1)))
    }
}
