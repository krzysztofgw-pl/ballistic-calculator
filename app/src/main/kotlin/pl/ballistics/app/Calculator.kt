package pl.ballistics.app

import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.AngularUnit
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.data.DragType
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
    ) : CalcResult {
        val angularUnit: AngularUnit get() = weapon.angularUnit
        val clickValue: Double? get() = weapon.clickValue.toNumber()?.takeIf { it > 0 }
    }

    data class Error(val message: String) : CalcResult
}

private class InputError(message: String) : Exception(message)

/** Accepts both "0.5" and the Polish "0,5". */
fun String.toNumber(): Double? = trim().replace(',', '.').toDoubleOrNull()

object Calculator {

    fun calculate(weapon: WeaponProfile?, ammo: AmmoProfile?, conditions: Conditions): CalcResult = try {
        if (weapon == null) throw InputError("Dodaj profil broni w zakładce Profile.")
        if (ammo == null) throw InputError("Wybierz amunicję dla: ${weapon.name}.")
        CalcResult.Ok(solve(weapon, ammo, conditions), weapon, ammo)
    } catch (e: InputError) {
        CalcResult.Error(e.message ?: "Błąd danych")
    } catch (e: IllegalArgumentException) {
        CalcResult.Error(e.message ?: "Nie można obliczyć trajektorii")
    }

    private fun num(value: String, name: String, positive: Boolean = false): Double {
        val v = value.toNumber() ?: throw InputError("Niepoprawna wartość: $name")
        if (positive && v <= 0) throw InputError("$name musi być większe od zera")
        return v
    }

    private fun solve(w: WeaponProfile, a: AmmoProfile, c: Conditions): Solution {
        val massKg = num(a.massGr, "masa pocisku", true) * Units.GRAIN
        val diameterM = num(a.diameterMm, "średnica", true) / 1000
        val lengthM = a.lengthMm.toNumber()?.takeIf { it > 0 }?.div(1000)

        val drag: DragModel = when (a.dragType) {
            DragType.CUSTOM -> parseCurve(a.customCurve, massKg, diameterM)
            else -> BallisticCoefficient(DragFunction.valueOf(a.dragType.name), num(a.bc, "BC", true))
        }
        val ammo = Ammunition(
            projectile = Projectile(drag, massKg, diameterM, lengthM),
            muzzleVelocityMps = num(a.muzzleVelocity, "prędkość początkowa", true),
            powderSensitivityMpsPerC = num(a.powderSensitivity, "czułość prochu"),
            powderReferenceTempC = num(a.powderReferenceTemp, "temperatura odniesienia prochu"),
        )
        val rifle = Rifle(
            sightHeightM = num(w.sightHeightCm, "wysokość celownika") / 100,
            zeroRangeM = num(w.zeroRangeM, "odległość przystrzelania", true),
            twistM = w.twistIn.toNumber()?.takeIf { it > 0 }?.times(Units.INCH),
            rightHandTwist = w.rightHandTwist,
        )

        val altitude = num(c.altitudeM, "wysokość n.p.m.")
        val temperature = num(c.temperatureC, "temperatura")
        val pressurePa = num(c.pressureHpa, "ciśnienie", true) * Units.HPA
        val humidity = (num(c.humidityPct, "wilgotność") / 100).coerceIn(0.0, 1.0)
        val atmosphere = if (c.pressureIsQnh) {
            Atmosphere.fromSeaLevelPressure(pressurePa, altitude, temperature, humidity)
        } else {
            Atmosphere(altitude, pressurePa, temperature, humidity)
        }
        val zeroAtmosphere = Atmosphere(
            stationPressurePa = num(w.zeroPressureHpa, "ciśnienie przy przystrzelaniu", true) * Units.HPA,
            temperatureC = num(w.zeroTemperatureC, "temperatura przy przystrzelaniu"),
        )

        val windSpeed = num(c.windSpeed, "prędkość wiatru")
        val winds = if (windSpeed != 0.0) listOf(Wind.fromClock(windSpeed, num(c.windClock, "kierunek wiatru"))) else emptyList()

        val conditions = ShotConditions(
            atmosphere = atmosphere,
            winds = winds,
            lookAngleRad = num(c.lookAngleDeg, "kąt strzału") * Units.DEG,
            cantRad = num(c.cantDeg, "przechył") * Units.DEG,
            latitudeRad = if (c.coriolis) num(c.latitudeDeg, "szerokość geograficzna") * Units.DEG else null,
            azimuthRad = if (c.coriolis) num(c.azimuthDeg, "azymut") * Units.DEG else 0.0,
        )
        val maxRange = num(c.maxRangeM, "zasięg tabeli", true)
        val step = num(c.stepM, "krok tabeli", true)
        if (maxRange / step > 1000) throw InputError("Za mały krok tabeli")

        val options = SolverOptions(
            maxRangeM = maxRange,
            stepM = step,
            spinDrift = c.spinDrift,
            aerodynamicJump = c.aerodynamicJump,
            coriolis = c.coriolis,
        )
        return Solver(options).solve(rifle, ammo, conditions, zeroAtmosphere)
    }

    /** One "Mach Cd" pair per line; separators: space, tab, semicolon, or comma between numbers. */
    private fun parseCurve(text: String, massKg: Double, diameterM: Double): CustomDragCurve {
        val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
            val parts = line.split(Regex("[\\s;]+")).let { if (it.size == 1) line.split(',') else it }
            if (parts.size < 2) throw InputError("Niepoprawny wiersz krzywej: $line")
            val mach = parts[0].toNumber() ?: throw InputError("Niepoprawny wiersz krzywej: $line")
            val cd = parts[1].toNumber() ?: throw InputError("Niepoprawny wiersz krzywej: $line")
            mach to cd
        }.sortedBy { it.first }.distinctBy { it.first }
        if (rows.size < 2) throw InputError("Krzywa Cd potrzebuje co najmniej dwóch punktów (Mach Cd)")
        return CustomDragCurve(
            rows.map { it.first }.toDoubleArray(),
            rows.map { it.second }.toDoubleArray(),
            massKg,
            diameterM,
        )
    }
}
