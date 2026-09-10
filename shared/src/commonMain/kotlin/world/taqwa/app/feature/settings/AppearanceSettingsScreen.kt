package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.appearance_ayah_widget_preview_label
import world.taqwa.app.resources.appearance_note
import world.taqwa.app.resources.appearance_theme_label
import world.taqwa.app.resources.appearance_widget_background_label
import world.taqwa.app.resources.appearance_prayer_widget_preview_label
import world.taqwa.app.resources.appearance_widget_add
import world.taqwa.app.resources.settings_appearance
import world.taqwa.app.resources.theme_dark
import world.taqwa.app.resources.theme_light
import world.taqwa.app.resources.theme_system
import world.taqwa.app.resources.widget_background_dark
import world.taqwa.app.resources.widget_background_follow_theme
import world.taqwa.app.resources.widget_background_light
import world.taqwa.app.widget.PinnableWidget
import world.taqwa.app.widget.WidgetContent
import world.taqwa.app.widget.WidgetContentBuilder
import world.taqwa.app.widget.WidgetMirrorWriter
import world.taqwa.app.widget.WidgetOffer
import world.taqwa.app.widget.WidgetPinRequester
import world.taqwa.app.widget.WidgetPlacement
import world.taqwa.app.widget.WidgetPlacementSource
import world.taqwa.app.widget.createWidgetKeyValueStore
import world.taqwa.app.widget.isIosPlatform
import world.taqwa.app.widget.translucentOrFrostedSubtitleKey
import world.taqwa.app.widget.translucentOrFrostedTitleKey
import world.taqwa.app.widget.widgetAddInstructionsKey
import world.taqwa.app.widget.widgetAddPath
import world.taqwa.app.widget.widgetOffer

@Composable
internal fun themeDisplayName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> Res.string.theme_system
        ThemeMode.LIGHT -> Res.string.theme_light
        ThemeMode.DARK -> Res.string.theme_dark
    },
)

/**
 * Four rows rather than a segmented control: a row with a check reads correctly to a screen
 * reader as a selected option, and it matches every other choice in settings. Only the fourth
 * option carries a [widgetBackgroundSubtitle] — its per-platform wording is long enough that it
 * needs its own line rather than crowding the check mark off the row.
 */
@Composable
internal fun widgetBackgroundTitle(value: WidgetBackground): String = when (value) {
    WidgetBackground.FOLLOW_THEME -> stringResource(Res.string.widget_background_follow_theme)
    WidgetBackground.LIGHT -> stringResource(Res.string.widget_background_light)
    WidgetBackground.DARK -> stringResource(Res.string.widget_background_dark)
    WidgetBackground.TRANSLUCENT_OR_FROSTED ->
        stringResource(translucentOrFrostedTitleKey(isIosPlatform))
}

@Composable
internal fun widgetBackgroundSubtitle(value: WidgetBackground): String? = when (value) {
    WidgetBackground.TRANSLUCENT_OR_FROSTED ->
        stringResource(translucentOrFrostedSubtitleKey(isIosPlatform))
    else -> null
}

