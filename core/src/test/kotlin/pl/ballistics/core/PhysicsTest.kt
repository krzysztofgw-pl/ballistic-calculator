package pl.ballistics.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhysicsTest {
    private val projectile = Projectile(
        BallisticCoefficient(DragFunction.G7, 0.243), 175 * Units.GRAIN, 0.308 * Units.INCH, 1.24 * Units.INCH,
    )
    private val ammo = Ammunition(projectile, 790.0)
    private val rifle = Rifle(0.045, 100.0, twistM = 10 * Units.INCH)
    private val plain = SolverOptions(maxRangeM = 1000.0, spinDrift = false, aerodynamicJump = false)

    private fun at1000(conditions: ShotConditions, options: SolverOptions = plain, r: Rifle = rifle) =
        Solver(options).solve(r, ammo, conditions).points.last()

    @Test
    fun icaoSeaLevel() {
        val a = Atmosphere.icao()
        assertEquals(1.225, a.density, 0.001)
        assertEquals(340.3, a.speedOfSound, 0.2)
        assertEquals(0.0, a.densityAltitudeM, 1.0)
        assertEquals(1000.0, Atmosphere.icao(1000.0).densityAltitudeM, 1.0)
    }

    @Test
    fun humidAirIsLighter() {
        val dry = Atmosphere(temperatureC = 30.0)
        val humid = dry.copy(relativeHumidity = 1.0)
        assertTrue(humid.density < dry.density)
        assertTrue(humid.densityAltitudeM > dry.densityAltitudeM)
    }

    @Test
    fun qnhReducesToStationPressure() {
        val a = Atmosphere.fromSeaLevelPressure(101_325.0, 1000.0, 8.5)
        assertEquals(Atmosphere.icao(1000.0).stationPressurePa, a.stationPressurePa, 1.0)
    }

    @Test
    fun coriolisDeflectsRightInNorthernHemisphere() {
        val north = at1000(ShotConditions(latitudeRad = 52 * Units.DEG, azimuthRad = 0.0))
        assertTrue(north.windageM > 0.02 && north.windageM < 0.2, "windage ${north.windageM}")
        val south = at1000(ShotConditions(latitudeRad = -52 * Units.DEG, azimuthRad = 0.0))
        assertTrue(south.windageM < 0)
    }

    @Test
    fun eotvosRaisesEastwardShots() {
        val base = at1000(ShotConditions())
        val east = at1000(ShotConditions(latitudeRad = 0.0, azimuthRad = PI / 2))
        val west = at1000(ShotConditions(latitudeRad = 0.0, azimuthRad = 3 * PI / 2))
        assertTrue(east.dropM > west.dropM)
        assertTrue(abs((east.dropM - west.dropM) / 2) < 0.2)
        assertTrue(abs(base.windageM) < 1e-9)
    }

    @Test
    fun spinDriftFollowsTwist() {
        val options = plain.copy(spinDrift = true)
        val right = at1000(ShotConditions(), options)
        val left = at1000(ShotConditions(), options, rifle.copy(rightHandTwist = false))
        assertTrue(right.spinDriftM > 0.1 && right.spinDriftM < 0.6, "drift ${right.spinDriftM}")
        assertEquals(-right.spinDriftM, left.spinDriftM, 1e-9)
    }

    @Test
    fun aerodynamicJumpLiftsShotWithWindFromLeft() {
        val options = plain.copy(aerodynamicJump = true)
        val fromLeft = Solver(options).solve(rifle, ammo, ShotConditions(winds = listOf(Wind(5.0, 1.5 * PI))))
        val fromRight = Solver(options).solve(rifle, ammo, ShotConditions(winds = listOf(Wind(5.0, 0.5 * PI))))
        assertTrue(fromLeft.aerodynamicJumpRad > 0)
        assertTrue(fromLeft.points.last().dropM > fromRight.points.last().dropM)
    }

    @Test
    fun millerStabilityForCommonLoad() {
        // .308 175 gr SMK, 1.24", 1:10" twist, 2600 fps, standard conditions.
        // By hand: 30·175 / (32.47² · 0.308³ · 4.026 · 17.21) = 2.46, × (2600/2800)^⅓ = 2.40.
        val sg = Stability.millerSg(175 * Units.GRAIN, 0.308 * Units.INCH, 1.24 * Units.INCH,
            10 * Units.INCH, 2600 * Units.FPS, 15.0, Atmosphere.ICAO_P0)
        assertEquals(2.40, sg, 0.01)
    }

    @Test
    fun uphillShotDropsLessThanLevel() {
        val level = at1000(ShotConditions())
        val uphill = at1000(ShotConditions(lookAngleRad = 30 * Units.DEG))
        assertTrue(uphill.dropM > level.dropM)
    }

    @Test
    fun cantMovesImpactSideways() {
        val canted = at1000(ShotConditions(cantRad = 5 * Units.DEG))
        assertTrue(canted.windageM > 0.05, "windage ${canted.windageM}")
    }

    @Test
    fun hotPowderShootsFlatter() {
        val sensitive = ammo.copy(powderSensitivityMpsPerC = 1.0)
        val cold = Solver(plain).solve(rifle, sensitive, ShotConditions(powderTempC = -10.0))
        val hot = Solver(plain).solve(rifle, sensitive, ShotConditions(powderTempC = 35.0))
        assertTrue(hot.points.last().dropM > cold.points.last().dropM)
    }

    @Test
    fun customCurveEqualsEquivalentBc() {
        // A custom curve equal to G7 scaled by the form factor must reproduce the BC result.
        val bc = 0.243
        val sd = projectile.massKg / (projectile.diameterM * projectile.diameterM) / Units.LB_PER_IN2
        val machs = DoubleArray(60) { it * 0.1 }
        val cds = DoubleArray(60) { DragFunction.G7.cd(machs[it]) * sd / bc }
        val custom = ammo.copy(projectile = projectile.copy(
            drag = CustomDragCurve(machs, cds, projectile.massKg, projectile.diameterM)))
        val a = Solver(plain).solve(rifle, ammo, ShotConditions()).points.last()
        val b = Solver(plain).solve(rifle, custom, ShotConditions()).points.last()
        assertEquals(a.dropM, b.dropM, 0.02)
    }
}

class ScopeMountTest {
    @Test
    fun caliperMethodAddsRadii() {
        // 10 mm gap, 18 mm bolt, 30 mm tube → 10 + 9 + 15 = 34 mm.
        kotlin.test.assertEquals(0.034, ScopeMount.measuredSightHeightM(0.010, 0.018, 0.030), 1e-9)
    }

    @Test
    fun largeObjectiveNeedsHighRings() {
        // 56 mm objective → ~64 mm bell → 32 mm radius + 3 mm clearance.
        kotlin.test.assertEquals(0.035, ScopeMount.minRingAxisHeightM(56.0), 1e-9)
    }
}
