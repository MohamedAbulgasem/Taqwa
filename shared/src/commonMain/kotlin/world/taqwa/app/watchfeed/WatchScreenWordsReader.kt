package world.taqwa.app.watchfeed

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.app_name
import world.taqwa.app.resources.prayer_a11y_prayer_current
import world.taqwa.app.resources.prayer_a11y_prayer_next
import world.taqwa.app.resources.prayer_a11y_prayer_passed
import world.taqwa.app.resources.prayer_qibla_detail
import world.taqwa.app.resources.qibla_back
import world.taqwa.app.resources.qibla_bearing
import world.taqwa.app.resources.qibla_best_effort_title
import world.taqwa.app.resources.qibla_cardinal_east
import world.taqwa.app.resources.qibla_cardinal_north
import world.taqwa.app.resources.qibla_cardinal_south
import world.taqwa.app.resources.qibla_cardinal_west
import world.taqwa.app.resources.qibla_distance
import world.taqwa.app.resources.qibla_facing_qibla
import world.taqwa.app.resources.qibla_interference_title
import world.taqwa.app.resources.qibla_needs_calibrating
import world.taqwa.app.resources.qibla_no_sensor_title
import world.taqwa.app.resources.qibla_title
import world.taqwa.app.resources.tasbeeh_of
import world.taqwa.app.resources.tasbeeh_preset_after_prayer
import world.taqwa.app.resources.tasbeeh_preset_alhamdulillah
import world.taqwa.app.resources.tasbeeh_preset_allahu_akbar
import world.taqwa.app.resources.tasbeeh_preset_astaghfirullah
import world.taqwa.app.resources.tasbeeh_preset_la_ilaha_illallah
import world.taqwa.app.resources.tasbeeh_preset_subhanallah
import world.taqwa.app.resources.tasbeeh_preset_subhanallahi_wa_bihamdihi
import world.taqwa.app.resources.tasbeeh_reset
import world.taqwa.app.resources.tasbeeh_reset_confirm
import world.taqwa.app.resources.tasbeeh_round
import world.taqwa.app.resources.tasbeeh_title
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.watch.WatchScreenWords

/**
 * The phone's own strings for the watch's screens (`ui.screens`, plan 3 Task 10), each read as its
 * pattern, with its places kept, by the phone's resource name (`"tasbeeh_round"`). A seam, as
 * [SpokenStrings] is for the same reason: `getString` reads the ambient resource environment, which
 * no argument can change, so a host test cannot ask it for seven languages, and stands in for it.
 */
internal interface ScreenStrings {
    /** The string [key], with its `%1$s` and `%2$s` places as they are. */
    suspend fun text(key: String): String

    /** The plural [key]'s phrase for [n], with its `%1$s` place as it is. */
    suspend fun counted(key: String, n: Int): String
}

/**
 * The strings from the app's resources, in the same environment `watchUiLanguage()` reads, so the
 * words and the language agree. Each place is passed back as itself, as [ResourceSpokenStrings]
 * does, so a pattern comes back with its places whether or not a bare read would format it. A key
 * outside [TEXTS] and [COUNTED] throws: the reader asks only for the table's keys.
 */
internal object ResourceScreenStrings : ScreenStrings {
    override suspend fun text(key: String): String =
        getString(TEXTS[key] ?: throw IllegalArgumentException("no string $key for the watch"), PLACE_1, PLACE_2)

    override suspend fun counted(key: String, n: Int): String =
        getPluralString(COUNTED[key] ?: throw IllegalArgumentException("no plural $key for the watch"), n, PLACE_1)

    private const val PLACE_1 = "%1\$s"
    private const val PLACE_2 = "%2\$s"

