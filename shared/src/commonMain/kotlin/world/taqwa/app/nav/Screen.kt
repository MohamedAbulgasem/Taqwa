package world.taqwa.app.nav

sealed interface Screen {
    data object Onboarding : Screen
    data object Today : Screen
    data object Quran : Screen
    data class Reader(val surah: Int, val ayah: Int) : Screen
    data class Mushaf(val page: Int) : Screen
    data object Settings : Screen
    data object PrayerTimesSettings : Screen
    data object NotificationSettings : Screen
    data object MethodPicker : Screen
    data object HighLatitudePicker : Screen
    data object ManualAdjustments : Screen
    data object LocationSettings : Screen
    data object CitySearch : Screen
    data object Appearance : Screen
    data object Attribution : Screen
    data object Qibla : Screen
}

/**
 * The Quran tab's own screens: its root and the two ways of reading, translation and Mushaf.
 *
 * Used by the widget-tap handler in `App`, which pushes [Screen.Quran] under the reader unless one
 * of these is already on top, so Back from an ayah opened off the home screen lands on the surah
 * list rather than leaving the Quran for whichever tab happened to be showing (D3, S23 round).
 */
fun isQuranScreen(screen: Screen): Boolean =
    screen is Screen.Quran || screen is Screen.Reader || screen is Screen.Mushaf
