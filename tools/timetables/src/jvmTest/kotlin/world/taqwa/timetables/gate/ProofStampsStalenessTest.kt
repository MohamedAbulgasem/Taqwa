package world.taqwa.timetables.gate

import world.taqwa.timetables.TestPaths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 11's committed `ProofStamps.kt` is generated, not hand-edited, and nothing re-runs the
 * generator on a plain build — a stamp refresh that forgot `./gradlew -p tools/timetables
 * generateProofStamps` would ship a stale table silently. This re-runs the generator in memory
 * against the committed `official/stamps/` and fails if the result differs from the committed
 * file, naming the fix in the failure message.
 */
class ProofStampsStalenessTest {

    @Test
    fun `ProofStamps kt matches what the generator produces from the committed stamps`() {
        val stampsDir = TestPaths.repoRoot.resolve("tools/timetables/official/stamps")
        val outFile = TestPaths.repoRoot.resolve(
            "shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ProofStamps.kt",
        )
        val expected = ProofStampsGenerator.generate(stampsDir)
        val committed = outFile.readText()
        assertEquals(
            expected,
            committed,
            "ProofStamps.kt is stale — re-run ./gradlew -p tools/timetables generateProofStamps and commit the result",
        )
    }
}
