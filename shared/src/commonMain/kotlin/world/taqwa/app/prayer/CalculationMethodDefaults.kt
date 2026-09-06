package world.taqwa.app.prayer

import world.taqwa.app.domain.CalculationMethodId

/**
 * The calculation method a user in a given country is most likely to expect. A wrong default is
 * worse than a visible choice: the times look plausible and are quietly a few minutes off, so the
 * mapping only claims the countries where one authority is genuinely dominant and falls back to
 * the Muslim World League everywhere else.
 */
object CalculationMethodDefaults {

    private val BY_COUNTRY = mapOf(
        "SA" to CalculationMethodId.UMM_AL_QURA,
        "TR" to CalculationMethodId.TURKEY,
        "US" to CalculationMethodId.ISNA,
        "CA" to CalculationMethodId.ISNA,
        "EG" to CalculationMethodId.EGYPTIAN,
        "PK" to CalculationMethodId.KARACHI,
        "IN" to CalculationMethodId.KARACHI,
        "BD" to CalculationMethodId.KARACHI,
        "ID" to CalculationMethodId.SINGAPORE,
        "MY" to CalculationMethodId.SINGAPORE,
        "SG" to CalculationMethodId.SINGAPORE,
        "AE" to CalculationMethodId.DUBAI,
        "KW" to CalculationMethodId.KUWAIT,
        "QA" to CalculationMethodId.QATAR,
        "IR" to CalculationMethodId.TEHRAN,
    )

    fun forCountry(countryCode: String?): CalculationMethodId =
        countryCode?.uppercase()?.let(BY_COUNTRY::get) ?: CalculationMethodId.MUSLIM_WORLD_LEAGUE
}
