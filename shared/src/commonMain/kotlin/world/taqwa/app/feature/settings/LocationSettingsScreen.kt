package world.taqwa.app.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.LocationSource
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.rememberLocationPermissionRequester
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.location_choose_city_instead
import world.taqwa.app.resources.location_city
import world.taqwa.app.resources.location_country
import world.taqwa.app.resources.location_denied_note
import world.taqwa.app.resources.location_in_use
import world.taqwa.app.resources.location_privacy_note
import world.taqwa.app.resources.location_timezone
import world.taqwa.app.resources.location_use_my_location
import world.taqwa.app.resources.settings_location
import world.taqwa.app.resources.today_current_location

/**
 * The timezone is on screen deliberately: when prayer times look wrong by a whole hour, a city
 * whose IANA zone does not match where the user actually is, is almost always the reason, and it
 * is invisible everywhere else in the app.
 */
@Composable
fun LocationSettingsScreen(
    location: GeoLocation?,
    /**
     * The city's name in the interface language, resolved once in `App.kt` and passed down so
     * this screen and the Settings row it is reached from can never disagree. Null when there is
     * no location, or none whose city the bundle knows a name for.
     */
    cityName: String?,
    locationSource: LocationSource,
    locationRepository: LocationRepository,
    onLocationPermission: (LocationPermission) -> Unit,
    onChooseCity: () -> Unit,
    onBack: () -> Unit,
) {
    var permission by remember { mutableStateOf(LocationPermission.NOT_REQUESTED) }
    LaunchedEffect(location) { permission = locationRepository.permission() }

    val requestLocation = rememberLocationPermissionRequester(locationRepository) { granted ->
        permission = granted
        onLocationPermission(granted)
    }

    SettingsScaffold(stringResource(Res.string.settings_location), onBack) {
        SettingsCard {
            TaqwaRow(
                stringResource(Res.string.location_use_my_location),
                trailing = {
                    // Read from the stored preference, not guessed from the location: a fix taken
                    // near a bundled city carries that city's name too, which is why the old
                    // guess ("no city name means GPS") read OFF for almost everyone who had it on.
                    TaqwaToggle(locationSource == LocationSource.GPS) { wantsGps ->
                        // There is always a location in force, so switching off is not "no
                        // location" — it is "a city I pick myself", which is the same
                        // destination as the row below. The source flips to MANUAL only when a
                        // city is actually picked, so backing out of the search leaves this on.
                        if (wantsGps) requestLocation() else onChooseCity()
                    }
                },
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.location_in_use))
        SettingsCard {
            // An em dash for "nothing yet" rather than a translated word: it reads the same in
            // both languages, and a country code or an IANA id is never translated either.
            TaqwaRow(
                stringResource(Res.string.location_city),
                value = cityName ?: stringResource(Res.string.today_current_location),
            )
            CardDivider()
            TaqwaRow(stringResource(Res.string.location_country), value = location?.countryCode ?: "-")
            CardDivider()
            TaqwaRow(stringResource(Res.string.location_timezone), value = location?.timeZoneId ?: "-")
        }

        Spacer(Modifier.height(16.dp))
        SettingsCard {
            TaqwaRow(stringResource(Res.string.location_choose_city_instead), onClick = onChooseCity)
        }

        Spacer(Modifier.height(16.dp))
        SettingsNote(stringResource(Res.string.location_privacy_note))

        if (permission == LocationPermission.DENIED) {
            Spacer(Modifier.height(12.dp))
            SettingsNote(stringResource(Res.string.location_denied_note))
        }
    }
}
