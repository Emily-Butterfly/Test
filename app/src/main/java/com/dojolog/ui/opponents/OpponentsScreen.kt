package com.dojolog.ui.opponents

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.Opponent
import com.dojolog.domain.OpponentSummary
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.ArtFilterChips
import com.dojolog.ui.components.ArtTags
import com.dojolog.ui.components.EmptyState
import com.dojolog.ui.theme.DojoColors
import java.time.LocalDate

/** Everyone you spar with or compete against, with your record against each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentsScreen(
    onBack: () -> Unit,
    onOpenOpponent: (Long) -> Unit,
    viewModel: OpponentsViewModel = viewModel(factory = OpponentsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The text lives here so typing never waits on the list; the view model filters by it.
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query) { viewModel.setQuery(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opponents") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.clearCreateError()
                    showCreate = true
                },
                icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                text = { Text("Add opponent") },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize())
            state.totalCount == 0 -> EmptyState(
                icon = Icons.Outlined.Groups,
                title = "No opponents yet",
                message = "Add the people you spar with or compete against, here or right in a sparring or " +
                    "competition session. Every matchup you log builds your record against them.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search by name or club") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.arts.size > 1) {
                    item(key = "arts") {
                        ArtFilterChips(state.arts, state.art, viewModel::setArt)
                    }
                }
                item(key = "summary") {
                    val who = Fmt.count(state.items.size, "person", "people")
                    val record = state.record
                    Text(
                        if (record.matchups > 0) "$who · overall ${Fmt.record(record)}" else who,
                        style = MaterialTheme.typography.labelMedium,
                        color = DojoColors.TextMuted,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .clearAndSetSemantics {
                                contentDescription = if (record.matchups > 0) "$who, overall ${Fmt.recordSpoken(record)}" else who
                            },
                    )
                }
                items(state.items, key = { it.opponent.id }) { summary ->
                    OpponentRow(summary, state.today, onClick = { onOpenOpponent(summary.opponent.id) })
                }
                if (state.items.isEmpty()) {
                    item(key = "none") {
                        Text(
                            if (query.isNotBlank()) "Nobody matches your search." else "Nobody faced in this martial art yet.",
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
        OpponentDialog(
            title = "Add opponent",
            confirmLabel = "Add",
            error = viewModel.createError,
            onConfirm = { opponent -> viewModel.create(opponent, onCreated = { showCreate = false }) },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun OpponentRow(summary: OpponentSummary, today: LocalDate, onClick: () -> Unit) {
    val record = summary.record
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary.opponent.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val about = summary.opponent.about()
                if (about.isNotEmpty()) {
                    Text(about, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
                }
                ArtTags(summary.arts, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (record.matchups == 0) {
                        "Not faced yet"
                    } else {
                        listOfNotNull(
                            Fmt.count(record.matchups, "matchup"),
                            summary.averageRating?.let { "avg ${Fmt.decimal(it)}" },
                            summary.lastFaced?.let { Fmt.relativeDay(it, today) },
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DojoColors.TextSecondary,
                )
            }
            if (record.scored > 0) {
                Spacer(Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = Fmt.recordSpoken(record) },
                ) {
                    Text("${record.wins}–${record.losses}–${record.draws}", style = MaterialTheme.typography.titleMedium)
                    Text("W–L–D", style = MaterialTheme.typography.labelSmall, color = DojoColors.TextMuted)
                }
            }
        }
    }
}

/** "Gracie Barra · Blue belt · -76 kg", leaving out what isn't known. */
internal fun Opponent.about(): String = listOf(club, grade, weight).filter { it.isNotBlank() }.joinToString(" · ")
