package com.dojolog.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.DayMark
import com.dojolog.domain.PeriodSummary
import com.dojolog.domain.Stats
import com.dojolog.domain.Streaks
import com.dojolog.domain.TrainingSession
import com.dojolog.domain.YearOverview
import com.dojolog.domain.disciplineKey
import com.dojolog.ui.firstDayOfWeek
import com.dojolog.ui.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** [marks] holds one colour mark per martial art trained that day. */
data class DayInfo(val sessions: Int, val marks: List<DayMark>)

/** A martial art shown in the calendar legend: its [key] picks the colour. */
data class LegendEntry(val key: String, val name: String)

data class CalendarUiState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    /** First cell of the grid: the 1st, or the start of its week in the previous month. */
    val gridStart: LocalDate = Stats.calendarGridStart(month, firstDayOfWeek),
    /** Last cell of the grid: the last day, or the end of its week in the next month. */
    val gridEnd: LocalDate = Stats.calendarGridEnd(month, firstDayOfWeek),
    /** Days with training, for every date shown in the grid. */
    val days: Map<LocalDate, DayInfo> = emptyMap(),
    /** The martial arts trained on the days shown, in colour-slot order. */
    val legend: List<LegendEntry> = emptyList(),
    val selectedSessions: List<TrainingSession> = emptyList(),
    val monthSummary: PeriodSummary = Stats.summarize(emptyList()),
    val streaks: Streaks = Streaks(0, 0),
    /** The year overview: the viewed month's year unless another one was picked. */
    val year: YearOverview = YearOverview(month.year, emptyMap(), 0, 0, 0, emptyList(), List(12) { 0 }),
    /** The art the year overview is limited to, as applied; null for all. */
    val yearArt: String? = null,
    /** Training days of every month with training, for the month picker. */
    val monthDays: Map<YearMonth, Int> = emptyMap(),
    /** The art with the most sessions: the rating key's colour on a day without training. */
    val mainArt: String? = null,
    val loading: Boolean = true,
)

/** Where the calendar is: the month shown, the chosen day and the year overview's settings. */
private data class CalendarNav(
    val month: YearMonth,
    val selected: LocalDate,
    /** The year the overview shows, or null to follow [month]. */
    val year: Int? = null,
    val yearArt: String? = null,
)

class CalendarViewModel(repository: TrainingRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val weekStart = firstDayOfWeek()
    private val nav = MutableStateFlow(CalendarNav(YearMonth.from(today), today))

    val state: StateFlow<CalendarUiState> =
        combine(
            repository.observeSessions(),
            repository.disciplineSlots.filterNotNull(),
            nav,
        ) { sessions, slots, nav ->
            val month = nav.month
            val selected = nav.selected
            val inMonth = sessions.filter { YearMonth.from(it.date) == month }
            val gridStart = Stats.calendarGridStart(month, weekStart)
            val gridEnd = Stats.calendarGridEnd(month, weekStart)
            val shown = sessions.filter { !it.date.isBefore(gridStart) && !it.date.isAfter(gridEnd) }
            // Paging to another month moves the selection there: today, else the latest
            // training day, else the 1st. Coming back restores the explicit selection.
            val effectiveSelection = when {
                YearMonth.from(selected) == month -> selected
                YearMonth.from(today) == month -> today
                else -> inMonth.maxByOrNull { it.date.toEpochDay() }?.date ?: month.atDay(1)
            }
            // The year overview's filter applies only while its chips show (two or more arts).
            val yearShown = nav.year ?: month.year
            val names = Stats.artNames(sessions)
            val allArts = Stats.yearOverview(sessions, yearShown, slots, names = names)
            val yearArt = nav.yearArt?.takeIf { key -> allArts.arts.size > 1 && allArts.arts.any { it.key == key } }
            val year = if (yearArt == null) allArts else Stats.yearOverview(sessions, yearShown, slots, yearArt, names)
            CalendarUiState(
                month = month,
                selected = effectiveSelection,
                today = today,
                firstDayOfWeek = weekStart,
                gridStart = gridStart,
                gridEnd = gridEnd,
                days = shown.groupBy { it.date }.mapValues { (_, daySessions) ->
                    DayInfo(daySessions.size, Stats.dayMarks(daySessions, slots))
                },
                legend = legendFor(shown, sessions, slots),
                selectedSessions = sessions.filter { it.date == effectiveSelection }.sortedBy { it.createdAt },
                monthSummary = Stats.summarize(inMonth),
                streaks = Stats.streaks(sessions.map { it.date }, today, weekStart),
                year = year,
                yearArt = yearArt,
                monthDays = Stats.trainingDaysByMonth(sessions),
                mainArt = Stats.mainArt(sessions),
                loading = false,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CalendarUiState(YearMonth.from(today), today, today, weekStart),
            )

    /** The arts in [shown], named as first written, in slot order. */
    private fun legendFor(
        shown: List<TrainingSession>,
        all: List<TrainingSession>,
        slots: Map<String, Int>,
    ): List<LegendEntry> {
        val names = Stats.artNames(all)
        return shown.map { disciplineKey(it.discipline) }.distinct()
            .map { key -> LegendEntry(key, names[key] ?: "Unspecified") }
            .sortedWith(compareBy<LegendEntry> { slots[it.key] ?: Int.MAX_VALUE }.thenBy { it.key })
    }

    fun showPreviousMonth() = showMonth(nav.value.month.minusMonths(1))

    fun showNextMonth() = showMonth(nav.value.month.plusMonths(1))

    /** Opens [month]; the year overview follows it. */
    fun showMonth(month: YearMonth) = nav.update { it.copy(month = month, year = null) }

    fun select(date: LocalDate) = nav.update { it.copy(selected = date, month = YearMonth.from(date), year = null) }

    /** Moves the year overview by [years] without changing the month shown. */
    fun shiftYear(years: Int) = nav.update { it.copy(year = (it.year ?: it.month.year) + years) }

    fun setYearArt(key: String?) = nav.update { it.copy(yearArt = key) }

    fun goToToday() = select(today)

    companion object {
        val Factory = viewModelFactory {
            initializer { CalendarViewModel(repository) }
        }
    }
}
