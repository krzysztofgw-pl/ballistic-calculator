package pl.ballistics.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** One forecast hour; wind at 10 m above ground, direction the wind blows from (true). */
data class ForecastHour(
    val timeSec: Long,
    val speedMps: Double,
    val fromDeg: Double,
    val gustMps: Double?,
    val temperatureC: Double?,
    val humidityPct: Double?,
    val pressureMslHpa: Double?,
)

data class WindSummary(
    /** Mean of the hourly speeds: what a crosswind correction needs, whatever the direction does. */
    val speedMps: Double,
    /** Direction of the mean wind vector, so a 350°/10° pair averages to 0°, not 180°. */
    val fromDeg: Double,
    val minSpeedMps: Double,
    val maxSpeedMps: Double,
    val gustMps: Double?,
    /** Length of the mean vector over the mean speed: 1 = steady direction, near 0 = swirling. */
    val steadiness: Double,
    val temperatureC: Double?,
    val humidityPct: Double?,
    val pressureMslHpa: Double?,
)

/** Forecast wind is for 10 m; near the ground it is slower. Power-law factors for a ~2 m line of fire. */
enum class WindHeight(val factor: Double) {
    FORECAST(1.0),
    OPEN((2.0 / 10).pow(1.0 / 7)),
    ROUGH((2.0 / 10).pow(0.25)),
}

/** Hourly forecasts from Open-Meteo (free for non-commercial use, CC BY 4.0, no API key). */
object WindForecast {
    private const val URL_FORMAT = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
        "&hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m,temperature_2m,relative_humidity_2m,pressure_msl" +
        "&wind_speed_unit=ms&timeformat=unixtime&forecast_days=2"

    /** The hourly forecast and the terrain height (m above sea level) Open-Meteo reports for the point. */
    data class Result(val hours: List<ForecastHour>, val elevationM: Double?)

    suspend fun fetch(latitude: Double, longitude: Double): Result = withContext(Dispatchers.IO) {
        val connection = URL(String.format(Locale.US, URL_FORMAT, latitude, longitude)).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) throw java.io.IOException("HTTP ${connection.responseCode}")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            Result(parse(text), elevation(text))
        } finally {
            connection.disconnect()
        }
    }

    fun elevation(text: String): Double? =
        runCatching { Json.parseToJsonElement(text).jsonObject["elevation"]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    fun parse(text: String): List<ForecastHour> {
        val hourly = Json.parseToJsonElement(text).jsonObject["hourly"]?.jsonObject ?: return emptyList()
        fun column(name: String): List<Double?> =
            (hourly[name] as? JsonArray)?.map { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        val times = hourly["time"]?.jsonArray?.map { it.jsonPrimitive.longOrNull } ?: return emptyList()
        val speed = column("wind_speed_10m")
        val direction = column("wind_direction_10m")
        val gust = column("wind_gusts_10m")
        val temperature = column("temperature_2m")
        val humidity = column("relative_humidity_2m")
        val pressure = column("pressure_msl")
        return times.indices.mapNotNull { i ->
            val t = times[i] ?: return@mapNotNull null
            val s = speed.getOrNull(i) ?: return@mapNotNull null
            val d = direction.getOrNull(i) ?: return@mapNotNull null
            ForecastHour(t, s, d, gust.getOrNull(i), temperature.getOrNull(i), humidity.getOrNull(i), pressure.getOrNull(i))
        }
    }

    /** Hours from the one containing [fromSec] up to and including the one containing [toSec]. */
    fun window(hours: List<ForecastHour>, fromSec: Long, toSec: Long): List<ForecastHour> {
        val start = fromSec - fromSec.mod(3600L)
        return hours.filter { it.timeSec in start..toSec }
    }

    fun summarize(hours: List<ForecastHour>): WindSummary? {
        if (hours.isEmpty()) return null
        // Vector of where the wind blows to: east and north components.
        var east = 0.0
        var north = 0.0
        for (h in hours) {
            val a = Math.toRadians(h.fromDeg)
            east -= h.speedMps * sin(a)
            north -= h.speedMps * cos(a)
        }
        east /= hours.size
        north /= hours.size
        val mean = hours.sumOf { it.speedMps } / hours.size
        val vector = hypot(east, north)
        return WindSummary(
            speedMps = mean,
            fromDeg = Math.toDegrees(atan2(-east, -north)).mod(360.0),
            minSpeedMps = hours.minOf { it.speedMps },
            maxSpeedMps = hours.maxOf { it.speedMps },
            gustMps = hours.mapNotNull { it.gustMps }.maxOrNull(),
            steadiness = if (mean > 0) vector / mean else 1.0,
            temperatureC = hours.mapNotNull { it.temperatureC }.averageOrNull(),
            humidityPct = hours.mapNotNull { it.humidityPct }.averageOrNull(),
            pressureMslHpa = hours.mapNotNull { it.pressureMslHpa }.averageOrNull(),
        )
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
