package world.taqwa.app.design

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TypeTest {
    @Test
    fun countdownUsesLightWeightForTheBigNumeral() {
        assertEquals(FontWeight.Light, TaqwaText.Latin.countdown.fontWeight)
    }

    @Test
    fun rowLabelAndRowTimeShareASize() {
        assertEquals(TaqwaText.Latin.rowLabel.fontSize, TaqwaText.Latin.rowTime.fontSize)
    }

    /**
     * Not a style preference. Android does not support letter spacing for complex scripts, and
     * leaving the spec's Latin tracking on an Arabic run makes Compose measure it several times
     * too wide — which wrapped "تقوى" in the middle of the word until this rule existed.
     */
    @Test
    fun arabicDropsTheLatinTracking() {
        listOf(TaqwaText.Latin.countdown, TaqwaText.Latin.screenTitle, TaqwaText.Latin.sectionLabel)
            .forEach { tracked ->
                assertNotEquals(TextUnit.Unspecified, tracked.letterSpacing)
                assertEquals(
                    TextUnit.Unspecified,
                    TaqwaText.forScript(tracked, arabic = true).letterSpacing,
                )
            }
    }

    @Test
    fun latinKeepsEveryOtherPropertyExactlyAsTheSpecSetsIt() {
        val title = TaqwaText.Latin.screenTitle
        assertEquals(title, TaqwaText.forScript(title, arabic = false))
        assertEquals(title.fontSize, TaqwaText.forScript(title, arabic = true).fontSize)
        assertEquals(title.fontWeight, TaqwaText.forScript(title, arabic = true).fontWeight)
    }
}
