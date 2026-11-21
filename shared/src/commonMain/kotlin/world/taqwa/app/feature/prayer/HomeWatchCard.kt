package world.taqwa.app.feature.prayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.AccessibilityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import world.taqwa.app.feature.settings.InstallHold
import world.taqwa.app.feature.settings.TapFollow
import world.taqwa.app.feature.settings.readWatchRows
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.watchface.WatchFaceOffer
import world.taqwa.app.watchface.WatchFaceResult
import world.taqwa.app.watchfeed.WatchAction
import world.taqwa.app.watchfeed.WatchOpen
import world.taqwa.app.watchfeed.WatchRequests
import world.taqwa.app.watchfeed.WatchTarget
import world.taqwa.app.watchfeed.WatchTargets
import world.taqwa.app.watchfeed.WatchTargetsSource
import world.taqwa.app.watchfeed.openOnWatch
import world.taqwa.app.watchfeed.watchTargetsSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What Home's watch card (1.7.0, Option B) shows, as [HomeWatchCard] publishes it:
 * - [Hidden]: nothing: not read yet, no row of Settings › Watch applies, or a reading that came
 *   after the first touch on Home, which waits for the next time Home comes to the front;
 * - [Offer]: Settings' row as a card, [step] on the watch named [watchName], the last tap's
 *   [answer] while it belongs to this card, and whether it was [tapped] since Home came to the
 *   front, so a step that changes in place is said as it comes to whoever tapped it;
 * - [Dismissed]: the time after the x, a line with Undo in the card's place;
 * - [Gone]: retired by the x for every watch and for good. Settings › Watch keeps its rows.
 */
sealed interface WatchCardUi {
    data object Hidden : WatchCardUi
    data class Offer(
        val step: WatchCardStep,
        val watchName: String,
        val answer: WatchCardAnswer? = null,
        val tapped: Boolean = false,
    ) : WatchCardUi
    data object Dismissed : WatchCardUi
    data object Gone : WatchCardUi
}

/** Settings › Watch's two Android rows: Taqwa's Play page on the watch, then the face's screen. */
enum class WatchCardStep { INSTALL, ADD_FACE }

/**
 * A tap's answer, as Settings' rows give it (Ruling 72 item 3): [ASKING] while the watch is asked,
 * [CONTINUE] once it took it or at the bound while it is still on its way, [UNREACHABLE] for a
 * refusal or a failure alone.
 */
enum class WatchCardAnswer { ASKING, CONTINUE, UNREACHABLE }

/**
 * The card's offer from Settings' rows, in Settings' order: the install first, since it names the
 * watch it acts on, then the face. One card for one watch, never two stacked; null where no row
 * applies, which is every iPhone's answer until its card is designed (Q2).
 */
internal fun watchCardOffer(targets: WatchTargets): Pair<WatchCardStep, WatchTarget>? {
    targets.install?.let { return WatchCardStep.INSTALL to it }
    targets.addFace?.let { return WatchCardStep.ADD_FACE to it }
    return null
}

/** What the card's tap asks of the watch: the row's own action. */
internal val WatchCardStep.action: WatchAction
    get() = when (this) {
        WatchCardStep.INSTALL -> WatchAction.INSTALL
        WatchCardStep.ADD_FACE -> WatchAction.ADD_FACE
    }

/** How the card's drawing of the watch looks: a dashed ghost before Taqwa, the real watch with a faint face before the face. */
internal enum class WatchLook { GHOST, FAINT }

internal val WatchCardUi.Offer.look: WatchLook
    get() = when (step) {
        WatchCardStep.INSTALL -> WatchLook.GHOST
        WatchCardStep.ADD_FACE -> WatchLook.FAINT
    }

/** The thin amber ring round the watch, while it answers a tap: asked, or taken. */
internal val WatchCardUi.Offer.ringed: Boolean
    get() = answer == WatchCardAnswer.ASKING || answer == WatchCardAnswer.CONTINUE

/**
 * How long the line with Undo stays after the x: the owner's six seconds ([HomeWatchCard.UNDO_WINDOW]),
 * or longer where the reader has asked for more time to act on what comes and goes, as [manager]
 * says for a line of words with a button (Android's "Time to take action"; with TalkBack below
 * Android 10, no end at all, so the line stays until Home leaves). Never shorter: the review of
 * 20 November, since the x is final once the line has gone (WCAG 2.2.1).
 */
internal fun undoWindow(manager: AccessibilityManager?): Duration {
    val usual = HomeWatchCard.UNDO_WINDOW
    val asked = manager?.calculateRecommendedTimeoutMillis(
        usual.inWholeMilliseconds,
        containsText = true,
        containsControls = true,
    ) ?: return usual
    return maxOf(asked.milliseconds, usual)
}

