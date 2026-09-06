package world.taqwa.app.qibla

interface Haptics {
    /** One discrete tick. Never a stream — the view model calls this once per alignment entry,
     * not once per frame while already aligned. */
    fun tick()
}

expect fun createHaptics(): Haptics
