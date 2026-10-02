package com.dojolog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsTest {

    private val today = LocalDate.of(2026, 10, 2) // a Friday
    private val armbar = Technique(1, "Armbar", TechniqueCategory.SUBMISSION)
    private val jab = Technique(2, "Jab", TechniqueCategory.STRIKE)

    private fun session(
        id: Long,
        date: LocalDate,
        minutes: Int = 60,
        overall: Float = 0f,
        ratings: Ratings = Ratings(),
        discipline: String = "BJJ",
        techniques: List<TechniqueEntry> = emptyList(),
    ) = TrainingSession(
        id = id,
        date = date,
        durationMinutes = minutes,
        discipline = discipline,
        type = SessionType.CLASS,
        overall = overall,
        ratings = ratings,
        techniques = techniques,
    )

    private fun entry(technique: Technique, reps: Int = 0, quality: Int = 0) =
        TechniqueEntry(technique.id, technique.name, technique.category, reps, quality)

    @Test
    fun ratingsAverageIgnoresUnratedCategories() {
        val ratings = Ratings(technique = 8, sparring = 6)
        assertEquals(7f, ratings.average()!!, 0.001f)
        assertNull(Ratings().average())
    }

    @Test
    fun ratingsWithClampsToScale() {
        val ratings = Ratings().with(RatingCategory.FOCUS, 14).with(RatingCategory.EFFORT, -2)
        assertEquals(MAX_SCORE, ratings.focus)
        assertEquals(0, ratings.effort)
    }

    @Test
    fun heatLevelBands() {
        assertEquals(0, Stats.heatLevel(0f))
        assertEquals(1, Stats.heatLevel(4.9f))
        assertEquals(2, Stats.heatLevel(5f))
        assertEquals(3, Stats.heatLevel(8.5f))
        assertEquals(4, Stats.heatLevel(10f))
    }

    @Test
    fun summarizeCountsDaysMinutesAndRatedAverage() {
        val sessions = listOf(
            session(1, today, minutes = 90, overall = 8f),
            session(2, today, minutes = 30),
            session(3, today.minusDays(2), minutes = 60, overall = 6f),
        )
        val summary = Stats.summarize(sessions)
        assertEquals(3, summary.sessions)
        assertEquals(2, summary.trainingDays)
        assertEquals(180, summary.totalMinutes)
        assertEquals(60, summary.averageMinutes)
        assertEquals(7f, summary.averageOverall!!, 0.001f)
    }

    @Test
    fun inPeriodKeepsInclusiveWindow() {
        val sessions = listOf(
            session(1, today),
            session(2, today.minusDays(29)),
            session(3, today.minusDays(30)),
            session(4, today.plusDays(1)),
        )
        val ids = Stats.inPeriod(sessions, StatsPeriod.DAYS_30, today).map { it.id }
        assertEquals(listOf(1L, 2L), ids)
        assertEquals(4, Stats.inPeriod(sessions, StatsPeriod.ALL, today).size)
    }

    @Test
    fun streakSurvivesUntilAWeekIsMissed() {
        val monday = DayOfWeek.MONDAY
        // Trained in the three previous weeks but not yet this week: the streak is alive.
        val dates = listOf(today.minusWeeks(1), today.minusWeeks(2), today.minusWeeks(3))
        assertEquals(Streaks(currentWeeks = 3, longestWeeks = 3), Stats.streaks(dates, today, monday))

        // A gap two weeks ago breaks the current run, but the longest run is remembered.
        val broken = listOf(today, today.minusWeeks(1), today.minusWeeks(3), today.minusWeeks(4), today.minusWeeks(5))
        assertEquals(Streaks(currentWeeks = 2, longestWeeks = 3), Stats.streaks(broken, today, monday))

        // Nothing last week or this week: no current streak.
        assertEquals(0, Stats.streaks(listOf(today.minusWeeks(2)), today, monday).currentWeeks)
    }

    @Test
    fun streakCountsSeveralSessionsInOneWeekOnce() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(Streaks(1, 1), Stats.streaks(dates, today, DayOfWeek.MONDAY))
    }

    @Test
    fun weeklyActivityBuckets() {
        val sessions = listOf(
            session(1, today, minutes = 60),
            session(2, today.minusDays(1), minutes = 30),
            session(3, today.minusDays(8), minutes = 45),
        )
        val buckets = Stats.activity(sessions, StatsPeriod.DAYS_30, today, DayOfWeek.MONDAY)
        assertEquals(BucketSize.WEEK, buckets.last().size)
        assertEquals(LocalDate.of(2026, 9, 28), buckets.last().start)
        assertEquals(2, buckets.last().sessions)
        assertEquals(90, buckets.last().minutes)
        assertEquals(1, buckets[buckets.size - 2].sessions)
        // 30 days back from Fri 2 Oct starts in the week of Mon 31 Aug.
        assertEquals(LocalDate.of(2026, 8, 31), buckets.first().start)
    }

    @Test
    fun allTimeActivitySwitchesToYearsForLongLogs() {
        val recent = listOf(session(1, today.minusMonths(2)))
        val monthly = Stats.activity(recent, StatsPeriod.ALL, today, DayOfWeek.MONDAY)
        assertEquals(6, monthly.size)
        assertEquals(BucketSize.MONTH, monthly.first().size)

        val old = listOf(session(1, LocalDate.of(2022, 5, 1)), session(2, today))
        val yearly = Stats.activity(old, StatsPeriod.ALL, today, DayOfWeek.MONDAY)
        assertEquals(BucketSize.YEAR, yearly.first().size)
        assertEquals(listOf(2022, 2023, 2024, 2025, 2026), yearly.map { it.start.year })
        assertEquals(1, yearly.first().sessions)
    }

    @Test
    fun techniqueSummaryAggregatesAcrossSessions() {
        val sessions = listOf(
            session(1, today.minusDays(10), techniques = listOf(entry(armbar, reps = 10, quality = 3))),
            session(2, today.minusDays(3), techniques = listOf(entry(armbar, reps = 15, quality = 5), entry(jab, reps = 100))),
            session(3, today, techniques = listOf(entry(jab, reps = 50, quality = 4))),
        )
        val summaries = Stats.techniqueSummaries(listOf(armbar, jab), sessions).associateBy { it.technique.id }

        val armbarStats = summaries.getValue(armbar.id)
        assertEquals(2, armbarStats.sessions)
        assertEquals(25, armbarStats.totalReps)
        assertEquals(4f, armbarStats.averageQuality!!, 0.001f)
        assertEquals(today.minusDays(10), armbarStats.firstPracticed)
        assertEquals(today.minusDays(3), armbarStats.lastPracticed)

        val jabStats = summaries.getValue(jab.id)
        assertEquals(150, jabStats.totalReps)
        assertEquals(4f, jabStats.averageQuality!!, 0.001f) // the unrated practice is ignored
    }

    @Test
    fun techniqueDetailBuildsHistoryAndMonthlyBuckets() {
        val sessions = listOf(
            session(1, today.minusMonths(2), techniques = listOf(entry(armbar, reps = 5, quality = 2))),
            session(2, today.minusDays(40), techniques = listOf(entry(armbar, reps = 5))),
            session(3, today.minusDays(5), techniques = listOf(entry(armbar, reps = 8, quality = 4))),
            session(4, today, techniques = listOf(entry(armbar, reps = 12, quality = 5))),
        )
        val detail = Stats.techniqueDetail(armbar, sessions, today)

        assertEquals(listOf(4L, 3L, 2L, 1L), detail.history.map { it.sessionId })
        assertEquals(listOf(2, 4, 5), detail.qualityTrend.map { it.quality })
        assertEquals(2, detail.sessionsLast30Days)
        assertEquals(6, detail.monthly.size)
        assertEquals(LocalDate.of(2026, 10, 1), detail.monthly.last().start)
        assertEquals(1, detail.monthly.last().sessions)
        assertEquals(12, detail.monthly.last().reps)
        assertEquals(1, detail.monthly[4].sessions) // September
    }

    @Test
    fun overviewRanksTechniquesAndGroupsDisciplines() {
        val sessions = listOf(
            session(1, today, minutes = 60, overall = 9f, discipline = "BJJ",
                ratings = Ratings(technique = 9, sparring = 7),
                techniques = listOf(entry(armbar), entry(jab))),
            session(2, today.minusDays(1), minutes = 90, overall = 7f, discipline = "bjj ",
                ratings = Ratings(technique = 7),
                techniques = listOf(entry(armbar))),
            session(3, today.minusDays(2), minutes = 45, discipline = "Muay Thai",
                techniques = listOf(entry(jab, reps = 200))),
            session(4, today.minusDays(200), minutes = 60, overall = 2f, discipline = "Judo",
                techniques = listOf(entry(jab))),
        )
        val overview = Stats.overview(sessions, listOf(armbar, jab), StatsPeriod.DAYS_30, today, DayOfWeek.MONDAY)

        assertEquals(3, overview.summary.sessions)
        assertEquals(8f, overview.summary.averageOverall!!, 0.001f)
        assertEquals(8f, overview.categoryAverages.getValue(RatingCategory.TECHNIQUE)!!, 0.001f)
        assertEquals(7f, overview.categoryAverages.getValue(RatingCategory.SPARRING)!!, 0.001f)
        assertNull(overview.categoryAverages.getValue(RatingCategory.FOCUS))

        // Both practised in two sessions; the jab wins the tie on reps.
        assertEquals(listOf("Jab", "Armbar"), overview.topTechniques.map { it.technique.name })

        assertEquals(listOf("BJJ" to 150, "Muay Thai" to 45), overview.disciplines.map { it.name to it.minutes })
        assertEquals(listOf(2L, 1L), overview.ratingTrend.map { it.id })
    }

    @Test
    fun niceTicksUseRoundSteps() {
        assertEquals(listOf(0f, 1f, 2f, 3f, 4f), Stats.niceTicks(4f, minStep = 1f))
        assertEquals(listOf(0f, 5f, 10f), Stats.niceTicks(9f, minStep = 1f))
        assertEquals(listOf(0f, 1f), Stats.niceTicks(0f, minStep = 1f))
        assertEquals(listOf(0f, 1f), Stats.niceTicks(1f, minStep = 1f))
        assertEquals(listOf(0f, 50f, 100f, 150f), Stats.niceTicks(130f))
    }
}
