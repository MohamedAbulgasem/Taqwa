package world.taqwa.app.location

import kotlinx.coroutines.test.runTest
import world.taqwa.app.city.CityRepository
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.data.KyrgyzLocalities
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A GPS fix is held to the device zone's country too where the nearest bundled city stands across a border (rulings
 * R256 and R259; the second trust audit's G4 and G5, 14–15 Nov 2026), through the app's own path: [LocationRepository]
 * .resolveGpsLocation over the real city list, then the engine's Automatic entry for what it stores. A place "follows"
 * an entry when Automatic is that entry, or a border resolution with that entry as one of its two sides (each start the
 * later of the two, each end the earlier: never before that entry's day). Here because it reads the list from the file
 * system, like `ZoneCountriesTest`.
 */
class GpsBorderTownsTest {

    private val csv: String by lazy {
        val moduleRelative = File("src/commonMain/composeResources/files/cities.csv")
        (if (moduleRelative.exists()) moduleRelative else File("shared/src/commonMain/composeResources/files/cities.csv")).readText()
    }

    private val cities by lazy { CityRepository(loadCsv = { csv }) }

    private class At(private val point: Pair<Double, Double>) : LocationProvider {
        override suspend fun permission(): LocationPermission = LocationPermission.GRANTED
        override suspend fun requestPermission(): LocationPermission = LocationPermission.GRANTED
        override suspend fun currentCoordinates(): Pair<Double, Double> = point
        override suspend fun lastKnownCoordinates(): Pair<Double, Double> = point
    }

    private suspend fun fix(lat: Double, lon: Double, zone: String): GeoLocation =
        requireNotNull(LocationRepository(At(lat to lon), currentZoneId = { zone }).resolveGpsLocation(cities))

    /** Whether Automatic entry [automatic] is [entry], or a border resolution with [entry] as one side (ruling R259). */
    private fun follows(automatic: String, entry: String): Boolean =
        automatic == entry || automatic.startsWith(Registry.BORDER_PREFIX) && entry in automatic.removePrefix(Registry.BORDER_PREFIX).split('+')

    /** Whether [location] is in [country] or may be ([GeoLocation.alsoCountryCode]). */
    private fun inCountry(location: GeoLocation, country: String) = location.countryCode == country || location.alsoCountryCode == country

    @Test
    fun `every Kyrgyz Muftiate locality resolved the GPS way under Asia Bishkek follows Kyrgyzstan's entry`() = runTest {
        // The 115 localities' points and Aravan town's own (KyrgyzLocalities.aravanTown): the 116 the audit resolved.
        val points = KyrgyzLocalities.all + KyrgyzLocalities.aravanTown
        var border = 0
        val wrong = points.mapNotNull { locality ->
            val resolved = fix(locality.lat, locality.lon, "Asia/Bishkek")
            val entry = PrayerTimesEngine().automaticEntryId(resolved)
            if (entry != "kg.default") border++
            if (inCountry(resolved, "KG") && follows(entry, "kg.default")) null else "${locality.key}: ${resolved.cityName} ${resolved.countryCode} $entry"
        }
        assertEquals(116, points.size)
        assertTrue(wrong.isEmpty(), "not held to Kyrgyzstan's entry: $wrong")
        // The 22 whose nearest app city is across the border are held to both countries' entries; the rest to the Muftiate's alone.
        assertEquals(22, border)
    }

    /** The audit's border places, each inside its own country, with its own zone, and the entry its country follows. */
    private val borderTowns = listOf(
        Town("Karkamış", 36.835, 37.999, "Europe/Istanbul", "TR", "tr.diyanet"),
        Town("Elbeyli", 36.676, 37.463, "Europe/Istanbul", "TR", "tr.diyanet"),
        Town("Posof", 41.51, 42.73, "Europe/Istanbul", "TR", "tr.diyanet"),
        Town("Aralık", 39.87, 44.52, "Europe/Istanbul", "TR", "tr.diyanet"),
        Town("Madha", 25.28, 56.33, "Asia/Muscat", "OM", "om.mara"),
        Town("Dibba Al-Baya", 25.63, 56.27, "Asia/Muscat", "OM", "om.mara"),
        Town("Bangar (Temburong)", 4.71, 115.07, "Asia/Brunei", "BN", "bn.mora"),
        Town("Batang Duri (Temburong)", 4.56, 115.15, "Asia/Brunei", "BN", "bn.mora"),
        Town("Tel al-Sultan (Rafah, Gaza)", 31.29, 34.235, "Asia/Gaza", "PS", "ps.gaza.cautious"),
        Town("Farkhana", 35.2817, -2.9636, "Africa/Casablanca", "MA", "ma.habous"),
        Town("Benapole", 23.042, 88.90, "Asia/Dhaka", "BD", "bd.ifb"),
        Town("Motaain", -9.0, 125.1, "Asia/Makassar", "ID", "id.kemenag"),
        Town("Hili (Al Ain)", 24.275, 55.77, "Asia/Dubai", "AE", "ae.awqaf"),
        Town("Taba", 29.493, 34.895, "Africa/Cairo", "EG", "eg.esa"),
        Town("Sokh", 39.96, 71.13, "Asia/Tashkent", "UZ", "uz.board"),
    ) + listOf("cholpon-ata", "manas-talas", "aytmatov", "amanbaev", "kyzyl-adyr", "pokrovka", "sheker", "taldy-bulak", "talas", "bakay-ata")
        .map { key ->
            val locality = KyrgyzLocalities.all.single { it.key == key }
            Town(locality.name, locality.lat, locality.lon, "Asia/Bishkek", "KG", "kg.default")
        }

    private class Town(val name: String, val lat: Double, val lon: Double, val zone: String, val country: String, val entry: String)

    @Test
    fun `the audit's border towns follow their own country's entry with their own zone`() = runTest {
        val wrong = borderTowns.mapNotNull { town ->
            val resolved = fix(town.lat, town.lon, town.zone)
            val entry = PrayerTimesEngine().automaticEntryId(resolved)
            if (inCountry(resolved, town.country) && follows(entry, town.entry)) null
            else "${town.name}: ${resolved.cityName} ${resolved.countryCode} $entry, expected ${town.country} ${town.entry}"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    /** Temburong's own district, not Brunei-Muara's: a beside city's region is not carried where the units go by the point. */
    @Test
    fun `a Temburong fix is held to Brunei's Temburong district`() = runTest {
        for (town in borderTowns.filter { it.country == "BN" }) {
            val resolved = fix(town.lat, town.lon, town.zone)
            assertEquals("BN", resolved.alsoCountryCode, town.name)
            assertEquals(null, resolved.alsoRegion, town.name)
            val brunei = Registry.resolve(PrayerTimesEngine.placeOf(resolved)).sides.single { it.entry.id == "bn.mora" }
            assertEquals("bn.mora", brunei.entry.id)
            assertTrue(brunei.unitName?.contains("Temburong") == true, "${town.name}: ${brunei.unitName}")
        }
    }

    /**
     * The second review's cases (15 Nov 2026): a phone in a city of its own country carrying the neighbour's zone (a
     * network's, or one set by hand). R256 moved it to the neighbour's entry (Johor Bahru to Singapore's: Fajr before
     * JAKIM's on every day); R259 holds it to both, so its own country's entry is always one side.
     */
    @Test
    fun `a city carrying the neighbour's zone is held to its own country's entry too`() = runTest {
        val metros = listOf(
            Town("Johor Bahru", 1.4655, 103.7578, "Asia/Singapore", "MY", "my.jakim"),
            Town("Limbang", 4.75, 115.0, "Asia/Brunei", "MY", "my.jakim"),
            Town("Gaziantep", 37.0662, 37.3833, "Asia/Damascus", "TR", "tr.diyanet"),
            Town("Shymkent (Tashkent zone)", 42.3, 69.6, "Asia/Tashkent", "KZ", "kz.qmdb"),
            Town("Shymkent (Bishkek zone)", 42.3, 69.6, "Asia/Bishkek", "KZ", "kz.qmdb"),
            Town("Almaty (Bishkek zone)", 43.25, 76.92, "Asia/Bishkek", "KZ", "kz.qmdb"),
            Town("Doha (Riyadh zone)", 25.2854, 51.531, "Asia/Riyadh", "QA", "qa.calendarhouse"),
            Town("Al Ain (Muscat zone)", 24.2075, 55.7447, "Asia/Muscat", "AE", "ae.awqaf"),
        )
        val wrong = metros.mapNotNull { town ->
            val resolved = fix(town.lat, town.lon, town.zone)
            val entry = PrayerTimesEngine().automaticEntryId(resolved)
            if (resolved.countryCode == town.country && follows(entry, town.entry)) null
            else "${town.name}: ${resolved.cityName} ${resolved.countryCode}/${resolved.alsoCountryCode} $entry, expected ${town.country} ${town.entry}"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    /** The control: places on the other side of the same borders, with their own zones, keep their own country. */
    @Test
    fun `the neighbours across those borders keep their own country`() = runTest {
        val neighbours = listOf(
            Town("Jarābulus", 36.817, 38.011, "Asia/Damascus", "SY", "sy.awqaf"),
            Town("Khawr Fakkān", 25.339, 56.356, "Asia/Dubai", "AE", "ae.awqaf"),
            Town("Nahwa", 25.27, 56.28, "Asia/Dubai", "AE", "ae.awqaf"),
            Town("Limbang", 4.75, 115.0, "Asia/Kuching", "MY", "my.jakim"),
            Town("Rafah (Egypt)", 31.275, 34.238, "Africa/Cairo", "EG", "eg.esa"),
            Town("Melilla", 35.29369, -2.93833, "Africa/Ceuta", "ES", "default.safe.europe"),
            Town("Ceuta", 35.88919, -5.32042, "Europe/Madrid", "ES", "default.safe.europe"),
            Town("Almaty", 43.25, 76.92, "Asia/Almaty", "KZ", "kz.qmdb"),
            Town("Taraz", 42.90, 71.37, "Asia/Almaty", "KZ", "kz.qmdb"),
            Town("Al Buraymī", 24.25, 55.79, "Asia/Muscat", "OM", "om.mara"),
            Town("Prizren", 42.2139, 20.7397, "Europe/Belgrade", "XK", "xk.bik"),
        )
        val wrong = neighbours.mapNotNull { town ->
            val resolved = fix(town.lat, town.lon, town.zone)
            val entry = PrayerTimesEngine().automaticEntryId(resolved)
            if (resolved.countryCode == town.country && entry == town.entry) null
            else "${town.name}: ${resolved.cityName} ${resolved.countryCode} $entry, expected ${town.country} ${town.entry}"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    /** A city picked by hand keeps the city's own country, whatever the phone's zone (spec §8): R256 is for fixes alone. */
    @Test
    fun `a city picked by hand keeps its own country`() = runTest {
        val almaty = requireNotNull(cities.search("Almaty").firstOrNull { it.countryCode == "KZ" })
        val picked = almaty.toGeoLocation()
        assertEquals("KZ", picked.countryCode)
        assertEquals("kz.qmdb", PrayerTimesEngine().automaticEntryId(picked))
    }
}
