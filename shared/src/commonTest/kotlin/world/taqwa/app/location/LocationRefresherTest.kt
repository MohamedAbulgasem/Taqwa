package world.taqwa.app.location

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.notifications.RescheduleTrigger
import world.taqwa.app.settings.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FixedProvider(private val coordinates: Pair<Double, Double>?) : LocationProvider {
    var callCount = 0
        private set

    override suspend fun permission(): LocationPermission =
        if (coordinates == null) LocationPermission.NOT_REQUESTED else LocationPermission.GRANTED

    override suspend fun requestPermission(): LocationPermission = permission()
    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        callCount++
        return coordinates
    }
}

/** Cape Town and Istanbul, the review's own travel scenario, plus Riyadh for the method default. */
private val CITIES = """
    id,name,region,country,countryCode,lat,lon,tz
    3369157,Cape Town,Western Cape,South Africa,ZA,-33.92584,18.42322,Africa/Johannesburg
    745044,Istanbul,Istanbul,Turkey,TR,41.01384,28.94966,Europe/Istanbul
    108410,Riyadh,Riyadh,Saudi Arabia,SA,24.68773,46.72185,Asia/Riyadh
""".trimIndent()

class LocationRefresherTest {

    private fun settings(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "/tmp/taqwa-refresher-$name.preferences_pb".toPath() },
    )

    private fun refresher(
        settings: SettingsRepository,
        coordinates: Pair<Double, Double>?,
        zoneId: String,
        provider: FixedProvider = FixedProvider(coordinates),
    ) = LocationRefresher(
        locationRepository = LocationRepository(provider),
        cityRepository = CityRepository(loadCsv = { CITIES }),
        settings = settings,
        currentZoneId = { zoneId },
    )

    private val capeTown = GeoLocation(-33.9258, 18.4232, "Africa/Johannesburg", "Cape Town", "ZA")

    @Test
    fun flyingToIstanbulReResolvesAndPersistsTheNewLocation() = runTest {
        val settings = settings("flew")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.GPS)
        val refreshed = refresher(settings, 41.0138 to 28.9496, "Europe/Istanbul")
            .refreshFor(RescheduleTrigger.APP_FOREGROUND)

        assertEquals("Istanbul", refreshed?.cityName)
        assertEquals("Europe/Istanbul", refreshed?.timeZoneId)
        val stored = settings.location.first()
        assertEquals("Istanbul", stored?.cityName)
        assertEquals("Europe/Istanbul", stored?.timeZoneId)
        // C4's country default rides along: the method follows the move unless the user chose one.
        assertEquals(CalculationMethodId.TURKEY, settings.prayerSettings.first().method)
    }

    @Test
    fun aRefreshThatMovesTheUserRecordsThatTheLocationCameFromAFix() = runTest {
        val settings = settings("source-gps")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.GPS)
        refresher(settings, 41.0138 to 28.9496, "Europe/Istanbul")
            .refreshFor(RescheduleTrigger.APP_FOREGROUND)

        // A GPS-sourced user who has moved still gets the "Use my location" toggle recorded as
        // ON — the fix and the toggle must never disagree.
        assertEquals(LocationSource.GPS, settings.locationSource.first())
    }

    @Test
    fun walkingAcrossTownLeavesTheStoredLocationAlone() = runTest {
        val settings = settings("walked")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.GPS)
        // What the store holds, not the fix handed to it: the repository rounds coordinates to
        // three decimals on the way in, and "left alone" means that stored value.
        val before = settings.location.first()
        // ~2 km away, same zone: below the 5 km threshold.
        val refreshed = refresher(settings, -33.9258 to 18.4448, "Africa/Johannesburg")
            .refreshFor(RescheduleTrigger.APP_FOREGROUND)

        assertEquals(before?.latitude, refreshed?.latitude)
        assertEquals(before?.longitude, settings.location.first()?.longitude)
    }

    @Test
    fun aTimezoneChangeAloneIsEnoughToReResolve() = runTest {
        val settings = settings("zone-only")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.GPS)
        // Same coordinates, different reported zone — the device crossed a zone boundary or the
        // user corrected the system setting.
        val refreshed = refresher(settings, -33.9258 to 18.4232, "Africa/Maputo")
            .refreshFor(RescheduleTrigger.TIMEZONE_CHANGED)

        assertEquals("Africa/Maputo", refreshed?.timeZoneId)
        assertEquals("Africa/Maputo", settings.location.first()?.timeZoneId)
    }

    @Test
    fun aManuallyChosenCityIsNeverOverwrittenWhenLocationWasNeverGranted() = runTest {
        val settings = settings("manual-city")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.MANUAL)
        val refreshed = refresher(settings, coordinates = null, zoneId = "Europe/Istanbul")
            .refreshFor(RescheduleTrigger.APP_FOREGROUND)

        assertEquals("Cape Town", refreshed?.cityName)
        assertEquals("Cape Town", settings.location.first()?.cityName)
    }

    @Test
    fun aManuallyPickedCityIsNeverSwappedBackOnForeground() = runTest {
        val settings = settings("manual-foreground")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.MANUAL)
        // Permission is in fact granted and a GPS fix to Istanbul is sitting right there — the
        // point of the fix is that a deliberate choice wins even when a fix is available.
        val provider = FixedProvider(41.0138 to 28.9496)
        val refreshed = refresher(settings, 41.0138 to 28.9496, "Europe/Istanbul", provider)
            .refreshFor(RescheduleTrigger.APP_FOREGROUND)

        assertEquals(0, provider.callCount)
        assertEquals("Cape Town", refreshed?.cityName)
        assertEquals("Cape Town", settings.location.first()?.cityName)
        assertEquals(LocationSource.MANUAL, settings.locationSource.first())
    }

    @Test
    fun gpsSourceStillReResolvesOnForeground() = runTest {
        val settings = settings("gps-foreground")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.GPS)
        val provider = FixedProvider(41.0138 to 28.9496)
        val refreshed = refresher(settings, 41.0138 to 28.9496, "Europe/Istanbul", provider)
            .refreshFor(RescheduleTrigger.APP_FOREGROUND)

        assertEquals(1, provider.callCount)
        assertEquals("Istanbul", refreshed?.cityName)
        assertEquals("Istanbul", settings.location.first()?.cityName)
        assertEquals(LocationSource.GPS, settings.locationSource.first())
    }

    @Test
    fun aManuallyPickedCityKeepsItsZoneWhenTheDeviceZoneChanges() = runTest {
        val settings = settings("manual-timezone")
        settings.setLocation(capeTown)
        settings.setLocationSource(LocationSource.MANUAL)
        // The traveller flew to Istanbul; the device zone follows, but the picked city does not.
        val provider = FixedProvider(41.0138 to 28.9496)
        val refreshed = refresher(settings, 41.0138 to 28.9496, "Europe/Istanbul", provider)
            .refreshFor(RescheduleTrigger.TIMEZONE_CHANGED)

        assertEquals(0, provider.callCount)
        assertEquals("Cape Town", refreshed?.cityName)
        assertEquals("Africa/Johannesburg", settings.location.first()?.timeZoneId)
        assertEquals(LocationSource.MANUAL, settings.locationSource.first())
    }

    @Test
    fun triggersThatAreAboutTheScheduleDoNotCostAGpsRead() = runTest {
        val settings = settings("boot")
        settings.setLocation(capeTown)
        val refreshed = refresher(settings, 41.0138 to 28.9496, "Europe/Istanbul")
            .refreshFor(RescheduleTrigger.BOOT_COMPLETED)

        assertEquals("Cape Town", refreshed?.cityName)
        assertEquals("Cape Town", settings.location.first()?.cityName)
    }

    @Test
    fun aUserWhoHasNoStoredLocationYetIsNotGivenOne() = runTest {
        val settings = settings("no-location")
        assertNull(
            refresher(settings, 41.0138 to 28.9496, "Europe/Istanbul")
                .refreshFor(RescheduleTrigger.APP_FOREGROUND),
        )
    }
}
