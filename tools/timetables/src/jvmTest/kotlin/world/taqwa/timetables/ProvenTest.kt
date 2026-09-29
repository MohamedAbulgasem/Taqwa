package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import world.taqwa.timetables.gate.GateManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The proven rule (spec §2, ruling R101), clause by clause, against the committed stamps. Only
 * entries no other track rewrites tonight are pinned by figure (gb.london.lupt, ca.toronto,
 * ly.awqaf, eg.esa, tr.diyanet); the totals are computed from the files, never typed.
 */
class ProvenTest {
    private val official = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val stamps = Stamp.load(official.resolve("stamps"))
    private val timetable = Timetable()

    private fun city(slug: String, cc: String, lat: Double, lon: Double, zone: String, admin1: String? = null) = City(
        slug = slug, id = 1, countryCode = cc, region = "europe", latitude = lat, longitude = lon, timeZone = zone,
        languages = listOf("en"), featured = emptySet(), names = mapOf("en" to slug), admin1 = admin1,
    )

    private val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London", "England")
    private val toronto = city("toronto-canada", "CA", 43.70643, -79.39864, "America/Toronto", "Ontario")
    private val sep1 = LocalDate(2026, 9, 1)
    private val oct31 = LocalDate(2026, 10, 31)

    private fun verdict(city: City, first: LocalDate = sep1, last: LocalDate = oct31, with: Map<String, Stamp> = stamps) =
        Proven.verdict(timetable.source(city, first).effective, with, first, last)

    @Test fun londonIsPublishedWithItsUnitsFigures() {
        val v = verdict(london)
        assertIs<Verdict.Published>(v)
        assertEquals(5, v.atMost) // the M25 unit's worst over the starts (Fajr and Isha)
        assertEquals(4015, v.stamp.placeDays)
        assertEquals(11, v.stamp.places)
        assertEquals(LocalDate(2026, 12, 31), v.stamp.last)
        assertEquals(false, v.stamp.cautious)
    }

    @Test fun tripoliIsHeldForItsClass() {
        val v = verdict(city("tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli"))
        assertIs<Verdict.Held>(v)
        assertTrue(v.reason.startsWith("class D_AUTHORITY"), v.reason)
    }

