package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.DarkColors
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.PreviewLockScreen
import world.taqwa.app.design.PreviewWallpaper
import world.taqwa.app.design.TaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.screenReaderRunning
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.drawChevron
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.feature.onboarding.drawMihrab
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_widget_add
import world.taqwa.app.resources.appearance_widget_background_label
import world.taqwa.app.resources.settings_widgets
import world.taqwa.app.resources.widget_background_dark
import world.taqwa.app.resources.widget_background_follow_theme
import world.taqwa.app.resources.widget_background_light
import world.taqwa.app.resources.widgets_ayah_body
import world.taqwa.app.resources.widgets_ayah_body_ios
import world.taqwa.app.resources.widgets_ayah_label
import world.taqwa.app.resources.widgets_ayah_name
import world.taqwa.app.resources.widgets_how_label
import world.taqwa.app.resources.widgets_intro
import world.taqwa.app.resources.widgets_intro_ios
import world.taqwa.app.resources.widgets_lock_body
import world.taqwa.app.resources.widgets_lock_then
import world.taqwa.app.resources.widgets_lock_label
import world.taqwa.app.resources.widgets_lock_name
import world.taqwa.app.resources.widgets_on_home
import world.taqwa.app.resources.widgets_on_home_ios
import world.taqwa.app.resources.widgets_on_lock_ios
import world.taqwa.app.resources.widgets_prayer_body
import world.taqwa.app.resources.widgets_prayer_body_ios
import world.taqwa.app.resources.widgets_prayer_label
import world.taqwa.app.resources.widgets_prayer_name
import world.taqwa.app.resources.widgets_refused_body
import world.taqwa.app.resources.widgets_refused_title
import world.taqwa.app.resources.widgets_show_how
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetContentBuilder
import world.taqwa.app.widget.WidgetCountdown
import world.taqwa.app.widget.WidgetPrayerRow
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.WidgetOffer
import world.taqwa.app.widget.WidgetPinRequester
import world.taqwa.app.widget.WidgetPlacement
import world.taqwa.app.widget.WidgetPlacementSource
import world.taqwa.app.widget.askToPin
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.isIosPlatform
import world.taqwa.app.widget.reread
import world.taqwa.app.widget.translucentOrFrostedTitleKey
import world.taqwa.app.widget.widgetAddPath
import world.taqwa.app.widget.widgetOffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Clock

/** The previews' corners inside their cards: inset 12 dp, they round less than the card's 18. */
private val PreviewCorner = 14.dp

/** The room above the hairline that opens a card's steps, folded ("Show me how") or open. */
private val HairlineRoom = 14.dp

/** The saved name under which the lock-screen card's "Show me how" is remembered as opened. */
private const val LockCard = "LOCK"

/**
 * Settings › Widgets (settings redesign spec, approved 30 Oct 2026): the background every widget
 * shares, first, because it is the setting people come back to change; then one card per widget
 * with its preview, which says what it is on this phone at a glance — a check when it is on the
 * home screen, a button where the launcher can be asked for it, and the steps by hand where it
 * cannot. On iPhone a last card shows the Lock Screen widget, with its own check when one is on the
 * Lock Screen and its steps when none is; nothing can ask for it, so it never has a button.
 */
