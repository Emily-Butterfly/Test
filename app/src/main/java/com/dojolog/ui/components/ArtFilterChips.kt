package com.dojolog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dojolog.domain.ArtCount
import com.dojolog.ui.theme.LocalDisciplineColors

/**
 * Limits a chart to one martial art: "All arts" plus one chip per art with its colour.
 * Shows nothing for fewer than two arts, where there is nothing to choose. Tapping the
 * selected art again goes back to all arts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtFilterChips(
    arts: List<ArtCount>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (arts.size < 2) return
    val colors = LocalDisciplineColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All arts") },
        )
        arts.forEach { art ->
            FilterChip(
                selected = selected == art.key,
                onClick = { onSelect(if (selected == art.key) null else art.key) },
                label = { Text(art.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.rampForKey(art.key).identity),
                    )
                },
            )
        }
    }
}
