package pl.ballistics.app.data

import androidx.annotation.StringRes
import pl.ballistics.app.R
import pl.ballistics.core.Units
import java.util.Locale

/**
 * Catalogue bullet data as published by the manufacturers (BC in lb/in²,
 * dimensions in inches). Lengths are approximate: measure your bullets with a
 * caliper for the best stability and spin-drift estimates.
 */
data class Bullet(
    val maker: String,
    val name: String,
    val diameterIn: Double,
    val massGr: Double,
    val g1: Double?,
    val g7: Double?,
    val lengthIn: Double? = null,
) {
    val label: String get() = "$maker $name ${fmt(massGr)} gr"
}

/** Factory ammunition: a bullet plus the catalogue muzzle velocity (test barrel, usually 24″). */
data class FactoryLoad(
    val maker: String,
    val cartridge: String,
    val bullet: Bullet,
    val muzzleVelocityFps: Double,
) {
    val label: String get() = "$maker $cartridge ${bullet.name} ${fmt(bullet.massGr)} gr"
}

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private fun num(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

/** Fills an ammo profile from a bullet, preferring the G7 BC for modern long boat-tails. */
fun AmmoProfile.withBullet(bullet: Bullet): AmmoProfile {
    val useG7 = bullet.g7 != null
    return copy(
        name = bullet.label,
        dragType = if (useG7) DragType.G7 else DragType.G1,
        bc = num(if (useG7) bullet.g7!! else bullet.g1!!, 3),
        massGr = fmt(bullet.massGr),
        diameterMm = num(bullet.diameterIn * Units.INCH * 1000, 2),
        lengthMm = bullet.lengthIn?.let { num(it * Units.INCH * 1000, 1) } ?: "",
    )
}

fun AmmoProfile.withFactoryLoad(load: FactoryLoad): AmmoProfile =
    withBullet(load.bullet).copy(
        name = load.label,
        muzzleVelocity = num(load.muzzleVelocityFps * Units.FPS, 0),
    )

object BulletLibrary {
    private val smk168 = Bullet("Sierra", "MatchKing HPBT", 0.308, 168.0, 0.462, 0.218, 1.215)
    private val smk175 = Bullet("Sierra", "MatchKing HPBT", 0.308, 175.0, 0.505, 0.243, 1.240)
    private val smk77 = Bullet("Sierra", "MatchKing HPBT", 0.224, 77.0, 0.372, null, 0.995)
    private val eldm73 = Bullet("Hornady", "ELD Match", 0.224, 73.0, 0.398, 0.200, 0.96)
    private val eldm75 = Bullet("Hornady", "ELD Match", 0.224, 75.0, 0.467, 0.235, 1.00)
    private val eldm108 = Bullet("Hornady", "ELD Match", 0.243, 108.0, 0.536, 0.270, 1.24)
    private val eldm140 = Bullet("Hornady", "ELD Match", 0.264, 140.0, 0.646, 0.326, 1.39)
    private val eldm147 = Bullet("Hornady", "ELD Match", 0.264, 147.0, 0.697, 0.351, 1.43)
    private val eldx143 = Bullet("Hornady", "ELD-X", 0.264, 143.0, 0.625, 0.315, 1.38)
    private val eldm178 = Bullet("Hornady", "ELD Match", 0.308, 178.0, 0.547, 0.275, 1.34)
    private val eldx178 = Bullet("Hornady", "ELD-X", 0.308, 178.0, 0.552, 0.278, 1.36)
    private val eldm208 = Bullet("Hornady", "ELD Match", 0.308, 208.0, 0.670, 0.337, 1.48)
    private val eldm285 = Bullet("Hornady", "ELD Match", 0.338, 285.0, 0.789, 0.399, 1.74)
    private val berger140 = Bullet("Berger", "Hybrid Target", 0.264, 140.0, 0.618, 0.311, 1.41)
    private val berger185 = Bullet("Berger", "Juggernaut OTM", 0.308, 185.0, 0.560, 0.283, 1.32)
    private val berger215 = Bullet("Berger", "Hybrid Target", 0.308, 215.0, 0.691, 0.354, 1.55)
    private val smk300 = Bullet("Sierra", "MatchKing HPBT", 0.338, 300.0, 0.768, null, 1.70)

    val bullets: List<Bullet> = listOf(
        eldm73, eldm75, smk77,
        eldm108,
        eldm140, berger140, eldx143, eldm147,
        smk168, smk175, eldm178, eldx178, berger185, eldm208, berger215,
        eldm285, smk300,
    )

    val factoryLoads: List<FactoryLoad> = listOf(
        FactoryLoad("Hornady Match", ".223 Rem", eldm73, 2790.0),
        FactoryLoad("Federal Gold Medal Match", ".223 Rem", smk77, 2720.0),
        FactoryLoad("Hornady Match", "6.5 Creedmoor", eldm140, 2710.0),
        FactoryLoad("Hornady Match", "6.5 Creedmoor", eldm147, 2695.0),
        FactoryLoad("Hornady Precision Hunter", "6.5 Creedmoor", eldx143, 2700.0),
        FactoryLoad("Federal Gold Medal Match", ".308 Win", smk168, 2650.0),
        FactoryLoad("Federal Gold Medal Match", ".308 Win", smk175, 2600.0),
        FactoryLoad("Hornady Match", ".308 Win", eldm178, 2600.0),
        FactoryLoad("Hornady Precision Hunter", ".308 Win", eldx178, 2600.0),
        FactoryLoad("Hornady Match", ".338 Lapua Mag", eldm285, 2745.0),
    )
}

/** Typical rail-top height above the bore axis; a starting point, not a measurement. */
enum class RailPlatform(@StringRes val label: Int, val railAboveBoreMm: Double?) {
    BOLT_PICATINNY(R.string.rail_bolt_picatinny, 16.0),
    BOLT_BASES(R.string.rail_bolt_weaver, 13.0),
    AR15(R.string.rail_ar15, 28.0),
    CUSTOM(R.string.rail_custom, null),
}
