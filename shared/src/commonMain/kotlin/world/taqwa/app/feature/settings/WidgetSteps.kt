package world.taqwa.app.feature.settings

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.StringResource
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.widgets_background_note
import world.taqwa.app.resources.widgets_ios_fallback
import world.taqwa.app.resources.widgets_step_android_1
import world.taqwa.app.resources.widgets_step_android_2
import world.taqwa.app.resources.widgets_step_android_3
import world.taqwa.app.resources.widgets_step_ios_hold_1
import world.taqwa.app.resources.widgets_step_ios_hold_2
import world.taqwa.app.resources.widgets_step_ios_hold_3
import world.taqwa.app.resources.widgets_step_ios_plus_1
import world.taqwa.app.resources.widgets_step_ios_plus_2
import world.taqwa.app.resources.widgets_step_ios_plus_3
import world.taqwa.app.resources.widgets_step_lock_1
import world.taqwa.app.resources.widgets_step_lock_2
import world.taqwa.app.resources.widgets_step_lock_3
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetAddPath
import world.taqwa.app.widget.WidgetOffer
import world.taqwa.app.widget.translucentOrFrostedSubtitleKey

/**
 * The small drawing beside each step: what the reader will see on the phone at that moment, so
 * the step can be followed by looking as well as by reading. Named for the moment rather than the
 * platform, because the empty-spot hold is the same gesture on both.
 */
internal enum class StepArt {
    HOLD_EMPTY_SPOT,
    WIDGETS_BUTTON,
    DRAG_INTO_PLACE,
    HOLD_APP_ICON,
    CHOOSE_SIZE,
    LANDS_ON_SCREEN,
    PLUS_BUTTON,
    SEARCH,
    HOLD_LOCK_SCREEN,
    SPACE_UNDER_CLOCK,
    ROUND_OR_WIDE,
}

/**
 * One way of adding a widget by hand, written out: [steps] in order, each with its drawing at the
 * same place in [art], and [fallback], a line for the phone on which the first step finds nothing.
 * The steps that name a widget take its name as their one argument.
 */
internal data class WidgetSteps(
    val steps: List<StringResource>,
    val fallback: StringResource?,
    val art: List<StepArt>,
) {
    init {
        require(art.size == steps.size) { "every step has its own drawing" }
    }
}

/**
 * This platform's steps for a home-screen widget. iOS 18 and later offer a widget's sizes when its
 * app's icon is held, the shortest path, but a Home Screen in some layouts shows nothing there, so
 * that path carries the Edit › Add Widget route as its fallback; the plus button of older iOS and
 * Android's Widgets sheet always work as written and need none.
 */
internal fun widgetSteps(path: WidgetAddPath): WidgetSteps = when (path) {
    WidgetAddPath.ANDROID_PIN -> WidgetSteps(
        steps = listOf(Res.string.widgets_step_android_1, Res.string.widgets_step_android_2, Res.string.widgets_step_android_3),
        fallback = null,
        art = listOf(StepArt.HOLD_EMPTY_SPOT, StepArt.WIDGETS_BUTTON, StepArt.DRAG_INTO_PLACE),
    )
    WidgetAddPath.IOS_HOLD_ICON -> WidgetSteps(
        steps = listOf(Res.string.widgets_step_ios_hold_1, Res.string.widgets_step_ios_hold_2, Res.string.widgets_step_ios_hold_3),
        fallback = Res.string.widgets_ios_fallback,
        art = listOf(StepArt.HOLD_APP_ICON, StepArt.CHOOSE_SIZE, StepArt.LANDS_ON_SCREEN),
    )
    WidgetAddPath.IOS_PLUS_BUTTON -> WidgetSteps(
        steps = listOf(Res.string.widgets_step_ios_plus_1, Res.string.widgets_step_ios_plus_2, Res.string.widgets_step_ios_plus_3),
        fallback = null,
        art = listOf(StepArt.HOLD_EMPTY_SPOT, StepArt.PLUS_BUTTON, StepArt.SEARCH),
    )
}

/**
 * The two ways a card writes its steps out. Android's are the warm by-hand panel, which says first
 * why there is no button, because its reader has just seen one do nothing or expected one; the
 * iPhone's are simply how iOS adds any widget, under a plain "How to add it".
 */
internal enum class StepsPanel { BY_HAND, HOW_TO }

/** The panel [path]'s steps are written out in; a pure function, so Android's refused path can be
 * followed to its panel in a host test. */
internal fun stepsPanel(path: WidgetAddPath): StepsPanel = when (path) {
    WidgetAddPath.ANDROID_PIN -> StepsPanel.BY_HAND
    WidgetAddPath.IOS_HOLD_ICON, WidgetAddPath.IOS_PLUS_BUTTON -> StepsPanel.HOW_TO
}