@Composable
fun WidgetsSettingsScreen(
    widgetBackground: WidgetBackground,
    onPickWidgetBackground: (WidgetBackground) -> Unit,
    widgetPinRequester: WidgetPinRequester,
    widgetPlacementSource: WidgetPlacementSource,
    onBack: () -> Unit,
) {
    // Null until a read answers, and while it is null no card shows a check, a button or steps:
    // they appear when the real answer arrives, rather than one flashing away when it turns out
    // the widget was already there. Read again on every ON_RESUME, not just on entering
    // composition, because the whole point is that the user leaves to add the widget and comes
    // back — and ON_RESUME rather than ON_START because the launcher's sheet that confirms an "Add
    // to home screen" (Pixel Launcher's AddItemActivity) is translucent: it only pauses Taqwa, so
    // no ON_START follows it, and the button would stay under the widget just added, ready to add
    // a second. A read that cannot tell is not an answer, so it leaves placement as it was
    // (reread): still null before any answer, and after one the last answer, which an iPhone's
    // pull of Control Center (a resume, and a read that can time out) must not take away. So a
    // re-read never sets placement back to null, and the cards do not flicker. The read itself
    // hops off the main thread, since Android's answer is a binder call to the launcher and iOS's
    // can take up to two seconds.
    var placement by remember { mutableStateOf<WidgetPlacement?>(null) }
    // A binder call to the launcher, and the answer cannot change while this screen is open, so
    // it is asked once rather than on every recomposition of either preview.
    val pinnable = remember(widgetPinRequester) {
        PinnableWidget.entries.associateWith(widgetPinRequester::isSupported)
    }
    // The widgets whose "Add to home screen" the launcher has turned down while this screen is
    // open: their button did nothing, so the steps by hand take its place. A request the launcher
    // took never lands here, so on that phone the screen stays as it was. Saved, as onboarding's
    // is, so a rotation or a process death does not bring back a button that does nothing.
    var pinRefused by rememberSaveable(stateSaver = PinRefusedSaver) { mutableStateOf(emptySet<PinnableWidget>()) }
    // The cards whose steps the reader opened with "Show me how", by the widget's name or
    // [LockCard]; saved for the same reason, so a rotation does not fold them away again.
    var shownByHand by rememberSaveable(stateSaver = ShownByHandSaver) { mutableStateOf(emptySet<String>()) }
    // The card whose steps the reader's own tap just opened, by refusing its button or with "Show
    // me how". What was tapped goes as the steps open, and TalkBack then put its focus wherever
    // it chose (on the device, the prayer card's title after the ayah card's refusal), so focus
    // moves into the steps instead. Only while a screen reader runs: moving focus also scrolls
    // into view, which a sighted reader, who never had focus there, must not meet. Not saved, so
    // a rotation, which loses a screen reader's place anyway, moves nothing again; and forgotten
    // once focus has moved, so steps that go and come back later (the widget added by hand and
    // removed again, each seen on returning to Taqwa) do not take it a second time.
    var openedByTap by remember { mutableStateOf<String?>(null) }
    val screenReader = screenReaderRunning()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(widgetPlacementSource, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val shown = placement
            placement = withContext(Dispatchers.Default) { widgetPlacementSource.reread(shown) }
        }
    }

    val systemIsDark = isSystemInDarkTheme()
    // Read once per composition rather than observed live: the mirror only changes when the
    // Prayer screen refreshes (once a second while it's open), and re-reading it here on every
    // recomposition would be wasted work for previews whose only job is to react to
    // [widgetBackground] changing.
    val mirrorContent: WidgetContent? = remember {
        WidgetMirrorWriter.read(createWidgetKeyValueStore())?.let(WidgetContentBuilder::build)
    }
    // Both offers are decided here, once, so each card knows whether another card's steps are
    // already open: the steps are the platform's, not the widget's, and are written out open only
    // where [stepsShownOpen] says. Null while placement is unknown, when no card offers anything.
    val offers = placement?.let { known ->
        PinnableWidget.entries.map { widget ->
            widget to widgetOffer(
                widget, known.has(widget), pinnable[widget] == true, widgetAddPath,
                refused = widget in pinRefused,
            )
        }
    }
    val openUnasked = offers?.let { stepsShownOpen(it, pinRefused) }.orEmpty()
    val onPinRefused: (PinnableWidget) -> Unit = {
        pinRefused = pinRefused + it
        openedByTap = it.name
    }

    SettingsScaffold(stringResource(Res.string.settings_widgets), onBack) {
        SettingsNote(stringResource(if (isIosPlatform) Res.string.widgets_intro_ios else Res.string.widgets_intro))

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(Res.string.appearance_widget_background_label))
        BackgroundChoice(selected = widgetBackground, onPick = onPickWidgetBackground)
        Spacer(Modifier.height(10.dp))
        SettingsNote(stringResource(widgetBackgroundNote(widgetBackground, isIosPlatform)))

        PinnableWidget.entries.forEach { widget ->
            val name = stringResource(widget.nameKey())
            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(widget.labelKey()))
            SettingsCard {
                DrawnAsPicture(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                    when (widget) {
                        // A picture of sample names, times and a countdown, which a screen
                        // reader read out before the card's own name (review round 2): hidden,
                        // as the Lock Screen's picture is, and the card's words say what it is.
                        PinnableWidget.PRAYER -> WidgetPreview(
                            background = widgetBackground,
                            systemIsDark = systemIsDark,
                            content = mirrorContent,
                            modifier = Modifier.clearAndSetSemantics {},
                            corner = PreviewCorner,
                        )
                        PinnableWidget.AYAH -> AyahWidgetPreview(
                            background = widgetBackground,
                            systemIsDark = systemIsDark,
                            corner = PreviewCorner,
                        )
                    }
                }
                CardTitle(name, stringResource(widget.bodyKey(isIosPlatform)))
                WidgetOfferArea(
                    name = name,
                    offer = offers?.firstOrNull { it.first == widget }?.second,
                    stepsOpen = widget in openUnasked || widget.name in shownByHand,
                    refused = widget in pinRefused,
                    screenReader = screenReader,
                    openedByTap = openedByTap == widget.name,
                    onFocusMoved = { if (openedByTap == widget.name) openedByTap = null },
                    pinRequester = widgetPinRequester,
                    onRefused = onPinRefused,
                    onShowSteps = {
                        shownByHand = shownByHand + widget.name
                        openedByTap = widget.name
                    },
                )
            }
        }

        if (isIosPlatform) {
            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(Res.string.widgets_lock_label))
            LockScreenCard(
                content = mirrorContent ?: sampleWidgetContent(),
                placed = placement?.lockScreen,
                stepsOpen = LockCard in shownByHand,
                screenReader = screenReader,
                openedByTap = openedByTap == LockCard,
                onFocusMoved = { if (openedByTap == LockCard) openedByTap = null },
                onShowSteps = {
                    shownByHand = shownByHand + LockCard
                    openedByTap = LockCard
                },
            )
        }
    }
}

/**
 * The narrowest width the previews are laid out at: the width Appearance gave them at 360 dp,
 * gutter to gutter, which every language's names and times were checked to fit.
 */
private val PictureWidth = 312.dp

/**
 * [content], one preview, drawn the way a picture of a widget is: laid out at [PictureWidth] when
 * the card is narrower, at the font size it has at 100 %, and then shrunk evenly to the width it
 * is given. The previews are stand-ins whose rows and cards have fixed sizes, so set 12 dp inside
 * a card at 360 dp, or with the reader's larger font, their names ran over their times and their
 * last rows were cut; drawn as a picture, they look exactly as they did with room, only smaller.
 * The reported height is the shrunk one, so the card around it closes up beneath it. Onboarding,
 * which sets them full width, draws them as they are.
 */
