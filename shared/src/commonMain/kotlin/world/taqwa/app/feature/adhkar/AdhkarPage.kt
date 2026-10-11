package world.taqwa.app.feature.adhkar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.adhkar.QuranPrefix
import world.taqwa.app.adhkar.Source
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.design.quran
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.isLatinScriptUi
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.i18n.uppercaseIn
import world.taqwa.app.quran.QuranText
import world.taqwa.app.quran.RTL_TRANSLATION_LANGUAGES
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhkar_a11y_count
import world.taqwa.app.resources.adhkar_once
import world.taqwa.app.resources.adhkar_times
import world.taqwa.app.resources.adhkar_times_today
import world.taqwa.app.resources.source_abu_dawud
import world.taqwa.app.resources.source_ahmad
import world.taqwa.app.resources.source_bukhari
import world.taqwa.app.resources.source_hakim
import world.taqwa.app.resources.source_ibn_majah
import world.taqwa.app.resources.source_muslim
import world.taqwa.app.resources.source_nasai
import world.taqwa.app.resources.source_quran
import world.taqwa.app.resources.source_tirmidhi

/**
 * One page of the session (spec §3.2): the label, the Arabic, the transliteration and translation
 * under it where the interface has them, and the references — a scrolling column at the 24 dp
 * gutter.
 *
 * **The whole page counts.** The tap surface is the page itself, not the column inside it: the
 * `clickable` sits outside the scroll, so the empty space under a short du'a counts as surely as
 * the words do, and a drag still scrolls a long one. No ripple — the ring in the foot is the
 * feedback, as on the tasbeeh.
 *
 * **What a screen reader hears** (spec §3.7): the page is one group, "label. words. reference",
 * with "Count" as a custom action beside the tap. The words are the translation where one is shown;
 * otherwise the Arabic, marked as Arabic so the reader's own Arabic voice says it. A Quran page's
 * Arabic is never voiced: its block stands for its reference line, and the reciter is its voice.
 */
