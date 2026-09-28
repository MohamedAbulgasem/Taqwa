package world.taqwa.app.feature.settings

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.day.PrayerDay
import world.taqwa.app.prayer.engine.method.AsrSchool
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A time a chosen timetable can give less cautiously than Automatic (spec §2.2, ruling R50): a
 * prayer that begins earlier, or a sunrise or an end of eating that comes later. [ASR_OTHER] is
 * the other school's Asr, which "Show both Asr times" puts under Asr.
 */
enum class CheckedTime { FAJR, DHUHR, ASR, ASR_OTHER, MAGHRIB, ISHA, SUNRISE, END_OF_EATING }

/**
 * The worst of it: [time], by up to [minutes] on the days it happens. [otherSchool] is the school
 * of [CheckedTime.ASR_OTHER]'s Asr, so the warning can name it ("Its Hanafi Asr begins…"); null
 * for every other time.
 */
data class EarlierFinding(val time: CheckedTime, val minutes: Int, val otherSchool: AsrSchool? = null)

/** What choosing a timetable found against Automatic over the next 12 months. */
sealed interface TimetableCheck {
    /** Never earlier, never later where it matters: the choice confirms at once. */
    data object NeverEarlier : TimetableCheck

    /** Earlier (or later) on some day: the warning, then keeping what is followed or "Follow it". */
    data class Earlier(val finding: EarlierFinding) : TimetableCheck

    /** The entry does not apply here (ruling R50): nothing to follow. */
    data object NotHere : TimetableCheck
}

/**
 * The earlier-than check (spec §2.2, rulings R50 and R52): a chosen timetable's own day against
 * Automatic's on every day of the next 12 months. Starts are compared for being earlier, sunrise
 * and the end of eating for being later; the finding names the start that runs furthest ahead,
 * else sunrise, else the end of eating, with the most minutes it does so on any day.
 */
class EarlierThanTally {
    private val worst = mutableMapOf<CheckedTime, Int>()

    fun add(own: PrayerDay, automatic: PrayerDay) {
        earlier(CheckedTime.FAJR, own.fajr, automatic.fajr)
        earlier(CheckedTime.DHUHR, own.dhuhr, automatic.dhuhr)
        earlier(CheckedTime.ASR, own.asr, automatic.asr)
        earlier(CheckedTime.ASR_OTHER, own.asrOther, automatic.asrOther)
        earlier(CheckedTime.MAGHRIB, own.maghrib, automatic.maghrib)
        earlier(CheckedTime.ISHA, own.isha, automatic.isha)
        later(CheckedTime.SUNRISE, own.sunrise, automatic.sunrise)
        later(CheckedTime.END_OF_EATING, own.endOfEating, automatic.endOfEating)
    }

    /** Null when nothing was ever earlier (or later). */
    fun finding(): EarlierFinding? {
        val starts = STARTS.mapNotNull { time -> worst[time]?.let { EarlierFinding(time, it) } }
        // The first start among equals, in the day's order, so the same year always names the same prayer.
        starts.maxByOrNull { it.minutes }?.let { best -> return starts.first { it.minutes == best.minutes } }
        worst[CheckedTime.SUNRISE]?.let { return EarlierFinding(CheckedTime.SUNRISE, it) }
        worst[CheckedTime.END_OF_EATING]?.let { return EarlierFinding(CheckedTime.END_OF_EATING, it) }
        return null
    }

    private fun earlier(time: CheckedTime, own: Instant, automatic: Instant) = note(time, automatic - own)

    private fun later(time: CheckedTime, own: Instant, automatic: Instant) = note(time, own - automatic)

    private fun note(time: CheckedTime, ahead: Duration) {
        val minutes = ahead.inWholeMinutes.toInt()
        if (minutes > 0) worst[time] = maxOf(worst[time] ?: 0, minutes)
    }

    companion object {
        private val STARTS = listOf(
            CheckedTime.FAJR, CheckedTime.DHUHR, CheckedTime.ASR, CheckedTime.ASR_OTHER, CheckedTime.MAGHRIB, CheckedTime.ISHA,
        )
    }
}

/** The prayer a start names, for its words: the other school's Asr is still Asr. */
fun CheckedTime.prayer(): Prayer? = when (this) {
    CheckedTime.FAJR -> Prayer.FAJR
    CheckedTime.DHUHR -> Prayer.DHUHR
    CheckedTime.ASR, CheckedTime.ASR_OTHER -> Prayer.ASR
    CheckedTime.MAGHRIB -> Prayer.MAGHRIB
    CheckedTime.ISHA -> Prayer.ISHA
    CheckedTime.SUNRISE, CheckedTime.END_OF_EATING -> null
}

/**
 * Runs the check from [from] through the day before the same date a year on, one day at a time:
 * [days] gives a date's (own, Automatic's) pair, null when the entry does not apply there.
 * [onProgress] hears the share done, a few dozen times in all. Cancellable between days, so a
 * user who leaves the screen stops it; the caller runs it off the main thread. [otherSchool] is the
 * school of both days' `asrOther`, named in the finding when that Asr is the one found.
 */
suspend fun checkAgainstAutomatic(
    from: LocalDate,
    days: (LocalDate) -> Pair<PrayerDay, PrayerDay>?,
    onProgress: (Float) -> Unit = {},
    otherSchool: AsrSchool? = null,
): TimetableCheck {
    val until = from.plus(1, DateTimeUnit.YEAR)
    val total = from.daysUntil(until)
    val tally = EarlierThanTally()
    var date = from
    for (i in 0 until total) {
        currentCoroutineContext().ensureActive()
        val (own, automatic) = days(date) ?: return TimetableCheck.NotHere
        tally.add(own, automatic)
        if (i % PROGRESS_EVERY == 0) onProgress(i.toFloat() / total)
        date = date.plus(1, DateTimeUnit.DAY)
    }
    onProgress(1f)
    return tally.finding()?.let { TimetableCheck.Earlier(it.naming(otherSchool)) } ?: TimetableCheck.NeverEarlier
}

/** [this], with [school] when it is the other school's Asr. */
private fun EarlierFinding.naming(school: AsrSchool?): EarlierFinding =
    if (time == CheckedTime.ASR_OTHER) copy(otherSchool = school) else this

/** A week at a time: enough for a line that moves, few enough not to flood the screen with frames. */
private const val PROGRESS_EVERY = 7
