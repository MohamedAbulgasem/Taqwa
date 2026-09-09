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
