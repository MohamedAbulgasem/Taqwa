package world.taqwa.app.wear

import android.content.SharedPreferences
import androidx.core.content.edit
import world.taqwa.app.wear.tile.Throttle

/**
 * What the complications and the Tile are drawn from, apart from the clock, as the app saw it when
 * it asked for both to be requested again: anything here that changes may change what they show.
 * - [feed]: the stored feed's `writtenAt`, null while there is none: every word and time they show;
 * - [newerVersion]: whether a feed in a newer format was seen (`FeedStore.newerVersionSeen`), which
 *   the Tile says as "Update Taqwa on your watch";
 * - [day]: the watch's own date, as an epoch day, in its own time zone;
 * - [zone], [locales] and [twentyFourHour]: the watch's time zone, its languages (the app's
 *   resources', a language chosen for the app alone included), and its 12- or 24-hour setting. The
 *   surfaces draw the phone's clock and words, but their own lines (the Tile's line with no feed,
 *   what the day and the dial say) are in the watch's language. Nothing they draw reads the
 *   watch's 12- or 24-hour setting today; it is kept so that the first line that does is covered;
 * - [versionCode] and [installedAt]: the build installed, and when (`PackageInfo.longVersionCode`
 *   and `lastUpdateTime`). An update keeps the app's storage, this stamp included, and so does a
 *   build installed over one of the same code (`install -r`, as in development: Ruling 71 item 5),
 *   so the first opening after either asks. A reinstall clears the stamp itself.
 */
internal data class SurfaceInputs(
    val feed: Long?,
    val newerVersion: Boolean,
    val day: Long,
    val zone: String,
    val locales: String,
    val twentyFourHour: Boolean,
    val versionCode: Long,
    val installedAt: Long,
)

/** The app's last request of both, made at [at] (epoch seconds), for [inputs]. */
internal data class SurfaceStamp(val inputs: SurfaceInputs, val at: Long)

/**
 * The owner's decision of 9 November: opening the watch app asks the complications and the Tile to
 * be requested again only when something they show could have changed since the app last asked
 * for both. Each request pushes their whole timelines into the system again, about 200 KB of
 * complications on Wear OS 6 and up to 200 KB of Tile, and the app is opened again and again
 * (every return from the face's screen, every wake), where nothing has changed.
 *
 * What it keeps of Ruling 22's net at app start: a reinstall finds no stamp, an update a new
 * version, a new feed a new [SurfaceInputs.feed] (the store asks for each feed itself, and a feed
 * whose request did not go out is asked for at the next opening), and a system that dropped what
 * it was given is asked again once the stamp is [MAX_AGE_SECONDS] old. A face newly added needs no
 * net: the system requests a source when a slot takes it.
 */
internal object SurfaceRefresh {

    /**
     * How old the last request may be before an opening asks whatever else holds, in seconds: six
     * hours, the sources' own update period (Ruling 14) and the Tile's net ([Throttle], in
     * `TodayTileService`), so an opening never asks more often than the system is asked to.
     *
     * A request draws ten prayer periods ahead (`ComplicationTimelines.PERIODS`): a day, and four
     * periods of the next, so about a day and a half at the least (36 hours on Tromsø's May vector,
     * whose morning is eleven hours long, and 40 in Cape Town). An opening this lets by therefore
     * finds the timelines reaching a whole day of the daily nudge (`DailyNudge.DAY`) past it, with
     * hours to spare for that alarm, which is inexact; twelve hours would leave Tromsø a minute
     * short of the day (`SurfaceRefreshTest.anOpeningThatDoesNotAskLeavesTheTimelinesADayAhead`).
     */
    const val MAX_AGE_SECONDS = 21_600L

    /**
     * Whether an opening at [now] (epoch seconds), with [inputs] as they are, asks both again, the
     * app's last request being [last] (null for none): when there is none, when anything in
     * [inputs] differs from what it was asked for, or when it was [MAX_AGE_SECONDS] ago or more. A
     * stamp ahead of [now] is due too, as [Throttle]'s is: the watch's clock was set back.
     */
    fun due(last: SurfaceStamp?, inputs: SurfaceInputs, now: Long): Boolean =
        last == null || last.inputs != inputs || Throttle.due(last.at, now, MAX_AGE_SECONDS)
}

