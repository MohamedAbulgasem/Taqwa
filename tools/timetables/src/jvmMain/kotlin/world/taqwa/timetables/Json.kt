package world.taqwa.timetables

/**
 * The few lines of JSON writing the generator needs, so it carries no serialisation dependency.
 * Maps, lists, strings, numbers, booleans and null; everything else is a bug. "</" is written
 * "<\/" so the output can be embedded in an HTML `<script>` element as it is.
 */
object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> string(out, value)
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value)
            is Double -> out.append(if (value % 1.0 == 0.0) value.toLong().toString() else value.toString())
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (key, v) ->
                    if (i > 0) out.append(',')
                    string(out, key as String)
                    out.append(':')
                    append(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    append(out, v)
                }
                out.append(']')
            }
            else -> error("Cannot write ${value::class.simpleName} as JSON")
        }
    }

    private fun string(out: StringBuilder, text: String) {
        out.append('"')
        var previous = ' '
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c == '/' && previous == '<' -> out.append("\\/")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
            previous = c
        }
        out.append('"')
    }
}
