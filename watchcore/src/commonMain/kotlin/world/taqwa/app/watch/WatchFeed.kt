package world.taqwa.app.watch

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * The feed: the one document the phone builds and sends to the watch, and everything the watch
 * draws (spec 1.1). The face, the complications, the Tile, the Smart Stack and the app's three
 * screens read this and nothing else, and the watch never works a time out for itself.
 *
 * The property names are the JSON keys, and the keys are the contract: the watchOS app reads the
 * same document in Swift, and a watch away from its phone keeps the last feed it was sent for
 * weeks. So a key is never renamed or repurposed. A new field is added with a default: a watch
 * not yet updated ignores the field, and a feed written before it still reads. And
 * [WatchFeedJson.VERSION] rises only for a change that a watch not yet updated could not read
 * correctly.
 */
@Serializable
data class WatchFeed(
    /** The format this feed is in: [WatchFeedJson.VERSION] when the phone wrote it. */
    val version: Int,
    /** When the phone wrote it, in epoch seconds. */
    val writtenAt: Long,
    /**
     * One entry for each date from the phone's yesterday, in date order: yesterday and the thirty
     * days from today, 31 in all, or fewer when the engine could not produce a later date, in
     * which case the feed stops at the day before it (spec 1.1). Yesterday is there so that the
     * night before today's Fajr has a previous start, and so that a start of yesterday's that falls
     * after midnight still counts.
     */
    val days: List<WatchDay>,
    val place: WatchPlace,
    val ui: WatchUi,
    /** The user's own tasbeeh phrases (`TasbeehStore.customPresets`). The built-in seven are in
     * the watch apps themselves. */
    val tasbeeh: List<WatchPhrase> = emptyList(),
)

/** One day of the feed: its six instants, and the strings written for them. */
@Serializable
data class WatchDay(
    /** The date, ISO 8601: `2026-10-18`. */
    val date: String,
    val times: WatchTimes,
    /**
     * Each time as the phone writes a clock time, in its digits: `16:10` on a 24-hour phone, and on
     * a 12-hour one the bare `4:10`, with no day period, which is the face's form. The 12-hour times
     * as the app writes them, day period and all, are [clockTime].
     */
    val clock: WatchSix<String>,
    /** The prayer names for this day in the phone's language. Dhuhr already reads Jumuʿah on a
     * Friday, as it does in the app. */
    val names: WatchSix<String>,
    /** The Hijri date as the Prayer screen writes it, with the phone's offset applied. */
    val hijri: String,
    /** The Gregorian date line in the face's form: `SUN 18 OCT` in Latin. */
    val gregorian: String,
    /** The weekday on its own, for Moon's window. */
    val weekday: String,
    /** The day of the month on its own, for Moon's window. */
    val dayNumber: String,
    /**
     * When this date begins at the place, in epoch seconds: its civil midnight in the zone the
     * Prayer screen computes today in (`TimeZone.of(location.timeZoneId)`, in `PrayerViewModel`),
     * which is the location's and not necessarily the phone's own. The feed carries no zone, and
     * the watch needs to know which date it is, and where a date ends: a date with a clock
     * change in it is 23 or 25 hours long, and this is how the watch sees that. 0 means the phone
     * could not say, and the rules then turn the date over at solar midnight, halfway between
     * its Dhuhr and the day before's.
     */
    val midnight: Long = 0L,
    /**
     * The Hijri date as the face's centre line and the Moon's curve write it: the day and the
     * month, with no year (`6 Jumada al-Ula`), built by the phone from the same parts as [hijri],
     * in its language and digits, its offset applied (Ruling 24: the approved mockups have no
     * year, and the line is narrow on a 450-pixel face). [hijri] stays the Prayer screen's full
     * form, for the app's screens. Empty in a feed written before the field was added: a watch
     * then shows [hijri] as it is.
     */
    val hijriDayMonth: String = "",
    /**
     * The Gregorian date as a screen reader is told it: the weekday and the month in full, in the
     * phone's language, case and digits (`Sunday 18 October`, `الأحد ١٨ أكتوبر`), built by the
     * phone from the same parts as [gregorian]. Never the face's capitals, which a speech engine
     * may spell out letter by letter (plan 1, Task 12). Empty in a feed written before the field
     * was added: a watch then says [gregorian] as it is.
     */
    val gregorianSpoken: String = "",
    /**
     * On a 12-hour phone, each of the six times as the app itself writes it, with its day period:
     * the phone's own `clockTime` (`TwelveHourClock.render`), `4:10 PM` with a no-break space before
     * the marker, the marker first in Turkish (`ÖS 4:10`), in the phone's digits. It is bare, with
     * no bidi isolate: the watch draws it as it is and isolates it where it splices it into a
     * sentence ([WatchRules.clockText], [WatchRules.spokenClock]). Null from a 24-hour phone, and in
     * a feed written before the field was added: the watch then draws and says [clock].
     */
    val clockTime: WatchSix<String>? = null,
)

