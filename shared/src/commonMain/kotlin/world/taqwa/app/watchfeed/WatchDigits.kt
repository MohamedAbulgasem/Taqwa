package world.taqwa.app.watchfeed

import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage

/**
 * The feed's digit rule (Ruling 9, "the zero of the phone's digit set", and the Lead's call 3).
 * The phone's own digits go to the watch only in the (interface language, digit set) pairs the
 * face is built and measured for, [FACE_PAIRS]. Any other pair is written in Western digits
 * throughout: fa-IR's Persian digits, mr-IN's Devanagari, an `-u-nu-arab` English phone. So
 * [zero] is always the zero of the feed's own strings, which the watch counts its numbers from.
 *
 * [phone] is the format's ten digits, each one a string, so that a supplementary-plane digit is
 * whole.
 */
internal class WatchDigits private constructor(
    private val phone: List<String>,
    language: UiLanguage,
) {
    /**
     * Whether the phone's digits go as they are: (language, the phone's zero) is one of
     * [FACE_PAIRS]. When not, the feed is Western throughout, and a caller whose string holds a
     * mark chosen by its digits (the distance's thousands mark) writes the Western one itself.
     */
    val kept: Boolean = phone[0].length == 1 && phone[0][0] in FACE_PAIRS.getValue(language)

    /** The phone's zero when its pair with the language is one the face draws, else `'0'`. */
    val zero: Char = if (kept) phone[0][0] else '0'

    /**
     * [text] as the feed writes it: itself when the phone's digits are kept, otherwise with each
     * of the phone's ten digits written as its Western digit, and nothing else changed. So a mark
     * chosen by the phone's digits, U+066C beside Arabic-Indic ones, would stay: the distance is
     * grouped Western by the builder instead.
     */
    fun write(text: String): String {
        if (kept) return text
        return buildString(text.length) {
            var i = 0
            while (i < text.length) {
                val digit = phone.indexOfFirst { text.startsWith(it, i) }
                if (digit >= 0) {
                    append('0' + digit)
                    i += phone[digit].length
                } else {
                    append(text[i])
                    i += 1
                }
            }
        }
    }

    companion object {
        /** The digit sets the face is built and measured for, by the zero each starts at. */
        val FACE_PAIRS: Map<UiLanguage, Set<Char>> = mapOf(
            UiLanguage.ENGLISH to setOf('0'),
            UiLanguage.FRENCH to setOf('0'),
            UiLanguage.TURKISH to setOf('0'),
            UiLanguage.INDONESIAN to setOf('0'),
            UiLanguage.ARABIC to setOf('0', Char(0x0660)), // ٠, Arabic-Indic
            UiLanguage.URDU to setOf('0', Char(0x06F0)), // ۰, extended Arabic-Indic, in Urdu's forms
            UiLanguage.BENGALI to setOf('0', Char(0x09E6)), // ০, Bengali
        )

        fun of(format: PlatformFormat, language: UiLanguage): WatchDigits =
            WatchDigits((0..9).map { format.localizedDigits(it) }, language)
    }
}
