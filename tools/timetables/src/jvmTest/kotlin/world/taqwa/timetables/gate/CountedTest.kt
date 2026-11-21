package world.taqwa.timetables.gate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ruling R214, the counting rule ([Counted]) on invented gate files and on [GateFixtureTest]'s ten
 * open MUIS days: a published table's days are counted once, a replay of it is a check alone, and
 * a cell the margins saw is no held-out cell. Rules only: nothing here reads the committed stamps.
 */
class CountedTest {

    private val root: File = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
    private val roots = OfficialRoots.of(root)
    private val header = GateManifest.HEADER.joinToString("\t")

    private val a = "open/SG-MUIS/muis-2026-02-a.txt"
    private val b = "open/SG-MUIS/muis-2026-02-b.txt"

    private fun row(path: String, split: String, columns: String = "F+E S D A M I", point: String = "\t") =
        "$path\tsg.muis\t$point\tAsia/Singapore\t$columns\tdaily\tstandard\t$split\t"

    private fun file(source: String, vararg lines: String) = GateManifest.parse(source, (listOf(header) + lines).joinToString("\n"))

    private fun join(vararg files: GateManifest) =
        GateManifest(files.flatMap { it.rows }, files.flatMap { it.sources }, fitted = files.flatMap { it.fitted })

    /** A point of Singapore that is not the entry's own. */
    private val elsewhere = "1.40\t103.80"

    private val main = file("sg-muis.tsv", row(a, "fit"), row(b, "test"))

    /** F17: the reach rows replay both tables at another point, and are marked test as the generator writes them. */
    private val reach = file("sg-muis-reach.tsv", row(a, "test", point = elsewhere), row(b, "test", point = elsewhere))

    @Test
    fun `a reach row replaying a table already counted adds no place, no place-day and no held-out day`() {
        val alone = Gate(roots).evaluate(main).entries.getValue("sg.muis")
        for (manifest in listOf(join(main, reach), join(reach, main))) {
            val result = Gate(roots).evaluate(manifest)
            val s = result.entries.getValue("sg.muis")
            // Checked: every row, twenty place-days at two points, and an early start at either would break the gate.
            assertEquals(4, result.checkedRows)
            assertEquals(20, s.placeDayCount)
            assertEquals(2, s.places.size)
            assertEquals(20, s.event(Event.FAJR).checked)
            // Shown: the two tables' own ten days at their one point, five of them held out, as without the reach rows.
            assertEquals(10, s.tableDayCount)
            assertEquals(1, s.tablePlaces.size)
            assertEquals(10, s.replayDayCount)
            assertEquals(alone.testDays, s.testDays)
            assertEquals(5, s.testDays)
            assertEquals(alone.ramadanDays, s.ramadanDays)
            assertEquals(alone.testRamadanDays, s.testRamadanDays)
            assertEquals(setOf(5), s.heldOut.values.map { it.checked }.toSet())
            val stamp = Stamps.stamp(s, "core")
            assertEquals(listOf(1, 10, 5), listOf("places", "placeDays", "heldOutDays").map { stamp[it] })
            assertEquals(mapOf("places" to 1, "placeDays" to 10), stamp["replays"])
            @Suppress("UNCHECKED_CAST")
            val heldOut = stamp["heldOut"] as Map<String, Any?>
            assertEquals(5, heldOut["placeDays"])
            // The fitted table's replay is in-sample whatever its split column says: only the held-out table's own days.
            assertEquals(setOf(listOf("2026-02-19..2026-02-23")), (heldOut["checkedByEvent"] as Map<*, *>).values.toSet())
            val report = result.report()
            assertTrue(": 4 rows checked (20 place-days, 10 of them the published tables' own and 10 the same tables replayed at other points)\n" in report, report)
            assertTrue("· 1 place · 2026-02-14..2026-02-23 · 10 place-days (5 held out, 6 in Ramadan) and 10 more replayed at 1 other points · 4 rows\n" in report, report)
        }
        // Without a replay the stamp has no such key and the report's first line is as it always was.
        val plain = Gate(roots).evaluate(main)
        assertEquals(null, Stamps.stamp(plain.entries.getValue("sg.muis"), "core")["replays"])
        assertTrue(": 2 rows checked (10 place-days)\n" in plain.report(), plain.report())
    }

