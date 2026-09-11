package world.taqwa.app.nav

sealed interface Screen {
    data object Onboarding : Screen
    data object Today : Screen
    data object Quran : Screen
    /**
     * [selectAyah] opens the reader with [ayah] already selected — its card tinted and its copy,
     * share and bookmark row showing — rather than merely scrolled to. Set when the ayah is what
     * the user tapped (the widget), not when the surah is (the tab root, the continue card).
     */
    data class Reader(val surah: Int, val ayah: Int, val selectAyah: Boolean = false) : Screen

    /**
     * [highlightSurah] and [highlightAyah] open the page with that ayah already highlighted and
     * its reference bar showing, the state a tap on the page itself would produce. Both null when
     * the page, not an ayah, is what was asked for.
     */
    data class Mushaf(
        val page: Int,
        val highlightSurah: Int? = null,
        val highlightAyah: Int? = null,
    ) : Screen
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

    /** The dhikr counter, pushed from the misbaha in the Prayer screen's header (spec §4). */
    data object Tasbeeh : Screen
}
