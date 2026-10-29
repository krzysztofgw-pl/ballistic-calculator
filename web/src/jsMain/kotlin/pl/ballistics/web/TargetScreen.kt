package pl.ballistics.web

import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.math.abs
import kotlin.math.roundToInt

/** One shot: the corrections to dial or hold at the chosen distance. */
object TargetScreen {

    fun render(root: HTMLElement) {
        val out = root.el("div")
        val c = Store.data.conditions
        root.card(S.sectionTarget) {
            val max = c.maxRangeM.toNumber() ?: 1000.0
            lateinit var distance: HTMLInputElement
            lateinit var slider: HTMLInputElement
            fun setDistance(v: String, fromSlider: Boolean = false) {
                if (fromSlider) distance.value = v else v.toNumber()?.let { slider.value = it.toString() }
                Store.updateConditions { it.copy(targetRangeM = v) }
            }
            el("div", "distance-row") {
                button("−1", "btn tonal small") { (rangeOf(Store.data.conditions) - 1).coerceAtLeast(1.0).let { setDistance(it.roundToInt().toString()); distance.value = it.roundToInt().toString() } }
                distance = numberField(S.labelTargetDistance, rangeOf(c).roundToInt().toString(), "m") { setDistance(it) }
                button("+1", "btn tonal small") { (rangeOf(Store.data.conditions) + 1).let { setDistance(it.roundToInt().toString()); distance.value = it.roundToInt().toString() } }
            }
            slider = el("input", "slider") {
                this as HTMLInputElement
                type = "range"
                min = "10"
                this.max = max.roundToInt().toString()
                step = "5"
                value = rangeOf(c).toString()
                oninput = { setDistance(value, fromSlider = true); null }
            } as HTMLInputElement
            lateinit var fields: HTMLElement
            switch(S.labelMovingTarget, c.targetMoving) { v ->
                Store.updateConditions { it.copy(targetMoving = v) }
                fields.style.display = if (v) "" else "none"
            }
            fields = row {
                style.display = if (c.targetMoving) "" else "none"
                numberField(S.labelTargetSpeed, c.targetSpeedMps, "m/s") { v -> Store.updateConditions { it.copy(targetSpeedMps = v) } }
                numberField(S.labelTargetCourse, c.targetDirectionDeg, "°") { v -> Store.updateConditions { it.copy(targetDirectionDeg = v) } }
            }
        }
        root.card(S.sectionShot) { ConditionsScreen.shotAngle(this) }
        fun draw() {
            out.clear()
            solution(out)
        }
        draw()
        Store.onChange(::draw)
    }

    /** The picked distance; blank picks the middle of the table, as on Android. */
    private fun rangeOf(c: Conditions): Double =
        c.targetRangeM.toNumber()?.takeIf { it > 0 } ?: ((c.maxRangeM.toNumber() ?: 1000.0) / 2)

    private fun solution(root: HTMLElement) {
        val result = Store.result
        if (result is CalcResult.Error) {
            root.card { el("p", "error", result.message) }
            return
        }
        result as CalcResult.Ok
        val c = Store.data.conditions
        val distance = rangeOf(c)
        val zero = result.solution.zeroElevationRad
        val speed = c.targetSpeedMps.toNumber()?.takeIf { c.targetMoving && it != 0.0 }
        val lead = speed?.let { result.setup.lead(distance, zero, it, c.targetDirectionDeg.toNumber() ?: 90.0) }
        val point = lead?.point ?: result.setup.pointAt(distance, zero)
        if (point == null) {
            root.card { el("p", "error", S.targetOutOfReach(distance.roundToInt())) }
            return
        }
        val unit = result.unit
        val click = result.clickValue
        val correction = Store.data.ui.correction(result.weapon.reticleId)
        val up = -point.dropRad / unit.radians
        val right = (-point.windageRad + (lead?.leadRad ?: 0.0)) / unit.radians

        root.card {
            el("div", "advice") {
                adviceRow(S.labelElevation, if (up >= 0) "↑" else "↓", abs(up), unit, click, correction.dialElevation)
                adviceRow(S.labelWindage, TableScreen.side(right).ifEmpty { "" }, abs(right), unit, click, correction.dialWindage)
            }
            if (lead != null) {
                el("p", "hint", S.leadHint(fmt(abs(lead.leadM), 2), if (lead.leadM >= 0) S.right else S.left))
            }
            el("p", "summary", listOf(
                "${point.rangeM.roundToInt()} m",
                "${point.velocityMps.roundToInt()} m/s",
                "${point.energyJ.roundToInt()} J",
                "${fmt(point.timeS, 2)} s",
                "Mach ${fmt(point.mach, 2)}",
            ).joinToString("  ·  "))
            el("p", "hint", S.dropLine(fmt(point.dropM * 100, 1), fmt(point.windageM * 100, 1)))
        }
    }

    private fun HTMLElement.adviceRow(label: String, direction: String, value: Double, unit: AngularUnit, click: Double?, dial: Boolean) {
        el("div", "advice-row") {
            el("span", "advice-label", label)
            if (dial && click != null) {
                val clicks = (value / click).roundToInt()
                el("span", "advice-value", "$direction${fmt(clicks * click, 2)} ${unit.label}")
                el("span", "advice-sub", S.dialClicks(clicks))
            } else {
                // Holds to the nearest 0.05 of the unit, the finest a reticle can be read to.
                el("span", "advice-value", "$direction${fmt((value / 0.05).roundToInt() * 0.05, 2)} ${unit.label}")
                el("span", "advice-sub", S.hold)
            }
        }
    }
}
