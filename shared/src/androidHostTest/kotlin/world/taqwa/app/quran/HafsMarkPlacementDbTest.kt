package world.taqwa.app.quran

import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The whole Quran as the app draws it, against the Hafs font itself and against pinned counts
 * (HafsSpelling's rules and table; docs/superpowers/reports/2026-11-07-quran-31-11.md, its
 * addendum, and the print-match design of the same day).
 *
 * Everything drawn comes from [DrawnQuran]: each ayah with what the repository says is read after
 * it, and each Mushaf line and word with what the page says follows it, so what is held here is
 * what the screens draw.
 *
 * The JDK's text layout works from the font's own tables as Android's HarfBuzz and Compose's iOS
 * Skia do. It runs with `liga` and `kern` off, which changes nothing here: the kashida glyphs come
 * from `calt`, and the placeholder is the font's own glyph for a code point.
 */
class HafsMarkPlacementDbTest {

    private val frc = FontRenderContext(null, true, true)
    private val font by lazy {
        System.setProperty("java.awt.headless", "true")
        Font.createFont(Font.TRUETYPE_FONT, resource("src/commonMain/composeResources/font/uthmanic_hafs.ttf")).deriveFont(1000f)
    }
    private fun glyphOf(codePoint: Int) = font.createGlyphVector(frc, Char(codePoint).toString()).getGlyphCode(0)
    private val alefGlyph by lazy { glyphOf(0x0670) }

    /** The dotted circle in a box the font draws for Tanzil's U+06E3 and U+06EB, its own glyphs for both. */
    private val placeholderGlyphs by lazy { setOf(glyphOf(0x06E3), glyphOf(0x06EB)) }

    /** The font's kashida glyphs, k01 to k150, by glyph id. */
    private val kashidaGlyphs = 1422..1571

    private fun glyphs(text: String) = text.toCharArray().let { font.layoutGlyphVector(frc, it, 0, it.size, Font.LAYOUT_RIGHT_TO_LEFT) }

    private fun alefRestsOnAKashida(text: String): Boolean {
        val glyphs = glyphs(text)
        val indices = 0 until glyphs.numGlyphs
        val kashidaClusters = indices.filter { glyphs.getGlyphCode(it) in kashidaGlyphs }.map { glyphs.getGlyphCharIndex(it) }.toSet()
        return indices.any { glyphs.getGlyphCode(it) == alefGlyph && glyphs.getGlyphCharIndex(it) in kashidaClusters }
    }

    private fun drawsAPlaceholder(text: String): Boolean = glyphs(text).let { g -> (0 until g.numGlyphs).any { g.getGlyphCode(it) in placeholderGlyphs } }

    private val stored by lazy { DrawnQuran.ayahs.map { it.text } }
    private val drawn by lazy { DrawnQuran.drawnAyahs }
    private val drawnWords by lazy { drawn.flatMap { it.split(' ') }.distinct() }

    private fun String.count(c: Int) = count { it.code == c }
    private fun List<String>.count(c: Int) = sumOf { it.count(c) }
    private fun List<String>.count(s: String) = sumOf { it.windowed(s.length).count { w -> w == s } }

