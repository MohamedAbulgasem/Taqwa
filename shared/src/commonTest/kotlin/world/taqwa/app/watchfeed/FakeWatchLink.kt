package world.taqwa.app.watchfeed

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import world.taqwa.app.watch.WatchCountdownPhrases
import world.taqwa.app.watch.WatchDay
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchLinkContract
import world.taqwa.app.watch.WatchPhrase
import world.taqwa.app.watch.WatchPlace
import world.taqwa.app.watch.WatchSix
import world.taqwa.app.watch.WatchTimes
import world.taqwa.app.watch.WatchUi
import kotlin.time.Duration

/**
 * A watch link for the sync's tests that records every call and answers as it is told. It
 * suspends only by `delay` or on a [CompletableDeferred], so a budget around it is cut on virtual
 * time and nowhere else (plan 2, Global Constraints, "Tests on virtual time").
 *
 * - [reachable] is the answer to the cheap question; [reachableTakes] delays it, [reachableGate]
 *   holds it until completed, and [reachableThrows] throws instead;
 * - [key] is the session's watch key ([WatchLink.watchKey]): null, as on Android, unless a test
 *   plays the iPhone's watches;
 * - [result] is each send's answer unless [answers] still holds one for it, in order, or the
 *   JSON's UTF-8 is longer than [limitBytes], which answers [WatchSendResult.TOO_LARGE];
 *   [sendTakes] delays each send unless [takes] still holds a delay for it, [gate] holds it until
 *   completed (a gate never completed is a send that never answers), and [sendThrows] throws
 *   instead; [onSend] runs first, as the link is called;
 * - [sent] holds every send as it was made, before any wait, so a send cut off by a budget is
 *   still in it.
 */
internal class FakeWatchLink(override val confirmsDelivery: Boolean = false) : WatchLink {

    /** One send as the link received it: the JSON and the delivery beside it. */
    data class Sent(val json: String, val delivery: WatchDelivery) {
        val feed: WatchFeed get() = requireNotNull(WatchFeedJson.decode(json)) { "not a feed: $json" }
        val fingerprint: String get() = delivery.fingerprint
        val seq: Long get() = delivery.seq

        /** What a watch that stored this send echoes. */
        val echo: String get() = WatchLinkContract.echoOf(delivery)
    }

    var reachable: Boolean = true
    var reachableTakes: Duration = Duration.ZERO
    var reachableGate: CompletableDeferred<Unit>? = null
    var reachableThrows: Exception? = null
    var reachableAsked: Int = 0
        private set

    var key: String? = null

    var result: WatchSendResult = WatchSendResult.SENT
    var limitBytes: Int = Int.MAX_VALUE
    val answers: ArrayDeque<WatchSendResult> = ArrayDeque()
    var sendTakes: Duration = Duration.ZERO
    val takes: ArrayDeque<Duration> = ArrayDeque()
    var gate: CompletableDeferred<Unit>? = null
    var sendThrows: Exception? = null
    var onSend: (suspend (Sent) -> Unit)? = null
    val sent: MutableList<Sent> = mutableListOf()

    override suspend fun reachable(): Boolean {
        reachableAsked++
        reachableThrows?.let { throw it }
        if (reachableTakes > Duration.ZERO) delay(reachableTakes)
        reachableGate?.await()
        return reachable
    }

    override suspend fun watchKey(): String? = key

    override suspend fun send(json: String, delivery: WatchDelivery): WatchSendResult {
        val one = Sent(json, delivery)
        sent += one
        onSend?.invoke(one)
        sendThrows?.let { throw it }
        val takes = takes.removeFirstOrNull() ?: sendTakes
        if (takes > Duration.ZERO) delay(takes)
        gate?.await()
        return answers.removeFirstOrNull()
            ?: if (json.encodeToByteArray().size > limitBytes) WatchSendResult.TOO_LARGE else result
    }
}

