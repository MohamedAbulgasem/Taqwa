package world.taqwa.app

import world.taqwa.app.nav.Screen
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * About these times links to Prayer times settings from wherever it was opened, except from that
 * screen itself: there the link would only stack it a second time, and Back is the way there.
 */
class AboutPrayerTimesLinkTest {

    @Test
    fun fromThePrayerScreenTheLinkShows() {
        assertTrue(aboutOffersPrayerTimesSettings(listOf(Screen.Prayer, Screen.AboutTimes)))
    }

    @Test
    fun fromPrayerTimesSettingsTheLinkHides() {
        assertFalse(
            aboutOffersPrayerTimesSettings(listOf(Screen.Settings, Screen.PrayerTimesSettings, Screen.AboutTimes)),
        )
    }

    @Test
    fun aStackOfOneStillShowsIt() {
        assertTrue(aboutOffersPrayerTimesSettings(listOf(Screen.AboutTimes)))
    }
}
