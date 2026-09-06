package world.taqwa.app.feature.qibla

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaCard

@Composable
fun QiblaScreen(state: QiblaUiState, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            QiblaUiState.NoSensor -> {
                Text("No compass sensor", style = TaqwaText.screenTitle, textAlign = TextAlign.Center)
                Text(
                    "This device has no magnetometer. Use a physical compass together with the " +
                        "bearing below.",
                    style = TaqwaText.caption, color = colors.textSecondary, textAlign = TextAlign.Center,
                )
            }
            is QiblaUiState.Searching -> {
                QiblaDial(state.headingDegrees, state.bearingDegrees, aligned = false, dimmed = false)
                BearingAndDistance(state.bearingDegrees, state.distanceKm)
            }
            is QiblaUiState.Aligned -> {
                QiblaDial(state.headingDegrees, state.bearingDegrees, aligned = true, dimmed = false)
                BearingAndDistance(state.bearingDegrees, state.distanceKm)
            }
            is QiblaUiState.LowAccuracy -> {
                QiblaDial(0.0, state.bearingDegrees, aligned = false, dimmed = true)
                TaqwaCard(Modifier.padding(top = 16.dp)) {
                    Text(
                        "Move your phone in a figure-eight motion. Metal, cases and speakers " +
                            "can interfere with the compass.",
                        style = TaqwaText.caption, color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BearingAndDistance(bearingDegrees: Double, distanceKm: Double) {
    Text(
        "${bearingDegrees.toInt()}° · ${distanceKm.toInt()} km to Makkah",
        style = TaqwaText.caption,
        color = LocalTaqwaColors.current.textSecondary,
        modifier = Modifier.padding(top = 16.dp),
    )
}
