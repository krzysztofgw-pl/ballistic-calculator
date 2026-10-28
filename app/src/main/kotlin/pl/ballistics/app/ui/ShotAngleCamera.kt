package pl.ballistics.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import pl.ballistics.app.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the back camera points: [elevationDeg] above the horizon, and the phone's roll about that axis. */
private data class CameraAxis(val elevationDeg: Double, val rollDeg: Double)

private const val PREFS = "camera_angle"
private const val KEY_TILT_UPRIGHT = "tilt_upright_deg"
private const val KEY_TILT_SIDEWAYS = "tilt_sideways_deg"
/** Starting zoom: on a phone with a telephoto lens the camera switches to it on its own. */
private const val START_ZOOM = 10f

/** Opens the camera to measure the shot angle; asks for the camera permission first. */
@Composable
fun ShotAngleButton(onAngle: (Double) -> Unit) {
    val context = LocalContext.current
    // Saveable, so the preview stays open when the screen turns between portrait and landscape.
    var open by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) open = true }
    IconButton(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            open = true
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }) {
        Icon(ImageVector.vectorResource(R.drawable.ic_camera), stringResource(R.string.action_measure_angle))
    }
    if (open) ShotAngleCamera(onDismiss = { open = false }, onAngle = { onAngle(it); open = false })
}

/**
 * Full-screen camera preview with a crosshair. The angle is that of the camera's axis to the horizon,
 * from the gravity sensor, so it reads the same however the phone is turned. A two-aim calibration at
 * the same point (portrait upright then upside down, or landscape one way then the other) removes the
 * camera's tilt in the phone body for that orientation; it is kept on this phone only.
 */
@Composable
private fun ShotAngleCamera(onDismiss: () -> Unit, onAngle: (Double) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    // The camera's tilt in the body, seen holding the phone upright and turned sideways.
    var tiltUpright by remember { mutableDoubleStateOf(prefs.getFloat(KEY_TILT_UPRIGHT, 0f).toDouble()) }
    var tiltSideways by remember { mutableDoubleStateOf(prefs.getFloat(KEY_TILT_SIDEWAYS, 0f).toDouble()) }
    fun tiltAt(rollDeg: Double) = tiltUpright * cos(Math.toRadians(rollDeg)) + tiltSideways * sin(Math.toRadians(rollDeg))
    val axis by rememberCameraAxis()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var firstAim by remember { mutableStateOf<CameraAxis?>(null) }
    val angle = axis?.let { it.elevationDeg + tiltAt(it.rollDeg) }

    DisposableEffect(Unit) {
        onDispose {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ future.get().unbindAll() }, ContextCompat.getMainExecutor(context))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        // A TextureView, which draws reliably inside a dialog window.
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                            val start = minOf(START_ZOOM, cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)
                            cam.cameraControl.setZoomRatio(start)
                            zoom = start
                            camera = cam
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val red = Color(0xFFE53935)
                val gap = 6.dp.toPx()
                val arm = 40.dp.toPx()
                val w = 1.5.dp.toPx()
                drawLine(red, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), w)
                drawLine(red, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), w)
                drawLine(red, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), w)
                drawLine(red, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), w)
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xB0000000)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    angle?.let { String.format(Locale.US, "%+.1f°", it) } ?: "—",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                )
                camera?.cameraInfo?.zoomState?.value?.let { z ->
                    if (z.maxZoomRatio > z.minZoomRatio) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Slider(
                                value = zoom,
                                onValueChange = { v -> zoom = v; camera?.cameraControl?.setZoomRatio(v) },
                                valueRange = z.minZoomRatio..z.maxZoomRatio,
                                modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.camera_zoom, String.format(Locale.US, "%.1f", zoom)), color = Color.White)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val now = axis ?: return@OutlinedButton
                        val first = firstAim
                        if (first == null) {
                            firstAim = now
                        } else {
                            // Same point, so the true angles are equal: e1 + tilt(r1) = e2 + tilt(r2). A portrait
                            // pair (upright, upside down) gives the upright tilt, a landscape pair the sideways one.
                            val c = cos(Math.toRadians(first.rollDeg)) - cos(Math.toRadians(now.rollDeg))
                            val sn = sin(Math.toRadians(first.rollDeg)) - sin(Math.toRadians(now.rollDeg))
                            when {
                                abs(c) >= 1.5 -> {
                                    tiltUpright = (now.elevationDeg - first.elevationDeg - tiltSideways * sn) / c
                                    prefs.edit().putFloat(KEY_TILT_UPRIGHT, tiltUpright.toFloat()).apply()
                                }
                                abs(sn) >= 1.5 -> {
                                    tiltSideways = (now.elevationDeg - first.elevationDeg - tiltUpright * c) / sn
                                    prefs.edit().putFloat(KEY_TILT_SIDEWAYS, tiltSideways.toFloat()).apply()
                                }
                                else -> Toast.makeText(context, context.getString(R.string.camera_calibrate_failed), Toast.LENGTH_LONG).show()
                            }
                            firstAim = null
                        }
                    }) {
                        Text(stringResource(if (firstAim == null) R.string.action_calibrate else R.string.camera_calibrate_flip), color = Color.White)
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = Color.White) }
                    Button(enabled = angle != null, onClick = { angle?.let(onAngle) }) {
                        Text(stringResource(R.string.action_use_angle, angle?.let { String.format(Locale.US, "%.1f", it) } ?: "—"))
                    }
                }
                Text(
                    stringResource(R.string.camera_angle_note) + " " +
                        stringResource(
                            R.string.camera_angle_calibration,
                            String.format(Locale.US, "%+.2f", tiltUpright), String.format(Locale.US, "%+.2f", tiltSideways),
                        ),
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** The back camera's axis from the gravity sensor (the accelerometer where there is none), smoothed over about half a second. */
@Composable
private fun rememberCameraAxis(): State<CameraAxis?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<CameraAxis?>(null) }
    DisposableEffect(Unit) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val g = DoubleArray(3)
        var first = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                for (k in 0..2) g[k] = if (first) event.values[k].toDouble() else g[k] + 0.1 * (event.values[k] - g[k])
                first = false
                val n = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
                if (n < 1e-3) return
                // The reading points up; the back camera looks along the phone's −z axis.
                state.value = CameraAxis(
                    elevationDeg = Math.toDegrees(asin((-g[2] / n).coerceIn(-1.0, 1.0))),
                    rollDeg = Math.toDegrees(atan2(g[0], g[1])),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensors.unregisterListener(listener) }
    }
    return state
}
