package world.taqwa.app.prayer

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerTime
import kotlin.math.sign
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** All three high-latitude substitution rules adhan2 supports. */
private val ALL_HIGH_LATITUDE_RULES = listOf(
    HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
    HighLatitudeRule.SEVENTH_OF_THE_NIGHT,
    HighLatitudeRule.TWILIGHT_ANGLE,
)

class PrayerTimesEngine {

    fun timesFor(location: GeoLocation, date: LocalDate, settings: PrayerSettings): DayPrayerTimes {
        val effectiveRule = HighLatitudeSelector.select(settings.highLatitude, location.latitude)

        val baseParams = settings.method.toAdhanParameters().copy(
            madhab = when (settings.madhab) {
                AsrMadhab.STANDARD -> Madhab.SHAFI
                AsrMadhab.HANAFI -> Madhab.HANAFI
            },
        )

        val dateComponents = DateComponents(date.year, date.monthNumber, date.dayOfMonth)

        // adhan2 0.0.7's PrayerTimes throws IllegalStateException outright on a true polar
        // day/night (no astronomical sunrise or sunset that date) — verified by decompiling the
        // resolved artifact (see task-5-report.md): the null-check on raw solar components at
        // PrayerTimes.kt:~168 runs before any HighLatitudeRule seasonal adjustment is applied, so
        // no HighLatitudeRule choice avoids it. Rather than let that crash reach the UI, fall back
        // to computing at the nearest latitude where a real sunrise/sunset exists (the same
        // latitude magnitude used as the automatic seventh-of-the-night threshold), keeping the
        // real longitude/date/method — the "nearest latitude" convention used by other prayer-time
        // implementations for the same edge case. Whether the exception fires depends only on the
        // date/coordinates, not on the chosen rule, so this applies identically to every rule
        // variant computed below.
        var nearestLatitudeFallbackApplied = false
        fun compute(rule: HighLatitudeRule): PrayerTimes {
            val params = baseParams.copy(highLatitudeRule = rule)
            return try {
                PrayerTimes(
                    coordinates = Coordinates(location.latitude, location.longitude),
                    dateComponents = dateComponents,
                    calculationParameters = params,
                )
            } catch (_: IllegalStateException) {
                nearestLatitudeFallbackApplied = true
                val clampedLatitude = HighLatitudeSelector.NEAREST_LATITUDE_FALLBACK * location.latitude.sign
                PrayerTimes(
                    coordinates = Coordinates(clampedLatitude, location.longitude),
                    dateComponents = dateComponents,
                    calculationParameters = params,
                )
            }
        }

        val computed = compute(effectiveRule.toAdhan())

        fun adjusted(p: Prayer, base: Instant) =
            PrayerTime(p, base + (settings.minuteAdjustments[p] ?: 0).minutes)

        // adhan2 has no signal for whether a HighLatitudeRule actually changed anything: on an
        // ordinary day the angle-based Fajr/Isha already fall within every rule's bound, so all
        // three rules agree. The note is only true when they genuinely diverge.
        fun ruleEngaged(): Boolean {
            val otherRules = ALL_HIGH_LATITUDE_RULES.filter { it != effectiveRule.toAdhan() }
            val variants = listOf(computed) + otherRules.map(::compute)
            return variants.map { it.fajr }.distinct().size > 1 ||
                variants.map { it.isha }.distinct().size > 1
        }

        return DayPrayerTimes(
            date = date,
            times = listOf(
                adjusted(Prayer.FAJR, computed.fajr),
                adjusted(Prayer.SUNRISE, computed.sunrise),
                adjusted(Prayer.DHUHR, computed.dhuhr),
                adjusted(Prayer.ASR, computed.asr),
                adjusted(Prayer.MAGHRIB, computed.maghrib),
                adjusted(Prayer.ISHA, computed.isha),
            ),
            highLatitudeRuleApplied =
                if (settings.highLatitude == HighLatitudePreference.AUTOMATIC &&
                    effectiveRule != HighLatitudePreference.MIDDLE_OF_NIGHT &&
                    ruleEngaged()
                ) effectiveRule else null,
            nearestLatitudeFallbackApplied = nearestLatitudeFallbackApplied,
        )
    }
}

/**
 * adhan2 0.0.7's `CalculationMethod` enum has no TEHRAN constant (verified by decompiling the
 * resolved artifact — see task-5-report.md). Tehran (Institute of Geophysics, University of
 * Tehran) is reconstructed manually with its published fajr/isha angles on top of `OTHER`.
 */
private fun CalculationMethodId.toAdhanParameters(): CalculationParameters = when (this) {
    CalculationMethodId.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
    CalculationMethodId.ISNA -> CalculationMethod.NORTH_AMERICA.parameters
    CalculationMethodId.EGYPTIAN -> CalculationMethod.EGYPTIAN.parameters
    CalculationMethodId.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA.parameters
    CalculationMethodId.KARACHI -> CalculationMethod.KARACHI.parameters
    CalculationMethodId.TEHRAN -> CalculationParameters(
        fajrAngle = 17.7,
        ishaAngle = 14.0,
        method = CalculationMethod.OTHER,
    )
    CalculationMethodId.DUBAI -> CalculationMethod.DUBAI.parameters
    CalculationMethodId.KUWAIT -> CalculationMethod.KUWAIT.parameters
    CalculationMethodId.QATAR -> CalculationMethod.QATAR.parameters
    CalculationMethodId.SINGAPORE -> CalculationMethod.SINGAPORE.parameters
    CalculationMethodId.TURKEY -> CalculationMethod.TURKEY.parameters
    CalculationMethodId.MOONSIGHTING_COMMITTEE -> CalculationMethod.MOON_SIGHTING_COMMITTEE.parameters
}

private fun HighLatitudePreference.toAdhan(): HighLatitudeRule = when (this) {
    HighLatitudePreference.SEVENTH_OF_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
    HighLatitudePreference.TWILIGHT_ANGLE -> HighLatitudeRule.TWILIGHT_ANGLE
    else -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
}
