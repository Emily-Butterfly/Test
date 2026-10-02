package com.dojolog.ui.opponents

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.ArtCount
import com.dojolog.domain.MatchRecord
import com.dojolog.domain.Opponent
import com.dojolog.domain.OpponentStats
import com.dojolog.domain.OpponentSummary
import com.dojolog.domain.Stats
import com.dojolog.domain.disciplineKey
import com.dojolog.ui.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OpponentsUiState(
    val loading: Boolean = true,
    /** Matching the search and the art filter, most recently faced first. */
    val items: List<OpponentSummary> = emptyList(),
    val totalCount: Int = 0,
    /** Your record over the people listed. */
    val record: MatchRecord = MatchRecord(),
    /** The named martial arts with matchups, for the filter; shown when there are two or more. */
    val arts: List<ArtCount> = emptyList(),
    /** The art the records are limited to, or null for all. */
    val art: String? = null,
    val today: LocalDate = LocalDate.now(),
)

class OpponentsViewModel(private val repository: TrainingRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val query = MutableStateFlow("")
    private val art = MutableStateFlow<String?>(null)

    val state: StateFlow<OpponentsUiState> = combine(
        repository.observeOpponents(),
        repository.observeSessions(),
        query,
        art,
    ) { opponents, sessions, query, art ->
        val names = Stats.artNames(sessions)
        val arts = sessions
            .filter { it.matchups.isNotEmpty() && disciplineKey(it.discipline).isNotEmpty() }
            .groupBy { disciplineKey(it.discipline) }
            .map { (key, group) -> ArtCount(key, names[key] ?: key, group.size) }
            .sortedWith(compareByDescending<ArtCount> { it.sessions }.thenBy { it.key })
        // Like the Stats filter: it only applies while its chips are shown.
        val selected = art?.takeIf { arts.size > 1 && arts.any { a -> a.key == it } }
        val needle = query.trim()
        val items = OpponentStats.summaries(opponents, sessions, selected, names).filter {
            needle.isEmpty() || it.opponent.name.contains(needle, ignoreCase = true) ||
                it.opponent.club.contains(needle, ignoreCase = true)
        }
        OpponentsUiState(
            loading = false,
            items = items,
            totalCount = opponents.size,
            record = items.fold(MatchRecord()) { total, item -> total + item.record },
            arts = arts,
            art = selected,
            today = today,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpponentsUiState())

    /** Error for the add dialog; null when there is nothing to report. */
    var createError by mutableStateOf<String?>(null)
        private set

    fun setQuery(value: String) {
        query.value = value
    }

    fun setArt(value: String?) {
        art.value = value
    }

    fun clearCreateError() {
        createError = null
    }

    fun create(opponent: Opponent, onCreated: () -> Unit) {
        viewModelScope.launch {
            if (repository.isOpponentNameTaken(opponent.name)) {
                createError = "“${opponent.name.trim()}” is already in your list."
            } else {
                repository.createOpponent(opponent)
                createError = null
                onCreated()
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OpponentsViewModel(repository) }
        }
    }
}
