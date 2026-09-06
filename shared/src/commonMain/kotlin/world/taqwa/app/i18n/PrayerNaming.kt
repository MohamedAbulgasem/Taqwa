package world.taqwa.app.i18n

import world.taqwa.app.domain.Prayer

private val ENGLISH = mapOf(
    Prayer.FAJR to "Fajr", Prayer.SUNRISE to "Sunrise", Prayer.DHUHR to "Dhuhr",
    Prayer.ASR to "Asr", Prayer.MAGHRIB to "Maghrib", Prayer.ISHA to "Isha",
)

private val ARABIC = mapOf(
    Prayer.FAJR to "الفجر", Prayer.SUNRISE to "الشروق", Prayer.DHUHR to "الظهر",
    Prayer.ASR to "العصر", Prayer.MAGHRIB to "المغرب", Prayer.ISHA to "العشاء",
)

/**
 * The spec's one rule for every prayer name shown anywhere — timeline, widgets, notifications:
 * in Arabic locale the Arabic name stands alone; everywhere else it is paired with the
 * localised name. `localizedName` is a parameter rather than a lookup so this object never
 * needs one entry per supported UI language.
 *
 * NOTE (Task 23): Task 22 owns this file's real home. It did not exist yet in this worktree when
 * Task 23 needed it to compile `WidgetContentBuilder`, so this is a verbatim copy of Task 22's
 * brief spec, kept minimal (no `LayoutDirection`/`CountdownFormatter`/`LocalizedNotificationCopy`
 * — Task 23 does not need them). Expect a merge conflict against Task 22's own commit; the fix is
 * to take Task 22's version, since the content should be identical.
 */
object PrayerNaming {
    fun arabicName(prayer: Prayer): String = ARABIC.getValue(prayer)

    fun englishName(prayer: Prayer): String = ENGLISH.getValue(prayer)

    fun display(prayer: Prayer, languageTag: String, localizedName: String): String =
        if (isArabicLanguage(languageTag)) arabicName(prayer)
        else "$localizedName · ${arabicName(prayer)}"

    private fun isArabicLanguage(languageTag: String): Boolean =
        languageTag.substringBefore('-').equals("ar", ignoreCase = true)
}