/**
 * A watch for the sync's tests that stores by the receivers' rule ([WatchLinkContract.stores]) and
 * echoes what it holds ([WatchLinkContract.echoOf]), as plan 2 Task 6 and plan 4 Task 5 build theirs.
 * [names] is the test's own (Ruling 52 item 7): no cache outlives the test that made it.
 */
internal class TestWatch(private val names: FeedNames = FeedNames()) {
    var held: FakeWatchLink.Sent? = null
        private set

    /** Takes [sent] when it moves the watch forward, and answers its acknowledgement; null when dropped. */
    fun take(sent: FakeWatchLink.Sent): String? {
        if (!WatchLinkContract.stores(held?.delivery, sent.delivery)) return null
        held = sent
        return echo
    }

    /** Its acknowledgement and its handshake alike. */
    val echo: String get() = WatchLinkContract.echoOf(held?.delivery)

    /** Whether it holds [feed]'s bytes, or, for a feed over the link's limit, its times alone. */
    fun holds(feed: WatchFeed): Boolean = held?.let { it.fingerprint in names.heldAs(feed) } == true

    /** The app's data gone: a reinstall, a cleared app, a reset. */
    fun wipe() {
        held = null
    }
}

/**
 * The fingerprints a watch holding a feed may hold, kept for one test, so the model test does not
 * encode the same few feeds again at every check. Each test makes its own, so no two tests share
 * one (Ruling 52 item 7).
 */
internal class FeedNames {
    private val names = mutableMapOf<WatchFeed, Set<String>>()

    /** [feed]'s own fingerprint, and its times alone's. */
    fun heldAs(feed: WatchFeed): Set<String> = names.getOrPut(feed) {
        setOf(watchFeedFingerprint(feed), watchFeedFingerprint(feed.copy(tasbeeh = emptyList())))
    }
}

/**
 * The feeds the sync's tests send: one small day, whose Fajr moves by [fajrShiftMinutes] (a
 * Manual adjustments stepper's + is one), with the user's [phrases], written at [writtenAt]. Only
 * what tells feeds apart matters here; the builder's own tests hold what a real feed says.
 */
internal fun syncFeed(
    fajrShiftMinutes: Int = 0,
    phrases: List<WatchPhrase> = emptyList(),
    writtenAt: Long = 1_792_303_200L,
    declination: Double = 26.0,
): WatchFeed {
    val midnight = 1_792_274_400L // Sunday 18 October 2026, 00:00 in Cape Town
    fun at(hour: Int, minute: Int): Long = midnight + (hour * 60L + minute) * 60L
    val fajr = at(4, 33) + fajrShiftMinutes * 60L
    return WatchFeed(
        version = WatchFeedJson.VERSION,
        writtenAt = writtenAt,
        days = listOf(
            WatchDay(
                date = "2026-10-18",
                times = WatchTimes(
                    fajr = fajr,
                    sunrise = at(6, 1),
                    dhuhr = at(12, 33),
                    asr = at(16, 10),
                    maghrib = at(19, 4),
                    isha = at(20, 24),
                ),
                clock = WatchSix("4:33", "6:01", "12:33", "16:10", "19:04", "20:24"),
                names = WatchSix("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
                hijri = "6 Jumada al-Awwal 1448",
                gregorian = "SUN 18 OCT",
                weekday = "SUN",
                dayNumber = "18",
                midnight = midnight,
            ),
        ),
        place = WatchPlace(
            city = "Cape Town",
            qiblaBearing = 23.0,
            qiblaKm = "6,600",
            declination = declination,
            southern = true,
        ),
        ui = WatchUi(
            language = "en",
            rtl = false,
            twelveHour = false,
            am = "AM",
            pm = "PM",
            countdown = WatchCountdownPhrases("in %1\$s h %2\$s min", "in %1\$s h", "in %1\$s min", "in under a minute"),
            nextIn = "%1\$s in",
        ),
        tasbeeh = phrases,
    )
}

/** One of the user's own phrases, as the feed carries it. */
internal fun syncPhrase(index: Int): WatchPhrase = WatchPhrase("custom_$index", "سبحان الله $index", 33)
