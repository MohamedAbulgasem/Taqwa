package world.taqwa.timetables.gate

import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.timetables.TestPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `a group or entry that matches nothing fails`() {
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
        val group = assertFailsWith<GateError> { manifest.only(groups = setOf("sg-muis", "sg-mius")) }
        assertTrue("no gate file sg-mius.tsv" in group.message!!, group.message)
        val entry = assertFailsWith<GateError> { manifest.only(groups = setOf("sg-muis"), entries = setOf("ru.dumrt")) }
        assertTrue("no gate row for entry ru.dumrt in sg-muis" in entry.message!!, entry.message)
        assertEquals(listOf("sg-muis.tsv"), manifest.only(entries = setOf("sg.muis")).rows.map { it.source }.distinct())
    }
}