@Composable
private fun DrawnAsPicture(modifier: Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Layout(content, modifier) { measurables, constraints ->
            val width = constraints.maxWidth
            val drawn = max(width, PictureWidth.roundToPx())
            val placeable = measurables.single().measure(Constraints.fixedWidth(drawn))
            val scale = width.toFloat() / drawn
            layout(width, (placeable.height * scale).roundToInt()) {
                // Shrunk from its top-left corner to exactly this width, which is the same picture
                // whichever way the screen reads, so the corner need not follow the direction.
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

private fun WidgetPlacement.has(widget: PinnableWidget): Boolean = when (widget) {
    PinnableWidget.PRAYER -> prayer
    PinnableWidget.AYAH -> ayah
}

private fun PinnableWidget.labelKey(): StringResource = when (this) {
    PinnableWidget.PRAYER -> Res.string.widgets_prayer_label
    PinnableWidget.AYAH -> Res.string.widgets_ayah_label
}

/**
 * The widget's name as its card gives it, which is also the name the steps tell the reader to look
 * for: the Android picker and the iPhone's widget gallery list each widget under it (the gallery's
 * prayer widget since the owner renamed it from "Taqwa", 31 Oct 2026). tools/i18n-check.py holds
 * each name to what the two systems list.
 */
private fun PinnableWidget.nameKey(): StringResource = when (this) {
    PinnableWidget.PRAYER -> Res.string.widgets_prayer_name
    PinnableWidget.AYAH -> Res.string.widgets_ayah_name
}

/** The line under the name, which ends with the sizes this platform's widget gallery offers. */
private fun PinnableWidget.bodyKey(isIos: Boolean): StringResource = when (this) {
    PinnableWidget.PRAYER -> if (isIos) Res.string.widgets_prayer_body_ios else Res.string.widgets_prayer_body
    PinnableWidget.AYAH -> if (isIos) Res.string.widgets_ayah_body_ios else Res.string.widgets_ayah_body
}

/**
 * The background options' labels. The fourth is labelled per platform because each platform
 * describes what it actually does; what it does is said in the note under the choice while it is
 * chosen ([widgetBackgroundNote]).
 */
@Composable
internal fun widgetBackgroundTitle(value: WidgetBackground): String = when (value) {
    WidgetBackground.FOLLOW_THEME -> stringResource(Res.string.widget_background_follow_theme)
    WidgetBackground.LIGHT -> stringResource(Res.string.widget_background_light)
    WidgetBackground.DARK -> stringResource(Res.string.widget_background_dark)
    WidgetBackground.TRANSLUCENT_OR_FROSTED ->
        stringResource(translucentOrFrostedTitleKey(isIosPlatform))
}

/** The background choice's corners: a whole pill at its 48 dp height, and a rounded panel when a
 * label wraps or the options fall back to two rows. */
private val ChoiceCorner = 24.dp

/** How far the chosen option's fill sits inside its target, as in [world.taqwa.app.design.components.TaqwaSegmented]. */
private val ChoiceInset = 3.dp

/** Each option's height at least: the touch target the settings screens keep everywhere. */
private val ChoiceMinHeight = 48.dp

/**
 * The four backgrounds as one pill of options, the look of [world.taqwa.app.design.components.TaqwaSegmented]
 * but spoken as what it is: a group of radio buttons (`selectableGroup` and [Role.RadioButton]),
 * so TalkBack and VoiceOver say which background is chosen and how many there are.
 *
 * Laid out by hand so no label is ever cut, in any of the seven languages or at a large font
 * size. Every option first gets its label on one line, the short ones sharing what is left so they
 * look even; when the row is too narrow for that, a label of two words wraps between them, centred,
 * and every option grows to the tallest; when even each label's longest word does not fit, the
 * four become two rows of two (the spec's fallback), and at the largest font sizes one column.
 */
@Composable
private fun BackgroundChoice(selected: WidgetBackground, onPick: (WidgetBackground) -> Unit) {
    val colors = LocalTaqwaColors.current
    val shape = RoundedCornerShape(ChoiceCorner)
    Layout(
        content = {
            WidgetBackground.entries.forEach { value ->
                val chosen = value == selected
                // The selectable takes the whole cell; only the drawn fill is inset, as in
                // TaqwaSegmented, so no option's target shrinks below the row's height.
                Box(
                    Modifier
                        .selectable(
                            selected = chosen,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onPick(value) },
                        )
                        .padding(ChoiceInset)
                        .clip(RoundedCornerShape(ChoiceCorner - ChoiceInset))
                        .background(if (chosen) colors.accent else colors.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        widgetBackgroundTitle(value),
                        style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (chosen) colors.background else colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        modifier = Modifier
            .padding(horizontal = SettingsGutter)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .selectableGroup(),
    ) { measurables, constraints ->
        val most = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val least = measurables.map { it.minIntrinsicWidth(Constraints.Infinity) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else most.sum()
        val columns = when {
            least.sum() <= width -> measurables.size
            least.max() <= width / 2 -> 2
            else -> 1
        }
        val widths = if (columns == measurables.size) {
            // Room for every label on one line: each keeps its line and the rest is shared so the
            // short ones come out even. Not room: each keeps at least its longest word and the
            // space goes to the labels that still want more, which then wrap between words.
            if (most.sum() <= width) shareWidth(width, floor = most, ceiling = null) else shareWidth(width, floor = least, ceiling = most)
        } else {
            // Rows of equal cells, the last cell of each taking the pixels the division leaves.
            measurables.indices.map { i ->
                val column = i % columns
                (width * (column + 1)) / columns - (width * column) / columns
            }
        }
        val rows = measurables.indices.chunked(columns)
        val minHeight = ChoiceMinHeight.roundToPx()
        val heights = rows.map { row -> max(minHeight, row.maxOf { measurables[it].maxIntrinsicHeight(widths[it]) }) }
        val placeables = rows.flatMapIndexed { r, row ->
            row.map { measurables[it].measure(Constraints.fixed(widths[it], heights[r])) }
        }
        layout(width, heights.sum()) {
            var y = 0
            var index = 0
            rows.forEachIndexed { r, row ->
                var x = 0
                row.forEach { i ->
                    // placeRelative: the first option is at the start under RTL too, as in every
                    // other row of these screens.
                    placeables[index++].placeRelative(x, y)
                    x += widths[i]
                }
                y += heights[r]
            }
        }
    }
}

/**
 * [width] split so each share is at least its [floor] and at most its [ceiling] (none when null),
 * as evenly as those allow: every share is one common width clamped to its own bounds. The common
 * width is found by halving, and the pixels its rounding leaves over go to the first shares that
 * can still take one, so the shares always add up to [width] exactly.
 */
private fun shareWidth(width: Int, floor: List<Int>, ceiling: List<Int>?): List<Int> {
    fun at(common: Int) = floor.indices.map { i ->
        val top = ceiling?.get(i) ?: Int.MAX_VALUE
        common.coerceIn(floor[i], max(floor[i], top))
    }
    var low = 0
    var high = width
    while (low < high) {
        val mid = (low + high) / 2
        if (at(mid).sum() >= width) high = mid else low = mid + 1
    }
    val below = at(low - 1).toMutableList()
    val above = at(low)
    var left = width - below.sum()
    for (i in below.indices) {
        if (left > 0 && above[i] > below[i]) {
            below[i] += 1
            left -= 1
        }
    }
    return below
}

@Composable
private fun CardTitle(name: String, body: String) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(name, style = TaqwaText.rowLabel, color = colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(body, style = TaqwaText.caption, color = colors.textSecondary)
    }
}

/**
 * The foot of one widget's card: nothing while placement is unknown ([offer] null), a check and
 * "On your home screen" when the widget is there, a button that asks the launcher for it where the
 * launcher takes such requests, and the platform's own steps where it does not — open when
 * [stepsOpen], otherwise behind a "Show me how" row, so two cards never write out the same steps
 * unasked.
 */
@Composable
internal fun WidgetOfferArea(
    /** The widget's name as the system's widget list shows it, for the steps that name it. */
    name: String,
    offer: WidgetOffer?,
    stepsOpen: Boolean,
    /** True when the steps replace a button the launcher refused. */
    refused: Boolean,
    pinRequester: WidgetPinRequester,
    onRefused: (PinnableWidget) -> Unit,
    onShowSteps: () -> Unit,
    /** Whether a screen reader is running, which decides how open steps make themselves known. */
    screenReader: Boolean = false,
    /** True when the reader's own tap has just opened these steps, by refusing the button or with
     * "Show me how"; what was tapped is gone, so a screen reader's focus moves into them. */
    openedByTap: Boolean = false,
    /** Called once a screen reader's focus has moved into these steps, so the screen forgets the tap. */
    onFocusMoved: () -> Unit = {},
) {
    when (offer) {
        null -> Spacer(Modifier.height(16.dp))
        WidgetOffer.None -> OnHomeScreen()
        // A request the launcher took: its confirmation sheet is system UI and its answer never
        // comes back, so the app does nothing further — the button simply is not there the next
        // time placement is read, on the ON_RESUME that follows the sheet (translucent, so it only
        // pauses Taqwa). A request it refused would leave the tap doing nothing, so the steps
        // replace the button.
        is WidgetOffer.Pin -> TaqwaPrimaryButton(
            text = stringResource(Res.string.appearance_widget_add),
            onClick = {
                pinRequester.askToPin(offer.widget, onTaken = {}, onRefused = { onRefused(offer.widget) })
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        )
        is WidgetOffer.Instructions -> if (stepsOpen) {
            // Decided once, as the steps appear. Another card's tap later moves the screen's
            // openedByTap on, and these steps must then neither take focus again nor start being
            // read out ([stepsOpening]); their nodes stay as they were, so nothing in them changes
            // under a screen reader that is still reading them.
            val opening = remember { stepsOpening(refused, screenReader, openedByTap) }
            when (stepsPanel(offer.path)) {
                StepsPanel.BY_HAND -> ByHandPanel(widgetSteps(offer.path), name, opening.focus, onFocusMoved, opening.readOut)
                StepsPanel.HOW_TO -> HowToPanel(widgetSteps(offer.path), name, opening.focus != null, onFocusMoved, opening.readOut)
            }
        } else {
            ShowHowRow(onShowSteps)
        }
    }
}

/** The widget is on a home screen: said plainly, in the secondary ink, with the accent check. */
@Composable
private fun OnHomeScreen() {
    PlacedStatus(stringResource(if (isIosPlatform) Res.string.widgets_on_home_ios else Res.string.widgets_on_home))
}

/** A widget is where [where] says: the accent check, then the words in the secondary ink. */
@Composable
private fun PlacedStatus(where: String) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusCheck()
        Spacer(Modifier.width(8.dp))
        Text(
            where,
            style = TaqwaText.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
    }
}

/**
 * A filled accent disc with the tick cut out of it in the background colour: a status, where
 * [world.taqwa.app.design.components.CheckMark]'s bare tick is a choice. Decorative, since the
 * words beside it say the same.
 */
@Composable
private fun StatusCheck() {
    val colors = LocalTaqwaColors.current
    Canvas(Modifier.size(18.dp)) {
        val u = size.width / 18f
        drawCircle(colors.accent, radius = 8f * u)
        val tick = Path().apply {
            moveTo(5.5f * u, 9.2f * u)
            lineTo(7.8f * u, 11.4f * u)
            lineTo(12.5f * u, 6.6f * u)
        }
        drawPath(tick, colors.background, style = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * A collapsed card's way to its steps: a whole-width row under a hairline, as a list row is. The
 * hairline keeps the [HowToPanel]'s room above it, so it never sits on the card's description
 * (Arabic descenders touched it, review round 2) and stays where it was when the steps open.
 *
 * To a screen reader it is a folded section (review round 2): besides its click it has the expand
 * action, which TalkBack reads as the row's state and offers by name. Opening it replaces the row
 * with the steps, so there is never an open state to say; focus goes to the first step instead.
 */
@Composable
private fun ShowHowRow(onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.padding(top = HairlineRoom)) {
        CardDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    expand {
                        onClick()
                        true
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.widgets_show_how),
                style = TaqwaText.caption.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            // The disclosure chevron turned to point down, at what opens beneath it; down is down
            // in both directions, so it is not mirrored.
            Canvas(Modifier.size(16.dp)) { rotate(90f) { drawChevron(colors.textTertiary, pointsForward = true) } }
        }
    }
}

/**
 * Android's steps, for a launcher that refused the request or never takes one: a warm panel inside
 * the card that says why first, because the reader has just seen a button do nothing, or expected
 * one. [focus] is where a screen reader's focus is moved to as it opens, if anywhere, after which
 * [onFocusMoved] is called. When [announce], the words that say why are read out as the panel
 * appears ([announcedWhen]); the steps below them stay nodes of their own.
 */
@Composable
private fun ByHandPanel(
    steps: WidgetSteps,
    name: String,
    focus: PanelFocus?,
    onFocusMoved: () -> Unit,
    announce: Boolean,
) {
    val colors = LocalTaqwaColors.current
    val why = remember { FocusRequester() }
    Column(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accent.copy(alpha = PanelTint))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth().announcedWhen(announce)) {
            Text(
                stringResource(Res.string.widgets_refused_title),
                style = TaqwaText.caption.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = if (focus == PanelFocus.WHY) Modifier.focusRequester(why).focusable() else Modifier,
            )
            Spacer(Modifier.height(2.dp))
            Text(stringResource(Res.string.widgets_refused_body), style = TaqwaText.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        StepList(steps, name, tile = colors.surface, tileSize = 48.dp, artSize = 36.dp, gap = 12.dp, focus == PanelFocus.FIRST_STEP, onFocusMoved)
    }
    if (focus == PanelFocus.WHY) FocusOnceHeard(why, onFocusMoved)
}

/** The warm panel's tint: the accent faint enough over the card that the white tiles still lift. */
private const val PanelTint = 0.10f

/**
 * The iPhone's steps, which are simply how iOS adds any widget: no apology, under a hairline and a
 * small heading, with the fallback line for a phone on which the first step finds nothing. When
 * [announce], its heading is read out as the panel appears ([announcedWhen]).
 */
@Composable
private fun HowToPanel(
    steps: WidgetSteps,
    name: String,
    focusFirstStep: Boolean,
    onFocusMoved: () -> Unit,
    announce: Boolean = false,
) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = HairlineRoom, bottom = 16.dp)) {
        CardDivider()
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(Res.string.widgets_how_label),
            style = TaqwaText.sectionLabel,
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() }.announcedWhen(announce),
        )
        Spacer(Modifier.height(10.dp))
        StepList(steps, name, tile = howToTile(colors), tileSize = 52.dp, artSize = 40.dp, gap = 14.dp, focusFirstStep, onFocusMoved)
        steps.fallback?.let {
            Spacer(Modifier.height(8.dp))
            Text(markedText(stringResource(it), name), style = TaqwaText.caption.copy(fontSize = 13.sp), color = colors.textSecondary)
        }
    }
}

@Composable
private fun StepList(
    steps: WidgetSteps,
    name: String,
    tile: Color,
    tileSize: Dp,
    artSize: Dp,
    gap: Dp,
    focusFirstStep: Boolean,
    onFocusMoved: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val firstStep = remember { FocusRequester() }
    steps.steps.forEachIndexed { i, key ->
        // Focusable only where focus is to land, so no other step becomes a stop of its own.
        val focus = if (i == 0 && focusFirstStep) Modifier.focusRequester(firstStep).focusable() else Modifier
        // One node per step, so a screen reader says the number and the sentence together and
        // passes over the drawing, which only repeats the sentence.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).then(focus).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepNumber(format.localizedDigits(i + 1))
            Spacer(Modifier.width(gap))
            Box(
                Modifier.size(tileSize).clip(RoundedCornerShape(12.dp)).background(tile).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                StepArtwork(steps.art[i], artSize)
            }
            Spacer(Modifier.width(gap))
            Text(
                markedText(stringResource(key), name),
                style = TaqwaText.caption.copy(fontSize = 15.sp),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (focusFirstStep) FocusOnceHeard(firstStep, onFocusMoved)
}

/**
 * Moves focus to [requester]'s node a moment after it appears. Android's accessibility bridge
 * tells a screen reader that focus has moved only to a node it has already seen, and it looks for
 * new nodes at most every 100 ms; asked at once, focus moved without TalkBack hearing of it, and
 * TalkBack put its own on the Back button instead (seen on the emulator, review round 2). Then
 * [onMoved], so the screen forgets the tap that asked for it.
 */
@Composable
private fun FocusOnceHeard(requester: FocusRequester, onMoved: () -> Unit) {
    val moved by rememberUpdatedState(onMoved)
    LaunchedEffect(requester) {
        delay(NewNodeHeardMs)
        requester.requestFocus()
        moved()
    }
}

/** Long enough for the bridge's next look for new nodes, and short enough to follow the tap. */
private const val NewNodeHeardMs = 250L

/** The step number's disc at the default text size, and the size of the digit in it. */
private val StepDisc = 22.dp
private val StepDigitSize = 12.sp

/**
 * A step's number, the background colour cut out of an ink disc. The disc grows by as much as the
 * digit does with the reader's larger text (review round 2): fixed at 22 dp, the digit's line no
 * longer fitted from about 1.5 times the size and was cut off at its edge. At the default size it
 * is the 22 dp it always was, and at any size the digit sits in it as it does then.
 */
@Composable
private fun StepNumber(digits: String) {
    val colors = LocalTaqwaColors.current
    // How much the reader's text size has grown the digit, which Android 14 and later work out
    // per size rather than by one factor, so the disc grows exactly as its digit does.
    val disc = with(LocalDensity.current) { StepDisc * (StepDigitSize.toDp() / StepDigitSize.value.dp) }
    Box(
        Modifier.size(max(StepDisc, disc)).clip(CircleShape).background(colors.textPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            digits,
            style = TaqwaText.caption.copy(fontSize = StepDigitSize, fontWeight = FontWeight.ExtraBold),
            color = colors.background,
            maxLines = 1,
        )
    }
}

/**
 * The iPhone's Lock Screen widget: the picture, then its own status, which placement reports apart
 * from the prayer widget on the Home Screen. Nothing while placement is unread or cannot be told
 * ([placed] null), the check and "On your Lock Screen" when one is there, and otherwise the steps
 * behind "Show me how": nothing can ask iOS for a Lock Screen widget, so there is never a button.
 */
@Composable
private fun LockScreenCard(
    content: WidgetContent,
    placed: Boolean?,
    stepsOpen: Boolean,
    screenReader: Boolean,
    openedByTap: Boolean,
    onFocusMoved: () -> Unit,
    onShowSteps: () -> Unit,
) {
    SettingsCard {
        LockScreenStage(content, Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp))
        CardTitle(stringResource(Res.string.widgets_lock_name), stringResource(Res.string.widgets_lock_body))
        when {
            placed == null -> Spacer(Modifier.height(16.dp))
            placed -> PlacedStatus(stringResource(Res.string.widgets_on_lock_ios))
            // Only ever opened by the reader's own tap, so a screen reader's focus follows it there,
            // once: decided as the steps appear, as a widget card's are.
            stepsOpen -> {
                val opening = remember { stepsOpening(refused = false, screenReader, openedByTap) }
                HowToPanel(LockScreenSteps, name = "", focusFirstStep = opening.focus != null, onFocusMoved)
            }
            else -> ShowHowRow(onShowSteps)
        }
    }
}

/**
 * A Lock Screen as the widget sits on it: the time now, large, and under it the rectangular
 * widget ([LockScreenAccessory]) from the same mirror the widget reads. A picture, so it is hidden
 * from screen readers; the card's own words say what it is.
 */
@Composable
private fun LockScreenStage(content: WidgetContent, modifier: Modifier) {
    val format = LocalPlatformFormat.current
    // The time when the screen opened, as the Lock Screen above the widget would show it; not kept
    // ticking, because the stage is a picture of a Lock Screen rather than a clock.
    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val ink = DarkColors.textPrimary
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PreviewCorner))
            .background(PreviewLockScreen)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 20.dp)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            format.clockTime(now.hour, now.minute),
            style = TaqwaText.countdown.copy(fontSize = 46.sp),
            color = ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        LockScreenAccessory(content)
    }
}

