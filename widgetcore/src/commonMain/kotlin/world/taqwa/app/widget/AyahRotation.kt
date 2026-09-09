package world.taqwa.app.widget

/**
 * Deterministic, storage-free daily rotation over a pool of `size` items (design spec §5).
 *
 * Every "round" of `size` consecutive epoch days sees each pool index exactly once (a
 * permutation of `0 until size`), and the boundary between two rounds never repeats an index —
 * so an install never sees the same ayah two days running, and never repeats any ayah until all
 * fifty have shown. Nothing is persisted beyond a per-install `seed`: the order for any day is
 * recomputed from (`epochDay`, `seed`, `size`) alone, so a change to the pool's size (adding or
 * removing ayahs) is handled without migrating stored state.
 *
 * Pure Kotlin stdlib only — no `kotlinx-datetime`, no `java.*` — so this compiles into the iOS
 * WidgetKit extension's tight memory ceiling alongside the Android widget.
 */
object AyahRotation {

    /**
     * Days since 1970-01-01 for a proleptic Gregorian civil date (Howard Hinnant's
     * `days_from_civil`). This is the only notion of "today" the widget module needs; the caller
     * supplies the device's local calendar date, so a day boundary matches the device's midnight
     * without pulling in a date/time library.
     */
    fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    /**
     * A permutation of `0 until size` for one rotation round, seeded from ([round], [seed]).
     * Built with Fisher-Yates over a SplitMix64 generator, then adjusted so its first element
     * never equals the *previous* round's last element — the "never the same ayah two days
     * running" rule across a round boundary. That comparison is against the unadjusted
     * [basePermutation], whose last element the adjustment below never touches for `size > 2`,
     * so the rule stays stable regardless of how many rounds back it is checked.
     */
    fun permutation(round: Long, seed: Long, size: Int): List<Int> {
        val order = basePermutation(round, seed, size)
        if (size > 1 && order[0] == basePermutation(round - 1, seed, size).last()) {
            val t = order[0]; order[0] = order[1]; order[1] = t
        }
        return order
    }

    /**
     * The pool index to show on [epochDay] for an install fixed to [seed], out of a pool of
     * [size] items. Total for any `epochDay` (including negative days, i.e. before 1970) and
     * deterministic: the same (`epochDay`, `seed`, `size`) always yields the same index.
     */
    fun indexFor(epochDay: Long, seed: Long, size: Int): Int {
        require(size > 0)
        val round = epochDay.floorDiv(size.toLong())
        val position = epochDay.mod(size.toLong()).toInt()
        return permutation(round, seed, size)[position]
    }

    private fun basePermutation(round: Long, seed: Long, size: Int): MutableList<Int> {
        val rng = SplitMix64(seed xor (round * GOLDEN))
        val order = MutableList(size) { it }
        for (i in size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        return order
    }

    private const val GOLDEN = -7046029254386353131L // 0x9E3779B97F4A7C15

    /** Minimal SplitMix64 PRNG: fast, seedable, and good enough for a Fisher-Yates shuffle. */
    private class SplitMix64(private var state: Long) {
        fun next(): Long {
            state += GOLDEN
            var z = state
            z = (z xor (z ushr 30)) * -4658895280553007687L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -7723592293110705685L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }

        fun nextInt(bound: Int): Int = ((next() ushr 33) % bound).toInt()
    }
}