    @Test
    fun `a table only a reach file reads is counted there, once`() {
        val manifest = join(file("sg-muis.tsv", row(a, "fit")), file("sg-muis-reach.tsv", row(b, "test", point = elsewhere), row(b, "test")))
        val s = Gate(roots).evaluate(manifest).entries.getValue("sg.muis")
        assertEquals(15, s.placeDayCount)
        assertEquals(10, s.tableDayCount)
        assertEquals(5, s.testDays)
        assertEquals(2, s.tablePlaces.size)
        assertEquals(5, s.replayDayCount)
    }

    @Test
    fun `the same table read by two rows of one file is counted at the first and a replay at the second`() {
        // One national table gated at two towns: one published table, one place, its days once.
        val manifest = file("xx.tsv", row(b, "test"), row(b, "test", point = elsewhere))
        val counted = Counted(manifest)
        assertEquals(1, counted.tables)
        assertEquals(listOf(true, false), manifest.rows.map(counted::isOwn))
        val s = Gate(roots).evaluate(manifest).entries.getValue("sg.muis")
        assertEquals(listOf(5, 5, 1, 5), listOf(s.tableDayCount, s.testDays, s.tablePlaces.size, s.replayDayCount))
        assertEquals(setOf(5), s.heldOut.values.map { it.checked }.toSet())
    }

    @Test
    fun `a cell is held out only where no fit row and no fitted line reads its event from the same table`() {
        val rows = file(
            "xx.tsv",
            row(a, "fit", "F - - - - -"),
            row(a, "test", "F+E S D A M I"),
            row(b, "test", "F S D A M I"),
            row(b, "fit", "E - - - - -"),
            row(b, "test", "Im - - - - -"),
        )
        val counted = Counted(rows)
        val (aFit, aTest, bTest, bEnd, bImsak) = rows.rows
        // Table a: its Fajr was fitted, so the test row's Fajr is in-sample and its other columns are held out;
        // and its place-days are not held out, since a start of the row is a fit cell.
        assertTrue(!counted.heldOut(aTest, Event.FAJR) && counted.heldOut(aTest, Event.MAGHRIB) && counted.heldOut(aTest, Event.END_OF_EATING))
        assertTrue(!counted.heldOut(aTest) && !counted.heldOut(aFit))
        assertTrue(counted.isOwn(aFit) && !counted.isOwn(aTest))
        assertTrue(counted.counts(aFit, Event.FAJR) && !counted.counts(aTest, Event.FAJR) && counted.counts(aTest, Event.MAGHRIB))
        // Table b: an end fitted on it (JAKIM's imsak, finding F20) leaves its starts held out, and its days with them.
        assertTrue(counted.heldOut(bTest) && counted.heldOut(bTest, Event.FAJR))
        assertTrue(!counted.heldOut(bEnd, Event.END_OF_EATING) && counted.heldOut(bImsak, Event.IMSAK))
        assertTrue(counted.isOwn(bTest) && !counted.isOwn(bEnd) && !counted.isOwn(bImsak))
        assertEquals(2, counted.tables)
    }

    /** The sweep of 13 Nov 2026 (F20): a margin widened to a held-out table's excess saw that table's cells of the event. */
    @Test
    fun `a fitted line makes the events it names fit cells and the row's days not held out only when it names a start`() {
        val end = file("sg-muis.tsv", row(a, "fit"), row(b, "test"), "@fitted\tsg.muis\t$b\tE\tthe end of eating's margin was read off this table")
        val s = Gate(roots).evaluate(end).entries.getValue("sg.muis")
        assertEquals(listOf(10, 5, 5), listOf(s.tableDayCount, s.testDays, s.testRamadanDays))
        assertEquals(null, s.heldOut[Event.END_OF_EATING])
        assertEquals(5, s.heldOut.getValue(Event.FAJR).checked)
        // Still checked: the line changes what is called held out, never what is compared.
        assertEquals(10, s.event(Event.END_OF_EATING).checked)

        val start = file("sg-muis.tsv", row(a, "fit"), row(b, "test"), "@fitted\tsg.muis\t$b\tF\tFajr was widened to this table's excess")
        val t = Gate(roots).evaluate(start).entries.getValue("sg.muis")
        assertEquals(listOf(10, 0, 0), listOf(t.tableDayCount, t.testDays, t.testRamadanDays))
        assertEquals(null, t.heldOut[Event.FAJR])
        assertEquals(5, t.heldOut.getValue(Event.MAGHRIB).checked)

        // And at a reach row replaying it too: the cell is the table's, wherever it is read.
        val replayed = join(start, file("sg-muis-reach.tsv", row(b, "test", point = elsewhere)))
        val counted = Counted(replayed)
        val replay = replayed.rows.last()
        assertTrue(!counted.heldOut(replay, Event.FAJR) && !counted.heldOut(replay) && counted.heldOut(replay, Event.MAGHRIB))
    }

