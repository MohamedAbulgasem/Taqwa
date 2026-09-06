package world.taqwa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.city.City
import world.taqwa.app.city.CityRepository
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.city_search_hint
import world.taqwa.app.resources.city_search_no_results
import world.taqwa.app.resources.city_search_placeholder
import world.taqwa.app.resources.city_search_title

/**
 * Every result shows its region and country beneath the name. There are eleven Londons in the
 * database, and picking the wrong one produces plausible-looking times that are quietly wrong
 * forever, with nothing on screen to explain why.
 */
@Composable
fun CitySearchScreen(
    cityRepository: CityRepository,
    onPick: (City) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalTaqwaColors.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<City>()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query) {
        // A short pause keeps the first keystroke from paying for the CSV parse while the user
        // is still typing; the repository caches it after that.
        delay(120)
        results = cityRepository.search(query)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SettingsScaffold(stringResource(Res.string.city_search_title), onBack) {
        Box(
            Modifier
                .padding(horizontal = SettingsGutter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(Res.string.city_search_placeholder),
                            style = TaqwaText.rowLabel,
                            color = colors.textTertiary,
                        )
                    }
                    inner()
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        when {
            query.isBlank() -> SettingsNote(stringResource(Res.string.city_search_hint))
            results.isEmpty() -> SettingsNote(
                stringResource(Res.string.city_search_no_results, query),
            )
            else -> SettingsCard {
                results.forEachIndexed { i, city ->
                    if (i > 0) CardDivider()
                    CityRow(city) { onPick(city) }
                }
            }
        }
    }
}

/** "England, United Kingdom" — or the country alone, never a stray leading comma. */
internal fun cityQualifier(city: City): String =
    listOf(city.region, city.countryName)
        .filter { it.isNotBlank() }
        .joinToString(", ")

@Composable
private fun CityRow(city: City, onClick: () -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(city.name, style = TaqwaText.rowLabel, color = colors.textPrimary)
        Text(cityQualifier(city), style = TaqwaText.caption, color = colors.textSecondary)
    }
}
