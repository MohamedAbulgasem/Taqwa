package world.taqwa.app.feature.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Timetable screen's choice (spec §2.2, ruling R52), with a fake earlier-than check. */
class TimetableChooserTest {

    private val finding = EarlierFinding(CheckedTime.ISHA, 22)
    private val applied = mutableListOf<Pair<String, Boolean>>()

    private fun chooser(scope: CoroutineScope, check: suspend (String, (Float) -> Unit) -> TimetableCheck) =
        TimetableChooser(scope, check, nameKeyOf = { "key:$it" }, apply = { id, confirmed -> applied += id to confirmed })

    /** The screen's own scope, which leaving the screen cancels. */
    private fun TestScope.screen(job: Job = Job()) = CoroutineScope(StandardTestDispatcher(testScheduler) + job)

    @Test
    fun aChoiceNeverEarlierIsStoredConfirmed() = runTest {
        val c = chooser(screen()) { _, _ -> TimetableCheck.NeverEarlier }
        c.choose("ca.ift")
        advanceUntilIdle()
        assertEquals(listOf("ca.ift" to true), applied)
        assertNull(c.checking)
        assertNull(c.warning)
    }

    @Test
    fun aChoiceEarlierWarnsAndIsStoredOnlyWhenFollowed() = runTest {
        val c = chooser(screen()) { _, _ -> TimetableCheck.Earlier(finding) }
        c.choose("ca.ift")
        advanceUntilIdle()
        assertEquals(PendingWarning("ca.ift", "key:ca.ift", finding), c.warning)
        assertTrue(applied.isEmpty())
        c.follow()
        assertEquals(listOf("ca.ift" to true), applied)
        assertNull(c.warning)
    }

