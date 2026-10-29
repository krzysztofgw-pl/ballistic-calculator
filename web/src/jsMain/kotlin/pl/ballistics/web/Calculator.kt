package pl.ballistics.web

import pl.ballistics.core.Ammunition
import pl.ballistics.core.Atmosphere
import pl.ballistics.core.BallisticCoefficient
import pl.ballistics.core.CustomDragCurve
import pl.ballistics.core.DragFunction
import pl.ballistics.core.DragModel
import pl.ballistics.core.Projectile
import pl.ballistics.core.Rifle
import pl.ballistics.core.ShotConditions
import pl.ballistics.core.Solution
import pl.ballistics.core.Solver
import pl.ballistics.core.SolverOptions
import pl.ballistics.core.TrajectoryPoint
import pl.ballistics.core.Units
import pl.ballistics.core.Wind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Accepts both "0.5" and the Polish "0,5". */
fun String.toNumber(): Double? = trim().replace(',', '.').toDoubleOrNull()

sealed interface CalcResult {
    data class Ok(val solution: Solution, val weapon: WeaponProfile, val ammo: AmmoProfile, val setup: Setup) : CalcResult {
        val unit: AngularUnit get() = weapon.angularUnit
        val clickValue: Double? get() = weapon.clickValue.toNumber()?.takeIf { it > 0 }
    }

    data class Error(val message: String) : CalcResult
}

/** The parsed solver inputs, kept so a screen can solve the same shot again at another distance. */
data class Setup(
    val rifle: Rifle,
    val ammo: Ammunition,
    val conditions: ShotConditions,
    val zeroAtmosphere: Atmosphere,
    val options: SolverOptions,
) {
    /** The point at exactly [rangeM]; [zeroElevationRad] from an earlier solution skips zeroing. */
    fun pointAt(rangeM: Double, zeroElevationRad: Double): TrajectoryPoint? =
        Solver(options.copy(maxRangeM = rangeM, stepM = rangeM))
            .solve(rifle, ammo, conditions, zeroAtmosphere, zeroElevationRad)
            .points.lastOrNull()?.takeIf { abs(it.rangeM - rangeM) < 1e-6 }

    /** A target moving at [speedMps] in [directionDeg] from the line of fire (0 = away, 90 = to the right). */
    fun lead(rangeM: Double, zeroElevationRad: Double, speedMps: Double, directionDeg: Double): Lead? {
        val radial = speedMps * cos(directionDeg * PI / 180)
        val crossing = speedMps * sin(directionDeg * PI / 180)
        var p = pointAt(rangeM, zeroElevationRad) ?: return null
        repeat(3) {
            if (abs(radial) < 1e-3) return@repeat
            p = pointAt(rangeM + radial * p.timeS, zeroElevationRad) ?: return null
        }
        return Lead(p, crossing * p.timeS)
    }
}

/** The impact where a moving target will be, and how far to lead it. */
data class Lead(val point: TrajectoryPoint, val leadM: Double) {
    val leadRad: Double get() = atan2(leadM, point.rangeM)
}

private class InputError(message: String) : Exception(message)

/** Mirrors the Android Calculator: profile text fields to solver inputs. */
object Calculator {

    fun calculate(weapon: WeaponProfile?, ammo: AmmoProfile?, c: Conditions): CalcResult = try {
        if (weapon == null) throw InputError(S.errorNoWeapon)
        if (ammo == null) throw InputError(S.errorNoAmmo(weapon.name))
        val setup = setup(weapon, ammo, c)
        CalcResult.Ok(Solver(setup.options).solve(setup.rifle, setup.ammo, setup.conditions, setup.zeroAtmosphere), weapon, ammo, setup)
    } catch (e: InputError) {
        CalcResult.Error(e.message ?: S.errorCannotSolve)
    } catch (e: IllegalArgumentException) {
        CalcResult.Error(e.message ?: S.errorCannotSolve)
    }

    private fun num(value: String, field: String, positive: Boolean = false): Double {
        val v = value.toNumber() ?: throw InputError(S.errorInvalid(field))
        if (positive && v <= 0) throw InputError(S.errorPositive(field))
        return v
    }

