package pl.ballistics.app

import androidx.annotation.StringRes
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.AngularUnit
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.data.DragType
import pl.ballistics.app.data.STANDARD_POWDER_SENSITIVITY
import pl.ballistics.app.data.WeaponProfile
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
import pl.ballistics.core.Units
import pl.ballistics.core.Wind

sealed interface CalcResult {
    data class Ok(
        val solution: Solution,
        val weapon: WeaponProfile,
        val ammo: AmmoProfile,
        val setup: Setup,
    ) : CalcResult {
        val angularUnit: AngularUnit get() = weapon.angularUnit
        val clickValue: Double? get() = weapon.clickValue.toNumber()?.takeIf { it > 0 }
    }

    data class Error(val message: UiText) : CalcResult
}

private class InputError(val text: UiText) : Exception()

/** Accepts both "0.5" and the Polish "0,5". */
fun String.toNumber(): Double? = trim().replace(',', '.').toDoubleOrNull()

object Calculator {

    fun calculate(weapon: WeaponProfile?, ammo: AmmoProfile?, conditions: Conditions): CalcResult = try {
        if (weapon == null) throw InputError(UiText.Res(R.string.error_no_weapon))
        if (ammo == null) throw InputError(UiText.Res(R.string.error_no_ammo, listOf(weapon.name)))
        val setup = setup(weapon, ammo, conditions)
        CalcResult.Ok(Solver(setup.options).solve(setup.rifle, setup.ammo, setup.conditions, setup.zeroAtmosphere), weapon, ammo, setup)
    } catch (e: InputError) {
        CalcResult.Error(e.text)
    } catch (e: IllegalArgumentException) {
        CalcResult.Error(e.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.error_cannot_solve))
    }

    private fun num(value: String, @StringRes name: Int, positive: Boolean = false): Double {
        val field = UiText.Res(name)
        val v = value.toNumber() ?: throw InputError(UiText.Res(R.string.error_invalid_value, listOf(field)))
        if (positive && v <= 0) throw InputError(UiText.Res(R.string.error_must_be_positive, listOf(field)))
        return v
    }

    private fun setup(w: WeaponProfile, a: AmmoProfile, c: Conditions): Setup {
        val massKg = num(a.massGr, R.string.field_bullet_mass, true) * Units.GRAIN
        val diameterM = num(a.diameterMm, R.string.field_diameter, true) / 1000
        val lengthM = a.lengthMm.toNumber()?.takeIf { it > 0 }?.div(1000)

        val drag: DragModel = when (a.dragType) {
            DragType.CUSTOM -> parseCurve(a.customCurve, massKg, diameterM)
            else -> BallisticCoefficient(DragFunction.valueOf(a.dragType.name), num(a.bc, R.string.field_bc, true))
        }
        val ammo = Ammunition(
            projectile = Projectile(drag, massKg, diameterM, lengthM),
            muzzleVelocityMps = num(w.muzzleVelocityFor(a), R.string.field_muzzle_velocity, true),
            powderSensitivityMpsPerC = a.powderSensitivity.takeIf { it.isNotBlank() }
                ?.let { num(it, R.string.field_powder_sensitivity) }?.takeIf { it != 0.0 }
                ?: STANDARD_POWDER_SENSITIVITY,
            powderReferenceTempC = a.powderReferenceTemp.takeIf { it.isNotBlank() }
                ?.let { num(it, R.string.field_powder_reference_temp) } ?: 21.0,
        )
        val rifle = Rifle(
            sightHeightM = num(w.sightHeightCm, R.string.field_sight_height) / 100,
            zeroRangeM = num(w.zeroRangeM, R.string.field_zero_range, true),
            twistM = w.twistIn.toNumber()?.takeIf { it > 0 }?.times(Units.INCH),
            rightHandTwist = w.rightHandTwist,
        )

        val altitude = num(c.altitudeM, R.string.field_altitude)
        val temperature = num(c.temperatureC, R.string.field_temperature)
        val pressurePa = num(c.pressureHpa, R.string.field_pressure, true) * Units.HPA
        val humidity = (num(c.humidityPct, R.string.field_humidity) / 100).coerceIn(0.0, 1.0)
        val atmosphere = if (c.pressureIsQnh) {
            Atmosphere.fromSeaLevelPressure(pressurePa, altitude, temperature, humidity)
        } else {
            Atmosphere(altitude, pressurePa, temperature, humidity)
        }
        val zeroAtmosphere = Atmosphere(
            stationPressurePa = num(w.zeroPressureHpa, R.string.field_zero_pressure, true) * Units.HPA,
            temperatureC = num(w.zeroTemperatureC, R.string.field_zero_temperature),
        )

        val windSpeed = num(c.windSpeed, R.string.field_wind_speed)
        val winds = if (windSpeed != 0.0) listOf(Wind.fromClock(windSpeed, num(c.windDirectionDeg, R.string.field_wind_direction) / 30)) else emptyList()

        val conditions = ShotConditions(
            atmosphere = atmosphere,
            winds = winds,
            lookAngleRad = num(c.lookAngleDeg, R.string.field_look_angle) * Units.DEG,
            cantRad = num(c.cantDeg, R.string.field_cant) * Units.DEG,
            latitudeRad = if (c.coriolis) num(c.latitudeDeg, R.string.field_latitude) * Units.DEG else null,
            azimuthRad = if (c.coriolis) num(c.azimuthDeg, R.string.field_azimuth) * Units.DEG else 0.0,
        )
        val maxRange = num(c.maxRangeM, R.string.field_table_range, true)
        val step = num(c.stepM, R.string.field_table_step, true)
        if (maxRange / step > 1000) throw InputError(UiText.Res(R.string.error_step_too_small))

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
            val bad = InputError(UiText.Res(R.string.error_bad_curve_row, listOf(line)))
            if (parts.size < 2) throw bad
            val mach = parts[0].toNumber() ?: throw bad
            val cd = parts[1].toNumber() ?: throw bad
            mach to cd
        }.sortedBy { it.first }.distinctBy { it.first }
        if (rows.size < 2) throw InputError(UiText.Res(R.string.error_curve_too_short))
        return CustomDragCurve(
            rows.map { it.first }.toDoubleArray(),
            rows.map { it.second }.toDoubleArray(),
            massKg,
            diameterM,
        )
    }
}
