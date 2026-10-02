package com.dojolog.ui.session

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
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.RatingCategory
import com.dojolog.domain.Ratings
import com.dojolog.domain.SessionType
import com.dojolog.domain.Technique
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.TechniqueEntry
import com.dojolog.domain.TrainingSession
import com.dojolog.ui.repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class EditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val durationText: String = "60",
    val discipline: String = "",
    val type: SessionType = SessionType.CLASS,
    val notes: String = "",
    val ratings: Ratings = Ratings(),
    val overallAuto: Boolean = true,
    val overallManual: Int = 0,
    val techniques: List<TechniqueEntry> = emptyList(),
    val createdAt: Long = 0,
) {
    /** What will be saved as the overall score (0 = unrated). */
    val overall: Float get() = if (overallAuto) ratings.average() ?: 0f else overallManual.toFloat()

    val durationMinutes: Int? get() = durationText.toIntOrNull()?.takeIf { it in 1..MAX_DURATION_MINUTES }

    val canSave: Boolean get() = loaded && durationMinutes != null
}

const val MAX_DURATION_MINUTES = 24 * 60

class SessionEditorViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sessionId: Long = savedStateHandle["sessionId"] ?: 0L
    private val initialEpochDay: Long = savedStateHandle["date"] ?: NO_DATE

    var state by mutableStateOf(EditorUiState())
        private set

    /** True once the user changed anything, so leaving can ask before discarding. */
    var dirty by mutableStateOf(false)
        private set

    var saved by mutableStateOf(false)
        private set

    val library: StateFlow<List<Technique>> =
        repository.observeTechniques().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentDisciplines: StateFlow<List<String>> =
        repository.observeRecentDisciplines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val existing = if (sessionId != 0L) repository.getSession(sessionId) else null
            state = if (existing != null) {
                EditorUiState(
                    loaded = true,
                    isNew = false,
                    date = existing.date,
                    durationText = existing.durationMinutes.toString(),
                    discipline = existing.discipline,
                    type = existing.type,
                    notes = existing.notes,
                    ratings = existing.ratings,
                    overallAuto = existing.overallAuto,
                    overallManual = existing.overall.roundToInt(),
                    techniques = existing.techniques,
                    createdAt = existing.createdAt,
                )
            } else {
                EditorUiState(
                    loaded = true,
                    isNew = true,
                    date = if (initialEpochDay != NO_DATE) LocalDate.ofEpochDay(initialEpochDay) else LocalDate.now(),
                    // Most people train one art most of the time: start from the last one used.
                    discipline = repository.observeRecentDisciplines().first().firstOrNull().orEmpty(),
                )
            }
        }
    }

    private fun edit(transform: (EditorUiState) -> EditorUiState) {
        state = transform(state)
        dirty = true
    }

    fun setDate(date: LocalDate) = edit { it.copy(date = date) }

    fun setDuration(text: String) = edit { it.copy(durationText = text.filter(Char::isDigit).take(4)) }

    fun setDiscipline(text: String) = edit { it.copy(discipline = text.take(60)) }

    fun setType(type: SessionType) = edit { it.copy(type = type) }

    fun setNotes(text: String) = edit { it.copy(notes = text) }

    fun setRating(category: RatingCategory, score: Int) = edit { it.copy(ratings = it.ratings.with(category, score)) }

    fun setOverallAuto(auto: Boolean) = edit {
        val manual = if (!auto && it.overallManual == 0) {
            it.ratings.average()?.roundToInt() ?: (MAX_SCORE / 2)
        } else {
            it.overallManual
        }
        it.copy(overallAuto = auto, overallManual = manual)
    }

    fun setOverallManual(score: Int) = edit { it.copy(overallManual = score.coerceIn(1, MAX_SCORE)) }

    fun addTechnique(technique: Technique) = edit { current ->
        if (current.techniques.any { it.techniqueId == technique.id }) {
            current
        } else {
            current.copy(
                techniques = current.techniques + TechniqueEntry(technique.id, technique.name, technique.category),
            )
        }
    }

    fun createAndAddTechnique(name: String, category: TechniqueCategory) {
        if (name.isBlank()) return
        viewModelScope.launch { addTechnique(repository.createTechnique(name, category)) }
    }

    fun updateEntry(index: Int, entry: TechniqueEntry) = edit {
        it.copy(techniques = it.techniques.toMutableList().also { list -> list[index] = entry })
    }

    fun removeEntry(index: Int) = edit {
        it.copy(techniques = it.techniques.toMutableList().also { list -> list.removeAt(index) })
    }

    private var saving = false

    fun save() {
        val current = state
        val minutes = current.durationMinutes ?: return
        // A quick double tap must not insert the session twice.
        if (!current.loaded || saving) return
        saving = true
        viewModelScope.launch {
            repository.saveSession(
                TrainingSession(
                    id = sessionId,
                    date = current.date,
                    durationMinutes = minutes,
                    discipline = current.discipline,
                    type = current.type,
                    notes = current.notes,
                    overall = current.overall,
                    overallAuto = current.overallAuto,
                    ratings = current.ratings,
                    techniques = current.techniques,
                    createdAt = current.createdAt,
                ),
            )
            saved = true
        }
    }

    companion object {
        const val NO_DATE = Long.MIN_VALUE

        val Factory = viewModelFactory {
            initializer { SessionEditorViewModel(repository, createSavedStateHandle()) }
        }
    }
}
