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
    /** Settings › Prayer times › Timetable (spec §2.2): Automatic, the timetables used nearby, Other methods. */
    data object Timetable : Screen

    /** Settings › Prayer times › Timetable › Other methods: the eleven that apply anywhere. */
    data object OtherMethods : Screen

    /** Match my mosque (spec §2.2): the board's Fajr and Isha against the timetables Taqwa knows. */
    data object MatchMyMosque : Screen
    data object HighLatitudePicker : Screen
    data object ManualAdjustments : Screen

    /** "About these times" (spec §2.3, task 11): whose times these are, and how sure Taqwa is of
     * them. Reached from Settings › Prayer times, and — once Task 10 wires it — from the Prayer
     * screen's ⓘ card and "Cautious times" line. */
    data object AboutTimes : Screen
    data object LocationSettings : Screen
    data object CitySearch : Screen
    data object Appearance : Screen

    /** Settings › Quran › Recitation (spec 3a §5.6): the voice, mobile data, and what is stored. */
    data object RecitationSettings : Screen

    /** The surahs of one reciter on this phone, with the deletes (spec 3a §5.6). */
    data class RecitationDownloads(val reciterId: String) : Screen
    /** Settings › About Taqwa (privacy spec §6): the promise, and where to check it. */
    data object About : Screen
    data object Attribution : Screen
    data object Qibla : Screen

    /** The dhikr counter, pushed from the misbaha in the Prayer screen's header (spec §4). */
    data object Tasbeeh : Screen
}