    @Test
    fun keepingWhatIsFollowedStoresNothing() = runTest {
        val c = chooser(screen()) { _, _ -> TimetableCheck.Earlier(finding) }
        c.choose("ca.ift")
        advanceUntilIdle()
        c.keep()
        c.follow()
        assertNull(c.warning)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun anEntryThatDoesNotApplyHereStoresNothing() = runTest {
        val c = chooser(screen()) { _, _ -> TimetableCheck.NotHere }
        c.choose("sa.ummalqura")
        advanceUntilIdle()
        assertTrue(applied.isEmpty())
        assertNull(c.warning)
    }

    @Test
    fun automaticNeedsNoCheck() = runTest {
        var checked = false
        val c = chooser(screen()) { _, _ -> checked = true; TimetableCheck.NeverEarlier }
        c.choose(AUTOMATIC_TIMETABLE)
        advanceUntilIdle()
        assertEquals(listOf(AUTOMATIC_TIMETABLE to false), applied)
        assertTrue(!checked)
    }

    @Test
    fun aChoiceThatNeedsNoCheckIsStoredUnconfirmedAtOnce() = runTest {
        var checked = false
        val c = TimetableChooser(
            screen(),
            check = { _, _ -> checked = true; TimetableCheck.Earlier(finding) },
            nameKeyOf = { it },
            apply = { id, confirmed -> applied += id to confirmed },
            needsCheck = { false },
        )
        c.choose("SEVENTH_OF_NIGHT")
        advanceUntilIdle()
        assertEquals(listOf("SEVENTH_OF_NIGHT" to false), applied)
        assertTrue(!checked)
    }

    @Test
    fun theCheckShowsItsProgressInTheRowItChecks() = runTest {
        val gate = CompletableDeferred<TimetableCheck>()
        val c = chooser(screen()) { _, onProgress -> onProgress(0.5f); gate.await() }
        c.choose("ca.ift")
        advanceUntilIdle()
        assertEquals(ChoiceInProgress("ca.ift", 0.5f), c.checking)
        gate.complete(TimetableCheck.NeverEarlier)
        advanceUntilIdle()
        assertNull(c.checking)
    }

    @Test
    fun leavingTheScreenDuringTheCheckStoresNothing() = runTest {
        val job = Job()
        val gate = CompletableDeferred<TimetableCheck>()
        val c = chooser(screen(job)) { _, _ -> gate.await() }
        c.choose("ca.ift")
        advanceUntilIdle()
        job.cancel()
        gate.complete(TimetableCheck.NeverEarlier)
        advanceUntilIdle()
        assertTrue(applied.isEmpty())
        assertNull(c.warning)
    }

    @Test
    fun aCheckThatFinishesAfterTheScreenIsGoneStillStoresNothing() = runTest {
        val job = Job()
        val gate = CompletableDeferred<TimetableCheck>()
        // A check that does not stop when asked to: its answer must still be dropped.
        val c = chooser(screen(job)) { _, _ -> withContext(NonCancellable) { gate.await() } }
        c.choose("ca.ift")
        advanceUntilIdle()
        job.cancel()
        gate.complete(TimetableCheck.NeverEarlier)
        advanceUntilIdle()
        assertTrue(applied.isEmpty())
    }

    @Test
    fun aReplacedChoiceNeverApplies() = runTest {
        val gates = mapOf("ca.ift" to CompletableDeferred<TimetableCheck>(), "ca.iit" to CompletableDeferred())
        val c = chooser(screen()) { id, _ -> withContext(NonCancellable) { gates.getValue(id).await() } }
        c.choose("ca.ift")
        advanceUntilIdle()
        c.choose("ca.iit")
        advanceUntilIdle()
        gates.getValue("ca.ift").complete(TimetableCheck.NeverEarlier)
        advanceUntilIdle()
        assertTrue(applied.isEmpty())
        assertEquals("ca.iit", c.checking?.id)
        gates.getValue("ca.iit").complete(TimetableCheck.NeverEarlier)
        advanceUntilIdle()
        assertEquals(listOf("ca.iit" to true), applied)
    }

    @Test
    fun aSettingsChangeDuringTheCheckCancelsItSoItNeverApplies() = runTest {
        // Review M11: the screen remembers a chooser per settings; a settings write while a check
        // runs forgets this one (RememberObserver) and its check, made under the old settings, must
        // never store anything, even one that finishes regardless.
        val gate = CompletableDeferred<TimetableCheck>()
        val c = chooser(screen()) { _, _ -> withContext(NonCancellable) { gate.await() } }
        c.choose("ca.ift")
        advanceUntilIdle()
        assertEquals("ca.ift", c.checking?.id)
        c.onForgotten()
        assertNull(c.checking)
        gate.complete(TimetableCheck.NeverEarlier)
        advanceUntilIdle()
        assertTrue(applied.isEmpty())
        assertNull(c.checking)
        assertNull(c.warning)
    }

    @Test
    fun aWarningWaitingWhenTheSettingsChangeIsDropped() = runTest {
        val c = chooser(screen()) { _, _ -> TimetableCheck.Earlier(finding) }
        c.choose("ca.ift")
        advanceUntilIdle()
        assertTrue(c.warning != null)
        c.onForgotten()
        c.follow()
        assertNull(c.warning)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun progressReachesTheStateOnlyThroughTheScreensScopeAndNeverAfterTheCheck() = runTest {
        // The check reports from its own (background) thread: each report is posted to the screen's
        // scope rather than written where it is made, and one that arrives after the check is done,
        // or after a newer choice, never brings the row's progress back.
        var report: ((Float) -> Unit)? = null
        val gate = CompletableDeferred<TimetableCheck>()
        val c = chooser(screen()) { _, onProgress -> report = onProgress; gate.await() }
        c.choose("ca.ift")
        advanceUntilIdle()
        report!!(0.5f)
        assertEquals(ChoiceInProgress("ca.ift", 0f), c.checking, "written only once the screen's scope runs it")
        advanceUntilIdle()
        assertEquals(ChoiceInProgress("ca.ift", 0.5f), c.checking)
        gate.complete(TimetableCheck.NeverEarlier)
        advanceUntilIdle()
        assertNull(c.checking)
        report!!(0.9f)
        advanceUntilIdle()
        assertNull(c.checking, "a late report after the check is done")
        assertEquals(listOf("ca.ift" to true), applied)
    }

    @Test
    fun aReplacedChoiceNeverWarns() = runTest {
        val gates = mapOf("ca.ift" to CompletableDeferred<TimetableCheck>(), "ca.iit" to CompletableDeferred())
        val c = chooser(screen()) { id, _ -> withContext(NonCancellable) { gates.getValue(id).await() } }
        c.choose("ca.ift")
        advanceUntilIdle()
        c.choose("ca.iit")
        gates.getValue("ca.ift").complete(TimetableCheck.Earlier(finding))
        advanceUntilIdle()
        assertNull(c.warning)
    }
}
