package com.dojolog.domain

import java.time.LocalDate

/** Highest score for the session rating (overall and every category). */
const val MAX_SCORE = 10

/** Highest per-technique execution quality. */
const val MAX_QUALITY = 5

/** Longest session that can be logged. */
const val MAX_DURATION_MINUTES = 24 * 60

enum class SessionType(val key: String, val label: String) {
    CLASS("class", "Class"),
    OPEN_MAT("open_mat", "Open mat"),
    SPARRING("sparring", "Sparring"),
    PRIVATE("private", "Private lesson"),
    SOLO("solo", "Solo drills"),
    COMPETITION("competition", "Competition");

    companion object {
        fun fromKey(key: String): SessionType = entries.firstOrNull { it.key == key } ?: CLASS
    }
}

enum class TechniqueCategory(val key: String, val label: String) {
    STRIKE("strike", "Strike"),
    KICK("kick", "Kick"),
    DEFENSE("defense", "Block / Defense"),
    THROW("throw", "Throw / Takedown"),
    SWEEP("sweep", "Sweep"),
    SUBMISSION("submission", "Submission"),
    ESCAPE("escape", "Escape"),
    POSITION("position", "Guard / Position"),
    FORM("form", "Form / Kata"),
    CONDITIONING("conditioning", "Conditioning"),
    OTHER("other", "Other");

    companion object {
        fun fromKey(key: String): TechniqueCategory = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

enum class RatingCategory(val label: String, val hint: String) {
    TECHNIQUE("Technique", "Execution and precision"),
    CONDITIONING("Conditioning", "Cardio, strength, stamina"),
    SPARRING("Sparring", "Live application under pressure"),
    FOCUS("Focus", "Mental sharpness and learning"),
    EFFORT("Effort", "How hard you pushed"),
}

/** Category scores from 1 to [MAX_SCORE]; 0 means "not rated" (e.g. no sparring that day). */
data class Ratings(
    val technique: Int = 0,
    val conditioning: Int = 0,
    val sparring: Int = 0,
    val focus: Int = 0,
    val effort: Int = 0,
) {
    operator fun get(category: RatingCategory): Int = when (category) {
        RatingCategory.TECHNIQUE -> technique
        RatingCategory.CONDITIONING -> conditioning
        RatingCategory.SPARRING -> sparring
        RatingCategory.FOCUS -> focus
        RatingCategory.EFFORT -> effort
    }

    fun with(category: RatingCategory, score: Int): Ratings {
        val clamped = score.coerceIn(0, MAX_SCORE)
        return when (category) {
            RatingCategory.TECHNIQUE -> copy(technique = clamped)
            RatingCategory.CONDITIONING -> copy(conditioning = clamped)
            RatingCategory.SPARRING -> copy(sparring = clamped)
            RatingCategory.FOCUS -> copy(focus = clamped)
            RatingCategory.EFFORT -> copy(effort = clamped)
        }
    }

    /** Mean of the rated categories, or null when none is rated. */
    fun average(): Float? {
        val rated = RatingCategory.entries.map { get(it) }.filter { it > 0 }
        return if (rated.isEmpty()) null else rated.average().toFloat()
    }
}

data class Technique(
    val id: Long,
    val name: String,
    val category: TechniqueCategory,
    val notes: String = "",
)

/** One technique practised during a session. [reps] and [quality] are 0 when not recorded. */
data class TechniqueEntry(
    val techniqueId: Long,
    val techniqueName: String,
    val category: TechniqueCategory,
    val reps: Int = 0,
    val quality: Int = 0,
    val notes: String = "",
)

/** Outcome of one matchup; [NONE] for rounds that weren't scored. */
enum class MatchResult(val key: String, val label: String) {
    WIN("win", "Win"),
    LOSS("loss", "Loss"),
    DRAW("draw", "Draw"),
    NONE("none", "No result");

    companion object {
        fun fromKey(key: String): MatchResult = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Someone you spar or compete against, with whatever you want to remember about them. */
data class Opponent(
    val id: Long,
    val name: String,
    val club: String = "",
    /** Belt, grade or rank. */
    val grade: String = "",
    /** Weight or weight class. */
    val weight: String = "",
    val notes: String = "",
)

/**
 * One matchup (a round, roll or fight) against an opponent. [rating] is how it went for
 * you, 1 to [MAX_SCORE], or 0 when not rated.
 */
data class Matchup(
    val opponentId: Long,
    val opponentName: String,
    val result: MatchResult = MatchResult.NONE,
    val rating: Int = 0,
    val notes: String = "",
)

data class TrainingSession(
    val id: Long = 0,
    val date: LocalDate,
    val durationMinutes: Int,
    val discipline: String,
    val type: SessionType,
    val notes: String = "",
    /** Overall score from 1 to [MAX_SCORE], or 0 when unrated. */
    val overall: Float = 0f,
    /** True when [overall] is calculated from [ratings] rather than set by hand. */
    val overallAuto: Boolean = true,
    val ratings: Ratings = Ratings(),
    val techniques: List<TechniqueEntry> = emptyList(),
    /** Opponents faced, in the order entered (sparring and competition). */
    val matchups: List<Matchup> = emptyList(),
    val createdAt: Long = 0,
) {
    val isRated: Boolean get() = overall > 0f
}

/** Session types that are logged with opponents. */
val SessionType.hasOpponents: Boolean get() = this == SessionType.SPARRING || this == SessionType.COMPETITION
