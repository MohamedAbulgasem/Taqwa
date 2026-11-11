package world.taqwa.app.watchfeed

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.feature.qibla.localizedGroupedKm
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.TabularHijriCalendar
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.CountdownDigits
import world.taqwa.app.i18n.DigitGrouping
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.i18n.wordsLocaleTag
import world.taqwa.app.prayer.TimelineBuilder
import world.taqwa.app.qibla.QiblaMath
import world.taqwa.app.qibla.WorldMagneticModel
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.watch.WatchDay
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watch.WatchPhrase
import world.taqwa.app.watch.WatchPlace
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watch.WatchSpoken
import world.taqwa.app.watch.WatchTimes
import world.taqwa.app.watch.WatchUi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * Everything the feed is built from, each as the app itself has it. The builder derives none of
 * them:
 *
 * - [language] is the app's interface language (`watchUiLanguage()`), never the tag's;
 * - [spoken] is the screen reader's words in that language (`watchSpoken()`), copied as they are;
 *   only the tests pass null;
 * - [screens] is the words of the watch's screens in that language (`watchScreenWords()`), copied
 *   as they are;
 * - [city] is the city's name as the Prayer screen shows it (`watchCityName`);
 * - [prayerSettings] are the stored prayer settings, of which the feed reads the Hijri offset and
 *   Show sunrise ([timesFor] has already applied the rest);
 * - [format] writes the clock, the dates and the digits, and [clockFormat] says 12 or 24 hours, as
 *   the format it was built with does;
 * - [phrases] are the user's own tasbeeh phrases (`TasbeehStore.customPresets`), in order;
 * - [timesFor] is the engine's day for a date at [location], which throws for a date it cannot give.
 */
internal data class WatchFeedInputs(
    val location: GeoLocation,
    val city: String,
    val language: UiLanguage,
    val spoken: WatchSpoken?,
    val screens: WatchScreenWords,
    val prayerSettings: PrayerSettings,
    val format: PlatformFormat,
    val clockFormat: ClockFormat,
    val phrases: List<TasbeehPreset>,
    val now: Instant,
    val timesFor: (LocalDate) -> DayPrayerTimes,
)

/**
 * The feed the phone sends its watch (spec 1.1), built from the phone's own engine days and its own
 * words, in the app's interface language and case, with the place as Ruling 39 allows it. Pure: it
 * reads no resource, no store and no clock of its own, so both platforms test it as it runs.
 *
 * Every string is the phone's: the words, the digits, the grouping and the city's name come from
 * the phone's own functions, and a fixture that disagrees is changed, never this. The exceptions
 * are the Lead's call 3's, for a digit set the face was not built for (written Western throughout,
 * the distance's thousands mark included: [WatchDigits]), and Ruling 39's rounding of the place.
 */
internal object WatchFeedBuilder {

    /** The phone's yesterday and the thirty days from today (Ruling 7). */
    const val DAYS = 31

    /** The most of the user's own phrases the watch is sent, in order. */
    const val MAX_PHRASES = 100

    /**
     * The most a feed without phrases may take, in any language, digit set and clock: the room the
     * times leave the phrases in [WatchLinkContract.FEED_BUDGET_BYTES]. The screens' words go into
     * this margin: plan 3's are in it (Task 10: 33,756 bytes at the largest, a Bengali phone on 12
     * hours with the worst city), and plan 4 adds its own and runs the size tests that hold it again.
     */
    const val TIMES_ALONE_MAX_BYTES = 40_000

    /** [json]'s UTF-8 bytes: what both links carry. */
    fun sizeOf(json: String): Int = json.encodeToByteArray().size

    /**
     * [km] to two significant figures (the Lead's call 5), so that the distance is about as coarse
     * along the line as the whole-degree bearing is across it: the whole kilometre from 10 km, ten
     * from 100, a hundred from 1,000 and a thousand from 10,000. Under 10 km it is [km] itself, which
     * `localizedGroupedKm` truncates to the phone's own whole kilometre.
     */
    fun roundedKm(km: Double): Double {
        if (km < 10.0) return km
        var step = 1.0
        while (km >= step * 100.0) step *= 10.0
        return (km / step).roundToLong() * step
    }

