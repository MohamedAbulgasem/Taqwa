package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.PrayerSettings

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
            remindBeforeMinutes = p[SettingsKeys.REMIND_BEFORE] ?: 0,
        )
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
            it[SettingsKeys.REMIND_BEFORE] = settings.remindBeforeMinutes
        }
    }

    /** Test-only hook for the forward-compatibility case. */
    internal suspend fun writeRawThemeForTest(raw: String) {
        store.edit { it[SettingsKeys.THEME] = raw }
    }
}
