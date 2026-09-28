package world.taqwa.app.prayer

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.SchoolChoice
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.registry.Place

/**
 * The app's door to the never-early engine ([PrayerEngine], spec §3): the Prayer screen, the
 * notifications, the widget mirror, Manual adjustments and the website all ask here, with the
 * stored location and settings, and get the same times.
 *
 * Holds no state: the engine's one bounded, thread-safe cache is shared by every instance, so a
 * receiver's fresh instance on `Dispatchers.Default` and the screen's on Main agree and reuse
 * each other's days.
 */
class PrayerTimesEngine {

    fun timesFor(location: GeoLocation, date: LocalDate, settings: PrayerSettings): DayPrayerTimes =
        dayFor(location, date, settings).toDayPrayerTimes()

    /** The engine's whole answer, with the resolution behind it, for the screens that explain it. */
    fun dayFor(location: GeoLocation, date: LocalDate, settings: PrayerSettings): EngineDay =
        PrayerEngine.dayTimes(placeOf(location), date, engineSettingsOf(settings))

    /** The id of the entry Automatic follows at [location]: a timetable confirmation is given under it (ruling R70). */
    fun automaticEntryId(location: GeoLocation): String = PrayerEngine.automaticEntryId(placeOf(location))

    /**
     * The day [timetable] ("automatic" or a registry entry id) gives here on its own, before the
     * user's adjustments, for Settings › Timetable and Match my mosque (spec §2.2); null when the
     * entry does not apply at [location] (see [PrayerEngine.ownDay]).
     */
    fun ownDay(location: GeoLocation, date: LocalDate, settings: PrayerSettings, timetable: String): PrayerDay? =
        PrayerEngine.ownDay(placeOf(location), date, choiceOf(timetable), engineSettingsOf(settings))

    companion object {
        private const val AUTOMATIC = "automatic"

        /** A stored timetable as the engine takes it. */
        fun choiceOf(timetable: String): TimetableChoice =
            if (timetable == AUTOMATIC) TimetableChoice.Automatic else TimetableChoice.Entry(timetable)

        /**
         * The engine's place for a stored location: its point and zone, its country (from the
         * zone where an old install stored none, spec §8) and its first-level region (ruling R32).
         */
        fun placeOf(location: GeoLocation): Place = Place(
            lat = location.latitude,
            lon = location.longitude,
            zoneId = location.timeZoneId,
            countryCode = location.countryCode ?: ZoneCountries.of(location.timeZoneId) ?: "",
            admin1 = location.region,
        )

        /** The settings that change the times, as the engine takes them. */
        fun engineSettingsOf(settings: PrayerSettings): EngineSettings = EngineSettings(
            timetable = choiceOf(settings.timetable),
            timetableConfirmed = settings.timetableConfirmed,
            timetableConfirmedUnder = settings.timetableConfirmedUnder,
            school = when (settings.school) {
                "standard" -> SchoolChoice.Standard
                "hanafi" -> SchoolChoice.Hanafi
                else -> SchoolChoice.Automatic
            },
            saudiFajrLater = settings.saudiFajrLater,
            legacyHighLatitude = settings.legacyHighLatitude?.legacyKind(),
            adjustmentsMinutes = settings.minuteAdjustments.filterValues { it != 0 },
            confirmedAdjustments = settings.confirmedAdjustments,
            hijriOffsetDays = settings.hijriOffsetDays,
        )

        private fun HighLatitudePreference.legacyKind(): String? = when (this) {
            HighLatitudePreference.AUTOMATIC -> null
            HighLatitudePreference.MIDDLE_OF_NIGHT -> HighLatRule.Legacy.MIDDLE
            HighLatitudePreference.SEVENTH_OF_NIGHT -> HighLatRule.Legacy.SEVENTH
            HighLatitudePreference.TWILIGHT_ANGLE -> HighLatRule.Legacy.ANGLE
        }

        private fun HighLatRule.preference(): HighLatitudePreference = when ((this as? HighLatRule.Legacy)?.kind) {
            HighLatRule.Legacy.MIDDLE -> HighLatitudePreference.MIDDLE_OF_NIGHT
            HighLatRule.Legacy.SEVENTH -> HighLatitudePreference.SEVENTH_OF_NIGHT
            HighLatRule.Legacy.ANGLE -> HighLatitudePreference.TWILIGHT_ANGLE
            else -> HighLatitudePreference.AUTOMATIC
        }

        private fun EngineDay.toDayPrayerTimes(): DayPrayerTimes {
            val d = day
            val ruleSetTwilight = Prayer.FAJR in d.setByRule || Prayer.ISHA in d.setByRule
            return DayPrayerTimes(
                date = d.date,
                times = listOf(
                    PrayerTime(Prayer.FAJR, d.fajr),
                    PrayerTime(Prayer.SUNRISE, d.sunrise),
                    PrayerTime(Prayer.DHUHR, d.dhuhr),
                    PrayerTime(Prayer.ASR, d.asr),
                    PrayerTime(Prayer.MAGHRIB, d.maghrib),
                    PrayerTime(Prayer.ISHA, d.isha),
                ),
                highLatitudeRuleApplied = if (ruleSetTwilight) {
                    effective.method?.highLatitude?.preference() ?: HighLatitudePreference.AUTOMATIC
                } else {
                    null
                },
                nearestLatitudeFallbackApplied = d.polar,
                asrOther = d.asrOther,
                sunset = d.sunset,
                endOfEating = d.endOfEating,
                ends = d.ends,
                setByRule = d.setByRule,
                polar = d.polar,
                sourceEntryId = effectiveEntry.id,
                entryClass = effective.entryClass,
            )
        }
    }
}
