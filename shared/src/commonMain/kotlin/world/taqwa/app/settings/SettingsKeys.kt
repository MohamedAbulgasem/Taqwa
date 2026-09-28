package world.taqwa.app.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import world.taqwa.app.domain.Prayer

internal object SettingsKeys {
    val THEME = stringPreferencesKey("theme_mode")
    // The old engine's method, whether the user chose it, and its Asr school: written by builds
    // before the never-early engine, read now only by SettingsRepository's migration (spec §8).
    val METHOD = stringPreferencesKey("calculation_method")
    val METHOD_USER_CHOSEN = booleanPreferencesKey("calculation_method_user_chosen")
    val MADHAB = stringPreferencesKey("asr_madhab")
    // The old high-latitude picker's rule, kept and read as PrayerSettings.legacyHighLatitude while
    // the timetable is an Other method (spec §8).
    val HIGH_LAT = stringPreferencesKey("high_latitude")
    val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
    val SHOW_SUNRISE = booleanPreferencesKey("show_sunrise")
    // One string rather than a key per prayer: the map is written and read as a unit, and a
    // single preference keeps the removal of an offset from leaving a stale key behind.
    val MINUTE_ADJUSTMENTS = stringPreferencesKey("minute_adjustments")
    // Now read/written by notification settings (Task 15); kept under the same preference name
    // so a value stored by Plan 1 keeps working.
    val REMIND_BEFORE = intPreferencesKey("remind_before_minutes")
    val ONBOARDED = booleanPreferencesKey("onboarding_complete")
    val LOCATION_LAT = doublePreferencesKey("location_latitude")
    val LOCATION_LON = doublePreferencesKey("location_longitude")
    val LOCATION_TZ = stringPreferencesKey("location_timezone")
    val LOCATION_CITY = stringPreferencesKey("location_city")
    // The GeoNames id of the chosen city, so its name can be shown in whatever language the
    // reader is in. Separate from LOCATION_CITY, which stays the English snapshot: a location
    // stored before this key existed has the name and no id.
    val LOCATION_CITY_ID = intPreferencesKey("location_city_id")
    // The answer to "what does LOCATION_CITY_ID's city read as, here?", remembered so a cold
    // start needs no lookup to print it. The name the header last showed, and the interface
    // language it was resolved in — the language matters because a stored Arabic name must not
    // be printed to a reader who has since switched to English. Both are cleared by setLocation
    // alongside the id, so a name can never outlive the city it belongs to.
    val LOCATION_CITY_DISPLAY_NAME = stringPreferencesKey("location_city_display_name")
    val LOCATION_CITY_DISPLAY_LANGUAGE = stringPreferencesKey("location_city_display_language")
    val LOCATION_COUNTRY = stringPreferencesKey("location_country")
    // The city's first-level region (admin-1) as the bundled city list spells it: the prayer
    // engine picks an authority's unit by it where the units are regions (Algeria's wilayas,
    // ruling R32). Cleared with the rest of the location's city facts.
    val LOCATION_REGION = stringPreferencesKey("location_region")
    // The Prayer screen's one-time backfill of the stored location's city facts has run, so it is
    // not run again for a city the list gives no region (Singapore, Hong Kong …). Cleared with
    // every new location.
    val LOCATION_BACKFILLED = booleanPreferencesKey("location_backfilled")
    // Whether the stored location came from a fix or from the city list. Not derivable from the
    // location itself — see LocationSource — and the only thing "Use my location" can read.
    val LOCATION_SOURCE = stringPreferencesKey("location_source")
    // Task 9 (spec §2.2, §8): the never-early engine's own settings. Populated once by
    // SettingsRepository.migrateIfNeeded from the old keys above, which it leaves in place, then
    // written per field by the Settings screens.
    val PRAYER_TIMETABLE = stringPreferencesKey("prayer_timetable")
    // The entry id the user last confirmed following (spec §8, ruling R52): the timetable is
    // confirmed while it equals PRAYER_TIMETABLE. The migration leaves it unset.
    val PRAYER_TIMETABLE_CONFIRMED = stringPreferencesKey("prayer_timetable_confirmed")
    // The id of the Automatic entry at the place where that confirmation was given (ruling R70):
    // where the place's Automatic differs (travel), the choice is paused again. A confirmation
    // stored without it (before R70) counts as not given.
    val PRAYER_TIMETABLE_CONFIRMED_UNDER = stringPreferencesKey("prayer_timetable_confirmed_under")
    val PRAYER_SCHOOL = stringPreferencesKey("prayer_school")
    val PRAYER_SHOW_BOTH_ASR = booleanPreferencesKey("prayer_show_both_asr")
    val PRAYER_SHOW_WHERE_DIFFER = booleanPreferencesKey("prayer_show_where_differ")
    val PRAYER_SAUDI_FAJR_LATER = booleanPreferencesKey("prayer_saudi_fajr_later")
    // "<PRAYER>:<entryId>" entries, same shape as MINUTE_ADJUSTMENTS but naming the registry
    // entry a negative adjustment was last confirmed against (spec §2.2).
    val PRAYER_ADJUST_CONFIRMED = stringPreferencesKey("prayer_adjust_confirmed")
    // Whether the once-only Sunni/cautious-times cards (spec §2.1) have already been shown.
    val PRAYER_CARD_SUNNI_SEEN = booleanPreferencesKey("prayer_card_sunni_seen")
    val PRAYER_CARD_CAUTIOUS_SEEN = booleanPreferencesKey("prayer_card_cautious_seen")
    // Gates SettingsRepository.migrateIfNeeded so the spec §8 migration runs at most once per
    // install; bump PRAYER_SETTINGS_SCHEMA_VERSION when the derivation changes.
    val PRAYER_SETTINGS_SCHEMA = intPreferencesKey("prayer_settings_schema")

    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val WIDGET_BACKGROUND = stringPreferencesKey("widget_background")
    fun soundKey(prayer: Prayer) = stringPreferencesKey("sound_${prayer.name.lowercase()}")

