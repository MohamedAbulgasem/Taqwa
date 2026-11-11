package world.taqwa.app.watchfeed

/**
 * An iPhone has no Wear watch: its one watch row is plan 4's "Add to Apple Watch", so Settings ›
 * Watch is that row alone. A tap that came anyway reaches nothing, said as such.
 */
internal actual fun watchTargetsSource(): WatchTargetsSource = object : WatchTargetsSource {
    override suspend fun targets(): WatchTargets = WatchTargets.NONE

    override suspend fun open(target: WatchTarget, action: WatchAction): Boolean = false
}
