package pl.ballistics.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.ballistics.app.AngularUnit
import pl.ballistics.app.DragType
import pl.ballistics.app.Inputs

@Composable
fun RifleScreen(i: Inputs, update: ((Inputs) -> Inputs) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Broń") {
            FieldRow { m ->
                NumberField("Wys. celownika", i.sightHeightCm, "cm", m) { v -> update { it.copy(sightHeightCm = v) } }
                NumberField("Przystrzelanie", i.zeroRangeM, "m", m) { v -> update { it.copy(zeroRangeM = v) } }
            }
            NumberField("Skok gwintu (1 obrót na)", i.twistIn, "cal", optional = true) { v ->
                update { it.copy(twistIn = v) }
            }
            SwitchRow("Gwint prawoskrętny", i.rightHandTwist) { v -> update { it.copy(rightHandTwist = v) } }
            Choice("Jednostki poprawek", i.angularUnit, AngularUnit.entries, { it.label }) { v ->
                update { it.copy(angularUnit = v, clickValue = if (v == AngularUnit.MRAD) "0.1" else "0.25") }
            }
            NumberField("Wartość kliku", i.clickValue, i.angularUnit.label) { v -> update { it.copy(clickValue = v) } }
        }

        Section("Pocisk") {
            Choice("Model oporu", i.dragType, DragType.entries, { if (it == DragType.CUSTOM) "Własna Cd(M)" else it.name }) { v ->
                update { it.copy(dragType = v) }
            }
            if (i.dragType == DragType.CUSTOM) {
                Text(
                    "Wklej krzywą oporu z pomiarów Dopplera: w każdym wierszu liczba Macha i Cd, np. \"0.80 0.157\".",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = i.customCurve,
                    onValueChange = { v -> update { it.copy(customCurve = v) } },
                    label = { Text("Mach  Cd") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            } else {
                NumberField("Współczynnik balistyczny ${i.dragType.name}", i.bc, "lb/in²") { v -> update { it.copy(bc = v) } }
            }
            FieldRow { m ->
                NumberField("Masa", i.massGr, "gr", m) { v -> update { it.copy(massGr = v) } }
                NumberField("Średnica", i.diameterMm, "mm", m) { v -> update { it.copy(diameterMm = v) } }
            }
            NumberField("Długość pocisku (stabilność, znos)", i.lengthMm, "mm", optional = true) { v ->
                update { it.copy(lengthMm = v) }
            }
        }

        Section("Amunicja") {
            NumberField("Prędkość początkowa", i.muzzleVelocity, "m/s") { v -> update { it.copy(muzzleVelocity = v) } }
            FieldRow { m ->
                NumberField("Czułość prochu", i.powderSensitivity, "m/s/°C", m) { v -> update { it.copy(powderSensitivity = v) } }
                NumberField("przy temp.", i.powderReferenceTemp, "°C", m) { v -> update { it.copy(powderReferenceTemp = v) } }
            }
        }

        Section("Warunki przystrzelania") {
            FieldRow { m ->
                NumberField("Temperatura", i.zeroTemperatureC, "°C", m) { v -> update { it.copy(zeroTemperatureC = v) } }
                NumberField("Ciśnienie stacji", i.zeroPressureHpa, "hPa", m) { v -> update { it.copy(zeroPressureHpa = v) } }
            }
        }
    }
}
