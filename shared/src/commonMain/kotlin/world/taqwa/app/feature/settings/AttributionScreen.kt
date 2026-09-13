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
import world.taqwa.app.quran.TextKind
import world.taqwa.app.quran.TranslationInfo
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.attribution_intro
import world.taqwa.app.resources.credit_audio
import world.taqwa.app.resources.credit_audio_detail
import world.taqwa.app.resources.credit_chime
import world.taqwa.app.resources.credit_chime_detail
import world.taqwa.app.resources.credit_calculation
import world.taqwa.app.resources.credit_calculation_detail
import world.taqwa.app.resources.credit_cities
import world.taqwa.app.resources.credit_cities_detail
import world.taqwa.app.resources.credit_quran_font
import world.taqwa.app.resources.credit_quran_font_detail
import world.taqwa.app.resources.credit_quran_layout
import world.taqwa.app.resources.credit_quran_layout_detail
import world.taqwa.app.resources.credit_quran_text
import world.taqwa.app.i18n.uiLanguage
import world.taqwa.app.recitation.Reciter
import world.taqwa.app.resources.credit_quran_text_detail
import world.taqwa.app.resources.credit_recitations
import world.taqwa.app.resources.credit_recitations_detail
import world.taqwa.app.resources.credit_translations
import world.taqwa.app.resources.credit_translations_detail
import world.taqwa.app.resources.credit_typeface
import world.taqwa.app.resources.credit_typeface_detail
import world.taqwa.app.resources.quran_sheet_transliteration
import world.taqwa.app.resources.settings_attribution

private data class Credit(
    val what: String,
    val detail: String,
    val source: String? = null,
    /** A second paragraph under the detail, in the same tint: the ten reciters, named. */
    val extra: String? = null,
)

/**
 * The contents of `docs/ATTRIBUTION.md`. GeoNames' CC BY 4.0 makes this screen an obligation.
 * The source lines stay literal: a domain name is the same in every language, and translating
 * one would break the very reference it exists to give.
 */
@Composable
private fun credits(translations: List<TranslationInfo>, reciters: List<Reciter>): List<Credit> = listOf(
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
    Credit(
        what = stringResource(Res.string.credit_chime),
        detail = stringResource(Res.string.credit_chime_detail),
        source = "mixkit.co",
    ),
    Credit(
        what = stringResource(Res.string.credit_quran_text),
        detail = stringResource(Res.string.credit_quran_text_detail),
        source = "tanzil.net",
    ),
    Credit(
        what = stringResource(Res.string.credit_quran_font),
        detail = stringResource(Res.string.credit_quran_font_detail),
        source = "qurancomplex.gov.sa",
    ),
    Credit(
        what = stringResource(Res.string.credit_quran_layout),
        detail = stringResource(Res.string.credit_quran_layout_detail),
        source = "github.com/zonetecde/mushaf-layout",
    ),
    Credit(
        what = stringResource(Res.string.credit_translations),
        detail = stringResource(Res.string.credit_translations_detail),
        source = "tanzil.net/trans",
    ),
    Credit(
        what = stringResource(Res.string.credit_recitations),
        detail = stringResource(Res.string.credit_recitations_detail),
        source = "alquran.cloud",
        // Read from the catalogue in force, never written down here, for the same reason the
        // translations below are read from the database: a reciter withdrawn from the manifest
        // (spec §2's one obligation we can be held to) disappears from the app *and* from its
        // credits, with no app update and no line left behind naming someone we no longer carry.
        extra = uiLanguage().arabicScript.let { arabic ->
            reciters.joinToString(if (arabic) "، " else ", ") { if (arabic) it.nameAr else it.nameEn }
        }.takeIf { it.isNotBlank() },
    ),
) + translations.map { translation ->
    // Read from the database, never hardcoded, so this list can never drift from what is
    // actually bundled (spec §6).
    // A translation's name is its own title in its own language and stays as stored; the
    // transliteration's stored name is the English word for what it is, so that one row takes the
    // localized label the reading sheet already uses instead.
    val what = if (translation.kind == TextKind.TRANSLITERATION) {
        stringResource(Res.string.quran_sheet_transliteration)
    } else {
        translation.name
    }
    Credit(what = what, detail = translation.translator)
}

@Composable
fun AttributionScreen(
    translations: List<TranslationInfo>,
    reciters: List<Reciter>,
    onBack: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val credits = credits(translations, reciters)
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
                    if (credit.extra != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(credit.extra, style = TaqwaText.caption, color = colors.textSecondary)
                    }
                    if (credit.source != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(credit.source, style = TaqwaText.caption, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}