/**
 * Home offers no Apple Watch face until the iPhone card is designed (Q2); the face stays Settings'
 * row. Never available, and an add that is never made fails.
 */
private object NoAppleFace : WatchFaceOffer {
    override fun available(): Boolean = false
    override fun add(onDone: (WatchFaceResult) -> Unit) = onDone(WatchFaceResult.FAILED)
}

/**
 * Home's watch card (1.7.0, Option B, the owner's decisions of 19 November): Settings › Watch's
 * offer, "Install on your watch" or "Add the Taqwa face", as a card above the Qibla card, for as
 * long as Settings would show that row, and never again once its x is pressed.
 *
 * It reads the watch as Settings does ([readWatchRows]: [loadWatchTargets]' 3 s bound, the install
 * row held for [hold] through the face's publishing, and readings again in place each time the watch
 * side says it changed and for [follow] after a tap), each time Home comes to the front
 * ([whileResumed]). The card is put on Home only by a reading that comes before the first touch or
 * scroll since Home came to the front ([touched]): one that comes later waits for the next time, so
 * the Qibla card never moves under a thumb, and once Home is touched with no card on it nothing more
 * is read that visit, since nothing could show. A card already on Home changes in place, from the
 * install to the face, and folds when no row applies; there is no "set up" moment, since the phone
 * cannot tell the face was chosen from the one activation spent. A card carried over from Home's
 * last visit is kept by the reading that brings Home back as by a reading in place: through one
 * that fails, and the install held through one of no row.
 *
 * Its tap ([open]) asks the watch through Settings' own [requests], so a card tap and a Settings tap
 * to the same watch join and the watch never opens its screen twice; its answer belongs to the card
 * and the visit it was made in. A tapped install card is held through readings of no row for its
 * tap's whole follow-up ([TapFollow.lasting], away from Home too), saying "Continue on your watch",
 * since Taqwa is then on its way to the watch: reinstalled on a watch the phone fed before, its face
 * comes only once its app is started, minutes later, and takes the card's place in place (the
 * review of 20 November); a tap the watch refused holds nothing. Its x ([dismiss]) writes the one
 * flag ([setDismissed]: every watch, both steps, for good) and leaves a line with Undo in its place
 * for the window the screen gives ([undoWindow]), folding only once no finger is down on Home
 * ([pressed]), or at once when Home leaves the front; [undo] clears the flag and brings the card
 * back. Once gone, Home never reads the watch again. Settings › Watch never reads the flag, which
 * is read once for each holder ([isDismissed]): the holder alone writes it.
 *
 * Held for the app's composition, above the screens, so the card keeps its place across the walk to
 * the Qibla screen and back; [scope] is that composition's, and every call here is made on its
 * thread. [readOn] is where the watch is read, off the main thread in the app.
 */
