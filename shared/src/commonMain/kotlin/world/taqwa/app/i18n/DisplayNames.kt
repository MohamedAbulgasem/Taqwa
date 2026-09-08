package world.taqwa.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.manropeFamily
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.high_lat_automatic
import world.taqwa.app.resources.high_lat_middle
import world.taqwa.app.resources.high_lat_seventh
import world.taqwa.app.resources.high_lat_twilight
import world.taqwa.app.resources.madhab_hanafi
import world.taqwa.app.resources.madhab_standard
import world.taqwa.app.resources.method_dubai
import world.taqwa.app.resources.method_egyptian
import world.taqwa.app.resources.method_isna
import world.taqwa.app.resources.method_karachi
import world.taqwa.app.resources.method_kuwait
import world.taqwa.app.resources.method_moonsighting
import world.taqwa.app.resources.method_muslim_world_league
import world.taqwa.app.resources.method_qatar
import world.taqwa.app.resources.method_singapore
import world.taqwa.app.resources.method_tehran
import world.taqwa.app.resources.method_turkey
import world.taqwa.app.resources.method_umm_al_qura
import world.taqwa.app.resources.prayer_asr
import world.taqwa.app.resources.prayer_dhuhr
import world.taqwa.app.resources.prayer_fajr
import world.taqwa.app.resources.prayer_isha
import world.taqwa.app.resources.prayer_maghrib
import world.taqwa.app.resources.prayer_sunrise
import world.taqwa.app.resources.sound_adhan
import world.taqwa.app.resources.sound_notification
import world.taqwa.app.resources.sound_silent
import world.taqwa.app.resources.sound_takbir
import world.taqwa.app.resources.ui_language

/**
 * True when the UI is Arabic: the strings Compose has actually resolved are the Arabic set. Read
 * from the resources themselves ([Res.string.ui_language] is "ar" only in `values-ar`) rather
 * than from the platform's locale tag, so this can never disagree with the words on screen — a
 * phone whose resource locale and default locale differ (a per-app language, a regional variant
 * the tag spells unexpectedly) used to get Arabic strings with the English naming rule, and so
 * showed every prayer name twice. Also usable outside a mirrored subtree, unlike
 * `LocalLayoutDirection`.
 */
@Composable
fun isRtlLocale(): Boolean = stringResource(Res.string.ui_language) == ARABIC_UI

private const val ARABIC_UI = "ar"

/**
 * Manrope ships no Arabic glyphs, so Arabic copy must come from the OS face — SF Arabic on iOS,
 * the OEM font on Android (spec §3). This is the one decision every text style in the app defers
 * to, which is why it is applied once in the theme rather than per `Text`.
 */
@Composable
fun uiFontFamily(): FontFamily = if (isRtlLocale()) FontFamily.Default else manropeFamily()

/** The prayer's name in the UI language alone — the pairing rule lives in [PrayerNaming]. */
@Composable
fun localizedPrayerName(prayer: Prayer): String = stringResource(
    when (prayer) {
        Prayer.FAJR -> Res.string.prayer_fajr
        Prayer.SUNRISE -> Res.string.prayer_sunrise
        Prayer.DHUHR -> Res.string.prayer_dhuhr
        Prayer.ASR -> Res.string.prayer_asr
        Prayer.MAGHRIB -> Res.string.prayer_maghrib
        Prayer.ISHA -> Res.string.prayer_isha
    },
)

@Composable
fun methodDisplayName(id: CalculationMethodId): String = stringResource(
    when (id) {
        CalculationMethodId.MUSLIM_WORLD_LEAGUE -> Res.string.method_muslim_world_league
        CalculationMethodId.ISNA -> Res.string.method_isna
        CalculationMethodId.EGYPTIAN -> Res.string.method_egyptian
        CalculationMethodId.UMM_AL_QURA -> Res.string.method_umm_al_qura
        CalculationMethodId.KARACHI -> Res.string.method_karachi
        CalculationMethodId.TEHRAN -> Res.string.method_tehran
        CalculationMethodId.DUBAI -> Res.string.method_dubai
        CalculationMethodId.KUWAIT -> Res.string.method_kuwait
        CalculationMethodId.QATAR -> Res.string.method_qatar
        CalculationMethodId.SINGAPORE -> Res.string.method_singapore
        CalculationMethodId.TURKEY -> Res.string.method_turkey
        CalculationMethodId.MOONSIGHTING_COMMITTEE -> Res.string.method_moonsighting
    },
)

@Composable
fun highLatitudeDisplayName(preference: HighLatitudePreference): String = stringResource(
    when (preference) {
        HighLatitudePreference.AUTOMATIC -> Res.string.high_lat_automatic
        HighLatitudePreference.MIDDLE_OF_NIGHT -> Res.string.high_lat_middle
        HighLatitudePreference.SEVENTH_OF_NIGHT -> Res.string.high_lat_seventh
        HighLatitudePreference.TWILIGHT_ANGLE -> Res.string.high_lat_twilight
    },
)

@Composable
fun madhabDisplayName(madhab: AsrMadhab): String = stringResource(
    when (madhab) {
        AsrMadhab.STANDARD -> Res.string.madhab_standard
        AsrMadhab.HANAFI -> Res.string.madhab_hanafi
    },
)

@Composable
fun soundDisplayName(sound: PrayerSound): String = stringResource(
    when (sound) {
        PrayerSound.SILENT -> Res.string.sound_silent
        PrayerSound.NOTIFICATION -> Res.string.sound_notification
        PrayerSound.TAKBIR -> Res.string.sound_takbir
        PrayerSound.ADHAN -> Res.string.sound_adhan
    },
)
