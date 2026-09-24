package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The formats a city page uses are the ones the app's Android formatter produces for a reader in
 * that country: CLDR digits and dates for the page language in the city's country.
 */
class FormatsTest {

    private val friday = LocalDate(2026, 9, 25)

    @Test
    fun libyanArabicUsesWesternDigitsAsTheAppDoesForALibyanReader() {
        assertEquals("5:35", Formats("ar", "LY").clock(5, 35))
    }

    @Test
    fun egyptianArabicUsesArabicIndicDigits() {
        assertEquals("٥:٣٥", Formats("ar", "EG").clock(5, 35))
    }

    @Test
    fun bengaliUsesBengaliDigits() {
        assertEquals("১৩:০০", Formats("bn", "BD").clock(13, 0))
    }

    @Test
    fun pakistaniUrduUsesWesternDigits() {
        assertEquals("13:00", Formats("ur", "PK").clock(13, 0))
    }

    @Test
    fun theHourIsNeverPaddedAndTheMinuteAlwaysIs() {
        assertEquals("5:07", Formats("en", "LY").clock(5, 7))
        assertEquals("13:00", Formats("en", "LY").clock(13, 0))
    }

    @Test
    fun englishFallsBackToBritishOrderWhereCldrHasNoEnglishForTheCountry() {
        assertEquals("25 September 2026", Formats("en", "LY").longDate(friday))
    }

    @Test
    fun englishFollowsTheCountryWhereCldrHasIt() {
        assertEquals("September 25, 2026", Formats("en", "US").longDate(friday))
    }

    @Test
    fun theDayOfTheMonthIsNeverPadded() {
        val text = Formats("en", "ZA").longDate(LocalDate(2026, 9, 5))
        assertTrue(!text.contains("05"), text)
        assertTrue(text.contains("5"), text)
    }

    @Test
    fun monthTitlesAreInThePageLanguage() {
        assertEquals("September 2026", Formats("en", "LY").monthYear(2026, 9))
        assertEquals("Eylül 2026", Formats("tr", "TR").monthYear(2026, 9))
        assertTrue(Formats("fr", "MA").monthYear(2026, 9).startsWith("septembre"), Formats("fr", "MA").monthYear(2026, 9))
    }

    @Test
    fun weekdaysAreInThePageLanguage() {
        assertEquals("Friday", Formats("en", "LY").weekday(friday))
        assertEquals("الجمعة", Formats("ar", "LY").weekday(friday))
        assertEquals("Fri", Formats("en", "LY").weekdayShort(friday))
    }

    @Test
    fun countryNamesAreCldrNamesInThePageLanguage() {
        assertEquals("Libya", Formats("en", "LY").countryName("LY"))
        assertEquals("ليبيا", Formats("ar", "LY").countryName("LY"))
        assertEquals("Libye", Formats("fr", "LY").countryName("LY"))
    }

    @Test
    fun distancesAreGroupedTheLocalWay() {
        assertEquals("2,916", Formats("en", "LY").distance(2916))
        assertEquals("٢٬٩١٦", Formats("ar", "EG").distance(2916))
    }

    @Test
    fun utcOffsetsReadLikeTheRestOfThePage() {
        assertEquals("UTC+2", Formats("en", "LY").utcOffset(2 * 3600))
        assertEquals("UTC+5:30", Formats("en", "IN").utcOffset(5 * 3600 + 30 * 60))
        assertEquals("UTC−4", Formats("en", "US").utcOffset(-4 * 3600))
        assertEquals("UTC", Formats("en", "GB").utcOffset(0))
        assertEquals("UTC+٣", Formats("ar", "SA").utcOffset(3 * 3600))
    }

    @Test
    fun hijriDatesUseTheAppsMonthNamesAndTheLocalDigits() {
        assertEquals("12 Rabi’ al-Thani 1448", Formats("en", "LY").hijri(1448, 4, 12))
        assertEquals("١٢ ربيع الآخر ١٤٤٨", Formats("ar", "EG").hijri(1448, 4, 12))
        assertEquals("12 ربيع الآخر 1448", Formats("ar", "LY").hijri(1448, 4, 12))
    }
}
