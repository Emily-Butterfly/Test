package com.dojolog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dojolog.domain.MAX_QUALITY
import com.dojolog.domain.MAX_SCORE
import com.dojolog.domain.Stats
import com.dojolog.ui.Fmt
import com.dojolog.ui.theme.DojoColors
import com.dojolog.ui.theme.LocalDisciplineColors
import com.dojolog.ui.theme.Ramp

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
                        }
                    }
                    action?.invoke()
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = DojoColors.TextMuted, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = DojoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = DojoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Two equal-width tiles side by side. */
@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/**
 * A session score in its martial art's colour, brighter for better ratings; an outline in
 * the art's colour around a dash when unrated.
 */
@Composable
fun ScoreBadge(score: Float, discipline: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val shape = RoundedCornerShape(if (large) 14.dp else 10.dp)
    val level = Stats.heatLevel(score)
    val ramp = LocalDisciplineColors.current.ramp(discipline)
    val description = if (level > 0) "Rating ${Fmt.score(score)} out of $MAX_SCORE" else "Not rated"
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = if (large) 64.dp else 44.dp, minHeight = if (large) 48.dp else 32.dp)
            .clip(shape)
            .then(
                if (level > 0) Modifier.background(ramp.fill(level))
                else Modifier.border(1.5.dp, ramp.identity, shape),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Fmt.score(score),
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (level > 0) ramp.ink(level) else DojoColors.TextMuted,
        )
    }
}

/** Diagonal hatching in a martial art's colour: trained, but not rated. */
fun Modifier.unratedHatch(ramp: Ramp): Modifier = drawBehind {
    clipRect {
        drawRect(ramp.steps[0].copy(alpha = 0.30f))
        val gap = 5.dp.toPx()
        val stroke = 1.5.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = ramp.identity.copy(alpha = 0.8f),
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = stroke,
            )
            x += gap
        }
    }
}

/** Read-only when [onValueChange] is null. Tapping the current value again clears it. */
@Composable
fun StarRating(
    value: Int,
    modifier: Modifier = Modifier,
    max: Int = MAX_QUALITY,
    starSize: Dp = 18.dp,
    onValueChange: ((Int) -> Unit)? = null,
) {
    val description = if (value > 0) "Quality $value of $max" else "Quality not rated"
    Row(
        modifier = if (onValueChange == null) {
            modifier.clearAndSetSemantics { contentDescription = description }
        } else {
            modifier.semantics { contentDescription = description }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (star in 1..max) {
            val filled = star <= value
            val icon = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder
            val tint = if (filled) DojoColors.Star else DojoColors.StarEmpty
            if (onValueChange != null) {
                IconButton(
                    onClick = { onValueChange(if (value == star) 0 else star) },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(icon, contentDescription = "$star of $max", tint = tint, modifier = Modifier.size(starSize))
                }
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(starSize))
            }
        }
    }
}

/** Horizontal bar with a faint track of the same hue. [fraction] is clamped to 0..1. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = DojoColors.ChartSeries) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.18f)),
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

@Composable
fun MeterRow(
    label: String,
    fraction: Float,
    valueText: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    color: Color = DojoColors.ChartSeries,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = DojoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = DojoColors.TextPrimary,
            )
        }
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = DojoColors.TextMuted)
        }
        Spacer(Modifier.height(6.dp))
        Meter(fraction, color = color)
    }
}

/** Small non-interactive name pills, e.g. the techniques of a session. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Pills(labels: List<String>, modifier: Modifier = Modifier, maxShown: Int = 5) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.take(maxShown).forEach { Pill(it) }
        if (labels.size > maxShown) Pill("+${labels.size - maxShown}")
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = DojoColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DojoColors.SurfaceHighest)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DojoColors.PrimaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = DojoColors.OnPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = DojoColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** A coloured square plus a label, for chart and calendar legends; hatched when [hatch] is set. */
@Composable
fun LegendSwatch(color: Color, label: String, hatch: Ramp? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .then(if (hatch != null) Modifier.unratedHatch(hatch) else Modifier.background(color)),
        )
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = DojoColors.TextSecondary)
    }
}
