package world.taqwa.timetables.gate

import world.taqwa.timetables.Json
import world.taqwa.timetables.TestPaths
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `checkStamps` (spec §5, ruling R86) needs no archive: [Gate.setUp] resolves every row's entry,
 * point and method from the manifest and the registry alone, so [CheckStamps.fingerprintsByEntry]
 * reaches the same fingerprints whether or not the roots it is given hold a table — the same open
 * MUIS fixture [GateFixtureTest] reads, here always with a deliberately missing archive.
 */
class CheckStampsTest {

    private val root: File = File(requireNotNull(javaClass.getResource("/gate-fixture")).toURI())
    private val roots = OfficialRoots(File("/nonexistent/archive"), root)
    private val header = GateManifest.HEADER.joinToString("\t")
    private val manifest = GateManifest.parse(
        "fixture.tsv",
        listOf(
            header,
            "open/SG-MUIS/muis-2026-02-a.txt\tsg.muis\t\t\tAsia/Singapore\tF+E S D A M I\tdaily\tstandard\tfit\tbefore Ramadan",
        ).joinToString("\n"),
    )
    private val coreHash = Stamps.coreHash(TestPaths.repoRoot)
    private val expectedHash = Stamps.engineHash(coreHash, CheckStamps.fingerprintsByEntry(manifest, roots).getValue("sg.muis"))

    /** A path that is never read: the tests below check `entries` on its own, and one dedicated
     * test covers the `ProofStamps.kt` comparison for real. */
    private val noProofFile = File("/nonexistent/ProofStamps.kt")

    private fun tempDir(): File = Files.createTempDirectory("check-stamps").toFile()

    private fun stampFile(dir: File, entryId: String, engineHash: String, broken: Int) {
        dir.resolve("$entryId.json").writeText(Json.pretty(mapOf("entry" to entryId, "engineHash" to engineHash, "broken" to broken)))
    }

    @Test
    fun `fingerprints match whether or not the roots hold an archive`() {
        val withoutArchive = CheckStamps.fingerprintsByEntry(manifest, OfficialRoots(File("/nonexistent/archive"), root))
        val withOpenOnly = CheckStamps.fingerprintsByEntry(manifest, OfficialRoots.of(root))
        assertEquals(withoutArchive, withOpenOnly)
        assertTrue(withoutArchive.getValue("sg.muis").isNotEmpty())
    }

    @Test
    fun `a fresh green stamp passes`() {
        val dir = tempDir()
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 0)
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile)
            assertTrue(result.entries.isEmpty(), result.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a stale engineHash fails and names the entry`() {
        val dir = tempDir()
        try {
            stampFile(dir, "sg.muis", "not-the-current-hash", broken = 0)
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile)
            assertEquals(listOf("sg.muis"), result.entries.map { it.entryId })
            assertTrue(result.entries.single().messages.any { "stale" in it }, result.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a red stamp fails and names the entry`() {
        val dir = tempDir()
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 3)
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile)
            assertEquals(listOf("sg.muis"), result.entries.map { it.entryId })
            assertTrue(result.entries.single().messages.any { "red" in it && "3" in it }, result.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a missing stamp fails and names the entry`() {
        val dir = tempDir()
        try {
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile)
            assertEquals(listOf("sg.muis"), result.entries.map { it.entryId })
            assertTrue(result.entries.single().messages.any { "no stamp" in it }, result.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a stale ProofStamps kt fails on its own even where every stamp is fresh`() {
        val dir = tempDir()
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 0)
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile)
            assertTrue(result.entries.isEmpty(), result.report())
            assertTrue(result.proofStampsStale)
            assertFalse(result.ok)
            assertTrue("ProofStamps.kt" in result.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a fresh ProofStamps kt matching the generator passes`() {
        val dir = tempDir()
        val proofFile = File.createTempFile("ProofStamps", ".kt")
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 0)
            proofFile.writeText(ProofStampsGenerator.generate(dir))
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, proofFile)
            assertTrue(result.ok, result.report())
        } finally {
            dir.deleteRecursively()
            proofFile.delete()
        }
    }

    @Test
    fun `an unresolvable manifest row fails loudly instead of an entry with short fingerprints`() {
        val bad = GateManifest.parse(
            "bad.tsv",
            listOf(header, "open/x.txt\tno.such.entry\t\t\tAsia/Singapore\tF S D A M I\tdaily\tstandard\tfit\t").joinToString("\n"),
        )
        val error = kotlin.runCatching { CheckStamps.fingerprintsByEntry(bad, roots) }.exceptionOrNull()
        assertTrue(error is GateError, "$error")
    }
}
