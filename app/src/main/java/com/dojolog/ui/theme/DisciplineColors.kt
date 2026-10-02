package com.dojolog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dojolog.domain.Stats
import com.dojolog.domain.disciplineKey

/**
 * One hue per martial art, four steps of rising intensity for the rating bands
 * (1–4, 5–6, 7–8, 9–10).
 *
 * The ramps share OKLCH lightness 0.48 / 0.565 / 0.655 / 0.75 (yellow +0.025) and were
 * checked with the dataviz palette validator against the dark card surface: every ramp
 * is single-hue with visible steps; at each rating level the first three hues stay apart
 * for all checked colour-vision types, and all eight stay apart for normal vision. Beyond
 * three arts some dim pairs are close under colour blindness, so the calendar legend
 * names every art shown.
 */
@Immutable
class Ramp(val steps: List<Color>, private val inks: List<Color>) {
    /** Fill for a rating step 1..4. */
    fun fill(level: Int): Color = steps[level.coerceIn(1, steps.size) - 1]

    /** Text colour that stays readable (≥ 4.4:1) on [fill]. */
    fun ink(level: Int): Color = inks[level.coerceIn(1, inks.size) - 1]

    /** The step used where an art is shown without a rating (legend swatches, bars). */
    val identity: Color get() = steps[2]
}

object DisciplinePalette {
    private val W = Color.White
    private val D = DojoColors.Background

    /** Assigned in this order, so the most distinct hues go to the first arts logged. */
    val ramps = listOf(
        Ramp(listOf(Color(0xFF025BB3), Color(0xFF2575D2), Color(0xFF4491F0), Color(0xFF74B1FE)), listOf(W, W, D, D)), // blue
        Ramp(listOf(Color(0xFF9F3603), Color(0xFFC5460A), Color(0xFFE56432), Color(0xFFFE885E)), listOf(W, W, D, D)), // orange
        Ramp(listOf(Color(0xFF046F4D), Color(0xFF078B61), Color(0xFF2BA97A), Color(0xFF52C797)), listOf(W, D, D, D)), // aqua
        Ramp(listOf(Color(0xFF895903), Color(0xFFA97004), Color(0xFFCC880B), Color(0xFFECA63D)), listOf(W, D, D, D)), // yellow
        Ramp(listOf(Color(0xFFA42058), Color(0xFFC13E70), Color(0xFFE05B8B), Color(0xFFFF7DA8)), listOf(W, W, D, D)), // magenta
        Ramp(listOf(Color(0xFF037202), Color(0xFF1B8E18), Color(0xFF40AB3A), Color(0xFF60C95A)), listOf(W, D, D, D)), // green
        Ramp(listOf(Color(0xFF5A4CAA), Color(0xFF7265C6), Color(0xFF8C80E4), Color(0xFFA8A0FF)), listOf(W, W, D, D)), // violet
        Ramp(listOf(Color(0xFFA42A31), Color(0xFFC24649), Color(0xFFE16263), Color(0xFFFF8482)), listOf(W, W, D, D)), // red
    )

    /** Unnamed sessions, and a ninth art onwards: never a generated hue. */
    val other = Ramp(listOf(Color(0xFF5A5E65), Color(0xFF72767D), Color(0xFF8D9198), Color(0xFFAAAEB6)), listOf(W, W, D, D))

    fun ramp(slot: Int?): Ramp = if (slot != null && slot in ramps.indices) ramps[slot] else other
}

/** Looks up the colour ramp of a martial art by name. */
@Immutable
class DisciplineColors(private val slots: Map<String, Int>) {
    fun rampForKey(key: String): Ramp = DisciplinePalette.ramp(slots[key])

    /**
     * A known art gets its own colour. A new name (e.g. being typed into the editor) previews
     * the colour it will get once saved: the next free slot.
     */
    fun ramp(discipline: String): Ramp {
        val key = disciplineKey(discipline)
        if (key.isEmpty()) return DisciplinePalette.other
        return DisciplinePalette.ramp(slots[key] ?: slots.size)
    }

    /** Fill for a session score, or null when unrated. */
    fun fill(discipline: String, score: Float): Color? {
        val level = Stats.heatLevel(score)
        return if (level == 0) null else ramp(discipline).fill(level)
    }
}

val LocalDisciplineColors = compositionLocalOf { DisciplineColors(emptyMap()) }
