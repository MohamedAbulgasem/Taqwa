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
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.attribution_intro
import world.taqwa.app.resources.credit_audio
import world.taqwa.app.resources.credit_audio_detail
import world.taqwa.app.resources.credit_calculation
import world.taqwa.app.resources.credit_calculation_detail
import world.taqwa.app.resources.credit_cities
import world.taqwa.app.resources.credit_cities_detail
import world.taqwa.app.resources.credit_typeface
import world.taqwa.app.resources.credit_typeface_detail
import world.taqwa.app.resources.settings_attribution

private data class Credit(val what: String, val detail: String, val source: String? = null)

/**
 * The contents of `docs/ATTRIBUTION.md`. GeoNames' CC BY 4.0 makes this screen an obligation.
 * The source lines stay literal: a domain name is the same in every language, and translating
 * one would break the very reference it exists to give.
 */
@Composable
private fun credits(): List<Credit> = listOf(
    Credit(
        what = stringResource(Res.string.credit_calculation),
        detail = stringResource(Res.string.credit_calculation_detail),
        source = "github.com/batoulapps/adhan-kotlin",
    ),
    Credit(
        what = stringResource(Res.string.credit_cities),
        detail = stringResource(Res.string.credit_cities_detail),
        source = "geonames.org",
    ),
    Credit(
        what = stringResource(Res.string.credit_typeface),
        detail = stringResource(Res.string.credit_typeface_detail),
    ),
    Credit(
        what = stringResource(Res.string.credit_audio),
        detail = stringResource(Res.string.credit_audio_detail),
    ),
)

@Composable
fun AttributionScreen(onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val credits = credits()
    SettingsScaffold(stringResource(Res.string.settings_attribution), onBack) {
        SettingsNote(stringResource(Res.string.attribution_intro))
        Spacer(Modifier.height(16.dp))
        SettingsCard {
            credits.forEachIndexed { i, credit ->
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
