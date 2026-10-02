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
    val loading: Boolean = true,
)

class CalendarViewModel(repository: TrainingRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val weekStart = firstDayOfWeek()
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selected = MutableStateFlow(today)

    val state: StateFlow<CalendarUiState> =
        combine(
            repository.observeSessions(),
            repository.disciplineSlots.filterNotNull(),
            month,
            selected,
        ) { sessions, slots, month, selected ->
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

    fun showPreviousMonth() = month.update { it.minusMonths(1) }

    fun showNextMonth() = month.update { it.plusMonths(1) }

    fun select(date: LocalDate) {
        selected.value = date
        month.value = YearMonth.from(date)
    }

    fun goToToday() = select(today)

    companion object {
        val Factory = viewModelFactory {
            initializer { CalendarViewModel(repository) }
        }
    }
}
