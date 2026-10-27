package pl.ballistics.app

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
    data class Ok(val solution: Solution, val inputs: Inputs) : CalcResult
    data class Error(val message: String) : CalcResult
}

private class InputError(message: String) : Exception(message)

/** Accepts both "0.5" and the Polish "0,5". */
fun String.toNumber(): Double? = trim().replace(',', '.').toDoubleOrNull()

object Calculator {

    fun calculate(inputs: Inputs): CalcResult = try {
        CalcResult.Ok(solve(inputs), inputs)
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

    private fun solve(i: Inputs): Solution {
        val massKg = num(i.massGr, "masa pocisku", true) * Units.GRAIN
        val diameterM = num(i.diameterMm, "średnica", true) / 1000
        val lengthM = i.lengthMm.toNumber()?.takeIf { it > 0 }?.div(1000)

        val drag: DragModel = when (i.dragType) {
            DragType.CUSTOM -> parseCurve(i.customCurve, massKg, diameterM)
            else -> BallisticCoefficient(DragFunction.valueOf(i.dragType.name), num(i.bc, "BC", true))
        }
        val ammo = Ammunition(
            projectile = Projectile(drag, massKg, diameterM, lengthM),
            muzzleVelocityMps = num(i.muzzleVelocity, "prędkość początkowa", true),
            powderSensitivityMpsPerC = num(i.powderSensitivity, "czułość prochu"),
            powderReferenceTempC = num(i.powderReferenceTemp, "temperatura odniesienia prochu"),
        )
        val rifle = Rifle(
            sightHeightM = num(i.sightHeightCm, "wysokość celownika") / 100,
            zeroRangeM = num(i.zeroRangeM, "odległość przystrzelania", true),
            twistM = i.twistIn.toNumber()?.takeIf { it > 0 }?.times(Units.INCH),
            rightHandTwist = i.rightHandTwist,
        )

        val altitude = num(i.altitudeM, "wysokość n.p.m.")
        val temperature = num(i.temperatureC, "temperatura")
        val pressurePa = num(i.pressureHpa, "ciśnienie", true) * Units.HPA
        val humidity = (num(i.humidityPct, "wilgotność") / 100).coerceIn(0.0, 1.0)
        val atmosphere = if (i.pressureIsQnh) {
            Atmosphere.fromSeaLevelPressure(pressurePa, altitude, temperature, humidity)
        } else {
            Atmosphere(altitude, pressurePa, temperature, humidity)
        }
        val zeroAtmosphere = Atmosphere(
            stationPressurePa = num(i.zeroPressureHpa, "ciśnienie przy przystrzelaniu", true) * Units.HPA,
            temperatureC = num(i.zeroTemperatureC, "temperatura przy przystrzelaniu"),
        )

        val windSpeed = num(i.windSpeed, "prędkość wiatru")
        val winds = if (windSpeed != 0.0) listOf(Wind.fromClock(windSpeed, num(i.windClock, "kierunek wiatru"))) else emptyList()

        val conditions = ShotConditions(
            atmosphere = atmosphere,
            winds = winds,
            lookAngleRad = num(i.lookAngleDeg, "kąt strzału") * Units.DEG,
            cantRad = num(i.cantDeg, "przechył") * Units.DEG,
            latitudeRad = if (i.coriolis) num(i.latitudeDeg, "szerokość geograficzna") * Units.DEG else null,
            azimuthRad = if (i.coriolis) num(i.azimuthDeg, "azymut") * Units.DEG else 0.0,
        )
        val maxRange = num(i.maxRangeM, "zasięg tabeli", true)
        val step = num(i.stepM, "krok tabeli", true)
        if (maxRange / step > 1000) throw InputError("Za mały krok tabeli")

        val options = SolverOptions(
            maxRangeM = maxRange,
            stepM = step,
            spinDrift = i.spinDrift,
            aerodynamicJump = i.aerodynamicJump,
            coriolis = i.coriolis,
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
