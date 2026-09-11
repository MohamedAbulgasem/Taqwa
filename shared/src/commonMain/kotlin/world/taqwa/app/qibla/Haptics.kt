package world.taqwa.app.qibla

interface Haptics {
    /** One discrete tick. Never a stream — the view model calls this once per alignment entry,
     * not once per frame while already aligned. */
    fun tick()

    /** The lightest pulse the device has, fired once per counted tap on the tasbeeh. It is felt
     * a hundred times in a row, so it has to stay under the threshold of annoyance. */
    fun count()

    /** Two pulses: a part of a multi-part set is done and the dhikr on screen has just changed. */
    fun partComplete()

    /** Three pulses, the last long: the whole set is done. The most distinct of the three, so it
     * is recognised without looking. */
    fun setComplete()
}

expect fun createHaptics(): Haptics
