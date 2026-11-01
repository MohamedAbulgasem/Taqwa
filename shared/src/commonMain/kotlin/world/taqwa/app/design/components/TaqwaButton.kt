package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors

/**
 * Full-width pill, 48dp tall — above the 44pt minimum with room for large-text settings. A
 * button to TalkBack and VoiceOver too, not only a thing that takes a double-tap.
 *
 * [enabled] false dims it to the disabled alpha and takes the click away outright, ripple
 * included: a button that looks dead but still fires is worse than either.
 *
 * With text larger than the default, the label keeps [LabelInset] clear of each rounded end and
 * wraps centred when it cannot: inside a Widgets card at 360 dp and font scale 1.3, Indonesian's
 * "Tambahkan ke layar utama" ran into the pill's ends. At the default text size the pill is drawn
 * as it always was, the label free to use the whole width and set from its start, because that is
 * Mohamed's rule for accessibility work: it leaves the default look alone. An inset at every size
 * wrapped "Keep" labels in the timetable sheets that fit on one line before (French "Garder Comité
 * d’observation du croissant" at 411 dp, "Keep Islamic Foundation Bangladesh" at 360 dp). Whether
 * the inset and the centring should hold at the default size too is his call (build log, review
 * round 2).
 */
@Composable
fun TaqwaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalTaqwaColors.current
    val largerText = LocalDensity.current.fontScale > 1f
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.textPrimary)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (largerText) LabelInset else 0.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = colors.background,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            textAlign = if (largerText) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

/**
 * How far a larger label stays from each end of the pill: inside the curve of its ends, where the
 * pill is still nearly its full height, so no letter sits on the rounding.
 */
private val LabelInset = 20.dp

/** Dimmed, not greyed: the pill keeps its shape so the sheet does not reflow when it goes live. */
private const val DisabledAlpha = 0.4f

/** The secondary action is never a second button. */
@Composable
fun TaqwaTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
