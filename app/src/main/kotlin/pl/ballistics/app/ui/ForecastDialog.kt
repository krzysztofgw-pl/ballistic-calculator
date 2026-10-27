package pl.ballistics.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import pl.ballistics.app.R
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.data.ForecastHour
import pl.ballistics.app.data.WindForecast
import pl.ballistics.app.data.WindHeight
import pl.ballistics.app.toNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface ForecastState {
    data object Loading : ForecastState
    data class Failed(val message: String) : ForecastState
    data class Ready(val hours: List<ForecastHour>) : ForecastState
}

private fun one(v: Double) = String.format(Locale.US, "%.1f", v)

/**
 * Fetches the hourly forecast for the phone's location and suggests the mean wind over a chosen
 * window, converted to the line of fire with the current shot azimuth.
 */
@Composable
fun ForecastDialog(conditions: Conditions, onApply: ((Conditions) -> Conditions) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ForecastState>(ForecastState.Loading) }
    var startHours by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(2) }
    var height by remember { mutableStateOf(WindHeight.OPEN) }
    var fillAtmosphere by remember { mutableStateOf(false) }

    val noLocation = stringResource(R.string.location_unavailable)
    LaunchedEffect(Unit) {
        val location = DeviceSensors.location(context)
        state = if (location == null) {
            ForecastState.Failed(noLocation)
        } else {
            try {
                ForecastState.Ready(WindForecast.fetch(location.latitude, location.longitude))
            } catch (e: Exception) {
                ForecastState.Failed(context.getString(R.string.forecast_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    val azimuth = conditions.azimuthDeg.toNumber() ?: 0.0
    val now = System.currentTimeMillis() / 1000
    val from = now + startHours * 3600L
    val ready = state as? ForecastState.Ready
    val window = ready?.let { WindForecast.window(it.hours, from, from + duration * 3600L) }.orEmpty()
    val summary = WindForecast.summarize(window)
    val suggested = summary?.let { it.speedMps * height.factor }
    val clock = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US).withZone(ZoneId.systemDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_forecast)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    ForecastState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.forecast_loading))
                    }
                    is ForecastState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    is ForecastState.Ready -> {
                        FieldRow { m ->
                            Column(m) {
                                Choice(stringResource(R.string.label_forecast_start), startHours, (0..24).toList(), {
                                    if (it == 0) stringResource(R.string.forecast_now) else stringResource(R.string.forecast_in_hours, it)
                                }) { startHours = it }
                            }
                            Column(m) {
                                Choice(stringResource(R.string.label_forecast_duration), duration, listOf(1, 2, 3, 4, 6, 8), {
                                    stringResource(R.string.forecast_hours, it)
                                }) { duration = it }
                            }
                        }
                        window.forEach { h ->
                            Text(
                                String.format(
                                    Locale.US, "%s  %4.1f m/s  %3d° %-3s  gust %s",
                                    clock.format(Instant.ofEpochSecond(h.timeSec)), h.speedMps, h.fromDeg.roundToInt(),
                                    compassPoint(h.fromDeg), h.gustMps?.let(::one) ?: "–",
                                ),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (summary == null) {
                            Text(stringResource(R.string.forecast_no_hours), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(
                                stringResource(
                                    R.string.forecast_summary,
                                    one(summary.speedMps), one(summary.minSpeedMps), one(summary.maxSpeedMps),
                                    summary.fromDeg.roundToInt(), compassPoint(summary.fromDeg),
                                    summary.gustMps?.let(::one) ?: "–", (summary.steadiness * 100).roundToInt(),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (summary.steadiness < 0.7) {
                                Text(stringResource(R.string.forecast_unsteady), color = MaterialTheme.colorScheme.error)
                            }
                            Choice(stringResource(R.string.label_wind_height), height, WindHeight.entries, {
                                stringResource(
                                    when (it) {
                                        WindHeight.FORECAST -> R.string.wind_height_forecast
                                        WindHeight.OPEN -> R.string.wind_height_open
                                        WindHeight.ROUGH -> R.string.wind_height_rough
                                    },
                                    one(it.factor),
                                )
                            }) { height = it }
                            val relative = (summary.fromDeg - azimuth).mod(360.0)
                            Text(
                                stringResource(
                                    R.string.forecast_suggestion,
                                    one(suggested!!), relative.roundToInt(), azimuth.roundToInt(),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            SwitchRow(stringResource(R.string.label_forecast_atmosphere), fillAtmosphere) { fillAtmosphere = it }
                        }
                        Text(stringResource(R.string.forecast_attribution), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = summary != null,
                onClick = {
                    val s = summary ?: return@TextButton
                    onApply { c ->
                        val az = c.azimuthDeg.toNumber() ?: 0.0
                        var next = c.copy(
                            windSpeed = one(s.speedMps * height.factor),
                            windMinMps = one(s.minSpeedMps * height.factor),
                            windMaxMps = one(s.maxSpeedMps * height.factor),
                            windFromDeg = (s.fromDeg - az).mod(360.0).roundToInt().mod(360).toString(),
                        )
                        if (fillAtmosphere) {
                            s.temperatureC?.let { next = next.copy(temperatureC = one(it)) }
                            s.humidityPct?.let { next = next.copy(humidityPct = it.roundToInt().toString()) }
                            s.pressureMslHpa?.let { next = next.copy(pressureHpa = one(it), pressureIsQnh = true) }
                        }
                        next
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
