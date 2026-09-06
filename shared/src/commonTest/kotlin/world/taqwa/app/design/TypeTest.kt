package world.taqwa.app.design

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeTest {
    @Test
    fun countdownUsesLightWeightForTheBigNumeral() {
        assertEquals(FontWeight.Light, TaqwaText.countdown.fontWeight)
    }

    @Test
    fun rowLabelAndRowTimeShareASize() {
        assertEquals(TaqwaText.rowLabel.fontSize, TaqwaText.rowTime.fontSize)
    }
}
