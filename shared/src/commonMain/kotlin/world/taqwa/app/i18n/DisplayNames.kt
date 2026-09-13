package world.taqwa.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.manropeFamily
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhan_voice_azeez
import world.taqwa.app.resources.adhan_voice_azemi
import world.taqwa.app.resources.adhan_voice_original
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
 * The language the interface is actually in: the one whose strings Compose has resolved. Read
 * from the resources themselves ([Res.string.ui_language] is the language's own code in each
 * `values-*` file) rather than from the platform's locale tag, so this can never disagree with
 * the words on screen — a phone whose resource locale and default locale differ (a per-app
 * language, a regional variant the tag spells unexpectedly) used to get Arabic strings with the
 * English naming rule, and so showed every prayer name twice.
 */
@Composable
fun uiLanguage(): UiLanguage = UiLanguage.of(stringResource(Res.string.ui_language))

/** Right-to-left interface: Arabic and Urdu. Usable outside a mirrored subtree, unlike `LocalLayoutDirection`. */
@Composable
fun isRtlLocale(): Boolean = uiLanguage().rtl

/** Latin-script interface: English, French, Turkish, Indonesian. Manrope and tracking apply. */
@Composable
fun isLatinScriptUi(): Boolean = uiLanguage().latinScript

/**
 * Manrope ships Latin glyphs only, so Arabic, Urdu and Bengali copy must come from the OS face —
 * SF on iOS, the OEM font on Android (spec §3). This is the one decision every text style in the
 * app defers to, which is why it is applied once in the theme rather than per `Text`.
 */
@Composable
fun uiFontFamily(): FontFamily = if (isLatinScriptUi()) manropeFamily() else FontFamily.Default

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

/**
 * The voice's name. A reciter's name is a name in either language — "Aaqib Azeez" is عاقب عزيز,
 * not a translation of it — so both sets are transliterations of the same person rather than
 * different words, and "Original" is the one entry that is genuinely translated.
 */
@Composable
fun adhanVoiceDisplayName(voice: AdhanVoice): String = stringResource(
    when (voice) {
        AdhanVoice.ORIGINAL -> Res.string.adhan_voice_original
        AdhanVoice.AZEEZ -> Res.string.adhan_voice_azeez
        AdhanVoice.AZEMI -> Res.string.adhan_voice_azemi
    },
)
