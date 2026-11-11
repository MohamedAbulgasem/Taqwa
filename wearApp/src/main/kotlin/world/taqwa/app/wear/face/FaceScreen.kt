package world.taqwa.app.wear.face

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import world.taqwa.app.watch.WatchFaceWords
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.R
import world.taqwa.app.wear.app.WatchColors
import world.taqwa.app.wear.app.WatchFonts

/** What the face screen says under its heading, and the button's words where a tap can help (else null). */
internal data class FaceView(val line: String, val button: String?)

/**
 * The face screen's line and button for [state] (null while the first answer is awaited):
 * - not on the watch, or in the picker and not chosen: what the face is, and "Add the Taqwa face";
 * - the watch's face: "Taqwa is your watch face.", no button;
 * - the one activation used: "Touch and hold your watch face, then choose Taqwa.", no button, so a
 *   stale phone row or `taqwa://face` by hand never meets a button that cannot work;
 * - a passing failure: "Couldn’t add the face. Try again later.", with the button to try again;
 * - no Watch Face Push, or no face in this build: what the face is, no button.
 */
internal fun faceView(state: FaceState?, words: WatchFaceWords): FaceView = when (state) {
    FaceState.NOT_INSTALLED, FaceState.INSTALLED -> FaceView(words.about, words.add)
    FaceState.ACTIVE -> FaceView(words.active, null)
    FaceState.CHOOSE_IT_YOURSELF -> FaceView(words.chooseIt, null)
    FaceState.FAILED -> FaceView(words.failed, words.add)
    FaceState.UNSUPPORTED, FaceState.NOT_BUNDLED, null -> FaceView(words.about, null)
}

/**
 * The face screen's words: the feed's, in the phone's language (the Lead's call 3, plan 2's call 6),
 * and the watch's own `face_*` for any the feed left blank, which is every one before a feed.
 */
internal fun faceWords(feed: WatchFeed?, own: WatchFaceWords): WatchFaceWords {
    val sent = feed?.ui?.face ?: return own
    fun String.or(other: String) = ifBlank { other }
    return WatchFaceWords(
        add = sent.add.or(own.add),
        about = sent.about.or(own.about),
        active = sent.active.or(own.active),
        chooseIt = sent.chooseIt.or(own.chooseIt),
        failed = sent.failed.or(own.failed),
    )
}

/** The watch's own five face phrases, in the watch's language, for a watch that has no feed yet. */
@Composable
internal fun ownFaceWords(): WatchFaceWords = WatchFaceWords(
    add = stringResource(R.string.face_add),
    about = stringResource(R.string.face_about),
    active = stringResource(R.string.face_active),
    chooseIt = stringResource(R.string.face_choose_it),
    failed = stringResource(R.string.face_failed),
)

/**
 * The face screen (spec 3.1): the app's name as its heading, the line [faceView] gives, and under it
 * the button, an amber outline, while a tap can help. The page scrolls, so a large font keeps every
 * word. For a screen reader the heading is a heading, the line a polite live region (so the end of
 * a tap is said as it comes), and the button says its words.
 */
@Composable
internal fun FaceScreen(title: String, view: FaceView, busy: Boolean, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = TextStyle(fontFamily = WatchFonts.manrope, fontWeight = WatchFonts.weight(800), fontSize = 16.sp, color = WatchColors.ink),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = view.line,
                style = TextStyle(fontFamily = WatchFonts.manrope, fontWeight = WatchFonts.weight(600), fontSize = 13.sp, color = WatchColors.mist),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            view.button?.let { words ->
                Spacer(Modifier.height(14.dp))
                FaceButton(words, enabled = !busy, onClick = onAdd)
            }
        }
    }
}

/** Google's smallest touch target on a watch, 48 by 48 dp (Wear app quality, item WO-V2). */
internal val FACE_TOUCH_MIN = 48.dp

/**
 * The amber pill the face screen's button and Today's chip are drawn as: an outline, the words in
 * the accent. The pill is about 36 dp tall at the default font size, so a touch target lies over
 * it ([FaceButtonMeasure]): the pill grown evenly to at least [FACE_TOUCH_MIN] each way, into the
 * room both callers leave around it. That is what a tap lands on and what a screen reader finds, as
 * "<words>, Button". The button keeps the pill's own size, so nothing around it moves and nothing
 * drawn changes. A press shows on the pill, as it did when the pill was the target.
 */
@Composable
internal fun FaceButton(words: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val presses = remember { MutableInteractionSource() }
    Layout(
        modifier = modifier,
        content = {
            // The pill, drawn as it always was. A screen reader finds its words on the target.
            Box(
                modifier = Modifier
                    .border(1.dp, if (enabled) WatchColors.accent else WatchColors.slate, shape)
                    .indication(presses, LocalIndication.current)
                    .clearAndSetSemantics { }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = words,
                    style = TextStyle(fontFamily = WatchFonts.manrope, fontWeight = WatchFonts.weight(700), fontSize = 13.sp, color = if (enabled) WatchColors.accent else WatchColors.slate),
                    textAlign = TextAlign.Center,
                )
            }
            // The touch target over it, which draws nothing.
            Box(
                Modifier
                    .clickable(interactionSource = presses, indication = null, enabled = enabled, onClick = onClick)
                    .clearAndSetSemantics {
                        contentDescription = words
                        role = Role.Button
                    },
            )
        },
        measurePolicy = FaceButtonMeasure,
    )
}

/**
 * [FaceButton]'s layout, over its two children, the pill and then the touch target. The pill is
 * measured as the caller asks and placed where the button is. The target is the pill grown evenly
 * to at least [FACE_TOUCH_MIN] each way, centred on it to the pixel (any odd pixel below), and may
 * reach past the button's edges. The button reports the pill's size.
 */
internal val FaceButtonMeasure = MeasurePolicy { measurables, constraints ->
    val pill = measurables[0].measure(constraints)
    val min = FACE_TOUCH_MIN.roundToPx()
    val width = maxOf(pill.width, min)
    val height = maxOf(pill.height, min)
    val touch = measurables[1].measure(Constraints.fixed(width, height))
    layout(pill.width, pill.height) {
        pill.place(0, 0)
        touch.place(-((width - pill.width) / 2), -((height - pill.height) / 2))
    }
}
