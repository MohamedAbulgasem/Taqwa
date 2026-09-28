package world.taqwa.timetables.golden

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.day.PrayerDay
import kotlin.time.Instant

/**
 * One row of the committed golden vector: [case]'s inputs and every field spec §3.2 asks for, minutes
 * from 00:00 UTC on [case]'s civil date (so the numbers stay small and the file stays compact) —
 * [GoldenVectorTest][world.taqwa.app.prayer.engine.golden] decodes this back the same way. UTC, not the
 * place's local midnight: the engine returns instants, and a platform's time-zone data may not yet know a
 * zone's change (British Columbia's permanent summer time from November 2026 moved every local minute by an
 * hour on one target and not the other), which is the clock line's business (spec §3.9), not this vector's.
 * Eighteen `;`-separated fields, in order: latitude and longitude (degrees × 1000, rounded), the IANA
 * zone, the ISO country code, the admin-1 name (empty when none), the ISO date, the settings token
 * ([SettingsCase.toToken]), then fajr, sunrise, dhuhr, asr, the other Asr, maghrib, isha, sunset, the
 * end of eating, imsak (`-` when null) and a flags bitmask: bit `p.ordinal` for each [Prayer] in
 * [PrayerDay.setByRule], bit 6 for [PrayerDay.polar].
 */
fun encodeRow(case: GoldenCase, day: PrayerDay): String {
    val midnight = case.date.atStartOfDayIn(TimeZone.UTC)
    fun minutes(instant: Instant): Long = (instant.epochSeconds - midnight.epochSeconds) / 60

    var flags = 0
    for (prayer in day.setByRule) flags = flags or (1 shl prayer.ordinal)
    if (day.polar) flags = flags or (1 shl POLAR_BIT)

    val fields = listOf(
        Math.round(case.seed.lat * 1000.0).toString(),
        Math.round(case.seed.lon * 1000.0).toString(),
        case.seed.zoneId,
        case.seed.countryCode,
        case.seed.admin1 ?: "",
        case.date.toString(),
        case.settings.toToken(),
        minutes(day.fajr).toString(),
        minutes(day.sunrise).toString(),
        minutes(day.dhuhr).toString(),
        minutes(day.asr).toString(),
        minutes(day.asrOther).toString(),
        minutes(day.maghrib).toString(),
        minutes(day.isha).toString(),
        minutes(day.sunset).toString(),
        minutes(day.endOfEating).toString(),
        day.imsak?.let { minutes(it).toString() } ?: "-",
        flags.toString(),
    )
    require(fields.none { it.contains(FIELD_SEPARATOR) }) { "a field of $case contains '$FIELD_SEPARATOR': $fields" }
    return fields.joinToString(FIELD_SEPARATOR)
}

/** The place-day [case] gives through the app's own entry point ([PrayerEngine.dayTimes]). */
fun compute(case: GoldenCase): PrayerDay =
    PrayerEngine.dayTimes(case.seed.place(), case.date, case.settings.toEngineSettings()).day

const val FIELD_SEPARATOR = ";"
const val POLAR_BIT = 6
