package world.taqwa.timetables.monitor

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.timetables.TestPaths
import world.taqwa.timetables.gate.OfficialRoots
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The monitor's per-table check (brief P item 4, ruling R93) on invented tables: the open MUIS
 * fixture (`gate-fixture`, Singapore Open Data Licence) shifted about, and tables written from what
 * the engine itself computes at run time (never a restricted printed time, ruling R69).
 */
class TableCheckTest {

    private val fixture: File = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
    private val official: File = Files.createTempDirectory("monitor-check").toFile()
    private val officialDir = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val roots = OfficialRoots(official, officialDir)
    private val check = TableCheck(roots, officialDir)
    private val today = LocalDate(2026, 10, 5)

    @AfterTest
    fun cleanUp() {
        official.deleteRecursively()
    }

    private fun table(
        source: String, key: String, path: String, entry: String?, lat: Double?, lon: Double?, zone: String, cc: String,
        columns: String, survey: String? = null, school: String = "standard", clock: String? = null,
    ) = MonitorTable(
        source, key, path, "$key (test)", lat, lon, zone, clock, cc, entry, survey, columns, "daily", school, "hash",
        "2026-10-05", "",
    )

    private val changed = TableCheck.Status(isNew = false, contentChanged = true, wasRed = false, lateness = null)
    private val again = TableCheck.Status(isNew = false, contentChanged = false, wasRed = false, lateness = null)
    private val wasRed = TableCheck.Status(isNew = false, contentChanged = false, wasRed = true, lateness = null)

    /**
     * The fixture's ten MUIS days as one monitor table, every time moved by [shiftMinutes] (the
     * sunrise column by [shiftSunrise]); [drop] leaves a date out; [columns] names what is read.
     */
    private fun muisTable(shiftMinutes: Int, drop: String? = null, columns: String = "F+E S D A M I", shiftSunrise: Int = shiftMinutes): MonitorTable {
        val lines = listOf("muis-2026-02-a.txt", "muis-2026-02-b.txt").flatMap { fixture.resolve("open/SG-MUIS/$it").readLines() }
            .filter { it.isNotBlank() && !it.startsWith("#") && (drop == null || !it.startsWith(drop)) }
            .map { line ->
                val parts = line.split(' ')
                parts[0] + parts.drop(1).mapIndexed { i, t ->
                    val (h, m) = t.split(':').map(String::toInt)
                    val total = (h * 60 + m + (if (i == 1) shiftSunrise else shiftMinutes) + 24 * 60) % (24 * 60)
                    " %02d:%02d".format(total / 60, total % 60)
                }.joinToString("")
            }
        val path = "archive/tables/monitor/sg-muis/singapore.txt"
        official.resolve(path).parentFile.mkdirs()
        official.resolve(path).writeText("# an invented table for the test\n" + lines.joinToString("\n") + "\n")
        return table("sg-muis", "singapore", path, "sg.muis", null, null, "Asia/Singapore", "SG", columns)
    }

    @Test
    fun `a new table the engine is never early against is fine and gets a gate row to paste at a dated path`() {
        val item = check.check(muisTable(0)).item
        assertEquals(Kind.NEW_TABLE_FINE, item.kind, item.toString())
        assertTrue(item.details.any { "gate row" in it && "archive/tables/held/sg-muis/singapore-2026-10-05.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest" in it }, item.toString())
        assertTrue(item.details.any { "first copy `archive/tables/monitor/sg-muis/singapore.txt` to `archive/tables/held/sg-muis/singapore-2026-10-05.txt`" in it }, item.toString())
        assertTrue(item.details.any { "10 place-days 2026-02-14..2026-02-23" in it }, item.toString())
    }

    @Test
    fun `a held table is labelled by what happened to it`() {
        assertEquals(Kind.CHANGED_FINE, check.check(muisTable(0), changed).item.kind)
        assertEquals(Kind.GREEN_AGAIN, check.check(muisTable(0), wasRed).item.kind)
        val checkedAgain = check.check(muisTable(0), again).item
        assertEquals(Kind.CHECKED_FINE, checkedAgain.kind)
        assertEquals("sg-muis/singapore", checkedAgain.title)
    }

