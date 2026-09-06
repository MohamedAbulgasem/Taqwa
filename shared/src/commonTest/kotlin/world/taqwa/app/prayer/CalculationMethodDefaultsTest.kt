package world.taqwa.app.prayer

import world.taqwa.app.domain.CalculationMethodId
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculationMethodDefaultsTest {

    @Test
    fun saudiArabiaUsesUmmAlQura() =
        assertEquals(CalculationMethodId.UMM_AL_QURA, CalculationMethodDefaults.forCountry("SA"))

    @Test
    fun turkeyUsesDiyanet() =
        assertEquals(CalculationMethodId.TURKEY, CalculationMethodDefaults.forCountry("TR"))

    @Test
    fun northAmericaUsesIsna() {
        assertEquals(CalculationMethodId.ISNA, CalculationMethodDefaults.forCountry("US"))
        assertEquals(CalculationMethodId.ISNA, CalculationMethodDefaults.forCountry("CA"))
    }

    @Test
    fun theSubcontinentUsesKarachi() {
        listOf("PK", "IN", "BD").forEach {
            assertEquals(CalculationMethodId.KARACHI, CalculationMethodDefaults.forCountry(it))
        }
    }

    @Test
    fun southeastAsiaUsesSingapore() {
        listOf("ID", "MY", "SG").forEach {
            assertEquals(CalculationMethodId.SINGAPORE, CalculationMethodDefaults.forCountry(it))
        }
    }

    @Test
    fun libyaFallsBackToMuslimWorldLeague() =
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, CalculationMethodDefaults.forCountry("LY"))

    @Test
    fun anUnknownOrAbsentCountryFallsBackRatherThanCrashing() {
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, CalculationMethodDefaults.forCountry(null))
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, CalculationMethodDefaults.forCountry("ZZ"))
    }

    @Test
    fun lowercaseCountryCodesAreAccepted() =
        assertEquals(CalculationMethodId.EGYPTIAN, CalculationMethodDefaults.forCountry("eg"))
}