    /** Every string the reader asks for, by its name. */
    private val TEXTS: Map<String, StringResource> = mapOf(
        "qibla_back" to Res.string.qibla_back,
        "tasbeeh_title" to Res.string.tasbeeh_title,
        "qibla_title" to Res.string.qibla_title,
        "app_name" to Res.string.app_name,
        "prayer_a11y_prayer_current" to Res.string.prayer_a11y_prayer_current,
        "prayer_a11y_prayer_passed" to Res.string.prayer_a11y_prayer_passed,
        "prayer_a11y_prayer_next" to Res.string.prayer_a11y_prayer_next,
        "tasbeeh_round" to Res.string.tasbeeh_round,
        "tasbeeh_of" to Res.string.tasbeeh_of,
        "tasbeeh_reset" to Res.string.tasbeeh_reset,
        "tasbeeh_reset_confirm" to Res.string.tasbeeh_reset_confirm,
        "tasbeeh_preset_after_prayer" to Res.string.tasbeeh_preset_after_prayer,
        "tasbeeh_preset_subhanallah" to Res.string.tasbeeh_preset_subhanallah,
        "tasbeeh_preset_alhamdulillah" to Res.string.tasbeeh_preset_alhamdulillah,
        "tasbeeh_preset_allahu_akbar" to Res.string.tasbeeh_preset_allahu_akbar,
        "tasbeeh_preset_astaghfirullah" to Res.string.tasbeeh_preset_astaghfirullah,
        "tasbeeh_preset_la_ilaha_illallah" to Res.string.tasbeeh_preset_la_ilaha_illallah,
        "tasbeeh_preset_subhanallahi_wa_bihamdihi" to Res.string.tasbeeh_preset_subhanallahi_wa_bihamdihi,
        "qibla_facing_qibla" to Res.string.qibla_facing_qibla,
        "qibla_distance" to Res.string.qibla_distance,
        "qibla_bearing" to Res.string.qibla_bearing,
        "prayer_qibla_detail" to Res.string.prayer_qibla_detail,
        "qibla_cardinal_north" to Res.string.qibla_cardinal_north,
        "qibla_cardinal_east" to Res.string.qibla_cardinal_east,
        "qibla_cardinal_south" to Res.string.qibla_cardinal_south,
        "qibla_cardinal_west" to Res.string.qibla_cardinal_west,
        "qibla_needs_calibrating" to Res.string.qibla_needs_calibrating,
        "qibla_interference_title" to Res.string.qibla_interference_title,
        "qibla_best_effort_title" to Res.string.qibla_best_effort_title,
        "qibla_no_sensor_title" to Res.string.qibla_no_sensor_title,
    )

    /**
     * Every plural the reader asks for. None yet: accessibility round 2's turns
     * (`qibla_a11y_turn_right`, `_left`) join here when round 2 puts them in the resources, and
     * until then they are [WatchWords.screens]'.
     */
    private val COUNTED: Map<String, PluralStringResource> = emptyMap()
}

/**
 * The words of the watch's screens in [language], the app's interface language
 * (`watchUiLanguage()`'s answer), as the feed carries them (`ui.screens`):
 *
 * - from [strings], the phone's own text, each read as its pattern: the titles, the app's name
 *   (`app_name`, which the Apple Watch draws, plan 4 Task 3), the Today rows' spoken states, the
 *   tasbeeh's words and each built-in's name (in `TasbeehPresets.builtIn`'s order, so one feed
 *   encodes as one text), and the Qibla dial's;
 * - from [WatchWords.screens] for [language], the rest: the two notices the Wear app says itself,
 *   the watches' new phrases (the crown's hint among them), and accessibility round 2's spoken
 *   phrases and turns.
 *
 * The default [strings] reads in the resource environment [language] was read in, never by a tag
 * or the device's locale, so the words are the app's own. A read that throws passes to the
 * caller, whose sync then answers that it failed: the words are never guessed.
 */
internal suspend fun watchScreenWords(language: UiLanguage, strings: ScreenStrings = ResourceScreenStrings): WatchScreenWords {
    val presets = LinkedHashMap<String, String>()
    for (preset in TasbeehPresets.builtIn) presets[preset.id] = strings.text("tasbeeh_preset_${preset.id}")
    return WatchWords.screens(language).copy(
        today = strings.text("qibla_back"),
        tasbeeh = strings.text("tasbeeh_title"),
        qibla = strings.text("qibla_title"),
        appName = strings.text("app_name"),
        rowCurrent = strings.text("prayer_a11y_prayer_current"),
        rowPassed = strings.text("prayer_a11y_prayer_passed"),
        rowNext = strings.text("prayer_a11y_prayer_next"),
        round = strings.text("tasbeeh_round"),
        of = strings.text("tasbeeh_of"),
        reset = strings.text("tasbeeh_reset"),
        resetConfirm = strings.text("tasbeeh_reset_confirm"),
        presets = presets,
        facingQibla = strings.text("qibla_facing_qibla"),
        distance = strings.text("qibla_distance"),
        bearing = strings.text("qibla_bearing"),
        qiblaDetail = strings.text("prayer_qibla_detail"),
        cardinals = listOf(
            strings.text("qibla_cardinal_north"),
            strings.text("qibla_cardinal_east"),
            strings.text("qibla_cardinal_south"),
            strings.text("qibla_cardinal_west"),
        ),
        needsCalibrating = strings.text("qibla_needs_calibrating"),
        interference = strings.text("qibla_interference_title"),
        bestEffort = strings.text("qibla_best_effort_title"),
        noSensor = strings.text("qibla_no_sensor_title"),
    )
}
