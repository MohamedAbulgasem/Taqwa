package world.taqwa.timetables.curves

import world.taqwa.timetables.TestPaths
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The committed `DiyanetEuropeCurves.kt` is exactly what the generator writes from the rows
 * [DIYANET_EUROPE_CITIES] names (the research captures and the pinned capture of 28 September), so
 * a hand edit to the curves, or a change to the generator or its rows that nobody regenerated, fails
 * here. Skipped, and says so, where the archive is not held (CI). Its message names no printed time
 * (R69): the curves are the method's own depressions.
 */
class DiyanetEuropeCurvesStalenessTest {

    private val official = File(System.getProperty("taqwa.official") ?: error("taqwa.official not set"))

    @Test
    fun `the committed curves are the generator's output byte for byte`() {
        if (!official.resolve("archive").isDirectory) {
            println("curves staleness skipped: no archive under $official")
            return
        }
        val committed = TestPaths.repoRoot.resolve(
            "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/DiyanetEuropeCurves.kt",
        )
        assertEquals(
            committed.readText(),
            renderDiyanetEuropeCurves(official),
            "DiyanetEuropeCurves.kt is not what generateDiyanetEuropeCurves writes; regenerate it",
        )
    }
}
