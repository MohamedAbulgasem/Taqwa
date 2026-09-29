package world.taqwa.timetables.gate

import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The UK outside London (`gb.cautious`) against the 71 Mawaqit calendars research-uk surveyed (ruling
 * R75), each at its own mosque's point: no start before a calendar's and no sunrise or end of eating
 * after it, but for the faults `official/survey/gb-cautious/faults.tsv` leaves out and the outliers
 * `outliers.tsv` records ([Survey]). The calendars are restricted and live in the local archive only:
 * without it (CI) the survey is skipped and says so, like [NeverEarlyGateTest].
 */
class UkMawaqitSurveyTest {

    private val officialDir = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val dir = officialDir.resolve("survey/gb-cautious")

    /** The survey as the monitor runs it too ([Surveys]): one list, so a survey added here runs weekly. */
    private fun survey() = requireNotNull(Surveys.named("gb-cautious")) { "gb-cautious is not in Surveys.all" }.load(officialDir)

    @Test
    fun `gb cautious is never early and never late against the uk mawaqit calendars but for its recorded outliers`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val result = survey().evaluate(OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official")))
        println(result.report())
        if (!result.held) return
        val broken = result.broken()
        if (broken.isNotEmpty()) fail("${broken.size} broken:\n" + broken.joinToString("\n"))
    }

    @Test
    fun `the survey lists 71 calendars and leaves 9 out whole each with its reason`() {
        val survey = survey()
        assertEquals(71, survey.calendars.size)
        val out = survey.calendars.filter { !it.used }
        assertEquals(9, out.size, out.joinToString { it.id })
        assertTrue(out.all { it.use.length > 20 }, "a calendar left out needs its reason")
        // No printed time in the committed files (ruling R69): dates, columns, figures and reasons only.
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        for (name in listOf("calendars.tsv", "faults.tsv", "outliers.tsv")) {
            val hit = dir.resolve(name).readLines().firstOrNull { time.containsMatchIn(it) }
            assertEquals(null, hit, "$name quotes a time")
        }
    }
}
