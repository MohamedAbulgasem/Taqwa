package world.taqwa.app.watchfeed

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.i18n.AndroidPlatformFormat
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import java.io.File
import java.util.Locale
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * The samples `:watchcore` commits (`watchcore/vectors/sample-feed-capetown*.json`) as the Android
 * host tests build from them: Cape Town, the moment the English sample was written, and its days
 * as the engine's. And, from them, plan 2 Task 3's largest feed on Android ([largestFeed]), which
 * `WatchFeedSampleTest` measures and `AndroidWatchLinkTest` sends, as a fixture beside
 * [WorstFeeds] rather than one test class built by another (Ruling 54 item 5).
 */
internal object CapeTownSample {

    /** Cape Town as the phone stores it: the bundle's city, with the sample's own point. */
    val LOCATION = GeoLocation(-33.9249, 18.4241, "Africa/Johannesburg", cityName = "Cape Town", cityId = 3369157)

    /** `:watchcore`'s vectors, from the module's directory (as Gradle runs the tests) or the repository's. */
    private fun vectors(): File =
        if (File("src/androidHostTest").isDirectory) File("../watchcore/vectors") else File("watchcore/vectors")

    /** The committed sample [name]. */
    fun read(name: String): WatchFeed = assertNotNull(WatchFeedJson.decode(File(vectors(), name).readText()), name)

    /** The English sample, every other sample's day and moment. */
    val english: WatchFeed by lazy { read("sample-feed-capetown.json") }

    /** When the samples were written: the builder's "now" for a feed meant to equal them. */
    val now: Instant get() = Instant.fromEpochSeconds(english.writtenAt)

    /** The English sample's own instants as the engine's days, so a feed built on them can equal the sample's. */
    val times: (LocalDate) -> DayPrayerTimes by lazy { timesOf(english) }

    /**
     * The largest feed an Android phone in [tag] builds with a hundred phrases of [kind]: the tag's
     * own format in 12-hour time and its interface language, with that language's spoken and
     * screens' words, on the sample's days ([WorstFeeds.largestFeed]).
     */
    fun largestFeed(tag: String, kind: PhraseKind): WatchFeed {
        val language = UiLanguage.of(tag)
        val format = AndroidPlatformFormat(Locale.forLanguageTag(tag), ClockFormat.TWELVE_HOUR)
        val spoken = BundledSpoken.of(language)
        val screens = BundledScreenStrings.words(language)
        return assertNotNull(WorstFeeds.largestFeed(LOCATION, format, language, spoken, screens, kind, now, times), "$tag $kind")
    }

    /** [feed]'s instants as the engine's days, by date; a date it does not hold is one the engine cannot give. */
    private fun timesOf(feed: WatchFeed): (LocalDate) -> DayPrayerTimes {
        val byDate = feed.days.associateBy { it.date }
        return { date ->
            val day = byDate[date.toString()] ?: error("the sample holds no $date")
            fun at(seconds: Long) = Instant.fromEpochSeconds(seconds)
            val t = day.times
            DayPrayerTimes(
                date = date,
                times = listOf(
                    PrayerTime(Prayer.FAJR, at(t.fajr)),
                    PrayerTime(Prayer.SUNRISE, at(t.sunrise)),
                    PrayerTime(Prayer.DHUHR, at(t.dhuhr)),
                    PrayerTime(Prayer.ASR, at(t.asr)),
                    PrayerTime(Prayer.MAGHRIB, at(t.maghrib)),
                    PrayerTime(Prayer.ISHA, at(t.isha)),
                ),
                highLatitudeRuleApplied = null,
                asrOther = at(t.asr),
                sunset = at(t.asrEnd ?: t.maghrib),
                endOfEating = at(t.fajr),
                ends = buildMap {
                    t.fajrEnd?.let { put(Prayer.FAJR, at(it)) }
                    t.asrEnd?.let { put(Prayer.ASR, at(it)) }
                },
                sourceEntryId = "sample",
                entryClass = EntryClass.D_NONE,
            )
        }
    }
}
