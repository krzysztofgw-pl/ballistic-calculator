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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.ballistics.app.R
import pl.ballistics.app.data.Conditions

@Composable
fun ConditionsScreen(i: Conditions, update: ((Conditions) -> Conditions) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(stringResource(R.string.section_atmosphere)) {
            FieldRow { m ->
                NumberField(stringResource(R.string.label_temperature), i.temperatureC, "°C", m) { v -> update { it.copy(temperatureC = v) } }
                NumberField(stringResource(R.string.label_humidity), i.humidityPct, "%", m) { v -> update { it.copy(humidityPct = v) } }
            }
            FieldRow { m ->
                NumberField(
                    stringResource(if (i.pressureIsQnh) R.string.label_pressure_qnh else R.string.label_pressure_station),
                    i.pressureHpa, "hPa", m,
                ) { v -> update { it.copy(pressureHpa = v) } }
                NumberField(stringResource(R.string.label_altitude), i.altitudeM, stringResource(R.string.unit_m_asl), m) { v ->
                    update { it.copy(altitudeM = v) }
                }
            }
            SwitchRow(stringResource(R.string.label_pressure_is_qnh), i.pressureIsQnh) { v -> update { it.copy(pressureIsQnh = v) } }
        }

        Section(stringResource(R.string.section_wind)) {
            FieldRow { m ->
                NumberField(stringResource(R.string.label_wind_speed), i.windSpeed, "m/s", m) { v -> update { it.copy(windSpeed = v) } }
                NumberField(stringResource(R.string.label_wind_from), i.windClock, stringResource(R.string.unit_oclock), m) { v ->
                    update { it.copy(windClock = v) }
                }
            }
            Text(stringResource(R.string.hint_wind_clock), style = MaterialTheme.typography.bodySmall)
        }

        Section(stringResource(R.string.section_shot)) {
            FieldRow { m ->
                NumberField(stringResource(R.string.label_look_angle), i.lookAngleDeg, "°", m) { v -> update { it.copy(lookAngleDeg = v) } }
                NumberField(stringResource(R.string.label_cant), i.cantDeg, "°", m) { v -> update { it.copy(cantDeg = v) } }
            }
            FieldRow { m ->
                NumberField(stringResource(R.string.label_table_range), i.maxRangeM, "m", m) { v -> update { it.copy(maxRangeM = v) } }
                NumberField(stringResource(R.string.label_table_step), i.stepM, "m", m) { v -> update { it.copy(stepM = v) } }
            }
        }

        Section(stringResource(R.string.section_advanced)) {
            SwitchRow(stringResource(R.string.label_spin_drift), i.spinDrift) { v -> update { it.copy(spinDrift = v) } }
            SwitchRow(stringResource(R.string.label_aero_jump), i.aerodynamicJump) { v -> update { it.copy(aerodynamicJump = v) } }
            SwitchRow(stringResource(R.string.label_coriolis), i.coriolis) { v -> update { it.copy(coriolis = v) } }
            if (i.coriolis) {
                FieldRow { m ->
                    NumberField(stringResource(R.string.label_latitude), i.latitudeDeg, "°", m) { v -> update { it.copy(latitudeDeg = v) } }
                    NumberField(stringResource(R.string.label_azimuth), i.azimuthDeg, "°", m) { v -> update { it.copy(azimuthDeg = v) } }
                }
            }
        }
    }
}
