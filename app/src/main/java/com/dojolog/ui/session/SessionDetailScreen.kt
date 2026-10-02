package com.dojolog.ui.session

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.Matchup
import com.dojolog.domain.OpponentStats
import com.dojolog.domain.RatingCategory
import com.dojolog.domain.TechniqueEntry
import com.dojolog.domain.TrainingSession
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.EmptyState
import com.dojolog.ui.components.MeterRow
import com.dojolog.ui.components.ResultBadge
import com.dojolog.ui.components.ScoreBadge
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.StarRating
import com.dojolog.ui.theme.DojoColors
import com.dojolog.ui.theme.LocalDisciplineColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenTechnique: (Long) -> Unit,
    onOpenOpponent: (Long) -> Unit,
    viewModel: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val session = state.session

    LaunchedEffect(state.delete) {
        if (state.delete == DeleteState.DONE) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.let { Fmt.weekdayDate(it.date) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null && state.delete == DeleteState.NONE) {
                        IconButton(onClick = { onEdit(session.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit session")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete session")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            state.loading || state.delete != DeleteState.NONE -> Box(Modifier.fillMaxSize())
            session == null -> EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = "Session not found",
                message = "It may have been deleted.",
                modifier = Modifier.padding(padding),
            )
            else -> SessionDetailContent(
                session = session,
                today = state.today,
                onOpenTechnique = onOpenTechnique,
                onOpenOpponent = onOpenOpponent,
                contentPadding = padding,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("The session, its ratings, techniques and matchups will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionDetailContent(
    session: TrainingSession,
    today: LocalDate,
    onOpenTechnique: (Long) -> Unit,
    onOpenOpponent: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.discipline.ifBlank { "Training" },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "${session.type.label} · ${Fmt.duration(session.durationMinutes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DojoColors.TextSecondary,
                        )
                        Text(
                            Fmt.relativeDay(session.date, today),
                            style = MaterialTheme.typography.bodySmall,
                            color = DojoColors.TextMuted,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ScoreBadge(session.overall, session.discipline, large = true)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (session.isRated) "Overall / $MAX_SCORE" else "Overall",
                            style = MaterialTheme.typography.labelSmall,
                            color = DojoColors.TextMuted,
                        )
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Rating breakdown",
                subtitle = when {
                    !session.isRated -> "Not rated"
                    session.overallAuto -> "Overall is the average of the rated categories"
                    else -> "Overall was set by hand"
                },
            ) {
                val artColor = LocalDisciplineColors.current.ramp(session.discipline).identity
                RatingCategory.entries.forEach { category ->
                    val score = session.ratings[category]
                    MeterRow(
                        label = category.label,
                        fraction = score / MAX_SCORE.toFloat(),
                        valueText = if (score > 0) "$score" else "–",
                        color = artColor,
                    )
                }
            }
        }
        if (session.matchups.isNotEmpty()) {
            item {
                val record = OpponentStats.record(listOf(session))
                SectionCard(title = "Opponents", subtitle = Fmt.record(record)) {
                    session.matchups.forEachIndexed { index, matchup ->
                        if (index > 0) HorizontalDivider(color = DojoColors.OutlineVariant)
                        MatchupRow(matchup, onClick = { onOpenOpponent(matchup.opponentId) })
                    }
                }
            }
        }
        item {
            SectionCard(title = "Techniques", subtitle = Fmt.count(session.techniques.size, "technique")) {
                if (session.techniques.isEmpty()) {
                    Text(
                        "No techniques logged for this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DojoColors.TextSecondary,
                    )
                }
                session.techniques.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(color = DojoColors.OutlineVariant)
                    TechniqueEntryRow(entry, onClick = { onOpenTechnique(entry.techniqueId) })
                }
            }
        }
        if (session.notes.isNotBlank()) {
            item {
                SectionCard(title = "Notes") {
                    Text(session.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TechniqueEntryRow(entry: TechniqueEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.techniqueName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val details = listOfNotNull(
                entry.category.label,
                entry.reps.takeIf { it > 0 }?.let { Fmt.count(it, "rep") },
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextSecondary)
            if (entry.notes.isNotBlank()) {
                Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
            }
        }
        if (entry.quality > 0) {
            StarRating(entry.quality, starSize = 16.dp)
        }
    }
}

@Composable
private fun MatchupRow(matchup: Matchup, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open ${matchup.opponentName}", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(matchup.opponentName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val details = listOfNotNull(
                matchup.result.label,
                matchup.rating.takeIf { it > 0 }?.let { "rated $it / $MAX_SCORE" },
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextSecondary)
            if (matchup.notes.isNotBlank()) {
                Text(matchup.notes, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
            }
        }
        ResultBadge(matchup.result)
    }
}