/** Where a screen reader's focus lands in steps the reader's own tap just opened: why they are
 * there, or their first step. */
internal enum class PanelFocus { WHY, FIRST_STEP }

/**
 * How a card's steps make themselves known as they open: [focus] is where a screen reader's focus
 * moves to, if anywhere, and [readOut] whether they are a polite live region, read out as they
 * appear.
 */
internal data class StepsOpening(val focus: PanelFocus?, val readOut: Boolean)

/**
 * How steps open. Under a screen reader they are made known by moving its focus into them, and
 * only when the reader's own tap opened them ([openedByThisTap]): at why they are there after the
 * launcher [refused] the button, at their first step after "Show me how". There they are never a
 * live region, which would read them out wherever the focus had gone. A refused panel that took
 * focus once became one as soon as another card's refusal or "Show me how" moved the focus away
 * (the Lead's follow-up, 31 October). Without a screen reader nothing takes focus, since moving
 * focus scrolls, and steps that replace a refused button are a polite live region instead. Steps
 * open from the start do neither: nothing was tapped.
 */
internal fun stepsOpening(refused: Boolean, screenReader: Boolean, openedByThisTap: Boolean) = StepsOpening(
    focus = when {
        !screenReader || !openedByThisTap -> null
        refused -> PanelFocus.WHY
        else -> PanelFocus.FIRST_STEP
    },
    readOut = refused && !screenReader,
)

/** The iPhone's Lock Screen widget, added from the Lock Screen's own editor on every iOS this app
 * runs on, so there is one way and no fallback. */
internal val LockScreenSteps = WidgetSteps(
    steps = listOf(Res.string.widgets_step_lock_1, Res.string.widgets_step_lock_2, Res.string.widgets_step_lock_3),
    fallback = null,
    art = listOf(StepArt.HOLD_LOCK_SCREEN, StepArt.SPACE_UNDER_CLOCK, StepArt.ROUND_OR_WIDE),
)

/**
 * The widget whose card shows the steps open, the first that needs them; null when none does.
 * The steps are the platform's, not the widget's, so a second card that needs them shows a
 * "Show me how" row instead of the same three steps again: one wall of text, not two.
 */
internal fun expandedStepsWidget(offers: List<Pair<PinnableWidget, WidgetOffer>>): PinnableWidget? =
    offers.firstOrNull { (_, offer) -> offer is WidgetOffer.Instructions }?.first

/**
 * Every card that shows its steps open without being asked: [expandedStepsWidget]'s, and each card
 * whose "Add to home screen" the launcher has [refused]. A refused card's steps are the answer to
 * the tap just made on it, so they open where the button was even when another card's are already
 * open, and steps that are open are never folded away because a card above them was refused later.
 * A refused widget that has since been placed opens nothing: its offer is no longer the steps.
 */
internal fun stepsShownOpen(
    offers: List<Pair<PinnableWidget, WidgetOffer>>,
    refused: Set<PinnableWidget>,
): Set<PinnableWidget> {
    val needSteps = offers.filter { (_, offer) -> offer is WidgetOffer.Instructions }.map { it.first }
    return needSteps.filterTo(mutableSetOf()) { it in refused || it == expandedStepsWidget(offers) }
}

/**
 * The line under the background choice. The fourth option's own subtitle says what it does on
 * this platform, which matters more while it is chosen than the general note; every other choice
 * reads the note for every widget.
 */
internal fun widgetBackgroundNote(value: WidgetBackground, isIos: Boolean): StringResource = when (value) {
    WidgetBackground.TRANSLUCENT_OR_FROSTED -> translucentOrFrostedSubtitleKey(isIos)
    else -> Res.string.widgets_background_note
}

/** Where a step's sentence takes the widget's name. */
private const val NamePlaceholder = "%1\$s"

/** What a translation puts on each side of the words it wants in bold. */
internal const val BoldMarker = "**"

/**
 * A step's sentence, or the fallback line, as it is shown: the widget's [name] in bold where the
 * sentence names it, and the words each translation marks between [BoldMarker]s in bold without
 * the marks. Both are what the reader is about to look for on the phone, the widget in the
 * system's own widget list and the buttons and the app's name on the way there, so they stand out
 * as the canvas has them. The name goes in where the placeholder is rather than being searched for
 * afterwards, so a sentence that happens to contain the name elsewhere is left as it is; the marks
 * come in pairs in every language (tools/i18n-check.py), so an odd run is the text between them.
 */
internal fun markedText(raw: String, name: String): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    raw.split(BoldMarker).forEachIndexed { run, text ->
        val marked = run % 2 == 1
        text.split(NamePlaceholder).forEachIndexed { i, part ->
            if (i > 0) withStyle(bold) { append(name) }
            if (marked) withStyle(bold) { append(part) } else append(part)
        }
    }
}
