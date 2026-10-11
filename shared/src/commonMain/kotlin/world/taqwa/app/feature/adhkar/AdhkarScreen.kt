package world.taqwa.app.feature.adhkar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.adhkar.AdhkarPhase
import world.taqwa.app.adhkar.AdhkarSet
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.Equaliser
import world.taqwa.app.design.components.RingArc
import world.taqwa.app.design.components.TaqwaSegmented
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.design.components.drawSpeaker
import world.taqwa.app.design.contentWidth
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhkar_a11y_next
import world.taqwa.app.resources.adhkar_a11y_previous
import world.taqwa.app.resources.adhkar_after_maghrib
import world.taqwa.app.resources.adhkar_after_sunrise
import world.taqwa.app.resources.adhkar_back
import world.taqwa.app.resources.adhkar_done_evening
import world.taqwa.app.resources.adhkar_done_morning
import world.taqwa.app.resources.adhkar_evening
import world.taqwa.app.resources.adhkar_morning
import world.taqwa.app.resources.adhkar_next_evening
import world.taqwa.app.resources.adhkar_next_morning
import world.taqwa.app.resources.adhkar_of
import world.taqwa.app.resources.adhkar_progress
import world.taqwa.app.resources.adhkar_segment_evening
import world.taqwa.app.resources.adhkar_segment_morning
import world.taqwa.app.resources.adhkar_until_maghrib
import world.taqwa.app.resources.adhkar_until_sunrise
import world.taqwa.app.resources.adhkar_window_evening
import world.taqwa.app.resources.adhkar_window_morning
import world.taqwa.app.resources.quran_reader_aa
import world.taqwa.app.resources.recitation_a11y_pause
import world.taqwa.app.resources.recitation_play
import world.taqwa.app.resources.tasbeeh_hint

/** The foot's ring and the count inside it (spec §3.1): a small cousin of the tasbeeh's. */
private val RingDiameter = 64.dp
private val CountSize = 22.sp
private val CountMinSize = 12.sp
private val CountStep = 0.5.sp

/** One tap's bump, the tasbeeh's: 1 → 1.06 → 1 over 120 ms. */
private const val BumpScale = 1.06f
private const val BumpHalfMillis = 60

/** The ring's tween to a new count, the tasbeeh's. */
private const val ProgressMillis = 250

/** The done page's halo behind the check (spec §3.3). */
private const val DoneHaloAlpha = 0.18f

/**
 * The adhkar session (spec §3.1): one dhikr at a time on the Tasbeeh grammar — a header naming the
 * set and where its time stands, the Morning | Evening switch, how many pages are counted, the
 * pager, and a foot with the ring.
 *
 * Stateless: [state] is the view model's, every gesture goes back through a callback. The screen
 * keeps only what is purely visual — the count's bump and the ring's tween. There is no reduced-
 * motion signal in the app to read yet (see `Equaliser`), so both run as the tasbeeh's do.
 *
 * [onPlay] is the Quran pages' speaker, null where there is nothing to play (and until recitation
 * is wired in); [onOpenReading] is the "Aa" button. `KeepScreenOn` is the route's, not this
 * screen's, so the screen can be composed under a UI test.
 *
 * Landscape is the same column, capped to the content width; the pager takes whatever height is
 * left, and a page scrolls.
 */
