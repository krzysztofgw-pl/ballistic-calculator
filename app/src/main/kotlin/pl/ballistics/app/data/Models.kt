package pl.ballistics.app.data

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import pl.ballistics.app.R
import pl.ballistics.core.Units
import java.util.UUID

enum class FocalPlane(@StringRes val label: Int) {
    FFP(R.string.focal_plane_ffp),
    SFP(R.string.focal_plane_sfp),
}

enum class AngularUnit(val label: String, val radians: Double) {
    MRAD("MRAD", Units.MRAD),
    MOA("MOA", Units.MOA),
}

/** G1…RA4 map to the standard drag functions; CUSTOM uses a pasted Cd(Mach) curve. */
enum class DragType { G1, G7, G2, G5, G6, G8, GI, GS, RA4, CUSTOM }

fun newId(): String = UUID.randomUUID().toString()

/*
 * Numeric fields are kept as text so the editors can hold half-typed values
 * ("0," while typing "0,5"). They are parsed by Calculator.
 */

/** A rifle with its scope and mount; the zero belongs here because it depends on all three. */
@Serializable
data class WeaponProfile(
    val id: String = newId(),
    val name: String = "New rifle",
    val caliber: String = "",
    val twistIn: String = "10",
    val rightHandTwist: Boolean = true,
    val barrelLengthCm: String = "",

    val scopeName: String = "",
    val angularUnit: AngularUnit = AngularUnit.MRAD,
    val clickValue: String = "0.1",
    val sightHeightCm: String = "4.5",
    val reticleId: String = "mrad-hash",
    val focalPlane: FocalPlane = FocalPlane.FFP,
    /** SFP only: magnification at which the reticle subtensions are true. */
    val calibratedMagnification: String = "10",
    val minMagnification: String = "1",
    val maxMagnification: String = "25",

    val zeroRangeM: String = "100",
    val zeroTemperatureC: String = "15",
    val zeroPressureHpa: String = "1013.25",

    /** Ammunition this rifle is zeroed with. */
    val ammoId: String? = null,
    /** Muzzle velocity measured with this rifle, by ammo id; it overrides the load's reference velocity. */
    val muzzleVelocities: Map<String, String> = emptyMap(),
) {
    fun measuredVelocity(ammo: AmmoProfile): String? = muzzleVelocities[ammo.id]?.takeIf { it.isNotBlank() }

    /** The velocity the solver uses: measured with this rifle if known, else the load's reference velocity. */
    fun muzzleVelocityFor(ammo: AmmoProfile): String = measuredVelocity(ammo) ?: ammo.muzzleVelocity

    fun withMeasuredVelocity(ammo: AmmoProfile, mps: String): WeaponProfile =
        copy(muzzleVelocities = if (mps.isBlank()) muzzleVelocities - ammo.id else muzzleVelocities + (ammo.id to mps))
}

@Serializable
data class AmmoProfile(
    val id: String = newId(),
    val name: String = "New load",
    val dragType: DragType = DragType.G7,
    /** BC for [dragType]; a number for one drag model is meaningless for another. */
    val bc: String = "0.25",
    /** BCs entered or loaded for the other drag models, restored when the model is switched back. */
    val bcByModel: Map<DragType, String> = emptyMap(),
    val customCurve: String = "",
    val massGr: String = "175",
    val diameterMm: String = "7.82",
    val lengthMm: String = "",
    /** Reference velocity: from the box, or a chronograph, in a barrel of [testBarrelCm]. */
    val muzzleVelocity: String = "800",
    val testBarrelCm: String = "",
    val powderSensitivity: String = "0",
    val powderReferenceTemp: String = "15",
    val notes: String = "",
)

/**
 * Switches the drag model without carrying the BC across: a G7 BC read as G1
 * (or the other way round) would be off by a factor of about two. The BC known
 * for the new model is restored, otherwise the field is left empty to be filled in.
 */
fun AmmoProfile.withDragType(type: DragType): AmmoProfile {
    if (type == dragType) return this
    val known = if (dragType == DragType.CUSTOM || bc.isBlank()) bcByModel else bcByModel + (dragType to bc)
    return copy(dragType = type, bcByModel = known, bc = known[type].orEmpty())
}

/** Conditions at the firing point and table settings, shared by all profiles. */
@Serializable
data class Conditions(
    val temperatureC: String = "15",
    val pressureHpa: String = "1013.25",
    val pressureIsQnh: Boolean = true,
    val altitudeM: String = "0",
    val humidityPct: String = "50",
    val windSpeed: String = "0",
    val windClock: String = "3",
    val lookAngleDeg: String = "0",
    val cantDeg: String = "0",
    val coriolis: Boolean = false,
    val latitudeDeg: String = "52",
    val azimuthDeg: String = "0",
    val spinDrift: Boolean = true,
    val aerodynamicJump: Boolean = true,
    val maxRangeM: String = "1000",
    val stepM: String = "50",
)

/** Everything the app stores; also the export/import file format. */
@Serializable
data class AppData(
    val version: Int = 1,
    val weapons: List<WeaponProfile> = emptyList(),
    val ammo: List<AmmoProfile> = emptyList(),
    val activeWeaponId: String? = null,
    val conditions: Conditions = Conditions(),
    /** Wall-clock time of the last local edit; the newer copy wins when syncing. */
    val modifiedAt: Long = 0,
) {
    val activeWeapon: WeaponProfile? get() = weapons.find { it.id == activeWeaponId } ?: weapons.firstOrNull()

    fun ammoFor(weapon: WeaponProfile): AmmoProfile? = ammo.find { it.id == weapon.ammoId }

    companion object {
        fun sample(): AppData {
            val ammo = AmmoProfile(
                name = "Sierra MatchKing 175 gr",
                dragType = DragType.G7,
                bc = "0.243",
                massGr = "175",
                diameterMm = "7.82",
                lengthMm = "31.5",
                muzzleVelocity = "790",
            )
            val weapon = WeaponProfile(name = ".308 rifle", caliber = ".308 Win", ammoId = ammo.id)
            return AppData(weapons = listOf(weapon), ammo = listOf(ammo), activeWeaponId = weapon.id)
        }
    }
}
