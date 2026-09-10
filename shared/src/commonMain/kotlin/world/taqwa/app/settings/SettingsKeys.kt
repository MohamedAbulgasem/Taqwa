package world.taqwa.app.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import world.taqwa.app.domain.Prayer

internal object SettingsKeys {
    val THEME = stringPreferencesKey("theme_mode")
    val METHOD = stringPreferencesKey("calculation_method")
    // Set only by the method picker. Everything else that writes PrayerSettings — madhab, the
    // Hijri offset, sunrise, manual adjustments — must leave it alone, because it is the single
    // signal that stops a later relocation from overwriting a deliberate choice.
    val METHOD_USER_CHOSEN = booleanPreferencesKey("calculation_method_user_chosen")
    val MADHAB = stringPreferencesKey("asr_madhab")
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
    // Whether the stored location came from a fix or from the city list. Not derivable from the
    // location itself — see LocationSource — and the only thing "Use my location" can read.
    val LOCATION_SOURCE = stringPreferencesKey("location_source")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val WIDGET_BACKGROUND = stringPreferencesKey("widget_background")
    fun soundKey(prayer: Prayer) = stringPreferencesKey("sound_${prayer.name.lowercase()}")

    val QURAN_MODE = stringPreferencesKey("quran_mode")
    val QURAN_SIZE = intPreferencesKey("quran_size")
    val QURAN_TRANSLITERATION = booleanPreferencesKey("quran_transliteration")
    val QURAN_TRANSLATION = stringPreferencesKey("quran_translation")
    val QURAN_LAST_SURAH = intPreferencesKey("quran_last_surah")
    val QURAN_LAST_AYAH = intPreferencesKey("quran_last_ayah")
    val QURAN_LAST_PAGE = intPreferencesKey("quran_last_page")

    /** Bookmarked ayahs as "<surah>:<ayah>:<epochMillis>" entries (spec 2b §2.2). */
    val QURAN_BOOKMARKS = stringSetPreferencesKey("quran_bookmarks")
}
