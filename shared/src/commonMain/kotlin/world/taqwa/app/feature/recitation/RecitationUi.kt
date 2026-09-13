package world.taqwa.app.feature.recitation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.LocalTaqwaDark
import world.taqwa.app.design.components.Equaliser
import world.taqwa.app.design.mushafFamily
import world.taqwa.app.i18n.LocalPlatformFormat
import world.taqwa.app.i18n.isRtlLocale
import world.taqwa.app.recitation.DownloadCopy
import world.taqwa.app.recitation.DownloadFailure
import world.taqwa.app.quran.Surah
import world.taqwa.app.quran.displayName
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.recitation_fail_cancelled
import world.taqwa.app.resources.recitation_fail_checksum
import world.taqwa.app.resources.recitation_fail_needs_wifi
import world.taqwa.app.resources.recitation_fail_no_network
import world.taqwa.app.resources.recitation_fail_no_space
import world.taqwa.app.resources.recitation_fail_server
import world.taqwa.app.resources.recitation_gigabytes
import world.taqwa.app.resources.recitation_kbps
import world.taqwa.app.resources.recitation_megabytes
import world.taqwa.app.resources.recitation_on_phone
import world.taqwa.app.resources.recitation_style_murattal

/**
 * A reciter's monogram (spec §3, §12.5): the initial of the given name in the Hafs face on a deep
 * tinted disc, one hue per reciter. No photographs anywhere in the app — of the twenty-five
 * reciters checked, two have a portrait with a defensible licence, and two archive photographs
 * beside eight monograms would look like an accident.
 *
 * Drawn at runtime, so it costs no asset and is exact at any density: 56 dp in the picker, 44 dp
 * in its rows, 40 dp on the player bar.
 */
