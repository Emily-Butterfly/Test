package com.dojolog.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dojolog.domain.Stats
import com.dojolog.domain.YearDay
import com.dojolog.domain.YearOverview
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.ArtFilterChips
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.drawUnratedHatch
import com.dojolog.ui.theme.DojoColors
import com.dojolog.ui.theme.LocalDisciplineColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

/**
 * The whole year at a glance, like GitHub's activity graph: one square per day, in the
 * colour of the art trained (the best rated one on days with several) and brighter for a
 * better rating. Tap a square to see that day, then open it in the calendar.
 */
@Composable
internal fun YearOverviewCard(
    overview: YearOverview,
    art: String?,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    onArt: (String?) -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onShowDay: (LocalDate) -> Unit,
    onShowMonth: (YearMonth) -> Unit,
) {
    var picked by rememberSaveable(overview.year) { mutableStateOf<Long?>(null) }
    val pickedDate = picked?.let(LocalDate::ofEpochDay)
    val names = overview.arts.associate { it.key to it.name } + ("" to "Unspecified")
    SectionCard(
        title = "Year ${overview.year}",
        subtitle = if (overview.trainingDays == 0) {
            "No training logged"
        } else {
            "${Fmt.count(overview.trainingDays, "training day")} · ${Fmt.count(overview.sessions, "session")} · " +
                Fmt.hours(overview.minutes)
        },
        action = {
            Row {
                IconButton(onClick = onPreviousYear) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year")
                }
                IconButton(onClick = onNextYear) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year")
                }
            }
        },
    ) {
        ArtFilterChips(overview.arts, art, onArt, Modifier.padding(bottom = 8.dp))
        YearHeatmap(
            overview = overview,
            firstDayOfWeek = firstDayOfWeek,
            today = today,
            picked = pickedDate,
            artName = art?.let { names[it] ?: it },
            onPick = { picked = it.toEpochDay() },
            onShowMonth = onShowMonth,
        )
        DayReadout(pickedDate, pickedDate?.let { overview.days[it] }, names, art?.let { names[it] ?: it }, today, onShowDay)
        CalendarLegend(
            entries = overview.arts.map { LegendEntry(it.key, it.name) },
            focus = pickedDate?.let { overview.days[it] }?.marks.orEmpty(),
            showArts = overview.arts.size < 2,
            scaleKey = art,
        )
    }
}

/** The tapped day in words, with the way into the calendar. */
@Composable
private fun DayReadout(
    date: LocalDate?,
    day: YearDay?,
    names: Map<String, String>,
    /** The art the grid is limited to, if any: other arts' days show as empty. */
    artName: String?,
    today: LocalDate,
    onShowDay: (LocalDate) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (date == null) {
            Text(
                "Tap a day to see what you trained.",
                style = MaterialTheme.typography.bodySmall,
                color = DojoColors.TextMuted,
                // Screen readers open months through the grid's actions instead.
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { },
            )
            return@Row
        }
        Column(Modifier.weight(1f)) {
            Text(
                (if (date == today) "Today · " else "") + Fmt.weekdayDate(date),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    day == null && date.isAfter(today) -> "Still to come"
                    day == null -> if (artName != null) "No $artName training" else "No training"
                    else -> day.marks.joinToString(" · ") { mark ->
                        val score = day.bestScores[mark.disciplineKey] ?: 0f
                        "${names[mark.disciplineKey] ?: mark.disciplineKey} ${if (score > 0f) Fmt.score(score) else "unrated"}"
                    } + if (day.sessions > day.marks.size) " · ${Fmt.count(day.sessions, "session")}" else ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = DojoColors.TextSecondary,
            )
        }
        TextButton(onClick = { onShowDay(date) }) { Text("Show in calendar") }
    }
}

/**
 * Weeks as columns and weekdays as rows. On a phone the year is split into two rows of half
 * a year each, so the squares stay big enough to see and tap.
 */
