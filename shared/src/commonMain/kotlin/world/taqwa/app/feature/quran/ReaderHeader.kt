package world.taqwa.app.feature.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.Equaliser
import world.taqwa.app.design.components.drawBook
import world.taqwa.app.design.components.drawSpeaker
import world.taqwa.app.design.contentWidth
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.design.quran
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_reader_aa
import world.taqwa.app.resources.quran_reader_mushaf
import world.taqwa.app.resources.recitation_a11y_downloading
import world.taqwa.app.resources.recitation_a11y_pause
import world.taqwa.app.resources.recitation_a11y_recitation
import world.taqwa.app.resources.recitation_play
import world.taqwa.app.feature.recitation.HeaderState

/** The 36 dp round icon buttons' size (spec §2.2). */
private val TouchTargetSize = 48.dp
private val IconButtonSize = 36.dp

/**
 * The reader's shared header (spec §2.2): back chevron, title and caption, then the mode-toggle
 * book button and the "Aa" settings button. Used by both [ReaderScreen] (translation mode, this
 * task) and the Mushaf screen (task 8) — [title] and [caption] are already resolved by the
 * caller, since translation mode names the surah while Mushaf mode names the current page's first
 * line's surah, and only the caller knows which.
 *
 * Under an Arabic UI the title is drawn in [mushafFamily] at 20 sp, matching the surah name
 * everywhere else in the app (spec §5.1); otherwise it is [TaqwaText.rowLabel] at extra-bold
 * weight. Callers pick the string to match — [Surah.nameArabic] under Arabic, [Surah.nameLatin]
 * otherwise — since only they know which surah is showing.
 */
@Composable
fun ReaderHeader(
    title: String,
    caption: String,
    mushafSelected: Boolean,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenSheet: () -> Unit,
    /** What recitation is doing for the surah on screen (spec 3a §5.1). */
    recitation: HeaderState = HeaderState.Idle,
    onRecitation: () -> Unit = {},
) {
    val colors = LocalTaqwaColors.current
    val arabic = isRtlLocale()
    Row(
        Modifier
            // Capped with the cards below it (spec: one content-width rule): on a landscape
            // screen an uncapped header would put the back chevron and the "Aa" button a whole
            // hand apart, with the surah name stranded in between.
            .contentWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BackChevron(onBack)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            if (arabic) {
                Text(title, style = TaqwaText.quran(20).copy(fontFamily = mushafFamily()), color = colors.textPrimary, maxLines = 1)
            } else {
                Text(
                    title,
                    style = TaqwaText.rowLabel.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
            }
            Text(caption, style = TaqwaText.caption.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeaderIconButton(
                selected = mushafSelected,
                description = stringResource(Res.string.quran_reader_mushaf),
                onClick = onToggleMode,
            ) { tint -> Canvas(Modifier.size(18.dp)) { drawBook(tint) } }
            HeaderIconButton(
                selected = false,
                description = stringResource(Res.string.quran_reader_aa),
                onClick = onOpenSheet,
            ) { tint ->
                Text(
                    "Aa",
                    style = TaqwaText.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                    color = tint,
                )
            }
            RecitationHeaderButton(recitation, onRecitation)
        }
    }
}

/**
 * The third header button (spec 3a §5.1, §12.7): a speaker at rest, the same speaker inside a
 * 2 dp accent progress ring while the surah is arriving, the three-bar equaliser while the voice
 * is going, and an accent-tinted speaker when it is paused.
 *
 * Four states on one 36 dp disc, and only one of them moves. That is the whole design brief for
 * this button: recitation has to be reachable from every Quran screen without ever being the
 * loudest thing on it, so the button says what it is doing in tint and in a single glyph swap,
 * and nothing else on the page animates at all.
 */
@Composable
private fun RecitationHeaderButton(state: HeaderState, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    // What the button says it will do, not what it is showing: a screen reader announcing
    // "Recitation" over a playing surah would leave the one thing a tap does unsaid.
    val description = stringResource(
        when (state) {
            HeaderState.Idle -> Res.string.recitation_a11y_recitation
            is HeaderState.Downloading -> Res.string.recitation_a11y_downloading
            HeaderState.Playing -> Res.string.recitation_a11y_pause
            HeaderState.Paused -> Res.string.recitation_play
        },
    )
    val live = state is HeaderState.Playing || state is HeaderState.Paused
    // Asked of the resolved strings, not of LocalLayoutDirection: the Mushaf forces its own page
    // to RTL whatever the interface language is, and the glyph follows the interface.
    val mirrored = isRtlLocale()
    HeaderIconButton(
        selected = false,
        description = description,
        onClick = onClick,
        ring = (state as? HeaderState.Downloading)?.fraction,
        tint = if (live) colors.accent else colors.textSecondary,
    ) { tint ->
        if (state is HeaderState.Playing) {
            Equaliser(tint, size = 17.dp)
        } else {
            Canvas(Modifier.size(18.dp)) { drawSpeaker(tint, pointsForward = !mirrored) }
        }
    }
}

/** One 36 dp round header button (spec §2.2): plain for "Aa", accent-haloed when [selected] (the
 * book button in Mushaf mode). No ripple — the halo itself is the selected state's feedback.
 *
 * [ring] draws a 2 dp accent arc around the disc, from the top, clockwise: a download in flight
 * (spec 3a §5.4), so that a reader who dismissed the download sheet and went on reading can still
 * see the surah arriving. It is an arc rather than a filling disc because the glyph inside has to
 * stay legible while it runs. */
@Composable
private fun HeaderIconButton(
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    ring: Float? = null,
    tint: Color? = null,
    content: @Composable (tint: Color) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val resolvedTint = tint ?: if (selected) colors.accent else colors.textSecondary
    // The finger gets 44 dp (spec §92); the eye gets the 36 dp disc drawn inside it.
    Box(
        Modifier
            .size(TouchTargetSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (ring != null) {
            Canvas(Modifier.size(IconButtonSize)) {
                val stroke = 2.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = colors.hairline,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = colors.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * ring.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier
                .size(IconButtonSize)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(colors.accent.copy(alpha = 0.18f), CircleShape) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content(resolvedTint)
        }
    }
}
