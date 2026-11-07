package world.taqwa.app.quran

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Writes the Quran as the app draws it to `shared/build/drawn-quran/`, for
 * `tools/quran-drawn-check.py` to hold against an outside text it is given by path (the King Fahd
 * Complex's, which the repository does not carry). The text comes from the app's own code through
 * [DrawnQuran], never from a re-implementation of the rules.
 *
 * - `ayahs.tsv`: surah, ayah, the stored text, the drawn text.
 * - `words.tsv`: page, line, position, surah, ayah, word, the stored Mushaf word, the drawn one.
 */
class DrawnQuranDumpTest {

    @Test fun writesTheDrawnQuranForTheOracle() {
        val out = File("build/drawn-quran").also { it.mkdirs() }
        val ayahs = DrawnQuran.ayahs.zip(DrawnQuran.drawnAyahs).joinToString("") { (a, drawn) ->
            "${a.surah}\t${a.number}\t${a.text}\t$drawn\n"
        }
        File(out, "ayahs.tsv").writeText(ayahs)
        val words = DrawnQuran.drawnWords.joinToString("") { w ->
            "${w.page}\t${w.line}\t${w.word.position}\t${w.word.surah}\t${w.word.ayah}\t${w.word.word}\t${w.word.text}\t${w.drawn}\n"
        }
        File(out, "words.tsv").writeText(words)
        assertEquals(6236, File(out, "ayahs.tsv").readLines().size)
        assertEquals(DrawnQuran.drawnWords.size, File(out, "words.tsv").readLines().size)
    }
}
