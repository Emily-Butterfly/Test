package com.dojolog.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dojolog.domain.OpponentStats
import com.dojolog.domain.TrainingSession
import com.dojolog.ui.Fmt
import com.dojolog.ui.theme.DojoColors

@Composable
fun SessionCard(
    session: TrainingSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (showDate) {
                    Text(
                        Fmt.weekdayDate(session.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = DojoColors.TextMuted,
                    )
                }
                Text(
                    session.discipline.ifBlank { "Training" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${session.type.label} · ${Fmt.duration(session.durationMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DojoColors.TextSecondary,
                )
                if (session.matchups.isNotEmpty()) {
                    val names = session.matchups.map { it.opponentName }.distinct().joinToString(", ")
                    Text(
                        "vs $names · ${Fmt.record(OpponentStats.record(listOf(session)))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DojoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (session.techniques.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Pills(session.techniques.map { it.techniqueName })
                }
            }
            Spacer(Modifier.width(12.dp))
            ScoreBadge(session.overall, session.discipline)
        }
    }
}
