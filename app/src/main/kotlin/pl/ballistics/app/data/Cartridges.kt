package pl.ballistics.app.data

import pl.ballistics.app.toNumber
import java.util.Locale
import kotlin.math.abs

/** A rifle cartridge and the bullet diameter it takes. */
data class Cartridge(val name: String, val bulletDiameterIn: Double, val aliases: List<String> = emptyList())

/** Common rifle cartridges, so loads can be grouped by caliber and matched to a rifle. */
object Cartridges {
    val all: List<Cartridge> = listOf(
        Cartridge(".22 LR", 0.223, listOf("22lr", ".22 Long Rifle")),
        Cartridge(".223 Rem", 0.224, listOf("223", ".223", ".223 Remington", "5.56x45", "5.56 NATO", "5.56")),
        Cartridge(".22-250 Rem", 0.224, listOf("22-250")),
        Cartridge(".22 Creedmoor", 0.224),
        Cartridge(".243 Win", 0.243, listOf("243", ".243 Winchester")),
        Cartridge("6mm Creedmoor", 0.243, listOf("6 Creedmoor", "6mm CM", "6 CM")),
        Cartridge("6mm BR", 0.243, listOf("6 BR", "6BR Norma")),
        Cartridge("6mm Dasher", 0.243, listOf("6 Dasher")),
        Cartridge("6mm GT", 0.243, listOf("6 GT")),
        Cartridge("6.5 Creedmoor", 0.264, listOf("6.5 CM", "6.5mm Creedmoor", "6,5 Creedmoor")),
        Cartridge("6.5x55 Swedish", 0.264, listOf("6.5x55", "6.5x55 SE", "6,5x55")),
        Cartridge("6.5 PRC", 0.264),
        Cartridge(".260 Rem", 0.264, listOf("260")),
        Cartridge("6.5x47 Lapua", 0.264, listOf("6.5x47")),
        Cartridge("7mm-08 Rem", 0.284, listOf("7mm-08", "7-08")),
        Cartridge("7mm Rem Mag", 0.284, listOf("7mm RM", "7mm Remington Magnum")),
        Cartridge("7mm PRC", 0.284),
        Cartridge("7x64", 0.284),
        Cartridge(".308 Win", 0.308, listOf("308", ".308", ".308 Winchester", "7.62x51", "7.62 NATO", "7,62x51")),
        Cartridge(".30-06 Springfield", 0.308, listOf("30-06", ".30-06")),
        Cartridge(".300 Win Mag", 0.308, listOf("300 WM", ".300 WM", ".300 Winchester Magnum")),
        Cartridge(".300 PRC", 0.308),
        Cartridge(".300 Norma Mag", 0.308, listOf(".300 Norma")),
        Cartridge(".300 Blackout", 0.308, listOf("300 BLK", ".300 AAC")),
        Cartridge("7.62x39", 0.311, listOf("7,62x39")),
        Cartridge("7.62x54R", 0.311, listOf("7,62x54R")),
        Cartridge(".303 British", 0.311, listOf(".303")),
        Cartridge("8x57 IS", 0.323, listOf("8x57", "8mm Mauser")),
        Cartridge(".338 Lapua Mag", 0.338, listOf(".338 LM", "338 Lapua", ".338 Lapua Magnum", "8.6x70")),
        Cartridge(".338 Win Mag", 0.338, listOf(".338 WM")),
        Cartridge(".375 CheyTac", 0.375, listOf("375 CT")),
        Cartridge(".408 CheyTac", 0.408, listOf("408 CT")),
        Cartridge(".50 BMG", 0.510, listOf("12.7x99", ".50 Browning")),
    )

    private fun key(s: String) = s.lowercase(Locale.US).replace(",", ".").filter { it.isLetterOrDigit() || it == '.' }.trimStart('.')

    private val byKey: Map<String, Cartridge> = buildMap {
        for (c in all) (c.aliases + c.name).forEach { putIfAbsent(key(it), c) }
    }

    /** The cartridge named by free text such as ".308 Win", "308" or "7.62x51"; null if unknown. */
    fun find(text: String): Cartridge? = text.takeIf { it.isNotBlank() }?.let { byKey[key(it)] }

    /** Bullet diameter for a rifle's caliber text, from a known cartridge or a bare ".308"-style number. */
    fun bulletDiameterIn(text: String): Double? =
        find(text)?.bulletDiameterIn ?: text.trim().takeIf { it.startsWith(".") }?.toNumber()?.takeIf { it in 0.17..0.6 }

    /** ".224″": bullet diameter as calibers are written. Not the cartridge name: a .223 Rem takes .224″ bullets. */
    fun inches(diameterIn: Double): String = String.format(Locale.US, "%.3f", diameterIn).removePrefix("0") + "″"

    /** Cartridges that take bullets of this diameter. */
    fun forDiameter(diameterIn: Double): List<Cartridge> = all.filter { sameDiameter(it.bulletDiameterIn, diameterIn) }

    /**
     * ".224″ (5.56 mm) bullets · .223 Rem, .22-250 Rem, .22 Creedmoor": a group of bullets by diameter,
     * naming the cartridges that use them so ".224" is not mistaken for a cartridge.
     */
    fun boreLabel(diameterIn: Double): String {
        val names = forDiameter(diameterIn).map { it.name }
        val cartridges = when {
            names.isEmpty() -> ""
            names.size <= 3 -> " · " + names.joinToString(", ")
            else -> " · " + names.take(3).joinToString(", ") + "…"
        }
        return "${inches(diameterIn)} (${metric(diameterIn)}) bullets$cartridges"
    }

    private fun metric(diameterIn: Double): String {
        val inch = String.format(Locale.US, "%.3f", diameterIn).removePrefix("0")
        return when (inch) {
            ".224" -> "5.56 mm"
            ".243" -> "6 mm"
            ".264" -> "6.5 mm"
            ".284" -> "7 mm"
            ".308" -> "7.62 mm"
            ".311" -> "7.62 mm"
            ".323" -> "8 mm"
            ".338" -> "8.6 mm"
            else -> String.format(Locale.US, "%.2f mm", diameterIn * 25.4)
        }
    }

    fun sameDiameter(a: Double, b: Double) = abs(a - b) < 0.0008
}

private const val MM_PER_IN = 25.4

/** The cartridge if one is named, otherwise the bore group from the bullet diameter. */
fun AmmoProfile.caliberGroup(): String =
    Cartridges.find(cartridge)?.name
        ?: cartridge.takeIf { it.isNotBlank() }
        ?: diameterMm.toNumber()?.let { Cartridges.boreLabel(it / MM_PER_IN) }
        ?: ""

/** Bullet diameter for sorting groups; unknown sorts last. */
fun AmmoProfile.diameterInOrMax(): Double = diameterMm.toNumber()?.div(MM_PER_IN) ?: Double.MAX_VALUE

/**
 * Whether a load can be fired from [weapon]: the same cartridge when both name a known one,
 * otherwise the same bullet diameter. A rifle without a recognised caliber accepts everything.
 */
fun AmmoProfile.fits(weapon: WeaponProfile): Boolean {
    val rifle = Cartridges.find(weapon.caliber)
    val load = Cartridges.find(cartridge)
    if (rifle != null && load != null) return rifle == load
    val rifleDiameter = Cartridges.bulletDiameterIn(weapon.caliber) ?: return true
    val loadDiameter = diameterMm.toNumber()?.div(MM_PER_IN) ?: return true
    return Cartridges.sameDiameter(rifleDiameter, loadDiameter)
}
