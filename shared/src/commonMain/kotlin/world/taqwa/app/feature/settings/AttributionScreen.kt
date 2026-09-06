package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider

private data class Credit(val what: String, val detail: String, val source: String? = null)

/** The contents of `docs/ATTRIBUTION.md`. GeoNames' CC BY 4.0 makes this screen an obligation. */
private val Credits = listOf(
    Credit(
        what = "Prayer time calculation",
        detail = "Adhan by Batoul Apps, MIT licence.",
        source = "github.com/batoulapps/adhan-kotlin",
    ),
    Credit(
        what = "City database",
        detail = "GeoNames cities15000, CC BY 4.0.",
        source = "geonames.org",
    ),
    Credit(
        what = "Manrope typeface",
        detail = "SIL Open Font Licence 1.1.",
    ),
    Credit(
        what = "Adhan and takbir audio",
        detail = "“Beautiful adhan” by Adam-synagda, CC0 1.0, via Wikimedia Commons.",
    ),
)

@Composable
fun AttributionScreen(onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    SettingsScaffold("Attribution & licences", onBack) {
        SettingsNote(
            "Taqwa is built on work other people gave away. Their licences require credit, and " +
                "it is owed regardless.",
        )
        Spacer(Modifier.height(16.dp))
        SettingsCard {
            Credits.forEachIndexed { i, credit ->
                if (i > 0) CardDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(credit.what, style = TaqwaText.rowLabel, color = colors.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(credit.detail, style = TaqwaText.caption, color = colors.textSecondary)
                    if (credit.source != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(credit.source, style = TaqwaText.caption, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}
