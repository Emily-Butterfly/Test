package com.dojolog.domain

import java.time.LocalDate

/** Wins, losses and draws, plus the matchups that weren't scored. */
data class MatchRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val unscored: Int = 0,
) {
    val matchups: Int get() = wins + losses + draws + unscored

    /** Matchups with a result. */
    val scored: Int get() = wins + losses + draws

    /** Share of the scored matchups that were won, or null when none was scored. */
    val winRate: Float? get() = if (scored == 0) null else wins.toFloat() / scored

    operator fun plus(other: MatchRecord): MatchRecord = MatchRecord(
        wins = wins + other.wins,
        losses = losses + other.losses,
        draws = draws + other.draws,
        unscored = unscored + other.unscored,
    )

    operator fun plus(result: MatchResult): MatchRecord = when (result) {
        MatchResult.WIN -> copy(wins = wins + 1)
        MatchResult.LOSS -> copy(losses = losses + 1)
        MatchResult.DRAW -> copy(draws = draws + 1)
        MatchResult.NONE -> copy(unscored = unscored + 1)
    }

    companion object {
        fun of(results: Iterable<MatchResult>): MatchRecord = results.fold(MatchRecord()) { record, result -> record + result }
    }
}

/** One matchup against an opponent, with the session it was in. */
data class MatchupRecord(
    val sessionId: Long,
    val date: LocalDate,
    val discipline: String,
    val type: SessionType,
    val result: MatchResult,
    val rating: Int,
    val notes: String,
)

data class OpponentSummary(
    val opponent: Opponent,
    val record: MatchRecord,
    /** Mean of the rated matchups, or null when none is rated. */
    val averageRating: Float?,
    /** How many matchups were rated. */
    val ratedCount: Int,
    val firstFaced: LocalDate?,
    val lastFaced: LocalDate?,
    /** The martial arts you faced them in, most sessions first. */
    val arts: List<ArtCount>,
)

data class OpponentDetail(
    val summary: OpponentSummary,
    /** Newest first. */
    val history: List<MatchupRecord>,
    /** Rated matchups, oldest first, at most [TREND_POINTS]. */
    val ratingTrend: List<MatchupRecord>,
)

object OpponentStats {

    /** Your record over every matchup in [sessions]. */
    fun record(sessions: List<TrainingSession>): MatchRecord =
        MatchRecord.of(sessions.flatMap { session -> session.matchups.map { it.result } })

    /** How many different people you faced in [sessions]. */
    fun opponentsFaced(sessions: List<TrainingSession>): Int =
        sessions.flatMap { session -> session.matchups.map { it.opponentId } }.distinct().size

    /**
     * Every opponent with their record, most recently faced first, then the ones never faced
     * A–Z. With [artKey] (a [disciplineKey]) only matchups in that art count, and only the
     * people faced in it are listed.
     */
    fun summaries(
        opponents: List<Opponent>,
        sessions: List<TrainingSession>,
        artKey: String? = null,
        names: Map<String, String> = Stats.artNames(sessions),
    ): List<OpponentSummary> {
        val counted = if (artKey == null) sessions else sessions.filter { disciplineKey(it.discipline) == artKey }
        val records = matchupRecords(counted)
        return opponents
            .map { summaryOf(it, records[it.id].orEmpty(), names) }
            .filter { artKey == null || it.record.matchups > 0 }
            .sortedWith(
                compareByDescending<OpponentSummary> { it.lastFaced?.toEpochDay() ?: Long.MIN_VALUE }
                    .thenBy { it.opponent.name.lowercase() },
            )
    }

    fun detail(opponent: Opponent, sessions: List<TrainingSession>): OpponentDetail {
        val records = matchupRecords(sessions)[opponent.id].orEmpty()
        return OpponentDetail(
            summary = summaryOf(opponent, records, Stats.artNames(sessions)),
            history = records.reversed(),
            ratingTrend = records.filter { it.rating > 0 }.takeLast(TREND_POINTS),
        )
    }

    /** Matchups per opponent, oldest first (by date, then as logged). */
    private fun matchupRecords(sessions: List<TrainingSession>): Map<Long, List<MatchupRecord>> {
        val ordered = sessions.sortedWith(compareBy<TrainingSession> { it.date.toEpochDay() }.thenBy { it.createdAt }.thenBy { it.id })
        val byOpponent = HashMap<Long, MutableList<MatchupRecord>>()
        for (session in ordered) {
            for (matchup in session.matchups) {
                byOpponent.getOrPut(matchup.opponentId) { mutableListOf() } += MatchupRecord(
                    sessionId = session.id,
                    date = session.date,
                    discipline = session.discipline,
                    type = session.type,
                    result = matchup.result,
                    rating = matchup.rating,
                    notes = matchup.notes,
                )
            }
        }
        return byOpponent
    }

    private fun summaryOf(opponent: Opponent, records: List<MatchupRecord>, names: Map<String, String>): OpponentSummary {
        val ratings = records.map { it.rating }.filter { it > 0 }
        return OpponentSummary(
            opponent = opponent,
            record = MatchRecord.of(records.map { it.result }),
            averageRating = if (ratings.isEmpty()) null else ratings.average().toFloat(),
            ratedCount = ratings.size,
            firstFaced = records.firstOrNull()?.date,
            lastFaced = records.lastOrNull()?.date,
            arts = records
                .groupBy { disciplineKey(it.discipline) }
                .filterKeys { it.isNotEmpty() }
                .map { (key, group) ->
                    ArtCount(key, names[key] ?: group.first().discipline.trim(), group.map { it.sessionId }.distinct().size)
                }
                .sortedWith(compareByDescending<ArtCount> { it.sessions }.thenBy { it.key }),
        )
    }
}
