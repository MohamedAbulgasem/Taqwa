package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AyahPool.REFS] as it ships in 1.7.0, copied here by value. An entry's index is what the rotation
 * resolves a (seed, day) to (spec §5), so once a list has shipped no entry may move or go: this
 * fails if any of the 365 below does, and lets entries be appended after them.
 *
 * Copied from the source on 20 November 2026 (the hundred of 1.6.0, then the 265 with Mohamed's
 * swap of 46:13 for 41:30). Change it only to append: a new entry goes after the last one here,
 * never between.
 */
class AyahPoolOrderTest {

    @Test
    fun theListAsShippedIn170IsUnchangedAndFirst() {
        assertTrue(
            AyahPool.REFS.size >= SHIPPED_IN_1_7_0.size,
            "AyahPool.REFS has ${AyahPool.REFS.size} entries, fewer than the ${SHIPPED_IN_1_7_0.size} that shipped",
        )
        SHIPPED_IN_1_7_0.forEachIndexed { index, ref ->
            assertEquals(ref, AyahPool.REFS[index], "entry $index moved or went: it shipped as ${ref.first}:${ref.second}")
        }
    }

    @Test
    fun theShippedListIsTheWholeYear() {
        assertEquals(365, SHIPPED_IN_1_7_0.size)
        assertEquals(SHIPPED_IN_1_7_0.size, SHIPPED_IN_1_7_0.toSet().size)
    }

    private companion object {
        val SHIPPED_IN_1_7_0: List<Pair<Int, Int>> = listOf(
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
            3 to 102, 3 to 133, 3 to 160, 3 to 200, 7 to 55,
            7 to 56, 7 to 180, 7 to 199, 8 to 46, 9 to 105,
            10 to 25, 10 to 58, 10 to 62, 11 to 114, 11 to 115,
            15 to 9, 15 to 49, 15 to 99, 16 to 96, 17 to 70,
            17 to 80, 17 to 81, 19 to 96, 20 to 114, 21 to 35,
            22 to 77, 23 to 115, 27 to 62, 27 to 79, 28 to 56,
            28 to 88, 30 to 60, 31 to 17, 33 to 21, 33 to 56,
            33 to 70, 35 to 15, 39 to 62, 40 to 65, 41 to 33,
            42 to 19, 42 to 30, 49 to 10, 51 to 55, 52 to 48,
            55 to 29, 59 to 18, 59 to 19, 62 to 10, 64 to 13,
            1 to 6, 2 to 28, 2 to 32, 2 to 42, 2 to 107,
            2 to 110, 2 to 115, 2 to 147, 2 to 155, 2 to 172,
            2 to 208, 2 to 212, 2 to 238, 2 to 245, 2 to 263,
            2 to 268, 2 to 269, 2 to 281, 3 to 6, 3 to 18,
            3 to 27, 3 to 92, 3 to 104, 3 to 134, 3 to 190,
            3 to 193, 3 to 194, 4 to 27, 4 to 28, 4 to 31,
            4 to 40, 4 to 48, 4 to 69, 4 to 85, 4 to 87,
            4 to 134, 4 to 174, 5 to 87, 5 to 100, 6 to 15,
            6 to 38, 6 to 60, 6 to 82, 6 to 95, 6 to 103,
            6 to 120, 6 to 160, 7 to 8, 7 to 10, 7 to 23,
            7 to 26, 7 to 31, 7 to 96, 7 to 128, 7 to 196,
            7 to 200, 7 to 201, 7 to 204, 7 to 205, 8 to 21,
            8 to 24, 8 to 27, 8 to 29, 9 to 119, 10 to 9,
            10 to 26, 10 to 44, 11 to 90, 11 to 105, 11 to 112,
            11 to 113, 11 to 123, 12 to 53, 12 to 86, 12 to 108,
            13 to 15, 13 to 24, 13 to 26, 13 to 29, 14 to 19,
            14 to 24, 14 to 27, 14 to 31, 14 to 34, 14 to 38,
            14 to 40, 14 to 41, 14 to 42, 15 to 21, 15 to 23,
            15 to 56, 15 to 85, 16 to 51, 16 to 53, 16 to 70,
            16 to 77, 16 to 78, 16 to 128, 17 to 9, 17 to 11,
            17 to 14, 17 to 19, 17 to 24, 17 to 25, 17 to 29,
            17 to 36, 17 to 44, 17 to 53, 17 to 66, 17 to 85,
            17 to 111, 18 to 27, 19 to 65, 19 to 93, 20 to 15,
            20 to 25, 20 to 55, 20 to 82, 20 to 111, 20 to 112,
            20 to 124, 20 to 131, 20 to 132, 21 to 1, 21 to 22,
            21 to 23, 22 to 1, 22 to 37, 22 to 38, 22 to 46,
            23 to 17, 23 to 29, 23 to 62, 23 to 97, 23 to 101,
            24 to 51, 24 to 56, 25 to 27, 25 to 58, 25 to 62,
            26 to 62, 26 to 80, 26 to 85, 26 to 90, 27 to 73,
            27 to 89, 27 to 93, 28 to 16, 28 to 73, 28 to 83,
            29 to 41, 29 to 45, 29 to 64, 30 to 17, 30 to 22,
            30 to 38, 30 to 41, 30 to 50, 31 to 12, 31 to 14,
            31 to 22, 32 to 12, 32 to 15, 33 to 43, 33 to 47,
            34 to 1, 34 to 2, 34 to 39, 34 to 50, 35 to 2,
            35 to 3, 35 to 5, 35 to 6, 35 to 29, 35 to 41,
            36 to 12, 36 to 22, 36 to 36, 36 to 83, 37 to 180,
            38 to 29, 39 to 10, 39 to 18, 39 to 20, 39 to 29,
            39 to 46, 39 to 54, 39 to 67, 39 to 69, 40 to 13,
            40 to 16, 40 to 19, 40 to 51, 41 to 46, 41 to 53,
            42 to 5, 42 to 11, 42 to 20, 42 to 25, 42 to 27,
            42 to 28, 42 to 43, 43 to 36, 43 to 67, 45 to 13,
            45 to 37, 46 to 13, 47 to 2, 47 to 17, 47 to 19,
            47 to 24, 47 to 33, 48 to 4, 49 to 6, 50 to 18,
            50 to 19, 51 to 22, 51 to 50, 52 to 21, 54 to 17,
            55 to 13, 55 to 46, 55 to 78, 57 to 3, 57 to 7,
            57 to 28, 58 to 9, 58 to 21, 59 to 21, 59 to 23,
            59 to 24, 61 to 2, 62 to 8, 63 to 9, 63 to 10,
            64 to 15, 64 to 16, 67 to 1, 67 to 12, 67 to 14,
            67 to 15, 67 to 19, 67 to 30, 70 to 5, 72 to 20,
            73 to 8, 73 to 19, 76 to 1, 76 to 9, 76 to 26,
            76 to 30, 78 to 40, 82 to 6, 84 to 6, 86 to 4,
            87 to 1, 87 to 14, 93 to 3, 93 to 4, 93 to 10,
            94 to 8, 95 to 8, 97 to 3, 98 to 8, 110 to 3,
        )
    }
}
