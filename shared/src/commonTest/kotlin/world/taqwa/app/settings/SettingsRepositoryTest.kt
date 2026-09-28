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
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An empty store of its own for one test: a fixed name would carry what an earlier run (or another
 * worktree, or the other target) wrote into the next, so each store gets a random suffix, like the
 * other store tests in this module.
 *
 * DataStore's OkioStorage requires an absolute path (it asserts Path.isAbsolute): a relative
 * "build/test-$name.preferences_pb" happens to work on the JVM (cwd == project dir) but is rejected
 * at runtime on iosSimulatorArm64Test with "OkioStorage requires absolute paths, but did not get an
 * absolute path". The Kotlin/Native test binary is a bare executable (not an app-sandboxed .app
 * bundle) that runs directly on the host Mac, so it shares the same POSIX filesystem as the JVM test
 * process — "/tmp" is writable and absolute on both, and never ends up committed to the repo.
 */
private fun freshStore(name: String) = run {
    val path = "/tmp/taqwa-test-$name-${Random.nextULong()}.preferences_pb".toPath()
    PreferenceDataStoreFactory.createWithPath { path }
}

class SettingsRepositoryTest {

    private fun repo(name: String) = SettingsRepository(freshStore(name))

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

    // -- a location save never writes a method or a timetable (spec §8) -------------------------

    @Test
    fun aLocationSaveInSaudiArabiaWritesNoMethodAndLeavesTheTimetableAutomatic() = runTest {
        val store = freshStore("t9-method-country-sa")
        val repo = SettingsRepository(store)
        repo.setLocation(GeoLocation(24.7136, 46.6753, "Asia/Riyadh", countryCode = "SA"))
        assertEquals("automatic", repo.prayerSettings.first().timetable)
        assertEquals(null, store.data.first()[SettingsKeys.METHOD])
    }

    @Test
    fun relocationNeverOverwritesATimetableTheUserChose() = runTest {
        val repo = repo("t9-timetable-user-chosen")
        repo.setTimetable("other.moonsighting")
        repo.setLocation(GeoLocation(24.7136, 46.6753, "Asia/Riyadh", countryCode = "SA"))
        assertEquals("other.moonsighting", repo.prayerSettings.first().timetable)
    }

