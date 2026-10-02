package com.dojolog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class OpponentStatsTest {
    private val today = LocalDate.of(2026, 10, 2)
    private val alex = Opponent(1, "Alex", club = "Gracie Barra")
    private val sam = Opponent(2, "sam")
    private val kim = Opponent(3, "Kim")

    private fun fight(opponent: Opponent, result: MatchResult = MatchResult.NONE, rating: Int = 0, notes: String = "") =
        Matchup(opponent.id, opponent.name, result, rating, notes)

    private fun session(
        id: Long,
        date: LocalDate,
        vararg matchups: Matchup,
        discipline: String = "BJJ",
        type: SessionType = SessionType.SPARRING,
        createdAt: Long = id,
    ) = TrainingSession(
        id = id,
        date = date,
        durationMinutes = 60,
        discipline = discipline,
        type = type,
        matchups = matchups.toList(),
        createdAt = createdAt,
    )

    @Test
    fun recordCountsResultsAndUnscoredRounds() {
        val record = MatchRecord.of(listOf(MatchResult.WIN, MatchResult.WIN, MatchResult.LOSS, MatchResult.DRAW, MatchResult.NONE))
        assertEquals(MatchRecord(wins = 2, losses = 1, draws = 1, unscored = 1), record)
        assertEquals(5, record.matchups)
        assertEquals(4, record.scored)
        assertEquals(0.5f, record.winRate!!, 0.001f)
        assertNull(MatchRecord(unscored = 3).winRate)
    }

    @Test
    fun summariesGiveEachOpponentTheirRecordMostRecentFirst() {
        val sessions = listOf(
            session(1, today.minusDays(10), fight(alex, MatchResult.WIN, 8), fight(sam, MatchResult.LOSS, 4)),
            session(2, today.minusDays(3), fight(sam, MatchResult.WIN, 9), fight(sam, MatchResult.NONE)),
            session(3, today.minusDays(1), fight(alex, MatchResult.LOSS, 5, "Caught me in a triangle"), discipline = "Judo", type = SessionType.COMPETITION),
        )
        val summaries = OpponentStats.summaries(listOf(alex, sam, kim), sessions)
        assertEquals(listOf("Alex", "sam", "Kim"), summaries.map { it.opponent.name })

        val a = summaries[0]
        assertEquals(MatchRecord(wins = 1, losses = 1), a.record)
        assertEquals(6.5f, a.averageRating!!, 0.001f)
        assertEquals(today.minusDays(10), a.firstFaced)
        assertEquals(today.minusDays(1), a.lastFaced)
        assertEquals(listOf(ArtCount("bjj", "BJJ", 1), ArtCount("judo", "Judo", 1)), a.arts)

        // Two rounds in one session count as two matchups but one session for the art.
        val s = summaries[1]
        assertEquals(MatchRecord(wins = 1, losses = 1, unscored = 1), s.record)
        assertEquals(listOf(ArtCount("bjj", "BJJ", 2)), s.arts)

        val k = summaries[2]
        assertEquals(MatchRecord(), k.record)
        assertNull(k.averageRating)
        assertNull(k.lastFaced)
    }

    @Test
    fun summariesCanBeLimitedToOneArt() {
        val sessions = listOf(
            session(1, today.minusDays(10), fight(alex, MatchResult.WIN), fight(sam, MatchResult.LOSS)),
            session(2, today.minusDays(1), fight(alex, MatchResult.LOSS), discipline = "judo "),
        )
        val judo = OpponentStats.summaries(listOf(alex, sam, kim), sessions, artKey = "judo")
        assertEquals(listOf("Alex"), judo.map { it.opponent.name })
        assertEquals(MatchRecord(losses = 1), judo.single().record)
        assertEquals("judo", judo.single().arts.single().name)
    }

    @Test
    fun detailListsHistoryNewestFirstAndTrendsOldestFirst() {
        val sessions = listOf(
            session(2, today.minusDays(1), fight(alex, MatchResult.WIN, 7), fight(alex, MatchResult.DRAW, 0, "Second round")),
            session(1, today.minusDays(5), fight(alex, MatchResult.LOSS, 4)),
            session(3, today, fight(sam, MatchResult.WIN, 9)),
        )
        val detail = OpponentStats.detail(alex, sessions)
        assertEquals(listOf("Second round", "", ""), detail.history.map { it.notes })
        assertEquals(listOf(MatchResult.DRAW, MatchResult.WIN, MatchResult.LOSS), detail.history.map { it.result })
        assertEquals(listOf(4, 7), detail.ratingTrend.map { it.rating })
        assertEquals(MatchRecord(wins = 1, losses = 1, draws = 1), detail.summary.record)
    }

    @Test
    fun overviewReportsTheRecordForThePeriod() {
        val sessions = listOf(
            session(1, today.minusDays(200), fight(alex, MatchResult.WIN)),
            session(2, today.minusDays(5), fight(alex, MatchResult.LOSS), fight(sam, MatchResult.WIN)),
            session(3, today, type = SessionType.CLASS),
        )
        val overview = Stats.overview(sessions, emptyList(), StatsPeriod.DAYS_90, today, DayOfWeek.MONDAY)
        assertEquals(MatchRecord(wins = 1, losses = 1), overview.record)
        assertEquals(2, overview.opponentsFaced)
        assertEquals(MatchRecord(wins = 2, losses = 1), Stats.overview(sessions, emptyList(), StatsPeriod.ALL, today, DayOfWeek.MONDAY).record)
    }
}
