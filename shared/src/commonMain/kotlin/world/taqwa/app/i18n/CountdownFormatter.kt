package world.taqwa.app.i18n

import kotlin.time.Duration

/**
 * The countdown ring's H:MM:SS text. The seconds are shown because a bare "0:20" was read as
 * twenty *seconds* by a first-time viewer; with three groups the string can only be read one way,
 * and the ring already refreshes every second. Unlike the timeline's clock times, which always
 * keep the locale's own digits, the countdown may fall back to Western digits: it is a duration,
 * not a clock time, so it carries less locale expectation, and the spec allows trading that off
 * against the tabular jitter Arabic-Indic glyphs might introduce (spec §4.2, "Consequence to
 * verify"). Whether the fallback is actually needed is an on-device verification, not something
 * this pure function can measure — [ARABIC_INDIC_DIGITS_VERIFIED_TABULAR] carries that verdict.
 */
object CountdownFormatter {

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

    fun countdown(duration: Duration, format: PlatformFormat, tabularDigitsVerified: Boolean): String {
        val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
        val hours = (totalSeconds / 3_600).toInt()
        val minutes = ((totalSeconds % 3_600) / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()

        val useWestern = defaultsToArabicIndicDigits(format.languageTag()) && !tabularDigitsVerified
        return if (useWestern) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${format.localizedDigits(hours)}:${padTwo(minutes, format)}:${padTwo(seconds, format)}"
        }
    }

    private fun padTwo(value: Int, format: PlatformFormat): String =
        if (value < 10) {
            "${format.localizedDigits(0)}${format.localizedDigits(value)}"
        } else {
            format.localizedDigits(value)
        }
}
