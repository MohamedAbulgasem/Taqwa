package world.taqwa.timetables

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {

    @Test
    fun valuesAreWrittenAsJson() {
        assertEquals(
            """{"a":1,"b":[true,false,null],"c":"x","d":2.5}""",
            Json.write(linkedMapOf("a" to 1, "b" to listOf(true, false, null), "c" to "x", "d" to 2.5)),
        )
    }

    @Test
    fun stringsAreEscaped() {
        assertEquals("\"q\\\"b\\\\n\\nt\\t\\u0001\"", Json.write("q\"b\\n\nt\t\u0001"))
    }

    @Test
    fun nonLatinTextIsWrittenAsIs() {
        assertEquals("\"طرابلس\"", Json.write("طرابلس"))
    }

    @Test
    fun aClosingScriptTagCannotAppearInTheOutput() {
        assertEquals("\"<\\/script>\"", Json.write("</script>"))
    }
}
