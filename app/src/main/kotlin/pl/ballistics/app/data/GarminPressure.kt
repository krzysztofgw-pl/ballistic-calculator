package pl.ballistics.app.data

import android.content.Context
import android.content.pm.PackageManager
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Station pressure from a Garmin watch's barometer, through the Station Pressure Connect IQ app
 * (source in `garmin/`) and Garmin Connect on the phone.
 */
object GarminPressure {
    /** The watch app's id from `garmin/manifest.xml`. */
    private const val APP_ID = "d8c4bc4d63d94a6cb0071345dc138c56"
    private const val CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"

    sealed interface Result {
        data class Pressure(val hPa: Double, val watch: String) : Result
        data object NoGarminConnect : Result
        data object NoWatch : Result
        data object AppNotInstalled : Result
        data object NoAnswer : Result
    }

    fun garminConnectInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(CONNECT_PACKAGE, 0) }.isSuccess

    /**
     * Opens the watch app (the watch asks to confirm) and waits for its reading. [onPrompt] is called
     * when the watch shows that prompt, so the user can be told to accept it.
     */
    suspend fun read(context: Context, timeoutMs: Long = 45_000, onPrompt: () -> Unit = {}): Result {
        if (!garminConnectInstalled(context)) return Result.NoGarminConnect
        val app = context.applicationContext
        val ciq = ConnectIQ.getInstance(app, ConnectIQ.IQConnectType.WIRELESS)
        if (withTimeoutOrNull(10_000) { initialize(ciq, app) } != true) return Result.NoGarminConnect
        val iqApp = IQApp(APP_ID)
        val devices = mutableListOf<IQDevice>()
        try {
            devices += runCatching { ciq.connectedDevices.orEmpty() }.getOrDefault(emptyList())
            if (devices.isEmpty()) return Result.NoWatch

            val answer = CompletableDeferred<Result.Pressure>()
            var notInstalled = 0
            for (device in devices) {
                runCatching {
                    ciq.registerForAppEvents(device, iqApp) { d, _, message, status ->
                        if (status != ConnectIQ.IQMessageStatus.SUCCESS) return@registerForAppEvents
                        pascals(message)?.let { answer.complete(Result.Pressure(it / 100.0, d.friendlyName.orEmpty())) }
                    }
                    ciq.openApplication(device, iqApp) { _, _, status ->
                        when (status) {
                            ConnectIQ.IQOpenApplicationStatus.PROMPT_SHOWN_ON_DEVICE -> onPrompt()
                            ConnectIQ.IQOpenApplicationStatus.APP_IS_NOT_INSTALLED -> notInstalled++
                            else -> Unit
                        }
                    }
                    // A running app answers this; one that is starting sends its reading by itself.
                    ciq.sendMessage(device, iqApp, mapOf("get" to 1)) { _, _, _ -> }
                }
            }
            return withTimeoutOrNull(timeoutMs) { answer.await() }
                ?: if (notInstalled == devices.size) Result.AppNotInstalled else Result.NoAnswer
        } finally {
            devices.forEach { runCatching { ciq.unregisterForApplicationEvents(it, iqApp) } }
            runCatching { ciq.shutdown(app) }
        }
    }

    private suspend fun initialize(ciq: ConnectIQ, context: Context): Boolean =
        suspendCancellableCoroutine { cont ->
            ciq.initialize(context, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus?) {
                    if (cont.isActive) cont.resume(false)
                }

                override fun onSdkShutDown() = Unit
            })
        }

    /** The watch sends `{"pa": <float>}`; anything outside a plausible range is ignored. */
    private fun pascals(message: List<Any?>?): Double? =
        message.orEmpty().firstNotNullOfOrNull { (it as? Map<*, *>)?.get("pa") as? Number }
            ?.toDouble()
            ?.takeIf { it in 30_000.0..110_000.0 }
}
