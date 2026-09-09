package world.taqwa.app.widget

/**
 * The curated fifty-ayah pool the ayah widget rotates through (design spec §3).
 *
 * Each entry is a `surah to ayah` reference. The list's order is never shown to the user; it
 * only fixes each ayah's index into [AyahRotation]'s permutation, so this order must never be
 * reshuffled once shipped — appending or removing entries is fine (the rotation copes with a
 * changed size because each round's order is derived, not stored), but reordering existing
 * entries would change which ayah a given (seed, day) resolves to for installs already rotating.
 */
object AyahPool {
    /** (surah, ayah) references, in the spec §3 order. 50 entries. */
    val REFS: List<Pair<Int, Int>> = listOf(
        9 to 51, 65 to 3, 3 to 173, 9 to 129, 39 to 36,
        64 to 11, 11 to 6, 39 to 53, 2 to 186, 40 to 60,
        4 to 110, 21 to 107, 2 to 153, 2 to 156, 94 to 6,
        3 to 139, 29 to 69, 47 to 7, 93 to 5, 13 to 28,
        2 to 152, 14 to 7, 33 to 41, 93 to 11, 30 to 21,
        50 to 16, 36 to 82, 51 to 56, 67 to 2, 99 to 7,
        53 to 39, 18 to 46, 74 to 38, 41 to 34, 49 to 13,
        16 to 90, 25 to 63, 31 to 18, 55 to 60, 112 to 1,
        20 to 14, 59 to 22, 6 to 162, 1 to 5, 2 to 201,
        25 to 74, 3 to 8, 23 to 118, 17 to 82, 16 to 97,
    )
}
