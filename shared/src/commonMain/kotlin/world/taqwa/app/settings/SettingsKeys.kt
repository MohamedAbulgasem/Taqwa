package world.taqwa.app.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object SettingsKeys {
    val THEME = stringPreferencesKey("theme_mode")
    val METHOD = stringPreferencesKey("calculation_method")
    val MADHAB = stringPreferencesKey("asr_madhab")
    val HIGH_LAT = stringPreferencesKey("high_latitude")
    val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
    val SHOW_SUNRISE = booleanPreferencesKey("show_sunrise")
    val REMIND_BEFORE = intPreferencesKey("remind_before_minutes")
    val ONBOARDED = booleanPreferencesKey("onboarding_complete")
    val LOCATION_LAT = doublePreferencesKey("location_latitude")
    val LOCATION_LON = doublePreferencesKey("location_longitude")
    val LOCATION_TZ = stringPreferencesKey("location_timezone")
    val LOCATION_CITY = stringPreferencesKey("location_city")
    val LOCATION_COUNTRY = stringPreferencesKey("location_country")
}
