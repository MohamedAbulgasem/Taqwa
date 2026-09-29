package world.taqwa.timetables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The run summary's lines (spec §2): one notice per held city, one warning per stamp about to run out. */
class MainTest {
    private fun city(slug: String, cc: String, lat: Double, lon: Double, zone: String, admin1: String? = null) = City(
        slug = slug, id = 1, countryCode = cc, region = "europe", latitude = lat, longitude = lon, timeZone = zone,
        languages = listOf("en"), featured = emptySet(), names = mapOf("en" to slug), admin1 = admin1,
    )

    private val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London", "England")
    private val tripoli = city("tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli")
    private val official = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val document = Document(AppStrings(TestPaths.appResources), Stamp.load(official.resolve("stamps")), official)

    @Test fun aHeldCityGetsANoticeWithItsReason() {
        val built = document.build(listOf(tripoli, london), Instant.parse("2026-09-25T00:07:00Z"))
        // London's stamp (through 31 December) covers November, the month after the two shown: no warning.
        assertEquals(listOf("::notice::tripoli-libya: held — class D_AUTHORITY is not proven"), notices(built))
        assertEquals("Wrote 1 cities (1 held) and 1 pages", summary(built))
    }

    @Test fun aStampThatEndsBeforeTheMonthAfterNextGetsAWarning() {
        // Built in November, the page shows November and December; January is not in the stamp.
        val built = document.build(listOf(london), Instant.parse("2026-11-01T12:00:00Z"))
        assertEquals(
            listOf("::warning::london-uk: gb.london.lupt's stamp ends 2026-12-31; the page after next month would be held — run the gate on the new table"),
            notices(built),
        )
    }
}
