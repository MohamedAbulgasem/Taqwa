package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutDirectionTest {

    @Test
    fun arabicAndItsRegionalVariantsAreRtl() {
        listOf("ar", "ar-EG", "ar-LY", "ar-SA").forEach { assertTrue(LayoutDirection.isRtl(it), it) }
    }

    @Test
    fun hebrewFarsiAndUrduAreAlsoRtl() {
        listOf("he", "fa", "ur").forEach { assertTrue(LayoutDirection.isRtl(it), it) }
    }

    @Test
    fun englishAndFrenchAreLtr() {
        listOf("en", "en-US", "fr").forEach { assertFalse(LayoutDirection.isRtl(it), it) }
    }

    @Test
    fun theCheckIsCaseInsensitive() {
        assertTrue(LayoutDirection.isRtl("AR-EG"))
    }
}
