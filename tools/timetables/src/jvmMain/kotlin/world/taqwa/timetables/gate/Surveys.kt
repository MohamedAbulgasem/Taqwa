package world.taqwa.timetables.gate

import java.io.File

/**
 * The committed surveys (`official/survey/<folder>`, [Survey]): each folder, the cautious entry it
 * holds to the promise, the zone its calendars are printed in (Canada's carry their own) and the
 * country code. `UkMawaqitSurveyTest` and `ContinentalMawaqitSurveyTest` run them; the monitor runs
 * them too, and checks a freshly fetched Mawaqit calendar against its own folder's faults and outliers.
 */
data class SurveyFolder(val folder: String, val entryId: String, val zoneId: String, val countryCode: String) {
    fun load(officialDir: File): Survey =
        Survey.load(officialDir.resolve("survey/$folder"), entryId = entryId, zoneId = zoneId, countryCode = countryCode)
}

object Surveys {
    val all: List<SurveyFolder> = listOf(
        SurveyFolder("gb-cautious", "gb.cautious", "Europe/London", "GB"),
        SurveyFolder("fr-cautious", "fr.cautious", "Europe/Paris", "FR"),
        SurveyFolder("be-cautious", "be.cautious", "Europe/Brussels", "BE"),
        SurveyFolder("nl-cautious", "nl.cautious", "Europe/Amsterdam", "NL"),
        SurveyFolder("de-cautious", "de.cautious", "Europe/Berlin", "DE"),
        SurveyFolder("ca-cautious", "ca.cautious", "America/Winnipeg", "CA"),
    )

    fun named(folder: String): SurveyFolder? = all.firstOrNull { it.folder == folder }
}
