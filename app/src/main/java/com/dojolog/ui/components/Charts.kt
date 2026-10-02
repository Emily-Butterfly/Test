package com.dojolog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dojolog.domain.Stats
import com.dojolog.ui.theme.DojoColors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** [readout] is shown above the chart when the bar is tapped (the touch stand-in for a hover tooltip). */
data class ChartBar(val label: String, val value: Float, val readout: String)

data class ChartPoint(val value: Float, val label: String, val readout: String)

fun formatTick(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format("%.1f", value)

/**
 * Single-series column chart: bars at most 24dp wide with 4dp rounded tops grown from one
 * baseline, hairline gridlines at round ticks. Tap a bar to read its value.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    wholeNumbers: Boolean = true,
) {
    if (bars.isEmpty()) return
    var selected by remember(bars) {
        mutableIntStateOf(bars.indexOfLast { it.value > 0f }.let { if (it >= 0) it else bars.lastIndex })
    }
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = DojoColors.TextMuted)
    val ticks = remember(bars, wholeNumbers) {
        Stats.niceTicks(bars.maxOf { it.value }, maxTicks = 3, minStep = if (wholeNumbers) 1f else 0f)
    }
    val geometry = remember { BarGeometry() }

    Column(modifier) {
        ChartReadout(bars[selected.coerceIn(0, bars.lastIndex)].readout)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars) {
                    detectTapGestures { offset -> selected = geometry.indexAt(offset.x, bars.size) }
                }
                .semantics { contentDescription = bars.joinToString(separator = "; ") { it.readout } },
        ) {
            val frame = drawAxes(measurer, axisStyle, ticks, bars.map { it.label }, rightInset = 0f)
            val slot = frame.width / bars.size
            geometry.left = frame.left
            geometry.slot = slot

            val barWidth = min(24.dp.toPx(), slot * 0.6f)
            val radius = 4.dp.toPx()
            bars.forEachIndexed { index, bar ->
                if (bar.value <= 0f) return@forEachIndexed
                val left = frame.left + slot * index + (slot - barWidth) / 2f
                val top = frame.yFor(bar.value)
                val r = min(radius, min(barWidth / 2f, frame.bottom - top))
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = top,
                            right = left + barWidth,
                            bottom = frame.bottom,
                            topLeftCornerRadius = CornerRadius(r),
                            topRightCornerRadius = CornerRadius(r),
                        ),
                    )
                }
                drawPath(path, DojoColors.ChartSeries.copy(alpha = if (index == selected) 1f else 0.5f))
            }

            // Thin the x labels so they never collide; the latest bar always keeps its label.
            // Edge labels are nudged inside the canvas, so also skip any that would then overlap.
            val labels = bars.map { measurer.measure(it.label, axisStyle) }
            val gap = 8.dp.toPx()
            val every = max(1, ceil((labels.maxOf { it.size.width } + gap) / slot).toInt())
            var nextRightEdge = Float.MAX_VALUE
            for (index in bars.lastIndex downTo 0) {
                if ((bars.lastIndex - index) % every != 0) continue
                val label = labels[index]
                val center = frame.left + slot * index + slot / 2f
                val x = (center - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                if (x + label.size.width + gap > nextRightEdge) continue
                drawText(label, topLeft = Offset(x, frame.bottom + 6.dp.toPx()))
                nextRightEdge = x
            }
        }
    }
}

/**
 * Single-series line chart: 2dp line over a 10% wash, a ringed marker on the selected
 * point and a hairline crosshair. Tap anywhere to move the selection.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    minValue: Float,
    maxValue: Float,
    ticks: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    surface: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = DojoColors.TextMuted)
    val geometry = remember { LineGeometry() }

    Column(modifier) {
        ChartReadout(points[selected.coerceIn(0, points.lastIndex)].readout)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset -> selected = geometry.nearest(offset.x, points.size) }
                }
                .semantics { contentDescription = points.joinToString(separator = "; ") { it.readout } },
        ) {
            val inset = 6.dp.toPx()
            val frame = drawAxes(
                measurer = measurer,
                style = axisStyle,
                ticks = ticks,
                xLabels = emptyList(),
                rightInset = 0f,
                minValue = minValue,
                maxValue = maxValue,
            )
            val left = frame.left + inset
            val right = frame.left + frame.width - inset
            geometry.left = left
            geometry.right = right
            fun xFor(index: Int): Float =
                if (points.size == 1) (left + right) / 2f else left + (right - left) * index / (points.size - 1)

            if (points.size > 1) {
                val line = Path()
                points.forEachIndexed { index, point ->
                    val x = xFor(index)
                    val y = frame.yFor(point.value)
                    if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(xFor(points.lastIndex), frame.bottom)
                    lineTo(xFor(0), frame.bottom)
                    close()
                }
                drawPath(area, DojoColors.ChartSeries.copy(alpha = 0.10f))
                drawPath(
                    line,
                    DojoColors.ChartSeries,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            val index = selected.coerceIn(0, points.lastIndex)
            val center = Offset(xFor(index), frame.yFor(points[index].value))
            drawLine(
                DojoColors.Baseline,
                Offset(center.x, frame.top),
                Offset(center.x, frame.bottom),
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(surface, radius = 6.dp.toPx(), center = center)
            drawCircle(DojoColors.ChartSeries, radius = 4.dp.toPx(), center = center)

            // Label only the two ends of the x axis.
            val first = measurer.measure(points.first().label, axisStyle)
            val labelY = frame.bottom + 6.dp.toPx()
            if (points.size == 1) {
                drawText(first, topLeft = Offset(xFor(0) - first.size.width / 2f, labelY))
            } else {
                val last = measurer.measure(points.last().label, axisStyle)
                drawText(first, topLeft = Offset(frame.left, labelY))
                drawText(last, topLeft = Offset(size.width - last.size.width, labelY))
            }
        }
    }
}

@Composable
private fun ChartReadout(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = DojoColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 18.dp)
            .padding(bottom = 8.dp),
    )
}

private class PlotFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val bottom: Float,
    private val minValue: Float,
    private val maxValue: Float,
) {
    fun yFor(value: Float): Float {
        val span = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        return bottom - ((value - minValue) / span).coerceIn(0f, 1f) * (bottom - top)
    }
}

/** Draws gridlines with y tick labels and returns the plotting area. */
private fun DrawScope.drawAxes(
    measurer: TextMeasurer,
    style: TextStyle,
    ticks: List<Float>,
    xLabels: List<String>,
    rightInset: Float,
    minValue: Float = ticks.first(),
    maxValue: Float = ticks.last(),
): PlotFrame {
    val tickLabels: List<TextLayoutResult> = ticks.map { measurer.measure(formatTick(it), style) }
    val labelGap = 8.dp.toPx()
    val axisWidth = tickLabels.maxOf { it.size.width } + labelGap
    val xLabelHeight = (xLabels.map { measurer.measure(it, style).size.height }.maxOrNull()
        ?: measurer.measure("0", style).size.height) + 6.dp.toPx()
    val top = tickLabels.first().size.height / 2f
    val frame = PlotFrame(
        left = axisWidth,
        top = top,
        width = size.width - axisWidth - rightInset,
        bottom = size.height - xLabelHeight,
        minValue = minValue,
        maxValue = maxValue,
    )
    ticks.forEachIndexed { index, tick ->
        val y = frame.yFor(tick)
        drawLine(
            color = if (index == 0) DojoColors.Baseline else DojoColors.Grid,
            start = Offset(frame.left, y),
            end = Offset(frame.left + frame.width, y),
            strokeWidth = 1.dp.toPx(),
        )
        val label = tickLabels[index]
        drawText(label, topLeft = Offset(axisWidth - labelGap - label.size.width, y - label.size.height / 2f))
    }
    return frame
}

private class BarGeometry {
    var left = 0f
    var slot = 1f
    fun indexAt(x: Float, count: Int): Int = ((x - left) / slot).toInt().coerceIn(0, count - 1)
}

private class LineGeometry {
    var left = 0f
    var right = 1f
    fun nearest(x: Float, count: Int): Int {
        if (count <= 1 || right <= left) return 0
        return ((x - left) / (right - left) * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }
}
