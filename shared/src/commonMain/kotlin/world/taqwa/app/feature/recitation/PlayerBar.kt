package world.taqwa.app.feature.recitation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.drawClose
import world.taqwa.app.design.components.drawPause
import world.taqwa.app.design.components.drawPlayTriangle
import world.taqwa.app.design.components.drawSkip
import world.taqwa.app.design.contentWidth
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_ayah_n
import world.taqwa.app.resources.recitation_a11y_change_reciter
import world.taqwa.app.resources.recitation_a11y_close
import world.taqwa.app.resources.recitation_a11y_go_to_playing
import world.taqwa.app.resources.recitation_a11y_next
import world.taqwa.app.resources.recitation_a11y_next_ayah
import world.taqwa.app.resources.recitation_a11y_pause
import world.taqwa.app.resources.recitation_a11y_previous
import world.taqwa.app.resources.recitation_a11y_previous_ayah
import world.taqwa.app.resources.recitation_arriving_next
import world.taqwa.app.resources.recitation_arriving_voice
import world.taqwa.app.resources.recitation_percent
import world.taqwa.app.resources.recitation_back_to_ayah
import world.taqwa.app.resources.recitation_play

/**
 * The finger's share of previous, play and next (spec §14.2): 48 dp, up from 44, and the glyphs
 * inside them up with it. The three sit shoulder to shoulder, and on the first phone round a
 * thumb that meant Next was landing on Play.
 */
private val Target = 48.dp

/** The dismiss cross. Its glyph stays the smallest of the four so it is the hardest to hit by
 * accident, but the target is the 48 dp accessibility minimum like the others. */
private val CloseTarget = 48.dp

/** The accent disc under the play triangle, 40 dp inside its 48 dp target (was 34 in 44). */
private val PlayDisc = 40.dp

private val Monogram = 40.dp

/** The monogram's box, with room for the incoming-voice ring around it. */
private val MonogramBox = 46.dp

/** The clock row: elapsed, the line, the total, with air above it so the clocks do not sit on
 * the bar's top edge (spec §15.2). */
private val ClockRow = 26.dp

/** The air above the clocks, inside [ClockRow]. */
private val ClockInset = 5.dp

/** The transport row: monogram, surah, the four buttons. */
private val TransportRow = 56.dp

/** How far the bar must be dragged down before it counts as a dismissal rather than a fumble. */
private val DismissDrag = 36.dp

/** A surah that runs an hour or more shows both its clocks as h:mm:ss. */
private const val HOUR_MS = 3_600_000L

/**
 * The player bar (spec §5.3, §14.2, §15): a hairline over the card surface, then the surah's
 * clock — elapsed, a 3 dp line, total, the way every music player draws a track — then a 56 dp
 * transport row. The whole thing is [PlayerBarHeight], which the reader and the Mushaf keep
 * clear.
 *
 * Previous and next move by **surah**, like a track skip; a long press on either moves by ayah
 * (spec §15.1). [onPrevious]/[onNext] are the surah moves, [onPreviousAyah]/[onNextAyah] the
 * long presses.
 *
 * The clock is the surah's, not the ayah's ([SurahTimeline]): "12:31" of "2:05:10" through
 * Al-Baqarah, moving at the speed of the recitation, gaps and all. A line that filled and emptied
 * every few seconds said nothing a listener could use.
 *
 * [surahName] is resolved by the caller — Arabic under an Arabic UI, Latin otherwise, as the
 * reader header does — because only the caller has the surah's row from the database.
 *
 * It is an overlay in the tab scaffold rather than part of any screen, so it survives the walk
 * from the reader to the Mushaf to the surah list.
 */
