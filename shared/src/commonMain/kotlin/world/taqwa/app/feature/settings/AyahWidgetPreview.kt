package world.taqwa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.design.PreviewWallpaper
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.quran.QuranText
import world.taqwa.app.widget.AyahPoolEntry
import world.taqwa.app.widget.AyahPoolMirror
import world.taqwa.app.widget.AyahRotation
import world.taqwa.app.widget.WidgetDigits
import world.taqwa.app.widget.WidgetPalette
import world.taqwa.app.widget.WidgetPaletteColors
import world.taqwa.app.widget.createWidgetKeyValueStore
import kotlin.time.Clock

/** The card's own aspect ratio (design spec §2): Android's default 4×3 cell, width:height. */
private const val CardAspectRatio = 320f / 236f

/**
 * A Compose stand-in of the ayah widget's 4×3 card (design spec §2, §9), drawn over the same
 * [PreviewWallpaper] frame [WidgetPreview] uses, in its own frame below it. Shows today's pool
 * entry when the app has already written the mirror; otherwise a fixed sample so the preview
 * still means something on a fresh install, before Today has ever run.
 */
@Composable
fun AyahWidgetPreview(
    background: WidgetBackground,
    systemIsDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = WidgetPalette.colorsFor(background, systemIsDark)
    // Read once per composition, like the prayer preview: the mirror only changes when Today
    // rewrites it, so re-reading on every recomposition would be wasted work for a preview whose
    // only job here is to react to [background] changing.
    val (mirror, seed) = remember {
        val store = createWidgetKeyValueStore()
        AyahPoolMirror.read(store) to (AyahPoolMirror.seed(store) ?: 0L)
    }
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val epochDay = AyahRotation.epochDay(today.year, today.monthNumber, today.dayOfMonth)
    val shown = mirror?.entryFor(epochDay, seed)?.let {
        AyahPreviewContent(
            it,
            mirror.showsTranslation,
            mirror.translationRtl,
            mirror.languageTag,
            mirror.arabicIndicDigits,
        )
    } ?: AyahPreviewContent(
        SampleEntry,
        showsTranslation = true,
        translationRtl = false,
        // No mirror, no tag to follow: the sample is the app's own copy, so it follows the device
        // (see [AyahCardFooter]).
        languageTag = null,
        arabicIndicDigits = null,
    )

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PreviewWallpaper)
            .padding(14.dp),
    ) {
        AyahCardSurface(shown, colors)
    }
}

/**
 * [entry] plus the per-mirror display values [AyahPoolMirror] carries alongside it.
 *
 * [languageTag] is the mirror's own tag, or null when this is the built-in sample and there is no
 * mirror to follow — the distinction both widgets make, and the one the footer needs (§M3).
 * [arabicIndicDigits] is the mirror's recorded digit choice, null on the same sample, where the
 * device's own format answers instead.
 */
private data class AyahPreviewContent(
    val entry: AyahPoolEntry,
    val showsTranslation: Boolean,
    val translationRtl: Boolean,
    val languageTag: String?,
    val arabicIndicDigits: Boolean?,
)

