package world.taqwa.app.feature.settings

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.registry.Place
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * What choosing a timetable costs on the JVM (Task 12): the earlier-than check reads a chosen
 * entry's own day and Automatic's on every day of the next 12 months. Automatic's year is kept for
 * the next choice, so the first choice at a place pays for both and every later one for its own
 * alone. iOS runs about eight times slower. Printed for the report; the bound is loose, only there
 * to catch a regression by an order of magnitude.
 */
class TimetableCheckTimingTest {

    private val places = listOf(
        "London" to Place(51.5074, -0.1278, "Europe/London", "GB"),
        "Manchester (cautious)" to Place(53.48095, -2.23743, "Europe/London", "GB"),
        "Tromsø (IRN's Makkah time, polar)" to Place(69.6492, 18.9553, "Europe/Oslo", "NO"),
    )

    private fun check(place: Place, id: String, from: LocalDate): Pair<TimetableCheck, Duration> = measureTimedValue {
        runBlocking {
            checkAgainstAutomatic(from, { d ->
                val own = PrayerEngine.ownDay(place, d, TimetableChoice.Entry(id), EngineSettings())
                val auto = PrayerEngine.ownDay(place, d, TimetableChoice.Automatic, EngineSettings())
                if (own == null || auto == null) null else own to auto
            })
        }
    }.let { it.value to it.duration }

    @Test
    fun aYearsCheckAtLondonManchesterAndTromso() {
        // Warm the JIT and the registry on another year first, so the numbers are the engine's.
        places.forEach { (_, place) -> check(place, "other.karachi", LocalDate(2025, 1, 1)) }
        PrayerEngine.clearCache()
        val report = buildString {
            for ((name, place) in places) {
                val (first, cold) = check(place, "other.mwl", LocalDate(2026, 10, 1))
                val (_, warm) = check(place, "other.isna", LocalDate(2026, 10, 1))
                appendLine("$name: first choice $cold ($first), the next $warm")
                assertTrue(cold < 10.seconds, "$name took $cold")
            }
        }
        println("TimetableCheckTimingTest\n$report")
    }
}
