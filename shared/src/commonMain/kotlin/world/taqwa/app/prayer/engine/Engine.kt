package world.taqwa.app.prayer.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.AsrModel
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.method.HighLatRule
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.RegistryEntry
import world.taqwa.app.prayer.engine.registry.Resolution
import kotlin.math.ceil
import kotlin.math.round
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * What the user chose that changes the times (spec §2.2): the engine's own view of the app's
 * `PrayerSettings`, so the engine needs nothing from the app.
 *
 * - [timetable]: Automatic, or a registry entry by id; outside the entry's scope Automatic applies.
 * - [timetableConfirmed]: whether the user confirmed following that entry; until then it applies no
 *   earlier than Automatic (spec §8, ruling R52). [timetableConfirmedUnder], when set, is the id of
 *   Automatic's entry where it was confirmed: at a place whose Automatic is another entry (the user
 *   travelled) the choice is paused again until confirmed there (ruling R70); null leaves the
 *   confirmation unbound, for callers with no place to bind it to (the app always binds it).
 * - [school]: the Asr school; Automatic is the place's own (its Automatic entry's, spec §3.7).
 * - [saudiFajrLater]: "Pray Fajr 5 minutes later", only under Umm al-Qura in Saudi Arabia.
 * - [legacyHighLatitude]: the old picker's rule ("middle", "seventh" or "angle", see
 *   [HighLatRule.Legacy]), only for the Other methods; null keeps the method's own rule.
 * - [adjustmentsMinutes]: signed minutes per prayer; [confirmedAdjustments] names, per prayer, the
 *   entry an earlier (negative) adjustment was confirmed under.
 * - [hijriOffsetDays]: the user's Hijri offset, which moves the Ramadan calendar where the engine
 *   falls back to the tabular one.
 */
data class EngineSettings(
    val timetable: TimetableChoice = TimetableChoice.Automatic,
    val timetableConfirmed: Boolean = false,
    val timetableConfirmedUnder: String? = null,
    val school: SchoolChoice = SchoolChoice.Automatic,
    val saudiFajrLater: Boolean = false,
    val legacyHighLatitude: String? = null,
    val adjustmentsMinutes: Map<Prayer, Int> = emptyMap(),
    val confirmedAdjustments: Map<Prayer, String> = emptyMap(),
    val hijriOffsetDays: Int = 0,
)

/** The timetable the user follows: Automatic, or one registry entry by its id. */
sealed interface TimetableChoice {
    data object Automatic : TimetableChoice
    data class Entry(val id: String) : TimetableChoice
}

/** The Asr school the user follows: the place's own, or one of the two. */
enum class SchoolChoice { Automatic, Standard, Hanafi }

/**
 * One place's day, as the app shows it.
 *
 * - [day]: after the pipeline (members, invariants, ends), the Saudi Fajr rule and the
 *   adjustments; every instant a whole minute.
 * - [resolution]: what Automatic resolves to here, whatever the user chose.
 * - [effective]: what the day was computed from: [resolution], or the chosen entry's own
 *   resolution when it is in scope here (with the legacy high-latitude rule for an Other method).
 * - [school]: the Asr school [PrayerDay.asr] is in; [PrayerDay.asrOther] is the other.
 * - [pausedAdjustments]: earlier adjustments not applied, because they were confirmed under
 *   another timetable, never confirmed, or the timetable itself is paused (spec §2.2, §8).
 * - [timetablePaused]: the chosen timetable applies but is not yet confirmed, so each start is no
 *   earlier than Automatic's and each end no later ([PausedTimetable], ruling R52).
 */
data class EngineDay(
    val day: PrayerDay,
    val resolution: Resolution,
    val effective: Resolution,
    val school: AsrSchool,
    val pausedAdjustments: Set<Prayer>,
    val timetablePaused: Boolean = false,
) {
    /** Automatic's entry, or the chosen one when it applies here. */
    val effectiveEntry: RegistryEntry get() = effective.entry
}

/**
 * The engine's one entry point for the app, its notifications and widgets, and the website (spec
 * §3.1): [DayPipeline] wrapped with the user's settings, the Saudi Fajr rule, the adjustments and
 * a bounded, thread-safe cache.
 *
 * Places are taken at 1e-3° (about 110 m), the precision the app stores a location at: the day is
 * computed at the rounded point, so a cached answer is exactly what its key gives, whoever asked
 * first, and the website's city (read from the city list at five decimals) gets exactly the app's
 * times for the same city.
 */
object PrayerEngine {

    /** Days held (spec §3.1); a month view and the notification window fit many times over. */
    const val CACHE_SIZE = 256

    /** Resolutions held: resolving is the costly part, and a place asks the same every day. */
    const val RESOLUTION_CACHE_SIZE = 64