    /** The archive's layout: the monitor pins every capture under its own date, so a table fetched again lies at a new path. */
    private fun captures(): OfficialRoots {
        val dir = kotlin.io.path.createTempDirectory("captures").toFile()
        val first = root.resolve(a).readText()
        val second = root.resolve(b).readLines().filterNot { it.startsWith("#") }.joinToString("\n")
        for (path in listOf(a, b)) dir.resolve(path).apply { parentFile.mkdirs() }.writeText(root.resolve(path).readText())
        // The same table a week later, whole: the five days the margins were fitted on, and five more.
        for (date in listOf("2026-10-09", "2026-10-16")) {
            dir.resolve("open/pinned/$date/muis-2026-02.txt").apply { parentFile.mkdirs() }.writeText(first + second + "\n")
        }
        return OfficialRoots.of(dir)
    }

    /** The review round of 14 Nov 2026: Diyanet's European cities and FIDS's Zürich were stamped held out on fitted days. */
    @Test
    fun `a later capture of a fitted table at another path is a fit table on the days the fitted one holds`() {
        val roots = captures()
        val later = "open/pinned/2026-10-09/muis-2026-02.txt"
        val again = "open/pinned/2026-10-16/muis-2026-02.txt"
        val once = file("sg-muis.tsv", row(a, "fit"), row(later, "test"))
        val s = Gate(roots).evaluate(once).entries.getValue("sg.muis")
        // Ten days at one place: the capture's first five are the fitted table's days, its last five no fit row holds.
        assertEquals(listOf(10, 5, 5), listOf(s.tableDayCount, s.testDays, s.testRamadanDays))
        assertEquals(setOf(5), s.heldOut.values.map { it.checked }.toSet())
        assertEquals(setOf(listOf("2026-02-19..2026-02-23")), s.heldOut.values.map { dateRanges(it.dates) }.toSet())
        // By event: a row fitted on Fajr alone leaves the later capture's other columns held out on every day.
        val fajr = file("sg-muis.tsv", row(a, "fit", "F - - - - -"), row(later, "test"))
        val t = Gate(roots).evaluate(fajr).entries.getValue("sg.muis")
        assertEquals(5, t.heldOut.getValue(Event.FAJR).checked)
        assertEquals(10, t.heldOut.getValue(Event.MAGHRIB).checked)
        assertEquals(5, t.testDays)
        // A `@fitted` line's days carry over the same way.
        val line = file("sg-muis.tsv", row(b, "test"), row(later, "test"), "@fitted\tsg.muis\t$b\t*\tevery margin was widened to this table")
        val u = Gate(roots).evaluate(line).entries.getValue("sg.muis")
        assertEquals(listOf(10, 5), listOf(u.tableDayCount, u.testDays))
        assertEquals(setOf(listOf("2026-02-14..2026-02-18")), u.heldOut.values.map { dateRanges(it.dates) }.toSet())
        // At another place the day is another cell: nothing was fitted there (no unit names the two rows as one place).
        val apart = file("sg-muis.tsv", row(a, "fit"), row(later, "test", point = elsewhere))
        assertEquals(10, Gate(roots).evaluate(apart).entries.getValue("sg.muis").testDays)

        // And a held-out day two captures print is one held-out cell, in whatever order the files are read.
        for (manifest in listOf(file("sg-muis.tsv", row(a, "fit"), row(later, "test"), row(again, "test")), file("sg-muis.tsv", row(again, "test"), row(later, "test"), row(a, "fit")))) {
            val twice = Gate(roots).evaluate(manifest).entries.getValue("sg.muis")
            assertEquals(listOf(10, 5), listOf(twice.tableDayCount, twice.testDays))
            assertEquals(setOf(5), twice.heldOut.values.map { it.checked }.toSet())
            assertEquals(setOf(5), twice.heldOut.values.map { it.late.sum() }.toSet())
            // Compared at every capture all the same.
            assertEquals(25, twice.event(Event.FAJR).checked)
        }
    }

