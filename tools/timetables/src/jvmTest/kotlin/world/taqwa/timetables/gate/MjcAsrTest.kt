package world.taqwa.timetables.gate

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.day.Ends
import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Ruling R161: the MJC's November 2026, as the weekly monitor pinned it on 1 Nov 2026, read from the archive
 * (`-Pofficial`) like the gate's own rows. za.mjc's Standard Asr is never before the MJC's printed Asr on any of
 * its days. Without the archive (CI) nothing is read and the test passes. Failures name the date only (R69).
 */
class MjcAsrTest {

    @Test
    fun `za mjc's standard asr is never before the MJC's printed asr on any day of its November 2026`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        val row = GateManifest.parse("MjcAsrTest", NOVEMBER).rows.single()
        val problems = mutableListOf<String>()
        val setup = Gate.setUp(row, roots, null, problems) ?: fail(problems.joinToString("\n"))
        val table = setup.table ?: return
        val asr = row.columns.indexOfFirst { Event.ASR_STANDARD in it }
        assertTrue(table.days.size >= 30, "the MJC's November 2026 holds ${table.days.size} days")

        val early = table.days.filter { official ->
            val minutes = official.minutes[asr] ?: fail("${official.date}: no printed Asr")
            val printed = Gate.officialInstant(official.date, minutes, Event.ASR_STANDARD, setup.clock, setup.zone)
            val day = Ends.withNextDay(
                DayPipeline.unended(setup.resolution, official.date, setup.zone),
                DayPipeline.unended(setup.resolution, official.date.plus(1, DateTimeUnit.DAY), setup.zone),
            )
            val shown = Gate.shown(day, Event.ASR_STANDARD, setup.entry.school) ?: fail("${official.date}: no Asr shown")
            shown < printed
        }.map { it.date }
        if (early.isNotEmpty()) fail("za.mjc's Standard Asr is before the MJC's printed Asr on ${early.joinToString()}")
    }

    private companion object {
        /** The row `prove` adds for the capture: za.mjc at its own unit, the MJC's columns as its other months. */
        val NOVEMBER = listOf(
            "path\tentry\tlat\tlon\tzone\tcolumns\tformat\tschool\tsplit\tnote\tmember",
            "archive/tables/pinned/za-mjc/2026-11-01/cape-town-2026-11.txt\tza.mjc/za.mjc\t\t\tAfrica/Johannesburg\t" +
                "F+E S D A M I\tdaily\tstandard\ttest\tMJC November 2026",
        ).joinToString("\n")
    }
}
