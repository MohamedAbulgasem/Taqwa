package world.taqwa.app.prayer.engine.method

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small Fourier series over the day of the year, such as Umm al-Qura's Fajr declination bias
 * (profiles-tested.md, data-umm-al-qura). [terms] holds `(a_k, b_k)` for k = 1, 2, …
 */
data class Harmonics(val c0: Double, val terms: List<Pair<Double, Double>>) {
    /** c0 + Σ (a_k cos kt + b_k sin kt), t = 2π (dayOfYear − 0.5) / daysInYear. */
    fun at(dayOfYear: Int, daysInYear: Int): Double {
        val t = 2.0 * PI * (dayOfYear - 0.5) / daysInYear
        var sum = c0
        terms.forEachIndexed { i, (a, b) ->
            val k = i + 1
            sum += a * cos(k * t) + b * sin(k * t)
        }
        return sum
    }
}
