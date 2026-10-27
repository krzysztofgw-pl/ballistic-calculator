package pl.ballistics.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One row of the firing solution. Range is measured along the line of sight;
 * drop and windage are offsets from the line of sight at that range.
 */
data class TrajectoryPoint(
    val rangeM: Double,
    val timeS: Double,
    val velocityMps: Double,
    val mach: Double,
    val energyJ: Double,
    /** Positive above the line of sight. */
    val dropM: Double,
    /** Positive right of the line of sight. Includes wind, spin drift and Coriolis. */
    val windageM: Double,
    val spinDriftM: Double,
) {
    /** Angular offset of the impact; dial or hold the opposite sign to correct. */
    val dropRad: Double get() = if (rangeM > 0) atan2(dropM, rangeM) else 0.0
    val windageRad: Double get() = if (rangeM > 0) atan2(windageM, rangeM) else 0.0
}

data class Solution(
    val points: List<TrajectoryPoint>,
    /** Bore elevation relative to the line of sight found by zeroing. */
    val zeroElevationRad: Double,
    val muzzleVelocityMps: Double,
    /** Miller stability factor at the muzzle, if bullet length and twist are known. */
    val stabilityFactor: Double?,
    val aerodynamicJumpRad: Double,
    val densityAltitudeM: Double,
    val speedOfSoundMps: Double,
)

/**
 * Three-degree-of-freedom point-mass trajectory solver (the model behind the
 * JBM, Applied Ballistics and Hornady 3DOF calculators), integrated with
 * fourth-order Runge–Kutta in an Earth-fixed frame: x downrange (horizontal),
 * y up, z right.
 *
 * Forces: drag against the air-relative velocity (wind included), gravity
 * (normal gravity by latitude and height when latitude is given), and the full
 * Coriolis acceleration −2Ω×v, which contains both horizontal Coriolis drift
 * and the vertical Eötvös effect. Spin drift and aerodynamic jump are added
 * from Litz's empirical fits, the standard choice when the bullet's
 * aerodynamic coefficients for a 6DOF/modified point-mass model are unknown.
 */
class Solver(private val options: SolverOptions = SolverOptions()) {

    /** Bore elevation above the line of sight that puts the impact on the zero point. */
    fun zero(rifle: Rifle, ammo: Ammunition, zeroAtmosphere: Atmosphere = Atmosphere.icao()): Double {
        val conditions = ShotConditions(atmosphere = zeroAtmosphere)
        val mv = ammo.muzzleVelocityAt(zeroAtmosphere.temperatureC)
        fun miss(elevation: Double): Double {
            val flight = Flight(rifle, ammo, conditions, mv, elevation, 0.0, coriolis = false)
            return flight.heightAt(rifle.zeroRangeM) - rifle.zeroOffsetM
        }
        // Secant iteration: the miss is almost linear in elevation.
        var e0 = 0.0
        var f0 = miss(e0)
        var e1 = (rifle.sightHeightM + rifle.zeroOffsetM) / rifle.zeroRangeM + 1e-3
        var f1 = miss(e1)
        repeat(30) {
            if (abs(f1) < 1e-7) return e1
            val e2 = e1 - f1 * (e1 - e0) / (f1 - f0)
            e0 = e1; f0 = f1
            e1 = e2; f1 = miss(e1)
        }
        if (abs(f1) < 1e-4) return e1
        throw IllegalArgumentException("Zero range ${rifle.zeroRangeM} m is not reachable")
    }

