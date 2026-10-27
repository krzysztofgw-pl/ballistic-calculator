package pl.ballistics.app.reticle

import androidx.annotation.StringRes
import pl.ballistics.app.R
import pl.ballistics.app.data.AngularUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

enum class Weight { FINE, MEDIUM, HEAVY }

/** Reticle elements in the reticle's own angular unit; x to the right, y up. */
sealed interface Mark {
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val weight: Weight = Weight.FINE) : Mark
    data class Dot(val x: Double, val y: Double, val r: Double) : Mark
    data class Label(val x: Double, val y: Double, val text: String) : Mark
}

/**
 * A generic reticle pattern. Subtensions are exact at the calibrated
 * magnification; in a first focal plane scope that is every magnification.
 *
 * @param extent half-width of the visible field, in [unit].
 * @param family reticles that are the same design in MRAD and MOA share a family.
 */
data class Reticle(
    val id: String,
    @StringRes val name: Int,
    val unit: AngularUnit,
    val extent: Double,
    val marks: List<Mark>,
    @StringRes val family: Int = name,
)

object Reticles {
    val all: List<Reticle> by lazy {
        val duplex = duplex()
        val dls2 = dls2()
        listOf(
            mradHash(), moaHash(),
            mradTree(), moaTree(),
            milDot(),
            duplex, inUnit(duplex, "duplex-mrad", R.string.reticle_duplex_mrad, AngularUnit.MRAD),
            dls1(), dls3(),
            dls2, inUnit(dls2, "dls-2-moa", R.string.reticle_dls_2_moa, AngularUnit.MOA),
            dlr3(),
        )
    }

    fun byId(id: String): Reticle = all.find { it.id == id } ?: all.first()

    /** One entry per design, in [unit] when the design comes in it. */
    fun families(unit: AngularUnit): List<Reticle> =
        all.groupBy { it.family }.values.map { v -> v.find { it.unit == unit } ?: v.first() }

    /** The MRAD and MOA versions of [reticle]'s design. */
    fun variants(reticle: Reticle): List<Reticle> = all.filter { it.family == reticle.family }

    /** [reticle]'s design in [unit], or [reticle] itself when there is no such version. */
    fun variant(reticle: Reticle, unit: AngularUnit): Reticle = variants(reticle).find { it.unit == unit } ?: reticle

    /** The same plain design measured in another unit; only for reticles without a scale. */
    private fun inUnit(r: Reticle, id: String, @StringRes name: Int, unit: AngularUnit): Reticle {
        val k = r.unit.radians / unit.radians
        return Reticle(id, name, unit, r.extent * k, r.marks.map { m ->
            when (m) {
                is Mark.Line -> m.copy(x1 = m.x1 * k, y1 = m.y1 * k, x2 = m.x2 * k, y2 = m.y2 * k)
                is Mark.Dot -> m.copy(x = m.x * k, y = m.y * k, r = m.r * k)
                is Mark.Label -> m.copy(x = m.x * k, y = m.y * k)
            }
        }, r.family)
    }

    private fun label(v: Double) = abs(v).let { if (it % 1.0 == 0.0) it.roundToInt().toString() else it.toString() }

    /** Crosshair with ticks every [minor], longer every [half] and [major]; numbers every [labelEvery]. */
    private fun MutableList<Mark>.tickedCross(
        left: Double, right: Double, down: Double, up: Double,
        minor: Double, half: Double, major: Double, labelEvery: Double, tick: Double,
    ) {
        add(Mark.Line(-left, 0.0, right, 0.0))
        add(Mark.Line(0.0, -down, 0.0, up))
        val n = floor(maxOf(left, right, down, up) / minor + 1e-9).toInt()
        for (i in 1..n) {
            val v = i * minor
            val len = when {
                isMultiple(v, major) -> tick
                isMultiple(v, half) -> tick * 0.7
                else -> tick * 0.4
            }
            if (v <= right + 1e-9) add(Mark.Line(v, -len, v, len))
            if (v <= left + 1e-9) add(Mark.Line(-v, -len, -v, len))
            if (v <= up + 1e-9) add(Mark.Line(-len, v, len, v))
            if (v <= down + 1e-9) add(Mark.Line(-len, -v, len, -v))
            if (isMultiple(v, labelEvery)) {
                if (v <= right + 1e-9) add(Mark.Label(v, -tick * 2.2, label(v)))
                if (v <= left + 1e-9) add(Mark.Label(-v, -tick * 2.2, label(v)))
                if (v <= down + 1e-9) add(Mark.Label(-tick * 2.6, -v, label(v)))
            }
        }
    }

    private fun isMultiple(v: Double, step: Double) = abs(v / step - (v / step).roundToInt()) < 1e-6

