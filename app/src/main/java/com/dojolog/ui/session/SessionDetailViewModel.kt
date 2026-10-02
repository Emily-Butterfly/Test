package com.dojolog.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dojolog.data.TrainingRepository
import com.dojolog.domain.TrainingSession
import com.dojolog.ui.repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DeleteState { NONE, DELETING, DONE }

data class SessionDetailUiState(
    val loading: Boolean = true,
    val session: TrainingSession? = null,
    val delete: DeleteState = DeleteState.NONE,
    val today: LocalDate = LocalDate.now(),
)

class SessionDetailViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])
    private val deleteState = MutableStateFlow(DeleteState.NONE)

    val state: StateFlow<SessionDetailUiState> =
        combine(repository.observeSession(sessionId), deleteState) { session, delete ->
            SessionDetailUiState(loading = false, session = session, delete = delete)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    /** Finishes the delete before reporting DONE, so leaving the screen can't cancel it. */
    fun delete() {
        if (deleteState.value != DeleteState.NONE) return
        deleteState.value = DeleteState.DELETING
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            deleteState.value = DeleteState.DONE
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SessionDetailViewModel(repository, createSavedStateHandle()) }
        }
    }
}
