package world.taqwa.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [onEachTimeInFront] and [eachTimeInFront] on Compose Multiplatform's iOS lifecycle: the app in
 * front is STARTED, and active is RESUMED. Control Center, Notification Center and a system alert
 * make the app inactive (RESUMED to STARTED and back) without it ever leaving the front; the
 * background is STOPPED, CREATED here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnsToFrontTest {

    private class App : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    /**
     * Runs [script] against an app watched by [watch]. Unconfined, so each lifecycle event is
     * handled before the script's next line; in the app the watcher's block runs a main-queue
     * turn later, a gap the jump's own checks cover ([jumpToPlaying]).
     */
    private fun runApp(watch: suspend Lifecycle.() -> Unit, script: suspend TestScope.(App) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val app = App()
            val watching = launch { app.lifecycle.watch() }
            script(app)
            watching.cancel()
        }

    private fun TestScope.goTo(app: App, state: Lifecycle.State) {
        app.registry.currentState = state
        runCurrent()
    }

    private fun TestScope.controlCenter(app: App) {
        goTo(app, Lifecycle.State.STARTED)
        goTo(app, Lifecycle.State.RESUMED)
    }

    private fun TestScope.background(app: App) {
        goTo(app, Lifecycle.State.CREATED)
        goTo(app, Lifecycle.State.RESUMED)
    }

    // ── onEachTimeInFront ───────────────────────────────────────────────────────────────

    @Test
    fun theFirstTimeInFrontCountsWithTheRequestCountFromWhenWatchingBegan() {
        val fronts = mutableListOf<Long>()
        runApp(watch = { onEachTimeInFront({ 2L }) { fronts += it } }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
        }
        assertEquals(listOf(2L), fronts)
    }

    /**
     * iOS can compose the scene in the background (a background refresh, a finished download)
     * and bring it forward later: its first time in front is the person opening the app, and it
     * counts when it comes, not when the scene was composed.
     */
    @Test
    fun aSceneComposedInTheBackgroundIsInFrontWhenItComesForward() {
        val fronts = mutableListOf<Long>()
        runApp(watch = { onEachTimeInFront({ 0L }) { fronts += it } }) { app ->
            goTo(app, Lifecycle.State.CREATED)
            assertEquals(emptyList(), fronts)
            goTo(app, Lifecycle.State.RESUMED)
        }
        assertEquals(1, fronts.size)
    }

    @Test
    fun controlCenterAndSystemAlertsAreNotANewTimeInFront() {
        val fronts = mutableListOf<Long>()
        runApp(watch = { onEachTimeInFront({ 0L }) { fronts += it } }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
            repeat(3) { controlCenter(app) }
        }
        assertEquals(1, fronts.size)
    }

    @Test
    fun eachWayBackFromTheBackgroundIsOneMoreTimeInFront() {
        val fronts = mutableListOf<Long>()
        runApp(watch = { onEachTimeInFront({ 0L }) { fronts += it } }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
            repeat(2) { background(app) }
        }
        assertEquals(3, fronts.size)
    }

    /**
     * Each time in front carries the request count from when the app last left, not from the
     * launch or from when it came back: an adhkar banner tapped while the app is in front is
     * behind it by the next return, and a request made while it is away is ahead of it.
     */
    @Test
    fun eachTimeInFrontCarriesTheRequestCountFromWhenTheAppLastLeft() {
        var requests = 2L
        val fronts = mutableListOf<Long>()
        runApp(watch = { onEachTimeInFront({ requests }) { fronts += it } }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
            requests = 3L
            goTo(app, Lifecycle.State.CREATED)
            requests = 4L
            goTo(app, Lifecycle.State.RESUMED)
            background(app)
        }
        assertEquals(listOf(2L, 3L, 4L), fronts)
    }

    // ── eachTimeInFront: what the iPhone does with it ───────────────────────────────────

    private class Effects {
        var reschedules = 0
        val jumps = mutableListOf<Long>()
    }

    private fun Lifecycle.watched(
        effects: Effects,
        playing: () -> Boolean,
        requests: () -> Long = { 0L },
        leftForOwnLink: () -> Boolean = { false },
        lastLeave: MutableStateFlow<Leave?> = MutableStateFlow(null),
    ) = suspend {
        eachTimeInFront(
            requestsMade = requests,
            leftForOwnLink = leftForOwnLink,
            reschedule = { effects.reschedules++ },
            playing = playing,
            openPlaying = { effects.jumps += it },
            lastLeave = lastLeave,
        )
    }

    @Test
    fun everyTimeInFrontPlansTheNotificationsAgainAndControlCenterDoesNot() {
        val effects = Effects()
        runApp(watch = { watched(effects, playing = { false })() }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
            repeat(3) { controlCenter(app) }
            background(app)
        }
        assertEquals(2, effects.reschedules)
        assertEquals(emptyList(), effects.jumps)
    }

    /**
     * With a voice going, the recited ayah is opened each time the app is in front, once it is
     * active: a URL or a notification response that comes with the activation is usually counted
     * first.
     * Control Center opens nothing.
     */
    @Test
    fun aVoicePlayingIsOpenedOnceTheAppIsActiveAndNotOnControlCenter() {
        val effects = Effects()
        var requests = 5L
        runApp(watch = { watched(effects, playing = { true }, requests = { requests })() }) { app ->
            goTo(app, Lifecycle.State.CREATED)
            goTo(app, Lifecycle.State.STARTED)
            assertEquals(emptyList(), effects.jumps, "opened before the app was active")
            goTo(app, Lifecycle.State.RESUMED)
            repeat(3) { controlCenter(app) }
            requests = 6L
            background(app)
        }
        assertEquals(listOf(5L, 6L), effects.jumps)
    }

    /**
     * In front for a moment and gone again before the app is active: the plan is made again, as
     * it is every time in front, and the wait for the activation ends with the visit.
     */
    @Test
    fun aVisitCutShortBeforeTheAppIsActiveOpensNothing() {
        val effects = Effects()
        runApp(watch = { watched(effects, playing = { true })() }) { app ->
            goTo(app, Lifecycle.State.CREATED)
            goTo(app, Lifecycle.State.STARTED)
            goTo(app, Lifecycle.State.CREATED)
        }
        assertEquals(emptyList(), effects.jumps)
        assertEquals(1, effects.reschedules)
    }

    /**
     * Back from a link the app opened itself (iOS Settings from the notifications screen, a mail,
     * a web page): the person returns to the screen they left from, not to the recited ayah. The
     * plan is still made again: B1's own case is coming back from iOS Settings.
     */
    @Test
    fun aReturnFromALinkTheAppOpenedOpensNothing() {
        val effects = Effects()
        var ownLink = false
        runApp(watch = { watched(effects, playing = { true }, leftForOwnLink = { ownLink })() }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
            ownLink = true
            goTo(app, Lifecycle.State.CREATED)
            ownLink = false
            goTo(app, Lifecycle.State.RESUMED)
        }
        assertEquals(listOf(0L), effects.jumps)
        assertEquals(2, effects.reschedules)
    }

    /**
     * iOS can build the scene afresh while a recitation keeps the process alive, and a widget tap
     * can be what rebuilt it, counted before the new composition starts watching. The new watcher
     * starts from the process's last leave, not from its own start, so the jump still yields to it.
     */
    @Test
    fun aCompositionBuiltAfreshStartsFromTheLastLeaveOfTheProcess() {
        val lastLeave = MutableStateFlow<Leave?>(null)
        var requests = 3L
        val before = Effects()
        runApp(watch = { watched(before, playing = { true }, requests = { requests }, lastLeave = lastLeave)() }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
            goTo(app, Lifecycle.State.CREATED)
        }
        requests = 4L
        val after = Effects()
        runApp(watch = { watched(after, playing = { true }, requests = { requests }, lastLeave = lastLeave)() }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
        }
        assertEquals(listOf(3L), after.jumps)
    }

    /** The same for a link the app opened: the scene rebuilt on the way back still knows it. */
    @Test
    fun aCompositionBuiltAfreshKnowsTheProcessLeftForItsOwnLink() {
        val lastLeave = MutableStateFlow<Leave?>(null)
        var ownLink = false
        val before = Effects()
        runApp(watch = { watched(before, playing = { true }, leftForOwnLink = { ownLink }, lastLeave = lastLeave)() }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
            ownLink = true
            goTo(app, Lifecycle.State.CREATED)
        }
        ownLink = false
        val after = Effects()
        runApp(watch = { watched(after, playing = { true }, leftForOwnLink = { ownLink }, lastLeave = lastLeave)() }) { app ->
            goTo(app, Lifecycle.State.RESUMED)
        }
        assertEquals(emptyList(), after.jumps)
        assertEquals(1, after.reschedules)
    }
}