    @Test
    fun aPrayerSettingWriteNeverWritesTheOldMethodKeys() = runTest {
        // The old keys are an old install's, read once by the migration; nothing writes them now.
        val store = freshStore("t9-no-old-keys")
        val repo = SettingsRepository(store)
        repo.setSchool("hanafi")
        repo.setHijriOffsetDays(1)
        repo.setShowSunrise(true)
        repo.setMinuteAdjustments(mapOf(Prayer.ISHA to 2))
        val stored = store.data.first()
        assertEquals(null, stored[SettingsKeys.METHOD])
        assertEquals(null, stored[SettingsKeys.METHOD_USER_CHOSEN])
        assertEquals(null, stored[SettingsKeys.MADHAB])
        assertEquals("hanafi", repo.prayerSettings.first().school)
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
    fun theHijriOffsetRoundTripsWithinTheRangeTheScreenOffers() = runTest {
        val r = repo("hijri-offset")
        assertEquals(0, r.prayerSettings.first().hijriOffsetDays)
        r.setHijriOffsetDays(-1)
        assertEquals(-1, r.prayerSettings.first().hijriOffsetDays)
        r.setHijriOffsetDays(5)
        assertEquals(1, r.prayerSettings.first().hijriOffsetDays)
    }

    @Test
    fun showSunriseRoundTrips() = runTest {
        val r = repo("sunrise-roundtrip")
        r.setShowSunrise(true)
        assertEquals(true, r.prayerSettings.first().showSunrise)
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
    fun aBackfillFillsOnlyWhatTheLocationLacksAndLeavesTheRestAlone() = runTest {
        val r = repo("loc-city-id-backfill")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB"))
        val stored = r.location.first()!!
        // The region is new: the times may change (ruling R53).
        assertEquals(true, r.backfillLocation(stored, cityId = 2643743, countryCode = "FR", region = "England"))
        val got = r.location.first()!!
        assertEquals(2643743, got.cityId)
        assertEquals("England", got.region)
        assertEquals("London", got.cityName)
        assertEquals("Europe/London", got.timeZoneId)
        // A country it already had is kept.
        assertEquals("GB", got.countryCode)
        // Nothing left to fill: nothing that moves the times was written.
        assertEquals(false, r.backfillLocation(got, cityId = 1, countryCode = "FR", region = "Kent"))
    }

    @Test
    fun theBackfillIsRememberedUntilTheNextLocation() = runTest {
        val r = repo("loc-backfill-done")
        r.setLocation(GeoLocation(1.28967, 103.85007, "Asia/Singapore", "Singapore", "SG", cityId = 1880252))
        assertEquals(false, r.locationBackfilled.first())
        r.backfillLocation(r.location.first()!!, cityId = 1880252, countryCode = "SG", region = "")
        assertEquals(true, r.locationBackfilled.first())
        assertEquals(null, r.location.first()!!.region)
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB"))
        assertEquals(false, r.locationBackfilled.first())
    }

    @Test
    fun aBackfillForALocationSinceReplacedIsDropped() = runTest {
        val r = repo("loc-backfill-stale")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London"))
        val old = r.location.first()!!
        r.setLocation(GeoLocation(30.06263, 31.24967, "Africa/Cairo", "Cairo"))
        assertEquals(false, r.backfillLocation(old, cityId = 2643743, countryCode = "GB", region = "England"))
        val got = r.location.first()!!
        assertEquals(null, got.cityId)
        assertEquals(null, got.countryCode)
        assertEquals(null, got.region)
    }

    @Test
    fun theRegionRoundTripsAndALaterLocationWithoutOneClearsIt() = runTest {
        val r = repo("loc-region")
        r.setLocation(GeoLocation(36.75, 5.06, "Africa/Algiers", "Béjaïa", "DZ", cityId = 2505329, region = "Béjaïa"))
        assertEquals("Béjaïa", r.location.first()!!.region)
        r.setLocation(GeoLocation(30.0, 31.0, "Africa/Cairo"))
        assertEquals(null, r.location.first()!!.region)
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
        r.setMinuteAdjustments(mapOf(Prayer.FAJR to 5, Prayer.ISHA to -3))
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
        val store = freshStore("reading-partial")
        store.edit { it.clear(); it[SettingsKeys.QURAN_SIZE] = 34 }
        val settings = SettingsRepository(store).readingSettings("ar-EG").first()
        assertEquals(34, settings.arabicSizeSp)
        assertEquals(ReadingMode.MUSHAF, settings.mode)
        assertEquals("ar.muyassar", settings.translationId)
        assertEquals(false, settings.transliteration)
    }

    @Test
    fun readingPositionStaysNullWhenOnlyTwoOfTheThreeKeysExist() = runTest {
        val store = freshStore("reading-position-partial")
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

    // -- the never-early engine's own settings (Task 9, spec §2.2) -------------------------------

    @Test
    fun theNewPrayerSettingsFieldsDefaultToAutomaticAndOff() = runTest {
        val s = repo("t9-prayer-fields-default").prayerSettings.first()
        assertEquals("automatic", s.timetable)
        assertEquals("automatic", s.school)
        assertEquals(false, s.showBothAsr)
        assertEquals(false, s.showWhereDiffer)
        assertEquals(false, s.saudiFajrLater)
        assertEquals(emptyMap(), s.confirmedAdjustments)
        assertEquals(null, s.legacyHighLatitude)
    }

    @Test
    fun theTimetableSchoolAndTheTwoSwitchesRoundTripPerField() = runTest {
        val r = repo("t9-prayer-fields-roundtrip")
        r.setTimetable("other.mwl")
        r.setSchool("hanafi")
        r.setShowBothAsr(true)
        r.setShowWhereDiffer(true)
        r.setSaudiFajrLater(true)
        val s = r.prayerSettings.first()
        assertEquals("other.mwl", s.timetable)
        assertEquals("hanafi", s.school)
        assertEquals(true, s.showBothAsr)
        assertEquals(true, s.showWhereDiffer)
        assertEquals(true, s.saudiFajrLater)
    }

    @Test
    fun confirmedAdjustmentsRoundTripPerPrayer() = runTest {
        val r = repo("t9-confirmed-adjustments")
        r.setConfirmedAdjustments(mapOf(Prayer.MAGHRIB to "other.turkey"))
        assertEquals(
            mapOf(Prayer.MAGHRIB to "other.turkey"),
            r.prayerSettings.first().confirmedAdjustments,
        )
    }

    @Test
    fun legacyHighLatitudeIsKeptOnlyWhileTheTimetableNamesAnOtherMethod() = runTest {
        val r = repo("t9-legacy-high-lat")
        r.setLegacyHighLatitude(HighLatitudePreference.MIDDLE_OF_NIGHT)
        assertEquals(null, r.prayerSettings.first().legacyHighLatitude, "Automatic ignores it")
        r.setTimetable("other.karachi")
        assertEquals(
            HighLatitudePreference.MIDDLE_OF_NIGHT,
            r.prayerSettings.first().legacyHighLatitude,
        )
    }

    @Test
    fun theSunniAndCautiousCardsDefaultToUnseenAndRoundTrip() = runTest {
        val r = repo("t9-cards-seen")
        assertEquals(false, r.sunniCardSeen.first())
        assertEquals(false, r.cautiousCardSeen.first())
        r.setSunniCardSeen(true)
        r.setCautiousCardSeen(true)
        assertEquals(true, r.sunniCardSeen.first())
        assertEquals(true, r.cautiousCardSeen.first())
    }
}

class PrayerSettingsMigrationTest {

    // Raw, pre-migration stores: each test writes only the old-style keys a real install of the
    // previous build would have, then reads through SettingsRepository to see what the spec §8
    // migration derives from them.
    private fun rawStore(name: String) = freshStore("migration-$name")

    @Test
    fun aUkProfileWithMwlAndNoFlagMigratesToAutomatic() = runTest {
        val store = rawStore("uk-mwl-no-flag")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.MUSLIM_WORLD_LEAGUE.name
            it[SettingsKeys.LOCATION_COUNTRY] = "GB"
        }
        assertEquals("automatic", SettingsRepository(store).prayerSettings.first().timetable)
    }

    @Test
    fun aPakistaniProfileWithKarachiAndNoFlagMigratesToAutomatic() = runTest {
        val store = rawStore("pk-karachi-no-flag")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.KARACHI.name
            it[SettingsKeys.LOCATION_COUNTRY] = "PK"
        }
        assertEquals("automatic", SettingsRepository(store).prayerSettings.first().timetable)
    }

    @Test
    fun aChosenTehranMigratesToAutomatic() = runTest {
        val store = rawStore("chosen-tehran")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.TEHRAN.name
            it[SettingsKeys.METHOD_USER_CHOSEN] = true
            it[SettingsKeys.LOCATION_COUNTRY] = "IR"
        }
        assertEquals("automatic", SettingsRepository(store).prayerSettings.first().timetable)
    }

