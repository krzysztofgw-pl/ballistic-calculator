package pl.ballistics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.ballistics.app.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.ballistics.app.CalcResult
import pl.ballistics.app.data.FocalPlane
import pl.ballistics.app.toNumber
import pl.ballistics.app.reticle.Mark
import pl.ballistics.app.reticle.Reticle
import pl.ballistics.app.reticle.Reticles
import pl.ballistics.app.reticle.Weight
import pl.ballistics.core.TrajectoryPoint
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the bullet lands, in reticle units: hold this point on the target. */
private data class Hold(val point: TrajectoryPoint, val x: Double, val y: Double)

@Composable
fun ReticleScreen(result: CalcResult?, modifier: Modifier = Modifier) {
    if (result !is CalcResult.Ok) {
        TableScreen(result, modifier)
        return
    }
    val weapon = result.weapon
    val own = Reticles.byId(weapon.reticleId)
    var previewId by remember(weapon.id) { mutableStateOf(own.id) }
    val reticle = Reticles.byId(previewId)
    val sfp = weapon.focalPlane == FocalPlane.SFP
    val calMag = weapon.calibratedMagnification.toNumber()?.takeIf { it > 0 } ?: 10.0
    val maxMag = weapon.maxMagnification.toNumber()?.takeIf { it > 1 } ?: 25.0
    var magnification by remember(weapon.id) { mutableFloatStateOf(calMag.toFloat()) }

    val points = result.solution.points.filter { it.rangeM > 0 }
    var selectedRange by remember(weapon.id) { mutableStateOf<Double?>(null) }
    val selected = points.find { it.rangeM == selectedRange } ?: points.getOrNull(points.size / 2)

    // In an SFP scope the reticle's angular size scales with calibrated / current magnification.
    val scale = if (sfp) magnification / calMag else 1.0
    val holds = points.map { p ->
        Hold(p, p.windageRad / reticle.unit.radians * scale, p.dropRad / reticle.unit.radians * scale)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Choice(stringResource(R.string.label_reticle), reticle, Reticles.all, {
            if (it.id == own.id) stringResource(R.string.reticle_own, stringResource(it.name)) else stringResource(it.name)
        }) {
            previewId = it.id
        }
        if (previewId != own.id) {
            Text(
                stringResource(R.string.reticle_preview_note, weapon.scopeName.ifBlank { weapon.name }, stringResource(own.name)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (sfp) {
            Text(
                stringResource(R.string.sfp_note, calMag.fmt(0), magnification.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = magnification,
                onValueChange = { magnification = it },
                valueRange = 1f..maxMag.toFloat(),
            )
        }

        ReticleView(reticle, holds, selected, Modifier.fillMaxWidth().aspectRatio(1f))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            points.forEach { p ->
                FilterChip(
                    selected = p == selected,
                    onClick = { selectedRange = p.rangeM },
                    label = { Text("${p.rangeM.roundToInt()}") },
                )
            }
        }

        selected?.let { p ->
            val h = holds.first { it.point == p }
            val unit = result.angularUnit
            val upDial = -p.dropRad / unit.radians
            val rightDial = -p.windageRad / unit.radians
            Text("${p.rangeM.roundToInt()} m", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.hold_on_reticle,
                    stringResource(if (h.y <= 0) R.string.below_center else R.string.above_center), h.y.absFmt(),
                    stringResource(if (h.x >= 0) R.string.right else R.string.left), h.x.absFmt(),
                    reticle.unit.label,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.or_dial,
                    stringResource(if (upDial >= 0) R.string.up else R.string.down), upDial.absFmt(),
                    stringResource(if (rightDial >= 0) R.string.right else R.string.left), rightDial.absFmt(),
                    unit.label,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (abs(h.x) > reticle.extent || abs(h.y) > reticle.extent) {
                Text(stringResource(R.string.hold_outside_reticle), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun Double.fmt(decimals: Int) = "%.${decimals}f".format(this)
private fun Double.absFmt() = abs(this).fmt(1)

@Composable
private fun ReticleView(reticle: Reticle, holds: List<Hold>, selected: TrajectoryPoint?, modifier: Modifier) {
    val text = rememberTextMeasurer()
    val ink = Color(0xFF111111)
    val hold = Color(0xFFE53935)
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val px = radius / reticle.extent
        fun at(x: Double, y: Double) = Offset(c.x + (x * px).toFloat(), c.y - (y * px).toFloat())

        val lens = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, radius)) }
        clipPath(lens) {
            drawRect(Brush.radialGradient(listOf(Color(0xFFF4F6F2), Color(0xFFC9D2C9)), center = c, radius = radius))
            reticle.marks.forEach { m ->
                when (m) {
                    is Mark.Line -> drawLine(
                        ink, at(m.x1, m.y1), at(m.x2, m.y2),
                        strokeWidth = when (m.weight) {
                            Weight.FINE -> 1.2.dp.toPx()
                            Weight.MEDIUM -> 2.dp.toPx()
                            Weight.HEAVY -> 5.dp.toPx()
                        },
                        cap = StrokeCap.Butt,
                    )
                    is Mark.Dot -> drawCircle(ink, maxOf((m.r * px).toFloat(), 1.5.dp.toPx()), at(m.x, m.y))
                    is Mark.Label -> label(text, m.text, at(m.x, m.y), ink)
                }
            }
            holds.forEach { h ->
                val isSelected = h.point == selected
                val p = at(h.x, h.y)
                drawCircle(hold, if (isSelected) 5.dp.toPx() else 3.dp.toPx(), p)
                if (isSelected) drawCircle(hold, 9.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                if (isSelected || h.point.rangeM.roundToInt() % 100 == 0) {
                    label(text, "${h.point.rangeM.roundToInt()}", p + Offset(14.dp.toPx(), 0f), hold)
                }
            }
        }
        drawCircle(Color(0xFF263238), radius, c, style = Stroke(4.dp.toPx()))
    }
}

private fun DrawScope.label(measurer: TextMeasurer, s: String, center: Offset, color: Color) {
    val layout = measurer.measure(s, TextStyle(fontSize = 10.sp, color = color))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}
