package world.taqwa.app.prayer.engine.registry

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.CalculationMethodId

/**
 * [OtherMethodsProofTest]'s independent reference: adhan2 itself, called directly, reproducing
 * the pre-Task-8 `PrayerTimesEngine`'s mapping (removed by Task 8, ruling R6, once
 * `PrayerSettings.method`/`madhab`/`highLatitude` were gone). That engine's own code — before it
 * was replaced — is `git show 4643f03:shared/src/commonMain/kotlin/world/taqwa/app/prayer/PrayerTimesEngine.kt`
 * (the commit just before Task 8's `e51a4b0`/`347d026`).
 *
 * The old engine picked the date by handing adhan2 the requested [LocalDate]'s year/month/day
 * as-is (no time-zone adjustment: adhan2's own `PrayerTimes` resolves times from coordinates and
 * date components alone, never the zone), and always resolved
 * [world.taqwa.app.domain.HighLatitudePreference.AUTOMATIC] to a concrete rule before calling
 * adhan2 (`HighLatitudeSelector.select`) — but for every non-Automatic preference (all this test
 * ever passes) that selector was a pass-through, so this helper just takes the resolved
 * [HighLatitudeRule] directly rather than reproducing the selector.
 */
internal object Adhan2Reference {

    /**
     * adhan2's own prayer times for [methodId] at Standard (Shafi'i) madhab, the given
     * [highLatitudeRule], and no method adjustments beyond what
     * [CalculationMethod.parameters]/[toAdhanParameters] already bakes in — including Singapore's
     * `Rounding.UP` (baked into `CalculationMethod.SINGAPORE.parameters` itself, not something the
     * old engine or this helper add on top).
     */
    fun timesFor(
        methodId: CalculationMethodId,
        lat: Double,
        lon: Double,
        date: LocalDate,
        highLatitudeRule: HighLatitudeRule,
    ): PrayerTimes {
        val params = methodId.toAdhanParameters().copy(
            madhab = Madhab.SHAFI,
            highLatitudeRule = highLatitudeRule,
        )
        val dateComponents = DateComponents(date.year, date.monthNumber, date.dayOfMonth)
        return PrayerTimes(
            coordinates = Coordinates(lat, lon),
            dateComponents = dateComponents,
            calculationParameters = params,
        )
    }

    /**
     * adhan2 0.0.7's `CalculationMethod` enum has no TEHRAN constant (verified by decompiling the
     * resolved artifact — see task-5-report.md, and the old engine's own copy of this comment).
     * Tehran (Institute of Geophysics, University of Tehran) is reconstructed manually with its
     * published fajr/isha angles on top of `OTHER`. Unused by this test (R12: 11 Other methods,
     * no Tehran) but kept so this mapping stays the old engine's mapping in full.
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
}
