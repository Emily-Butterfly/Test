package com.dojolog.ui.techniques

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.Stats
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.TechniqueSummary
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

enum class TechniqueSort(val label: String) {
    MOST_PRACTISED("Most practised"),
    RECENT("Recently practised"),
    QUALITY("Best quality"),
    NAME("Name (A–Z)"),
}

data class TechniquesUiState(
    val loading: Boolean = true,
    val items: List<TechniqueSummary> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val category: TechniqueCategory? = null,
    val sort: TechniqueSort = TechniqueSort.MOST_PRACTISED,
    val categories: List<TechniqueCategory> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

class TechniquesViewModel(private val repository: TrainingRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<TechniqueCategory?>(null)
    private val sort = MutableStateFlow(TechniqueSort.MOST_PRACTISED)

    private val summaries = combine(repository.observeTechniques(), repository.observeSessions()) { techniques, sessions ->
        Stats.techniqueSummaries(techniques, sessions)
    }

    val state: StateFlow<TechniquesUiState> =
        combine(summaries, query, category, sort) { all, query, category, sort ->
            val needle = query.trim()
            val filtered = all.filter {
                (category == null || it.technique.category == category) &&
                    (needle.isEmpty() || it.technique.name.contains(needle, ignoreCase = true))
            }
            TechniquesUiState(
                loading = false,
                items = filtered.sortedWith(comparatorFor(sort)),
                totalCount = all.size,
                query = query,
                category = category,
                sort = sort,
                categories = all.map { it.technique.category }.distinct().sortedBy { it.ordinal },
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TechniquesUiState())

    /** Error for the create dialog; null when there is nothing to report. */
    var createError by mutableStateOf<String?>(null)
        private set

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(value: TechniqueCategory?) {
        category.value = value
    }

    fun setSort(value: TechniqueSort) {
        sort.value = value
    }

    fun clearCreateError() {
        createError = null
    }

    fun create(name: String, category: TechniqueCategory, notes: String, onCreated: () -> Unit) {
        viewModelScope.launch {
            if (repository.isTechniqueNameTaken(name)) {
                createError = "“${name.trim()}” is already in your library."
            } else {
                repository.createTechnique(name, category, notes)
                createError = null
                onCreated()
            }
        }
    }

    private fun comparatorFor(sort: TechniqueSort): Comparator<TechniqueSummary> {
        val byName = compareBy<TechniqueSummary> { it.technique.name.lowercase() }
        return when (sort) {
            TechniqueSort.MOST_PRACTISED -> compareByDescending<TechniqueSummary> { it.sessions }
                .thenByDescending { it.totalReps }
                .then(byName)
            TechniqueSort.RECENT -> compareByDescending<TechniqueSummary> { it.lastPracticed?.toEpochDay() ?: Long.MIN_VALUE }
                .then(byName)
            TechniqueSort.QUALITY -> compareByDescending<TechniqueSummary> { it.averageQuality ?: -1f }
                .thenByDescending { it.sessions }
                .then(byName)
            TechniqueSort.NAME -> byName
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TechniquesViewModel(repository) }
        }
    }
}
