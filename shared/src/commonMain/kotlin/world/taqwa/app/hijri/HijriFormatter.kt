package world.taqwa.app.hijri

import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage

object HijriFormatter {
    fun monthNameEnglish(month: Int): String = monthName(month, UiLanguage.ENGLISH)

    fun monthNameArabic(month: Int): String = monthName(month, UiLanguage.ARABIC)

    fun monthName(month: Int, language: UiLanguage): String = HijriMonthNames.of(month, language)

    /**
     * The day and year go through [PlatformFormat.localizedDigits], so an Egyptian reader gets
     * ٢٤ صفر ١٤٤٨, a Libyan one 24 صفر 1448 and a Bangladeshi one ২৪ সফর ১৪৪৮ — the same CLDR rule
     * the clock times follow, and the reason no digit set is hard-coded here.
     */
    fun format(date: HijriDate, format: PlatformFormat = EnglishPlatformFormat): String {
        val month = monthName(date.month, UiLanguage.of(format.languageTag()))
        return "${format.localizedDigits(date.day)} $month ${format.localizedDigits(date.year)}"
    }
}
