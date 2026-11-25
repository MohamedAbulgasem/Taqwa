package world.taqwa.app.prayer

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The rule a fix takes its countries by (rulings R256 and R259): the nearest city always, and beside it the device
 * zone's countries' nearest city where the zone belongs to other countries and one of their cities is near enough.
 * Small invented lists; `GpsBorderTownsTest` (Android host) runs the real one at the audit's border towns, and
 * tools/timetables' `GpsCountryTest` at every gate point.
 */
class FixCountryTest {

    private class C(val name: String, val cc: String, val lat: Double, val lon: Double, val zone: String)

    private fun index(vararg cities: C) = FixCountry.Index(cities.toList(), { it.lat }, { it.lon }, { it.cc }, { it.zone })

    // Invented towns of two countries with entries of their own (Kyrgyzstan's and Kazakhstan's), either side of a
    // border at 0.0 E in the open Atlantic, far from any unit of theirs; a third country (Uzbekistan) shares the
    // first's zone with three towns of its own (each country three, so the zone counts as both's).
    private val west = C("West Town", "KG", 0.0, -0.10, "Zone/West")
    private val westFar = C("West Capital", "KG", 0.0, -1.10, "Zone/West")
    private val westSouth = C("West Village", "KG", -0.6, -0.6, "Zone/West")
    private val east = C("East Town", "KZ", 0.0, 0.05, "Zone/East")
    private val shared = C("Shared Town", "UZ", 0.5, -0.50, "Zone/West")
    private val shared2 = C("Shared Village", "UZ", 0.9, -0.90, "Zone/West")
    private val shared3 = C("Shared Hamlet", "UZ", 1.2, -1.20, "Zone/West")
    private val list = index(westFar, west, westSouth, east, shared, shared2, shared3)

    @Test
    fun `the nearest city alone where the zone is its own country's`() {
        val choice = FixCountry.choice(0.0, 0.04, "Zone/East", list)
        assertEquals("East Town", choice?.city?.name)
        assertEquals(FixCountry.Reason.NEAREST, choice?.reason)
        assertNull(choice?.beside)
    }

    @Test
    fun `the nearest city and beside it the zone's own country's nearest where the nearest stands across the border`() {
        // Nearer to East Town, but the phone's zone is West's: the place is named after East Town and may be in West's
        // country too, whose nearest town is 15.6 km off.
        val choice = FixCountry.choice(0.0, 0.04, "Zone/West", list)
        assertEquals("East Town", choice?.city?.name)
        assertEquals("East Town", FixCountry.choose(0.0, 0.04, "Zone/West", list)?.name)
        assertEquals("West Town", choice?.beside?.name)
        assertEquals(FixCountry.Reason.ZONE, choice?.reason)
        assertEquals(15.6, choice?.besideKm ?: 0.0, 0.1)
    }

    @Test
    fun `a zone two countries share never puts anyone beside its other country`() {
        // Shared Town's country is one of Zone/West's, so a fix nearest to it stays there alone.
        val choice = FixCountry.choice(0.49, -0.48, "Zone/West", list)
        assertEquals("Shared Town", choice?.city?.name)
        assertNull(choice?.beside)
    }

    @Test
    fun `a country with a stray city or two in a zone does not make the zone its`() {
        // One Uzbek town in Zone/West among two Kyrgyz ones: the zone is Kyrgyzstan's alone, so a fix nearest the Uzbek
        // town under it may be in Kyrgyzstan too.
        val stray = index(westFar, west, shared)
        assertEquals(setOf("KG"), stray.countriesOf("Zone/West"))
        val choice = FixCountry.choice(0.49, -0.48, "Zone/West", stray)
        assertEquals("Shared Town", choice?.city?.name)
        assertEquals("West Town", choice?.beside?.name)
        // Three make it the zone's too.
        assertEquals(setOf("KG", "UZ"), list.countriesOf("Zone/West"))
    }

    @Test
    fun `a zone the list does not name leaves the nearest city alone`() {
        val choice = FixCountry.choice(0.0, 0.04, "UTC", list)
        assertEquals("East Town", choice?.city?.name)
        assertEquals(FixCountry.Reason.ZONE_UNKNOWN, choice?.reason)
        assertNull(choice?.beside)
        assertEquals("East Town", FixCountry.choose(0.0, 0.04, null, list)?.name)
    }

    @Test
    fun `a zone whose countries have no city near enough adds nothing`() {
        // A fix 300 km east of every West city, 1 km from East Town: a zone set by hand, or not yet moved.
        val far = index(west, C("Far East", "KZ", 0.0, 2.60, "Zone/East"))
        val choice = FixCountry.choice(0.0, 2.61, "Zone/West", far)
        assertEquals("Far East", choice?.city?.name)
        assertEquals(FixCountry.Reason.ZONE_TOO_FAR, choice?.reason)
        assertNull(choice?.beside)
    }

