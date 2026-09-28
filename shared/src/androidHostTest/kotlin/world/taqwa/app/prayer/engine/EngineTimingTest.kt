package world.taqwa.app.prayer.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.registry.Place
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * What a month view costs on the JVM (Task 8): 30 days at London and at Tromsø, from a cold cache
 * and again from a warm one. High latitudes cost most, since the 20 min/day limit looks back over
 * earlier days; iOS runs these about eight times slower. Printed for the report; the bound is loose,
 * only there to catch a regression by an order of magnitude.
 */
class EngineTimingTest {

    private val london = Place(51.5074, -0.1278, "Europe/London", "GB")
    private val tromso = Place(69.6492, 18.9553, "Europe/Oslo", "NO")

    private fun month(place: Place, first: LocalDate): Duration = measureTime {
        repeat(30) { PrayerEngine.dayTimes(place, first.plus(it, DateTimeUnit.DAY), EngineSettings()) }
    }

    @Test
    fun `a month view at london and at tromso`() {
        // Warm the JIT and the registry on another month first, so the numbers are the engine's.
        PrayerEngine.clearCache()
        month(london, LocalDate(2025, 1, 1))
        month(tromso, LocalDate(2025, 1, 1))
        val report = buildString {
            for ((name, place) in listOf("London" to london, "Tromsø" to tromso)) {
                for (first in listOf(LocalDate(2026, 6, 1), LocalDate(2026, 12, 1), LocalDate(2026, 9, 1))) {
                    PrayerEngine.clearCache()
                    val cold = month(place, first)
                    val warm = month(place, first)
                    appendLine("$name from $first: cold $cold, warm $warm")
                    assertTrue(cold < 10.seconds, "$name from $first took $cold")
                }
            }
        }
        println("EngineTimingTest\n$report")
    }
}
