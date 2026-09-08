package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageNameTest {

    /** The seven languages the app bundles a Quran translation in; the picker labels every row
     * with one of these, so a gap here is a blank-looking row on the device. */
    @Test
    fun englishNamesEveryBundledTranslationLanguage() {
        assertEquals("English", EnglishPlatformFormat.languageName("en"))
        assertEquals("Arabic", EnglishPlatformFormat.languageName("ar"))
        assertEquals("Indonesian", EnglishPlatformFormat.languageName("id"))
        assertEquals("Urdu", EnglishPlatformFormat.languageName("ur"))
        assertEquals("Bengali", EnglishPlatformFormat.languageName("bn"))
        assertEquals("Turkish", EnglishPlatformFormat.languageName("tr"))
        assertEquals("French", EnglishPlatformFormat.languageName("fr"))
    }

    // The database's language column is not ours to police, so an unknown code names the row after
    // itself rather than leaving it empty.
    @Test
    fun anUnknownCodeFallsBackToTheCodeItself() {
        assertEquals("sw", EnglishPlatformFormat.languageName("sw"))
        assertEquals("", EnglishPlatformFormat.languageName(""))
    }

    @Test
    fun theCodeIsMatchedCaseInsensitively() {
        assertEquals("Urdu", EnglishPlatformFormat.languageName("UR"))
    }

    // The interface default exists so the test fakes need not implement it; like longDate it must
    // be the English rendering, not the bare code.
    @Test
    fun theInterfaceDefaultIsTheEnglishMap() {
        val fake = object : PlatformFormat {
            override fun languageTag() = "xx"
            override fun localizedDigits(number: Int) = number.toString()
            override fun clockTime(hour: Int, minute: Int) = "$hour:$minute"
        }
        assertEquals("Bengali", fake.languageName("bn"))
    }
}
