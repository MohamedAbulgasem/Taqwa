package world.taqwa.app.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watchfeed.WatchTarget
import world.taqwa.app.watchfeed.WatchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The hold on the install row (the review of 9 November). A watch that gets Taqwa has `taqwa_watch`
 * with the install, and `taqwa_face_addable` only once its app has run and published its face, so a
 * reading in place between the two answers no row at all. Settings keeps the install row meanwhile,
 * for the app's 20 s at most, rather than hiding the section until the face row comes; then it takes
 * no row as it comes. Any other answer is shown at once.
 *
 * On virtual time: the test's scheduler runs the hold, and [ended] records when each hold ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InstallHoldTest {

    private val galaxy = WatchTarget(nodeId = "4a1f", name = "Galaxy Watch8 Classic (5F2A)")
    private val pixel = WatchTarget(nodeId = "77c0", name = "Google Pixel Watch 4 (0D3B)")
    private val installRow = WatchTargets(install = galaxy, addFace = null)
    private val faceRow = WatchTargets(install = null, addFace = galaxy)
    private val none = WatchTargets.NONE

    /** The virtual time at which each hold ended. */
    private val ended = mutableListOf<Long>()

    /** A hold of the app's length on the test's scheduler, each end recorded in [ended]. */
    private fun TestScope.hold() = InstallHold(InstallHold.APP, backgroundScope) { ended += currentTime }

    /**
     * 20 s, the bounds on the watch's way from the install to the face's word: the phone's feed,
     * whose message starts the watch app, within `WatchFeedSync.CALLER_BOUND` (10 s) of
     * `taqwa_watch`'s change, then the app's start publishing the face within `FACE_BOUND_MS` (9 s).
     */
    @Test
    fun theAppHoldsTheInstallRowFor20Seconds() {
        assertEquals(20.seconds, InstallHold.APP)
    }

    @Test
    fun noRowAfterAnInstallRowIsHeldUntilTheHoldEndsThenTaken() = runTest {
        val hold = hold()
        assertEquals(installRow, hold.rows(shown = installRow, read = none), "held")
        advanceTimeBy(19_999)
        runCurrent()
        assertEquals(emptyList(), ended, "still holding")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(20_000L), ended, "one more reading asked at its end")
        assertEquals(none, hold.rows(shown = installRow, read = none), "taken once the hold is over")
    }

    /** The owner's flow: the face row comes while the install row is held, and takes its place at once. */
    @Test
    fun anotherRowDuringTheHoldIsShownAtOnceAndEndsIt() = runTest {
        val hold = hold()
        hold.rows(shown = installRow, read = none)
        advanceTimeBy(6_000)
        assertEquals(faceRow, hold.rows(shown = installRow, read = faceRow))
        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(emptyList(), ended, "no reading asked for a hold already ended")
    }

    /** No row again meanwhile, from a tap's follow or the listener: still held, and the hold keeps its end. */
    @Test
    fun noRowAgainDuringTheHoldKeepsItsEnd() = runTest {
        val hold = hold()
        hold.rows(shown = installRow, read = none)
        advanceTimeBy(5_000)
        assertEquals(installRow, hold.rows(shown = installRow, read = none))
        advanceTimeBy(10_000)
        assertEquals(installRow, hold.rows(shown = installRow, read = none))
        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(listOf(20_000L), ended, "one end, 20 s after the first no row")
    }

    /** A reading that fails answers the rows shown (`loadWatchTargets`' otherwise): nothing changes, the hold included. */
    @Test
    fun aReadingThatAnswersTheRowsShownChangesNothing() = runTest {
        val hold = hold()
        hold.rows(shown = installRow, read = none)
        advanceTimeBy(5_000)
        assertEquals(installRow, hold.rows(shown = installRow, read = installRow))
        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(listOf(20_000L), ended, "neither ended nor started again")
        assertEquals(none, hold.rows(shown = installRow, read = none), "taken once it is over")
    }

    /** Only rows with an install row are held: a face taken, or no row to no row, is taken at once. */
    @Test
    fun onlyRowsWithAnInstallRowAreHeld() = runTest {
        val hold = hold()
        assertEquals(none, hold.rows(shown = faceRow, read = none), "the face taken: the section goes at once")
        assertEquals(none, hold.rows(shown = none, read = none))
        assertEquals(installRow, hold.rows(shown = faceRow, read = installRow))
        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(emptyList(), ended, "no hold started")
    }

    /**
     * A hold over is spent once a reading takes an answer, so the next watch that gets Taqwa is held
     * as long: the hold's end reading here finds the install row still there (Play services had not
     * caught up), and a later no row is held again.
     */
    @Test
    fun aHoldOverIsSpentByTheNextAnswer() = runTest {
        val hold = hold()
        hold.rows(shown = installRow, read = none)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(installRow, hold.rows(shown = installRow, read = installRow), "the hold's end reading")
        advanceTimeBy(40_000)
        assertEquals(installRow, hold.rows(shown = installRow, read = none), "held again")
        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(listOf(20_000L, 80_000L), ended)
        val twoWatches = WatchTargets(install = pixel, addFace = galaxy)
        assertEquals(twoWatches, hold.rows(shown = none, read = twoWatches))
    }
}
