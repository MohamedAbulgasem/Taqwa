package world.taqwa.app.wear

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import world.taqwa.app.watch.WatchDay
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson

/**
 * The debug build's feed until a phone has sent one: 31 days of Cape Town in English and a 24-hour
 * clock, the very file `:watchcore` commits as `vectors/sample-feed-capetown.json`
 * (`Fixtures.capeTownSample()`, which `VectorsTest` keeps in step), copied byte for byte into the
 * debug assets with the other samples and nothing else of the vectors (`sampleFeedAssets` in
 * wearApp/build.gradle.kts). Debug builds only: this source set does not exist in the release build.
 *
 * The sample is dated from Saturday 17 October 2026, and a watch is looked at on whatever day it
 * is, so [redate] moves it by whole days to the date wanted. Which date, and where from:
 *
 * - [installIfEmpty] runs when the app starts and installs the sample for today, if no feed is
 *   stored yet;
 * - the `DEBUG_FEED` broadcast (DebugFeedReceiver) installs it for the date of the `epoch` it is
 *   given, for a screenshot at 14:58 on a day of the developer's choosing, or one of the other
 *   samples ([NAMES]: Arabic, a twelve-hour clock) when its `sample` extra names one.
 *
 * Either way the sample is stored with no delivery (`FeedStore.write`'s default), and the delivery
 * held is removed first, so a sample never carries a phone's. The watch then holds nothing from the
 * phone: its next handshake (at its start, on a reconnect, daily) says so, and a paired phone that
 * is nearby replaces the sample with its own feed (plan 2 Task 6).
 *
 * **The hold** ([HOLD]): a watch cleared with the broadcast's `--ez clear true` is to stay as a
 * watch that has never held a feed, its process's next start included, so that "Open Taqwa on your
 * phone" can be looked at (plan 3 Task 4). The receiver then [hold]s the sample: [installIfEmpty]
 * installs nothing while the hold file is in the app's files, and [install] lifts it only when the
 * store takes the feed. So a newer format offered to a cleared watch (`--ei version 2`) leaves it
 * without a feed, saying "Update Taqwa on your watch".
 */
object SampleFeed {

    /** The hold's file, in the app's files: while it is there, nothing installs the sample at start. */
    const val HOLD = "sample.held"

    /** The sample in the debug assets, as `sampleFeedAssets` copies it from `:watchcore`'s vectors. */
    private const val ASSET = "sample-feed-capetown.json"

    /**
     * The other samples, by the name the `DEBUG_FEED` broadcast's `sample` extra gives
     * (`Fixtures.capeTownSamples()` in `:watchcore`, committed beside the first): the same days in
     * Arabic and on a twelve-hour clock, so that the face is looked at as those phones write it.
     */
    val NAMES: List<String> = listOf("en12", "ar-ly12", "ar-sa")

    /** The asset of the sample called [name], the first one for an empty name, or null for a name there is no sample of. */
    internal fun assetOf(name: String): String? = when (name) {
        "" -> ASSET
        in NAMES -> "sample-feed-capetown-$name.json"
        else -> null
    }

    /**
     * The date of the sample's second day, Sunday 18 October 2026: the day the mockups draw, the
     * one every vector in `:watchcore` is worked on, and the day [redate] lands on the date wanted.
     * The day before it is the first, because the phone's feed begins at yesterday (spec 1.1).
     */
    private const val MOCK_DATE = "2026-10-18"

    /**
     * The zone the date is read in: South Africa's, where the sample's city is. It keeps UTC+2 all
     * year, so a day there is always 86,400 seconds, and the sample's midnights are still midnights
     * once it has been moved by whole days.
     */
    private val ZONE: ZoneId = ZoneId.of("Africa/Johannesburg")

    private const val DAY_SECONDS = 86_400L

    /**
     * Stores the sample for today ([nowSeconds]) when no feed is stored, and says whether it did.
     * A feed that is stored, a real one from a phone, is never touched. An asset that cannot be
     * read is thrown: it means the build is wrong, and a debug build is the place to hear of it.
     */
    fun installIfEmpty(
        context: Context,
        store: FeedStore,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): Boolean = installIfEmpty(context.filesDir, store, nowSeconds) { readAsset(context) }