    @Test
    fun `a table with a day missing says so instead of hiding the hole in a range`() {
        val item = check.check(muisTable(0, drop = "2026-02-18")).item
        assertEquals(Kind.NEW_TABLE_FINE, item.kind, item.toString())
        assertTrue(item.details.any { "9 of 10 days 2026-02-14..2026-02-23; missing 2026-02-18" in it }, item.toString())
    }

    @Test
    fun `a table printed three minutes later shows the engine early and is never early attention`() {
        val outcome = check.check(muisTable(+3))
        val item = outcome.item
        assertEquals(Kind.NEVER_EARLY, item.kind, item.toString())
        assertTrue(outcome.red)
        assertTrue(item.details.any { "fajr: 10 early" in it }, item.toString())
        assertEquals(10, item.days)
        assertEquals(3, item.worst)
        assertEquals("table:sg-muis/singapore:never-early", item.key)
        assertTrue("2026-02-14..2026-02-23" in item.label, item.label)
        assertTrue(item.next != null && "never excused" in item.next, item.toString())
        // No printed time reaches the report: dates, counts and minutes only.
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        assertTrue((item.details + item.title).none { time.containsMatchIn(it) }, item.toString())
    }

    @Test
    fun `lateness over the limit on the table's own entry is raised once and carried until it worsens`() {
        // The starts printed five minutes earlier than the engine (sunrise as printed, Fajr's column not read,
        // so no end moves): never early, five minutes late, over MUIS's limit.
        fun late(minutes: Int, columns: String = "- S D A M I", drop: String? = null) = muisTable(-minutes, drop = drop, columns = columns, shiftSunrise = 0)
        val first = check.check(late(5, drop = "2026-02-23"), TableCheck.Status.NEW, today)
        val raised = first.item
        assertEquals(Kind.OWN_LATE, raised.kind, raised.toString())
        assertTrue(raised.kind.attention)
        assertTrue(first.red, "an open lateness keeps the table checked every run")
        assertTrue(raised.details.first().startsWith("raised for the first time"), raised.toString())
        val record = first.lateness!!
        assertEquals(today, record.since)
        assertTrue(record.worst >= 5, record.toString())
        assertEquals(setOf("dhuhr", "asrStandard", "maghrib", "isha"), record.events)
        assertTrue(record.cells > 0)
        assertEquals("table:sg-muis/singapore:late", raised.key)
        // The next run, the table grew by a day (more cells, the same worst, the same events): still open,
        // informational, the exit code untouched (ruling N2: growth alone is never worse).
        val grown = check.check(late(5), TableCheck.Status(false, true, true, record), LocalDate(2026, 10, 12))
        assertEquals(Kind.STILL_OPEN, grown.item.kind, grown.item.toString())
        assertTrue(!grown.item.kind.attention)
        assertEquals(today, grown.item.since)
        assertEquals(today, grown.lateness!!.since)
        assertTrue(grown.lateness!!.cells > record.cells, "the cells are recorded for the record")
        assertEquals(record.worst, grown.lateness!!.worst, "the worst is a high-water mark")
        // A higher worst: raised again, its first date kept, the high-water mark raised.
        val worse = check.check(late(7), TableCheck.Status(false, true, true, grown.lateness), LocalDate(2026, 10, 19))
        assertEquals(Kind.OWN_LATE, worse.item.kind, worse.item.toString())
        assertTrue(worse.item.details.first().startsWith("worse: the worst grew"), worse.item.toString())
        assertEquals(today, worse.lateness!!.since)
        assertEquals(record.worst + 2, worse.lateness!!.worst)
        // Back to five minutes: not worse than the mark (carried), even though the cells changed.
        val back = check.check(late(5), TableCheck.Status(false, true, true, worse.lateness), LocalDate(2026, 10, 26))
        assertEquals(Kind.STILL_OPEN, back.item.kind, back.item.toString())
        assertEquals(record.worst + 2, back.lateness!!.worst)
        // An event over the limit for the first time: a record without Dhuhr among its events, then Dhuhr's
        // column read (a start printed earlier is late, never early, so this is the only change): raised again.
        val withoutDhuhr = back.lateness!!.copy(events = back.lateness!!.events - "dhuhr")
        val newEvent = check.check(late(5), TableCheck.Status(false, true, true, withoutDhuhr), LocalDate(2026, 11, 2))
        assertEquals(Kind.OWN_LATE, newEvent.item.kind, newEvent.item.toString())
        assertTrue(newEvent.item.details.first().startsWith("worse: dhuhr over the limit for the first time"), newEvent.item.toString())
        assertTrue("dhuhr" in newEvent.lateness!!.events && newEvent.lateness!!.events.containsAll(withoutDhuhr.events))
    }

