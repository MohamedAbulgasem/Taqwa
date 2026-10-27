package world.taqwa.timetables.gate

import world.taqwa.timetables.Json
import world.taqwa.timetables.TestPaths
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The monitor's publish job writes `ProofStamps.kt` itself with tools/timetables/monitor/ci/proof_stamps.py (the
 * Python standard library alone, on a runner that never ran Gradle), so the Kotlin it pushes is never the proving
 * job's copy (docs/MONITOR.md, "Publishing"). The port must write exactly what [ProofStampsGenerator] writes: on the
 * committed stamps, and on stamps whose text holds every character a Kotlin string literal must escape (finding D9).
 */
class ProofStampsPortTest {

    private val port = TestPaths.repoRoot.resolve("tools/timetables/monitor/ci/proof_stamps.py")

    private fun python(stampsDir: File): String {
        val process = ProcessBuilder("python3", "-I", port.path, stampsDir.path).redirectErrorStream(false).start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val err = process.errorStream.readBytes().toString(Charsets.UTF_8)
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "proof_stamps.py did not finish")
        assertEquals(0, process.exitValue(), err)
        return out
    }

    @Test
    fun `the python port writes the committed stamps byte for byte as the kotlin generator does`() {
        val stamps = TestPaths.repoRoot.resolve("tools/timetables/official/stamps")
        val kotlin = ProofStampsGenerator.generate(stamps)
        assertEquals(kotlin, python(stamps))
        // And both are the committed file (ProofStampsStalenessTest holds the Kotlin side alone).
        val committed = TestPaths.repoRoot.resolve(
            "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ProofStamps.kt",
        )
        assertEquals(committed.readText(), kotlin)
    }

    @Test
    fun `a stamp's text that could end a literal or open a template is escaped the same way by both`() {
        val dir = Files.createTempDirectory("proof-stamps-port").toFile()
        try {
            val nasty = "a\\b \"q\" \${x} \$y\nz\rw\tv\u0001u  ok—é 😀"
            dir.resolve("zz.test.json").writeText(
                Json.pretty(
                    mapOf(
                        "entry" to "zz.test-x",
                        "places" to 2,
                        "placeDays" to 30,
                        "ramadanDays" to 0,
                        "last" to "2026-11-30\"",
                        "events" to mapOf("fajr" to mapOf("worstLate" to 1), "isha\$" to mapOf("worstLate" to 2)),
                        "units" to mapOf(
                            "u\"1" to mapOf("events" to mapOf("fajr" to mapOf("worstLate" to 1)), "checked" to listOf("2026-11-01")),
                            "u2" to mapOf("events" to emptyMap<String, Any>(), "checked" to listOf("2026-11-01..2026-11-30")),
                        ),
                        "lateLimits" to mapOf(
                            "class B: 2 min" to mapOf("reason" to null, "events" to listOf("isha", "fajr")),
                            "unit u2: 3 min" to mapOf("reason" to nasty, "events" to listOf("sunrise")),
                        ),
                        "checked" to listOf("2026-11-01..2026-11-30"),
                    ),
                ),
            )
            dir.resolve("aa.other.json").writeText(Json.pretty(mapOf("entry" to "aa.other", "places" to 1)))
            val kotlin = ProofStampsGenerator.generate(dir)
            assertEquals(kotlin, python(dir))
            // No raw newline, carriage return or control character inside a literal, and every dollar escaped.
            val reasonLine = kotlin.lines().single { "reason = \"a" in it }
            assertTrue("\\\${x} \\\$y\\nz\\rw\\tv\\u0001u\\u2028" in reasonLine, reasonLine)
            assertFalse(Regex("(?<!\\\\)\\$").containsMatchIn(kotlin.substringAfter("object ProofStamps")), "an unescaped dollar")
            assertFalse(kotlin.any { it.code < 0x20 && it != '\n' }, "a raw control character")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an entry id that is not a plain id, a keyword or a helper's name is refused by both, since it becomes a function name`() {
        for (id in listOf("zz.test-\$x", "a(){}; fun b", "a.B", "", "fun", "null", "eg.units", "eg.checked2")) {
            val dir = Files.createTempDirectory("proof-stamps-port").toFile()
            try {
                dir.resolve("x.json").writeText(Json.pretty(mapOf("entry" to id, "places" to 1)))
                assertTrue(runCatching { ProofStampsGenerator.generate(dir) }.isFailure, id)
                val process = ProcessBuilder("python3", "-I", port.path, dir.path).redirectErrorStream(true).start()
                process.inputStream.readBytes()
                assertTrue(process.waitFor(60, TimeUnit.SECONDS))
                assertTrue(process.exitValue() != 0, id)
            } finally {
                dir.deleteRecursively()
            }
        }
    }
}
