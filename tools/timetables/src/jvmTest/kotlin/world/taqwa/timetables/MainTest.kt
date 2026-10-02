package world.taqwa.timetables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The run summary's lines (spec §2, rulings R116, R117): one notice per held city, one per city
 * shown with its current month alone, naming the timetable and the first day that keep the next
 * month off, and one per member table excused on days a page shows.
 */
class MainTest {
    private fun city(slug: String, cc: String, lat: Double, lon: Double, zone: String, admin1: String? = null) = City(
        slug = slug, id = 1, countryCode = cc, region = "europe", latitude = lat, longitude = lon, timeZone = zone,
        languages = listOf("en"), featured = emptySet(), names = mapOf("en" to slug), admin1 = admin1,
    )

    private val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London", "England")
    private val tripoli = city("tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli")
    private val toronto = city("toronto-canada", "CA", 43.70643, -79.39864, "America/Toronto", "Ontario")
    private val istanbul = city("istanbul-turkiye", "TR", 41.01384, 28.94966, "Europe/Istanbul", "Istanbul")
    private val official = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val document = Document(AppStrings(TestPaths.appResources), Stamp.load(official.resolve("stamps")), official)

    @Test fun aHeldCityGetsANoticeWithItsReason() {
        val built = document.build(listOf(tripoli, london), Instant.parse("2026-09-25T00:07:00Z"))
        // London shows September and October, both checked: nothing to say about it.
        assertEquals(listOf("::notice::tripoli-libya: held — class D_AUTHORITY is not proven"), notices(built))
        assertEquals("Wrote 1 cities (1 held) and 1 pages", summary(built))
    }

    @Test fun aCityShownWithItsCurrentMonthAloneGetsANotice() {
        // The first run of 1 October: Diyanet's captures reach 1 November (ruling R116).
        val built = document.build(listOf(istanbul), Instant.parse("2026-10-01T06:07:00Z"))
        assertEquals(
            listOf("::notice::istanbul-turkiye: showing October alone — the next month is not yet checked: tr.diyanet, first unchecked day 2 Nov 2026"),
            notices(built),
        )
        assertEquals("Wrote 1 cities (0 held) and 1 pages", summary(built))
    }

    @Test fun aCityShownWithAMembersExcusedDaysGetsANotice() {
        // Ruling R117: Toronto shows October and November from 1 October; IFT's nine fault days in
        // November are excused (IIT's and MAC's tables are checked on them), and the run says so.
        val built = document.build(listOf(toronto), Instant.parse("2026-10-01T06:07:00Z"))
        assertEquals(
            listOf("::notice::toronto-canada: 9 days excused: ca.ift (a member of ca.toronto) recorded faults"),
            notices(built),
        )
    }

    @Test fun aStampEndingWithTheNextMonthSaysNothingUntilThatMonthIsShownAlone() {
        // Built in November, the page shows November and December, both checked: no notice (the
        // warning a month ahead of a hold is gone — a month shown alone is that notice now).
        assertEquals(emptyList<String>(), notices(document.build(listOf(london), Instant.parse("2026-11-01T12:00:00Z"))))
        // Built in December, January 2027 is not in the stamp: December alone, a month before the hold.
        assertEquals(
            listOf("::notice::london-uk: showing December alone — the next month is not yet checked: gb.london.lupt, first unchecked day 1 Jan 2027"),
            notices(document.build(listOf(london), Instant.parse("2026-12-01T12:00:00Z"))),
        )
    }
}