    /** Automatic's own days held for [ownDay]: a whole year, which every timetable choice's check reads again. */
    const val AUTOMATIC_DAYS_CACHE_SIZE = 400

    /** How much later Fajr is shown with "Pray Fajr 5 minutes later" (spec §2.2). */
    val SAUDI_FAJR_LATER = 5.minutes

    private const val UMM_AL_QURA_ID = "sa.ummalqura"
    private const val SAUDI_ARABIA = "SA"
    private const val OTHER_METHOD_PREFIX = "other."
    private val LEGACY_KINDS = setOf(HighLatRule.Legacy.MIDDLE, HighLatRule.Legacy.SEVENTH, HighLatRule.Legacy.ANGLE)

    private data class DayKey(val place: Place, val date: LocalDate, val settings: EngineSettings)

    private data class ResolutionKey(val place: Place, val timetable: TimetableChoice, val legacyHighLatitude: String?)

    private data class AutomaticDayKey(val place: Place, val date: LocalDate, val school: AsrSchool, val hijriOffsetDays: Int)

    /** Automatic's resolution, and the one the day follows; [chosen] when that is the user's own entry. */
    private class Resolved(val automatic: Resolution, val effective: Resolution, val chosen: Boolean)

    private val days = BoundedCache<DayKey, EngineDay>(CACHE_SIZE)
    private val resolutions = BoundedCache<ResolutionKey, Resolved>(RESOLUTION_CACHE_SIZE)
    private val automaticDays = BoundedCache<AutomaticDayKey, PrayerDay>(AUTOMATIC_DAYS_CACHE_SIZE)

    /** [place]'s day on [date] under [settings]; the same object while it stays cached. */
    fun dayTimes(place: Place, date: LocalDate, settings: EngineSettings): EngineDay {
        val at = canonical(place)
        return days.getOrPut(DayKey(at, date, settings)) { compute(at, date, settings) }
    }

    /**
     * The day [timetable] gives at [place] on [date] on its own: Automatic's, or the entry's as if
     * chosen and confirmed (with the legacy high-latitude rule for an Other method), in the Asr
     * school [settings] gives, before the Saudi rule and any adjustment, and before Isha's end is
     * known ([DayPipeline.unended]). For Settings › Timetable (spec §2.2): each row's times, Match my
     * mosque's candidates and the earlier-than check (ruling R52). Null when the entry is unknown or
     * does not apply here (ruling R50).
     *
     * Not kept in the day cache, whose days are the user's own: a year's check would evict every
     * day the screens and the alarms are using. Automatic's own days have a cache of their own,
     * a year long, since every choice's check compares with the same year of them.
     */
    fun ownDay(place: Place, date: LocalDate, timetable: TimetableChoice, settings: EngineSettings): PrayerDay? {
        val at = canonical(place)
        val resolved = resolutions.getOrPut(ResolutionKey(at, timetable, settings.legacyHighLatitude)) {
            resolve(at, settings.copy(timetable = timetable))
        }
        val zone = TimeZone.of(at.zoneId)
        val school = schoolFor(settings.school, resolved)
        return when (timetable) {
            TimetableChoice.Automatic -> automaticDays.getOrPut(AutomaticDayKey(at, date, school, settings.hijriOffsetDays)) {
                DayPipeline.unended(resolved.automatic, date, zone, school, settings.hijriOffsetDays)
            }
            is TimetableChoice.Entry -> {
                val own = resolved.effective.takeIf { it.entry.id == timetable.id } ?: return null
                DayPipeline.unended(own, date, zone, school, settings.hijriOffsetDays)
            }
        }
    }

    /**
     * The id of the entry Automatic follows at [place] (the rounded point, as every day is computed):
     * what a timetable confirmation is bound to (ruling R70).
     */
    fun automaticEntryId(place: Place): String {
        val at = canonical(place)
        return resolutions.getOrPut(ResolutionKey(at, TimetableChoice.Automatic, null)) {
            resolve(at, EngineSettings())
        }.automatic.entry.id
    }

    /** How many days are cached now (tests). */
    internal val cachedDays: Int get() = days.size

    /** Empties every cache (tests). */
    internal fun clearCache() {
        days.clear()
        resolutions.clear()
        automaticDays.clear()
    }

