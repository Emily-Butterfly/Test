package com.dojolog.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.MAX_SCORE
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.LegendSwatch
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.SessionCard
import com.dojolog.ui.components.StatTile
import com.dojolog.ui.components.TileRow
import com.dojolog.ui.components.unratedHatch
import com.dojolog.ui.theme.DojoColors
import com.dojolog.ui.theme.LocalDisciplineColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import java.time.format.TextStyle as JavaTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenSession: (Long) -> Unit,
    onAddSession: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dojo Log") },
                actions = {
                    IconButton(onClick = viewModel::goToToday) {
                        Icon(Icons.Outlined.Today, contentDescription = "Jump to today")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddSession(state.selected) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Log session") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "calendar") {
                SectionCard {
                    MonthHeader(
                        month = state.month,
                        onPrevious = viewModel::showPreviousMonth,
                        onNext = viewModel::showNextMonth,
                    )
                    Spacer(Modifier.height(8.dp))
                    MonthGrid(
                        month = state.month,
                        gridStart = state.gridStart,
                        gridEnd = state.gridEnd,
                        selected = state.selected,
                        today = state.today,
                        days = state.days,
                        names = state.legend.associate { it.key to it.name },
                        firstDayOfWeek = state.firstDayOfWeek,
                        onSelect = viewModel::select,
                        onSwipePrevious = viewModel::showPreviousMonth,
                        onSwipeNext = viewModel::showNextMonth,
                    )
                    Spacer(Modifier.height(12.dp))
                    CalendarLegend(state.legend)
                }
            }
            item(key = "summary") {
                val summary = state.monthSummary
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TileRow {
                        StatTile(
                            label = "Sessions this month",
                            value = summary.sessions.toString(),
                            supporting = Fmt.count(summary.trainingDays, "training day"),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Time on the mat",
                            value = Fmt.hours(summary.totalMinutes),
                            supporting = if (summary.sessions > 0) "${Fmt.duration(summary.averageMinutes)} avg" else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TileRow {
                        StatTile(
                            label = "Avg rating",
                            value = summary.averageOverall?.let { "${Fmt.decimal(it)} / $MAX_SCORE" } ?: "–",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Week streak",
                            value = Fmt.count(state.streaks.currentWeeks, "week"),
                            supporting = "Best: ${Fmt.count(state.streaks.longestWeeks, "week")}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "day-header") {
                Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                    Text(Fmt.fullDate(state.selected), style = MaterialTheme.typography.titleMedium)
                    Text(
                        Fmt.relativeDay(state.selected, state.today),
                        style = MaterialTheme.typography.bodySmall,
                        color = DojoColors.TextMuted,
                    )
                }
            }
            if (!state.loading && state.selectedSessions.isEmpty()) {
                item(key = "rest-day") {
                    RestDay()
                }
            }
            items(state.selectedSessions, key = { it.id }) { session ->
                SessionCard(session = session, onClick = { onOpenSession(session.id) })
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            Fmt.monthYear(month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/**
 * The month as whole weeks: the first and last rows also show the neighbouring months'
 * days (dimmed), and tapping one of those days opens its month.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    gridStart: LocalDate,
    gridEnd: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    days: Map<LocalDate, DayInfo>,
    names: Map<String, String>,
    firstDayOfWeek: DayOfWeek,
    onSelect: (LocalDate) -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
) {
    val weekdays = remember(firstDayOfWeek) { (0L until 7L).map { firstDayOfWeek.plus(it) } }
    val locale = Locale.getDefault()
    val rows = (ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1) / 7

    Column(
        Modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    val threshold = 56.dp.toPx()
                    if (abs(total) > threshold) {
                        if (total > 0) onSwipePrevious() else onSwipeNext()
                    }
                },
                onHorizontalDrag = { change, amount ->
                    total += amount
                    change.consume()
                },
            )
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    day.getDisplayName(JavaTextStyle.SHORT, locale).take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = DojoColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val date = gridStart.plusDays((row * 7 + column).toLong())
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                    ) {
                        DayCell(
                            date = date,
                            info = days[date],
                            names = names,
                            isSelected = date == selected,
                            isToday = date == today,
                            isFuture = date.isAfter(today),
                            otherMonth = when {
                                date.isBefore(month.atDay(1)) -> "previous month"
                                date.isAfter(month.atEndOfMonth()) -> "next month"
                                else -> null
                            },
                            onClick = { onSelect(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    info: DayInfo?,
    names: Map<String, String>,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    /** "previous month" or "next month" for the neighbouring days, else null. */
    otherMonth: String?,
    onClick: () -> Unit,
) {
    val colors = LocalDisciplineColors.current
    val shape = RoundedCornerShape(12.dp)
    val marks = info?.marks.orEmpty()
    val single = marks.singleOrNull()
    val solidFill = single != null && single.level > 0
    val textColor = when {
        solidFill -> colors.rampForKey(single!!.disciplineKey).ink(single.level)
        marks.isNotEmpty() -> DojoColors.TextPrimary
        isFuture || otherMonth != null -> DojoColors.TextMuted
        else -> DojoColors.TextSecondary
    }
    // Stripes and hatching mix light and dark under the number, so give it a halo.
    val textStyle = if (marks.isNotEmpty() && !solidFill) {
        MaterialTheme.typography.bodyMedium.copy(shadow = Shadow(Color.Black.copy(alpha = 0.85f), blurRadius = 6f))
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val description = buildString {
        append(Fmt.fullDate(date))
        if (isToday) append(", today")
        if (otherMonth != null) append(", ").append(otherMonth)
        if (info == null) {
            append(", no training")
        } else {
            append(", ").append(Fmt.count(info.sessions, "session"))
            marks.forEach { mark ->
                append(", ").append(names[mark.disciplineKey] ?: mark.disciplineKey.ifEmpty { "unspecified" })
                append(if (mark.level > 0) " rated ${LEVEL_LABELS[mark.level - 1]}" else " not rated")
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (otherMonth != null) 0.45f else 1f)
            .clip(shape)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, DojoColors.TextPrimary, shape)
                    isToday -> Modifier.border(1.dp, DojoColors.TextSecondary, shape)
                    else -> Modifier
                },
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (marks.isNotEmpty()) {
            // One stripe per martial art, separated by a thin gap of the card surface.
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                marks.forEach { mark ->
                    val ramp = colors.rampForKey(mark.disciplineKey)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (mark.level > 0) Modifier.background(ramp.fill(mark.level))
                                else Modifier.unratedHatch(ramp),
                            ),
                    )
                }
            }
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = textStyle,
            fontWeight = if (info != null || isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        if (info != null && info.sessions > marks.size) {
            // More sessions than stripes: show the count as dots.
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(min(info.sessions, 3)) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(textColor),
                    )
                }
            }
        }
    }
}

private val LEVEL_LABELS = listOf("1 to 4", "5 to 6", "7 to 8", "9 to 10")

/** Which colour is which art, and how brightness maps to rating (in the first art's colour). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend(entries: List<LegendEntry>) {
    if (entries.isEmpty()) {
        Text(
            "Each martial art gets its own colour; brighter means a better rating.",
            style = MaterialTheme.typography.labelSmall,
            color = DojoColors.TextMuted,
        )
        return
    }
    val colors = LocalDisciplineColors.current
    val scale = colors.rampForKey(entries.first().key)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEach { LegendSwatch(colors.rampForKey(it.key).identity, it.name) }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Rating:", style = MaterialTheme.typography.labelSmall, color = DojoColors.TextMuted)
            LegendSwatch(Color.Unspecified, "unrated", hatch = scale)
            LegendSwatch(scale.fill(1), "1–4")
            LegendSwatch(scale.fill(2), "5–6")
            LegendSwatch(scale.fill(3), "7–8")
            LegendSwatch(scale.fill(4), "9–10")
        }
    }
}

@Composable
private fun RestDay() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SelfImprovement, contentDescription = null, tint = DojoColors.TextMuted)
        Spacer(Modifier.size(12.dp))
        Text(
            "No training logged for this day. Tap “Log session” to add one.",
            style = MaterialTheme.typography.bodyMedium,
            color = DojoColors.TextSecondary,
        )
    }
}
