package com.dojolog.ui.opponents

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
import com.dojolog.domain.Opponent
import com.dojolog.domain.OpponentDetail
import com.dojolog.domain.OpponentStats
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

data class OpponentDetailUiState(
    val loading: Boolean = true,
    val opponent: Opponent? = null,
    val detail: OpponentDetail? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

class OpponentDetailViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val opponentId: Long = checkNotNull(savedStateHandle["opponentId"])
    private val today = LocalDate.now()
    private val deleteState = MutableStateFlow(false to false) // deleting to deleted

    val state: StateFlow<OpponentDetailUiState> = combine(
        repository.observeOpponent(opponentId),
        repository.observeSessions(),
        deleteState,
    ) { opponent, sessions, (deleting, deleted) ->
        OpponentDetailUiState(
            loading = false,
            opponent = opponent,
            detail = opponent?.let { OpponentStats.detail(it, sessions) },
            deleting = deleting,
            deleted = deleted,
            today = today,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpponentDetailUiState())

    var editError by mutableStateOf<String?>(null)
        private set

    fun clearEditError() {
        editError = null
    }

    fun update(opponent: Opponent, onSaved: () -> Unit) {
        viewModelScope.launch {
            if (repository.updateOpponent(opponent.copy(id = opponentId))) {
                editError = null
                onSaved()
            } else {
                editError = "Someone else is already called “${opponent.name.trim()}”."
            }
        }
    }

    /** Finishes the delete before reporting it, so leaving the screen can't cancel it. */
    fun delete() {
        if (deleteState.value.first) return
        deleteState.value = true to false
        viewModelScope.launch {
            repository.deleteOpponent(opponentId)
            deleteState.value = true to true
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OpponentDetailViewModel(repository, createSavedStateHandle()) }
        }
    }
}
