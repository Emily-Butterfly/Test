package com.dojolog.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.Overview
import com.dojolog.domain.Stats
import com.dojolog.domain.StatsPeriod
import com.dojolog.domain.Streaks
import com.dojolog.ui.firstDayOfWeek
import com.dojolog.ui.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.DAYS_90,
    val overview: Overview? = null,
    val streaks: Streaks = Streaks(0, 0),
    val hasSessions: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

class StatsViewModel(repository: TrainingRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val weekStart = firstDayOfWeek()
    private val period = MutableStateFlow(StatsPeriod.DAYS_90)

    val state: StateFlow<StatsUiState> = combine(
        repository.observeSessions(),
        repository.observeTechniques(),
        period,
    ) { sessions, techniques, period ->
        StatsUiState(
            loading = false,
            period = period,
            overview = Stats.overview(sessions, techniques, period, today, weekStart),
            streaks = Stats.streaks(sessions.map { it.date }, today, weekStart),
            hasSessions = sessions.isNotEmpty(),
            today = today,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(value: StatsPeriod) {
        period.value = value
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel(repository) }
        }
    }
}
