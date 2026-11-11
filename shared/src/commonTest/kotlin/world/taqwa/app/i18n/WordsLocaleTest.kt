package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The words locale of the watch feed (plan 2, Task 1, the Lead's call 3): the phone's own locale
 * gives the date words only when the tag's own language is the interface language. The test is the
 * tag's primary subtag, never `UiLanguage.of(tag)`, which answers English for every language
 * outside the seven, so a Hebrew or Persian phone on the English interface would pass it.
 */
class WordsLocaleTest {

    @Test
    fun theWordsLocaleIsTheTagsOwnLanguageOnly() {
        val own = listOf(
            "en-US" to UiLanguage.ENGLISH,
            "en" to UiLanguage.ENGLISH,
            "ar-SA" to UiLanguage.ARABIC,
            "ar_SA" to UiLanguage.ARABIC,
            "id-ID" to UiLanguage.INDONESIAN,
            "in-ID" to UiLanguage.INDONESIAN,
            "ur-PK-u-nu-arabext" to UiLanguage.URDU,
            "bn-BD" to UiLanguage.BENGALI,
        )
        for ((tag, language) in own) assertTrue(isTagsOwnLanguage(tag, language), "$tag with ${language.code}")

        val other = listOf(
            "he-IL" to UiLanguage.ENGLISH,
            "hi-IN" to UiLanguage.ENGLISH,
            "fa-IR" to UiLanguage.ENGLISH,
            "mr-IN" to UiLanguage.ENGLISH,
            "zh-CN" to UiLanguage.ENGLISH,
            "zh-Hans-CN" to UiLanguage.ENGLISH,
            "he_IL" to UiLanguage.ENGLISH,
            "az-AZ" to UiLanguage.ENGLISH,
            "ar-SA" to UiLanguage.ENGLISH,
            "en-US" to UiLanguage.ARABIC,
        )
        for ((tag, language) in other) assertFalse(isTagsOwnLanguage(tag, language), "$tag with ${language.code}")
    }

    @Test
    fun theWordsLocaleTagIsTheTagOrTheLanguage() {
        assertEquals("tr-TR", wordsLocaleTag("tr-TR", UiLanguage.TURKISH))
        assertEquals("tr-TR", wordsLocaleTag("tr_TR", UiLanguage.TURKISH))
        assertEquals("ar-SA", wordsLocaleTag("ar-SA", UiLanguage.ARABIC))
        assertEquals("in-ID", wordsLocaleTag("in-ID", UiLanguage.INDONESIAN))
        assertEquals("en", wordsLocaleTag("az-AZ", UiLanguage.ENGLISH))
        assertEquals("en", wordsLocaleTag("he-IL", UiLanguage.ENGLISH))
        assertEquals("en", wordsLocaleTag("ar-SA", UiLanguage.ENGLISH))
        assertEquals("ar", wordsLocaleTag("en-US", UiLanguage.ARABIC))
    }
}
