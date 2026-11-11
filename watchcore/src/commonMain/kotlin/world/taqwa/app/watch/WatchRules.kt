package world.taqwa.app.watch

/**
 * The five prayers whose start the watch counts to, in the order of the day. Sunrise is not one: it
 * only ends Fajr, as it is no member of `ObligatoryPrayers` on the phone.
 */
enum class WatchPrayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA }

/**
 * One start in the feed: [prayer] begins at [at], in epoch seconds. [dayIndex] is the place in
 * [WatchFeed.days] of the day that holds it, which is not always the date it falls on: in a
 * high-latitude summer a day's Maghrib, even its Isha, can fall after midnight.
 */
data class WatchStart(val prayer: WatchPrayer, val at: Long, val dayIndex: Int)

/**
 * A moment between two starts: the [previous], which has arrived, and the [next], which has not.
 * [secondsLeft] is the time to [next], and [progress] is how far from [previous] to [next] the
 * moment has got, from 0 to 1: the fill of the ring.
 */
data class WatchMoment(
    val previous: WatchStart,
    val next: WatchStart,
    val secondsLeft: Long,
    val progress: Float,
)

/**
 * The rules every watch surface applies to the feed (spec 1.4): which start is next and which
 * came before, how far the ring has filled, how the countdown reads, which row is lit, and when
 * the watch should ask the phone for a new feed. The watch reads the feed and never works a time
 * out for itself, so these are the only decisions it makes; the face, the complications, the Tile
 * and the app's screens all make them here and nowhere else.
 *
 * They restate the phone's `TimelineBuilder` over the feed, and the watch is to behave as it does,
 * exactly:
 *
 * - [moment] is the next start, the previous start and the ring of `buildForWidgets`, which
 *   `build` gives too. The day is the one the moment is on, and the starts that count are the
 *   phone's own: yesterday's that come before today's Fajr (`yesterdaysStarts`) and all five of
 *   today's, at whatever hour they fall, with tomorrow's first start after now as the next when
 *   none of those is ahead (`firstOfTomorrowAfter`);
 * - [litOnList] is the highlight of `build`, which the Prayer screen draws, and [litOnGlance] is
 *   the highlight of `buildForWidgets`, which the home-screen widgets draw. Each watch surface
 *   copies the phone surface it stands for.
 *
 * It differs from the phone only where the feed may give it less than the phone has:
 *
 * - **A feed with no yesterday.** One that begins at today has no previous start to measure from
 *   before that day's Fajr, and [moment] is null there rather than invent one. A feed that begins
 *   at yesterday, as the phone's is to, has one.
 * - **A day with no midnight.** The phone's day turns over at the clock's midnight, which each day
 *   of the feed says in its `midnight`. A day that says 0 cannot, and turns over at solar midnight
 *   instead (see [todayIndex]).
 */
object WatchRules {

    /**
     * Every start in the feed as one list in time order: each day's five, never its Sunrise, with a
     * start left out where it falls at or after the next day's Fajr (Tromsø on 17 May: Isha at
     * 00:41 on the 18th, the 18th's Fajr at 00:38). It is the feed once every date has turned, for a
     * caller that wants one list, and [needsRefresh] ends on its last start. It is not what [moment]
     * reads: the phone counts to a day's late starts until the date turns, and a list with no dates
     * in it cannot.
     */
    fun starts(feed: WatchFeed): List<WatchStart> {
        val starts = ArrayList<WatchStart>(feed.days.size * WatchPrayer.entries.size)
        feed.days.forEachIndexed { index, day ->
            val nextFajr = feed.days.getOrNull(index + 1)?.times?.fajr
            for (prayer in WatchPrayer.entries) {
                val at = day.times.startOf(prayer)
                if (nextFajr == null || at < nextFajr) starts += WatchStart(prayer, at, index)
            }
        }
        return starts
    }

