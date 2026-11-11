package world.taqwa.app.wear.qibla

import kotlin.test.Test
import kotlin.test.assertTrue
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.qibla.QiblaSweep.FORMS
import world.taqwa.app.wear.qibla.QiblaSweep.SCALES
import world.taqwa.app.wear.qibla.QiblaSweep.WIDER
import world.taqwa.app.wear.qibla.QiblaSweep.grow
import world.taqwa.app.wear.qibla.QiblaSweep.host
import world.taqwa.app.wear.qibla.QiblaSweep.letters

/**
 * Ruling 68 item 4, T9: every caption the page draws over a dial that turns (the bearing, "Facing
 * qibla" and the tilt notice) keeps [QiblaFit.LETTER_AIR] from every turning letter's ink at every
 * heading, judged by Ruling 66 item 3's exact geometry ([Judge]), each of its lines as the page
 * breaks it, in all seven languages and each digit set, in both system-font variants, at every font
 * size from 0.85 to 2.0 in hundredths, measured as the host measures and 3 % wider. Ruling 62 item 5
 * saw the turning "E" meet the last "l" of "Hold your wrist level" on emulator-5558 (P3.9's
 * `qibla-minors/`); the remedy is the half anchor's rule or the words, never clearing the dial.
 */
class QiblaCaptionTest {

    /** T9: no glyph of a caption over a turning dial comes within LETTER_AIR of a turning letter's, at any heading. */
    @Test
    fun t9EveryCaptionOverATurningDialKeepsItsAirFromEveryLetterAtEveryHeading() {
        val failures = mutableListOf<String>()
        var least = Double.MAX_VALUE to ""
        each { case, f, laid ->
            val letters = letters(case.form, grow(f), case.variant)
            laid.lines.forEachIndexed { k, line ->
                val gap = Judge.letterGap(laid.ink[k], letters)
                if (gap < least.first) least = gap to "$case at $f: '$line' ${"%.2f".format(gap)}"
                if (gap < QiblaFit.LETTER_AIR - EPSILON) failures += "$case at $f: '$line' (line ${k + 1} of ${laid.lines.size}, size ${laid.size}) keeps ${"%.2f".format(gap)} from a letter"
            }
        }
        println("[measure] T9: the least air from a caption to a turning letter: ${least.second}")
        // T9's table for the device pass: each caption's size, lines and box at the drawing's font size and at 1.3.
        for (case in CASES.filter { it.wider == 1.0 && (it.line != QiblaType.bearing || it.text.contains(QiblaSweep.digits(it.form, "23"))) }) {
            val row = listOf(1f, 1.3f).joinToString(" | ") { f -> layout(case, f).let { "$f: ${it.size} on ${it.lines.size} (${it.lines.joinToString(" / ")}), box ${"%.1f".format(it.width)}" } }
            println("[table] T9: $case $row")
        }
        assertTrue(failures.isEmpty(), "${failures.size} caption lines meet the letters' air, the first: ${failures.take(12).joinToString("\n")}")
    }

    /** T9: a caption over a turning dial is never cut: its words fit the lines its box gives it, at the size it is drawn. */
    @Test
    fun t9NoCaptionOverATurningDialIsCut() {
        val failures = mutableListOf<String>()
        each { case, f, laid -> if (laid.cut) failures += "$case at $f: cut after '${laid.lines.last()}' (size ${laid.size}, ${laid.lines.size} lines)" }
        assertTrue(failures.isEmpty(), "${failures.size} captions cut, the first: ${failures.take(12).joinToString("\n")}")
    }

    /** T9: a caption keeps [HUB_AIR] from the needle's hub, its first line its highest. */
    @Test
    fun t9ACaptionKeepsClearOfTheHub() {
        val hub = 100f + QiblaDialMeasures.HUB * QiblaDialMeasures.K
        val failures = mutableListOf<String>()
        var least = Float.MAX_VALUE to ""
        each { case, f, laid ->
            val room = laid.ink.first().minOf { it.top } - hub
            if (room < least.first) least = room to "$case at $f: '${laid.lines.first()}' ${"%.2f".format(room)}"
            if (room < HUB_AIR - EPSILON) failures += "$case at $f: '${laid.lines.first()}' at ${laid.size} reaches ${"%.2f".format(room)} from the hub"
        }
        println("[measure] T9: the least room from a caption to the hub: ${least.second}")
        assertTrue(failures.isEmpty(), "${failures.size} captions near the hub, the first: ${failures.take(12).joinToString("\n")}")
    }

