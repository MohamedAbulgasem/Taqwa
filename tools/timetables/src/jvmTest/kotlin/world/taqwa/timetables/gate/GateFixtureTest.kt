package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.AboutTemplate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.lateLimitFor
import world.taqwa.app.prayer.engine.registry.ownLateLimitFor
import world.taqwa.app.prayer.engine.registry.reachLateLimit
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.Json
import world.taqwa.timetables.TestPaths
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * The gate end to end on ten days of MUIS's open table (`src/jvmTest/resources/gate-fixture`);
 * cautious entries are in [CautiousGateTest].
 */
class GateFixtureTest {

    private val root: File = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())

    private val roots = OfficialRoots.of(root)

    private val header = GateManifest.HEADER.joinToString("\t")

    private val muis = GateManifest.parse(
        "fixture.tsv",
        listOf(
            header,
            "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\tbefore Ramadan",
            "open/SG-MUIS/muis-2026-02-b.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\tRamadan",
        ).joinToString("\n"),
    )

    private fun manifest(vararg rows: String) = GateManifest.parse("t.tsv", (listOf(header) + rows).joinToString("\n"))

    @Test
    fun `ten open muis days go through the gate`() {
        val result = Gate(roots, PINNED).evaluate(muis)
        assertEquals(2, result.checkedRows)
        assertTrue(result.skippedRows.isEmpty())
        val s = result.entries.getValue("sg.muis")
        assertEquals(10, s.placeDayCount)
        assertEquals(5, s.testDays)
        assertEquals(1, s.places.size)
        assertEquals(LocalDate(2026, 2, 14), s.first)
        assertEquals(LocalDate(2026, 2, 23), s.last)
        // Days actually in Ramadan: Umm al-Qura began Ramadan 1447 on 18 February.
        assertEquals(6, s.ramadanDays)
        assertEquals(5, s.testRamadanDays)
        // The held-out tally is the test file's five days alone.
        assertEquals(setOf(5), s.heldOut.values.map { it.checked }.toSet())
        assertEquals(
            listOf(Event.FAJR, Event.SUNRISE, Event.DHUHR, Event.ASR_STANDARD, Event.MAGHRIB, Event.ISHA, Event.END_OF_EATING),
            s.events.keys.toList(),
        )
        for ((event, e) in s.events) {
            assertEquals(10, e.checked, event.key)
            assertEquals(0, e.early, event.key)
            assertEquals(0, e.lateEnd, event.key)
            assertEquals(10, e.late.sum(), event.key)
        }
        assertEquals(EXPECTED_LATE, s.events.mapKeys { it.key.key }.mapValues { it.value.late.toList() })
        assertEquals(0, s.outOfOrder)

        val report = result.report()
        assertTrue(": 2 rows checked (10 place-days)\n" in report, report)
        assertTrue("sg.muis — class " in report, report)
    }

    @Test
    fun `a row reading ends alone adds no place-day and must fall on a day a row reading a start holds`() {
        // Held out and read first, the ends-only row neither makes the fit days held out nor counts a day of its own.
        val result = Gate(roots, PINNED).evaluate(
            manifest(
                "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tE - - - - -\tdaily\tstandard\ttest\t",
                "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\tfit\t",
            ),
        )
        val s = result.entries.getValue("sg.muis")
        assertEquals(5, s.placeDayCount)
        assertEquals(0, s.testDays)
        assertEquals(5, s.event(Event.END_OF_EATING).checked)
        assertEquals(5, s.heldOut.getValue(Event.END_OF_EATING).checked)

        // Alone, its days are no place-days of the entry's: a mistake in the gate file.
        val error = assertFailsWith<GateError> {
            Gate(roots, PINNED).evaluate(
                manifest(
                    "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\tfit\t",
                    "open/SG-MUIS/muis-2026-02-b.txt\tsg.muis\t\t\tAsia/Singapore\tE - - - - -\tdaily\tstandard\ttest\t",
                ),
            )
        }
        assertTrue("t.tsv:3: reads ends alone on " in error.message!!, error.message)
        assertTrue("which no row reading a start holds at its point" in error.message!!, error.message)
        assertTrue("t.tsv:2" !in error.message!!, error.message)
    }

    @Test
    fun `a start moved two minutes earlier is early and an end of eating moved later is a late end`() {
        val result = Gate(roots, change = { m -> m.copy(margins = m.margins.copy(fajr = m.margins.fajr - 120, sunrise = m.margins.sunrise + 120)) })
            .evaluate(muis)
        val s = result.entries.getValue("sg.muis")
        assertEquals(10, s.event(Event.FAJR).early)
        assertTrue(result.violations().any { "sg.muis fajr: 10 early" in it }, result.report())
        // A sunrise moved later no longer reaches the gate as a late end: the real sun at the place caps it (ruling R265).
        assertEquals(0, s.event(Event.SUNRISE).lateEnd)
        // The end of eating, which no sun caps, two minutes later (Fajr four, so that it may follow): ten late ends.
        val late = Gate(roots, change = { m ->
            m.copy(margins = m.margins.copy(fajr = m.margins.fajr + 240), endOfEatingMarginSeconds = m.endOfEatingMarginSeconds + 120)
        }).evaluate(muis)
        assertEquals(10, late.entries.getValue("sg.muis").event(Event.END_OF_EATING).lateEnd, late.report())
        assertTrue(late.violations().any { "sg.muis endOfEating: 10 late ends" in it }, late.report())
    }

    @Test
    fun `an entry's recorded late limit lifts the class limit for its events and its reason reaches the stamp`() {
        val strict = Gate(roots, lookup = BASELINE).evaluate(muis).entries.getValue("sg.muis")
        val worst = strict.events.values.maxOf { it.worst }
        val excepted = BASELINE_MUIS.copy(
            lateLimits = listOf(LateLimit(worst, "the fixture's own reason", TimedEvent.entries.toSet())),
        )
        val result = Gate(roots, lookup = { if (it == "sg.muis") excepted else Registry.byId(it) }).evaluate(muis)
        assertTrue(result.violations().isEmpty(), result.report())
        assertTrue(
            "sg.muis — class A, late limit by exception: fajr/sunrise/dhuhr/asrStandard/maghrib/isha/endOfEating $worst from entry sg.muis" in
                result.report(),
            result.report(),
        )
        val stamp = Stamps.stamp(result.entries.getValue("sg.muis"), "core")
        @Suppress("UNCHECKED_CAST")
        val limits = stamp["lateLimits"] as Map<String, Map<String, Any>>
        assertEquals("the fixture's own reason", limits.getValue("entry sg.muis: $worst min")["reason"])
    }

    /** Ruling R41: an exception for Dhuhr leaves every other event at the class limit. */
    @Test
    fun `a dhuhr-only exception leaves fajr at the class limit`() {
        val strict = Gate(roots, PINNED, lookup = BASELINE).evaluate(muis).entries.getValue("sg.muis")
        val excepted = BASELINE_MUIS.copy(
            lateLimits = listOf(LateLimit(3, "a Dhuhr-only reason", setOf(TimedEvent.DHUHR))),
        )
        val result = Gate(roots, PINNED, lookup = { if (it == "sg.muis") excepted else Registry.byId(it) }).evaluate(muis)
        val s = result.entries.getValue("sg.muis")
        assertEquals(setOf(3), s.event(Event.DHUHR).limits)
        assertEquals(setOf(1), s.event(Event.FAJR).limits)
        assertEquals(setOf(1), s.event(Event.ASR_STANDARD).limits)
        assertEquals(0, s.event(Event.DHUHR).over)
        // Asr keeps its three 2-minute days over the class limit: the exception is not its.
        assertEquals(strict.event(Event.ASR_STANDARD).over, s.event(Event.ASR_STANDARD).over)
        assertEquals(3, s.event(Event.ASR_STANDARD).over)
        assertTrue("sg.muis — class A, late limit 1 (by exception: dhuhr 3 from entry sg.muis)" in result.report(), result.report())
        @Suppress("UNCHECKED_CAST")
        val limits = Stamps.stamp(s, "core")["lateLimits"] as Map<String, Map<String, Any>>
        assertEquals(listOf("dhuhr"), limits.getValue("entry sg.muis: 3 min")["events"])
        assertEquals(listOf("fajr", "sunrise", "asrStandard", "maghrib", "isha", "endOfEating"), limits.getValue("class A: 1 min")["events"])
    }

    @Test
    fun `an entry with no units reads its area's reach limits under its own id, and a variant of it does not`() {
        // Ruling R298, as a rule over whatever ReachLimits.kt holds: for every event the registry's MUIS takes the larger of
        // its own limit and its area's measured worst keyed by its id; a copy built under the same id (a fixture's) takes
        // its own limits alone.
        val muis = requireNotNull(Registry.byId("sg.muis"))
        assertNull(Units.of("sg.muis"))
        for (event in TimedEvent.entries) {
            val own = ownLateLimitFor(event, null, muis)
            val reach = reachLateLimit("sg.muis", "sg.muis", event)
            val expected = if (reach == null || (own != null && own.minutes >= reach.minutes)) own else reach
            assertEquals(expected, lateLimitFor(event, null, muis), "$event")
            val variant = muis.copy(entryClass = EntryClass.A, about = AboutTemplate.AUTHORITY_CHECKED)
            assertEquals(ownLateLimitFor(event, null, variant), lateLimitFor(event, null, variant), "$event")
        }
    }

    @Test
    fun `a unit's late limit comes before its entry's for its own events`() {
        // Ben Guerdane: no reach limit of its own (ReachLimits.kt), so the unit's and the entry's alone decide.
        val entry = Registry.byId("tn.inm")!!
        val unit = Units.of("tn.inm")!!.units.first { it.id == "ben-guerdane" }
        assertEquals(Limit(2, "class B", null), Gate.lateLimit(Event.DHUHR, unit, entry, EntryClass.B))
        val excepted = entry.copy(lateLimits = listOf(LateLimit(3, "the entry's", setOf(TimedEvent.DHUHR, TimedEvent.FAJR))))
        val own = unit.copy(lateLimits = listOf(LateLimit(14, "the printed 12:00 Zuhr", setOf(TimedEvent.DHUHR))))
        assertEquals(Limit(14, "unit ben-guerdane", "the printed 12:00 Zuhr"), Gate.lateLimit(Event.DHUHR, own, excepted, EntryClass.B))
        assertEquals(Limit(3, "entry tn.inm", "the entry's"), Gate.lateLimit(Event.FAJR, own, excepted, EntryClass.B))
        assertEquals(Limit(2, "class B", null), Gate.lateLimit(Event.SUNRISE, own, excepted, EntryClass.B))
        assertEquals(Limit(3, "entry tn.inm", "the entry's"), Gate.lateLimit(Event.DHUHR, null, excepted, EntryClass.D_AUTHORITY))
        assertEquals(Limit(3, "class D (authority)", null), Gate.lateLimit(Event.ISHA, null, excepted, EntryClass.D_AUTHORITY))
        // Ruling R270: where a unit's reach was measured later than its own limit, the measured figure is the unit's limit,
        // and a recorded limit of the unit's that is larger still stands (Yelabuga's Fajr was 126 before its reach read 132).
        val dumRt = Registry.byId("ru.dumrt")!!
        val kazan = Units.of("ru.dumrt")!!.units.first { it.id == "kazan" }
        val reach = assertNotNull(reachLateLimit("ru.dumrt", "kazan", TimedEvent.FAJR))
        assertEquals(Limit(reach.minutes, "unit kazan", reach.reason), Gate.lateLimit(Event.FAJR, kazan, dumRt, EntryClass.B))
        val wider = kazan.copy(lateLimits = listOf(LateLimit(reach.minutes + 1, "a recorded one", setOf(TimedEvent.FAJR))))
        assertEquals(Limit(reach.minutes + 1, "unit kazan", "a recorded one"), Gate.lateLimit(Event.FAJR, wider, dumRt, EntryClass.B))
    }

    @Test
    fun `a unit's rows are reported on their own`() {
        val result = Gate(OfficialRoots.of(TestPaths.repoRoot.resolve("tools/timetables/official"))).evaluate(
            manifest("open/RU-DUMRT/off_kzn_mosque.txt\tru.dumrt/kazan\t\t\tEurope/Moscow\t- - D - - -\tdaily\t-\ttest\t"),
        )
        val s = result.entries.getValue("ru.dumrt")
        val kazan = s.units.getValue("kazan")
        assertEquals(365, kazan.placeDays.size)
        assertEquals(setOf(2), kazan.events.getValue(Event.DHUHR).limits)
        assertEquals(365, kazan.events.getValue(Event.DHUHR).checked)
        @Suppress("UNCHECKED_CAST")
        val units = Stamps.stamp(s, "core")["units"] as Map<String, Map<String, Any>>
        val stamped = units.getValue("kazan")
        assertEquals(listOf("Kazan", 365), listOf("name", "placeDays").map { stamped[it] })
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("dhuhr"), (stamped["lateLimits"] as Map<String, Map<String, Any>>).getValue("class B: 2 min")["events"])
        // Ruling R115: the unit's checked dates, as runs, so a page can hold every day no table checked.
        assertEquals(listOf("2026-01-01..2026-12-31"), stamped["checked"])
        // Ruling R148: and by event — the one column this row reads, and no other.
        assertEquals(mapOf("dhuhr" to listOf("2026-01-01..2026-12-31")), stamped["checkedByEvent"])
    }

    @Test
    fun `the stamp records the entry's checked dates as runs`() {
        val s = Gate(roots, PINNED).evaluate(muis).entries.getValue("sg.muis")
        assertEquals(listOf("2026-02-14..2026-02-23"), Stamps.stamp(s, "core")["checked"])
        // A day missing from the tables leaves a hole between two runs, never one span over it.
        val holed = manifest(
            "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t",
        )
        assertEquals(listOf("2026-02-14..2026-02-18"), Stamps.stamp(Gate(roots, PINNED).evaluate(holed).entries.getValue("sg.muis"), "core")["checked"])
    }

    @Test
    fun `the stamp records each event's checked dates as runs and an unread event has none`() {
        // Ruling R148: a Ramadan is proven event by event, so the stamp says which days each event was compared on.
        val all = Stamps.stamp(Gate(roots, PINNED).evaluate(muis).entries.getValue("sg.muis"), "core")["checkedByEvent"]
        assertEquals(
            listOf("fajr", "sunrise", "dhuhr", "asrStandard", "maghrib", "isha", "endOfEating").associateWith { listOf("2026-02-14..2026-02-23") },
            all,
        )
        // The second file read without its end of eating: that event's runs stop with the first file (Pretoria's shape).
        val partial = manifest(
            "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t",
            "open/SG-MUIS/muis-2026-02-b.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t",
        )
        val stamp = Stamps.stamp(Gate(roots, PINNED).evaluate(partial).entries.getValue("sg.muis"), "core")
        @Suppress("UNCHECKED_CAST")
        val byEvent = stamp["checkedByEvent"] as Map<String, List<String>>
        assertEquals(listOf("2026-02-14..2026-02-23"), byEvent["fajr"])
        assertEquals(listOf("2026-02-14..2026-02-18"), byEvent["endOfEating"])
        assertEquals(listOf("2026-02-14..2026-02-23"), stamp["checked"])
        // Read back: tabular Ramadan 1447 begins on 18 February and its guard on the 17th, so the ten days hold seven guarded
        // days for Fajr and Maghrib and, with the second file's end of eating unread, two for the end of eating; the summary
        // counts each event on its own.
        val summary = StampSummary.of(stamp, "sg.muis")
        val gap = summary.ramadanGaps(Ramadan.of(1447)).single()
        assertEquals(listOf(7, 2, 7), Ramadan.EVENTS.map { Ramadan.GUARD_DAYS - gap.uncheckedByEvent.getValue(it).size })
        assertEquals("2 of 32 days (fajr 7, endOfEating 2, maghrib 7)", gap.describe())
    }

    @Test
    fun `a table whose times were rounded up is never read with an end on a start's column`() {
        // Ruling R149 (E16): Dawat-e-Islami's months say their starts were rounded up from the source's seconds; as the end
        // of eating's reference the printed Fajr could then stand up to 59 s after the authority's moment.
        val archive = Files.createTempDirectory("gate-rounded").toFile()
        try {
            val lines = root.resolve("open/SG-MUIS/muis-2026-02-a.txt").readLines()
            archive.resolve("archive").mkdirs()
            archive.resolve("archive/rounded.txt").writeText(
                (listOf("# Source seconds rounded up to the minute (starts, never early); sunrise rounded down (an end).") + lines).joinToString("\n"),
            )
            val gate = Gate(OfficialRoots(archive, root), PINNED)
            val error = assertFailsWith<GateError> {
                gate.evaluate(manifest("archive/rounded.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\t"))
            }
            assertTrue("t.tsv:2: archive/rounded.txt says its times were rounded up to the minute" in error.message!!, error.message)
            assertTrue("(fajr+endOfEating)" in error.message!! && "ruling R149" in error.message!!, error.message)
            // Its starts alone, or an end from a column of its own, are read.
            assertEquals(1, gate.evaluate(manifest("archive/rounded.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t")).checkedRows)
            assertEquals(1, gate.evaluate(manifest("archive/rounded.txt\tsg.muis\t\t\tAsia/Singapore\tF E D A M I\tdaily\tstandard\ttest\t")).checkedRows)
            // The words have to be the table's own, in a comment line: a line of times never carries them.
            assertTrue(!Gate.roundedUp(lines) && Gate.roundedUp(listOf("# ... Rounded UP ...")) && !Gate.roundedUp(listOf("rounded up")))
            // Other ways to say it are caught too; a minute's mere mention, or "rounded down", is not.
            for (said in listOf("# seconds ceil'd to the minute", "# each start rounds it up", "# times taken to the next whole minute", "# rounding up the seconds")) {
                assertTrue(Gate.roundedUp(listOf(said)), said)
            }
            for (said in listOf("# sunrise rounded down", "# printed to the minute", "# the next minute's table", "# around Uppsala")) {
                assertTrue(!Gate.roundedUp(listOf(said)), said)
            }
        } finally {
            archive.deleteRecursively()
        }
    }

    @Test
    fun `no archive means no rows checked even where the checkout holds the open tables`() {
        val result = Gate(OfficialRoots(File("/nonexistent/official"), root)).evaluate(muis)
        assertEquals(0, result.checkedRows)
        assertEquals(2, result.skippedRows.size)
        assertTrue(result.violations().isEmpty())
        assertTrue(result.report().contains(": 0 rows checked (0 place-days), 2 rows skipped (no archive there)"), result.report())
    }

    @Test
    fun `open tables come from the checkout and the rest from the archive`() {
        val archive = Files.createTempDirectory("gate-archive").toFile()
        try {
            archive.resolve("archive/x").mkdirs()
            root.resolve("open/SG-MUIS/muis-2026-02-a.txt").copyTo(archive.resolve("archive/x/muis.txt"))
            val result = Gate(OfficialRoots(archive, root)).evaluate(
                manifest(
                    "open/SG-MUIS/muis-2026-02-b.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t",
                    "archive/x/muis.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\tfit\t",
                ),
            )
            assertEquals(2, result.checkedRows)
            assertEquals(10, result.entries.getValue("sg.muis").placeDayCount)
        } finally {
            archive.deleteRecursively()
        }
    }

    @Test
    fun `a table printed in another clock is read in that clock`() {
        val archive = Files.createTempDirectory("gate-clock").toFile()
        try {
            // The same five MUIS days printed in UTC on each row's own date (Singapore is UTC+8 all
            // year): Fajr and sunrise fall on the clock's day before, 21:58 and 23:17.
            val utc = root.resolve("open/SG-MUIS/muis-2026-02-a.txt").readLines().filter { !it.startsWith("#") }.map { line ->
                val parts = line.split(' ')
                parts[0] + parts.drop(1).joinToString("") { t ->
                    val (h, m) = t.split(':').map(String::toInt)
                    val total = ((h - 8) * 60 + m + 24 * 60) % (24 * 60)
                    " %02d:%02d".format(total / 60, total % 60)
                }
            }
            archive.resolve("archive").mkdirs()
            archive.resolve("archive/utc.txt").writeText(utc.joinToString("\n"))
            val clockHeader = (GateManifest.HEADER + "clock").joinToString("\t")
            val local = Gate(roots, PINNED).evaluate(
                manifest("open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t"),
            ).entries.getValue("sg.muis")
            val inUtc = Gate(OfficialRoots(archive, root), PINNED).evaluate(
                GateManifest.parse(
                    "u.tsv",
                    "$clockHeader\narchive/utc.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t\tUTC",
                ),
            ).entries.getValue("sg.muis")
            assertEquals(local.events.mapValues { it.value.late.toList() }, inUtc.events.mapValues { it.value.late.toList() })
            assertTrue(inUtc.events.values.all { it.early == 0 && it.lateEnd == 0 })
        } finally {
            archive.deleteRecursively()
        }
    }

    @Test
    fun `a table the archive does not hold is a mistake`() {
        val error = assertFailsWith<GateError> {
            Gate(roots).evaluate(manifest("open/SG-MUIS/not-held.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t"))
        }
        assertTrue("t.tsv:2: open/SG-MUIS/not-held.txt is not held" in error.message!!, error.message)
    }

    @Test
    fun `a row that reads no day is a mistake`() {
        // Seven columns for a six-column table: every line is unreadable.
        val error = assertFailsWith<GateError> {
            Gate(roots).evaluate(manifest("open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I -\tdaily\tstandard\ttest\t"))
        }
        assertTrue("t.tsv:2: no day read from open/SG-MUIS/muis-2026-02-a.txt (5 unreadable lines" in error.message!!, error.message)
    }

    @Test
    fun `both lines of a repeated date are checked`() {
        val archive = Files.createTempDirectory("gate-repeat").toFile()
        try {
            val lines = root.resolve("open/SG-MUIS/muis-2026-02-a.txt").readLines()
            // The 14 February line again, with a Fajr two minutes later than the engine's.
            archive.resolve("archive").mkdirs()
            archive.resolve("archive/repeat.txt").writeText((lines + "2026-02-14 06:01 07:17 13:20 16:38 19:22 20:32").joinToString("\n"))
            val result = Gate(OfficialRoots(archive, root), PINNED).evaluate(
                manifest("archive/repeat.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t"),
            )
            val s = result.entries.getValue("sg.muis")
            assertEquals(5, s.placeDayCount)
            assertEquals(1, s.duplicates)
            assertEquals(6, s.event(Event.FAJR).checked)
            assertEquals(1, s.event(Event.FAJR).early)
            assertEquals(1, Stamps.stamp(s, "core")["repeatedLines"])
        } finally {
            archive.deleteRecursively()
        }
    }

    @Test
    fun `a stamp holds statistics and never an official time`() {
        val s = Gate(roots).evaluate(muis).entries.getValue("sg.muis")
        val core = Stamps.coreHash(TestPaths.repoRoot)
        val json = Json.pretty(Stamps.stamp(s, core))
        assertEquals(json, Json.pretty(Stamps.stamp(s, core)), "the same inputs, the same bytes")
        assertTrue("\"placeDays\": 10" in json, json)
        assertTrue("\"ramadanDays\": 6" in json, json)
        @Suppress("UNCHECKED_CAST")
        val heldOut = Stamps.stamp(s, core)["heldOut"] as Map<String, Any>
        assertEquals(listOf(5, 5), listOf(heldOut["placeDays"], heldOut["ramadanDays"]))
        assertTrue("\"unreadableLines\": 0" in json && "\"repeatedLines\": 0" in json, json)
        val times = Regex("""\b\d{1,2}:\d{2}\b""")
        assertTrue(times.findAll(json).none(), "a clock time in the stamp: $json")
        assertEquals(16, (Stamps.stamp(s, core)["engineHash"] as String).length)
    }

    private companion object {
        /**
         * MUIS as a class A entry with no recorded exceptions: the baseline the exception tests
         * lift, independent of the class and exceptions Task 7 gives the real entry (Ruling R57).
         */
        val BASELINE_MUIS = Registry.byId("sg.muis")!!.copy(entryClass = EntryClass.A, lateLimits = emptyList())
        val BASELINE: (String) -> RegistryEntry? = { if (it == "sg.muis") BASELINE_MUIS else Registry.byId(it) }

        /** MUIS's margins as Task 5 fitted them, so that a refit (Task 7) leaves these counts alone. */
        val PINNED: (TimetableMethod) -> TimetableMethod = { m ->
            m.copy(margins = EventOffsets(fajr = 21, sunrise = 58, dhuhr = 79, asr = 31, maghrib = 18, isha = 20), endOfEatingMarginSeconds = 0)
        }

        /**
         * Minutes late (0, 1, 2, 3+; for sunrise and the end of eating, minutes early) per event over
         * the ten fixture days, with [PINNED]'s margins.
         */
        val EXPECTED_LATE = mapOf(
            "fajr" to listOf(0, 10, 0, 0),
            // The real sun's cap (ruling R265): MUIS prints its sunrise on or after the minute the sun rose in, so the
            // sunrise shown is that minute, a minute before the print on these ten days.
            "sunrise" to listOf(0, 10, 0, 0),
            "dhuhr" to listOf(7, 3, 0, 0),
            "asrStandard" to listOf(0, 7, 3, 0),
            "maghrib" to listOf(5, 4, 1, 0),
            "isha" to listOf(6, 4, 0, 0),
            "endOfEating" to listOf(0, 10, 0, 0),
        )
    }
}