/**
 * A day's six instants in epoch seconds, exactly as the phone's engine produced them (the polar
 * and high-latitude rules included), and the two ends the Prayer screen's rule needs
 * (`DayPrayerTimes.ends`).
 */
@Serializable
data class WatchTimes(
    val fajr: Long,
    val sunrise: Long,
    val dhuhr: Long,
    val asr: Long,
    val maghrib: Long,
    val isha: Long,
    /** When Fajr ends: sunrise. Null on a day with none to give, such as a polar day. */
    val fajrEnd: Long? = null,
    /** When Asr ends: the astronomical sunset, which is not Maghrib's start. Null on a day with
     * none to give, such as a polar day. */
    val asrEnd: Long? = null,
)

/** One value for each of a day's six times, in the order of the day. The clock strings and the
 * names share this shape, and [of] reads it by [WatchTime]. */
@Serializable
data class WatchSix<T>(
    val fajr: T,
    val sunrise: T,
    val dhuhr: T,
    val asr: T,
    val maghrib: T,
    val isha: T,
)

/**
 * The six times of a day, in the order of the day: the five prayers and Sunrise, which is no
 * prayer ([WatchPrayer] has none) but which the face's day dial draws, and Today's rows and the
 * Tile's draw and say while the phone shows it ([WatchUi.showSunrise]). It is the key every surface
 * reads a day's [WatchSix] by.
 */
enum class WatchTime {
    FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA;

    companion object {
        /** The time a prayer begins at: its namesake. */
        fun of(prayer: WatchPrayer): WatchTime = when (prayer) {
            WatchPrayer.FAJR -> FAJR
            WatchPrayer.DHUHR -> DHUHR
            WatchPrayer.ASR -> ASR
            WatchPrayer.MAGHRIB -> MAGHRIB
            WatchPrayer.ISHA -> ISHA
        }
    }
}

/** The value for [time]. */
fun <T> WatchSix<T>.of(time: WatchTime): T = when (time) {
    WatchTime.FAJR -> fajr
    WatchTime.SUNRISE -> sunrise
    WatchTime.DHUHR -> dhuhr
    WatchTime.ASR -> asr
    WatchTime.MAGHRIB -> maghrib
    WatchTime.ISHA -> isha
}

/**
 * Where the feed was made, as Ruling 39 allows the watch to be told it: the city, as the Prayer
 * screen names it; the Qibla's bearing to the phone's whole degree; the declination to a whole
 * degree; the distance to two significant figures; and the hemisphere. Never a coordinate.
 */
@Serializable
data class WatchPlace(
    /** The city's name as the Prayer screen shows it, in the phone's language (`watchCityName`), or
     * "" when the location has neither a city the bundle knows nor a stored name. */
    val city: String,
    /** The Qibla's bearing, in degrees clockwise from true north, to the whole degree the Prayer
     * screen's Qibla card reads (`QiblaMath.bearing(location).toInt()`): always a whole number. */
    val qiblaBearing: Double,
    /** The distance to the Kaaba in kilometres, to two significant figures (6,558 km is "6,600",
     * 69 km "69", and under 10 km the phone's own whole kilometre), already written in the feed's
     * digits ([WatchUi.digitZero]) and grouping: the phone's own Qibla card string where its digits
     * are kept, and Western throughout, the thousands mark too, where they are not ("6,600" in
     * English, never U+066C beside Western digits; `QiblaMath.distanceKm`,
     * `WatchFeedBuilder.roundedKm`). */
    val qiblaKm: String,
    /** The magnetic declination here, in degrees east of true north and negative to the west, to a
     * whole degree (`WorldMagneticModel.declinationDegrees`): a watch compass is good to a few. */
    val declination: Double,
    /** Whether the place is south of the equator: a flag and nothing more, for the way Moon
     * draws its phase. */
    val southern: Boolean,
)

/**
 * How the phone writes things, so that each watch writes them the same way whatever language the
 * watch itself is set to: the language, the digits, the clock and the phrases the watch's
 * screens are made of (the string bundle, spec 1.3), the app's screens' own words among them
 * ([screens]).
 */
