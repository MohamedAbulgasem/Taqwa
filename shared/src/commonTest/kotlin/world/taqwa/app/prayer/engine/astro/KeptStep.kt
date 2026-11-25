package world.taqwa.app.prayer.engine.astro

/**
 * The moment [SunClock.crossing] keeps ([Crossing.kept]): the iteration's step, or the bisection's moment where it found no
 * step. Before the final review of 20 Nov 2026 every time read from a crossing [SunClock] found was read on that moment; the
 * app no longer reads it on its own (a start reads [Crossing.start], an end [Crossing.end]), and on a night the sun only
 * grazes an altitude, where a settled step was found before, it is now null with the crossing. Tests only: where a test
 * means the sun's moment at a place and day well clear of the sun's culminations, all three are the same moment.
 */
fun SunClock.altitudeTime(altitudeDeg: Double, morning: Boolean, declinationBiasDeg: Double = 0.0): Double? =
    crossing(altitudeDeg, morning, declinationBiasDeg)?.kept
