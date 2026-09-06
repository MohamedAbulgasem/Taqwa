package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
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
    fun locationSourceDefaultsToManualSoTheToggleIsOffBeforeAnyoneGrantsAnything() = runTest {
        assertEquals(LocationSource.MANUAL, repo("location-source-default").locationSource.first())
    }

    @Test
    fun locationSourceRoundTripsBothWays() = runTest {
        val r = repo("location-source-round-trip")
        r.setLocationSource(LocationSource.GPS)
        assertEquals(LocationSource.GPS, r.locationSource.first())
        // And back: turning "Use my location" off is a real, persisted state, not the absence
        // of one — which was the whole bug.
        r.setLocationSource(LocationSource.MANUAL)
        assertEquals(LocationSource.MANUAL, r.locationSource.first())
    }

    @Test
    fun storingALocationLeavesTheSourceAloneSoACancelledCitySearchCannotFlipTheToggle() = runTest {
        val r = repo("location-source-independent")
        r.setLocationSource(LocationSource.GPS)
        r.setLocation(GeoLocation(51.5, -0.12, "Europe/London", "London", "GB"))
        assertEquals(LocationSource.GPS, r.locationSource.first())
    }

    @Test
    fun aGpsFixInSaudiArabiaSelectsUmmAlQura() = runTest {
        val repo = repo("method-country-sa")
        repo.applyCountryDefaultMethod("SA")
        assertEquals(CalculationMethodId.UMM_AL_QURA, repo.prayerSettings.first().method)
    }

    @Test
    fun aCountryWithNoDominantAuthorityFallsBackToMuslimWorldLeague() = runTest {
        val repo = repo("method-country-za")
        repo.applyCountryDefaultMethod("ZA")
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, repo.prayerSettings.first().method)
    }

    @Test
    fun relocationNeverOverwritesAMethodTheUserChose() = runTest {
        val repo = repo("method-user-chosen")
        repo.setPrayerSettings(PrayerSettings(method = CalculationMethodId.MOONSIGHTING_COMMITTEE))
        repo.setMethodUserChosen()
        // Moving to Saudi Arabia would otherwise pull the method to Umm al-Qura.
        val applied = repo.applyCountryDefaultMethod("SA")
        assertEquals(CalculationMethodId.MOONSIGHTING_COMMITTEE, applied)
        assertEquals(
            CalculationMethodId.MOONSIGHTING_COMMITTEE,
            repo.prayerSettings.first().method,
        )
    }

    @Test
    fun writingPrayerSettingsDoesNotByItselfCountAsChoosingAMethod() = runTest {
        val repo = repo("method-not-chosen-by-madhab")
        // Every control on the prayer-times screen writes the whole PrayerSettings; only the
        // method picker latches the flag.
        repo.setPrayerSettings(PrayerSettings(madhab = AsrMadhab.HANAFI))
        assertEquals(false, repo.methodUserChosen.first())
        repo.applyCountryDefaultMethod("TR")
        assertEquals(CalculationMethodId.TURKEY, repo.prayerSettings.first().method)
    }

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

    @Test
    fun minuteAdjustmentsDefaultToEmpty() = runTest {
        assertEquals(emptyMap(), repo("adj-default").prayerSettings.first().minuteAdjustments)
    }

    @Test
    fun minuteAdjustmentsSurviveARoundTrip() = runTest {
        val r = repo("adj-roundtrip")
        r.setPrayerSettings(
            PrayerSettings(minuteAdjustments = mapOf(Prayer.FAJR to 5, Prayer.ISHA to -3)),
        )
        assertEquals(
            mapOf(Prayer.FAJR to 5, Prayer.ISHA to -3),
            r.prayerSettings.first().minuteAdjustments,
        )
    }

    @Test
    fun aMalformedStoredAdjustmentYieldsAnEmptyMapRatherThanCrashing() = runTest {
        val r = repo("adj-corrupt")
        r.writeRawMinuteAdjustmentsForTest("NOON:5,FAJR:later,,:::")
        assertEquals(emptyMap(), r.prayerSettings.first().minuteAdjustments)
    }

    @Test
    fun widgetBackgroundDefaultsToFollowTheme() = runTest {
        assertEquals(world.taqwa.app.domain.WidgetBackground.FOLLOW_THEME, repo("widget-default").widgetBackground.first())
    }

    @Test
    fun widgetBackgroundRoundTrips() = runTest {
        val r = repo("widget-roundtrip")
        r.setWidgetBackground(world.taqwa.app.domain.WidgetBackground.DARK)
        assertEquals(world.taqwa.app.domain.WidgetBackground.DARK, r.widgetBackground.first())
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
