package world.taqwa.app.feature.prayer

import androidx.compose.ui.platform.AccessibilityManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import world.taqwa.app.feature.settings.InstallHold
import world.taqwa.app.feature.settings.TapFollow
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.watchfeed.FakeWatchTargetsSource
import world.taqwa.app.watchfeed.WatchAction
import world.taqwa.app.watchfeed.WatchRequests
import world.taqwa.app.watchfeed.WatchTarget
import world.taqwa.app.watchfeed.WatchTargets
import world.taqwa.app.watchfeed.openOnWatch
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Home's watch card (1.7.0, Option B): Settings › Watch's offer as a card above the Qibla card. It
 * reads the watch as Settings does, in Settings' order, and is put on Home only when its reading
 * comes before the first touch or scroll since Home came to the front, so the Qibla card never moves
 * under a thumb; a card already there changes in place and folds when no row applies. Its tap asks
 * the watch through Settings' own requests, and a tapped install card waits through its tap's
 * follow-up for the face; its x retires it for every watch and for good, with six seconds to undo
 * in its place (longer where the reader asked for more time), never folding under a finger.
 *
 * On virtual time: every wait, the readings' 3 s bound, the hold, a tap's 10 s bound, its follow-up
 * and the undo window run on the test's scheduler. Home in front is [front]'s job; leaving is its
 * cancellation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeWatchCardTest {

    private val galaxy = WatchTarget(nodeId = "4a1f", name = "Galaxy Watch7")
    private val pixel = WatchTarget(nodeId = "77c0", name = "Google Pixel Watch 4 (0D3B)")
    private fun install(t: WatchTarget = galaxy) = WatchTargets(install = t, addFace = null)
    private fun face(t: WatchTarget = galaxy) = WatchTargets(install = null, addFace = t)
    private fun installCard(answer: WatchCardAnswer? = null, tapped: Boolean = false) =
        WatchCardUi.Offer(WatchCardStep.INSTALL, galaxy.name, answer, tapped)
    private fun faceCard(answer: WatchCardAnswer? = null, tapped: Boolean = false, on: WatchTarget = galaxy) =
        WatchCardUi.Offer(WatchCardStep.ADD_FACE, on.name, answer, tapped)

    /** The stored flag, and every write in order. */
    private class Flag(var stored: Boolean = false) {
        val writes = mutableListOf<Boolean>()
    }

    /**
     * The user's watch as the test moves it: the source reads [rows], each tap answers as [opens]
     * says, and [change] is the watch side saying it changed (Android's capabilities).
     */
    private class Watch(var rows: WatchTargets = WatchTargets.NONE) {
        private val said = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val source = FakeWatchTargetsSource(answer = { rows }, changed = said)
        fun change() {
            said.tryEmit(Unit)
        }
    }

    /** No tap followed by readings, unless a test is about that. */
    private val noFollow = TapFollow(every = 1.seconds, lasting = Duration.ZERO)

    /** Taps' requests of the test's own, on its clock, as the app's are made. */
    private fun TestScope.testRequests() =
        WatchRequests(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)), clock = testScheduler.timeSource)

    private fun TestScope.cardFor(
        source: FakeWatchTargetsSource,
        flag: Flag = Flag(),
        follow: TapFollow = noFollow,
        hold: Duration = InstallHold.APP,
        requests: WatchRequests = testRequests(),
        isDismissed: suspend () -> Boolean = { flag.stored },
        setDismissed: suspend (Boolean) -> Unit = {
            flag.stored = it
            flag.writes += it
        },
    ): HomeWatchCard {
        val on = UnconfinedTestDispatcher(testScheduler)
        return HomeWatchCard(
            source = source,
            isDismissed = isDismissed,
            setDismissed = setDismissed,
            scope = CoroutineScope(SupervisorJob() + on),
            requests = requests,
            follow = follow,
            hold = hold,
            readOn = on,
        )
    }

    /** Home in front until the job is cancelled. */
    private fun TestScope.front(card: HomeWatchCard): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { card.whileResumed() }

    /** The card's tap as the screen makes it: a touch on Home, then the tap. */
    private fun HomeWatchCard.tap() {
        touched()
        open()
    }

    // -- The readings and the entry rule (Task 5) ----------------------------------------------

    @Test
    fun settingsOrderPutsTheInstallFirst() {
        assertEquals(WatchCardStep.INSTALL to galaxy, watchCardOffer(WatchTargets(install = galaxy, addFace = pixel)))
        assertEquals(WatchCardStep.ADD_FACE to pixel, watchCardOffer(face(pixel)))
        assertNull(watchCardOffer(WatchTargets.NONE))
    }

    @Test
    fun theDrawingTellsTheStep() {
        assertEquals(WatchLook.GHOST, installCard().look)
        assertEquals(WatchLook.FAINT, faceCard().look)
        val answers = listOf(null, WatchCardAnswer.ASKING, WatchCardAnswer.CONTINUE, WatchCardAnswer.UNREACHABLE)
        assertEquals(listOf(false, true, true, false), answers.map { installCard(it).ringed }, "the ring only while the watch answers")
        assertEquals(listOf(false, true, true, false), answers.map { faceCard(it).ringed })
        assertEquals(WatchAction.INSTALL, WatchCardStep.INSTALL.action)
        assertEquals(WatchAction.ADD_FACE, WatchCardStep.ADD_FACE.action)
    }

    @Test
    fun aWatchWithoutTaqwaShowsTheInstallCardNamedForIt() = runTest {
        val card = cardFor(Watch(install()).source)
        front(card)
        runCurrent()
        assertEquals(installCard(), card.card.value)
    }

    @Test
    fun aWatchWithTheFaceToAddShowsTheFaceCard() = runTest {
        val card = cardFor(Watch(face()).source)
        front(card)
        runCurrent()
        assertEquals(faceCard(), card.card.value)
    }

    @Test
    fun noWatchShowsNoCard() = runTest {
        val watch = Watch(WatchTargets.NONE)
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value)
        assertEquals(1, watch.source.asked)
    }

    @Test
    fun aReadingAfterTheFirstTouchWaitsForTheNextResume() = runTest {
        val reading = CompletableDeferred<WatchTargets>()
        val card = cardFor(FakeWatchTargetsSource(answer = { reading.await() }))
        val home = front(card)
        runCurrent()
        card.touched()
        reading.complete(install())
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value, "the Qibla card stays where the thumb found it")
        home.cancel()
        runCurrent()
        front(card)
        runCurrent()
        assertEquals(installCard(), card.card.value)
    }

    /** The nit of 20 November: a touch while the flag is still being read is this visit's own. */
    @Test
    fun aTouchWhileTheFlagIsReadStillKeepsTheCardOut() = runTest {
        val flagRead = CompletableDeferred<Boolean>()
        val card = cardFor(Watch(install()).source, isDismissed = { flagRead.await() })
        front(card)
        runCurrent()
        card.touched()
        flagRead.complete(false)
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value, "the touch during the flag's read was forgotten")
    }

    @Test
    fun aCardOnScreenChangesInPlaceAfterATouch() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        card.touched()
        watch.rows = face()
        watch.change()
        runCurrent()
        assertEquals(faceCard(), card.card.value)
    }

    @Test
    fun aCardFoldsWhenNoRowApplies() = runTest {
        val watch = Watch(face())
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value)
    }

    /** Taqwa just installed, its face not yet published: no row at all, held for the app's 20 s. */
    @Test
    fun theInstallCardIsHeldWhileTheWatchPublishesItsFace() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        card.touched()
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        advanceTimeBy(19.seconds)
        assertEquals(installCard(), card.card.value)
        watch.rows = face()
        watch.change()
        runCurrent()
        assertEquals(faceCard(), card.card.value)
    }

    /** A card nobody tapped, folded by the hold after a touch, waits for the next resume. */
    @Test
    fun aFoldedCardComesBackOnlyAtAResumeBeforeATouch() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source, hold = 300.milliseconds)
        val home = front(card)
        runCurrent()
        card.touched()
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value, "folded at the hold's end")
        watch.rows = face()
        watch.change()
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value, "not put back after the touch")
        home.cancel()
        runCurrent()
        front(card)
        runCurrent()
        assertEquals(faceCard(), card.card.value)
    }

    @Test
    fun aReadingThatFailsKeepsTheCard() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        watch.source.answer = { error("Play services") }
        watch.change()
        runCurrent()
        assertEquals(installCard(), card.card.value)
    }

    /**
     * From the Qibla screen, another tab or the background: the card is there before any reading,
     * and a reading that never answers keeps it past its 3 s bound (the review of 20 November).
     */
    @Test
    fun theCardKeepsItsPlaceWhenHomeComesBack() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source)
        val home = front(card)
        runCurrent()
        home.cancel()
        runCurrent()
        watch.source.answer = { awaitCancellation() }
        front(card)
        runCurrent()
        assertEquals(installCard(), card.card.value)
        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(installCard(), card.card.value, "the slow reading folded the card")
    }

    /** Back on Home between the install and the face: the card is held as a reading in place is. */
    @Test
    fun aResumeBetweenTheInstallAndTheFaceHoldsTheCard() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source)
        val home = front(card)
        runCurrent()
        home.cancel()
        runCurrent()
        watch.rows = WatchTargets.NONE
        front(card)
        runCurrent()
        card.touched()
        advanceTimeBy(10.seconds)
        assertEquals(installCard(), card.card.value, "the resume's reading of no row folded the card")
        watch.rows = face()
        watch.change()
        runCurrent()
        assertEquals(faceCard(), card.card.value)
    }

    /** Only the install is held: a face card whose resume reads no row has been taken, and folds. */
    @Test
    fun aResumeThatReadsNoRowFoldsAFaceCardAtOnce() = runTest {
        val watch = Watch(face())
        val card = cardFor(watch.source)
        val home = front(card)
        runCurrent()
        home.cancel()
        runCurrent()
        watch.rows = WatchTargets.NONE
        front(card)
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value)
    }

    /** The review of 20 November: once Home is touched with no card on it, nothing more is read. */
    @Test
    fun aTouchWithNoCardEndsTheVisitsReadings() = runTest {
        val watch = Watch(WatchTargets.NONE)
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        assertEquals(1, watch.source.asked)
        card.touched()
        watch.change()
        runCurrent()
        assertEquals(1, watch.source.asked, "the watch was read again for a card that could not show")
    }

    // -- A tap and its answer (Task 6) ---------------------------------------------------------

    @Test
    fun aTapAsksTheWatchOnceAndSaysContinueWhenTaken() = runTest {
        val taken = CompletableDeferred<Boolean>()
        val watch = Watch(install())
        watch.source.opens = { _, _ -> taken.await() }
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        card.open()
        card.open()
        assertEquals(installCard(WatchCardAnswer.ASKING, tapped = true), card.card.value)
        assertEquals(listOf(galaxy to WatchAction.INSTALL), watch.source.opened, "the second tap sent nothing")
        taken.complete(true)
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.CONTINUE, tapped = true), card.card.value)
    }

    /** Ruling 72 item 3: still on its way at the 10 s bound is "Continue on your watch". */
    @Test
    fun aTapStillOnItsWayAtTheBoundSaysContinueAndATapMeanwhileSendsNothing() = runTest {
        val watch = Watch(install())
        watch.source.opens = { _, _ -> awaitCancellation() }
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        card.open()
        advanceTimeBy(10.seconds)
        runCurrent()
        card.open()
        assertEquals(installCard(WatchCardAnswer.CONTINUE, tapped = true), card.card.value)
        assertEquals(1, watch.source.opened.size)
    }

    @Test
    fun aRefusedTapSaysCouldNotReachAndTryAgainSendsAnew() = runTest {
        val watch = Watch(face())
        watch.source.opens = { _, _ -> false }
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        card.open()
        runCurrent()
        assertEquals(faceCard(WatchCardAnswer.UNREACHABLE, tapped = true), card.card.value)
        card.open()
        runCurrent()
        assertEquals(2, watch.source.opened.size, "Try again sent anew")
    }

    @Test
    fun aFreshResumeClearsTheAnswerAndALateAnswerIsDropped() = runTest {
        val taken = CompletableDeferred<Boolean>()
        val watch = Watch(install())
        watch.source.opens = { _, _ -> taken.await() }
        val card = cardFor(watch.source)
        val home = front(card)
        runCurrent()
        card.open()
        home.cancel()
        runCurrent()
        front(card)
        runCurrent()
        assertEquals(installCard(), card.card.value, "the last visit's answer and tap are not this one's")
        taken.complete(true)
        runCurrent()
        assertEquals(installCard(), card.card.value, "the last visit's answer is not this one's")
    }

    /**
     * A new step drops the last one's answer, and is said as it comes to whoever tapped (the review
     * of 20 November: a screen reader heard nothing when Install became Add the face).
     */
    @Test
    fun anAnswerBelongsToItsStepAndWatchAndANewStepIsSaid() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        card.open()
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.CONTINUE, tapped = true), card.card.value)
        watch.rows = face()
        watch.change()
        runCurrent()
        assertEquals(faceCard(tapped = true), card.card.value)
    }

    /** As Settings: a tap is followed by readings, so Install becomes Add the face in place. */
    @Test
    fun aTapIsFollowedByReadings() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source, follow = TapFollow(every = 100.milliseconds, lasting = 500.milliseconds))
        front(card)
        runCurrent()
        card.tap()
        runCurrent()
        watch.rows = face()
        advanceTimeBy(200.milliseconds)
        runCurrent()
        assertEquals(faceCard(tapped = true), card.card.value)
    }

    /** Settings' tap on the same watch joins the card's request: the watch never opens twice. */
    @Test
    fun aCardTapAndASettingsTapJoinOneRequest() = runTest {
        val taken = CompletableDeferred<Boolean>()
        val watch = Watch(install())
        watch.source.opens = { _, _ -> taken.await() }
        val requests = testRequests()
        val card = cardFor(watch.source, requests = requests)
        front(card)
        runCurrent()
        card.open()
        runCurrent()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            openOnWatch(watch.source, galaxy, WatchAction.INSTALL, requests = requests).collect { }
        }
        runCurrent()
        assertEquals(1, watch.source.opened.size)
    }

    /** Two watches: the install for one; once it has Taqwa, the other's face, its answer cleared. */
    @Test
    fun withTwoWatchesTheInstallGoesFirstThenTheOthersFace() = runTest {
        val watch = Watch(WatchTargets(install = galaxy, addFace = pixel))
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        assertEquals(installCard(), card.card.value)
        card.tap()
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.CONTINUE, tapped = true), card.card.value)
        watch.rows = face(pixel)
        watch.change()
        runCurrent()
        assertEquals(faceCard(tapped = true, on = pixel), card.card.value)
        card.open()
        runCurrent()
        assertEquals(listOf(galaxy to WatchAction.INSTALL, pixel to WatchAction.ADD_FACE), watch.source.opened)
    }

    // -- A tapped install card waits for its face (the review of 20 November) ------------------

    /**
     * Reinstalled on a watch the phone fed before, Taqwa publishes its face only once its app is
     * started, which can be minutes after the install: the card the user tapped keeps its place,
     * saying "Continue on your watch", until the face takes it over in place.
     */
    @Test
    fun aTappedInstallCardWaitsThroughItsFollowUpForTheFace() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source, follow = TapFollow.APP)
        front(card)
        runCurrent()
        card.tap()
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.CONTINUE, tapped = true), card.card.value)
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.CONTINUE, tapped = true), card.card.value, "folded before the face came")
        watch.rows = face()
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(faceCard(tapped = true), card.card.value)
    }

    @Test
    fun aTappedInstallCardFoldsWhenItsFollowUpEndsWithNoRow() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source, follow = TapFollow.APP)
        front(card)
        runCurrent()
        card.tap()
        runCurrent()
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        advanceTimeBy(179.seconds)
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.CONTINUE, tapped = true), card.card.value)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value)
    }

    /** A tap the watch never took holds nothing more: the install is held its 20 s, as Settings'. */
    @Test
    fun aRefusedTapHoldsTheInstallCardNoLonger() = runTest {
        val watch = Watch(install())
        watch.source.opens = { _, _ -> false }
        val card = cardFor(watch.source, follow = TapFollow.APP)
        front(card)
        runCurrent()
        card.tap()
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.UNREACHABLE, tapped = true), card.card.value)
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        advanceTimeBy(26.seconds)
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value)
    }

    /** Away from Home and back within the follow-up: the tapped card still waits for its face. */
    @Test
    fun aTappedInstallCardStillWaitsAfterHomeIsLeftAndComesBack() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source, follow = TapFollow.APP)
        val home = front(card)
        runCurrent()
        card.tap()
        runCurrent()
        home.cancel()
        runCurrent()
        watch.rows = WatchTargets.NONE
        advanceTimeBy(40.seconds)
        front(card)
        runCurrent()
        card.touched()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(installCard(), card.card.value, "folded before the follow-up's end")
        watch.rows = face()
        watch.change()
        runCurrent()
        assertEquals(faceCard(), card.card.value)
    }

    @Test
    fun undoWhileATappedInstallCardWaitsBringsItBack() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source, follow = TapFollow.APP)
        front(card)
        runCurrent()
        card.tap()
        runCurrent()
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        advanceTimeBy(30.seconds)
        runCurrent()
        card.dismiss()
        card.undo()
        assertEquals(installCard(), card.card.value)
    }

    // -- The x, Undo and gone (Task 7) ---------------------------------------------------------

    @Test
    fun theXShowsTheUndoLineAndRetiresTheCardForGoodAfterSixSeconds() = runTest {
        val flag = Flag()
        val watch = Watch(install())
        val card = cardFor(watch.source, flag)
        val home = front(card)
        runCurrent()
        card.dismiss()
        advanceTimeBy(5_900.milliseconds)
        assertEquals(WatchCardUi.Dismissed, card.card.value)
        assertEquals(listOf(true), flag.writes)
        advanceTimeBy(200.milliseconds)
        runCurrent()
        assertEquals(WatchCardUi.Gone, card.card.value)
        home.cancel()
        runCurrent()
        front(card)
        runCurrent()
        assertEquals(WatchCardUi.Gone, card.card.value)
        assertEquals(1, watch.source.asked, "Home never reads the watch again")
    }

    /** The reader's own time to act (the review of 20 November): the line stays as long as asked. */
    @Test
    fun aLongerUndoWindowIsHonoured() = runTest {
        val card = cardFor(Watch(install()).source)
        front(card)
        runCurrent()
        card.dismiss(30.seconds)
        advanceTimeBy(29.seconds)
        runCurrent()
        assertEquals(WatchCardUi.Dismissed, card.card.value)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(WatchCardUi.Gone, card.card.value)
    }

    /** No end at all (TalkBack below Android 10): the line stays until Home leaves. */
    @Test
    fun anEndlessUndoWindowLastsUntilHomeLeaves() = runTest {
        val flag = Flag()
        val card = cardFor(Watch(install()).source, flag)
        val home = front(card)
        runCurrent()
        card.dismiss(Duration.INFINITE)
        advanceTimeBy(1.minutes * 60)
        runCurrent()
        assertEquals(WatchCardUi.Dismissed, card.card.value)
        home.cancel()
        runCurrent()
        assertEquals(WatchCardUi.Gone, card.card.value)
        assertEquals(true, flag.stored)
    }

    /** The review of 20 November: the line never folds under a finger; it folds as the finger lifts. */
    @Test
    fun theUndoLineWaitsForTheFingerToLiftBeforeItFolds() = runTest {
        val card = cardFor(Watch(install()).source)
        front(card)
        runCurrent()
        card.dismiss()
        advanceTimeBy(5.seconds)
        card.pressed(true)
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(WatchCardUi.Dismissed, card.card.value, "folded under the finger")
        card.pressed(false)
        assertEquals(WatchCardUi.Gone, card.card.value)
    }

    /** Undo pressed as the time ran out: the press is taken, and its lift folds nothing. */
    @Test
    fun anUndoPressedAsTheTimeRunsOutIsTaken() = runTest {
        val flag = Flag()
        val card = cardFor(Watch(install()).source, flag)
        front(card)
        runCurrent()
        card.dismiss()
        advanceTimeBy(5_900.milliseconds)
        card.pressed(true)
        advanceTimeBy(200.milliseconds)
        runCurrent()
        card.undo()
        card.pressed(false)
        runCurrent()
        assertEquals(installCard(), card.card.value)
        assertEquals(false, flag.stored)
    }

    @Test
    fun undoWithinTheWindowBringsTheCardBackAndClearsTheFlag() = runTest {
        val flag = Flag()
        val card = cardFor(Watch(install()).source, flag)
        front(card)
        runCurrent()
        card.dismiss()
        advanceTimeBy(3.seconds)
        card.undo()
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(installCard(), card.card.value)
        assertEquals(listOf(true, false), flag.writes)
        assertEquals(false, flag.stored)
    }

    /**
     * The nit of 20 November: Undo, then Home left at once, before the flag's write of "not
     * dismissed" is done: the card is still there when Home comes back.
     */
    @Test
    fun undoThenLeavingAtOnceKeepsTheCard() = runTest {
        val flag = Flag()
        val written = CompletableDeferred<Unit>()
        val card = cardFor(
            Watch(install()).source,
            flag,
            setDismissed = { value ->
                if (!value) written.await()
                flag.stored = value
                flag.writes += value
            },
        )
        val home = front(card)
        runCurrent()
        card.dismiss()
        runCurrent()
        card.undo()
        home.cancel()
        runCurrent()
        front(card)
        runCurrent()
        written.complete(Unit)
        runCurrent()
        assertEquals(installCard(), card.card.value)
        assertEquals(false, flag.stored)
    }

    @Test
    fun leavingHomeDuringTheWindowEndsIt() = runTest {
        val flag = Flag()
        val card = cardFor(Watch(install()).source, flag)
        val home = front(card)
        runCurrent()
        card.dismiss()
        home.cancel()
        runCurrent()
        assertEquals(WatchCardUi.Gone, card.card.value)
        assertEquals(true, flag.stored)
    }

    @Test
    fun aCardDismissedInAnEarlierLaunchIsGoneAndReadsNothing() = runTest {
        val watch = Watch(install())
        val card = cardFor(watch.source, Flag(stored = true))
        front(card)
        runCurrent()
        assertEquals(WatchCardUi.Gone, card.card.value)
        assertEquals(0, watch.source.asked)
    }

    /**
     * Set up: the face taken, so no row applies, which a face card takes at once. (An install card
     * is held through a reading of no row for the app's 20 s, as Settings' row is: that is the watch
     * publishing its face, not a watch set up.)
     */
    @Test
    fun undoAfterTheWatchWasSetUpShowsNothing() = runTest {
        val flag = Flag()
        val watch = Watch(face())
        val card = cardFor(watch.source, flag)
        front(card)
        runCurrent()
        assertEquals(faceCard(), card.card.value)
        card.dismiss()
        watch.rows = WatchTargets.NONE
        watch.change()
        runCurrent()
        card.undo()
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value)
        assertEquals(false, flag.stored)
    }

    @Test
    fun dismissWhileATapIsOnItsWayThenUndoJoinsTheRequest() = runTest {
        val taken = CompletableDeferred<Boolean>()
        val watch = Watch(install())
        watch.source.opens = { _, _ -> taken.await() }
        val card = cardFor(watch.source)
        front(card)
        runCurrent()
        card.open()
        card.dismiss()
        card.undo()
        assertEquals(installCard(), card.card.value, "Undo brought back the old answer or its tap")
        card.open()
        runCurrent()
        assertEquals(installCard(WatchCardAnswer.ASKING, tapped = true), card.card.value)
        assertEquals(1, watch.source.opened.size, "the tap after Undo joined the request on its way")
    }

    @Test
    fun theXOnNothingDoesNothing() = runTest {
        val flag = Flag()
        val card = cardFor(Watch(WatchTargets.NONE).source, flag)
        front(card)
        runCurrent()
        card.dismiss()
        runCurrent()
        assertEquals(WatchCardUi.Hidden, card.card.value)
        assertTrue(flag.writes.isEmpty())
    }

    // -- The reader's time to act (the review of 20 November) ----------------------------------

    /** Android's "Time to take action", as Compose hands it on: [millis] where the reader set it. */
    private class TimeToAct(private val millis: Long) : AccessibilityManager {
        val asked = mutableListOf<List<Boolean>>()
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            asked += listOf(containsIcons, containsText, containsControls)
            return millis
        }
    }

    @Test
    fun theUndoLineStaysAsLongAsTheReaderAskedAndNeverLessThanSixSeconds() {
        assertEquals(6.seconds, undoWindow(null), "no word from the platform")
        val reader = TimeToAct(60_000)
        assertEquals(60.seconds, undoWindow(reader))
        assertEquals(listOf(listOf(false, true, true)), reader.asked, "asked for a line of words with a button")
        assertEquals(6.seconds, undoWindow(TimeToAct(6_000)))
        assertEquals(6.seconds, undoWindow(TimeToAct(1_000)), "shorter than the owner's six seconds")
        assertEquals(Duration.INFINITE, undoWindow(TimeToAct(Long.MAX_VALUE)), "TalkBack below Android 10")
    }

    // -- As the app makes it (the review of 20 November) ---------------------------------------

    /**
     * The app's holder keeps its x in the settings store's own flag, [SettingsRepository.homeWatchCardDismissed],
     * and in no other: a holder made from the same store later is gone and reads nothing.
     */
    @Test
    fun theAppsCardKeepsItsXInItsOwnFlag() = runTest {
        val path = "/tmp/taqwa-test-home-watch-card-${Random.nextULong()}.preferences_pb".toPath()
        FileSystem.SYSTEM.delete(path, mustExist = false)
        val settings = SettingsRepository(PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) { path })
        val watch = Watch(install())
        val on = UnconfinedTestDispatcher(testScheduler)
        val card = homeWatchCard(settings, CoroutineScope(SupervisorJob() + on), watch.source, readOn = on)
        val home = backgroundScope.launch(on) { card.whileResumed() }
        assertEquals(installCard(), withTimeoutOrNull(5.seconds) { card.card.first { it == installCard() } })
        card.dismiss()
        val kept = withTimeoutOrNull(5.seconds) { settings.homeWatchCardDismissed.first { it } }
        assertEquals(true, kept, "the x was not kept in Home's own flag")
        assertEquals(false, settings.cautiousCardSeen.first(), "the cautious card's flag")
        assertEquals(false, settings.sunniCardSeen.first(), "the Sunni card's flag")
        home.cancel()
        val later = homeWatchCard(settings, CoroutineScope(SupervisorJob() + on), watch.source, readOn = on)
        backgroundScope.launch(on) { later.whileResumed() }
        val gone = withTimeoutOrNull(5.seconds) { later.card.first { it == WatchCardUi.Gone } }
        assertEquals(WatchCardUi.Gone, gone, "a later holder from the same store brought the card back")
        assertEquals(1, watch.source.asked, "the later holder read the watch")
        FileSystem.SYSTEM.delete(path, mustExist = false)
    }
}