    /**
     * Stores the sample called [name] ([NAMES], or the first sample for an empty name) for the date
     * of [epochSeconds], over whatever is stored. With a [version], it is offered in that format
     * instead (a version above this build's is turned away as `NEWER_VERSION`, and whatever is
     * stored stays); with a [city], it names that city, for the longest city's shots.
     */
    fun install(
        context: Context,
        store: FeedStore,
        epochSeconds: Long,
        name: String = "",
        version: Int? = null,
        city: String? = null,
    ): FeedWriteResult {
        val asset = requireNotNull(assetOf(name)) { "there is no sample called '$name': the names are ${NAMES.joinToString()}" }
        return install(context.filesDir, store, epochSeconds, readAsset(context, asset), version, city)
    }

    /** Holds the sample: until a feed is stored, nothing installs it at start ([HOLD]). */
    fun hold(filesDir: File) {
        filesDir.mkdirs()
        File(filesDir, HOLD).writeText("")
    }

    /** Whether the sample is held ([hold]). */
    fun held(filesDir: File): Boolean = File(filesDir, HOLD).exists()

    internal fun installIfEmpty(filesDir: File, store: FeedStore, nowSeconds: Long, sampleJson: () -> String): Boolean {
        if (held(filesDir) || store.changes.value != null) return false
        return install(filesDir, store, nowSeconds, sampleJson()) == FeedWriteResult.ACCEPTED
    }

    internal fun install(
        filesDir: File,
        store: FeedStore,
        epochSeconds: Long,
        sampleJson: String,
        version: Int? = null,
        city: String? = null,
    ): FeedWriteResult {
        val sample = checkNotNull(WatchFeedJson.decode(sampleJson)) { "the sample feed is not a feed this build reads" }
        var feed = redate(sample, epochSeconds)
        if (version != null) feed = feed.copy(version = version)
        if (city != null) feed = feed.copy(place = feed.place.copy(city = city))
        val result = store.write(WatchFeedJson.encode(feed))
        // Only a feed the store took ends the hold: an offer it turned away leaves the watch as it was.
        if (result == FeedWriteResult.ACCEPTED) File(filesDir, HOLD).delete()
        return result
    }

    /**
     * [feed] moved by whole days so that its Sunday 18 October 2026 is the date of [epochSeconds]
     * in Johannesburg. Everything that is an instant moves by the same number of days: every time
     * of every day, the ends, each day's midnight (0, which says the phone could not tell, stays
     * 0), and when the feed was written, which stays 08:00 on the mock day. The words stay as they
     * are, the date strings and the Hijri date among them: they are display text, and a sample
     * is allowed to name a Sunday on a Thursday.
     */
    internal fun redate(feed: WatchFeed, epochSeconds: Long): WatchFeed {
        check(feed.days.any { it.date == MOCK_DATE }) { "the sample has no day dated $MOCK_DATE" }
        val target = Instant.ofEpochSecond(epochSeconds).atZone(ZONE).toLocalDate()
        val seconds = (target.toEpochDay() - LocalDate.parse(MOCK_DATE).toEpochDay()) * DAY_SECONDS
        return feed.copy(writtenAt = feed.writtenAt + seconds, days = feed.days.map { it.movedBy(seconds) })
    }

    private fun WatchDay.movedBy(seconds: Long): WatchDay = copy(
        times = times.copy(
            fajr = times.fajr + seconds,
            sunrise = times.sunrise + seconds,
            dhuhr = times.dhuhr + seconds,
            asr = times.asr + seconds,
            maghrib = times.maghrib + seconds,
            isha = times.isha + seconds,
            fajrEnd = times.fajrEnd?.plus(seconds),
            asrEnd = times.asrEnd?.plus(seconds),
        ),
        midnight = if (midnight == 0L) 0L else midnight + seconds,
    )

    private fun readAsset(context: Context, asset: String = ASSET): String =
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
