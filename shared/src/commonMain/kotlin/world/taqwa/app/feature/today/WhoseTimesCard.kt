package world.taqwa.app.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.timetable_calculated
import world.taqwa.app.resources.timetable_cautious
import world.taqwa.app.resources.today_about_a11y
import world.taqwa.app.resources.today_about_these_times
import world.taqwa.app.resources.today_times_by
import world.taqwa.app.resources.today_whose_a11y
import world.taqwa.app.resources.today_whose_calculated_body
import world.taqwa.app.resources.today_whose_cautious_body
import world.taqwa.app.resources.today_whose_checked_body
import world.taqwa.app.resources.today_whose_checked_title
import world.taqwa.app.resources.today_whose_unchecked_body
import world.taqwa.app.resources.today_whose_unchecked_title
import kotlin.math.max

/**
 * Whose times the Prayer screen shows (spec §2.1): the timetable the day follows, by its short
 * name's key, and how sure Taqwa is of it at this place. The effective entry's, so a timetable the
 * user chose is the one named.
 */
data class WhoseTimes(val shortNameKey: String, val entryClass: EntryClass) {

    /** Cautious times: the screen adds the "Cautious times ›" line and may show the cautious card. */
    val cautious: Boolean get() = entryClass == EntryClass.C

    companion object {
        fun of(day: EngineDay): WhoseTimes = WhoseTimes(day.effectiveEntry.shortNameKey, day.effective.entryClass)
    }
}

/**
 * A name inside a sentence of the other direction stays whole (spec §2.4): "Umm al-Qura" in an
 * Arabic sentence, «أم القرى» in an English one.
 */
internal fun isolated(text: String): String = "\u2068$text\u2069"

/** The card's title: "Diyanet timetable", "Cautious times", "ISNA method, not yet fully checked", "Calculated by Taqwa". */
@Composable
private fun whoseTitle(whose: WhoseTimes): String {
    val name = isolated(authorityShortName(whose.shortNameKey))
    return when (whose.entryClass) {
        EntryClass.A, EntryClass.B -> stringResource(Res.string.today_whose_checked_title, name)
        EntryClass.C -> stringResource(Res.string.timetable_cautious)
        EntryClass.D_AUTHORITY -> stringResource(Res.string.today_whose_unchecked_title, name)
        EntryClass.D_NONE -> stringResource(Res.string.timetable_calculated)
    }
}

/** The card's one sentence, naming [place] where the class has something to say about it. */
@Composable
private fun whoseSentence(whose: WhoseTimes, place: String): String {
    val name = isolated(authorityShortName(whose.shortNameKey))
    return when (whose.entryClass) {
        EntryClass.A, EntryClass.B -> stringResource(Res.string.today_whose_checked_body, name, isolated(place))
        EntryClass.C -> stringResource(Res.string.today_whose_cautious_body)
        EntryClass.D_AUTHORITY -> stringResource(Res.string.today_whose_unchecked_body, name, isolated(place))
        EntryClass.D_NONE -> stringResource(Res.string.today_whose_calculated_body)
    }
}

/** What a screen reader calls the times: "Times by Diyanet", "Cautious times", "Calculated by Taqwa". */
@Composable
private fun whoseSpoken(whose: WhoseTimes): String = when (whose.entryClass) {
    EntryClass.C -> stringResource(Res.string.timetable_cautious)
    EntryClass.D_NONE -> stringResource(Res.string.timetable_calculated)
    else -> stringResource(Res.string.today_times_by, isolated(authorityShortName(whose.shortNameKey)))
}

/** The ⓘ's one label (spec §2.4): "Times by Diyanet, İstanbul. Opens whose times these are". */
@Composable
internal fun whoseLabel(whose: WhoseTimes, place: String): String =
    stringResource(Res.string.today_whose_a11y, whoseSpoken(whose), isolated(place))

/**
 * The ⓘ at the end of the date: a 1 em circled i, drawn rather than typed so it has the same
 * weight in every script, tertiary until its card is open.
 */
