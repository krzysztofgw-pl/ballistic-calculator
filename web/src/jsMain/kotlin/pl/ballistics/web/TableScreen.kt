package pl.ballistics.web

import org.w3c.dom.HTMLElement
import pl.ballistics.core.TrajectoryPoint
import kotlin.math.abs
import kotlin.math.roundToInt

/** The range card for the active rifle, as on Android. */
object TableScreen {

    fun render(root: HTMLElement) {
        val out = root.el("div")
        root.card {
            val c = Store.data.conditions
            row {
                numberField(S.labelTableRange, c.maxRangeM, "m") { v -> Store.updateConditions { it.copy(maxRangeM = v) } }
                numberField(S.labelTableStep, c.stepM, "m") { v -> Store.updateConditions { it.copy(stepM = v) } }
            }
        }
        fun draw() {
            out.clear()
            table(out, Store.result)
        }
        draw()
        Store.onChange(::draw)
    }

    private fun table(root: HTMLElement, result: CalcResult) {
        when (result) {
            is CalcResult.Error -> root.card { el("p", "error", result.message) }
            is CalcResult.Ok -> {
                val s = result.solution
                root.card {
                    el("div", "list-title", "${result.weapon.name} · ${result.ammo.name}")
                    el("p", "summary", listOfNotNull(
                        S.summaryMuzzleVelocity(s.muzzleVelocityMps.roundToInt()),
                        S.summaryDensityAltitude(s.densityAltitudeM.roundToInt()),
                        s.stabilityFactor?.let { S.summaryStability(fmt(it, 2)) },
                    ).joinToString("  ·  "))
                    s.stabilityFactor?.let {
                        if (it < 1.0) el("p", "error", S.warningUnstable) else if (it < 1.4) el("p", "error", S.warningMarginal)
                    }
                    el("p", "hint", S.tableLegend(result.unit.label, result.weapon.clickValue))
                }
                root.el("div", "table-wrap") {
                    el("table", "range-table") {
                        el("thead") {
                            el("tr") { S.columns.forEach { el("th", text = it) } }
                        }
                        el("tbody") {
                            s.points.forEach { p -> el("tr") { cells(p, result.unit.radians, result.clickValue).forEach { el("td", text = it) } } }
                        }
                    }
                }
            }
        }
    }

    private fun cells(p: TrajectoryPoint, unitRad: Double, click: Double?): List<String> {
        // Correction is the angle to dial: opposite to the impact offset.
        val up = -p.dropRad / unitRad
        val right = -p.windageRad / unitRad
        val shown = p.rangeM > 0
        return listOf(
            p.rangeM.roundToInt().toString(),
            fmt(p.dropM * 100, 1),
            if (shown) "↑" + fmt(up, 2) else "",
            if (shown && click != null) (up / click).roundToInt().toString() else "",
            fmt(p.windageM * 100, 1),
            if (shown) side(right) + fmt(abs(right), 2) else "",
            if (shown && click != null) side(right) + (abs(right) / click).roundToInt() else "",
            p.velocityMps.roundToInt().toString(),
            p.energyJ.roundToInt().toString(),
            fmt(p.timeS, 3),
            fmt(p.mach, 2),
        )
    }

    fun side(right: Double) = when {
        right > 0.005 -> "R"
        right < -0.005 -> "L"
        else -> ""
    }
}
