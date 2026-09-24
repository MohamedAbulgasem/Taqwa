package world.taqwa.app.hijri

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

/**
 * The month names on their own, so code that cannot see Compose — the website's timetable
 * generator compiles this file for the JVM — reads exactly the names the app shows.
 */
object HijriMonthNames {
    fun of(month: Int, language: UiLanguage): String =
        MONTHS.getValue(language).getOrNull(month - 1) ?: error("Hijri month out of range: $month")
}
