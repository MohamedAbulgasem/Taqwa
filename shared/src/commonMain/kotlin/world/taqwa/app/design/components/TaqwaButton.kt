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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors

/** Full-width pill, 48dp tall — above the 44pt minimum with room for large-text settings. */
@Composable
fun TaqwaPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.textPrimary)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.background, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

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
