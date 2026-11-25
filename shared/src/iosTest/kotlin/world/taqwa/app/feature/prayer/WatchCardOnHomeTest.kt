package world.taqwa.app.feature.prayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LocalScreenReaderOverride
import world.taqwa.app.feature.settings.TapFollow
import world.taqwa.app.i18n.isolated
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.prayer_card_cautious_title
import world.taqwa.app.resources.prayer_no_location_title
import world.taqwa.app.resources.prayer_polar_line
import world.taqwa.app.resources.prayer_watch_about
import world.taqwa.app.resources.prayer_watch_dismiss
import world.taqwa.app.resources.prayer_watch_dismissed
import world.taqwa.app.resources.prayer_watch_install_on
import world.taqwa.app.resources.prayer_watch_title_install
import world.taqwa.app.resources.prayer_watch_undo
import world.taqwa.app.resources.qibla_title
import world.taqwa.app.watchfeed.FakeWatchTargetsSource
import world.taqwa.app.watchfeed.WatchAction
import world.taqwa.app.watchfeed.WatchRequests
import world.taqwa.app.watchfeed.WatchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Home's watch card on the Prayer screen: in the one-time card's slot, above the Qibla card, with
 * the one-time card's gaps (14 dp, the card, 14 dp; 6 dp under a line), never beside a one-time
 * card and never in a launch that showed one, nowhere without a place, and in the right pane
 * sideways. Every touch and scroll on Home is reported, because the card is put in only before the
 * first. Then the card wired as the app wires it: read when Home comes to the front, put in only if
 * that reading comes before a touch, kept in its place when Home is left and comes back, and its tap,
 * x and Undo reaching the watch and the flag.
 *
 * Expected words come from the resources: the simulator's language decides every word on screen.
 */
@OptIn(ExperimentalTestApi::class)
class WatchCardOnHomeTest {

    private suspend fun installLabel(): String = listOf(
        getString(Res.string.prayer_watch_title_install),
        getString(Res.string.prayer_watch_install_on, isolated(galaxy.name)),
        getString(Res.string.prayer_watch_about),
    ).joinToString(". ")

    /** The Qibla card is one clickable row that fills the card, so its top is the card's. */
    private suspend fun ComposeUiTest.qiblaTop(): Dp =
        onNodeWithText(getString(Res.string.qibla_title)).getUnclippedBoundsInRoot().top

    private suspend fun ComposeUiTest.card(): DpRect =
        onNodeWithContentDescription(installLabel()).getUnclippedBoundsInRoot()

    private suspend fun ComposeUiTest.cards(): Int = cardsLabelled(installLabel())

    /** How many cards with [label] are on screen; not suspending, for a wait's condition. */
    private fun ComposeUiTest.cardsLabelled(label: String): Int =
        onAllNodesWithContentDescription(label).fetchSemanticsNodes().size

    // -- On the Prayer screen (Task 11) ---------------------------------------------------------

    @Test
    fun theCardSitsBetweenTheTimesAndTheQiblaCard() = runComposeUiTest {
        var watchCard by mutableStateOf<WatchCardUi>(WatchCardUi.Hidden)
        setContent { Home(readyHome(), watchCard) }
        // With no card the Qibla card sits 14 dp under the times card: where the card's top must be.
        val underTheTimes = qiblaTop()
        watchCard = cardOf()
        waitForIdle()
        val c = card()
        near(underTheTimes, c.top, "the card's top, 14 dp under the times card")
        near(c.bottom + 14.dp, qiblaTop(), "the Qibla card's top, 14 dp under the card")
    }

    @Test
    fun theGapsMatchTheOneTimeCardsUnderALine() = runComposeUiTest {
        setContent { Home(readyHome(polar = true), cardOf()) }
        val line = onNodeWithContentDescription(getString(Res.string.prayer_polar_line)).getUnclippedBoundsInRoot()
        val c = card()
        near(line.bottom + 6.dp, c.top, "the card's top under the polar line")
        near(c.bottom + 14.dp, qiblaTop(), "the Qibla card's top")
    }

    @Test
    fun aOneTimeCardTakesTheSlotAndTheWatchCardWaits() = runComposeUiTest {
        setContent { Home(readyHome(oneTimeCard = OneTimeCard.CAUTIOUS), cardOf()) }
        onAllNodesWithText(getString(Res.string.prayer_card_cautious_title)).assertCountEquals(1)
        assertEquals(0, cards())
        onAllNodesWithContentDescription(getString(Res.string.prayer_watch_dismiss)).assertCountEquals(0)
    }

    @Test
    fun aLaunchThatHadAOneTimeCardShowsNoWatchCard() = runComposeUiTest {
        setContent { Home(readyHome(oneTimeCardThisLaunch = true), cardOf()) }
        assertEquals(0, cards())
    }

    @Test
    fun noCardWithoutAPlace() = runComposeUiTest {
        setContent { Home(PrayerUiState.NeedsLocation, cardOf()) }
        onAllNodesWithText(getString(Res.string.prayer_no_location_title)).assertCountEquals(1)
        assertEquals(0, cards())
    }