@Composable
fun AppearanceSettingsScreen(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    widgetBackground: WidgetBackground,
    onPickWidgetBackground: (WidgetBackground) -> Unit,
    widgetPinRequester: WidgetPinRequester,
    widgetPlacementSource: WidgetPlacementSource,
    onBack: () -> Unit,
) {
    // Starts at Unknown, which reports both widgets as placed and so offers nothing: the row
    // fades in when the real answer arrives, rather than flashing away when it turns out the
    // widget was already there. Read again on every ON_START, not just on entering composition,
    // because the whole point is that the user leaves to add the widget and comes back — and the
    // read itself hops off the main thread, since Android's answer is a binder call to the
    // launcher and iOS's can take up to two seconds.
    var placement by remember { mutableStateOf(WidgetPlacement.Unknown) }
    // A binder call to the launcher, and the answer cannot change while this screen is open, so
    // it is asked once rather than on every recomposition of either preview.
    val pinnable = remember(widgetPinRequester) {
        PinnableWidget.entries.associateWith(widgetPinRequester::isSupported)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(widgetPlacementSource, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            placement = withContext(Dispatchers.Default) { widgetPlacementSource.current() }
        }
    }

    SettingsScaffold(stringResource(Res.string.settings_appearance), onBack) {
        SectionLabel(stringResource(Res.string.appearance_theme_label))
        SettingsCard {
            ThemeMode.entries.forEachIndexed { i, mode ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = themeDisplayName(mode),
                    onClick = { onPick(mode) },
                    selectable = true,
                    trailing = { if (mode == current) CheckMark() },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsNote(stringResource(Res.string.appearance_note))

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.appearance_widget_background_label))
        SettingsCard {
            WidgetBackground.entries.forEachIndexed { i, value ->
                if (i > 0) CardDivider()
                TaqwaRow(
                    label = widgetBackgroundTitle(value),
                    subtitle = widgetBackgroundSubtitle(value),
                    onClick = { onPickWidgetBackground(value) },
                    selectable = true,
                    trailing = { if (value == widgetBackground) CheckMark() },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel(stringResource(Res.string.appearance_prayer_widget_preview_label))
        val systemIsDark = isSystemInDarkTheme()
        // Read once per composition rather than observed live: the mirror only changes when
        // Today refreshes (once a second while it's open), and re-reading it here on every
        // recomposition would be wasted work for a preview whose only job is to react to
        // [widgetBackground] changing.
        val mirrorContent: WidgetContent? = remember {
            WidgetMirrorWriter.read(createWidgetKeyValueStore())?.let(WidgetContentBuilder::build)
        }
        WidgetPreview(
            background = widgetBackground,
            systemIsDark = systemIsDark,
            content = mirrorContent,
            modifier = Modifier.padding(horizontal = SettingsGutter),
        )
        // Both offers are decided here, once, so the caption below can tell which preview it
        // belongs under.
        val prayerOffer = widgetOffer(
            PinnableWidget.PRAYER, placement.prayer, pinnable[PinnableWidget.PRAYER] == true, widgetAddPath,
        )
        val ayahOffer = widgetOffer(
            PinnableWidget.AYAH, placement.ayah, pinnable[PinnableWidget.AYAH] == true, widgetAddPath,
        )
        // Where the steps go, when the platform cannot be asked and they have to be written out.
        // They are the platform's, not the widget's, so they are said once: under whichever
        // preview needs them when only one does, and after both when both do, where they read as
        // a note on the screen rather than on the ayah card they happen to follow.
        val bothNeedSteps = prayerOffer is WidgetOffer.Instructions && ayahOffer is WidgetOffer.Instructions
        WidgetAddOffer(prayerOffer, widgetPinRequester, showInstructions = !bothNeedSteps)

        Spacer(Modifier.height(14.dp))
        SectionLabel(stringResource(Res.string.appearance_ayah_widget_preview_label))
        AyahWidgetPreview(
            background = widgetBackground,
            systemIsDark = systemIsDark,
            modifier = Modifier.padding(horizontal = SettingsGutter),
        )
        // The steps are the same sentence either way — they are how this platform adds any widget
        // at all — so they are said once, under the first preview that needs them, and suppressed
        // under the second. Said under both they read as a copy-paste; said only at the bottom
        // they would sit under the ayah preview while describing a missing prayer widget.
        WidgetAddOffer(ayahOffer, widgetPinRequester, showInstructions = !bothNeedSteps)

        if (bothNeedSteps) {
            Spacer(Modifier.height(10.dp))
            SettingsNote(stringResource(widgetAddInstructionsKey((prayerOffer as WidgetOffer.Instructions).path)))
        }
    }
}

/**
 * What sits directly under one preview: nothing at all when the widget is already on a home
 * screen, a row that asks the launcher for it where the launcher takes such requests, and the
 * platform's own steps where it does not. [showInstructions] is false when the caller is saying
 * those steps once for the whole screen instead, which it does when both widgets need them.
 */
@Composable
private fun WidgetAddOffer(
    offer: WidgetOffer,
    pinRequester: WidgetPinRequester,
    showInstructions: Boolean,
) {
    when (offer) {
        WidgetOffer.None -> Unit
        is WidgetOffer.Pin -> {
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                // Fire and forget: the launcher's confirmation sheet is system UI and its answer
                // never comes back, so the app does nothing further — the row simply is not there
                // the next time placement is read, on the ON_START that follows.
                TaqwaRow(
                    label = stringResource(Res.string.appearance_widget_add),
                    onClick = { pinRequester.requestPin(offer.widget) },
                    trailing = { ForwardChevron() },
                )
            }
        }
        is WidgetOffer.Instructions -> if (showInstructions) {
            Spacer(Modifier.height(10.dp))
            SettingsNote(stringResource(widgetAddInstructionsKey(offer.path)))
        }
    }
}

/**
 * The disclosure chevron, drawn from literal coordinates like [BackChevron] and mirrored by hand:
 * a `Row`'s trailing edge already moves itself under RTL, so the glyph inside it must flip too or
 * it points back the way the text came.
 */
@Composable
private fun ForwardChevron() {
    val colors = LocalTaqwaColors.current
    val pointsRight = LocalLayoutDirection.current == LayoutDirection.Ltr
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        fun x(fraction: Float) = if (pointsRight) w * fraction else w * (1f - fraction)
        val path = Path().apply {
            moveTo(x(0.36f), w * 0.14f)
            lineTo(x(0.68f), w * 0.50f)
            lineTo(x(0.36f), w * 0.86f)
        }
        drawPath(
            path = path,
            color = colors.textTertiary,
            style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