internal class HomeWatchCard(
    private val source: WatchTargetsSource,
    private val isDismissed: suspend () -> Boolean,
    private val setDismissed: suspend (Boolean) -> Unit,
    private val scope: CoroutineScope,
    private val requests: WatchRequests = WatchRequests.process,
    private val bound: Duration = WatchRequests.BOUND,
    private val follow: TapFollow = TapFollow.APP,
    private val hold: Duration = InstallHold.APP,
    private val undoFor: Duration = UNDO_WINDOW,
    private val readOn: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _card = MutableStateFlow<WatchCardUi>(WatchCardUi.Hidden)

    /** What Home draws. */
    val card: StateFlow<WatchCardUi> = _card.asStateFlow()

    private enum class Phase { OFFERING, UNDO, GONE }

    private var phase = Phase.OFFERING

    /** Whether the flag was read: once, after which [phase] says what it says. */
    private var flagRead = false

    /** The latest reading of the watch, whether or not a card shows it. */
    private var targets = WatchTargets.NONE

    /** The offer on Home, or null for none. */
    private var onScreen: Pair<WatchCardStep, WatchTarget>? = null

    /** Whether Home was touched or scrolled since it came to the front. */
    private var touched = false

    /** Whether a finger, pen or mouse button is down on Home now. */
    private var pressing = false

    /** Whether the undo line's time ran out under a finger, so it folds as the finger lifts. */
    private var retireOnLift = false

    /** Whether a stay of Home in front is being read ([whileResumed]). */
    private var visiting = false

    /** Whether this stay's readings can change nothing more: the card gone, or Home touched with none on it. */
    private val visitOver = MutableStateFlow(false)

    private var answer: WatchCardAnswer? = null

    /** Whether the card's request is still on its way, so a tap meanwhile starts nothing more. */
    private var asking = false

    /** Whether the card was tapped since Home came to the front, or since Undo. */
    private var tapped = false

    /** Which card and visit a tap's answer belongs to: it is dropped once this has moved on. */
    private var generation = 0

    /** The card's taps so far, so a tap's hold is ended by that tap's own refusal alone. */
    private var tapsMade = 0

    /** A tapped install card's hold for its tap's follow-up, while it runs, and whose tap it is. */
    private var tapHold: Job? = null
    private var tapHoldOf = 0

    private var undoTimer: Job? = null

    /** The last flag write, which the next one waits for, so the flag ends as the last call says. */
    private var writing: Job? = null

    /** The card's taps, each followed by readings for a while, as Settings' rows' are. */
    private val taps = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * One stay of Home in front: the flag first, the first time, and nothing more once the card is
     * retired; then the watch read until Home leaves, the card is gone, or Home is touched with no
     * card on it. A card already on Home stays as it was, its last answer and tap cleared, until the
     * reading says otherwise. Leaving during the undo window ends it.
     */
    suspend fun whileResumed() {
        // Before anything suspends: a touch from here on is this stay's.
        touched = false
        if (phase == Phase.GONE) return
        if (!flagRead) {
            writing?.join()
            if (isDismissed()) {
                phase = Phase.GONE
                publish()
                return
            }
            flagRead = true
        }
        tapped = false
        forget()
        publish()
        visiting = true
        visitOver.value = false
        settle()
        try {
            if (visitOver.value) return
            coroutineScope {
                val kept = onScreen != null
                val reading = launch {
                    readWatchRows(
                        source,
                        NoAppleFace,
                        taps,
                        follow,
                        hold,
                        shown = { targets },
                        on = readOn,
                        keepOnResume = kept,
                    ) { rows, _ -> take(rows) }
                }
                visitOver.first { it }
                reading.cancel()
            }
        } finally {
            visiting = false
            if (phase == Phase.UNDO) retire()
        }
    }

    /** A finger, pen or mouse put down on Home, or a scroll: a card not yet on Home now waits. */
    fun touched() {
        touched = true
        settle()
    }

    /**
     * A finger, pen or mouse button put down on Home ([down]), or the last one lifted, as Home's
     * screen sees them after its own buttons: the undo line never folds under one.
     */
    fun pressed(down: Boolean) {
        pressing = down
        if (!down && retireOnLift) {
            retireOnLift = false
            if (phase == Phase.UNDO) retire()
        }
    }

    /** The card's tap: its step asked of its watch, once while the request is on its way. */
    fun open() {
        val (step, target) = onScreen ?: return
        if (phase != Phase.OFFERING || asking) return
        asking = true
        tapped = true
        answer = WatchCardAnswer.ASKING
        val tap = ++tapsMade
        if (step == WatchCardStep.INSTALL) holdForTap(tap)
        publish()
        taps.tryEmit(Unit)
        val visit = generation
        scope.launch {
            try {
                openOnWatch(source, target, step.action, bound, requests).collect { open ->
                    // The watch never took it: its card is held no longer than Settings' row is.
                    if (open == WatchOpen.UNREACHABLE && tapHoldOf == tap) endTapHold()
                    if (generation == visit) {
                        answer = when (open) {
                            WatchOpen.OPENED, WatchOpen.QUEUED -> WatchCardAnswer.CONTINUE
                            WatchOpen.UNREACHABLE -> WatchCardAnswer.UNREACHABLE
                        }
                        publish()
                    }
                }
            } finally {
                if (generation == visit) asking = false
            }
        }
    }

    /**
     * The x: the flag written at once, and the undo line for [window], the screen's ([undoWindow]),
     * then folded once no finger is down on Home. Only with a card on Home.
     */
    fun dismiss(window: Duration = undoFor) {
        if (phase != Phase.OFFERING || onScreen == null) return
        phase = Phase.UNDO
        publish()
        write(true)
        undoTimer = scope.launch {
            delay(window)
            undoTimer = null
            if (phase != Phase.UNDO) return@launch
            if (pressing) retireOnLift = true else retire()
        }
    }

    /**
     * Undo, within the window: the flag cleared, and the card back as the watch reads now, or as its
     * tap still holds it, without its last answer.
     */
    fun undo() {
        if (phase != Phase.UNDO) return
        undoTimer?.cancel()
        undoTimer = null
        retireOnLift = false
        phase = Phase.OFFERING
        // As the watch reads now, which may be no card at all; a tap still on its way is joined by
        // the next one (Settings' requests), so the card comes back without its old answer.
        val now = watchCardOffer(targets) ?: onScreen?.takeIf { heldForTap }
        if (now != onScreen) endTapHold()
        onScreen = now
        tapped = false
        forget()
        write(false)
        publish()
        settle()
    }

    /** A reading's rows: the card in place, folded, put in before a touch, or left for the next time. */
    private fun take(rows: WatchTargets) {
        targets = rows
        if (phase != Phase.OFFERING) return
        val offer = watchCardOffer(rows)
        when {
            // Taqwa on its way to the watch the card's tap sent it to: the face to come takes its place.
            offer == null && heldForTap -> Unit
            onScreen != null || !touched -> show(offer)
        }
        publish()
        settle()
    }

    /** [offer] on Home; a different card, or none, drops the last one's answer, its tap and its hold. */
    private fun show(offer: Pair<WatchCardStep, WatchTarget>?) {
        if (offer == onScreen) return
        onScreen = offer
        endTapHold()
        forget()
    }

    /** No answer, and no tap of this card's on its way: one still going says nothing more here. */
    private fun forget() {
        generation++
        answer = null
        asking = false
    }

    /**
     * A tapped install card held through readings of no row for its tap's follow-up, [follow]'s
     * [TapFollow.lasting], away from Home too: Settings reads its rows that long after a tap.
     */
    private fun holdForTap(tap: Int) {
        endTapHold()
        if (!follow.lasting.isPositive()) return
        tapHoldOf = tap
        tapHold = scope.launch {
            delay(follow.lasting)
            tapHold = null
            foldUnheld()
        }
    }

    private fun endTapHold() {
        tapHold?.cancel()
        tapHold = null
    }

    /** Whether the install card on Home is held for its tap. */
    private val heldForTap: Boolean
        get() = tapHold != null && onScreen?.first == WatchCardStep.INSTALL

    /**
     * At a tap's hold's end: a card no reading answers any more folds while Home is in front; away
     * from it, the reading that brings Home back decides.
     */
    private fun foldUnheld() {
        if (phase != Phase.OFFERING || !visiting || onScreen == null) return
        if (watchCardOffer(targets) != null) return
        show(null)
        publish()
        settle()
    }

    /** The undo line's end: the card gone for good. */
    private fun retire() {
        undoTimer?.cancel()
        undoTimer = null
        retireOnLift = false
        endTapHold()
        phase = Phase.GONE
        publish()
        settle()
    }

    /** This stay's readings end once they can change nothing: the card gone, or Home touched with no card on it. */
    private fun settle() {
        if (phase == Phase.GONE || (phase == Phase.OFFERING && touched && onScreen == null)) visitOver.value = true
    }

    private fun write(value: Boolean) {
        val previous = writing
        writing = scope.launch {
            previous?.join()
            setDismissed(value)
        }
    }

    private fun publish() {
        _card.value = when (phase) {
            Phase.GONE -> WatchCardUi.Gone
            Phase.UNDO -> WatchCardUi.Dismissed
            Phase.OFFERING -> onScreen?.let { (step, target) -> WatchCardUi.Offer(step, target.name, answer, tapped) } ?: WatchCardUi.Hidden
        }
    }

    companion object {
        /** How long the line with Undo stays after the x (the owner's Q4), unless the reader asked for longer ([undoWindow]). */
        val UNDO_WINDOW: Duration = 6.seconds
    }
}