@Composable
fun PlayerBar(
    bar: BarState,
    surahName: String,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPicker: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onNextAyah: () -> Unit = {},
    onPreviousAyah: () -> Unit = {},
    /** The name of [BarState.incoming]'s surah, resolved by the caller like [surahName]. */
    incomingSurahName: String = "",
    /** A tap on the surah and ayah: show the ayah being recited (spec §15.5). */
    onOpenPlaying: () -> Unit = {},
    /** A tap on the line (spec §17.5): how far along it, from the reading edge, 0 to 1. */
    onSeek: (Float) -> Unit = {},
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val arabic = isRtlLocale()
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dismissPx = with(LocalDensity.current) { DismissDrag.toPx() }
    // The line eases to each new position rather than stepping: a seek moves it by a whole
    // stretch, and a bar that jumped would read as a glitch rather than as progress.
    val fraction by animateFloatAsState(bar.fraction, tween(400), label = "recitationProgress")
    val hasClock = bar.durationMs > 0L
    val hours = bar.durationMs >= HOUR_MS
    val changeReciterLabel = stringResource(Res.string.recitation_a11y_change_reciter)
    val goToPlayingLabel = stringResource(Res.string.recitation_a11y_go_to_playing)
    val elapsed = if (hasClock) formatClock(bar.positionMs, hours, format::localizedDigits) else ""
    val total = if (hasClock) formatClock(bar.durationMs, hours, format::localizedDigits) else ""

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            // The bar covers the bottom edge, so it is the bar that clears the gesture area.
            // Inside the tab scaffold's own consumed insets this comes to nothing and the tab
            // bar below it does the clearing instead — which is exactly right: on a tab root the
            // bar is not the bottom-most thing on the screen.
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .pointerInput(Unit) {
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (travelled > dismissPx) onDismiss() },
                ) { change, delta ->
                    change.consume()
                    travelled += delta
                    if (travelled < 0f) travelled = 0f
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        // What the bar is waiting on (spec §15.4): the next surah after a skip, or a new voice's
        // copy of this one. Slides in above the clock and goes when the download lands, so the
        // tap that started it is seen to have done something while this surah plays on.
        AnimatedVisibility(
            visible = bar.incoming != null,
            enter = expandVertically(tween(BAR_MILLIS)) + fadeIn(tween(BAR_MILLIS)),
            exit = shrinkVertically(tween(BAR_MILLIS)) + fadeOut(tween(BAR_MILLIS / 2)),
        ) {
            val incoming = bar.incoming
            if (incoming != null) {
                IncomingStrip(
                    name = if (incoming.surah != bar.surah) incomingSurahName else reciterName(incoming.reciter),
                    nextSurah = incoming.surah != bar.surah,
                    fraction = incoming.fraction,
                )
            }
        }
        Row(
            Modifier
                .contentWidth()
                .height(ClockRow)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(top = ClockInset)) { Clock(elapsed) }
            // The line is 3 dp and stays 3 dp; what takes the tap is the whole height of the row
            // around it, exactly as wide as the line, so a fraction of this box is a fraction of
            // the line (spec §17.5). It fills from the reading edge — the right one under an
            // Arabic UI — so that is the edge a tap is measured from.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .then(
                        if (!hasClock) {
                            Modifier
                        } else {
                            Modifier
                                .pointerInput(forward) {
                                    detectTapGestures { tap ->
                                        val along = (tap.x / size.width).coerceIn(0f, 1f)
                                        onSeek(if (forward) along else 1f - along)
                                    }
                                }
                                .semantics {
                                    progressBarRangeInfo = ProgressBarRangeInfo(bar.fraction.coerceIn(0f, 1f), 0f..1f)
                                    setProgress { target ->
                                        onSeek(target)
                                        true
                                    }
                                }
                        },
                    )
                    .padding(top = ClockInset),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(colors.hairline),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(colors.accent),
                    )
                }
            }
            Box(Modifier.padding(top = ClockInset)) { Clock(total) }
        }
        Row(
            Modifier
                .contentWidth()
                .height(TransportRow)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Two taps, split where the eye already splits them (spec §15.5): the monogram is the
            // voice and opens the picker; the surah and ayah are the place and open it.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(MonogramBox)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenPicker,
                        )
                        .semantics { contentDescription = changeReciterLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    ReciterMonogram(bar.reciter, Monogram)
                    bar.incoming?.let { IncomingRing(it.fraction) }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenPlaying,
                        )
                        .semantics { contentDescription = goToPlayingLabel }
                        .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    if (arabic) {
                        Text(
                            surahName,
                            fontFamily = mushafFamily(),
                            fontSize = 17.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            surahName,
                            style = TaqwaText.rowLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        stringResource(Res.string.quran_ayah_n, format.localizedDigits(bar.ayah)),
                        style = TaqwaText.caption.copy(fontSize = 12.sp),
                        // The one place buffering shows: the ayah caption goes quiet while the
                        // container is being opened, rather than a spinner appearing and leaving.
                        color = if (bar.buffering) colors.textTertiary else colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            TransportButton(
                description = stringResource(Res.string.recitation_a11y_previous),
                onClick = onPrevious,
                longPressDescription = stringResource(Res.string.recitation_a11y_previous_ayah),
                onLongClick = onPreviousAyah,
            ) { tint -> Canvas(Modifier.size(22.dp)) { drawSkip(tint, forward = !forward) } }
            PlayPauseDisc(playing = bar.playing, onClick = onToggle)
            TransportButton(
                description = stringResource(Res.string.recitation_a11y_next),
                onClick = onNext,
                longPressDescription = stringResource(Res.string.recitation_a11y_next_ayah),
                onLongClick = onNextAyah,
            ) { tint -> Canvas(Modifier.size(22.dp)) { drawSkip(tint, forward = forward) } }
            TransportButton(
                description = stringResource(Res.string.recitation_a11y_close),
                onClick = onDismiss,
                size = CloseTarget,
            ) { tint -> Canvas(Modifier.size(16.dp)) { drawClose(tint) } }
        }
    }
}