    private fun compute(place: Place, date: LocalDate, settings: EngineSettings): EngineDay {
        val resolved = resolutions.getOrPut(ResolutionKey(place, settings.timetable, settings.legacyHighLatitude)) {
            resolve(place, settings)
        }
        val effective = resolved.effective
        val zone = TimeZone.of(place.zoneId)
        val school = schoolFor(settings.school, resolved)
        var day = DayPipeline.day(effective, date, zone, school, settings.hijriOffsetDays)
        // Ruling R70: a confirmation given where Automatic was another entry does not travel.
        val confirmedHere = settings.timetableConfirmed &&
            (settings.timetableConfirmedUnder == null || settings.timetableConfirmedUnder == resolved.automatic.entry.id)
        val timetablePaused = resolved.chosen && !confirmedHere
        if (timetablePaused) {
            val automatic = DayPipeline.day(resolved.automatic, date, zone, school, settings.hijriOffsetDays)
            day = PausedTimetable.combine(day, automatic)
        }
        if (settings.saudiFajrLater && effective.entry.id == UMM_AL_QURA_ID && place.countryCode == SAUDI_ARABIA) {
            // The shown Fajr and its alert move; the fast still begins at Umm al-Qura's Fajr.
            day = day.copy(fajr = day.fajr + SAUDI_FAJR_LATER)
        }
        val applied = Adjustments.apply(
            day = day,
            minutes = settings.adjustmentsMinutes,
            // While the timetable itself is paused, so is every earlier adjustment: a confirmation
            // made under the paused entry would take a time below Automatic's (ruling R52).
            confirmed = if (timetablePaused) emptyMap() else settings.confirmedAdjustments,
            entryId = effective.entry.id,
            dhuhrFloor = { startOf(sky(place, date, zone).transit() + 60.0) },
            asrFloor = { sky(place, date, zone).asr(AsrSchool.STANDARD.shadowFactor, AsrModel.EXACT_MOMENT)?.let(::startOf) },
            maghribFloor = { sky(place, date, zone).altitudeTime(horizonOf(effective), morning = false)?.let(::startOf) },
        )
        return EngineDay(applied.day, resolved.automatic, effective, school, applied.paused, timetablePaused)
    }

    /** The Asr school a day is computed in: Automatic is the place's own, whatever timetable applies (spec §3.7). */
    private fun schoolFor(choice: SchoolChoice, resolved: Resolved): AsrSchool = when (choice) {
        SchoolChoice.Automatic -> resolved.automatic.entry.school
        SchoolChoice.Standard -> AsrSchool.STANDARD
        SchoolChoice.Hanafi -> AsrSchool.HANAFI
    }

    /**
     * Automatic's resolution, and the one the day follows: the chosen entry's where it exists and
     * is in scope (spec §2.2), else Automatic's. The legacy high-latitude rule applies to an Other
     * method only; an authority brings its own.
     */
    private fun resolve(place: Place, settings: EngineSettings): Resolved {
        val automatic = Registry.resolve(place)
        val chosen = (settings.timetable as? TimetableChoice.Entry)
            ?.let { Registry.byId(it.id) }
            ?.takeIf { it.id != automatic.entry.id && Registry.inScope(it, place) }
        val effective = chosen?.let { Registry.resolveEntry(it, place) } ?: automatic
        return Resolved(automatic, withLegacyRule(effective, settings.legacyHighLatitude), chosen = chosen != null)
    }

    private fun withLegacyRule(resolution: Resolution, kind: String?): Resolution {
        val method = resolution.method ?: return resolution
        if (kind == null || kind !in LEGACY_KINDS || !resolution.entry.id.startsWith(OTHER_METHOD_PREFIX)) return resolution
        return resolution.copy(method = method.copy(highLatitude = HighLatRule.Legacy(kind)))
    }

    /**
     * The sun's altitude at sunset under [resolution]: its method's horizon, or for a cautious entry
     * the lowest of its members' (the latest sunset, the safe side for a floor).
     */
    private fun horizonOf(resolution: Resolution): Double =
        resolution.method?.horizonDeg ?: resolution.members.minOf { it.method.horizonDeg }

    /**
     * The astronomical sun at the user's point on [date] (the exact model, the zone's offset at local
     * noon), for the adjustments' floors: Dhuhr a minute after its transit, Asr at its Standard
     * shadow, Maghrib at its sunset (ruling R51, review C1).
     */
    private fun sky(place: Place, date: LocalDate, zone: TimeZone): SunClock {
        val noonOffset = zone.offsetAt(date.atTime(12, 0).toInstant(zone)).totalSeconds
        return SunClock(place.lat, place.lon, date, noonOffset, SunModel.EXACT)
    }

    /** A start: rounded up to the minute. */
    private fun startOf(epochSeconds: Double): Instant = Instant.fromEpochSeconds(ceil(epochSeconds / 60.0).toLong() * 60)

    private fun canonical(place: Place): Place =
        place.copy(lat = roundedE3(place.lat), lon = roundedE3(place.lon), countryCode = place.countryCode.uppercase())

    /** As `SettingsRepository` stores a location's coordinates, to the same double. */
    private fun roundedE3(degrees: Double): Double = round(degrees * 1000.0) / 1000.0
}