    fun solve(
        rifle: Rifle,
        ammo: Ammunition,
        conditions: ShotConditions,
        zeroAtmosphere: Atmosphere = Atmosphere.icao(),
    ): Solution {
        val elevation = zero(rifle, ammo, zeroAtmosphere)
        val air = conditions.atmosphere.stateAt()
        val mv = ammo.muzzleVelocityAt(conditions.powderTempC ?: conditions.atmosphere.temperatureC)
        val p = ammo.projectile

        val sg = if (p.lengthM != null && rifle.twistM != null) {
            Stability.millerSg(p.massKg, p.diameterM, p.lengthM, rifle.twistM, mv,
                air.temperatureK - Atmosphere.C_TO_K, air.pressurePa)
        } else null

        val jump = if (options.aerodynamicJump && sg != null && p.lengthM != null) {
            val crosswind = conditions.winds.firstOrNull()?.vector?.z ?: 0.0
            Stability.litzAeroJumpRadPerMps(sg, p.lengthM / p.diameterM, rifle.rightHandTwist) * crosswind
        } else 0.0

        val flight = Flight(rifle, ammo, conditions, mv, elevation, jump, options.coriolis)
        val driftSign = if (rifle.rightHandTwist) 1.0 else -1.0
        val points = flight.record(options.stepM, options.maxRangeM).map { pt ->
            if (options.spinDrift && sg != null) {
                val drift = driftSign * Stability.litzSpinDriftM(sg, pt.timeS)
                pt.copy(windageM = pt.windageM + drift, spinDriftM = drift)
            } else pt
        }
        return Solution(
            points = points,
            zeroElevationRad = elevation,
            muzzleVelocityMps = mv,
            stabilityFactor = sg,
            aerodynamicJumpRad = jump,
            densityAltitudeM = conditions.atmosphere.densityAltitudeM,
            speedOfSoundMps = air.speedOfSoundMps,
        )
    }

