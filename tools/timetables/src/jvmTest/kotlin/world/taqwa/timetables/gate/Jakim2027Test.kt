package world.taqwa.timetables.gate

import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The monitor's 2027 e-solat tables for Kuala Lumpur, George Town and Kangar, read from their pinned
 * copies of 11 Nov 2026 (`archive/tables/pinned/my-jakim/2026-11-11/`, byte-identical to the live
 * files that day), never the monitor's live files, which JAKIM may republish under the test: with the
 * zones refitted on 2025–2027 (4 Nov 2026), no day of 2027 shows a start before e-solat's or an end
 * after it, and none is later than its limit. Without the archive (CI) nothing is read and the test
 * passes and says so; with the archive held, a missing pinned copy fails. Failures name dates and
 * differences only (R69). Labuan's 2027 table is left out: e-solat's own data prints its Maghrib 12
 * hours off on six days of May and its Asr an hour off on 10 January.
 */
class Jakim2027Test {

    @Test
    fun `the refitted zones hold every day of their 2027 tables`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val roots = OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))
        if (!official.resolve("archive").isDirectory) {
            println("Jakim 2027 skipped: no archive under $official")
            return
        }
        for (zone in ZONES) {
            val pinned = official.resolve("$PINNED/$zone-2027.txt")
            assertTrue(pinned.isFile, "the archive is held but $PINNED/$zone-2027.txt is missing")
        }
        val result = Gate(roots).evaluate(GateManifest.parse("Jakim2027Test", ROWS))
        val stats = result.entries.getValue("my.jakim")
        assertTrue(stats.broken == 0, result.report())
    }

    private companion object {
        const val PINNED = "archive/tables/pinned/my-jakim/2026-11-11"
        val ZONES = listOf("WLY01", "PNG01", "PLS01")
        val ROWS = (
            listOf("path\tentry\tlat\tlon\tzone\tcolumns\tformat\tschool\tsplit\tnote\tmember") +
                ZONES.map { zone ->
                    "$PINNED/$zone-2027.txt\tmy.jakim/$zone\t\t\tAsia/Kuala_Lumpur\t" +
                        "F+E S D A M I\tdaily\tstandard\ttest\t$zone 2027"
                }
            ).joinToString("\n")
    }
}