    /**
     * The feed at [WatchFeedInputs.now], or null when the engine cannot give yesterday or today.
     *
     * - **Dates:** yesterday through today + 29, at the place (`TimeZone.of(location.timeZoneId)`).
     *   The first date the engine cannot give (its `timesFor` throws anything but a cancellation)
     *   ends the feed at the day before (spec 1.1).
     * - **Each day:** the engine's own instants, unchanged; each date's midnight at the place
     *   (Ruling 8); the clock without its day period, and on a 12-hour phone each time as the app
     *   writes it, day period and all ([WatchDay.clockTime]); the names in the interface language,
     *   Dhuhr as Jumuʿah on a Friday at the place; the Hijri date and its day and month, from one
     *   date with the phone's offset (Ruling 24); and the Gregorian line, weekday, day and spoken
     *   date.
     * - **The place:** the city as given, the Qibla's bearing to the phone's whole degree, the
     *   declination to a whole degree, the distance to two figures, the hemisphere. Never a
     *   coordinate.
     * - **Sunrise:** shown or not as the phone's Show sunrise has it (`PrayerSettings.showSunrise`,
     *   [WatchUi.showSunrise]); every day keeps its Sunrise either way.
     * - **Digits:** every string that can hold one goes through [WatchDigits], and the distance's
     *   thousands mark follows its digits ([distance]). The phrase templates hold none of the
     *   phone's, and their places stay as they are.
     * - **Size:** the times always go; the first [MAX_PHRASES] of the user's phrases go, in order,
     *   as many as keep the feed within [budgetBytes] in UTF-8. Only the tests pass [budgetBytes].
     *
     * A failed encode (a NaN, say) is thrown to the caller, whose sync answers that it failed.
     */
    fun build(inputs: WatchFeedInputs, budgetBytes: Int = WatchLinkContract.FEED_BUDGET_BYTES): WatchFeed? {
        val zone = TimeZone.of(inputs.location.timeZoneId)
        val today = inputs.now.toLocalDateTime(zone).date
        val digits = WatchDigits.of(inputs.format, inputs.language)

        val days = mutableListOf<WatchDay>()
        for (offset in -1 until DAYS - 1) {
            val date = today.plus(offset, DateTimeUnit.DAY)
            val times = timesOrNull(inputs, date) ?: break
            days += day(date, times, inputs, zone, digits)
        }
        // Yesterday and today, at the least: the night before today's Fajr needs its previous start.
        if (days.size < 2) return null

        val feed = WatchFeed(
            version = WatchFeedJson.VERSION,
            writtenAt = inputs.now.epochSeconds,
            days = days,
            place = place(inputs, today, digits),
            ui = ui(inputs, digits),
        )
        val phrases = inputs.phrases.take(MAX_PHRASES).mapNotNull { preset ->
            preset.parts.firstOrNull()?.let { WatchPhrase(preset.id, it.dhikr.arabic, preset.total) }
        }
        return withThePhrasesThatFit(feed, phrases, budgetBytes)
    }

