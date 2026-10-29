package pl.ballistics.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-checks against py-ballisticcalc 3.1.1 (RK4 engine), an independent
 * implementation of the same point-mass model. ICAO atmosphere, 5 cm sight
 * height, 100 m zero, no spin effects.
 */
class ReferenceTrajectoryTest {

    // range m, drop m, windage m, velocity m/s, time s
    private val g1Reference = doubleArrayOf(
        100.0, -0.00000, 0.00000, 740.806, 0.12990,
        200.0, -0.12914, 0.00000, 684.150, 0.27037,
        300.0, -0.46836, 0.00000, 629.958, 0.42270,
        400.0, -1.05541, 0.00000, 578.357, 0.58839,
        500.0, -1.93651, 0.00000, 529.575, 0.76911,
        600.0, -3.16838, 0.00000, 483.989, 0.96669,
        700.0, -4.82016, 0.00000, 442.101, 1.18299,
        800.0, -6.97508, 0.00000, 404.534, 1.41965,
        900.0, -9.73062, 0.00000, 372.058, 1.67773,
        1000.0, -13.19567, 0.00000, 345.399, 1.95719,
    )

    private val g7WindReference = doubleArrayOf(
        100.0, -0.00000, 0.01804, 788.406, 0.12216,
        200.0, -0.10815, 0.07498, 729.302, 0.25404,
        300.0, -0.40115, 0.17545, 672.815, 0.39680,
        400.0, -0.91136, 0.32471, 619.011, 0.55177,
        500.0, -1.67829, 0.52897, 567.597, 0.72048,
        600.0, -2.75065, 0.79590, 518.268, 0.90486,
        700.0, -4.18954, 1.13506, 470.877, 1.10729,
        800.0, -6.07270, 1.55827, 425.468, 1.33074,
        900.0, -8.50032, 2.07972, 382.403, 1.57875,
        1000.0, -11.60218, 2.71538, 342.622, 1.85532,
    )

    private fun ammo(drag: DragModel, mv: Double) =
        Ammunition(Projectile(drag, 168 * Units.GRAIN, 0.308 * Units.INCH), mv)

    private val rifle = Rifle(sightHeightM = 0.05, zeroRangeM = 100.0)

    private fun check(solution: Solution, reference: DoubleArray) {
        val byRange = solution.points.associateBy { it.rangeM.toInt() }
        for (i in reference.indices step 5) {
            val p = byRange.getValue(reference[i].toInt())
            val drop = reference[i + 1]
            val windage = reference[i + 2]
            // 0.1 MOA or 5 mm, whichever is larger.
            val tol = maxOf(0.005, 0.1 * Units.MOA * p.rangeM)
            assertTrue(abs(p.dropM - drop) < tol, "drop at ${p.rangeM}: ${p.dropM} vs $drop")
            assertTrue(abs(p.windageM - windage) < tol, "windage at ${p.rangeM}: ${p.windageM} vs $windage")
            assertTrue(abs(p.velocityMps / reference[i + 3] - 1) < 0.002, "velocity at ${p.rangeM}")
            assertTrue(abs(p.timeS / reference[i + 4] - 1) < 0.002, "time at ${p.rangeM}")
        }
    }

    @Test
    fun g1MatchesReference() {
        val solution = Solver(SolverOptions(maxRangeM = 1000.0)).solve(
            rifle, ammo(BallisticCoefficient(DragFunction.G1, 0.5), 800.0), ShotConditions(),
        )
        check(solution, g1Reference)
    }

    @Test
    fun g7WithCrosswindMatchesReference() {
        val wind = Wind(4.0, fromRad = 1.5 * PI) // from the left
        val solution = Solver(SolverOptions(maxRangeM = 1000.0)).solve(
            rifle, ammo(BallisticCoefficient(DragFunction.G7, 0.25), 850.0), ShotConditions(winds = listOf(wind)),
        )
        check(solution, g7WindReference)
    }
}
