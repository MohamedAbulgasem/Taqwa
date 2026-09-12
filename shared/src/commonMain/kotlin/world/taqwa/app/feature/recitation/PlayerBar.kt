package world.taqwa.app.feature.recitation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
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
import world.taqwa.app.resources.recitation_a11y_close
import world.taqwa.app.resources.recitation_back_to_ayah
import world.taqwa.app.resources.recitation_a11y_next
import world.taqwa.app.resources.recitation_a11y_pause
import world.taqwa.app.resources.recitation_a11y_previous
import world.taqwa.app.resources.recitation_play

/** The 44 dp the finger gets, whatever the eye is given inside it (spec §92). */
private val Target = 44.dp

/** How far the bar must be dragged down before it counts as a dismissal rather than a fumble. */
private val DismissDrag = 36.dp

/**
 * The player bar (spec §5.3): a 56 dp strip on the card surface with a hairline top edge and a
 * 2 dp accent line running along that edge to say how far through the surah the voice is.
 *
 * [surahName] is resolved by the caller — Arabic under an Arabic UI, Latin otherwise, as the
 * reader header does — because only the caller has the surah's row from the database.
 *
 * It is an overlay in the tab scaffold rather than part of any screen, so it survives the walk
 * from the reader to the Mushaf to the surah list; the reader and the Mushaf keep
 * [PlayerBarHeight] of space clear at their foot so it never covers the last ayah.
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
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val arabic = isRtlLocale()
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dismissPx = with(LocalDensity.current) { DismissDrag.toPx() }
    // The line eases to each new position rather than stepping: an ayah boundary moves it by a
    // whole notch, and a bar that jumped would read as a glitch rather than as progress.
    val fraction by animateFloatAsState(bar.fraction, tween(400), label = "recitationProgress")

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
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.hairline)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(colors.accent),
            )
        }
        Row(
            Modifier
                .contentWidth()
                .height(PlayerBarHeight)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenPicker,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReciterMonogram(bar.reciter, 40.dp)
                Column(Modifier.padding(horizontal = 10.dp)) {
                    if (arabic) {
                        Text(
                            surahName,
                            fontFamily = mushafFamily(),
                            fontSize = 17.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            surahName,
                            style = TaqwaText.rowLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
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
            ) { tint -> Canvas(Modifier.size(18.dp)) { drawSkip(tint, forward = !forward) } }
            PlayPauseDisc(playing = bar.playing, onClick = onToggle)
            TransportButton(
                description = stringResource(Res.string.recitation_a11y_next),
                onClick = onNext,
            ) { tint -> Canvas(Modifier.size(18.dp)) { drawSkip(tint, forward = forward) } }
            TransportButton(
                description = stringResource(Res.string.recitation_a11y_close),
                onClick = onDismiss,
                size = 36.dp,
            ) { tint -> Canvas(Modifier.size(15.dp)) { drawClose(tint) } }
        }
    }
}

/** The 34 dp accent disc at the middle of the transport (spec §5.3), with 44 dp of finger around
 * it. Playing draws two bars; stopped, the triangle. */
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
        Box(Modifier.size(34.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(15.dp)) {
                if (playing) drawPause(colors.surface) else drawPlayTriangle(colors.surface)
            }
        }
    }
}

@Composable
private fun TransportButton(
    description: String,
    onClick: () -> Unit,
    size: Dp = Target,
    content: @Composable (Color) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
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
