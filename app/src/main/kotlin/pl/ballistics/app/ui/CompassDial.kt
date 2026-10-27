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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How a marker is drawn: wind blows in from the rim, the shot goes out from the centre. */
enum class DialArrow { INWARD, OUTWARD }

/**
 * One direction on a dial. [active] is the one a tap or drag sets; the others are drawn thinner.
 * [label] is written at the tip, e.g. "N" for a north marker on a dial that is not north-up.
 */
data class DialMarker(val deg: Double, val arrow: DialArrow, val color: Color, val active: Boolean, val label: String? = null)

/**
 * A 360° dial, 0° at the top and clockwise, with ticks every 10° and labels every 30°.
 * Tap or drag anywhere on it to set the active marker, rounded to [stepDeg].
 *
 * @param outerLabel label for each multiple of 30°; null for a small dial without labels.
 * @param innerLabel optional second ring of labels (clock hours, cardinal points).
 */
@Composable
fun CompassDial(
    markers: List<DialMarker>,
    onChange: (Double) -> Unit,
    centerText: String,
    outerLabel: ((Int) -> String)?,
    modifier: Modifier = Modifier,
    innerLabel: (Int) -> String? = { null },
    stepDeg: Double = 1.0,
    maxSize: Dp = 360.dp,
) {
    val current by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val outerStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    val innerStyle = MaterialTheme.typography.labelLarge.copy(color = colors.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val centerStyle = (if (outerLabel == null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium)
        .copy(color = colors.onSurface, textAlign = TextAlign.Center)
    val markerStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

    fun angleAt(p: Offset, size: IntSize): Double {
        val deg = Math.toDegrees(atan2((p.x - size.width / 2f).toDouble(), (size.height / 2f - p.y).toDouble()))
        return ((deg / stepDeg).roundToInt() * stepDeg).mod(360.0)
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .widthIn(max = maxSize)
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

            for (d in 0 until 360 step if (outerLabel == null) 30 else 10) {
                val major = d % 30 == 0
                val dir = unit(d.toDouble())
                val len = (if (major) 14.dp else 7.dp).toPx() * (if (outerLabel == null) 0.6f else 1f)
                drawLine(colors.onSurfaceVariant, c + dir * r, c + dir * (r - len), (if (major) 2.dp else 1.dp).toPx())
                if (major && outerLabel != null) {
                    label(measurer.measure(outerLabel(d), outerStyle), c + dir * (r - 30.dp.toPx()))
                    innerLabel(d)?.let { label(measurer.measure(it, innerStyle), c + dir * (r - 58.dp.toPx())) }
                }
            }

            // The active marker goes on top.
            markers.sortedBy { it.active }.forEach { m ->
                val dir = unit(m.deg)
                val rim = c + dir * (r - 6.dp.toPx())
                val color = if (m.active) m.color else m.color.copy(alpha = 0.55f)
                val width = if (m.active) 1f else 0.6f
                when (m.arrow) {
                    DialArrow.INWARD -> drawArrow(rim, c + dir * (r * 0.3f), color, width)
                    DialArrow.OUTWARD -> drawArrow(c, rim - dir * (if (m.label != null) 14.dp.toPx() else 0f), color, width)
                }
                m.label?.let { label(measurer.measure(it, markerStyle.copy(color = color)), c + dir * (r - 8.dp.toPx())) }
            }
            drawCircle(colors.surface, r * (if (outerLabel == null) 0.42f else 0.26f), c)
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

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, scale: Float = 1f) {
    val width = 4.dp.toPx() * scale
    val head = 16.dp.toPx() * scale
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
