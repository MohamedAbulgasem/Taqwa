package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.settings_add_to_apple_watch
import world.taqwa.app.resources.settings_add_to_apple_watch_failed
import world.taqwa.app.resources.settings_group_watch
import world.taqwa.app.resources.settings_watch_continue
import world.taqwa.app.resources.settings_watch_face
import world.taqwa.app.resources.settings_watch_install
import world.taqwa.app.resources.settings_watch_unreachable
import world.taqwa.app.watchface.WatchFaceOffer
import world.taqwa.app.watchface.WatchFaceResult
import world.taqwa.app.watchface.createWatchFaceOffer
import world.taqwa.app.watchfeed.WatchAction
import world.taqwa.app.watchfeed.WatchOpen
import world.taqwa.app.watchfeed.WatchRequests
import world.taqwa.app.watchfeed.WatchTarget
import world.taqwa.app.watchfeed.WatchTargets
import world.taqwa.app.watchfeed.WatchTargetsSource
import world.taqwa.app.watchfeed.loadWatchTargets
import world.taqwa.app.watchfeed.openOnWatch
import world.taqwa.app.watchfeed.watchSectionShown
import world.taqwa.app.watchfeed.watchTargetsSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// The phone's watch rows in Settings: plan 4's "Add to Apple Watch" (its call 6) and plan 5's
// Android rows, together in the root's Watch section, between App and About.

/**
 * Settings › Watch (plan 5 Task 6, spec 5): the section, with its gap before it, shown while a row
 * applies ([watchSectionShown]), and nothing at all otherwise.
 * - **Android:** "Install on your watch", its value the watch's name, opens Taqwa's Play page on a
 *   watch without Taqwa; "Add the Taqwa face" opens the face's screen on a watch that has the face
 *   to add (the Lead's call 5). Both go to the watch through `RemoteActivityHelper`, and a tap's
 *   answer, "Continue on your watch" (taken, or still on its way at the bound) or "Couldn't reach
 *   your watch" (failed), is a line of its own under the row ([RowAnswer]), said as it comes.
 * - **iPhone:** plan 4's "Add to Apple Watch" ([WatchFaceRow]) alone, while a watch is paired and
 *   the face is bundled.
 *
 * The rows are asked each time Settings is resumed, never remembered, off the main thread and for
 * 3 s at most ([loadWatchTargets]): a watch is paired, gets Taqwa and takes its face outside this
 * screen. While Settings stays in front they are asked again in place ([watchRowsChanged]) each
 * time the watch side says it changed ([WatchTargetsSource.changes], [WatchFaceOffer.changes]),
 * and after a row's tap for [follow], the app's [TapFollow.APP] unless a test gives its own. So the
 * install row gives way to "Add the Taqwa face" once the watch has Taqwa and has published its face,
 * and the section goes once the face is taken and nothing is left to offer (the owner's report of
 * 9 November: the install row stayed until Settings was left and brought back). Between the two
 * the watch has Taqwa and no face to offer yet, which is no row at all, so the install row is held
 * for up to [hold], the app's [InstallHold.APP] unless a test gives its own, rather than the
 * section going and coming back. A reading in place keeps each row's tap answer while the row
 * stays, and one that fails keeps the rows as they were; only a resume's reading starts afresh.
 *
 * A row's tap goes through [requests], the app's own [WatchRequests.process] unless a test gives
 * its own, and says "Continue on your watch" at [bound] while still on its way ([openOnWatch]), the
 * app's [WatchRequests.BOUND] unless a test gives a shorter one: Ruling 74 item 12.
 */
@Composable
internal fun WatchSection(
    source: WatchTargetsSource = remember { watchTargetsSource() },
    offer: WatchFaceOffer = remember { createWatchFaceOffer() },
    requests: WatchRequests = WatchRequests.process,
    bound: Duration = WatchRequests.BOUND,
    follow: TapFollow = TapFollow.APP,
    hold: Duration = InstallHold.APP,
) {
    var targets by remember(source) { mutableStateOf(WatchTargets.NONE) }
    var appleOffered by remember(offer) { mutableStateOf(false) }
    // Which reading of the rows is shown: a tap's answer belongs to the reading it was made in, so a
    // watch that could not be reached is asked again, by name, when Settings comes back. A reading
    // in place, while Settings stays in front, is part of the same one.
    var reading by remember(source) { mutableIntStateOf(0) }
    // The rows' taps, each followed by readings for a while; one made while nobody follows is lost.
    val taps = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(source, offer, follow, hold, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            readWatchRows(source, offer, taps, follow, hold, shown = { targets }, offered = { appleOffered = it }) { rows, resumed ->
                targets = rows
                if (resumed) reading += 1
            }
        }
    }
    if (!watchSectionShown(targets, appleOffered)) return

    GroupGap()
    SectionLabel(stringResource(Res.string.settings_group_watch))
    SettingsCard {
        val install = targets.install
        val addFace = targets.addFace
        if (install != null) {
            WatchActionRow(
                source,
                install,
                WatchAction.INSTALL,
                stringResource(Res.string.settings_watch_install),
                named = true,
                reading,
                requests,
                bound,
                tapped = { taps.tryEmit(Unit) },
            )
        }
        if (addFace != null) {
            if (install != null) CardDivider()
            WatchActionRow(
                source,
                addFace,
                WatchAction.ADD_FACE,
                stringResource(Res.string.settings_watch_face),
                named = false,
                reading,
                requests,
                bound,
                tapped = { taps.tryEmit(Unit) },
            )
        }
        WatchFaceRow(offer, offered = appleOffered, divider = install != null || addFace != null)
    }
}

