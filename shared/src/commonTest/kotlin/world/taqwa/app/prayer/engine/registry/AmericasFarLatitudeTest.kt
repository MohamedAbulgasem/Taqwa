package world.taqwa.app.prayer.engine.registry

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.authorities.Generic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ruling R247 (13 Nov 2026, the audit's finding F18, and its review of 14 Nov): from [Generic.AMERICAS_FAR_LATITUDE] a
 * place of the Americas' safe default takes the Moonsighting Committee's day at its own point beside the region's
 * ([Generic.americasAt]). Held as rules, with no printed time committed here (the Committee's calculator's own tables
 * for ten points of the band are the gate's: `gate/safe-default-americas-promise.tsv`). What stands beside the region
 * is the app's model of the Committee's rule, and these tests compare the region with that model: in the band, at the
 * app's towns and on a grid of latitudes in both hemispheres, under either Asr school and as the app shows the day
 * ([DayPipeline.day]), no start is before the model's at the same point and no sunrise or end of eating after it, every
 * day of two years; below the band the region's method is untouched, and its Fajr is still never before the model's.
 */
class AmericasFarLatitudeTest {
    private val americas = requireNotNull(Registry.byId("default.safe.americas"))
    private val committee = requireNotNull(Registry.byId("other.moonsighting"))
    private val schools = listOf(AsrSchool.STANDARD, AsrSchool.HANAFI)

    private fun days(): Sequence<LocalDate> =
        generateSequence(LocalDate(2026, 1, 1)) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it.year <= 2027 }

    private val band = listOf(
        "Isla Hornos" to Place(-55.97, -67.27, "America/Punta_Arenas", "CL"),
        "Puerto Williams" to Place(-54.9341, -67.6109, "America/Punta_Arenas", "CL"),
        "Ushuaia" to Place(-54.81084, -68.31591, "America/Argentina/Ushuaia", "AR"),
        "Grytviken" to Place(-54.28111, -36.5092, "Atlantic/South_Georgia", "GS"),
        "Punta Arenas" to Place(-53.15483, -70.91129, "America/Punta_Arenas", "CL"),
        "Río Gallegos" to Place(-51.62261, -69.21813, "America/Argentina/Rio_Gallegos", "AR"),
        "Stanley" to Place(-51.69382, -57.85701, "Atlantic/Stanley", "FK"),
        "El Calafate" to Place(-50.34075, -72.27682, "America/Argentina/Rio_Gallegos", "AR"),
        "Comodoro Rivadavia" to Place(-45.86413, -67.49656, "America/Argentina/Catamarca", "AR"),
        "Gobernador Costa" to Place(-44.04969, -70.59809, "America/Argentina/Catamarca", "AR"),
        "Paso de Indios" to Place(-43.86335, -69.04561, "America/Argentina/Catamarca", "AR"),
        "Saint-Pierre" to Place(46.78091, -56.17196, "America/Miquelon", "PM"),
    )

    private val below = listOf(
        "Rawson" to Place(-43.30016, -65.10228, "America/Argentina/Catamarca", "AR"),
        "Puerto Montt" to Place(-41.4693, -72.94237, "America/Santiago", "CL"),
        "Buenos Aires" to Place(-34.61315, -58.37723, "America/Argentina/Buenos_Aires", "AR"),
        "Bogotá" to Place(4.60971, -74.08175, "America/Bogota", "CO"),
        "Mexico City" to Place(19.42847, -99.12766, "America/Mexico_City", "MX"),
        "Nassau" to Place(25.05823, -77.34306, "America/Nassau", "BS"),
    )

    /** A point of the region at [latitude], north or south: the land there is Saint-Pierre's or Patagonia's. */
    private fun gridPlace(latitude: Double): Place =
        if (latitude > 0) Place(latitude, -56.0, "America/Miquelon", "PM") else Place(latitude, -68.0, "America/Argentina/Ushuaia", "AR")

    /** In the band: every start of [place]'s day the later of the region's and the Committee's, every end the earlier. */
    private fun assertNeverBeforeTheCommittee(name: String, place: Place) {
        val resolution = Registry.resolve(place)
        assertEquals(americas.id, resolution.entry.id, name)
        assertEquals(EntryClass.D_NONE, resolution.entryClass, name)
        assertEquals(1, requireNotNull(resolution.method).alongside.size, "$name: the Committee's day stands beside the region's")
        val theirs = Registry.resolveEntry(committee, place)
        val zone = TimeZone.of(place.zoneId)
        for (school in schools) {
            for (date in days()) {
                val day = DayPipeline.day(resolution, date, zone, school)
                val other = DayPipeline.day(theirs, date, zone, school)
                val label = "$name $date $school"
                assertTrue(day.fajr >= other.fajr, "$label: Fajr before the Committee's")
                assertTrue(day.dhuhr >= other.dhuhr, "$label: Dhuhr before the Committee's")
                assertTrue(day.asr >= other.asr, "$label: Asr before the Committee's")
                assertTrue(day.maghrib >= other.maghrib, "$label: Maghrib before the Committee's")
                assertTrue(day.isha >= other.isha, "$label: Isha before the Committee's")
                assertTrue(day.sunrise <= other.sunrise, "$label: sunrise after the Committee's")
                assertTrue(day.endOfEating <= other.endOfEating, "$label: the fast begins after the Committee's")
                assertTrue(day.endOfEating <= day.fajr, "$label: the fast begins after Fajr")
                for ((prayer, end) in day.ends) {
                    val theirEnd = other.ends[prayer] ?: continue
                    assertTrue(end <= theirEnd, "$label: $prayer ends after the Committee's")
                }
            }
        }
    }

    @Test
    fun `in the far south and north no start is before the committee's at the same point and no end after it`() {
        for ((name, place) in band) assertNeverBeforeTheCommittee(name, place)
    }

    @Test
    fun `the same holds at every latitude of the band in both hemispheres`() {
        val edge = Generic.AMERICAS_FAR_LATITUDE
        val latitudes = listOf(edge, edge + 0.25, edge + 0.5) + (45..56).map { it.toDouble() }
        for (sign in listOf(1.0, -1.0)) {
            for (latitude in latitudes) assertNeverBeforeTheCommittee("${sign * latitude}°", gridPlace(sign * latitude))
        }
    }

    @Test
    fun `below the band the region's own method stands alone`() {
        val base = requireNotNull(americas.method)
        for ((name, place) in below) {
            val resolution = Registry.resolve(place)
            assertEquals(americas.id, resolution.entry.id, name)
            val user = GeoPoint(place.lat, place.lon)
            assertEquals(Registry.methodAlone(americas.id, base, user, place.zoneId), resolution.method, name)
            assertTrue(requireNotNull(resolution.method).alongside.isEmpty(), name)
        }
    }

    /**
     * The band's edge is not too far out: up to it the region's Fajr, alone, is never before the Committee's, so no place
     * below the band needs the Committee's day for its dawn. (The region's Dhuhr is up to a minute before the Committee's
     * + 5 throughout the Americas: its own convention where the Committee's table is no one's reference.)
     */
    @Test
    fun `below the band the region's fajr alone is never before the committee's`() {
        val edge = Generic.AMERICAS_FAR_LATITUDE
        val latitudes = listOf(edge - 0.01, edge - 0.25, edge - 0.5, 42.0, 40.0, 35.0, 30.0, 20.0, 10.0, 0.5)
        for (sign in listOf(1.0, -1.0)) {
            for (latitude in latitudes) {
                val place = gridPlace(sign * latitude)
                val resolution = Registry.resolve(place)
                assertTrue(requireNotNull(resolution.method).alongside.isEmpty(), "${place.lat}°")
                val theirs = Registry.resolveEntry(committee, place)
                val zone = TimeZone.of(place.zoneId)
                for (date in days()) {
                    val day = DayPipeline.day(resolution, date, zone, AsrSchool.STANDARD)
                    val other = DayPipeline.day(theirs, date, zone, AsrSchool.STANDARD)
                    assertTrue(day.fajr >= other.fajr, "${place.lat}° $date: Fajr before the Committee's")
                    assertTrue(day.endOfEating <= other.endOfEating, "${place.lat}° $date: the fast begins after the Committee's")
                }
            }
        }
    }

    @Test
    fun `the band begins at the same latitude in both hemispheres`() {
        val edge = Generic.AMERICAS_FAR_LATITUDE
        for (sign in listOf(1.0, -1.0)) {
            val inside = GeoPoint(sign * edge, -65.0)
            val outside = GeoPoint(sign * (edge - 0.01), -65.0)
            assertTrue(Generic.americasAt(outside).alongside.isEmpty(), "$outside")
            val beside = Generic.americasAt(inside).alongside.single()
            assertEquals(inside, beside.point)
            assertEquals(committee.id, beside.method.id)
            assertEquals(Generic.americas.method, Generic.americasAt(inside).copy(alongside = emptyList()))
        }
        // The gate's rows for this region, the United States' mosques, all stand below the band.
        assertTrue(edge > 41.0 && edge < 45.0)
    }
}
