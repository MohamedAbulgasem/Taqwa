package world.taqwa.timetables

/**
 * The few lines of JSON writing the generator needs, so it carries no serialisation dependency.
 * Maps, lists, strings, numbers, booleans and null; everything else is a bug. "</" is written
 * "<\/" so the output can be embedded in an HTML `<script>` element as it is. [pretty] puts each
 * member on its own line, two spaces deep, for files people read in diffs (the gate's stamps).
 */
object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value, null) }.toString()

    fun pretty(value: Any?): String = StringBuilder().also { append(it, value, 0) }.append('\n').toString()

    /**
     * The reader half, for the stamps [write] and [pretty] produce (Task 11's proof-stamp table
     * generator; nothing else in this module reads JSON back yet). Objects become
     * `LinkedHashMap<String, Any?>` in the order written, arrays `List<Any?>`, numbers `Long` when
     * they carry no `.` or exponent, else `Double`. Whitespace-tolerant; not a general-purpose
     * parser (no comments, no trailing commas) since it only ever reads this object's own output.
     */
    fun parse(text: String): Any? = Reader(text).let { r -> r.skipWs(); val v = r.readValue(); r.skipWs(); require(r.atEnd()) { "trailing content at ${r.pos}" }; v }

    private class Reader(val text: String) {
        var pos = 0
        fun atEnd() = pos >= text.length
        fun peek(): Char = text[pos]
        fun skipWs() { while (pos < text.length && text[pos].isWhitespace()) pos++ }
        fun expect(c: Char) { require(!atEnd() && text[pos] == c) { "expected '$c' at $pos" }; pos++ }

        fun readValue(): Any? {
            skipWs()
            return when {
                atEnd() -> error("unexpected end of input")
                peek() == '{' -> readObject()
                peek() == '[' -> readArray()
                peek() == '"' -> readString()
                text.startsWith("true", pos) -> true.also { pos += 4 }
                text.startsWith("false", pos) -> false.also { pos += 5 }
                text.startsWith("null", pos) -> null.also { pos += 4 }
                else -> readNumber()
            }
        }

        fun readObject(): LinkedHashMap<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (!atEnd() && peek() == '}') { pos++; return map }
            while (true) {
                skipWs()
                val key = readString()
                skipWs()
                expect(':')
                map[key] = readValue()
                skipWs()
                when {
                    !atEnd() && peek() == ',' -> { pos++; continue }
                    !atEnd() && peek() == '}' -> { pos++; break }
                    else -> error("expected ',' or '}' at $pos")
                }
            }
            return map
        }

        fun readArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWs()
            if (!atEnd() && peek() == ']') { pos++; return list }
            while (true) {
                list += readValue()
                skipWs()
                when {
                    !atEnd() && peek() == ',' -> { pos++; continue }
                    !atEnd() && peek() == ']' -> { pos++; break }
                    else -> error("expected ',' or ']' at $pos")
                }
            }
            return list
        }

        fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(!atEnd()) { "unterminated string at $pos" }
                val c = text[pos++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        require(!atEnd()) { "unterminated escape at $pos" }
                        when (val e = text[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'u' -> { out.append(text.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                            else -> error("unknown escape '\\$e' at $pos")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        fun readNumber(): Any {
            val start = pos
            if (!atEnd() && (peek() == '-' || peek() == '+')) pos++
            var isDouble = false
            while (!atEnd() && (peek().isDigit() || peek() == '.' || peek() == 'e' || peek() == 'E' || peek() == '-' || peek() == '+')) {
                if (peek() == '.' || peek() == 'e' || peek() == 'E') isDouble = true
                pos++
            }
            val slice = text.substring(start, pos)
            require(slice.isNotEmpty()) { "expected a number at $start" }
            return if (isDouble) slice.toDouble() else slice.toLong()
        }
    }

    /** [depth] is null for one line, else how deep [value] sits. */
    private fun append(out: StringBuilder, value: Any?, depth: Int?) {
        fun newline(level: Int) {
            if (depth != null) out.append('\n').append("  ".repeat(level))
        }
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
                    newline((depth ?: 0) + 1)
                    string(out, key as String)
                    out.append(if (depth != null) ": " else ":")
                    append(out, v, depth?.plus(1))
                }
                if (value.isNotEmpty()) newline(depth ?: 0)
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(if (depth != null) ", " else ",")
                    append(out, v, depth?.plus(1))
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