@Composable
fun AdhkarScreen(
    state: AdhkarUiState,
    onBack: () -> Unit,
    onSelectSet: (AdhkarSet) -> Unit,
    onGoTo: (Int) -> Unit,
    onTap: () -> Unit,
    onPlay: (() -> Unit)?,
    playing: Boolean,
    onOpenReading: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val scope = rememberCoroutineScope()
    val page = state.pages.getOrNull(state.currentPage)

    // Kicked on every counted tap; `snapTo` restarts it rather than queueing, so a fast burst
    // bumps once per tap and never falls behind (the tasbeeh's rule).
    val bump = remember { Animatable(1f) }
    val progress by animateFloatAsState(
        targetValue = if (page == null || page.target <= 0) 0f else page.count.toFloat() / page.target,
        animationSpec = tween(durationMillis = ProgressMillis),
        label = "adhkarProgress",
    )

    fun count() {
        // A completed page and the done page take the tap and do nothing; nor does the count.
        val counts = state.pages.getOrNull(state.currentPage)?.done == false
        onTap()
        if (counts) {
            scope.launch {
                bump.snapTo(1f)
                bump.animateTo(BumpScale, tween(BumpHalfMillis))
                bump.animateTo(1f, tween(BumpHalfMillis))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .contentWidth(),
        ) {
            Header(state, onBack, onPlay, playing, onOpenReading)
            if (!state.loaded) return@Column

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TaqwaSegmented(
                    options = listOf(
                        stringResource(Res.string.adhkar_segment_morning),
                        stringResource(Res.string.adhkar_segment_evening),
                    ),
                    selectedIndex = state.set.ordinal,
                    onSelect = { onSelectSet(AdhkarSet.entries[it]) },
                )
            }
            Spacer(Modifier.height(12.dp))
            ProgressRow(state.completed, state.pages.size)

            // Keyed on the set: the other set's pager opens on its own first undone page rather
            // than sliding there from wherever this one was.
            key(state.set) {
                val pagerState = rememberPagerState(initialPage = state.currentPage) { state.pages.size + 1 }
                SessionPagerSync(pagerState, state.currentPage, onGoTo)
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                    val shown = state.pages.getOrNull(index)
                    if (shown == null) {
                        DonePage(state.set, onBack)
                    } else {
                        AdhkarPageView(
                            page = shown,
                            arabicSizeSp = state.arabicSizeSp,
                            showTranslation = state.showTranslation,
                            transliteration = state.transliteration,
                            onTap = { count() },
                        )
                    }
                }
            }

            // Until the first tap of the session, as on the tasbeeh.
            if (state.completed == 0 && state.pages.all { it.count == 0 }) {
                Text(
                    stringResource(Res.string.tasbeeh_hint),
                    style = TaqwaText.caption.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Foot(state, page, progress, bump.value, onGoTo)
        }
    }
}

/**
 * Keeps the pager and the session's page in step, both ways: the view model moves the page (a
 * completed page advancing, a chevron) and the pager follows; the reader swipes and the view model
 * follows the page the pager settles on.
 *
 * **The pager always lands.** Every new [page] is animated to without asking first whether the
 * pager is already there — `animateScrollToPage` returns at once when it is exactly on the page,
 * and "already there" by `currentPage` alone is true of a pager stopped 40 % of the way off it.
 *
 * **While the session is moving the pager, the pager does not report.** A scroll that starts
 * while another is running records the page it was passing as "settled", so a second page change
 * arriving mid-animation would report that half-way page; the view model would move back to it,
 * that would cancel the animation, and the pager would rest between two pages. So the settled
 * page is ignored while a target of the session's is pending. The target is cleared when its
 * animation finishes, or when a finger takes the pager (the mutation is cancelled under an effect
 * that is still active) — the swipe is then the reader's, and is reported when it settles: the
 * target is held until the finger's scroll has stopped, since while it runs `settledPage` is still
 * the page the animation set out from, and reporting that would send the session back to it under
 * the reader's finger. The finger's scroll is given a frame to start first: the animation is
 * cancelled before the scroll that cancelled it begins, and in between nothing is scrolling. An
 * effect cancelled by the next target leaves the target to that one.
 */
@Composable
internal fun SessionPagerSync(pagerState: PagerState, page: Int, onSettled: (Int) -> Unit) {
    var pendingTarget by remember(pagerState) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pagerState, page) {
        pendingTarget = page
        try {
            pagerState.animateScrollToPage(page)
        } catch (interrupted: CancellationException) {
            if (!isActive) throw interrupted
            withFrameNanos { }
            snapshotFlow { pagerState.isScrollInProgress }.first { scrolling -> !scrolling }
        }
        pendingTarget = null
    }
    val settled by rememberUpdatedState(onSettled)
    LaunchedEffect(pagerState) {
        snapshotFlow { if (pendingTarget == null) pagerState.settledPage else null }
            .filterNotNull()
            .collect { settled(it) }
    }
}

/**
 * The page after the last (spec §3.3): a check in a faint accent halo, "Morning adhkar · done",
 * when the other set opens, and the way back to the prayer times. No button, no streak, no total,
 * nothing to share — the set is said, and that is all it says. It takes no tap: there is nothing
 * left to count.
 *
 * Centred in the pager's height, and scrolling when that height is short — landscape, or a
 * 200 % font — rather than clipping (spec §3.7).
 */
@Composable
private fun DonePage(set: AdhkarSet, onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val morning = set == AdhkarSet.MORNING
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = DoneHaloAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                CheckMark()
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (morning) Res.string.adhkar_done_morning else Res.string.adhkar_done_evening),
                style = TaqwaText.rowLabel,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            // The other set, and when it opens: the morning done points to the evening after Asr.
            Text(
                stringResource(if (morning) Res.string.adhkar_next_evening else Res.string.adhkar_next_morning),
                style = TaqwaText.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            TaqwaTextLink(stringResource(Res.string.adhkar_back), onBack)
        }
    }
}

/**
 * The Tasbeeh header idiom: the chevron, the set's name in the reader header's words and weight
 * with the phase under it, then the speaker (Quran pages, when there is something to play) and
 * "Aa". The name and phase are padded down by the chevron's own 8 dp so the three line up on its
 * centre.
 */
