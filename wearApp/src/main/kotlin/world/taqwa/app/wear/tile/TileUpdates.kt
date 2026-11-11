package world.taqwa.app.wear.tile

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService

/**
 * Asks the system to request the Tile again: when a feed is stored, when a newer format is seen
 * (so the update is on the Tile at once), when the app opens and something the Tile shows may have
 * changed (`SurfaceRefresh`), and when the debug build clears the store. Nothing happens while no
 * Tile is added. A request that cannot be made is logged and goes no further: the Tile is asked
 * for again when it stops being fresh. True when the request was made.
 */
object TileUpdates {
    fun request(context: Context): Boolean =
        try {
            TileService.getUpdater(context).requestUpdate(TodayTileService::class.java)
            true
        } catch (e: RuntimeException) {
            Log.w("TaqwaTile", "could not ask for the Tile to be requested again", e)
            false
        }
}
