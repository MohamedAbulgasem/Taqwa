package world.taqwa.app.widget

/**
 * Rewrites the ASCII digits in an already-composed widget string (e.g. `"1:05"`) to the digit
 * set a BCP-47 language tag defaults to under CLDR.
 *
 * Most numbers a widget shows (`WidgetContent.nextClockTime`, `WidgetPrayerRow.clockTime`) are
 * pre-formatted by `PlatformFormat` in `:shared` before they ever reach the wire, and already
 * carry the right digits. This object exists for the handful of numbers a widget still builds
 * itself by raw string interpolation (the countdown "h:mm" — I9) — those need the same digit set
 * or they visibly disagree with the clock time sitting right next to them.
 *
 * The rule mirrors `CountdownFormatter.defaultsToArabicIndicDigits` in
 * `shared/src/commonMain/kotlin/world/taqwa/app/i18n/CountdownFormatter.kt`: `ar-LY`, `ar-MA`,
 * `ar-TN`, `ar-DZ` default to Western digits despite being Arabic, so every other `ar` tag
 * (including bare `"ar"`, and `ar-EG`/`ar-SA` explicitly) gets Arabic-Indic. `ar-EH` (Western
 * Sahara) and `ar-MR` (Mauritania) are folded into the same Western-default group — CLDR treats
 * them the same as the other Maghreb-adjacent territories, they are just not exercised by
 * `CountdownFormatter`'s narrower two-locale test.
 *
 * Unlike `CountdownFormatter`, there is no tabular-jitter fallback here. That fallback exists
 * only for the in-app Today ring, whose countdown ticks every second and would visibly jitter in
 * the system Arabic font's non-tabular Arabic-Indic digits (spec §4.2). A widget countdown is
 * minute-granular and redrawn at most once a minute — the jitter that fallback guards against
 * cannot occur here, so the exception does not apply (I9).
 *
 * `fa` (Persian) and `ur` (Urdu) are left on Western digits. Neither `PlatformFormat` nor
 * `CountdownFormatter` gives either language a non-Western digit set anywhere in this app today
 * (only `LayoutDirection` knows about them, and only for RTL layout) — Persian and Urdu's own
 * CLDR-default Extended Arabic-Indic digits (`۰۱۲۳۴۵۶۷۸۹`) are a different glyph set from Arabic's
 * Arabic-Indic digits, so guessing one here without an existing app convention to match would add
 * a behaviour no other code agrees with. Left Western until the app itself handles them.
 */
object WidgetDigits {
    private const val WESTERN_DIGITS = "0123456789"
    private const val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    /** `ar-*` region codes that default to Western digits despite the language being Arabic. */
    private val WESTERN_ARABIC_REGIONS = setOf("LY", "MA", "TN", "DZ", "EH", "MR")

    private fun digitsFor(languageTag: String): String {
        val tag = languageTag.uppercase()
        val language = tag.substringBefore('-')
        if (language != "AR") return WESTERN_DIGITS
        val region = tag.substringAfter('-', missingDelimiterValue = "")
        return if (region in WESTERN_ARABIC_REGIONS) WESTERN_DIGITS else ARABIC_INDIC_DIGITS
    }

    /**
     * Replaces every ASCII digit `0`-`9` in [text] with the digit [languageTag] defaults to.
     * Everything else in [text] — `:` separators, spaces, RTL marks — passes through unchanged.
     */
    fun localize(text: String, languageTag: String): String {
        val digits = digitsFor(languageTag)
        if (digits === WESTERN_DIGITS) return text
        return buildString(text.length) {
            for (c in text) {
                append(if (c in '0'..'9') digits[c - '0'] else c)
            }
        }
    }
}
