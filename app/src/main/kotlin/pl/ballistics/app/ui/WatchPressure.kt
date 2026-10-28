package pl.ballistics.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * Fills in the station pressure from a Garmin watch's barometer, through Garmin Connect. Shown when
 * the watch is switched on in Settings; without Garmin Connect it reports that as its failure.
 */
@Composable
fun WatchPressureButton(update: ((Conditions) -> Conditions) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    // As wide as the pressure field above it. Progress and the reading show as toasts; a failure leaves a
    // warning icon whose dialog keeps Garmin Connect's status, the only diagnostic in the field.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            enabled = job == null,
            modifier = Modifier.weight(1f),
            onClick = {
                job = scope.launch {
                    error = null
                    toast(context.getString(R.string.watch_pressure_connecting))
                    val result = GarminPressure.read(context) { toast(context.getString(R.string.watch_pressure_confirm)) }
                    error = when (result) {
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
        Row(Modifier.weight(1f)) {
            if (error != null) {
                IconButton(onClick = { showError = true }) {
                    Icon(Icons.Filled.Warning, stringResource(R.string.watch_pressure_problem), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (showError) {
        error?.let { text ->
            AlertDialog(
                onDismissRequest = { showError = false },
                icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                title = { Text(stringResource(R.string.watch_pressure_problem)) },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { showError = false }) { Text(stringResource(R.string.action_close)) } },
            )
        }
    }
}