    @Test fun cairoIsHeldBecauseTheStampEndsInSeptember() {
        val v = verdict(city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo"))
        assertIs<Verdict.Held>(v)
        assertTrue(v.reason.contains("covers 2025-01-08..2026-09-30"), v.reason)
    }

    @Test fun istanbulIsHeldInSeptemberAndPublishedFromOctober() {
        val istanbul = city("istanbul-turkiye", "TR", 41.01384, 28.94966, "Europe/Istanbul", "Istanbul")
        val september = verdict(istanbul)
        assertIs<Verdict.Held>(september)
        assertTrue(september.reason.contains("covers 2026-09-25"), september.reason)
        assertIs<Verdict.Published>(verdict(istanbul, LocalDate(2026, 10, 1), LocalDate(2026, 11, 30)))
    }

    @Test fun birminghamIsHeldAsNotMeasured() {
        val v = verdict(city("birmingham-uk", "GB", 52.48142, -1.89983, "Europe/London", "England"))
        assertIs<Verdict.Held>(v)
        assertEquals("not measured at this place", v.reason)
    }

    @Test fun torontoIsPublishedAsCautiousWithoutAnAtMostFigure() {
        // Ruling R105: a cautious place claims no "at most N minutes after the earliest" figure —
        // the entry-wide worst would be another place's spread — and its verdict never needs one.
        val v = verdict(toronto)
        assertIs<Verdict.Published>(v)
        assertTrue(v.stamp.cautious)
        assertNull(v.atMost)
        assertEquals(1400, v.stamp.placeDays)
    }

    @Test fun aRedStampHoldsTheCity() {
        val red = london("broken" to "1")
        val v = verdict(london, with = mapOf(red.entry to red))
        assertIs<Verdict.Held>(v)
        assertEquals("gb.london.lupt's stamp is red", v.reason)
    }

    @Test fun aStampWithoutThePlacesUnitHoldsTheCity() {
        val elsewhere = london("units" to """{"gb.elsewhere": {"broken": 0, "events": {}}}""")
        val v = verdict(london, with = mapOf(elsewhere.entry to elsewhere))
        assertIs<Verdict.Held>(v)
        assertEquals("unit gb.london.lupt is not in gb.london.lupt's stamp", v.reason)
    }

    @Test fun aRedUnitHoldsTheCity() {
        val redUnit = london("units" to """{"gb.london.lupt": {"broken": 2, "events": {}}}""")
        val v = verdict(london, with = mapOf(redUnit.entry to redUnit))
        assertIs<Verdict.Held>(v)
        assertEquals("unit gb.london.lupt is red", v.reason)
    }

    @Test fun aUnitRowMissingAStartHoldsTheCity() {
        // Eastern Libya's rows lack Fajr and Maghrib (ruling R73): no figure for the whole day.
        val partial = london("units" to """{"gb.london.lupt": {"broken": 0, "events": {"dhuhr": {"worstLate": 1, "late": {"0": 1}}}}}""")
        val v = verdict(london, with = mapOf(partial.entry to partial))
        assertIs<Verdict.Held>(v)
        assertEquals("no start is measured at this unit", v.reason)
    }

    @Test fun aMissingStampHoldsTheCity() {
        val v = verdict(london, with = emptyMap())
        assertIs<Verdict.Held>(v)
        assertEquals("no stamp for gb.london.lupt", v.reason)
    }

    @Test fun fajrSharesAreTheLateHistogramOverItsOwnCheckedCount() {
        val london = stamps.getValue("gb.london.lupt")
        val shares = london.shares(london.unitEvents("gb.london.lupt")!!, "fajr")
        assertEquals(listOf("0", "1", "2", "3+"), shares.keys.toList())
        assertEquals(1.0, shares.values.sum(), 0.001)
        // The "same minute" share is the stamp's own exactShare, to the four decimals it keeps.
        assertEquals(0.3077, shares.getValue("0"), 0.00001)
    }

    @Test fun theEntryWideWorstIsOverTheStartsOnly() {
        // no.cautious's ends run later than its starts; the checks page's figure is the starts'.
        val toronto = stamps.getValue("ca.toronto")
        assertEquals(7, toronto.worstStarts(toronto.events)) // Isha 7, not the end of eating's 4
        val london = stamps.getValue("gb.london.lupt")
        assertEquals(5, london.worstStarts(london.events))
    }

    @Test fun aGreenStampHasNoEarlyStartAndNoLateEnd() {
        val london = stamps.getValue("gb.london.lupt")
        assertEquals(0, london.early())
        assertEquals(0, london.lateEnds())
    }

    @Test fun theGateRowsAreCountedAsTheGateReadsThem() {
        val expected = GateManifest.load(official.resolve("gate")).rows.size
        assertTrue(expected > 0)
        assertEquals(expected, ProofTotals.gateRows(official))
    }

    @Test fun theSurveyCalendarsAreCountedFromEveryCalendarsFile() {
        val dirs = official.resolve("survey").listFiles { f -> f.isDirectory }!!.toList()
        val expected = dirs.sumOf { dir ->
            dir.resolve("calendars.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.size - 1
        }
        assertTrue(dirs.isNotEmpty() && expected > 0)
        assertEquals(expected, ProofTotals.surveyCalendars(official))
    }

    /** London's committed stamp with one top-level member replaced by the JSON [override] value. */
    @Suppress("UNCHECKED_CAST")
    private fun london(override: Pair<String, String>): Stamp {
        val root = LinkedHashMap(Json.parse(official.resolve("stamps/gb.london.lupt.json").readText()) as Map<String, Any?>)
        val (key, value) = override
        require(key in root) { "no $key in the stamp" }
        root[key] = Json.parse(value)
        return Stamp(root)
    }
}
