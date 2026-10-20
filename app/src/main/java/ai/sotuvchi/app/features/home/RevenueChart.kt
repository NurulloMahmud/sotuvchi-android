package ai.sotuvchi.app.features.home

import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.design.Theme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Axis maths for the revenue chart, kept apart from drawing so it can be tested. */
object ChartScale {
    /** The step between y-axis lines and the top value, with `intervals` lines above zero. */
    fun niceScale(maxValue: Int, intervals: Int = 4): Pair<Int, Int> {
        if (maxValue <= 0) return 1 to intervals
        val rawStep = maxValue.toDouble() / intervals
        val magnitude = 10.0.pow(floor(log10(rawStep)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.firstOrNull { it >= rawStep } ?: (10 * magnitude)
        val intStep = max(1, ceil(step).toInt())
        return intStep to intStep * intervals
    }

    /** Evenly spread indices for x-axis labels. */
    fun labelIndices(count: Int, desired: Int = 4): List<Int> {
        if (count <= 0) return emptyList()
        if (count <= desired) return (0 until count).toList()
        return (0 until desired).map { Math.round(it * (count - 1).toFloat() / (desired - 1)) }.distinct()
    }
}

/** Daily revenue as bars, drawn on a Canvas: charcoal bars, hairline grid, compact money labels. */
@Composable
fun RevenueChart(days: List<Pair<LocalDate, Int>>, modifier: Modifier = Modifier) {
    val colors = Theme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.inkSubtle)
    val maxRevenue = days.maxOfOrNull { it.second } ?: 0
    val (step, top) = ChartScale.niceScale(maxRevenue)
    val xLabels = ChartScale.labelIndices(days.size)

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val bottomGutter = 22.dp.toPx()
        val yLabels = (0..4).map { Money.compact(step * it) }
        val leftGutter = yLabels.maxOf { measurer.measure(it, labelStyle).size.width } + 8.dp.toPx()
        val plotWidth = size.width - leftGutter
        val plotHeight = size.height - bottomGutter

        // Grid and y labels
        for (line in 0..4) {
            val y = plotHeight - plotHeight * line / 4f
            drawLine(colors.hairlineSoft, Offset(leftGutter, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(yLabels[line], labelStyle)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }

        if (days.isEmpty()) return@Canvas
        val slot = plotWidth / days.size
        val barWidth = slot * 0.7f
        days.forEachIndexed { index, (_, revenue) ->
            val height = if (top > 0) plotHeight * revenue / top.toFloat() else 0f
            if (height > 0f) {
                drawRoundedBar(
                    color = colors.ink,
                    left = leftGutter + slot * index + (slot - barWidth) / 2f,
                    top = plotHeight - height,
                    width = barWidth,
                    height = height,
                    radius = 3.dp.toPx(),
                )
            }
        }
        for (index in xLabels) {
            val layout = measurer.measure(DateText.shortDay(days[index].first), labelStyle)
            val center = leftGutter + slot * index + slot / 2f
            val x = (center - layout.size.width / 2f).coerceIn(leftGutter, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, plotHeight + 6.dp.toPx()))
        }
    }
}

private fun DrawScope.drawRoundedBar(color: androidx.compose.ui.graphics.Color, left: Float, top: Float, width: Float, height: Float, radius: Float) {
    drawRoundRect(color, topLeft = Offset(left, top), size = Size(width, height), cornerRadius = CornerRadius(radius, radius))
}
