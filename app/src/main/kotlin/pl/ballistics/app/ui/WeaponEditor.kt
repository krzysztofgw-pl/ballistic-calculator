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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.AngularUnit
import pl.ballistics.app.data.RailPlatform
import pl.ballistics.app.data.WeaponProfile
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
        title = if (isNew) "Nowa broń" else w.name,
        canDelete = !isNew,
        onSave = { onSave(w) },
        onDelete = onDelete,
        onClose = onClose,
    ) {
        Section("Broń") {
            TextInput("Nazwa", w.name) { w = w.copy(name = it) }
            TextInput("Kaliber", w.caliber) { w = w.copy(caliber = it) }
            NumberField("Skok gwintu (1 obrót na)", w.twistIn, "cal", optional = true) { w = w.copy(twistIn = it) }
            SwitchRow("Gwint prawoskrętny", w.rightHandTwist) { w = w.copy(rightHandTwist = it) }
        }

        Section("Luneta i montaż") {
            TextInput("Luneta", w.scopeName) { w = w.copy(scopeName = it) }
            Choice("Jednostki poprawek", w.angularUnit, AngularUnit.entries, { it.label }) { v ->
                w = w.copy(angularUnit = v, clickValue = if (v == AngularUnit.MRAD) "0.1" else "0.25")
            }
            NumberField("Wartość kliku", w.clickValue, w.angularUnit.label) { w = w.copy(clickValue = it) }
            NumberField("Wysokość osi lunety nad osią lufy", w.sightHeightCm, "cm") { w = w.copy(sightHeightCm = it) }
            TextButton(onClick = { mountHelper = true }) { Text("Pomóż wyznaczyć wysokość") }
        }

        Section("Przystrzelanie") {
            Choice(
                "Amunicja",
                ammo.find { it.id == w.ammoId },
                listOf<AmmoProfile?>(null) + ammo,
                { it?.name ?: "wybierz" },
            ) { w = w.copy(ammoId = it?.id) }
            NumberField("Odległość zera", w.zeroRangeM, "m") { w = w.copy(zeroRangeM = it) }
            FieldRow { m ->
                NumberField("Temperatura", w.zeroTemperatureC, "°C", m) { w = w.copy(zeroTemperatureC = it) }
                NumberField("Ciśnienie stacji", w.zeroPressureHpa, "hPa", m) { w = w.copy(zeroPressureHpa = it) }
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
        title = { Text("Wysokość lunety") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Metoda", measure, listOf(true, false), { if (it) "Pomiar" else "Szacunek" }) { measure = it }
                if (measure) {
                    Text(
                        "Otwórz zamek i zmierz suwmiarką odstęp od wierzchu trzonu zamka do spodu tubusa lunety.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    NumberField("Odstęp zamek–tubus", gap, "mm") { gap = it }
                    NumberField("Średnica trzonu zamka", bolt, "mm") { bolt = it }
                    NumberField("Średnica tubusa", tube, "mm") { tube = it }
                } else {
                    Choice("Platforma", platform, RailPlatform.entries, { it.label }) {
                        platform = it
                        it.railAboveBoreMm?.let { v -> railAboveBore = v.toString() }
                    }
                    NumberField("Szyna nad osią lufy", railAboveBore, "mm") { railAboveBore = it }
                    NumberField("Średnica obiektywu", objective, "mm") { objective = it }
                    val minAxis = objective.toNumber()?.let { ScopeMount.minRingAxisHeightM(it) * 1000 }
                    if (minAxis != null) {
                        Text(
                            "Najniższe pierścienie mieszczące obiektyw: oś ok. %.0f mm nad szyną.".format(minAxis),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    NumberField("Wysokość osi pierścieni nad szyną", ringAxis, "mm", optional = true) { ringAxis = it }
                    Text(
                        "Producent montażu zwykle podaje wysokość osi w mm. Puste pole = najniższe pasujące pierścienie. " +
                            "Wysokości szyn są orientacyjne, pomiar jest dokładniejszy.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    resultM?.let { "Wysokość: ${cm(it)} cm" } ?: "Uzupełnij pola",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { resultM?.let { onUse(cm(it)) } }, enabled = resultM != null) { Text("Użyj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