/**
 * One Android row: [label], its value the watch's name when [named], and after a tap the tap's
 * answer under it ([RowAnswer]), so TalkBack says "Continue on your watch" as it comes: once the
 * watch took it, and at the bound while it is still on its way ([openOnWatch], Ruling 72 item 3).
 * "Couldn't reach your watch" only when it failed. A tap while this row's request is on its way,
 * the bound passed or not, starts nothing more; after the rows are read again, a tap joins the
 * request still on its way in [requests], so the watch never opens the screen twice. Once the
 * request has expired ([WatchRequests.EXPIRY]), the row takes a tap again, and it sends anew.
 * Each tap that starts is [tapped], so the section follows it with readings ([TapFollow]).
 *
 * Not the row's value: a value has one line, and where it and the label do not both fit the label
 * keeps 60 % of the row, which cut even the English ("Continue on your w…") on a 411 dp phone. A
 * tap has no other answer.
 */
@Composable
private fun WatchActionRow(
    source: WatchTargetsSource,
    target: WatchTarget,
    action: WatchAction,
    label: String,
    named: Boolean,
    reading: Int,
    requests: WatchRequests,
    bound: Duration,
    tapped: () -> Unit,
) {
    // The last tap's answer, and whether its request is still on its way: both belong to the reading.
    var answer by remember(source, target, reading) { mutableStateOf<WatchOpen?>(null) }
    var asking by remember(source, target, reading) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column {
        LinkRow(
            label,
            value = if (named) target.name else null,
            // The glyph's own rule (drawExternalLink): its arrow leaves toward the trailing edge.
            forward = LocalLayoutDirection.current == LayoutDirection.Ltr,
        ) {
            if (asking) return@LinkRow
            asking = true
            answer = null
            tapped()
            scope.launch {
                try {
                    openOnWatch(source, target, action, bound, requests).collect { answer = it }
                } finally {
                    asking = false
                }
            }
        }
        when (answer) {
            WatchOpen.OPENED, WatchOpen.QUEUED -> RowAnswer(stringResource(Res.string.settings_watch_continue))
            WatchOpen.UNREACHABLE -> RowAnswer(stringResource(Res.string.settings_watch_unreachable))
            null -> Unit
        }
    }
}

/**
 * What a tap on the row above came to, in a line of its own level with the row's label, wrapped
 * rather than cut, and said as it comes (a polite live region). [WatchActionRow]'s answers and
 * [WatchFaceRow]'s failure line both come this way.
 */
@Composable
private fun RowAnswer(text: String) {
    // One node that holds the words: CMP 1.12.1's iOS mediator announces a live region only from
    // that node's own label, and a box that merges nothing has none.
    Column(
        Modifier.padding(bottom = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        // Level with the row's label: TaqwaRow's 16 dp, inside the card.
        SettingsNote(text, gutter = 12.dp)
    }
}

/**
 * "Add to Apple Watch" (plan 4 Task 10, spec 4.5): one of Apple's faces with Taqwa's complications
 * in place, from the bundled `.watchface` file, through the system's own add-face sheet. Drawn
 * inside a settings card, after a row of its own when [divider] says so, so it brings that divider;
 * nothing at all while [offered] does not hold, which on Android is always, and on the iPhone is
 * until a watch is paired and the file is bundled.
 *
 * The offer is asked each time the app comes to the front, never remembered: a watch is paired and
 * unpaired in the Watch app, outside Taqwa. [WatchSection] asks it with its own rows, and again in
 * place when the session says it changed, and passes the answer in [offered]; left out, the row
 * asks for itself, at each coming to the front alone. A face that was not added says so in one
 * line under the row, spoken as it comes, until one is added or the row goes.
 */
@Composable
internal fun WatchFaceRow(
    offer: WatchFaceOffer = remember { createWatchFaceOffer() },
    offered: Boolean = rememberOffered(offer),
    divider: Boolean = true,
) {
    // Before any state of its own: a row that goes forgets its line, so it comes back clean.
    if (!offered) return
    var failed by remember(offer) { mutableStateOf(false) }

    Column {
        if (divider) CardDivider()
        LinkRow(
            stringResource(Res.string.settings_add_to_apple_watch),
            value = null,
            // The glyph's own rule (drawExternalLink): its arrow leaves toward the trailing edge.
            forward = LocalLayoutDirection.current == LayoutDirection.Ltr,
        ) {
            offer.add { result -> failed = result == WatchFaceResult.FAILED }
        }
        if (failed) RowAnswer(stringResource(Res.string.settings_add_to_apple_watch_failed))
    }
}

/** Whether [offer] is available, asked again each time the app comes to the front. */
@Composable
private fun rememberOffered(offer: WatchFaceOffer): Boolean {
    var offered by remember(offer) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(offer, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            offered = offer.available()
        }
    }
    return offered
}