    /**
     * T9: a caption's last line keeps the half anchor (Ruling 66 item 2, the remedy Ruling 68 item 4
     * names): its baseline is [QiblaFit.captionY] at every font size, at the size TextAutoSize draws
     * it, on one line or two. A box set from a larger size than the one drawn would lift a second
     * line off it (French "poignet à plat" at 13.5 in a box of 15, 1.60 units high at 1.0).
     */
    @Test
    fun t9ACaptionsLastLineKeepsTheHalfAnchor() {
        val failures = mutableListOf<String>()
        var most = 0f to ""
        each { case, f, laid ->
            val anchor = QiblaFit.captionY(case.line, f)
            val last = laid.baselines.last()
            val off = anchor - last
            val row = "$case at $f: '${laid.lines.last()}' (line ${laid.lines.size} of ${laid.lines.size}, drawn at ${laid.size} in a box of ${laid.boxSize}) " +
                "at ${"%.2f".format(last)}, the anchor ${"%.2f".format(anchor)}"
            if (kotlin.math.abs(off) > kotlin.math.abs(most.first)) most = off to row
            if (kotlin.math.abs(off) > EPSILON) failures += row
        }
        println("[measure] T9: the farthest a caption's last baseline lies from the half anchor: ${most.second.ifEmpty { "none, every one on it" }}")
        assertTrue(failures.isEmpty(), "${failures.size} captions off the half anchor, the first: ${failures.take(12).joinToString("\n")}")
    }

    /** T9: as the font grows a caption over a turning dial never shrinks, and keeps its lines and its box's width. */
    @Test
    fun t9ACaptionNeverShrinksAsTheFontGrowsAndKeepsItsLines() {
        val failures = mutableListOf<String>()
        var before: Pair<Case, Laid>? = null
        each { case, f, laid ->
            val last = before?.takeIf { it.first == case }?.second
            if (last != null) {
                if (laid.size < last.size - EPSILON) failures += "$case: ${last.size} then ${laid.size} at $f"
                if (laid.lines.size != last.lines.size) failures += "$case: ${last.lines.size} lines then ${laid.lines.size} at $f"
                if (kotlin.math.abs(laid.width - last.width) > EPSILON) failures += "$case: its box ${last.width} then ${laid.width} at $f"
            }
            before = case to laid
        }
        assertTrue(failures.isEmpty(), "${failures.size} steps, the first: ${failures.take(12).joinToString("\n")}")
    }

    /**
     * T9: the letters' room at a baseline ([QiblaFit.captionRoom]) is the widest box centred across
     * the dial, from a line's highest ink to its lowest, that the test's own judge finds LETTER_AIR
     * from every letter at every heading: a hundredth narrower keeps the air, a twentieth wider does not.
     */
    @Test
    fun t9TheLettersRoomIsTheWidestBoxTheJudgeAllows() {
        var cases = 0
        for (form in FORMS) for (variant in QiblaSweep.variants(form)) for (g in listOf(1f, 1.15f, 1.3f)) {
            val letters = letters(form, g, variant)
            for (y in listOf(119f, 125f, 138.2f, 141f)) for ((top, bottom) in listOf(-0.8f to 0.25f, -0.9f to 0.46f, -0.75f to 0f)) {
                for (size in listOf(11f, 15f, 18f)) {
                    val room = QiblaFit.captionRoom(y, top * size, bottom * size, letters)
                    fun box(width: Float) = listOf(InkBox(100f - width / 2, 100f + width / 2, y + top * size, y + bottom * size))
                    assertTrue(room > 40f, "$form at $g, y $y: a room of $room")
                    val inside = Judge.letterGap(box(room - 0.01f), letters)
                    val beyond = Judge.letterGap(box(room + 0.05f), letters)
                    assertTrue(inside >= QiblaFit.LETTER_AIR - EPSILON, "$form at $g, y $y, $top..$bottom at $size: $room keeps only ${"%.3f".format(inside)}")
                    assertTrue(beyond < QiblaFit.LETTER_AIR, "$form at $g, y $y, $top..$bottom at $size: wider than $room still keeps ${"%.3f".format(beyond)}")
                    cases++
                }
            }
        }
        // A line far below the letters' circle has no letter to keep from; one on it has no room.
        val en = letters(QiblaSweep.form("en", "west"), 1f, HostInk.Variant.UI)
        assertTrue(QiblaFit.captionRoom(190f, -10f, 2f, en) == Float.POSITIVE_INFINITY)
        assertTrue(QiblaFit.captionRoom(172f, -8f, 2f, en) == 0f)
        assertTrue(cases > 0)
    }

    // ---- The captions, laid out as the page lays them out

