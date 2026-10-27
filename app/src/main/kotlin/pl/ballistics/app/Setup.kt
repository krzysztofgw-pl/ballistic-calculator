package pl.ballistics.app

import pl.ballistics.core.Ammunition
import pl.ballistics.core.Atmosphere
import pl.ballistics.core.Rifle
import pl.ballistics.core.ShotConditions
import pl.ballistics.core.Solver
import pl.ballistics.core.SolverOptions
import pl.ballistics.core.TrajectoryPoint
import pl.ballistics.core.Wind
import kotlin.math.abs

/** The parsed solver inputs, kept so a screen can solve the same shot again with other conditions. */
data class Setup(
    val rifle: Rifle,
    val ammo: Ammunition,
    val conditions: ShotConditions,
    val zeroAtmosphere: Atmosphere,
    val options: SolverOptions,
) {
    /**
     * The point at exactly [rangeM]; [zeroElevationRad] from an earlier solution skips zeroing.
     * [winds] replace those of [conditions].
     */
    fun pointAt(
        rangeM: Double,
        zeroElevationRad: Double,
        winds: List<Wind>? = null,
        conditions: ShotConditions = this.conditions,
        ammo: Ammunition = this.ammo,
    ): TrajectoryPoint? =
        Solver(options.copy(maxRangeM = rangeM, stepM = rangeM))
            .solve(rifle, ammo, conditions.copy(winds = winds ?: conditions.winds), zeroAtmosphere, zeroElevationRad)
            .points.lastOrNull()?.takeIf { abs(it.rangeM - rangeM) < 1e-6 }
}