    @Test
    fun aChosenMwlMigratesToItsOwnEntry() = runTest {
        val store = rawStore("chosen-mwl")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.MUSLIM_WORLD_LEAGUE.name
            it[SettingsKeys.METHOD_USER_CHOSEN] = true
            it[SettingsKeys.LOCATION_COUNTRY] = "GB"
        }
        assertEquals("other.mwl", SettingsRepository(store).prayerSettings.first().timetable)
    }

    @Test
    fun aMigratedTimetableIsNotConfirmedUntilTheUserConfirmsIt() = runTest {
        val store = rawStore("chosen-isna-unconfirmed")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.ISNA.name
            it[SettingsKeys.METHOD_USER_CHOSEN] = true
            it[SettingsKeys.LOCATION_COUNTRY] = "GB"
        }
        val repo = SettingsRepository(store)
        val migrated = repo.prayerSettings.first()
        assertEquals("other.isna", migrated.timetable)
        assertEquals(false, migrated.timetableConfirmed)
        assertEquals(null, store.data.first()[SettingsKeys.PRAYER_TIMETABLE_CONFIRMED])
        repo.setTimetableConfirmed("other.isna", "gb.london.lupt")
        assertEquals(true, repo.prayerSettings.first().timetableConfirmed)
        // Another timetable needs its own confirmation.
        repo.setTimetable("other.mwl")
        assertEquals(false, repo.prayerSettings.first().timetableConfirmed)
        repo.setTimetableConfirmed(null, null)
        repo.setTimetable("other.isna")
        assertEquals(false, repo.prayerSettings.first().timetableConfirmed)
    }

    @Test
    fun aStoredHanafiAsrStaysHanafi() = runTest {
        val store = rawStore("asr-hanafi")
        store.edit { it.clear(); it[SettingsKeys.MADHAB] = AsrMadhab.HANAFI.name }
        assertEquals("hanafi", SettingsRepository(store).prayerSettings.first().school)
    }

    @Test
    fun aStoredStandardAsrMigratesToAutomatic() = runTest {
        val store = rawStore("asr-standard")
        store.edit { it.clear(); it[SettingsKeys.MADHAB] = AsrMadhab.STANDARD.name }
        assertEquals("automatic", SettingsRepository(store).prayerSettings.first().school)
    }

    @Test
    fun aNegativeMaghribAdjustmentStaysButIsUnconfirmed() = runTest {
        val store = rawStore("negative-maghrib")
        store.edit { it.clear(); it[SettingsKeys.MINUTE_ADJUSTMENTS] = "MAGHRIB:-2" }
        val settings = SettingsRepository(store).prayerSettings.first()
        assertEquals(-2, settings.minuteAdjustments[Prayer.MAGHRIB])
        assertEquals(null, settings.confirmedAdjustments[Prayer.MAGHRIB])
    }

    @Test
    fun theMigrationRunsOnlyOnce() = runTest {
        val store = rawStore("runs-once")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.MOONSIGHTING_COMMITTEE.name
            it[SettingsKeys.METHOD_USER_CHOSEN] = true
        }
        val repo = SettingsRepository(store)
        assertEquals("other.moonsighting", repo.prayerSettings.first().timetable)
        // A later Settings screen picks a different entry directly, writing only the new key.
        store.edit { it[SettingsKeys.PRAYER_TIMETABLE] = "other.karachi" }
        // Were the migration to run again, it would recompute "other.moonsighting" from the
        // still-unchanged calculation_method key and stomp the screen's own write.
        assertEquals("other.karachi", repo.prayerSettings.first().timetable)
    }

    // -- review round 1: a switch flipped before the first-ever read must not lose the method ----

    @Test
    fun aSwitchSetterCalledBeforeAnyReadStillMigratesAChosenMwlCorrectly() = runTest {
        val store = rawStore("switch-before-any-read")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.MUSLIM_WORLD_LEAGUE.name
            it[SettingsKeys.METHOD_USER_CHOSEN] = true
        }
        val repo = SettingsRepository(store)
        // No prayerSettings collection has ever happened yet — this switch setter is the very
        // first write to touch this store.
        repo.setShowBothAsr(true)
        val settings = repo.prayerSettings.first()
        assertEquals("other.mwl", settings.timetable, "the chosen method must not be lost")
        assertEquals(true, settings.showBothAsr)
    }

    @Test
    fun theMigrationApproximatedByTwoSequentialFirstWritesYieldsTheSameResult() = runTest {
        val store = rawStore("two-first-writes")
        store.edit {
            it.clear()
            it[SettingsKeys.METHOD] = CalculationMethodId.MOONSIGHTING_COMMITTEE.name
            it[SettingsKeys.METHOD_USER_CHOSEN] = true
        }
        val repo = SettingsRepository(store)
        // Two different setters, each capable of triggering the migration, called one after the
        // other on a store neither has migrated yet — approximating two concurrent first writes
        // (DataStore serialises real concurrent `edit` calls; this exercises the same "whoever
        // runs first derives, the other only adds its own field" contract sequentially).
        repo.setShowBothAsr(true)
        repo.setSaudiFajrLater(true)
        val settings = repo.prayerSettings.first()
        assertEquals("other.moonsighting", settings.timetable)
        assertEquals(true, settings.showBothAsr)
        assertEquals(true, settings.saudiFajrLater)
    }

    // -- review round 1: migratedTimetable over every method, at home and away -------------------

    @Test
    fun migratedTimetableCoversEveryMethodsHomeAndAwayCountry() {
        data class Case(val method: CalculationMethodId, val country: String?, val expected: String)
        val cases = listOf(
            // No stand-in country exists for these two — always their own Other entry.
            Case(CalculationMethodId.MUSLIM_WORLD_LEAGUE, "GB", "other.mwl"),
            Case(CalculationMethodId.MUSLIM_WORLD_LEAGUE, null, "other.mwl"),
            Case(CalculationMethodId.MOONSIGHTING_COMMITTEE, "GB", "other.moonsighting"),
            // TEHRAN is always Automatic, regardless of country (it is leaving the registry).
            Case(CalculationMethodId.TEHRAN, "IR", "automatic"),
            Case(CalculationMethodId.TEHRAN, "DE", "automatic"),
            // ISNA: home is US or CA.
            Case(CalculationMethodId.ISNA, "US", "automatic"),
            Case(CalculationMethodId.ISNA, "CA", "automatic"),
            Case(CalculationMethodId.ISNA, "GB", "other.isna"),
            // EGYPTIAN: home is EG.
            Case(CalculationMethodId.EGYPTIAN, "EG", "automatic"),
            Case(CalculationMethodId.EGYPTIAN, "SA", "other.egyptian"),
            // UMM_AL_QURA: home is SA.
            Case(CalculationMethodId.UMM_AL_QURA, "SA", "automatic"),
            Case(CalculationMethodId.UMM_AL_QURA, "AE", "other.ummalqura"),
            // KARACHI: home is BD per spec §8 — not Pakistan.
            Case(CalculationMethodId.KARACHI, "BD", "automatic"),
            Case(CalculationMethodId.KARACHI, "PK", "other.karachi"),
            // DUBAI: home is AE.
            Case(CalculationMethodId.DUBAI, "AE", "automatic"),
            Case(CalculationMethodId.DUBAI, "SA", "other.dubai"),
            // KUWAIT: home is KW.
            Case(CalculationMethodId.KUWAIT, "KW", "automatic"),
            Case(CalculationMethodId.KUWAIT, "SA", "other.kuwait"),
            // QATAR: home is QA.
            Case(CalculationMethodId.QATAR, "QA", "automatic"),
            Case(CalculationMethodId.QATAR, "SA", "other.qatar"),
            // SINGAPORE: home is SG, MY, ID or BN.
            Case(CalculationMethodId.SINGAPORE, "SG", "automatic"),
            Case(CalculationMethodId.SINGAPORE, "MY", "automatic"),
            Case(CalculationMethodId.SINGAPORE, "ID", "automatic"),
            Case(CalculationMethodId.SINGAPORE, "BN", "automatic"),
            Case(CalculationMethodId.SINGAPORE, "GB", "other.singapore"),
            // TURKEY: home is TR.
            Case(CalculationMethodId.TURKEY, "TR", "automatic"),
            Case(CalculationMethodId.TURKEY, "DE", "other.turkey"),
        )
        for (case in cases) {
            assertEquals(
                case.expected,
                migratedTimetable(case.method, userChosen = true, countryCode = case.country),
                "${case.method} in ${case.country}",
            )
        }
        // Every CalculationMethodId is covered above at least once.
        assertEquals(
            CalculationMethodId.entries.toSet(),
            cases.map { it.method }.toSet(),
        )
    }
}

class NotificationSettingsStorageTest {

    private fun repo(name: String) = SettingsRepository(freshStore("notif-$name"))

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
        r.setHijriOffsetDays(1)
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
