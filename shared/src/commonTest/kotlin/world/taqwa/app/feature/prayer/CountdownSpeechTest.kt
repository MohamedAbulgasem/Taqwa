package world.taqwa.app.feature.prayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CountdownSpeechTest {

    @Test
    fun aCountdownSplitsIntoWholeHoursAndMinutes() {
        assertEquals(CountdownParts(4, 10), countdownParts(4.hours + 10.minutes + 2.seconds))
        assertEquals(CountdownParts(0, 59), countdownParts(59.minutes + 59.seconds))
        assertEquals(CountdownParts(1, 0), countdownParts(60.minutes))
    }

    @Test
    fun underAMinuteIsItsOwnCase() {
        assertTrue(countdownParts(30.seconds).underAMinute)
        assertFalse(countdownParts(1.minutes).underAMinute)
        // A countdown that has just crossed zero speaks as "under a minute", never as negative.
        assertEquals(CountdownParts(0, 0), countdownParts((-5).seconds))
    }

    @Test
    fun aPrayerRowReadsNameThenPillsThenTime() {
        assertEquals("Fajr, 5:10", prayerRowDescription("Fajr", emptyList(), "5:10"))
        assertEquals("Dhuhr, Jumuʿah, 12:30", prayerRowDescription("Dhuhr", listOf("Jumuʿah"), "12:30"))
        assertEquals(
            "Isha, Set by rule, 23:10",
            prayerRowDescription("Isha", listOf("Set by rule"), "23:10"),
        )
    }
}