    // R1: every sounded sukun becomes the head of kha and every silent-letter sign the ring; R2:
    // every tatweel before a superscript alef goes; R6: after a lam, ـٔ َا becomes أٓ; R5 then R4: each
    // tanwin becomes its short vowel before a small meem, or staggered unless a throat letter or ٱ is
    // read next; and the table's 44 words. Nothing else.
    @Test fun eachRuleChangesExactlyItsOwnMarksOverTheWholeQuran() {
        val tatweelBeforeAlef = "ـٰ"
        val hamzaOnTatweelAlef = listOf(0x0640, 0x0654, 0x064E, 0x0627).joinToString("") { Char(it).toString() }
        val alefHamzaMadda = listOf(0x0623, 0x0653).joinToString("") { Char(it).toString() }
        // R1
        assertEquals(37_148, stored.count(0x0652), "sounded sukun stored")
        assertEquals(3_988, stored.count(0x06DF), "silent-letter signs stored")
        assertEquals(stored.count(0x0652), drawn.count(0x06E1), "every sounded sukun drawn as the head of kha")
        assertEquals(stored.count(0x06DF), drawn.count(0x0652), "every silent-letter sign drawn as the ring")
        assertEquals(0, drawn.count(0x06DF))
        assertEquals(0, stored.count(0x06E1))
        // R2
        assertEquals(5_924, stored.count(tatweelBeforeAlef), "tatweels before a superscript alef")
        assertEquals(0, drawn.count(tatweelBeforeAlef))
        // R6
        val lamAlef = stored.sumOf { ayah -> Regex("ل[\\u064B-\\u065F\\u0670]*$hamzaOnTatweelAlef").findAll(ayah).count() }
        assertEquals(277, lamAlef, "lam, then a hamza on a tatweel, a fatha and an alef")
        assertEquals(stored.count(alefHamzaMadda) + lamAlef, drawn.count(alefHamzaMadda))
        assertEquals(stored.count(hamzaOnTatweelAlef) - lamAlef, drawn.count(hamzaOnTatweelAlef))
        // No other tatweel goes, and the table adds 2:97's.
        assertEquals(stored.count(0x0640) - stored.count(tatweelBeforeAlef) - lamAlef + 1, drawn.count(0x0640), "no other tatweel goes")
        assertEquals(stored.count(0x0670), drawn.count(0x0670))
        // R5
        val iqlab = listOf(0x064B, 0x064C, 0x064D).sumOf { t -> listOf(0x06E2, 0x06ED).sumOf { m -> stored.count("${Char(t)}${Char(m)}") } }
        assertEquals(339, iqlab, "tanwins before a small meem")
        assertEquals(240, listOf(0x064B, 0x064C, 0x064D).sumOf { t -> stored.count("${Char(t)}${Char(0x06E2)}") }, "before the high meem")
        assertEquals(0, listOf(0x064B, 0x064C, 0x064D).sumOf { t -> listOf(0x06E2, 0x06ED).sumOf { m -> drawn.count("${Char(t)}${Char(m)}") } })
        // R4
        val tanwins = listOf(0x064B, 0x064C, 0x064D).sumOf { stored.count(it) }
        val staggered = listOf(0x0657, 0x065E, 0x0656).sumOf { drawn.count(it) }
        val stacked = listOf(0x064B, 0x064C, 0x064D).sumOf { drawn.count(it) }
        assertEquals(0, listOf(0x0657, 0x065E, 0x0656).sumOf { stored.count(it) })
        assertEquals(8_893, tanwins, "tanwins stored")
        assertEquals(6_643, staggered, "tanwins drawn staggered")
        assertEquals(1_911, stacked, "tanwins drawn stacked, before a throat letter or ٱ")
        assertEquals(tanwins, iqlab + staggered + stacked)
        // The table: the sajdah's line on 26 words, the hamza below its seat in 14, and the four single words.
        assertEquals(0, stored.count(0x06E4))
        assertEquals(26, drawn.count(0x06E4), "words under a sajdah's line")
        assertEquals(26, DrawnQuran.drawnWords.sumOf { it.drawn.count(0x06E4) }, "and in the Mushaf")
        assertEquals(0, stored.count(0x0655))
        assertEquals(14, drawn.count(0x0655), "hamzas below their seat")
        assertEquals(stored.count(0x0626) - 13, drawn.count(0x0626))
        assertEquals(stored.count(0x0624) - 1, drawn.count(0x0624))
        assertEquals(stored.count(0x0621) + 1, drawn.count(0x0621), "2:72's hamza on the line")
        assertEquals(stored.count(0x0654) - 1 - lamAlef, drawn.count(0x0654))
        assertEquals(1, stored.count(0x06EB))
        assertEquals(0, drawn.count(0x06EB), "12:11's ishmam sign")
        assertEquals(stored.count(0x06EC) + 1, drawn.count(0x06EC))
        assertEquals(1, stored.count(0x06E3))
        assertEquals(0, drawn.count(0x06E3), "52:37's small seen")
        assertEquals(stored.count(0x06DC) + 1, drawn.count(0x06DC))
        // The sakta's seen: where Tanzil writes it apart after its word, the space before it goes.
        val seenApart = " " + Char(0x06DC)
        assertEquals(
            listOf("18:1", "36:52", "69:28", "75:27", "83:14"),
            DrawnQuran.ayahs.filter { seenApart in it.text }.map { "${it.surah}:${it.number}" },
            "the sakta's seen written apart",
        )
        assertEquals(5, stored.count(seenApart))
        assertEquals(0, drawn.count(seenApart))
        assertEquals(0, DrawnQuran.drawnWords.count { seenApart in it.drawn }, "and in the Mushaf")
        assertEquals(stored.count(' '.code) - 5, drawn.count(' '.code), "no other space goes")
        // Nothing else: the drawn text has the stored text's length less R2's tatweels, two
        // characters for each of R6's words and the five spaces, plus the table's: 2:97's tatweel,
        // a hamza below each of 14 seats and 26 lines.
        assertEquals(stored.sumOf { it.length } - stored.count(tatweelBeforeAlef) - 2 * lamAlef - 5 + 1 + 14 + 26, drawn.sumOf { it.length })
    }