    /**
     * The second trust audit's G27 (14 Nov 2026): a row that names no unit, at a point that resolves to a unit, re-reading
     * the unit's fitted table under another path (İstanbul's, Ankara's and Tekirdağ's at the app's points), is the unit's
     * on those days: not held out. Keyed by its point alone it was.
     */
    @Test
    fun `an untagged row at a unit's town re-reading a fit capture is not held out`() {
        val manifest = file(
            "tr-x.tsv",
            "$a\tsg.muis/u1\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t",
            row("open/pinned/2026-11-11/u1.txt", "test", point = elsewhere),
        )
        val day = 20_500
        val days: (GateRow) -> List<java.util.BitSet> = { r -> r.columns.map { java.util.BitSet().apply { set(day) } } }
        val town = manifest.rows.single { it.unit == null }
        val byPoint = Counted(manifest, days)
        assertTrue(byPoint.heldOut(town, Event.FAJR, day), "keyed by its point, the old reading")
        val byUnit = Counted(manifest, days, unitOf = { r -> if (r.unit == null && r.lat != null) "u1" else null })
        assertEquals(false, byUnit.heldOut(town, Event.FAJR, day))
        assertEquals(false, byUnit.heldOut(town, day))
        assertEquals(byUnit.scope(manifest.rows.single { it.unit == "u1" }), byUnit.scope(town))
        // A day the fit does not hold stays held out.
        assertTrue(byUnit.heldOut(town, Event.FAJR, day + 1))
    }

    @Test
    fun `without the tables the rule is by file alone, and a cautious row's cell is fit where its member's own is`() {
        val later = "open/pinned/2026-10-09/muis-2026-02.txt"
        val files = file("sg-muis.tsv", row(a, "fit"), row(later, "test"))
        val byFile = Counted(files)
        val capture = files.rows.last()
        assertTrue(byFile.heldOut(capture) && byFile.heldOut(capture, Event.FAJR) && byFile.heldOut(capture, Event.FAJR, 20_500))

        // ca.toronto's second halves: each member's margins were widened to its own, so the cautious rows reading them are in-sample.
        val headerWithMember = header + "\tmember"
        fun cautious(path: String, split: String, member: String) = "$path\txx.cautious\t1.40\t103.80\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\t$split\t\t$member"
        val both = GateManifest.parse(
            "xx.tsv",
            listOf(
                headerWithMember,
                row(a, "fit").replace("sg.muis", "xx.one") + "\t", row(b, "test").replace("sg.muis", "xx.one") + "\t",
                row(b, "test").replace("sg.muis", "xx.two") + "\t",
                cautious(a, "fit", "xx.one"), cautious(b, "test", "xx.one"), cautious(b, "test", "xx.two"),
                "@fitted\txx.one\t$b\tM I\tMaghrib and Isha were widened to the held-out half",
            ).joinToString("\n"),
        )
        val counted = Counted(both)
        val (ofOne, ofTwo) = both.rows.takeLast(2)
        assertTrue(!counted.heldOut(ofOne, Event.MAGHRIB) && !counted.heldOut(ofOne, Event.ISHA) && counted.heldOut(ofOne, Event.FAJR))
        assertTrue(!counted.heldOut(ofOne), "a start of the member's was fitted on the table, so its days are no held-out days")
        assertTrue(counted.heldOut(ofTwo, Event.MAGHRIB) && counted.heldOut(ofTwo), "the other member's margins never saw it")
    }
}
