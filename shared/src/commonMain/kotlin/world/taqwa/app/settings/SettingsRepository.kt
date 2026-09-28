package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AdhanVoice
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
import world.taqwa.app.quran.ReadingMode
import world.taqwa.app.quran.ReadingPosition
import world.taqwa.app.quran.ReadingSettings
import kotlin.math.round
import world.taqwa.app.recitation.RecitationManifest
import world.taqwa.app.recitation.RecitationSettings

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

/**
 * Same shape as [encodeMinuteAdjustments]/[decodeMinuteAdjustments] but the value is a registry
 * entry id (e.g. `"MAGHRIB:other.turkey"`) rather than a minute count — see
 * [PrayerSettings.confirmedAdjustments].
 */
internal fun encodeConfirmedAdjustments(confirmed: Map<Prayer, String>): String =
    confirmed.entries.joinToString(",") { "${it.key.name}:${it.value}" }

internal fun decodeConfirmedAdjustments(raw: String?): Map<Prayer, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(",").mapNotNull { entry ->
        val name = entry.substringBefore(':', missingDelimiterValue = "")
        val prayer = Prayer.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        val id = entry.substringAfter(':', missingDelimiterValue = "")
        if (id.isBlank()) return@mapNotNull null
        prayer to id
    }.toMap()
}

/**
 * Spec §8's method migration, run once per install by
 * [SettingsRepository.migratePrayerSettingsIfNeeded] (called transactionally from every setter
 * that touches a migrated field, and from the read path via
 * [SettingsRepository.migrateIfNeeded]). A method the user never chose always becomes
 * `"automatic"` — today's app writes a country
 * default on every location save, so an unchosen stored method carries no signal at all. A method
 * the user *did* choose becomes `"automatic"` only when it is the authority's own stand-in for the
 * country the location was last resolved to (TEHRAN always, since it is being removed from the
 * registry outright); anywhere else it becomes the matching Other entry, per R12's fixed mapping.
 */
internal fun migratedTimetable(
    method: CalculationMethodId,
    userChosen: Boolean,
    countryCode: String?,
): String {
    if (!userChosen) return "automatic"
    val country = countryCode?.uppercase()
    return when (method) {
        CalculationMethodId.TEHRAN -> "automatic"
        CalculationMethodId.SINGAPORE ->
            if (country in setOf("SG", "MY", "ID", "BN")) "automatic" else "other.singapore"
        CalculationMethodId.KARACHI -> if (country == "BD") "automatic" else "other.karachi"
        CalculationMethodId.TURKEY -> if (country == "TR") "automatic" else "other.turkey"
        CalculationMethodId.UMM_AL_QURA -> if (country == "SA") "automatic" else "other.ummalqura"
        CalculationMethodId.EGYPTIAN -> if (country == "EG") "automatic" else "other.egyptian"
        CalculationMethodId.DUBAI -> if (country == "AE") "automatic" else "other.dubai"
        CalculationMethodId.KUWAIT -> if (country == "KW") "automatic" else "other.kuwait"
        CalculationMethodId.QATAR -> if (country == "QA") "automatic" else "other.qatar"
        CalculationMethodId.ISNA -> if (country in setOf("US", "CA")) "automatic" else "other.isna"
        CalculationMethodId.MUSLIM_WORLD_LEAGUE -> "other.mwl"
        CalculationMethodId.MOONSIGHTING_COMMITTEE -> "other.moonsighting"
    }
}

/**
 * The stored city's name as the header last printed it, with the interface language it was
 * resolved in. Kept together because neither half means anything alone: a name is only safe to
 * show a reader whose language is the one it was resolved for.
 */
