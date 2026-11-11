package world.taqwa.app.wear

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.wear.complication.ComplicationSources
import world.taqwa.app.wear.face.FACE_BOUND_MS
import world.taqwa.app.wear.face.FaceCapability
import world.taqwa.app.wear.face.FacePushes
import world.taqwa.app.wear.face.refreshAfterUpdate
import world.taqwa.app.wear.link.AskWhy
import world.taqwa.app.wear.link.DailyNudge
import world.taqwa.app.wear.link.PhoneAsk
import world.taqwa.app.wear.tile.TileUpdates

/**
 * The Taqwa watch app's process. It holds the one [FeedStore] that everything in the app reads the
 * feed through: the screens, and the complication sources, the Tile and the face's data later on,
 * all of which run in this process, so that they see the same feed and the same changes.
 *
 * At every start it arms the daily nudge ([DailyNudge], Ruling 22's safety net, which carries the
 * daily handshake), and shakes hands with the phone when the watch holds nothing from it
 * ([AskWhy.STARTED]: a reinstalled app, a new watch, a reset, which the phone cannot know of).
 *
 * Open because the debug build starts from a subclass (`src/debug`, DebugWearApp) that gives the
 * store a sample feed; the release build runs this class itself.
 */
open class TaqwaWearApp : Application() {

    /**
     * The feed the phone last sent, kept in the app's own files. It is made when it is first
     * wanted, which is at every process start now: the start's handshake ([AskWhy.STARTED]) reads
     * what the watch holds before it asks.
     * Every feed that is stored asks the complication sources and the Tile to be requested again,
     * because what they have served was drawn ahead from the feed before it, and stamps that ask
     * ([askSurfaces]), so the next opening need not make it again; a feed in a newer format asks
     * for the Tile, so that it says "Update Taqwa on your watch" at once.
     */
    val feedStore: FeedStore by lazy {
        FeedStore(File(filesDir, FEED_FILE_NAME), onNewerVersion = { TileUpdates.request(this) }) {
            askSurfaces(why = "feed", always = true)
        }
    }

    /** The app's last request of the complications and the Tile, and what it was for ([SurfaceRefresh]). */
    private val surfaces: SurfaceMemory by lazy { SurfaceMemory(getSharedPreferences(SurfaceMemory.FILE, Context.MODE_PRIVATE)) }

    /**
     * The build installed, and when ([SurfaceInputs.versionCode], [SurfaceInputs.installedAt]),
     * read once: neither changes while the process lives, since an update ends it. Zero for both
     * when the system cannot say, which only makes the next opening ask.
     */
    private val installed: Pair<Long, Long> by lazy {
        try {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, 0)
            info.longVersionCode to info.lastUpdateTime
        } catch (_: PackageManager.NameNotFoundException) {
            0L to 0L
        }
    }

    /**
     * The process's own scope for the link's work off the main thread: the handshakes and the
     * nudges' broadcasts. It lives as long as the process; a process that ends first loses only
     * the ask it was making, which the next chance makes again.
     */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Off the main thread: arming reads the nudge's anchor from the app's files.
        scope.launch {
            try {
                DailyNudge.arm(this@TaqwaWearApp)
            } catch (e: Exception) {
                // The next process start arms it; the app itself must start whatever the alarm says.
                Log.w(TAG, "the daily nudge could not be armed", e)
            }
        }
        askPhone(AskWhy.STARTED)
        refreshFace()
    }

    /**
     * Plan 5 Task 5: at every start, the face in Taqwa's slot is updated to the one this build
     * carries, and the phone told whether there is a face to add ([refreshAfterUpdate]). An update
     * of the app starts the process, so this runs beside FaceUpdateReceiver; the process's one
     * FacePush makes the second a no-op. Off the main thread, bounded, and it never throws.
     */
    private fun refreshFace() {
        scope.launch {
            try {
                withTimeoutOrNull(FACE_BOUND_MS) {
                    refreshAfterUpdate(FacePushes.of(this@TaqwaWearApp)) { state, spent -> FaceCapability.publish(this@TaqwaWearApp, state, spent) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "the face could not be refreshed", e)
            }
        }
    }

    /**
     * Each time the watch app's screens come to the front (`WearMainActivity.onResume`): Ruling 22's
     * app-start net, the complications and the Tile asked to be requested again when something they
     * show may have changed since the app last asked ([SurfaceRefresh.due], the owner's decision of
     * 9 November), then a handshake when the feed needs one (none, three days left, a week old, or
     * spent: spec 2.2 and 2.6). Plan 2 Task 6 put the first and last in the status screen's
     * `onResume`; they moved here with the screens (plan 3 Task 4), and plan 3 Task 8 added the
     * Tile's. Both run on [scope], off the main thread, and never throw.
     */
    fun onAppOpened() {
        scope.launch {
            try {
                askSurfaces(why = "app start", always = false)
            } catch (e: Exception) {
                // The next opening, feed, Tile or daily nudge asks again.
                Log.w(TAG, "the complications and the Tile could not be asked again", e)
            }
        }
        askPhone(AskWhy.OPENED)
    }

    /**
     * Asks the complications and the Tile to be requested again, for [why] (the log's word), when
     * [always] or when [SurfaceRefresh.due] says so, and stamps the ask once both went out
     * ([SurfaceMemory]). When what they draw from cannot be read, it asks unstamped, as every
     * opening asked before the stamp.
     */
    private fun askSurfaces(why: String, always: Boolean) {
        val now = System.currentTimeMillis() / 1000
        val ask = {
            val complications = ComplicationSources.requestUpdates(this, why)
            TileUpdates.request(this) && complications
        }
        val inputs = try {
            surfaceInputs(now)
        } catch (e: RuntimeException) {
            Log.w(TAG, "what the complications and the Tile draw from could not be read", e)
            null
        }
        when {
            inputs == null -> ask()
            always -> surfaces.askAnyway(inputs, now, ask)
            else -> surfaces.askIfDue(inputs, now, ask)
        }
    }

    /** What the complications and the Tile draw from at [now] (epoch seconds), apart from the clock. */
    private fun surfaceInputs(now: Long): SurfaceInputs {
        val zone = ZoneId.systemDefault()
        val (versionCode, installedAt) = installed
        return SurfaceInputs(
            feed = feedStore.changes.value?.writtenAt,
            newerVersion = feedStore.newerVersionSeen.value,
            day = Instant.ofEpochSecond(now).atZone(zone).toLocalDate().toEpochDay(),
            zone = zone.id,
            locales = resources.configuration.locales.toLanguageTags(),
            twentyFourHour = DateFormat.is24HourFormat(this),
            versionCode = versionCode,
            installedAt = installedAt,
        )
    }

    /**
     * Shakes hands with the phone for [why] when [PhoneAsk] finds it due, on [scope] inside
     * [PhoneAsk.BOUND]: it never blocks its caller, and it never throws.
     */
    internal fun askPhone(why: AskWhy) {
        scope.launch {
            try {
                withTimeoutOrNull(PhoneAsk.BOUND) { PhoneAsk(this@TaqwaWearApp, feedStore).askIfDue(why, System.currentTimeMillis() / 1000) }
            } catch (_: Exception) {
                // Lost; the next start, opening, reconnect or daily nudge asks again.
            }
        }
    }

    private companion object {
        const val FEED_FILE_NAME = "feed.json"
        const val TAG = "TaqwaWear"
    }
}
