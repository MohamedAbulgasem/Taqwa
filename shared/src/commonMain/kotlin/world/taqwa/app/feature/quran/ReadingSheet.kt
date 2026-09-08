package world.taqwa.app.feature.quran

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.CheckMark
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.TaqwaSegmented
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.design.quran
import world.taqwa.app.feature.settings.TaqwaToggle
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.quran_mode_mushaf
import world.taqwa.app.resources.quran_mode_translation
import world.taqwa.app.resources.quran_sheet_mode
import world.taqwa.app.resources.quran_sheet_size
import world.taqwa.app.resources.quran_sheet_translation
import world.taqwa.app.resources.quran_sheet_transliteration
import world.taqwa.app.resources.quran_size_mushaf_note
import kotlin.math.roundToInt

/** Falls back to the bundled default's own name (spec §2.5) — matches [ReadingSettings]'s own
 * "en.sahih" fallback in [ReaderViewModel], never a translated string, since a translation's name
 * is data, not UI text. */
private const val FALLBACK_TRANSLATION_NAME = "Saheeh International"
private const val FALLBACK_TRANSLATION_ID = "en.sahih"

/** The slider's discrete stops between 22 and 40 sp in steps of 2 (spec §2.5): nine values, so
 * eight steps between the two ends — [Slider]'s own `steps` counts only the stops strictly
 * between [ReadingSettings.MIN_SIZE] and [ReadingSettings.MAX_SIZE]. */
private val SliderSteps = (ReadingSettings.MAX_SIZE - ReadingSettings.MIN_SIZE) / ReadingSettings.SIZE_STEP - 1

/** [CheckMark]'s own canvas size, reserved on every translation row so the rows' language labels
 * share one right edge whether or not the row is the selected one. */
private val CheckMarkSize = 20.dp

/**
 * The reading-settings sheet's picker order (spec §2.5): the tafsir first (there is at most one —
 * Tafsir al-Muyassar), then the remaining bundled translations by language, so the list groups by
 * kind before it groups by anything else. A free function rather than inline sorting inside
 * [ReadingSheet] so it is trivial to unit test on its own, and reusable by [ReaderViewModel], which
 * exposes [ReaderUiState.Ready.translations] already in this order.
 */
internal fun orderForSheet(list: List<TranslationInfo>): List<TranslationInfo> {
    val (tafsir, translations) = list.partition { it.kind == TextKind.TAFSIR }
    return tafsir + translations.sortedBy { it.language }
}

