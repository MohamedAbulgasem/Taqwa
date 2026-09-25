package world.taqwa.app.recitation

import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The translation from what WorkManager reports to what the download sheet reads.
 *
 * The rule worth a test of its own is the disappearing act: a surah that committed has no state,
 * because from that moment the library is what says the reader owns it, and a chip left behind in
 * the state map would be a second answer to the same question.
 */
class RecitationWorkTest {

    private val key = DownloadKey("ar.alafasy", 2)
    private val other = DownloadKey("ar.alafasy", 112)

    private fun snapshot(
        key: DownloadKey = this.key,
        state: WorkInfo.State,
        done: Long = 0L,
        total: Long = 0L,
        verifying: Boolean = false,
        reason: DownloadFailure? = null,
    ) = RecitationWork.Snapshot(key, state, done, total, verifying, reason)

    @Test
    fun anEnqueuedWorkIsQueued() {
        val states = RecitationWork.statesOf(listOf(snapshot(state = WorkInfo.State.ENQUEUED)))
        assertEquals(DownloadState.Queued, states[key])
    }

    @Test
    fun aRunningWorkWithProgressIsDownloading() {
        val states = RecitationWork.statesOf(
            listOf(snapshot(state = WorkInfo.State.RUNNING, done = 1_000L, total = 4_000L))
        )
        assertEquals(DownloadState.Downloading(1_000L, 4_000L), states[key])
        assertEquals(0.25f, (states[key] as DownloadState.Downloading).fraction)
    }

    @Test
    fun aRunningWorkWaitingForASlotIsStillQueued() {
        val states = RecitationWork.statesOf(listOf(snapshot(state = WorkInfo.State.RUNNING)))
        assertEquals(DownloadState.Queued, states[key])
    }

    @Test
    fun aRunningWorkThatIsHashingIsVerifying() {
        val states = RecitationWork.statesOf(
            listOf(snapshot(state = WorkInfo.State.RUNNING, done = 4_000L, total = 4_000L, verifying = true))
        )
        assertEquals(DownloadState.Verifying, states[key])
    }

    @Test
    fun theStateMapDropsTheKeyOnceTheDownloadHasCommitted() {
        val states = RecitationWork.statesOf(
            listOf(
                snapshot(state = WorkInfo.State.SUCCEEDED, done = 4_000L, total = 4_000L),
                snapshot(key = other, state = WorkInfo.State.RUNNING, done = 10L, total = 40L),
            )
        )
        assertTrue(key !in states)
        assertEquals(setOf(other), states.keys)
    }

    @Test
    fun aCancelledWorkIsNotAFailure() {
        val states = RecitationWork.statesOf(listOf(snapshot(state = WorkInfo.State.CANCELLED)))
        assertTrue(states.isEmpty())
    }

    @Test
    fun aFailedWorkCarriesTheReasonItReported() {
        val states = RecitationWork.statesOf(
            listOf(snapshot(state = WorkInfo.State.FAILED, reason = DownloadFailure.NEEDS_WIFI))
        )
        assertEquals(DownloadState.Failed(DownloadFailure.NEEDS_WIFI), states[key])
    }

    @Test
    fun aFailedWorkWithNoReasonIsReportedAsAServerFailure() {
        val states = RecitationWork.statesOf(listOf(snapshot(state = WorkInfo.State.FAILED)))
        assertEquals(DownloadState.Failed(DownloadFailure.SERVER), states[key])
    }

    @Test
    fun aLiveAttemptOutranksTheFailedOneItReplaced() {
        val states = RecitationWork.statesOf(
            listOf(
                snapshot(state = WorkInfo.State.FAILED, reason = DownloadFailure.SERVER),
                snapshot(state = WorkInfo.State.RUNNING, done = 5L, total = 50L),
            )
        )
        assertEquals(DownloadState.Downloading(5L, 50L), states[key])
    }

    @Test
    fun theWorkNamesFollowTheSpec() {
        assertEquals("recitation-ar.alafasy-2", RecitationWork.uniqueName(key))
        assertEquals("recitation", RecitationWork.TAG_ALL)
        assertEquals("recitation-ar.alafasy", RecitationWork.reciterTag("ar.alafasy"))
        assertEquals("recitation-key-ar.alafasy/2", RecitationWork.keyTag(key))
        assertEquals(key, DownloadKey.parse(key.wire))
    }
}
