package com.dojolog.ui.techniques

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.MAX_QUALITY
import com.dojolog.domain.PracticeRecord
import com.dojolog.domain.Technique
import com.dojolog.domain.TechniqueDetail
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.BarChart
import com.dojolog.ui.components.ChartBar
import com.dojolog.ui.components.ChartPoint
import com.dojolog.ui.components.EmptyState
import com.dojolog.ui.components.LineChart
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.StarRating
import com.dojolog.ui.components.StatTile
import com.dojolog.ui.components.TileRow
import com.dojolog.ui.theme.DojoColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechniqueDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: TechniqueDetailViewModel = viewModel(factory = TechniqueDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val technique = state.technique
    val detail = state.detail

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(technique?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (technique != null && !state.deleting) {
                        IconButton(onClick = {
                            viewModel.clearEditError()
                            showEdit = true
                        }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit technique")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete technique")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            state.loading || state.deleting -> Box(Modifier.fillMaxSize())
            technique == null || detail == null -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "Technique not found",
                message = "It may have been deleted.",
                modifier = Modifier.padding(padding),
            )
            else -> TechniqueDetailContent(
                technique = technique,
                detail = detail,
                today = state.today,
                onOpenSession = onOpenSession,
                contentPadding = padding,
            )
        }
    }

    if (showEdit && technique != null) {
        TechniqueDialog(
            title = "Edit technique",
            confirmLabel = "Save",
            initialName = technique.name,
            initialCategory = technique.category,
            initialNotes = technique.notes,
            error = viewModel.editError,
            onConfirm = { name, category, notes ->
                viewModel.update(name, category, notes, onSaved = { showEdit = false })
            },
            onDismiss = { showEdit = false },
        )
    }

    if (confirmDelete && technique != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${technique.name}”?") },
            text = {
                Text(
                    "It will be removed from your library and from every session it was logged in. " +
                        "The sessions themselves are kept.",
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
private fun TechniqueDetailContent(
    technique: Technique,
    detail: TechniqueDetail,
    today: LocalDate,
    onOpenSession: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val summary = detail.summary
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(technique.category.label, style = MaterialTheme.typography.labelLarge, color = DojoColors.Primary)
                if (technique.notes.isNotBlank()) {
                    Text(technique.notes, style = MaterialTheme.typography.bodyMedium, color = DojoColors.TextSecondary)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TileRow {
                    StatTile(
                        label = "Sessions",
                        value = summary.sessions.toString(),
                        supporting = "${detail.sessionsLast30Days} in last 30 days",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Total reps",
                        value = if (summary.totalReps > 0) summary.totalReps.toString() else "–",
                        supporting = if (summary.sessions > 0 && summary.totalReps > 0) {
                            "${summary.totalReps / summary.sessions} per session"
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                TileRow {
                    StatTile(
                        label = "Avg quality",
                        value = summary.averageQuality?.let { "${Fmt.decimal(it)} / $MAX_QUALITY" } ?: "–",
                        supporting = if (detail.qualityTrend.isEmpty()) "Not rated yet" else {
                            Fmt.count(detail.qualityTrend.size, "rating")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Last practised",
                        value = summary.lastPracticed?.let { Fmt.relativeDay(it, today) } ?: "Never",
                        supporting = summary.firstPracticed?.let { "Since ${Fmt.mediumDate(it)}" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (summary.sessions > 0) {
            item {
                val bars = remember(detail.monthly) {
                    detail.monthly.map { bucket ->
                        ChartBar(
                            label = Fmt.bucketLabel(bucket),
                            value = bucket.sessions.toFloat(),
                            readout = "${Fmt.bucketName(bucket)} · ${Fmt.count(bucket.sessions, "session")}" +
                                if (bucket.reps > 0) " · ${Fmt.count(bucket.reps, "rep")}" else "",
                        )
                    }
                }
                SectionCard(title = "Practice frequency", subtitle = "Sessions per month") {
                    BarChart(bars)
                }
            }
            item {
                SectionCard(title = "Execution quality", subtitle = "Your 1–$MAX_QUALITY rating each time you drilled it") {
                    if (detail.qualityTrend.size < 2) {
                        Text(
                            "Rate the quality in at least two sessions to see a trend.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DojoColors.TextSecondary,
                        )
                    } else {
                        val points = remember(detail.qualityTrend) {
                            detail.qualityTrend.map { record ->
                                ChartPoint(
                                    value = record.quality.toFloat(),
                                    label = Fmt.dayMonth(record.date),
                                    readout = "${Fmt.mediumDate(record.date)} · ${record.quality} / $MAX_QUALITY",
                                )
                            }
                        }
                        LineChart(
                            points = points,
                            minValue = 0f,
                            maxValue = MAX_QUALITY.toFloat(),
                            ticks = (0..MAX_QUALITY).map { it.toFloat() },
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = "History", subtitle = Fmt.count(detail.history.size, "practice", "practices")) {
                if (detail.history.isEmpty()) {
                    Text(
                        "Not practised yet. Add it to a session to start tracking it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DojoColors.TextSecondary,
                    )
                }
                detail.history.forEachIndexed { index, record ->
                    if (index > 0) HorizontalDivider(color = DojoColors.OutlineVariant)
                    PracticeRow(record, today, onClick = { onOpenSession(record.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun PracticeRow(record: PracticeRecord, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.weekdayDate(record.date), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val details = listOfNotNull(
                record.discipline.ifBlank { null },
                record.reps.takeIf { it > 0 }?.let { Fmt.count(it, "rep") },
                Fmt.relativeDay(record.date, today),
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextSecondary)
            if (record.notes.isNotBlank()) {
                Text(record.notes, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
            }
        }
        if (record.quality > 0) {
            StarRating(record.quality, starSize = 16.dp)
        }
    }
}