/**
 * "Next: An-Nisa · 38 %", or "Ash-Shatri · 38 %" for a voice arriving for the surah playing: one
 * line in the caption size with the name in the accent, over a hairline of its own, [IncomingStripHeight]
 * tall. The percentage moves; the ring on the monogram moves with it.
 */
@Composable
private fun IncomingStrip(name: String, nextSurah: Boolean, fraction: Float) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val percent = stringResource(Res.string.recitation_percent, format.localizedDigits((fraction * 100).toInt().coerceIn(0, 100)))
    val template = stringResource(if (nextSurah) Res.string.recitation_arriving_next else Res.string.recitation_arriving_voice, name, percent)
    // The name in the accent, the rest secondary: built by splitting the resolved sentence on
    // the name, so the words around it stay whatever the language put there.
    val at = template.indexOf(name)
    val text = buildAnnotatedString {
        if (at < 0 || name.isEmpty()) {
            append(template)
        } else {
            append(template.substring(0, at))
            withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) { append(name) }
            append(template.substring(at + name.length))
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .contentWidth()
                .height(IncomingStripHeight - 1.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text,
                style = TaqwaText.caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
    }
}

/** One of the two clocks: small, secondary, and in tabular figures so it does not breathe. */
@Composable
private fun Clock(text: String) {
    Text(
        text,
        style = TaqwaText.caption.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"),
        color = LocalTaqwaColors.current.textSecondary,
        maxLines = 1,
    )
}

/**
 * The chosen voice's copy of this surah arriving, drawn around the voice being heard (spec
 * §14.3): the header button's own 2 dp ring, at the monogram's size. It says "something is on
 * its way" without a word, and goes when the voice changes.
 */
@Composable
private fun IncomingRing(fraction: Float) {
    val colors = LocalTaqwaColors.current
    val eased by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "incomingVoice")
    Canvas(Modifier.size(MonogramBox)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = colors.hairline,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = colors.accent,
            startAngle = -90f,
            sweepAngle = 360f * eased,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** The 40 dp accent disc at the middle of the transport (spec §5.3, §14.2), with 48 dp of finger
 * around it. Playing draws two bars; stopped, the triangle. */
@Composable
private fun PlayPauseDisc(playing: Boolean, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val label = stringResource(if (playing) Res.string.recitation_a11y_pause else Res.string.recitation_play)
    Box(
        Modifier
            .size(Target)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(PlayDisc).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(17.dp)) {
                if (playing) drawPause(colors.surface) else drawPlayTriangle(colors.surface)
            }
        }
    }
}

/**
 * A transport button. With [onLongClick] it is two buttons in one — the tap and the hold — and a
 * screen reader gets the hold as a custom action under [longPressDescription], since it cannot
 * long-press. The hold answers with the platform's long-press haptic, the one cue that the
 * finger has crossed from one move to the other.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransportButton(
    description: String,
    onClick: () -> Unit,
    size: Dp = Target,
    longPressDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (Color) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val press = if (onLongClick == null) {
        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
        )
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(press)
            .semantics {
                contentDescription = description
                if (onLongClick != null && longPressDescription != null) {
                    customActions = listOf(CustomAccessibilityAction(longPressDescription) { onLongClick(); true })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(colors.textSecondary)
    }
}

/**
 * The bar with its own entrance (spec: it moves like the ayah action row does — nothing in this
 * app snaps into place). Slides up from under the bottom edge and back down out of it.
 */
@Composable
fun PlayerBarHost(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(BAR_MILLIS)) { it } + fadeIn(tween(BAR_MILLIS)),
        exit = slideOutVertically(tween(BAR_MILLIS)) { it } + fadeOut(tween(BAR_MILLIS / 2)),
        modifier = modifier,
    ) { content() }
}

/**
 * "Back to ayah 153" (spec §5.3): a dark pill above the bar, shown when the voice has moved on to
 * an ayah more than a screen from where the reader is. A tap goes there and re-arms following.
 */
@Composable
fun BackToAyahPill(ayah: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    Box(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(colors.textPrimary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.recitation_back_to_ayah, format.localizedDigits(ayah)),
            style = TaqwaText.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            color = colors.background,
            maxLines = 1,
        )
    }
}

private const val BAR_MILLIS = 240
