package world.taqwa.app.watchfeed

import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.isTagsOwnLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tags the face is measured for, and the digit sets each one may carry in the feed, which
 * Task 2's forms and Task 3's size test both read.
 */
class PhoneTagsTest {

    @Test
    fun everyTagIsItsOwnLanguageAndEveryFallbackIsOutsideTheSeven() {
        for (tag in PhoneTags.TAGS) {
            assertTrue(isTagsOwnLanguage(tag, UiLanguage.of(tag)), tag)
        }
        for (tag in PhoneTags.FALLBACK_TAGS) {
            assertEquals(UiLanguage.ENGLISH, UiLanguage.of(tag), tag)
            assertFalse(isTagsOwnLanguage(tag, UiLanguage.ENGLISH), tag)
        }
        assertTrue(PhoneTags.TAGS.intersect(PhoneTags.FALLBACK_TAGS.toSet()).isEmpty())
        for (language in UiLanguage.entries) {
            assertTrue(PhoneTags.TAGS.any { UiLanguage.of(it) == language }, "no tag for ${language.code}")
        }
    }

    @Test
    fun theNumberingTagsAreTheTagThenItsOtherSets() {
        assertEquals(listOf("ar-LY", "ar-LY-u-nu-latn", "ar-LY-u-nu-arab"), PhoneTags.numberingTags("ar-LY"))
        assertEquals(listOf("ur-IN", "ur-IN-u-nu-latn", "ur-IN-u-nu-arabext"), PhoneTags.numberingTags("ur-IN"))
        for (tag in listOf("bn-BD", "en-GB", "fa-IR")) {
            assertEquals(listOf(tag), PhoneTags.numberingTags(tag))
        }
    }

    /**
     * A tag that already names its digits, as a phone's own tag may (the feed's `ui.language`
     * keeps `ar-SA-u-nu-latn` as the phone has it), is never given a second `-u-` extension, which
     * no locale reads: its other sets are named on the tag without its extension, each once.
     */
    @Test
    fun aTagThatAlreadyNamesItsDigitsIsNotGivenASecondExtension() {
        assertEquals(listOf("ar-SA-u-nu-latn", "ar-SA-u-nu-arab"), PhoneTags.numberingTags("ar-SA-u-nu-latn"))
        assertEquals(listOf("ar-LY-u-nu-arab", "ar-LY-u-nu-latn"), PhoneTags.numberingTags("ar-LY-u-nu-arab"))
        assertEquals(listOf("ur-PK-u-nu-arabext", "ur-PK-u-nu-latn"), PhoneTags.numberingTags("ur-PK-u-nu-arabext"))
        assertEquals(
            listOf("ar-EG-u-ca-islamic", "ar-EG-u-nu-latn", "ar-EG-u-nu-arab"),
            PhoneTags.numberingTags("ar-EG-u-ca-islamic"),
        )
        for (tag in listOf("ar-SA-u-nu-latn", "ar-LY-u-nu-arab", "ur-PK-u-nu-arabext", "ar-EG-u-ca-islamic", "AR-SA-U-NU-LATN")) {
            for (t in PhoneTags.numberingTags(tag)) {
                assertEquals(1, Regex("-u-", RegexOption.IGNORE_CASE).findAll(t).count(), t)
            }
        }
    }

    @Test
    fun aNumberingTagKeepsItsTagsLanguage() {
        for (tag in PhoneTags.TAGS + PhoneTags.FALLBACK_TAGS) {
            val language = UiLanguage.of(tag)
            for (t in PhoneTags.numberingTags(tag)) {
                assertEquals(language, UiLanguage.of(t), t)
                assertEquals(isTagsOwnLanguage(tag, language), isTagsOwnLanguage(t, language), t)
            }
        }
    }

    @Test
    fun theDigitSetsAreTheFeedsOwnInOrder() {
        val western = DigitSets.WESTERN.joinToString("")
        val arabicIndic = DigitSets.ARABIC_INDIC.joinToString("")
        val extended = DigitSets.EXTENDED_ARABIC_INDIC.joinToString("")
        val bengali = DigitSets.BENGALI.joinToString("")

        assertEquals(
            listOf(PhoneDigitSet("ar-SA", arabicIndic), PhoneDigitSet("ar-SA-u-nu-latn", western)),
            PhoneTags.digitSets("ar-SA", writing(DigitSets.ARABIC_INDIC)),
        )
        assertEquals(
            listOf(PhoneDigitSet("ar-LY", western), PhoneDigitSet("ar-LY-u-nu-arab", arabicIndic)),
            PhoneTags.digitSets("ar-LY", writing(DigitSets.WESTERN)),
        )
        assertEquals(
            listOf(PhoneDigitSet("ur-IN", extended), PhoneDigitSet("ur-IN-u-nu-latn", western)),
            PhoneTags.digitSets("ur-IN", writing(DigitSets.EXTENDED_ARABIC_INDIC)),
        )
        assertEquals(
            listOf(PhoneDigitSet("bn-BD", bengali)),
            PhoneTags.digitSets("bn-BD", writing(DigitSets.BENGALI)),
        )
        assertEquals(
            listOf(PhoneDigitSet("fa-IR", western)),
            PhoneTags.digitSets("fa-IR", writing(DigitSets.EXTENDED_ARABIC_INDIC)),
        )
        assertEquals(
            listOf(PhoneDigitSet("mr-IN", western)),
            PhoneTags.digitSets("mr-IN", writing(DigitSets.DEVANAGARI)),
        )
    }

    /**
     * A runtime whose formats write [own] for a bare tag, and the set a `-u-nu-` extension names
     * for a tag that carries one.
     */
    private fun writing(own: List<String>): (String) -> DigitSetFormat = { tag ->
        val set = when {
            tag.endsWith("-u-nu-latn") -> DigitSets.WESTERN
            tag.endsWith("-u-nu-arabext") -> DigitSets.EXTENDED_ARABIC_INDIC
            tag.endsWith("-u-nu-arab") -> DigitSets.ARABIC_INDIC
            else -> own
        }
        DigitSetFormat(set, tag)
    }
}