/**
 * The app's card, as [world.taqwa.app.App] holds it: the watch read through [source] on [readOn],
 * and its one flag kept in [settings]' own, [SettingsRepository.homeWatchCardDismissed].
 */
internal fun homeWatchCard(
    settings: SettingsRepository,
    scope: CoroutineScope,
    source: WatchTargetsSource = watchTargetsSource(),
    readOn: CoroutineDispatcher = Dispatchers.Default,
): HomeWatchCard = HomeWatchCard(
    source = source,
    isDismissed = { settings.homeWatchCardDismissed.first() },
    setDismissed = settings::setHomeWatchCardDismissed,
    scope = scope,
    readOn = readOn,
)

/**
 * Whether Home reads the watch for its card in this state: while it loads, and once ready in a
 * launch that showed no one-time card; never without a place, where no card can show.
 */
internal val PrayerUiState.readsWatchCard: Boolean
    get() = when (this) {
        PrayerUiState.Loading -> true
        PrayerUiState.NeedsLocation -> false
        is PrayerUiState.Ready -> !oneTimeCardThisLaunch
    }

/**
 * Reads [card]'s watch while Home is in front: each time the screen is RESUMED, until it is not.
 * Home's own: the card is read only while Home is the screen in front, and only in a [state] where
 * it can show ([readsWatchCard]): not without a place, nor in a launch that showed a one-time card.
 */
@Composable
internal fun FollowWatchCard(card: HomeWatchCard, state: PrayerUiState) {
    if (!state.readsWatchCard) return
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(card, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { card.whileResumed() }
    }
}
