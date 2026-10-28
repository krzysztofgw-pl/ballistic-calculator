package pl.ballistics.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.ballistics.app.R
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.data.UiSettings
import pl.ballistics.app.toNumber
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ConditionsScreen(
    i: Conditions,
    ui: UiSettings,
    update: ((Conditions) -> Conditions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Wind changes most, so it comes first.
        Section(stringResource(R.string.section_wind)) {
            NumberField(stringResource(R.string.label_wind_speed), i.windSpeed, "m/s") { v -> update { it.copy(windSpeed = v) } }
            WindAndAzimuthDial(i, ui, update)
        }

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
            if (ui.garminWatch) WatchPressureButton(update)
        }

        Section(stringResource(R.string.section_shot)) {
            FieldRow { m ->
                Row(m, verticalAlignment = Alignment.CenterVertically) {
                    NumberField(stringResource(R.string.label_look_angle), i.lookAngleDeg, "°", Modifier.weight(1f)) { v ->
                        update { it.copy(lookAngleDeg = v) }
                    }
                    ShotAngleButton { deg -> update { it.copy(lookAngleDeg = String.format(Locale.US, "%.1f", deg)) } }
                }
                NumberField(stringResource(R.string.label_cant), i.cantDeg, "°", m) { v -> update { it.copy(cantDeg = v) } }
            }
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

private fun cardinal(deg: Int): String? = when (deg) {
    0 -> "N"
    90 -> "E"
    180 -> "S"
    270 -> "W"
    else -> null
}

/** What a tap or drag on the dial sets. */
private enum class DialEdit { WIND, AZIMUTH }

/**
 * One dial for the wind and the direction of fire. North-up it reads like a map: the shot goes out
 * along the azimuth and the wind comes in from its true bearing. Otherwise the line of fire points up,
 * the wind reads as a clock and a north marker shows the azimuth. Moving the azimuth keeps the wind
 * where it is in the frame shown: its true bearing north-up, its clock direction otherwise.
 */
@Composable
private fun WindAndAzimuthDial(i: Conditions, ui: UiSettings, update: ((Conditions) -> Conditions) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val withPermission = rememberWithLocationPermission()
    var editing by rememberSaveable { mutableStateOf(DialEdit.WIND) }
    var status by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var forecast by remember { mutableStateOf(false) }

    val relative = i.windDirectionDeg.toNumber()
    val azimuth = i.azimuthDeg.toNumber() ?: 0.0
    val bearing = relative?.let { (it + azimuth).mod(360.0) }
    val wind = MaterialTheme.colorScheme.primary
    val shot = MaterialTheme.colorScheme.tertiary

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1f).clickable { update { it.copy(windDialNorthUp = !it.windDialNorthUp) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(i.windDialNorthUp, onCheckedChange = { v -> update { it.copy(windDialNorthUp = v) } })
            Text(stringResource(R.string.label_north_up))
        }
        FilterChip(editing == DialEdit.WIND, { editing = DialEdit.WIND }, label = { Text(stringResource(R.string.dial_edit_wind)) })
        FilterChip(editing == DialEdit.AZIMUTH, { editing = DialEdit.AZIMUTH }, label = { Text(stringResource(R.string.dial_edit_azimuth)) })
    }

    val windLine = if (i.windDialNorthUp) {
        bearing?.let { stringResource(R.string.center_wind_true, deg(it), compassPoint(it)) }
    } else {
        relative?.let { stringResource(R.string.center_wind_clock, clockHour(it)) }
    } ?: "–"
    val center = windLine + "\n" + stringResource(R.string.center_azimuth, deg(azimuth))

    if (i.windDialNorthUp) {
        CompassDial(
            markers = listOfNotNull(
                DialMarker(azimuth, DialArrow.OUTWARD, shot, editing == DialEdit.AZIMUTH),
                bearing?.let { DialMarker(it, DialArrow.INWARD, wind, editing == DialEdit.WIND) },
            ),
            onChange = { v ->
                when (editing) {
                    DialEdit.WIND -> update { it.copy(windFromDeg = deg(v - (it.azimuthDeg.toNumber() ?: 0.0))) }
                    DialEdit.AZIMUTH -> update { c ->
                        val keep = c.windDirectionDeg.toNumber()?.let { it + (c.azimuthDeg.toNumber() ?: 0.0) }
                        c.copy(azimuthDeg = deg(v), windFromDeg = keep?.let { deg(it - v) } ?: c.windFromDeg)
                    }
                }
            },
            centerText = center,
            outerLabel = { "$it°" },
            innerLabel = { cardinal(it) },
            stepDeg = if (editing == DialEdit.WIND) 5.0 else 1.0,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        CompassDial(
            markers = listOfNotNull(
                DialMarker(0.0, DialArrow.OUTWARD, shot, false),
                DialMarker((-azimuth).mod(360.0), DialArrow.OUTWARD, shot, editing == DialEdit.AZIMUTH, label = "N"),
                relative?.let { DialMarker(it, DialArrow.INWARD, wind, editing == DialEdit.WIND) },
            ),
            onChange = { v ->
                when (editing) {
                    DialEdit.WIND -> update { it.copy(windFromDeg = deg(v)) }
                    // The dial shows where north is; the azimuth is the line of fire seen from north.
                    DialEdit.AZIMUTH -> update { it.copy(azimuthDeg = deg(-v)) }
                }
            },
            centerText = center,
            outerLabel = { "$it°" },
            innerLabel = { if (it == 0) "12" else (it / 30).toString() },
            stepDeg = if (editing == DialEdit.WIND) 5.0 else 1.0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    relative?.let {
        Text(
            stringResource(R.string.wind_true_bearing, deg(it), clockHour(it), deg(bearing!!), compassPoint(bearing), deg(azimuth)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { withPermission { forecast = true } }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_forecast_short), maxLines = 1)
        }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
            withPermission {
                scope.launch {
                    warning = null
                    status = context.getString(R.string.compass_reading)
                    val reading = DeviceSensors.magneticHeading(context)
                    if (reading == null) {
                        status = context.getString(R.string.compass_unavailable)
                        return@launch
                    }
                    val location = DeviceSensors.lastKnown(context)
                    val magnetic = reading.headingDeg
                    if (location != null) {
                        val declination = DeviceSensors.declinationDeg(location)
                        val heading = magnetic + declination
                        update { c ->
                            val lat = String.format(Locale.US, "%.2f", location.latitude)
                            c.copy(azimuthDeg = deg(heading)).let { if (ui.locationSetsLatitude) it.copy(latitudeDeg = lat) else it }
                        }
                        status = context.getString(R.string.compass_true, deg(heading), String.format(Locale.US, "%+.1f", declination))
                    } else {
                        update { it.copy(azimuthDeg = deg(magnetic)) }
                        status = context.getString(R.string.compass_magnetic, deg(magnetic))
                    }
                    val expected = location?.let { DeviceSensors.expectedFieldMicroTesla(it) }
                    warning = when (reading.problem(expected)) {
                        DeviceSensors.CompassProblem.UNCALIBRATED -> context.getString(R.string.compass_uncalibrated)
                        DeviceSensors.CompassProblem.INACCURATE ->
                            context.getString(R.string.compass_inaccurate, reading.headingAccuracyDeg?.roundToInt() ?: 0)
                        DeviceSensors.CompassProblem.DISTURBED -> context.getString(
                            R.string.compass_disturbed,
                            reading.fieldMicroTesla?.roundToInt() ?: 0,
                            expected?.roundToInt() ?: 0,
                        )
                        null -> null
                    }
                }
            }
        }) {
            Icon(ImageVector.vectorResource(R.drawable.ic_compass), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_compass_short), maxLines = 1)
        }
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    warning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    if (forecast) ForecastDialog(i, ui, update) { forecast = false }
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
private fun LatitudeInput(i: Conditions, update: ((Conditions) -> Conditions) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val withPermission = rememberWithLocationPermission()
    var reading by remember { mutableStateOf(false) }
    var accuracyM by remember { mutableStateOf<Int?>(null) }

    // The field holds the size of the latitude and the button its hemisphere, so south needs no minus key.
    val raw = i.latitudeDeg.trim()
    var south by remember { mutableStateOf(raw.startsWith("-")) }
    LaunchedEffect(raw) { if (raw.isNotEmpty() && raw != "-") south = raw.startsWith("-") }
    fun store(size: String, isSouth: Boolean) {
        south = isSouth
        update { it.copy(latitudeDeg = if (isSouth && size.isNotBlank()) "-$size" else size) }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        NumberField(stringResource(R.string.label_latitude), raw.removePrefix("-"), "°", Modifier.weight(1f)) { v ->
            accuracyM = null
            if (v.startsWith("-")) store(v.removePrefix("-"), true) else store(v, south)
        }
        OutlinedButton(
            onClick = { accuracyM = null; store(raw.removePrefix("-"), !south) },
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text(stringResource(if (south) R.string.hemisphere_south else R.string.hemisphere_north)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(enabled = !reading, onClick = {
                withPermission {
                    scope.launch {
                        reading = true
                        val location = DeviceSensors.location(context)
                        reading = false
                        if (location == null) {
                            accuracyM = null
                            Toast.makeText(context, context.getString(R.string.location_unavailable), Toast.LENGTH_LONG).show()
                        } else {
                            val size = String.format(Locale.US, "%.2f", abs(location.latitude))
                            store(size, location.latitude < 0)
                            accuracyM = location.accuracy.roundToInt()
                        }
                    }
                }
            }) {
                if (reading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.LocationOn, stringResource(R.string.action_my_location))
            }
            accuracyM?.let { Text(stringResource(R.string.location_accuracy, it), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