@Composable
private fun Header(
    state: AdhkarUiState,
    onBack: () -> Unit,
    onPlay: (() -> Unit)?,
    playing: Boolean,
    onOpenReading: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BackChevron(onBack)
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 4.dp, top = 8.dp)) {
            Text(
                stringResource(if (state.set == AdhkarSet.EVENING) Res.string.adhkar_evening else Res.string.adhkar_morning),
                style = TaqwaText.rowLabel.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.loaded) {
                Text(
                    phaseCaption(state),
                    style = TaqwaText.caption.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onPlay != null) {
            val mirrored = isRtlLocale()
            HeaderButton(
                description = stringResource(if (playing) Res.string.recitation_a11y_pause else Res.string.recitation_play),
                onClick = onPlay,
            ) {
                val tint = if (playing) colors.accent else colors.textSecondary
                if (playing) {
                    Equaliser(tint, size = 17.dp)
                } else {
                    Canvas(Modifier.size(18.dp)) { drawSpeaker(tint, pointsForward = !mirrored) }
                }
            }
        }
        HeaderButton(description = stringResource(Res.string.quran_reader_aa), onClick = onOpenReading) {
            Text(
                "Aa",
                style = TaqwaText.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                color = colors.textSecondary,
                // The button says what it opens; the two letters are its picture, not its name.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** A 44 dp header target with no ripple, drawn and placed as the tasbeeh's plus is. */
@Composable
private fun HeaderButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(end = 8.dp, top = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Where the set's time stands (spec §3.4): "Until sunrise · 6:12" in its best time, "After sunrise ·
 * until 12:46" in its make-up time, and the plain window outside both.
 */
@Composable
private fun phaseCaption(state: AdhkarUiState): String {
    val morning = state.set == AdhkarSet.MORNING
    val until = state.until
    return when {
        state.phase == AdhkarPhase.BEST && until != null ->
            stringResource(if (morning) Res.string.adhkar_until_sunrise else Res.string.adhkar_until_maghrib, until)
        state.phase == AdhkarPhase.MAKEUP && until != null ->
            stringResource(if (morning) Res.string.adhkar_after_sunrise else Res.string.adhkar_after_maghrib, until)
        else -> stringResource(if (morning) Res.string.adhkar_window_morning else Res.string.adhkar_window_evening)
    }
}

/** "4 of 18" and the continue-reading card's 3 dp bar: pages counted over pages. */
@Composable
private fun ProgressRow(completed: Int, pages: Int) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val fraction = if (pages <= 0) 0f else (completed.toFloat() / pages).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.adhkar_progress, format.localizedDigits(completed), format.localizedDigits(pages)),
            style = TaqwaText.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.hairline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent),
            )
        }
    }
}

/**
 * The foot (spec §3.1): previous, the ring with the count and "of 3" beside it, next. On a page
 * said once — and on the done page — the ring and its caption are drawn invisible rather than
 * left out, so the chevrons never move.
 */
@Composable
private fun Foot(
    state: AdhkarUiState,
    page: AdhkarPageUi?,
    progress: Float,
    scale: Float,
    onGoTo: (Int) -> Unit,
) {
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChevronButton(
            description = stringResource(Res.string.adhkar_a11y_previous),
            enabled = state.currentPage > 0,
            pointsForward = !forward,
            onClick = { onGoTo(state.currentPage - 1) },
        )
        Counter(page, progress, scale)
        ChevronButton(
            description = stringResource(Res.string.adhkar_a11y_next),
            enabled = state.currentPage < state.pages.size,
            pointsForward = forward,
            onClick = { onGoTo(state.currentPage + 1) },
        )
    }
}

/**
 * The count in the ring, in the countdown's own style at 22 sp — shrinking in half points to fit
 * at large text sizes rather than spilling over the stroke ([TextAutoSize.StepBased], as the
 * ayah actions' labels do) — and "of 3" beside it.
 *
 * A screen reader hears the count as "1 of 3", politely, after every tap; "of 3" alone is not said
 * again.
 */
@Composable
private fun Counter(page: AdhkarPageUi?, progress: Float, scale: Float) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val hidden = page == null || page.target == 1
    val count = page?.count ?: 0
    val target = page?.target ?: 1
    val spoken = stringResource(Res.string.adhkar_progress, format.localizedDigits(count), format.localizedDigits(target))
    Row(
        if (hidden) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(RingDiameter), contentAlignment = Alignment.Center) {
            RingArc(progress = progress, diameter = RingDiameter)
            // `BasicText` for `autoSize`; it inherits nothing, so the app's text style is merged
            // in by hand (AyahActions does the same).
            BasicText(
                format.localizedDigits(count),
                style = LocalTextStyle.current.merge(TaqwaText.countdown).copy(
                    fontSize = CountSize,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = CountMinSize, maxFontSize = CountSize, stepSize = CountStep),
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = spoken
                    },
            )
        }
        Text(
            stringResource(Res.string.adhkar_of, format.localizedDigits(target)),
            style = TaqwaText.caption,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Previous or next: a 44 dp target around a 20 dp chevron, faint when there is nowhere to go. */
@Composable
private fun ChevronButton(description: String, enabled: Boolean, pointsForward: Boolean, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val tint = if (enabled) colors.textTertiary else colors.textTertiary.copy(alpha = 0.35f)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) { drawChevron(tint, pointsForward) }
    }
}
