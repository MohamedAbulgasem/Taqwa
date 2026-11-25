package world.taqwa.timetables.gate

import world.taqwa.timetables.Json
import world.taqwa.timetables.TestPaths
import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.AuthorityUnit
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.TimedEvent
import world.taqwa.app.prayer.engine.registry.UnitSet
import world.taqwa.app.prayer.engine.registry.data.ReachLimits
import world.taqwa.timetables.units.ReachAreas
import world.taqwa.timetables.units.ReachRows
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
     * [heldOut] are the runs a held-out cell of each event was compared on (ruling R246): the same as
     * [checked] unless given, so a stamp here is held out wherever it is checked.
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
        demoted: List<Map<String, String>> = emptyList(),
        heldOut: List<String> = checked,
        heldOutEvents: List<String> = events,
        unitHeldOut: Map<String, List<String>> = units,
        showsImsak: Boolean = false,
    ) {
        val stamp = linkedMapOf<String, Any?>(
            "entry" to entryId, "class" to entryClass, "engineHash" to engineHash, "broken" to broken, "checked" to checked,
            "checkedByEvent" to events.associateWith { checked },
            "showsImsak" to showsImsak.takeIf { it },
            "heldOut" to mapOf("checkedByEvent" to heldOutEvents.associateWith { heldOut }),
            "demoted" to demoted.takeIf { it.isNotEmpty() },
            "units" to units.takeIf { it.isNotEmpty() }?.mapValues { (id, runs) ->
                val read = unitEvents[id] ?: Ramadan.EVENTS
                mapOf(
                    "name" to id, "checked" to runs, "checkedByEvent" to read.associateWith { runs },
                    "heldOutByEvent" to read.associateWith { unitHeldOut[id].orEmpty() },
                ) + if (showsImsak) mapOf("showsImsak" to true) else emptyMap()
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
    fun `a checked timetable the monitor demoted stops a release, and only the monitor's own run lets it through`() {
        // Ruling R185: prove wrote the @demoted line; the stamp carries it while the registry still says class B.
        val dir = tempDir()
        try {
            val line = listOf(mapOf("unit" to "east", "since" to "2027-01-12", "reason" to "fajr early on 3 place-days, by up to 2 min"))
            stampFile(dir, "sg.muis", expectedHash, broken = 0, demoted = line)
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile)
            assertEquals(listOf("sg.muis"), result.entries.map { it.entryId })
            val message = result.entries.single().messages.single()
            assertTrue("the monitor demoted sg.muis/east since 2027-01-12 (fajr early on 3 place-days, by up to 2 min)" in message, message)
            assertTrue("still calls it a checked timetable (class B)" in message && "R185" in message, message)
            // The monitor's own run publishes the record: the demotion alone does not stop it.
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, allowDemoted = true).entries.isEmpty())
            // With the registry's class lowered (the stamp then says so) the line may stand.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = "C", demoted = line)
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile).entries.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `with a ramadan an entry the cover says is covered under O4 passes and is listed, and one it names fails with the reasons`() {
        val dir = tempDir()
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2026-12-31"))
            var asked: Triple<String, Ramadan, Int>? = null
            val yes = RamadanCover { stamp, ramadan, gaps ->
                asked = Triple(stamp.entryId, ramadan, gaps.single().uncheckedByEvent.getValue("fajr").size)
                Cover.Covered("the rule's own words")
            }
            val covered = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448, cover = yes)
            assertTrue(covered.entries.isEmpty(), covered.report())
            assertEquals(listOf("sg.muis" to "the rule's own words"), covered.covered)
            assertEquals(Triple("sg.muis", ramadan1448, 32), asked)
            // Named: the old message, and why the rule does not cover it.
            val no = RamadanCover { _, _, _ -> Cover.Named(listOf("(a) one reason", "(b) another")) }
            val named = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448, cover = no)
            val message = named.entries.single().messages.single()
            assertTrue("is not proven: 0 of 32 days checked; checked through 2026-12-31 before it; not covered under O4 (ruling R185): (a) one reason; (b) another — fetch" in message, message)
            assertTrue(named.covered.isEmpty())
            // An entry with no gap is never asked; nor is one that is no checked timetable; nor any without a Ramadan.
            val never = RamadanCover { _, _, _ -> error("not asked") }
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-03-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448, cover = never).covered.isEmpty())
            stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = "C", checked = listOf("2024-01-01..2026-12-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448, cover = never).entries.isEmpty())
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2026-12-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, cover = never).entries.isEmpty())
            // Covered under O4 does not excuse a standing demotion: the release still stops on it.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2026-12-31"), demoted = listOf(mapOf("since" to "2027-01-12", "reason" to "r")))
            val demoted = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448, cover = yes)
            assertTrue(demoted.entries.single().messages.single().startsWith("the monitor demoted sg.muis since 2027-01-12 (r)"), demoted.report())
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

    /** Ruling R246: the guard counts held-out rows only; a day in the gate by a fitted row alone is named apart. */
    @Test
    fun `with a ramadan an entry whose days are checked in fitted rows only is named apart and refused`() {
        val dir = tempDir()
        try {
            // Every guarded day checked, none of them held out: the method was fitted to the very table.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-12-31"), heldOut = listOf("2024-01-01..2026-12-31"))
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            val message = result.entries.single().messages.single()
            assertTrue("is checked and not held out: 0 of 32 days held out" in message, message)
            assertTrue("R246" in message && "not proven" !in message, message)
            // The O4 cover is never asked for such an entry: its days are published and in the gate already.
            val never = RamadanCover { _, _, _ -> error("not asked") }
            assertEquals(1, CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448, cover = never).entries.size)
            // One event fitted and the others held out (a margin widened to the held-out table's Fajr alone): still named, by event.
            stampFile(
                dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-12-31"),
                heldOut = listOf("2024-01-01..2027-12-31"), heldOutEvents = listOf("endOfEating", "maghrib"),
            )
            val fajr = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue("0 of 32 days held out (fajr 0)" in fajr.entries.single().messages.single(), fajr.report())
            // By unit: the one whose 2027 table is a fit row is named, the other is not.
            stampFile(
                dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-12-31"),
                units = mapOf("east" to listOf("2024-01-01..2027-12-31"), "west" to listOf("2024-01-01..2027-12-31")),
                unitHeldOut = mapOf("east" to listOf("2027-01-01..2027-12-31"), "west" to listOf("2024-01-01..2026-12-31")),
            )
            val unit = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue("1 of 2 units (west 0 of 32 days held out)" in unit.entries.single().messages.single(), unit.report())
            // A unit short of the month is short first, and is not also called fitted.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-02-20"), heldOut = emptyList())
            val short = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue("is not proven: 14 of 32 days checked" in short.entries.single().messages.single(), short.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Ruling R246: the imsak is guarded where the unit shows one and a table of it prints one; named alone where none does. */
    @Test
    fun `with a ramadan an imsak the tables print is guarded and one no table prints is a notice`() {
        val dir = tempDir()
        try {
            val all = listOf("2024-01-01..2027-12-31")
            // Shown and printed, and checked on every guarded day: green.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = all, events = Ramadan.EVENTS + "imsak", showsImsak = true)
            val green = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue(green.entries.isEmpty() && green.imsakUnheld.isEmpty(), green.report())
            // Shown and printed, the imsak's own runs ending before the month: short, and the line says which event.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = all, events = Ramadan.EVENTS + "imsak", showsImsak = true)
            @Suppress("UNCHECKED_CAST")
            val stamp = Json.parse(dir.resolve("sg.muis.json").readText()) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val byEvent = (stamp["checkedByEvent"] as Map<String, Any?>) + ("imsak" to listOf("2024-01-01..2026-12-31"))
            dir.resolve("sg.muis.json").writeText(Json.pretty(stamp + ("checkedByEvent" to byEvent)))
            val short = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue("0 of 32 days (imsak 0) checked" in short.entries.single().messages.single(), short.report())
            // Shown, and no table prints one: nothing is refused, the unit is named in a notice.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = all, showsImsak = true)
            val unheld = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue(unheld.entries.isEmpty(), unheld.report())
            assertEquals(listOf("sg.muis"), unheld.imsakUnheld)
            assertTrue(unheld.notices().single().let { "show an imsak that no table held for them prints" in it && "sg.muis" in it }, unheld.notices().toString())
            // Not shown: the imsak is no event of the month here, printed or not.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = all)
            val plain = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448)
            assertTrue(plain.entries.isEmpty() && plain.notices().isEmpty(), plain.report())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Ruling R246: a Published-method entry short of the month is named on every run and refuses none. */
    @Test
    fun `a published-method entry short of the ramadan is named in a notice and never refused`() {
        val dir = tempDir()
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = "D (authority)", checked = listOf("2024-01-01..2026-12-31"))
            for (result in listOf(
                CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448),
                CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, coming = ramadan1448),
            )) {
                assertTrue(result.entries.isEmpty(), result.report())
                assertEquals(listOf("sg.muis"), result.publishedMethod.map { it.first })
                val notice = result.notices().single()
                assertTrue("1 Published-method entry (class D (authority))" in notice && "sg.muis: 0 of 32 days checked" in notice, notice)
                assertTrue("no build is refused for it" in notice, notice)
            }
            // Taqwa's own calculation claims neither a table nor a published method: not read at all.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = "D (none)", checked = listOf("2024-01-01..2026-12-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, coming = ramadan1448).notices().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Ruling R273 (the second trust audit's G30): a cautious entry short of the month is named, with the flag or without, never in silence. */
    @Test
    fun `a cautious entry short of the ramadan is named in a notice and not refused`() {
        val dir = tempDir()
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = "C", checked = listOf("2024-01-01..2026-12-31"))
            for (result in listOf(
                CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448),
                CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, coming = ramadan1448),
            )) {
                assertEquals(CheckStamps.CAUTIOUS_REFUSES, result.entries.isNotEmpty(), result.report())
                assertEquals(listOf("sg.muis"), result.cautious.map { it.first })
                val notice = result.notices().single()
                assertTrue("1 Cautious-times entry (class C)" in notice && "sg.muis: 0 of 32 days checked" in notice, notice)
                assertTrue(result.publishedMethod.isEmpty() && result.notProven.isEmpty())
            }
            // Every guarded day held out at every member: nothing to say.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, entryClass = "C", checked = listOf("2024-01-01..2027-12-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, coming = ramadan1448).cautious.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Ruling R246: green never hides it. A run without the flag reads the coming Ramadan, says who is short, and passes. */
    @Test
    fun `without a ramadan the coming one is read and the entries not yet proven are named without failing`() {
        val dir = tempDir()
        val proofFile = File.createTempFile("ProofStamps", ".kt")
        try {
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2026-12-31"))
            proofFile.writeText(ProofStampsGenerator.generate(dir))
            val result = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, proofFile, coming = ramadan1448)
            assertTrue(result.ok, result.report())
            assertEquals(listOf("sg.muis"), result.notProven.map { it.first })
            val notice = result.notices().single()
            assertTrue(notice.startsWith("checkStamps: NOT YET PROVEN for Ramadan 1448"), notice)
            assertTrue("1 checked timetable (class A or B)" in notice && "sg.muis: 0 of 32 days checked" in notice, notice)
            assertTrue("does not fail" in notice && "-Pramadan=1448" in notice, notice)
            // Fitted rows only: named the same way, in its own words.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-12-31"), heldOut = listOf("2024-01-01..2026-12-31"))
            proofFile.writeText(ProofStampsGenerator.generate(dir))
            val fitted = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, proofFile, coming = ramadan1448)
            assertTrue(fitted.ok, fitted.report())
            assertTrue("sg.muis: checked in fitted rows only at 0 of 32 days held out" in fitted.notices().single(), fitted.notices().toString())
            // Proven: nothing to say. And with neither a Ramadan nor a coming one, nothing is read.
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2027-12-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, coming = ramadan1448).notices().isEmpty())
            stampFile(dir, "sg.muis", expectedHash, broken = 0, checked = listOf("2024-01-01..2026-12-31"))
            assertTrue(CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile).notices().isEmpty())
            // With the flag the same entry is the run's own problem, and is not listed a second time.
            val flagged = CheckStamps.check(TestPaths.repoRoot, dir, manifest, roots, noProofFile, ramadan = ramadan1448, coming = ramadan1448)
            assertEquals(1, flagged.entries.size)
            assertTrue(flagged.notProven.isEmpty() && flagged.notices().isEmpty(), flagged.notices().toString())
        } finally {
            dir.deleteRecursively()
            proofFile.delete()
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
    fun `the cut names each checked entry whose units stop being shown as checked within the window`() {
        // Ruling R278: invented stamps, so the rule is held and never a live figure. A checked entry (ma.habous) with one
        // unit whose run ends inside the window, one beyond it and one no run holds today; a class D entry is never named.
        val today = kotlinx.datetime.LocalDate(2026, 11, 15)
        fun stamp(id: String, units: Map<String, List<String>>) = world.taqwa.app.prayer.engine.registry.data.ProofStamp(
            entryId = id, places = units.size, placeDays = 1, ramadanDays = 0, lastChecked = null, worstLateMinutes = emptyMap(),
            worstLateByUnit = units.mapValues { emptyMap() }, lateLimits = emptyList(), checked = emptyList(), checkedByUnit = units,
        )
        val lines = CheckStamps.labelEnds(
            today,
            stamps = listOf(
                stamp("ma.habous", mapOf("soon" to listOf("2026-10-01..2026-11-30"), "later" to listOf("2026-10-01..2027-12-31"), "gone" to listOf("2026-09-01..2026-10-31"))),
                stamp("tj.default", mapOf("any" to listOf("2026-11-01..2026-11-30"))),
            ),
        )
        assertEquals(1, lines.size, lines.toString())
        val line = lines.single()
        assertTrue("ma.habous" in line && "1 of 3 units not checked today" in line && "1 of 3 units checked through 2026-11-30" in line, line)
        assertTrue("2027-12-31" !in line, line)
    }

    @Test
    fun `a release is refused while a reach proof is owed and any other run says it and passes`() {
        // The rule, never the standing list: a unit left out of its entry's reach rows and a pending entry are each owed.
        val owed = ReachRows.outstanding(
            specs = listOf(ReachRows.Spec("aa.one", "aa-one", "AA"), ReachRows.Spec("bb.two", "bb-two", "BB", leftOut = setOf("zed", "alpha"))),
            pending = listOf(ReachRows.Spec("cc.three", "cc-three", "CC")),
            unspecified = emptyList(),
            overCeiling = emptyList(),
            unswept = emptyList(),
            beyondArea = emptyList(),
        )
        assertEquals(2, owed.size, owed.toString())
        assertTrue(owed[0].startsWith("bb.two:") && "alpha, zed" in owed[0], owed[0])
        assertTrue(owed[1].startsWith("cc.three:") && "pending" in owed[1], owed[1])
        assertTrue(
            ReachRows.outstanding(
                specs = listOf(ReachRows.Spec("aa.one", "aa-one", "AA")), pending = emptyList(), unspecified = emptyList(), overCeiling = emptyList(),
                unswept = emptyList(), beyondArea = emptyList(),
            ).isEmpty(),
        )

        val (stops, said) = CheckStamps.reachHold(release = true, outstanding = owed)
        assertEquals(2, stops.size)
        assertTrue(said.isEmpty())
        val (passes, notices) = CheckStamps.reachHold(release = false, outstanding = owed)
        assertTrue(passes.isEmpty())
        assertEquals(2, notices.size)
        // Nothing owed stops nothing.
        assertEquals(emptyList<String>() to emptyList<String>(), CheckStamps.reachHold(release = true, outstanding = emptyList()))
        // The standing lists are what the release check reads: whatever they leave out or hold pending is owed.
        assertEquals(
            ReachRows.SPECS.count { it.leftOut.isNotEmpty() } + ReachRows.PENDING.size +
                ReachRows.unspecified(ReachRows.SPECS + ReachRows.PENDING).size + ReachRows.overCeiling().size + ReachRows.unswept().size +
                ReachRows.beyondArea().size,
            CheckStamps.reachHold(release = true).first.size,
        )
    }

    @Test
    fun `an entry whose measured units reach past their points is owed a reach spec and a figure over the ceiling a ruling`() {
        // G26 (the second trust audit, 14 Nov 2026): the hold read only the specs' own list; an entry with measured units of
        // a radius over 0 and no spec is owed one, whatever the list says.
        val unnamed = ReachRows.unspecified(specs = emptyList())
        assertTrue("ma.habous" in unnamed && "my.jakim" in unnamed && "tr.diyanet" in unnamed, unnamed.toString())
        assertTrue(ReachRows.unspecified(ReachRows.SPECS + ReachRows.PENDING).none { it in (ReachRows.SPECS + ReachRows.PENDING).map { s -> s.entry } })
        // Only entries the gate checks: an entry with no gate row claims nothing (no stamp, no figure).
        assertEquals(listOf("ma.habous"), ReachRows.unspecified(specs = emptyList(), gated = setOf("ma.habous", "no.such")))
        // Ruling R270: a measured reach limit over 4 min is named until the owner rules; at or under it, or ruled, it is not.
        val minutes = mapOf("aa.one" to mapOf("u1" to mapOf(TimedEvent.FAJR to 5, TimedEvent.ISHA to 4), "u2" to mapOf(TimedEvent.ASR to 3)))
        val over = ReachRows.overCeiling(minutes, ruled = emptyMap())
        assertEquals(1, over.size, over.toString())
        assertTrue(over.single().startsWith("aa.one: 1 reach limit over 4 min") && "u1 fajr 5" in over.single(), over.single())
        assertTrue(ReachRows.overCeiling(minutes, ruled = mapOf("aa.one/u1" to mapOf(TimedEvent.FAJR to 5))).isEmpty())
        assertEquals(1, ReachRows.overCeiling(minutes, ruled = mapOf("aa.one/u1" to mapOf(TimedEvent.FAJR to 4))).size)
        // Ruling R291: a ruling is per event. Another of a ruled unit's events rising over the ceiling is named again, even
        // under the unit's largest ruled figure; so is a ruled event rising past its value.
        val risen = mapOf("aa.one" to mapOf("u1" to mapOf(TimedEvent.FAJR to 5, TimedEvent.ASR to 5, TimedEvent.ISHA to 6)))
        assertEquals(
            mapOf(TimedEvent.ASR to 5),
            ReachRows.unruled("aa.one", "u1", risen, ruled = mapOf("aa.one/u1" to mapOf(TimedEvent.FAJR to 5, TimedEvent.ISHA to 6, TimedEvent.MAGHRIB to 9))),
        )
        assertEquals(
            mapOf(TimedEvent.ISHA to 6),
            ReachRows.unruled("aa.one", "u1", risen, ruled = mapOf("aa.one/u1" to mapOf(TimedEvent.FAJR to 5, TimedEvent.ASR to 5, TimedEvent.ISHA to 5))),
        )
        // Every standing ruling names a unit ReachLimits.kt measures, by `entry/unit`, and each of its events at a figure over
        // the ceiling (a ruling at or under it, or on a key no unit has, would accept nothing and read as if it did). The
        // values themselves are the figures of 16 Nov 2026, derived by a script (R291); tests hold rules, not those figures.
        assertTrue(ReachRows.RULED.isNotEmpty())
        ReachRows.RULED.forEach { (key, events) ->
            assertTrue(ReachLimits.MINUTES[key.substringBefore('/')]?.containsKey(key.substringAfter('/')) == true, "$key is no unit ReachLimits.kt measures")
            assertTrue(events.isNotEmpty(), "$key rules no event")
            events.forEach { (e, m) -> assertTrue(m > ReachRows.CEILING, "$key $e: $m") }
        }
    }

    @Test
    fun `a figure claimed across an area no reach row reads is owed until an area sweep reads it`() {
        // Ruling R297 (16 Nov 2026): the rule, on entries and unit sets made up here. An entry with no units that is measured
        // claims its countries; a measured unit of radius 0 claims the shape its `choose` reads; a measured entry whose set
        // keeps it measured beyond every unit claims the ground past them. A cautious entry, an entry not measured, and one whose measured units all have a
        // radius are no area claim (the radius sweep, ReachRows.SPECS, reads those).
        val muis = requireNotNull(Registry.byId("sg.muis"))
        val method = requireNotNull(muis.method)
        fun unit(id: String, km: Double, measured: Boolean = true) = AuthorityUnit(id, id, GeoPoint(1.0, 103.0), km, measured = measured)
        val sets = mapOf(
            "zz.shape" to UnitSet("zz.shape", listOf(unit("zz.shape", 0.0))) { method },
            "zz.circles" to UnitSet("zz.circles", listOf(unit("a", 30.0), unit("b", 0.0, measured = false))) { method },
            "zz.beyond" to UnitSet("zz.beyond", listOf(unit("a", 30.0)), measuredBeyond = true) { method },
        )
        val entries = listOf(
            muis.copy(id = "zz.country"),
            muis.copy(id = "zz.unmeasured", measured = false),
            muis.copy(id = "zz.shape"),
            muis.copy(id = "zz.circles"),
            muis.copy(id = "zz.beyond"),
            requireNotNull(Registry.byId("gb.cautious")).copy(id = "zz.cautious"),
        )
        val claims = ReachAreas.areaClaims(entries, { sets[it] })
        assertEquals(listOf("zz.beyond", "zz.country", "zz.shape"), claims)
        // Owed while no area claim reads it, and only for entries the gate checks; read, it is not owed.
        val unswept = ReachRows.unswept(gated = setOf("zz.country", "zz.shape"), areas = emptyList(), claims = claims)
        assertEquals(listOf("zz.country", "zz.shape"), unswept)
        val owed = ReachRows.outstanding(specs = emptyList(), pending = emptyList(), unspecified = emptyList(), overCeiling = emptyList(), unswept = unswept, beyondArea = emptyList())
        assertEquals(2, owed.size, owed.toString())
        assertTrue(owed.all { "claims a figure across an area" in it && "R297" in it }, owed.toString())
        assertTrue(CheckStamps.reachHold(release = true, outstanding = owed).first.size == 2)
        val read = ReachAreas.AREAS.first().let { a ->
            ReachAreas.AreaClaim("zz.country", a.group, null, "ZZ", a.zone, a.box, a.stepKm, a.claim, "made up")
        }
        assertEquals(listOf("zz.shape"), ReachRows.unswept(gated = setOf("zz.country", "zz.shape"), areas = listOf(read), claims = claims))
        // Ruling R300: a set whose `choose` hands a measured unit to a place beyond its circle claims the unit's figure
        // there. chosenBeyond finds such a place (not one inside the circle, nor a set that chooses by circle alone), and an
        // entry so found is an area claim once named (CityUnitsTest holds the names to what the app's cities show).
        val a = unit("a", 30.0)
        val near = Place(1.1, 103.0, "Asia/Singapore", "SG")
        val far = Place(2.0, 103.0, "Asia/Singapore", "SG")
        val chosen = UnitSet("zz.chosen", listOf(a), choose = { a }) { method }
        assertEquals(listOf(far to a), ReachAreas.chosenBeyond(chosen, listOf(near, far)))
        assertEquals(emptyList(), ReachAreas.chosenBeyond(UnitSet("zz.circles", listOf(a)) { method }, listOf(near, far)))
        val withChosen = entries + muis.copy(id = "zz.chosen")
        val chosenSets = sets + ("zz.chosen" to chosen)
        assertTrue("zz.chosen" in ReachAreas.areaClaims(withChosen, { chosenSets[it] }, beyond = setOf("zz.chosen")))
        assertTrue("zz.chosen" !in ReachAreas.areaClaims(withChosen, { chosenSets[it] }, beyond = emptySet()))
        // The standing list: every area sweep reads a claim the registry makes, and every claim no sweep reads is owed: on
        // 16 Nov 2026 Brunei-Muara past its circle and JAKIM's towns (ruling R300), until a sweep reads them; since ruling
        // R309 (17 Nov 2026) Brunei's districts are chosen only within their own circles, and since ruling R310 (17 Nov
        // 2026) JAKIM's zones are read across all the ground the app hands them: none is owed.
        assertTrue(ReachAreas.areaClaims().containsAll(ReachAreas.AREAS.map { it.entry }), ReachAreas.areaClaims().toString())
        assertEquals(ReachAreas.areaClaims() - ReachAreas.AREAS.map { it.entry }.toSet(), ReachRows.unswept(gated = null))
        assertEquals(emptyList<String>(), ReachRows.unswept(gated = null))
        // The claims read across an area are the ones whose reach limit gives the area's reason in the app
        // (AREA_READ_UNITS, `<entry>/<unit>`, an entry with no units under its own id), and no other unit's does; a claim
        // other tables ride beside (JAKIM's zones, ruling R310: AreaClaim.ridden) keeps the reason that names them.
        assertEquals(
            ReachAreas.AREAS.filter { !it.ridden }.map { "${it.entry}/${it.unit ?: it.entry}" }.toSet(),
            world.taqwa.app.prayer.engine.registry.AREA_READ_UNITS,
        )
    }

    @Test
    fun `a figure over the ceiling read beyond a drawn area refuses a release until the owner rules`() {
        // The rule, never the standing figures: a line of BEYOND_AREA is owed like a figure over the ceiling inside the
        // area, said for the entries the gate checks, and gone with the line.
        val beyond = mapOf("zz.ground" to "read past its ground over the ceiling")
        val lines = ReachRows.beyondArea(gated = null, beyond = beyond)
        assertEquals(listOf("zz.ground: read past its ground over the ceiling"), lines)
        assertEquals(emptyList<String>(), ReachRows.beyondArea(gated = setOf("other"), beyond = beyond))
        val owed = ReachRows.outstanding(
            specs = emptyList(), pending = emptyList(), unspecified = emptyList(), overCeiling = emptyList(), unswept = emptyList(),
            beyondArea = lines,
        )
        assertEquals(lines, owed)
        assertEquals(1, CheckStamps.reachHold(release = true, outstanding = owed).first.size)
        assertTrue(CheckStamps.reachHold(release = false, outstanding = owed).first.isEmpty())
        // Every entry the standing list names has an area the proof draws: the line speaks of past it. None since ruling
        // R307 (Bahrain and Singapore read across the app's whole cell).
        for (entry in ReachRows.BEYOND_AREA.keys) {
            assertTrue(ReachAreas.AREAS.any { it.entry == entry }, "$entry: no area drawn in ReachAreas.AREAS")
        }
        // And the standing hold says each of them.
        val stops = CheckStamps.reachHold(release = true).first
        for ((entry, what) in ReachRows.BEYOND_AREA) assertTrue(stops.any { it.startsWith("$entry: $what") }, entry)
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
