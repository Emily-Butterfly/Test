package com.dojolog.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

enum class StatsPeriod(val label: String, val days: Long?) {
    DAYS_30("30 days", 30),
    DAYS_90("90 days", 90),
    YEAR("12 months", 365),
    ALL("All time", null),
}

enum class BucketSize { WEEK, MONTH, YEAR }

/** Activity inside [start, start + size). */
data class ActivityBucket(
    val start: LocalDate,
    val size: BucketSize,
    val sessions: Int,
    val minutes: Int = 0,
    val reps: Int = 0,
)

data class PeriodSummary(
    val sessions: Int,
    val trainingDays: Int,
    val totalMinutes: Int,
    val averageOverall: Float?,
    val averageMinutes: Int,
    /** Sessions with an overall rating. */
    val ratedSessions: Int = 0,
)

data class TechniqueSummary(
    val technique: Technique,
    val sessions: Int,
    val totalReps: Int,
    val averageQuality: Float?,
    /** How many practices had a quality rating. */
    val ratedCount: Int = 0,
    val firstPracticed: LocalDate?,
    val lastPracticed: LocalDate?,
    /** The martial arts it was practised in, most sessions first. */
    val arts: List<ArtCount> = emptyList(),
)

/** How many sessions of one martial art ([key] is its [disciplineKey]) included a technique. */
data class ArtCount(val key: String, val name: String, val sessions: Int)

/** One time a technique was practised. */
data class PracticeRecord(
    val sessionId: Long,
    val date: LocalDate,
    val discipline: String,
    val reps: Int,
    val quality: Int,
    val notes: String,
)

data class TechniqueDetail(
    val summary: TechniqueSummary,
    /** Newest first. */
    val history: List<PracticeRecord>,
    /** The last six months, oldest first. */
    val monthly: List<ActivityBucket>,
    /** Practices with a quality rating, oldest first, at most [TREND_POINTS]. */
    val qualityTrend: List<PracticeRecord>,
    val sessionsLast30Days: Int,
)

/** [key] is the art's [disciplineKey]; [name] is how it was first written (see [Stats.artNames]). */
data class DisciplineShare(val key: String, val name: String, val sessions: Int, val minutes: Int)

data class Overview(
    val summary: PeriodSummary,
    val categoryAverages: Map<RatingCategory, Float?>,
    val activity: List<ActivityBucket>,
    /** Rated sessions, oldest first, at most [TREND_POINTS]. */
    val ratingTrend: List<TrainingSession>,
    val topTechniques: List<TechniqueSummary>,
    val disciplines: List<DisciplineShare>,
    /** Your record over the matchups in the period. */
    val record: MatchRecord = MatchRecord(),
    /** Different people faced in the period. */
    val opponentsFaced: Int = 0,
)

/** Consecutive calendar weeks with at least one session. */
data class Streaks(val currentWeeks: Int, val longestWeeks: Int)

/** One martial art trained on a calendar day, at the colour step of its best rating there. */
data class DayMark(val disciplineKey: String, val level: Int)

const val TREND_POINTS = 30

/** How many martial arts one calendar day shows side by side. */
const val MAX_DAY_MARKS = 3

/** Identity of a martial art: names are compared ignoring case and surrounding spaces. */
fun disciplineKey(name: String): String = name.trim().lowercase()

object Stats {

    /** Colour step for a score: 0 = unrated, 1..4 from weakest to strongest. */
    fun heatLevel(score: Float): Int = when {
        score <= 0f -> 0
        score < 5f -> 1
        score < 7f -> 2
        score < 9f -> 3
        else -> 4
    }

    /**
     * Colour slot of every named martial art, in the order each was first logged, so an art
     * keeps its colour while others are added later. Unnamed sessions get no slot.
     */
    fun disciplineSlots(sessions: List<TrainingSession>): Map<String, Int> = reconcileSlots(emptyMap(), sessions)

