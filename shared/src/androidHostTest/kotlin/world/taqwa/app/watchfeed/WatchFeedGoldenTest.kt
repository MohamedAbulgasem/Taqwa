package world.taqwa.app.watchfeed

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.AndroidPlatformFormat
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchFeedJson
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.time.Instant

/**
 * The golden feeds: whole feeds as the phone builds them, committed in `watchcore/vectors/phone/`
 * for the watchOS port's decoder (plan 4) and for the face's Urdu shots (Task 3 Step 6). They hold
 * the shapes a decoder has to meet: an English month, a Friday's Jumuʿah, Tromsø's May (its
 * Maghrib and Isha past midnight, then from the 18th the polar rule's times), Arabic on a
 * twelve-hour clock, and Urdu in extended Arabic-Indic digits, each with the screen reader's words
 * and the words of the watch's screens in its language.
 * The engine gives Fajr's and Asr's ends on every one of those days, so no golden carries a null
 * end; the rules' vectors (`capetown-no-ends`) do. The Friday's phone has Show sunrise off, as a
 * phone has it until the user turns it on; the other four have it on, so that the Galaxy watch's
 * tests that weigh and lay out the Tile on them meet its six rows, the heavier case.
 *
 * Every instant is frozen in `vectors/phone/times/` ([FrozenTimes]), never the engine's today, so
 * the files are byte-stable: no engine change or new timetable row moves a byte of them. The cities
 * are literals, frozen as the instants are; `WatchCityNameTest` and the sample test hold the path
 * that names them.
 *
 * The test fails when a file is missing or differs. After a change that is meant, rewrite them with
 * `./gradlew :shared:testAndroidHostTest -Ptaqwa.updateVectors` (which never touches the tables),
 * read the diff, and commit it. The tables are recorded once, with `-Ptaqwa.recordTimes`.
 */
class WatchFeedGoldenTest {

    private fun phone(): File =
        if (File("src/androidHostTest").isDirectory) File("../watchcore/vectors/phone") else File("watchcore/vectors/phone")

    private class Golden(
        val name: String,
        val table: String,
        val location: GeoLocation,
        val city: String,
        val tag: String,
        val clock: ClockFormat,
        val now: Instant,
        val showSunrise: Boolean = true,
    )

    private val capeTown = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", cityName = "Cape Town")
    private val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", cityName = "Tromsø")
    private val tripoli = GeoLocation(32.8872, 13.1913, "Africa/Tripoli", cityName = "Tripoli")

    private val tables = listOf(
        Triple("capetown", capeTown, LocalDate(2026, 10, 17)..LocalDate(2026, 11, 21)),
        Triple("tromso-may", tromso, LocalDate(2026, 5, 14)..LocalDate(2026, 6, 13)),
        Triple("tripoli", tripoli, LocalDate(2026, 10, 17)..LocalDate(2026, 11, 16)),
    )

    private val goldens = listOf(
        Golden("phone-capetown-en", "capetown", capeTown, "Cape Town", "en", ClockFormat.TWENTY_FOUR_HOUR, Instant.parse("2026-10-18T06:00:00Z")),
        Golden("phone-capetown-friday-en", "capetown", capeTown, "Cape Town", "en", ClockFormat.TWENTY_FOUR_HOUR, Instant.parse("2026-10-23T06:00:00Z"), showSunrise = false),
        Golden("phone-capetown-ur24", "capetown", capeTown, "کیپ ٹاؤن", "ur-PK-u-nu-arabext", ClockFormat.TWENTY_FOUR_HOUR, Instant.parse("2026-10-18T06:00:00Z")),
        Golden("phone-tromso-may-en", "tromso-may", tromso, "Tromsø", "en", ClockFormat.TWENTY_FOUR_HOUR, Instant.parse("2026-05-15T10:00:00Z")),
        Golden("phone-tripoli-ar-ly12", "tripoli", tripoli, "طرابلس", "ar-LY", ClockFormat.TWELVE_HOUR, Instant.parse("2026-10-18T06:00:00Z")),
    )

    @Test
    fun phoneFeedsAreUpToDate() {
        val directory = phone()
        if (System.getProperty("taqwa.recordTimes") != null) {
            for ((name, location, dates) in tables) FrozenTimes.record(File(directory, "times/$name.json"), location, dates)
        }

        val files = goldens.associate { golden ->
            val language = UiLanguage.of(golden.tag)
            val feed = assertNotNull(
                WatchFeedBuilder.build(
                    WatchFeedInputs(
                        location = golden.location,
                        city = golden.city,
                        language = language,
                        spoken = BundledSpoken.of(language),
                        screens = BundledScreenStrings.words(language),
                        prayerSettings = PrayerSettings(showSunrise = golden.showSunrise),
                        format = AndroidPlatformFormat(Locale.forLanguageTag(golden.tag), golden.clock),
                        clockFormat = golden.clock,
                        phrases = emptyList(),
                        now = golden.now,
                        timesFor = FrozenTimes.read(File(directory, "times/${golden.table}.json")),
                    ),
                ),
                golden.name,
            )
            "${golden.name}.json" to WatchFeedJson.encode(feed) + "\n"
        }

        if (System.getProperty("taqwa.updateVectors") != null) {
            directory.mkdirs()
            files.forEach { (name, text) -> File(directory, name).writeText(text) }
            return
        }

        val problems = files.mapNotNull { (name, text) -> problemWith(File(directory, name), text) }
        if (problems.isNotEmpty()) {
            fail(
                problems.joinToString("\n") + "\nTo write them again: " +
                    "./gradlew :shared:testAndroidHostTest -Ptaqwa.updateVectors. Read the diff before you commit it.",
            )
        }
    }

    private fun problemWith(file: File, expected: String): String? {
        if (!file.exists()) return "${file.path} is missing."
        val actual = file.readText()
        if (actual == expected) return null
        val column = actual.commonPrefixWith(expected).length
        fun excerpt(text: String) = text.substring((column - 40).coerceAtLeast(0), minOf(text.length, column + 80))
        return "${file.path} is not the feed the builder writes. First difference at character $column:\n" +
            "    file:    ${excerpt(actual)}\n" +
            "    builder: ${excerpt(expected)}"
    }
}
