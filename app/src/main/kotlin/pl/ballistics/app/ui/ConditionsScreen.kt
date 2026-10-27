package pl.ballistics.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.ballistics.app.R
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.toNumber
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
                NumberField(stringResource(R.string.label_wind_from), i.windDirectionDeg, "°", m) { v -> update { it.copy(windFromDeg = v) } }
            }
            WindDial(i, update)
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
            AzimuthDial(i, update)
        }

        Section(stringResource(R.string.section_advanced)) {
            SwitchRow(stringResource(R.string.label_spin_drift), i.spinDrift) { v -> update { it.copy(spinDrift = v) } }
            SwitchRow(stringResource(R.string.label_aero_jump), i.aerodynamicJump) { v -> update { it.copy(aerodynamicJump = v) } }
            SwitchRow(stringResource(R.string.label_coriolis), i.coriolis) { v -> update { it.copy(coriolis = v) } }
            if (i.coriolis) LatitudeInput(i, update)
        }
    }
}

private fun deg(v: Double): String = v.roundToInt().mod(360).toString()

private fun clockHour(deg: Double): String {
    val h = deg.mod(360.0) / 30
    val rounded = (h * 2).roundToInt() / 2.0
    val v = if (rounded == 0.0 || rounded == 12.0) 12.0 else rounded
    return if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
}

@Composable
private fun WindDial(i: Conditions, update: ((Conditions) -> Conditions) -> Unit) {
    val relative = i.windDirectionDeg.toNumber()
    val azimuth = i.azimuthDeg.toNumber() ?: 0.0
    SwitchRow(stringResource(R.string.label_wind_north_up), i.windDialNorthUp) { v -> update { it.copy(windDialNorthUp = v) } }
    if (i.windDialNorthUp) {
        val bearing = relative?.let { (it + azimuth).mod(360.0) }
        CompassDial(
            valueDeg = bearing,
            onChange = { v -> update { it.copy(windFromDeg = deg(v - (it.azimuthDeg.toNumber() ?: 0.0))) } },
            arrow = DialArrow.INWARD,
            centerText = bearing?.let { stringResource(R.string.wind_center_true, deg(it), compassPoint(it)) } ?: "–",
            outerLabel = { "$it°" },
            innerLabel = { cardinal(it) },
            referenceDeg = azimuth,
            stepDeg = 5.0,
        )
        Text(stringResource(R.string.hint_wind_dial_north), style = MaterialTheme.typography.bodySmall)
    } else {
        CompassDial(
            valueDeg = relative,
            onChange = { v -> update { it.copy(windFromDeg = deg(v)) } },
            arrow = DialArrow.INWARD,
            centerText = relative?.let { stringResource(R.string.wind_center_relative, deg(it), clockHour(it)) } ?: "–",
            outerLabel = { "$it°" },
            innerLabel = { if (it == 0) "12" else (it / 30).toString() },
            stepDeg = 5.0,
        )
        Text(stringResource(R.string.hint_wind_dial), style = MaterialTheme.typography.bodySmall)
    }
    relative?.let {
        val bearing = (it + azimuth).mod(360.0)
        Text(
            stringResource(R.string.wind_true_bearing, deg(it), clockHour(it), deg(bearing), compassPoint(bearing), deg(azimuth)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val withPermission = rememberWithLocationPermission()
    var forecast by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { withPermission { forecast = true } }) { Text(stringResource(R.string.action_forecast_wind)) }
    if (forecast) ForecastDialog(i, update) { forecast = false }
}

private fun cardinal(deg: Int): String? = when (deg) {
    0 -> "N"
    90 -> "E"
    180 -> "S"
    270 -> "W"
    else -> null
}

/** Runs [action] once location permission has been asked for (granted or not). */
@Composable
private fun rememberWithLocationPermission(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.invoke()
        pending = null
    }
    return { action ->
        if (DeviceSensors.hasLocationPermission(context)) {
            action()
        } else {
            pending = action
            launcher.launch(DeviceSensors.locationPermissions)
        }
    }
}

@Composable
private fun AzimuthDial(i: Conditions, update: ((Conditions) -> Conditions) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val withPermission = rememberWithLocationPermission()
    var status by remember { mutableStateOf<String?>(null) }

    val azimuth = i.azimuthDeg.toNumber()
    NumberField(stringResource(R.string.label_azimuth), i.azimuthDeg, "°") { v -> update { it.copy(azimuthDeg = v) } }
    CompassDial(
        valueDeg = azimuth,
        onChange = { v -> update { it.copy(azimuthDeg = deg(v)) } },
        arrow = DialArrow.OUTWARD,
        centerText = azimuth?.let { "${deg(it)}°\n${compassPoint(it)}" } ?: "–",
        outerLabel = { "$it°" },
        innerLabel = { cardinal(it) },
    )
    Text(stringResource(R.string.hint_azimuth), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = {
        withPermission {
            scope.launch {
                status = context.getString(R.string.compass_reading)
                val magnetic = DeviceSensors.magneticHeadingDeg(context)
                if (magnetic == null) {
                    status = context.getString(R.string.compass_unavailable)
                    return@launch
                }
                val location = DeviceSensors.lastKnown(context)
                if (location != null) {
                    val declination = DeviceSensors.declinationDeg(location)
                    val heading = magnetic + declination
                    update { it.copy(azimuthDeg = deg(heading)) }
                    status = context.getString(R.string.compass_true, deg(heading), String.format(Locale.US, "%+.1f", declination))
                } else {
                    update { it.copy(azimuthDeg = deg(magnetic)) }
                    status = context.getString(R.string.compass_magnetic, deg(magnetic))
                }
            }
        }
    }) { Text(stringResource(R.string.action_phone_compass)) }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    Text(stringResource(R.string.hint_compass), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun LatitudeInput(i: Conditions, update: ((Conditions) -> Conditions) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val withPermission = rememberWithLocationPermission()
    var status by remember { mutableStateOf<String?>(null) }

    val latitude = i.latitudeDeg.toNumber()
    NumberField(stringResource(R.string.label_latitude), i.latitudeDeg, "°") { v -> update { it.copy(latitudeDeg = v) } }
    latitude?.takeIf { abs(it) <= 90 }?.let {
        val text = String.format(Locale.US, "%.2f", abs(it))
        Text(
            stringResource(if (it >= 0) R.string.latitude_north else R.string.latitude_south, text),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    OutlinedButton(onClick = {
        withPermission {
            scope.launch {
                status = context.getString(R.string.location_reading)
                val location = DeviceSensors.location(context)
                status = if (location == null) {
                    context.getString(R.string.location_unavailable)
                } else {
                    val lat = String.format(Locale.US, "%.2f", location.latitude)
                    update { it.copy(latitudeDeg = lat) }
                    context.getString(R.string.location_result, lat, location.accuracy.roundToInt().toString())
                }
            }
        }
    }) { Text(stringResource(R.string.action_my_location)) }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    Text(stringResource(R.string.hint_latitude), style = MaterialTheme.typography.bodySmall)
}
