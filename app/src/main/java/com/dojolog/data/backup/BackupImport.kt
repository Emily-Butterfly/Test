package com.dojolog.data.backup

import com.dojolog.domain.MAX_DURATION_MINUTES
import com.dojolog.domain.MAX_QUALITY
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.MatchResult
import com.dojolog.domain.Ratings
import com.dojolog.domain.SessionType
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.nameKey
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** An opponent already in the app, with the details an import may fill in. */
data class ExistingOpponent(
    val id: Long,
    val club: String,
    val grade: String,
    val weight: String,
    val notes: String,
)

/** What the app already holds, as far as an import needs to know. */
data class ExistingData(
    /** [nameKey]s of the techniques in the library. */
    val techniques: Set<String> = emptySet(),
    /** By [nameKey]. */
    val opponents: Map<String, ExistingOpponent> = emptyMap(),
    /** [BackupSession.createdAt] of every session, which identifies it. */
    val sessionCreatedAt: Set<Long> = emptySet(),
)

data class PlannedEntry(val techniqueKey: String, val reps: Int, val quality: Int, val notes: String)

data class PlannedMatchup(val opponentKey: String, val result: MatchResult, val rating: Int, val notes: String)

/** A session to add, checked and with its techniques and opponents referenced by [nameKey]. */
data class PlannedSession(
    val date: LocalDate,
    val durationMinutes: Int,
    val discipline: String,
    val type: SessionType,
    val notes: String,
    val overall: Float,
    val overallAuto: Boolean,
    val ratings: Ratings,
    val createdAt: Long,
    val techniques: List<PlannedEntry>,
    val matchups: List<PlannedMatchup>,
)

/** New details for an opponent already in the app: only its empty fields are filled in. */
data class OpponentFill(val id: Long, val club: String, val grade: String, val weight: String, val notes: String)

data class ImportPlan(
    /** Techniques whose name isn't in the library yet. */
    val newTechniques: List<BackupTechnique>,
    /** Opponents whose name isn't in the app yet. */
    val newOpponents: List<BackupOpponent>,
    val opponentFills: List<OpponentFill>,
    val sessions: List<PlannedSession>,
    /** Sessions the app already has (same [BackupSession.createdAt]); they are left as they are. */
    val skippedSessions: Int,
    /** Sessions that couldn't be read, e.g. with a broken date. */
    val invalidSessions: Int,
    /** Technique entries and matchups whose technique or opponent is missing from the file. */
    val droppedReferences: Int,
    val colors: Map<String, Int>,
)

/**
 * Works out what importing a backup adds to the app. Nothing already in the app is removed
 * or overwritten: techniques and opponents are matched by name, sessions by when they were
 * first logged, so importing the same file twice adds nothing the second time.
 */
object BackupImport {
    private const val MAX_REPS = 99_999
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2100