    @Test
    fun `the plausible distance stretches to three times the nearest city's in empty country`() {
        // The nearest city 120 km off, the zone's 329 km off: past 200 km but within three times, so the zone's country too.
        val desert = index(C("Oasis", "KG", 20.0, -30.0, "Zone/West"), C("Coast", "KZ", 20.0, -32.0, "Zone/East"))
        val choice = FixCountry.choice(20.0, -33.15, "Zone/West", desert)
        assertEquals("Coast", choice?.city?.name)
        assertEquals("Oasis", choice?.beside?.name)
        assertEquals(FixCountry.Reason.ZONE, choice?.reason)
        // Nearer the Coast (42 km), Oasis (251 km) is past both 200 km and three times the Coast's distance.
        assertEquals(FixCountry.Reason.ZONE_TOO_FAR, FixCountry.choice(20.0, -32.4, "Zone/West", desert)?.reason)
    }

    @Test
    fun `a beside city's region is carried except where the units go by the point`() {
        assertEquals("Chuy", FixCountry.besideRegion("KG", "Chuy"))
        assertNull(FixCountry.besideRegion("BN", "Brunei-Muara District"))
        assertNull(FixCountry.besideRegion("dz", "Tlemcen"))
        assertNull(FixCountry.besideRegion("KG", ""))
    }

    /** Nouadhibou, Dakhla and Rabat as the city list has them, around Habous's Lagouira and Bir Gandouz units. */
    private val saharan = index(
        C("Nouadhibou", "MR", 20.94214, -17.03842, "Africa/Nouakchott"),
        C("Dakhla", "EH", 23.68477, -15.95798, "Africa/El_Aaiun"),
        C("Rabat", "MA", 34.01325, -6.83255, "Africa/Casablanca"),
    )

    @Test
    fun `a country following the same entry as the zone's is the zone's`() {
        // Bir Gandouz under a Moroccan phone's zone: Nouadhibou is the nearest city (100 km), Dakhla (Western Sahara,
        // Habous like Morocco) within three times that.
        val choice = FixCountry.choice(21.65, -16.43, "Africa/Casablanca", saharan)
        assertEquals("Nouadhibou", choice?.city?.name)
        assertEquals("Dakhla", choice?.beside?.name)
        assertEquals(FixCountry.Reason.ZONE, choice?.reason)
        // And a Western Sahara town nearest Dakhla under Morocco's zone keeps Dakhla.
        assertEquals(FixCountry.Reason.NEAREST, FixCountry.choice(23.70, -15.93, "Africa/Casablanca", saharan)?.reason)
    }

    @Test
    fun `inside the reach of a unit of the zone's entry the zone's country is beside at any distance`() {
        // Lagouira, Habous's unit 12 km from Nouadhibou and 330 km from Dakhla: under either Moroccan zone, Dakhla.
        for (zone in listOf("Africa/Casablanca", "Africa/El_Aaiun")) {
            val choice = FixCountry.choice(20.84, -17.06, zone, saharan)
            assertEquals("Nouadhibou", choice?.city?.name, zone)
            assertEquals("Dakhla", choice?.beside?.name, zone)
            assertEquals(FixCountry.Reason.ZONE_UNIT, choice?.reason, zone)
        }
        // Under Mauritania's own zone, Nouadhibou alone.
        assertNull(FixCountry.choice(20.84, -17.06, "Africa/Nouakchott", saharan)?.beside)
    }

    @Test
    fun `an empty list has no city`() {
        assertNull(FixCountry.choose(0.0, 0.0, "Zone/West", index()))
    }

    @Test
    fun `the index finds the city a scan of the whole list finds and the first on a tie`() {
        val random = Random(256)
        val cities = List(2_000) { i ->
            C("c$i", "X${i % 7}", random.nextDouble(-80.0, 80.0), random.nextDouble(-180.0, 180.0), "Zone/${i % 5}")
        } + C("twin-1", "T1", 10.0, 10.0, "Zone/T") + C("twin-2", "T2", 10.0, 10.0, "Zone/T")
        val index = FixCountry.Index(cities, { it.lat }, { it.lon }, { it.cc }, { it.zone })
        repeat(500) {
            val lat = random.nextDouble(-85.0, 85.0)
            val lon = random.nextDouble(-180.0, 180.0)
            assertEquals(cities.minBy { km(lat, lon, it.lat, it.lon) }.name, index.nearest(lat, lon)?.first?.name, "$lat,$lon")
            val odd = cities.filter { it.cc == "X3" }.minBy { km(lat, lon, it.lat, it.lon) }.name
            assertEquals(odd, index.nearest(lat, lon) { it.cc == "X3" }?.first?.name, "$lat,$lon X3")
        }
        assertEquals("twin-1", index.nearest(10.0, 10.0)?.first?.name)
        assertEquals("twin-1", index.nearest(10.01, 9.99)?.first?.name)
    }

    private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val la1 = lat1 * PI / 180
        val la2 = lat2 * PI / 180
        val h = sin((la2 - la1) / 2).let { it * it } + cos(la1) * cos(la2) * sin((lon2 - lon1) * PI / 360).let { it * it }
        return 2 * 6371.0 * asin(min(1.0, sqrt(h)))
    }
}
