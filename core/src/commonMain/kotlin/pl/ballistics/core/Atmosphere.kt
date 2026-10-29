package pl.ballistics.core

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Air properties at one point of the trajectory. */
data class AirState(
    val densityKgM3: Double,
    val speedOfSoundMps: Double,
    val temperatureK: Double,
    val pressurePa: Double,
)

/**
 * Atmosphere at the firing point, extended upward and downward with the ICAO
 * temperature lapse rate. Density is computed for humid air (Buck vapour
 * pressure, ideal mixture of dry air and water vapour), and the speed of sound
 * uses the virtual temperature.
 *
 * @param altitudeM firing point altitude above mean sea level.
 * @param stationPressurePa absolute pressure measured at the firing point (not QNH).
 * @param relativeHumidity 0.0 to 1.0.
 */
data class Atmosphere(
    val altitudeM: Double = 0.0,
    val stationPressurePa: Double = ICAO_P0,
    val temperatureC: Double = 15.0,
    val relativeHumidity: Double = 0.0,
) {
    init {
        require(stationPressurePa > 0) { "Pressure must be positive" }
        require(relativeHumidity in 0.0..1.0) { "Relative humidity must be within 0..1" }
    }

    val temperatureK: Double get() = temperatureC + C_TO_K

    /** Air state at [heightM] above (or below, if negative) the firing point. */
    fun stateAt(heightM: Double = 0.0): AirState {
        val t0 = temperatureK
        val t = (t0 - LAPSE * heightM).coerceAtLeast(150.0)
        val p = stationPressurePa * (t / t0).pow(BARO_EXPONENT)
        return airState(t, p, relativeHumidity)
    }

    val density: Double get() = stateAt().densityKgM3
    val speedOfSound: Double get() = stateAt().speedOfSoundMps

    /** Altitude in the ICAO standard atmosphere that has the same air density. */
    val densityAltitudeM: Double
        get() = ICAO_T0 / LAPSE * (1.0 - (density / ICAO_RHO0).pow(1.0 / (BARO_EXPONENT - 1.0)))

    companion object {
        const val ICAO_P0 = 101_325.0
        const val ICAO_T0 = 288.15
        const val ICAO_RHO0 = 1.225
        const val LAPSE = 0.0065                       // K/m
        const val C_TO_K = 273.15
        private const val R = 8.314462618              // J/(mol·K)
        private const val M_DRY = 0.0289652            // kg/mol
        private const val M_VAPOR = 0.018016           // kg/mol
        private const val G0 = 9.80665
        private const val R_DRY = R / M_DRY
        private const val GAMMA = 1.4
        const val BARO_EXPONENT = G0 * M_DRY / (R * LAPSE)   // ≈ 5.2559

        /** ICAO standard atmosphere at the given altitude. */
        fun icao(altitudeM: Double = 0.0, relativeHumidity: Double = 0.0): Atmosphere {
            val t = ICAO_T0 - LAPSE * altitudeM
            return Atmosphere(
                altitudeM = altitudeM,
                stationPressurePa = ICAO_P0 * (t / ICAO_T0).pow(BARO_EXPONENT),
                temperatureC = t - C_TO_K,
                relativeHumidity = relativeHumidity,
            )
        }

        /** Builds the atmosphere from a sea-level-corrected pressure (QNH, as weather services report it). */
        fun fromSeaLevelPressure(
            qnhPa: Double,
            altitudeM: Double,
            temperatureC: Double,
            relativeHumidity: Double = 0.0,
        ) = Atmosphere(
            altitudeM = altitudeM,
            stationPressurePa = qnhPa * (1.0 - LAPSE * altitudeM / ICAO_T0).pow(BARO_EXPONENT),
            temperatureC = temperatureC,
            relativeHumidity = relativeHumidity,
        )

        /** Saturation vapour pressure over water, Buck (1996). */
        fun saturationVaporPressurePa(temperatureC: Double): Double =
            611.21 * exp((18.678 - temperatureC / 234.5) * (temperatureC / (257.14 + temperatureC)))

        internal fun airState(temperatureK: Double, pressurePa: Double, rh: Double): AirState {
            val pv = rh * saturationVaporPressurePa(temperatureK - C_TO_K)
            val density = ((pressurePa - pv) * M_DRY + pv * M_VAPOR) / (R * temperatureK)
            val virtualT = temperatureK / (1.0 - (pv / pressurePa) * (1.0 - M_VAPOR / M_DRY))
            return AirState(density, sqrt(GAMMA * R_DRY * virtualT), temperatureK, pressurePa)
        }
    }
}
