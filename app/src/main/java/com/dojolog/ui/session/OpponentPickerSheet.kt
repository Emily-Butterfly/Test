package com.dojolog.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dojolog.domain.MatchRecord
import com.dojolog.domain.Opponent
import com.dojolog.domain.OpponentSummary
import com.dojolog.domain.nameKey
import com.dojolog.ui.Fmt
import com.dojolog.ui.theme.DojoColors

/**
 * Pick who you faced; typing a new name offers to add the person. The people you faced
 * most recently come first. Someone can be added more than once (several rounds), and the
 * sheet stays open so a whole session's rounds can be added in a row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentPickerSheet(
    opponents: List<OpponentSummary>,
    /** How many times each opponent (by id) is already in this session. */
    addedCounts: Map<Long, Int>,
    onPick: (Opponent) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val matches = remember(opponents, trimmed) {
        if (trimmed.isEmpty()) opponents else opponents.filter { it.opponent.name.contains(trimmed, ignoreCase = true) }
    }
    val exactMatch = opponents.any { nameKey(it.opponent.name) == nameKey(trimmed) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add opponents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search or type a new name") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            if (trimmed.isNotEmpty() && !exactMatch) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("New opponent: “$trimmed”", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "You can add their club, grade and notes later under Stats › Opponents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DojoColors.TextMuted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onCreate(trimmed)
                                query = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add and log a matchup") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(Modifier.fillMaxWidth()) {
                if (opponents.isEmpty() && trimmed.isEmpty()) {
                    item {
                        Text(
                            "Nobody here yet. Type the name of your training partner or opponent above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DojoColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(matches, key = { it.opponent.id }) { summary ->
                    val added = addedCounts[summary.opponent.id] ?: 0
                    ListItem(
                        headlineContent = { Text(summary.opponent.name) },
                        supportingContent = {
                            Text(
                                pickerDetails(summary, Fmt::record),
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = pickerDetails(summary, Fmt::recordSpoken)
                                },
                            )
                        },
                        trailingContent = {
                            if (added > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = DojoColors.Primary)
                                    if (added > 1) Text("×$added", style = MaterialTheme.typography.labelLarge, color = DojoColors.Primary)
                                }
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = DojoColors.TextMuted)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(
                            onClickLabel = if (added > 0) "Add another matchup" else "Add matchup",
                        ) { onPick(summary.opponent) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * "Gracie Barra · 3W 1L 1D": what helps tell people apart, then the record so far, written
 * by [record] (short on screen, in words for screen readers).
 */
private fun pickerDetails(summary: OpponentSummary, record: (MatchRecord) -> String): String {
    val opponent = summary.opponent
    val about = listOf(opponent.club, opponent.grade).filter { it.isNotBlank() }
    val result = if (summary.record.matchups == 0) "Not faced yet" else record(summary.record)
    return (about + result).joinToString(" · ")
}