@Composable
private fun AyahCardSurface(shown: AyahPreviewContent, colors: WidgetPaletteColors) {
    val (entry, showsTranslation, translationRtl, languageTag, arabicIndicDigits) = shown
    val textColor = Color(colors.textArgb)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CardAspectRatio)
            .clip(RoundedCornerShape(19.dp))
            .background(Color(colors.backgroundArgb).copy(alpha = colors.backgroundAlpha))
            .padding(14.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    buildAnnotatedString {
                        append(entry.arabic)
                        append(QuranText.MARKER_SEPARATOR)
                        withStyle(SpanStyle(color = Color(colors.accentArgb))) {
                            append(QuranText.arabicIndic(entry.ayah))
                        }
                    },
                    style = ayahArabicStyle(entry.arabic),
                    fontFamily = mushafFamily(),
                    color = textColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showsTranslation) {
                Spacer(Modifier.height(8.dp))
                val translationDirection = if (translationRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides translationDirection) {
                    Text(
                        entry.translation,
                        style = TaqwaText.caption.copy(fontSize = translationSizeFor(entry.translation)),
                        color = textColor.copy(alpha = 0.62f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AyahCardFooter(entry, textColor, languageTag, arabicIndicDigits)
    }
}

/**
 * Spec §2's footer. [languageTag] is the mirror's, or null for the built-in sample.
 *
 * Which script the footer uses, and which digits, follow the *mirror* rather than the device
 * whenever there is a mirror — because that is exactly what both real widgets do
 * (`TaqwaAyahGlanceWidget.readAyahRender`, `AyahTimelineProvider.entries`): the tag is the one the
 * surah names in the mirror were read under, so the digits beside them stay in the same script.
 * A preview that took the device's answer instead would disagree with the card it is previewing
 * whenever the two differ — the window between a language change and the mirror's rewrite. The
 * digits follow the mirror's recorded [arabicIndicDigits] rather than being re-derived from the
 * tag, for the reason D2 records: on an `ar-LY` S23 the tag rule and the device's own ICU data
 * give different answers, and the mirror carries the one the app is actually drawing. Only the
 * sample, which the app owns and no mirror describes, falls back to the device's own values.
 */
@Composable
private fun AyahCardFooter(
    entry: AyahPoolEntry,
    textColor: Color,
    languageTag: String?,
    arabicIndicDigits: Boolean?,
) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(textColor.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.height(8.dp))
        val format = LocalPlatformFormat.current
        val plainReference = "${entry.surah}:${entry.ayah}"
        val reference = if (arabicIndicDigits != null) {
            WidgetDigits.localize(plainReference, arabicIndicDigits)
        } else {
            "${format.localizedDigits(entry.surah)}:${format.localizedDigits(entry.ayah)}"
        }
        val arabicUi = languageTag?.startsWith("ar") ?: isRtlLocale()
        if (arabicUi) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.surahArabic,
                    fontFamily = mushafFamily(),
                    fontSize = 15.sp,
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    reference,
                    style = TaqwaText.caption.copy(fontSize = 12.sp),
                    color = textColor.copy(alpha = 0.42f),
                    maxLines = 1,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = textColor)) {
                            append(entry.surahLatin)
                        }
                        withStyle(SpanStyle(color = textColor.copy(alpha = 0.42f))) {
                            append(" · ")
                            append(reference)
                        }
                    },
                    style = TaqwaText.caption.copy(fontSize = 12.sp),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    entry.surahArabic,
                    fontFamily = mushafFamily(),
                    fontSize = 16.sp,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Arabic style for the card (design spec §2): 1.75× line height, unlike the reader's 2×.
 *
 * The real widgets auto-fit (spec §2: 28 sp down to 17 sp, measured against the drawn height). The
 * preview cannot measure the same way — it is a fixed 320:236 box in a scrolling settings screen —
 * so it approximates that fit with a deterministic rule on the ayah's own length, which is what
 * decides how many lines it takes at a given size:
 *
 *   ≤ 8 Arabic words → 22 sp; ≤ 16 → 20 sp; more → 18 sp.
 *
 * The three steps are what the longest ayahs in the pool need to stay inside the card: at a flat
 * 22 sp, 39:53 and 2:186 overran the footer and were clipped. 18 sp is still above the widgets' own
 * 17 sp floor, so nothing the preview shows is smaller than what the card can draw.
 */
private fun ayahArabicStyle(arabic: String): TextStyle {
    val words = arabic.split(' ').count { it.isNotBlank() }
    val size = when {
        words <= 8 -> 22f
        words <= 16 -> 20f
        else -> 18f
    }
    return TextStyle(
        fontSize = size.sp,
        lineHeight = (size * 1.75f).sp,
        textAlign = TextAlign.Right,
        textDirection = TextDirection.Rtl,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
}

/**
 * The translation's size, on the same deterministic principle as [ayahArabicStyle] and by its
 * character count, since a translation is one wrapped paragraph rather than a shaped Arabic line:
 * ≤ 120 characters → 13 sp, longer → 12 sp. The pool caps a translation at 245 characters
 * (spec §3), so 12 sp is the floor this can ever reach.
 */
private fun translationSizeFor(translation: String) =
    if (translation.length <= 120) 13.sp else 12.sp

/**
 * Ar-Ra'd 13:28, its Saheeh International translation, for when the mirror has not been written
 * yet: onboarding runs before Today has ever shown, and Appearance can be opened on a fresh
 * install before the pool exists.
 */
private val SampleEntry = AyahPoolEntry(
    surah = 13,
    ayah = 28,
    surahLatin = "Ar-Ra'd",
    surahArabic = "الرعد",
    arabic = "ٱلَّذِينَ ءَامَنُواْ وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ ٱللَّهِ ۗ أَلَا بِذِكْرِ ٱللَّهِ تَطْمَئِنُّ ٱلْقُلُوبُ",
    translation = "Those who have believed and whose hearts are assured by the remembrance of Allah. Unquestionably, by the remembrance of Allah hearts are assured.\"",
)
