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
 * The monitor's per-table check (brief P, item 4) on invented tables: the open MUIS fixture
 * (`gate-fixture`, Singapore Open Data Licence) shifted about, and calendars written from what the
 * engine itself computes at run time (never a restricted printed time, ruling R69).
 */
class TableCheckTest {

    private val fixture: File = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
    private val official: File = Files.createTempDirectory("monitor-check").toFile()
    private val officialDir = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val roots = OfficialRoots(official, officialDir)
    private val check = TableCheck(roots, officialDir)

    @AfterTest
    fun cleanUp() {
        official.deleteRecursively()
    }

    private fun table(
        source: String, key: String, path: String, entry: String?, lat: Double?, lon: Double?, zone: String, cc: String,
        columns: String, status: String = "new", survey: String? = null, school: String = "standard", clock: String? = null,
    ) = MonitorTable(
        source, key, path, "$key (test)", lat, lon, zone, clock, cc, entry, survey, columns, "daily", school, "hash", status,
        "2026-10-05", "",
    )

    /** The fixture's ten MUIS days as one monitor table, every time moved by [shiftMinutes]. */
    private fun muisTable(shiftMinutes: Int, status: String = "new"): MonitorTable {
        val lines = listOf("muis-2026-02-a.txt", "muis-2026-02-b.txt").flatMap { fixture.resolve("open/SG-MUIS/$it").readLines() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split(' ')
                parts[0] + parts.drop(1).joinToString("") { t ->
                    val (h, m) = t.split(':').map(String::toInt)
                    val total = (h * 60 + m + shiftMinutes + 24 * 60) % (24 * 60)
                    " %02d:%02d".format(total / 60, total % 60)
                }
            }
        val path = "archive/tables/monitor/sg-muis/singapore.txt"
        official.resolve(path).parentFile.mkdirs()
        official.resolve(path).writeText("# an invented table for the test\n" + lines.joinToString("\n") + "\n")
        return table("sg-muis", "singapore", path, "sg.muis", null, null, "Asia/Singapore", "SG", "F+E S D A M I", status)
    }

    @Test
    fun `a new table the engine is never early against is fine and gets a gate row to paste`() {
        val item = check.check(muisTable(0))
        assertEquals(Kind.NEW_TABLE_FINE, item.kind, item.toString())
        assertTrue(item.details.any { "gate row" in it && "archive/tables/monitor/sg-muis/singapore.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\ttest" in it }, item.toString())
        assertTrue(item.details.any { "10 place-days 2026-02-14..2026-02-23" in it }, item.toString())
    }

    @Test
    fun `a changed table that is fine is reported as changed`() {
        assertEquals(Kind.CHANGED_FINE, check.check(muisTable(0, status = "changed")).kind)
    }

    @Test
    fun `a table printed three minutes later shows the engine early and needs attention`() {
        val item = check.check(muisTable(+3))
        assertEquals(Kind.EARLY_OR_LATE, item.kind, item.toString())
        assertTrue(item.details.any { "fajr: 10 early" in it }, item.toString())
        assertTrue(item.next != null && "never excused" in item.next, item.toString())
        // No printed time reaches the report: dates, counts and minutes only.
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        assertTrue((item.details + item.title).none { time.containsMatchIn(it) }, item.toString())
    }

    @Test
    fun `a table the archive does not hold cannot be checked`() {
        val item = check.check(table("x", "missing", "archive/tables/monitor/x/missing.txt", "sg.muis", null, null, "Asia/Singapore", "SG", "F S D A M I"))
        assertEquals(Kind.FETCH_BROKEN, item.kind)
        assertTrue(item.details.any { "is not held" in it }, item.toString())
    }

    @Test
    fun `an unknown entry cannot be checked`() {
        val item = check.check(table("x", "k", "archive/x.txt", "no.such", 1.0, 2.0, "UTC", "SG", "F S D A M I"))
        assertEquals(Kind.FETCH_BROKEN, item.kind)
        assertTrue(item.details.any { "no registry entry" in it }, item.toString())
    }

    @Test
    fun `a table of an entry automatic follows is checked as that entry at the point`() {
        val plan = check.plan(table("sa-ummalqura", "makkah", "archive/t.txt", "sa.ummalqura", 21.426666, 39.831666, "Asia/Riyadh", "SA", "F+E S D A M I"))
        val row = plan as TableCheck.Plan.Row
        assertEquals("sa.ummalqura", row.manifest.rows.single().entry)
        assertEquals(null, row.manifest.rows.single().member)
        assertEquals(21.426666, row.manifest.rows.single().lat)
        assertTrue("Automatic follows sa.ummalqura" in row.why, row.why)
    }

    @Test
    fun `a member's table where automatic follows a cautious entry is checked as that member`() {
        val plan = check.plan(table("tr-diyanet-europe", "paris", "archive/t.txt", "tr.diyanet.europe", 48.85341, 2.3488, "Europe/Paris", "FR", "F+E S D A M I"))
        val row = plan as TableCheck.Plan.Row
        assertEquals("fr.cautious", row.manifest.rows.single().entry)
        assertEquals("tr.diyanet.europe", row.manifest.rows.single().member)
        assertTrue(row.text.endsWith("\ttr.diyanet.europe\t"), row.text)
    }

