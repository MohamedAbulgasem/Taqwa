package world.taqwa.app.watchfeed

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watchfeed.WatchOpen.OPENED
import world.taqwa.app.watchfeed.WatchOpen.QUEUED
import world.taqwa.app.watchfeed.WatchOpen.UNREACHABLE
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Settings › Watch on Android (plan 5 Task 6, spec 5, the Lead's call 5): which watch each row acts
 * on, when the section shows, what each row opens on the watch, and how long Settings waits.
 *
 * - "Install on your watch" is for the first directly connected watch without Taqwa's watch app.
 * - "Add the Taqwa face" is for the first directly connected watch with Taqwa that says it has the
 *   face to add (`taqwa_face_addable`, published while `FaceCapability.wanted` holds), so the row
 *   goes quiet with the watch's own chip.
 * - Nothing without the Wear API, and nothing for a watch reached only through the cloud.
 * - A tap says "Continue on your watch" once the watch took it, or while it is still on its way at
 *   the bound, and "Couldn't reach your watch" only when it failed; a second tap joins one still on
 *   its way (Ruling 72 item 3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchTargetsTest {

    private val galaxy = WatchPeer(id = "4a1f", name = "Galaxy Watch8 Classic (5F2A)", nearby = true)
    private val pixel = WatchPeer(id = "77c0", name = "Pixel Watch 4", nearby = true)
    private val throughTheCloud = WatchPeer(id = "cloud", name = "Galaxy Watch8 Classic (5F2A)", nearby = false)

    private fun target(peer: WatchPeer) = WatchTarget(nodeId = peer.id, name = peer.name)

    @Test
    fun noWearApiShowsNoRows() {
        assertEquals(WatchTargets.NONE, watchTargets(connected = null, withApp = emptySet(), wantsFace = emptySet()))
        // Whatever the capability answers said: without the Wear API there is no watch to act on.
        assertEquals(WatchTargets.NONE, watchTargets(connected = null, withApp = setOf(galaxy.id), wantsFace = setOf(galaxy.id)))
        // A phone with the Wear API and no watch at all.
        assertEquals(WatchTargets.NONE, watchTargets(connected = emptyList(), withApp = emptySet(), wantsFace = emptySet()))
    }

    @Test
    fun aWatchWithoutTaqwaOffersTheInstall() {
        assertEquals(
            WatchTargets(install = target(galaxy), addFace = null),
            watchTargets(connected = listOf(galaxy), withApp = emptySet(), wantsFace = emptySet()),
        )
    }

    @Test
    fun aWatchWithTaqwaAndNoFaceOffersTheFace() {
        assertEquals(
            WatchTargets(install = null, addFace = target(galaxy)),
            watchTargets(connected = listOf(galaxy), withApp = setOf(galaxy.id), wantsFace = setOf(galaxy.id)),
        )
    }

    @Test
    fun aWatchWithTaqwaAndItsFaceShowsNothing() {
        // The face is active, or its one activation is spent: the watch no longer says it has a face to add.
        assertEquals(
            WatchTargets.NONE,
            watchTargets(connected = listOf(galaxy), withApp = setOf(galaxy.id), wantsFace = emptySet()),
        )
        // A face to add is offered only on a watch with Taqwa: two answers that crossed never make a face row alone.
        assertNull(watchTargets(connected = listOf(galaxy), withApp = emptySet(), wantsFace = setOf(galaxy.id)).addFace)
    }

    @Test
    fun aWatchOutOfReachShowsNothing() {
        assertEquals(
            WatchTargets.NONE,
            watchTargets(connected = listOf(throughTheCloud), withApp = emptySet(), wantsFace = emptySet()),
        )
        assertEquals(
            WatchTargets.NONE,
            watchTargets(connected = listOf(throughTheCloud), withApp = setOf(throughTheCloud.id), wantsFace = setOf(throughTheCloud.id)),
        )
    }

    @Test
    fun twoWatchesEachGetTheirRow() {
        assertEquals(
            WatchTargets(install = target(pixel), addFace = target(galaxy)),
            watchTargets(connected = listOf(pixel, galaxy), withApp = setOf(galaxy.id), wantsFace = setOf(galaxy.id)),
        )
        // Each row takes the first directly connected watch it applies to, in the system's order.
        assertEquals(
            WatchTargets(install = target(pixel), addFace = null),
            watchTargets(connected = listOf(throughTheCloud, pixel, galaxy), withApp = emptySet(), wantsFace = emptySet()),
        )
        assertEquals(
            WatchTargets(install = null, addFace = target(galaxy)),
            watchTargets(
                connected = listOf(throughTheCloud, galaxy, pixel),
                withApp = setOf(throughTheCloud.id, galaxy.id, pixel.id),
                wantsFace = setOf(throughTheCloud.id, galaxy.id, pixel.id),
            ),
        )
    }

    @Test
    fun theSectionFollowsTheTargetsOrTheAppleFace() {
        assertFalse(watchSectionShown(WatchTargets.NONE, appleFaceOffered = false))
        assertTrue(watchSectionShown(WatchTargets.NONE, appleFaceOffered = true))
        val install = WatchTargets(install = target(galaxy), addFace = null)
        val face = WatchTargets(install = null, addFace = target(galaxy))
        val both = WatchTargets(install = target(pixel), addFace = target(galaxy))
        for (targets in listOf(install, face, both)) {
            assertTrue(watchSectionShown(targets, appleFaceOffered = false), "$targets")
            assertTrue(watchSectionShown(targets, appleFaceOffered = true), "$targets")
        }
    }

    @Test
    fun theInstallIntentIsThePlayStorePage() {
        assertEquals(
            RemoteIntentSpec(
                action = "android.intent.action.VIEW",
                category = "android.intent.category.BROWSABLE",
                uri = "market://details?id=world.taqwa.app",
            ),
            remoteIntentSpec(WatchAction.INSTALL),
        )
    }

    @Test
    fun theFaceIntentIsTheWatchsFaceScreen() {
        assertEquals(
            RemoteIntentSpec(
                action = "android.intent.action.VIEW",
                category = "android.intent.category.BROWSABLE",
                uri = "taqwa://face",
            ),
            remoteIntentSpec(WatchAction.ADD_FACE),
        )
        assertEquals(WatchLinkContract.FACE_URI, remoteIntentSpec(WatchAction.ADD_FACE).uri)
    }

    @Test
    fun aSlowNodeQueryNeverHoldsSettings() = runTest {
        val slow = FakeWatchTargetsSource(answer = {
            delay(10.seconds)
            WatchTargets(install = target(galaxy), addFace = null)
        })
        val started = testScheduler.currentTime
        assertEquals(WatchTargets.NONE, loadWatchTargets(slow))
        assertEquals(3_000L, testScheduler.currentTime - started, "answered at the 3 s bound, never later")

        // An answer inside the bound is the answer.
        val prompt = FakeWatchTargetsSource(answer = {
            delay(2_900)
            WatchTargets(install = target(galaxy), addFace = null)
        })
        assertEquals(WatchTargets(install = target(galaxy), addFace = null), loadWatchTargets(prompt))
    }

    @Test
    fun aThrowingSourceIsNone() = runTest {
        val throwing = FakeWatchTargetsSource(answer = { throw IllegalStateException("Play services answered an error") })
        assertEquals(WatchTargets.NONE, loadWatchTargets(throwing))
        // A stray cancellation from inside the source is its failure, not Settings' own.
        val stray = FakeWatchTargetsSource(answer = { throw CancellationException("a task cancelled inside the source") })
        assertEquals(WatchTargets.NONE, loadWatchTargets(stray))
    }

    /**
     * The owner's report of 9 November: while Settings stays in front it reads the rows again as
     * the watch side changes, and such a reading that fails keeps what is shown ([loadWatchTargets]'s
     * `otherwise`), so a passing error never takes the rows, and a tap's answer with them. Only an
     * answer changes the rows, and no row is an answer: the watch took its face, or went away.
     */
    @Test
    fun aReadingThatFailsCanKeepWhatIsShown() = runTest {
        val shown = WatchTargets(install = target(galaxy), addFace = null)
        val throwing = FakeWatchTargetsSource(answer = { throw IllegalStateException("Play services answered an error") })
        assertEquals(shown, loadWatchTargets(throwing, otherwise = shown))
        val stray = FakeWatchTargetsSource(answer = { throw CancellationException("a task cancelled inside the source") })
        assertEquals(shown, loadWatchTargets(stray, otherwise = shown))
        val slow = FakeWatchTargetsSource(answer = {
            delay(10.seconds)
            WatchTargets.NONE
        })
        val started = currentTime
        assertEquals(shown, loadWatchTargets(slow, otherwise = shown))
        assertEquals(3_000L, currentTime - started, "kept at the 3 s bound, never later")
        assertEquals(WatchTargets.NONE, loadWatchTargets(FakeWatchTargetsSource(), otherwise = shown))
        val face = WatchTargets(install = null, addFace = target(galaxy))
        assertEquals(face, loadWatchTargets(FakeWatchTargetsSource(answer = { face }), otherwise = shown))
    }

    @Test
    fun leavingSettingsCancelsTheLoad() = runTest {
        val never = CompletableDeferred<WatchTargets>()
        val source = FakeWatchTargetsSource(answer = { never.await() })
        val load = async { loadWatchTargets(source) }
        testScheduler.advanceTimeBy(1_000)
        load.cancel()
        assertFailsWith<CancellationException> { load.await() }
        assertEquals(1, source.asked)
    }

    // ── A row's tap (Ruling 72 item 3, Ruling 74 items 9-11) ──

    /**
     * Taps' requests on the test's own scheduler and clock, started at once as the app's are, in a
     * scope of their own: a request outlives the tap that sent it.
     */
    private fun TestScope.requests() = WatchRequests(
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
        clock = testScheduler.timeSource,
    )

    /** One tap's answers, each with the virtual time it came at after the tap. */
    private suspend fun TestScope.answersOf(tap: Flow<WatchOpen>): List<Pair<WatchOpen, Long>> {
        val tapped = currentTime
        return tap.map { it to currentTime - tapped }.toList()
    }

    @Test
    fun aTapTheWatchTakesContinuesOnIt() = runTest {
        val source = FakeWatchTargetsSource()
        assertEquals(listOf(OPENED to 0L), answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests())))
        assertEquals(listOf(target(galaxy) to WatchAction.ADD_FACE), source.opened)
    }

    /**
     * "Couldn't reach your watch" only for a refused or failed start, a throw, or no watch to start
     * it on: the iPhone's source and a phone without Play services answer false at once.
     */
    @Test
    fun aTapTheWatchRefusesIsUnreachable() = runTest {
        val source = FakeWatchTargetsSource(opens = { _, _ -> false })
        assertEquals(listOf(UNREACHABLE to 0L), answersOf(openOnWatch(source, target(pixel), WatchAction.INSTALL, requests = requests())))
        assertEquals(listOf(target(pixel) to WatchAction.INSTALL), source.opened)
    }

    @Test
    fun aThrowingOpenIsUnreachable() = runTest {
        val thrown = listOf(
            IllegalStateException("RemoteIntentException: the companion app answered RESULT_FAILED"),
            CancellationException("a task cancelled inside the source"),
        )
        for (error in thrown) {
            val source = FakeWatchTargetsSource(opens = { _, _ -> throw error })
            assertEquals(listOf(UNREACHABLE), openOnWatch(source, target(galaxy), WatchAction.INSTALL, requests = requests()).toList(), "$error")
        }
    }

    /**
     * P5.6's finding, seen again at P5.12 Step 3: with the phone's Wear OS companion cached, Android
     * held `RemoteActivityHelper`'s `REMOTE_INTENT` (DEFERRED) and delivered it 45 s after the tap,
     * and the watch opened the screen then. So the bound running out answers "Continue on your
     * watch", the request left on its way and never cancelled, and the companion's answer when it
     * comes stands.
     */
    @Test
    fun aTapStillWithTheCompanionAtTheBoundContinuesOnTheWatch() = runTest {
        var delivered = false
        val source = FakeWatchTargetsSource(opens = { _, _ ->
            delay(45.seconds)
            delivered = true
            true
        })
        assertEquals(
            listOf(QUEUED to 10_000L, OPENED to 45_000L),
            answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests())),
        )
        assertTrue(delivered, "the bound left the request on its way")
    }

    /** Ruling 76 item 8: the one bound, [openOnWatch]'s default and the rows' `WatchSection`'s. */
    @Test
    fun theBoundIsTenSeconds() {
        assertEquals(10.seconds, WatchRequests.BOUND)
    }

    @Test
    fun aTapTheCompanionNeverAnswersStaysOnItsWay() = runTest {
        val never = CompletableDeferred<Boolean>()
        val source = FakeWatchTargetsSource(opens = { _, _ -> never.await() })
        val tapped = currentTime
        assertEquals(QUEUED, openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests()).first())
        assertEquals(10_000L, currentTime - tapped, "answered at the 10 s bound, never later")
    }

    /** A request the companion takes late and then refuses, or throws on, says so when it ends. */
    @Test
    fun aQueuedTapTheCompanionThenRefusesSaysSo() = runTest {
        val refused = FakeWatchTargetsSource(opens = { _, _ ->
            delay(45.seconds)
            false
        })
        assertEquals(
            listOf(QUEUED to 10_000L, UNREACHABLE to 45_000L),
            answersOf(openOnWatch(refused, target(galaxy), WatchAction.INSTALL, requests = requests())),
        )
        val throwing = FakeWatchTargetsSource(opens = { _, _ ->
            delay(45.seconds)
            throw IllegalStateException("RemoteIntentException: the companion app answered RESULT_FAILED")
        })
        assertEquals(
            listOf(QUEUED to 10_000L, UNREACHABLE to 45_000L),
            answersOf(openOnWatch(throwing, target(galaxy), WatchAction.INSTALL, requests = requests())),
        )
    }

    /**
     * A second tap on a row while its first is on its way sends nothing more, so a held request
     * delivered late never opens `FaceActivity` twice: it joins the first and takes its answer. The
     * first is already past the bound, so the second says "Continue on your watch" at once (Ruling
     * 74 item 10), never a fresh 10 s later. Once the first has ended, a tap is a request of its
     * own.
     */
    @Test
    fun aSecondTapWhileTheFirstIsPendingSendsNothingMore() = runTest {
        val companion = CompletableDeferred<Boolean>()
        val source = FakeWatchTargetsSource(opens = { _, _ -> companion.await() })
        val requests = requests()
        val first = async { answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)) }
        advanceTimeBy(12_000) // the first said "Continue on your watch" at 10 s, the watch still shows nothing
        val second = async { answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)) }
        advanceTimeBy(15_000)
        assertEquals(1, source.opened.size, "the second tap sent nothing")
        companion.complete(true) // the companion wakes, and the watch opens the face's screen once
        assertEquals(listOf(QUEUED to 10_000L, OPENED to 27_000L), first.await())
        assertEquals(listOf(QUEUED to 0L, OPENED to 15_000L), second.await())

        source.opens = { _, _ -> true }
        assertEquals(listOf(OPENED), openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests).toList())
        assertEquals(2, source.opened.size, "the first had ended: this tap is sent")
    }

    /**
     * The bound counts from the request's sending, for every tap that joins it: a tap 4 s after the
     * first says "Continue on your watch" with it, at 10 s, not 10 s after its own tap.
     */
    @Test
    fun aTapJoiningBeforeTheBoundAnswersAtTheRequestsBound() = runTest {
        val companion = CompletableDeferred<Boolean>()
        val source = FakeWatchTargetsSource(opens = { _, _ -> companion.await() })
        val requests = requests()
        val first = async { answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)) }
        advanceTimeBy(4_000)
        val second = async { answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)) }
        advanceTimeBy(26_000)
        companion.complete(true)
        assertEquals(listOf(QUEUED to 10_000L, OPENED to 30_000L), first.await())
        assertEquals(listOf(QUEUED to 6_000L, OPENED to 26_000L), second.await())
        assertEquals(1, source.opened.size, "the second tap sent nothing")
    }

    /** Only the same row's request is joined: another action, or another watch, is sent. */
    @Test
    fun aTapForAnotherActionOrWatchIsSentBesideOneOnItsWay() = runTest {
        val companion = CompletableDeferred<Boolean>()
        val source = FakeWatchTargetsSource(opens = { _, _ -> companion.await() })
        val requests = requests()
        val rows = listOf(target(galaxy) to WatchAction.ADD_FACE, target(galaxy) to WatchAction.INSTALL, target(pixel) to WatchAction.ADD_FACE)
        val taps = rows.map { (watch, action) -> async { openOnWatch(source, watch, action, requests = requests).toList() } }
        runCurrent()
        assertEquals(rows, source.opened)
        companion.complete(true)
        for (tap in taps) assertEquals(listOf(OPENED), tap.await())
    }

    /**
     * Leaving Settings cancels a tap's wait and never its request, which the system holds either
     * way: a tap when Settings comes back joins it, sends nothing, and says "Continue on your
     * watch" at the request's bound.
     */
    @Test
    fun leavingSettingsLeavesTheRequestOnItsWay() = runTest {
        var delivered = false
        val source = FakeWatchTargetsSource(opens = { _, _ ->
            delay(45.seconds)
            delivered = true
            true
        })
        val requests = requests()
        val tap = async { openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests).toList() }
        advanceTimeBy(2_000)
        tap.cancel()
        assertFailsWith<CancellationException> { tap.await() }
        assertEquals(
            listOf(QUEUED to 8_000L, OPENED to 43_000L),
            answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)),
        )
        assertEquals(1, source.opened.size, "the tap after coming back sent nothing")
        assertTrue(delivered)
    }

    /**
     * Ruling 74 item 9: a request on its way expires [WatchRequests.EXPIRY] (2 minutes) after it
     * was sent. Until then a tap joins it and sends nothing; a tap that follows it stops following
     * at the expiry, its row still saying "Continue on your watch"; and a tap from then on sends
     * anew, since a second `FaceActivity` is better than a row that never sends again.
     */
    @Test
    fun aRequestOnItsWayExpiresTwoMinutesAfterItWasSent() = runTest {
        assertEquals(2.minutes, WatchRequests.EXPIRY)
        val never = CompletableDeferred<Boolean>()
        val source = FakeWatchTargetsSource(opens = { _, _ -> never.await() })
        val requests = requests()
        val first = async { answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)) }
        advanceTimeBy(119_999)
        runCurrent()
        assertFalse(first.isCompleted, "the first tap follows its request until the expiry")
        val joined = async { answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)) }
        runCurrent()
        assertEquals(1, source.opened.size, "a tap a millisecond before the expiry sent nothing")
        advanceTimeBy(1)
        runCurrent()
        assertTrue(first.isCompleted, "the first tap stopped following at the expiry")
        assertEquals(listOf(QUEUED to 10_000L), first.await())
        assertEquals(listOf(QUEUED to 0L), joined.await())

        val anew = async { answersOf(openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests)) }
        runCurrent()
        assertEquals(2, source.opened.size, "a tap at the expiry is sent anew")
        advanceTimeBy(130_000)
        assertEquals(listOf(QUEUED to 10_000L), anew.await())
    }

    /**
     * Ruling 76 item 9: the very moment of the expiry, the first's watcher still to run. A tap then
     * joins nothing and is sent anew (the join's `left.isPositive()`), and the first's watcher,
     * when it runs, lets go of its own request and never the new one in its place (its identity
     * check).
     */
    @Test
    fun aTapAtTheExpiryIsSentAnewAndTheFirstsWatcherLeavesItAlone() = runTest {
        val row = galaxy.id to WatchAction.ADD_FACE
        val never = CompletableDeferred<Boolean>()
        val source = FakeWatchTargetsSource(opens = { _, _ -> never.await() })
        val requests = requests()
        val first = requests.request(source, target(galaxy), WatchAction.ADD_FACE)
        advanceTimeBy(120_000) // up to the first's watcher, which wakes at 120,000 ms and has not run
        val anew = requests.request(source, target(galaxy), WatchAction.ADD_FACE)
        assertNotSame(first, anew, "a tap at the expiry joins nothing")
        assertEquals(2, source.opened.size, "a tap at the expiry is sent anew")
        runCurrent()
        assertEquals(setOf(row), requests.rowsOnTheirWay(), "the first's watcher let go of its own request alone")
        assertSame(anew, requests.request(source, target(galaxy), WatchAction.ADD_FACE), "the next tap joins the new one")
        assertEquals(2, source.opened.size, "the next tap sent nothing")
    }

    /**
     * Ruling 76 item 9: a request that is over, its watcher still to take it out, is joined by no
     * tap (the join's `answer.isActive`): that tap would send nothing and hear an old answer. Its
     * scope dispatches, so nothing it starts has run yet; a cancel is what ends a request before
     * that.
     */
    @Test
    fun aRequestOverAndNotYetLetGoIsNotJoined() = runTest {
        val row = galaxy.id to WatchAction.ADD_FACE
        val source = FakeWatchTargetsSource()
        val requests = WatchRequests(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            clock = testScheduler.timeSource,
        )
        val first = requests.request(source, target(galaxy), WatchAction.ADD_FACE)
        first.answer.cancel()
        assertEquals(setOf(row), requests.rowsOnTheirWay(), "its watcher has not run")
        assertNotSame(first, requests.request(source, target(galaxy), WatchAction.ADD_FACE))
    }

    /**
     * Ruling 74 item 11: a request leaves the requests on their way when it ends, however it ends
     * (taken, refused, thrown), or when it expires; until then it is held for its row alone.
     */
    @Test
    fun aRequestLeavesTheRequestsOnTheirWayWhenItEndsOrExpires() = runTest {
        val row = galaxy.id to WatchAction.ADD_FACE
        val ends = listOf<Pair<String, () -> Boolean>>(
            "taken" to { true },
            "refused" to { false },
            "thrown" to { throw IllegalStateException("RemoteIntentException: the companion app answered RESULT_FAILED") },
        )
        for ((how, end) in ends) {
            val source = FakeWatchTargetsSource(opens = { _, _ ->
                delay(45.seconds)
                end()
            })
            val requests = requests()
            val tap = async { openOnWatch(source, target(galaxy), WatchAction.ADD_FACE, requests = requests).toList() }
            advanceTimeBy(30_000)
            assertEquals(setOf(row), requests.rowsOnTheirWay(), how)
            tap.await()
            assertEquals(emptySet(), requests.rowsOnTheirWay(), "$how: an ended request is let go")
        }

        val never = CompletableDeferred<Boolean>()
        val requests = requests()
        val tap = async { openOnWatch(FakeWatchTargetsSource(opens = { _, _ -> never.await() }), target(galaxy), WatchAction.ADD_FACE, requests = requests).toList() }
        advanceTimeBy(119_999)
        runCurrent()
        assertEquals(setOf(row), requests.rowsOnTheirWay())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(emptySet(), requests.rowsOnTheirWay(), "an expired request is let go")
        tap.cancel()
    }
}