    private fun setup(w: WeaponProfile, a: AmmoProfile, c: Conditions): Setup {
        val massKg = num(a.massGr, S.fieldMass, true) * Units.GRAIN
        val diameterM = num(a.diameterMm, S.fieldDiameter, true) / 1000
        val lengthM = a.lengthMm.toNumber()?.takeIf { it > 0 }?.div(1000)

        val drag: DragModel = when (a.dragType) {
            DragType.CUSTOM -> parseCurve(a.customCurve, massKg, diameterM)
            else -> BallisticCoefficient(DragFunction.valueOf(a.dragType.name), num(a.bc, S.fieldBc, true))
        }
        val ammo = Ammunition(
            projectile = Projectile(drag, massKg, diameterM, lengthM),
            muzzleVelocityMps = num(w.muzzleVelocityFor(a), S.fieldMuzzleVelocity, true),
            powderSensitivityMpsPerC = a.powderSensitivity.takeIf { it.isNotBlank() }
                ?.let { num(it, S.fieldPowderSensitivity) }?.takeIf { it != 0.0 }
                ?: STANDARD_POWDER_SENSITIVITY,
            powderReferenceTempC = a.powderReferenceTemp.takeIf { it.isNotBlank() }
                ?.let { num(it, S.fieldPowderReferenceTemp) } ?: 21.0,
        )
        val rifle = Rifle(
            sightHeightM = num(w.sightHeightCm, S.fieldSightHeight) / 100,
            zeroRangeM = num(w.zeroRangeM, S.fieldZeroRange, true),
            twistM = w.twistIn.toNumber()?.takeIf { it > 0 }?.times(Units.INCH),
            rightHandTwist = w.rightHandTwist,
        )

        val altitude = num(c.altitudeM, S.fieldAltitude)
        val temperature = num(c.temperatureC, S.fieldTemperature)
        val pressurePa = num(c.pressureHpa, S.fieldPressure, true) * Units.HPA
        val humidity = (num(c.humidityPct, S.fieldHumidity) / 100).coerceIn(0.0, 1.0)
        val atmosphere = if (c.pressureIsQnh) {
            Atmosphere.fromSeaLevelPressure(pressurePa, altitude, temperature, humidity)
        } else {
            Atmosphere(altitude, pressurePa, temperature, humidity)
        }
        val zeroAtmosphere = Atmosphere(
            stationPressurePa = num(w.zeroPressureHpa, S.fieldZeroPressure, true) * Units.HPA,
            temperatureC = num(w.zeroTemperatureC, S.fieldZeroTemperature),
        )

        val windSpeed = num(c.windSpeed, S.fieldWindSpeed)
        val winds = if (windSpeed != 0.0) listOf(Wind.fromClock(windSpeed, num(c.windDirectionDeg, S.fieldWindDirection) / 30)) else emptyList()

        val conditions = ShotConditions(
            atmosphere = atmosphere,
            winds = winds,
            lookAngleRad = num(c.lookAngleDeg, S.fieldLookAngle) * Units.DEG,
            cantRad = num(c.cantDeg, S.fieldCant) * Units.DEG,
            latitudeRad = if (c.coriolis) num(c.latitudeDeg, S.fieldLatitude) * Units.DEG else null,
            azimuthRad = if (c.coriolis) num(c.azimuthDeg, S.fieldAzimuth) * Units.DEG else 0.0,
        )
        val maxRange = num(c.maxRangeM, S.fieldTableRange, true)
        val step = num(c.stepM, S.fieldTableStep, true)
        if (maxRange / step > 1000) throw InputError(S.errorStepTooSmall)

        val options = SolverOptions(
            maxRangeM = maxRange,
            stepM = step,
            spinDrift = c.spinDrift,
            aerodynamicJump = c.aerodynamicJump,
            coriolis = c.coriolis,
        )
        return Setup(rifle, ammo, conditions, zeroAtmosphere, options)
    }

    /** One "Mach Cd" pair per line; separators: space, tab, semicolon, or comma between numbers. */
    private fun parseCurve(text: String, massKg: Double, diameterM: Double): CustomDragCurve {
        val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
            val parts = line.split(Regex("[\\s;]+")).let { if (it.size == 1) line.split(',') else it }
            val bad = InputError(S.errorBadCurveRow(line))
            if (parts.size < 2) throw bad
            val mach = parts[0].toNumber() ?: throw bad
            val cd = parts[1].toNumber() ?: throw bad
            mach to cd
        }.sortedBy { it.first }.distinctBy { it.first }
        if (rows.size < 2) throw InputError(S.errorCurveTooShort)
        return CustomDragCurve(rows.map { it.first }.toDoubleArray(), rows.map { it.second }.toDoubleArray(), massKg, diameterM)
    }
}
