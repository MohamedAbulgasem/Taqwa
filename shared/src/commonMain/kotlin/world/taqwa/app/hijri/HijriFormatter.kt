package world.taqwa.app.hijri

import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming

private val MONTHS = listOf(
    "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
    "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
    "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
)

/**
 * The Arabic month names are the real ones, not a transliteration of the transliteration. Note
 * ربيع الآخر rather than ربيع الثاني and جمادى الآخرة rather than جمادى الثانية: both are the
 * classical forms and the ones an Arabic almanac prints.
 */
private val MONTHS_AR = listOf(
    "محرّم", "صفر", "ربيع الأوّل", "ربيع الآخر",
    "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
    "رمضان", "شوّال", "ذو القعدة", "ذو الحجّة",
)

object HijriFormatter {
    fun monthNameEnglish(month: Int): String =
        MONTHS.getOrNull(month - 1) ?: error("Hijri month out of range: $month")

    fun monthNameArabic(month: Int): String =
        MONTHS_AR.getOrNull(month - 1) ?: error("Hijri month out of range: $month")

    /**
     * The day and year go through [PlatformFormat.localizedDigits], so an Egyptian reader gets
     * ٢٤ صفر ١٤٤٨ and a Libyan one 24 صفر 1448 — the same CLDR rule the clock times follow, and
     * the reason no digit set is hard-coded here.
     */
    fun format(date: HijriDate, format: PlatformFormat = EnglishPlatformFormat): String {
        val month = if (PrayerNaming.isArabicLanguage(format.languageTag())) {
            monthNameArabic(date.month)
        } else {
            monthNameEnglish(date.month)
        }
        return "${format.localizedDigits(date.day)} $month ${format.localizedDigits(date.year)}"
    }
}
