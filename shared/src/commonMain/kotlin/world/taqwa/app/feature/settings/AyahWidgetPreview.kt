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
        AyahPreviewContent(it, mirror.showsTranslation, mirror.translationRtl)
    } ?: AyahPreviewContent(SampleEntry, showsTranslation = true, translationRtl = false)

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

/** [entry] plus the two per-mirror display flags [AyahPoolMirror] carries alongside it. */
private data class AyahPreviewContent(
    val entry: AyahPoolEntry,
    val showsTranslation: Boolean,
    val translationRtl: Boolean,
)

@Composable
private fun AyahCardSurface(shown: AyahPreviewContent, colors: WidgetPaletteColors) {
    val (entry, showsTranslation, translationRtl) = shown
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
                    style = AyahArabicStyle,
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
                        style = TaqwaText.caption.copy(fontSize = 13.sp),
                        color = textColor.copy(alpha = 0.62f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AyahCardFooter(entry, textColor)
    }
}

@Composable
private fun AyahCardFooter(entry: AyahPoolEntry, textColor: Color) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(textColor.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.height(8.dp))
        val format = LocalPlatformFormat.current
        val reference = "${format.localizedDigits(entry.surah)}:${format.localizedDigits(entry.ayah)}"
        if (isRtlLocale()) {
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

/** Arabic style for the card (design spec §2): 1.75× line height, unlike the reader's 2×. */
private val AyahArabicStyle = TextStyle(
    fontSize = 22.sp,
    lineHeight = 38.5.sp,
    textAlign = TextAlign.Right,
    textDirection = TextDirection.Rtl,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

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
