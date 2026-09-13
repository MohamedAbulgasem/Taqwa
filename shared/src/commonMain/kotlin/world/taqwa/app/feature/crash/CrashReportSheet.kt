package world.taqwa.app.feature.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.crash_not_now
import world.taqwa.app.resources.crash_send
import world.taqwa.app.resources.crash_sheet_body
import world.taqwa.app.resources.crash_sheet_title

/**
 * Shown once per new crash report, on the launch after the crash (crash spec §3.1). Same chrome
 * as every other sheet; the primary pill sends, the text link is the only other way out besides
 * swiping it away, and both count as the report having been offered.
 */
@Composable
fun CrashReportSheet(onSend: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(Res.string.crash_sheet_title),
                style = TaqwaText.screenTitle.copy(fontSize = 20.sp),
                color = colors.textPrimary,
            )
            Text(
                stringResource(Res.string.crash_sheet_body),
                style = TaqwaText.caption.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = colors.textSecondary,
            )
            TaqwaPrimaryButton(
                stringResource(Res.string.crash_send),
                onClick = onSend,
                modifier = Modifier.padding(top = 6.dp),
            )
            TaqwaTextLink(stringResource(Res.string.crash_not_now), onClick = onDismiss)
        }
    }
}