/**
 * How Settings follows a row's tap with readings of its own, in case the watch side does not say it
 * changed: one each [every], for [lasting] after the tap, and a tap meanwhile starts it again. It
 * is a fallback beside the listening ([WatchTargetsSource.changes]): no device has shown yet how
 * promptly a watch's capability change reaches Settings, and a tap is when the watch is about to
 * change. Only while Settings stays in front: leaving it ends the follow, and coming back reads the
 * rows anyway.
 */
internal data class TapFollow(val every: Duration, val lasting: Duration) {

    /** One after each [every], up to [lasting] after the latest of [taps]. */
    fun after(taps: Flow<Unit>): Flow<Unit> = channelFlow {
        val readings = if (every.isPositive()) (lasting / every).toInt() else 0
        taps.collectLatest {
            repeat(readings) {
                delay(every)
                send(Unit)
            }
        }
    }

    companion object {
        /**
         * The app's: every 5 s for 3 minutes, so at most 36 readings after a tap, each the one a
         * resume makes, bounded at 3 s ([loadWatchTargets]). Every 5 s, so the rows trail the watch
         * by at most 5 s and a reading. For 3 minutes, so it covers what follows the tap on the
         * watch: a request a cached companion holds is joined for 2 minutes
         * ([WatchRequests.EXPIRY]; it delivered one 45 s late at P5.12 Step 3), and the wearer
         * still acts on the watch once it opens, the Play Store's install or the face screen's
         * button with its permission and the push (FaceActivity's 20 s). Past it, the listening
         * and the next resume.
         */
        val APP: TapFollow = TapFollow(every = 5.seconds, lasting = 3.minutes)
    }
}

/**
 * How [WatchSection] keeps the install row while a watch that has just got Taqwa has not yet said it
 * has the face to add (the review of 9 November). Taqwa's watch app brings `taqwa_watch` with the
 * install, a static capability, so a reading in place drops the install row at once; but
 * `taqwa_face_addable` comes only once the app has run and published its face
 * (`TaqwaWearApp.refreshFace`), which the phone's feed, sent at `taqwa_watch`'s change, starts. In
 * between the rows answer no row at all ([watchTargets]), and without a hold the whole section
 * would go and come back with the face row: About moving up and down, and TalkBack's focus on the
 * row dropped.
 *
 * So a reading in place that takes rows with an install row to no row at all keeps the rows shown,
 * for [lasting] from the first such reading, and once that has passed [ended] asks for one more
 * reading, whose answer is taken as it comes. Any other answer meanwhile, the face row above all, is
 * shown at once and ends the hold; one that answers the rows shown (a reading that failed, through
 * `loadWatchTargets`' otherwise) changes nothing, the hold included. A hold over is spent by the
 * next answer taken, so the next watch to get Taqwa is held as long. Rows without an install row are
 * never held: a face taken makes the section go at once.
 *
 * The rows cannot tell why no row came, so a watch without Taqwa that leaves while its install row
 * is shown keeps that row for the hold too. Each coming to the front makes a new hold
 * ([WatchSection]), and Settings' resume's own reading takes no row as it comes; Home's card, kept
 * from Home's last visit, is held from its resume's reading on ([readWatchRows]' keepOnResume).
 * [rows] is called, and the hold runs, on [scope]'s dispatcher alone, Settings' main thread in the
 * app, so nothing here needs a lock.
 */
