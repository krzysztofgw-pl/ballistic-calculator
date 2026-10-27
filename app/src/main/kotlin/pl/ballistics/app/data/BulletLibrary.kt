package pl.ballistics.app.data

import androidx.annotation.StringRes
import pl.ballistics.app.R
import pl.ballistics.core.Units
import java.util.Locale

/**
 * Catalogue bullet data as published by the manufacturers (BC in lb/in²,
 * dimensions in inches). Lengths are approximate: measure your bullets with a
 * caliper for the best stability and spin-drift estimates.
 *
 * @param twistIn fastest recommended twist, inches per turn, when the maker publishes it.
 */
data class Bullet(
    val maker: String,
    val name: String,
    val diameterIn: Double,
    val massGr: Double,
    val g1: Double?,
    val g7: Double?,
    val lengthIn: Double? = null,
    val twistIn: Double? = null,
    val source: String = "",
) {
    val label: String get() = "$maker $name ${caliber(diameterIn)} ${fmt(massGr)} gr"
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

/** ".308", ".2645": the usual way calibers are written, so a search for "308" finds them. */
private fun caliber(diameterIn: Double) = fmt(diameterIn).removePrefix("0")

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
    /** All rifle bullets from `bullets.csv`, ordered by caliber, maker and weight. */
    val bullets: List<Bullet> by lazy {
        val stream = checkNotNull(BulletLibrary::class.java.getResourceAsStream("/bullets.csv")) { "bullets.csv missing" }
        stream.bufferedReader().useLines { parseCsv(it) }
    }

    internal fun parseCsv(lines: Sequence<String>): List<Bullet> {
        val rows = lines.filter { it.isNotBlank() && !it.startsWith("#") }.map(::splitCsv).toList()
        val header = rows.first()
        fun List<String>.col(name: String) = getOrNull(header.indexOf(name)).orEmpty()
        return rows.drop(1).map { r ->
            Bullet(
                maker = r.col("maker"),
                name = r.col("name"),
                diameterIn = r.col("diameter_in").toDouble(),
                massGr = r.col("weight_gr").toDouble(),
                g1 = r.col("g1").toDoubleOrNull(),
                g7 = r.col("g7").toDoubleOrNull(),
                lengthIn = r.col("length_in").toDoubleOrNull(),
                twistIn = r.col("twist_in").toDoubleOrNull(),
                source = r.col("source"),
            )
        }
    }

    /** Splits one CSV line; fields may be quoted, with "" for a literal quote. */
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private fun bullet(maker: String, name: String, diameterIn: Double, massGr: Double): Bullet =
        bullets.first { it.maker == maker && it.name == name && it.diameterIn == diameterIn && it.massGr == massGr }

    val factoryLoads: List<FactoryLoad> by lazy {
        listOf(
            FactoryLoad("Hornady Match", ".223 Rem", bullet("Hornady", "ELD Match", 0.224, 73.0), 2790.0),
            FactoryLoad("Federal Gold Medal Match", ".223 Rem", bullet("Sierra", "MatchKing HPBT", 0.224, 77.0), 2720.0),
            FactoryLoad("Hornady Match", "6mm Creedmoor", bullet("Hornady", "ELD Match", 0.243, 108.0), 2960.0),
            FactoryLoad("Hornady Match", "6.5 Creedmoor", bullet("Hornady", "ELD Match", 0.264, 140.0), 2710.0),
            FactoryLoad("Hornady Match", "6.5 Creedmoor", bullet("Hornady", "ELD Match", 0.264, 147.0), 2695.0),
            FactoryLoad("Hornady Precision Hunter", "6.5 Creedmoor", bullet("Hornady", "ELD-X", 0.264, 143.0), 2700.0),
            FactoryLoad("Hornady Match", "6.5 PRC", bullet("Hornady", "ELD Match", 0.264, 147.0), 2910.0),
            FactoryLoad("Federal Gold Medal Match", ".308 Win", bullet("Sierra", "MatchKing HPBT", 0.308, 168.0), 2650.0),
            FactoryLoad("Federal Gold Medal Match", ".308 Win", bullet("Sierra", "MatchKing HPBT", 0.308, 175.0), 2600.0),
            FactoryLoad("Hornady Match", ".308 Win", bullet("Hornady", "ELD Match", 0.308, 178.0), 2600.0),
            FactoryLoad("Hornady Precision Hunter", ".308 Win", bullet("Hornady", "ELD-X", 0.308, 178.0), 2600.0),
            FactoryLoad("Hornady Match", ".300 PRC", bullet("Hornady", "ELD Match", 0.308, 225.0), 2810.0),
            FactoryLoad("Hornady Match", ".338 Lapua Mag", bullet("Hornady", "ELD Match", 0.338, 285.0), 2745.0),
        )
    }
}

/** Typical rail-top height above the bore axis; a starting point, not a measurement. */
enum class RailPlatform(@StringRes val label: Int, val railAboveBoreMm: Double?) {
    BOLT_PICATINNY(R.string.rail_bolt_picatinny, 16.0),
    BOLT_BASES(R.string.rail_bolt_weaver, 13.0),
    AR15(R.string.rail_ar15, 28.0),
    CUSTOM(R.string.rail_custom, null),
}
