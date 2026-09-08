package world.taqwa.app.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class QuranModelsTest {

    private val surah = Surah(18, "الكهف", "Al-Kahf", "The Cave", Revelation.MAKKI, 110, 293, 15)

    @Test
    fun displayNameIsArabicUnderAnArabicUi() {
        assertEquals("الكهف", surah.displayName(rtl = true))
    }

    @Test
    fun displayNameIsLatinUnderALatinUi() {
        assertEquals("Al-Kahf", surah.displayName(rtl = false))
    }
}
