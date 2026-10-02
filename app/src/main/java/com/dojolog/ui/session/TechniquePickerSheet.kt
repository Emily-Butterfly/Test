package com.dojolog.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dojolog.domain.ArtCount
import com.dojolog.domain.Technique
import com.dojolog.domain.TechniqueCategory
import com.dojolog.domain.disciplineKey
import com.dojolog.ui.theme.DojoColors

/**
 * Search the technique library and add entries to the session; typing a new name offers to
 * create it. Techniques already practised in this session's martial art ([sessionArt]) come
 * first, and every technique shows the arts it was practised in. Stays open so several
 * techniques can be added in a row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TechniquePickerSheet(
    library: List<Technique>,
    arts: Map<Long, List<ArtCount>>,
    sessionArt: String,
    alreadyAdded: Set<Long>,
    onPick: (Technique) -> Unit,
    onCreate: (String, TechniqueCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf(TechniqueCategory.OTHER) }
    val trimmed = query.trim()
    val artKey = disciplineKey(sessionArt)
    val matches = remember(library, arts, artKey, trimmed) {
        val found = if (trimmed.isEmpty()) library else library.filter { it.name.contains(trimmed, ignoreCase = true) }
        // Most practised in this art first; the rest keep the library's A–Z order.
        found.sortedByDescending { technique -> arts[technique.id]?.firstOrNull { it.key == artKey }?.sessions ?: 0 }
    }
    val exactMatch = library.any { it.name.equals(trimmed, ignoreCase = true) }

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
                Text("Add techniques", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search or type a new technique") },
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
                        Text("New technique: “$trimmed”", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Category", style = MaterialTheme.typography.labelMedium, color = DojoColors.TextMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TechniqueCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = newCategory == category,
                                    onClick = { newCategory = category },
                                    label = { Text(category.label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onCreate(trimmed, newCategory)
                                query = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Create and add") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(Modifier.fillMaxWidth()) {
                if (library.isEmpty() && trimmed.isEmpty()) {
                    item {
                        Text(
                            "Your technique library is empty. Type a name above to create your first technique.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DojoColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(matches, key = { it.id }) { technique ->
                    val added = technique.id in alreadyAdded
                    ListItem(
                        headlineContent = { Text(technique.name) },
                        supportingContent = {
                            val practisedIn = arts[technique.id].orEmpty().joinToString(", ") { it.name }
                            Text(
                                if (practisedIn.isEmpty()) technique.category.label
                                else "${technique.category.label} · $practisedIn",
                            )
                        },
                        trailingContent = {
                            if (added) Icon(Icons.Filled.Check, contentDescription = "Added", tint = DojoColors.Primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = !added) { onPick(technique) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