    @Test
    fun aTouchOrAScrollOnHomeIsReported() {
        runComposeUiTest {
            val taps = CardTaps()
            setContent { Home(readyHome(), WatchCardUi.Hidden, taps) }
            waitForIdle()
            assertEquals(0, taps.touched, "nothing before the touch")
            onRoot().performTouchInput {
                down(Offset(20f, 20f))
                up()
            }
            waitForIdle()
            assertTrue(taps.touched >= 1, "the touch was not reported")
        }
        runComposeUiTest {
            val taps = CardTaps()
            // Short, so the page scrolls.
            setContent { Home(readyHome(), WatchCardUi.Hidden, taps, height = 480.dp) }
            waitForIdle()
            assertEquals(0, taps.touched, "nothing before the scroll")
            onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100f) }
            waitForIdle()
            assertTrue(taps.touched >= 1, "the scroll was not reported")
        }
    }

    /** The review of 20 November: the undo line folds only once the last finger has lifted. */
    @Test
    fun aPressAndTheLastLiftAreReported() = runComposeUiTest {
        val taps = CardTaps()
        setContent { Home(readyHome(), WatchCardUi.Hidden, taps) }
        waitForIdle()
        onRoot().performTouchInput { down(Offset(20f, 20f)) }
        waitForIdle()
        assertEquals(listOf(true), taps.pressed, "the press")
        onRoot().performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf(true, false), taps.pressed, "the lift")
    }

    @Test
    fun sidewaysTheCardIsInTheRightPaneAboveTheQiblaCard() = runComposeUiTest {
        setContent { Home(readyHome(), cardOf(), width = 891.dp, height = 411.dp) }
        val c = card()
        assertTrue(c.left >= 891.dp / 2 - 0.5.dp, "the card starts at ${c.left}, not in the right pane")
        near(c.bottom + 14.dp, qiblaTop(), "the Qibla card's top under the card")
    }

    @Test
    fun theCardsButtonsReachTheirCallbacks() = runComposeUiTest {
        val taps = CardTaps()
        var watchCard by mutableStateOf<WatchCardUi>(cardOf())
        setContent { Home(readyHome(), watchCard, taps) }
        onNodeWithContentDescription(installLabel()).performClick()
        assertEquals(1, taps.open)
        onNodeWithContentDescription(getString(Res.string.prayer_watch_dismiss)).performClick()
        assertEquals(1, taps.dismiss)
        assertEquals(1, taps.open, "the x opened the watch")
        watchCard = WatchCardUi.Dismissed
        waitForIdle()
        onNode(hasText(getString(Res.string.prayer_watch_undo)) and hasClickAction()).performClick()
        assertEquals(1, taps.undo)
    }

    // -- Wired as the app wires it (Task 12) ----------------------------------------------------

    /** The dismissal flag, in memory. */
    private class Flag(var stored: Boolean = false)

    /**
     * A holder as the app makes it, on the scene's own scope; its readings taken on the thread that
     * answers them, so a reading the test completes is on screen by the next idle.
     */
    @Composable
    private fun rememberCard(source: FakeWatchTargetsSource, flag: Flag = Flag()): HomeWatchCard {
        val scope = rememberCoroutineScope()
        return remember {
            HomeWatchCard(
                source = source,
                isDismissed = { flag.stored },
                setDismissed = { flag.stored = it },
                scope = scope,
                requests = WatchRequests(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)),
                follow = TapFollow(every = 1.seconds, lasting = Duration.ZERO),
                readOn = Dispatchers.Unconfined,
            )
        }
    }

    /** Home in [state] with [card] followed while [front] says Home is in front, its callbacks bound to the holder. */
    @Composable
    private fun Wired(card: HomeWatchCard, front: Front, state: PrayerUiState = readyHome()) {
        CompositionLocalProvider(LocalLifecycleOwner provides front) {
            FollowWatchCard(card, state)
            val shown by card.card.collectAsState()
            Home(
                state,
                shown,
                onOpen = card::open,
                onDismiss = card::dismiss,
                onUndo = card::undo,
                onTouched = card::touched,
                onPressed = card::pressed,
            )
        }
    }

    /**
     * The review of 20 November: Home reads no watch where no card can show, without a place or in
     * a launch that showed a one-time card.
     */
    @Test
    fun theWatchIsNotReadWhereNoCardCanShow() {
        for (state in listOf(readyHome(oneTimeCardThisLaunch = true), PrayerUiState.NeedsLocation)) {
            runComposeUiTest {
                val source = FakeWatchTargetsSource(answer = { installOn() })
                setContent { Wired(rememberCard(source), Front(), state) }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                assertEquals(0, source.asked, "the watch was read on $state")
            }
        }
    }

    /**
     * Under a screen reader the x leaves the focus on Undo, and Undo puts it back on the card: the
     * whole round trip a TalkBack user makes, as the app wires it.
     */
    @Test
    fun underAScreenReaderTheXAndUndoKeepTheFocusWhereTheCardIs() = runComposeUiTest {
        val label = installLabel()
        val source = FakeWatchTargetsSource(answer = { installOn() })
        setContent {
            CompositionLocalProvider(LocalScreenReaderOverride provides true) { Wired(rememberCard(source), Front()) }
        }
        waitUntil("the card", timeoutMillis = 5_000) { cardsLabelled(label) == 1 }
        onNodeWithContentDescription(getString(Res.string.prayer_watch_dismiss)).performClick()
        val undo = hasText(getString(Res.string.prayer_watch_undo)) and hasClickAction()
        waitUntil("Undo focused", timeoutMillis = 5_000) { onAllNodes(undo and isFocused()).fetchSemanticsNodes().size == 1 }
        onNode(undo).performClick()
        waitUntil("the card focused", timeoutMillis = 5_000) {
            onAllNodes(hasContentDescription(label) and isFocused()).fetchSemanticsNodes().size == 1
        }
    }

    @Test
    fun aReadingBeforeAnyTouchPutsTheCardInAboveTheQiblaCard() = runComposeUiTest {
        val label = installLabel()
        val reading = CompletableDeferred<WatchTargets>()
        val source = FakeWatchTargetsSource(answer = { reading.await() })
        setContent { Wired(rememberCard(source), Front()) }
        waitUntil("the reading asked", timeoutMillis = 5_000) { source.asked == 1 }
        waitForIdle()
        val before = qiblaTop()
        reading.complete(installOn())
        waitUntil("the card", timeoutMillis = 5_000) { cardsLabelled(label) == 1 }
        waitForIdle()
        near(before + card().height + 14.dp, qiblaTop(), "the Qibla card moved down by the card and its gap")
    }

    @Test
    fun aTouchBeforeTheReadingLeavesTheQiblaCardWhereItIsUntilTheNextResume() = runComposeUiTest {
        val label = installLabel()
        val reading = CompletableDeferred<WatchTargets>()
        val source = FakeWatchTargetsSource(answer = { reading.await() })
        val front = Front()
        setContent { Wired(rememberCard(source), front) }
        waitUntil("the reading asked", timeoutMillis = 5_000) { source.asked == 1 }
        waitForIdle()
        val before = qiblaTop()
        onRoot().performTouchInput {
            down(Offset(20f, 20f))
            up()
        }
        waitForIdle()
        reading.complete(installOn())
        waitForIdle()
        assertEquals(0, cards(), "the card was put in after the touch")
        near(before, qiblaTop(), "the Qibla card moved under the thumb")
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("the card at the next resume", timeoutMillis = 5_000) { cardsLabelled(label) == 1 }
    }

    @Test
    fun theCardKeepsItsPlaceWhenHomeIsLeftAndComesBack() = runComposeUiTest {
        val label = installLabel()
        var answer: suspend () -> WatchTargets = { installOn() }
        val source = FakeWatchTargetsSource(answer = { answer() })
        val front = Front()
        var onHome by mutableStateOf(true)
        setContent {
            // Held above Home, as App.kt holds it: the walk to the Qibla screen disposes Home alone.
            val card = rememberCard(source)
            if (onHome) Wired(card, front)
        }
        waitUntil("the card", timeoutMillis = 5_000) { cardsLabelled(label) == 1 }
        onHome = false
        waitForIdle()
        assertEquals(0, cards())
        answer = { awaitCancellation() }
        mainClock.autoAdvance = false
        onHome = true
        mainClock.advanceTimeByFrame()
        assertEquals(1, cards(), "the card is not there on Home's first frame back")
    }

    @Test
    fun tappingTheCardAsksTheWatchAndTheXThenUndoBringsItBack() = runComposeUiTest {
        val label = installLabel()
        val source = FakeWatchTargetsSource(answer = { installOn() })
        val flag = Flag()
        setContent { Wired(rememberCard(source, flag), Front()) }
        waitUntil("the card", timeoutMillis = 5_000) { cardsLabelled(label) == 1 }
        onNodeWithContentDescription(installLabel()).performClick()
        waitForIdle()
        assertEquals(listOf(galaxy to WatchAction.INSTALL), source.opened)
        onNodeWithContentDescription(getString(Res.string.prayer_watch_dismiss)).performClick()
        val line = getString(Res.string.prayer_watch_dismissed)
        waitUntil("the undo line", timeoutMillis = 5_000) { onAllNodesWithText(line).fetchSemanticsNodes().size == 1 }
        waitForIdle()
        assertEquals(true, flag.stored)
        onNode(hasText(getString(Res.string.prayer_watch_undo)) and hasClickAction()).performClick()
        waitUntil("the card again", timeoutMillis = 5_000) { cardsLabelled(label) == 1 }
        waitForIdle()
        assertEquals(false, flag.stored)
    }
}
