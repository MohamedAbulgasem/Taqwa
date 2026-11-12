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

    /**
     * A stamp as [Stamps.stamp] writes it, with [checked] as the runs of each of [events] too (ruling
     * R148: by default every event a Ramadan turns on), and each unit of [units] likewise with [unitEvents].
     */
    private fun stampFile(
        dir: File,
        entryId: String,
        engineHash: String,
        broken: Int,
        entryClass: String = "B",
        checked: List<String> = emptyList(),
        units: Map<String, List<String>> = emptyMap(),
        events: List<String> = Ramadan.EVENTS,
        unitEvents: Map<String, List<String>> = emptyMap(),
    ) {
        val stamp = linkedMapOf<String, Any?>(
            "entry" to entryId, "class" to entryClass, "engineHash" to engineHash, "broken" to broken, "checked" to checked,
            "checkedByEvent" to events.associateWith { checked },
            "units" to units.takeIf { it.isNotEmpty() }?.mapValues { (id, runs) ->
                mapOf("name" to id, "checked" to runs, "checkedByEvent" to (unitEvents[id] ?: Ramadan.EVENTS).associateWith { runs })
            },
        )
        dir.resolve("$entryId.json").writeText(Json.pretty(stamp.filterValues { it != null }))
    }

    private val ramadan1448 = Ramadan.of(1448)

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
    fun `with a ramadan an a or b stamp whose checked days do not cover it fails and names the entry`() {
        val dir = tempDir()
        try {
            // Muscat's shape (ruling R145): the envelope spans the month, no run holds a day of it.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2026-12-31", "2031-03-01..2031-03-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile).entries.isEmpty())
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertEquals(listOf("sg.muis"), result.entries.map { it.entryId })
            val message = result.entries.single().messages.single()
            assertTrue("Ramadan 1448 (2027-02-08..2027-03-09, guarded 2027-02-07..2027-03-10) is not proven: 0 of 32 days checked; checked through 2026-12-31 before it" in message, message)
            // Covered: green.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-03-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448).entries.isEmpty())
            // One day of it unchecked: not covered.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-03-08"))
            val short = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue(short.entries.single().messages.single().contains("30 of 32 days checked"), short.report())
            // The tabular month alone is not enough (ruling R146): the day either side of it is guarded too, the first
            // tarawih's eve and the last fast of a country that begins a day late, as the engine's own Ramadan runs.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2027-02-08..2027-03-09"))
            val month = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue(month.entries.single().messages.single().contains("30 of 32 days checked; no day checked before it"), month.report())
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2027-02-07..2027-03-10"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448).entries.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `with a ramadan a unit short of it fails while the entry's own runs cover it`() {
        val dir = tempDir()
        try {
            stampFile(
                dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-12-31"),
                units = mapOf("east" to listOf("2024-01-01..2027-12-31"), "west" to listOf("2024-01-01..2026-12-31")),
            )
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            val message = result.entries.single().messages.single()
            assertTrue("1 of 2 units short (west 0 of 32 days); the short units checked through 2026-12-31 before it" in message, message)
            assertTrue("east" !in message.substringBefore(";"), message)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `with a ramadan a unit whose fajr and maghrib cover it but whose end of eating was never checked is short`() {
        val dir = tempDir()
        try {
            // Pretoria's shape (ruling R148): Darul Ihsan's tables print no Suhoor column, so the unit's days are all
            // checked while its end of eating is checked on none of them.
            stampFile(
                dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2026-01-01..2027-12-31"),
                units = mapOf("pretoria" to listOf("2026-01-01..2027-12-31"), "durban" to listOf("2025-01-01..2029-12-31")),
                unitEvents = mapOf("pretoria" to listOf("fajr", "maghrib")),
            )
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            val message = result.entries.single().messages.single()
            assertTrue("1 of 2 units short (pretoria 0 of 32 days (endOfEating 0)); the short units checked through 2027-02-06 before it" in message, message)
            assertTrue("R148" in message, message)
            // An end of eating checked on all but the last day: the unit is short by that day, and the line says which event.
            stampFile(
                dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2026-01-01..2027-12-31"),
                units = mapOf("pretoria" to listOf("2026-01-01..2027-12-31")),
            )
            @Suppress("UNCHECKED_CAST")
            val stamp = Json.parse(dir.resolve("sg.muis.json").readText()) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val pretoria =((stamp["units"] as Map<String, Any?>)["pretoria"] as Map<String, Any?>).toMutableMap()
            pretoria["checkedByEvent"] = mapOf("fajr" to listOf("2026-01-01..2027-12-31"), "endOfEating" to listOf("2026-01-01..2027-03-08"), "maghrib" to listOf("2026-01-01..2027-12-31"))
            dir.resolve("sg.muis.json").writeText(Json.pretty(stamp + ("units" to mapOf("pretoria" to pretoria))))
            val short = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue("1 of 1 unit short (pretoria 30 of 32 days (endOfEating 30))" in short.entries.single().messages.single(), short.report())
            // A stamp from before the per-event runs: its runs say nothing of any event, so it covers no day.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2026-01-01..2027-12-31"), events = emptyList())
            val older = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue("0 of 32 days checked; checked through 2027-02-06 before it" in older.entries.single().messages.single(), older.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `with a ramadan a demoted stamp is not held to it`() {
        val dir = tempDir()
        try {
            for (demoted in listOf("C", "D (authority)", "D (none)")) {
                stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = demoted, checked = listOf("2024-01-01..2026-12-31"))
                val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
                assertTrue(result.entries.isEmpty(), "$demoted: ${result.report()}")
            }
            // A split class with A or B in it is held.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = "A/D (authority)", checked = listOf("2024-01-01..2026-12-31"))
            assertEquals(listOf("sg.muis"), CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448).entries.map { it.entryId })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `without a ramadan the check hints inside the eight weeks before one and through it`() {
        assertEquals(null, CheckStamps.ramadanHint(kotlinx.datetime.LocalDate(2026, 12, 13)))
        val hint = CheckStamps.ramadanHint(kotlinx.datetime.LocalDate(2026, 12, 14))
        assertTrue(hint != null && "-Pramadan=1448" in hint && "TAQWA_RAMADAN=1448" in hint, hint)
        // Through the guard's last day, 10 March (ruling R146).
        assertTrue(CheckStamps.ramadanHint(kotlinx.datetime.LocalDate(2027, 3, 10)) != null)
        assertEquals(null, CheckStamps.ramadanHint(kotlinx.datetime.LocalDate(2027, 3, 11)))
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

    @Test
    fun `a ramadan whose guarded days ended before today is refused as a typo`() {
        val today = kotlinx.datetime.LocalDate(2026, 11, 12)
        assertEquals(null, CheckStamps.pastRamadan(Ramadan.of(1448), today))
        val typo = CheckStamps.pastRamadan(Ramadan.of(1447), today)
        assertTrue(typo != null && "-Pramadan=1448" in typo, typo)
        // Through the guard's last day the month is still the one being built for.
        assertEquals(null, CheckStamps.pastRamadan(Ramadan.of(1448), kotlinx.datetime.LocalDate(2027, 3, 10)))
        assertTrue(CheckStamps.pastRamadan(Ramadan.of(1448), kotlinx.datetime.LocalDate(2027, 3, 11)) != null)
    }
}