    /** A single integrated flight with fixed launch conditions. */
    private inner class Flight(
        rifle: Rifle,
        ammo: Ammunition,
        private val conditions: ShotConditions,
        muzzleVelocity: Double,
        elevationRad: Double,
        jumpRad: Double,
        coriolis: Boolean,
    ) {
        private val mass = ammo.projectile.massKg
        private val drag = ammo.projectile.drag
        private val atmosphere = conditions.atmosphere

        // Line of sight and its upright (uncanted) normal and right vectors.
        private val los = Vec3(cos(conditions.lookAngleRad), sin(conditions.lookAngleRad), 0.0)
        private val up = Vec3(-sin(conditions.lookAngleRad), cos(conditions.lookAngleRad), 0.0)
        private val right = Vec3(0.0, 0.0, 1.0)

        private val start: Vec3
        private val launch: Vec3
        private val omega: Vec3
        private val gravity: Double

        init {
            // The scope sits at the origin; cant rotates the rifle about the line of sight.
            val rifleUp = up * cos(conditions.cantRad) + right * sin(conditions.cantRad)
            start = rifleUp * -rifle.sightHeightM
            val bore = (los * cos(elevationRad) + rifleUp * sin(elevationRad) + up * jumpRad).normalized()
            launch = bore * muzzleVelocity

            val lat = conditions.latitudeRad
            omega = if (coriolis && lat != null) {
                val a = conditions.azimuthRad
                Vec3(cos(lat) * cos(a), sin(lat), -cos(lat) * sin(a)) * EARTH_RATE
            } else Vec3.ZERO
            gravity = if (lat != null) normalGravity(lat, atmosphere.altitudeM) else STANDARD_GRAVITY
        }

        private fun windAt(x: Double): Vec3 {
            for (w in conditions.winds) if (x <= w.untilRangeM) return w.vector
            return conditions.winds.lastOrNull()?.vector ?: Vec3.ZERO
        }

        private fun acceleration(p: Vec3, v: Vec3): Vec3 {
            val air = atmosphere.stateAt(p.y)
            val rel = v - windAt(p.x)
            val speed = rel.magnitude
            val k = drag.dragFactor(speed / air.speedOfSoundMps, speed)
            val dragAcc = rel * (-k * air.densityKgM3 * speed)
            val g = gravity - FREE_AIR_GRADIENT * p.y
            return dragAcc + Vec3(0.0, -g, 0.0) - (omega cross v) * 2.0
        }

        /**
         * Integrates until the line-of-sight range passes [maxRangeM], calling
         * [onStep] with each pair of consecutive states.
         */
        private inline fun integrate(maxRangeM: Double, onStep: (State, State) -> Boolean) {
            val dt = options.timeStepS
            var s = State(0.0, start, launch)
            val maxTime = 60.0
            while (s.t < maxTime) {
                val p = s.p
                val v = s.v
                val k1v = acceleration(p, v)
                val k1p = v
                val k2v = acceleration(p + k1p * (dt / 2), v + k1v * (dt / 2))
                val k2p = v + k1v * (dt / 2)
                val k3v = acceleration(p + k2p * (dt / 2), v + k2v * (dt / 2))
                val k3p = v + k2v * (dt / 2)
                val k4v = acceleration(p + k3p * dt, v + k3v * dt)
                val k4p = v + k3v * dt
                val next = State(
                    s.t + dt,
                    p + (k1p + k2p * 2.0 + k3p * 2.0 + k4p) * (dt / 6),
                    v + (k1v + k2v * 2.0 + k3v * 2.0 + k4v) * (dt / 6),
                )
                if (!onStep(s, next)) return
                s = next
                if ((s.p dot los) > maxRangeM) return
                if (s.v.magnitude < options.minVelocityMps) return
                if ((s.p dot up) < -options.maxDropM) return
            }
        }

        fun heightAt(rangeM: Double): Double {
            var result = Double.NaN
            integrate(rangeM) { a, b ->
                val sa = a.p dot los
                val sb = b.p dot los
                if (sb >= rangeM) {
                    val f = (rangeM - sa) / (sb - sa)
                    result = ((a.p + (b.p - a.p) * f) dot up)
                    false
                } else true
            }
            if (result.isNaN()) throw IllegalArgumentException("Zero range $rangeM m is not reachable")
            return result
        }

        fun record(stepM: Double, maxRangeM: Double): List<TrajectoryPoint> {
            val out = ArrayList<TrajectoryPoint>()
            var next = 0.0
            integrate(maxRangeM + 1e-9) { a, b ->
                val sa = a.p dot los
                val sb = b.p dot los
                while (next <= maxRangeM + 1e-9 && sb >= next) {
                    val f = if (sb > sa) ((next - sa) / (sb - sa)).coerceIn(0.0, 1.0) else 0.0
                    out += point(next, a, b, f)
                    next += stepM
                }
                true
            }
            return out
        }

        private fun point(range: Double, a: State, b: State, f: Double): TrajectoryPoint {
            val p = a.p + (b.p - a.p) * f
            val v = a.v + (b.v - a.v) * f
            val t = a.t + (b.t - a.t) * f
            val speed = v.magnitude
            val air = atmosphere.stateAt(p.y)
            return TrajectoryPoint(
                rangeM = range,
                timeS = t,
                velocityMps = speed,
                mach = (v - windAt(p.x)).magnitude / air.speedOfSoundMps,
                energyJ = 0.5 * mass * speed * speed,
                dropM = p dot up,
                windageM = p dot right,
                spinDriftM = 0.0,
            )
        }
    }

    private class State(val t: Double, val p: Vec3, val v: Vec3)

    companion object {
        const val EARTH_RATE = 7.2921159e-5        // rad/s
        const val STANDARD_GRAVITY = 9.80665
        private const val FREE_AIR_GRADIENT = 3.086e-6   // (m/s²)/m

        /** WGS-84 normal gravity (Somigliana) with the free-air height correction. */
        fun normalGravity(latitudeRad: Double, heightM: Double): Double {
            val s2 = sin(latitudeRad).let { it * it }
            val g0 = 9.7803253359 * (1 + 0.00193185265241 * s2) / sqrt(1 - 0.00669437999013 * s2)
            return g0 - FREE_AIR_GRADIENT * heightM
        }
    }
}
