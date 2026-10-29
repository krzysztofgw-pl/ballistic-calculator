package pl.ballistics.web

import kotlinx.serialization.Serializable
import pl.ballistics.core.Units
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * The same data as the Android app (app/.../data/Models.kt), field for field, so a file
 * exported on Android imports here and the other way round. Labels live in Strings.kt.
 * Numeric fields are kept as text, as typed; Calculator parses them.
 */

enum class FocalPlane { FFP, SFP }

enum class AngularUnit(val label: String, val radians: Double) {
    MRAD("MRAD", Units.MRAD),
    MOA("MOA", Units.MOA),
}

/** G1…RA4 map to the standard drag functions; CUSTOM uses a pasted Cd(Mach) curve. */
enum class DragType { G1, G7, G2, G5, G6, G8, GI, GS, RA4, CUSTOM }

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/** Typical change of muzzle velocity with powder temperature, used when a load gives none. */
const val STANDARD_POWDER_SENSITIVITY = 1.0

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
    val reticleGraduation: String = "",
    val focalPlane: FocalPlane = FocalPlane.FFP,
    val calibratedMagnification: String = "10",
    val minMagnification: String = "1",
    val maxMagnification: String = "25",
    val zeroRangeM: String = "100",
    val zeroTemperatureC: String = "15",
    val zeroPressureHpa: String = "1013.25",
    val ammoId: String? = null,
    val groupMoa: String = "",
    val muzzleVelocities: Map<String, String> = emptyMap(),
) {
    fun measuredVelocity(ammo: AmmoProfile): String? = muzzleVelocities[ammo.id]?.takeIf { it.isNotBlank() }

    fun muzzleVelocityFor(ammo: AmmoProfile): String = measuredVelocity(ammo) ?: ammo.muzzleVelocity

    fun withMeasuredVelocity(ammo: AmmoProfile, mps: String): WeaponProfile =
        copy(muzzleVelocities = if (mps.isBlank()) muzzleVelocities - ammo.id else muzzleVelocities + (ammo.id to mps))
}

@Serializable
data class AmmoProfile(
    val id: String = newId(),
    val name: String = "New load",
    val cartridge: String = "",
    val dragType: DragType = DragType.G7,
    val bc: String = "0.25",
    val bcByModel: Map<DragType, String> = emptyMap(),
    val customCurve: String = "",
    val massGr: String = "175",
    val diameterMm: String = "7.82",
    val lengthMm: String = "",
    val muzzleVelocity: String = "800",
    val testBarrelCm: String = "",
    val powderSensitivity: String = "",
    val powderReferenceTemp: String = "21",
    val velocitySd: String = "",
    val notes: String = "",
)

/** Switches the drag model without carrying the BC across, as on Android. */
fun AmmoProfile.withDragType(type: DragType): AmmoProfile {
    if (type == dragType) return this
    val known = if (dragType == DragType.CUSTOM || bc.isBlank()) bcByModel else bcByModel + (dragType to bc)
    return copy(dragType = type, bcByModel = known, bc = known[type].orEmpty())
}

@Serializable
data class Conditions(
    val temperatureC: String = "15",
    val pressureHpa: String = "1013.25",
    val pressureIsQnh: Boolean = true,
    val altitudeM: String = "0",
    val humidityPct: String = "50",
    val windSpeed: String = "0",
    val windClock: String = "3",
    val windFromDeg: String? = null,
    val windDialNorthUp: Boolean = false,
    val lookAngleDeg: String = "0",
    val cantDeg: String = "0",
    val coriolis: Boolean = false,
    val latitudeDeg: String = "52",
    val azimuthDeg: String = "0",
    val spinDrift: Boolean = true,
    val aerodynamicJump: Boolean = true,
    val maxRangeM: String = "1000",
    val stepM: String = "50",
    val targetRangeM: String = "",
    val targetMoving: Boolean = false,
    val targetSpeedMps: String = "0",
    val targetDirectionDeg: String = "90",
    val targetWidthCm: String = "50",
    val targetHeightCm: String = "50",
    val windMinMps: String = "",
    val windMaxMps: String = "",
    val windUncertaintyMps: String = "1",
    val rangeUncertaintyM: String = "5",
) {
    /** Wind direction in degrees from the line of fire, converted from the clock for older data. */
    val windDirectionDeg: String
        get() = windFromDeg ?: windClock.toNumber()?.let { (it * 30).let { d -> if (d % 1.0 == 0.0) d.toInt().toString() else d.toString() } } ?: windClock
}

@Serializable
data class SavedTarget(
    val id: String = newId(),
    val name: String = "",
    val rangeM: String = "",
    val lookAngleDeg: String = "0",
    val azimuthDeg: String = "0",
    val moving: Boolean = false,
    val speedMps: String = "0",
    val directionDeg: String = "90",
    val widthCm: String = "50",
    val heightCm: String = "50",
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Correction(val dialElevation: Boolean = false, val dialWindage: Boolean = false) {
    companion object {
        fun defaultFor(reticleId: String) = if (reticleId.endsWith("-tree")) Correction() else Correction(dialElevation = true)
    }
}

@Serializable
data class UiSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val tabsAtTop: Boolean = false,
    val subTabsAtTop: Boolean = false,
    val keepScreenOn: Boolean = false,
    val locationSetsLatitude: Boolean = true,
    val locationSetsAltitude: Boolean = false,
    val garminWatch: Boolean = false,
    val checkUpdateOnStart: Boolean = false,
    val corrections: Map<String, Correction> = emptyMap(),
) {
    fun correction(reticleId: String): Correction = corrections[reticleId] ?: Correction.defaultFor(reticleId)
}

/** Everything the app stores; also the export/import file format, shared with Android. */
@Serializable
data class AppData(
    val version: Int = 1,
    val weapons: List<WeaponProfile> = emptyList(),
    val ammo: List<AmmoProfile> = emptyList(),
    val activeWeaponId: String? = null,
    val conditions: Conditions = Conditions(),
    val targets: List<SavedTarget> = emptyList(),
    val activeTargetId: String? = null,
    val ui: UiSettings = UiSettings(),
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
