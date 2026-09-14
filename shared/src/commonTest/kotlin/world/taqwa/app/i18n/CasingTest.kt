package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class CasingTest {
    @Test
    fun turkishDotsItsCapitalIAndUndotsTheOther() {
        assertEquals("İKİNDİ VAKTİNE KALAN", "İkindi vaktine kalan".uppercaseIn(UiLanguage.TURKISH))
        assertEquals("ISPARTA", "ısparta".uppercaseIn(UiLanguage.TURKISH))
    }

    @Test
    fun otherLatinLanguagesUppercasePlainlyAndOtherScriptsAreLeftAlone() {
        assertEquals("FAJR IN", "Fajr in".uppercaseIn(UiLanguage.ENGLISH))
        assertEquals("LEVER DU SOLEIL", "Lever du soleil".uppercaseIn(UiLanguage.FRENCH))
        assertEquals("فجر میں باقی", "فجر میں باقی".uppercaseIn(UiLanguage.URDU))
        assertEquals("ফজর পর্যন্ত বাকি", "ফজর পর্যন্ত বাকি".uppercaseIn(UiLanguage.BENGALI))
    }


    @Test
    fun turkishLowerCasingKeepsTheDots() {
        assertEquals("iman", "İMAN".lowercaseIn(UiLanguage.TURKISH))
        assertEquals("ışık", "IŞIK".lowercaseIn(UiLanguage.TURKISH))
        assertEquals("islam", "ISLAM".lowercaseIn(UiLanguage.ENGLISH))
        // Latin letters inside a non-Latin translation still fold.
        assertEquals("allah", "Allah".lowercaseIn(UiLanguage.URDU))
    }
}
