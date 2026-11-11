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
     *
     * The month is named in [language], which defaults to the one [format]'s tag resolves to, so
     * every caller from before the watch feed is unchanged. The feed passes the app's own interface
     * language, which a per-app language or a phone outside the seven can set apart from the tag.
     */
    fun format(
        date: HijriDate,
        format: PlatformFormat = EnglishPlatformFormat,
        language: UiLanguage = UiLanguage.of(format.languageTag()),
    ): String = "${formatDayMonth(date, format, language)} ${format.localizedDigits(date.year)}"

    /**
     * [format]'s output without its year, from the same day digits and month name: the watch
     * face's centre line and the Moon's curve, `6 Jumada al-Ula` (Ruling 24).
     */
    fun formatDayMonth(
        date: HijriDate,
        format: PlatformFormat = EnglishPlatformFormat,
        language: UiLanguage = UiLanguage.of(format.languageTag()),
    ): String = "${format.localizedDigits(date.day)} ${monthName(date.month, language)}"
}