@Serializable
data class WatchUi(
    /**
     * The words locale's tag (`wordsLocaleTag`): the phone's own tag when its language is the app's
     * interface language, with any extension it carries (`ar-SA`, `ar-SA-u-nu-latn`), and
     * otherwise the interface language's bare code (`en` for an az-AZ phone on the English
     * interface). The watch takes letter case from it and nothing else, so that it capitalises a
     * name as the app does ("MAGHRIB", never Azerbaijani's "MAGHRİB"). The words, the direction and
     * the digits come from the feed's own words, [rtl] and [digitZero], never from this tag.
     */
    val language: String,
    val rtl: Boolean,
    /**
     * The zero of the digit set the phone writes its numbers in, so that the numbers the watch
     * makes itself (the countdown's) are written in the set of the clock strings it was sent:
     * '0' Western, '٠' Arabic-Indic, '০' Bengali, '۰' extended Arabic-Indic (Urdu and
     * Persian). The nine digits follow the zero in Unicode, in each of these sets. It is the
     * phone's own answer to what a 0 looks like, which on the widgets is
     * `WidgetDigits.nativeZero(languageTag)` when the app draws its language's own digits. It
     * replaces an `arabicIndicDigits` flag from before any phone sent a feed, which could name
     * only the first of them. On the wire it is a string of one character:
     * `"digitZero":"٠"`.
     */
    val digitZero: Char = '0',
    /** Twelve hours or twenty-four, as the app's `ClockFormat` has it. */
    val twelveHour: Boolean,
    /** The twelve-hour clock's markers: `AM` and `PM`, or ص and م. */
    val am: String,
    val pm: String,
    val countdown: WatchCountdownPhrases,
    /** The "next prayer in" label, with `%1$s` where the prayer's name goes: the phone's
     * `prayer_next_in`, `%1$s in` in English and `متبقٍّ على %1$s` in Arabic. */
    val nextIn: String,
    /**
     * What a screen reader is told of the countdown, in the phone's spoken words and not the
     * screen's abbreviations (plan 1, Task 12). Null in a feed written before the field was added:
     * a watch then says the screen's words.
     */
    val spoken: WatchSpoken? = null,
    /**
     * The words of the watch app's screens and its Tile, in the phone's language. Empty in a feed
     * written before them, which only a development feed is: a watch then speaks English. Read them
     * through [WatchFeed.words], which gives the English for any word the feed left blank.
     */
    val screens: WatchScreenWords = WatchScreenWords(),
    /**
     * The zero of the digits the phone's countdown ring writes its H:MM:SS in, which Today's ring
     * copies, where they are not [digitZero]'s: '0' on an ar-SA, ar-EG or Bengali phone, whose ring
     * keeps Western digits while its clock strings do not (`CountdownDigits.westernFallback`, until
     * Arabic-Indic digits are verified tabular). Null
     * means [digitZero], and is what a feed written before the field reads as. On the wire it is a
     * string of one character, as [digitZero] is, or null.
     */
    val ringDigitZero: Char? = null,
    /**
     * Whether the app's interface language is written in Latin letters (`isLatinScriptUi()`): only
     * then does the watch show a dhikr's transliteration and write names in capitals. True in a
     * feed written before the field, whose phones all wrote Latin capitals.
     */
    val latinScript: Boolean = true,
    /**
     * The words of the Galaxy watch's face screen and Today's face chip (plan 5 Task 5), in the
     * phone's language. Empty in a feed written before them: the watch then says its own
     * resources' (`face_*`), which are also what it says before any feed.
     */
    val face: WatchFaceWords = WatchFaceWords(),
    /**
     * Whether the phone shows Sunrise: its Show sunrise setting (Settings › Prayer times,
     * `PrayerSettings.showSunrise`, off until the user turns it on), which on the phone shows or
     * hides the Prayer screen's Sunrise row and nothing else (`TimelineBuilder`). The watches follow
     * it the same way (the owner's decision of 9 November 2026): Today's rows, the Tile's rows and
     * the day dial's spoken times on the Galaxy watch, and Today's rows on the Apple Watch, have
     * Sunrise only while it is true. Nothing else moves: no countdown counts to Sunrise either way
     * ([WatchPrayer] has none), and each day keeps its Sunrise, which ends Fajr and shapes the day
     * dial. True in a feed written before the field, whose watches always showed Sunrise. A watch
     * not yet updated ignores it and goes on showing Sunrise, which leaves the rest of the feed
     * read correctly, so it joined the feed with no change to [WatchFeedJson.VERSION].
     */
    val showSunrise: Boolean = true,
)

/**
 * The five phrases of the Galaxy watch's face screen (plan 5 Task 5): the chip and button that add
 * the Taqwa face, the line that says what it is, and the line for each way the tap can end. Every
 * field is empty by default, as [WatchScreenWords]'s are, so they joined the feed with no change to
 * [WatchFeedJson.VERSION]. A field the feed leaves blank is the watch's own phrase.
 */
