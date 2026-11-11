package world.taqwa.app.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watchface.WatchFaceOffer
import world.taqwa.app.watchface.WatchFaceResult
import world.taqwa.app.watchfeed.FakeWatchTargetsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * When Settings › Watch reads its rows again while Settings stays in front (the owner's report of
 * 9 November: after he tapped "Install on your watch" and Taqwa was installed on his Galaxy Watch,
 * the row stayed until he left Settings and came back). Settings reads them again whenever the watch
 * side says it changed (Android's capabilities, the iPhone's session), and for a while after a
 * row's tap ([TapFollow]), in case the watch side says nothing.
 *
 * On virtual time: the test's scheduler runs every wait, and each reading is timed by it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchRowsChangedTest {

    /** An offer that says it changed as [changed] does, and is never available, as Android's. */
    private class Offer(private val changed: Flow<Unit> = emptyFlow()) : WatchFaceOffer {
        override fun available(): Boolean = false
        override fun add(onDone: (WatchFaceResult) -> Unit) = onDone(WatchFaceResult.FAILED)
        override fun changes(): Flow<Unit> = changed
    }

    /** Something that says it changed when the test emits, kept for none that listens later. */
    private fun said() = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** The virtual time of each reading [changes] asks for, collected from now on, the test's scheduler. */
    private fun TestScope.readingsOf(changes: Flow<Unit>): List<Long> {
        val at = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.collect { at += currentTime } }
        return at
    }

    /** [follow]'s readings after taps, the source and the offer saying nothing. */
    private fun TestScope.followed(taps: Flow<Unit>, follow: TapFollow = TapFollow.APP) =
        readingsOf(watchRowsChanged(FakeWatchTargetsSource(), Offer(), taps, follow, listenOn = StandardTestDispatcher(testScheduler)))

    /**
     * The fallback's numbers. Every 5 s: the rows trail the watch by at most 5 s and a reading (3 s
     * at most). For 3 minutes: a request a cached companion holds is joined for 2 minutes
     * (`WatchRequests.EXPIRY`), and the wearer still has to act on the watch after it opens: the
     * Play Store's install, or the face screen's button with its permission and the push (20 s).
     */
    @Test
    fun theAppFollowsATapEveryFiveSecondsForThreeMinutes() {
        assertEquals(TapFollow(every = 5.seconds, lasting = 3.minutes), TapFollow.APP)
    }

    @Test
    fun aTapIsReadAfterEveryFiveSecondsForThreeMinutesAndThenNoMore() = runTest {
        val taps = said()
        val at = followed(taps)
        runCurrent()
        taps.tryEmit(Unit)
        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals((1..36).map { it * 5_000L }, at, "36 readings, the last at 3 minutes")
    }

    @Test
    fun aTapMeanwhileStartsTheThreeMinutesAgain() = runTest {
        val taps = said()
        val at = followed(taps)
        runCurrent()
        taps.tryEmit(Unit)
        advanceTimeBy(62_000)
        runCurrent()
        taps.tryEmit(Unit)
        advanceTimeBy(30.minutes)
        runCurrent()
        val first = (1..12).map { it * 5_000L }
        val second = (1..36).map { 62_000L + it * 5_000L }
        assertEquals(first + second, at, "the second tap's 3 minutes, never both at once")
    }

    @Test
    fun withoutATapNothingIsRead() = runTest {
        val at = followed(said())
        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals(emptyList(), at)
    }

    @Test
    fun theWatchSideTheAppleSessionAndATapEachAskForAReading() = runTest {
        val watch = said()
        val session = said()
        val taps = said()
        val at = readingsOf(
            watchRowsChanged(
                FakeWatchTargetsSource(changed = watch),
                Offer(session),
                taps,
                TapFollow(every = 1.seconds, lasting = 2.seconds),
                listenOn = StandardTestDispatcher(testScheduler),
            ),
        )
        runCurrent()
        watch.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(10_000)
        session.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(10_000)
        taps.tryEmit(Unit)
        advanceTimeBy(30.minutes)
        runCurrent()
        assertEquals(listOf(0L, 10_000L, 21_000L, 22_000L), at)
    }

    /** The end of a hold on the install row ([InstallHold]) asks for a reading, as any change does. */
    @Test
    fun theEndOfAHoldAsksForAReading() = runTest {
        val ends = said()
        val at = readingsOf(
            watchRowsChanged(FakeWatchTargetsSource(), Offer(), emptyFlow(), TapFollow.APP, ends, StandardTestDispatcher(testScheduler)),
        )
        runCurrent()
        advanceTimeBy(20_000)
        ends.tryEmit(Unit)
        runCurrent()
        assertEquals(listOf(20_000L), at)
    }

    /** A source whose listening fails says nothing more, and the rest still ask: it ends nothing in Settings. */
    @Test
    fun aSourceThatCannotListenEndsNothing() = runTest {
        val session = said()
        val source = FakeWatchTargetsSource(changed = flow { throw IllegalStateException("Play services refused the listener") })
        val at = readingsOf(watchRowsChanged(source, Offer(session), emptyFlow(), TapFollow.APP, listenOn = StandardTestDispatcher(testScheduler)))
        runCurrent()
        session.tryEmit(Unit)
        runCurrent()
        assertEquals(listOf(0L), at)
    }

    /** Changes while a reading runs are one reading after it: readings never queue up behind a slow one. */
    @Test
    fun changesDuringAReadingAreOneReadingAfterIt() = runTest {
        val watch = said()
        val at = mutableListOf<Long>()
        backgroundScope.launch {
            watchRowsChanged(FakeWatchTargetsSource(changed = watch), Offer(), emptyFlow(), TapFollow.APP, listenOn = StandardTestDispatcher(testScheduler))
                .collect {
                    at += currentTime
                    delay(3.seconds) // a reading at its bound
                }
        }
        runCurrent()
        watch.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(1_000)
        repeat(3) {
            watch.tryEmit(Unit)
            runCurrent()
        }
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(listOf(0L, 3_000L), at)
    }
}
