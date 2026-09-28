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

    private class Country(val folder: String, val entryId: String, val zoneId: String, val countryCode: String, val calendars: Int)

    private val countries = listOf(
        Country("fr-cautious", "fr.cautious", "Europe/Paris", "FR", 12),
        Country("be-cautious", "be.cautious", "Europe/Brussels", "BE", 12),
        Country("nl-cautious", "nl.cautious", "Europe/Amsterdam", "NL", 12),
        Country("de-cautious", "de.cautious", "Europe/Berlin", "DE", 12),
        // Its calendars carry their own zones (three across the country).
        Country("ca-cautious", "ca.cautious", "America/Winnipeg", "CA", 6),
    )

    private fun dir(c: Country): File = TestPaths.repoRoot.resolve("tools/timetables/official/survey/${c.folder}")

    private fun survey(c: Country) = Survey.load(dir(c), entryId = c.entryId, zoneId = c.zoneId, countryCode = c.countryCode)

    private fun check(c: Country) {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val result = survey(c).evaluate(OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official")))
        println("${c.entryId}: " + result.report())
        if (!result.held) return
        val broken = result.broken()
        if (broken.isNotEmpty()) fail("${c.entryId}: ${broken.size} broken:\n" + broken.joinToString("\n"))
    }

    @Test
    fun `france is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(countries[0])

    @Test
    fun `belgium is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(countries[1])

    @Test
    fun `the netherlands is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(countries[2])

    @Test
    fun `germany is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(countries[3])

    @Test
    fun `canada elsewhere is never early and never late against its mawaqit calendars but for its recorded outliers`() = check(countries[4])

    @Test
    fun `each survey lists its calendars and quotes no printed time`() {
        val time = Regex("""\b\d{1,2}:\d{2}\b""")
        for (c in countries) {
            val survey = survey(c)
            assertEquals(c.calendars, survey.calendars.size, c.entryId)
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