@Serializable
data class WatchFaceWords(
    /** "Add the Taqwa face": Today's chip and the face screen's button. */
    val add: String = "",
    /** "The next prayer and its countdown on your watch face." */
    val about: String = "",
    /** "Taqwa is your watch face.": the face is active. */
    val active: String = "",
    /** "Touch and hold your watch face, then choose Taqwa.": the one activation is spent or refused. */
    val chooseIt: String = "",
    /** "Couldn’t add the face. Try again later." */
    val failed: String = "",
)

/**
 * The countdown as the phone's screen reader says it (the Prayer screen's countdown ring, the
 * audit's sr-countdown finding): one sentence, with the hours and the minutes in full words and in
 * the plural each number takes, and never the screen's `h` and `min`, or `س` and `د`, which a
 * speech engine reads as letters. Every phrase is the phone's own string in its language; the
 * watch fills `%1$s`, `%2$s` and `%3$s` in the order given.
 */
@Serializable
data class WatchSpoken(
    /** The prayer, the time left and the prayer's clock time: `%1$s in %2$s, at %3$s`
     * (`prayer_a11y_countdown`), `%1$s بعد %2$s، الساعة %3$s` in Arabic. */
    val countdown: String,
    /** The prayer and its clock time when less than a minute is left: `%1$s in under a minute, at
     * %2$s` (`prayer_a11y_countdown_soon`). */
    val countdownSoon: String,
    /** The hours and the minutes of a time left, joined: `%1$s %2$s` (`duration_hours_minutes`),
     * `%1$s و%2$s` in Arabic. */
    val hoursMinutes: String,
    /** `%1$s hours`, in the phrase each number of hours takes (`a11y_hours`): see [WatchCount]. */
    val hours: List<WatchCount>,
    /** `%1$s minutes`, the same way (`a11y_minutes`). */
    val minutes: List<WatchCount>,
)

/**
 * The phrase for every number up to [upTo] that no earlier entry of its list takes. A list runs
 * from 0 upwards in the order of [upTo], and its last phrase takes every number above it too. The
 * phone makes the list by asking its own plural for every number from 0 to 99 and keeping one
 * entry for each run of numbers that share a phrase, so the watch picks a phrase by the number
 * alone and never by a language's plural rules. English hours are `0: %1$s hours`, `1: %1$s hour`,
 * `99: %1$s hours`; Arabic minutes are `1: %1$s دقيقة`, `2: %1$s دقيقتين`, `10: %1$s دقائق` and
 * `99: %1$s دقيقة`.
 */
@Serializable
data class WatchCount(
    val upTo: Int,
    /** The phrase, with `%1$s` where the number goes, written in the phone's digits. */
    val phrase: String,
)

/**
 * The countdown's four forms, each worded in the phone's language, and the compact forms of the
 * three that hold a number, which the Galaxy watch's Tile writes ([WatchRules.compactCountdown]).
 * The watch fills `%1$s` and `%2$s` with numbers in the phone's digits. Under a minute there is no
 * number to fill, and no compact form.
 *
 * A compact form left empty is not written at all (`EncodeDefault.Mode.NEVER`), the one exception
 * to [WatchFeedJson]'s every key on the wire, so that a language that keeps its full forms costs
 * the feed nothing (the Lead's ruling of 9 November 2026: plan 2 Task 3's worst case, all 100
 * phrases on an ar-SA phone, fits within [WatchLinkContract.FEED_BUDGET_BYTES] and the budget does
 * not move). Both readers take the key's absence for empty, as they must for a feed written
 * before it: kotlinx by the default, the Swift port by `readDefaulted`.
 */
@Serializable
data class WatchCountdownPhrases(
    /** Hours and minutes: `in %1$s h %2$s min`. */
    val hoursMinutes: String,
    /** Whole hours: `in %1$s h`. */
    val hours: String,
    /** Minutes alone: `in %1$s min`. */
    val minutes: String,
    /** Under a minute: `in under a minute`. */
    val underAMinute: String,
    /**
     * Hours and minutes as the Tile's line writes them: `in %1$sh %2$sm`, each unit a letter or so
     * with no space before it (Mohamed's report of 9 November 2026). Empty in a feed written
     * before the compact forms, and in a language that keeps its full ones: the Tile then writes
     * [hoursMinutes]. Every other surface writes the full forms, and a watch not yet updated
     * ignores these, so they joined the feed with no change to [WatchFeedJson.VERSION]. Not
     * written when empty.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val compactHoursMinutes: String = "",
    /** Whole hours as the Tile's line writes them: `in %1$sh`. Empty: [hours], and not written. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val compactHours: String = "",
    /** Minutes alone as the Tile's line writes them: `in %1$sm`. Empty: [minutes], and not written. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val compactMinutes: String = "",
)

/** One of the user's own tasbeeh phrases: its id, the phrase, and how many to count. */
@Serializable
data class WatchPhrase(
    val id: String,
    val phrase: String,
    val target: Int,
)
