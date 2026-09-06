package world.taqwa.app.i18n

import world.taqwa.app.domain.HighLatitudePreference

/**
 * Today's high-latitude note, in both languages.
 *
 * It lives in Kotlin rather than `strings.xml` for the same reason [world.taqwa.app.notifications.LocalizedNotificationCopy]
 * does: the sentence is assembled by a plain, non-composable view model — which is also what
 * makes it testable without a resource loader — and the polar case splices a rule name into the
 * middle of it, which a flat resource string cannot do without a second parallel template per
 * rule. Every string a *composable* renders is in the resources.
 *
 * The seasonal wording is deliberate. The one-seventh and twilight-angle rules bind in **summer**,
 * when a short night would otherwise put Fajr absurdly early — not in winter. "The sun never sets
 * far enough here", the copy this replaces, described the wrong half of the year in the common
 * case. The polar sentence is unchanged: there the sun really does not rise or set.
 */
object HighLatitudeCopy {

    fun note(
        languageTag: String,
        polarFallback: Boolean,
        rule: HighLatitudePreference?,
    ): String? {
        val arabic = PrayerNaming.isArabicLanguage(languageTag)
        return when {
            polarFallback -> polar(arabic, rule)
            rule == HighLatitudePreference.SEVENTH_OF_NIGHT -> if (arabic) {
                "الليل قصير هنا في هذا الوقت من السنة. يُحسب الفجر والعشاء بقاعدة سُبع الليل."
            } else {
                "Nights are short here at this time of year. Fajr and Isha use the one-seventh rule."
            }
            rule == HighLatitudePreference.TWILIGHT_ANGLE -> if (arabic) {
                "الليل قصير هنا في هذا الوقت من السنة. يُحسب الفجر والعشاء بقاعدة زاوية الشفق."
            } else {
                "Nights are short here at this time of year. Fajr and Isha use the twilight angle rule."
            }
            rule != null -> if (arabic) {
                "يُحسب الفجر والعشاء بقاعدة منتصف الليل عند خط العرض هذا."
            } else {
                "Fajr and Isha use the middle of the night rule at this latitude."
            }
            else -> null
        }
    }

    /**
     * Polar day or night means adhan2 could not compute the day at all, so EVERY time on screen —
     * Maghrib included — came from a different latitude. That leads the sentence. The Fajr/Isha
     * rule is still named afterwards, because it was selected from the user's real latitude and
     * produced the two times they are most likely to question.
     */
    private fun polar(arabic: Boolean, rule: HighLatitudePreference?): String = buildString {
        if (arabic) {
            append("لا تشرق الشمس ولا تغرب هنا اليوم. حُسبت كل المواقيت لأقرب خط عرض تشرق فيه وتغرب")
            ruleName(arabic, rule)?.let { append("، مع حساب الفجر والعشاء بـ$it") }
            append(".")
        } else {
            append("The sun does not rise or set here today. All times are calculated for the ")
            append("nearest latitude where it does")
            ruleName(arabic, rule)?.let { append(", with Fajr and Isha using $it") }
            append(".")
        }
    }

    private fun ruleName(arabic: Boolean, rule: HighLatitudePreference?): String? = when (rule) {
        HighLatitudePreference.SEVENTH_OF_NIGHT -> if (arabic) "قاعدة سُبع الليل" else "the one-seventh rule"
        HighLatitudePreference.TWILIGHT_ANGLE -> if (arabic) "قاعدة زاوية الشفق" else "the twilight angle rule"
        HighLatitudePreference.MIDDLE_OF_NIGHT -> if (arabic) "قاعدة منتصف الليل" else "the middle of the night rule"
        else -> null
    }
}
