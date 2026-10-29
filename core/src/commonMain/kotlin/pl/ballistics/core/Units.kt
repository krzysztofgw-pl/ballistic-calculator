package pl.ballistics.core

import kotlin.math.PI

/** Conversion factors to SI. The solver works in SI units only; convert at the edges. */
object Units {
    const val GRAIN = 6.479891e-5           // kg
    const val INCH = 0.0254                 // m
    const val FOOT = 0.3048                 // m
    const val YARD = 0.9144                 // m
    const val MPH = 0.44704                 // m/s
    const val FPS = 0.3048                  // m/s
    const val HPA = 100.0                   // Pa
    const val INHG = 3386.389               // Pa
    const val FT_LBF = 1.3558179483         // J

    /** 1 lb/in² expressed in kg/m², for ballistic coefficients. */
    const val LB_PER_IN2 = 703.0695796

    const val MOA = PI / 10800.0            // rad
    const val MRAD = 1e-3                   // rad
    const val DEG = PI / 180.0              // rad

    fun fahrenheitToCelsius(f: Double) = (f - 32.0) * 5.0 / 9.0
    fun celsiusToFahrenheit(c: Double) = c * 9.0 / 5.0 + 32.0
}
