package com.dojolog.ui.techniques

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SportsMartialArts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.MAX_QUALITY
import com.dojolog.domain.TechniqueSummary
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.EmptyState
import com.dojolog.ui.components.StarRating
import com.dojolog.ui.theme.DojoColors
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechniquesScreen(
    onOpenTechnique: (Long) -> Unit,
    viewModel: TechniquesViewModel = viewModel(factory = TechniquesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Techniques") },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort techniques")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            TechniqueSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        viewModel.setSort(option)
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (option == state.sort) Icon(Icons.Filled.Check, contentDescription = "Selected")
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.clearCreateError()
                showCreate = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add technique")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!state.loading && state.totalCount == 0) {
                EmptyState(
                    icon = Icons.Outlined.SportsMartialArts,
                    title = "No techniques yet",
                    message = "Build your library here, or add techniques while logging a session. " +
                        "Each one tracks how often you drill it, reps and execution quality.",
                )
                return@Column
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search techniques") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            if (state.categories.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.category == null,
                            onClick = { viewModel.setCategory(null) },
                            label = { Text("All") },
                        )
                    }
                    items(state.categories) { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { viewModel.setCategory(if (state.category == category) null else category) },
                            label = { Text(category.label) },
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "${Fmt.count(state.items.size, "technique")} · ${state.sort.label.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = DojoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.technique.id }) { summary ->
                    TechniqueRow(summary, today = state.today, onClick = { onOpenTechnique(summary.technique.id) })
                }
                if (state.items.isEmpty()) {
                    item {
                        Text(
                            "No techniques match your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DojoColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        TechniqueDialog(
            title = "New technique",
            confirmLabel = "Add",
            error = viewModel.createError,
            onConfirm = { name, category, notes ->
                viewModel.create(name, category, notes, onCreated = { showCreate = false })
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun TechniqueRow(summary: TechniqueSummary, today: LocalDate, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary.technique.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(summary.technique.category.label, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    statsLine(summary, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = DojoColors.TextSecondary,
                )
            }
            summary.averageQuality?.let { quality ->
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    StarRating(quality.roundToInt(), starSize = 14.dp)
                    Text(
                        "${Fmt.decimal(quality)} / $MAX_QUALITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = DojoColors.TextMuted,
                    )
                }
            }
        }
    }
}

private fun statsLine(summary: TechniqueSummary, today: LocalDate): String {
    if (summary.sessions == 0) return "Not practised yet"
    return listOfNotNull(
        Fmt.count(summary.sessions, "session"),
        summary.totalReps.takeIf { it > 0 }?.let { Fmt.count(it, "rep") },
        summary.lastPracticed?.let { Fmt.relativeDay(it, today) },
    ).joinToString(" · ")
}
