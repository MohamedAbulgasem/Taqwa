package world.taqwa.app.watchfeed

import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The digits the feed writes (Ruling 9 and the Lead's call 3): the phone's own only in the
 * (language, digits) pairs the face is built and measured for, and Western throughout otherwise.
 */
class WatchDigitsTest {

    @Test
    fun theFourPairsTheFaceDrawsAreKept() {
        for ((language, zeros) in WatchDigits.FACE_PAIRS) {
            for (zero in zeros) {
                val format = DigitSetFormat(DigitSets.from(zero.code))
                val digits = WatchDigits.of(format, language)
                assertEquals(zero, digits.zero, "${language.code} with ${zero.code}")
                val text = "SUN ${format.localizedDigits(16)}:${format.localizedDigits(47)}"
                assertEquals(text, digits.write(text), "${language.code} with ${zero.code}")
            }
        }
    }

    @Test
    fun aSetTheFaceWasNotBuiltForTheLanguageIsWrittenWestern() {
        val cases = listOf(
            DigitSets.DEVANAGARI to UiLanguage.ENGLISH, // mr-IN
            DigitSets.EXTENDED_ARABIC_INDIC to UiLanguage.ENGLISH, // fa-IR
            DigitSets.ARABIC_INDIC to UiLanguage.ENGLISH,
            DigitSets.EXTENDED_ARABIC_INDIC to UiLanguage.ARABIC,
        )
        for ((set, language) in cases) {
            val format = DigitSetFormat(set)
            val digits = WatchDigits.of(format, language)
            assertEquals('0', digits.zero, "${set[0]} with ${language.code}")
            val text = "SUN ${format.localizedDigits(16)}:${format.localizedDigits(47)}"
            assertEquals("SUN 16:47", digits.write(text), "${set[0]} with ${language.code}")
        }
    }

    @Test
    fun aSupplementarySetIsWrittenWestern() {
        val format = DigitSetFormat(DigitSets.CHAKMA)
        assertEquals(2, DigitSets.CHAKMA[0].length, "a Chakma digit is two UTF-16 units")
        val digits = WatchDigits.of(format, UiLanguage.ENGLISH)
        assertEquals('0', digits.zero)
        assertEquals("16:47", digits.write("${format.localizedDigits(16)}:${format.localizedDigits(47)}"))
    }
}

/** Ten digits from a zero's code point, each one a string, so a supplementary-plane digit is whole. */
internal object DigitSets {
    fun from(zero: Int): List<String> = (0..9).map { codePointString(zero + it) }

    val WESTERN = from(0x30)
    val ARABIC_INDIC = from(0x0660)
    val EXTENDED_ARABIC_INDIC = from(0x06F0)
    val BENGALI = from(0x09E6)
    val DEVANAGARI = from(0x0966)
    val CHAKMA = from(0x11136)

    private fun codePointString(cp: Int): String =
        if (cp < 0x10000) {
            Char(cp).toString()
        } else {
            val v = cp - 0x10000
            charArrayOf(Char(0xD800 + (v shr 10)), Char(0xDC00 + (v and 0x3FF))).concatToString()
        }
}

/** A format that writes its numbers in [digits] and does nothing else a test here looks at. */
internal class DigitSetFormat(private val digits: List<String>, private val tag: String = "und") : PlatformFormat {
    override fun languageTag(): String = tag
    override fun localizedDigits(number: Int): String = number.toString().map { digits[it - '0'] }.joinToString("")
    override fun clockTime(hour: Int, minute: Int): String =
        "${localizedDigits(hour)}:${localizedDigits(minute / 10)}${localizedDigits(minute % 10)}"
}
