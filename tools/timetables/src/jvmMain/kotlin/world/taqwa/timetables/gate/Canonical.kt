package world.taqwa.timetables.gate

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.math.abs

/**
 * A method (or any registry value) in words that are the same on every machine: what the stamps'
 * fingerprint hashes ([Stamps.fingerprint]), so that the Mac and the Linux runner write the same
 * `engineHash` for the same engine and methods.
 *
 * A data class's `toString` was not that. It prints a double with every digit. The curves a method
 * carries (its Fajr and Isha angles by day, its end-of-eating depressions) are computed with `sin` and
 * `cos`, and HotSpot's own routines for those give different last bits on each CPU, enough to move
 * every hash that holds one: the twelve curve entries the cloud's `prove` rewrote on 3 and 5 October,
 * and the Mac back. The runner's own values were never measured here. Against the JDK's portable
 * routines, a stand-in for another CPU's and not the runner's (at the cloud's 5 October commit the
 * runner, the Mac and the portable routines give three different hashes for each of the twelve),
 * 46,381 of the stamps' values differ, by up to 3.7e-14 of a degree. Here instead:
 *
 * - every double is written as a whole number of millionths ([MILLIONTHS]): a millionth of a degree
 *   for an angle or a point, a millionth of a night for a fraction. That is the finest step any
 *   method value is written to in the registry (Umm al-Qura's declination bias has six decimals).
 *   QMDB's points are kept to 1e-7 of a degree (`QmdbPlaceCodec.SCALE`) and a few Umm al-Qura points
 *   to 1e-8; a millionth drops at most 0.11 m of them, so no literal loses a digit that matters. A
 *   micro-degree moves a twilight time by about 0.4 ms at 50° N; more only on the days near persistent
 *   twilight in the north, where the sun's depression barely changes. The margins are whole seconds
 *   and the times whole minutes;
 * - every array and list by its elements in order, a set by its elements and a map by its keys, each
 *   sorted by its own canonical words;
 * - an enum by its type and name, a string quoted, a record (a data class, a data object, a `Pair`)
 *   by its type and every instance field, sorted by name. A field added to a method is in the
 *   fingerprint the day it is added, with no list here to keep up to date;
 * - anything else (a lambda, a lazy value, a platform object, whose words or identity are the
 *   compiler's or the run's) is refused, loudly, rather than printed.
 *
 * Rounding to the nearest millionth can still flip a computed value, but only where it lies within its
 * last-bit difference of a half-millionth. Of the 77,866 distinct values the fingerprints held on 11
 * October 2026, the closest lay 5.4e-12 of a degree from one: 144 times the widest difference the
 * portable routines gave, and 22 times the widest with every `sin`, `cos`, `tan`, `exp`, `log`,
 * `log10` and `pow` answer moved by up to 16 units in its last place (2.4e-13). Over the whole manifest
 * that is about 2e-4 expected crossings with the portable routines' differences (summed 2.4e-10, over
 * a millionth), and about 2e-3 if every maths answer can differ by up to 2 units in its last place
 * (summed 2.4e-9). With the portable routines, and with the moved answers on four seeds, all 71
 * engine hashes stayed the same (BUILD-LOG, 10–11 October). Neither is the runner's own maths, which
 * was never run here. A literal never crosses, since it parses to the same double everywhere.
 */
object Canonical {

    /** How finely a double is kept: whole millionths (see the class comment). */
    const val MILLIONTHS = 1_000_000.0

    /** [value] in canonical words. */
    fun of(value: Any?): String = StringBuilder().also { write(it, value, 0) }.toString()

    /** A double as whole millionths; NaN and the infinities by name. */
    fun number(x: Double): String = when {
        x.isNaN() -> "NaN"
        x == Double.POSITIVE_INFINITY -> "Inf"
        x == Double.NEGATIVE_INFINITY -> "-Inf"
        else -> {
            require(abs(x) < LARGEST) { "cannot fingerprint $x: beyond ±$LARGEST" }
            Math.round(x * MILLIONTHS).toString()
        }
    }

    /** Well inside a Long once in millionths; no method value comes near it. */
    private const val LARGEST = 1e12

    /** Deeper than any method nests: a value this deep is a cycle. */
    private const val MAX_DEPTH = 24

