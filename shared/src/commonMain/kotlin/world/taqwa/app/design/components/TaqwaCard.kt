package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText

@Composable
fun TaqwaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
fun CardDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalTaqwaColors.current.hairline))
}

/**
 * [subtitle] is for a caveat the row itself cannot express — a known limitation of the option,
 * not a restatement of it. Left null, the row is exactly as it was.
 */
@Composable
fun TaqwaRow(
    label: String,
    value: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (subtitle == null) {
            Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
        } else {
            Column(Modifier.weight(1f, fill = false).padding(end = 12.dp)) {
                Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary)
                Text(subtitle, style = TaqwaText.caption, color = colors.textSecondary)
            }
        }
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (value != null) {
                Text(
                    value,
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
                )
            }
            trailing?.invoke()
        }
    }
}
