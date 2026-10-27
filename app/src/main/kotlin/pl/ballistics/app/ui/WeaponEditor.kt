package pl.ballistics.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.ballistics.app.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.AngularUnit
import pl.ballistics.app.data.FocalPlane
import pl.ballistics.app.data.RailPlatform
import pl.ballistics.app.data.WeaponProfile
import pl.ballistics.app.reticle.Reticles
import pl.ballistics.app.toNumber
import pl.ballistics.core.ScopeMount
import java.util.Locale

@Composable
fun WeaponEditor(
    initial: WeaponProfile,
    ammo: List<AmmoProfile>,
    isNew: Boolean,
    onSave: (WeaponProfile) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var w by remember { mutableStateOf(initial) }
    var mountHelper by remember { mutableStateOf(false) }

    EditorScaffold(
        title = if (isNew) stringResource(R.string.title_new_rifle) else w.name,
        canDelete = !isNew,
        onSave = { onSave(w) },
        onDelete = onDelete,
        onClose = onClose,
    ) {
        Section(stringResource(R.string.section_rifle)) {
            TextInput(stringResource(R.string.label_name), w.name) { w = w.copy(name = it) }
            TextInput(stringResource(R.string.label_caliber), w.caliber) { w = w.copy(caliber = it) }
            NumberField(stringResource(R.string.label_twist), w.twistIn, "in", optional = true) { w = w.copy(twistIn = it) }
            SwitchRow(stringResource(R.string.label_right_twist), w.rightHandTwist) { w = w.copy(rightHandTwist = it) }
        }

        Section(stringResource(R.string.section_scope_mount)) {
            TextInput(stringResource(R.string.label_scope), w.scopeName) { w = w.copy(scopeName = it) }
            Choice(stringResource(R.string.label_turret_units), w.angularUnit, AngularUnit.entries, { it.label }) { v ->
                w = w.copy(angularUnit = v, clickValue = if (v == AngularUnit.MRAD) "0.1" else "0.25")
            }
            NumberField(stringResource(R.string.label_click_value), w.clickValue, w.angularUnit.label) { w = w.copy(clickValue = it) }
            Choice(stringResource(R.string.label_reticle), Reticles.byId(w.reticleId), Reticles.all, { stringResource(it.name) }) { w = w.copy(reticleId = it.id) }
            Choice(stringResource(R.string.label_focal_plane), w.focalPlane, FocalPlane.entries, { stringResource(it.label) }) { w = w.copy(focalPlane = it) }
            FieldRow { m ->
                if (w.focalPlane == FocalPlane.SFP) {
                    NumberField(stringResource(R.string.label_calibrated_mag), w.calibratedMagnification, "×", m) {
                        w = w.copy(calibratedMagnification = it)
                    }
                }
                NumberField(stringResource(R.string.label_max_mag), w.maxMagnification, "×", m) { w = w.copy(maxMagnification = it) }
            }
            NumberField(stringResource(R.string.label_sight_height), w.sightHeightCm, "cm") { w = w.copy(sightHeightCm = it) }
            TextButton(onClick = { mountHelper = true }) { Text(stringResource(R.string.action_sight_height_helper)) }
        }

        Section(stringResource(R.string.section_zero)) {
            Choice(
                stringResource(R.string.label_load),
                ammo.find { it.id == w.ammoId },
                listOf<AmmoProfile?>(null) + ammo,
                { it?.name ?: stringResource(R.string.choose) },
            ) { w = w.copy(ammoId = it?.id) }
            NumberField(stringResource(R.string.label_zero_range), w.zeroRangeM, "m") { w = w.copy(zeroRangeM = it) }
            FieldRow { m ->
                NumberField(stringResource(R.string.label_temperature), w.zeroTemperatureC, "°C", m) { w = w.copy(zeroTemperatureC = it) }
                NumberField(stringResource(R.string.label_pressure_station), w.zeroPressureHpa, "hPa", m) { w = w.copy(zeroPressureHpa = it) }
            }
        }
    }

    if (mountHelper) {
        MountHelperDialog(
            onDismiss = { mountHelper = false },
            onUse = { cm -> w = w.copy(sightHeightCm = cm); mountHelper = false },
        )
    }
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun cm(m: Double) = String.format(Locale.US, "%.2f", m * 100)

@Composable
private fun MountHelperDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var measure by remember { mutableStateOf(true) }
    // Caliper method
    var gap by remember { mutableStateOf("") }
    var bolt by remember { mutableStateOf("18") }
    var tube by remember { mutableStateOf("30") }
    // Estimate from mount
    var platform by remember { mutableStateOf(RailPlatform.BOLT_PICATINNY) }
    var railAboveBore by remember { mutableStateOf(platform.railAboveBoreMm.toString()) }
    var objective by remember { mutableStateOf("50") }
    var ringAxis by remember { mutableStateOf("") }

    val resultM: Double? = if (measure) {
        val g = gap.toNumber(); val b = bolt.toNumber(); val t = tube.toNumber()
        if (g != null && b != null && t != null) ScopeMount.measuredSightHeightM(g / 1000, b / 1000, t / 1000) else null
    } else {
        val rail = railAboveBore.toNumber()
        val minAxis = objective.toNumber()?.let { ScopeMount.minRingAxisHeightM(it) }
        val axis = ringAxis.toNumber()?.div(1000) ?: minAxis
        if (rail != null && axis != null) ScopeMount.estimatedSightHeightM(rail / 1000, axis) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_sight_height)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.label_method), measure, listOf(true, false), { stringResource(if (it) R.string.method_measure else R.string.method_estimate) }) { measure = it }
                if (measure) {
                    Text(
                        stringResource(R.string.hint_caliper),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    NumberField(stringResource(R.string.label_bolt_tube_gap), gap, "mm") { gap = it }
                    NumberField(stringResource(R.string.label_bolt_diameter), bolt, "mm") { bolt = it }
                    NumberField(stringResource(R.string.label_tube_diameter), tube, "mm") { tube = it }
                } else {
                    Choice(stringResource(R.string.label_platform), platform, RailPlatform.entries, { stringResource(it.label) }) {
                        platform = it
                        it.railAboveBoreMm?.let { v -> railAboveBore = v.toString() }
                    }
                    NumberField(stringResource(R.string.label_rail_above_bore), railAboveBore, "mm") { railAboveBore = it }
                    NumberField(stringResource(R.string.label_objective), objective, "mm") { objective = it }
                    val minAxis = objective.toNumber()?.let { ScopeMount.minRingAxisHeightM(it) * 1000 }
                    if (minAxis != null) {
                        Text(
                            stringResource(R.string.hint_min_ring_axis, minAxis),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    NumberField(stringResource(R.string.label_ring_axis), ringAxis, "mm", optional = true) { ringAxis = it }
                    Text(
                        stringResource(R.string.hint_ring_axis),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    resultM?.let { stringResource(R.string.sight_height_result, cm(it)) } ?: stringResource(R.string.fill_in_fields),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { resultM?.let { onUse(cm(it)) } }, enabled = resultM != null) { Text(stringResource(R.string.action_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
