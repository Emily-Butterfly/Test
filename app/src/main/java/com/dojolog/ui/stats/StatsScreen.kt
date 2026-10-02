package com.dojolog.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.BucketSize
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.Overview
import com.dojolog.domain.RatingCategory
import com.dojolog.domain.StatsPeriod
import com.dojolog.domain.Streaks
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.BarChart
import com.dojolog.ui.components.ChartBar
import com.dojolog.ui.components.ChartPoint
import com.dojolog.ui.components.EmptyState
import com.dojolog.ui.components.LineChart
import com.dojolog.ui.components.MeterRow
import com.dojolog.ui.components.SectionCard
import com.dojolog.ui.components.StatTile
import com.dojolog.ui.components.TileRow
import com.dojolog.ui.theme.DojoColors
import com.dojolog.ui.theme.LocalDisciplineColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenTechnique: (Long) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val overview = state.overview
        if (!state.loading && !state.hasSessions) {
            EmptyState(
                icon = Icons.Outlined.Insights,
                title = "No stats yet",
                message = "Log your first training session and your progress will show up here.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "period") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StatsPeriod.entries) { period ->
                        FilterChip(
                            selected = state.period == period,
                            onClick = { viewModel.setPeriod(period) },
                            label = { Text(period.label) },
                        )
                    }
                }
            }
            if (overview != null) {
                item(key = "tiles") { SummaryTiles(overview, state.streaks) }
                item(key = "activity") { ActivityCard(overview) }
                item(key = "trend") { RatingTrendCard(overview) }
                item(key = "breakdown") { BreakdownCard(overview) }
                item(key = "techniques") { TopTechniquesCard(overview, onOpenTechnique) }
                if (overview.disciplines.size > 1) {
                    item(key = "disciplines") { DisciplinesCard(overview) }
                }
            }
        }
    }
}

@Composable
private fun SummaryTiles(overview: Overview, streaks: Streaks) {
    val summary = overview.summary
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TileRow {
            StatTile(
                label = "Sessions",
                value = summary.sessions.toString(),
                supporting = Fmt.count(summary.trainingDays, "training day"),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Time trained",
                value = Fmt.hours(summary.totalMinutes),
                supporting = if (summary.sessions > 0) "${Fmt.duration(summary.averageMinutes)} avg" else null,
                modifier = Modifier.weight(1f),
            )
        }
        TileRow {
            StatTile(
                label = "Avg rating",
                value = summary.averageOverall?.let { "${Fmt.decimal(it)} / $MAX_SCORE" } ?: "–",
                supporting = Fmt.count(overview.ratingTrend.size, "rated session"),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Week streak",
                value = Fmt.count(streaks.currentWeeks, "week"),
                supporting = "Best: ${Fmt.count(streaks.longestWeeks, "week")}",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActivityCard(overview: Overview) {
    val bars = remember(overview.activity) {
        overview.activity.map { bucket ->
            ChartBar(
                label = Fmt.bucketLabel(bucket),
                value = bucket.sessions.toFloat(),
                readout = "${Fmt.bucketName(bucket)} · ${Fmt.count(bucket.sessions, "session")} · " +
                    Fmt.hours(bucket.minutes),
            )
        }
    }
    val unit = when (overview.activity.firstOrNull()?.size) {
        BucketSize.WEEK -> "week"
        BucketSize.YEAR -> "year"
        else -> "month"
    }
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Activity",
        subtitle = "Sessions per $unit · tap a bar for details",
    ) {
        BarChart(bars)
    }
}

@Composable
private fun RatingTrendCard(overview: Overview) {
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Overall rating",
        subtitle = "Rated sessions in order, out of $MAX_SCORE",
    ) {
        if (overview.ratingTrend.size < 2) {
            Text(
                "Rate at least two sessions in this period to see a trend.",
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextSecondary,
            )
        } else {
            val points = remember(overview.ratingTrend) {
                overview.ratingTrend.map { session ->
                    ChartPoint(
                        value = session.overall,
                        label = Fmt.dayMonth(session.date),
                        readout = "${Fmt.mediumDate(session.date)} · ${session.discipline.ifBlank { "Training" }} · " +
                            "${Fmt.score(session.overall)} / $MAX_SCORE",
                    )
                }
            }
            LineChart(
                points = points,
                minValue = 0f,
                maxValue = MAX_SCORE.toFloat(),
                ticks = listOf(0f, 5f, 10f),
            )
        }
    }
}

@Composable
private fun BreakdownCard(overview: Overview) {
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Rating breakdown",
        subtitle = "Average per category, out of $MAX_SCORE",
    ) {
        RatingCategory.entries.forEach { category ->
            val average = overview.categoryAverages[category]
            MeterRow(
                label = category.label,
                fraction = (average ?: 0f) / MAX_SCORE,
                valueText = average?.let { Fmt.decimal(it) } ?: "–",
            )
        }
    }
}

@Composable
private fun TopTechniquesCard(overview: Overview, onOpenTechnique: (Long) -> Unit) {
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Most practised techniques",
        subtitle = "By number of sessions",
    ) {
        if (overview.topTechniques.isEmpty()) {
            Text(
                "No techniques logged in this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextSecondary,
            )
        }
        val most = overview.topTechniques.maxOfOrNull { it.sessions } ?: 1
        overview.topTechniques.forEach { summary ->
            MeterRow(
                label = summary.technique.name,
                fraction = summary.sessions / most.toFloat(),
                valueText = Fmt.count(summary.sessions, "session"),
                supporting = listOfNotNull(
                    summary.technique.category.label,
                    summary.totalReps.takeIf { it > 0 }?.let { Fmt.count(it, "rep") },
                    summary.averageQuality?.let { "quality ${Fmt.decimal(it)}/5" },
                ).joinToString(" · "),
                onClick = { onOpenTechnique(summary.technique.id) },
            )
        }
    }
}

@Composable
private fun DisciplinesCard(overview: Overview) {
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Martial arts",
        subtitle = "Time trained",
    ) {
        val most = overview.disciplines.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
        val colors = LocalDisciplineColors.current
        overview.disciplines.forEach { share ->
            MeterRow(
                label = share.name,
                fraction = share.minutes / most.toFloat(),
                valueText = Fmt.hours(share.minutes),
                supporting = Fmt.count(share.sessions, "session"),
                color = colors.rampForKey(share.key).identity,
            )
        }
    }
}