/**
 * The app's last request of the complications and the Tile ([SurfaceStamp]), kept in [prefs], the
 * app's own `SharedPreferences` (the file `surfaces`), so that it outlives the process. Each write
 * is applied (`edit`'s default, `apply`): it reaches the disk in the background. One that is lost
 * with the process costs one request more at the next opening.
 *
 * An ask, and the stamp it leaves, happen under one lock, so that two openings in the same moment
 * never both ask, and an opening just after a stored feed finds the feed's stamp. Only an ask that
 * went out (`ask` answers true) is stamped: one that did not is made again at the next opening.
 */
internal class SurfaceMemory(private val prefs: SharedPreferences) {

    private val lock = Any()

    /** Runs [ask] when [SurfaceRefresh.due] says so, for an opening; true when it was due. */
    fun askIfDue(inputs: SurfaceInputs, now: Long, ask: () -> Boolean): Boolean =
        synchronized(lock) {
            if (!SurfaceRefresh.due(last(), inputs, now)) return false
            if (ask()) write(SurfaceStamp(inputs, now))
            true
        }

    /** Runs [ask] whatever the stamp says, for a feed stored, and stamps it when it went out. */
    fun askAnyway(inputs: SurfaceInputs, now: Long, ask: () -> Boolean) {
        synchronized(lock) {
            if (ask()) write(SurfaceStamp(inputs, now))
        }
    }

    /** The stamp kept, or null when there is none, or it is missing anything but the feed. */
    fun last(): SurfaceStamp? =
        try {
            if (!prefs.contains(AT) || KEPT.any { !prefs.contains(it) }) {
                null
            } else {
                SurfaceStamp(
                    SurfaceInputs(
                        feed = if (prefs.contains(FEED)) prefs.getLong(FEED, 0L) else null,
                        newerVersion = prefs.getBoolean(NEWER, false),
                        day = prefs.getLong(DAY, 0L),
                        zone = prefs.getString(ZONE, null) ?: return null,
                        locales = prefs.getString(LOCALES, null) ?: return null,
                        twentyFourHour = prefs.getBoolean(CLOCK_24, false),
                        versionCode = prefs.getLong(VERSION, 0L),
                        installedAt = prefs.getLong(INSTALLED, 0L),
                    ),
                    prefs.getLong(AT, 0L),
                )
            }
        } catch (_: ClassCastException) {
            // A key of another type, from no build of this app: no stamp, so the next opening asks.
            null
        }

    private fun write(stamp: SurfaceStamp) {
        val inputs = stamp.inputs
        prefs.edit {
            if (inputs.feed != null) putLong(FEED, inputs.feed) else remove(FEED)
            putBoolean(NEWER, inputs.newerVersion)
            putLong(DAY, inputs.day)
            putString(ZONE, inputs.zone)
            putString(LOCALES, inputs.locales)
            putBoolean(CLOCK_24, inputs.twentyFourHour)
            putLong(VERSION, inputs.versionCode)
            putLong(INSTALLED, inputs.installedAt)
            putLong(AT, stamp.at)
        }
    }

    companion object {
        /** The file in the app's `SharedPreferences` that holds the stamp. */
        const val FILE = "surfaces"

        private const val FEED = "feed"
        private const val NEWER = "newer"
        private const val DAY = "day"
        private const val ZONE = "zone"
        private const val LOCALES = "locales"
        private const val CLOCK_24 = "clock24"
        private const val VERSION = "version"
        private const val INSTALLED = "installed"
        private const val AT = "at"

        /** Every key a stamp has but the feed's, which is absent while there is no feed. */
        private val KEPT = listOf(NEWER, DAY, ZONE, LOCALES, CLOCK_24, VERSION, INSTALLED)
    }
}
