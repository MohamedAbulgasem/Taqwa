package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
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
    fun theCityIdRoundTripsAlongsideTheEnglishName() = runTest {
        val r = repo("loc-city-id-roundtrip")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB", cityId = 2643743))
        val got = r.location.first()!!
        assertEquals(2643743, got.cityId)
        // The name stays the English snapshot: the id is what the language follows, not a
        // replacement for it.
        assertEquals("London", got.cityName)
    }

    @Test
    fun aLocationStoredWithoutACityIdReadsBackWithNull() = runTest {
        val r = repo("loc-city-id-absent")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB"))
        assertEquals(null, r.location.first()!!.cityId)
    }

    @Test
    fun anIdBackfilledOnItsOwnLeavesTheRestOfTheLocationAlone() = runTest {
        val r = repo("loc-city-id-backfill")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB"))
        r.setLocationCityId(2643743)
        val got = r.location.first()!!
        assertEquals(2643743, got.cityId)
        assertEquals("London", got.cityName)
        assertEquals("Europe/London", got.timeZoneId)
        assertEquals("GB", got.countryCode)
    }

    @Test
    fun aLaterLocationWithNoCityClearsTheIdRatherThanKeepingTheOldCitys() = runTest {
        val r = repo("loc-city-id-cleared")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB", cityId = 2643743))
        r.setLocation(GeoLocation(30.0, 31.0, "Africa/Cairo"))
        assertEquals(null, r.location.first()!!.cityId)
    }

    // -- the name the header remembers (D1) ----------------------------------------------------

    @Test
    fun aFreshInstallRemembersNoDisplayNameAtAll() = runTest {
        assertEquals(null, repo("loc-city-name-absent").resolvedCityName.first())
    }

    @Test
    fun theDisplayNameRoundTripsWithTheLanguageItWasResolvedIn() = runTest {
        val r = repo("loc-city-name-roundtrip")
        r.setLocation(
            GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB", cityId = 2643743),
            ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"),
        )
        assertEquals(ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"), r.resolvedCityName.first())
        // Still only a caption for the id: the English snapshot underneath is untouched.
        assertEquals("London", r.location.first()!!.cityName)
    }

    @Test
    fun aNameWrittenOnItsOwnLeavesTheRestOfTheLocationAlone() = runTest {
        val r = repo("loc-city-name-write")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB", cityId = 2643743))
        r.setResolvedCityName(ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"))
        val got = r.location.first()!!
        assertEquals(2643743, got.cityId)
        assertEquals("London", got.cityName)
        assertEquals("Europe/London", got.timeZoneId)
        assertEquals("\u0644\u0646\u062F\u0646", r.resolvedCityName.first()!!.name)
    }

    @Test
    fun replacingTheLocationClearsTheRememberedNameSoItCannotOutliveItsCity() = runTest {
        val r = repo("loc-city-name-cleared")
        r.setLocation(
            GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB", cityId = 2643743),
            ResolvedCityName("\u0644\u0646\u062F\u0646", "ar-LY"),
        )
        // A GPS fix that landed nowhere near a bundled city: no id, and so no name either.
        r.setLocation(GeoLocation(30.0, 31.0, "Africa/Cairo"))
        assertEquals(null, r.location.first()!!.cityId)
        assertEquals(null, r.resolvedCityName.first(), "London's Arabic name survived the move")
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

    @Test
    fun readingSettingsDefaultToTheDeviceLanguageWhenNothingIsStored() = runTest {
        val d = repo("reading-default").readingSettings("ar-LY").first()
        assertEquals(ReadingMode.MUSHAF, d.mode)
        assertEquals("ar.muyassar", d.translationId)
    }

    @Test
    fun readingSettingsRoundTripAndOverrideOnlyTheStoredKeys() = runTest {
        val r = repo("reading-roundtrip")
        // Only the size is ever written — mode and translation must keep following the
        // language default, not silently pin to whatever ReadingSettings() defaults to.
        val current = r.readingSettings("ar-LY").first()
        r.setReadingSettings(current.copy(arabicSizeSp = 32))
        val after = r.readingSettings("ar-LY").first()
        assertEquals(32, after.arabicSizeSp)
        assertEquals(ReadingMode.MUSHAF, after.mode)
        assertEquals("ar.muyassar", after.translationId)
    }

    @Test
    fun readingPositionIsNullUntilAllThreeKeysAreStored() = runTest {
        assertEquals(null, repo("reading-position-empty").readingPosition.first())
    }

    // The merge is field by field: a store holding only the size must still hand back the
    // language's own defaults for the two keys it never saw, not ReadingSettings()'s English ones.
    @Test
    fun aPartiallyWrittenStoreFallsBackPerFieldToTheLanguageDefaults() = runTest {
        val store = PreferenceDataStoreFactory.createWithPath {
            "/tmp/taqwa-test-reading-partial.preferences_pb".toPath()
        }
        store.edit { it.clear(); it[SettingsKeys.QURAN_SIZE] = 34 }
        val settings = SettingsRepository(store).readingSettings("ar-EG").first()
        assertEquals(34, settings.arabicSizeSp)
        assertEquals(ReadingMode.MUSHAF, settings.mode)
        assertEquals("ar.muyassar", settings.translationId)
        assertEquals(false, settings.transliteration)
    }

    @Test
    fun readingPositionStaysNullWhenOnlyTwoOfTheThreeKeysExist() = runTest {
        val store = PreferenceDataStoreFactory.createWithPath {
            "/tmp/taqwa-test-reading-position-partial.preferences_pb".toPath()
        }
        store.edit { it.clear(); it[SettingsKeys.QURAN_LAST_SURAH] = 2; it[SettingsKeys.QURAN_LAST_AYAH] = 255 }
        assertEquals(null, SettingsRepository(store).readingPosition.first())
        store.edit { it[SettingsKeys.QURAN_LAST_PAGE] = 42 }
        assertEquals(ReadingPosition(surah = 2, ayah = 255, page = 42), SettingsRepository(store).readingPosition.first())
    }

    @Test
    fun readingPositionRoundTrips() = runTest {
        val r = repo("reading-position-roundtrip")
        r.setReadingPosition(ReadingPosition(surah = 2, ayah = 255, page = 42))
        assertEquals(ReadingPosition(surah = 2, ayah = 255, page = 42), r.readingPosition.first())
    }

    /**
     * A GPS fix arrives with metre precision and prayer times do not need it: three decimals is
     * about 110 m, which moves no prayer time by a second and no qibla bearing by a visible
     * amount. Storing the rounded value is the "we could not reconstruct your street even if the
     * file leaked" half of the location promise.
     */
    @Test fun storedCoordinatesAreRoundedToThreeDecimals() = runTest {
        val r = repo("loc-rounding")
        r.setLocation(GeoLocation(51.5074123, -0.1278456, "Europe/London", "London", "GB"))
        val got = r.location.first()!!
        assertEquals(51.507, got.latitude)
        assertEquals(-0.128, got.longitude)
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
    fun theAdhanVoiceDefaultsToTheOriginalRecording() = runTest {
        assertEquals(AdhanVoice.ORIGINAL, repo("voice-default").notificationSettings.first().voice)
    }

    @Test
    fun tahajjudIsOffUntilSomeoneTurnsItOn() = runTest {
        val s = repo("tahajjud-default").notificationSettings.first()
        assertEquals(false, s.tahajjud)
        assertEquals(PrayerSound.NOTIFICATION, s.tahajjudSound)
    }

    @Test
    fun tahajjudAndItsSoundRoundTrip() = runTest {
        val r = repo("tahajjud-roundtrip")
        r.setNotificationSettings(
            r.notificationSettings.first().copy(tahajjud = true, tahajjudSound = PrayerSound.TAKBIR),
        )
        val s = r.notificationSettings.first()
        assertEquals(true, s.tahajjud)
        assertEquals(PrayerSound.TAKBIR, s.tahajjudSound)
    }

    @Test
    fun aStoredTahajjudSoundItDoesNotOfferFallsBackToTheChime() = runTest {
        val r = repo("tahajjud-adhan")
        r.setNotificationSettings(
            r.notificationSettings.first().copy(tahajjud = true, tahajjudSound = PrayerSound.ADHAN),
        )
        assertEquals(PrayerSound.NOTIFICATION, r.notificationSettings.first().tahajjudSound)
    }

    @Test
    fun theAdhanVoiceRoundTrips() = runTest {
        val r = repo("voice-roundtrip")
        r.setNotificationSettings(r.notificationSettings.first().copy(voice = AdhanVoice.AZEEZ))
        assertEquals(AdhanVoice.AZEEZ, r.notificationSettings.first().voice)
    }

    @Test
    fun anUnknownStoredAdhanVoiceFallsBackToTheOriginal() = runTest {
        val r = repo("corrupt-voice")
        r.writeRawAdhanVoiceForTest("MUEZZIN_OF_MARS")
        assertEquals(AdhanVoice.ORIGINAL, r.notificationSettings.first().voice)
    }

    @Test
    fun choosingAVoiceLeavesThePerPrayerSoundsAlone() = runTest {
        val r = repo("voice-independent")
        val s = r.notificationSettings.first()
        r.setNotificationSettings(s.copy(sounds = s.sounds + (Prayer.FAJR to PrayerSound.ADHAN)))
        r.setNotificationSettings(r.notificationSettings.first().copy(voice = AdhanVoice.AZEMI))
        val back = r.notificationSettings.first()
        assertEquals(PrayerSound.ADHAN, back.soundFor(Prayer.FAJR))
        assertEquals(AdhanVoice.AZEMI, back.voice)
    }

    @Test
    fun theLeadOptionsStartAtNever() {
        assertEquals(0, NotificationSettings.LeadOptions.first())
    }

}