/**
 * The rectangular Lock Screen widget as TaqwaWidgetViews.swift draws it: a first line with the
 * next prayer's "Maghrib in" phrase at the start and its time at the end ([lockScreenHeadline]),
 * and a smaller, fainter line under it naming the prayer after that with its time at the end
 * ("then Isha · العشاء", 19:50: the owner's decision of 1 November, where it once had no time). The
 * real accessory shows no countdown, so the picture shows none either; it once drew "in 0:21"
 * there, which the widget never says. At least the accessory's width on a Lock Screen, so the time
 * sits apart from the phrase as it does there. Each line is set as the accessory sets it at large
 * text sizes ([AccessoryLine]).
 */
@Composable
internal fun LockScreenAccessory(content: WidgetContent) {
    val ink = DarkColors.textPrimary
    Column(
        Modifier
            .widthIn(min = AccessoryWidth)
            .width(IntrinsicSize.Max)
            .clip(RoundedCornerShape(12.dp))
            .background(ink.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AccessoryLine(
                lockScreenHeadline(content),
                TaqwaText.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ink),
                Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            AccessoryLine(
                content.nextClockTime,
                TaqwaText.rowTime.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ink),
            )
        }
        prayerAfterNext(content)?.let { then ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AccessoryLine(
                    stringResource(Res.string.widgets_lock_then, then.displayName),
                    TaqwaText.caption.copy(fontSize = 11.sp, color = ink.copy(alpha = 0.7f)),
                    Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                AccessoryLine(
                    then.clockTime,
                    TaqwaText.rowTime.copy(fontSize = 11.sp, color = ink.copy(alpha = 0.7f)),
                )
            }
        }
    }
}

