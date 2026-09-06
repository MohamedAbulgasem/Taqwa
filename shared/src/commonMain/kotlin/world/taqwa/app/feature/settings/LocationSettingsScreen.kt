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
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.location.LocationPermission
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.rememberLocationPermissionRequester

/**
 * The timezone is on screen deliberately: when prayer times look wrong by a whole hour, a city
 * whose IANA zone does not match where the user actually is, is almost always the reason, and it
 * is invisible everywhere else in the app.
 */
@Composable
fun LocationSettingsScreen(
    location: GeoLocation?,
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

    // A stored location carries a city name only when it was chosen by hand; a GPS fix has none.
    val usingGps = permission == LocationPermission.GRANTED &&
        location != null &&
        location.cityName == null

    SettingsScaffold("Location", onBack) {
        SettingsCard {
            TaqwaRow(
                "Use my location",
                trailing = {
                    TaqwaToggle(usingGps) { wantsGps ->
                        // There is always a location in force, so switching off is not "no
                        // location" — it is "a city I pick myself", which is the same
                        // destination as the row below.
                        if (wantsGps) requestLocation() else onChooseCity()
                    }
                },
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("IN USE")
        SettingsCard {
            TaqwaRow("City", value = location?.cityName ?: "Current location")
            CardDivider()
            TaqwaRow("Country", value = location?.countryCode ?: "—")
            CardDivider()
            TaqwaRow("Time zone", value = location?.timeZoneId ?: "—")
        }

        Spacer(Modifier.height(16.dp))
        SettingsCard {
            TaqwaRow("Choose a city instead", onClick = onChooseCity)
        }

        Spacer(Modifier.height(16.dp))
        SettingsNote(
            "Coordinates are stored on your device and used only to calculate times. " +
                "Nothing is sent anywhere.",
        )

        if (permission == LocationPermission.DENIED) {
            Spacer(Modifier.height(12.dp))
            SettingsNote(
                "Location access is currently denied. You can grant it in your device settings, " +
                    "or choose a city instead.",
            )
        }
    }
}
