package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.getString
import world.taqwa.app.design.LightColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.settings_add_to_apple_watch
import world.taqwa.app.resources.settings_group_watch
import world.taqwa.app.resources.settings_watch_continue
import world.taqwa.app.resources.settings_watch_face
import world.taqwa.app.resources.settings_watch_install
import world.taqwa.app.resources.settings_watch_unreachable
import world.taqwa.app.watchface.WatchFaceOffer
import world.taqwa.app.watchface.WatchFaceResult
import world.taqwa.app.watchfeed.WatchAction
import world.taqwa.app.watchfeed.WatchRequests
import world.taqwa.app.watchfeed.WatchTarget
import world.taqwa.app.watchfeed.WatchTargets
import world.taqwa.app.watchfeed.WatchTargetsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Settings › Watch (plan 5 Task 6): the section between App and About, shown while a row applies.
 * On Android its rows are "Install on your watch", named after the watch, and "Add the Taqwa face";
 * on the iPhone it holds plan 4's "Add to Apple Watch" alone. The rows are asked again each time
 * Settings comes back to the front, and while it stays there, each time the watch side says it
 * changed and for a while after a tap (the owner's report of 9 November). A tap's answer is a line
 * of its own under its row, whole however narrow the phone and large the text, and said as it comes.
 *
 * Common code over a fake source and a fake offer, so it runs here, on the simulator target, where a
 * Compose scene composes under kotlin.test. Each test's taps go through requests of its own and a
 * bound of [BOUND] (Ruling 74 item 12): no test shares the app's `WatchRequests.process`, and none
 * waits the real 10 s. No tap is followed by readings ([NO_FOLLOW]) unless the test is about that,
 * and an install row is held for the app's 20 s ([InstallHold.APP]), longer than any test waits,
 * unless the test waits for the hold's end.
 */
@OptIn(ExperimentalTestApi::class)
class WatchSectionTest {

    private val galaxy = WatchTarget(nodeId = "4a1f", name = "Galaxy Watch8 Classic (5F2A)")
    private val pixel = WatchTarget(nodeId = "77c0", name = "Google Pixel Watch 4 (0D3B)")

    /**
     * A source that answers [targets] (or throws while [fails]) and each tap with [opens]
     * (suspending until it is given), or, without it, with [reaches] for the tap's action. It says
     * the watch side changed at each [change], to Settings while it [listening].
     */
    private class Source(
        var targets: WatchTargets,
        var opens: CompletableDeferred<Boolean>? = null,
        val reaches: (WatchAction) -> Boolean = { true },
    ) : WatchTargetsSource {
        var asked = 0
        var fails = false
        val opened = mutableListOf<Pair<WatchTarget, WatchAction>>()
        private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override suspend fun targets(): WatchTargets {
            asked += 1
            check(!fails) { "Play services answered an error" }
            return targets
        }
        override suspend fun open(target: WatchTarget, action: WatchAction): Boolean {
            opened += target to action
            return opens?.await() ?: reaches(action)
        }
        override fun changes(): Flow<Unit> = changed
        val listening: Int get() = changed.subscriptionCount.value
        fun change() {
            check(changed.tryEmit(Unit)) { "nobody listening" }
        }
    }

    /** An offer that says [offered], and says it changed at each [change] (the iPhone's session). */
    private class Offer(var offered: Boolean) : WatchFaceOffer {
        var asked = 0
        private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override fun available(): Boolean {
            asked += 1
            return offered
        }
        override fun add(onDone: (WatchFaceResult) -> Unit) = onDone(WatchFaceResult.FAILED)
        override fun changes(): Flow<Unit> = changed
        val listening: Int get() = changed.subscriptionCount.value
        fun change() {
            check(changed.tryEmit(Unit)) { "nobody listening" }
        }
    }

    /** The app in front or not, as the test moves it. */
    private class Front : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        init { registry.currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
        fun leave() { registry.currentState = Lifecycle.State.CREATED }
        fun come() { registry.currentState = Lifecycle.State.RESUMED }
    }

    /**
     * Taps' requests of the test's own, as the app's are made: started on the tap's thread, in a
     * scope of the test's own. [expiry] is the app's 2 minutes unless a test needs it sooner.
     */
    private fun requests(expiry: Duration = WatchRequests.EXPIRY) =
        WatchRequests(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), expiry = expiry)

    /** Not TaqwaTheme: its system-bar effects crash the simulator test process. */
    @Composable
    private fun Section(
        source: WatchTargetsSource,
        offer: WatchFaceOffer,
        front: Front = Front(),
        requests: WatchRequests = remember { requests() },
        bound: Duration = BOUND,
        follow: TapFollow = NO_FOLLOW,
        hold: Duration = InstallHold.APP,
    ) {
        CompositionLocalProvider(LocalTaqwaColors provides LightColors, LocalLifecycleOwner provides front) {
            WatchSection(source, offer, requests, bound, follow, hold)
        }
    }

    /** The section on a phone [width] wide, with the reader's text at [fontScale] times. */
    @Composable
    private fun Phone(source: WatchTargetsSource, width: Dp, fontScale: Float) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.width(width)) { Section(source, Offer(offered = false)) }
        }
    }

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    /** Every line of [text] drawn to its end: none cut with an ellipsis, none below the room it has. */
    private fun ComposeUiTest.assertWhole(text: String) {
        val layout = layoutOf(text)
        assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "\"$text\" ends in an ellipsis")
        assertFalse(layout.didOverflowHeight, "\"$text\" is cut at the bottom")
    }

    /**
     * Both rows tapped, the install row's watch out of reach and the face row's watch reached, so
     * both answers are drawn at once; then each label and each answer is read whole.
     */
    private suspend fun ComposeUiTest.tapBothAndReadThemWhole() {
        val install = getString(Res.string.settings_watch_install)
        val face = getString(Res.string.settings_watch_face)
        val unreachable = getString(Res.string.settings_watch_unreachable)
        val going = getString(Res.string.settings_watch_continue)
        waitUntil("the rows", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onNodeWithText(install).performClick()
        onNodeWithText(face).performClick()
        waitUntil("both answers", timeoutMillis = 5_000) {
            onAllNodesWithText(unreachable).fetchSemanticsNodes().size == 1 && onAllNodesWithText(going).fetchSemanticsNodes().size == 1
        }
        waitForIdle()
        for (text in listOf(install, face, unreachable, going)) assertWhole(text)
    }

    @Test
    fun noWatchRowAndNoAppleFaceShowNoSection() = runComposeUiTest {
        val source = Source(WatchTargets.NONE)
        setContent { Section(source, Offer(offered = false)) }
        waitUntil("the targets asked", timeoutMillis = 5_000) { source.asked == 1 }
        waitForIdle()
        onAllNodesWithText(getString(Res.string.settings_group_watch)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.settings_watch_install)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.settings_watch_face)).assertCountEquals(0)
    }

    @Test
    fun aWatchWithoutTaqwaShowsTheInstallRowWithTheWatchsName() = runComposeUiTest {
        setContent { Section(Source(WatchTargets(install = galaxy, addFace = null)), Offer(offered = false)) }
        val install = getString(Res.string.settings_watch_install)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(install).fetchSemanticsNodes().size == 1 }
        // The heading screen readers jump by, and the row with the watch's name as its value.
        onAllNodes(hasText(getString(Res.string.settings_group_watch)) and isHeading()).assertCountEquals(1)
        onAllNodes(hasText(install) and hasText(galaxy.name)).assertCountEquals(1)
        onAllNodesWithText(getString(Res.string.settings_watch_face)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.settings_add_to_apple_watch)).assertCountEquals(0)
    }

    @Test
    fun aWatchThatHasTheFaceToAddShowsTheFaceRow() = runComposeUiTest {
        setContent { Section(Source(WatchTargets(install = null, addFace = galaxy)), Offer(offered = false)) }
        val face = getString(Res.string.settings_watch_face)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onAllNodes(hasText(getString(Res.string.settings_group_watch)) and isHeading()).assertCountEquals(1)
        onAllNodesWithText(getString(Res.string.settings_watch_install)).assertCountEquals(0)
    }

    @Test
    fun theAppleFaceAloneIsTheSection() = runComposeUiTest {
        setContent { Section(Source(WatchTargets.NONE), Offer(offered = true)) }
        val apple = getString(Res.string.settings_add_to_apple_watch)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(apple).fetchSemanticsNodes().size == 1 }
        onAllNodes(hasText(getString(Res.string.settings_group_watch)) and isHeading()).assertCountEquals(1)
        onAllNodesWithText(getString(Res.string.settings_watch_install)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.settings_watch_face)).assertCountEquals(0)
    }

    @Test
    fun aTapThatReachesTheWatchSaysContinueOnItAsItComes() = runComposeUiTest {
        val answer = CompletableDeferred<Boolean>()
        val source = Source(WatchTargets(install = galaxy, addFace = null), opens = answer)
        setContent { Section(source, Offer(offered = false)) }
        val install = getString(Res.string.settings_watch_install)
        val going = getString(Res.string.settings_watch_continue)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(install).fetchSemanticsNodes().size == 1 }
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(0)
        onNodeWithText(install).performClick()
        // A second tap while the watch is asked starts nothing more.
        onNodeWithText(install).performClick()
        answer.complete(true)
        waitUntil("the answer", timeoutMillis = 5_000) { onAllNodesWithText(going).fetchSemanticsNodes().size == 1 }
        assertEquals(listOf(galaxy to WatchAction.INSTALL), source.opened)
        // The answer is its own line, spoken as it comes, and the row keeps the watch's name.
        onAllNodes(polite and hasText(going)).assertCountEquals(1)
        onAllNodes(hasText(install) and hasText(galaxy.name)).assertCountEquals(1)
    }

    @Test
    fun aTapThatCannotReachTheWatchSaysSo() = runComposeUiTest {
        val source = Source(WatchTargets(install = null, addFace = galaxy), opens = CompletableDeferred(false))
        setContent { Section(source, Offer(offered = false)) }
        val face = getString(Res.string.settings_watch_face)
        val unreachable = getString(Res.string.settings_watch_unreachable)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onNodeWithText(face).performClick()
        waitUntil("the answer", timeoutMillis = 5_000) { onAllNodesWithText(unreachable).fetchSemanticsNodes().size == 1 }
        assertEquals(listOf(galaxy to WatchAction.ADD_FACE), source.opened)
        onAllNodes(polite and hasText(unreachable)).assertCountEquals(1)
        onAllNodesWithText(face).assertCountEquals(1)
    }

    /**
     * Ruling 72 item 3: a tap the watch's companion has not taken by the bound (the app's 10 s, here
     * [SHORT_BOUND]) is still on its way (P5.6's finding: Android holds the `REMOTE_INTENT` while the
     * companion is cached, and the watch opens the screen when it wakes). It says "Continue on your
     * watch", and a tap on the row then sends nothing more, so the watch never opens the face's
     * screen twice.
     */
    @Test
    fun aTapStillOnItsWayAtTheBoundSaysContinueAndSendsNothingMore() = runComposeUiTest {
        val companion = CompletableDeferred<Boolean>()
        val source = Source(WatchTargets(install = null, addFace = galaxy), opens = companion)
        setContent { Section(source, Offer(offered = false), bound = SHORT_BOUND) }
        val face = getString(Res.string.settings_watch_face)
        val going = getString(Res.string.settings_watch_continue)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onNodeWithText(face).performClick()
        // The bound is waited for in real time: the scene's FlushCoroutineDispatcher (CMP 1.12.1)
        // puts a `delay` on the test clock, but not `withTimeout`'s `invokeOnTimeout`.
        waitUntil("the answer at the bound", timeoutMillis = 5_000) { onAllNodesWithText(going).fetchSemanticsNodes().size == 1 }
        onAllNodes(polite and hasText(going)).assertCountEquals(1)
        onAllNodesWithText(getString(Res.string.settings_watch_unreachable)).assertCountEquals(0)
        onNodeWithText(face).performClick()
        waitForIdle()
        assertEquals(listOf(galaxy to WatchAction.ADD_FACE), source.opened, "the second tap sent nothing")
        companion.complete(true)
        waitForIdle()
        onAllNodesWithText(going).assertCountEquals(1)
    }

    /**
     * Ruling 72 item 3: Settings left and brought back while a tap is still on its way reads the
     * rows again, and a tap then joins the one on its way and sends nothing.
     */
    @Test
    fun aTapAfterSettingsComesBackJoinsTheOneStillOnItsWay() = runComposeUiTest {
        val companion = CompletableDeferred<Boolean>()
        val source = Source(WatchTargets(install = null, addFace = galaxy), opens = companion)
        val front = Front()
        setContent { Section(source, Offer(offered = false), front) }
        val face = getString(Res.string.settings_watch_face)
        val going = getString(Res.string.settings_watch_continue)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onNodeWithText(face).performClick()
        waitForIdle()
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("the rows read again", timeoutMillis = 5_000) { source.asked == 2 }
        onNodeWithText(face).performClick()
        waitForIdle()
        assertEquals(listOf(galaxy to WatchAction.ADD_FACE), source.opened, "the tap after coming back sent nothing")
        companion.complete(true)
        waitUntil("the answer", timeoutMillis = 5_000) { onAllNodesWithText(going).fetchSemanticsNodes().size == 1 }
    }

    /**
     * Ruling 74 item 9: a request the companion never answers expires (the app's 2 minutes, here
     * 3 s) and the row takes a tap again, which sends anew: a second `FaceActivity` is better than
     * a row that never sends again. Until then a tap sends nothing. The expiry leaves the tap
     * before it about 2.7 s past the bound, in real time, so a stalled simulator does not fail a
     * correct build (Ruling 76 item 10).
     */
    @Test
    fun aTapAfterTheRequestExpiredSendsAnew() = runComposeUiTest {
        val expiry = 3.seconds
        val requests = requests(expiry)
        val source = Source(WatchTargets(install = null, addFace = galaxy), opens = CompletableDeferred())
        setContent { Section(source, Offer(offered = false), requests = requests, bound = SHORT_BOUND) }
        val face = getString(Res.string.settings_watch_face)
        val going = getString(Res.string.settings_watch_continue)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onNodeWithText(face).performClick()
        waitUntil("the answer at the bound", timeoutMillis = 5_000) { onAllNodesWithText(going).fetchSemanticsNodes().size == 1 }
        // Marked once the request is surely sent, so a stall before here cannot make the wait short.
        val sent = TimeSource.Monotonic.markNow()
        onNodeWithText(face).performClick()
        waitForIdle()
        assertEquals(1, source.opened.size, "a tap before the expiry sent nothing")
        // Real time, as the bound: the expiry is `withTimeout`'s too.
        waitUntil("past the expiry", timeoutMillis = 10_000) { sent.elapsedNow() > expiry + 300.milliseconds }
        waitForIdle()
        onAllNodesWithText(going).assertCountEquals(1)
        onNodeWithText(face).performClick()
        waitForIdle()
        assertEquals(listOf(galaxy to WatchAction.ADD_FACE, galaxy to WatchAction.ADD_FACE), source.opened, "the tap after the expiry sent anew")
    }

    /**
     * Review round 1 of Task 6: the answer was the row's one-line value, and where the two do not
     * both fit the label keeps 60 % of the row, so even English was cut ("Continue on your w…") on a
     * 411 dp phone. On a 360 dp phone at the default text size, both answers and both labels are
     * drawn whole.
     */
    @Test
    fun onA360DpPhoneEachAnswerIsWhole() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = pixel), reaches = { it == WatchAction.ADD_FACE })
        setContent { Phone(source, width = 360.dp, fontScale = 1f) }
        tapBothAndReadThemWhole()
    }

    /** At twice the text size, on a phone narrower than any Taqwa runs on, they wrap and stay whole. */
    @Test
    fun atTwiceTheTextSizeEachAnswerWrapsAndIsWhole() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = pixel), reaches = { it == WatchAction.ADD_FACE })
        setContent { Phone(source, width = 300.dp, fontScale = 2f) }
        tapBothAndReadThemWhole()
        assertTrue(layoutOf(getString(Res.string.settings_watch_unreachable)).lineCount > 1, "the answer did not wrap")
    }

    /** A watch out of reach at the tap is asked again, by name, once Settings comes back. */
    @Test
    fun aTapsAnswerLastsUntilTheRowsAreReadAgain() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null), opens = CompletableDeferred(false))
        val front = Front()
        setContent { Section(source, Offer(offered = false), front) }
        val install = getString(Res.string.settings_watch_install)
        val unreachable = getString(Res.string.settings_watch_unreachable)
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(install).fetchSemanticsNodes().size == 1 }
        onNodeWithText(install).performClick()
        waitUntil("the answer", timeoutMillis = 5_000) { onAllNodesWithText(unreachable).fetchSemanticsNodes().size == 1 }
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("the answer gone", timeoutMillis = 5_000) { onAllNodesWithText(unreachable).fetchSemanticsNodes().isEmpty() }
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(0)
        onAllNodes(hasText(install) and hasText(galaxy.name)).assertCountEquals(1)
        assertEquals(2, source.asked)
    }

    @Test
    fun theRowsAreAskedAgainEachTimeSettingsComesBack() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        val front = Front()
        setContent { Section(source, Offer(offered = false), front) }
        val install = getString(Res.string.settings_watch_install)
        val face = getString(Res.string.settings_watch_face)
        val going = getString(Res.string.settings_watch_continue)
        waitUntil("the install row", timeoutMillis = 5_000) { onAllNodesWithText(install).fetchSemanticsNodes().size == 1 }
        onNodeWithText(install).performClick()
        waitUntil("the answer", timeoutMillis = 5_000) { onAllNodesWithText(going).fetchSemanticsNodes().size == 1 }
        // Taqwa installed from the watch's Play Store, then Settings brought back: the face row, fresh.
        source.targets = WatchTargets(install = null, addFace = galaxy)
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("the face row", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onAllNodesWithText(install).assertCountEquals(0)
        onAllNodesWithText(going).assertCountEquals(0)
        // Then the face made active on the watch: no section at all.
        val heading = getString(Res.string.settings_group_watch)
        source.targets = WatchTargets.NONE
        front.leave()
        waitForIdle()
        front.come()
        waitUntil("no section", timeoutMillis = 5_000) { onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty() }
        assertTrue(source.asked >= 3, "asked ${source.asked} times")
    }

    // ── While Settings stays in front (the owner's report of 9 November) ──

    /**
     * He tapped "Install on your watch", Taqwa was installed on the watch, and the row stayed until
     * he left Settings and came back. Now the watch side's word reads the rows again in place: the
     * install row gives way to the face row, and once the face is taken the section goes.
     */
    @Test
    fun theRowsFollowTheWatchWhileSettingsStaysInFront() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        val front = Front()
        setContent { Section(source, Offer(offered = false), front) }
        val install = getString(Res.string.settings_watch_install)
        val face = getString(Res.string.settings_watch_face)
        val heading = getString(Res.string.settings_group_watch)
        waitUntil("the install row and its listening", timeoutMillis = 5_000) {
            onAllNodesWithText(install).fetchSemanticsNodes().size == 1 && source.listening == 1
        }
        // Taqwa installed from the watch's Play Store; Settings never left.
        source.targets = WatchTargets(install = null, addFace = galaxy)
        source.change()
        waitUntil("the face row", timeoutMillis = 5_000) { onAllNodesWithText(face).fetchSemanticsNodes().size == 1 }
        onAllNodesWithText(install).assertCountEquals(0)
        // Then the face made the watch's own: nothing left to offer, so no section.
        source.targets = WatchTargets.NONE
        source.change()
        waitUntil("no section", timeoutMillis = 5_000) { onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty() }
        assertEquals(3, source.asked)
    }

    /**
     * The owner's flow as the watch makes it (the review of 9 November): `taqwa_watch` comes with the
     * install, and `taqwa_face_addable` only once the watch app has run and published its face, so
     * in between the rows answer no row at all, here twice (the listener's word, then a tap's
     * follow). The install row is held meanwhile, and the face row takes its place: the Watch heading
     * never leaves, so About never moves and TalkBack's focus stays.
     */
    @Test
    fun theSectionStaysWhileAWatchThatHasJustGotTaqwaPublishesItsFace() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        setContent { Section(source, Offer(offered = false)) }
        val install = getString(Res.string.settings_watch_install)
        val face = getString(Res.string.settings_watch_face)
        val heading = getString(Res.string.settings_group_watch)
        waitUntil("the install row and its listening", timeoutMillis = 5_000) {
            onAllNodesWithText(install).fetchSemanticsNodes().size == 1 && source.listening == 1
        }
        // Taqwa installed: the watch has taqwa_watch, and has not yet published its face.
        source.targets = WatchTargets.NONE
        source.change()
        waitUntil("the first reading of no row", timeoutMillis = 5_000) { source.asked == 2 }
        source.change()
        // The third reading starts only once the second's rows are in place.
        waitUntil("the second reading of no row", timeoutMillis = 5_000) { source.asked == 3 }
        waitForIdle()
        onAllNodesWithText(heading).assertCountEquals(1)
        onAllNodes(hasText(install) and hasText(galaxy.name)).assertCountEquals(1)
        // The watch app has run and published its face.
        source.targets = WatchTargets(install = null, addFace = galaxy)
        source.change()
        waitUntil("the face row, the heading kept", timeoutMillis = 5_000) {
            check(onAllNodesWithText(heading).fetchSemanticsNodes().size == 1) { "the Watch heading left" }
            onAllNodesWithText(face).fetchSemanticsNodes().size == 1
        }
        onAllNodesWithText(install).assertCountEquals(0)
        assertEquals(4, source.asked)
    }

    /**
     * The hold is bounded: a watch that publishes no face within it (the face already taken, or an
     * app nothing starts) loses its section once it ends, at the one reading the end asks for.
     */
    @Test
    fun noRowIsTakenOnceTheHoldIsOver() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        setContent { Section(source, Offer(offered = false), hold = SHORT_HOLD) }
        val install = getString(Res.string.settings_watch_install)
        val heading = getString(Res.string.settings_group_watch)
        waitUntil("the install row and its listening", timeoutMillis = 5_000) {
            onAllNodesWithText(install).fetchSemanticsNodes().size == 1 && source.listening == 1
        }
        source.targets = WatchTargets.NONE
        source.change()
        waitUntil("no section, once the hold is over", timeoutMillis = 5_000) { onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty() }
        waitForIdle()
        assertEquals(3, source.asked, "the resume's reading, the one held, and the one at the hold's end")
    }

    /** Each coming to the front starts afresh: no hold outlives Settings, and the resume's reading takes no row as it comes. */
    @Test
    fun noHoldOutlivesSettings() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        val front = Front()
        setContent { Section(source, Offer(offered = false), front) }
        val install = getString(Res.string.settings_watch_install)
        val heading = getString(Res.string.settings_group_watch)
        waitUntil("the install row and its listening", timeoutMillis = 5_000) {
            onAllNodesWithText(install).fetchSemanticsNodes().size == 1 && source.listening == 1
        }
        source.targets = WatchTargets.NONE
        source.change()
        waitUntil("the reading held", timeoutMillis = 5_000) { source.asked == 2 }
        front.leave()
        waitForIdle()
        front.come()
        // Well inside the app's 20 s hold.
        waitUntil("no section", timeoutMillis = 5_000) { onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty() }
    }

    /** The watch side is listened to while Settings is in front, and no longer once it is left. */
    @Test
    fun theWatchSideIsListenedToOnlyWhileSettingsIsInFront() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        val offer = Offer(offered = false)
        val front = Front()
        setContent { Section(source, offer, front) }
        // The first reading is waited for too: left before it is dispatched, it is cancelled unread, and
        // coming back would then make the second reading the first.
        waitUntil("listening and read", timeoutMillis = 5_000) {
            source.listening == 1 && offer.listening == 1 && source.asked == 1
        }
        front.leave()
        waitUntil("no longer listening", timeoutMillis = 5_000) { source.listening == 0 && offer.listening == 0 }
        front.come()
        waitUntil("listening again", timeoutMillis = 5_000) { source.listening == 1 && offer.listening == 1 }
        waitUntil("the rows read again", timeoutMillis = 5_000) { source.asked == 2 }
    }

    /**
     * A reading in place keeps a row's tap answer while the row stays: the line belongs to the
     * reading Settings made when it came to the front, and only that is started afresh.
     */
    @Test
    fun aTapsAnswerStaysWhileTheRowsAreReadAgainInPlace() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null), opens = CompletableDeferred(false))
        setContent { Section(source, Offer(offered = false)) }
        val install = getString(Res.string.settings_watch_install)
        val unreachable = getString(Res.string.settings_watch_unreachable)
        waitUntil("the row and its listening", timeoutMillis = 5_000) {
            onAllNodesWithText(install).fetchSemanticsNodes().size == 1 && source.listening == 1
        }
        onNodeWithText(install).performClick()
        waitUntil("the answer", timeoutMillis = 5_000) { onAllNodesWithText(unreachable).fetchSemanticsNodes().size == 1 }
        source.change()
        waitUntil("the rows read again", timeoutMillis = 5_000) { source.asked == 2 }
        waitForIdle()
        onAllNodes(polite and hasText(unreachable)).assertCountEquals(1)
        onAllNodes(hasText(install) and hasText(galaxy.name)).assertCountEquals(1)
    }

    /** A reading in place that fails keeps the rows, and the answer with them: only an answer changes them. */
    @Test
    fun aReadingInPlaceThatFailsKeepsTheRows() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        setContent { Section(source, Offer(offered = false)) }
        val install = getString(Res.string.settings_watch_install)
        val going = getString(Res.string.settings_watch_continue)
        waitUntil("the row and its listening", timeoutMillis = 5_000) {
            onAllNodesWithText(install).fetchSemanticsNodes().size == 1 && source.listening == 1
        }
        onNodeWithText(install).performClick()
        waitUntil("the answer", timeoutMillis = 5_000) { onAllNodesWithText(going).fetchSemanticsNodes().size == 1 }
        source.fails = true
        source.change()
        waitUntil("the rows read again", timeoutMillis = 5_000) { source.asked == 2 }
        waitForIdle()
        onAllNodes(hasText(install) and hasText(galaxy.name)).assertCountEquals(1)
        onAllNodesWithText(going).assertCountEquals(1)
    }

    /**
     * The fallback: should the watch side say nothing, a tap is followed by readings for a while,
     * here every 100 ms for 500 ms, and then no more. So the face row comes after the install
     * without a word from the watch, and the readings stop on their own.
     */
    @Test
    fun aTapIsFollowedByReadingsForAWhileAndThenNoMore() = runComposeUiTest {
        val source = Source(WatchTargets(install = galaxy, addFace = null))
        val follow = TapFollow(every = 100.milliseconds, lasting = 500.milliseconds)
        setContent { Section(source, Offer(offered = false), follow = follow) }
        val install = getString(Res.string.settings_watch_install)
        val face = getString(Res.string.settings_watch_face)
        waitUntil("the install row", timeoutMillis = 5_000) { onAllNodesWithText(install).fetchSemanticsNodes().size == 1 }
        assertEquals(1, source.asked)
        onNodeWithText(install).performClick()
        source.targets = WatchTargets(install = null, addFace = galaxy)
        waitUntil("the face row, with no word from the watch", timeoutMillis = 5_000) {
            onAllNodesWithText(face).fetchSemanticsNodes().size == 1
        }
        // Real time well past the 500 ms, in case the scene's clock runs the waits in real time.
        val tapped = TimeSource.Monotonic.markNow()
        waitUntil("past the follow", timeoutMillis = 5_000) { tapped.elapsedNow() > 1.seconds }
        waitForIdle()
        val asked = source.asked
        assertTrue(asked in 2..6, "at most the follow's 5 readings after the first, asked $asked")
        val later = TimeSource.Monotonic.markNow()
        waitUntil("a while later", timeoutMillis = 5_000) { later.elapsedNow() > 1.seconds }
        waitForIdle()
        assertEquals(asked, source.asked, "no reading once the follow is over")
    }

    /** The iPhone: the session's word asks "Add to Apple Watch" again while Settings stays in front. */
    @Test
    fun theAppleRowFollowsTheSessionWhileSettingsStaysInFront() = runComposeUiTest {
        val offer = Offer(offered = false)
        setContent { Section(Source(WatchTargets.NONE), offer) }
        val apple = getString(Res.string.settings_add_to_apple_watch)
        val heading = getString(Res.string.settings_group_watch)
        waitUntil("listening", timeoutMillis = 5_000) { offer.listening == 1 && offer.asked == 1 }
        onAllNodesWithText(heading).assertCountEquals(0)
        // The session's activation completed, or a watch was paired, with Settings open.
        offer.offered = true
        offer.change()
        waitUntil("the row", timeoutMillis = 5_000) { onAllNodesWithText(apple).fetchSemanticsNodes().size == 1 }
        // And unpaired, or the session made inactive for another watch.
        offer.offered = false
        offer.change()
        waitUntil("no section", timeoutMillis = 5_000) { onAllNodesWithText(heading).fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        /** No tap followed by readings: the tests that count readings see only their own. */
        val NO_FOLLOW = TapFollow(every = 1.seconds, lasting = Duration.ZERO)

        /**
         * Each test's bound: short, so a test that reached it would not wait the app's 10 s, and
         * long enough that a tap the test answers at once is answered before it.
         */
        val BOUND = 2.seconds

        /** The bound for the tests that wait for it. */
        val SHORT_BOUND = 300.milliseconds

        /** The hold for the test that waits for its end, short of the app's 20 s. */
        val SHORT_HOLD = 300.milliseconds
    }
}
