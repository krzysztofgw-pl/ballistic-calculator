package pl.ballistics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How the value is drawn: wind blows in from the rim, the shot goes out from the centre. */
enum class DialArrow { INWARD, OUTWARD }

/**
 * A 360° dial, 0° at the top and clockwise, with ticks every 10° and labels every 30°.
 * Tap or drag anywhere on it to set the value, rounded to [stepDeg].
 *
 * @param outerLabel label for each multiple of 30°.
 * @param innerLabel optional second ring of labels (clock hours, cardinal points).
 * @param referenceDeg optional secondary marker, e.g. the line of fire on a north-up wind dial.
 */
@Composable
fun CompassDial(
    valueDeg: Double?,
    onChange: (Double) -> Unit,
    arrow: DialArrow,
    centerText: String,
    outerLabel: (Int) -> String,
    innerLabel: (Int) -> String? = { null },
    referenceDeg: Double? = null,
    stepDeg: Double = 1.0,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val outerStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    val innerStyle = MaterialTheme.typography.labelLarge.copy(color = colors.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val centerStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center)

    fun angleAt(p: Offset, size: IntSize): Double {
        val deg = Math.toDegrees(atan2((p.x - size.width / 2f).toDouble(), (size.height / 2f - p.y).toDouble()))
        return ((deg / stepDeg).roundToInt() * stepDeg).mod(360.0)
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(stepDeg) { detectTapGestures { current(angleAt(it, size)) } }
                .pointerInput(stepDeg) {
                    detectDragGestures(onDragStart = { current(angleAt(it, size)) }) { change, _ ->
                        change.consume()
                        current(angleAt(change.position, size))
                    }
                },
        ) {
            val c = center
            val r = size.minDimension / 2 - 4.dp.toPx()
            drawCircle(colors.surfaceVariant, r, c)
            drawCircle(colors.outline, r, c, style = Stroke(2.dp.toPx()))

            for (d in 0 until 360 step 10) {
                val major = d % 30 == 0
                val dir = unit(d.toDouble())
                val len = (if (major) 14.dp else 7.dp).toPx()
                drawLine(colors.onSurfaceVariant, c + dir * r, c + dir * (r - len), (if (major) 2.dp else 1.dp).toPx())
                if (major) {
                    label(measurer.measure(outerLabel(d), outerStyle), c + dir * (r - 30.dp.toPx()))
                    innerLabel(d)?.let { label(measurer.measure(it, innerStyle), c + dir * (r - 58.dp.toPx())) }
                }
            }

            referenceDeg?.let {
                val dir = unit(it)
                drawLine(colors.tertiary, c, c + dir * (r - 4.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
            }
            valueDeg?.let {
                val dir = unit(it)
                val rim = c + dir * (r - 6.dp.toPx())
                when (arrow) {
                    DialArrow.INWARD -> drawArrow(rim, c + dir * (r * 0.3f), colors.primary)
                    DialArrow.OUTWARD -> drawArrow(c, rim, colors.primary)
                }
            }
            drawCircle(colors.surface, r * 0.24f, c)
            label(measurer.measure(centerText, centerStyle), c)
        }
    }
}

private fun unit(deg: Double): Offset {
    val a = Math.toRadians(deg)
    return Offset(sin(a).toFloat(), -cos(a).toFloat())
}

private fun DrawScope.label(layout: TextLayoutResult, at: Offset) =
    drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color) {
    val width = 4.dp.toPx()
    val head = 16.dp.toPx()
    val dir = (to - from) / (to - from).getDistance()
    val normal = Offset(-dir.y, dir.x)
    val base = to - dir * head
    drawLine(color, from, base, width, StrokeCap.Round)
    val path = Path().apply {
        moveTo(to.x, to.y)
        (base + normal * (head * 0.5f)).let { lineTo(it.x, it.y) }
        (base - normal * (head * 0.5f)).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, color)
}

/** Compass point for a bearing: N, NNE, NE … */
fun compassPoint(deg: Double): String {
    val points = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return points[((deg.mod(360.0) / 22.5).roundToInt()) % 16]
}
