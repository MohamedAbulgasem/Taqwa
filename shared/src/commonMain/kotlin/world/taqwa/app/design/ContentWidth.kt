package world.taqwa.app.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a column of reading content is ever allowed to grow.
 *
 * A phone in landscape is around 900 dp across. Rows, cards and hairline dividers stretched to
 * that width read as a spreadsheet rather than a page: the eye loses the line between a row's
 * label on the far left and its value on the far right, and the cards' corners stop registering
 * as a card at all. 600 dp is a little wider than the largest phone in portrait, so on a phone
 * held upright nothing in the app moves by a single pixel; it is only the wide cases — landscape,
 * tablets, a resized window — that get the cap.
 */
val ContentMaxWidth: Dp = 600.dp

/**
 * Caps this element at [max] and centres it in whatever width it was given, leaving the parent —
 * and so the page background — full-bleed behind it.
 *
 * The four steps are one idiom and none of them is redundant: `fillMaxWidth` claims the whole
 * width so the centring has something to centre inside; `wrapContentWidth` drops the minimum
 * width constraint again and aligns the child in that claimed width; `widthIn` caps what is left;
 * the second `fillMaxWidth` makes the child take all of the capped width rather than shrinking to
 * whatever its own content happens to measure.
 */
fun Modifier.contentWidth(max: Dp = ContentMaxWidth): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = max)
    .fillMaxWidth()

/**
 * How far below the safe-drawing inset every tab root puts its own title.
 *
 * The three tabs are switched between constantly, so their titles have to sit on one line: a
 * title that jumps when the bar is tapped reads as the whole page shifting. Quran and Settings
 * reach this figure as a 20 dp spacer plus the 4 dp their title carries itself; the Prayer tab
 * has no spacer and applies it directly to the block holding the city and the two dates.
 */
val TabRootTitleTop: Dp = 24.dp
