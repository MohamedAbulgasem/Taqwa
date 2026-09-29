package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.timetables.gate.GateManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The proven rule (spec §2, rulings R101 and R115), clause by clause, against the committed stamps.
 * Only entries no other track rewrites tonight are pinned by figure (gb.london.lupt, ca.toronto,
 * ly.awqaf, eg.esa, tr.diyanet); the totals are computed from the files, never typed. The dates
 * pinned here are the tables' own coverage — dates only, never a time (ruling R69).
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
    private val istanbul = city("istanbul-turkiye", "TR", 41.01384, 28.94966, "Europe/Istanbul", "Istanbul")
    private val sep1 = LocalDate(2026, 9, 1)
    private val oct1 = LocalDate(2026, 10, 1)
    private val oct31 = LocalDate(2026, 10, 31)
    private val nov30 = LocalDate(2026, 11, 30)

    private fun place(city: City) = Place(city.latitude, city.longitude, city.timeZone, city.countryCode, city.admin1)

    private fun verdict(city: City, first: LocalDate = sep1, last: LocalDate = oct31, with: Map<String, Stamp> = stamps) =
        Proven.verdict(timetable.source(city, first).effective, place(city), with, first, last)

    /** The committed stamp of [entry] with [edit] applied to its parsed JSON (numbers are Longs). */
    @Suppress("UNCHECKED_CAST")
    private fun stamp(entry: String, edit: (MutableMap<String, Any?>) -> Unit): Stamp {
        val root = LinkedHashMap(Json.parse(official.resolve("stamps/$entry.json").readText()) as Map<String, Any?>)
        edit(root)
        return Stamp(root)
    }

    /** London's stamp with its unit's checked runs replaced. */
    @Suppress("UNCHECKED_CAST")
    private fun londonChecked(vararg runs: String) = stamp("gb.london.lupt") { root ->
        val units = LinkedHashMap(root["units"] as Map<String, Any?>)
        val unit = LinkedHashMap(units["gb.london.lupt"] as Map<String, Any?>)
        unit["checked"] = runs.toList()
        units["gb.london.lupt"] = unit
        root["units"] = units
    }

    /** Toronto's stamp with its members' checked runs by point replaced. */
    private fun torontoMembers(vararg members: Pair<String, Map<String, List<String>>>) = stamp("ca.toronto") { root ->
        root["members"] = members.associate { (id, points) ->
            id to mapOf("checked" to points.values.flatten(), "points" to points)
        }
    }

    private val mosque = "43.798,-79.2417" // a Toronto mosque's point, as the gate file gives it
    private val trondheim = "63.43049,10.39506"
    private val year = listOf("2026-01-01..2026-12-31")

    // ── The committed stamps ──

    @Test fun londonIsPublishedWithItsUnitsFigures() {
        val v = verdict(london)
        assertIs<Verdict.Published>(v)
        assertEquals(5, v.atMost) // the M25 unit's worst over the starts (Fajr and Isha)
        assertEquals(4015, v.stamp.placeDays)
        assertEquals(11, v.stamp.places)
        assertEquals(LocalDate(2026, 12, 31), v.through)
        assertEquals(false, v.stamp.cautious)
    }

    @Test fun tripoliIsHeldForItsClass() {
        val v = verdict(city("tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli"))
        assertIs<Verdict.Held>(v)
        assertTrue(v.reason.startsWith("class D_AUTHORITY"), v.reason)
    }

    @Test fun cairoIsHeldBecauseItsTableEndsInSeptember() {
        val v = verdict(city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo"))
        assertIs<Verdict.Held>(v)
        assertEquals("eg.esa: no checked table day from 1 Oct 2026", v.reason)
    }

    @Test fun istanbulIsHeldOnBothSidesOfDiyanetsHole() {
        // Diyanet's captures hold 25 Sep – 29 Oct 2026 (the research's to 25 Oct, the weekly monitor's
        // of 28 Sep to 29 Oct) and 2027: September's first days and the last of October through
        // December were never checked (review C1, ruling R115).
        val september = verdict(istanbul)
        assertIs<Verdict.Held>(september)
        assertEquals("tr.diyanet: no checked table day 1 Sep 2026 – 24 Sep 2026", september.reason)
        val october = verdict(istanbul, oct1, nov30)
        assertIs<Verdict.Held>(october)
        assertEquals("tr.diyanet: no checked table day 30 Oct 2026 – 31 Dec 2026", october.reason)
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

    @Test fun torontoIsHeldForNovembersUncheckedDays() {
        // Its three member tables lack 1–6 and 28–30 November (review C1): the October–November page waits.
        val v = verdict(toronto, oct1, nov30)
        assertIs<Verdict.Held>(v)
        assertTrue(v.reason.endsWith("(a member of ca.toronto): no checked table day 1 Nov 2026 – 6 Nov 2026"), v.reason)
    }

    // ── Each clause on a synthetic stamp (built from a committed one; never a time) ──

    @Test fun aRedStampHoldsTheCity() {
        val red = stamp("gb.london.lupt") { it["broken"] = 1L }
        val v = verdict(london, with = mapOf(red.entry to red))
        assertIs<Verdict.Held>(v)
        assertEquals("gb.london.lupt's stamp is red", v.reason)
    }

    @Test fun aStampWithoutThePlacesUnitHoldsTheCity() {
        val elsewhere = stamp("gb.london.lupt") { it["units"] = Json.parse("""{"gb.elsewhere": {"broken": 0, "events": {}}}""") }
        val v = verdict(london, with = mapOf(elsewhere.entry to elsewhere))
        assertIs<Verdict.Held>(v)
        assertEquals("unit gb.london.lupt is not in gb.london.lupt's stamp", v.reason)
    }

    @Test fun aRedUnitHoldsTheCity() {
        val redUnit = stamp("gb.london.lupt") { it["units"] = Json.parse("""{"gb.london.lupt": {"broken": 2, "events": {}}}""") }
        val v = verdict(london, with = mapOf(redUnit.entry to redUnit))
        assertIs<Verdict.Held>(v)
        assertEquals("unit gb.london.lupt is red", v.reason)
    }

    @Test fun aUnitRowMissingAStartHoldsTheCity() {
        // Eastern Libya's rows lack Fajr and Maghrib (ruling R73): no figure for the whole day.
        val partial = stamp("gb.london.lupt") {
            it["units"] = Json.parse(
                """{"gb.london.lupt": {"broken": 0, "checked": ["2026-01-01..2026-12-31"], "events": {"dhuhr": {"worstLate": 1, "late": {"0": 1}}}}}""",
            )
        }
        val v = verdict(london, with = mapOf(partial.entry to partial))
        assertIs<Verdict.Held>(v)
        assertEquals("no start is measured at this unit", v.reason)
    }

    @Test fun aMissingStampHoldsTheCity() {
        val v = verdict(london, with = emptyMap())
        assertIs<Verdict.Held>(v)
        assertEquals("no stamp for gb.london.lupt", v.reason)
    }

    @Test fun aHoleInTheUnitsCheckedDaysHoldsTheCityAndIsNamed() {
        val holed = londonChecked("2026-01-01..2026-10-25", "2027-01-01..2027-12-31")
        val v = verdict(london, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Held>(v)
        assertEquals("gb.london.lupt: no checked table day 26 Oct 2026 – 31 Dec 2026", v.reason)
    }

    @Test fun aHoleOutsideTheDaysShownDoesNotHoldTheCity() {
        val holed = londonChecked("2026-01-01..2026-06-30", "2026-08-01..2026-12-31")
        val v = verdict(london, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Published>(v)
        assertEquals(LocalDate(2026, 12, 31), v.through)
    }

    @Test fun throughIsTheEndOfTheRunHoldingTheDaysShownNotTheStampsLast() {
        val holed = londonChecked("2026-01-01..2026-10-31", "2027-01-01..2027-12-31")
        val v = verdict(london, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Published>(v)
        assertEquals(LocalDate(2026, 10, 31), v.through)
        assertEquals(LocalDate(2026, 12, 31), v.stamp.last)
    }

    @Test fun aStampWithoutCheckedRunsHoldsTheCity() {
        // An envelope alone (a stamp from before ruling R115) proves no day: the safe side.
        val old = stamp("gb.london.lupt") { root ->
            root.remove("checked")
            @Suppress("UNCHECKED_CAST")
            val units = LinkedHashMap(root["units"] as Map<String, Any?>)
            @Suppress("UNCHECKED_CAST")
            units["gb.london.lupt"] = LinkedHashMap(units["gb.london.lupt"] as Map<String, Any?>).also { it.remove("checked") }
            root["units"] = units
        }
        val v = verdict(london, with = mapOf(old.entry to old))
        assertIs<Verdict.Held>(v)
        assertEquals("gb.london.lupt: no checked table day from 1 Sep 2026", v.reason)
    }

    @Test fun aCautiousMembersHoleHoldsTheCityAndNamesTheMember() {
        val holed = torontoMembers(
            "ca.ift" to mapOf(mosque to listOf("2026-01-01..2026-10-31", "2026-11-07..2026-11-27", "2026-12-01..2026-12-31")),
            "ca.iit" to mapOf(mosque to year),
            "ca.mac" to mapOf(mosque to year),
        )
        val v = verdict(toronto, oct1, nov30, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Held>(v)
        assertEquals("ca.ift (a member of ca.toronto): no checked table day 1 Nov 2026 – 6 Nov 2026", v.reason)
        // The same members over September and October: every day checked, through the run's end.
        val autumn = verdict(toronto, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Published>(autumn)
        assertEquals(LocalDate(2026, 10, 31), autumn.through)
    }

    @Test fun aMemberCheckedOnlyFarAwayCountsForNothingHere() {
        // Rows at Trondheim prove nothing at Oslo; at Toronto a member with rows only that far, and
        // no unit of its own covering the city in the stamps given, has no checked day here.
        val far = torontoMembers(
            "ca.ift" to mapOf(mosque to year),
            "ca.iit" to mapOf(trondheim to year),
            "ca.mac" to mapOf(mosque to year),
        )
        val v = verdict(toronto, with = mapOf(far.entry to far))
        assertIs<Verdict.Held>(v)
        assertEquals("ca.iit (a member of ca.toronto): no checked table at this place", v.reason)
    }

    @Test fun aMembersOwnUnitCoveringThePlaceProvesItsDays() {
        // The cautious file holds no rows for ca.iit near Toronto, but ca.iit is its own entry whose
        // unit covers the city: that unit's green checked days stand in.
        val cautious = torontoMembers("ca.ift" to mapOf(mosque to year), "ca.mac" to mapOf(mosque to year))
        val iit = stamps.getValue("ca.iit")
        val v = verdict(toronto, with = mapOf(cautious.entry to cautious, iit.entry to iit))
        assertIs<Verdict.Published>(v)
    }

    // ── The figures ──

    @Test fun fajrSharesAreTheLateHistogramOverItsOwnCheckedCount() {
        val london = stamps.getValue("gb.london.lupt")
        val shares = london.shares(london.unitEvents("gb.london.lupt")!!, "fajr")
        assertEquals(listOf("0", "1", "2", "3+"), shares.keys.toList())
        assertEquals(1.0, shares.values.sum(), 0.001)
        // The "same minute" share is the stamp's own exactShare, to the four decimals it keeps.
        assertEquals(0.3077, shares.getValue("0"), 0.00001)
    }

    @Test fun theEntryWideWorstIsOverTheStartsOnly() {
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

    @Test fun checkedRunsAreReadBackAsDates() {
        val runs = Checked.parse(listOf("2026-02-14..2026-02-18", "2026-02-20"))
        assertEquals(true, runs.covers(LocalDate(2026, 2, 18)))
        assertEquals(false, runs.covers(LocalDate(2026, 2, 19)))
        assertEquals(LocalDate(2026, 2, 19), runs.firstUncovered(LocalDate(2026, 2, 14), LocalDate(2026, 2, 20)))
        assertNull(runs.firstUncovered(LocalDate(2026, 2, 15), LocalDate(2026, 2, 17)))
        assertEquals(LocalDate(2026, 2, 18), runs.endOf(LocalDate(2026, 2, 16)))
        assertEquals(LocalDate(2026, 2, 20), runs.nextStartAfter(LocalDate(2026, 2, 19)))
        // A union merges touching and overlapping runs.
        val union = Checked.union(listOf(runs, Checked.parse(listOf("2026-02-19", "2026-02-21..2026-02-25"))))
        assertEquals(LocalDate(2026, 2, 25), union.endOf(LocalDate(2026, 2, 14)))
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
}
