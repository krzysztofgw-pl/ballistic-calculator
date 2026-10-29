package pl.ballistics.core

/**
 * Sight height (bore axis to scope axis) helpers. The value matters most at
 * short range and for the zero angle, so it pays to get it within a millimetre.
 */
object ScopeMount {
    /** Objective bell outer diameter is typically the clear aperture plus ~8 mm of housing. */
    private const val BELL_WALL_M = 0.008

    /**
     * Caliper method: measure the gap between the top of the bolt body and the
     * bottom of the scope tube, then add both radii.
     */
    fun measuredSightHeightM(gapM: Double, boltDiameterM: Double, tubeDiameterM: Double): Double =
        gapM + boltDiameterM / 2 + tubeDiameterM / 2

    /**
     * Lowest ring axis height above the rail top that clears the objective bell
     * by [clearanceM].
     */
    fun minRingAxisHeightM(objectiveMm: Double, clearanceM: Double = 0.003): Double =
        (objectiveMm / 1000 + BELL_WALL_M) / 2 + clearanceM

    /** Estimate from the mount: rail top above bore axis plus ring axis height above the rail. */
    fun estimatedSightHeightM(railAboveBoreM: Double, ringAxisHeightM: Double): Double =
        railAboveBoreM + ringAxisHeightM
}
