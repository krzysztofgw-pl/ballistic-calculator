package pl.ballistics.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * @param lengthM bullet length; needed for stability, spin drift and aerodynamic jump.
 */
data class Projectile(
    val drag: DragModel,
    val massKg: Double,
    val diameterM: Double,
    val lengthM: Double? = null,
)

/**
 * @param powderSensitivityMpsPerC change of muzzle velocity per °C of powder temperature.
 */
data class Ammunition(
    val projectile: Projectile,
    val muzzleVelocityMps: Double,
    val powderSensitivityMpsPerC: Double = 0.0,
    val powderReferenceTempC: Double = 15.0,
) {
    fun muzzleVelocityAt(powderTempC: Double) =
        muzzleVelocityMps + powderSensitivityMpsPerC * (powderTempC - powderReferenceTempC)
}

/**
 * @param sightHeightM optical axis above bore axis.
 * @param twistM barrel twist length (distance per turn); null when unknown.
 * @param zeroOffsetM point of impact above the aim point at the zero range (usually 0).
 */
data class Rifle(
    val sightHeightM: Double,
    val zeroRangeM: Double,
    val twistM: Double? = null,
    val rightHandTwist: Boolean = true,
    val zeroOffsetM: Double = 0.0,
)

/**
 * Wind blowing over the stretch of the trajectory that ends at [untilRangeM].
 *
 * @param fromRad direction the wind blows FROM, relative to the line of fire,
 *   clockwise: 0 = headwind, π/2 = from the right (3 o'clock), π = tailwind.
 * @param verticalMps upward air movement (updrafts in mountains).
 */
data class Wind(
    val speedMps: Double,
    val fromRad: Double,
    val verticalMps: Double = 0.0,
    val untilRangeM: Double = Double.POSITIVE_INFINITY,
) {
    /** Air velocity in the shooter frame (x downrange, y up, z right). */
    val vector: Vec3 get() = Vec3(-speedMps * cos(fromRad), verticalMps, -speedMps * sin(fromRad))

    companion object {
        /** Clock notation: 12 = from the target, 3 = from the right. */
        fun fromClock(speedMps: Double, hour: Double, untilRangeM: Double = Double.POSITIVE_INFINITY) =
            Wind(speedMps, hour / 12.0 * 2 * Math.PI, untilRangeM = untilRangeM)
    }
}

/**
 * @param lookAngleRad line-of-sight inclination, positive uphill.
 * @param cantRad rifle cant, positive clockwise as seen by the shooter.
 * @param latitudeRad and [azimuthRad] (direction of fire, clockwise from true north)
 *   enable Coriolis and Eötvös effects and latitude-dependent gravity.
 * @param powderTempC defaults to the air temperature.
 */
data class ShotConditions(
    val atmosphere: Atmosphere = Atmosphere.icao(),
    val winds: List<Wind> = emptyList(),
    val lookAngleRad: Double = 0.0,
    val cantRad: Double = 0.0,
    val latitudeRad: Double? = null,
    val azimuthRad: Double = 0.0,
    val powderTempC: Double? = null,
)

data class SolverOptions(
    val maxRangeM: Double = 1000.0,
    val stepM: Double = 100.0,
    val timeStepS: Double = 0.0005,
    val minVelocityMps: Double = 50.0,
    val maxDropM: Double = 1000.0,
    val spinDrift: Boolean = true,
    val aerodynamicJump: Boolean = true,
    val coriolis: Boolean = true,
)