/**
 * The reading-settings sheet (spec §2.5, task 7): Arabic size slider with a live preview, the
 * transliteration toggle, the translation picker, and the reading-mode switch. Hosted inside a
 * `ModalBottomSheet` by the caller (currently [ReaderScreen]; the Mushaf screen, task 8, hosts the
 * same component) — this composable only draws the sheet's contents, since the sheet chrome
 * itself (container colour, shape, drag handle) is shared with every other sheet in the app.
 *
 * Every change here writes through [onChange] immediately except the Arabic size, which updates
 * [previewAyah]'s rendered size live during the drag but only calls [onChange] once the drag ends
 * (`Slider.onValueChangeFinished`), so scrubbing the slider does not write to disk on every frame.
 *
 * Picking a reading mode always dismisses the sheet (spec §2.5 point 4): [onChange] fires first
 * only if the mode actually changed, so the caller's own mode-switch path — which persists the
 * mode itself and navigates — runs exactly once, and picking the mode already showing is a no-op
 * dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSheet(
    settings: ReadingSettings,
    translations: List<TranslationInfo>,
    previewAyah: String,
    mushafMode: Boolean,
    onChange: (ReadingSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current

    // Re-keyed to the persisted value: a commit from this same slider round-trips back through
    // [onChange] and settles here again, and an external settings change (another device, a
    // future sync) should also snap the slider to the new truth rather than keep a stale drag.
    var liveSize by remember(settings.arabicSizeSp) { mutableStateOf(settings.arabicSizeSp.toFloat()) }
    var translationExpanded by remember { mutableStateOf(false) }

    Column(
        // Scrollable because material3 caps a ModalBottomSheet at the screen height and then lets
        // its content overflow silently: with the seven-row translation picker open, the reading
        // mode row was being drawn on top of the last picker row on a 1080p phone. The navigation
        // bar inset is the column's own, not the sheet's, so it only costs height once the content
        // actually reaches the gesture bar.
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TaqwaRow(
                label = stringResource(Res.string.quran_sheet_size),
                value = format.localizedDigits(liveSize.roundToInt()),
            )
            val sliderInteractions = remember { MutableInteractionSource() }
            val sliderColors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.hairline,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = colors.accent.copy(alpha = 0.4f),
                disabledActiveTrackColor = colors.accent.copy(alpha = 0.4f),
                disabledInactiveTrackColor = colors.hairline.copy(alpha = 0.4f),
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent,
            )
            Slider(
                value = liveSize,
                onValueChange = { liveSize = it },
                onValueChangeFinished = { onChange(settings.copy(arabicSizeSp = liveSize.roundToInt())) },
                valueRange = ReadingSettings.MIN_SIZE.toFloat()..ReadingSettings.MAX_SIZE.toFloat(),
                steps = SliderSteps,
                enabled = !mushafMode,
                colors = sliderColors,
                interactionSource = sliderInteractions,
                // A plain round dot and an unbroken line (spec §2.5's mockup), not material3's own
                // default "expressive" thumb (a thin vertical bar with a gap opening around it) or
                // its stop-indicator dot at the track's end.
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = sliderInteractions,
                        colors = sliderColors,
                        enabled = !mushafMode,
                        thumbSize = DpSize(20.dp, 20.dp),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = sliderColors,
                        enabled = !mushafMode,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (mushafMode) {
                Text(
                    stringResource(Res.string.quran_size_mushaf_note),
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                )
            }
        }

        Column {
            CardDivider()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                // The roundel takes the accent here as it does on every ayah card: the preview
                // must show the size the reader will actually use, colour included.
                val (base, marker) = QuranText.splitMarker(previewAyah)
                Text(
                    buildAnnotatedString {
                        append(base)
                        withStyle(SpanStyle(color = colors.accent)) { append(marker) }
                    },
                    fontFamily = mushafFamily(),
                    style = TaqwaText.quran(liveSize.roundToInt()).copy(textAlign = TextAlign.Center),
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
            CardDivider()
        }

        TaqwaRow(
            label = stringResource(Res.string.quran_sheet_transliteration),
            trailing = {
                TaqwaToggle(
                    checked = settings.transliteration,
                    onCheckedChange = { onChange(settings.copy(transliteration = it)) },
                )
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = translations.firstOrNull { it.id == settings.translationId }
            // What the reader actually loads when the stored id is not bundled (ReaderViewModel
            // falls back to Saheeh International), so the check mark agrees with the text shown.
            val effectiveId = current?.id ?: FALLBACK_TRANSLATION_ID
            TaqwaRow(
                label = stringResource(Res.string.quran_sheet_translation),
                value = current?.name ?: FALLBACK_TRANSLATION_NAME,
                onClick = { translationExpanded = !translationExpanded },
                // The list unfolding beneath is the tap's feedback; a ripple would be a second one.
                ripple = false,
            )
            if (translationExpanded) {
                TaqwaCard {
                    translations.forEachIndexed { index, info ->
                        if (index > 0) CardDivider()
                        TaqwaRow(
                            label = info.name,
                            subtitle = info.translator,
                            // The translation's own language, named in the reader's UI language:
                            // the names in the list are the translators' own ("Muhammad Hamidullah"
                            // says nothing about French to someone who does not read it), so the
                            // language is the only part of the row a reader can choose by.
                            value = format.languageName(info.language),
                            selectable = true,
                            onClick = {
                                onChange(settings.copy(translationId = info.id))
                                translationExpanded = false
                            },
                            // A fixed slot on every row, empty or not: TaqwaRow lays the value out
                            // against the trailing slot's width, so a missing check mark would let
                            // each unselected row's language slide 20 dp further out than the
                            // selected one's and break the column they otherwise line up in.
                            trailing = {
                                Box(Modifier.size(CheckMarkSize), contentAlignment = Alignment.Center) {
                                    if (info.id == effectiveId) CheckMark()
                                }
                            },
                        )
                    }
                }
            }
        }

        TaqwaRow(
            label = stringResource(Res.string.quran_sheet_mode),
            trailing = {
                TaqwaSegmented(
                    options = listOf(
                        stringResource(Res.string.quran_mode_translation),
                        stringResource(Res.string.quran_mode_mushaf),
                    ),
                    selectedIndex = if (settings.mode == ReadingMode.MUSHAF) 1 else 0,
                    onSelect = { index ->
                        val newMode = if (index == 0) ReadingMode.TRANSLATION else ReadingMode.MUSHAF
                        if (newMode != settings.mode) onChange(settings.copy(mode = newMode))
                        onDismiss()
                    },
                )
            },
        )
    }
}
