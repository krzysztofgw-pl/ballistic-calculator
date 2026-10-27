package pl.ballistics.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One-shot readings of the phone's position and heading for the conditions screen. */
object DeviceSensors {

    val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasLocationPermission(context: Context) = locationPermissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** A fresh fix if one comes within 20 s, otherwise the most recent known one; null without permission. */
    @SuppressLint("MissingPermission")
    suspend fun location(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        for (provider in providers) {
            val fix = withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) {
                                if (cont.isActive) cont.resume(it)
                            }
                        } else {
                            val listener = object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    if (cont.isActive) cont.resume(location)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                                override fun onProviderEnabled(provider: String) = Unit
                                override fun onProviderDisabled(provider: String) = Unit
                            }
                            signal.setOnCancelListener { lm.removeUpdates(listener) }
                            @Suppress("DEPRECATION")
                            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                        }
                    } catch (e: SecurityException) {
                        cont.resume(null)
                    } catch (e: IllegalArgumentException) {
                        cont.resume(null)
                    }
                }
            }
            if (fix != null) return fix
        }
        return lastKnown(context)
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * The magnetic heading the phone points along, averaged over half a second: the top edge
     * when the phone lies flat, the back camera when it is held upright. Null without a compass.
     */
    suspend fun magneticHeadingDeg(context: Context): Double? {
        val sm = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return null
        return withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { cont ->
                var sumSin = 0.0
                var sumCos = 0.0
                var count = 0
                val r = FloatArray(9)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        SensorManager.getRotationMatrixFromVector(r, event.values)
                        // Columns of r are the phone's axes in east-north-up coordinates.
                        val flat = abs(r[7]) < abs(r[8])
                        val east = if (flat) r[1] else -r[2]
                        val north = if (flat) r[4] else -r[5]
                        val a = atan2(east.toDouble(), north.toDouble())
                        sumSin += sin(a); sumCos += cos(a); count++
                        if (count >= 25) {
                            sm.unregisterListener(this)
                            if (cont.isActive) cont.resume(Math.toDegrees(atan2(sumSin, sumCos)).mod(360.0))
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
                }
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    /** Magnetic declination (east positive) at [location] now. */
    fun declinationDeg(location: Location): Double =
        GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis(),
        ).declination.toDouble()
}
