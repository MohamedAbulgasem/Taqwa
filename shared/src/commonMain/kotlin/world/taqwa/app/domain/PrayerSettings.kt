package world.taqwa.app.domain

enum class AsrMadhab { STANDARD, HANAFI }

enum class HighLatitudePreference { AUTOMATIC, MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, TWILIGHT_ANGLE }

enum class CalculationMethodId {
    MUSLIM_WORLD_LEAGUE, ISNA, EGYPTIAN, UMM_AL_QURA, KARACHI, TEHRAN,
    DUBAI, KUWAIT, QATAR, SINGAPORE, TURKEY, MOONSIGHTING_COMMITTEE
}

data class PrayerSettings(
    val method: CalculationMethodId = CalculationMethodId.MUSLIM_WORLD_LEAGUE,
    val madhab: AsrMadhab = AsrMadhab.STANDARD,
    val highLatitude: HighLatitudePreference = HighLatitudePreference.AUTOMATIC,
    val hijriOffsetDays: Int = 0,
    val showSunrise: Boolean = false,
    val remindBeforeMinutes: Int = 0,
    val minuteAdjustments: Map<Prayer, Int> = emptyMap(),
)
