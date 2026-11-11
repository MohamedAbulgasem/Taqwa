package world.taqwa.app.wear.tile

/**
 * How often something may be done again: Ruling 22's safety net from the Tile, which asks the
 * complications to be requested again each time the Tile is requested, at most once in
 * [TodayTileService]'s six hours (the stamp `complications.asked` in the app's files).
 */
internal object Throttle {

    /**
     * Whether it is due at [now] (epoch seconds), last done at [lastAt] (null for never): [gapSeconds]
     * or more since then. A stamp ahead of [now] is due too: the watch's clock was set back, and
     * the stamp would otherwise hold the net shut for as long as the clock was moved.
     */
    fun due(lastAt: Long?, now: Long, gapSeconds: Long): Boolean =
        lastAt == null || lastAt > now || now - lastAt >= gapSeconds
}
