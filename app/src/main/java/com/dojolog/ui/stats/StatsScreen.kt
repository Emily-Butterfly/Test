package com.dojolog.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dojolog.domain.ArtCount
import com.dojolog.domain.BucketSize
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.Overview
import com.dojolog.domain.RatingCategory
import com.dojolog.domain.StatsPeriod
import com.dojolog.domain.Streaks
import com.dojolog.ui.Fmt
import com.dojolog.ui.components.ArtFilterChips
import com.dojolog.ui.components.BackupMenu
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenTechnique: (Long) -> Unit,
    onOpenOpponents: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                actions = {
                    IconButton(onClick = onOpenOpponents) {
                        Icon(Icons.Outlined.Groups, contentDescription = "Record by opponent")
                    }
                    BackupMenu(onOpenBackup)
                },
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
                action = {
                    TextButton(onClick = onOpenBackup) { Text("Restore from a backup") }
                },
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
                item(key = "opponents") { OpponentsCard(overview, onOpenOpponents) }
                // The named arts of the period, for the charts' filter chips.
                val arts = overview.disciplines
                    .filter { it.key.isNotEmpty() }
                    .map { ArtCount(it.key, it.name, it.sessions) }
                val chosen = state.arts
                item(key = "activity") {
                    ActivityCard(overview, arts, chosen.activity) { viewModel.setArt(StatsChart.ACTIVITY, it) }
                }
                item(key = "trend") {
                    RatingTrendCard(overview, arts, chosen.trend) { viewModel.setArt(StatsChart.TREND, it) }
                }
                item(key = "breakdown") {
                    BreakdownCard(overview, arts, chosen.breakdown) { viewModel.setArt(StatsChart.BREAKDOWN, it) }
                }
                item(key = "techniques") {
                    TopTechniquesCard(overview, arts, chosen.techniques, { viewModel.setArt(StatsChart.TECHNIQUES, it) }, onOpenTechnique)
                }
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
                supporting = Fmt.count(summary.ratedSessions, "rated session"),
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

/** Your record in the period, and the way to the record against each opponent. */
@Composable
private fun OpponentsCard(overview: Overview, onOpenOpponents: () -> Unit) {
    val record = overview.record
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Sparring & competition",
        subtitle = if (record.matchups > 0) {
            "${Fmt.count(record.matchups, "matchup")} against ${Fmt.count(overview.opponentsFaced, "opponent")}"
        } else {
            null
        },
    ) {
        if (record.matchups == 0) {
            Text(
                "Add opponents to your sparring and competition sessions to track how each matchup went.",
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextSecondary,
            )
        } else {
            TileRow {
                StatTile(
                    label = "Record",
                    value = if (record.scored > 0) "${record.wins}–${record.losses}–${record.draws}" else "–",
                    supporting = if (record.scored > 0) "Wins–losses–draws" else "No results logged",
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = "Record: ${Fmt.recordSpoken(record)}" },
                )
                StatTile(
                    label = "Win rate",
                    value = record.winRate?.let { "${(it * 100).roundToInt()}%" } ?: "–",
                    supporting = if (record.unscored > 0) "${record.unscored} without a result" else Fmt.count(record.scored, "result"),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenOpponents, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Record by opponent")
        }
    }
}

@Composable
private fun ActivityCard(overview: Overview, arts: List<ArtCount>, art: String?, onArt: (String?) -> Unit) {
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
    val artName = arts.nameOf(art)
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Activity",
        subtitle = (if (artName != null) "$artName sessions" else "Sessions") + " per $unit · tap a bar for details",
    ) {
        ArtFilterChips(arts, art, onArt, Modifier.padding(bottom = 8.dp))
        BarChart(bars, color = artColor(art))
    }
}

@Composable
private fun RatingTrendCard(overview: Overview, arts: List<ArtCount>, art: String?, onArt: (String?) -> Unit) {
    val artName = arts.nameOf(art)
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Overall rating",
        subtitle = "Rated ${if (artName != null) "$artName sessions" else "sessions"} in order, out of $MAX_SCORE",
    ) {
        ArtFilterChips(arts, art, onArt, Modifier.padding(bottom = 8.dp))
        if (overview.ratingTrend.size < 2) {
            Text(
                "Rate at least two ${if (artName != null) "$artName sessions" else "sessions"} in this period to see a trend.",
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
                color = artColor(art),
            )
        }
    }
}

@Composable
private fun BreakdownCard(overview: Overview, arts: List<ArtCount>, art: String?, onArt: (String?) -> Unit) {
    val artName = arts.nameOf(art)
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Rating breakdown",
        subtitle = if (artName != null) "$artName sessions, average per category" else "Average per category, out of $MAX_SCORE",
    ) {
        ArtFilterChips(arts, art, onArt, Modifier.padding(bottom = 8.dp))
        val color = artColor(art)
        RatingCategory.entries.forEach { category ->
            val average = overview.categoryAverages[category]
            MeterRow(
                label = category.label,
                fraction = (average ?: 0f) / MAX_SCORE,
                valueText = average?.let { Fmt.decimal(it) } ?: "–",
                color = color,
            )
        }
    }
}

@Composable
private fun TopTechniquesCard(
    overview: Overview,
    arts: List<ArtCount>,
    art: String?,
    onArt: (String?) -> Unit,
    onOpenTechnique: (Long) -> Unit,
) {
    val artName = arts.nameOf(art)
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = "Most practised techniques",
        subtitle = if (artName != null) "Sessions of $artName" else "By number of sessions",
    ) {
        ArtFilterChips(arts, art, onArt, Modifier.padding(bottom = 8.dp))
        if (overview.topTechniques.isEmpty()) {
            Text(
                if (artName != null) "No techniques logged for $artName in this period."
                else "No techniques logged in this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextSecondary,
            )
        }
        val most = overview.topTechniques.maxOfOrNull { it.sessions } ?: 1
        val barColor = artColor(art)
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
                color = barColor,
                onClick = { onOpenTechnique(summary.technique.id) },
            )
        }
    }
}

private fun List<ArtCount>.nameOf(key: String?): String? = key?.let { k -> firstOrNull { it.key == k }?.name }

/** A chart limited to one art takes that art's colour; across all arts it stays neutral. */
@Composable
private fun artColor(art: String?): Color =
    art?.let { LocalDisciplineColors.current.rampForKey(it).identity } ?: DojoColors.ChartSeries

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
