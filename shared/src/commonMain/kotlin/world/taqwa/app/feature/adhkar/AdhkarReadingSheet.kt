package world.taqwa.app.feature.adhkar

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaCard
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.feature.settings.TaqwaToggle
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.isLatinScriptUi
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.quran.ReadingSettings
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhkar_reading
import world.taqwa.app.resources.adhkar_translation
import world.taqwa.app.resources.quran_sheet_size
import world.taqwa.app.resources.quran_sheet_transliteration
import kotlin.math.roundToInt

/**
 * The slider's stops, the reader's own: 22 to 40 sp in steps of 2, ten sizes, so eight stops
 * strictly between the two ends ([Slider]'s `steps` counts only those).
 */
private val SliderSteps = (ReadingSettings.MAX_SIZE - ReadingSettings.MIN_SIZE) / ReadingSettings.SIZE_STEP - 1

/**
 * The adhkar's "Aa" sheet (spec §3.6), "Reading": the Arabic size, and the two switches the pages
 * read — built from the reader's [world.taqwa.app.feature.quran.ReadingSheet] parts, not the reader's
 * sheet itself. There is no translation picker, no reading mode and no voice row here: the Quran
 * pages take the reader's translation and the reciter chosen in Settings, and a du'a has no audio.
 *
 * The size and the transliteration are the reader's own keys ([settings], written whole through
 * [onChange]) — one knob for the whole app; the translation is the adhkar's own switch
 * ([showTranslation], [onTranslation]). [preview] is the du'a fragment the size is shown on
 * ([AdhkarUiState.readingPreview]).
 */
@Composable
fun AdhkarReadingSheet(
    settings: ReadingSettings,
    showTranslation: Boolean,
    preview: String,
    onChange: (ReadingSettings) -> Unit,
    onTranslation: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        AdhkarReadingContent(settings, showTranslation, preview, onChange, onTranslation)
    }
}

/**
 * The sheet's contents, apart from its chrome so that a UI test can compose them.
 *
 * - The size row and slider are the reader's, colours, 20 dp dot and unbroken track included, and
 *   live here: the Mushaf's fixed-size note has no place in the adhkar. As in the reader, the
 *   drag moves only the preview and the number; the size is written once, when the drag ends.
 * - The preview is the du'a face at the live size, drawn by [DuaArabic] exactly as the page draws
 *   it. It is a picture of the size, not words to read, so a screen reader skips it — as the
 *   header's "Aa" letters are skipped in favour of the button's name.
 * - Transliteration only under a Latin interface, where the pages show it; the translation switch
 *   under every interface but Arabic, where the pages never show a translation (spec §3.2). Under
 *   the Arabic interface the sheet is the size alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdhkarReadingContent(
    settings: ReadingSettings,
    showTranslation: Boolean,
    preview: String,
    onChange: (ReadingSettings) -> Unit,
    onTranslation: (Boolean) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val transliterationRow = isLatinScriptUi()
    val translationRow = uiLanguage() != UiLanguage.ARABIC

    // Re-keyed to the stored size, as the reader's is: a committed drag comes back through the
    // store and settles here, and a change made elsewhere snaps the slider to it.
    var liveSize by remember(settings.arabicSizeSp) { mutableStateOf(settings.arabicSizeSp.toFloat()) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.adhkar_reading),
            style = TaqwaText.screenTitle.copy(fontSize = 20.sp),
            color = colors.textPrimary,
        )

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
            )
            Slider(
                value = liveSize,
                onValueChange = { liveSize = it },
                onValueChangeFinished = { onChange(settings.copy(arabicSizeSp = liveSize.roundToInt())) },
                valueRange = ReadingSettings.MIN_SIZE.toFloat()..ReadingSettings.MAX_SIZE.toFloat(),
                steps = SliderSteps,
                enabled = true,
                colors = sliderColors,
                interactionSource = sliderInteractions,
                // The reader's plain round dot and unbroken line, not material3's bar thumb with a
                // gap around it and a stop dot at the end.
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = sliderInteractions,
                        colors = sliderColors,
                        enabled = true,
                        thumbSize = DpSize(20.dp, 20.dp),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = sliderColors,
                        enabled = true,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (preview.isNotEmpty()) {
            Column {
                CardDivider()
                Column(Modifier.padding(vertical = 10.dp)) {
                    DuaArabic(preview, liveSize.roundToInt(), voiced = false, textAlign = TextAlign.Center)
                }
                CardDivider()
            }
        }

        if (transliterationRow || translationRow) {
            TaqwaCard {
                if (transliterationRow) {
                    TaqwaRow(
                        label = stringResource(Res.string.quran_sheet_transliteration),
                        trailing = {
                            TaqwaToggle(
                                checked = settings.transliteration,
                                onCheckedChange = { onChange(settings.copy(transliteration = it)) },
                            )
                        },
                    )
                }
                if (transliterationRow && translationRow) CardDivider()
                if (translationRow) {
                    TaqwaRow(
                        label = stringResource(Res.string.adhkar_translation),
                        trailing = { TaqwaToggle(checked = showTranslation, onCheckedChange = onTranslation) },
                    )
                }
            }
        }
    }
}
