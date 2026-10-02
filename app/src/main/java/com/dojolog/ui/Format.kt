package com.dojolog.ui

import com.dojolog.domain.ActivityBucket
import com.dojolog.domain.BucketSize
import com.dojolog.domain.MatchRecord
import com.dojolog.domain.MatchResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** Display formatting shared by every screen. */
object Fmt {
    private val locale: Locale get() = Locale.getDefault()

    fun score(value: Float): String = when {
        value <= 0f -> "–"
        value % 1f == 0f -> value.toInt().toString()
        else -> String.format(locale, "%.1f", value)
    }

    fun decimal(value: Float): String = String.format(locale, "%.1f", value)

    /** A single session's length: "45 min", "1 h 30 min". */
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /** A total of time: "45 min", "12.5 h". */
    fun hours(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> String.format(locale, "%.1f h", minutes / 60f)
    }

    fun count(n: Int, singular: String, plural: String = singular + "s"): String =
        "$n ${if (n == 1) singular else plural}"

    fun fullDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    fun mediumDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun dayMonth(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    fun weekdayDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))

    fun monthYear(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    fun relativeDay(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days == -1L -> "Tomorrow"
            days < 0 -> "In ${-days} days"
            days < 60 -> "$days days ago"
            else -> mediumDate(date)
        }
    }

    /** A record in a few characters: "3W 1L 0D", plus rounds without a result: "· 2 unscored". */
    fun record(record: MatchRecord): String {
        val parts = mutableListOf<String>()
        if (record.scored > 0) parts += "${record.wins}W ${record.losses}L ${record.draws}D"
        if (record.unscored > 0) parts += "${record.unscored} unscored"
        return parts.joinToString(" · ").ifEmpty { "No matchups" }
    }

    /** The same record in words, for screen readers: "3 wins, 1 loss, no draws". */
    fun recordSpoken(record: MatchRecord): String {
        if (record.matchups == 0) return "No matchups"
        val parts = mutableListOf<String>()
        if (record.scored > 0) {
            parts += count(record.wins, "win")
            parts += count(record.losses, "loss", "losses")
            parts += count(record.draws, "draw")
        }
        if (record.unscored > 0) parts += count(record.unscored, "unscored round")
        return parts.joinToString(", ")
    }

    /** One letter for a result, as used in records: W, L, D, or a dash when unscored. */
    fun resultLetter(result: MatchResult): String = when (result) {
        MatchResult.WIN -> "W"
        MatchResult.LOSS -> "L"
        MatchResult.DRAW -> "D"
        MatchResult.NONE -> "–"
    }

    /** Short x-axis label for an activity bucket. */
    fun bucketLabel(bucket: ActivityBucket): String = when (bucket.size) {
        BucketSize.WEEK -> dayMonth(bucket.start)
        BucketSize.MONTH -> bucket.start.format(DateTimeFormatter.ofPattern("MMM", locale))
        BucketSize.YEAR -> bucket.start.year.toString()
    }

    /** Longer name for an activity bucket, used in chart read-outs. */
    fun bucketName(bucket: ActivityBucket): String = when (bucket.size) {
        BucketSize.WEEK -> "Week of ${dayMonth(bucket.start)}"
        BucketSize.MONTH -> monthYear(YearMonth.from(bucket.start))
        BucketSize.YEAR -> bucket.start.year.toString()
    }
}

fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

/** Milliseconds per day, for converting the Material date picker's UTC millis. */
const val DAY_MILLIS = 86_400_000L
