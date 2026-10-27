package pl.ballistics.app

import kotlinx.serialization.Serializable

enum class AngularUnit(val label: String, val radians: Double) {
    MRAD("MRAD", pl.ballistics.core.Units.MRAD),
    MOA("MOA", pl.ballistics.core.Units.MOA),
}

/** G1…RA4 map to the standard drag functions; CUSTOM uses a pasted Cd(Mach) curve. */
enum class DragType { G1, G7, G2, G5, G6, G8, GI, GS, RA4, CUSTOM }

/**
 * Everything the user types, kept as text so fields can hold half-typed values.
 * Parsed into solver inputs by [Calculator].
 */
@Serializable
data class Inputs(
    // Rifle
    val sightHeightCm: String = "4.5",
    val zeroRangeM: String = "100",
    val twistIn: String = "10",
    val rightHandTwist: Boolean = true,
    val angularUnit: AngularUnit = AngularUnit.MRAD,
    val clickValue: String = "0.1",

    // Ammunition
    val dragType: DragType = DragType.G7,
    val bc: String = "0.243",
    val customCurve: String = "",
    val massGr: String = "175",
    val diameterMm: String = "7.82",
    val lengthMm: String = "31.5",
    val muzzleVelocity: String = "790",
    val powderSensitivity: String = "0",
    val powderReferenceTemp: String = "15",

    // Conditions at zeroing
    val zeroTemperatureC: String = "15",
    val zeroPressureHpa: String = "1013.25",

    // Current conditions
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

    // Table
    val maxRangeM: String = "1000",
    val stepM: String = "50",
)
