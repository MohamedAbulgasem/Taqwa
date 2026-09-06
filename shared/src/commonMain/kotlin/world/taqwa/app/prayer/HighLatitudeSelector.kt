package world.taqwa.app.prayer

import world.taqwa.app.domain.HighLatitudePreference
import kotlin.math.abs

/**
 * Above roughly 48 degrees the sun stops dropping far enough below the horizon for true
 * Fajr and Isha, so a substitution rule is required. Thresholds follow common practice:
 * seventh-of-the-night from 48 degrees, twilight angle from 65 where nights are shortest.
 */
object HighLatitudeSelector {

    private const val SEVENTH_THRESHOLD = 48.0
    private const val TWILIGHT_THRESHOLD = 65.0

    /**
     * Latitude magnitude [PrayerTimesEngine] substitutes in when adhan2 cannot compute a real
     * sunrise/sunset for the requested coordinates on a given date (true polar day or night).
     * This is a distinct concept from [SEVENTH_THRESHOLD] even though it currently shares the
     * same numeric value: that threshold decides which high-latitude *rule* to apply, while this
     * one decides which latitude to recompute the *entire day* at when adhan2 cannot produce a
     * real sunrise/sunset at all. Kept as its own constant so the two can diverge independently.
     */
    const val NEAREST_LATITUDE_FALLBACK = 48.0

    fun select(preference: HighLatitudePreference, latitude: Double): HighLatitudePreference {
        if (preference != HighLatitudePreference.AUTOMATIC) return preference
        val lat = abs(latitude)
        return when {
            lat >= TWILIGHT_THRESHOLD -> HighLatitudePreference.TWILIGHT_ANGLE
            lat >= SEVENTH_THRESHOLD -> HighLatitudePreference.SEVENTH_OF_NIGHT
            else -> HighLatitudePreference.MIDDLE_OF_NIGHT
        }
    }
}
