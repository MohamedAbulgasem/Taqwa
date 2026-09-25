package world.taqwa.app.i18n

/**
 * Which digits the Today ring's countdown is written in. It lives apart from [CountdownFormatter],
 * which needs `PlatformFormat` and so Compose, so that the website's timetable generator
 * (`tools/timetables`) can compile the same rule and a city page counts down in the app's digits.
 */
object CountdownDigits {

    /**
     * Set by the on-device check in Task 22's verification step. Defaults to `false` — the
     * conservative, always-correct choice — until someone confirms Arabic-Indic glyphs are
     * tabular in the system Arabic face on both an Android OEM Arabic font and iOS's SF Arabic.
     */
    const val ARABIC_INDIC_DIGITS_VERIFIED_TABULAR = false

    /**
     * ar-EG and ar-SA default to Arabic-Indic digits; ar-LY, ar-MA, ar-TN and ar-DZ default to
     * Western already, so the fallback question never arises for them. Bengali defaults to its
     * own digits everywhere, and the same tabular-width question applies to them.
     */
    fun defaultsToArabicIndicDigits(languageTag: String): Boolean =
        languageTag.uppercase() in setOf("AR-EG", "AR-SA") || UiLanguage.of(languageTag) == UiLanguage.BENGALI

    /** True when the countdown for [languageTag] is written in Western digits rather than the locale's own. */
    fun westernFallback(languageTag: String, tabularDigitsVerified: Boolean = ARABIC_INDIC_DIGITS_VERIFIED_TABULAR): Boolean =
        defaultsToArabicIndicDigits(languageTag) && !tabularDigitsVerified
}
