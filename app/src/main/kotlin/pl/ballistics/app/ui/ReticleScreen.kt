package pl.ballistics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.ballistics.app.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
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
import pl.ballistics.app.data.AngularUnit
import pl.ballistics.app.data.FocalPlane
import pl.ballistics.app.toNumber
import pl.ballistics.app.reticle.Mark
import pl.ballistics.app.reticle.Reticle
import pl.ballistics.app.reticle.Reticles
import pl.ballistics.app.reticle.Weight
import pl.ballistics.core.TrajectoryPoint
import kotlin.math.abs
import kotlin.math.sign
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
    val maxMag = weapon.maxMagnification.toNumber()?.takeIf { it > 1 } ?: 25.0
    val minMag = weapon.minMagnification.toNumber()?.takeIf { it > 0 && it < maxMag } ?: 1.0
    val calMag = weapon.calibratedMagnification.toNumber()?.takeIf { it > 0 } ?: maxMag
    // Start where the reticle reads true and looks as designed: SFP at its calibrated power, FFP at full power.
    val startMag = if (sfp) calMag.coerceIn(minMag, maxMag) else maxMag
    var magnification by remember(weapon.id, sfp, startMag, minMag) { mutableFloatStateOf(startMag.toFloat()) }
    var unlocked by remember(weapon.id) { mutableStateOf(false) }
    // Unlocked, the zoom goes past the scope's range: down to 1× and up to twice its top power.
    val range = if (unlocked) minOf(1.0, minMag).toFloat()..(maxMag * 2).toFloat() else minMag.toFloat()..maxMag.toFloat()
    val currentRange by rememberUpdatedState(range)

    val points = result.solution.points.filter { it.rangeM > 0 }
    var selectedRange by remember(weapon.id) { mutableStateOf<Double?>(null) }
    val selected = points.find { it.rangeM == selectedRange } ?: points.getOrNull(points.size / 2)

    // In an SFP scope the reticle's angular size scales with calibrated / current magnification.
    // In an FFP scope reticle and target scale together, so only the field of view changes.
    val scale = if (sfp) magnification / calMag else 1.0
    val field = if (sfp) reticle.extent else reticle.extent * maxMag / magnification
    val holds = points.map { p ->
        Hold(p, p.windageRad / reticle.unit.radians * scale, p.dropRad / reticle.unit.radians * scale)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReticleChoice(stringResource(R.string.label_reticle), reticle, reticle.unit, {
            stringResource(it.family)
        }) {
            previewId = it.id
        }
        if (previewId != own.id) {
            Text(
                stringResource(R.string.reticle_preview_note, weapon.scopeName.ifBlank { weapon.name }, stringResource(own.name)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (sfp) Text(stringResource(R.string.sfp_note, calMag.magFmt()), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.magnification_value, magnification.toDouble().magFmt()),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            FilterChip(
                selected = unlocked,
                onClick = {
                    unlocked = !unlocked
                    if (!unlocked) magnification = magnification.coerceIn(minMag.toFloat(), maxMag.toFloat())
                },
                label = { Text(stringResource(R.string.action_unlock_magnification)) },
            )
        }
        Slider(
            value = magnification.coerceIn(range),
            onValueChange = { magnification = it },
            valueRange = range,
        )
        if (magnification < minMag - 1e-3 || magnification > maxMag + 1e-3) {
            Text(
                stringResource(R.string.magnification_outside_range, minMag.magFmt(), maxMag.magFmt()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        ReticleView(
            reticle, holds, selected, field,
            Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(weapon.id) {
                // Two-finger pinch only, so a one-finger drag still scrolls the screen.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                magnification = (magnification * zoom).coerceIn(currentRange)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        )

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

/**
 * Picks a reticle design, then its MRAD or MOA version when it comes in both.
 * A newly picked design starts in [preferredUnit] when it has that version.
 */
@Composable
internal fun ReticleChoice(
    label: String,
    selected: Reticle,
    preferredUnit: AngularUnit,
    name: @Composable (Reticle) -> String,
    onSelect: (Reticle) -> Unit,
) {
    Choice(label, selected, Reticles.families(preferredUnit), name) { onSelect(Reticles.variant(it, preferredUnit)) }
    val variants = Reticles.variants(selected)
    if (variants.size > 1) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.label_reticle_units), Modifier.weight(1f))
            variants.forEach { v ->
                FilterChip(selected = v.id == selected.id, onClick = { onSelect(v) }, label = { Text(v.unit.label) })
            }
        }
    }
}

private fun Double.fmt(decimals: Int) = "%.${decimals}f".format(this)
private fun Double.magFmt() = if (abs(this - roundToInt()) < 0.05) roundToInt().toString() else fmt(1)
private fun Double.absFmt() = abs(this).fmt(1)

@Composable
private fun ReticleView(reticle: Reticle, holds: List<Hold>, selected: TrajectoryPoint?, field: Double, modifier: Modifier) {
    val text = rememberTextMeasurer()
    val ink = Color(0xFF111111)
    val hold = Color(0xFFE53935)
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val px = radius / field
        fun at(x: Double, y: Double) = Offset(c.x + (x * px).toFloat(), c.y - (y * px).toFloat())
        // Crosshair arms drawn to the reticle's outer part run on to the edge of the field of view, as in the scope.
        val edge = maxOf(field, reticle.extent) * 1.5
        fun reach(v: Double) = if (abs(v) >= reticle.extent * 0.8) sign(v) * maxOf(abs(v), edge) else v
        fun arm(m: Mark.Line): Mark.Line = when {
            m.x1 == 0.0 && m.x2 == 0.0 -> m.copy(y1 = reach(m.y1), y2 = reach(m.y2))
            m.y1 == 0.0 && m.y2 == 0.0 -> m.copy(x1 = reach(m.x1), x2 = reach(m.x2))
            else -> m
        }

        val lens = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, radius)) }
        clipPath(lens) {
            drawRect(Brush.radialGradient(listOf(Color(0xFFF4F6F2), Color(0xFFC9D2C9)), center = c, radius = radius))
            reticle.marks.forEach { m ->
                when (m) {
                    is Mark.Line -> arm(m).let { l -> drawLine(
                        ink, at(l.x1, l.y1), at(l.x2, l.y2),
                        strokeWidth = when (m.weight) {
                            Weight.FINE -> 1.2.dp.toPx()
                            Weight.MEDIUM -> 2.dp.toPx()
                            Weight.HEAVY -> 5.dp.toPx()
                        },
                        cap = StrokeCap.Butt,
                    ) }
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
