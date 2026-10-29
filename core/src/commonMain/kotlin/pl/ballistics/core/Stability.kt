package pl.ballistics.core

import kotlin.math.cbrt
import kotlin.math.pow

/** Gyroscopic stability and spin-related empirical corrections (Miller, Litz). */
object Stability {
    /**
     * Miller twist rule gyroscopic stability factor Sg, corrected for velocity
     * and air density. Sg < 1 is unstable, 1–1.4 marginal, ≥ 1.5 recommended.
     */
    fun millerSg(
        massKg: Double,
        diameterM: Double,
        lengthM: Double,
        twistM: Double,
        velocityMps: Double,
        temperatureC: Double,
        pressurePa: Double,
    ): Double {
        val m = massKg / Units.GRAIN
        val d = diameterM / Units.INCH
        val l = lengthM / diameterM
        val t = twistM / diameterM
        val sg = 30.0 * m / (t * t * d.pow(3) * l * (1 + l * l))
        val fv = cbrt(velocityMps / Units.FPS / 2800.0)
        val fa = (Units.celsiusToFahrenheit(temperatureC) + 460.0) / (59.0 + 460.0) *
            (ICAO_INHG / (pressurePa / Units.INHG))
        return sg * fv * fa
    }

    /** Litz spin drift in metres after [timeS] of flight (always to the twist side). */
    fun litzSpinDriftM(sg: Double, timeS: Double): Double =
        1.25 * (sg + 1.2) * timeS.pow(1.83) * Units.INCH

    /**
     * Litz aerodynamic jump: vertical deflection in radians per m/s of crosswind
     * (positive crosswind blows left to right). For right-hand twist a wind from
     * the left lifts the impact; the sign flips for left-hand twist.
     */
    fun litzAeroJumpRadPerMps(sg: Double, lengthCalibers: Double, rightHandTwist: Boolean): Double {
        val moaPerMph = 0.01 * sg - 0.0024 * lengthCalibers + 0.032
        val sign = if (rightHandTwist) 1.0 else -1.0
        return sign * moaPerMph * Units.MOA / Units.MPH
    }

    private const val ICAO_INHG = 29.92
}