    /** The engine's day, or null for a date it cannot give. A cancellation is never swallowed. */
    private fun timesOrNull(inputs: WatchFeedInputs, date: LocalDate): DayPrayerTimes? = try {
        inputs.timesFor(date)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun day(date: LocalDate, times: DayPrayerTimes, inputs: WatchFeedInputs, zone: TimeZone, digits: WatchDigits): WatchDay {
        val format = inputs.format
        val language = inputs.language

        fun clock(prayer: Prayer): String {
            val local = times.time(prayer).toLocalDateTime(zone)
            return digits.write(format.clockFigures(local.hour, local.minute))
        }

        // The app's own 12-hour time (`TwelveHourClock.render`: a no-break space, the marker first
        // in Turkish), bare: the watch isolates it where it splices it into a sentence. Its marker is
        // the phone locale's, as the Prayer screen writes it (plan 3's call 9), even where `ui.am`
        // and `ui.pm`, the words locale's, differ: a he-IL phone on the English interface sends
        // "4:10 אחה״צ" beside "AM"/"PM" (`WatchFeedSampleTest`, a twelve-hour phone outside the seven).
        fun clockTime(prayer: Prayer): String {
            val local = times.time(prayer).toLocalDateTime(zone)
            return digits.write(format.clockTime(local.hour, local.minute))
        }

        fun name(prayer: Prayer): String =
            if (TimelineBuilder.isJumuah(prayer, times.time(prayer), zone)) {
                WatchWords.jumuah(language)
            } else {
                PrayerNaming.name(prayer, language.code)
            }

        // The offset moves the Gregorian date before the conversion, as the Prayer screen does,
        // never the Hijri day itself.
        val hijri = TabularHijriCalendar.fromGregorian(date.plus(inputs.prayerSettings.hijriOffsetDays, DateTimeUnit.DAY))
        val line = WatchDateLine.of(date, format, language)

        return WatchDay(
            date = date.toString(),
            times = WatchTimes(
                fajr = times.time(Prayer.FAJR).epochSeconds,
                sunrise = times.time(Prayer.SUNRISE).epochSeconds,
                dhuhr = times.time(Prayer.DHUHR).epochSeconds,
                asr = times.time(Prayer.ASR).epochSeconds,
                maghrib = times.time(Prayer.MAGHRIB).epochSeconds,
                isha = times.time(Prayer.ISHA).epochSeconds,
                fajrEnd = times.ends[Prayer.FAJR]?.epochSeconds,
                asrEnd = times.ends[Prayer.ASR]?.epochSeconds,
            ),
            clock = WatchSix(
                fajr = clock(Prayer.FAJR),
                sunrise = clock(Prayer.SUNRISE),
                dhuhr = clock(Prayer.DHUHR),
                asr = clock(Prayer.ASR),
                maghrib = clock(Prayer.MAGHRIB),
                isha = clock(Prayer.ISHA),
            ),
            names = WatchSix(
                fajr = name(Prayer.FAJR),
                sunrise = name(Prayer.SUNRISE),
                dhuhr = name(Prayer.DHUHR),
                asr = name(Prayer.ASR),
                maghrib = name(Prayer.MAGHRIB),
                isha = name(Prayer.ISHA),
            ),
            hijri = digits.write(HijriFormatter.format(hijri, format, language)),
            gregorian = digits.write(line.gregorian),
            weekday = line.weekday,
            dayNumber = digits.write(line.dayNumber),
            midnight = date.atStartOfDayIn(zone).epochSeconds,
            hijriDayMonth = digits.write(HijriFormatter.formatDayMonth(hijri, format, language)),
            gregorianSpoken = digits.write(line.spoken),
            clockTime = if (inputs.clockFormat != ClockFormat.TWELVE_HOUR) null else WatchSix(
                fajr = clockTime(Prayer.FAJR),
                sunrise = clockTime(Prayer.SUNRISE),
                dhuhr = clockTime(Prayer.DHUHR),
                asr = clockTime(Prayer.ASR),
                maghrib = clockTime(Prayer.MAGHRIB),
                isha = clockTime(Prayer.ISHA),
            ),
        )
    }

    /**
     * The place as Ruling 39 allows it: the city, the bearing as the Prayer screen's Qibla card reads
     * it (`PrayerScreen.kt`, the whole degree), the declination to a whole degree at [today], the
     * distance to two figures in the feed's digits and grouping ([distance]), and the hemisphere.
     * No coordinate.
     */
    private fun place(inputs: WatchFeedInputs, today: LocalDate, digits: WatchDigits): WatchPlace {
        val location = inputs.location
        val declination = WorldMagneticModel.declinationDegrees(
            location.latitude,
            location.longitude,
            0.0,
            WorldMagneticModel.decimalYear(today),
        )
        return WatchPlace(
            city = inputs.city,
            qiblaBearing = QiblaMath.bearing(location).toInt().toDouble(),
            qiblaKm = distance(roundedKm(QiblaMath.distanceKm(location)), inputs, digits),
            declination = declination.roundToInt().toDouble(),
            southern = location.latitude < 0,
        )
    }

    /**
     * The distance as the feed writes it. Where [digits] keeps the phone's digits it is the phone's
     * own Qibla card string (`localizedGroupedKm`), mark and all. Where it does not (the Lead's call
     * 3) it is Western throughout, the mark included: [DigitGrouping]'s mark for Western digits in
     * the interface language, whose words the watch writes beside it ("6,600" in English, a narrow
     * space in French, "6.600" in Turkish). Rewriting the phone's string with [WatchDigits.write]
     * alone would keep U+066C, which `DigitGrouping` picks for the Arabic-Indic digits the feed no
     * longer holds, beside Western ones. The whole kilometre is truncated, as the phone's is.
     */
    private fun distance(km: Double, inputs: WatchFeedInputs, digits: WatchDigits): String =
        if (digits.kept) {
            localizedGroupedKm(km, inputs.format)
        } else {
            DigitGrouping.group(km.toLong(), inputs.language.code) { it.toString() }
        }

    /**
     * How the phone writes: the words locale's tag, from which the watch takes letter case alone;
     * the interface language's direction; the zero of the feed's own digits; the clock; the day
     * markers; the face's, the screen reader's, the screens' and the face screen's phrases, in
     * the interface language; the zero of the ring's digits where it is not the feed's ([ringZero]); whether
     * the interface is in Latin letters, under which alone the watch shows a transliteration; and
     * whether the phone shows Sunrise, its Show sunrise setting.
     */
    private fun ui(inputs: WatchFeedInputs, digits: WatchDigits): WatchUi {
        val language = inputs.language
        val (am, pm) = inputs.format.dayPeriods(language)
        return WatchUi(
            language = wordsLocaleTag(inputs.format.languageTag(), language),
            rtl = language.rtl,
            digitZero = digits.zero,
            twelveHour = inputs.clockFormat == ClockFormat.TWELVE_HOUR,
            am = am,
            pm = pm,
            countdown = WatchWords.countdown(language),
            nextIn = PrayerNaming.nextInPattern(language.code),
            spoken = inputs.spoken,
            screens = inputs.screens,
            ringDigitZero = ringZero(inputs, digits),
            latinScript = language.latinScript,
            face = WatchWords.face(language),
            showSunrise = inputs.prayerSettings.showSunrise,
        )
    }

    /**
     * The zero of the digits the phone's countdown ring writes in, where they are not the feed's
     * own (Ruling 47), else null: Western on an ar-SA, ar-EG or Bengali phone whose clock strings
     * keep their own digits, as the Prayer screen's ring falls back
     * (`CountdownDigits.westernFallback`, on the format's tag, as `CountdownFormatter` asks it).
     */
    private fun ringZero(inputs: WatchFeedInputs, digits: WatchDigits): Char? =
        '0'.takeIf { CountdownDigits.westernFallback(inputs.format.languageTag()) && it != digits.zero }

    /**
     * [feed] with the longest run of [phrases], from the first, that keeps its UTF-8 encoding within
     * [budgetBytes]: all of them when they fit, otherwise found by bisection, so 100 phrases cost at
     * most 8 encodes. The times are never cut: when no phrase fits, the feed goes without.
     */
    private fun withThePhrasesThatFit(feed: WatchFeed, phrases: List<WatchPhrase>, budgetBytes: Int): WatchFeed {
        fun with(count: Int) = feed.copy(tasbeeh = phrases.take(count))
        fun fits(count: Int) = sizeOf(WatchFeedJson.encode(with(count))) <= budgetBytes

        if (fits(phrases.size)) return with(phrases.size)
        var fitting = 0 // none always goes
        var tooMany = phrases.size
        while (tooMany - fitting > 1) {
            val middle = (fitting + tooMany) / 2
            if (fits(middle)) fitting = middle else tooMany = middle
        }
        return with(fitting)
    }
}
