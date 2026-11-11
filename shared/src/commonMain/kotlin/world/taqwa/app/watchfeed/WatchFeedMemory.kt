package world.taqwa.app.watchfeed

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import world.taqwa.app.settings.SettingsKeys
import world.taqwa.app.watch.WatchDelivery
import world.taqwa.app.watch.WatchLinkContract
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What the phone knows of its watch: one record (Ruling 50 item 3, as the design checks amended
 * it). Every field is a number, a fingerprint or a random key; none names a place or a person.
 *
 * - [origin]: this install, a random number made once (item 1). It travels with every feed.
 * - [lastSeq]: the highest delivery number this install has used, 0 before the first: sent, or set
 *   aside by a forget ([forgotten]). A number is written down before the link is called, so it is
 *   never used for another feed.
 * - [lastStandsFor]: the fingerprint of the full feed that number's send stood for; the times
 *   alone after a `TOO_LARGE` stand for the full feed the link refused. Null when that number was
 *   set aside, or nothing it stood for is known.
 * - [lastSent]: the fingerprint that number travelled with: the times alone after a `TOO_LARGE`,
 *   otherwise [lastStandsFor] (amendment A4). Null when [lastStandsFor] is.
 * - [confirmedSeq]: the number named by the last echo of this origin the phone took in, lower or
 *   higher (amendment A1b), or, on a link that does not confirm, the number it handed over last
 *   (item 4). Null when nothing is confirmed.
 * - [confirmedFor]: the watch [confirmedSeq] counts for (amendment A3): on the iPhone the key the
 *   bridge keeps in that watch's own directory; on Android the Wear node id of the watch whose
 *   echo was taken in (Ruling 52 item 9).
 *
 * Only [WatchFeedSync]'s worker reads or writes it, through [WatchFeedMemory].
 */
internal data class WatchFeedRecord(
    val origin: String,
    val lastSeq: Long = 0,
    val lastStandsFor: String? = null,
    val lastSent: String? = null,
    val confirmedSeq: Long? = null,
    val confirmedFor: String? = null,
) {
    /** The delivery [lastSeq] travelled with, or null before the first send. */
    val lastDelivery: WatchDelivery?
        get() = lastSent?.takeIf { lastSeq >= 1 }?.let { WatchDelivery(origin, lastSeq, it) }

    /**
     * Item 3: an ask about the feed named [fingerprint], while a send goes to the watches [keys]
     * name ([WatchLink.watchKeys]), is `UNCHANGED` exactly when the watch [confirmedFor] names has
     * confirmed the latest delivery, that watch is one of them, and that delivery stood for this
     * feed. Everything else sends. So a confirmation counts only while its watch is one a send goes
     * to (amendment A3; Ruling 52 item 9): the iPhone's session watch, or one of the Android
     * phone's nearby watches with Taqwa. A watch switched to between asks never inherits another's.
     * One switched to while an Android ask builds its feed is not in that ask's [keys], read before
     * the build: `WatchFeedSync` step 4's known limit.
     */
    fun holds(fingerprint: String, keys: Set<String?>): Boolean =
        confirmedSeq != null && confirmedSeq == lastSeq && lastStandsFor == fingerprint && confirmedFor in keys

    /** [holds] while a send goes to the one watch [key] names: the iPhone's session watch. */
    fun holds(fingerprint: String, key: String?): Boolean = holds(fingerprint, setOf(key))

    /** Item 1: the record as written down before a send, under the next number. */
    fun sending(standsFor: String, sent: String): WatchFeedRecord =
        copy(lastSeq = lastSeq + 1, lastStandsFor = standsFor, lastSent = sent)

    /**
     * Item 4: a link that does not confirm handed [lastSeq] over to the watch [key] names, and the
     * system delivers the latest handed context and nothing older after it.
     */
    fun handedOver(key: String?): WatchFeedRecord = copy(confirmedSeq = lastSeq, confirmedFor = key)

    /**
     * Item 6 and amendment A2: nothing is confirmed, and the next number is set aside, standing for
     * nothing and never sent, so that no echo the watch sent before the forget (an acknowledgement
     * still on its way when the watch was wiped, say) can name the latest number again. The next
     * send takes the number after it. Numbers are never given back.
     */
    fun forgotten(): WatchFeedRecord =
        copy(lastSeq = lastSeq + 1, lastStandsFor = null, lastSent = null, confirmedSeq = null, confirmedFor = null)

    /**
     * The record once the watch [from] names has echoed [echo] (null: it holds nothing the phone
     * can name), taken in only from the watches [keys] name:
     * - from a watch that is not one of them: ignored. On the iPhone [keys] is the session's one
     *   watch (amendment A3). On Android the sync passes the echoing node itself, since every echo
     *   is taken in there, for the node it came from (Ruling 52 item 9), and counts only while that
     *   node is nearby ([holds]);
     * - nothing, another origin's, or one numbered above [MAX_ECHOED_SEQ] (Ruling 52 item 3: no
     *   phone of ours numbers that far, and a lift there would run the next numbers past a `Long`):
     *   [forgotten];
     * - this origin's, above [lastSeq], or at [lastSeq] with another fingerprint than [lastSent]
     *   (the numbers fell behind: a record restored despite item 1): [lastSeq] is lifted to it and
     *   nothing it stood for is known, so the next ask sends above it (item 5, amendment A4);
     * - otherwise its number is confirmed for that watch, lower or higher (A1b). Every echo is true
     *   when the watch sends it, within one origin a watch only moves forward, and an echo sent
     *   before a forget names a number below the one the forget set aside. So a stale echo can
     *   only make the phone send once more, except within plan 2's known limits: one sent before a
     *   wipe or a straggler the phone has not heard of.
     */
    fun echoed(echo: WatchDelivery?, from: String?, keys: Set<String?>): WatchFeedRecord = when {
        from !in keys -> this
        echo == null || echo.origin != origin || echo.seq > MAX_ECHOED_SEQ -> forgotten()
        echo.seq > lastSeq || (echo.seq == lastSeq && echo.fingerprint != lastSent) ->
            copy(lastSeq = echo.seq, lastStandsFor = null, lastSent = null, confirmedSeq = echo.seq, confirmedFor = from)
        else -> copy(confirmedSeq = echo.seq, confirmedFor = from)
    }

    /** [echoed] while a send goes to the one watch [session] names: the iPhone's session watch. */
    fun echoed(echo: WatchDelivery?, from: String?, session: String?): WatchFeedRecord = echoed(echo, from, setOf(session))

    companion object {
        /**
         * The highest number an echo may lift the record to (Ruling 52 item 3): half a `Long`.
         * Above it an echo counts as another install's, so neither a send nor a forget after it can
         * overflow, which would leave every later ask `FAILED`. A phone that counts one number a
         * send never comes near it.
         */
        const val MAX_ECHOED_SEQ: Long = Long.MAX_VALUE / 2
    }
}

