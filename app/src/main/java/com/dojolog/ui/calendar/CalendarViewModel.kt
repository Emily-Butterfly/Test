package com.dojolog.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.PeriodSummary
import com.dojolog.domain.Stats
import com.dojolog.domain.Streaks
import com.dojolog.domain.TrainingSession
import com.dojolog.ui.firstDayOfWeek
import com.dojolog.ui.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** [heat] is the rating step of the day's best session (0 = trained but unrated). */
data class DayInfo(val sessions: Int, val heat: Int)

data class CalendarUiState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val days: Map<LocalDate, DayInfo> = emptyMap(),
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
        combine(repository.observeSessions(), month, selected) { sessions, month, selected ->
            val inMonth = sessions.filter { YearMonth.from(it.date) == month }
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
                days = inMonth.groupBy { it.date }.mapValues { (_, daySessions) ->
                    DayInfo(daySessions.size, Stats.heatLevel(daySessions.maxOf { it.overall }))
                },
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
