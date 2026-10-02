package com.dojolog.ui.opponents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonOff
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.MatchupRecord
import com.dojolog.domain.Opponent
import com.dojolog.domain.OpponentDetail
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.ArtTags
import com.dojolog.ui.components.ChartPoint
import com.dojolog.ui.components.EmptyState
import com.dojolog.ui.components.LineChart
import com.dojolog.ui.components.ResultBadge
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.StatTile
import com.dojolog.ui.components.TileRow
import com.dojolog.ui.theme.DojoColors
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: OpponentDetailViewModel = viewModel(factory = OpponentDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val opponent = state.opponent
    val detail = state.detail

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    val openEdit = {
        viewModel.clearEditError()
        showEdit = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(opponent?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (opponent != null && !state.deleting) {
                        IconButton(onClick = openEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit details")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete opponent")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            state.loading || state.deleting -> Box(Modifier.fillMaxSize())
            opponent == null || detail == null -> EmptyState(
                icon = Icons.Outlined.PersonOff,
                title = "Opponent not found",
                message = "They may have been deleted.",
                modifier = Modifier.padding(padding),
            )
            else -> OpponentDetailContent(
                opponent = opponent,
                detail = detail,
                today = state.today,
                onEdit = openEdit,
                onOpenSession = onOpenSession,
                contentPadding = padding,
            )
        }
    }

    if (showEdit && opponent != null) {
        OpponentDialog(
            title = "Edit details",
            confirmLabel = "Save",
            initial = opponent,
            error = viewModel.editError,
            onConfirm = { viewModel.update(it, onSaved = { showEdit = false }) },
            onDismiss = { showEdit = false },
        )
    }

    if (confirmDelete && opponent != null) {
        val matchups = detail?.summary?.record?.matchups ?: 0
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${opponent.name}”?") },
            text = {
                Text(
                    if (matchups > 0) {
                        "They will be removed with their ${Fmt.count(matchups, "matchup")} from your sessions. " +
                            "The sessions themselves are kept."
                    } else {
                        "They will be removed from your list."
                    },
                )
            },
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
private fun OpponentDetailContent(
    opponent: Opponent,
    detail: OpponentDetail,
    today: LocalDate,
    onEdit: () -> Unit,
    onOpenSession: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val summary = detail.summary
    val record = summary.record
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "about") {
            SectionCard(title = "About") {
                val about = opponent.about()
                if (about.isEmpty() && opponent.notes.isBlank()) {
                    Text(
                        "Nothing noted yet. Add their club, grade, weight and what you've learned about their game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DojoColors.TextSecondary,
                    )
                } else {
                    InfoLine("Club", opponent.club)
                    InfoLine("Grade", opponent.grade)
                    InfoLine("Weight", opponent.weight)
                    if (opponent.notes.isNotBlank()) {
                        Text(
                            opponent.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                ArtTags(summary.arts, showCounts = true, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (about.isEmpty() && opponent.notes.isBlank()) "Add details" else "Edit details")
                }
            }
        }
        item(key = "tiles") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TileRow {
                    StatTile(
                        label = "Record",
                        value = if (record.scored > 0) "${record.wins}–${record.losses}–${record.draws}" else "–",
                        supporting = if (record.scored > 0) "Wins–losses–draws" else "No results yet",
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics { contentDescription = "Record: ${Fmt.recordSpoken(record)}" },
                    )
                    StatTile(
                        label = "Win rate",
                        value = record.winRate?.let { "${(it * 100).roundToInt()}%" } ?: "–",
                        supporting = if (record.unscored > 0) {
                            "${record.scored} of ${record.matchups} with a result"
                        } else {
                            Fmt.count(record.matchups, "matchup")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                TileRow {
                    StatTile(
                        label = "Avg rating",
                        value = summary.averageRating?.let { "${Fmt.decimal(it)} / $MAX_SCORE" } ?: "–",
                        supporting = if (detail.ratingTrend.isEmpty()) "Not rated yet" else Fmt.count(detail.ratingTrend.size, "rating"),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Last faced",
                        value = summary.lastFaced?.let { Fmt.relativeDay(it, today) } ?: "Never",
                        supporting = summary.firstFaced?.let { "Since ${Fmt.mediumDate(it)}" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (detail.ratingTrend.size >= 2) {
            item(key = "trend") {
                SectionCard(title = "How it went", subtitle = "Your rating of each matchup, out of $MAX_SCORE") {
                    val points = remember(detail.ratingTrend) {
                        detail.ratingTrend.map { matchup ->
                            ChartPoint(
                                value = matchup.rating.toFloat(),
                                label = Fmt.dayMonth(matchup.date),
                                readout = "${Fmt.mediumDate(matchup.date)} · ${matchup.result.label} · ${matchup.rating} / $MAX_SCORE",
                            )
                        }
                    }
                    LineChart(points = points, minValue = 0f, maxValue = MAX_SCORE.toFloat(), ticks = listOf(0f, 5f, 10f))
                }
            }
        }
        item(key = "history") {
            SectionCard(title = "Matchups", subtitle = if (record.matchups > 0) "Newest first" else null) {
                if (detail.history.isEmpty()) {
                    Text(
                        "No matchups yet. Add them as an opponent in a sparring or competition session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DojoColors.TextSecondary,
                    )
                }
                detail.history.forEachIndexed { index, matchup ->
                    if (index > 0) HorizontalDivider(color = DojoColors.OutlineVariant)
                    HistoryRow(matchup, onClick = { onOpenSession(matchup.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DojoColors.TextMuted, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryRow(matchup: MatchupRecord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open session", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.weekdayDate(matchup.date), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val details = listOfNotNull(
                matchup.result.label,
                matchup.rating.takeIf { it > 0 }?.let { "rated $it / $MAX_SCORE" },
                matchup.discipline.trim().ifBlank { null },
                matchup.type.label,
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextSecondary)
            if (matchup.notes.isNotBlank()) {
                Text(matchup.notes, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
            }
        }
        Spacer(Modifier.width(8.dp))
        ResultBadge(matchup.result)
    }
}