@Composable
internal fun AdhkarPageView(
    page: AdhkarPageUi,
    arabicSizeSp: Int,
    showTranslation: Boolean,
    transliteration: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTaqwaColors.current
    val language = uiLanguage()
    val countLabel = stringResource(Res.string.adhkar_a11y_count)
    val reference = referenceLine(page.item.sources)
    val quran = page.quran
    // A Quran page shows the reader's translation of the passage, a du'a its own.
    val translation = if (quran != null) quran.translation else page.translation
    // Never under the Arabic interface: the Arabic is already the reader's own language there.
    val translationShown = showTranslation && language != UiLanguage.ARABIC && translation != null
    Column(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // "Double-tap to count", not to "activate".
                onClickLabel = countLabel,
                onClick = onTap,
            )
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(countLabel) { onTap(); true })
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(pageLabel(page).uppercaseIn(language), style = TaqwaText.sectionLabel, color = colors.accent)
        Spacer(Modifier.height(16.dp))
        if (quran != null) {
            QuranBlock(quran, page.item.quran?.prefix, arabicSizeSp, reference)
        } else if (page.arabic != null) {
            DuaArabic(page.arabic, arabicSizeSp, voiced = !translationShown)
        }
        if (transliteration && isLatinScriptUi() && page.transliteration != null) {
            Spacer(Modifier.height(8.dp))
            // Not voiced: a screen reader's Latin voice reading a romanised Arabic line is noise
            // between the label and the meaning.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    page.transliteration,
                    style = TaqwaText.caption.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic),
                    color = colors.textTertiary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                )
            }
        }
        if (translationShown && translation != null) {
            Spacer(Modifier.height(10.dp))
            // A du'a's translation is in the interface language; a Quran page's is the reader's
            // chosen translation, in whatever language that is.
            val rtl = if (quran != null) quran.translationLanguage in RTL_TRANSLATION_LANGUAGES else language.rtl
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Text(
                    translation,
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            reference,
            style = TaqwaText.caption.copy(fontSize = 12.sp),
            color = colors.textTertiary,
            // A Quran page's block already says its reference; once is enough.
            modifier = if (quran != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A du'a in the OS face (Manrope has no Arabic): the reader's Arabic size less 2 sp, so it sits a
 * step below the Quran's Hafs face at the same setting and the two faces tell the reader which is
 * which; 1.8× leading for the harakat, and no tracking — tracking a cursive script breaks it.
 *
 * [voiced] false leaves it out of the page's spoken group, where the translation speaks for it.
 * The reading sheet's preview line is drawn by this too, centred ([textAlign]), so the size it
 * shows is the size the page will use.
 */
@Composable
internal fun DuaArabic(arabic: String, sizeSp: Int, voiced: Boolean, textAlign: TextAlign = TextAlign.Right) {
    val colors = LocalTaqwaColors.current
    val size = sizeSp - DUA_SIZE_STEP
    val text = remember(arabic) {
        buildAnnotatedString { withStyle(SpanStyle(localeList = LocaleList("ar"))) { append(arabic) } }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = size.sp,
                lineHeight = (size * DUA_LEADING).sp,
                letterSpacing = 0.sp,
            ),
            textAlign = textAlign,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().then(if (voiced) Modifier else Modifier.clearAndSetSemantics {}),
        )
    }
}

/**
 * A Quran passage as the reader draws one (spec §3.2, §3.5): the Uthmani text from `quran.db` in
 * the Hafs face with each ayah's roundel, exactly as [world.taqwa.app.feature.quran.AyahCard] —
 * and before it the istiʿadhah in the OS face at size − 6 in the secondary colour (the words Hisn
 * prints before Ayat al-Kursi, not Quran), or the basmala line the reader draws above a surah.
 *
 * The whole block is one node that says its [reference] and nothing else: the Quran is never read
 * out by a phone voice.
 */
@Composable
private fun QuranBlock(text: QuranPageText, prefixKind: QuranPrefix?, sizeSp: Int, reference: String) {
    val colors = LocalTaqwaColors.current
    val hafs = mushafFamily()
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = reference }) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val prefix = text.prefix
            if (prefix != null) {
                when (prefixKind) {
                    QuranPrefix.BASMALA -> Text(
                        prefix,
                        fontFamily = hafs,
                        fontSize = BASMALA_SIZE,
                        textAlign = TextAlign.Center,
                        color = colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> {
                        val size = sizeSp - ISTIADHAH_SIZE_STEP
                        Text(
                            prefix,
                            style = TextStyle(
                                fontFamily = FontFamily.Default,
                                fontSize = size.sp,
                                lineHeight = (size * DUA_LEADING).sp,
                                letterSpacing = 0.sp,
                            ),
                            textAlign = TextAlign.Right,
                            color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                passage(text.lines, colors.accent),
                style = TaqwaText.quran(sizeSp),
                fontFamily = hafs,
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The ayahs run on as one paragraph, each closed by its roundel in the accent. */
private fun passage(lines: List<Pair<String, Int?>>, accent: Color): AnnotatedString =
    buildAnnotatedString {
        lines.forEachIndexed { index, (ayah, number) ->
            if (index > 0) append(' ')
            append(ayah)
            if (number != null) {
                append(QuranText.MARKER_SEPARATOR)
                withStyle(SpanStyle(color = accent)) { append(QuranText.arabicIndic(number)) }
            }
        }
    }

/**
 * "3 TIMES", "ONCE", "100 TIMES TODAY": how often the page is said, before the casing rule. Under
 * the scripts without case it is the words as written.
 */
@Composable
internal fun pageLabel(page: AdhkarPageUi): String {
    val digits = LocalPlatformFormat.current.localizedDigits(page.target)
    return when {
        page.item.countDaily -> stringResource(Res.string.adhkar_times_today, digits)
        page.target == 1 -> stringResource(Res.string.adhkar_once)
        else -> pluralStringResource(Res.plurals.adhkar_times, page.target, digits)
    }
}

/**
 * "Abu Dawud 5088 · Tirmidhi 3388": each collection by its name in the interface language, each
 * number with the interface's digits. A collection this build has no name for is shown as the
 * file spells it rather than dropped — a reference is never silently lost.
 */
@Composable
internal fun referenceLine(sources: List<Source>): String {
    val format = LocalPlatformFormat.current
    val parts = sources.map { source ->
        val number = buildString {
            source.number.forEach { c -> if (c in '0'..'9') append(format.localizedDigits(c - '0')) else append(c) }
        }
        "${sourceName(source.collection)} $number"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun sourceName(collection: String): String = when (collection) {
    "quran" -> stringResource(Res.string.source_quran)
    "bukhari" -> stringResource(Res.string.source_bukhari)
    "muslim" -> stringResource(Res.string.source_muslim)
    "abu_dawud" -> stringResource(Res.string.source_abu_dawud)
    "tirmidhi" -> stringResource(Res.string.source_tirmidhi)
    "ibn_majah" -> stringResource(Res.string.source_ibn_majah)
    "nasai" -> stringResource(Res.string.source_nasai)
    "hakim" -> stringResource(Res.string.source_hakim)
    "ahmad" -> stringResource(Res.string.source_ahmad)
    else -> collection
}

/** The du'a's Arabic sits this far under the reader's Arabic size (spec §3.2). */
private const val DUA_SIZE_STEP = 2

/** The istiʿadhah before Ayat al-Kursi, in the OS face, this far under it. */
private const val ISTIADHAH_SIZE_STEP = 6

/** Leading for the OS face's Arabic: room for the harakat above and below. */
private const val DUA_LEADING = 1.8f

/** The reader's own basmala line, at the size the reader draws it. */
private val BASMALA_SIZE = 24.sp
