package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.method.EventOffsets
import world.taqwa.app.prayer.engine.method.TimetableMethod
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.LateLimit
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.Units
import world.taqwa.timetables.Json
import world.taqwa.timetables.TestPaths
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `a start moved two minutes earlier is early and a sunrise moved later is a late end`() {
        val result = Gate(roots, change = { m -> m.copy(margins = m.margins.copy(fajr = m.margins.fajr - 120, sunrise = m.margins.sunrise + 120)) })
            .evaluate(muis)
        val s = result.entries.getValue("sg.muis")
        assertEquals(10, s.event(Event.FAJR).early)
        assertEquals(10, s.event(Event.SUNRISE).lateEnd)
        assertTrue(result.violations().any { "sg.muis fajr: 10 early" in it }, result.report())
        assertTrue(result.violations().any { "sg.muis sunrise: 10 late ends" in it }, result.report())
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
    fun `a unit's late limit comes before its entry's for its own events`() {
        val entry = Registry.byId("ru.dumrt")!!
        val kazan = Units.of("ru.dumrt")!!.units.first { it.id == "kazan" }
        assertEquals(Limit(2, "class B", null), Gate.lateLimit(Event.DHUHR, kazan, entry, EntryClass.B))
        val excepted = entry.copy(lateLimits = listOf(LateLimit(3, "the entry's", setOf(TimedEvent.DHUHR, TimedEvent.FAJR))))
        val own = kazan.copy(lateLimits = listOf(LateLimit(14, "the printed 12:00 Zuhr", setOf(TimedEvent.DHUHR))))
        assertEquals(Limit(14, "unit kazan", "the printed 12:00 Zuhr"), Gate.lateLimit(Event.DHUHR, own, excepted, EntryClass.B))
        assertEquals(Limit(3, "entry ru.dumrt", "the entry's"), Gate.lateLimit(Event.FAJR, own, excepted, EntryClass.B))
        assertEquals(Limit(2, "class B", null), Gate.lateLimit(Event.SUNRISE, own, excepted, EntryClass.B))
        assertEquals(Limit(3, "entry ru.dumrt", "the entry's"), Gate.lateLimit(Event.DHUHR, null, excepted, EntryClass.D_AUTHORITY))
        assertEquals(Limit(3, "class D (authority)", null), Gate.lateLimit(Event.ISHA, null, excepted, EntryClass.D_AUTHORITY))
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
            "sunrise" to listOf(9, 1, 0, 0),
            "dhuhr" to listOf(7, 3, 0, 0),
            "asrStandard" to listOf(0, 7, 3, 0),
            "maghrib" to listOf(5, 4, 1, 0),
            "isha" to listOf(6, 4, 0, 0),
            "endOfEating" to listOf(0, 10, 0, 0),
        )
    }
}
