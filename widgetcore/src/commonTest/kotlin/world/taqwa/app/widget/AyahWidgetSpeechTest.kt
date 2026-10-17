package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class AyahWidgetSpeechTest {

    @Test
    fun theCardIsItsNameItsReferenceThenItsTranslation() {
        assertEquals(
            "Ayah of the day, Ar-Ra’d, ayah 28. Those who have believed and whose hearts are assured.",
            AyahWidgetSpeech.label("Ayah of the day", "Ar-Ra’d, ayah 28", "Those who have believed and whose hearts are assured."),
        )
    }

    @Test
    fun withoutATranslationTheSentenceStopsAtTheReference() {
        assertEquals("آية اليوم, الرعد، الآية ٢٨", AyahWidgetSpeech.label("آية اليوم", "الرعد، الآية ٢٨", null))
        assertEquals("Ayah of the day, Ar-Ra’d, ayah 28", AyahWidgetSpeech.label("Ayah of the day", "Ar-Ra’d, ayah 28", " "))
    }
}