    // Search finds a word by its place in the stored ayah (ArabicWordAlignment) and lights it in the
    // drawn one (highlightArabicWords) at HafsSpelling.drawnWordPositions: in every ayah, each stored
    // word lands on the drawn word with the same letters, and a sakta's seen on the word it joins.
    @Test fun searchLightsEachWordAtItsPlaceInTheDrawnAyah() {
        val space = Regex("\\s+")
        fun words(text: String) = text.split(space).filter { it.isNotEmpty() }
        fun letters(word: String) = word.filter { (it in 'ء'..'غ' || it in 'ف'..'ي') && it !in "اأإآءؤئىيو" }
        val wrong = mutableListOf<String>()
        DrawnQuran.ayahs.zip(drawn).forEach { (ayah, drawnAyah) ->
            val storedWords = words(ayah.text)
            val drawnWords = words(drawnAyah)
            val at = storedWords.indices.map { world.taqwa.app.widget.HafsSpelling.drawnWordPositions(ayah.text, setOf(it)).single() }
            if (at.toSet() != drawnWords.indices.toSet()) wrong += "${ayah.surah}:${ayah.number}: ${storedWords.size} words land on ${at.toSet().size} of ${drawnWords.size}"
            storedWords.forEachIndexed { k, word ->
                val ok = if (word == Char(0x06DC).toString()) {
                    k > 0 && at[k] == at[k - 1] && drawnWords[at[k]].endsWith(word)
                } else {
                    letters(word) == letters(drawnWords[at[k]])
                }
                if (!ok) wrong += "${ayah.surah}:${ayah.number} word $k"
            }
        }
        assertEquals(emptyList(), wrong)
    }

    // The basmala's بِسْمِ and ٱلرَّحْمَـٰنِ carry a sounded sukun each, ٱلرَّحْمَـٰنِ a tatweel before its alef.
    @Test fun theBasmalaIsDrawnWithTheHeadOfKhaAndItsAlefInPlace() {
        val basmala = DrawnQuran.basmala
        val drawnBasmala = world.taqwa.app.widget.HafsSpelling.forDrawing(basmala, DrawnQuran.ayahs[1].text, world.taqwa.app.widget.QuranPlace(1, 1))
        assertEquals(drawn.first(), drawnBasmala)
        assertEquals(2, drawnBasmala.count(0x06E1))
        assertEquals(0, drawnBasmala.count(0x0640))
    }

    @Test fun noSuperscriptAlefRestsOnAKashidaInAnyWordAyahMushafLineOrMushafWord() {
        val wrong = (drawnWords + drawn + DrawnQuran.drawnLines.map { it.drawn } + DrawnQuran.drawnWords.map { it.drawn }.distinct())
            .filter(::alefRestsOnAKashida)
        assertEquals(emptyList(), wrong, "texts with a superscript alef the font cannot place")
    }

    // Every code point drawn is one the font maps, and the font's placeholder, its dotted circle for
    // Tanzil's U+06EB (12:11) and U+06E3 (52:37), is drawn nowhere: the table hands it the signs the
    // Complex writes there, which the font draws as the 1405 print has them.
    @Test fun theFontMapsEveryCodePointAndDrawsThePlaceholderNowhere() {
        val unmapped = drawn.joinToString("").toSet().filter { !it.isWhitespace() && !font.canDisplay(it) }
        assertEquals(emptyList(), unmapped.map { "U+%04X".format(it.code) })
        val withPlaceholder = DrawnQuran.ayahs.zip(drawn).filter { (_, d) -> drawsAPlaceholder(d) }.map { (a, _) -> "${a.surah}:${a.number}" }
        assertEquals(emptyList(), withPlaceholder)
        val words = (drawnWords + DrawnQuran.drawnWords.map { it.drawn }.distinct()).filter(::drawsAPlaceholder)
        assertEquals(emptyList(), words, "words drawing the placeholder")
    }

    // The Mushaf draws word by word, each with the next word (or line, or page) after it; put back
    // together, its words are the ayah exactly as the Reader draws it, and its lines too.
    @Test fun theMushafDrawsEveryWordAsItsAyahDoes() {
        val byAyah = DrawnQuran.drawnWords.groupBy { it.word.surah to it.word.ayah }
        DrawnQuran.ayahs.zip(drawn).forEach { (ayah, drawnAyah) ->
            val words = byAyah.getValue(ayah.surah to ayah.number).sortedBy { it.word.word }.map { it.drawn }
            val last = words.last().substringBeforeLast(' ')
            assertEquals(drawnAyah, (words.dropLast(1) + last).joinToString(" "), "${ayah.surah}:${ayah.number}")
        }
        val byLine = DrawnQuran.drawnWords.groupBy { it.page to it.line }
        DrawnQuran.drawnLines.forEach { line ->
            assertEquals(line.drawn, byLine.getValue(line.page to line.line.line).joinToString(" ") { it.drawn }, "page ${line.page} line ${line.line.line}")
        }
    }