/**
 * One of the accessory's lines in [style], as the real accessory sets it when the reader's text is
 * large (the Lead's follow-up, 31 October): on one line at its size while it fits, and otherwise
 * shrunk until it does, to no less than [AccessoryMinScale] of that size, as
 * `.minimumScaleFactor(0.6)` shrinks it in TaqwaWidgetViews.swift. Where even that is too long, it
 * wraps at that size, so it is whole: the accessory would end it in "…", and a line cut at one
 * line, as this one was, could end mid-word. At the default size every line fits as it did.
 *
 * `BasicText` for `autoSize`, given [style] alone, as the `Text` it replaces was: a `Text` given a
 * style does not take `LocalTextStyle` as well, and merging it in here gave the lines the body
 * text's taller line height and another face for the Arabic.
 */
@Composable
private fun AccessoryLine(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style, autoSize = ShrinkThenWrap(style.fontSize))
}

/** The least the accessory shrinks a line to, as a share of its size (`.minimumScaleFactor`). */
internal const val AccessoryMinScale = 0.6f

/** How far each try shrinks a line, in sp: half points, as the adhkar counter's do. */
private const val AccessoryShrinkStep = 0.5f

/**
 * The size, in sp, an accessory line of [size] sp is set at: the largest from [size] down to
 * [AccessoryMinScale] of it, in [AccessoryShrinkStep]s, at which it [fitsOnOneLine]; and the
 * smallest when none does, at which it then wraps.
 */
