package world.taqwa.app.feature.prayer

import world.taqwa.app.i18n.UiLanguage
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
        assertEquals("Fajr, 5:10", prayerRowDescription("Fajr", emptyList(), "5:10", UiLanguage.ENGLISH))
        assertEquals("Dhuhr, Jumuʿah, 12:30", prayerRowDescription("Dhuhr", listOf("Jumuʿah"), "12:30", UiLanguage.ENGLISH))
        assertEquals(
            "Isha, Set by rule, 23:10",
            prayerRowDescription("Isha", listOf("Set by rule"), "23:10", UiLanguage.ENGLISH),
        )
    }

    /**
     * M8 point A5 (6 November 2026): in Arabic and Urdu, the interfaces written right to left, the
     * row's parts are joined by the Arabic comma "، ", as the watches' rows are (Ruling 64 item 6).
     */
    @Test
    fun anArabicOrUrduRowIsJoinedByTheArabicComma() {
        assertEquals("الفجر، ٥:١٠", prayerRowDescription("الفجر", emptyList(), "٥:١٠", UiLanguage.ARABIC))
        assertEquals("الظهر، الجمعة، ١٢:٣٠", prayerRowDescription("الظهر", listOf("الجمعة"), "١٢:٣٠", UiLanguage.ARABIC))
        assertEquals("عصر، ۱۶:۱۰", prayerRowDescription("عصر", emptyList(), "۱۶:۱۰", UiLanguage.URDU))
    }

    /**
     * The phone's half of A5 (Ruling 73 item 2): the rows hand over the interface's language
     * (`uiLanguage()`, the `ui_language` resource), and the join follows from it alone, so a row
     * that is told it runs left to right in Arabic or Urdu fails here. Every tag the resource or a
     * locale spells, each of the seven languages, written out rather than read off `rtl`.
     */
    @Test
    fun theRowsJoinFollowsTheInterfacesLanguage() {
        for (tag in listOf("ar", "ar-SA", "ar_EG", "ur", "ur-PK", "ur-IN")) {
            assertEquals("العصر، ١٦:١٠", prayerRowDescription("العصر", emptyList(), "١٦:١٠", UiLanguage.of(tag)), tag)
        }
        for (tag in listOf("en", "en-GB", "fr", "tr", "id", "in", "bn", "bn-BD")) {
            assertEquals("Asr, 16:10", prayerRowDescription("Asr", emptyList(), "16:10", UiLanguage.of(tag)), tag)
        }
    }
}
