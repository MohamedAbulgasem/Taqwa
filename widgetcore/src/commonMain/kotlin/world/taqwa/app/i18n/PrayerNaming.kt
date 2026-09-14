package world.taqwa.app.i18n

import world.taqwa.app.domain.Prayer

private fun names(fajr: String, sunrise: String, dhuhr: String, asr: String, maghrib: String, isha: String) = mapOf(
    Prayer.FAJR to fajr, Prayer.SUNRISE to sunrise, Prayer.DHUHR to dhuhr,
    Prayer.ASR to asr, Prayer.MAGHRIB to maghrib, Prayer.ISHA to isha,
)

/**
 * The six names in each interface language, as that language's Muslims write them. Kept in step
 * with the `prayer_*` strings of the matching `values-*` file by hand — these exist so the two
 * widget processes and the notification receiver, which have no resource lookup, can name a
 * prayer at render time.
 */
private val NAMES: Map<UiLanguage, Map<Prayer, String>> = mapOf(
    UiLanguage.ENGLISH to names("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
    UiLanguage.ARABIC to names("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"),
    UiLanguage.FRENCH to names("Fajr", "Lever du soleil", "Dhuhr", "Asr", "Maghrib", "Isha"),
    UiLanguage.TURKISH to names("Sabah", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"),
    UiLanguage.INDONESIAN to names("Subuh", "Terbit", "Dzuhur", "Ashar", "Maghrib", "Isya"),
    UiLanguage.URDU to names("فجر", "طلوعِ آفتاب", "ظہر", "عصر", "مغرب", "عشاء"),
    UiLanguage.BENGALI to names("ফজর", "সূর্যোদয়", "যোহর", "আসর", "মাগরিব", "এশা"),
)

/**
 * The "next prayer in" heading Today's ring and the widgets render, `{prayer}` standing for the
 * name. Kept in step with `Res.string.today_next_in` of each language by hand.
 */
private val COUNTDOWN: Map<UiLanguage, String> = mapOf(
    UiLanguage.ENGLISH to "{prayer} in",
    UiLanguage.ARABIC to "متبقٍ على {prayer}",
    UiLanguage.FRENCH to "{prayer} dans",
    UiLanguage.TURKISH to "{prayer} vaktine",
    UiLanguage.INDONESIAN to "Menuju {prayer}",
    UiLanguage.URDU to "{prayer} میں باقی",
    UiLanguage.BENGALI to "{prayer} পর্যন্ত বাকি",
)

/**
 * The spec's one rule for every prayer name shown anywhere — timeline, widgets, notifications:
 * in an Arabic-script interface the interface's own name stands alone; everywhere else it is
 * paired with the Arabic name, which every Muslim reads whatever their language.
 */
object PrayerNaming {
    fun arabicName(prayer: Prayer): String = NAMES.getValue(UiLanguage.ARABIC).getValue(prayer)

    fun englishName(prayer: Prayer): String = NAMES.getValue(UiLanguage.ENGLISH).getValue(prayer)

    /** The name in the interface language [languageTag] resolves to; English for one the app does not speak. */
    fun name(prayer: Prayer, languageTag: String): String = NAMES.getValue(UiLanguage.of(languageTag)).getValue(prayer)

    fun countdownLabel(prayer: Prayer, languageTag: String): String {
        val language = UiLanguage.of(languageTag)
        return COUNTDOWN.getValue(language).replace("{prayer}", name(prayer, languageTag))
    }

    /**
     * [localizedName] is what the caller has resolved for its own interface (a composable's
     * string resource, or [name] where there is none); it is what pairs with the Arabic.
     */
    fun display(prayer: Prayer, languageTag: String, localizedName: String): String =
        if (UiLanguage.of(languageTag).arabicScript) {
            name(prayer, languageTag)
        } else {
            "$localizedName · ${arabicName(prayer)}"
        }

    fun isArabicLanguage(languageTag: String): Boolean = UiLanguage.of(languageTag) == UiLanguage.ARABIC
}