internal fun accessoryLineSize(size: Float, fitsOnOneLine: (Float) -> Boolean): Float {
    val least = size * AccessoryMinScale
    var tried = size
    while (tried > least) {
        if (fitsOnOneLine(tried)) return tried
        tried -= AccessoryShrinkStep
    }
    return least
}

/** [AccessoryLine]'s sizing ([accessoryLineSize]), laying the line out at each size it tries
 * within the width it is given. */
private data class ShrinkThenWrap(val size: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        accessoryLineSize(size.value) { tried ->
            val line = performLayout(constraints, text, tried.sp)
            line.lineCount == 1 && !line.didOverflowWidth
        }.sp
}

/**
 * The picture's first line, the words the real accessory's first line says: `entry.headline` in
 * TaqwaWidgetViews.swift, which is [WidgetCountdown.headline] of the same mirror's content: the
 * "next prayer in" phrase the mirror carries in the reader's language,
 * "Maghrib in", "متبقٍّ على المغرب", "Akşam vaktine". That is the phrase whenever the widget has a
 * countdown to give, as it does on a Lock Screen with a mirror of the day, and the picture shows the
 * widget so. It once named the prayer alone there; the owner chose the accessory's words (31 October).
 */
internal fun lockScreenHeadline(content: WidgetContent): String =
    WidgetCountdown.headline(content, remainingMinutes = content.countdownMinutes)

