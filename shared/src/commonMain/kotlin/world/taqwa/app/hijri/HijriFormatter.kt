package world.taqwa.app.hijri

import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage

/**
 * The twelve months in each interface language, in the forms an almanac in that language prints.
 * The Arabic names are the real ones, not a transliteration of the transliteration: ربيع الآخر
 * rather than ربيع الثاني and جمادى الآخرة rather than جمادى الثانية, both the classical forms.
 */
private val MONTHS: Map<UiLanguage, List<String>> = mapOf(
    UiLanguage.ENGLISH to listOf(
        "Muharram", "Safar", "Rabi’ al-Awwal", "Rabi’ al-Thani",
        "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha’ban",
        "Ramadan", "Shawwal", "Dhu al-Qi’dah", "Dhu al-Hijjah",
    ),
    UiLanguage.ARABIC to listOf(
        "محرّم", "صفر", "ربيع الأوّل", "ربيع الآخر",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوّال", "ذو القعدة", "ذو الحجّة",
    ),
    UiLanguage.FRENCH to listOf(
        "Mouharram", "Safar", "Rabi’ al-Awwal", "Rabi’ al-Thani",
        "Joumada al-Oula", "Joumada al-Akhira", "Rajab", "Cha'ban",
        "Ramadan", "Chawwal", "Dhou al-Qi'da", "Dhou al-Hijja",
    ),
    UiLanguage.TURKISH to listOf(
        "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir",
        "Cemaziyelevvel", "Cemaziyelahir", "Recep", "Şaban",
        "Ramazan", "Şevval", "Zilkade", "Zilhicce",
    ),
    UiLanguage.INDONESIAN to listOf(
        "Muharam", "Safar", "Rabiulawal", "Rabiulakhir",
        "Jumadilawal", "Jumadilakhir", "Rajab", "Syakban",
        "Ramadan", "Syawal", "Zulkaidah", "Zulhijah",
    ),
    UiLanguage.URDU to listOf(
        "محرم", "صفر", "ربیع الاول", "ربیع الثانی",
        "جمادی الاول", "جمادی الثانی", "رجب", "شعبان",
        "رمضان", "شوال", "ذوالقعدہ", "ذوالحجہ",
    ),
    UiLanguage.BENGALI to listOf(
        "মহররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি",
        "জমাদিউল আউয়াল", "জমাদিউস সানি", "রজব", "শাবান",
        "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ",
    ),
)

object HijriFormatter {
    fun monthNameEnglish(month: Int): String = monthName(month, UiLanguage.ENGLISH)

    fun monthNameArabic(month: Int): String = monthName(month, UiLanguage.ARABIC)

    fun monthName(month: Int, language: UiLanguage): String =
        MONTHS.getValue(language).getOrNull(month - 1) ?: error("Hijri month out of range: $month")

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
