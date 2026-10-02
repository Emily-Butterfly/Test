package com.dojolog.ui.opponents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dojolog.domain.Opponent

/** Add a person or edit what you know about them. [error] is shown under the name, e.g. for a duplicate. */
@Composable
fun OpponentDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (Opponent) -> Unit,
    onDismiss: () -> Unit,
    initial: Opponent = Opponent(id = 0, name = ""),
    error: String? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var club by rememberSaveable { mutableStateOf(initial.club) }
    var grade by rememberSaveable { mutableStateOf(initial.grade) }
    var weight by rememberSaveable { mutableStateOf(initial.weight) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = club,
                    onValueChange = { club = it.take(80) },
                    label = { Text("Club or gym") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it.take(40) },
                    label = { Text("Belt, grade or rank") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.take(40) },
                    label = { Text("Weight or weight class") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("Style, strengths, what works against them") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        initial.copy(
                            name = name.trim(),
                            club = club.trim(),
                            grade = grade.trim(),
                            weight = weight.trim(),
                            notes = notes.trim(),
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