data class ResolvedCityName(val name: String, val languageTag: String)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> =
        store.data.map { it[SettingsKeys.THEME].toEnumOr(ThemeMode.SYSTEM) }

    val onboardingComplete: Flow<Boolean> =
        store.data.map { it[SettingsKeys.ONBOARDED] ?: false }

    val prayerSettings: Flow<PrayerSettings> = store.data.map { p ->
        val migrated = migrateIfNeeded(p)
        val timetable = migrated[SettingsKeys.PRAYER_TIMETABLE] ?: "automatic"
        // Ruling R70: a confirmation holds with the Automatic entry it was given under; one stored
        // without it (before R70) counts as not given, so the choice waits to be confirmed again.
        val confirmedUnder = migrated[SettingsKeys.PRAYER_TIMETABLE_CONFIRMED_UNDER]
            .takeIf { migrated[SettingsKeys.PRAYER_TIMETABLE_CONFIRMED] == timetable }
        PrayerSettings(
            // Clamped to the range the settings screen offers, like every other stored value here:
            // a preferences file from elsewhere must not be able to shift the date by a year.
            hijriOffsetDays = (migrated[SettingsKeys.HIJRI_OFFSET] ?: 0).coerceIn(-1, 1),
            showSunrise = migrated[SettingsKeys.SHOW_SUNRISE] ?: false,
            minuteAdjustments = decodeMinuteAdjustments(migrated[SettingsKeys.MINUTE_ADJUSTMENTS]),
            timetable = timetable,
            timetableConfirmed = confirmedUnder != null,
            timetableConfirmedUnder = confirmedUnder,
            school = migrated[SettingsKeys.PRAYER_SCHOOL] ?: "automatic",
            showBothAsr = migrated[SettingsKeys.PRAYER_SHOW_BOTH_ASR] ?: false,
            showWhereDiffer = migrated[SettingsKeys.PRAYER_SHOW_WHERE_DIFFER] ?: false,
            saudiFajrLater = migrated[SettingsKeys.PRAYER_SAUDI_FAJR_LATER] ?: false,
            confirmedAdjustments = decodeConfirmedAdjustments(migrated[SettingsKeys.PRAYER_ADJUST_CONFIRMED]),
            // Spec §8: kept for Other methods, ignored (null) while Automatic.
            legacyHighLatitude = if (timetable == "automatic") null
            else migrated[SettingsKeys.HIGH_LAT].toEnumOr(HighLatitudePreference.AUTOMATIC),
        )
    }

    /** Once-only, once-migrated flags for the Sunni and cautious-times cards (spec §2.1). */
    val sunniCardSeen: Flow<Boolean> =
        store.data.map { it[SettingsKeys.PRAYER_CARD_SUNNI_SEEN] ?: false }

    val cautiousCardSeen: Flow<Boolean> =
        store.data.map { it[SettingsKeys.PRAYER_CARD_CAUTIOUS_SEEN] ?: false }

    val notificationSettings: Flow<NotificationSettings> = store.data.map { p ->
        NotificationSettings(
            enabled = p[SettingsKeys.NOTIFICATIONS_ENABLED] ?: true,
            sounds = ObligatoryPrayers.associateWith { prayer ->
                p[SettingsKeys.soundKey(prayer)].toEnumOr(PrayerSound.TAKBIR)
            },
            remindBeforeMinutes = p[SettingsKeys.REMIND_BEFORE] ?: 0,
            voice = p[SettingsKeys.ADHAN_VOICE].toEnumOr(AdhanVoice.ORIGINAL),
            tahajjud = p[SettingsKeys.TAHAJJUD_ENABLED] ?: false,
            // A stored level Tahajjud does not offer — a hand-edited file — is the default again.
            tahajjudSound = p[SettingsKeys.TAHAJJUD_SOUND].toEnumOr(PrayerSound.NOTIFICATION)
                .takeIf { it in NotificationSettings.TahajjudSounds } ?: PrayerSound.NOTIFICATION,
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
            region = p[SettingsKeys.LOCATION_REGION],
        )
    }

    /**
     * Whether the one-time backfill of the stored location's city facts ([backfillLocation]) has
     * run for it: a city the list gives no region leaves the location without one for good.
     */
    val locationBackfilled: Flow<Boolean> =
        store.data.map { it[SettingsKeys.LOCATION_BACKFILLED] ?: false }

    /**
     * What the header printed last time, and in which language — null on a fresh install, after a
     * relocation that has not been resolved yet, and for a build older than this one. Read once
     * per run, on the Prayer screen's first refresh, so the name is on the first frame instead of
     * arriving ~0.4 s later behind the 1.6 MB city-list parse.
     */
    val resolvedCityName: Flow<ResolvedCityName?> = store.data.map { p ->
        val name = p[SettingsKeys.LOCATION_CITY_DISPLAY_NAME]
        val language = p[SettingsKeys.LOCATION_CITY_DISPLAY_LANGUAGE]
        if (name == null || language == null) null else ResolvedCityName(name, language)
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

    /** The mode alone, leaving size, transliteration and translation as they are. */
    suspend fun setReadingMode(mode: ReadingMode) {
        store.edit { it[SettingsKeys.QURAN_MODE] = mode.name }
    }

    /** The translation alone, by its id ("fr.hamidullah"). */
    suspend fun setTranslation(id: String) {
        store.edit { it[SettingsKeys.QURAN_TRANSLATION] = id }
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

    /**
     * The recitation preferences (spec 3a §12.1, §12.6): the voice, and whether a surah may be
     * fetched over mobile data. Both absent on a fresh install, which is what makes Alafasy and
     * Wi-Fi-only the defaults rather than something written at first launch.
     */
    val recitationSettings: Flow<RecitationSettings> = store.data.map { p ->
        RecitationSettings(
            reciterId = p[SettingsKeys.RECITATION_RECITER] ?: RecitationManifest.DEFAULT_RECITER,
            downloadOnMobileData = p[SettingsKeys.RECITATION_MOBILE_DATA] ?: false,
            autoDownload = p[SettingsKeys.RECITATION_AUTO_DOWNLOAD] ?: false,
            autoDownloadAsked = p[SettingsKeys.RECITATION_AUTO_DOWNLOAD_ASKED] ?: false,
            readAloud = p[SettingsKeys.RECITATION_READ_ALOUD] ?: false,
        )
    }

    /**
     * Spec §15.3, written by the download sheet's confirm and by the Settings toggle. Either
     * counts as having been asked, so the sheet stops pre-ticking the box once a choice exists.
     */
    suspend fun setRecitationAutoDownload(value: Boolean) {
        store.edit {
            it[SettingsKeys.RECITATION_AUTO_DOWNLOAD] = value
            it[SettingsKeys.RECITATION_AUTO_DOWNLOAD_ASKED] = true
        }
    }

    suspend fun setRecitationSettings(settings: RecitationSettings) {
        store.edit {
            it[SettingsKeys.RECITATION_RECITER] = settings.reciterId
            it[SettingsKeys.RECITATION_MOBILE_DATA] = settings.downloadOnMobileData
        }
    }

    /**
     * The picker's own write. Separate from [setRecitationSettings] because picking a voice must
     * not also re-assert the mobile-data switch: the two are changed from different screens, and
     * a read-modify-write of the pair from the picker would race the Settings toggle.
     */
    suspend fun setRecitationReciter(reciterId: String) {
        store.edit { it[SettingsKeys.RECITATION_RECITER] = reciterId }
    }

    /** Settings › Recitation's "Download over mobile data" (spec §5.6, §12.6). Its own write for
     * the same reason [setRecitationReciter] is: the two are changed from different surfaces. */
    suspend fun setRecitationMobileData(value: Boolean) {
        store.edit { it[SettingsKeys.RECITATION_MOBILE_DATA] = value }
    }

    /** Read-aloud's switch (read-aloud spec §6), from the reading sheet or Settings › Recitation. */
    suspend fun setRecitationReadAloud(value: Boolean) {
        store.edit { it[SettingsKeys.RECITATION_READ_ALOUD] = value }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[SettingsKeys.THEME] = mode.name }
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[SettingsKeys.ONBOARDED] = value }
    }

    /** [PrayerSettings.hijriOffsetDays] alone, clamped to the range the screen offers. */
    suspend fun setHijriOffsetDays(days: Int) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.HIJRI_OFFSET] = days.coerceIn(-1, 1)
        }
    }

    suspend fun setShowSunrise(value: Boolean) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.SHOW_SUNRISE] = value
        }
    }

    /** [PrayerSettings.minuteAdjustments] alone — written by Manual adjustments. */
    suspend fun setMinuteAdjustments(adjustments: Map<Prayer, Int>) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.MINUTE_ADJUSTMENTS] = encodeMinuteAdjustments(adjustments)
        }
    }

    /**
     * The high-latitude rule for the Other methods ([PrayerSettings.legacyHighLatitude]), stored
     * under the old picker's key, which is what the migration keeps for them (spec §8).
     */
    suspend fun setLegacyHighLatitude(preference: HighLatitudePreference) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.HIGH_LAT] = preference.name
        }
    }

    /**
     * The spec §8 derivation, callable from *inside* any `store.edit` transform — every setter
     * below calls this before writing its own field, and so does the read path's
     * [migrateIfNeeded]. The schema check happens against `this` — the live `MutablePreferences`
     * DataStore hands the transform, which always reflects every previously *committed* edit
     * (`androidx.datastore.core.DataStore` serialises `edit` calls one at a time; nothing here
     * runs against a stale snapshot) — so no matter which caller's `store.edit` is the one that
     * actually gets serialised first, exactly one of them sees schema 0 and derives, and every
     * other one (including a concurrent one) sees the schema already bumped and no-ops here,
     * before going on to write its own field regardless. This is what review round 1 found
     * missing: the previous version checked the schema *outside* the transaction (against a
     * caller's own stale read) and had four setters merely stamp the schema without ever
     * deriving, so a switch flipped before the first-ever [prayerSettings] read could permanently
     * strand a user's real historic method as Automatic.
     */
    private fun MutablePreferences.migratePrayerSettingsIfNeeded() {
        val schema = this[SettingsKeys.PRAYER_SETTINGS_SCHEMA] ?: 0
        if (schema >= PRAYER_SETTINGS_SCHEMA_VERSION) return
        val method = this[SettingsKeys.METHOD].toEnumOr(CalculationMethodId.MUSLIM_WORLD_LEAGUE)
        val userChosen = this[SettingsKeys.METHOD_USER_CHOSEN] ?: false
        val country = this[SettingsKeys.LOCATION_COUNTRY]
        this[SettingsKeys.PRAYER_TIMETABLE] = migratedTimetable(method, userChosen, country)
        val madhab = this[SettingsKeys.MADHAB].toEnumOr(AsrMadhab.STANDARD)
        this[SettingsKeys.PRAYER_SCHOOL] = if (madhab == AsrMadhab.HANAFI) "hanafi" else "automatic"
        // Negative adjustments (spec §8's "kept overrides that begin earlier") are left exactly
        // where they were — `minute_adjustments` is untouched here — and `prayer_adjust_confirmed`
        // is left unset, which decodeConfirmedAdjustments reads as empty: every prayer starts
        // paused until re-confirmed in the new Settings screens, never silently pre-confirmed by
        // the migration itself.
        this[SettingsKeys.PRAYER_SETTINGS_SCHEMA] = PRAYER_SETTINGS_SCHEMA_VERSION
    }

    /** [PrayerSettings.timetable] alone — written by the Timetable row and Match my mosque. */
    suspend fun setTimetable(timetable: String) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.PRAYER_TIMETABLE] = timetable
        }
    }

    /**
     * Records that the user confirmed following [entryId] (the Timetable screen's warning, spec
     * §2.2) at a place whose Automatic entry is [automaticId] (ruling R70), or clears it with null.
     * [PrayerSettings.timetableConfirmed] holds while the stored timetable is this entry, so
     * choosing another one needs its own confirmation, and applies where Automatic is still
     * [automaticId].
     */
    suspend fun setTimetableConfirmed(entryId: String?, automaticId: String?) {
        store.edit { it.confirm(if (automaticId == null) null else entryId, automaticId) }
    }

    /**
     * The Timetable screen's and Match my mosque's choice in one write (spec §2.2, ruling R52):
     * [timetable], confirmed at a place whose Automatic entry is [confirmedUnder] when that is
     * given (the earlier-than check found nothing, or the user chose "Follow it"; ruling R70),
     * else with no confirmation, so no reader ever sees the new timetable under an old one's
     * confirmation or the other way round.
     */
    suspend fun chooseTimetable(timetable: String, confirmedUnder: String?) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.PRAYER_TIMETABLE] = timetable
            it.confirm(if (confirmedUnder == null) null else timetable, confirmedUnder)
        }
    }

    /** A confirmation of [entryId] under the Automatic entry [automaticId], both or neither (ruling R70). */
    private fun MutablePreferences.confirm(entryId: String?, automaticId: String?) {
        migratePrayerSettingsIfNeeded()
        if (entryId == null || automaticId == null) {
            remove(SettingsKeys.PRAYER_TIMETABLE_CONFIRMED)
            remove(SettingsKeys.PRAYER_TIMETABLE_CONFIRMED_UNDER)
        } else {
            this[SettingsKeys.PRAYER_TIMETABLE_CONFIRMED] = entryId
            this[SettingsKeys.PRAYER_TIMETABLE_CONFIRMED_UNDER] = automaticId
        }
    }

    /**
     * One prayer's minute adjustment and, for an earlier one, the entry it was confirmed under
     * (Manual adjustments' "Use −2 min", spec §2.2), in one write. [confirmedUnder] null leaves the
     * prayer's confirmation as it was.
     */
    suspend fun setMinuteAdjustment(prayer: Prayer, minutes: Int, confirmedUnder: String?) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            val adjustments = decodeMinuteAdjustments(it[SettingsKeys.MINUTE_ADJUSTMENTS]).toMutableMap()
            if (minutes == 0) adjustments.remove(prayer) else adjustments[prayer] = minutes
            it[SettingsKeys.MINUTE_ADJUSTMENTS] = encodeMinuteAdjustments(adjustments)
            if (confirmedUnder != null) {
                val confirmed = decodeConfirmedAdjustments(it[SettingsKeys.PRAYER_ADJUST_CONFIRMED]) + (prayer to confirmedUnder)
                it[SettingsKeys.PRAYER_ADJUST_CONFIRMED] = encodeConfirmedAdjustments(confirmed)
            }
        }
    }

    /** [PrayerSettings.school] alone — written by the Asr row. */
    suspend fun setSchool(school: String) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.PRAYER_SCHOOL] = school
        }
    }

    suspend fun setShowBothAsr(value: Boolean) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.PRAYER_SHOW_BOTH_ASR] = value
        }
    }

    suspend fun setShowWhereDiffer(value: Boolean) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.PRAYER_SHOW_WHERE_DIFFER] = value
        }
    }

    /** Saudi Arabia's "Pray Fajr 5 minutes later" (spec §2.2), off by default. */
    suspend fun setSaudiFajrLater(value: Boolean) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.PRAYER_SAUDI_FAJR_LATER] = value
        }
    }

    /** [PrayerSettings.confirmedAdjustments] alone — written by the manual-adjustment dialog. */
    suspend fun setConfirmedAdjustments(confirmed: Map<Prayer, String>) {
        store.edit {
            it.migratePrayerSettingsIfNeeded()
            it[SettingsKeys.PRAYER_ADJUST_CONFIRMED] = encodeConfirmedAdjustments(confirmed)
        }
    }

    suspend fun setSunniCardSeen(value: Boolean) {
        store.edit { it[SettingsKeys.PRAYER_CARD_SUNNI_SEEN] = value }
    }

    suspend fun setCautiousCardSeen(value: Boolean) {
        store.edit { it[SettingsKeys.PRAYER_CARD_CAUTIOUS_SEEN] = value }
    }

    /**
     * The read path's own entry into [migratePrayerSettingsIfNeeded]. [current] — the
     * [Preferences] [prayerSettings]'s own `map` was just handed by `store.data` — is used only as
     * a cheap outer guard to skip `store.edit` entirely on the overwhelmingly common
     * already-migrated case; it can never cause a *missed* migration, because the schema key only
     * ever counts up, so a stale "not yet migrated" reading here is always safe to act on (the
     * inner, transactional check inside [migratePrayerSettingsIfNeeded] is what actually decides,
     * against live state, whether to derive) and a stale "already migrated" reading can never
     * happen. `DataStore.edit` returns the post-edit [Preferences], so on the rare occasion this
     * does take the slow path, the same collection reads the migrated values immediately, with no
     * extra round trip through `store.data`.
     */
    private suspend fun migrateIfNeeded(current: Preferences): Preferences {
        val schema = current[SettingsKeys.PRAYER_SETTINGS_SCHEMA] ?: 0
        if (schema >= PRAYER_SETTINGS_SCHEMA_VERSION) return current
        return store.edit { it.migratePrayerSettingsIfNeeded() }
    }

    companion object {
        /** Bump when [migratePrayerSettingsIfNeeded]'s derivation changes, so it reruns. */
        internal const val PRAYER_SETTINGS_SCHEMA_VERSION = 3
    }

    suspend fun setNotificationSettings(settings: NotificationSettings) {
        store.edit { e ->
            e[SettingsKeys.NOTIFICATIONS_ENABLED] = settings.enabled
            ObligatoryPrayers.forEach { prayer ->
                e[SettingsKeys.soundKey(prayer)] = settings.soundFor(prayer).name
            }
            e[SettingsKeys.REMIND_BEFORE] = settings.remindBeforeMinutes
            e[SettingsKeys.ADHAN_VOICE] = settings.voice.name
            e[SettingsKeys.TAHAJJUD_ENABLED] = settings.tahajjud
            e[SettingsKeys.TAHAJJUD_SOUND] = settings.tahajjudSound.name
        }
    }

    suspend fun setWidgetBackground(value: WidgetBackground) {
        store.edit { it[SettingsKeys.WIDGET_BACKGROUND] = value.name }
    }

    /**
     * Stores a location, and with it the name its city reads as — [resolved] — when the caller
     * already knows it. The city picker does: the row the user tapped was already rendered in
     * their language, so the header can print that name on the very next launch without looking
     * anything up. Every other caller (a GPS fix, a relocation) passes null and the two keys are
     * **cleared**, exactly as the id is: a name left behind by the previous city would caption
     * the new coordinates with the old city's label.
     */
    suspend fun setLocation(location: GeoLocation, resolved: ResolvedCityName? = null) {
        store.edit {
            if (resolved == null) {
                it.remove(SettingsKeys.LOCATION_CITY_DISPLAY_NAME)
                it.remove(SettingsKeys.LOCATION_CITY_DISPLAY_LANGUAGE)
            } else {
                it[SettingsKeys.LOCATION_CITY_DISPLAY_NAME] = resolved.name
                it[SettingsKeys.LOCATION_CITY_DISPLAY_LANGUAGE] = resolved.languageTag
            }
            // Three decimals, about 110 m: prayer times and the qibla bearing do not move at that
            // scale, and a GPS fix's metre precision is not something the file should hold.
            it[SettingsKeys.LOCATION_LAT] = location.latitude.roundedTo3dp()
            it[SettingsKeys.LOCATION_LON] = location.longitude.roundedTo3dp()
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
            val region = location.region
            if (region == null) it.remove(SettingsKeys.LOCATION_REGION) else it[SettingsKeys.LOCATION_REGION] = region
            it.remove(SettingsKeys.LOCATION_BACKFILLED)
        }
    }

    /**
     * Fills in what a location stored by an older build lacks — its city id, country and region —
     * from its city, leaving everything it already has alone. Only while the stored coordinates are
     * still [location]'s: the lookup behind it takes a moment, and a city picked or a fix taken
     * meanwhile must not be captioned with the old city.
     *
     * True when it wrote the country or the region, which can change the timetable the prayer
     * engine resolves, so what was scheduled with the old times needs refreshing (ruling R53).
     */
    suspend fun backfillLocation(location: GeoLocation, cityId: Int, countryCode: String, region: String): Boolean {
        var timesMayChange = false
        store.edit {
            val unchanged = it[SettingsKeys.LOCATION_LAT] == location.latitude &&
                it[SettingsKeys.LOCATION_LON] == location.longitude
            if (unchanged) {
                it[SettingsKeys.LOCATION_BACKFILLED] = true
                if (it[SettingsKeys.LOCATION_CITY_ID] == null) it[SettingsKeys.LOCATION_CITY_ID] = cityId
                if (it[SettingsKeys.LOCATION_COUNTRY] == null) {
                    it[SettingsKeys.LOCATION_COUNTRY] = countryCode
                    timesMayChange = true
                }
                if (it[SettingsKeys.LOCATION_REGION] == null && region.isNotEmpty()) {
                    it[SettingsKeys.LOCATION_REGION] = region
                    timesMayChange = true
                }
            }
        }
        return timesMayChange
    }

    /**
     * Records the name the header is now showing, for the language it was resolved in. Written by
     * the one place that resolves it — `TodayViewModel` — and only when the answer or the language
     * has actually changed: the Prayer screen ticks once a second and this must not be a write per
     * tick. Leaves the rest of the location alone for the same reason [backfillLocation] does.
     */
    suspend fun setResolvedCityName(resolved: ResolvedCityName) {
        store.edit {
            it[SettingsKeys.LOCATION_CITY_DISPLAY_NAME] = resolved.name
            it[SettingsKeys.LOCATION_CITY_DISPLAY_LANGUAGE] = resolved.languageTag
        }
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

    private fun Double.roundedTo3dp(): Double = round(this * 1000.0) / 1000.0

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

    /** Test-only hook for the forward-compatibility case, matching [writeRawThemeForTest]. */
    internal suspend fun writeRawAdhanVoiceForTest(raw: String) {
        store.edit { it[SettingsKeys.ADHAN_VOICE] = raw }
    }
}