/**
 * The prayer the accessory's second line names, chosen as `prayerAfterNext` in
 * TaqwaWidgetViews.swift chooses it: the row after the next prayer's, wrapping past Isha to Fajr,
 * or the first row when the next prayer is not among them; none when there are no rows.
 */
internal fun prayerAfterNext(content: WidgetContent): WidgetPrayerRow? {
    val rows = content.rows
    if (rows.isEmpty()) return null
    val next = rows.indexOfFirst { it.displayName == content.nextPrayerDisplayName }
    return if (next < 0) rows.first() else rows[(next + 1) % rows.size]
}

/** About the width iOS gives a rectangular Lock Screen widget, so the picture is the same shape. */
private val AccessoryWidth = 160.dp

/** The refused widgets by name, which any platform's saved state can hold. */
private val PinRefusedSaver = listSaver<Set<PinnableWidget>, String>(
    save = { refused -> refused.map { it.name } },
    restore = { names -> names.mapTo(mutableSetOf(), PinnableWidget::valueOf) },
)

/** The cards opened by hand, by the names [WidgetsSettingsScreen] gives them. */
private val ShownByHandSaver = listSaver<Set<String>, String>(
    save = { shown -> shown.toList() },
    restore = { names -> names.toSet() },
)

/**
 * A polite live region only when [announce], which [stepsOpening] decides: steps that replace a
 * refused button with no screen reader's focus moving into them are read out, and steps that were
 * there from the start are not announced as the screen's placement arrives.
 *
 * The region sits on a node that merges the words to be read, never on a box around them: CMP
 * 1.12.1's iOS mediator announces a live region only from that node's own label, and a box that
 * merges nothing has none, so VoiceOver stayed silent (as for the watch rows, 8aea9361). It is
 * put on the panel's first words alone, not on the whole panel, because a node that merges its
 * descendants takes the steps into itself and a screen reader could then no longer reach them one
 * by one; they stay nodes of their own, a swipe away from the words that announce them.
 */
private fun Modifier.announcedWhen(announce: Boolean): Modifier =
    if (announce) semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite } else this

@Composable
private fun StepArtwork(art: StepArt, size: Dp) {
    val colors = LocalTaqwaColors.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    val lockOutline = remember(colors) { lockScreenOutline(colors) }
    Canvas(Modifier.size(size)) { drawStepArt(art, colors, forward, lockOutline) }
}

/** The tiles the iPhone's step drawings sit on: the theme's hairline at half strength. */
internal fun howToTile(colors: TaqwaColors): Color = colors.hairline.copy(alpha = 0.5f)

/**
 * The outline drawn round the Lock Screen phones in the step drawings, or null where they need
 * none. They are slabs of [PreviewLockScreen], the slate of the picture above them, which stands
 * out from the light theme's pale tiles at 13.6:1 but sat on the dark theme's tiles at 1.1:1, its
 * edge lost (the Lead's follow-up, 31 October). Where the slate falls below [GraphicContrast]
 * against its tile, it is outlined in the dark theme's ink, the ink of the marks drawn on it,
 * which stands out from that tile at 15:1, as much as the slate does on the light theme. The light
 * theme's drawings are unchanged.
 */
internal fun lockScreenOutline(colors: TaqwaColors): Color? {
    val tile = howToTile(colors).compositeOver(colors.surface)
    return if (contrastRatio(PreviewLockScreen, tile) >= GraphicContrast) null else DarkColors.textPrimary
}

/** WCAG 2's least contrast for the parts of a graphic needed to make it out (1.4.11). */
private const val GraphicContrast = 3f

/** WCAG 2's contrast ratio of two opaque colours, from 1 to 21. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val lighter = max(a.luminance(), b.luminance())
    val darker = min(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * The step drawings, on a 40-unit grid like the glyphs' 16: a phone is a rounded slab of
 * [PreviewWallpaper] (or [PreviewLockScreen], outlined in [lockOutline] where it has one) with
 * white marks for what sits on it, and the app's own pieces are drawn in the theme's ink, accent
 * and ring, so they read as the previews above them do. [forward] mirrors the two that have a
 * side, the plus button's corner and the search field's magnifier, which iOS moves to the other
 * side under a right-to-left language.
 */
