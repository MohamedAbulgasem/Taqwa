package world.taqwa.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.manropeFamily
import world.taqwa.app.domain.AdhanVoice
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.feature.common.authorityShortName
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.adhan_voice_azeez
import world.taqwa.app.resources.adhan_voice_azemi
import world.taqwa.app.resources.adhan_voice_original
import world.taqwa.app.resources.asr_automatic
import world.taqwa.app.resources.high_lat_automatic
import world.taqwa.app.resources.high_lat_middle
import world.taqwa.app.resources.high_lat_seventh
import world.taqwa.app.resources.high_lat_twilight
import world.taqwa.app.resources.madhab_hanafi
import world.taqwa.app.resources.madhab_standard
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
import world.taqwa.app.resources.timetable_automatic
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

/**
 * The name of a stored timetable choice ([world.taqwa.app.domain.PrayerSettings.timetable]):
 * "Automatic", or the registry entry's short name; an id the registry no longer has reads as itself.
 */
@Composable
fun timetableDisplayName(timetable: String): String = when (timetable) {
    AUTOMATIC_TIMETABLE -> stringResource(Res.string.timetable_automatic)
    else -> Registry.byId(timetable)?.let { authorityShortName(it.shortNameKey) } ?: timetable
}

private const val AUTOMATIC_TIMETABLE = "automatic"

@Composable
fun highLatitudeDisplayName(preference: HighLatitudePreference): String = stringResource(
    when (preference) {
        HighLatitudePreference.AUTOMATIC -> Res.string.high_lat_automatic
        HighLatitudePreference.MIDDLE_OF_NIGHT -> Res.string.high_lat_middle
        HighLatitudePreference.SEVENTH_OF_NIGHT -> Res.string.high_lat_seventh
        HighLatitudePreference.TWILIGHT_ANGLE -> Res.string.high_lat_twilight
    },
)

/** The name of a stored Asr school ([world.taqwa.app.domain.PrayerSettings.school]). */
@Composable
fun schoolDisplayName(school: String): String = stringResource(
    when (school) {
        "standard" -> Res.string.madhab_standard
        "hanafi" -> Res.string.madhab_hanafi
        else -> Res.string.asr_automatic
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
