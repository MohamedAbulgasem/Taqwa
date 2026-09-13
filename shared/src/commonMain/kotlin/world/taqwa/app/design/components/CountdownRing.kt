package world.taqwa.app.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.i18n.uppercaseIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText

/** The ring's own diameter on a phone held upright — the size everything below is drawn against. */
val CountdownRingSize = 196.dp

@Composable
fun CountdownRing(
    progress: Float,
    label: String,
    countdown: String,
    clockTime: String,
    modifier: Modifier = Modifier,
    diameter: Dp = CountdownRingSize,
) {
    val colors = LocalTaqwaColors.current
    // [diameter] is only ever *smaller* than the default, when a sideways screen gives the ring
    // less than its own half-page to sit in; the stroke keeps its share of the diameter, so a
    // shrunken ring reads as the same ring rather than as a thicker one. The track, the arc and
    // its mirrored sweep live in [RingArc], which the tasbeeh's counter draws too.
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        RingArc(progress = progress, diameter = diameter)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.uppercaseIn(uiLanguage()),
                style = TaqwaText.sectionLabel.copy(fontSize = 11.sp),
                color = colors.accent,
                textAlign = TextAlign.Center,
            )
            // 36sp is the largest size at which the longest string a ring can show, "10:00:00" in
            // tabular Manrope Light, still clears the stroke on both sides (149dp inside 178dp);
            // scaled with the diameter so a landscape-shrunken ring keeps the same proportions.
            Text(
                text = countdown,
                style = TaqwaText.countdown.copy(fontSize = 36.sp * (diameter / CountdownRingSize)),
                color = colors.textPrimary,
            )
            Text(text = clockTime, style = TaqwaText.caption, color = colors.textSecondary)
        }
    }
}
