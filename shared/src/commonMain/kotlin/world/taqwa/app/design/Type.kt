package world.taqwa.app.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import world.taqwa.app.i18n.isLatinScriptUi
import world.taqwa.app.i18n.uiFontFamily
import world.taqwa.app.resources.Manrope_ExtraBold
import world.taqwa.app.resources.Manrope_Light
import world.taqwa.app.resources.Manrope_Regular
import world.taqwa.app.resources.Manrope_SemiBold
import world.taqwa.app.resources.Res

@Composable
fun manropeFamily(): FontFamily = FontFamily(
    Font(Res.font.Manrope_Light, FontWeight.Light),
    Font(Res.font.Manrope_Regular, FontWeight.Normal),
    Font(Res.font.Manrope_SemiBold, FontWeight.SemiBold),
    Font(Res.font.Manrope_ExtraBold, FontWeight.ExtraBold),
)

object TaqwaText {

    /**
     * The styles as the spec specifies them, tracked for Manrope. Read these directly only where
     * there is no composition to ask about the language — the properties below are what screens
     * use, and they are these run through [forScript].
     */
    object Latin {
        /**
         * Manrope's default figures are proportional (its "1" is 40% narrower than its "0"), so a
         * ticking countdown would shift sideways every second. The font ships tabular figures under
         * `tnum`; every style that shows a time asks for them.
         */
        const val TABULAR = "tnum"

        val countdown = TextStyle(fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
        val screenTitle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.02).em)
        val rowLabel = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        val rowTime = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, fontFeatureSettings = TABULAR)
        val sectionLabel = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.14.em)
        val caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp)
    }

    /**
     * Arabic gets no tracking. Letter spacing is a Latin display refinement; Arabic is cursive,
     * and the platforms say as much — Android does not support letter spacing for complex
     * scripts. Leaving the spec's −0.02em on an Arabic string does not merely look wrong: it
     * makes Compose measure the run several times its real width, so "تقوى" wrapped in the
     * middle of the word on the welcome screen and "اختر مدينة" broke across two lines in a
     * title with 350dp to spare. Dropping the tracking is both the right typography and the fix.
     */
    fun forScript(style: TextStyle, arabic: Boolean): TextStyle =
        if (arabic && style.letterSpacing != TextUnit.Unspecified) {
            style.copy(letterSpacing = TextUnit.Unspecified)
        } else {
            style
        }

    val countdown: TextStyle @Composable get() = forScript(Latin.countdown, !isLatinScriptUi())
    val screenTitle: TextStyle @Composable get() = forScript(Latin.screenTitle, !isLatinScriptUi())
    val rowLabel: TextStyle @Composable get() = forScript(Latin.rowLabel, !isLatinScriptUi())
    val rowTime: TextStyle @Composable get() = forScript(Latin.rowTime, !isLatinScriptUi())
    val sectionLabel: TextStyle @Composable get() = forScript(Latin.sectionLabel, !isLatinScriptUi())
    val caption: TextStyle @Composable get() = forScript(Latin.caption, !isLatinScriptUi())
}

/**
 * Manrope for Latin; the OS face for Arabic, which Manrope has no glyphs for at all (spec §3).
 * Applied here, once, rather than per `Text`: Material3 hands this typography's `bodyLarge` down
 * as `LocalTextStyle`, so every `Text` in the app inherits the right face without asking.
 */
@Composable
fun TaqwaTypography(): Typography {
    val family = uiFontFamily()
    // Every non-Latin script, not only Arabic: Bengali is upright and left-to-right and still has
    // no business carrying Manrope's tracking.
    val arabic = !isLatinScriptUi()
    val base = Typography()

    // Material3 hands `bodyLarge` down as LocalTextStyle, and its own default carries 0.5sp of
    // tracking. A style of ours that leaves letterSpacing unspecified inherits that — so the
    // Arabic rule has to be applied here too, not only to TaqwaText.
    fun face(style: TextStyle) = TaqwaText.forScript(style.copy(fontFamily = family), arabic)

    return Typography(
        displayLarge = face(base.displayLarge),
        headlineMedium = face(base.headlineMedium),
        titleMedium = face(base.titleMedium),
        bodyLarge = face(base.bodyLarge),
        bodyMedium = face(base.bodyMedium),
        labelSmall = face(base.labelSmall),
    )
}
