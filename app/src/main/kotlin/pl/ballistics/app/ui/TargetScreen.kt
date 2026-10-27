package pl.ballistics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.ballistics.app.CalcResult
import pl.ballistics.app.Lead
import pl.ballistics.app.TargetAnalysis
import pl.ballistics.app.TargetInputs
import pl.ballistics.app.lead
import pl.ballistics.app.data.AngularUnit
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.data.FocalPlane
import pl.ballistics.app.toNumber
import pl.ballistics.app.reticle.Mark
import pl.ballistics.app.reticle.Reticle
import pl.ballistics.app.reticle.Reticles
import pl.ballistics.app.reticle.Weight
import pl.ballistics.core.TrajectoryPoint
import pl.ballistics.core.Units
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.roundToInt

/** The shot at [distance]; [point] is null when the bullet does not get there. */
private data class Solved(val distance: Double, val point: TrajectoryPoint?, val lead: Lead?, val analysis: TargetAnalysis?)

/** Where the bullet lands, in reticle units: hold this point on the target. */
private data class Hold(val point: TrajectoryPoint, val x: Double, val y: Double)

@Composable
fun TargetScreen(
    result: CalcResult?,
    conditions: Conditions,
    update: ((Conditions) -> Conditions) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    val solution = result.solution
    val points = solution.points.filter { it.rangeM > 0 }
    val maxDistance = points.lastOrNull()?.rangeM ?: 1000.0
    // While the slider is dragged the distance lives here and is saved when the finger lifts.
    var dragged by remember { mutableStateOf<Float?>(null) }
    var typed by remember { mutableStateOf<String?>(null) }
    val distance = dragged?.roundToInt()?.toDouble()
        ?: conditions.targetRangeM.toNumber()?.takeIf { it > 0 }
        ?: points.getOrNull(points.size / 2)?.rangeM ?: 0.0
    fun setDistance(m: Double) = update { it.copy(targetRangeM = m.coerceIn(1.0, maxDistance).roundToInt().toString()) }
    val targetSpeed = conditions.targetSpeedMps.toNumber()?.takeIf { it > 0 && conditions.targetMoving }
    val targetDirection = conditions.targetDirectionDeg.toNumber() ?: 90.0

    val inputs = TargetInputs(
        windMinMps = conditions.windMinMps.toNumber()?.takeIf { it >= 0 },
        windMaxMps = conditions.windMaxMps.toNumber()?.takeIf { it >= 0 },
        widthM = (conditions.targetWidthCm.toNumber()?.takeIf { it > 0 } ?: 50.0) / 100,
        heightM = (conditions.targetHeightCm.toNumber()?.takeIf { it > 0 } ?: 50.0) / 100,
        velocitySdMps = result.ammo.velocitySd.toNumber()?.takeIf { it >= 0 } ?: TargetInputs.DEFAULT_VELOCITY_SD,
        windSdMps = conditions.windUncertaintyMps.toNumber()?.takeIf { it >= 0 } ?: 1.0,
        rangeSdM = conditions.rangeUncertaintyM.toNumber()?.takeIf { it >= 0 } ?: 5.0,
        groupRad = (weapon.groupMoa.toNumber()?.takeIf { it > 0 } ?: TargetInputs.DEFAULT_GROUP_MOA) * Units.MOA,
    )
    // The distance is solved on its own first; the sweeps for the charts and tools follow once it settles,
    // and until then the previous ones for the same distance stay on screen.
    val solved by produceState<Solved?>(null, result, distance, targetSpeed, targetDirection, inputs) {
        val (point, lead) = withContext(Dispatchers.Default) {
            runCatching {
                val p = result.setup.pointAt(distance, solution.zeroElevationRad)
                p to targetSpeed?.let { result.setup.lead(distance, solution.zeroElevationRad, it, targetDirection) }
            }.getOrDefault(null to null)
        }
        value = Solved(distance, point, lead, value?.takeIf { it.distance == distance }?.analysis)
        if (point != null) {
            delay(300)
            value = Solved(distance, point, lead, withContext(Dispatchers.Default) {
                runCatching { TargetAnalysis.of(result.setup, solution.zeroElevationRad, distance, inputs) }.getOrNull()
            })
        }
    }
    // While dragging, the last solved distance stays on screen until the next one is ready.
    val current = solved?.takeIf { it.distance == distance || dragged != null }
    val analysis = current?.analysis?.takeIf { current.distance == distance }
    val outOfReach = current != null && current.point == null
    val selected = points.find { abs(it.rangeM - distance) < 1e-6 } ?: current?.point

    // In an SFP scope the reticle's angular size scales with calibrated / current magnification.
    // In an FFP scope reticle and target scale together, so only the field of view changes.
    val scale = if (sfp) magnification / calMag else 1.0
    val field = if (sfp) reticle.extent else reticle.extent * maxMag / magnification
    val holds = (points + listOfNotNull(selected?.takeIf { it !in points })).map { p ->
        Hold(p, p.windageRad / reticle.unit.radians * scale, p.dropRad / reticle.unit.radians * scale)
    }
    // A moving target sits on the mark that is the lead away from the impact, against its motion.
    val leadHold = current?.lead?.let { l ->
        Hold(l.point, (l.point.windageRad - l.leadRad) / reticle.unit.radians * scale, l.point.dropRad / reticle.unit.radians * scale)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The typed distance and the slider share one row; the arrows step a metre either way.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Focusing the field empties it for a fresh entry; the last distance stays as the placeholder.
            NumberField(
                stringResource(R.string.label_target_distance),
                dragged?.roundToInt()?.toString() ?: typed ?: distance.roundToInt().toString(),
                "m",
                Modifier.width(116.dp).onFocusChanged { f ->
                    if (f.isFocused && typed == null) typed = "" else if (!f.isFocused) typed = null
                },
                optional = true,
                placeholder = distance.roundToInt().toString(),
            ) { v ->
                typed = v
                if ((v.toNumber() ?: 0.0) > 0) update { it.copy(targetRangeM = v) }
            }
            IconButton(onClick = { setDistance(distance - 1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.action_closer))
            }
            Slider(
                value = distance.toFloat().coerceIn(1f, maxDistance.toFloat()),
                onValueChange = { dragged = it },
                onValueChangeFinished = { dragged?.let { setDistance(it.toDouble()) }; dragged = null },
                valueRange = 1f..maxDistance.toFloat(),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { setDistance(distance + 1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.action_farther))
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable { update { it.copy(targetMoving = !it.targetMoving) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(conditions.targetMoving, onCheckedChange = { v -> update { it.copy(targetMoving = v) } })
            Text(stringResource(R.string.label_moving_target))
        }
        if (conditions.targetMoving) {
            FieldRow { m ->
                NumberField(stringResource(R.string.label_target_speed), conditions.targetSpeedMps, "m/s", m) { v ->
                    update { it.copy(targetSpeedMps = v) }
                }
                NumberField(stringResource(R.string.label_target_direction), conditions.targetDirectionDeg, "°", m) { v ->
                    update { it.copy(targetDirectionDeg = v) }
                }
            }
        }

        selected?.let { p ->
            val h = holds.first { it.point == p }
            val unit = result.angularUnit
            val upDial = -p.dropRad / unit.radians
            val rightDial = -p.windageRad / unit.radians
            Text(
                stringResource(
                    R.string.hold_on_reticle,
                    stringResource(if (h.y <= 0) R.string.below_center else R.string.above_center), h.y.absFmt(),
                    stringResource(if (h.x >= 0) R.string.right else R.string.left), h.x.absFmt(),
                    reticle.unit.label,
                ),
                style = MaterialTheme.typography.titleSmall,
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
        leadHold?.let { h ->
            val l = current?.lead ?: return@let
            Text(
                stringResource(
                    R.string.moving_hold,
                    stringResource(if (h.y <= 0) R.string.below_center else R.string.above_center), h.y.absFmt(),
                    stringResource(if (h.x >= 0) R.string.right else R.string.left), h.x.absFmt(),
                    reticle.unit.label,
                    stringResource(if (l.leadM >= 0) R.string.right else R.string.left), abs(l.leadM).fmt(2),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF1E88E5),
            )
        }
        if (selected == null) {
            if (outOfReach) {
                Text(stringResource(R.string.target_out_of_reach, distance.roundToInt()), color = MaterialTheme.colorScheme.error)
            } else {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }

        ReticleView(
            reticle, holds, selected, leadHold, field,
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

        TargetTools(result, conditions, update, analysis, inputs, reticle.unit) { setDistance(it) }
        if (analysis != null) {
            Collapsible(stringResource(R.string.section_charts)) { TargetCharts(result, analysis, distance) }
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
private fun ReticleView(
    reticle: Reticle,
    holds: List<Hold>,
    selected: TrajectoryPoint?,
    lead: Hold?,
    field: Double,
    modifier: Modifier,
) {
    val text = rememberTextMeasurer()
    val ink = Color(0xFF111111)
    val hold = Color(0xFFE53935)
    val moving = Color(0xFF1E88E5)
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
            lead?.let { h ->
                val p = at(h.x, h.y)
                drawCircle(moving, 5.dp.toPx(), p)
                drawCircle(moving, 9.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
            }
        }
        drawCircle(Color(0xFF263238), radius, c, style = Stroke(4.dp.toPx()))
    }
}

private fun DrawScope.label(measurer: TextMeasurer, s: String, center: Offset, color: Color) {
    val layout = measurer.measure(s, TextStyle(fontSize = 10.sp, color = color))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

private fun clock(deg: Double): String {
    val hours = (deg / 30).mod(12.0).let { if (it == 0.0) 12.0 else it }
    return if (hours % 1.0 == 0.0) hours.roundToInt().toString() else "%d:%02d".format(hours.toInt(), ((hours % 1.0) * 60).roundToInt())
}

/** Spin drift by distance and the windage correction by wind speed and by wind direction at the chosen distance. */
@Composable
private fun TargetCharts(result: CalcResult.Ok, a: TargetAnalysis, distance: Double) {
    val unit = result.angularUnit
    val digits = if (unit == AngularUnit.MOA) 1 else 2
    fun correction(v: Double): String = when {
        abs(v) < 0.5 * 10.0.pow(-digits) -> "0"
        v > 0 -> "R" + abs(v).fmt(digits)
        else -> "L" + abs(v).fmt(digits)
    }
    val windSpeed = result.setup.conditions.winds.firstOrNull()?.speedMps ?: 0.0
    val windFrom = result.setup.conditions.winds.firstOrNull()?.fromRad?.let { Math.toDegrees(it).mod(360.0) }

    LineChart(
        title = stringResource(R.string.chart_wind_speed, unit.label),
        series = TargetAnalysis.DIRECTIONS.mapIndexed { i, d ->
            ChartSeries(
                stringResource(R.string.wind_from_clock, clock(d)),
                chartColors[i % chartColors.size],
                a.bySpeed[d].orEmpty().map { (s, c) -> s to c / unit.radians },
            )
        },
        xTicks = TargetAnalysis.SPEEDS.filter { it.roundToInt() % 2 == 0 },
        xLabel = { it.roundToInt().toString() },
        yLabel = ::correction,
        markerX = windSpeed.takeIf { it > 0 },
    )
    LineChart(
        title = stringResource(R.string.chart_wind_direction, unit.label),
        series = TargetAnalysis.DIRECTION_SPEEDS.mapIndexed { i, s ->
            ChartSeries(
                stringResource(R.string.wind_speed_series, s.roundToInt()),
                chartColors[i % chartColors.size],
                a.byDirection[s].orEmpty().map { (d, c) -> d to c / unit.radians },
            )
        },
        xTicks = (0..12).map { it * 30.0 },
        xLabel = { clock(it) },
        yLabel = ::correction,
        markerX = windFrom,
    )

    val drift = result.solution.points.filter { it.rangeM > 0 }
    if (drift.any { it.spinDriftM != 0.0 }) {
        // Spin drift pushes the bullet one way; the correction is the other way.
        LineChart(
            title = stringResource(R.string.chart_spin_drift, unit.label),
            series = listOf(ChartSeries("", chartColors[0], drift.map { it.rangeM to -atan2(it.spinDriftM, it.rangeM) / unit.radians })),
            xTicks = niceTicks(0.0, drift.last().rangeM).filter { it <= drift.last().rangeM },
            xLabel = { it.roundToInt().toString() },
            yLabel = ::correction,
            markerX = distance,
        )
        val p = a.point
        Text(
            stringResource(
                R.string.spin_drift_at, p.rangeM.roundToInt(), (p.spinDriftM * 100).fmt(1),
                correction(-atan2(p.spinDriftM, p.rangeM) / unit.radians), unit.label,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Text(
            stringResource(if (result.setup.options.spinDrift) R.string.spin_drift_needs_data else R.string.spin_drift_off),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