@Composable
private fun YearHeatmap(
    overview: YearOverview,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    picked: LocalDate?,
    artName: String?,
    onPick: (LocalDate) -> Unit,
    onShowMonth: (YearMonth) -> Unit,
) {
    val colors = LocalDisciplineColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = DojoColors.TextMuted)
    val locale = Locale.getDefault()
    val year = overview.year
    val gridStart = Stats.yearGridStart(year, firstDayOfWeek)
    val weeks = (ChronoUnit.DAYS.between(gridStart, Stats.yearGridEnd(year, firstDayOfWeek)).toInt() + 1) / 7
    val description = "$year" + (if (artName != null) ", $artName" else "") + ": " +
        Fmt.count(overview.trainingDays, "training day") + ". " +
        Month.entries.mapIndexed { index, month ->
            "${month.getDisplayName(TextStyle.FULL, locale)} ${Fmt.count(overview.monthDays[index], "day")}"
        }.joinToString(", ") + ". Open a month from the actions."
    // Screen readers can't pick a square; they open a month in the calendar instead.
    val monthActions = Month.entries.mapIndexedNotNull { index, month ->
        if (overview.monthDays[index] == 0) {
            null
        } else {
            CustomAccessibilityAction("Show ${month.getDisplayName(TextStyle.FULL, locale)} $year in calendar") {
                onShowMonth(YearMonth.of(year, month))
                true
            }
        }
    }
    // Room for the labels as the font size setting draws them.
    val density = LocalDensity.current
    val monthLabels = remember(measurer, labelStyle, locale, density) {
        with(density) {
            Month.entries.maxOf { measurer.measure(it.getDisplayName(TextStyle.SHORT, locale), labelStyle).size.height }.toDp()
        }
    }.coerceAtLeast(16.dp)
    val dayLabels = remember(measurer, labelStyle, locale, firstDayOfWeek, density) {
        with(density) {
            listOf(1, 3, 5).maxOf {
                measurer.measure(firstDayOfWeek.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale), labelStyle).size.width
            }.toDp() + 4.dp
        }
    }.coerceAtLeast(14.dp)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val strips = if ((maxWidth - dayLabels) / weeks >= 9.dp) 1 else 2
        val perStrip = (weeks + strips - 1) / strips
        val pitch = ((maxWidth - dayLabels) / perStrip).coerceAtMost(16.dp)
        val stripGap = 8.dp
        val height = (monthLabels + pitch * 7) * strips + stripGap * (strips - 1)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(year, firstDayOfWeek, strips, perStrip, pitch, monthLabels, dayLabels) {
                    detectTapGestures { offset ->
                        val stripHeight = (monthLabels + pitch * 7).toPx() + stripGap.toPx()
                        val strip = floor(offset.y / stripHeight).toInt()
                        val inStrip = offset.y - strip * stripHeight - monthLabels.toPx()
                        val column = floor((offset.x - dayLabels.toPx()) / pitch.toPx()).toInt()
                        val row = floor(inStrip / pitch.toPx()).toInt()
                        if (strip !in 0 until strips || row !in 0..6 || column !in 0 until perStrip) return@detectTapGestures
                        val week = strip * perStrip + column
                        val date = gridStart.plusDays(week * 7L + row)
                        if (week < weeks && date.year == year) onPick(date)
                    }
                }
                .semantics {
                    contentDescription = description
                    customActions = monthActions
                },
        ) {
            val pitchPx = pitch.toPx()
            val gap = (pitchPx * 0.18f).coerceAtLeast(1.dp.toPx())
            val cell = pitchPx - gap
            val radius = CornerRadius(2.dp.toPx())
            val left = dayLabels.toPx()
            val labelHeight = monthLabels.toPx()
            val stripHeight = labelHeight + pitchPx * 7 + stripGap.toPx()
            val empty = DojoColors.SurfaceHighest

            for (strip in 0 until strips) {
                val top = strip * stripHeight
                val gridTop = top + labelHeight
                // Weekday initials on alternate rows, as on GitHub.
                for (row in listOf(1, 3, 5)) {
                    val label = measurer.measure(firstDayOfWeek.plus(row.toLong()).getDisplayName(TextStyle.NARROW, locale), labelStyle)
                    drawText(label, topLeft = Offset(0f, gridTop + row * pitchPx + (cell - label.size.height) / 2f))
                }
                var labelEdge = 0f
                for (column in 0 until perStrip) {
                    val week = strip * perStrip + column
                    if (week >= weeks) break
                    val x = left + column * pitchPx
                    if (column == 0) {
                        // Each row of the graph starts with the month it is in.
                        val first = (0 until 7).map { gridStart.plusDays(week * 7L + it) }.firstOrNull { it.year == year }
                        if (first != null) {
                            val label = measurer.measure(first.month.getDisplayName(TextStyle.SHORT, locale), labelStyle)
                            drawText(label, topLeft = Offset(x, top))
                            labelEdge = x + label.size.width + 4.dp.toPx()
                        }
                    }
                    for (row in 0 until 7) {
                        val date = gridStart.plusDays(week * 7L + row)
                        if (date.year != year) continue
                        val topLeft = Offset(x, gridTop + row * pitchPx)
                        val square = Size(cell, cell)
                        val day = overview.days[date]
                        when {
                            day == null -> drawRoundRect(
                                empty.copy(alpha = if (date.isAfter(today)) 0.35f else 1f),
                                topLeft,
                                square,
                                radius,
                            )
                            day.main.level > 0 -> drawRoundRect(colors.rampForKey(day.main.disciplineKey).fill(day.main.level), topLeft, square, radius)
                            // Trained but not rated: hatched, as in the month and the key.
                            else -> clipPath(Path().apply { addRoundRect(RoundRect(Rect(topLeft, square), radius)) }) {
                                drawUnratedHatch(
                                    colors.rampForKey(day.main.disciplineKey),
                                    topLeft,
                                    square,
                                    gap = 3.dp.toPx(),
                                    stroke = 1.dp.toPx(),
                                )
                            }
                        }
                        if (date == picked || date == today) {
                            val ring = (if (date == picked) 1.5.dp else 1.dp).toPx()
                            drawRoundRect(
                                if (date == picked) DojoColors.TextPrimary else DojoColors.TextSecondary,
                                topLeft - Offset(ring, ring) / 2f,
                                Size(cell + ring, cell + ring),
                                radius,
                                style = Stroke(ring),
                            )
                        }
                        // A month's name over the week its first day falls in.
                        if (date.dayOfMonth == 1) {
                            val label = measurer.measure(date.month.getDisplayName(TextStyle.SHORT, locale), labelStyle)
                            if (x >= labelEdge && x + label.size.width <= size.width) {
                                drawText(label, topLeft = Offset(x, top))
                                labelEdge = x + label.size.width + 4.dp.toPx()
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
