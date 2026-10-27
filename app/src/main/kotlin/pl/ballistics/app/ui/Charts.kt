package pl.ballistics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class ChartSeries(val label: String, val color: Color, val points: List<Pair<Double, Double>>)

/** Distinct line colours that read on both light and dark backgrounds. */
val chartColors = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00897B),
)

/** A plain line chart with a zero line, an optional vertical marker and a legend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    title: String,
    series: List<ChartSeries>,
    xTicks: List<Double>,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    markerX: Double? = null,
) {
    val text = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val all = series.flatMap { it.points }
    if (all.isEmpty()) return
    val xMin = minOf(all.minOf { it.first }, xTicks.minOrNull() ?: Double.MAX_VALUE)
    val xMax = maxOf(all.maxOf { it.first }, xTicks.maxOrNull() ?: -Double.MAX_VALUE).let { if (it > xMin) it else xMin + 1 }
    val yTicks = niceTicks(minOf(0.0, all.minOf { it.second }), maxOf(0.0, all.maxOf { it.second }))
    val yMin = yTicks.first()
    val yMax = yTicks.last()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            val left = yTicks.maxOf { text.measure(yLabel(it), labelStyle).size.width } + 6.dp.toPx()
            val bottom = text.measure("0", labelStyle).size.height + 4.dp.toPx()
            val top = 6.dp.toPx()
            val right = 12.dp.toPx()
            val w = size.width - left - right
            val h = size.height - top - bottom
            fun x(v: Double) = left + ((v - xMin) / (xMax - xMin)).toFloat() * w
            fun y(v: Double) = top + ((yMax - v) / (yMax - yMin)).toFloat() * h

            yTicks.forEach { t ->
                drawLine(if (t == 0.0) ink else grid, Offset(left, y(t)), Offset(left + w, y(t)), strokeWidth = if (t == 0.0) 1.5f else 1f)
                val l = text.measure(yLabel(t), labelStyle)
                drawText(l, topLeft = Offset(left - l.size.width - 4.dp.toPx(), y(t) - l.size.height / 2f))
            }
            xTicks.forEach { t ->
                drawLine(grid, Offset(x(t), top), Offset(x(t), top + h), strokeWidth = 1f)
                val l = text.measure(xLabel(t), labelStyle)
                drawText(l, topLeft = Offset(x(t) - l.size.width / 2f, top + h + 2.dp.toPx()))
            }
            markerX?.takeIf { it in xMin..xMax }?.let { m ->
                drawLine(
                    ink, Offset(x(m), top), Offset(x(m), top + h),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
            series.forEach { s ->
                val path = Path()
                s.points.forEachIndexed { i, (px, py) ->
                    if (i == 0) path.moveTo(x(px), y(py)) else path.lineTo(x(px), y(py))
                }
                drawPath(path, s.color, style = Stroke(2.dp.toPx()))
            }
        }
        if (series.size > 1 || series.firstOrNull()?.label?.isNotEmpty() == true) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Canvas(Modifier.size(10.dp)) { drawCircle(s.color) }
                        Text(s.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Round tick values covering [min]..[max], about five of them. */
internal fun niceTicks(min: Double, max: Double): List<Double> {
    val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
    val raw = span / 5
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
    val start = floor(min / step) * step
    val end = maxOf(ceil(max / step) * step, start + step)
    val count = ((end - start) / step).roundToInt()
    return (0..count).map { start + it * step }.map { if (abs(it) < step * 1e-6) 0.0 else it }
}
