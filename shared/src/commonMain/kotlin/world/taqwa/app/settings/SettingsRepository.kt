package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound

/** Reads a stored enum name, falling back to [fallback] when the value is absent or unrecognised. */
private inline fun <reified E : Enum<E>> String?.toEnumOr(fallback: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

class SettingsRepository(private val store: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> =
        store.data.map { it[SettingsKeys.THEME].toEnumOr(ThemeMode.SYSTEM) }

    val onboardingComplete: Flow<Boolean> =
        store.data.map { it[SettingsKeys.ONBOARDED] ?: false }

    val prayerSettings: Flow<PrayerSettings> = store.data.map { p ->
        PrayerSettings(
            method = p[SettingsKeys.METHOD].toEnumOr(CalculationMethodId.MUSLIM_WORLD_LEAGUE),
            madhab = p[SettingsKeys.MADHAB].toEnumOr(AsrMadhab.STANDARD),
            highLatitude = p[SettingsKeys.HIGH_LAT].toEnumOr(HighLatitudePreference.AUTOMATIC),
            hijriOffsetDays = p[SettingsKeys.HIJRI_OFFSET] ?: 0,
            showSunrise = p[SettingsKeys.SHOW_SUNRISE] ?: false,
        )
    }

    val notificationSettings: Flow<NotificationSettings> = store.data.map { p ->
        NotificationSettings(
            enabled = p[SettingsKeys.NOTIFICATIONS_ENABLED] ?: true,
            sounds = ObligatoryPrayers.associateWith { prayer ->
                p[SettingsKeys.soundKey(prayer)].toEnumOr(PrayerSound.TAKBIR)
            },
            remindBeforeMinutes = p[SettingsKeys.REMIND_BEFORE] ?: 0,
        )
    }

    val location: Flow<GeoLocation?> = store.data.map { p ->
        val lat = p[SettingsKeys.LOCATION_LAT]
        val lon = p[SettingsKeys.LOCATION_LON]
        val tz = p[SettingsKeys.LOCATION_TZ]
        if (lat == null || lon == null || tz == null) null
        else GeoLocation(lat, lon, tz, p[SettingsKeys.LOCATION_CITY], p[SettingsKeys.LOCATION_COUNTRY])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[SettingsKeys.THEME] = mode.name }
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[SettingsKeys.ONBOARDED] = value }
    }

    suspend fun setPrayerSettings(settings: PrayerSettings) {
        store.edit {
            it[SettingsKeys.METHOD] = settings.method.name
            it[SettingsKeys.MADHAB] = settings.madhab.name
            it[SettingsKeys.HIGH_LAT] = settings.highLatitude.name
            it[SettingsKeys.HIJRI_OFFSET] = settings.hijriOffsetDays
            it[SettingsKeys.SHOW_SUNRISE] = settings.showSunrise
        }
    }

    suspend fun setNotificationSettings(settings: NotificationSettings) {
        store.edit { e ->
            e[SettingsKeys.NOTIFICATIONS_ENABLED] = settings.enabled
            ObligatoryPrayers.forEach { prayer ->
                e[SettingsKeys.soundKey(prayer)] = settings.soundFor(prayer).name
            }
            e[SettingsKeys.REMIND_BEFORE] = settings.remindBeforeMinutes
        }
    }

    suspend fun setLocation(location: GeoLocation) {
        store.edit {
            it[SettingsKeys.LOCATION_LAT] = location.latitude
            it[SettingsKeys.LOCATION_LON] = location.longitude
            it[SettingsKeys.LOCATION_TZ] = location.timeZoneId
            location.cityName?.let { n -> it[SettingsKeys.LOCATION_CITY] = n }
            location.countryCode?.let { c -> it[SettingsKeys.LOCATION_COUNTRY] = c }
        }
    }

    /** Test-only hook for the forward-compatibility case. */
    internal suspend fun writeRawThemeForTest(raw: String) {
        store.edit { it[SettingsKeys.THEME] = raw }
    }

    /** Test-only hook for the forward-compatibility case, matching [writeRawThemeForTest]. */
    internal suspend fun writeRawSoundForTest(prayer: Prayer, raw: String) {
        store.edit { it[SettingsKeys.soundKey(prayer)] = raw }
    }
}