/**
 * Where [WatchFeedRecord] is kept. Only [WatchFeedSync]'s worker touches it.
 */
internal interface WatchFeedMemory {
    /** The record. The first read of an install makes its origin, in one edit with an empty record. */
    suspend fun read(): WatchFeedRecord

    /** Writes [record] whole, origin and numbers together, in one edit. */
    suspend fun write(record: WatchFeedRecord)
}

/**
 * The record in the app's settings store, so that it outlives the process: a reschedule after a
 * restart is still not sent to a watch that has echoed the feed, and a number is never used twice.
 *
 * The settings store is never restored from a backup (Android's data extraction rules exclude it,
 * and `DataStoreFactory.ios.kt` marks its directory excluded from backup), so a new install, a
 * cleared app or a new phone makes a new origin (item 1). Origin and numbers share the one file and
 * every edit, so the store's `ReplaceFileCorruptionHandler`, which resets the whole file, takes
 * them away together: numbers never start again under a kept origin. A store whose origin is gone,
 * or holds one `WatchLinkContract.isOrigin` refuses (Ruling 52 item 3: every send's delivery would
 * refuse it, and every ask would fail for good), starts a whole new record under a new one.
 */
internal class DataStoreWatchFeedMemory(
    private val store: DataStore<Preferences>,
    private val newOrigin: () -> String = ::newWatchOrigin,
) : WatchFeedMemory {

    override suspend fun read(): WatchFeedRecord {
        store.data.first().let { stored -> recordOf(stored)?.let { return it } }
        var made: WatchFeedRecord? = null
        store.edit { prefs ->
            made = recordOf(prefs) ?: WatchFeedRecord(newOrigin()).also { put(prefs, it) }
        }
        return checkNotNull(made)
    }

    override suspend fun write(record: WatchFeedRecord) {
        store.edit { put(it, record) }
    }

    private fun recordOf(prefs: Preferences): WatchFeedRecord? {
        val origin = prefs[SettingsKeys.WATCH_FEED_ORIGIN]?.takeIf(WatchLinkContract::isOrigin) ?: return null
        return WatchFeedRecord(
            origin = origin,
            lastSeq = prefs[SettingsKeys.WATCH_FEED_LAST_SEQ] ?: 0,
            lastStandsFor = prefs[SettingsKeys.WATCH_FEED_LAST_STANDS_FOR],
            lastSent = prefs[SettingsKeys.WATCH_FEED_LAST_SENT],
            confirmedSeq = prefs[SettingsKeys.WATCH_FEED_CONFIRMED_SEQ],
            confirmedFor = prefs[SettingsKeys.WATCH_FEED_CONFIRMED_FOR],
        )
    }

    private fun put(prefs: MutablePreferences, record: WatchFeedRecord) {
        prefs[SettingsKeys.WATCH_FEED_ORIGIN] = record.origin
        prefs[SettingsKeys.WATCH_FEED_LAST_SEQ] = record.lastSeq
        prefs.putOrRemove(SettingsKeys.WATCH_FEED_LAST_STANDS_FOR, record.lastStandsFor)
        prefs.putOrRemove(SettingsKeys.WATCH_FEED_LAST_SENT, record.lastSent)
        prefs.putOrRemove(SettingsKeys.WATCH_FEED_CONFIRMED_SEQ, record.confirmedSeq)
        prefs.putOrRemove(SettingsKeys.WATCH_FEED_CONFIRMED_FOR, record.confirmedFor)
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
}

/**
 * A new install's origin: 16 lowercase hex characters (`WatchLinkContract.isOrigin`) of a random
 * UUID, which the standard library draws from the platform's secure generator. Not a secret: a
 * number that no other install of this user's will draw.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun newWatchOrigin(): String = Uuid.random().toHexString().take(16)
