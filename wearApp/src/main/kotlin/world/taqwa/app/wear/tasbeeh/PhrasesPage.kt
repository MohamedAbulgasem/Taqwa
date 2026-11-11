package world.taqwa.app.wear.tasbeeh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text
import kotlin.math.roundToInt
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.words
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.app.LocalRoundScreen
import world.taqwa.app.wear.app.LocalWatchUnits
import world.taqwa.app.wear.app.WatchColors
import world.taqwa.app.wear.app.WatchFonts
import world.taqwa.app.wear.app.WatchUnits

/**
 * The phrase list (`gTasbeehList`): its title, then a row for each phrase, the built-ins first and
 * his own after them ([PhraseList.of]), the one being counted outlined in the accent, then "Reset"
 * (question 2), which asks the phone's own question before it starts the phrase again. The bezel
 * scrolls the list. Choosing a row, or resetting, returns to the counter ([onChoose], [onReset]).
 *
 * For a screen reader: the title is a heading, each row is one node, its name, then ", " and its
 * total, and whether it is selected; "Reset" is the list's last item, so a walk down reaches it.
 */
@Composable
internal fun PhrasesPage(feed: WatchFeed, ui: TasbeehUi, phrases: List<TasbeehPreset>, onChoose: (String) -> Unit, onReset: () -> Unit) {
    val words = feed.words()
    val rows = phraseRows(feed, phrases, selected = ui.preset.id)
    val units = LocalWatchUnits.current
    val state = rememberTransformingLazyColumnState()
    var asking by remember { mutableStateOf(false) }

    TransformingLazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = units.x(BOTTOM_ROOM)),
        verticalArrangement = Arrangement.spacedBy(units.x(ROW_GAP), Alignment.Top),
    ) {
        item(key = "title") { TitleItem(words.tasbeeh) }
        items(rows.size, key = { "phrase:${rows[it].id}" }) { i ->
            val row = rows[i]
            RowItem(
                name = row.name,
                target = row.target,
                nameColor = if (row.selected) WatchColors.accent else WatchColors.ink,
                // His own in the system font, as the phone writes his phrases; SemiBold, the weight
                // the list's 700 is drawn in.
                family = if (row.custom) FontFamily.Default else WatchFonts.manrope,
                weight = WatchFonts.weight(700),
                outlined = row.selected,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = null) { onChoose(row.id) }
                    .clearAndSetSemantics {
                        contentDescription = row.spoken
                        selected = row.selected
                    },
            )
        }
        item(key = "reset") {
            RowItem(
                name = words.reset,
                target = null,
                nameColor = WatchColors.mist,
                family = WatchFonts.manrope,
                weight = WatchFonts.weight(700),
                outlined = false,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = null) { asking = true }
                    .clearAndSetSemantics {
                        contentDescription = words.reset
                        role = Role.Button
                    },
            )
        }
    }

    // The phone's own question; the confirm is said as "Reset", in the phone's words. The feed has
    // no word for the way out, so the dismiss keeps the library's own icon and its description.
    AlertDialog(
        visible = asking,
        onDismissRequest = { asking = false },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    asking = false
                    onReset()
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = WatchColors.accent, contentColor = WatchColors.black),
            ) {
                Icon(CHECK, contentDescription = words.reset)
            }
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(
                onClick = { asking = false },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = WatchColors.surface, contentColor = WatchColors.ink),
            )
        },
        title = {
            Text(
                words.resetConfirm,
                color = WatchColors.ink,
                fontFamily = WatchFonts.manrope,
                fontWeight = WatchFonts.weight(600),
                textAlign = TextAlign.Center,
            )
        },
    )
}

