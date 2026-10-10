package world.taqwa.app.i18n

/**
 * A whole number with its thousands grouped the way the reader's language groups them: the
 * distance to Makkah on the Prayer screen's Qibla card and on the Qibla screen, and the same
 * distance on a city page. It lives apart from the Qibla screen, which is Compose, so that the
 * website's timetable generator (`tools/timetables`) compiles this very file: the app and the site
 * cannot group a number two ways.
 *
 * commonMain has no number formatter, so the digits are the caller's, one at a time — the app's
 * `PlatformFormat.localizedDigits`, the page's CLDR digits on the site — and only the mark between
 * the groups is chosen here. It is the partner of the decimal mark the app already writes for the
 * same reader (`NumberStyle`, the recitation sizes' "58.2 MB"), so no number in the app can carry
 * one reader's thousands mark and another's decimal one:
 *
 * - English, Urdu in Western digits, and Arabic in Western digits (Libya, the Maghreb, and any
 *   phone set to Latin numerals): a comma, "2,916". The app writes their decimals with a point.
 * - French: a narrow no-break space, "2 916", French typography's own and CLDR's for French; it
 *   is also what the app's French strings already put before ? : ; and inside « ».
 * - Turkish and Indonesian: a point, "2.916". Both write the decimal with a comma, so "2,916 km"
 *   read as two point nine kilometres there.
 * - Arabic-Indic digits (Arabic in Egypt, the Gulf, the Levant) and the Extended Arabic-Indic
 *   digits Urdu and Persian use where a phone draws them: U+066C ARABIC THOUSANDS SEPARATOR,
 *   "٢٬٩١٦", the mark the platform itself writes beside those digits. A Latin comma there has the
 *   shape of the Arabic decimal separator ٫, which `NumberStyle` writes in "٩٫٣".
 * - Bengali digits: a comma, "২,৯১৬", as Bangladesh writes them and CLDR has them.
 *
 * The digit set decides before the language does, as it does in `NumberStyle`: a Samsung phone in
 * ar-LY draws Arabic-Indic digits, and its distance then takes the Arabic mark with them.
 */
object DigitGrouping {

    /** U+066C ARABIC THOUSANDS SEPARATOR, written with Arabic-Indic and Extended Arabic-Indic digits. */
    val ARABIC_THOUSANDS_SEPARATOR: Char = Char(0x066C)

    /** U+202F NARROW NO-BREAK SPACE: French grouping, which never lets a number break across lines. */
    val NARROW_NO_BREAK_SPACE: Char = Char(0x202F)

    /**
     * The mark between groups of three for a reader whose interface is [languageTag] and whose
     * platform writes zero as [platformZero] (the caller's own rendering of the digit 0, which says
     * which digits everything else on the screen is drawn in).
     */
    fun separator(languageTag: String, platformZero: String): Char {
        val zero = platformZero.firstOrNull { it.isDigit() }?.code
        return when (zero) {
            ARABIC_INDIC_ZERO, EXTENDED_ARABIC_INDIC_ZERO -> ARABIC_THOUSANDS_SEPARATOR
            BENGALI_ZERO -> ','
            else -> when (UiLanguage.of(languageTag)) {
                UiLanguage.FRENCH -> NARROW_NO_BREAK_SPACE
                UiLanguage.TURKISH, UiLanguage.INDONESIAN -> '.'
                UiLanguage.ENGLISH, UiLanguage.ARABIC, UiLanguage.URDU, UiLanguage.BENGALI -> ','
            }
        }
    }

    /**
     * [number] (never negative) in the caller's digits, a [separator] before every group of three
     * counted from the right: "2.916" for Turkish, "٢٬٩١٦" for Egyptian Arabic, "916" with none.
     */
    fun group(number: Long, languageTag: String, digit: (Int) -> String): String {
        require(number >= 0) { "a grouped number is never negative: $number" }
        val separator = separator(languageTag, digit(0))
        val plain = number.toString()
        return buildString {
            plain.forEachIndexed { i, c ->
                if (i > 0 && (plain.length - i) % 3 == 0) append(separator)
                append(digit(c - '0'))
            }
        }
    }

    private const val ARABIC_INDIC_ZERO = 0x0660
    private const val EXTENDED_ARABIC_INDIC_ZERO = 0x06F0
    private const val BENGALI_ZERO = 0x09E6
}
