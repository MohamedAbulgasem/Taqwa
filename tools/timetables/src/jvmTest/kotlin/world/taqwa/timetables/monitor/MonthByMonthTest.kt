package world.taqwa.timetables.monitor

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.Cover
import world.taqwa.timetables.gate.DateRun
import world.taqwa.timetables.gate.GateManifest
import world.taqwa.timetables.gate.Ramadan
import world.taqwa.timetables.gate.StampDemotion
import world.taqwa.timetables.gate.StampSummary
import world.taqwa.timetables.gate.UnitSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Ruling R185 (the owner's O4), condition by condition, on invented sources, recipes, captures and
 * stamps — rules, never a live figure: which of today's entries the rule covers moves with what
 * their authorities publish, and is `checkStamps -Pramadan`'s to say, not a test's.
 */
class MonthByMonthTest {

    private val ramadan = Ramadan.of(1448)
    private val gateHeader = GateManifest.HEADER.joinToString("\t")

    /** Two gate files: the invented authority's own (unit-less), and one of an authority checked by unit. */
    private val manifest = GateManifest(
        GateManifest.parse("xx-auth.tsv", "$gateHeader\nopen/a.txt\txx.auth\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t").rows +
            GateManifest.parse("yy-units.tsv", "$gateHeader\nopen/b.txt\tyy.units/east\t1.0\t10.0\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t").rows +
            GateManifest.parse("zz-other.tsv", "$gateHeader\nopen/c.txt\tzz.other\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t").rows,
        listOf("xx-auth.tsv", "yy-units.tsv", "zz-other.tsv"),
    )

    private fun sources(vararg lines: String) = Source.parse(Source.HEADER.joinToString("\t") + "\n" + lines.joinToString("\n"))

    private fun recipes(vararg lines: String) = Recipes.parse(Recipes.HEADER.joinToString("\t") + "\n" + lines.joinToString("\n") + "\n")

    private fun table(source: String, key: String, entry: String?, lat: Double? = null, lon: Double? = null, name: String = key) = MonitorTable(
        source, key, "archive/tables/monitor/$source/$key.txt", name, lat, lon, "Asia/Singapore", null, "XX", entry, null,
        "F+E S D A M I", "daily", "standard", "hash", "2026-11-12", "",
    )

    private val weekly = sources("xx-auth\txx.auth (and xx.cautious as its member)\tfetch\tweekly\t-\t\t")
    private val ownRows = recipes("xx-auth\t*\txx-auth.tsv\tindex\t-\tindex\tall\t-")
    private val capture = table("xx-auth", "city-2026", "xx.auth")

    private fun days(from: String, to: String): Set<LocalDate> =
        generateSequence(LocalDate.parse(from)) { LocalDate.fromEpochDays(it.toEpochDays() + 1) }.takeWhile { it <= LocalDate.parse(to) }.toSet()

    private val year2026 = days("2026-01-01", "2026-12-31")

    /** The day the guard runs, and a fetch of every source that ended well three days before it. */
    private val today = LocalDate(2026, 11, 13)
    private val fetchedOk = { _: String -> MonthByMonth.FetchRun(LocalDate(2026, 11, 10), "ok") }

    private fun rule(
        sources: List<Source> = weekly,
        recipes: List<Recipe> = ownRows,
        index: List<MonitorTable>? = listOf(capture),
        printed: (MonitorTable) -> Set<LocalDate>? = { year2026 },
        unitAt: (String, Double, Double, String) -> String? = { _, _, _, _ -> null },
        lastFetch: (String) -> MonthByMonth.FetchRun? = fetchedOk,
    ) = MonthByMonth(sources, recipes, index, manifest, printed, unitAt, lastFetch, today)

    private fun runs(vararg runs: String) = runs.map(DateRun::parse)

    /** The rule with a capture that prints no day yet: condition (a) alone is in question. */
    private val quiet get() = rule(printed = { emptySet() })

    /**
     * A green class B stamp of the invented authority, checked whole on [checked] for each of [events]: by default
     * the whole of 2026, which holds Ramadan 1447 (18 Feb–19 Mar 2026) and the months since; a month of Ramadan
     * days held out of the fit.
     */
    private fun stamp(
        checked: List<DateRun> = runs("2026-01-01..2026-12-31"),
        events: List<String> = Ramadan.EVENTS,
        broken: Int = 0,
        demoted: List<StampDemotion> = emptyList(),
        heldOutRamadanDays: Int = 30,
    ) = StampSummary(
        "xx.auth", setOf("B"), checked.firstOrNull()?.from, checked.lastOrNull()?.to, broken, checked, emptyList(),
        events.associateWith { checked }, demoted, heldOutRamadanDays,
    )

    private fun verdict(rule: MonthByMonth, stamp: StampSummary) = rule.verdict(stamp, ramadan, stamp.ramadanGaps(ramadan))

    private fun named(rule: MonthByMonth, stamp: StampSummary): List<String> = assertIs<Cover.Named>(verdict(rule, stamp)).reasons

    @Test
    fun `an entry proven on the months held and a Ramadan before, fetched into its own gate rows with every day fetched in the gate, is covered`() {
        val covered = assertIs<Cover.Covered>(verdict(rule(), stamp()))
        assertTrue("1 unit short of Ramadan 1448" in covered.words, covered.words)
        assertTrue(
            "(a) each proven on at least 28 days of Fajr, the end of eating and Maghrib in 2026-02-07..2027-02-06 and on at least 10 of each " +
                "in an earlier Ramadan, the entry on 30 Ramadan place-days held out of the fit" in covered.words,
            covered.words,
        )
        // What (b) is, in its own words: the monitor's cadence, never "as published"; and how far the captures print.
        assertTrue(
            "(b) fetched on the monitor's cadence, not on the day of publication (xx-auth weekly, last ok 2026-11-10, its captures printing " +
                "through 2026-12-31; a missed run leaves its days unchecked until the next), every day fetched in the gate" in covered.words,
            covered.words,
        )
        assertTrue("(c) demoted by prove on the first failing day of the authority's own capture" in covered.words, covered.words)
    }

    @Test
    fun `a - a method proven on fewer than a month's days of any of the three events within the twelve months before is named`() {
        // Ramadan 1446 (March 2025) whole, so that only the months before this Ramadan are in question.
        val r1446 = "2025-02-28..2025-03-31"
        // 27 days: one short of a month.
        val thin = named(quiet, stamp(runs(r1446, "2026-12-05..2026-12-31")))
        assertTrue(thin.single().startsWith("(a) not proven on a month's days (28 of each of Fajr, the end of eating and Maghrib in 2026-02-07..2027-02-06"), thin.toString())
        assertTrue("xx.auth (fajr on 27 days, endOfEating on 27 days, maghrib on 27 days)" in thin.single(), thin.toString())
        assertIs<Cover.Covered>(verdict(quiet, stamp(runs(r1446, "2026-12-04..2026-12-31"))))
        // Two runs count together.
        assertIs<Cover.Covered>(verdict(quiet, stamp(runs(r1446, "2026-06-01..2026-06-14", "2026-12-18..2026-12-31"))))
        // Days over twelve months before the guarded days begin (7 February 2027) do not count: a year and more of old
        // days and one recent day is one day (the review of 13 Nov 2026: the sum of all days ever let this pass).
        val old = named(quiet, stamp(runs("2025-01-01..2026-02-06")))
        assertTrue("fajr on 0 days" in old.single(), old.toString())
        val oneRecent = named(quiet, stamp(runs("2025-01-01..2026-02-07")))
        assertTrue("fajr on 1 days" in oneRecent.single(), oneRecent.toString())
        assertIs<Cover.Covered>(verdict(quiet, stamp(runs("2025-01-01..2026-03-06"))))
        // Nor do the guarded days themselves, which are what is not yet proven.
        assertTrue("fajr on 27 days" in named(rule(printed = { emptySet() }), stamp(runs(r1446, "2027-01-11..2027-02-10"))).single())
        // Pretoria's shape (ruling R148): tables with no end of eating prove nothing of a fast's beginning.
        val noEnd = named(rule(), stamp(events = listOf("fajr", "maghrib")))
        assertTrue("endOfEating on 0 days" in noEnd.single() && "fajr on" !in noEnd.single(), noEnd.toString())
        // A red stamp is no proof either.
        assertTrue("(a) its stamp is red" in named(rule(), stamp(broken = 2)))
    }

    @Test
    fun `a - a method never checked through a Ramadan before, or never held out in one, is named`() {
        // Amman's shape: three months of autumn days and none of any fasting month.
        val autumn = named(quiet, stamp(runs("2026-10-01..2026-12-31")))
        assertEquals(1, autumn.size, autumn.toString())
        assertTrue("xx.auth (no earlier Ramadan checked)" in autumn.single(), autumn.toString())
        // A stray day or two of the month is not the month: nine days of Ramadan 1447 (guarded 17 Feb–20 Mar 2026).
        val stray = named(quiet, stamp(runs("2026-02-17..2026-02-25", "2026-10-01..2026-12-31")))
        assertTrue("xx.auth (an earlier Ramadan on 9 of its days at most (Ramadan 1447))" in stray.single(), stray.toString())
        assertIs<Cover.Covered>(verdict(quiet, stamp(runs("2026-02-17..2026-02-26", "2026-10-01..2026-12-31"))))
        // Every third day through the month counts (INM's far delegations): eleven days.
        val third = generateSequence(LocalDate(2026, 2, 17)) { LocalDate.fromEpochDays(it.toEpochDays() + 3) }.takeWhile { it <= LocalDate(2026, 3, 20) }.map { "$it" }.toList()
        assertEquals(11, third.size)
        assertIs<Cover.Covered>(verdict(quiet, stamp(runs(*third.toTypedArray(), "2026-10-01..2026-12-31"))))
        // The best single Ramadan decides, never two thin ones added up; and one five years back still counts, one six does not.
        val two = named(quiet, stamp(runs("2025-03-01..2025-03-06", "2026-02-18..2026-02-23", "2026-10-01..2026-12-31")))
        assertTrue("an earlier Ramadan on 6 of its days at most" in two.single(), two.toString())
        val r1443 = Ramadan.of(1443)
        val r1442 = Ramadan.of(1442)
        assertIs<Cover.Covered>(verdict(quiet, stamp(runs("${r1443.first}..${r1443.last}", "2026-10-01..2026-12-31"))))
        assertTrue("no earlier Ramadan checked" in named(quiet, stamp(runs("${r1442.first}..${r1442.last}", "2026-10-01..2026-12-31"))).single())
        // Each event its own: a month of Fajr and Maghrib and three days of the end of eating.
        val events = StampSummary(
            "xx.auth", setOf("B"), LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), 0, runs("2026-01-01..2026-12-31"), emptyList(),
            mapOf("fajr" to runs("2026-01-01..2026-12-31"), "maghrib" to runs("2026-01-01..2026-12-31"), "endOfEating" to runs("2026-02-17..2026-02-19", "2026-04-01..2026-12-31")),
            heldOutRamadanDays = 30,
        )
        assertTrue("an earlier Ramadan on 3 of its days at most (Ramadan 1447)" in named(quiet, events).single(), named(quiet, events).toString())
        // In-sample days alone: fitted on its Ramadan, tested on none it was not fitted to.
        val fitted = named(rule(), stamp(heldOutRamadanDays = 9))
        assertEquals(
            listOf("(a) its method was never tested on a Ramadan it was not fitted to: 9 Ramadan place-days in rows held out of the fit, fewer than 10 (the stamp's heldOut.ramadanDays, the entry's as a whole)"),
            fitted,
        )
        assertIs<Cover.Covered>(verdict(rule(), stamp(heldOutRamadanDays = 10)))
        // The stamp's own count is read (`heldOut.ramadanDays`); a stamp without it has none.
        fun parsed(heldOut: String) = StampSummary.of(
            @Suppress("UNCHECKED_CAST") (world.taqwa.timetables.Json.parse("""{"entry": "xx.auth", "class": "B", "broken": 0$heldOut}""") as Map<String, Any?>), "xx.auth",
        )
        assertEquals(44, parsed(""", "heldOut": {"placeDays": 638, "ramadanDays": 44}""").heldOutRamadanDays)
        assertEquals(0, parsed("").heldOutRamadanDays)
    }

    @Test
    fun `b - an entry with no automatic source, no capture, or no recipe that makes the capture its own gate row is named`() {
        val none = "(b) no automatic source of sources.tsv holds a table of the authority's own site that the recipes turn into this entry's own rows at 1 of its units: xx.auth"
        // Read by hand: either field says so.
        assertEquals(listOf(none), named(rule(sources("xx-auth\txx.auth\tmanual\tmanual\t2027-01\t\t")), stamp()))
        assertEquals(listOf(none), named(rule(sources("xx-auth\txx.auth\tfetch\tmanual\t2027-01\t\t")), stamp()))
        // A source of another entry, though its capture names this one; and no source at all.
        assertEquals(listOf(none), named(rule(sources("xx-auth\tzz.other\tfetch\tweekly\t-\t\t")), stamp()))
        assertEquals(listOf(none), named(rule(sources("zz-other\tzz.other\tfetch\tweekly\t-\t\t")), stamp()))
        // The source is fetched but holds no table yet.
        assertEquals(listOf(none), named(rule(index = emptyList()), stamp()))
        // No recipe line; a line that makes only a cautious entry's member row; a line into a gate file without the entry.
        assertEquals(listOf(none), named(rule(recipes = recipes("zz-other\t*\tzz-other.tsv\tindex\t-\tindex\tall\t-")), stamp()))
        assertEquals(listOf(none), named(rule(recipes = recipes("xx-auth\t*\txx-auth.tsv\txx.auth\tindex\tall\tall\t-")), stamp()))
        assertEquals(listOf(none), named(rule(recipes = recipes("xx-auth\t*\tzz-other.tsv\tindex\t-\tindex\tall\t-")), stamp()))
        // A capture the index files under another entry.
        assertEquals(listOf(none), named(rule(index = listOf(table("xx-auth", "city-2026", "zz.other"))), stamp()))
        // A line a more specific line of the same gate file replaces does not count (Recipes.applies).
        val replaced = recipes("xx-auth\t*\txx-auth.tsv\tindex\t-\tindex\tall\t-", "xx-auth\tcity-*\txx-auth.tsv\tzz.other\t-\tindex\tall\t-")
        assertEquals(listOf(none), named(rule(recipes = replaced), stamp()))
    }

    @Test
    fun `b - without the monitor's index nothing is covered`() {
        val reasons = named(rule(index = null), stamp())
        assertTrue(reasons.single().startsWith("(b) the monitor's index is not here"), reasons.toString())
        // As the committed files stand without the archive: every entry stays named.
        val committed = MonthByMonth.load(TestPaths.repoRoot, null, GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate")), today)
        assertTrue(named(committed, stamp()).any { it.startsWith("(b) the monitor's index is not here") })
        val nowhere = MonthByMonth.load(TestPaths.repoRoot, java.io.File("/nonexistent/official"), manifest, today)
        assertTrue(named(nowhere, stamp()).any { it.startsWith("(b) the monitor's index is not here") })
    }

    @Test
    fun `b - a table read from a republisher's mirror is not the authority being watched, and its days are published all the same`() {
        // JAKIM's shape: the year ahead read from the mirror when e-solat fails, the fetcher saying so in the table's name.
        val mirror = table("xx-auth", "city-2027", "xx.auth", name = "City 2027 (mirror)")
        assertTrue(mirror.mirror && !capture.mirror)
        val only = named(rule(index = listOf(mirror), printed = { emptySet() }), stamp())
        assertEquals(
            listOf(
                "(b) no automatic source of sources.tsv holds a table of the authority's own site that the recipes turn into this entry's own rows at " +
                    "1 of its units: xx.auth (xx-auth/city-2027 only, a republisher's mirror)",
            ),
            only,
        )
        // Beside the authority's own capture the entry is fed; what the mirror prints of the month and the gate does not hold still names it.
        val printed = { t: MonitorTable -> if (t.mirror) days("2027-01-01", "2027-12-31") else year2026 }
        val both = named(rule(index = listOf(capture, mirror), printed = printed), stamp())
        assertEquals(1, both.size, both.toString())
        assertTrue("xx-auth/city-2027 already prints 32 of xx.auth's unchecked days (2027-02-07..2027-03-10)" in both.single(), both.toString())
        // A mirror's other days are prove's to take or leave: they do not stand against the authority's own captures.
        assertIs<Cover.Covered>(verdict(rule(index = listOf(capture, mirror), printed = { t -> if (t.mirror) days("2027-01-01", "2027-02-06") else year2026 }), stamp()))
    }

    @Test
    fun `b - a Ramadan day the authority has already published and the gate does not hold is not a day still to come`() {
        // The capture prints 7–10 February 2027 as well: prove left its row out (or has not run), so the entry is named.
        val printed = year2026 + days("2027-01-01", "2027-02-10")
        val reasons = named(rule(printed = { printed }), stamp(runs("2026-01-01..2027-02-06")))
        assertEquals(1, reasons.size, reasons.toString())
        assertTrue(reasons.single().startsWith("(b) days its authority has already published are not in the gate"), reasons.toString())
        assertTrue("xx-auth/city-2026 already prints 4 of xx.auth's unchecked days (2027-02-07..2027-02-10)" in reasons.single(), reasons.toString())
        // Days published up to the eve of the guarded days are no such day.
        assertIs<Cover.Covered>(verdict(rule(printed = { year2026 + days("2027-01-01", "2027-02-06") }), stamp(runs("2026-01-01..2027-02-06"))))
        // Once the gate holds them, they are no longer unchecked: here through 10 February, the capture printing no further.
        assertIs<Cover.Covered>(verdict(rule(printed = { printed }), stamp(runs("2026-01-01..2027-02-10"))))
        // A capture that cannot be read proves nothing about what is published.
        val unread = named(rule(printed = { null }), stamp())
        assertTrue("xx-auth/city-2026 could not be read" in unread.single(), unread.toString())
    }

    @Test
    fun `b - every day the monitor fetched is in the gate, or prove is not seen doing its part`() {
        // The capture prints January 2027 and the stamp stops with 2026: fetched, and prove has not put it into the gate
        // (the review of 13 Nov 2026: the guard read the fetch records alone, and said "checked by prove").
        val reasons = named(rule(printed = { year2026 + days("2027-01-01", "2027-01-31") }), stamp())
        assertEquals(
            listOf(
                "(b) prove has not put every day the monitor fetched into the gate (it left their row out, or has not run since the fetch): " +
                    "31 of the days xx-auth/city-2026 prints (2027-01-01..2027-01-31) at xx.auth",
            ),
            reasons,
        )
        assertIs<Cover.Covered>(verdict(rule(printed = { year2026 + days("2027-01-01", "2027-01-31") }), stamp(runs("2026-01-01..2027-01-31"))))
        // Each event on its own: a day in the gate for Fajr and Maghrib and not for the end of eating is not in the gate.
        val partly = StampSummary(
            "xx.auth", setOf("B"), LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), 0, runs("2026-01-01..2026-12-31"), emptyList(),
            mapOf("fajr" to runs("2026-01-01..2026-12-31"), "maghrib" to runs("2026-01-01..2026-12-31"), "endOfEating" to runs("2026-01-01..2026-12-24")),
            heldOutRamadanDays = 30,
        )
        assertTrue("7 of the days xx-auth/city-2026 prints (2026-12-25..2026-12-31)" in named(rule(), partly).single())
    }

    @Test
    fun `b - an imsak that only a table read by hand prints is fetched by no source and the entry stays named`() {
        // Singapore's shape (ruling R246): the year's table, fetched, prints the prayers on every day and no imsak; the
        // imsak is checked on the 30 days of last Ramadan's own timetable, read by hand. (a) holds for the imsak too
        // (30 days within the twelve months before, which are an earlier Ramadan's), and nothing fetches the month's imsak.
        fun shows(imsak: List<DateRun>) = StampSummary(
            "xx.auth", setOf("B"), LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), 0, runs("2026-01-01..2026-12-31"), emptyList(),
            Ramadan.EVENTS.associateWith { runs("2026-01-01..2026-12-31") } + ("imsak" to imsak),
            heldOutRamadanDays = 30, showsImsak = true,
        )
        val reasons = named(rule(), shows(runs("2026-02-18..2026-03-19")))
        assertEquals(
            listOf(
                "(b) the tables the monitor fetches are not seen printing its imsak, so the imsak of Ramadan 1448 (2027-02-08..2027-03-09, " +
                    "guarded 2027-02-07..2027-03-10) waits for a table read by hand at 1 of its units: xx.auth (of the 328 days of " +
                    "2026-02-07..2027-02-06 that xx-auth/city-2026 prints and its Fajr is checked on, the imsak is checked on 30)",
            ),
            reasons,
        )
        // The same entry whose captures print the imsak on every day they print is covered like any other.
        assertIs<Cover.Covered>(verdict(rule(), shows(runs("2026-01-01..2026-12-31"))))
        // And an entry that shows no imsak is not asked for one.
        assertIs<Cover.Covered>(verdict(rule(), stamp()))
    }

    @Test
    fun `b - a guarded day the gate holds on Fajr, the end of eating and Maghrib is not called published and left out for its imsak alone`() {
        // Singapore's shape whole (the reviews of 20 Nov 2026, the merge review's minor 3): every event checked from 2026 to
        // 2027 but the imsak, which is checked on the 30 days of last Ramadan's own timetable alone, read by hand (a manual
        // source); and a second capture, the next year's table, that prints the guarded days without an imsak. Those days
        // are in the gate on the three: they are not days "already published and not in the gate", and the one reason is
        // that the month's imsak waits for a table read by hand.
        val sources = sources("xx-auth\txx.auth\tfetch\tweekly\t-\t\t", "xx-auth-imsakiah\txx.auth\tmanual\tmanual\t2027-01\t\t")
        val nextYear = table("xx-auth", "city-2027", "xx.auth")
        val printed = { t: MonitorTable -> if (t.key == "city-2027") days("2027-01-01", "2027-12-31") else year2026 }
        fun shows(endOfEating: List<DateRun>) = StampSummary(
            "xx.auth", setOf("B"), LocalDate(2026, 1, 1), LocalDate(2027, 12, 31), 0, runs("2026-01-01..2027-12-31"), emptyList(),
            mapOf("fajr" to runs("2026-01-01..2027-12-31"), "endOfEating" to endOfEating, "maghrib" to runs("2026-01-01..2027-12-31")) +
                ("imsak" to runs("2026-02-18..2026-03-19")),
            heldOutRamadanDays = 30, showsImsak = true,
        )
        val reasons = named(rule(sources = sources, index = listOf(capture, nextYear), printed = printed), shows(runs("2026-01-01..2027-12-31")))
        assertEquals(
            listOf(
                "(b) the tables the monitor fetches are not seen printing its imsak, so the imsak of Ramadan 1448 (2027-02-08..2027-03-09, " +
                    "guarded 2027-02-07..2027-03-10) waits for a table read by hand at 1 of its units: xx.auth (of the 365 days of " +
                    "2026-02-07..2027-02-06 that xx-auth/city-2026, xx-auth/city-2027 print and its Fajr is checked on, the imsak is checked on 30)",
            ),
            reasons,
        )
        // A guarded day one of the three is not checked on, and the next year's capture prints, is still one left out.
        val short = named(
            rule(sources = sources, index = listOf(capture, nextYear), printed = printed),
            shows(runs("2026-01-01..2027-02-20", "2027-03-11..2027-12-31")),
        )
        assertEquals(2, short.size, short.toString())
        assertTrue("xx-auth/city-2027 already prints 18 of xx.auth's unchecked days (2027-02-21..2027-03-10)" in short[0], short.toString())
        assertTrue(short[1].startsWith("(b) the tables the monitor fetches are not seen printing its imsak"), short.toString())
    }

    @Test
    fun `b - a source whose newest fetch on record did not end ok, is too old for its cadence, brought no current day, or is missing is not seen fetching`() {
        fun last(date: String, status: String) = { _: String -> MonthByMonth.FetchRun(LocalDate.parse(date), status) }
        val seen = "(b) the monitor is not seen fetching it (a fetch that ended ok, recent for its cadence, in monitor/fetch): "
        // A broken fetcher checks no day (the shape of a page that moved): failed, or come back in part.
        assertEquals(listOf(seen + "xx-auth's last fetch (2026-11-11) ended failed"), named(rule(lastFetch = last("2026-11-11", "failed")), stamp()))
        assertEquals(listOf(seen + "xx-auth's last fetch (2026-11-11) ended partial"), named(rule(lastFetch = last("2026-11-11", "partial")), stamp()))
        // Never fetched at all.
        assertEquals(listOf(seen + "xx-auth has no fetch on record"), named(rule(lastFetch = { null }), stamp()))
        // A weekly source: one run's days and a day, and no more — a missed run is seven days nothing compared.
        assertIs<Cover.Covered>(verdict(rule(lastFetch = last("2026-11-05", "ok")), stamp()))
        assertEquals(
            listOf(seen + "xx-auth was last fetched on 2026-11-04, over 8 days before 2026-11-13"),
            named(rule(lastFetch = last("2026-11-04", "ok")), stamp()),
        )
        // A source fetched once a month has a month and a day.
        val monthly = sources("xx-auth\txx.auth\tfetch\tmonth-start\t-\t\t")
        assertIs<Cover.Covered>(verdict(rule(monthly, lastFetch = last("2026-10-12", "ok")), stamp()))
        assertTrue("over 32 days before" in named(rule(monthly, lastFetch = last("2026-10-11", "ok")), stamp()).single())
        assertEquals(8, MonthByMonth.freshDays("weekly"))
        assertEquals(8, MonthByMonth.freshDays("hijri-month"))
        assertEquals(32, MonthByMonth.freshDays("monthly"))
        // A fetch that ended ok and brought no day up to its own date fetched nothing current (every table "unchanged",
        // or a year's table whose year has ended): the captures must print through the day of the fetch at least.
        val stale = named(rule(printed = { days("2026-01-01", "2026-11-09") }), stamp())
        assertEquals(listOf(seen + "xx-auth's fetch of 2026-11-10 brought no current day: its captures print through 2026-11-09"), stale)
        assertIs<Cover.Covered>(verdict(rule(printed = { days("2026-01-01", "2026-11-10") }), stamp()))
        // An entry no source feeds is named for that, not for a fetch nobody was to make.
        assertEquals(1, named(rule(index = emptyList(), lastFetch = { null }), stamp()).size)
    }

    @Test
    fun `b - the newest dated fetch record that ran the source decides and a run it skipped is no run`() {
        fun log(date: String, vararg runs: Pair<String, String>) =
            FetchLog(LocalDate.parse(date), runs.map { (id, status) -> SourceRun(id, status, "", 0, emptyMap()) })
        val logs = listOf(
            log("2026-11-11", "xx-auth" to "skipped", "zz-other" to "failed"),
            log("2026-11-09", "xx-auth" to "ok", "zz-other" to "ok"),
            log("2026-11-02", "xx-auth" to "failed"),
        )
        assertEquals(MonthByMonth.FetchRun(LocalDate(2026, 11, 9), "ok"), MonthByMonth.lastRun(logs, "xx-auth"))
        assertEquals(MonthByMonth.FetchRun(LocalDate(2026, 11, 11), "failed"), MonthByMonth.lastRun(logs, "zz-other"))
        assertEquals(null, MonthByMonth.lastRun(logs, "yy-feed"))
        // Read from the dated files of a checkout's monitor/fetch, newest first; latest.json and logs are not records.
        val official = kotlin.io.path.createTempDirectory("fetch-records").toFile()
        try {
            val dir = official.resolve("monitor/fetch").apply { mkdirs() }
            fun record(date: String, status: String) =
                """{"date": "$date", "sources": {"xx-auth": {"status": "$status", "message": "", "requests": 1, "tables": {}}}}"""
            dir.resolve("2026-11-02.json").writeText(record("2026-11-02", "failed"))
            dir.resolve("2026-11-09.json").writeText(record("2026-11-09", "ok"))
            dir.resolve("latest.json").writeText(record("2026-11-20", "failed"))
            dir.resolve("2026-11-09.log").writeText("not a record")
            dir.resolve("2026-11-10.json").writeText("not json")
            assertEquals(listOf(LocalDate(2026, 11, 9), LocalDate(2026, 11, 2)), MonthByMonth.fetchLogs(official).map { it.date })
            assertEquals(MonthByMonth.FetchRun(LocalDate(2026, 11, 9), "ok"), MonthByMonth.lastRun(MonthByMonth.fetchLogs(official), "xx-auth"))
        } finally {
            official.deleteRecursively()
        }
        assertEquals(emptyList(), MonthByMonth.fetchLogs(null))
        assertEquals(emptyList(), MonthByMonth.fetchLogs(java.io.File("/nonexistent/official")))
    }

    @Test
    fun `c - an entry or unit the monitor demoted is no longer covered`() {
        val whole = named(rule(), stamp(demoted = listOf(StampDemotion(null, LocalDate(2027, 1, 12), "fajr early on 3 place-days"))))
        assertEquals(listOf("(c) the monitor demoted xx.auth since 2027-01-12 (fajr early on 3 place-days)"), whole)
        val unit = named(rule(), stamp(demoted = listOf(StampDemotion("east", LocalDate(2027, 1, 19), "maghrib early on 1 place-days"))))
        assertEquals(listOf("(c) the monitor demoted xx.auth/east since 2027-01-19 (maghrib early on 1 place-days)"), unit)
    }

    @Test
    fun `an entry checked by unit is covered only when every unit short of the month is fed`() {
        val year = runs("2026-01-01..2026-12-31")
        val byEvent = Ramadan.EVENTS.associateWith { year }
        val units = listOf(UnitSummary("east", "East", year, byEvent), UnitSummary("west", "West", year, byEvent), UnitSummary("south", "South", runs("2026-01-01..2027-12-31"), Ramadan.EVENTS.associateWith { runs("2026-01-01..2027-12-31") }))
        val stamp = StampSummary("yy.units", setOf("A"), LocalDate(2026, 1, 1), LocalDate(2027, 12, 31), 0, year, units, byEvent, heldOutRamadanDays = 30)
        val sources = sources("yy-feed\tyy.units\tfetch\thijri-month\t-\t\t")
        val recipes = recipes("yy-feed\t*\tyy-units.tsv\tindex\t-\tindex\tall\t-")
        // The index's point decides the unit, as the gate resolves a row there.
        val unitAt = { id: String, lat: Double, _: Double, _: String -> if (id == "yy.units") (if (lat < 5) "east" else "west") else null }
        val east = table("yy-feed", "east-1448-05", "yy.units", 1.0, 10.0)
        val west = table("yy-feed", "west-1448-05", "yy.units", 9.0, 10.0)
        // South covers the month and is no gap; east alone fed: west is named.
        val one = MonthByMonth(sources, recipes, listOf(east), manifest, { year2026 }, unitAt, fetchedOk, today).let { it.verdict(stamp, ramadan, stamp.ramadanGaps(ramadan)) }
        assertEquals(
            listOf("(b) no automatic source of sources.tsv holds a table of the authority's own site that the recipes turn into this entry's own rows at 1 of its units: west"),
            assertIs<Cover.Named>(one).reasons,
        )
        val both = MonthByMonth(sources, recipes, listOf(east, west), manifest, { year2026 }, unitAt, fetchedOk, today).let { it.verdict(stamp, ramadan, stamp.ramadanGaps(ramadan)) }
        val words = assertIs<Cover.Covered>(both).words
        assertTrue("2 units short of Ramadan 1448" in words && "yy-feed hijri-month" in words, words)
        // A recipe that names the unit itself needs no point; a table with neither is fed to no unit.
        val named = recipes("yy-feed\twest-*\tyy-units.tsv\tyy.units/west\t-\tunit\tall\t-", "yy-feed\teast-*\tyy-units.tsv\tindex\t-\tindex\tall\t-")
        val pointless = MonthByMonth(sources, named, listOf(east, table("yy-feed", "west-1448-05", "yy.units")), manifest, { year2026 }, unitAt, fetchedOk, today)
        assertIs<Cover.Covered>(pointless.verdict(stamp, ramadan, stamp.ramadanGaps(ramadan)))
        val lost = MonthByMonth(sources, recipes, listOf(east, table("yy-feed", "west-1448-05", "yy.units")), manifest, { year2026 }, unitAt, fetchedOk, today)
        assertIs<Cover.Named>(lost.verdict(stamp, ramadan, stamp.ramadanGaps(ramadan)))
        // One unit thin and another published: every reason is given at once.
        val thin = stamp.copy(units = listOf(units[0].copy(checkedByEvent = mapOf("fajr" to year, "maghrib" to year)), units[1]))
        val all = MonthByMonth(sources, recipes, listOf(east, west), manifest, { t -> if (t.key.startsWith("west")) year2026 + days("2027-02-07", "2027-02-28") else year2026 }, unitAt, fetchedOk, today)
            .verdict(thin, ramadan, thin.ramadanGaps(ramadan))
        val reasons = assertIs<Cover.Named>(all).reasons
        assertEquals(2, reasons.size, reasons.toString())
        assertTrue(reasons[0].startsWith("(a) not proven on a month's days") && "east (endOfEating on 0 days, no earlier Ramadan checked)" in reasons[0], reasons.toString())
        assertTrue("yy-feed/west-1448-05 already prints 22 of west's unchecked days (2027-02-07..2027-02-28)" in reasons[1], reasons.toString())
    }

    @Test
    fun `a source's entries are the ids before its bracketed note`() {
        fun of(entries: String) = MonthByMonth.entriesOf(Source("s", entries, "f", "weekly", null, "", ""))
        assertEquals(setOf("no.irn"), of("no.irn (and no.cautious as its member)"))
        assertEquals(setOf("tr.diyanet", "other.turkey"), of("tr.diyanet, other.turkey"))
        assertEquals(setOf("ca.ift", "ca.iit", "ca.mac"), of("ca.ift, ca.iit, ca.mac (and ca.toronto as their cautious entry)"))
    }

    @Test
    fun `every automatic source of the committed catalogue that names an entry has a recipe line, and every entry it names is in the registry`() {
        // What (b) reads: a source spelt wrongly, or fetched with no recipe, would silently feed nothing.
        val official = TestPaths.repoRoot.resolve("tools/timetables/official")
        val sources = Source.load(official.resolve("monitor/sources.tsv"))
        val recipes = Recipes.load(official.resolve("monitor/recipes.tsv"))
        val manifest = GateManifest.load(official.resolve("gate"))
        for (source in sources.filter { !it.manual }) {
            val own = recipes.filter { it.source == source.id }
            // The surveys (Mawaqit) name countries, no entry: they prove no timetable and have no recipe.
            val ids = MonthByMonth.entriesOf(source).filter { '.' in it }
            if (ids.isEmpty()) continue
            assertTrue(own.isNotEmpty(), "${source.id} is fetched and has no line in recipes.tsv")
            for (id in ids) assertTrue(Registry.byId(id) != null, "${source.id} names $id, which the registry does not hold")
            // Each named entry the gate holds rows of has a recipe line into a gate file that holds its own rows: a line
            // of its own (member `-`), or a clone of the rows that read the capture before (`same`, Diyanet's — which
            // the rule does not count as a feed, so such an entry stays named).
            for (id in ids.filter { i -> manifest.rows.any { it.entry == i && it.member == null } }) {
                assertTrue(
                    own.any { r -> (r.member == "-" || r.entry == "same") && manifest.rows.any { it.source == r.gate && it.entry == id && it.member == null } },
                    "${source.id}: no recipe line makes its captures $id's own rows",
                )
            }
        }
        // The rule's unit lookup resolves a point as the gate does: Kuala Lumpur lies in JAKIM's WLY01.
        assertEquals("WLY01", MonthByMonth.registryUnitAt("my.jakim", 3.1390, 101.6869, "Asia/Kuala_Lumpur"))
        assertEquals(null, MonthByMonth.registryUnitAt("no.such.entry", 0.0, 0.0, "UTC"))
    }
    @Test
    fun `b - a table that grows by the day each run reads samples the month and is no cover`() {
        // ESA's shape (the audit's finding F25): a weekly run that keeps the one day its page shows, every fetched day in the gate.
        val weeklyDays = (0 until 12).map { LocalDate.fromEpochDays(LocalDate(2026, 8, 24).toEpochDays() + 7 * it) }.toSet()
        assertEquals(LocalDate(2026, 11, 9), weeklyDays.max())
        val checked = runs("2025-01-01..2026-12-31")
        val reasons = named(rule(printed = { weeklyDays }, lastFetch = { MonthByMonth.FetchRun(LocalDate(2026, 11, 9), "ok") }), stamp(checked))
        assertTrue(
            reasons.single().startsWith("(b) the monitor's captures sample its days and do not hold them (fewer than 10 of the 30 days up to the last they print"),
            reasons.toString(),
        )
        assertTrue("xx-auth prints 5 of the 30 days up to 2026-11-09 at xx.auth" in reasons.single(), reasons.toString())
        // A table its authority prints every third day holds all there is (INM's far delegations): ten of the thirty.
        val thirds = (0 until 30).map { LocalDate.fromEpochDays(LocalDate(2026, 8, 14).toEpochDays() + 3 * it) }.toSet()
        assertEquals(LocalDate(2026, 11, 9), thirds.max())
        assertIs<Cover.Covered>(verdict(rule(printed = { thirds }, lastFetch = { MonthByMonth.FetchRun(LocalDate(2026, 11, 9), "ok") }), stamp(checked)))
        // And one that holds every day of a week a run, the week's run of days.
        assertIs<Cover.Covered>(verdict(rule(printed = { days("2026-10-01", "2026-11-09") }, lastFetch = { MonthByMonth.FetchRun(LocalDate(2026, 11, 9), "ok") }), stamp(checked)))
        // A capture that prints nine days and no more is named, ten is not: the count, not the run's shape.
        assertIs<Cover.Named>(verdict(rule(printed = { days("2026-11-02", "2026-11-10") }), stamp(checked)))
        assertIs<Cover.Covered>(verdict(rule(printed = { days("2026-11-01", "2026-11-10") }), stamp(checked)))
    }

}
