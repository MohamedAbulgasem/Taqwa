package world.taqwa.app.wear.tile

import android.util.Log
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlinx.coroutines.Dispatchers
import world.taqwa.app.wear.TaqwaWearApp
import world.taqwa.app.wear.complication.ComplicationSources
import world.taqwa.app.wear.complication.orOnFailure
import world.taqwa.app.wear.link.readStamp
import world.taqwa.app.wear.link.writeStamp

/**
 * The Tile (spec 2.4): the next start counted down, the day's six times (five where the phone hides
 * Sunrise) with the widgets' lit row, and the city, drawn from the feed alone (`TilePlan`,
 * `TileLayout`), in the phone's language and direction. The countdown moves each minute with the
 * app's process dead (`TileCountdown`); the timeline holds ten prayer periods and ends in the
 * feed's "Open Taqwa on your phone". With no feed it says the watch's own line, as the pages do. A
 * tap opens the app on Today.
 *
 * Each request is also Ruling 22's safety net from the Tile: the complications are asked to be
 * requested again, at most once in six hours ([Throttle], `complications.asked`).
 *
 * The system asks for the Tile when it is added, when it has stopped being fresh
 * (`TilePlan.freshnessMillis`) and whenever the app asks (`TileUpdates`: a feed stored, a newer
 * format seen, the app opened when the Tile may show something new).
 */
class TodayTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
            val now = System.currentTimeMillis() / 1000
            askComplicationsIfDue(now)
            tile(requestParams, now)
        }

    /** The Tile draws no image, so its resources are none, at the version the Tile names. */
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    private fun tile(request: RequestBuilders.TileRequest, now: Long): TileBuilders.Tile {
        val store = (application as TaqwaWearApp).feedStore
        val feed = store.changes.value
        val newer = store.newerVersionSeen.value
        val screen = TileScreen.of(request.deviceConfiguration)
        val noFeed = { TimelineBuilders.Timeline.fromLayoutElement(TileLayout.noFeed(getString(TilePlan.emptyLine(newer)), screen, packageName)) }
        var entries = emptyList<TileEntry>()
        var bytes = 0
        val timeline = orOnFailure(
            compute = {
                val sent = feed?.let { TileLayout.fitted(it, newer, now, screen, HOST_RTL, packageName, PaintMeasure(screen.pixelsPerUnit)) }
                entries = sent?.entries.orEmpty()
                bytes = sent?.bytes ?: 0
                if (sent == null || entries.isEmpty()) noFeed() else sent.timeline
            },
            // A feed the rules were never shown could throw anywhere in the plan: the Tile then
            // says the watch's own line until the next feed, rather than end the process.
            fallback = { entries = emptyList(); noFeed() },
        ) { e -> Log.w(TAG, "the Tile could not be drawn from the feed, so it says the watch's own line", e) }
        val schema = request.deviceConfiguration.rendererSchemaVersion
        Log.d(TAG, "Tile: ${entries.size} entries, ${entries.count { it.moves }} counting down, $bytes bytes, schema ${schema.major}.${schema.minor}")
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(TilePlan.freshnessMillis(entries, now))
            .build()
    }

    /**
     * Ruling 22's safety net from the Tile: asks for every complication to be requested again,
     * unless that was asked less than six hours ago. The stamp is best effort: one that cannot be
     * written only means the next request asks again.
     */
    private fun askComplicationsIfDue(now: Long) {
        val asked = File(filesDir, ASKED_FILE)
        if (!Throttle.due(readStamp(asked), now, ASK_GAP_SECONDS)) return
        ComplicationSources.requestUpdates(this, why = "tile")
        writeStamp(asked, now)
    }

    private companion object {
        const val TAG = "TaqwaTile"

        /**
         * The renderer's own direction, which `inTileOrder` turns a Row against: none. On the Wear OS
         * 6 emulator with the watch's language set to Arabic, both an Arabic and an English feed came
         * out reversed when the service read its configuration's direction (P3.8 Step 5): the
         * renderer lays a Row out left to right whatever the watch's language, so what is seen is
         * the feed's direction alone. Plan 5's device pass checks a Galaxy in Arabic.
         */
        const val HOST_RTL = false
        const val RESOURCES_VERSION = "1"
        const val ASKED_FILE = "complications.asked"
        const val ASK_GAP_SECONDS = 21_600L
    }
}
