package world.taqwa.app.quran

import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import world.taqwa.app.widget.HafsSpelling

/**
 * The whole Quran against the Hafs font itself, not against a list of words. A reader reported
 * ضَلَـٰلٍ at 31:11 reading as ضَلَلٍ: the superscript alef was drawn, but outside the word, on the
 * baseline left of its last letter (docs/superpowers/reports/2026-11-07-quran-31-11.md).
 *
 * The JDK's text layout works from the font's own tables as Android's HarfBuzz and Compose's iOS
 * Skia do. It runs with `liga` and `kern` off, which changes nothing here: the kashida glyphs come
 * from `calt`. The font has no right place for a superscript alef on a kashida glyph, the one it
 * makes of a tatweel and the word's last letter: each of the eleven it can land on puts it on the
 * baseline outside the word, over the last letter, or into a vowel. So the fault is exact: an alef
 * sharing a cluster with one of those glyphs.
 */
class HafsMarkPlacementDbTest {

    private val frc = FontRenderContext(null, true, true)
    private val font by lazy {
        System.setProperty("java.awt.headless", "true")
        Font.createFont(Font.TRUETYPE_FONT, resource("src/commonMain/composeResources/font/uthmanic_hafs.ttf")).deriveFont(1000f)
    }
    private val alefGlyph by lazy { font.createGlyphVector(frc, Char(0x0670).toString()).getGlyphCode(0) }

    /** The font's kashida glyphs, k01 to k150, by glyph id. */
    private val kashidaGlyphs = 1422..1571

    private fun alefRestsOnAKashida(text: String): Boolean {
        val chars = text.toCharArray()
        val glyphs = font.layoutGlyphVector(frc, chars, 0, chars.size, Font.LAYOUT_RIGHT_TO_LEFT)
        val indices = 0 until glyphs.numGlyphs
        val kashidaClusters = indices.filter { glyphs.getGlyphCode(it) in kashidaGlyphs }.map { glyphs.getGlyphCharIndex(it) }.toSet()
        return indices.any { glyphs.getGlyphCode(it) == alefGlyph && glyphs.getGlyphCharIndex(it) in kashidaClusters }
    }

    private fun column(sql: String): List<String> =
        DriverManager.getConnection("jdbc:sqlite:" + resource("src/commonMain/composeResources/files/quran.db").absolutePath).use { db ->
            db.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    private val ayahs by lazy { column("SELECT text_uthmani FROM ayah ORDER BY surah, number") }
    private val words by lazy { ayahs.flatMap { it.split(' ') } }
    private val tatweelBeforeAlef = "${Char(0x0640)}${Char(0x0670)}"

    @Test fun noSuperscriptAlefRestsOnAKashidaAnywhereInTheQuran() {
        val wrong = words.distinct().filter { alefRestsOnAKashida(HafsSpelling.forDrawing(it)) }
        assertEquals(emptyList(), wrong, "words whose superscript alef the font cannot place")
    }

    @Test fun norOnAWholeAyahOrAMushafLineOrAMushafWord() {
        val mushafLines = column("SELECT text FROM page_line WHERE type = 'text'")
        val mushafWords = column("SELECT DISTINCT text FROM line_word")
        val wrong = (ayahs + mushafLines + mushafWords).filter { alefRestsOnAKashida(HafsSpelling.forDrawing(it)) }
        assertEquals(emptyList(), wrong, "texts with a superscript alef the font cannot place")
    }

    // The rule touches exactly the words the font gets wrong, in both directions: nothing that was
    // already right changes shape, and if the font or the text ever changes this says so.
    @Test fun theRuleCoversExactlyTheWordsTheFontMisplaces() {
        val distinct = words.distinct()
        val misplaced = distinct.filter(::alefRestsOnAKashida).toSet()
        val rewritten = distinct.filter { HafsSpelling.forDrawing(it) != it }.toSet()
        assertEquals(emptySet(), misplaced - rewritten, "misplaced, and the rule leaves them")
        assertEquals(emptySet(), rewritten - misplaced, "rewritten, and the font had them right")
        assertEquals(322, misplaced.size)
        assertEquals(1333, words.count { it in misplaced })
    }

    // A word the rule touches is the stored word with every U+0640 before U+0670 gone and nothing
    // else changed, which is what Tanzil's own download without the tatweel option has for it. A
    // word it leaves alone keeps every one. Dropping them on screen is a rendering choice.
    @Test fun aRewrittenWordLosesEveryTatweelBeforeAnAlefAndNothingElse() {
        val alef = Char(0x0670).toString()
        words.distinct().forEach { word ->
            val drawn = HafsSpelling.forDrawing(word)
            if (drawn != word) assertEquals(word.replace(tatweelBeforeAlef, alef), drawn, word)
        }
        assertEquals(1448, words.sumOf { word -> if (HafsSpelling.forDrawing(word) != word) word.windowed(2).count { it == tatweelBeforeAlef } else 0 })
    }

    // Nothing here changes what is stored, and copy and share hand out what is stored.
    @Test fun theStoredAyahStillCarriesItsTatweel() {
        val row = column("SELECT text_uthmani FROM ayah WHERE surah = 31 AND number = 11").single()
        val dalal = listOf(0x0636, 0x064E, 0x0644, 0x064E, 0x0640, 0x0670, 0x0644, 0x064D).joinToString("") { Char(it).toString() }
        assertEquals(dalal, row.split(' ')[13])
        assertEquals(3, row.split(' ').count { tatweelBeforeAlef in it })
    }

    // A screen drawing Quran text in the Hafs face has to hand it through forDrawing: a new file, or
    // a call dropped from a file that has others, fails here. It counts calls, so a new call in a
    // listed file means looking at it and raising its number. Files that only ever draw a surah's
    // name or a reciter's initial carry no superscript alef.
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
    }

    private fun resource(path: String): File {
        val moduleRelative = File(path)
        if (moduleRelative.exists()) return moduleRelative
        val rootRelative = File("shared/$path")
        if (rootRelative.exists()) return rootRelative
        error("$path not found from ${File(".").absolutePath}")
    }
}
