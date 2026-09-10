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
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.domain.NotificationSettings
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.prayer.CalculationMethodDefaults
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings

/** Reads a stored enum name, falling back to [fallback] when the value is absent or unrecognised. */
private inline fun <reified E : Enum<E>> String?.toEnumOr(fallback: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

/**
 * Per-prayer minute offsets serialise as `"FAJR:5,ISHA:-3"` — one preference, read and written as
 * a unit. Parsing is deliberately tolerant in the same spirit as [toEnumOr]: an entry naming a
 * prayer this build does not know, or carrying a value that is not an integer, is skipped rather
 * than thrown, so a preference file written by another version can never stop the app starting.
 */
internal fun encodeMinuteAdjustments(adjustments: Map<Prayer, Int>): String =
    adjustments.entries
        .filter { it.value != 0 }
        .joinToString(",") { "${it.key.name}:${it.value}" }

internal fun decodeMinuteAdjustments(raw: String?): Map<Prayer, Int> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(",").mapNotNull { entry ->
        val name = entry.substringBefore(':', missingDelimiterValue = "")
        val prayer = Prayer.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        val minutes = entry.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
        prayer to minutes
    }.toMap()
}

class SettingsRepository(private val store: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> =
        store.data.map { it[SettingsKeys.THEME].toEnumOr(ThemeMode.SYSTEM) }

    val onboardingComplete: Flow<Boolean> =
        store.data.map { it[SettingsKeys.ONBOARDED] ?: false }

    /** True once the user has picked a calculation method by hand. See [applyCountryDefaultMethod]. */
    val methodUserChosen: Flow<Boolean> =
        store.data.map { it[SettingsKeys.METHOD_USER_CHOSEN] ?: false }

    val prayerSettings: Flow<PrayerSettings> = store.data.map { p ->
        PrayerSettings(
            method = p[SettingsKeys.METHOD].toEnumOr(CalculationMethodId.MUSLIM_WORLD_LEAGUE),
            madhab = p[SettingsKeys.MADHAB].toEnumOr(AsrMadhab.STANDARD),
            highLatitude = p[SettingsKeys.HIGH_LAT].toEnumOr(HighLatitudePreference.AUTOMATIC),
            hijriOffsetDays = p[SettingsKeys.HIJRI_OFFSET] ?: 0,
            showSunrise = p[SettingsKeys.SHOW_SUNRISE] ?: false,
            minuteAdjustments = decodeMinuteAdjustments(p[SettingsKeys.MINUTE_ADJUSTMENTS]),
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

    val widgetBackground: Flow<WidgetBackground> =
        store.data.map { it[SettingsKeys.WIDGET_BACKGROUND].toEnumOr(WidgetBackground.FOLLOW_THEME) }

    val location: Flow<GeoLocation?> = store.data.map { p ->
        val lat = p[SettingsKeys.LOCATION_LAT]
        val lon = p[SettingsKeys.LOCATION_LON]
        val tz = p[SettingsKeys.LOCATION_TZ]
        if (lat == null || lon == null || tz == null) null
        else GeoLocation(
            latitude = lat,
            longitude = lon,
            timeZoneId = tz,
            cityName = p[SettingsKeys.LOCATION_CITY],
            countryCode = p[SettingsKeys.LOCATION_COUNTRY],
            cityId = p[SettingsKeys.LOCATION_CITY_ID],
        )
    }

    /**
     * Manual until something says otherwise: a user who has never granted the permission, or who
     * upgrades from a build that did not store this, has not turned "Use my location" on.
     */
    val locationSource: Flow<LocationSource> =
        store.data.map { it[SettingsKeys.LOCATION_SOURCE].toEnumOr(LocationSource.MANUAL) }

    /**
     * The reader's preferences: [ReadingSettings.defaultsFor] the device language, with each key
     * that has actually been stored overriding its own default individually — a user who has
     * only ever touched the size slider keeps the language-appropriate mode and translation.
     */
    fun readingSettings(defaultLanguageTag: String): Flow<ReadingSettings> = store.data.map { p ->
        val defaults = ReadingSettings.defaultsFor(defaultLanguageTag)
        ReadingSettings(
            mode = p[SettingsKeys.QURAN_MODE].toEnumOr(defaults.mode),
            arabicSizeSp = p[SettingsKeys.QURAN_SIZE] ?: defaults.arabicSizeSp,
            transliteration = p[SettingsKeys.QURAN_TRANSLITERATION] ?: defaults.transliteration,
            translationId = p[SettingsKeys.QURAN_TRANSLATION] ?: defaults.translationId,
        ).clamped()
    }

    suspend fun setReadingSettings(settings: ReadingSettings) {
        store.edit {
            it[SettingsKeys.QURAN_MODE] = settings.mode.name
            it[SettingsKeys.QURAN_SIZE] = settings.arabicSizeSp
            it[SettingsKeys.QURAN_TRANSLITERATION] = settings.transliteration
            it[SettingsKeys.QURAN_TRANSLATION] = settings.translationId
        }
    }

    /** Null until all three position keys exist — a fresh install has nowhere to resume to. */
    val readingPosition: Flow<ReadingPosition?> = store.data.map { p ->
        val surah = p[SettingsKeys.QURAN_LAST_SURAH]
        val ayah = p[SettingsKeys.QURAN_LAST_AYAH]
        val page = p[SettingsKeys.QURAN_LAST_PAGE]
        if (surah == null || ayah == null || page == null) null else ReadingPosition(surah, ayah, page)
    }

    suspend fun setReadingPosition(position: ReadingPosition) {
        store.edit {
            it[SettingsKeys.QURAN_LAST_SURAH] = position.surah
            it[SettingsKeys.QURAN_LAST_AYAH] = position.ayah
            it[SettingsKeys.QURAN_LAST_PAGE] = position.page
        }
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
            it[SettingsKeys.MINUTE_ADJUSTMENTS] = encodeMinuteAdjustments(settings.minuteAdjustments)
        }
    }

    /**
     * Records that the method now stored was chosen by the user, not derived from their country.
     * Written by the method picker alone — [setPrayerSettings] deliberately does not set it, since
     * every other control on the prayer-times screen also writes the whole [PrayerSettings].
     */
    suspend fun setMethodUserChosen() {
        store.edit { it[SettingsKeys.METHOD_USER_CHOSEN] = true }
    }

    /**
     * Applies the calculation method a user in [countryCode] is most likely to expect, unless they
     * have already chosen one themselves. Called wherever a location is persisted: an unchosen
     * method should follow the user to Riyadh or Istanbul, but a deliberate choice must survive
     * the move. Returns the method now in force.
     */
    suspend fun applyCountryDefaultMethod(countryCode: String?): CalculationMethodId {
        val default = CalculationMethodDefaults.forCountry(countryCode)
        var applied = default
        store.edit { p ->
            if (p[SettingsKeys.METHOD_USER_CHOSEN] == true) {
                applied = p[SettingsKeys.METHOD].toEnumOr(CalculationMethodId.MUSLIM_WORLD_LEAGUE)
            } else {
                p[SettingsKeys.METHOD] = default.name
            }
        }
        return applied
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

    suspend fun setWidgetBackground(value: WidgetBackground) {
        store.edit { it[SettingsKeys.WIDGET_BACKGROUND] = value.name }
    }

    suspend fun setLocation(location: GeoLocation) {
        store.edit {
            it[SettingsKeys.LOCATION_LAT] = location.latitude
            it[SettingsKeys.LOCATION_LON] = location.longitude
            it[SettingsKeys.LOCATION_TZ] = location.timeZoneId
            // Cleared, not skipped, when absent: a GPS fix has no city name, and leaving the
            // previously chosen city's label in place would caption the new coordinates with
            // the old city's name.
            val city = location.cityName
            if (city == null) it.remove(SettingsKeys.LOCATION_CITY) else it[SettingsKeys.LOCATION_CITY] = city
            val country = location.countryCode
            if (country == null) it.remove(SettingsKeys.LOCATION_COUNTRY) else it[SettingsKeys.LOCATION_COUNTRY] = country
            // Cleared for the same reason the name is: a fix that landed nowhere near a bundled
            // city must not keep the previous city's id, or the header would name a city the
            // coordinates are no longer in.
            val cityId = location.cityId
            if (cityId == null) it.remove(SettingsKeys.LOCATION_CITY_ID) else it[SettingsKeys.LOCATION_CITY_ID] = cityId
        }
    }

    /**
     * Writes only the city id, leaving every other part of the stored location alone. Used by the
     * one-time migration of a location saved before ids existed: it read the location some time
     * ago, and rewriting the whole thing would undo a fix or a city pick that landed in between.
     */
    suspend fun setLocationCityId(cityId: Int) {
        store.edit { it[SettingsKeys.LOCATION_CITY_ID] = cityId }
    }

    /**
     * Recorded by every path that persists a location: a fix writes [LocationSource.GPS], the
     * city list writes [LocationSource.MANUAL]. Kept separate from [setLocation] because turning
     * the toggle off only becomes real once a city is actually picked — a cancelled search must
     * leave both the location and the source alone.
     */
    suspend fun setLocationSource(source: LocationSource) {
        store.edit { it[SettingsKeys.LOCATION_SOURCE] = source.name }
    }

    /** Test-only hook for the forward-compatibility case. */
    internal suspend fun writeRawThemeForTest(raw: String) {
        store.edit { it[SettingsKeys.THEME] = raw }
    }

    /** Test-only hook for the forward-compatibility case, matching [writeRawThemeForTest]. */
    internal suspend fun writeRawSoundForTest(prayer: Prayer, raw: String) {
        store.edit { it[SettingsKeys.soundKey(prayer)] = raw }
    }

    /** Test-only hook for the forward-compatibility case, matching [writeRawThemeForTest]. */
    internal suspend fun writeRawMinuteAdjustmentsForTest(raw: String) {
        store.edit { it[SettingsKeys.MINUTE_ADJUSTMENTS] = raw }
    }
}
