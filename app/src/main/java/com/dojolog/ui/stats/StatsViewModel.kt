package com.dojolog.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.ChartArts
import com.dojolog.domain.Overview
import com.dojolog.domain.Stats
import com.dojolog.domain.StatsPeriod
import com.dojolog.domain.Streaks
import com.dojolog.domain.disciplineKey
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
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.DAYS_90,
    val overview: Overview? = null,
    val streaks: Streaks = Streaks(0, 0),
    val hasSessions: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    /** The martial art each chart is limited to, as applied (see [StatsViewModel.setArt]). */
    val arts: ChartArts = ChartArts(),
)

/** The Stats charts that can be limited to one martial art. */
enum class StatsChart { ACTIVITY, TREND, BREAKDOWN, TECHNIQUES }

class StatsViewModel(repository: TrainingRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val weekStart = firstDayOfWeek()
    private val period = MutableStateFlow(StatsPeriod.DAYS_90)
    private val chartArts = MutableStateFlow<Map<StatsChart, String>>(emptyMap())

    val state: StateFlow<StatsUiState> = combine(
        repository.observeSessions(),
        repository.observeTechniques(),
        period,
        chartArts,
    ) { sessions, techniques, period, chartArts ->
        // A chart's filter only applies while its chips are shown: two or more named arts in
        // the period, the chosen one among them. Otherwise the chart falls back to all arts,
        // and the choice is kept for when such a period is picked again.
        val namedArts = Stats.inPeriod(sessions, period, today)
            .map { disciplineKey(it.discipline) }
            .filter { it.isNotEmpty() }
            .toSet()
        fun art(chart: StatsChart) = chartArts[chart]?.takeIf { namedArts.size > 1 && it in namedArts }
        val arts = ChartArts(
            activity = art(StatsChart.ACTIVITY),
            trend = art(StatsChart.TREND),
            breakdown = art(StatsChart.BREAKDOWN),
            techniques = art(StatsChart.TECHNIQUES),
        )
        StatsUiState(
            loading = false,
            period = period,
            overview = Stats.overview(sessions, techniques, period, today, weekStart, arts),
            arts = arts,
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

    /** Limits [chart] to the martial art [key] (a [disciplineKey]); null shows every art. */
    fun setArt(chart: StatsChart, key: String?) {
        chartArts.update { if (key == null) it - chart else it + (chart to key) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel(repository) }
        }
    }
}
