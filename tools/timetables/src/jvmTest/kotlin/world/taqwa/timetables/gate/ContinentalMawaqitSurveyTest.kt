package world.taqwa.timetables.gate

import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * France, Belgium, the Netherlands, Germany and Canada outside its gated cities against the Mawaqit
 * calendars research-mawaqit spot-checked (ruling R87), each at its own mosque's point: no start
 * before a calendar's and no sunrise or end of eating after it, but for the faults each survey's
 * `faults.tsv` leaves out and the outliers its `outliers.tsv` records ([Survey]). The calendars are
 * restricted and live in the local archive only: without it (CI) each survey is skipped and says so,
 * like [NeverEarlyGateTest] and [UkMawaqitSurveyTest].
 */
class ContinentalMawaqitSurveyTest {

    private val officialDir = TestPaths.repoRoot.resolve("tools/timetables/official")

    /** How many calendars each survey lists; the surveys themselves come from [Surveys.all], the monitor's list too. */
    private val calendars = mapOf("fr-cautious" to 12, "be-cautious" to 12, "nl-cautious" to 12, "de-cautious" to 12, "ca-cautious" to 6)

    private fun country(folder: String): SurveyFolder = requireNotNull(Surveys.named(folder)) { "$folder is not in Surveys.all" }

    private val countries = calendars.keys.map(::country)

    private fun dir(c: SurveyFolder): File = officialDir.resolve("survey/${c.folder}")

    private fun survey(c: SurveyFolder) = c.load(officialDir)

    private fun check(c: SurveyFolder) {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val result = survey(c).evaluate(OfficialRoots(official, officialDir))
        println("${c.entryId}: " + result.report())
        if (!result.held) return
        val broken = result.broken()
        if (broken.isNotEmpty()) fail("${c.entryId}: ${broken.size} broken:\n" + broken.joinToString("\n"))
    }

    @Test
    fun `france is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(country("fr-cautious"))

    @Test
    fun `belgium is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(country("be-cautious"))

    @Test
    fun `the netherlands is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(country("nl-cautious"))

    @Test
    fun `germany is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(country("de-cautious"))

    @Test
    fun `canada elsewhere is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(country("ca-cautious"))

    @Test
    fun `every survey folder on disk is in the one list the tests and the monitor share`() {
        val onDisk = officialDir.resolve("survey").listFiles { f -> f.isDirectory && f.resolve("calendars.tsv").isFile }!!
            .map { it.name }.toSet()
        assertEquals(onDisk, Surveys.all.map { it.folder }.toSet(), "a survey folder missing from Surveys.all would never run weekly")
        assertEquals(onDisk - "gb-cautious", calendars.keys, "each continental survey names its calendar count here")
    }

    @Test
    fun `each survey lists its calendars and quotes no printed time`() {
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        for (c in countries) {
            val survey = survey(c)
            assertEquals(calendars.getValue(c.folder), survey.calendars.size, c.entryId)
            val out = survey.calendars.filter { !it.used }
            assertTrue(out.all { it.use.length > 20 }, "${c.entryId}: a calendar left out needs its reason")
            // No printed time in the committed files (ruling R69): dates, columns, figures and reasons only.
            for (name in listOf("calendars.tsv", "faults.tsv", "outliers.tsv")) {
                val hit = dir(c).resolve(name).readLines().firstOrNull { time.containsMatchIn(it) }
                assertEquals(null, hit, "${c.folder}/$name quotes a time")
            }
        }
    }
}