    /**
     * Where [now] (epoch seconds) sits in the feed, as `TimelineBuilder.buildForWidgets` has it for
     * the phone. The day is the one [now] is on, and the starts that count are the phone's own list:
     * yesterday's that come before today's Fajr (`yesterdaysStarts`), then all five of today's,
     * whatever hour they fall at. The first of them after [now] is the next, and the latest at or
     * before it the previous, so a start that has just arrived is the previous and begins a period.
     * Of two starts on one instant the earlier prayer is the next and the later the previous: a
     * combined polar Maghrib and Isha (R72) is the next as the Maghrib and the previous as the Isha.
     * When none of them is ahead, the next is tomorrow's first start after [now]
     * (`firstOfTomorrowAfter`); when none has arrived, the previous is yesterday's Isha.
     *
     * So a start that falls after the next day's Fajr is counted to until midnight, as the phone
     * counts to it (Tromsø in a polar night: a Maghrib at 00:45 on the 19th, the 19th's Fajr at
     * 00:38, so that from Asr on the 18th the next start is that Maghrib), and is dropped once the
     * date has turned, when it is one of yesterday's starts after today's Fajr.
     *
     * [WatchMoment.progress] is the share of the period that has passed, as
     * `TimelineBuilder.progress` has it, and [WatchMoment.secondsLeft] is the whole time to the
     * next start, which [countdown] words.
     *
     * Null when the feed has no honest answer: [now] is on no day of the feed; no start is left
     * ahead of it, because the feed has run out; or none has arrived and there is no yesterday to
     * begin the period in, as before the first Fajr of a feed that begins at that day.
     */
    fun moment(feed: WatchFeed, now: Long): WatchMoment? {
        val today = todayIndex(feed, now) ?: return null
        val starts = yesterdaysStarts(feed, today) + startsOfDay(feed, today)

        val next = starts.filter { it.at > now }.minByOrNull { it.at }
            ?: firstOfTomorrowAfter(feed, today + 1, now)
            ?: return null
        // The fallback never fires on engine data, where yesterday's Fajr is before today's and so
        // has arrived; it is `TimelineBuilder`'s own, kept so that the two cannot disagree.
        val previous = latestAtOrBefore(starts, now)
            ?: feed.days.getOrNull(today - 1)?.let { WatchStart(WatchPrayer.ISHA, it.times.isha, today - 1) }
            ?: return null

        val total = next.at - previous.at
        val elapsed = now - previous.at
        val progress = if (total <= 0L) 0f else (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        return WatchMoment(previous, next, next.at - now, progress)
    }

    /**
     * The time left to a start, [secondsLeft], in the feed's own words and digits: whole minutes,
     * floored, as hours and minutes, whole hours or minutes alone; and under a minute, the
     * phrase that has no number in it. A time that has run out is under a minute too, never a
     * negative one.
     */
    fun countdown(feed: WatchFeed, secondsLeft: Long): String {
        val phrases = feed.ui.countdown
        return worded(feed, secondsLeft, phrases.hoursMinutes, phrases.hours, phrases.minutes)
    }

    /**
     * The time left to a start as the Galaxy watch's Tile writes it on its line: [countdown]'s
     * whole minutes in the feed's compact forms, "in 1h 12m", each unit a letter or so with no
     * space before it (Mohamed's report of 9 November 2026). A form the feed leaves blank, as a
     * feed written before them leaves all three and Arabic, Urdu and Bengali leave theirs, is the
     * full one, "بعد ١ س ١٢ د", and under a minute has only the one form. What a screen reader is
     * told stays [spokenCountdown]'s full words.
     */
    fun compactCountdown(feed: WatchFeed, secondsLeft: Long): String {
        val phrases = feed.ui.countdown
        return worded(
            feed,
            secondsLeft,
            phrases.compactHoursMinutes.ifBlank { phrases.hoursMinutes },
            phrases.compactHours.ifBlank { phrases.hours },
            phrases.compactMinutes.ifBlank { phrases.minutes },
        )
    }

    /** [secondsLeft] in whole minutes, floored, in [hoursMinutes], [hours] or [minutes], and the feed's under-a-minute below one. */
    private fun worded(feed: WatchFeed, secondsLeft: Long, hoursMinutes: String, hours: String, minutes: String): String {
        if (secondsLeft < 60L) return feed.ui.countdown.underAMinute
        val totalMinutes = secondsLeft / 60L
        val h = totalMinutes / 60L
        val m = totalMinutes % 60L
        return when {
            h == 0L -> minutes.fill(digits(feed, m))
            m == 0L -> hours.fill(digits(feed, h))
            else -> hoursMinutes.fill(digits(feed, h), digits(feed, m))
        }
    }

    /**
     * The countdown to [name], whose clock time is [clock], as a screen reader is told it
     * [secondsLeft] before it, in the feed's spoken words ([WatchUi.spoken]): `Asr in 1 hour 12
     * minutes, at 16:10`. [clock] is the time as [spokenClock] gives it, so that a 12-hour time is
     * said whole, with its day period. The time left is [countdown]'s, whole minutes floored, as
     * hours and minutes, whole hours or minutes alone, each in the phrase its number takes
     * ([counted]); under a minute it is the sentence with no number in it. Null when the feed has no spoken words, one
     * written before them, or a list of phrases that is empty; the watch then says the screen's.
     */
    fun spokenCountdown(feed: WatchFeed, name: String, clock: String, secondsLeft: Long): String? {
        val spoken = feed.ui.spoken?.takeIf { it.hours.isNotEmpty() && it.minutes.isNotEmpty() } ?: return null
        if (secondsLeft < 60L) return spoken.countdownSoon.fill(name, clock)
        val totalMinutes = secondsLeft / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        val duration = when {
            hours == 0L -> counted(feed, spoken.minutes, minutes)
            minutes == 0L -> counted(feed, spoken.hours, hours)
            else -> spoken.hoursMinutes.fill(counted(feed, spoken.hours, hours), counted(feed, spoken.minutes, minutes))
        }
        return spoken.countdown.fill(name, duration, clock)
    }

    /**
     * [n] in the phrase [counts] gives it (the first whose `upTo` is [n] or more, and the last for
     * any number beyond them all), written in the feed's digits ([digits]).
     */
    fun counted(feed: WatchFeed, counts: List<WatchCount>, n: Long): String =
        (counts.firstOrNull { n <= it.upTo } ?: counts.last()).phrase.fill(digits(feed, n))

    /**
     * [n] in the digits the phone writes in: the nine that follow the feed's `digitZero`, so
     * Western, Arabic-Indic, Bengali or extended Arabic-Indic, whichever the phone drew its
     * clock strings in.
     */
    fun digits(feed: WatchFeed, n: Long): String = inDigits(n.toString(), feed.ui.digitZero)

    /**
     * [text] with each Western digit, 0 to 9, written as the digit that many places after [zero]:
     * '0' leaves it as it is, '٠' gives Arabic-Indic, '০' Bengali and '۰' extended Arabic-Indic.
     * Everything else stays as it is, digits of another set too. A number the watch makes itself
     * is written this way: the countdown's in the feed's `digitZero`, and Today's H:MM:SS in
     * `ringDigitZero ?: digitZero`, the phone ring's.
     */
    fun inDigits(text: String, zero: Char): String {
        if (zero == '0') return text
        return buildString(text.length) {
            for (c in text) append(if (c in '0'..'9') zero + (c - '0') else c)
        }
    }

    /**
     * [pattern] with its places written in: each `%n$s` becomes the nth of [parts], wherever it
     * stands, so a language may put `%2$s` before `%1$s` (round 2's Bengali count, "%2$s-এর মধ্যে
     * %1$s"), and a place may come more than once. A part goes in as it is and is never filled in
     * turn, so a phrase of his own that holds "%2$s" is said as he typed it. A place with no part
     * stays as it is. The feed's phrases are the phone's strings read as their patterns, and their
     * places are always `%n$s`.
     */
    fun filled(pattern: String, vararg parts: String): String {
        if (parts.isEmpty()) return pattern
        return PLACE.replace(pattern) { place ->
            place.groupValues[1].toIntOrNull()?.let { parts.getOrNull(it - 1) } ?: place.value
        }
    }

    /**
     * The clock time of [time] on [day] as a surface draws it: the app's own 12-hour time with its
     * day period ([WatchDay.clockTime], "4:10 PM") from a 12-hour phone that sent it, and the
     * day's [WatchDay.clock] otherwise ("16:10"). Sunrise is drawn this way as the five are.
     */
    fun clockText(day: WatchDay, time: WatchTime): String = day.clockTime?.of(time) ?: day.clock.of(time)

    /**
     * The clock time of [time] on [day] spliced into a spoken sentence: [clockText], between a
     * first-strong isolate and its pop (U+2068, U+2069) when it holds anything but digits and a
     * colon, so the sentence around it cannot reorder its pieces and an Urdu "6:32 PM" is never said
     * "PM 6:32". That is the phone's `isolatedClock` rule: a 24-hour time, in any digit set, comes
     * back as it is, so every 24-hour line is byte for byte what it was. Every spoken line on the
     * watch splices its times through here, never the bare [WatchDay.clock].
     */
    fun spokenClock(day: WatchDay, time: WatchTime): String {
        val text = clockText(day, time)
        return if (text.all { it.isDigit() || it == ':' }) text else "$FIRST_STRONG_ISOLATE$text$POP_DIRECTIONAL_ISOLATE"
    }

    /**
     * The row the Prayer screen lights at [now] (`TimelineBuilder.build`), which is the watch
     * app's Today list: the latest of today's prayers to have begun, except that Fajr stops
     * being lit at sunrise and Asr at sunset (`DayPrayerTimes.ends`). A prayer without an end,
     * as on a polar day, stays lit until the next; of two on one instant the first is lit. Before
     * today's Fajr nothing is lit, and yesterday's Isha, though it has begun, lights no row. Null
     * when nothing is.
     */
    fun litOnList(feed: WatchFeed, now: Long): WatchPrayer? {
        val index = todayIndex(feed, now) ?: return null
        val latest = startsOfDay(feed, index).filter { it.at <= now }.maxByOrNull { it.at } ?: return null

        // Today's prayer is current only when nothing of yesterday's began after it. No start of
        // an engine day is earlier than that day's Fajr, so this never fires on its data; it is
        // `TimelineBuilder.build`'s own guard, kept so that the two cannot disagree.
        val latestOfYesterday = yesterdaysStarts(feed, index).filter { it.at <= now }.maxOfOrNull { it.at }
        if (latestOfYesterday != null && latest.at < latestOfYesterday) return null

        return latest.prayer.takeUnless { hasEndedForTheHighlight(it, feed.days[index].times, now) }
    }

    /**
     * The row the home-screen widgets light at [now] (`TimelineBuilder.buildForWidgets`), which
     * is the Tile's and the Smart Stack's: the latest of today's prayers to have begun, which
     * stays lit until the next begins; of two on one instant the last is lit. No end applies.
     * Before today's Fajr nothing is lit. Null when nothing is.
     *
     * The widgets move to the Prayer screen's rule in the Ramadan release, and the Tile and the
     * Smart Stack move with them (spec 1.4): this function is then [litOnList].
     */
    fun litOnGlance(feed: WatchFeed, now: Long): WatchPrayer? {
        val index = todayIndex(feed, now) ?: return null
        return startsOfDay(feed, index).lastOrNull { it.at <= now }?.prayer
    }

    /**
     * Whether the watch should ask the phone for a new feed (spec 2.2): the last start is less
     * than three days away, or past; or the feed was written more than a week ago, which the
     * phone's own sends should never let happen while it is in reach. A feed with no start at all
     * needs one too.
     */
    fun needsRefresh(feed: WatchFeed, now: Long): Boolean {
        val last = starts(feed).lastOrNull() ?: return true
        return last.at - now < REFRESH_WITHIN || now - feed.writtenAt > REFRESH_AFTER
    }

    /**
     * The place in [WatchFeed.days] of the day that [now] (epoch seconds) is on, or null when the
     * feed has no such day: the day every rule here works on, and so the day a surface that shows
     * the date (a complication's, the Today screen's) has to take it from, to turn over where the
     * rules do. It is a day's own midnight that turns it over, and not the clock of a watch whose
     * zone is not the place's. See [todayIndex] for how the day is found.
     */
    fun today(feed: WatchFeed, now: Long): Int? = todayIndex(feed, now)

    /**
     * Where the day at [index] in [WatchFeed.days] begins (epoch seconds), as [today] turns the date
     * over: the day's own midnight, or solar midnight where the phone could not say (see
     * [todayIndex]). A surface that draws a date from its first second, as the Sun style's dial
     * does, begins it here, so that it turns over where the rules do and keeps no rule of its own.
     * [index] is a place in the feed's days.
     */
    fun dayStart(feed: WatchFeed, index: Int): Long {
        require(index in feed.days.indices) { "no day $index in a feed of ${feed.days.size}" }
        return turnOver(feed.days, index)
    }

    /**
     * The place in [WatchFeed.days] of the day that [now] is on, or null when the feed has no such
     * day. The rules work on *today's* starts, and the phone knows today from the date at the
     * place. The feed carries no time zone, but each day carries its [WatchDay.midnight], and a
     * day is the stretch from its midnight to the next day's: the phone's own date, exactly, on a
     * date of 23 or 25 hours too. The last day ends a day after its midnight, which is an hour out
     * if that date has a clock change in it, at the end of a feed that is nearly spent anyway.
     *
     * A day whose midnight is 0, which is what the phone says when it cannot say, begins at solar
     * midnight instead: halfway between its Dhuhr and the day before's, which the engine never
     * moves for the polar rules. In most places that falls in the night between Isha and Fajr,
     * within about an hour and a half of the clock's midnight; at Tromsø in May, where Fajr is at
     * 00:38, it falls at 00:46, after Fajr. The rules then differ from the phone's in the hours
     * between the two midnights, when the watch is still on the day before or already on the day
     * after. A feed that lacks one midnight falls back at that day's boundary alone.
     */
    private fun todayIndex(feed: WatchFeed, now: Long): Int? {
        val days = feed.days
        if (days.isEmpty()) return null

        // Day i holds [turnOver(i), turnOver(i + 1)).
        val index = days.indices.lastOrNull { turnOver(days, it) <= now } ?: return null
        return index.takeIf { now < turnOver(days, index + 1) }
    }

    /**
     * Where day [i] of [days] begins, and for [i] one past the last day where the last one ends.
     * The first and last days have no neighbour to halve the distance to, and take half a day
     * from their own Dhuhr.
     */
    private fun turnOver(days: List<WatchDay>, i: Int): Long {
        val midnight = days.getOrNull(i)?.midnight ?: 0L
        return when {
            midnight != 0L -> midnight
            i == 0 -> days.first().times.dhuhr - HALF_A_DAY
            i == days.size -> days.last().let { last ->
                if (last.midnight != 0L) last.midnight + A_DAY else last.times.dhuhr + HALF_A_DAY
            }
            else -> (days[i - 1].times.dhuhr + days[i].times.dhuhr) / 2
        }
    }

    /** Day [index]'s five starts in the order of the day, at whatever hour each falls. */
    private fun startsOfDay(feed: WatchFeed, index: Int): List<WatchStart> {
        val times = feed.days[index].times
        return WatchPrayer.entries.map { WatchStart(it, times.startOf(it), index) }
    }

    /**
     * `TimelineBuilder.yesterdaysStarts`: the starts of the day before day [today] that come before
     * its Fajr. In a high-latitude summer one of them can fall after midnight, on today's date; it
     * counts while it is ahead, and only when it comes before today's Fajr: once today's Fajr is in,
     * the night is over, whatever a later start of yesterday's says.
     */
    private fun yesterdaysStarts(feed: WatchFeed, today: Int): List<WatchStart> {
        if (today == 0) return emptyList()
        val fajr = feed.days[today].times.fajr
        return startsOfDay(feed, today - 1).filter { it.at < fajr }
    }

    /**
     * `TimelineBuilder.firstOfTomorrowAfter`: the first of day [tomorrow]'s starts that is still
     * after [now]. Null when the feed holds no such day, or when none of its starts is ahead, which
     * engine data never does: the phone answers tomorrow's Fajr there, a time already past.
     */
    private fun firstOfTomorrowAfter(feed: WatchFeed, tomorrow: Int, now: Long): WatchStart? {
        if (tomorrow > feed.days.lastIndex) return null
        return startsOfDay(feed, tomorrow).filter { it.at > now }.minByOrNull { it.at }
    }

    /** The latest of [starts] at or before [now]; of two on one instant, the later in the list. */
    private fun latestAtOrBefore(starts: List<WatchStart>, now: Long): WatchStart? {
        var latest: WatchStart? = null
        for (start in starts) {
            if (start.at <= now && (latest == null || start.at >= latest.at)) latest = start
        }
        return latest
    }

    /** `TimelineBuilder.hasEndedForTheHighlight`: Fajr from sunrise, Asr from sunset, none else. */
    private fun hasEndedForTheHighlight(prayer: WatchPrayer, day: WatchTimes, now: Long): Boolean = when (prayer) {
        WatchPrayer.FAJR -> day.fajrEnd?.let { now >= it } ?: false
        WatchPrayer.ASR -> day.asrEnd?.let { now >= it } ?: false
        else -> false
    }

    private fun WatchTimes.startOf(prayer: WatchPrayer): Long = when (prayer) {
        WatchPrayer.FAJR -> fajr
        WatchPrayer.DHUHR -> dhuhr
        WatchPrayer.ASR -> asr
        WatchPrayer.MAGHRIB -> maghrib
        WatchPrayer.ISHA -> isha
    }

    /** A phrase with its parts written in, by [filled]. */
    private fun String.fill(vararg parts: String): String = filled(this, *parts)

    /** `%1$s`, `%2$s` and so on: a place in one of the feed's phrases, and the number of its part. */
    private val PLACE = Regex("%([0-9]+)\\\$s")

    private const val FIRST_STRONG_ISOLATE = '\u2068'
    private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

    private const val A_DAY = 86_400L
    private const val HALF_A_DAY = A_DAY / 2
    private const val REFRESH_WITHIN = 3 * A_DAY
    private const val REFRESH_AFTER = 7 * A_DAY
}
