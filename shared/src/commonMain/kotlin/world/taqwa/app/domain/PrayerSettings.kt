package world.taqwa.app.domain

/**
 * The old app's stored Asr school (`asr_madhab`), read now only by
 * [world.taqwa.app.settings.SettingsRepository]'s migration (spec §8: a stored Hanafi stays).
 */
enum class AsrMadhab { STANDARD, HANAFI }

/**
 * The old high-latitude picker's rules (`high_latitude`): kept as [PrayerSettings.legacyHighLatitude]
 * for the Other methods (spec §2.2, §8), which the engine maps onto its `HighLatRule.Legacy`.
 */
enum class HighLatitudePreference { AUTOMATIC, MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, TWILIGHT_ANGLE }

/**
 * The old app's stored calculation methods (`calculation_method`), read now only by the migration,
 * which turns each into Automatic or an Other method's registry id (spec §8, ruling R12).
 */
enum class CalculationMethodId {
    MUSLIM_WORLD_LEAGUE, ISNA, EGYPTIAN, UMM_AL_QURA, KARACHI, TEHRAN,
    DUBAI, KUWAIT, QATAR, SINGAPORE, TURKEY, MOONSIGHTING_COMMITTEE
}

/**
 * The prayer-time settings (spec §2.2), populated once from an old install by
 * [world.taqwa.app.settings.SettingsRepository]'s migration and, from then on, written per field:
 * - [timetable] is `"automatic"` or a registry entry id (e.g. `"other.mwl"`, spec §8's
 *   `Entry(global id)`); plain `String` because the registry's own entry type does not exist here.
 * - [timetableConfirmed]: the user confirmed following [timetable] (the Timetable screen's warning,
 *   spec §2.2). Until then a chosen entry applies no earlier than Automatic (spec §8, ruling R52);
 *   the migration never confirms one, and choosing another entry needs its own confirmation.
 *   [timetableConfirmedUnder] is the id of the Automatic entry where it was confirmed (ruling R70):
 *   a place whose Automatic is another entry holds the choice to Automatic again until the user
 *   confirms it there; null leaves the confirmation unbound (only tests build one so).
 * - [school] is `"automatic"`, `"standard"` or `"hanafi"`; the migration only ever produces the
 *   first and the last (a stored [AsrMadhab.HANAFI] stays Hanafi, anything else becomes Automatic).
 * - [confirmedAdjustments] names, per prayer, the entry id a *negative* minute adjustment was
 *   last confirmed against (spec §2.2's "Use −2 min" dialog). A prayer with a negative value in
 *   [minuteAdjustments] but no entry here, or another entry's, is paused — not applied — until
 *   re-confirmed; the migration never adds entries, so every kept negative adjustment starts
 *   paused.
 * - [legacyHighLatitude] is the old picker's rule, only while [timetable] names an Other method;
 *   spec §8: "kept for Other methods and ignored otherwise". `null` while Automatic.
 */
data class PrayerSettings(
    val hijriOffsetDays: Int = 0,
    val showSunrise: Boolean = false,
    val minuteAdjustments: Map<Prayer, Int> = emptyMap(),
    val timetable: String = "automatic",
    val timetableConfirmed: Boolean = false,
    val timetableConfirmedUnder: String? = null,
    val school: String = "automatic",
    val showBothAsr: Boolean = false,
    val showWhereDiffer: Boolean = false,
    val saudiFajrLater: Boolean = false,
    val confirmedAdjustments: Map<Prayer, String> = emptyMap(),
    val legacyHighLatitude: HighLatitudePreference? = null,
)
