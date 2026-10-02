package com.dojolog.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.DayMark
import com.dojolog.domain.MAX_SCORE
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.BackupMenu
import com.dojolog.ui.components.LegendSwatch
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.SessionCard
import com.dojolog.ui.components.StatTile
import com.dojolog.ui.components.TileRow
import com.dojolog.ui.components.unratedHatch
import com.dojolog.ui.theme.DojoColors
import com.dojolog.ui.theme.LocalDisciplineColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenSession: (Long) -> Unit,
    onAddSession: (LocalDate) -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dojo Log") },
                actions = {
                    IconButton(onClick = viewModel::goToToday) {
                        Icon(Icons.Outlined.Today, contentDescription = "Jump to today")
                    }
                    BackupMenu(onOpenBackup)
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
            state = listState,
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
                        onPick = { showMonthPicker = true },
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
                    )
                    Spacer(Modifier.height(12.dp))
                    // The rating scale takes the colour of the selected day's art(s).
                    CalendarLegend(
                        entries = state.legend,
                        focus = state.days[state.selected]?.marks.orEmpty(),
                        scaleKey = state.mainArt,
                    )
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
            item(key = "year") {
                YearOverviewCard(
                    overview = state.year,
                    art = state.yearArt,
                    mainArt = state.mainArt,
                    firstDayOfWeek = state.firstDayOfWeek,
                    today = state.today,
                    onArt = viewModel::setYearArt,
                    onPreviousYear = { viewModel.shiftYear(-1) },
                    onNextYear = { viewModel.shiftYear(1) },
                    onShowDay = { date ->
                        viewModel.select(date)
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onShowMonth = { month ->
                        viewModel.showMonth(month)
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                )
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            shown = state.month,
            today = state.today,
            monthDays = state.monthDays,
            onPick = { month ->
                viewModel.showMonth(month)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }
}

/** The month's name opens the month picker; the arrows step one month. */
@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Choose a month", onClick = onPick)
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(Fmt.monthYear(month), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DojoColors.TextSecondary)
            }
        }
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
) {
    val weekdays = remember(firstDayOfWeek) { (0L until 7L).map { firstDayOfWeek.plus(it) } }
    val locale = Locale.getDefault()
    val rows = (ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1) / 7

    Column {
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
        MaterialTheme.typography.bodyMedium.copy(shadow = Shadow(Halo, blurRadius = 6f))
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
            // One stripe per martial art, separated by a thin gap of the card surface. Days of
            // the neighbouring months get a smaller tile, never a dimmer one: brightness is
            // the rating.
            Row(
                Modifier
                    .matchParentSize()
                    .then(if (otherMonth != null) Modifier.padding(5.dp).clip(RoundedCornerShape(8.dp)) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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
            fontWeight = if ((info != null || isToday) && otherMonth == null) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        if (info != null && info.sessions > marks.size) {
            // More sessions than stripes: show the count as dots, ringed like the number's
            // halo where they sit on stripes or hatching.
            val halo = !solidFill
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (otherMonth != null) 6.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(min(info.sessions, 3)) {
                    Box(
                        Modifier
                            .size(if (halo) 6.dp else 4.dp)
                            .then(if (halo) Modifier.background(Halo, CircleShape).padding(1.dp) else Modifier)
                            .background(textColor, CircleShape),
                    )
                }
            }
        }
    }
}

/** Dark edge that keeps light marks readable over stripes and hatching. */
private val Halo = Color.Black.copy(alpha = 0.85f)

private val LEVEL_LABELS = listOf("1 to 4", "5 to 6", "7 to 8", "9 to 10")

/**
 * Which colour is which art (left out with [showArts] false, e.g. when filter chips already
 * say it), and how brightness maps to rating. The rating scale is drawn in the colour of
 * each art in [focus], the selected day's, with that day's step ringed; on a day without
 * training it uses [scaleKey] (the art with the most sessions), or else the first art in
 * [entries].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarLegend(
    entries: List<LegendEntry>,
    focus: List<DayMark>,
    showArts: Boolean = true,
    scaleKey: String? = null,
) {
    if (entries.isEmpty() && focus.isEmpty() && scaleKey == null) {
        Text(
            "Each martial art gets its own colour; brighter means a better rating.",
            style = MaterialTheme.typography.labelSmall,
            color = DojoColors.TextMuted,
        )
        return
    }
    val colors = LocalDisciplineColors.current
    val names = entries.associate { it.key to it.name }
    val scales: List<DayMark?> = focus.ifEmpty { listOf(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showArts && entries.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entries.forEach { LegendSwatch(colors.rampForKey(it.key).identity, it.name) }
            }
        }
        scales.forEach { mark ->
            val key = mark?.disciplineKey ?: scaleKey ?: entries.firstOrNull()?.key.orEmpty()
            val scale = colors.rampForKey(key)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (focus.size > 1) "${names[key] ?: key.ifEmpty { "Unspecified" }}:" else "Rating:",
                    style = MaterialTheme.typography.labelSmall,
                    color = DojoColors.TextMuted,
                )
                LegendSwatch(Color.Unspecified, "unrated", hatch = scale, marked = mark?.level == 0)
                LEVEL_RANGES.forEachIndexed { index, range ->
                    LegendSwatch(scale.fill(index + 1), range, marked = mark?.level == index + 1)
                }
            }
        }
    }
}

private val LEVEL_RANGES = listOf("1–4", "5–6", "7–8", "9–10")

/** Pick any month: a year at a time, each month with its number of training days. */
@Composable
internal fun MonthPickerDialog(
    shown: YearMonth,
    today: LocalDate,
    monthDays: Map<YearMonth, Int>,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by rememberSaveable { mutableIntStateOf(shown.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }, enabled = year > MIN_YEAR) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year")
                }
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = { year++ }, enabled = year < MAX_YEAR) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (column in 0 until 3) {
                            val month = YearMonth.of(year, row * 3 + column + 1)
                            MonthChoice(
                                month = month,
                                isShown = month == shown,
                                isCurrent = month == YearMonth.from(today),
                                trainingDays = monthDays[month] ?: 0,
                                onClick = { onPick(month) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(YearMonth.from(today)) }) { Text("This month") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MonthChoice(
    month: YearMonth,
    isShown: Boolean,
    isCurrent: Boolean,
    trainingDays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val ink = if (isShown) MaterialTheme.colorScheme.onPrimaryContainer else DojoColors.TextPrimary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isShown) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (isCurrent && !isShown) BorderStroke(1.dp, DojoColors.TextSecondary) else null,
        modifier = modifier.semantics { selected = isShown },
    ) {
        val days = if (trainingDays > 0) Fmt.count(trainingDays, "day") else "–"
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(vertical = 10.dp)
                .clearAndSetSemantics {
                    contentDescription = "${Fmt.monthYear(month)}, " +
                        (if (trainingDays > 0) Fmt.count(trainingDays, "training day") else "no training") +
                        if (isCurrent) ", this month" else ""
                },
        ) {
            Text(month.month.getDisplayName(JavaTextStyle.SHORT, locale), style = MaterialTheme.typography.titleSmall, color = ink)
            Text(days, style = MaterialTheme.typography.labelSmall, color = if (isShown) ink else DojoColors.TextMuted)
        }
    }
}

private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100

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