@Composable
internal fun InfoGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = 2f * u
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(tint, radius = 9f * u, center = c, style = Stroke(width = stroke))
        drawLine(tint, Offset(c.x, c.y - u), Offset(c.x, c.y + 5f * u), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(c.x, c.y - 4.5f * u), Offset(c.x, c.y - 4f * u), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * The small card the ⓘ opens, anchored under the city-and-date block (mockup §1): whose times
 * these are, one sentence, and "About these times ›". The next tap anywhere closes it, the link
 * included, which also opens About.
 *
 * [width] is the block's; [caretX] is where the ⓘ's centre falls, from the block's left edge, so
 * the caret points at the glyph in either direction.
 */
@Composable
internal fun WhoseTimesPopup(
    whose: WhoseTimes,
    place: String,
    width: Dp,
    caretX: Float?,
    onOpenAboutTimes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(gap) { BelowTheBlock(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        WhoseTimesCardContent(whose, place, caretX, onOpenAboutTimes, onDismiss, Modifier.width(width))
    }
}

/** The ⓘ's card itself, as [WhoseTimesPopup] floats it: its caret, then the card. */
@Composable
internal fun WhoseTimesCardContent(
    whose: WhoseTimes,
    place: String,
    caretX: Float?,
    onOpenAboutTimes: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTaqwaColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(modifier) {
        Column(
            Modifier
                .padding(top = CaretHeight - 1.dp)
                .fillMaxWidth()
                .shadow(
                    8.dp,
                    shape,
                    ambientColor = colors.textPrimary.copy(alpha = 0.2f),
                    spotColor = colors.textPrimary.copy(alpha = 0.3f),
                )
                .background(colors.surface, shape)
                .border(1.dp, colors.hairline, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(start = 16.dp, end = 16.dp, top = 13.dp),
        ) {
            Text(
                whoseTitle(whose),
                style = TaqwaText.caption.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                whoseSentence(whose, place),
                style = TaqwaText.caption.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = colors.textSecondary,
            )
            AboutLink {
                onDismiss()
                onOpenAboutTimes()
            }
        }
        if (caretX != null) Caret(caretX, colors.surface, colors.hairline)
    }
}

/** "About these times ›" in the accent, its own 44 dp target inside the card. */
@Composable
private fun AboutLink(onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val pointsForward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.today_about_these_times),
            style = TaqwaText.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
            color = colors.accent,
        )
        Spacer(Modifier.width(3.dp))
        // Drawn, not typed: a typed › is not mirrored under Arabic on Android (spec §2.4).
        Canvas(Modifier.size(11.dp)) { drawChevron(colors.accent, pointsForward) }
    }
}

private val CaretHeight = 7.dp

/** The card's pointer: a small triangle over its top edge, hiding the border beneath it. */
@Composable
private fun Caret(x: Float, fill: Color, edge: Color) {
    Canvas(Modifier.fillMaxWidth().height(CaretHeight)) {
        val half = CaretHeight.toPx()
        val cx = x.coerceIn(half * 3, size.width - half * 3)
        val base = size.height
        val path = Path().apply {
            moveTo(cx - half, base)
            lineTo(cx, 0f)
            lineTo(cx + half, base)
            close()
        }
        drawPath(path, fill)
        val stroke = 1.dp.toPx()
        drawLine(edge, Offset(cx - half, base), Offset(cx, 0f), strokeWidth = stroke)
        drawLine(edge, Offset(cx, 0f), Offset(cx + half, base), strokeWidth = stroke)
    }
}

/** Under the anchor (the city-and-date block), aligned with its start edge, kept on screen. */
private class BelowTheBlock(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        return IntOffset(x.coerceIn(0, max(0, windowSize.width - popupContentSize.width)), anchorBounds.bottom + gap)
    }
}

/**
 * One quiet tertiary line under the prayer list that opens About these times: "Cautious times ›"
 * where mosques differ (spec §2.1, decision §10.1), the polar sentence on a polar day. Its target
 * is 48 dp tall however short the text; [label] is what a screen reader says instead of the text.
 */
@Composable
internal fun TertiaryLine(
    text: String,
    label: String,
    onClick: () -> Unit,
    horizontalPadding: Dp,
    onClickLabel: String? = null,
) {
    val colors = LocalTaqwaColors.current
    val pointsForward = LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            // The label replaces the text's own reading, so the line is spoken once.
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f, fill = false),
            style = TaqwaText.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
            color = colors.textTertiary,
        )
        Spacer(Modifier.width(4.dp))
        Canvas(Modifier.size(12.dp)) { drawChevron(colors.textTertiary, pointsForward) }
    }
}

/** The source line's label (spec §2.4): "Cautious times, Toronto. Opens About these times". */
@Composable
internal fun aboutLabel(whose: WhoseTimes, place: String): String =
    stringResource(Res.string.today_about_a11y, whoseSpoken(whose), isolated(place))
