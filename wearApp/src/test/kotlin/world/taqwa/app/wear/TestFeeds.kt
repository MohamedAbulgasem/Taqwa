package world.taqwa.app.wear

import java.io.File
import world.taqwa.app.watch.WatchCountdownPhrases
import world.taqwa.app.watch.WatchDay
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watch.WatchUi

/**
 * The feeds these tests store. They are the real thing and not a toy: the sample feed that
 * `:watchcore` commits (`vectors/sample-feed-capetown.json`, `Fixtures.capeTownSample()` as the
 * phone is to send it, 31 days of Cape Town in English), which is also the file the debug build
 * carries as its sample. Its size, about 19 KB, is the size of a feed a watch really stores, which
 * is what makes a file half written easy to catch.
 *
 * The sample's first day is Saturday 17 October 2026 and its second is the mockups' Sunday 18th,
 * the day the figures in these tests are worked on: Fajr 04:33, Sunrise 06:01, Dhuhr 12:33, Asr
 * 16:10, Maghrib 19:04 and Isha 20:24 in Cape Town (UTC+2), every day alike, with Friday's Dhuhr
 * reading Jumuʿah. [at] counts days from that Sunday, as `Fixtures.at` does in `:watchcore`.
 */
internal object TestFeeds {

    /** The sample as the file holds it: the exact text a phone would send. */
    val sampleJson: String by lazy { sampleFile().readText() }

    /** [sampleJson] as the watch reads it. */
    val sample: WatchFeed by lazy {
        checkNotNull(WatchFeedJson.decode(sampleJson)) { "the committed sample feed no longer decodes" }
    }

    /** [sample] written as if by a different phone at a different time, so that two feeds differ. */
    fun another(city: String = "Durban", writtenAt: Long = sample.writtenAt + 3_600L): WatchFeed =
        sample.copy(writtenAt = writtenAt, place = sample.place.copy(city = city))

    fun json(feed: WatchFeed): String = WatchFeedJson.encode(feed)

    /** 00:00 on Sunday 18 October 2026 in Cape Town (UTC+2), in epoch seconds. */
    const val MOCK_MIDNIGHT: Long = 1_792_274_400L

    /**
     * [hour]:[minute]:[second] on the date [day] days after the sample's Sunday 18 October, Cape
     * Town time, as the epoch seconds a watch would read it as. The sample's list holds that Sunday
     * as `days[1]`, so `days[day + 1]` is the day.
     */
    fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        MOCK_MIDNIGHT + day * 86_400L + hour * 3_600L + minute * 60L + second

    /**
     * The sample as a phone set to Arabic with Saudi digits sends it: the prayers' Arabic names,
     * the clock times in Arabic-Indic digits, and the Arabic countdown phrases of the plan's global
     * constraints. The date lines stay English; the tests that use this one do not read them.
     */
    fun arabicSaudi(): WatchFeed = sample.copy(
        ui = WatchUi(
            language = "ar-SA",
            rtl = true,
            digitZero = '٠',
            twelveHour = false,
            am = "ص",
            pm = "م",
            countdown = WatchCountdownPhrases(
                hoursMinutes = "بعد %1\$s س %2\$s د",
                hours = "بعد %1\$s س",
                minutes = "بعد %1\$s د",
                underAMinute = "بعد أقل من دقيقة",
            ),
            nextIn = "متبقٍّ على %1\$s",
        ),
        days = sample.days.map { day ->
            day.copy(
                names = WatchSix(
                    fajr = "الفجر",
                    sunrise = "الشروق",
                    dhuhr = if (day.names.dhuhr != "Dhuhr") "الجمعة" else "الظهر",
                    asr = "العصر",
                    maghrib = "المغرب",
                    isha = "العشاء",
                ),
                clock = WatchSix(
                    fajr = inArabicIndic(day.clock.fajr),
                    sunrise = inArabicIndic(day.clock.sunrise),
                    dhuhr = inArabicIndic(day.clock.dhuhr),
                    asr = inArabicIndic(day.clock.asr),
                    maghrib = inArabicIndic(day.clock.maghrib),
                    isha = inArabicIndic(day.clock.isha),
                ),
            )
        },
    )

    private fun inArabicIndic(text: String): String =
        text.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")

    /**
     * [feed] as a phone with Show sunrise off sends it (`ui.showSunrise`), as a phone has it until
     * the user turns it on. The samples have it on, as the mockups draw the day's six rows.
     */
    fun withoutSunrise(feed: WatchFeed): WatchFeed = feed.copy(ui = feed.ui.copy(showSunrise = false))

    /** [feed] with [change] applied to day [index] (a place in `days`, not a date). */
    fun withDay(feed: WatchFeed, index: Int, change: (WatchDay) -> WatchDay): WatchFeed =
        feed.copy(days = feed.days.mapIndexed { i, day -> if (i == index) change(day) else day })

    /**
     * The sample with one night the way a high-latitude summer gives the engine one
     * (`Fixtures.lateMaghribNight` in `:watchcore`): Tuesday 20 October's Maghrib (00:45) and Isha
     * (00:52) fall on Wednesday the 21st, whose own Fajr is at 00:38. The phone counts to that
     * Maghrib until midnight and drops it when the date turns over; the figures are invented and
     * their shape is the engine's.
     */
    fun lateMaghribNight(): WatchFeed =
        withDay(
            withDay(sample, 3) { d ->
                d.copy(times = d.times.copy(maghrib = d.midnight + 86_400L + 45 * 60L, isha = d.midnight + 86_400L + 52 * 60L))
            },
            4,
        ) { d -> d.copy(times = d.times.copy(fajr = d.midnight + 38 * 60L)) }

    /**
     * One of the debug build's other samples beside the sample (`sample-feed-capetown-<name>.json`):
     * `en12` (English on twelve hours), `ar-ly12` (Arabic in Libya, Western digits, twelve hours)
     * and `ar-sa` (Arabic in Saudi Arabia, Arabic-Indic digits), each in the phone's own words, its
     * dates included.
     */
    fun other(name: String): WatchFeed =
        checkNotNull(WatchFeedJson.decode(File(sampleFile().parentFile, "sample-feed-capetown-$name.json").readText())) { name }

    /**
     * One of the phone's golden feeds (`watchcore/vectors/phone/phone-<name>.json`), the bytes
     * `:shared`'s `WatchFeedGoldenTest` holds the phone's builder to: `tromso-may-en` is Tromsø in
     * May 2026, where Maghrib and Isha fall after midnight and Sunrise comes a minute after Fajr.
     */
    fun phone(name: String): WatchFeed =
        checkNotNull(WatchFeedJson.decode(File(sampleFile().parentFile, "phone/phone-$name.json").readText())) { name }

    /** Gradle hands the path in (`taqwa.sampleFeed`, wearApp/build.gradle.kts); a bare JVM run looks for it. */
    private fun sampleFile(): File {
        val path = System.getProperty("taqwa.sampleFeed")
        val file = if (path != null) File(path) else File("../watchcore/vectors/sample-feed-capetown.json")
        check(file.isFile) { "the sample feed is not at ${file.absolutePath}" }
        return file
    }
}