@Composable
fun ReciterMonogram(reciter: Reciter, size: Dp, modifier: Modifier = Modifier) {
    val dark = LocalTaqwaDark.current
    val hue = reciter.hueColors
    Box(
        modifier.size(size).background(Color(hue.disc(dark)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            reciter.monogram,
            fontFamily = mushafFamily(),
            fontSize = monogramSize(size),
            color = Color(hue.glyph(dark)),
            textAlign = TextAlign.Center,
            maxLines = 1,
            // Optically centred, not box-centred. The Hafs face reserves room above the letter
            // for a full stack of vocalisation marks, so a bare «م» centred on its line box sits
            // visibly low in the disc; lifting it by a tenth of the disc puts the ink in the
            // middle, which is what the eye reads as centred.
            modifier = Modifier.offset(y = -(size.value * 0.09f).dp),
        )
    }
}

/**
 * The letter at half the disc. The Hafs face draws well above and below its baseline (it is a
 * Quran face, built for full vocalisation), so an initial set at the usual 60 % of a disc runs out
 * of it; 46 % is what leaves the same optical margin at 40 dp and at 56 dp.
 */
private fun monogramSize(disc: Dp): TextUnit = (disc.value * 0.50f).sp

/** The reciter's name in the language the UI is in (spec §5.5). */
@Composable
fun reciterName(reciter: Reciter): String = if (isRtlLocale()) reciter.nameAr else reciter.nameEn

/** "Murattal · 64 kbps" — the download sheet's row, where the bitrate is what the reader is
 * about to spend megabytes on and so belongs on screen. */
@Composable
fun reciterStyleAndBitrate(reciter: Reciter): String {
    val format = LocalPlatformFormat.current
    val style = stringResource(Res.string.recitation_style_murattal)
    val kbps = stringResource(Res.string.recitation_kbps, format.localizedDigits(reciter.kbps))
    return "$style · $kbps"
}

/**
 * "Murattal", or "Murattal · 12 of 114 surahs on this phone" once there is something to count
 * (spec §5.5). The bitrate is deliberately absent here: in the picker it is the voice that is
 * being chosen, and ten rows each ending in a number nobody is comparing is ten rows of noise.
 */
@Composable
fun reciterPickerCaption(reciter: Reciter, downloadedCount: Int): String {
    val format = LocalPlatformFormat.current
    val style = stringResource(Res.string.recitation_style_murattal)
    if (downloadedCount <= 0) return style
    // Both numbers through the same formatter. The 114 used to be baked into the Arabic string
    // as ١١٤, which under ar-LY — whose numbering system is Latin, like every other number this
    // app prints for Mohamed — put an Arabic-Indic total beside a Latin count in one sentence.
    val on = stringResource(
        Res.string.recitation_on_phone,
        format.localizedDigits(downloadedCount),
        format.localizedDigits(SURAHS),
    )
    return "$style · $on"
}

/** The Quran's surahs, for the picker's "N of 114" alone. */
private const val SURAHS = 114

/**
 * "58.2 MB", in the locale's own digits and decimal separator. The arithmetic is
 * [DownloadCopy.megabytes], which the download notification already prints with — one shape for a
 * size across the whole feature, whether it is drawn by a composable or baked into a
 * notification by a worker with no composition to ask.
 */
@Composable
fun megabytes(bytes: Long): String = stringResource(Res.string.recitation_megabytes, megabytesBare(bytes))

/**
 * A size in whichever of the two units reads: "58.2 MB" for a surah, "0.9 GB" for a whole Quran.
 *
 * Only the storage surfaces use this — Settings › Recitation, its Downloads screen and the sheet's
 * whole-Quran button. A single surah is always [megabytes], because the largest of the 114 is
 * 110 MB and a column of sizes that changed unit halfway down would be a column nobody could
 * compare. See [DownloadCopy.useGigabytes] for where the boundary is and why.
 */
@Composable
fun dataSize(bytes: Long): String {
    val arabic = arabicDigits()
    return if (DownloadCopy.useGigabytes(bytes)) {
        stringResource(Res.string.recitation_gigabytes, DownloadCopy.gigabytes(bytes, arabic))
    } else {
        stringResource(Res.string.recitation_megabytes, DownloadCopy.megabytes(bytes, arabic))
    }
}

/** The bare number, for the first half of "9.3 of 58.2 MB" — the unit is printed once, for the
 * pair, so the line does not say MB twice. */
@Composable
fun megabytesBare(bytes: Long): String = DownloadCopy.megabytes(bytes, arabicDigits())

/**
 * Whether this locale prints Arabic-Indic digits, asked of the platform's own formatter rather
 * than of the text direction.
 *
 * They are not the same question. Mohamed's ar-LY writes Arabic words with Latin numerals — every
 * other number in the app already comes out that way, through
 * [world.taqwa.app.i18n.PlatformFormat.localizedDigits] — so a size formatted from `isRtlLocale()`
 * put «٠٫٢» on the sheet's button directly above «64 ك.ب/ث», which is the mixed-numeral bug the
 * app formats everything to avoid. Egyptian Arabic still gets ٩٫٣.
 */
@Composable
private fun arabicDigits(): Boolean = LocalPlatformFormat.current.localizedDigits(0) != "0"

/** Why a download stopped, as a sentence a reader can act on (spec §5.4). */
@Composable
fun downloadFailureSentence(reason: DownloadFailure): String = stringResource(
    when (reason) {
        DownloadFailure.NO_NETWORK -> Res.string.recitation_fail_no_network
        DownloadFailure.NEEDS_WIFI -> Res.string.recitation_fail_needs_wifi
        DownloadFailure.NOT_ENOUGH_SPACE -> Res.string.recitation_fail_no_space
        DownloadFailure.SERVER -> Res.string.recitation_fail_server
        DownloadFailure.CHECKSUM -> Res.string.recitation_fail_checksum
        DownloadFailure.CANCELLED -> Res.string.recitation_fail_cancelled
    },
)

/**
 * A surah's name in the UI's language, looked up off the composition. The player bar and the
 * download sheet both hold a surah *number* and neither has any business opening the database, so
 * the one lookup lives here and both draw the answer.
 */
@Composable
fun rememberSurahName(surah: Int?, lookup: suspend (Int) -> Surah): String {
    val arabic = isRtlLocale()
    val name by produceState("", surah, arabic) {
        val number = surah
        value = if (number == null) {
            ""
        } else {
            runCatching { lookup(number) }.getOrNull()?.displayName(arabic).orEmpty()
        }
    }
    return name
}

/**
 * The playing ayah's own mark: the equaliser in the accent, drawn wherever a speaker glyph would
 * otherwise be — the reader's ayah action row and the Mushaf's reference pill. It lives here
 * rather than beside the player bar because both of those are drawn on screens that know nothing
 * about the bar.
 */
@Composable
fun PlayingMark(modifier: Modifier = Modifier) {
    Equaliser(LocalTaqwaColors.current.accent, modifier, size = 14.dp)
}

/** The height the player bar occupies, which the reader's list and the Mushaf's pager keep clear
 * so the bar never covers the last ayah of a surah. */
val PlayerBarHeight: Dp = 56.dp
