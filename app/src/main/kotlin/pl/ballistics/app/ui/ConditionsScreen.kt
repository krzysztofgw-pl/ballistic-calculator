package pl.ballistics.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.ballistics.app.data.Conditions

@Composable
fun ConditionsScreen(i: Conditions, update: ((Conditions) -> Conditions) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Atmosfera") {
            FieldRow { m ->
                NumberField("Temperatura", i.temperatureC, "°C", m) { v -> update { it.copy(temperatureC = v) } }
                NumberField("Wilgotność", i.humidityPct, "%", m) { v -> update { it.copy(humidityPct = v) } }
            }
            FieldRow { m ->
                NumberField(if (i.pressureIsQnh) "Ciśnienie QNH" else "Ciśnienie stacji", i.pressureHpa, "hPa", m) { v ->
                    update { it.copy(pressureHpa = v) }
                }
                NumberField("Wysokość", i.altitudeM, "m n.p.m.", m) { v -> update { it.copy(altitudeM = v) } }
            }
            SwitchRow("Ciśnienie zredukowane do poziomu morza (z prognozy)", i.pressureIsQnh) { v ->
                update { it.copy(pressureIsQnh = v) }
            }
        }

        Section("Wiatr") {
            FieldRow { m ->
                NumberField("Prędkość", i.windSpeed, "m/s", m) { v -> update { it.copy(windSpeed = v) } }
                NumberField("Kierunek (skąd)", i.windClock, "godz.", m) { v -> update { it.copy(windClock = v) } }
            }
            Text(
                "Kierunek jak na tarczy zegara: 12 = od celu, 3 = z prawej, 9 = z lewej.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Strzał") {
            FieldRow { m ->
                NumberField("Kąt strzału", i.lookAngleDeg, "°", m) { v -> update { it.copy(lookAngleDeg = v) } }
                NumberField("Przechył broni", i.cantDeg, "°", m) { v -> update { it.copy(cantDeg = v) } }
            }
            FieldRow { m ->
                NumberField("Zasięg tabeli", i.maxRangeM, "m", m) { v -> update { it.copy(maxRangeM = v) } }
                NumberField("Krok", i.stepM, "m", m) { v -> update { it.copy(stepM = v) } }
            }
        }

        Section("Efekty zaawansowane") {
            SwitchRow("Znos żyroskopowy (spin drift)", i.spinDrift) { v -> update { it.copy(spinDrift = v) } }
            SwitchRow("Skok aerodynamiczny od wiatru", i.aerodynamicJump) { v -> update { it.copy(aerodynamicJump = v) } }
            SwitchRow("Efekt Coriolisa i Eötvösa", i.coriolis) { v -> update { it.copy(coriolis = v) } }
            if (i.coriolis) {
                FieldRow { m ->
                    NumberField("Szerokość geogr.", i.latitudeDeg, "°", m) { v -> update { it.copy(latitudeDeg = v) } }
                    NumberField("Azymut strzału", i.azimuthDeg, "°", m) { v -> update { it.copy(azimuthDeg = v) } }
                }
            }
        }
    }
}
