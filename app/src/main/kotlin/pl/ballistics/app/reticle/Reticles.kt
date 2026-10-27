package pl.ballistics.app.reticle

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
 */
data class Reticle(
    val id: String,
    val name: String,
    val unit: AngularUnit,
    val extent: Double,
    val marks: List<Mark>,
)

object Reticles {
    val all: List<Reticle> by lazy {
        listOf(mradHash(), mradTree(), milDot(), moaHash(), moaTree(), duplex())
    }

    fun byId(id: String): Reticle = all.find { it.id == id } ?: all.first()

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

    private fun mradHash() = Reticle("mrad-hash", "MRAD, podziałka 0,2", AngularUnit.MRAD, 12.0, buildList<Mark> {
        tickedCross(10.0, 10.0, 10.0, 6.0, minor = 0.2, half = 0.5, major = 1.0, labelEvery = 2.0, tick = 0.3)
        add(Mark.Line(10.0, 0.0, 12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(-10.0, 0.0, -12.0, 0.0, Weight.HEAVY))
        add(Mark.Line(0.0, -10.0, 0.0, -12.0, Weight.HEAVY))
        add(Mark.Line(0.0, 6.0, 0.0, 12.0, Weight.HEAVY))
    })

    /** Holdover grid: a dot every 1 mil, rows widening with drop, like modern "tree" reticles. */
    private fun mradTree() = Reticle("mrad-tree", "MRAD, drzewko", AngularUnit.MRAD, 12.0, buildList<Mark> {
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

    private fun milDot() = Reticle("mil-dot", "Mil-Dot", AngularUnit.MRAD, 8.0, buildList<Mark> {
        add(Mark.Line(-5.0, 0.0, 5.0, 0.0))
        add(Mark.Line(0.0, -5.0, 0.0, 5.0))
        for (k in 1..4) {
            val v = k.toDouble()
            add(Mark.Dot(v, 0.0, 0.125)); add(Mark.Dot(-v, 0.0, 0.125))
            add(Mark.Dot(0.0, v, 0.125)); add(Mark.Dot(0.0, -v, 0.125))
        }
        posts(5.0, 8.0)
    })

    private fun moaHash() = Reticle("moa-hash", "MOA, podziałka 2", AngularUnit.MOA, 40.0, buildList<Mark> {
        tickedCross(30.0, 30.0, 34.0, 20.0, minor = 2.0, half = 10.0, major = 10.0, labelEvery = 10.0, tick = 1.0)
        posts(34.0, 40.0)
    })

    private fun moaTree() = Reticle("moa-tree", "MOA, drzewko", AngularUnit.MOA, 40.0, buildList<Mark> {
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

    /** Classic hunting duplex: fine centre, heavy outer posts. */
    private fun duplex() = Reticle("duplex", "Duplex", AngularUnit.MOA, 40.0, buildList<Mark> {
        add(Mark.Line(-12.0, 0.0, 12.0, 0.0))
        add(Mark.Line(0.0, -12.0, 0.0, 12.0))
        posts(12.0, 40.0)
    })
}