    /**
     * Updates the [stored] colour slots for the current [sessions]: every art that is still
     * logged keeps its slot, arts with no sessions left give theirs up, and new arts take the
     * lowest free slots in the order they were first logged. A new art listed in [preferred]
     * (e.g. from an imported backup) gets that slot instead when it is free. Unnamed sessions
     * get no slot.
     */
    fun reconcileSlots(
        stored: Map<String, Int>,
        sessions: List<TrainingSession>,
        preferred: Map<String, Int> = emptyMap(),
    ): Map<String, Int> {
        val firstLogged = sessions
            .filter { disciplineKey(it.discipline).isNotEmpty() }
            .groupBy { disciplineKey(it.discipline) }
            .mapValues { (_, group) -> group.minOf { it.createdAt } }
        val slots = stored.filterKeys { it in firstLogged }.toMutableMap()
        val taken = slots.values.toMutableSet()
        val newKeys = firstLogged.entries
            .filter { it.key !in slots }
            .sortedWith(compareBy<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { it.key }
        for (key in newKeys) {
            val slot = preferred[key] ?: continue
            if (slot >= 0 && taken.add(slot)) slots[key] = slot
        }
        var next = 0
        for (key in newKeys) {
            if (key in slots) continue
            while (next in taken) next++
            slots[key] = next
            taken += next
        }
        return slots
    }

    /**
     * The martial arts trained on one day, each at the colour step of its best rating that
     * day (0 when none of its sessions is rated), in slot order. With more than [maxMarks]
     * arts, the best rated ones are kept.
     */
    fun dayMarks(
        daySessions: List<TrainingSession>,
        slots: Map<String, Int>,
        maxMarks: Int = MAX_DAY_MARKS,
    ): List<DayMark> {
        val slotOrder = compareBy<DayMark> { slots[it.disciplineKey] ?: Int.MAX_VALUE }.thenBy { it.disciplineKey }
        return daySessions
            .groupBy { disciplineKey(it.discipline) }
            .map { (key, group) -> DayMark(key, heatLevel(group.maxOf { it.overall })) }
            .sortedWith(compareByDescending<DayMark> { it.level }.then(slotOrder))
            .take(maxMarks)
            .sortedWith(slotOrder)
    }

    /** First day shown in a month grid: the start of the week containing the 1st. */
    fun calendarGridStart(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate =
        weekStart(month.atDay(1), firstDayOfWeek)

    /** Last day shown in a month grid: the end of the week containing the last day. */
    fun calendarGridEnd(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate =
        weekStart(month.atEndOfMonth(), firstDayOfWeek).plusDays(6)

    fun summarize(sessions: List<TrainingSession>): PeriodSummary {
        val rated = sessions.filter { it.isRated }
        val minutes = sessions.sumOf { it.durationMinutes }
        return PeriodSummary(
            sessions = sessions.size,
            trainingDays = sessions.map { it.date }.distinct().size,
            totalMinutes = minutes,
            averageOverall = if (rated.isEmpty()) null else rated.map { it.overall }.average().toFloat(),
            averageMinutes = if (sessions.isEmpty()) 0 else minutes / sessions.size,
            ratedSessions = rated.size,
        )
    }

    fun inPeriod(sessions: List<TrainingSession>, period: StatsPeriod, today: LocalDate): List<TrainingSession> {
        val days = period.days ?: return sessions
        val start = today.minusDays(days - 1)
        return sessions.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
    }

    fun categoryAverages(sessions: List<TrainingSession>): Map<RatingCategory, Float?> =
        RatingCategory.entries.associateWith { category ->
            val scores = sessions.map { it.ratings[category] }.filter { it > 0 }
            if (scores.isEmpty()) null else scores.average().toFloat()
        }

    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    /**
     * Sessions grouped for the activity chart: weekly for short periods, monthly for a
     * year, and monthly or yearly for all time depending on how far back the log goes.
     */
    fun activity(
        sessions: List<TrainingSession>,
        period: StatsPeriod,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): List<ActivityBucket> {
        val days = period.days
        val size: BucketSize
        val starts: List<LocalDate>
        when {
            days != null && days <= 90 -> {
                size = BucketSize.WEEK
                val last = weekStart(today, firstDayOfWeek)
                val first = weekStart(today.minusDays(days - 1), firstDayOfWeek)
                starts = generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.toList()
            }
            days != null -> {
                size = BucketSize.MONTH
                starts = monthStarts(YearMonth.from(today), 12)
            }
            else -> {
                val firstDate = sessions.minByOrNull { it.date.toEpochDay() }?.date
                    ?.takeIf { it.isBefore(today) } ?: today
                val span = ChronoUnit.MONTHS.between(YearMonth.from(firstDate), YearMonth.from(today)).toInt() + 1
                if (span <= 24) {
                    size = BucketSize.MONTH
                    starts = monthStarts(YearMonth.from(today), max(span, 6))
                } else {
                    size = BucketSize.YEAR
                    starts = (firstDate.year..today.year).map { LocalDate.of(it, 1, 1) }
                }
            }
        }
        return starts.map { start ->
            val end = bucketEnd(start, size)
            val inBucket = sessions.filter { !it.date.isBefore(start) && it.date.isBefore(end) }
            ActivityBucket(start, size, inBucket.size, inBucket.sumOf { it.durationMinutes })
        }
    }

    fun streaks(dates: Collection<LocalDate>, today: LocalDate, firstDayOfWeek: DayOfWeek): Streaks {
        val weeks = dates.filter { !it.isAfter(today) }
            .map { weekStart(it, firstDayOfWeek) }
            .distinct()
            .sortedBy { it.toEpochDay() }
        if (weeks.isEmpty()) return Streaks(0, 0)

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (week in weeks) {
            run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
            longest = max(longest, run)
            previous = week
        }

        // The streak is still alive if this week has no session yet but last week did.
        val weekSet = weeks.toHashSet()
        val thisWeek = weekStart(today, firstDayOfWeek)
        var cursor = if (thisWeek in weekSet) thisWeek else thisWeek.minusWeeks(1)
        var current = 0
        while (cursor in weekSet) {
            current++
            cursor = cursor.minusWeeks(1)
        }
        return Streaks(current, longest)
    }

    /**
     * The display name of every named martial art: how it was written in the first session
     * logged with it. Spellings that differ only in case or spaces are one art, and every
     * screen names it the same way.
     */
    fun artNames(sessions: List<TrainingSession>): Map<String, String> =
        sessions
            .filter { disciplineKey(it.discipline).isNotEmpty() }
            .groupBy { disciplineKey(it.discipline) }
            .mapValues { (_, group) ->
                group.minWith(compareBy<TrainingSession> { it.createdAt }.thenBy { it.id }).discipline.trim()
            }

    /** [names] are the art names to show (see [artNames]); pass them when [sessions] is a subset. */
    fun techniqueSummaries(
        techniques: List<Technique>,
        sessions: List<TrainingSession>,
        names: Map<String, String> = artNames(sessions),
    ): List<TechniqueSummary> {
        val records = practiceRecords(sessions)
        return techniques.map { summaryOf(it, records[it.id].orEmpty(), names) }
    }

    fun techniqueDetail(technique: Technique, sessions: List<TrainingSession>, today: LocalDate): TechniqueDetail {
        val records = practiceRecords(sessions)[technique.id].orEmpty()
            .sortedWith(compareBy<PracticeRecord> { it.date.toEpochDay() }.thenBy { it.sessionId })
        val thisMonth = YearMonth.from(today)
        val monthly = monthStarts(thisMonth, 6).map { start ->
            val month = YearMonth.from(start)
            val inMonth = records.filter { YearMonth.from(it.date) == month }
            ActivityBucket(
                start = start,
                size = BucketSize.MONTH,
                sessions = inMonth.map { it.sessionId }.distinct().size,
                reps = inMonth.sumOf { it.reps },
            )
        }
        val since = today.minusDays(29)
        return TechniqueDetail(
            summary = summaryOf(technique, records, artNames(sessions)),
            history = records.reversed(),
            monthly = monthly,
            qualityTrend = records.filter { it.quality > 0 }.takeLast(TREND_POINTS),
            sessionsLast30Days = records
                .filter { !it.date.isBefore(since) && !it.date.isAfter(today) }
                .map { it.sessionId }
                .distinct()
                .size,
        )
    }

    /**
     * The techniques practised in the most sessions, optionally only counting sessions of
     * one martial art ([artKey] is its [disciplineKey]).
     */
    fun topTechniques(
        techniques: List<Technique>,
        sessions: List<TrainingSession>,
        artKey: String? = null,
        limit: Int = 5,
        names: Map<String, String> = artNames(sessions),
    ): List<TechniqueSummary> {
        val counted = if (artKey == null) sessions else sessions.filter { disciplineKey(it.discipline) == artKey }
        return techniqueSummaries(techniques, counted, names)
            .filter { it.sessions > 0 }
            .sortedWith(
                compareByDescending<TechniqueSummary> { it.sessions }
                    .thenByDescending { it.totalReps }
                    .thenBy { it.technique.name.lowercase() },
            )
            .take(limit)
    }

    /** [techniqueArt] limits the top techniques to one martial art; null counts every art. */
    fun overview(
        sessions: List<TrainingSession>,
        techniques: List<Technique>,
        period: StatsPeriod,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        techniqueArt: String? = null,
    ): Overview {
        val selected = inPeriod(sessions, period, today)
        val names = artNames(sessions)
        val top = topTechniques(techniques, selected, techniqueArt, names = names)
        val disciplines = selected
            .groupBy { disciplineKey(it.discipline) }
            .map { (key, group) ->
                DisciplineShare(
                    key = key,
                    name = names[key] ?: "Unspecified",
                    sessions = group.size,
                    minutes = group.sumOf { it.durationMinutes },
                )
            }
            .sortedByDescending { it.minutes }
        val trend = selected
            .filter { it.isRated }
            .sortedWith(compareBy<TrainingSession> { it.date.toEpochDay() }.thenBy { it.createdAt })
            .takeLast(TREND_POINTS)
        return Overview(
            summary = summarize(selected),
            categoryAverages = categoryAverages(selected),
            activity = activity(sessions, period, today, firstDayOfWeek),
            ratingTrend = trend,
            topTechniques = top,
            disciplines = disciplines,
            record = OpponentStats.record(selected),
            opponentsFaced = OpponentStats.opponentsFaced(selected),
        )
    }

    /**
     * Evenly spaced axis ticks from 0 that cover [maxValue] with at most [maxTicks]
     * intervals, using 1/2/5 steps. [minStep] keeps count axes on whole numbers.
     */
    fun niceTicks(maxValue: Float, maxTicks: Int = 4, minStep: Float = 0f): List<Float> {
        val target = if (maxValue > 0f) maxValue else 1f
        val rough = target / maxTicks
        val magnitude = 10.0.pow(floor(log10(rough.toDouble())))
        val step = listOf(1.0, 2.0, 5.0, 10.0)
            .map { it * magnitude }
            .first { it >= rough - 1e-9 }
            .toFloat()
            .coerceAtLeast(minStep)
        val intervals = ceil(target / step - 1e-6f).toInt().coerceAtLeast(1)
        return (0..intervals).map { it * step }
    }

    private fun summaryOf(technique: Technique, records: List<PracticeRecord>, names: Map<String, String>): TechniqueSummary {
        val qualities = records.map { it.quality }.filter { it > 0 }
        return TechniqueSummary(
            technique = technique,
            sessions = records.map { it.sessionId }.distinct().size,
            totalReps = records.sumOf { it.reps },
            averageQuality = if (qualities.isEmpty()) null else qualities.average().toFloat(),
            ratedCount = qualities.size,
            firstPracticed = records.minByOrNull { it.date.toEpochDay() }?.date,
            lastPracticed = records.maxByOrNull { it.date.toEpochDay() }?.date,
            arts = records
                .groupBy { disciplineKey(it.discipline) }
                .filterKeys { it.isNotEmpty() }
                .map { (key, group) ->
                    ArtCount(
                        key = key,
                        name = names[key] ?: group.first().discipline.trim(),
                        sessions = group.map { it.sessionId }.distinct().size,
                    )
                }
                .sortedWith(compareByDescending<ArtCount> { it.sessions }.thenBy { it.key }),
        )
    }

    private fun practiceRecords(sessions: List<TrainingSession>): Map<Long, List<PracticeRecord>> {
        val byTechnique = HashMap<Long, MutableList<PracticeRecord>>()
        for (session in sessions) {
            for (entry in session.techniques) {
                byTechnique.getOrPut(entry.techniqueId) { mutableListOf() } += PracticeRecord(
                    sessionId = session.id,
                    date = session.date,
                    discipline = session.discipline,
                    reps = entry.reps,
                    quality = entry.quality,
                    notes = entry.notes,
                )
            }
        }
        return byTechnique
    }

    private fun monthStarts(last: YearMonth, count: Int): List<LocalDate> =
        (count - 1 downTo 0).map { last.minusMonths(it.toLong()).atDay(1) }

    private fun bucketEnd(start: LocalDate, size: BucketSize): LocalDate = when (size) {
        BucketSize.WEEK -> start.plusWeeks(1)
        BucketSize.MONTH -> start.plusMonths(1)
        BucketSize.YEAR -> start.plusYears(1)
    }
}
