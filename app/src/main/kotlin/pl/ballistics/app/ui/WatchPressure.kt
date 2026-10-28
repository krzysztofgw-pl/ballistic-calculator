package pl.ballistics.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
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

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    // As wide as the pressure field above it. Progress and the reading show as toasts; a failure stays
    // under the button, since Garmin Connect's status is the only diagnostic in the field.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = job == null,
            modifier = Modifier.weight(1f),
            onClick = {
                job = scope.launch {
                    status = null
                    toast(context.getString(R.string.watch_pressure_connecting))
                    val result = GarminPressure.read(context) { toast(context.getString(R.string.watch_pressure_confirm)) }
                    status = when (result) {
                        is GarminPressure.Result.Pressure -> {
                            val hPa = String.format(Locale.US, "%.1f", result.hPa)
                            update { it.copy(pressureHpa = hPa, pressureIsQnh = false) }
                            toast(context.getString(R.string.watch_pressure_result, hPa))
                            null
                        }
                        GarminPressure.Result.NoGarminConnect -> context.getString(R.string.watch_pressure_no_connect)
                        GarminPressure.Result.NoWatch -> context.getString(R.string.watch_pressure_no_watch)
                        GarminPressure.Result.AppNotInstalled -> context.getString(R.string.watch_pressure_not_installed)
                        is GarminPressure.Result.NoAnswer -> context.getString(R.string.watch_pressure_no_answer, result.details)
                    }
                    job = null
                }
            },
        ) {
            if (job != null) {
                CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
            } else {
                Icon(ImageVector.vectorResource(R.drawable.ic_watch), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_watch_pressure_short), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}
