package world.taqwa.app.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.uthmanic_hafs

/**
 * Quran text uses only the bundled Uthmanic Hafs Mushaf font (spec §5) — never the UI's Manrope
 * or the OS Arabic face, which do not carry the Hafs riwayah's glyph shapes or the ayah-end
 * digits that [world.taqwa.app.quran.QuranText.withMarker] relies on.
 */
@Composable
fun mushafFamily(): FontFamily = FontFamily(Font(Res.font.uthmanic_hafs))

/**
 * The Quran text style (spec §5): a fixed 2.0x line-height keeps the Hafs glyphs' large stacked
 * diacritics from clipping into the line above or below, right-aligned and read right-to-left
 * regardless of the surrounding UI language. The font family is applied at the call site — via
 * [mushafFamily] — because building a [FontFamily] from resources needs composition.
 */
fun TaqwaText.quran(sizeSp: Int): TextStyle = TextStyle(
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 2).sp,
    textAlign = TextAlign.Right,
    textDirection = TextDirection.Rtl,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)
