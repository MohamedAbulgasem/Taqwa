package world.taqwa.app.watchfeed

import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage

/**
 * The phone tags the watch face is measured for, and the digit sets each can carry in the feed.
 * It lives here, in commonMain, so that Task 2's forms (`PhoneForms`, which the face's fit tools
 * read) and Task 3's size test read one list and one rule, and neither needs the other. No release
 * code calls it.
 *
 * The rule runs on whatever runtime builds the formats: the JVM's CLDR in the host tests, the
 * phone's ICU in Task 7's harness.
 */
internal object PhoneTags {

    /** Phones in the seven languages, with the regions whose digits or words differ. */
    val TAGS: List<String> = listOf(
        "en", "en-GB", "en-US", "en-ZA",
        "ar-SA", "ar-EG", "ar-LY", "ar-MA", "ar-TN", "ar-DZ", "ar-IQ", "ar-SY", "ar-JO", "ar-LB", "ar-AE",
        "fr-FR", "fr-MA", "fr-DZ", "fr-CA",
        "tr-TR",
        "id-ID",
        "ur-PK", "ur-IN",
        "bn-BD", "bn-IN",
    )

    /** Phones outside the seven languages, which the app shows in English (the Lead's call 3). */
    val FALLBACK_TAGS: List<String> = listOf("hi-IN", "he-IL", "fa-IR", "mr-IN", "zh-CN")

    /**
     * The tags a format is built on, one for each digit set the feed may carry for [tag]: [tag]
     * itself first, the runtime's own digits; then, for Arabic, Western and Arabic-Indic, and for
     * Urdu, Western and extended Arabic-Indic, each by its Unicode `-u-nu-` extension. Nothing more
     * for any other tag.
     *
     * A tag that already carries a Unicode extension, as a phone's own tag may (`ar-SA-u-nu-latn`,
     * which the feed's `ui.language` keeps), is never given a second `-u-`, which no locale reads:
     * its other sets are named on the tag without its extension, and a set it already names is
     * listed once.
     */
    fun numberingTags(tag: String): List<String> {
        val base = tag.withoutUnicodeExtension()
        return when (UiLanguage.of(tag)) {
            UiLanguage.ARABIC -> listOf(tag, "$base-u-nu-latn", "$base-u-nu-arab")
            UiLanguage.URDU -> listOf(tag, "$base-u-nu-latn", "$base-u-nu-arabext")
            else -> listOf(tag)
        }.distinctBy { it.lowercase() }
    }

    /** [this] without its `-u-` extension and whatever follows it, the singleton read in any case. */
    private fun String.withoutUnicodeExtension(): String {
        val at = indexOf("-u-", ignoreCase = true)
        return if (at < 0) this else substring(0, at)
    }

    /**
     * For each of [numberingTags] in order, the format [formatFor] builds on it and its ten digits
     * as the feed writes them ([WatchDigits.write]), each set once. So the first set is the
     * runtime's own when its pair with the tag's language is one the face draws, else Western;
     * every Arabic tag also carries the other of Western and Arabic-Indic, and every Urdu tag the
     * other of Western and extended Arabic-Indic; and a fallback tag carries Western alone.
     */
    fun digitSets(tag: String, formatFor: (String) -> PlatformFormat): List<PhoneDigitSet> {
        val language = UiLanguage.of(tag)
        val sets = mutableListOf<PhoneDigitSet>()
        for (formatTag in numberingTags(tag)) {
            val format = formatFor(formatTag)
            val digits = WatchDigits.of(format, language).write((0..9).joinToString("") { format.localizedDigits(it) })
            if (sets.none { it.digits == digits }) sets += PhoneDigitSet(formatTag, digits)
        }
        return sets
    }
}

/** A digit set the feed may carry: the tag its format is built on, and its ten digits as the feed writes them. */
internal data class PhoneDigitSet(val formatTag: String, val digits: String)
