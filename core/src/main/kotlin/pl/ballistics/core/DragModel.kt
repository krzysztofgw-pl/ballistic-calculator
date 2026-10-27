package pl.ballistics.core

import kotlin.math.PI

/** Reference projectiles whose Cd(Mach) curves define the ballistic coefficient scale. */
enum class DragFunction(table: DoubleArray) {
    G1(DragTables.G1),
    G2(DragTables.G2),
    G5(DragTables.G5),
    G6(DragTables.G6),
    G7(DragTables.G7),
    G8(DragTables.G8),
    GI(DragTables.GI),
    GS(DragTables.GS),
    RA4(DragTables.RA4);

    val cd: MonotoneCubic = MonotoneCubic.fromPairs(table)
}

/**
 * Supplies the drag factor k such that the drag deceleration is a = k · ρ · v²,
 * with ρ in kg/m³ and v the airspeed in m/s. For a projectile, k = Cd · A / (2m).
 */
sealed interface DragModel {
    fun dragFactor(mach: Double, airspeed: Double): Double
}

private fun bcToSi(bcLbPerIn2: Double) = bcLbPerIn2 * Units.LB_PER_IN2

/**
 * Classic ballistic coefficient against a standard drag function.
 * Since BC = m / (i·d²), the factor is Cd_ref(M) · π / (8 · BC).
 *
 * @param bc ballistic coefficient in lb/in² (the number printed on bullet boxes).
 */
data class BallisticCoefficient(val function: DragFunction, val bc: Double) : DragModel {
    init {
        require(bc > 0) { "BC must be positive" }
    }

    private val scale = PI / (8.0 * bcToSi(bc))

    override fun dragFactor(mach: Double, airspeed: Double) = function.cd(mach) * scale
}

/**
 * Velocity-banded BC as published by Sierra and others: each band's BC applies
 * while the airspeed is at or above that band's threshold.
 */
data class BandedBallisticCoefficient(val function: DragFunction, val bands: List<Band>) : DragModel {
    data class Band(val minVelocityMps: Double, val bc: Double)

    private val sorted = bands.sortedByDescending { it.minVelocityMps }
    private val scales = sorted.map { PI / (8.0 * bcToSi(it.bc)) }

    init {
        require(bands.isNotEmpty()) { "At least one BC band required" }
        require(bands.all { it.bc > 0 }) { "BC must be positive" }
    }

    override fun dragFactor(mach: Double, airspeed: Double): Double {
        val i = sorted.indexOfFirst { airspeed >= it.minVelocityMps }.let { if (it < 0) sorted.lastIndex else it }
        return function.cd(mach) * scales[i]
    }
}

/**
 * The projectile's own measured drag curve, e.g. from Doppler radar (Lapua, Hornady 4DOF).
 * This is the most accurate option because no reference shape is assumed.
 */
class CustomDragCurve(
    machs: DoubleArray,
    cds: DoubleArray,
    val massKg: Double,
    val diameterM: Double,
) : DragModel {
    private val cd = MonotoneCubic(machs, cds)
    private val scale = PI * diameterM * diameterM / (8.0 * massKg)

    override fun dragFactor(mach: Double, airspeed: Double) = cd(mach) * scale
}