internal class InstallHold(
    private val lasting: Duration,
    private val scope: CoroutineScope,
    private val ended: () -> Unit,
) {
    /** The hold running, if one is: it ends at [lasting], or when an answer is taken first. */
    private var holding: Job? = null

    /** Whether the hold has reached [lasting], so the next reading's answer is taken whatever it is. */
    private var over = false

    /** The rows to show after a reading in place answered [read] while [shown] were shown. */
    fun rows(shown: WatchTargets, read: WatchTargets): WatchTargets {
        if (read == WatchTargets.NONE && shown.install != null && !over) {
            if (holding == null) {
                holding = scope.launch {
                    delay(lasting)
                    over = true
                    ended()
                }
            }
            return shown
        }
        if (read != shown || over) {
            holding?.cancel()
            holding = null
            over = false
        }
        return read
    }

    companion object {
        /**
         * The app's: 20 s, the bounds on the watch's way from the install to the face's word,
         * rounded up. The phone's feed, whose message starts the watch app, is sent within
         * `WatchFeedSync.CALLER_BOUND` (10 s) of `taqwa_watch`'s change, and the app's start
         * publishes the face within `FACE_BOUND_MS` (9 s, `FaceCapability`'s own 5 s inside it).
         * Long enough for that, as P5.6 Step 6 check 2 saw the face published at the app's start
         * (nobody has timed it from the install); short enough that a watch whose face never comes
         * loses its section soon. Past it, a face that comes later brings the section back by
         * itself: the listening and a tap's follow read it. A watch the phone has fed before is
         * sent no feed when Taqwa is installed on it again (`WATCH_APPEARED` is damped: the phone's
         * record says the watch holds it), so its app publishes its face only once something else
         * starts it, such as the Play Store's Open.
         */
        val APP: Duration = 20.seconds
    }
}

/**
 * When [WatchSection] reads its rows again while Settings stays in front: each time [source] says
 * the watch side changed (listened to on [listenOn], off the main thread), each time [offer] says
 * the session changed, as [follow] follows each of [taps], and at each of [holdEnds], a hold on the
 * install row ending ([InstallHold]). Several before a reading is done are one reading after it
 * (conflated). A source or an offer that cannot listen says nothing more and ends nothing, so a
 * throw there never reaches Settings.
 */
internal fun watchRowsChanged(
    source: WatchTargetsSource,
    offer: WatchFaceOffer,
    taps: Flow<Unit>,
    follow: TapFollow,
    holdEnds: Flow<Unit> = emptyFlow(),
    listenOn: CoroutineDispatcher = Dispatchers.Default,
): Flow<Unit> = channelFlow {
    // Merged by hand: merge()'s own channel drops conflate()'s overflow in kotlinx.coroutines 1.10.2
    // (ChannelLimitedFlowMerge.produceImpl passes the capacity alone), so it would queue every change.
    launch { source.changes().flowOn(listenOn).catch { }.collect { send(Unit) } }
    launch { offer.changes().catch { }.collect { send(Unit) } }
    launch { follow.after(taps).collect { send(Unit) } }
    launch { holdEnds.collect { send(Unit) } }
}.conflate()

/**
 * One stay in front for the watch rows, Settings' and Home's: the rows read as the screen comes to
 * the front, then again in place each time [watchRowsChanged] says so, an install row held for
 * [hold] ([InstallHold]). [offered] is told [WatchFaceOffer.available] before each reading and [show]
 * each reading's rows, `resumed` for the first; [shown] is what the last reading left on screen, which
 * a reading that fails keeps and the hold compares with. The reads and the listening run [on], off
 * the main thread in the app; [show] is called on the caller's own. Runs until cancelled.
 *
 * The first reading, Settings', takes its rows as they come, no row and a failure included: the
 * section is built afresh. With [keepOnResume], Home's, for a card kept from Home's last visit, it is
 * taken as a reading in place is: a failure keeps what [shown] says, and an install row is held
 * through no row (the review of 20 November).
 */
internal suspend fun readWatchRows(
    source: WatchTargetsSource,
    offer: WatchFaceOffer,
    taps: Flow<Unit>,
    follow: TapFollow,
    hold: Duration,
    shown: () -> WatchTargets,
    offered: (Boolean) -> Unit = {},
    on: CoroutineDispatcher = Dispatchers.Default,
    keepOnResume: Boolean = false,
    show: (rows: WatchTargets, resumed: Boolean) -> Unit,
): Unit = coroutineScope {
    // The end of each hold, read as any other change.
    val holdEnds = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    // Listening starts as the rows are read, so a change heard meanwhile is read after them,
    // and Android's says one change once its listener is in place, for one that came before.
    val changed = watchRowsChanged(source, offer, taps, follow, holdEnds, listenOn = on).produceIn(this)
    // A new hold at each coming to the front, whose first reading below takes no row as it comes
    // unless what is on screen is kept.
    val held = InstallHold(hold, this) { holdEnds.tryEmit(Unit) }
    offered(offer.available())
    if (keepOnResume) {
        val before = shown()
        val read = withContext(on) { loadWatchTargets(source, otherwise = before) }
        show(held.rows(before, read), true)
    } else {
        show(withContext(on) { loadWatchTargets(source) }, true)
    }
    for (change in changed) {
        val before = shown()
        offered(offer.available())
        val read = withContext(on) { loadWatchTargets(source, otherwise = before) }
        show(held.rows(before, read), false)
    }
}