    @Test
    fun `a table automatic does not follow is checked as its own entry`() {
        // Inside the M25 Automatic follows London Unified; Diyanet's London table is still Diyanet's.
        val plan = check.plan(table("tr-diyanet-europe", "london", "archive/t.txt", "tr.diyanet.europe", 51.5074, -0.1278, "Europe/London", "GB", "F+E S D A M I"))
        val row = plan as TableCheck.Plan.Row
        assertEquals("tr.diyanet.europe", row.manifest.rows.single().entry)
        assertEquals(null, row.manifest.rows.single().member)
        assertTrue("not this table's" in row.why, row.why)
    }

    @Test
    fun `a unit named in the entry is checked at the unit's own point`() {
        val plan = check.plan(table("my-jakim", "WLY01", "archive/t.txt", "my.jakim/WLY01", 3.0, 101.0, "Asia/Kuala_Lumpur", "MY", "F S D A M I"))
        val row = plan as TableCheck.Plan.Row
        assertEquals("WLY01", row.manifest.rows.single().unit)
        assertEquals(null, row.manifest.rows.single().lat)
    }

    @Test
    fun `a table with no point and no fixed point is refused`() {
        val plan = check.plan(table("x", "k", "archive/t.txt", "tr.diyanet", null, null, "Europe/Istanbul", "TR", "F S D A M I"))
        assertTrue(plan is TableCheck.Plan.Refused, plan.toString())
    }

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
        if (listed != null) {
            val known = check.plan(table("mawaqit", listed, "archive/t.txt", null, 52.48, -1.89, "Europe/London", "GB", "F S D A M I", survey = "gb-cautious")) as TableCheck.Plan.Calendar
            assertTrue(known.survey.outliers.any { it.calendar == listed }, known.survey.outliers.joinToString { it.label })
            assertTrue("as the survey checks it" in known.why, known.why)
        }
    }

    @Test
    fun `a canadian calendar keeps its own zone`() {
        val plan = check.plan(table("mawaqit", "x", "archive/t.txt", null, 44.65, -63.6, "America/Halifax", "CA", "F S D A M I", survey = "ca-cautious")) as TableCheck.Plan.Calendar
        assertEquals(TimeZone.of("America/Halifax"), plan.survey.calendars.single().zone)
    }

    /**
     * A calendar written from the engine's own gb.cautious days at an invented point, every start
     * printed [startShift] minutes after the engine's and sunrise a minute after it: the engine is
     * never early when the shift is negative (the mosque's times before its own), early on every
     * day when it is positive.
     */
    private fun engineCalendar(startShift: Int): MonitorTable {
        val lat = 53.1
        val lon = -1.7
        val zone = TimeZone.of("Europe/London")
        val resolution = Registry.resolveEntry(Registry.byId("gb.cautious")!!, Place(lat, lon, zone.id, "GB"))
        val lines = mutableListOf<String>()
        var date = LocalDate(2026, 10, 10)
        repeat(10) {
            val day = Ends.withNextDay(DayPipeline.unended(resolution, date, zone), DayPipeline.unended(resolution, date.plus(1, DateTimeUnit.DAY), zone))
            fun hm(instant: Instant): String {
                val t = instant.toLocalDateTime(zone).time
                return "%02d:%02d".format(t.hour, t.minute)
            }
            val shift = startShift.minutes
            lines += "$date ${hm(day.fajr + shift)} ${hm(day.sunrise + 1.minutes)} ${hm(day.dhuhr + shift)} ${hm(day.asr + shift)} ${hm(day.maghrib + shift)} ${hm(day.isha + shift)}"
            date = date.plus(1, DateTimeUnit.DAY)
        }
        val path = "archive/tables/monitor/mawaqit/invented-mosque.txt"
        official.resolve(path).parentFile.mkdirs()
        official.resolve(path).writeText("# an invented calendar from the engine's own days\n" + lines.joinToString("\n") + "\n")
        return table("mawaqit", "invented-mosque", path, null, lat, lon, zone.id, "GB", "F S D A M I", survey = "gb-cautious")
    }

    @Test
    fun `a mosque calendar the engine is never early against is fine`() {
        val item = check.check(engineCalendar(-1))
        assertEquals(Kind.NEW_TABLE_FINE, item.kind, item.toString())
        assertTrue(item.details.any { "10 place-days" in it && "gb.cautious" in it }, item.toString())
        assertTrue(item.details.any { "calendars.tsv row" in it }, item.toString())
    }

    @Test
    fun `a mosque calendar printed after the engine's starts needs attention`() {
        val item = check.check(engineCalendar(+5))
        assertEquals(Kind.EARLY_OR_LATE, item.kind, item.toString())
        assertTrue(item.details.any { "not recorded: invented-mosque" in it && "fajr early by 5 min" in it }, item.toString())
    }
}