    // A change to a rule, its order, the context any screen hands it, or the stored text shows here
    // first. Pinned from this run; the print-match oracle (tools/quran-drawn-check.py) is what says
    // the value is right.
    @Test fun theWholeDrawnQuranIsPinned() {
        assertEquals("313f83ec829f1508e263cfdeee4bdd2129426fb3195e39e6fb42830ddaa6c644", sha256(drawn.joinToString("\n")))
    }

    // quran.db's ayah text is Tanzil's Uthmani file byte for byte, apart from the basmala taken off
    // the first ayah of the 112 surahs that carry it (the pipeline's verify_tanzil_bytes checks it
    // against the download itself; this pins the result). Copy and share hand this out.
    @Test fun theStoredTextIsTanzilsOwnBytes() {
        assertEquals("3a802babd61fd0835121feea6c054e16df4809417968e1f7b58aa18cdbae6e66", sha256(stored.joinToString("\n")))
        val row = stored[DrawnQuran.ayahs.indexOfFirst { it.surah == 31 && it.number == 11 }]
        val dalal = listOf(0x0636, 0x064E, 0x0644, 0x064E, 0x0640, 0x0670, 0x0644, 0x064D).joinToString("") { Char(it).toString() }
        assertEquals(dalal, row.split(' ')[13])
    }

    // A screen drawing Quran text in the Hafs face has to hand it through forDrawing: a new file, or
    // a call dropped from a file that has others, fails here. It counts calls, so a new call in a
    // listed file means looking at it and raising its number. Files that only ever draw a surah's
    // name or a reciter's initial carry no superscript alef, sukun or tanwin to convert.
    @Test fun everyPlaceDrawingAyahsInTheHafsFaceUsesTheDrawnSpelling() {
        val namesOnly = setOf("MushafFont.kt", "ReaderHeader.kt", "RecitationUi.kt", "PlayerBar.kt", "DownloadSheet.kt")
        val roots = listOf("src/commonMain/kotlin", "../androidApp/src/main/kotlin", "../iosApp/TaqwaWidget", "../iosApp/iosApp")
        val hafs = listOf("mushafFamily()", "FONT_HAFS", "TaqwaHafs.font")
        val calls = roots.flatMap { root -> resource(root).walkTopDown().toList() }
            .filter { it.extension == "kt" || it.extension == "swift" }
            .filter { it.name !in namesOnly }
            .filter { file -> file.readText().let { source -> hafs.any { it in source } } }
            .associate { it.name to it.readText().windowed("forDrawing(".length).count { window -> window == "forDrawing(" } }
        assertEquals(
            mapOf(
                "AdhkarPage.kt" to 2,
                "AyahCard.kt" to 1,
                "AyahCardRenderer.kt" to 1,
                "AyahWidgetPreview.kt" to 1,
                "MushafPageView.kt" to 3,
                "QuranRootScreen.kt" to 2,
                "ReaderScreen.kt" to 1,
                "ReadingSheet.kt" to 1,
                "TaqwaAyahWidget.swift" to 1,
            ),
            calls.toSortedMap(),
            "draw Quran text with HafsSpelling.forDrawing",
        )
        // Every call hands it what is read next and where the text stands (a call without them
        // does not compile); none may hand either as a bare null, which would say nothing follows
        // where something does, or that the text is at no place in the Quran.
        val withNull = roots.flatMap { root -> resource(root).walkTopDown().toList() }
            .filter { (it.extension == "kt" || it.extension == "swift") && it.name in calls }
            .flatMap { file -> callArguments(file.readText(), "forDrawing(").map { file.name to it } }
            .filter { (_, args) -> args.size != 3 || args.drop(1).any { it.substringAfter(':').trim() in setOf("null", "nil") } }
        assertEquals(emptyList(), withNull)
    }

    /** The top-level arguments of every call to [call] in [source], each trimmed, labels kept. */
    private fun callArguments(source: String, call: String): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var at = source.indexOf(call)
        while (at >= 0) {
            val args = mutableListOf<String>()
            var depth = 0
            val current = StringBuilder()
            var i = at + call.length
            while (i < source.length) {
                val c = source[i]
                when {
                    c == '(' -> { depth++; current.append(c) }
                    c == ')' && depth == 0 -> break
                    c == ')' -> { depth--; current.append(c) }
                    c == ',' && depth == 0 -> { args += current.toString().trim(); current.clear() }
                    else -> current.append(c)
                }
                i++
            }
            args += current.toString().trim()
            out += args
            at = source.indexOf(call, i)
        }
        return out
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun resource(path: String): File {
        val moduleRelative = File(path)
        if (moduleRelative.exists()) return moduleRelative
        val rootRelative = File("shared/$path")
        if (rootRelative.exists()) return rootRelative
        error("$path not found from ${File(".").absolutePath}")
    }
}
