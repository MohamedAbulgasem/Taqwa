package world.taqwa.app.wear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import world.taqwa.app.wear.complication.ComplicationSources
import world.taqwa.app.wear.tile.TileUpdates

/**
 * Debug builds only. Replaces the stored feed with [SampleFeed], moved to the date of the `epoch`
 * extra (epoch seconds; the date is Johannesburg's) or to today when there is none, so that the
 * watch can be shown at a moment of the developer's choosing, 14:58 for a screenshot:
 *
 *     adb -s emulator-5558 shell am broadcast -a world.taqwa.app.wear.DEBUG_FEED --el epoch 1793883480
 *
 * That reaches the receiver `DebugWearApp` registers, so the app's process has to be alive: start
 * the app first. To send it to an app that is not running, name the receiver declared in the
 * manifest, which starts the process:
 *
 *     adb -s emulator-5558 shell am broadcast -n world.taqwa.app/world.taqwa.app.wear.DebugFeedReceiver \
 *         -a world.taqwa.app.wear.DEBUG_FEED --el epoch 1793883480
 *
 * Either way the broadcast ends with a line saying what was stored, which `am` prints. Moving the
 * feed does not move the watch's clock: a screenshot at 14:58 needs the device's clock at 14:58
 * on the date of the epoch as well.
 *
 * A `sample` extra names another sample (`SampleFeed.NAMES`): `--es sample ar-ly12` stores the
 * same days as a Libyan phone on a twelve-hour clock writes them, `ar-sa` a Saudi phone's, and
 * `en12` an English one on twelve hours.
 *
 * Three more extras show the watch app's states without a usable feed (plan 3 Task 4):
 *
 * - `--ez clear true` forgets everything the store holds (`FeedStore.clear`), holds the sample so
 *   that the process's next start does not put it back (`SampleFeed.hold`), and asks the
 *   complications and the Tile to draw again. Its line is `CLEARED`. Every page and the Tile then
 *   say "Open Taqwa on your phone", until a feed is stored again: a plain `DEBUG_FEED` does it,
 *   and lifts the hold.
 * - `--ei version 2` offers the sample in that format. The store turns it away as `NEWER_VERSION`,
 *   so the watch says "Update Taqwa on your watch", and whatever was stored (a feed, or none) stays.
 * - `--es city <name>` stores the sample with that city, for the longest city's shots.
 *
 * A second action, `world.taqwa.app.wear.DEBUG_REBIND`, stores nothing: it asks the complications and
 * the Tile to draw again from what the store holds, so that the face binds its slots again after a
 * tool has rewritten files/feed.json by hand (tools/watchface/lib.sh rebind_face). Since plan 5
 * Task 4 the face reaches the watch only inside this app, through Watch Face Push, so the tools no
 * longer install the face again to make it ask. Its line is `REBOUND`.
 */
class DebugFeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REBIND) {
            val app = context.applicationContext as TaqwaWearApp
            ComplicationSources.requestUpdates(app, why = "debug")
            TileUpdates.request(app)
            Log.i(TAG, "The complications and the Tile are asked to draw again")
            if (isOrderedBroadcast) setResult(Activity.RESULT_OK, "REBOUND", null)
            return
        }
        if (intent.action != ACTION) return
        if (intent.getBooleanExtra(EXTRA_CLEAR, false)) {
            val app = context.applicationContext as TaqwaWearApp
            app.feedStore.clear()
            SampleFeed.hold(app.filesDir)
            ComplicationSources.requestUpdates(app, why = "debug")
            TileUpdates.request(app)
            Log.i(TAG, "The store is cleared, and the sample held")
            if (isOrderedBroadcast) setResult(Activity.RESULT_OK, "CLEARED", null)
            return
        }
        val epoch = if (intent.hasExtra(EXTRA_EPOCH)) {
            @Suppress("DEPRECATION") // The extra may have been sent as any type, so the typed getters will not do.
            epochOf(intent.extras?.get(EXTRA_EPOCH))
        } else {
            System.currentTimeMillis() / 1000
        }
        if (epoch == null) {
            Log.e(TAG, "$EXTRA_EPOCH must be a whole number of epoch seconds")
            if (isOrderedBroadcast) setResult(Activity.RESULT_CANCELED, "$EXTRA_EPOCH is not epoch seconds", null)
            return
        }

        val name = intent.getStringExtra(EXTRA_SAMPLE).orEmpty()
        if (SampleFeed.assetOf(name) == null) {
            Log.e(TAG, "there is no sample called '$name'")
            if (isOrderedBroadcast) setResult(Activity.RESULT_CANCELED, "no sample '$name': ${SampleFeed.NAMES.joinToString()}", null)
            return
        }

        val version = if (intent.hasExtra(EXTRA_VERSION)) intent.getIntExtra(EXTRA_VERSION, 0) else null
        val city = intent.getStringExtra(EXTRA_CITY)
        val store = (context.applicationContext as TaqwaWearApp).feedStore
        val result = SampleFeed.install(context, store, epoch, name, version, city)
        // The city is not logged: in a debug build it is the developer's own, but no line names a place.
        Log.i(TAG, "The sample feed '$name' for the date of $epoch${version?.let { ", version $it" }.orEmpty()}: $result")
        if (isOrderedBroadcast) {
            val details = listOfNotNull(
                name.takeIf { it.isNotEmpty() }?.let { "sample $it" },
                version?.let { "version $it" },
                city?.let { "a city of ${it.length} characters" },
            )
            setResult(Activity.RESULT_OK, "$result for epoch $epoch${details.joinToString("") { ", $it" }}", null)
        }
    }

    companion object {
        const val ACTION = "world.taqwa.app.wear.DEBUG_FEED"
        const val ACTION_REBIND = "world.taqwa.app.wear.DEBUG_REBIND"
        const val EXTRA_EPOCH = "epoch"
        const val EXTRA_SAMPLE = "sample"
        const val EXTRA_CLEAR = "clear"
        const val EXTRA_VERSION = "version"
        const val EXTRA_CITY = "city"
        private const val TAG = "TaqwaDebugFeed"
    }
}

/**
 * The epoch seconds in an extra, whichever way the shell sent them (`--el`, `--ei`, `--es`), or
 * null when it is none of those or is no whole number.
 */
internal fun epochOf(extra: Any?): Long? = when (extra) {
    is Long -> extra
    is Int -> extra.toLong()
    is String -> extra.toLongOrNull()
    else -> null
}
