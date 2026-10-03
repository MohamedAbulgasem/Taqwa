package world.taqwa.timetables

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.timetables.gate.GateManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The proven rule (spec §2, rulings R101, R115 and R116), clause by clause, against the committed
 * stamps, and which of its two months a page shows.
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
    private val sep30 = LocalDate(2026, 9, 30)
    private val oct1 = LocalDate(2026, 10, 1)
    private val oct31 = LocalDate(2026, 10, 31)
    private val nov1 = LocalDate(2026, 11, 1)

    private fun place(city: City) = Place(city.latitude, city.longitude, city.timeZone, city.countryCode, city.admin1)

    /** The month of [first] and the next, whole: the two a page may show when built in [first]'s month. */
    private fun months(first: LocalDate): List<ClosedRange<LocalDate>> {
        val start = LocalDate(first.year, first.month, 1)
        val next = start.plus(1, DateTimeUnit.MONTH)
        return listOf(start..next.minus(1, DateTimeUnit.DAY), next..next.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))
    }

    private fun verdict(city: City, first: LocalDate = sep1, with: Map<String, Stamp> = stamps) =
        Proven.verdict(timetable.source(city, first).effective, place(city), with, months(first))

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

    /**
     * Toronto's stamp with its members' checked runs by point replaced, and [excused] — by member,
     * by point, the runs a gate file excuses as faults there (ruling R117).
     */
    private fun torontoMembers(
        vararg members: Pair<String, Map<String, List<String>>>,
        excused: Map<String, Map<String, List<String>>> = emptyMap(),
    ) = stamp("ca.toronto") { root ->
        root["members"] = members.associate { (id, points) ->
            id to (
                mapOf("checked" to points.values.flatten(), "points" to points) +
                    excused[id]?.let { mapOf("excused" to it.mapValues { (_, runs) -> mapOf("fault" to runs) }) }.orEmpty()
                )
        }
    }

    /** A Toronto member's year without 1–6 November, and those days, as a gate file would excuse them. */
    private val yearWithoutNov1to6 = listOf("2026-01-01..2026-10-31", "2026-11-07..2026-12-31")
    private val nov1to6 = listOf("2026-11-01..2026-11-06")

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
        assertEquals(months(sep1), v.months) // September and October, both checked
        assertNull(v.nextUnchecked)
    }

    @Test fun tripoliIsHeldForItsClass() {
        val v = verdict(city("tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli"))
        assertIs<Verdict.Held>(v)
        assertTrue(v.reason.startsWith("class D_AUTHORITY"), v.reason)
    }

    @Test fun cairoShowsSeptemberAndOctoberThenOctoberAloneBecauseItsTablesEndInOctober() {
        // Ruling R116: the monitor's captures of 28 Sep and 2 Oct 2026 hold September and October, so a
        // page built in September shows both; from 1 October it shows October alone and names November.
        val cairo = city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo")
        val september = verdict(cairo)
        assertIs<Verdict.Published>(september)
        assertEquals(months(sep1), september.months)
        assertNull(september.nextUnchecked)
        val october = verdict(cairo, oct1)
        assertIs<Verdict.Published>(october)
        laterProofOnly(october, "eg.esa", nov1)
    }

    /**
     * A page built on 1 October from the committed stamps, which on 2 October 2026 held October whole and
     * November not from [firstUnchecked]: October alone, [firstUnchecked] named. The weekly monitor's prove
     * (docs/MONITOR.md) only adds checked days, so from then on the page may also show November (ruling
     * R116: whole months only) or name a later first unchecked day, never an earlier one.
     */
    private fun laterProofOnly(october: Verdict.Published, timetable: String, firstUnchecked: LocalDate) {
        assertEquals(oct1..oct31, october.months.first())
        assertTrue(october.months.size in 1..2, october.months.toString())
        assertTrue(october.through >= oct31, october.through.toString())
        val next = october.nextUnchecked
        if (october.months.size == 1) {
            assertTrue(next != null && next.timetable == timetable && next.day >= firstUnchecked, next.toString())
        } else {
            assertEquals(nov1..LocalDate(2026, 11, 30), october.months[1])
            assertTrue(next == null || next.day > LocalDate(2026, 11, 30), next.toString())
        }
    }

    @Test fun istanbulIsHeldInSeptemberAndShowsOctoberAloneUntilNovemberIsChecked() {
        // Diyanet's captures hold 25 Sep – 1 Nov 2026 (the research's to 25 Oct, the weekly monitor's of
        // 28 Sep to 29 Oct and of 2 Oct to 1 Nov) and 2027: September's first days were never checked
        // (review C1, ruling R115), so a September page is held; October is checked whole, so from
        // 1 October the page shows it alone (ruling R116) and names 2 November, the first day unchecked.
        val september = verdict(istanbul)
        assertIs<Verdict.Held>(september)
        assertEquals("tr.diyanet: no checked table day 1 Sep 2026 – 24 Sep 2026", september.reason)
        val october = verdict(istanbul, oct1)
        assertIs<Verdict.Published>(october)
        laterProofOnly(october, "tr.diyanet", LocalDate(2026, 11, 2))
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
        // 1,400 on 2 October 2026; the weekly monitor's prove only adds rows (docs/MONITOR.md).
        assertTrue(v.stamp.placeDays >= 1400, v.stamp.placeDays.toString())
    }

    @Test fun torontoShowsOctoberAndNovemberWithIftsFaultDaysExcused() {
        // IFT's table keeps daylight time on 1–6 November and prints Isha 5 min after its neighbours on
        // 28–30 November; ca-toronto.tsv records both as IFT's faults, and IIT's and MAC's member rows
        // leave the same days out. Ruling R117: those days do not hold the page — on them IIT's and
        // MAC's own tables are checked at their units — so from 1 October the page shows October and
        // November, the nine days named as excused for IFT.
        val v = verdict(toronto, oct1)
        assertIs<Verdict.Published>(v)
        assertEquals(months(oct1), v.months)
        assertNull(v.nextUnchecked)
        // 31 December on 2 October 2026; later once the weekly monitor's prove holds IFT's next year.
        assertTrue(v.through >= LocalDate(2026, 12, 31), v.through.toString())
        assertNull(v.atMost)
        assertEquals(listOf(Excused("ca.ift (a member of ca.toronto)", "recorded faults", 9)), v.excused)
        // Without IIT's and MAC's own stamps no member is checked on those days: a hole, as before.
        val alone = verdict(toronto, oct1, with = stamps - "ca.iit" - "ca.mac")
        assertIs<Verdict.Published>(alone)
        assertEquals(listOf(oct1..oct31), alone.months)
        assertEquals(Unchecked("ca.toronto (every member's table excused)", nov1), alone.nextUnchecked)
        assertEquals(oct31, alone.through)
        assertEquals(emptyList(), alone.excused)
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

    @Test fun aHoleInTheCurrentMonthHoldsTheCityAndIsNamed() {
        val holed = londonChecked("2026-01-01..2026-10-25", "2027-01-01..2027-12-31")
        val v = verdict(london, oct1, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Held>(v)
        assertEquals("gb.london.lupt: no checked table day 26 Oct 2026 – 31 Dec 2026", v.reason)
    }

    // ── Ruling R116: whole months — the current one or no page, the next only when checked too ──

    @Test fun theCurrentMonthAndTheNextBothCheckedShowTwoMonths() {
        val whole = londonChecked("2026-09-01..2026-10-31")
        val v = verdict(london, with = mapOf(whole.entry to whole))
        assertIs<Verdict.Published>(v)
        assertEquals(listOf(sep1..sep30, oct1..oct31), v.months)
        assertNull(v.nextUnchecked)
        assertEquals(oct31, v.through)
    }

    @Test fun theCurrentMonthAloneCheckedShowsItAlone() {
        // The table ends with the current month: September is shown, October waits for its table.
        val september = londonChecked("2026-01-01..2026-09-30")
        val v = verdict(london, with = mapOf(september.entry to september))
        assertIs<Verdict.Published>(v)
        assertEquals(listOf(sep1..sep30), v.months)
        assertEquals(Unchecked("gb.london.lupt", oct1), v.nextUnchecked)
        assertEquals(sep30, v.through)
        assertEquals(5, v.atMost) // the figures are the unit's, whatever the months shown
    }

    @Test fun aCurrentMonthNotCheckedWholeHoldsTheCityWhateverTheNext() {
        // One unchecked day of September holds the page, though October is checked whole.
        val late = londonChecked("2026-09-02..2026-12-31")
        val v = verdict(london, with = mapOf(late.entry to late))
        assertIs<Verdict.Held>(v)
        assertEquals("gb.london.lupt: no checked table day 1 Sep 2026 – 1 Sep 2026", v.reason)
    }

    @Test fun aHoleInTheNextMonthShowsTheCurrentMonthAlone() {
        // One unchecked day of October: a partly checked month is never shown, so September stands
        // alone and the notice names the day. Through is still the end of the run September sits in.
        val holed = londonChecked("2026-01-01..2026-10-14", "2026-10-16..2026-12-31")
        val v = verdict(london, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Published>(v)
        assertEquals(listOf(sep1..sep30), v.months)
        assertEquals(Unchecked("gb.london.lupt", LocalDate(2026, 10, 15)), v.nextUnchecked)
        assertEquals(LocalDate(2026, 10, 14), v.through)
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
        val with = mapOf(holed.entry to holed)
        // In November the member's hole is in the current month: no page.
        val v = verdict(toronto, nov1, with = with)
        assertIs<Verdict.Held>(v)
        assertEquals("ca.ift (a member of ca.toronto): no checked table day 1 Nov 2026 – 6 Nov 2026", v.reason)
        // In October it is in the next: October alone, the member named.
        val october = verdict(toronto, oct1, with = with)
        assertIs<Verdict.Published>(october)
        assertEquals(listOf(oct1..oct31), october.months)
        assertEquals(Unchecked("ca.ift (a member of ca.toronto)", nov1), october.nextUnchecked)
        // The same members over September and October: every day checked, through the run's end.
        val autumn = verdict(toronto, with = with)
        assertIs<Verdict.Published>(autumn)
        assertEquals(listOf(sep1..sep30, oct1..oct31), autumn.months)
        assertEquals(oct31, autumn.through)
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

    // ── Ruling R117: a member's day excused for a recorded reason ──

    @Test fun aMembersExcusedDayIsCoveredWhereAnotherMemberIsChecked() {
        val excused = torontoMembers(
            "ca.ift" to mapOf(mosque to yearWithoutNov1to6),
            "ca.iit" to mapOf(mosque to year),
            "ca.mac" to mapOf(mosque to year),
            excused = mapOf("ca.ift" to mapOf(mosque to nov1to6)),
        )
        val v = verdict(toronto, nov1, with = mapOf(excused.entry to excused))
        assertIs<Verdict.Published>(v)
        assertEquals(months(nov1), v.months)
        assertNull(v.nextUnchecked)
        assertEquals(LocalDate(2026, 12, 31), v.through)
        assertEquals(listOf(Excused("ca.ift (a member of ca.toronto)", "recorded faults", 6)), v.excused)
    }

    @Test fun aDayEveryMemberIsExcusedOnIsAHole() {
        // Every shown day needs one member actually checked: excused by all three, 1–6 November holds.
        val all = torontoMembers(
            "ca.ift" to mapOf(mosque to yearWithoutNov1to6),
            "ca.iit" to mapOf(mosque to yearWithoutNov1to6),
            "ca.mac" to mapOf(mosque to yearWithoutNov1to6),
            excused = listOf("ca.ift", "ca.iit", "ca.mac").associateWith { mapOf(mosque to nov1to6) },
        )
        val with = mapOf(all.entry to all)
        val v = verdict(toronto, nov1, with = with)
        assertIs<Verdict.Held>(v)
        assertEquals("ca.toronto: no member's table checked day 1 Nov 2026 – 6 Nov 2026 (every member's excused)", v.reason)
        val october = verdict(toronto, oct1, with = with)
        assertIs<Verdict.Published>(october)
        assertEquals(listOf(oct1..oct31), october.months)
        assertEquals(Unchecked("ca.toronto (every member's table excused)", nov1), october.nextUnchecked)
        // Where a member's own unit was checked on those days (IIT's table, whole at its mosque), it is checked there.
        val unit = verdict(toronto, nov1, with = with + ("ca.iit" to stamps.getValue("ca.iit")))
        assertIs<Verdict.Published>(unit)
        assertEquals(months(nov1), unit.months)
        assertEquals(
            listOf("ca.ift", "ca.mac").map { Excused("$it (a member of ca.toronto)", "recorded faults", 6) },
            unit.excused,
        )
    }

    @Test fun anUnrecordedGapStillHoldsTheCity() {
        // Only 1–3 November are excused; 4–6 are simply missing: the member is named from 4 November.
        val part = torontoMembers(
            "ca.ift" to mapOf(mosque to yearWithoutNov1to6),
            "ca.iit" to mapOf(mosque to year),
            "ca.mac" to mapOf(mosque to year),
            excused = mapOf("ca.ift" to mapOf(mosque to listOf("2026-11-01..2026-11-03"))),
        )
        val v = verdict(toronto, nov1, with = mapOf(part.entry to part))
        assertIs<Verdict.Held>(v)
        assertEquals("ca.ift (a member of ca.toronto): no checked table day 4 Nov 2026 – 6 Nov 2026", v.reason)
    }

    @Test fun anExcuseFarAwayExcusesNothingHere() {
        // The excuse is the member's at the point its rows were read at: Trondheim's prove nothing at Toronto.
        val far = torontoMembers(
            "ca.ift" to mapOf(mosque to yearWithoutNov1to6),
            "ca.iit" to mapOf(mosque to year),
            "ca.mac" to mapOf(mosque to year),
            excused = mapOf("ca.ift" to mapOf(trondheim to nov1to6)),
        )
        val v = verdict(toronto, nov1, with = mapOf(far.entry to far))
        assertIs<Verdict.Held>(v)
        assertEquals("ca.ift (a member of ca.toronto): no checked table day 1 Nov 2026 – 6 Nov 2026", v.reason)
    }

    @Test fun aClassAOrBPageIsUnchangedByExcuses() {
        // Ruling R117 is a cautious place's: an A or B page needs its own unit checked on every day it shows,
        // whatever a stamp says of members.
        val holed = stamp("gb.london.lupt") { root ->
            @Suppress("UNCHECKED_CAST")
            val units = LinkedHashMap(root["units"] as Map<String, Any?>)
            @Suppress("UNCHECKED_CAST")
            units["gb.london.lupt"] = LinkedHashMap(units["gb.london.lupt"] as Map<String, Any?>).also {
                it["checked"] = listOf("2026-01-01..2026-09-14", "2026-09-16..2026-12-31")
            }
            root["units"] = units
            root["members"] = mapOf(
                "gb.london.lupt" to mapOf("points" to mapOf("51.5,-0.12" to year), "excused" to mapOf("51.5,-0.12" to mapOf("fault" to listOf("2026-09-15")))),
            )
        }
        val v = verdict(london, with = mapOf(holed.entry to holed))
        assertIs<Verdict.Held>(v)
        assertEquals("gb.london.lupt: no checked table day 15 Sep 2026 – 15 Sep 2026", v.reason)
        val whole = verdict(london)
        assertIs<Verdict.Published>(whole)
        assertEquals(emptyList(), whole.excused)
    }

    @Test fun checkedRunsIntersectAndSubtract() {
        val a = Checked.parse(listOf("2026-11-01..2026-11-30"))
        val b = Checked.parse(listOf("2026-11-05..2026-11-07", "2026-11-20..2026-12-10"))
        assertEquals(listOf(LocalDate(2026, 11, 5)..LocalDate(2026, 11, 7), LocalDate(2026, 11, 20)..LocalDate(2026, 11, 30)), a.intersect(b).runs)
        assertEquals(
            listOf(LocalDate(2026, 11, 1)..LocalDate(2026, 11, 4), LocalDate(2026, 11, 8)..LocalDate(2026, 11, 19)),
            a.minus(b).runs,
        )
        assertEquals(6, b.daysIn(LocalDate(2026, 11, 1), LocalDate(2026, 11, 22)))
        assertEquals(0, Checked.NONE.daysIn(LocalDate(2026, 11, 1), LocalDate(2026, 11, 22)))
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