    /** Which adhan recording the Takbir and Adhan levels play — one voice for all five
     * prayers, so one key rather than one per prayer. Absent until the user picks a voice,
     * which is what makes the original the default for everyone who never does. */
    val ADHAN_VOICE = stringPreferencesKey("adhan_voice")
    val TAHAJJUD_ENABLED = booleanPreferencesKey("tahajjud_enabled")
    val TAHAJJUD_SOUND = stringPreferencesKey("tahajjud_sound")

    val QURAN_MODE = stringPreferencesKey("quran_mode")
    val QURAN_SIZE = intPreferencesKey("quran_size")
    val QURAN_TRANSLITERATION = booleanPreferencesKey("quran_transliteration")
    val QURAN_TRANSLATION = stringPreferencesKey("quran_translation")
    val QURAN_LAST_SURAH = intPreferencesKey("quran_last_surah")
    val QURAN_LAST_AYAH = intPreferencesKey("quran_last_ayah")
    val QURAN_LAST_PAGE = intPreferencesKey("quran_last_page")

    /** Bookmarked ayahs as "<surah>:<ayah>:<epochMillis>" entries (spec 2b §2.2). */
    val QURAN_BOOKMARKS = stringSetPreferencesKey("quran_bookmarks")

    /** The preset the counter reopens on; absent until one is picked (spec Tasbeeh §6). */
    val TASBEEH_SELECTED = stringPreferencesKey("tasbeeh_selected")

    /** Every preset keeps its own place, as "<count>:<round>", under its own key: switching
     * presets must not disturb the count of the one left behind. */
    fun tasbeehStateKey(presetId: String) = stringPreferencesKey("tasbeeh_state_$presetId")

    /** Custom phrases as "<id>\u001F<phrase>\u001F<target>" entries. U+001F because the phrase
     * is free text in any script and may contain any punctuation a reader can type. */
    val TASBEEH_CUSTOM = stringSetPreferencesKey("tasbeeh_custom")

    /** The voice the reader listens in; absent until one is picked, which is what makes Alafasy
     * the default for everyone who never does (spec 3a §12.1). */
    val RECITATION_RECITER = stringPreferencesKey("recitation_reciter")

    /** Wi-Fi only unless this is on (spec 3a §12.6). The per-download override is not stored. */
    val RECITATION_MOBILE_DATA = booleanPreferencesKey("recitation_mobile_data")

    /** Play on a surah that is not on the phone fetches it without the sheet (spec §15.3). */
    val RECITATION_AUTO_DOWNLOAD = booleanPreferencesKey("recitation_auto_download")

    /** Whether the download sheet has ever been confirmed, which is when the choice above is
     * first written; before that the sheet offers it ticked. */
    val RECITATION_AUTO_DOWNLOAD_ASKED = booleanPreferencesKey("recitation_auto_download_asked")

    /** Read-aloud spec §1. */
    val RECITATION_READ_ALOUD = booleanPreferencesKey("recitation_read_aloud")

    /** Which surahs of one reciter are downloaded, as decimal surah numbers. A key per reciter,
     * not one set of "<reciter>:<surah>" entries, so deleting a reciter is one key removed and so
     * the picker's per-reciter count is one read. [RECITATION_DOWNLOADED_PREFIX] is what lets
     * `RecitationLibrary.reconcile` find every such key without knowing the catalogue. */
    fun recitationDownloadedKey(reciterId: String) =
        stringSetPreferencesKey("$RECITATION_DOWNLOADED_PREFIX$reciterId")

    const val RECITATION_DOWNLOADED_PREFIX = "recitation_downloaded_"

    /** When the catalogue was last *asked* for, epoch millis (spec 3a §4). The attempt rather
     * than the success, so a repository that is down cannot turn every app start into an HTTP
     * timeout; see `ManifestRefresher`. */
    val RECITATION_MANIFEST_CHECKED = longPreferencesKey("recitation_manifest_checked")

    /**
     * True once the reader has touched recitation in any way — the speaker button, Play on an
     * ayah, the picker, the Recitation settings screen, "Download the whole Quran". The daily
     * catalogue fetch is gated on this (privacy spec §2), so an install that never opens
     * recitation never opens a socket.
     */
    val RECITATION_ENGAGED = booleanPreferencesKey("recitation_engaged")
}
