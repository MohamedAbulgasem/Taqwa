package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    // DataStore's OkioStorage requires an absolute path (it asserts Path.isAbsolute). The
    // brief's literal "build/test-$name.preferences_pb" is relative, which happens to work on
    // the JVM (cwd == project dir) but is rejected at runtime on iosSimulatorArm64Test with
    // "OkioStorage requires absolute paths, but did not get an absolute path". The Kotlin/Native
    // test binary is a bare executable (not an app-sandboxed .app bundle) that runs directly on
    // the host Mac, so it shares the same POSIX filesystem as the JVM test process — "/tmp" is
    // writable and absolute on both. Each test uses a distinct filename, matching the brief's
    // intent of isolated per-test storage that never ends up committed to the repo.
    private fun repo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-test-$name.preferences_pb".toPath() }
    )

    @Test
    fun themeModeDefaultsToSystem() = runTest {
        assertEquals(ThemeMode.SYSTEM, repo("theme-default").themeMode.first())
    }

    @Test
    fun themeModeRoundTrips() = runTest {
        val r = repo("theme-roundtrip")
        r.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, r.themeMode.first())
    }

    @Test
    fun madhabDefaultsToStandard() = runTest {
        assertEquals(AsrMadhab.STANDARD, repo("madhab").prayerSettings.first().madhab)
    }

    @Test
    fun showSunriseDefaultsToOff() = runTest {
        assertEquals(false, repo("sunrise").prayerSettings.first().showSunrise)
    }

    @Test
    fun unknownStoredValueFallsBackToDefaultRatherThanCrashing() = runTest {
        val r = repo("corrupt")
        r.setThemeMode(ThemeMode.DARK)
        // simulate a value written by a future version
        r.writeRawThemeForTest("PLAID")
        assertEquals(ThemeMode.SYSTEM, r.themeMode.first())
    }

    @Test
    fun locationIsNullUntilOneIsChosen() = runTest {
        assertEquals(null, repo("loc-empty").location.first())
    }

    @Test
    fun locationRoundTripsIncludingTimezone() = runTest {
        val r = repo("loc-roundtrip")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB"))
        val got = r.location.first()!!
        assertEquals("Europe/London", got.timeZoneId)
        assertEquals("London", got.cityName)
    }
}

class NotificationSettingsStorageTest {

    // See the absolute-path note on SettingsRepositoryTest.repo — DataStore requires an
    // absolute path on both the JVM and iosSimulatorArm64Test.
    private fun repo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-test-notif-$name.preferences_pb".toPath() }
    )

    @Test
    fun everyPrayerDefaultsToTakbir() = runTest {
        val s = repo("defaults").notificationSettings.first()
        ObligatoryPrayers.forEach { assertEquals(PrayerSound.TAKBIR, s.soundFor(it), "$it") }
    }

    @Test
    fun notificationsAreOnByDefault() = runTest {
        assertTrue(repo("enabled").notificationSettings.first().enabled)
    }

    @Test
    fun remindBeforeDefaultsToNever() = runTest {
        assertEquals(0, repo("lead").notificationSettings.first().remindBeforeMinutes)
    }

    @Test
    fun soundsRoundTripPerPrayer() = runTest {
        val r = repo("roundtrip")
        val s = r.notificationSettings.first()
        r.setNotificationSettings(
            s.copy(sounds = s.sounds + mapOf(Prayer.FAJR to PrayerSound.ADHAN,
                                             Prayer.ISHA to PrayerSound.SILENT)),
        )
        val back = r.notificationSettings.first()
        assertEquals(PrayerSound.ADHAN, back.soundFor(Prayer.FAJR))
        assertEquals(PrayerSound.SILENT, back.soundFor(Prayer.ISHA))
        assertEquals(PrayerSound.TAKBIR, back.soundFor(Prayer.ASR))
    }

    @Test
    fun writingPrayerSettingsDoesNotResetTheChosenSounds() = runTest {
        val r = repo("independent")
        val s = r.notificationSettings.first()
        r.setNotificationSettings(s.copy(sounds = s.sounds + (Prayer.FAJR to PrayerSound.ADHAN)))
        r.setPrayerSettings(PrayerSettings(hijriOffsetDays = 1))
        assertEquals(PrayerSound.ADHAN, r.notificationSettings.first().soundFor(Prayer.FAJR))
    }

    @Test
    fun anUnknownStoredSoundFallsBackToTakbirRatherThanCrashing() = runTest {
        val r = repo("corrupt-sound")
        r.writeRawSoundForTest(Prayer.MAGHRIB, "TRUMPET")
        assertEquals(PrayerSound.TAKBIR, r.notificationSettings.first().soundFor(Prayer.MAGHRIB))
    }

    @Test
    fun theLeadOptionsStartAtNever() {
        assertEquals(0, NotificationSettings.LeadOptions.first())
    }
}