private fun DrawScope.drawStepArt(art: StepArt, colors: TaqwaColors, forward: Boolean, lockOutline: Color?) {
    val u = size.width / 40f
    // A mark's left edge, mirrored when the layout runs right to left.
    fun x(left: Float, width: Float = 0f) = if (forward) left else 40f - left - width
    fun box(left: Float, top: Float, width: Float, height: Float, radius: Float, color: Color, style: DrawStyle = Fill) =
        drawRoundRect(color, Offset(left * u, top * u), Size(width * u, height * u), CornerRadius(radius * u), style = style)
    fun dashed(width: Float, dash: Float) =
        Stroke(width = width * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * u, dash * u)))
    // The marks a home screen shows on its wallpaper are white in either theme, as the wallpaper
    // itself stays the same; on a Lock Screen they are the dark theme's ink.
    val mark = Color.White
    val lockInk = DarkColors.textPrimary
    val outline = Stroke(width = 1f * u)
    // The Lock Screen phone, its outline drawn inside its edge so the phone keeps its size.
    fun lockScreen() {
        box(7f, 4f, 26f, 32f, 6f, PreviewLockScreen)
        lockOutline?.let { box(7.5f, 4.5f, 25f, 31f, 5.5f, it, outline) }
    }
    when (art) {
        StepArt.HOLD_EMPTY_SPOT -> {
            box(7f, 4f, 26f, 32f, 6f, PreviewWallpaper)
            drawCircle(mark, 7.5f * u, Offset(20f * u, 21f * u), style = dashed(1.7f, 2.7f))
            drawCircle(mark, 2.8f * u, Offset(20f * u, 21f * u))
        }
        StepArt.WIDGETS_BUTTON -> {
            box(6f, 12f, 28f, 16f, 8f, colors.textPrimary)
            val square = Stroke(width = 1.5f * u)
            box(12f, 16.75f, 6.5f, 6.5f, 1.6f, colors.background, square)
            box(21.5f, 16.75f, 6.5f, 6.5f, 1.6f, colors.background, square)
        }
        StepArt.DRAG_INTO_PLACE -> {
            box(7f, 4f, 26f, 32f, 6f, PreviewWallpaper)
            box(11f, 10f, 18f, 10f, 3f, mark)
            val arrow = 1.7f * u
            drawLine(mark, Offset(20f * u, 23f * u), Offset(20f * u, 31f * u), arrow, StrokeCap.Round)
            drawLine(mark, Offset(16.7f * u, 27.7f * u), Offset(20f * u, 31f * u), arrow, StrokeCap.Round)
            drawLine(mark, Offset(23.3f * u, 27.7f * u), Offset(20f * u, 31f * u), arrow, StrokeCap.Round)
        }
        StepArt.HOLD_APP_ICON -> {
            drawCircle(colors.ring, 18f * u, center, style = dashed(1.5f, 3f))
            box(9f, 9f, 22f, 22f, 6f, colors.surface)
            box(9f, 9f, 22f, 22f, 6f, colors.hairline, outline)
            // The launcher icon's own mark, at the 78 % of its tile the launcher draws it.
            inset(9f * u) { scale(0.78f) { drawMihrab(colors.textPrimary, colors.ring) } }
        }
        StepArt.CHOOSE_SIZE -> {
            box(6f, 8f, 28f, 24f, 5f, colors.surface)
            box(6f, 8f, 28f, 24f, 5f, colors.hairline, outline)
            box(10f, 12f, 9f, 9f, 2.5f, colors.hairline)
            box(21f, 12f, 9f, 9f, 2.5f, colors.accent)
            box(10f, 24f, 20f, 4f, 2f, colors.hairline)
        }
        StepArt.LANDS_ON_SCREEN -> {
            box(6f, 5f, 28f, 30f, 6f, PreviewWallpaper)
            box(10f, 9f, 20f, 10f, 3f, mark)
            listOf(10f, 17.5f, 25f).forEach { left -> box(left, 23f, 5f, 5f, 1.5f, mark.copy(alpha = 0.4f)) }
        }
        StepArt.PLUS_BUTTON -> {
            box(6f, 5f, 28f, 30f, 6f, PreviewWallpaper)
            listOf(10f, 17.5f, 25f).forEach { left ->
                box(left, 19f, 5f, 5f, 1.5f, mark.copy(alpha = 0.4f))
                box(left, 26.5f, 5f, 5f, 1.5f, mark.copy(alpha = 0.4f))
            }
            val plus = Offset(x(12.5f) * u, 11.5f * u)
            drawCircle(mark, 4f * u, plus)
            val arm = 2f * u
            drawLine(colors.textPrimary, plus - Offset(arm, 0f), plus + Offset(arm, 0f), 1.4f * u, StrokeCap.Round)
            drawLine(colors.textPrimary, plus - Offset(0f, arm), plus + Offset(0f, arm), 1.4f * u, StrokeCap.Round)
        }
        StepArt.SEARCH -> {
            box(6f, 8f, 28f, 24f, 5f, colors.surface)
            box(6f, 8f, 28f, 24f, 5f, colors.hairline, outline)
            box(9.5f, 11.5f, 21f, 6f, 3f, colors.hairline)
            val lens = Offset(x(13f) * u, 14.3f * u)
            drawCircle(colors.textTertiary, 1.6f * u, lens, style = Stroke(width = 1.1f * u))
            val handle = if (forward) Offset(1.2f * u, 1.2f * u) else Offset(-1.2f * u, 1.2f * u)
            drawLine(colors.textTertiary, lens + handle, lens + handle * 2f, 1.1f * u, StrokeCap.Round)
            box(x(9.5f, 7f), 21f, 7f, 7f, 2f, colors.accent)
            box(x(18.5f, 12f), 22f, 12f, 2.5f, 1.25f, colors.hairline)
            box(x(18.5f, 8f), 25.5f, 8f, 2f, 1f, colors.hairline)
        }
        StepArt.HOLD_LOCK_SCREEN -> {
            lockScreen()
            box(14f, 9f, 12f, 4f, 2f, lockInk)
            drawCircle(lockInk, 6f * u, Offset(20f * u, 24f * u), style = dashed(1.5f, 2.4f))
            drawCircle(lockInk, 2.4f * u, Offset(20f * u, 24f * u))
        }
        StepArt.SPACE_UNDER_CLOCK -> {
            lockScreen()
            box(14f, 9f, 12f, 4f, 2f, lockInk)
            box(11f, 17f, 18f, 7f, 3f, colors.ring, dashed(1.4f, 2.2f))
        }
        StepArt.ROUND_OR_WIDE -> {
            lockScreen()
            box(14f, 9f, 12f, 4f, 2f, lockInk)
            drawCircle(colors.ring, 3.5f * u, Offset(14.5f * u, 21f * u))
            box(20f, 17.5f, 9f, 7f, 2.5f, colors.ring)
        }
    }
}
