package pl.ballistics.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.ballistics.app.R
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.data.GarminPressure
import java.util.Locale

/**
 * Fills in the station pressure from a Garmin watch's barometer. Shown only when Garmin Connect is
 * installed, since the reading travels through it.
 */
@Composable
fun WatchPressureButton(update: ((Conditions) -> Conditions) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    if (!remember { GarminPressure.garminConnectInstalled(context) }) return

    OutlinedButton(
        enabled = job == null,
        onClick = {
            job = scope.launch {
                status = context.getString(R.string.watch_pressure_connecting)
                val result = GarminPressure.read(context) { status = context.getString(R.string.watch_pressure_confirm) }
                status = when (result) {
                    is GarminPressure.Result.Pressure -> {
                        val hPa = String.format(Locale.US, "%.1f", result.hPa)
                        update { it.copy(pressureHpa = hPa, pressureIsQnh = false) }
                        context.getString(R.string.watch_pressure_result, hPa)
                    }
                    GarminPressure.Result.NoGarminConnect -> context.getString(R.string.watch_pressure_no_connect)
                    GarminPressure.Result.NoWatch -> context.getString(R.string.watch_pressure_no_watch)
                    GarminPressure.Result.AppNotInstalled -> context.getString(R.string.watch_pressure_not_installed)
                    GarminPressure.Result.NoAnswer -> context.getString(R.string.watch_pressure_no_answer)
                }
                job = null
            }
        },
    ) { Text(stringResource(R.string.action_watch_pressure)) }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}
