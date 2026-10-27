package pl.ballistics.app

import pl.ballistics.core.TrajectoryPoint
import pl.ballistics.core.Wind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shot at one distance under the current conditions, and how its windage correction
 * changes with wind speed and direction. Corrections are in radians, positive to the right.
 */
data class TargetAnalysis(
    val point: TrajectoryPoint,
    /** Wind direction (degrees from the line of fire) → correction by wind speed. */
    val bySpeed: Map<Double, List<Pair<Double, Double>>>,
    /** Wind speed → correction by wind direction in degrees. */
    val byDirection: Map<Double, List<Pair<Double, Double>>>,
) {
    companion object {
        val SPEEDS = (0..10).map { it.toDouble() }
        /** 9, 10:30, 12, 1:30 and 3 o'clock. */
        val DIRECTIONS = listOf(270.0, 315.0, 0.0, 45.0, 90.0)
        val DIRECTION_SPEEDS = listOf(2.0, 4.0, 6.0, 8.0)
        val SWEEP = (0..24).map { it * 15.0 }

        /** Null when the bullet does not reach [rangeM]. */
        fun of(setup: Setup, zeroElevationRad: Double, rangeM: Double): TargetAnalysis? {
            val point = setup.pointAt(rangeM, zeroElevationRad) ?: return null
            val calm = setup.pointAt(rangeM, zeroElevationRad, emptyList())?.let { -it.windageRad } ?: return null
            fun correction(speed: Double, fromDeg: Double): Double =
                if (speed == 0.0) calm
                else setup.pointAt(rangeM, zeroElevationRad, listOf(Wind.fromClock(speed, fromDeg / 30)))?.let { -it.windageRad } ?: Double.NaN
            return TargetAnalysis(
                point = point,
                bySpeed = DIRECTIONS.associateWith { d -> SPEEDS.map { it to correction(it, d) }.filterNot { it.second.isNaN() } },
                byDirection = DIRECTION_SPEEDS.associateWith { s -> SWEEP.map { it to correction(s, it) }.filterNot { it.second.isNaN() } },
            )
        }
    }
}

/** The hold for a moving target: the impact at the distance it will be at, and how far to lead it. */
data class Lead(val point: TrajectoryPoint, val leadM: Double) {
    /** Lead angle, positive when the target moves right. */
    val leadRad: Double get() = atan2(leadM, point.rangeM)
}

/**
 * A target at [rangeM] moving at [speedMps] in [directionDeg] from the line of fire (0 = away, 90 = to the right).
 * The bullet meets it where it will be after the time of flight, so the distance is iterated with that time.
 */
fun Setup.lead(rangeM: Double, zeroElevationRad: Double, speedMps: Double, directionDeg: Double): Lead? {
    val radial = speedMps * cos(Math.toRadians(directionDeg))
    val crossing = speedMps * sin(Math.toRadians(directionDeg))
    var p = pointAt(rangeM, zeroElevationRad) ?: return null
    repeat(3) {
        if (abs(radial) < 1e-3) return@repeat
        p = pointAt(rangeM + radial * p.timeS, zeroElevationRad) ?: return null
    }
    return Lead(p, crossing * p.timeS)
}