    private fun MutableList<Mark>.posts(from: Double, to: Double) {
        add(Mark.Line(from, 0.0, to, 0.0, Weight.HEAVY))
        add(Mark.Line(-from, 0.0, -to, 0.0, Weight.HEAVY))
        add(Mark.Line(0.0, from, 0.0, to, Weight.HEAVY))
        add(Mark.Line(0.0, -from, 0.0, -to, Weight.HEAVY))
    }

    private fun mradHash() = Reticle("mrad-hash", R.string.reticle_mrad_hash, AngularUnit.MRAD, 12.0, family = R.string.reticle_family_hash, marks = buildList<Mark> {
        tickedCross(10.0, 10.0, 10.0, 6.0, minor = 0.2, half = 0.5, major = 1.0, labelEvery = 2.0, tick = 0.3)
        add(Mark.Line(10.0, 0.0, 12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(-10.0, 0.0, -12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(0.0, -10.0, 0.0, -12.0, Weight.HEAVY))
        add(Mark.Line(0.0, 6.0, 0.0, 12.0, Weight.HEAVY))
    })

    /** Holdover grid: a dot every 1 mil, rows widening with drop, like modern "tree" reticles. */
    private fun mradTree() = Reticle("mrad-tree", R.string.reticle_mrad_tree, AngularUnit.MRAD, 12.0, family = R.string.reticle_family_tree, marks = buildList<Mark> {
        tickedCross(10.0, 10.0, 12.0, 6.0, minor = 0.2, half = 0.5, major = 1.0, labelEvery = 2.0, tick = 0.25)
        for (row in 1..11) {
            val y = -row.toDouble()
            val width = minOf(1 + row / 2, 6)
            for (k in 1..width) {
                add(Mark.Dot(k.toDouble(), y, 0.06))
                add(Mark.Dot(-k.toDouble(), y, 0.06))
                add(Mark.Line(k - 0.5, y - 0.08, k - 0.5, y + 0.08))
                add(Mark.Line(-(k - 0.5), y - 0.08, -(k - 0.5), y + 0.08))
            }
        }
        add(Mark.Line(10.0, 0.0, 12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(-10.0, 0.0, -12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(0.0, 6.0, 0.0, 12.0, Weight.HEAVY))
    })

    private fun milDot() = Reticle("mil-dot", R.string.reticle_mil_dot, AngularUnit.MRAD, 8.0, buildList<Mark> {
        add(Mark.Line(-5.0, 0.0, 5.0, 0.0))
        add(Mark.Line(0.0, -5.0, 0.0, 5.0))
        for (k in 1..4) {
            val v = k.toDouble()
            add(Mark.Dot(v, 0.0, 0.125)); add(Mark.Dot(-v, 0.0, 0.125))
            add(Mark.Dot(0.0, v, 0.125)); add(Mark.Dot(0.0, -v, 0.125))
        }
        posts(5.0, 8.0)
    })

    private fun moaHash() = Reticle("moa-hash", R.string.reticle_moa_hash, AngularUnit.MOA, 40.0, family = R.string.reticle_family_hash, marks = buildList<Mark> {
        tickedCross(30.0, 30.0, 34.0, 20.0, minor = 2.0, half = 10.0, major = 10.0, labelEvery = 10.0, tick = 1.0)
        posts(34.0, 40.0)
    })

    private fun moaTree() = Reticle("moa-tree", R.string.reticle_moa_tree, AngularUnit.MOA, 40.0, family = R.string.reticle_family_tree, marks = buildList<Mark> {
        tickedCross(30.0, 30.0, 38.0, 20.0, minor = 2.0, half = 10.0, major = 10.0, labelEvery = 10.0, tick = 0.9)
        for (row in 1..9) {
            val y = -4.0 * row
            val width = minOf(1 + row / 2, 6)
            for (k in 1..width) {
                add(Mark.Dot(4.0 * k, y, 0.25))
                add(Mark.Dot(-4.0 * k, y, 0.25))
            }
        }
        add(Mark.Line(30.0, 0.0, 40.0, 0.0, Weight.HEAVY))
        add(Mark.Line(-30.0, 0.0, -40.0, 0.0, Weight.HEAVY))
        add(Mark.Line(0.0, 20.0, 0.0, 40.0, Weight.HEAVY))
    })

    /**
     * Delta Optical Stryker HD DLS-1: MRAD, second focal plane, calibrated at 40x.
     * Delta publishes 0.2 and 1 MRAD markings, 0.005 MRAD lines and a 0.03 MRAD
     * centre point; the extent of the scale is drawn to the published diagram.
     */
    private fun dls1() = Reticle("dls-1", R.string.reticle_dls_1, AngularUnit.MRAD, 12.0, family = R.string.reticle_family_dls_1_3, marks = buildList<Mark> {
        tickedCross(10.0, 10.0, 10.0, 5.0, minor = 0.2, half = 0.5, major = 1.0, labelEvery = 5.0, tick = 0.25)
        add(Mark.Dot(0.0, 0.0, 0.015))
        add(Mark.Line(10.0, 0.0, 12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(-10.0, 0.0, -12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(0.0, -10.0, 0.0, -12.0, Weight.HEAVY))
        add(Mark.Line(0.0, 5.0, 0.0, 12.0, Weight.HEAVY))
    })

    /** DLS-2: the same scope with a plain fine cross and an illuminated centre point. */
    private fun dls2() = Reticle("dls-2", R.string.reticle_dls_2, AngularUnit.MRAD, 12.0, family = R.string.reticle_family_dls_2, marks = buildList<Mark> {
        add(Mark.Line(-10.0, 0.0, 10.0, 0.0))
        add(Mark.Line(0.0, -10.0, 0.0, 10.0))
        add(Mark.Dot(0.0, 0.0, 0.015))
    })

    /** DLS-3: the DLS-1 layout scaled in MOA, with 1 and 5 MOA markings. */
    private fun dls3() = Reticle("dls-3", R.string.reticle_dls_3, AngularUnit.MOA, 40.0, family = R.string.reticle_family_dls_1_3, marks = buildList<Mark> {
        tickedCross(34.0, 34.0, 34.0, 17.0, minor = 1.0, half = 5.0, major = 5.0, labelEvery = 10.0, tick = 0.9)
        add(Mark.Dot(0.0, 0.0, 0.05))
        add(Mark.Line(34.0, 0.0, 40.0, 0.0, Weight.HEAVY))
        add(Mark.Line(-34.0, 0.0, -40.0, 0.0, Weight.HEAVY))
        add(Mark.Line(0.0, -34.0, 0.0, -40.0, Weight.HEAVY))
        add(Mark.Line(0.0, 17.0, 0.0, 40.0, Weight.HEAVY))
    })

    /**
     * Delta Optical Stryker HD 3.5-21x44 DLR-3: MRAD, second focal plane,
     * calibrated at 21x. Drawn from Delta's published diagram: a broken centre,
     * 0.2 MRAD ticks out to 1, then 0.5 MRAD ticks, numbered at 5 and 10, a
     * T-bar at 3 MRAD up and heavy side posts from 5.5 MRAD.
     */
    private fun dlr3() = Reticle("dlr-3", R.string.reticle_dlr_3, AngularUnit.MRAD, 10.5, buildList<Mark> {
        // Broken centre: two short dashes on each arm around a fine dot.
        for ((from, to) in listOf(0.08 to 0.18, 0.26 to 0.34)) {
            add(Mark.Line(from, 0.0, to, 0.0)); add(Mark.Line(-from, 0.0, -to, 0.0))
            add(Mark.Line(0.0, from, 0.0, to)); add(Mark.Line(0.0, -from, 0.0, -to))
        }
        add(Mark.Dot(0.0, 0.0, 0.02))
        add(Mark.Line(0.42, 0.0, 5.5, 0.0)); add(Mark.Line(-0.42, 0.0, -5.5, 0.0))
        add(Mark.Line(0.0, 0.42, 0.0, 3.0)); add(Mark.Line(0.0, -0.42, 0.0, -10.5))
        add(Mark.Line(5.5, 0.0, 10.5, 0.0, Weight.HEAVY)); add(Mark.Line(-5.5, 0.0, -10.5, 0.0, Weight.HEAVY))

        fun len(v: Double) = when {
            abs(v - v.roundToInt()) < 1e-6 -> 0.16
            else -> 0.08
        }
        val stops = listOf(0.6, 0.8) + generateSequence(1.0) { it + 0.5 }.takeWhile { it <= 10.0 + 1e-9 }
        for (v in stops) {
            val l = len(v)
            if (v <= 5.0 + 1e-9) {
                val h = if (abs(v - 5.0) < 1e-6) 0.35 else l
                add(Mark.Line(v, -h, v, h)); add(Mark.Line(-v, -h, -v, h))
            }
            if (v < 3.0 - 1e-9) add(Mark.Line(-l, v, l, v))
            add(Mark.Line(-l, -v, l, -v))
        }
        add(Mark.Line(-0.3, 3.0, 0.3, 3.0))
        add(Mark.Line(-0.3, -5.0, 0.3, -5.0))
        add(Mark.Label(5.0, 0.8, "5")); add(Mark.Label(-5.0, 0.8, "5"))
        add(Mark.Label(0.6, 3.0, "3"))
        add(Mark.Label(0.6, -5.0, "5"))
        add(Mark.Label(0.7, -10.0, "10"))
    })

    /** Classic hunting duplex: fine centre, heavy outer posts. */
    private fun duplex() = Reticle("duplex", R.string.reticle_duplex, AngularUnit.MOA, 40.0, family = R.string.reticle_family_duplex, marks = buildList<Mark> {
        add(Mark.Line(-12.0, 0.0, 12.0, 0.0))
        add(Mark.Line(0.0, -12.0, 0.0, 12.0))
        posts(12.0, 40.0)
    })
}
