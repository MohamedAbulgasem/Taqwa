package world.taqwa.app.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
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
    val countdown = TextStyle(fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-0.02).em)
    val screenTitle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.02).em)
    val rowLabel = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val rowTime = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp)
    val sectionLabel = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.14.em)
    val caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp)
}

@Composable
fun TaqwaTypography(): Typography {
    val family = manropeFamily()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
