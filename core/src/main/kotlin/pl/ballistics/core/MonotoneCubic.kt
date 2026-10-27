package pl.ballistics.core

/**
 * Monotone piecewise-cubic Hermite interpolation (Fritsch–Carlson / PCHIP).
 * Smooth like a spline, but never overshoots between tabulated drag points.
 * Outside the table the end values are held constant.
 */
class MonotoneCubic(private val xs: DoubleArray, private val ys: DoubleArray) {
    private val slopes: DoubleArray

    init {
        require(xs.size == ys.size && xs.size >= 2) { "Need at least two points" }
        for (i in 1 until xs.size) require(xs[i] > xs[i - 1]) { "x must be strictly increasing" }
        val n = xs.size
        val delta = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        slopes = DoubleArray(n)
        slopes[0] = delta[0]
        slopes[n - 1] = delta[n - 2]
        for (i in 1 until n - 1) {
            if (delta[i - 1] * delta[i] <= 0.0) {
                slopes[i] = 0.0
            } else {
                // Weighted harmonic mean keeps the interpolant monotone.
                val h0 = xs[i] - xs[i - 1]
                val h1 = xs[i + 1] - xs[i]
                val w1 = 2 * h1 + h0
                val w2 = h1 + 2 * h0
                slopes[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])
            }
        }
    }

    operator fun invoke(x: Double): Double {
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[ys.size - 1]
        var lo = 0
        var hi = xs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] > x) hi = mid else lo = mid
        }
        val h = xs[hi] - xs[lo]
        val t = (x - xs[lo]) / h
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[lo] +
            (t3 - 2 * t2 + t) * h * slopes[lo] +
            (-2 * t3 + 3 * t2) * ys[hi] +
            (t3 - t2) * h * slopes[hi]
    }

    companion object {
        /** Builds an interpolant from a flat array of (x, y) pairs. */
        fun fromPairs(pairs: DoubleArray): MonotoneCubic {
            require(pairs.size % 2 == 0)
            return MonotoneCubic(
                DoubleArray(pairs.size / 2) { pairs[2 * it] },
                DoubleArray(pairs.size / 2) { pairs[2 * it + 1] },
            )
        }
    }
}