    @Test
    fun `a calendar whose cells are last year's capture re-dated is said so`() {
        // The first calendar the UK survey lists, with an invented held capture for 2026 and the same cells fetched as 2027.
        val listed = officialDir.resolve("survey/gb-cautious/calendars.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split('\t') }.first { it[5] == "yes" && it[4] == "F S D A M I" }
        val heldPath = listed[0]
        val slug = heldPath.substringAfterLast('/').removeSuffix(".txt")
        fun cells(year: Int, shiftDhuhr: Int = 0) = (1..40).joinToString("\n") { d ->
            val date = LocalDate(year, 1, 1).plus(d - 1, DateTimeUnit.DAY)
            val dh = 12 * 60 + 10 + d + shiftDhuhr
            "$date 06:${(30 + d % 20).toString().padStart(2, '0')} 08:0${d % 10} ${dh / 60}:${(dh % 60).toString().padStart(2, '0')} 14:0${d % 10} 16:${(20 + d % 30).toString().padStart(2, '0')} 18:0${d % 10}"
        }
        official.resolve(heldPath).parentFile.mkdirs()
        official.resolve(heldPath).writeText("# an invented held capture\n" + cells(2026) + "\n")
        val path = "archive/tables/monitor/mawaqit/$slug.txt"
        official.resolve(path).parentFile.mkdirs()
        official.resolve(path).writeText("# the same cells fetched a year later\n" + cells(2027) + "\n")
        val table = table("mawaqit", slug, path, null, listed[2].toDouble(), listed[3].toDouble(), "Europe/London", "GB", "F S D A M I", survey = "gb-cautious")
        val plan = check.plan(table) as TableCheck.Plan.Calendar
        assertEquals(slug, plan.held?.id)
        val note = check.reDated(table, plan)
        assertEquals(1, note.size, note.toString())
        assertTrue("the survey's 2026 capture re-dated (40 of 40 days identical by month and day)" in note.single(), note.single())
        // Cells that differ (a real 2027 calendar) say nothing.
        official.resolve(path).writeText("# a real new year\n" + cells(2027, shiftDhuhr = 3) + "\n")
        assertEquals(emptyList(), check.reDated(table, plan))
        // The same year is not re-dating either.
        official.resolve(path).writeText("# this year's\n" + cells(2026) + "\n")
        assertEquals(emptyList(), check.reDated(table, plan))
    }

    @Test
    fun `a table the archive does not hold cannot be checked`() {
        val outcome = check.check(table("x", "missing", "archive/tables/monitor/x/missing.txt", "sg.muis", null, null, "Asia/Singapore", "SG", "F S D A M I"))
        assertEquals(Kind.FETCH_BROKEN, outcome.item.kind)
        assertTrue(outcome.red)
        assertEquals("table:x/missing:broken", outcome.item.key, "its own key: a table that turns unreadable is news beside its never-early item")
        assertTrue(outcome.item.details.any { "is not held" in it }, outcome.item.toString())
    }

    @Test
    fun `an unknown entry or a bad zone cannot be checked and never sinks the run`() {
        val unknown = check.check(table("x", "k", "archive/x.txt", "no.such", 1.0, 2.0, "UTC", "SG", "F S D A M I")).item
        assertEquals(Kind.FETCH_BROKEN, unknown.kind)
        assertTrue(unknown.details.any { "no registry entry" in it }, unknown.toString())
        val badZone = check.check(muisTable(0).copy(zone = "Mars/Olympus")).item
        assertEquals(Kind.FETCH_BROKEN, badZone.kind, badZone.toString())
        val badCalendarZone = check.check(table("mawaqit", "x", "archive/t.txt", null, 52.0, -1.0, "Nowhere/Town", "GB", "F S D A M I", survey = "gb-cautious")).item
        assertEquals(Kind.FETCH_BROKEN, badCalendarZone.kind, badCalendarZone.toString())
    }

    private fun rows(plan: TableCheck.Plan) = (plan as TableCheck.Plan.Rows).rows

    @Test
    fun `a table of an entry automatic follows is checked as that entry at the point`() {
        val rows = rows(check.plan(table("sa-ummalqura", "makkah", "archive/t.txt", "sa.ummalqura", 21.426666, 39.831666, "Asia/Riyadh", "SA", "F+E S D A M I")))
        assertEquals(1, rows.size, rows.toString())
        assertEquals("sa.ummalqura", rows.single().entry)
        assertEquals(null, rows.single().member)
        assertEquals(21.426666, rows.single().lat)
        assertTrue("Automatic follows sa.ummalqura" in rows.single().why, rows.single().why)
    }

    @Test
    fun `a member's table where automatic follows a cautious entry is checked as that member and as its own entry`() {
        val rows = rows(check.plan(table("tr-diyanet-europe", "paris", "archive/t.txt", "tr.diyanet.europe", 48.85341, 2.3488, "Europe/Paris", "FR", "F+E S D A M I")))
        assertEquals(2, rows.size, rows.toString())
        assertEquals("fr.cautious", rows[0].entry)
        assertEquals("tr.diyanet.europe", rows[0].member)
        assertTrue(!rows[0].own)
        assertEquals("tr.diyanet.europe", rows[1].entry)
        assertEquals(null, rows[1].member)
        assertTrue(rows[1].own)
        assertTrue("drifting member model" in rows[1].why, rows[1].why)
    }

    @Test
    fun `a unit-named table is checked at its unit's point and as the member of the cautious entry automatic follows there`() {
        // Diyanet's Amsterdam table names its unit; Automatic at Amsterdam follows nl.cautious, whose member Diyanet is (review I3).
        val rows = rows(check.plan(table("tr-diyanet-europe", "amsterdam", "archive/t.txt", "tr.diyanet.europe/tr.diyanet.europe.amsterdam", 52.37403, 4.88969, "Europe/Amsterdam", "NL", "F+E S D A M I")))
        assertEquals(2, rows.size, rows.toString())
        assertEquals("nl.cautious", rows[0].entry)
        assertEquals("tr.diyanet.europe", rows[0].member)
        assertTrue(rows[0].lat != null && rows[0].lon != null, "the member row is at the unit's reference point")
        assertEquals("tr.diyanet.europe/tr.diyanet.europe.amsterdam", rows[1].entry)
        assertEquals(null, rows[1].lat)
        // A Turkish district: Automatic follows Diyanet itself there, so the unit row alone.
        val turkish = rows(check.plan(table("tr-diyanet", "ankara", "archive/t.txt", "tr.diyanet/9206", null, null, "Europe/Istanbul", "TR", "F+E S D A M I")))
        assertEquals(1, turkish.size, turkish.toString())
        assertEquals("9206", turkish.single().entry.substringAfter('/'))
    }

    @Test
    fun `a table automatic does not follow is checked as its own entry`() {
        // Inside the M25 Automatic follows London Unified; Diyanet's London table is still Diyanet's.
        val rows = rows(check.plan(table("tr-diyanet-europe", "london", "archive/t.txt", "tr.diyanet.europe", 51.5074, -0.1278, "Europe/London", "GB", "F+E S D A M I")))
        assertEquals(1, rows.size)
        assertEquals("tr.diyanet.europe", rows.single().entry)
        assertTrue("not this table's" in rows.single().why, rows.single().why)
    }

    @Test
    fun `a unit named in the entry is checked at the unit's own point`() {
        val rows = rows(check.plan(table("my-jakim", "WLY01", "archive/t.txt", "my.jakim/WLY01", 3.0, 101.0, "Asia/Kuala_Lumpur", "MY", "F S D A M I")))
        assertEquals(1, rows.size)
        assertEquals("my.jakim/WLY01", rows.single().entry)
        assertEquals(null, rows.single().lat)
    }

    @Test
    fun `a table with no point and no fixed point is refused`() {
        val plan = check.plan(table("x", "k", "archive/t.txt", "tr.diyanet", null, null, "Europe/Istanbul", "TR", "F S D A M I"))
        assertTrue(plan is TableCheck.Plan.Refused, plan.toString())
    }

    /** A table written from [entryId]'s own days at a point: each start moved by [startShift] minutes, sunrise a minute later. */
    private fun engineTable(
        source: String, key: String, entryId: String, lat: Double, lon: Double, zoneId: String, cc: String, startShift: Int,
        columns: String = "F S D A M I", survey: String? = null, from: LocalDate = LocalDate(2026, 10, 10),
        edit: (LocalDate, MutableList<Instant>) -> Unit = { _, _ -> },
    ): MonitorTable {
        val zone = TimeZone.of(zoneId)
        val resolution = Registry.resolveEntry(Registry.byId(entryId)!!, Place(lat, lon, zone.id, cc))
        val lines = mutableListOf<String>()
        var date = from
        repeat(10) {
            val day = Ends.withNextDay(DayPipeline.unended(resolution, date, zone), DayPipeline.unended(resolution, date.plus(1, DateTimeUnit.DAY), zone))
            fun hm(instant: Instant): String {
                val t = instant.toLocalDateTime(zone).time
                return "%02d:%02d".format(t.hour, t.minute)
            }
            val shift = startShift.minutes
            val cells = mutableListOf(day.fajr + shift, day.sunrise + 1.minutes, day.dhuhr + shift, day.asr + shift, day.maghrib + shift, day.isha + shift)
            edit(date, cells)
            val kept = columns.split(' ').mapIndexed { i, c -> if (c == "-") "-" else hm(cells[i]) }
            lines += "$date ${kept.joinToString(" ")}"
            date = date.plus(1, DateTimeUnit.DAY)
        }
        val path = "archive/tables/monitor/$source/$key.txt"
        official.resolve(path).parentFile.mkdirs()
        official.resolve(path).writeText("# an invented table from the engine's own days\n" + lines.joinToString("\n") + "\n")
        return table(source, key, path, if (survey == null) entryId else null, lat, lon, zone.id, cc, columns, survey = survey)
    }

    private fun engineCalendar(startShift: Int, from: LocalDate = LocalDate(2026, 10, 10), edit: (LocalDate, MutableList<Instant>) -> Unit = { _, _ -> }) =
        engineTable("mawaqit", "invented-mosque", "gb.cautious", 53.1, -1.7, "Europe/London", "GB", startShift, survey = "gb-cautious", from = from, edit = edit)

    @Test
    fun `a mosque calendar goes through its survey with the faults and outliers recorded for it`() {
        val listed = officialDir.resolve("survey/gb-cautious/outliers.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split('\t')[0] }.firstOrNull { it != "*" }
        val unknown = check.plan(table("mawaqit", "no-such-mosque", "archive/t.txt", null, 52.48, -1.89, "Europe/London", "GB", "F S D A M I", survey = "gb-cautious"))
        val calendar = unknown as TableCheck.Plan.Calendar
        assertEquals("gb.cautious", calendar.survey.entry.id)
        assertEquals(1, calendar.survey.calendars.size)
        assertTrue(calendar.survey.outliers.all { it.calendar == "*" }, calendar.survey.outliers.joinToString { it.label })
        assertTrue("does not list yet" in calendar.why, calendar.why)
        assertEquals(null, calendar.held)
        if (listed != null) {
            val known = check.plan(table("mawaqit", listed, "archive/t.txt", null, 52.48, -1.89, "Europe/London", "GB", "F S D A M I", survey = "gb-cautious")) as TableCheck.Plan.Calendar
            assertTrue(known.survey.outliers.any { it.calendar == listed }, known.survey.outliers.joinToString { it.label })
            assertTrue("as the survey checks it" in known.why, known.why)
            assertEquals(listed, known.held?.id)
        }
    }

    @Test
    fun `a calendar its survey leaves out is not checked and stays green`() {
        val leftOut = officialDir.resolve("survey/gb-cautious/calendars.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split('\t') }.firstOrNull { it[5] != "yes" }
        if (leftOut == null) return
        val slug = leftOut[0].substringAfterLast('/').removeSuffix(".txt")
        val item = check.check(table("mawaqit", slug, "archive/t.txt", null, 52.48, -1.89, "Europe/London", "GB", "F S D A M I", survey = "gb-cautious")).item
        assertEquals(Kind.GREEN, item.kind, item.toString())
        assertTrue("leaves it out" in item.title, item.title)
    }

    @Test
    fun `a canadian calendar keeps its own zone`() {
        val plan = check.plan(table("mawaqit", "x", "archive/t.txt", null, 44.65, -63.6, "America/Halifax", "CA", "F S D A M I", survey = "ca-cautious")) as TableCheck.Plan.Calendar
        assertEquals(TimeZone.of("America/Halifax"), plan.survey.calendars.single().zone)
    }

    @Test
    fun `a mosque calendar the engine is never early against is fine`() {
        val item = check.check(engineCalendar(-1)).item
        assertEquals(Kind.NEW_TABLE_FINE, item.kind, item.toString())
        assertTrue(item.details.any { "10 place-days" in it && "gb.cautious" in it }, item.toString())
        assertTrue(item.details.any { "calendars.tsv row" in it && "archive/tables/uk-mawaqit/invented-mosque.txt" in it }, item.toString())
    }

    @Test
    fun `a mosque calendar printed after the engine's starts is never early attention`() {
        val outcome = check.check(engineCalendar(+5))
        val item = outcome.item
        assertEquals(Kind.NEVER_EARLY, item.kind, item.toString())
        assertTrue(outcome.red)
        assertTrue(item.details.any { "not recorded: invented-mosque" in it && "fajr early by 5 min" in it }, item.toString())
        assertEquals(10, item.days)
        assertTrue(item.worst >= 5, item.toString())
        assertTrue(item.details.none { "suspected calendar fault" in it }, "a plain shift is not a calendar's slip: " + item.toString())
    }

    @Test
    fun `a run of cells twelve hours off is a suspected calendar fault with a faults row ready to paste`() {
        // After the change of clocks the winter Dhuhr falls before noon; stored twelve hours later it reads as
        // an afternoon time, and the engine's Dhuhr is early by about 720 min (the Manchester case of review Q2).
        val slipped = LocalDate(2026, 10, 29)..LocalDate(2026, 10, 31)
        val outcome = check.check(engineCalendar(-1, from = LocalDate(2026, 10, 26)) { date, cells -> if (date in slipped) cells[2] = cells[2] + 720.minutes })
        val item = outcome.item
        assertEquals(Kind.NEVER_EARLY, item.kind, item.toString())
        assertTrue(item.details.any { "dhuhr early by 7" in it }, item.toString())
        val suspected = item.details.firstOrNull { it.startsWith("suspected calendar fault") }
        assertTrue(suspected != null && "column D on 2026-10-29..2026-10-31 (3 days)" in suspected && "+7" in suspected, item.toString())
        val row = item.details.firstOrNull { it.startsWith("faults.tsv row to paste") }
        assertTrue(row != null && "`invented-mosque\t2026-10-29\t2026-10-31\tD\tsuspected" in row, item.toString())
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        assertTrue(item.details.none { time.containsMatchIn(it) }, item.toString())
    }

    @Test
    fun `lateness on a member row is for the record while the member's own entry is held to its limit`() {
        // Diyanet's own Paris days printed twenty minutes before the engine's starts (no Maghrib, no end of eating):
        // fr.cautious waits for the latest member, so it is late by the spread, never early.
        val table = engineTable("tr-diyanet-europe", "paris", "tr.diyanet.europe", 48.85341, 2.3488, "Europe/Paris", "FR", -20, columns = "F S D A - I")
        val outcome = check.check(table, TableCheck.Status.NEW, today)
        val kinds = outcome.items.map { it.kind }
        assertTrue(Kind.NEVER_EARLY !in kinds, outcome.items.toString())
        val member = outcome.items.first { it.kind == Kind.FOR_THE_RECORD && "member" in it.title }
        assertTrue(member.details.any { "fr.cautious" in it && "over the late limit" in it }, member.toString())
        assertTrue(!member.kind.attention)
        val own = outcome.items.first { it.kind == Kind.OWN_LATE }
        assertTrue(own.details.any { "tr.diyanet.europe" in it && "over the late limit" in it }, own.toString())
        assertTrue(own.details.any { "member tr.diyanet.europe" in it }, "the member row's figures are listed too: " + own.toString())
        assertTrue(outcome.items.none { it.kind == Kind.NEW_TABLE_FINE })
    }
}
