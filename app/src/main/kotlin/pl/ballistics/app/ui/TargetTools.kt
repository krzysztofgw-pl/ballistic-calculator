package pl.ballistics.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.ballistics.app.CalcResult
import pl.ballistics.app.R
import pl.ballistics.app.TargetAnalysis
import pl.ballistics.app.TargetInputs
import pl.ballistics.app.data.AngularUnit
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.rangeFromSize
import pl.ballistics.app.toNumber
import kotlin.math.abs
import kotlin.math.roundToInt

/** A card whose content opens and closes with its title; it remembers its state across screens. */
@Composable
fun Collapsible(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

private fun Double.f(decimals: Int) = "%.${decimals}f".format(this)
private fun cm(m: Double) = (m * 100).roundToInt().toString()

/** Wind bracket, range error, temperature, hit probability and ranging with the reticle, at the chosen distance. */
@Composable
fun TargetTools(
    result: CalcResult.Ok,
    conditions: Conditions,
    update: ((Conditions) -> Conditions) -> Unit,
    analysis: TargetAnalysis?,
    inputs: TargetInputs,
    reticleUnit: AngularUnit,
    onUseDistance: (Double) -> Unit,
) {
    val unit = result.angularUnit
    val digits = if (unit == AngularUnit.MOA) 1 else 2
    val right = stringResource(R.string.right_short)
    val left = stringResource(R.string.left_short)
    fun side(rad: Double): String {
        val v = rad / unit.radians
        return when {
            abs(v) < 0.5 * Math.pow(10.0, -digits.toDouble()) -> "0"
            v > 0 -> right + abs(v).f(digits)
            else -> left + abs(v).f(digits)
        }
    }

    Collapsible(stringResource(R.string.section_wind_bracket)) {
        FieldRow { m ->
            NumberField(stringResource(R.string.label_wind_min), conditions.windMinMps, "m/s", m, optional = true) { v ->
                update { it.copy(windMinMps = v) }
            }
            NumberField(stringResource(R.string.label_wind_max), conditions.windMaxMps, "m/s", m, optional = true) { v ->
                update { it.copy(windMaxMps = v) }
            }
        }
        analysis?.bracket?.let { b ->
            val spread = abs(b.maxRad - b.minRad)
            Text(
                stringResource(
                    R.string.wind_bracket_result, b.minMps.f(1), b.maxMps.f(1), side(b.minRad), side(b.maxRad), unit.label,
                    (spread / unit.radians).f(digits), cm(spread * analysis.point.rangeM),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Collapsible(stringResource(R.string.section_range_error)) {
        analysis?.tolerance?.let { t ->
            t.misses.forEach { m ->
                Text(
                    stringResource(
                        R.string.range_error_row, (if (m.errorM > 0) "+" else "−") + abs(m.errorM).roundToInt(), cm(abs(m.missM)),
                        stringResource(if (m.missM >= 0) R.string.high else R.string.low),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(
                    R.string.range_error_window,
                    t.shortM?.let { "−" + abs(it).roundToInt() } ?: "−50+",
                    t.longM?.let { "+" + it.roundToInt() } ?: "+50+",
                    cm(inputs.heightM),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }

    Collapsible(stringResource(R.string.section_temperature)) {
        analysis?.temperatures?.forEach { row ->
            Text(
                stringResource(
                    R.string.temperature_row, row.temperatureC.roundToInt(), row.muzzleVelocityMps.roundToInt(),
                    (-row.point.dropRad / unit.radians).f(digits), unit.label,
                ),
                style = if (row.deltaC == 0.0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Collapsible(stringResource(R.string.section_hit_probability)) {
        FieldRow { m ->
            NumberField(stringResource(R.string.label_wind_uncertainty), conditions.windUncertaintyMps, "m/s", m) { v ->
                update { it.copy(windUncertaintyMps = v) }
            }
            NumberField(stringResource(R.string.label_range_uncertainty), conditions.rangeUncertaintyM, "m", m) { v ->
                update { it.copy(rangeUncertaintyM = v) }
            }
        }
        val assumed = stringResource(R.string.assumed)
        Text(
            stringResource(
                R.string.hit_inputs,
                inputs.velocitySdMps.f(1) + if (result.ammo.velocitySd.toNumber() == null) " $assumed" else "",
                (inputs.groupRad / AngularUnit.MOA.radians).f(2) + if (result.weapon.groupMoa.toNumber() == null) " $assumed" else "",
                cm(inputs.widthM), cm(inputs.heightM),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        analysis?.hit?.let { h ->
            Text(
                stringResource(R.string.hit_result, (h.probability * 100).roundToInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.hit_breakdown,
                    cm(h.verticalSdM), cm(h.fromVelocityM), cm(h.fromRangeM), cm(h.fromRifleM),
                    cm(h.horizontalSdM), cm(h.fromWindM), cm(h.fromRifleM),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Collapsible(stringResource(R.string.section_ranging)) {
        var size by remember { mutableStateOf("") }
        var reading by remember { mutableStateOf("") }
        val sizeCm = size.toNumber() ?: (inputs.heightM * 100)
        FieldRow { m ->
            NumberField(stringResource(R.string.label_ranging_size), size, "cm", m, placeholder = cm(inputs.heightM)) { size = it }
            NumberField(stringResource(R.string.label_ranging_reading), reading, reticleUnit.label, m) { reading = it }
        }
        rangeFromSize(sizeCm / 100, (reading.toNumber() ?: 0.0) * reticleUnit.radians)?.let { d ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ranging_result, d.roundToInt()), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { onUseDistance(d) }) { Text(stringResource(R.string.action_use_distance)) }
            }
        }
    }
}
