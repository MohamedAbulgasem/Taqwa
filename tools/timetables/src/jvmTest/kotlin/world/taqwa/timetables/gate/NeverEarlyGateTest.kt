package world.taqwa.timetables.gate

import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The promise, on every official day held locally (spec §5): for every gate row whose file exists
 * under the official root, whatever the entry's class, no start before the official minute and no
 * sunrise, end of eating or imsak after it; lateness within the entry's limit (A 1, B 2, C 1 after
 * the latest member, D 3) or the `LateLimit` its unit or entry records (ruling R37); and every day in order before
 * any repair.
 *
 * The root is `-Pofficial`, `TAQWA_OFFICIAL` or this checkout's own `tools/timetables/official`
 * with its git-ignored `archive/` (see `build.gradle.kts`, which stops the run where that archive is
 * missing off CI); `open/` tables come from this checkout. Without an archive (CI) every row is
 * skipped and the test passes with "0 rows checked"; with it, a table not held or one that yields
 * no day fails. `-PgateGroup` and
 * `-Pentry` narrow it to one group's rows, and fail when they match nothing.
 */
class NeverEarlyGateTest {

    @Test
    fun `every official day held locally is never early and within its limit`() {
        val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))
        val manifest = GateManifest.load(TestPaths.repoRoot.resolve("tools/timetables/official/gate"))
            .only(groups = listed("taqwa.gateGroups"), entries = listed("taqwa.gateEntries"))
        assertTrue(manifest.rows.isNotEmpty(), "no gate rows")

        val result = Gate(OfficialRoots(official, TestPaths.repoRoot.resolve("tools/timetables/official"))).evaluate(manifest)
        println(result.report())
        val broken = result.violations()
        if (broken.isNotEmpty()) fail("${broken.size} broken:\n" + broken.joinToString("\n"))
    }

    private fun listed(property: String): Set<String> =
        System.getProperty(property).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