    fun plan(backup: BackupFile, existing: ExistingData): ImportPlan {
        val techniqueKeys = HashMap<Long, String>()
        val newTechniques = LinkedHashMap<String, BackupTechnique>()
        for (technique in backup.techniques) {
            val name = technique.name.trim()
            if (name.isEmpty() || technique.id in techniqueKeys) continue
            val key = nameKey(name)
            techniqueKeys[technique.id] = key
            if (key !in existing.techniques && key !in newTechniques) {
                newTechniques[key] = technique.copy(
                    name = name,
                    category = TechniqueCategory.fromKey(technique.category).key,
                    notes = technique.notes.trim(),
                )
            }
        }

        val opponentKeys = HashMap<Long, String>()
        val newOpponents = LinkedHashMap<String, BackupOpponent>()
        val fills = LinkedHashMap<Long, OpponentFill>()
        for (opponent in backup.opponents) {
            val name = opponent.name.trim()
            if (name.isEmpty() || opponent.id in opponentKeys) continue
            val key = nameKey(name)
            opponentKeys[opponent.id] = key
            val inApp = existing.opponents[key]
            if (inApp != null) {
                val current = fills[inApp.id] ?: OpponentFill(inApp.id, inApp.club, inApp.grade, inApp.weight, inApp.notes)
                val filled = OpponentFill(
                    id = inApp.id,
                    club = current.club.ifBlank { opponent.club.trim() },
                    grade = current.grade.ifBlank { opponent.grade.trim() },
                    weight = current.weight.ifBlank { opponent.weight.trim() },
                    notes = current.notes.ifBlank { opponent.notes.trim() },
                )
                if (filled != OpponentFill(inApp.id, inApp.club, inApp.grade, inApp.weight, inApp.notes)) fills[inApp.id] = filled
            } else if (key !in newOpponents) {
                newOpponents[key] = opponent.copy(
                    name = name,
                    club = opponent.club.trim(),
                    grade = opponent.grade.trim(),
                    weight = opponent.weight.trim(),
                    notes = opponent.notes.trim(),
                )
            }
        }

        val seen = HashSet(existing.sessionCreatedAt)
        val sessions = ArrayList<PlannedSession>()
        var skipped = 0
        var invalid = 0
        var dropped = 0
        backup.sessions.forEachIndexed { index, session ->
            val date = try {
                LocalDate.parse(session.date.trim())
            } catch (e: DateTimeParseException) {
                null
            }
            // The editor's date picker can't go outside these years; anything else is damage.
            if (date == null || date.year !in MIN_YEAR..MAX_YEAR) {
                invalid++
                return@forEachIndexed
            }
            // A session without a creation time gets one from its date, unique within the file.
            val createdAt = if (session.createdAt > 0) {
                session.createdAt
            } else {
                generateSequence(date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + index) { it + 1 }
                    .first { it !in seen }
            }
            if (!seen.add(createdAt)) {
                skipped++
                return@forEachIndexed
            }
            val entries = session.techniques.mapNotNull { entry ->
                val key = techniqueKeys[entry.techniqueId]
                if (key == null) {
                    dropped++
                    null
                } else {
                    PlannedEntry(key, entry.reps.coerceIn(0, MAX_REPS), entry.quality.coerceIn(0, MAX_QUALITY), entry.notes.trim())
                }
            }
            val matchups = session.matchups.mapNotNull { matchup ->
                val key = opponentKeys[matchup.opponentId]
                if (key == null) {
                    dropped++
                    null
                } else {
                    PlannedMatchup(key, MatchResult.fromKey(matchup.result), matchup.rating.coerceIn(0, MAX_SCORE), matchup.notes.trim())
                }
            }
            val ratings = session.ratings
            sessions += PlannedSession(
                date = date,
                durationMinutes = session.durationMinutes.coerceIn(1, MAX_DURATION_MINUTES),
                discipline = session.discipline.trim(),
                type = SessionType.fromKey(session.type),
                notes = session.notes.trim(),
                overall = session.overall.takeIf { it.isFinite() }?.coerceIn(0f, MAX_SCORE.toFloat()) ?: 0f,
                overallAuto = session.overallAuto,
                ratings = Ratings(
                    technique = ratings.technique.coerceIn(0, MAX_SCORE),
                    conditioning = ratings.conditioning.coerceIn(0, MAX_SCORE),
                    sparring = ratings.sparring.coerceIn(0, MAX_SCORE),
                    focus = ratings.focus.coerceIn(0, MAX_SCORE),
                    effort = ratings.effort.coerceIn(0, MAX_SCORE),
                ),
                createdAt = createdAt,
                techniques = entries,
                matchups = matchups,
            )
        }

        return ImportPlan(
            newTechniques = newTechniques.values.toList(),
            newOpponents = newOpponents.values.toList(),
            opponentFills = fills.values.toList(),
            sessions = sessions,
            skippedSessions = skipped,
            invalidSessions = invalid,
            droppedReferences = dropped,
            colors = backup.colors
                .mapKeys { (key, _) -> key.trim().lowercase() }
                .filter { (key, slot) -> key.isNotEmpty() && slot in 0 until MAX_COLOR_SLOTS },
        )
    }

    /** Far more than the palette has; anything beyond is a damaged file. */
    private const val MAX_COLOR_SLOTS = 64
}
