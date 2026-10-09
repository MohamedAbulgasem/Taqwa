package world.taqwa.app.prayer.engine.registry.data

/**
 * The packing of [QmdbPlaceList]: fifteen characters a place, in base 64 (`0-9A-Za-z-_`, most significant digit
 * first): QMDB's id (3), the latitude and the longitude in 1e-7 degrees from [LAT_BASE] and [LON_BASE] (5 each), the
 * unit's reach in 0.1 km (2). A coordinate QMDB writes with up to seven decimals is kept exactly (an integer number
 * of 1e-7 degrees, divided once, is the same double as the decimal text). The text stays under the 64 KB a JVM
 * string constant may hold by being cut into parts, which the generated file joins at run time.
 */
object QmdbPlaceCodec {
    const val WIDTH = 15
    const val LAT_BASE = 40.0
    const val LON_BASE = 46.0
    const val SCALE = 10_000_000L
    const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"

    private fun value(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'Z' -> c - 'A' + 10
        in 'a'..'z' -> c - 'a' + 36
        '-' -> 62
        '_' -> 63
        else -> error("not a base-64 digit: $c")
    }

    private fun number(s: String, from: Int, width: Int): Long {
        var n = 0L
        for (i in from until from + width) n = n * 64 + value(s[i])
        return n
    }

    /** The places [packed] holds, in its order. */
    fun decode(packed: String): List<QmdbPlaceList.Place> {
        require(packed.length % WIDTH == 0) { "QMDB places: ${packed.length} characters is not a whole number of places" }
        return List(packed.length / WIDTH) { k ->
            val at = k * WIDTH
            QmdbPlaceList.Place(
                qmdbId = number(packed, at, 3).toInt(),
                lat = (LAT_BASE * SCALE + number(packed, at + 3, 5)) / SCALE.toDouble(),
                lon = (LON_BASE * SCALE + number(packed, at + 8, 5)) / SCALE.toDouble(),
                reachKm = number(packed, at + 13, 2) / 10.0,
            )
        }
    }

    /** [n] as [width] base-64 digits. */
    fun digits(n: Long, width: Int): String {
        require(n >= 0) { "a negative number does not pack: $n" }
        val out = CharArray(width)
        var v = n
        for (i in width - 1 downTo 0) {
            out[i] = DIGITS[(v % 64).toInt()]
            v /= 64
        }
        require(v == 0L) { "$n needs more than $width base-64 digits" }
        return out.concatToString()
    }
}