    /** A caption over a turning dial: its form, the caption's line and text, the font variant and how much wider it is measured. */
    private data class Case(val form: QiblaSweep.Form, val line: Line, val text: String, val variant: HostInk.Variant, val wider: Double) {
        override fun toString() = "$form ${variant.name.lowercase()} '$text'${if (wider > 1) " 3 % wider" else ""}"
    }

    /**
     * A caption as drawn: its size, its box's width and largest size, its lines, each line's
     * baseline and glyph ink on the dial, and whether its words overflow its lines.
     */
    private data class Laid(
        val size: Float,
        val width: Float,
        val boxSize: Float,
        val lines: List<String>,
        val baselines: List<Float>,
        val ink: List<List<InkBox>>,
        val cut: Boolean,
    )

    /** Every case at every font size, in order of case and font size, each layout made once for the sizes the measures cap to. */
    private fun each(check: (Case, Float, Laid) -> Unit) {
        var rows = 0
        for (case in CASES) {
            val byScale = HashMap<Float, Laid>()
            for (f in SCALES) {
                val capped = f.coerceIn(1f, 1.3f)
                check(case, f, byScale.getOrPut(capped) { layout(case, capped) })
                rows++
            }
        }
        assertTrue(rows > 0)
    }

    /**
     * The page's layout of [case] at [fontScale]: its box ([QiblaType.captionBox]), its size as
     * TextAutoSize steps it (the largest from the line's floor, a half at a time, whose lines fit the
     * box), its lines as Android's simple strategy breaks them there, each centred across the dial,
     * the first at the box's baseline and the next a pitch below.
     */
    private fun layout(case: Case, fontScale: Float): Laid {
        val line = case.line
        val glyphs = host(case.variant).line(case.text, 800, case.form.rtl).boxes
        val turning = QiblaType.Turning(glyphs.minOf { it.top }, glyphs.maxOf { it.bottom }) { grow -> letters(case.form, grow, case.variant) }
        val box = QiblaType.captionBox(line, fontScale, round = true, turning) { size, width -> wrapped(case, size, width).size }
        val most = box.size
        var size = line.range(fontScale).start
        while (size + STEP <= most + EPSILON && wrapped(case, size + STEP, box.width).size <= box.maxLines) size += STEP
        val all = wrapped(case, size, box.width)
        val lines = all.take(box.maxLines)
        val baselines = lines.indices.map { k -> box.y + k * QiblaType.TITLE_PITCH * size / line.units }
        val ink = lines.mapIndexed { k, text ->
            host(case.variant).line(text, 800, case.form.rtl).scaled(size).wider(case.wider).centred().map { it.moved(100f, baselines[k]) }
        }
        return Laid(size, box.width, box.size, lines, baselines, ink, cut = all.size > box.maxLines)
    }

    /** [case]'s words in lines at [size] in [width] units, as Android's simple strategy breaks them, in ExtraBold, measured [Case.wider]. */
    private fun wrapped(case: Case, size: Float, width: Float): List<String> {
        val host = host(case.variant)
        val out = mutableListOf<String>()
        var line = ""
        for (word in case.text.split(' ')) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && host.line(trial, 800, case.form.rtl).advance * size * case.wider > width) {
                out += line
                line = word
            } else {
                line = trial
            }
        }
        return out + line
    }

    private companion object {
        const val EPSILON = 1e-3f

        /** How far a caption keeps from the needle's hub, in units (T6's rule for Indonesian's two-line notice, now every caption's). */
        const val HUB_AIR = 2f

        /** TextAutoSize's step for a caption, in units (`QiblaPage`'s). */
        const val STEP = 0.5f

        /** Every caption over a turning dial: the bearing (the dimmed sweep's widest bearings, 23 and 359), "Facing qibla" and the tilt notice, in every form, variant and width. */
        val CASES: List<Case> by lazy {
            FORMS.flatMap { form ->
                QiblaSweep.variants(form).flatMap { variant ->
                    val bearing = QiblaSweep.phoneString(form.folder, "qibla_bearing")
                    val captions = QiblaSweep.bearings(form, variant).map { QiblaType.bearing to QiblaSweep.fill(bearing, QiblaSweep.digits(form, "$it")) } +
                        (QiblaType.facing to QiblaSweep.phoneString(form.folder, "qibla_facing_qibla")) +
                        (QiblaType.level to QiblaSweep.watchWord(QiblaSweep.LANGUAGES.getValue(form.language), "holdLevel"))
                    captions.flatMap { (line, text) -> WIDER.map { Case(form, line, text, variant, it) } }
                }
            }
        }
    }
}
