package com.dojolog.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.RatingCategory
import com.dojolog.domain.SessionType
import com.dojolog.domain.TechniqueEntry
import com.dojolog.ui.DAY_MILLIS
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.ScoreBadge
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.StarRating
import com.dojolog.ui.theme.DojoColors
import java.time.LocalDate
import kotlin.math.roundToInt

private val QUICK_DURATIONS = listOf(30 to "30m", 45 to "45m", 60 to "1h", 90 to "1.5h", 120 to "2h")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionEditorScreen(
    onDone: () -> Unit,
    viewModel: SessionEditorViewModel = viewModel(factory = SessionEditorViewModel.Factory),
) {
    val state = viewModel.state
    val library by viewModel.library.collectAsStateWithLifecycle()
    val disciplines by viewModel.recentDisciplines.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTechniquePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) onDone()
    }
    val requestClose: () -> Unit = {
        if (viewModel.dirty) {
            confirmDiscard = true
        } else {
            onDone()
        }
    }
    BackHandler(enabled = viewModel.dirty && !viewModel.saved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Log session" else "Edit session") },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "details") {
                DetailsCard(
                    state = state,
                    recentDisciplines = disciplines,
                    onPickDate = { showDatePicker = true },
                    onDuration = viewModel::setDuration,
                    onDiscipline = viewModel::setDiscipline,
                    onType = viewModel::setType,
                )
            }
            item(key = "techniques") {
                TechniquesCard(
                    entries = state.techniques,
                    onAdd = { showTechniquePicker = true },
                    onChange = viewModel::updateEntry,
                    onRemove = viewModel::removeEntry,
                )
            }
            item(key = "rating") {
                RatingCard(
                    state = state,
                    onAuto = viewModel::setOverallAuto,
                    onManual = viewModel::setOverallManual,
                    onCategory = viewModel::setRating,
                )
            }
            item(key = "notes") {
                SectionCard(title = "Notes") {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::setNotes,
                        placeholder = { Text("What clicked? What needs work next time?") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date.toEpochDay() * DAY_MILLIS)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker works in UTC midnight millis.
                    pickerState.selectedDateMillis?.let { viewModel.setDate(LocalDate.ofEpochDay(it / DAY_MILLIS)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTechniquePicker) {
        TechniquePickerSheet(
            library = library,
            alreadyAdded = state.techniques.map { it.techniqueId }.toSet(),
            onPick = viewModel::addTechnique,
            onCreate = viewModel::createAndAddTechnique,
            onDismiss = { showTechniquePicker = false },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this session haven't been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDone()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsCard(
    state: EditorUiState,
    recentDisciplines: List<String>,
    onPickDate: () -> Unit,
    onDuration: (String) -> Unit,
    onDiscipline: (String) -> Unit,
    onType: (SessionType) -> Unit,
) {
    SectionCard(title = "Session") {
        Surface(
            onClick = onPickDate,
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, DojoColors.Outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = DojoColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Date", style = MaterialTheme.typography.labelSmall, color = DojoColors.TextMuted)
                    Text(Fmt.fullDate(state.date), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.durationText,
            onValueChange = onDuration,
            label = { Text("Duration") },
            suffix = { Text("min") },
            singleLine = true,
            isError = state.durationMinutes == null,
            supportingText = if (state.durationMinutes == null) {
                { Text("Enter 1–$MAX_DURATION_MINUTES minutes") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_DURATIONS.forEach { (minutes, label) ->
                FilterChip(
                    selected = state.durationMinutes == minutes,
                    onClick = { onDuration(minutes.toString()) },
                    label = { Text(label) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.discipline,
            onValueChange = onDiscipline,
            label = { Text("Martial art") },
            placeholder = { Text("e.g. BJJ, Karate, Muay Thai") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        val suggestions = recentDisciplines.filterNot { it.equals(state.discipline.trim(), ignoreCase = true) }
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { name ->
                    SuggestionChip(onClick = { onDiscipline(name) }, label = { Text(name) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Type", style = MaterialTheme.typography.labelLarge, color = DojoColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionType.entries.forEach { type ->
                FilterChip(
                    selected = state.type == type,
                    onClick = { onType(type) },
                    label = { Text(type.label) },
                )
            }
        }
    }
}

@Composable
private fun TechniquesCard(
    entries: List<TechniqueEntry>,
    onAdd: () -> Unit,
    onChange: (Int, TechniqueEntry) -> Unit,
    onRemove: (Int) -> Unit,
) {
    SectionCard(
        title = "Techniques",
        subtitle = if (entries.isEmpty()) null else Fmt.count(entries.size, "technique"),
    ) {
        if (entries.isEmpty()) {
            Text(
                "Add what you drilled to track reps and execution quality per technique over time.",
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
        }
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = DojoColors.OutlineVariant)
            TechniqueEntryEditor(
                entry = entry,
                onChange = { onChange(index, it) },
                onRemove = { onRemove(index) },
            )
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add technique")
        }
    }
}

@Composable
private fun TechniqueEntryEditor(
    entry: TechniqueEntry,
    onChange: (TechniqueEntry) -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.techniqueName, style = MaterialTheme.typography.titleSmall)
                Text(entry.category.label, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${entry.techniqueName}")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (entry.reps > 0) entry.reps.toString() else "",
                onValueChange = { text ->
                    onChange(entry.copy(reps = text.filter(Char::isDigit).take(5).toIntOrNull() ?: 0))
                },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Quality", style = MaterialTheme.typography.labelSmall, color = DojoColors.TextMuted)
                StarRating(
                    value = entry.quality,
                    starSize = 24.dp,
                    onValueChange = { onChange(entry.copy(quality = it)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = entry.notes,
            onValueChange = { onChange(entry.copy(notes = it)) },
            placeholder = { Text("Note (optional)") },
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RatingCard(
    state: EditorUiState,
    onAuto: (Boolean) -> Unit,
    onManual: (Int) -> Unit,
    onCategory: (RatingCategory, Int) -> Unit,
) {
    SectionCard(title = "Rating") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Overall score", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        !state.overallAuto -> "Set by hand"
                        state.overall > 0f -> "Average of the rated categories"
                        else -> "Rate a category below to score the session"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DojoColors.TextMuted,
                )
            }
            ScoreBadge(state.overall, state.discipline, large = true)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Calculate from categories",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.overallAuto, onCheckedChange = onAuto)
        }
        if (!state.overallAuto) {
            Slider(
                value = state.overallManual.coerceIn(1, MAX_SCORE).toFloat(),
                onValueChange = { onManual(it.roundToInt()) },
                valueRange = 1f..MAX_SCORE.toFloat(),
                steps = MAX_SCORE - 2,
            )
        }
        HorizontalDivider(color = DojoColors.OutlineVariant, modifier = Modifier.padding(vertical = 12.dp))
        Text(
            "Slide a category to 0 if it didn't apply today.",
            style = MaterialTheme.typography.bodySmall,
            color = DojoColors.TextMuted,
        )
        RatingCategory.entries.forEach { category ->
            CategorySlider(
                category = category,
                score = state.ratings[category],
                onChange = { onCategory(category, it) },
            )
        }
    }
}

@Composable
private fun CategorySlider(category: RatingCategory, score: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(category.label, style = MaterialTheme.typography.bodyLarge)
                Text(category.hint, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
            }
            Text(
                if (score > 0) "$score" else "–",
                style = MaterialTheme.typography.titleMedium,
                color = if (score > 0) DojoColors.TextPrimary else DojoColors.TextMuted,
            )
        }
        Slider(
            value = score.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..MAX_SCORE.toFloat(),
            steps = MAX_SCORE - 1,
        )
    }
}