    private fun write(out: StringBuilder, value: Any?, depth: Int) {
        require(depth < MAX_DEPTH) { "cannot fingerprint a value nested $MAX_DEPTH deep (a cycle?)" }
        when (value) {
            null -> out.append("null")
            is Double -> out.append(number(value))
            is Float -> out.append(number(value.toDouble()))
            is Int, is Long, is Short, is Byte, is Boolean -> out.append(value.toString())
            is Char -> quote(out, value.toString())
            is String -> quote(out, value)
            // A constant with a body is its enum's anonymous subclass: name the enum itself.
            is Enum<*> -> out.append(typeName(value.javaClass.let { if (it.isEnum) it else it.superclass })).append('.').append(value.name)
            is DoubleArray -> elements(out, value.asList(), depth)
            is FloatArray -> elements(out, value.asList(), depth)
            is IntArray -> elements(out, value.asList(), depth)
            is LongArray -> elements(out, value.asList(), depth)
            is ShortArray -> elements(out, value.asList(), depth)
            is ByteArray -> elements(out, value.asList(), depth)
            is BooleanArray -> elements(out, value.asList(), depth)
            is CharArray -> elements(out, value.asList(), depth)
            is Array<*> -> elements(out, value.asList(), depth)
            is List<*> -> elements(out, value, depth)
            is Set<*> -> {
                out.append('{')
                value.map { of(it, depth + 1) }.sorted().joinTo(out, ",")
                out.append('}')
            }
            is Map<*, *> -> {
                out.append('{')
                value.entries.map { (k, v) -> of(k, depth + 1) to of(v, depth + 1) }.sortedBy { it.first }
                    .joinTo(out, ",") { (k, v) -> "$k:$v" }
                out.append('}')
            }
            is kotlinx.datetime.LocalDate -> out.append(value.toString())
            else -> record(out, value, depth)
        }
    }

    private fun of(value: Any?, depth: Int): String = StringBuilder().also { write(it, value, depth) }.toString()

    private fun elements(out: StringBuilder, values: List<*>, depth: Int) {
        out.append('[')
        values.forEachIndexed { i, v ->
            if (i > 0) out.append(',')
            write(out, v, depth + 1)
        }
        out.append(']')
    }

    private fun quote(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            if (c == '"' || c == '\\') out.append('\\')
            out.append(c)
        }
        out.append('"')
    }

    /** A record: its type, then every instance field of it and its superclasses, sorted by name. */
    private fun record(out: StringBuilder, value: Any, depth: Int) {
        val type = value.javaClass
        require(isRecord(type, value)) {
            "cannot fingerprint a ${type.name}: not a value the stamps know how to write the same on every machine " +
                "(a lambda, a lazy value or a platform object); make it a data class of plain values, or teach Canonical it"
        }
        out.append(typeName(type)).append('(')
        fieldsOf(type).forEachIndexed { i, field ->
            if (i > 0) out.append(',')
            out.append(field.name).append('=')
            write(out, field.get(value), depth + 1)
        }
        out.append(')')
    }

    /**
     * Our own classes (the engine's and the registry's, plain named ones) and the standard library's
     * pairs. Never a lambda, an anonymous object or a hidden class, whose names and captured fields are
     * the compiler's, nor a Kotlin function or lazy value.
     */
    private fun isRecord(type: Class<*>, value: Any): Boolean =
        (type.name.startsWith("world.taqwa.") || type == Pair::class.java || type == Triple::class.java) &&
            !type.isAnonymousClass && !type.isSynthetic && !type.isHidden && !type.isLocalClass &&
            !type.name.contains("\$\$") && value !is Function<*> && value !is Lazy<*>

    private val fieldCache = java.util.concurrent.ConcurrentHashMap<Class<*>, List<Field>>()

    private fun fieldsOf(type: Class<*>): List<Field> = fieldCache.getOrPut(type) {
        generateSequence(type) { it.superclass }.takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .sortedWith(compareBy({ it.name }, { it.declaringClass.name }))
            .onEach { require(it.trySetAccessible()) { "cannot read ${it.declaringClass.name}.${it.name} to fingerprint it" } }
            .toList()
    }

    /** The type's binary name without its package: `EndOfEating$DawnAngle`, `Pair`. */
    private fun typeName(type: Class<*>): String = type.name.substringAfterLast('.')
}