/** The list's title at y 34, centred in the room above the first row (which begins at 50). */
@Composable
private fun TitleItem(title: String) {
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val unit = units.dp * density.density
    val round = LocalRoundScreen.current
    val line = TasbeehType.title
    val size = line.range(density.fontScale).endInclusive
    val width = WatchUnits.chordUnits(TITLE_Y - size * ASCENT, round)
    Layout(
        content = {
            ListText(
                title,
                line,
                TextStyle(color = WatchColors.ink, fontFamily = WatchFonts.manrope, fontWeight = WatchFonts.weight(800), textAlign = TextAlign.Center),
            )
        },
        modifier = Modifier.fillMaxWidth().height(units.x(FIRST_ROW_TOP - ROW_GAP)).semantics { heading() },
    ) { measurables, constraints ->
        val text = measurables.single().measure(Constraints(maxWidth = (width * unit).roundToInt().coerceAtMost(constraints.maxWidth)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            text.place((constraints.maxWidth - text.width) / 2, (TITLE_Y * unit).roundToInt() - text[FirstBaseline])
        }
    }
}

/**
 * A row (`gTasbeehList`): a 144-unit pill on the surface colour, its [name] from its start and its
 * [target] at its end (or the name alone, centred, when there is none), outlined in the accent when
 * [outlined], mirrored for a right-to-left phone. It is 25 units tall at the drawing's size, and
 * grows with its name as drawn ([TasbeehType.rowHeight]); its lines sit where the drawing has them,
 * 16.4 of its 25 units down. A name with too little room shrinks to its line's size and is then cut
 * with an ellipsis; the target always keeps its line. [modifier] is the row's click and semantics.
 */
@Composable
private fun RowItem(
    name: String,
    target: String?,
    nameColor: Color,
    family: FontFamily,
    weight: FontWeight,
    outlined: Boolean,
    modifier: Modifier,
) {
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val unit = units.dp * density.density
    var drawn by remember(density.fontScale) { mutableFloatStateOf(TasbeehType.rowName.range(density.fontScale).endInclusive) }
    val height = TasbeehType.rowHeight(drawn)
    val shape = RoundedCornerShape(percent = 50)
    Layout(
        content = {
            ListText(
                name,
                TasbeehType.rowName,
                TextStyle(color = nameColor, fontFamily = family, fontWeight = weight),
                drawn = { size -> if (size != drawn) drawn = size },
            )
            if (target != null) {
                ListText(
                    target,
                    TasbeehType.rowTarget,
                    TextStyle(color = WatchColors.mist, fontFamily = WatchFonts.manrope, fontWeight = WatchFonts.weight(600), fontFeatureSettings = "tnum"),
                )
            }
        },
        modifier = Modifier
            .width(units.x(ROW_WIDTH))
            .height(units.x(height))
            .clip(shape)
            .background(WatchColors.surface)
            .then(if (outlined) Modifier.border(units.x(OUTLINE), WatchColors.accent, shape) else Modifier)
            .then(modifier),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val inset = (ROW_INSET * unit).roundToInt()
        val baseline = (height * BASELINE_FRACTION * unit).roundToInt()
        val targetText = measurables.getOrNull(1)?.measure(Constraints())
        val room = width - 2 * inset - (targetText?.let { it.width + (NAME_GAP * unit).roundToInt() } ?: 0)
        val nameText = measurables[0].measure(Constraints(maxWidth = room.coerceAtLeast(0)))
        layout(width, constraints.maxHeight) {
            if (targetText == null) {
                nameText.place((width - nameText.width) / 2, baseline - nameText[FirstBaseline])
            } else {
                nameText.placeRelative(inset, baseline - nameText[FirstBaseline])
                targetText.placeRelative(width - inset - targetText.width, baseline - targetText[FirstBaseline])
            }
        }
    }
}

/**
 * A line of the list: one line in [style], sized by its [line]'s range at the wearer's font size
 * (`TextAutoSize`, as `LineText` sizes a line), cut with an ellipsis. [drawn] hears the size it
 * was drawn at, in units, which a row's height follows.
 */
@Composable
private fun ListText(text: String, line: Line, style: TextStyle, drawn: ((Float) -> Unit)? = null) {
    val units = LocalWatchUnits.current
    val density = LocalDensity.current
    val sizes = line.range(density.fontScale)
    BasicText(
        text = text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = units.sp(sizes.start, density),
            maxFontSize = units.sp(sizes.endInclusive, density),
            stepSize = units.sp(STEP, density),
        ),
        onTextLayout = drawn?.let { hear ->
            { result: TextLayoutResult ->
                val size = result.layoutInput.style.fontSize
                if (size.isSp) hear(with(density) { size.toDp() }.value / units.dp)
            }
        },
    )
}

/** Material's check, the confirm's glyph, in its 24-unit box. */
private val CHECK: ImageVector = ImageVector.Builder(name = "check", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(
        pathData = PathData {
            moveTo(9f, 16.17f)
            lineTo(4.83f, 12f)
            lineTo(3.41f, 13.41f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineTo(19.59f, 5.59f)
            close()
        },
        fill = SolidColor(Color.Black),
    )
    .build()

/** The list in the drawing's units: the title's baseline, the first row's top, the rows' width, inset and gap, the gap before a target, the outline and the lines. */
private const val TITLE_Y = 34f
private const val FIRST_ROW_TOP = 50f
private const val ROW_WIDTH = 144f
private const val ROW_INSET = 12f
private const val ROW_GAP = 4f
private const val NAME_GAP = 6f
private const val OUTLINE = 1.3f
private const val BASELINE_FRACTION = 16.4f / 25f

/** Room below the last row, so that it can be scrolled up clear of the round screen's edge. */
private const val BOTTOM_ROOM = 60f

private const val ASCENT = 0.8f
private const val STEP = 0.5f
