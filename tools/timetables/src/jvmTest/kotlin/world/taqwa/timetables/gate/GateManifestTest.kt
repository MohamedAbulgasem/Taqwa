package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.timetables.TestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GateManifestTest {

    private val header = GateManifest.HEADER.joinToString("\t")

    private fun parse(vararg lines: String) = GateManifest.parse("test.tsv", (listOf(header) + lines).joinToString("\n"))

    @Test
    fun `a row maps each column to its events`() {
        val m = parse("open/SG-MUIS/a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\tnote here")
        val row = m.rows.single()
        assertEquals("open/SG-MUIS/a.txt", row.path)
        assertEquals("sg.muis", row.entry)
        assertNull(row.unit)
        assertNull(row.lat)
        assertEquals(
            listOf(
                listOf(Event.FAJR, Event.END_OF_EATING), listOf(Event.SUNRISE), listOf(Event.DHUHR),
                listOf(Event.ASR_STANDARD), listOf(Event.MAGHRIB), listOf(Event.ISHA),
            ),
            row.columns,
        )
        assertEquals(AsrSchool.STANDARD, row.school)
        assertEquals(Split.FIT, row.split)
        assertEquals("note here", row.note)
        assertEquals("test.tsv:2", row.where)
    }

    @Test
    fun `a row of an envelope file is held to the promise alone`() {
        // The city-point round 2 review: a table replayed where the place may follow another is no unit's figure.
        val line = "open/SG-MUIS/a.txt\tsg.muis\t1.3\t103.8\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\tnote"
        val text = (listOf(header) + line).joinToString("\n")
        assertTrue(GateManifest.parse("us-isna-envelope.tsv", text).rows.single().promiseOnly)
        assertTrue(!GateManifest.parse("us-isna.tsv", text).rows.single().promiseOnly)
    }

    @Test
    fun `a row of a hand-kept promise file is held to the promise alone and is no published table`() {
        // Ruling R247: a calculator's table no place is known to follow guards the promise and enters no figure.
        val line = "manual/mcw/a.txt\tdefault.safe.americas\t-54.8\t-68.3\tAmerica/Argentina/Ushuaia\tF S D A M I\tdaily\tstandard\tfit\tnote"
        val text = (listOf(header) + line).joinToString("\n")
        val kept = GateManifest.parse("safe-default-americas-promise.tsv", text)
        assertTrue(kept.rows.single().promiseOnly)
        assertTrue(!kept.rows.single().reach)
        assertEquals(0, world.taqwa.timetables.ProofTotals.gateTables(kept))
        assertEquals(1, world.taqwa.timetables.ProofTotals.gateTables(GateManifest.parse("safe-default-americas.tsv", text)))
        // The envelope's generator never takes a hand-kept file for one of its own.
        assertTrue(!"safe-default-americas-promise.tsv".endsWith(GateRow.PROMISE_ONLY_SUFFIX))
    }

    @Test
    fun `an excuse line names a member's days, where, why and the reason, and is no row`() {
        // Ruling R117: dates and a reason only, never a printed time.
        val m = parse(
            "@excuse\tca.toronto\tca.ift\t*\t2026-11-01..2026-11-06\tfault\tthe table keeps daylight time",
            "@excuse\tca.cautious\tca.fifteen\t53.6080,-113.5260\t2026-12-24\tunpublished\tno row that day",
        )
        assertTrue(m.rows.isEmpty())
        val (all, one) = m.excuses
        assertEquals("ca.toronto", all.entry)
        assertEquals("ca.ift", all.member)
        assertNull(all.at)
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 1), all.days.start)
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 6), all.days.endInclusive)
        assertEquals(ExcuseKind.FAULT, all.kind)
        assertEquals("the table keeps daylight time", all.reason)
        assertEquals("test.tsv:2", all.where)
        assertEquals("53.608,-113.526", pointKey(one.at!!))
        assertEquals(one.days.start, one.days.endInclusive)
        assertEquals(ExcuseKind.UNPUBLISHED, one.kind)
        // A group's excuses go with its rows.
        val group = GateManifest.parse("ca-x.tsv", header + "\n@excuse\ta\tb\t*\t2026-01-01\tfault\tr")
        assertEquals(1, group.only(setOf("ca-x")).excuses.size)
    }

    @Test
    fun `an excuse line without its reason, kind, dates or point is refused`() {
        val error = assertFailsWith<GateError> {
            parse(
                "@excuse\tca.toronto\tca.ift\t*\t2026-11-01\tfault",
                "@excuse\tca.toronto\tca.ift\t*\t2026-11-01\twrong\ta reason",
                "@excuse\tca.toronto\tca.ift\t*\t2026-11-06..2026-11-01\tfault\ta reason",
                "@excuse\tca.toronto\tca.ift\tsomewhere\t2026-11-01\tfault\ta reason",
            )
        }
        assertEquals(4, error.problems.size, error.message)
        assertTrue("test.tsv:3: 'wrong' (fault or unpublished)" in error.problems, error.message)
        assertTrue("test.tsv:4: dates '2026-11-06..2026-11-01' (yyyy-mm-dd or from..to)" in error.problems, error.message)
        assertTrue("test.tsv:5: point 'somewhere' (* or lat,lon)" in error.problems, error.message)
    }

    @Test
    fun `a demoted line names an entry or one of its units, the day and the reason, and is no row`() {
        // Ruling R185: the monitor's own record; dates, counts and minutes only, never a printed time.
        val row = "x.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t"
        val m = parse(
            row,
            "@demoted\tsg.muis/east\t2027-01-12\tfajr early on 3 place-days, by up to 2 min",
            "@demoted\tsg.muis\t2027-01-19\tmaghrib early on 1 place-days, by up to 1 min",
            "@demoted\tsg.muis/east\t2027-01-05\tan earlier line for the same unit",
            "@demoted\tsg.muis/west\t2027-01-26\tanother unit",
        )
        assertEquals(1, m.rows.size)
        val (east, whole) = m.demotions
        assertEquals("sg.muis", east.entry)
        assertEquals("east", east.unit)
        assertEquals("sg.muis/east", east.target)
        assertEquals(kotlinx.datetime.LocalDate(2027, 1, 12), east.since)
        assertEquals("fajr early on 3 place-days, by up to 2 min", east.reason)
        assertEquals("test.tsv:3", east.where)
        assertNull(whole.unit)
        assertEquals("sg.muis", whole.target)
        // A line for the whole entry covers each of its units; a unit's line covers that unit alone.
        assertTrue(whole.covers("sg.muis", "east") && whole.covers("sg.muis", null) && !whole.covers("sg.other", null))
        assertTrue(east.covers("sg.muis", "east") && !east.covers("sg.muis", "west") && !east.covers("sg.muis", null))
        // What stands: the whole entry's line alone where there is one; else each unit's earliest.
        assertEquals(listOf(whole), standing(m.demotions, "sg.muis"))
        val units = standing(m.demotions.filter { it.unit != null }, "sg.muis")
        assertEquals(listOf("east" to kotlinx.datetime.LocalDate(2027, 1, 5), "west" to kotlinx.datetime.LocalDate(2027, 1, 26)), units.map { it.unit to it.since })
        assertEquals(emptyList(), standing(m.demotions, "sg.other"))
        // The line prove writes reads back as it was written, and a group's demotions go with its rows.
        val line = GateManifest.demotedLine("sg.muis", "east", kotlinx.datetime.LocalDate(2027, 1, 12), "a reason\twith a tab")
        assertEquals("@demoted\tsg.muis/east\t2027-01-12\ta reason with a tab", line)
        val group = GateManifest.parse("sg-x.tsv", header + "\n" + row + "\n" + line)
        assertEquals("a reason with a tab", group.demotions.single().reason)
        assertEquals(1, group.only(setOf("sg-x")).demotions.size)
        assertEquals(0, GateManifest(group.rows, listOf("sg-x.tsv", "b.tsv"), demotions = group.demotions).only(setOf("b")).demotions.size)
    }

    @Test
    fun `a demoted line without its reason or date, with an empty unit, or for an entry the file has no row of is refused`() {
        val row = "x.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t"
        val error = assertFailsWith<GateError> {
            parse(
                row,
                "@demoted\tsg.muis\t2027-01-12",
                "@demoted\tsg.muis\t12 Jan 2027\ta reason",
                "@demoted\tsg.muis/\t2027-01-12\ta reason",
                "@demoted\tsg.mius\t2027-01-12\ta reason",
            )
        }
        assertEquals(4, error.problems.size, error.message)
        assertTrue("test.tsv:3: @demoted <entry>[/<unit>] <yyyy-mm-dd> <reason>" in error.problems, error.message)
        assertTrue("test.tsv:4: date '12 Jan 2027' (yyyy-mm-dd)" in error.problems, error.message)
        assertTrue("test.tsv:5: @demoted names 'sg.muis/' (<entry> or <entry>/<unit>)" in error.problems, error.message)
        assertTrue("test.tsv:6: @demoted names sg.mius, which has no row in test.tsv" in error.problems, error.message)
    }

    /** Ruling R214's sweep: the cells of a test row a margin was set on are named fit cells, by table and event. */
    @Test
    fun `a fitted line names an entry's table and the events a margin saw there`() {
        val fit = "a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t"
        val test = "tables/2026/b.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest\t"
        val east = "tables/2027/c.txt\tsg.muis/east\t1.3\t103.9\tAsia/Singapore\tIm F S D A M I\tdaily\tstandard\ttest\t"
        val m = parse(
            fit, test, east,
            "@fitted\tsg.muis\ttables/2026/b.txt\tF A\tFajr and Asr were widened to this table's excess",
            "@fitted\tsg.muis/east\ttables/*\t*\tevery margin of the unit was read off these",
            "@fitted\tsg.muis\ttables/2027/*\tIm\tthe imsak's margin",
            "@fitted\tsg.muis\ttables/*/b.txt\tM\tthe table of every year, derived from one a margin was set on",
        )
        val (one, unit, imsak, derived) = m.fitted
        assertEquals(setOf(Event.FAJR, Event.ASR_STANDARD, Event.ASR_HANAFI), one.events)
        assertEquals("test.tsv:5", one.where)
        assertEquals("Fajr and Asr were widened to this table's excess", one.reason)
        assertNull(one.unit)
        assertEquals(Event.entries.toSet(), unit.events)
        assertEquals("east", unit.unit)
        assertEquals(setOf(Event.IMSAK), imsak.events)
        // A path names one table; a prefix ending in * names every table under it; a unit's line names that unit's rows alone.
        val (a, b, c) = m.rows
        assertTrue(one.names(b) && !one.names(a) && !one.names(c))
        assertTrue(unit.names(c) && !unit.names(b))
        assertTrue(imsak.names(c) && !imsak.names(b))
        // One * anywhere stands for any run of characters: a folder's table of every year, and no other file there.
        assertTrue(derived.names(b) && !derived.names(a) && !derived.names(c))
        assertFalse(derived.copy(path = "tables/2026/b*b.txt").names(b), "the head and the tail never share a character")
        // A group's lines go with its rows, and an entry's with the entry.
        assertEquals(4, m.only(setOf("test")).fitted.size)
        assertEquals(0, GateManifest(m.rows, listOf("test.tsv", "b.tsv"), fitted = m.fitted).only(setOf("b")).fitted.size)
        assertEquals(4, m.only(entries = setOf("sg.muis")).fitted.size)
        val other = "d.txt\tsg.other\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t"
        assertEquals(0, GateManifest(m.rows + parse(other).rows, m.sources, fitted = m.fitted).only(entries = setOf("sg.other")).fitted.size)
    }

    @Test
    fun `a fitted line short of a cell, with an unknown event, or naming no test row reading those events is refused`() {
        val fit = "a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\t"
        val test = "b.txt\tsg.muis\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\ttest\t"
        val error = assertFailsWith<GateError> {
            parse(
                fit, test,
                "@fitted\tsg.muis\tb.txt\tF",
                "@fitted\tsg.muis\tb.txt\tF X\ta reason",
                "@fitted\tsg.muis/\tb.txt\tF\ta reason",
                // A fit row's table: nothing there is stamped held out, so the line would lower no figure.
                "@fitted\tsg.muis\ta.txt\tF\ta reason",
                // The test row reads no imsak; a path spelt wrongly; another entry.
                "@fitted\tsg.muis\tb.txt\tIm\ta reason",
                "@fitted\tsg.muis\tc.txt\t*\ta reason",
                "@fitted\tsg.mius\tb.txt\t*\ta reason",
                // A pattern holds one *: two would name more than anyone reading the line can check.
                "@fitted\tsg.muis\t*b*\t*\ta reason",
            )
        }
        assertEquals(8, error.problems.size, error.message)
        assertTrue("test.tsv:4: @fitted <entry>[/<unit>] <path or pattern with one *> <events or *> <reason>" in error.problems, error.message)
        assertTrue("test.tsv:11: @fitted pattern '*b*' holds more than one *" in error.problems, error.message)
        assertTrue(error.problems.any { it.startsWith("test.tsv:5: event 'X' (") }, error.message)
        assertTrue("test.tsv:6: @fitted names 'sg.muis/' (<entry> or <entry>/<unit>)" in error.problems, error.message)
        for (line in 7..10) {
            assertTrue(error.problems.any { it.startsWith("test.tsv:$line: @fitted names no test row of test.tsv reading those events at ") }, error.message)
        }
    }

    /** The committed gate files: every fitted line gives its reason, and none names a whole root without a table's folder. */
    @Test
    fun `the committed fitted lines each name a table or a folder's prefix and say why`() {
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        assertTrue(manifest.fitted.isNotEmpty())
        for (f in manifest.fitted) {
            assertTrue(f.reason.length >= 20, "${f.where}: say what was fitted and where it is recorded")
            // `archive/tables/<round>/…` or `open/<AUTHORITY>/…`: at least the folder the tables lie in, never a root alone.
            val named = f.path.substringBefore('*').trimEnd('/').split('/').filter { it.isNotEmpty() }
            val root = if (named.firstOrNull() == "archive") 2 else 1
            assertTrue(named.size >= root + 2, "${f.where}: ${f.path} names no table")
        }
    }

    @Test
    fun `a hanafi row's A is the hanafi asr and a unit follows the entry`() {
        val row = parse("x.txt\tru.dumrt/kazan\t55.79\t49.12\tEurope/Moscow\tIm F S - A As M I\tdaily\thanafi\ttest\t").rows.single()
        assertEquals("ru.dumrt", row.entry)
        assertEquals("kazan", row.unit)
        assertEquals(55.79, row.lat)
        assertEquals(49.12, row.lon)
        assertEquals(Split.TEST, row.split)
        assertEquals(
            listOf(
                listOf(Event.IMSAK), listOf(Event.FAJR), listOf(Event.SUNRISE), emptyList(), listOf(Event.ASR_HANAFI),
                listOf(Event.ASR_STANDARD), listOf(Event.MAGHRIB), listOf(Event.ISHA),
            ),
            row.columns,
        )
    }

    @Test
    fun `a clock column names the zone the table is printed in and defaults to the zone`() {
        val text = listOf(
            (GateManifest.HEADER + "clock").joinToString("\t"),
            "a.txt\tgb.lupt\t51.5\t-0.12\tEurope/London\tF S\tdaily\t-\ttest\t\tUTC",
            "b.txt\tgb.lupt\t51.5\t-0.12\tEurope/London\tF S\tdaily\t-\ttest\t\t",
        ).joinToString("\n")
        val (printedInGmt, printedLocally) = GateManifest.parse("c.tsv", text).rows
        assertEquals("UTC", printedInGmt.clockZone)
        assertEquals("Europe/London", printedInGmt.zone)
        assertNull(printedLocally.clock)
        assertEquals("Europe/London", printedLocally.clockZone)
    }

    @Test
    fun `the header may come in any order and comments are skipped`() {
        val text = """
            # a comment
            note	split	school	format	columns	zone	lon	lat	entry	path

            last	test	-	daily	- - D - - -	Europe/Moscow			ru.dumrt/kazan	x.txt
        """.trimIndent()
        val row = GateManifest.parse("g.tsv", text).rows.single()
        assertEquals(listOf(emptyList(), emptyList(), listOf(Event.DHUHR), emptyList(), emptyList(), emptyList()), row.columns)
        assertNull(row.school)
        assertEquals("g.tsv:4", row.where)
    }

    @Test
    fun `every problem is reported at once with its line`() {
        val error = assertFailsWith<GateError> {
            parse(
                "a.txt\tsg.muis\t\t\tAsia/Singapore\tI F S D A M Is\tdaily\tstandard\tfit\t",
                "b.txt\tsg.muis\t1.3\t\tAsia/Singapore\tF S\tdaily\tstandard\tlater\t",
                "c.txt\tsg.muis\t\t\tAsia/Singapore\tF A\tdaily\t-\tfit\t",
                "d.txt\tsg.muis\t\t\tAsia/Singapore\tF F+E\tdaily\tstandard\tfit\t",
            )
        }
        val text = error.message!!
        assertTrue("test.tsv:2: unknown column 'Is'" in text && "I is Isha" in text, text)
        assertTrue("test.tsv:3: give both lat and lon" in text, text)
        assertTrue("test.tsv:3: split 'later'" in text, text)
        assertTrue("test.tsv:4: column A needs the row's school" in text, text)
        assertTrue("test.tsv:5: an event is mapped to two columns" in text, text)
        assertEquals(5, error.problems.size, text)
    }

    @Test
    fun `a header that misses a column is refused`() {
        val text = "path\tentry\tlat\tlon\tzone\tcolumns\tschool\tsplit\tnote\na.txt\tsg.muis\t\t\tAsia/Singapore\tF\tstandard\tfit\t\n"
        val error = assertFailsWith<GateError> { GateManifest.parse("h.tsv", text) }
        assertTrue("missing format" in error.message!!, error.message)
        assertEquals(1, error.problems.size, error.message)
    }

    @Test
    fun `a late-limit exception is not the gate file's to make`() {
        val error = assertFailsWith<GateError> { parse("@lateLimit\tsg.muis\t*\t2\tone-day offset") }
        assertTrue("test.tsv:2: a late-limit exception lives with its registry entry or unit (ruling R37" in error.message!!, error.message)
    }

    @Test
    fun `the committed gate files parse`() {
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        assertTrue(manifest.rows.any { it.entry == "sg.muis" }, "sg-muis.tsv")
        assertTrue(manifest.rows.any { it.entry == "ru.dumrt" }, "ru-dumrt.tsv")
        for (row in manifest.rows) OfficialFormats.named(row.format)
        assertEquals(manifest.rows.filter { it.source == "sg-muis.tsv" }, manifest.only(groups = setOf("sg-muis")).rows)
    }

    @Test
    fun `no committed row reads a dawat-e-islami month with the end of eating on its fajr column`() {
        // Ruling R149 (E16): the archived months round their starts up from the source's seconds, so the gate refuses
        // F+E on them when it reads the table (Gate.roundedUp); this holds the same without the archive, so that a
        // hand-added month copied from the rows of before 18 Oct 2026 is caught in CI too.
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val rows = manifest.rows.filter { "/off-di-" in it.path }
        assertTrue(rows.isNotEmpty(), "the Dawat-e-Islami rows")
        val shared = rows.filter { row -> row.columns.any { Event.FAJR in it && Event.END_OF_EATING in it } }
        assertEquals(emptyList(), shared.map { it.where }, "rows reading F+E on a rounded-up month")
        assertTrue(rows.all { it.path.endsWith("-e.txt") }, rows.filter { !it.path.endsWith("-e.txt") }.map { it.where }.toString())
    }

    @Test
    fun `a group or entry that matches nothing fails`() {
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val group = assertFailsWith<GateError> { manifest.only(groups = setOf("sg-muis", "sg-mius")) }
        assertTrue("no gate file sg-mius.tsv" in group.message!!, group.message)
        val entry = assertFailsWith<GateError> { manifest.only(groups = setOf("sg-muis"), entries = setOf("ru.dumrt")) }
        assertTrue("no gate row for entry ru.dumrt in sg-muis" in entry.message!!, entry.message)
        // MUIS's own file and, since ruling R297, its rows read across Singapore.
        assertEquals(setOf("sg-muis.tsv", "sg-muis-reach.tsv"), manifest.only(entries = setOf("sg.muis")).rows.map { it.source }.toSet())
    }
}
