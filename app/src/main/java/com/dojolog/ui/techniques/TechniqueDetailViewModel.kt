package com.dojolog.ui.techniques

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.Stats
import com.dojolog.domain.Technique
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.TechniqueDetail
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

data class TechniqueDetailUiState(
    val loading: Boolean = true,
    val technique: Technique? = null,
    val detail: TechniqueDetail? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

class TechniqueDetailViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val techniqueId: Long = checkNotNull(savedStateHandle["techniqueId"])
    private val today = LocalDate.now()
    private val deleteState = MutableStateFlow(false to false) // deleting to deleted

    val state: StateFlow<TechniqueDetailUiState> = combine(
        repository.observeTechnique(techniqueId),
        repository.observeSessions(),
        deleteState,
    ) { technique, sessions, (deleting, deleted) ->
        TechniqueDetailUiState(
            loading = false,
            technique = technique,
            detail = technique?.let { Stats.techniqueDetail(it, sessions, today) },
            deleting = deleting,
            deleted = deleted,
            today = today,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TechniqueDetailUiState())

    var editError by mutableStateOf<String?>(null)
        private set

    fun clearEditError() {
        editError = null
    }

    fun update(name: String, category: TechniqueCategory, notes: String, onSaved: () -> Unit) {
        val current = state.value.technique ?: return
        viewModelScope.launch {
            if (repository.updateTechnique(current.copy(name = name, category = category, notes = notes))) {
                editError = null
                onSaved()
            } else {
                editError = "Another technique is already called “${name.trim()}”."
            }
        }
    }

    /** Finishes the delete before reporting it, so leaving the screen can't cancel it. */
    fun delete() {
        if (deleteState.value.first) return
        deleteState.value = true to false
        viewModelScope.launch {
            repository.deleteTechnique(techniqueId)
            deleteState.value = true to true
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TechniqueDetailViewModel(repository, createSavedStateHandle()) }
        }
    }
}
