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

    /** The same id as a dashed UUID, the form Garmin's samples use; messages are matched on either. */
    private const val APP_UUID = "d8c4bc4d-63d9-4a6c-b007-1345dc138c56"
    private const val CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"

    sealed interface Result {
        data class Pressure(val hPa: Double, val watch: String) : Result
        data object NoGarminConnect : Result
        data object NoWatch : Result
        data object AppNotInstalled : Result

        /** What Garmin Connect reported on the way, to tell where it stopped. */
        data class NoAnswer(val details: String) : Result
    }

    fun garminConnectInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(CONNECT_PACKAGE, 0) }.isSuccess

    /**
     * Opens the watch app (the watch asks to confirm) and waits for its reading. [onPrompt] is called
     * when the watch shows that prompt, so the user can be told to accept it. The watch shows its reading
     * in [unit] ("hPa", "mmHg" or "inHg"), the app's pressure unit; the reading itself travels in Pa.
     */
    suspend fun read(context: Context, unit: String = "hPa", timeoutMs: Long = 45_000, onPrompt: () -> Unit = {}): Result {
        if (!garminConnectInstalled(context)) return Result.NoGarminConnect
        val app = context.applicationContext
        val ciq = ConnectIQ.getInstance(app, ConnectIQ.IQConnectType.WIRELESS)
        if (withTimeoutOrNull(10_000) { initialize(ciq, app) } != true) return Result.NoGarminConnect
        val iqApp = IQApp(APP_ID)
        val apps = listOf(iqApp, IQApp(APP_UUID))
        val devices = mutableListOf<IQDevice>()
        try {
            devices += runCatching { ciq.connectedDevices.orEmpty() }.getOrDefault(emptyList())
            if (devices.isEmpty()) return Result.NoWatch

            val answer = CompletableDeferred<Result.Pressure>()
            var notInstalled = 0
            val trace = linkedSetOf<String>()
            for (device in devices) {
                runCatching {
                    for (each in apps) ciq.registerForAppEvents(device, each) { d, _, message, status ->
                        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                            trace += "received $status"
                            return@registerForAppEvents
                        }
                        if (pascals(message) == null) trace += "received $message"
                        pascals(message)?.let { answer.complete(Result.Pressure(it / 100.0, d.friendlyName.orEmpty())) }
                    }
                    ciq.openApplication(device, iqApp) { _, _, status ->
                        trace += "open $status"
                        when (status) {
                            ConnectIQ.IQOpenApplicationStatus.PROMPT_SHOWN_ON_DEVICE -> onPrompt()
                            ConnectIQ.IQOpenApplicationStatus.APP_IS_NOT_INSTALLED -> notInstalled++
                            else -> Unit
                        }
                    }
                }.onFailure { trace += it.javaClass.simpleName }
            }
            // The app may still be starting when the first request arrives, and a request it misses is
            // lost, so ask again every few seconds until a reading comes.
            return withTimeoutOrNull(timeoutMs) {
                while (!answer.isCompleted) {
                    for (device in devices) {
                        runCatching {
                            for (each in apps) {
                                ciq.sendMessage(device, each, mapOf("get" to 1, "unit" to unit)) { _, a, status ->
                                    trace += "request ${if (a?.applicationId == APP_UUID) "uuid " else ""}$status"
                                }
                            }
                        }.onFailure { trace += it.javaClass.simpleName }
                    }
                    withTimeoutOrNull(3_000) { answer.await() }
                }
                answer.await()
            } ?: if (notInstalled == devices.size) Result.AppNotInstalled else Result.NoAnswer(trace.joinToString())
        } finally {
            for (device in devices) apps.forEach { runCatching { ciq.unregisterForApplicationEvents(device, it) } }
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
