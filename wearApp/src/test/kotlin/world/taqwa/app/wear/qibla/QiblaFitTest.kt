package world.taqwa.app.wear.qibla

import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import world.taqwa.app.qibla.CompassLowReason
import world.taqwa.app.qibla.DialState
import world.taqwa.app.qibla.Guidance
import world.taqwa.app.qibla.Turn
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.words
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.app.Line
import world.taqwa.app.wear.qibla.QiblaSweep.Case
import world.taqwa.app.wear.qibla.QiblaSweep.FIGURES
import world.taqwa.app.wear.qibla.QiblaSweep.FORMS
import world.taqwa.app.wear.qibla.QiblaSweep.SCALES
import world.taqwa.app.wear.qibla.QiblaSweep.SWEEP
import world.taqwa.app.wear.qibla.QiblaSweep.form
import world.taqwa.app.wear.qibla.QiblaSweep.grow
import world.taqwa.app.wear.qibla.QiblaSweep.host
import world.taqwa.app.wear.qibla.QiblaSweep.layout
import world.taqwa.app.wear.qibla.QiblaSweep.letters

/**
 * Ruling 66 items 2 to 6, the tests T1 to T7 (T8, the words, is `QiblaModelTest`'s and the feed's):
 * the Qibla detail is the watch's short form on one line, clear of the turning letters at every
 * heading, inside the ticks, and never smaller as the font grows. The cases are [QiblaSweep]'s:
 * every figure the phone sends, seven languages in each of their digit sets, fonts from 0.85 to 2.0
 * in hundredths, both dials, measured as the host measures and 3 % wider, the ink the glyphs'
 * outlines ([HostInk]), each layout the page's and each judged by the test's own geometry ([Judge]).
 */
class QiblaFitTest {

    // ---- T1 to T5, over the sweep

    /**
     * T1: one line, never under 9, never cut. What binds is the page's three constants, which the
     * page hands its detail's `BasicText`: `QiblaType.detail.maxLines` 1, `DETAIL_SOFT_WRAP` false
     * (never wrapped, so a device font wider than the host's measure cannot give it a second line)
     * and `DETAIL_OVERFLOW` Visible (never an ellipsis); and the size floor, 9 or more in every row
     * of the sweep. The rows' lines and cut are one and false by construction (`QiblaSweep.layout`
     * writes them, since the page has no second-line path to measure), so their asserts hold only
     * that seam.
     */
    @Test
    fun t1TheDetailIsOneLineNeverUnderNineAndNeverCut() {
        assertEquals(1, QiblaType.detail.maxLines)
        assertFalse(QiblaType.DETAIL_SOFT_WRAP, "never wrapped, so a wider device font cannot take a second line")
        assertEquals(TextOverflow.Visible, QiblaType.DETAIL_OVERFLOW, "never an ellipsis")
        each { series, i ->
            assertEquals(1, series.lines[i], "on two lines: ${series.row(i)}")
            assertTrue(series.size[i] >= 9f - EPSILON, "under 9: ${series.row(i)}")
            assertFalse(series.cut[i], "cut: ${series.row(i)}")
        }
        val turning = SWEEP.count { it.bearing == null }
        println("[measure] the sweep: ${SWEEP.size} series, ${SWEEP.size * SCALES.size} rows, ${turning * SCALES.size} of them on the turning dial")
    }

    /** T2: a turning dial's detail keeps LETTER_AIR from every letter's ink, as drawn at its font size, at every heading. No exception. */
    @Test
    fun t2TheDetailKeepsItsAirFromEveryTurningLetterAtEveryHeading() {
        var least: Pair<Float, String>? = null
        each(dimmed = false) { series, i ->
            val gap = series.letterGap[i]
            assertTrue(gap >= AIR - EPSILON, "a turning letter ${"%.2f".format(gap)} from the detail: ${series.row(i)}")
            if (least == null || gap < least!!.first) least = gap to series.row(i)
        }
        println("[measure] T2: the least gap to a turning letter, ${least?.second}")
        printTable()
    }

    /**
     * T2's letters are drawn as their lines say (Ruling 67 item 2). The page grows every dial letter
     * by north's growth (`QiblaPage`'s `grow`, and [QiblaFit.JUDGED_GROW] = north's cap above the
     * drawing's size), and draws it at its line's units times that growth, so a cap or a floor of
     * `QiblaType.cardinal`'s own would be silently ignored: east, south and west would not grow as
     * their line says, and T2 would judge letters the page does not draw. So each letter's line
     * grows exactly as north's does, and never shrinks.
     */
    @Test
    fun t2TheDialsLettersGrowAsTheirLinesSay() {
        for (line in listOf(QiblaType.north, QiblaType.cardinal)) {
            assertEquals(QiblaFit.JUDGED_GROW, line.cap, "a letter's cap is the cap the letters are judged at")
            for (fontScale in SCALES) {
                val range = line.range(fontScale)
                assertEquals(line.units, range.start, "never smaller than the drawing's ${line.units} at $fontScale")
                assertEquals(line.units * grow(fontScale), range.endInclusive, 1e-4f, "${line.units} units grown as north grows at $fontScale")
            }
        }
    }

    /** T3: every dial's detail keeps LETTER_AIR inside the circle where the ticks begin (TICK_ROOM). */
    @Test
    fun t3TheDetailKeepsInsideTheTicksOnEveryDial() {
        each { series, i ->
            assertTrue(series.tickGap[i] >= AIR - EPSILON, "${"%.2f".format(series.tickGap[i])} inside the ticks: ${series.row(i)}")
        }
    }

    /**
     * T3's dimmed rows hold the bearings whose ink reaches nearest the ticks (Ruling 67 item 2): the
     * sweep takes its dimmed dial's bearings by their measured ink, as T3 judges it, not by their
     * advance. In Bengali digits every bearing of three figures has one advance, so the advance
     * took 100 to 104; ২০০° reaches nearer the ticks than any of them, since ২'s ink begins nearer
     * its pen and runs below the baseline. So no bearing the phone can send reaches nearer the ticks
     * than the nearest of those swept, in any form or font variant.
     */
    @Test
    fun t3TheDimmedSweepTakesTheBearingsWhoseInkReachesNearestTheTicks() {
        for (form in FORMS) for (variant in QiblaSweep.variants(form)) {
            val swept = QiblaSweep.bearings(form, variant)
            val nearest = swept.minOf { QiblaSweep.bearingReach(form, variant, it) }
            println("[measure] T3: $form ${variant.name.lowercase()}'s dimmed bearings $swept, the nearest ${"%.2f".format(nearest)} inside the ticks with 20,000 km at 10")
            for (bearing in 0..359) {
                val reach = QiblaSweep.bearingReach(form, variant, bearing)
                assertTrue(reach >= nearest - 1e-9, "$form ${variant.name.lowercase()}: $bearing° keeps ${"%.3f".format(reach)} inside the ticks, nearer than the swept $swept (${"%.3f".format(nearest)})")
            }
        }
        for (variant in HostInk.Variant.entries) {
            assertTrue(200 in QiblaSweep.bearings(form("bn", "beng"), variant), "Bengali's ২০০° among the swept, ${variant.name.lowercase()} fonts")
        }
    }

    /** T4: the size never falls as the font size grows. */
    @Test
    fun t4TheSizeNeverFallsAsTheFontGrows() {
        each { series, i ->
            if (i > 0) assertTrue(series.size[i] >= series.size[i - 1] - EPSILON, "${series.size[i - 1]} at ${SCALES[i - 1]}, then ${series.row(i)}")
        }
    }

    /** T5: at the drawing's font size and below, every figure is the drawing's 10 on one line; and the places the ruling names. */
    @Test
    fun t5AtTheDrawingsFontSizeEveryFigureIsTenOnOneLine() {
        each { series, i ->
            if (SCALES[i] <= 1f) {
                assertEquals(1, series.lines[i], series.row(i))
                assertEquals(10f, series.size[i], series.row(i))
            }
        }
        for ((place, form, km) in listOf(
            Triple("Cape Town", form("en", "west"), 6_600),
            Triple("Jakarta", form("id", "west"), 7_900),
            Triple("Surabaya", form("id", "west"), 8_600),
            Triple("Dhaka", form("bn", "beng"), 5_300),
            Triple("Dhaka", form("bn", "west"), 5_300),
        )) {
            for (bearing in listOf(null, 23)) {
                val laid = layout(Case(form, km, bearing, 1f, 1.0, HostInk.Variant.UI))
                assertEquals(10f, laid.size, "$place, ${QiblaSweep.text(form, km, bearing)}: $laid")
                assertEquals(1, laid.lines, "$place: $laid")
            }
        }
    }

    // ---- T6, the positions

    /** T6: the half anchor (Ruling 66 item 2): y 141 and 155 at the drawing's size, the caption rising as it grows and the detail sinking only as it grows past the caption. */
    @Test
    fun t6TheCaptionAndTheDetailStandOnTheHalfAnchor() {
        for (line in CAPTIONS) {
            assertEquals(141f, QiblaFit.captionY(line, 1f))
            assertEquals(155f, QiblaFit.detailY(line, 1f, 10f))
            assertEquals(141f, QiblaFit.captionY(line, 0.85f), "a smaller font size never shrinks a line")
            for (fontScale in SCALES) {
                val c = line.range(fontScale).endInclusive / line.units
                assertEquals(c, QiblaFit.growth(line, fontScale), 1e-6f)
                assertEquals(155f - 14f * c, QiblaFit.captionY(line, fontScale), 1e-4f)
                for (size in listOf(9f, 9.5f, 10f, 11f, 12f, 12.5f, 13f)) {
                    assertEquals(155f + 14f * max(0f, size / 10f - c), QiblaFit.detailY(line, fontScale, size), 1e-4f)
                }
            }
            // Every caption grows alike (its cap 1.2), so every caption puts the detail in one place.
            assertEquals(QiblaFit.growth(QiblaType.bearing, 1.3f), QiblaFit.growth(line, 1.3f))
        }
        assertEquals(155f - 14f * 1.2f, QiblaFit.captionY(QiblaType.bearing, 2f), 1e-4f)
        assertEquals(155f + 14f * 0.1f, QiblaFit.detailY(QiblaType.bearing, 1.3f, 13f), 1e-4f)
        assertEquals(155f, QiblaFit.detailY(QiblaType.bearing, 1.3f, 11f), "a detail grown less than its caption keeps 155")
        // The sizes tried: 10, 9.5, 9 at the drawing's size and below; above it from the grown size's 0.5 down to the size at 1.0.
        assertEquals(listOf(10f, 9.5f, 9f), QiblaFit.sizes(1f, atOne = 9f))
        assertEquals(listOf(10f, 9.5f, 9f), QiblaFit.sizes(0.85f, atOne = 10f))
        assertEquals(listOf(10.5f, 10f), QiblaFit.sizes(1.06f, atOne = 10f))
        assertEquals(listOf(12f, 11.5f, 11f, 10.5f, 10f, 9.5f), QiblaFit.sizes(1.24f, atOne = 9.5f))
        assertEquals(listOf(13f, 12.5f, 12f, 11.5f, 11f, 10.5f, 10f), QiblaFit.sizes(1.3f, atOne = 10f))
        assertEquals(QiblaFit.sizes(1.3f, atOne = 10f), QiblaFit.sizes(2f, atOne = 10f))
    }

    /**
     * T6: the ink between the caption and the detail is never less than at the drawing's size,
     * whatever size the detail is drawn at, in the Latin scripts by a bound: Manrope ExtraBold's
     * deepest reach below the baseline in any caption, against the highest any detail reaches
     * above its own, wherever the caption breaks and wherever across the glyphs stand. Arabic's,
     * Urdu's and Bengali's captions reach deeper (the elegant Naskh's ي, 0.46 of its size), so the
     * next test holds all seven languages by the glyphs themselves.
     */
    @Test
    fun t6TheCaptionGapIsNeverTighterThanAtTheDrawingsSize() {
        val below = captionDescent()
        val above = detailAscent()
        fun gap(line: Line, fontScale: Float, size: Float) =
            QiblaFit.detailY(line, fontScale, size) - QiblaFit.captionY(line, fontScale) - below * line.range(fontScale).endInclusive - above * size
        for (line in CAPTIONS) {
            val drawing = gap(line, 1f, 10f)
            assertTrue(drawing > 0f, "the drawing's own gap: $drawing")
            for (fontScale in SCALES) {
                for (size in QiblaFit.sizes(fontScale, atOne = 9f)) {
                    val g = gap(line, fontScale, size)
                    assertTrue(g >= drawing - EPSILON, "at $fontScale a ${line.units}-unit caption over a $size detail: ${"%.2f".format(g)}, the drawing's ${"%.2f".format(drawing)}")
                }
            }
        }
        println("[measure] T6: caption descent ${"%.3f".format(below)} em, detail ascent ${"%.3f".format(above)} em")
    }

    /**
     * T6 in all seven languages (Ruling 67 item 2): the room between each caption and the detail
     * drawn under it is never less than at the drawing's size, and more than none there, for every
     * caption the page draws over a detail (the bearing, "Facing qibla" and the tilt notice over
     * the distance; a given-up compass's two titles over the bearing and the distance), every
     * figure the phone sends, every font size and every size the detail is tried at, in each digit
     * set and both system-font variants. Arabic's, Urdu's and Bengali's glyphs are HostInk's model
     * of the watch's own fonts (`qibla-ink.py`), whose elegant Naskh reaches 0.46 of its size below
     * the baseline, deeper than Manrope's 0.255 the Latin bound above leaves room for; so the room
     * is measured on the glyphs: from the lowest ink of the caption's last line, as the page breaks
     * it in its box (its size stepped as TextAutoSize steps it), to the highest of the detail's.
     * The caption's ink is taken at its line's largest size, which the half anchor moves it by
     * (TextAutoSize may draw it smaller, so higher, never larger), and the room is held across the
     * whole width, wherever the glyphs stand, as the Latin bound holds it.
     */
    @Test
    fun t6TheCaptionGapIsNeverTighterThanAtTheDrawingsSizeInEveryLanguage() {
        val capped = SCALES.map { it.coerceIn(1f, 1.3f) }.distinct()
        val least = HashMap<String, Pair<Float, String>>()
        for (form in FORMS) for (variant in QiblaSweep.variants(form)) {
            val bearings = QiblaSweep.bearings(form, variant)
            for (caption in captionsOverADetail(form, bearings)) {
                val details = if (caption.dimmed) bearings.flatMap { b -> FIGURES.map { QiblaSweep.text(form, it, b) } } else FIGURES.map { QiblaSweep.text(form, it, null) }
                val drawing = captionOnTheDial(form, variant, caption, 1f)
                val atOne = details.map { captionRoom(drawing, detailInk(form, variant, it, 10f), QiblaFit.detailY(caption.line, 1f, 10f)) }
                atOne.forEachIndexed { k, room ->
                    assertTrue(room > 0f, "$form ${variant.name.lowercase()}: '${drawing.last}' over '${details[k]}' at the drawing's size: ${"%.2f".format(room)}")
                }
                for (fontScale in capped) {
                    val laid = captionOnTheDial(form, variant, caption, fontScale)
                    for (size in QiblaFit.sizes(fontScale, atOne = 9f)) {
                        val y = QiblaFit.detailY(caption.line, fontScale, size)
                        details.forEachIndexed { k, detail ->
                            val room = captionRoom(laid, detailInk(form, variant, detail, size), y)
                            assertTrue(
                                room >= atOne[k] - EPSILON,
                                "$form ${variant.name.lowercase()}: '${caption.text}' (its last line '${laid.last}') over '$detail' at $size, font size $fontScale: " +
                                    "${"%.2f".format(room)}, the drawing's ${"%.2f".format(atOne[k])}",
                            )
                        }
                    }
                }
                val k = atOne.indices.minBy { atOne[it] }
                val key = "${form.language}/${QiblaSweep.DIGIT_NAMES.getValue(form.digits)} ${variant.name.lowercase()}"
                if (least[key] == null || atOne[k] < least.getValue(key).first) least[key] = atOne[k] to "'${drawing.last}' over '${details[k]}'"
            }
        }
        for ((key, value) in least.toSortedMap()) println("[measure] T6: $key, the least room under a caption at the drawing's size ${"%.2f".format(value.first)} units, ${value.second}")
    }

    /** T6: a reading's arrival never resizes or moves the detail or the caption: Searching before it (a heading that is not a number) is laid out as Searching with one. */
    @Test
    fun t6SearchingBeforeTheFirstReadingIsLaidOutAsSearching() {
        for ((name, feed) in SAMPLES) {
            for (tilted in listOf(false, true)) {
                val waiting = qiblaScreen(DialState.Searching(Double.NaN, guidance = null), tilted, feed)
                val reading = qiblaScreen(DialState.Searching(340.0, Guidance(Turn.RIGHT, 45)), tilted, feed)
                assertEquals(emptyList(), waiting.cardinals, "no letters drawn yet")
                assertEquals(feed.words().cardinals, waiting.dialLetters, "$name: the letters are judged all the same")
                assertEquals(reading.dialLetters, waiting.dialLetters, name)
                assertEquals(reading.detail, waiting.detail, name)
                assertEquals(reading.captionLine, waiting.captionLine, name)
                for (fontScale in listOf(1f, 1.15f, 1.3f)) {
                    assertEquals(screenLayout(reading, feed, fontScale), screenLayout(waiting, feed, fontScale), "$name at $fontScale")
                }
            }
            assertEquals(feed.words().cardinals, qiblaScreen(DialState.Aligned(24.0), tilted = false, feed = feed).dialLetters, name)
            for (dimmed in listOf(DialState.NoSensor, DialState.BestEffort(CompassLowReason.INTERFERENCE), DialState.Low(CompassLowReason.CALIBRATION))) {
                assertEquals(emptyList(), qiblaScreen(dimmed, tilted = false, feed = feed).dialLetters, "$name: a dimmed dial has no letters to keep clear of")
            }
        }
    }

    // T6's two-line tilt notice and the hub: Ruling 68 item 4's T9 holds every caption over a turning
    // dial clear of the hub, two lines or one, in all seven languages (QiblaCaptionTest).

    // ---- T7, the insurance and the ink

    /** T7: each letter is drawn at clamp(gap / LETTER_AIR, 0, 1): none where it would sit on the detail's ink, faded within the air, whole beyond it. */
    @Test
    fun t7ALetterFadesOnlyWithinTheAirAndVanishesOnTheInk() {
        assertEquals(1f, QiblaFit.LETTER_AIR)
        val detail = listOf(InkBox(90f, 110f, 150f, 157f))
        val letter = listOf(InkBox(-3f, 3f, -4f, 3f))
        // Its middle inside detail − letter (87..113 across, 147..161 down): it would sit on the ink.
        assertEquals(0f, QiblaFit.letterAlpha(100f, 155f, detail, letter))
        assertEquals(0f, QiblaFit.letterAlpha(113f, 160f, detail, letter), "on the edge")
        // Within the air: its distance from that box.
        assertEquals(0.5f, QiblaFit.letterAlpha(113.5f, 155f, detail, letter), 1e-5f)
        assertEquals(0.25f, QiblaFit.letterAlpha(100f, 146.75f, detail, letter), 1e-5f)
        // Beyond it, whole.
        assertEquals(1f, QiblaFit.letterAlpha(114f, 155f, detail, letter))
        assertEquals(1f, QiblaFit.letterAlpha(130f, 120f, detail, letter))
        assertEquals(1f, QiblaFit.letterAlpha(100f, 155f, emptyList(), letter), "no detail, nothing to fade for")
        // The nearest of several glyphs decides.
        assertEquals(0f, QiblaFit.letterAlpha(130f, 155f, detail + InkBox(125f, 128f, 150f, 157f), letter))
    }

    /** T7: wherever the size rule kept the air, the insurance is idle at every heading: no letter fades, in any language. */
    @Test
    fun t7TheInsuranceIsIdleWhereTheAirIsKept() {
        for (form in FORMS) for (variant in QiblaSweep.variants(form)) {
            for (km in listOf(6_600, 7_900, 11_000, 20_000)) {
                for (fontScale in listOf(1f, 1.15f, 1.3f)) {
                    val case = Case(form, km, null, fontScale, 1.0, variant)
                    val laid = layout(case)
                    val drawn = letters(form, grow(fontScale), variant)
                    for (quarter in 0 until 360 * 4) {
                        drawn.forEachIndexed { i, letter ->
                            val a = Math.toRadians(i * 90.0 - quarter / 4.0)
                            val x = (100 + Judge.LETTERS * sin(a)).toFloat()
                            val y = (100 - Judge.LETTERS * cos(a)).toFloat()
                            assertEquals(1f, QiblaFit.letterAlpha(x, y, laid.ink, letter), "letter $i at heading ${quarter / 4.0}: $case $laid")
                        }
                    }
                }
            }
        }
    }

    /**
     * T7 and its guard (Ruling 66 item 4): the ink is the glyphs' outlines, never the line-box
     * selection rectangles `TextLayoutResult.getPathForRange` gives (1.37 em tall in Manrope). A
     * Manrope digit's box, measured through the page's own [GlyphInk], is at most 0.8 em tall; a
     * selection's would not pass. And the page's layout reads the ink it is given: in selection boxes,
     * English's distances are laid out otherwise.
     */
    @Test
    fun t7TheInkIsTheGlyphsOutlinesAndTheLayoutReadsIt() {
        val host = host(HostInk.Variant.UI)
        for (digit in "0123456789") {
            val ink = host.line("$digit", 600, rtl = false)
            val tall = ink.boxes.maxOf { it.bottom } - ink.boxes.minOf { it.top }
            assertTrue(tall <= 0.8f, "'$digit' is $tall em tall")
        }
        val boxed = selectionInk(host.line("6,600 km", 600, rtl = false))
        val boxedTall = boxed.boxes.maxOf { it.bottom } - boxed.boxes.minOf { it.top }
        assertTrue(boxedTall > 0.8f, "the guard tells a selection's box (${"%.3f".format(boxedTall)} em) from a glyph's")

        val english = form("en", "west")
        var otherwise = 0
        for (km in FIGURES) for (fontScale in listOf(1f, 1.15f, 1.3f)) {
            val glyphs = layout(Case(english, km, null, fontScale, 1.0, HostInk.Variant.UI))
            val text = QiblaSweep.text(english, km, null)
            val selected = QiblaFit.detail(QiblaType.bearing, fontScale, ink = { size -> selectionInk(host.line(text, 600, rtl = false)).scaled(size) }) { grow ->
                QiblaSweep.cardinals("values").mapIndexed { i, letter ->
                    val line = if (i == 0) QiblaType.north else QiblaType.cardinal
                    val ink = selectionInk(host.line(letter, if (i == 0) 800 else 600, rtl = false)).scaled(line.units * grow)
                    QiblaFit.letterInk(ink, width = ink.advance, penStart = 0f, grow = grow)
                }
            }
            if (selected.size != glyphs.size) otherwise++
        }
        assertTrue(otherwise > 0, "the layout takes its sizes from the ink it is given")
        println("[measure] T7: in selection boxes $otherwise of ${FIGURES.size * 3} English distances would be drawn at another size")
    }

    /**
     * T7: the ink is laid out as the page draws it. Compose resolves a text direction it is not
     * given (`TextDirection.Unspecified`, 1.12.1's `resolveTextDirection`) to the page's layout
     * direction, not the first strong letter's, so on a right-to-left page Urdu's "6,600 km" is drawn
     * "km 6,600" (emulator-5558, 31 October), and an English page's "6,600 km" left to right.
     */
    @Test
    fun t7ALineIsMeasuredInThePagesDirection() {
        fun order(text: String, rtl: Boolean): List<Pair<String, Boolean>> {
            val runs = mutableListOf<Pair<String, Boolean>>()
            GlyphInk.of(text, rtl) { t, start, end, runRtl -> runs += t.substring(start, end) to runRtl; Ink(emptyList(), (end - start).toFloat()) }
            return runs
        }
        assertEquals(listOf("6,600 km" to false), order("6,600 km", rtl = false))
        assertEquals(listOf("km" to false, " " to true, "6,600" to false), order("6,600 km", rtl = true))
        // The space before كم is the right-to-left run's own, drawn at its right.
        assertEquals(listOf(" كم" to true, "٦٬٦٠٠" to false), order("٦٬٦٠٠ كم", rtl = true))
        // A right-to-left page's line in Latin letters alone, and a left-to-right page's in Arabic.
        assertEquals(listOf("N" to false), order("N", rtl = true))
        assertEquals(listOf("كم" to true), order("كم", rtl = false))
    }

    /**
     * T7: the host's ink knows the marks a native reviewer may write into a short form, so that such
     * a form is measured as the watch draws it (Ruling 67 item 2; which files a form touches, by
     * language, is plan 3's Ruling 67 note): a direction mark (left to right, right to left, the
     * Arabic letter mark) is drawn with no ink and no advance, as Android draws one, and only orders
     * the runs around it; and between Manrope's figures and letters a no-break space is a space
     * (beside the system fonts' own, the next test).
     */
    @Test
    fun t7ADirectionMarkHasNoInkAndTakesNoRoom() {
        for (variant in HostInk.Variant.entries) {
            val host = host(variant)
            for (mark in listOf('\u200E', '\u200F', '\u061C')) {
                val alone = host.line("$mark", 600, rtl = false)
                assertEquals(emptyList(), alone.boxes, "U+${"%04X".format(mark.code)}")
                assertEquals(0f, alone.advance, "U+${"%04X".format(mark.code)}")
                val plain = host.line("6,600 km", 600, rtl = false)
                val marked = host.line("6,600$mark km", 600, rtl = false)
                assertEquals(plain.advance, marked.advance, 1e-5f, "U+${"%04X".format(mark.code)}")
                assertEquals(plain.boxes.size, marked.boxes.size, "U+${"%04X".format(mark.code)}")
                plain.boxes.zip(marked.boxes).forEach { (a, b) -> assertEquals(a.left, b.left, 1e-5f); assertEquals(a.right, b.right, 1e-5f); assertEquals(a.top, b.top, 1e-5f) }
            }
            assertEquals(host.line("6,600 km", 600, rtl = false).advance, host.line("6,600\u00A0km", 600, rtl = false).advance, 1e-6f, "a no-break space")
        }
    }

    /**
     * T7: a no-break space is drawn in the font of what stands before it in its run of one
     * direction, as the watch draws it (Ruling 67 item 2's review round 2). U+00A0 is one of
     * minikin's sticky marks, and Noto Naskh Arabic's and Noto Sans Bengali's cmaps have it, so it
     * is Bengali's own (0.26 em) after Bengali digits or letters, and Naskh's (0.221) between
     * Arabic-Indic or Extended digits grouped by it (the bidirectional algorithm keeps a common
     * separator between two numbers in their run) and after Arabic letters, the Arabic letter mark
     * between or not. Where it begins its run it is Manrope's (0.200), as between Arabic-Indic
     * digits and كم on a right-to-left page, where it opens the run of كم, and after a direction
     * mark. Each is held to its parts and the font's own no-break space in both variants and both
     * weights, and, in the elegant fonts emulator-5558 draws, to the watch's own advance (measured
     * there on 31 October with `StaticLayout` at 1,000 px over the app's Manrope SemiBold, in ems).
     * French's narrow no-break space, which no cmap of these has, is the stand-in's quarter em,
     * never narrower than the watch's (0.2485, its next fallback's).
     */
    @Test
    fun t7ANoBreakSpaceIsTheFontOfWhatStandsBeforeItInItsRun() {
        val naskh = 0.221f // Noto Naskh Arabic Bold's U+00A0 (both variants), 452 of 2,048 units, as hb-shape rounds it
        val bengali = 0.26f // Noto Sans Bengali's uni00A0.beng (both variants, every weight), 260 of 1,000
        val manrope = 0.2f
        class Row(val text: String, val rtl: Boolean, val parts: List<String>, val space: Float, val watch: Float?)
        val rows = listOf(
            Row("১০০\u00A0km", rtl = false, listOf("১০০", "km"), bengali, watch = 3.4960f),
            Row("৬\u00A0৬০০", rtl = false, listOf("৬", "৬০০"), bengali, watch = null),
            Row("উ\u00A0পূ", rtl = false, listOf("উ", "পূ"), bengali, watch = 1.7370f),
            Row("٦\u00A0٦٠٠", rtl = true, listOf("٦", "٦٠٠"), naskh, watch = 1.8896f),
            Row("۷\u00A0۹۰۰", rtl = true, listOf("۷", "۹۰۰"), naskh, watch = 1.7837f),
            Row("كم\u00A0١٠٠", rtl = true, listOf("كم", "١٠٠"), naskh, watch = 2.2783f),
            Row("كم\u00A0كم", rtl = true, listOf("كم", "كم"), naskh, watch = 2.2158f),
            Row("كم\u061C\u00A0١٠٠", rtl = true, listOf("كم", "١٠٠"), naskh, watch = 2.2783f),
            Row("١٠٠\u00A0كم", rtl = true, listOf("١٠٠", "كم"), manrope, watch = 2.2576f),
            Row("كم\u200F\u00A0١٠٠", rtl = true, listOf("كم", "١٠٠"), manrope, watch = 2.2576f),
            Row("১০০\u200E\u00A0km", rtl = false, listOf("১০০", "km"), manrope, watch = 3.4360f),
        )
        var held = 0
        var nearest = 0f
        for (variant in HostInk.Variant.entries) for (weight in listOf(600, 800)) {
            val host = host(variant)
            for (row in rows) {
                val what = "'${row.text.map { if (it.code < 0x80 || Character.isLetterOrDigit(it)) "$it" else "U+%04X".format(it.code) }.joinToString("")}', ${variant.name.lowercase()} $weight"
                val line = host.line(row.text, weight, row.rtl)
                val parts = row.parts.map { host.line(it, weight, row.rtl) }
                assertEquals(parts.sumOf { it.advance.toDouble() }.toFloat() + row.space, line.advance, 5e-4f, "$what: its parts and the no-break space it stands in")
                assertEquals(parts.sumOf { it.boxes.size }, line.boxes.size, "$what: its parts' glyphs, and none for the space")
                if (variant == HostInk.Variant.ELEGANT && weight == 600 && row.watch != null) {
                    assertEquals(row.watch, line.advance, 2e-3f, "$what: as emulator-5558 draws it")
                    nearest = maxOf(nearest, abs(row.watch - line.advance))
                }
                held++
            }
            val narrow = host.line("১০০\u202Fkm", weight, rtl = false)
            assertEquals(host.line("১০০", weight, rtl = false).advance + 0.25f + host.line("km", weight, rtl = false).advance, narrow.advance, 5e-4f, "a narrow no-break space is the stand-in's quarter em")
            if (variant == HostInk.Variant.ELEGANT && weight == 600) assertTrue(narrow.advance >= 3.4845f, "never narrower than the watch's (3.4845 em)")
        }
        assertEquals(2 * 2 * rows.size, held)
        println("[measure] T7: ${rows.size} no-break-space lines in both variants and weights; the elegant SemiBold ones within ${"%.4f".format(nearest)} em of emulator-5558's")
    }

    /**
     * T7: a right-to-left run of the system fonts is set from the left in drawing order, its last
     * part first (Ruling 67's left gap: the mutant that set its parts in logical order survived every
     * test, which held only the advance and the glyph count). "ش كم" (a no-break space between) is one
     * right-to-left run, drawn كم, the space, ش from the left; in "كم ١٠٠" the digits are a run of
     * their own on the left, then the space and كم. Each glyph where its parts put it, in both variants.
     */
    @Test
    fun t7ARightToLeftRunIsSetFromTheLeftInDrawingOrder() {
        val naskh = 0.221f // Noto Naskh Arabic Bold's U+00A0, as in the test above
        for (variant in HostInk.Variant.entries) for (weight in listOf(600, 800)) {
            val host = host(variant)
            fun part(text: String, rtl: Boolean) = host.line(text, weight, rtl)
            fun set(vararg parts: Pair<Ink, Float>): List<InkBox> {
                var pen = 0f
                return parts.flatMap { (ink, after) -> ink.boxes.map { it.moved(pen, 0f) }.also { pen += ink.advance + after } }
            }
            val km = part("كم", rtl = true)
            val rows = listOf(
                "ش\u00A0كم" to set(km to naskh, part("ش", rtl = true) to 0f),
                "كم\u00A0١٠٠" to set(part("١٠٠", rtl = true) to naskh, km to 0f),
            )
            for ((text, expected) in rows) {
                val line = host.line(text, weight, rtl = true)
                assertEquals(expected.size, line.boxes.size, "$text, ${variant.name.lowercase()} $weight")
                expected.zip(line.boxes).forEach { (want, got) ->
                    assertEquals(want.left, got.left, 5e-4f, "$text, ${variant.name.lowercase()} $weight: $got where its parts put $want")
                    assertEquals(want.right, got.right, 5e-4f, "$text, ${variant.name.lowercase()} $weight: $got where its parts put $want")
                }
            }
        }
    }

    /**
     * T7: a sticky mark the watch draws in a system font, after that font's letters or digits in one
     * run (its cmap has it: the full stop, the colon, the comma, the exclamation mark and the hyphens in
     * the Naskh fonts; Bengali's those and more), is an error where the model holds no run for it,
     * never Manrope's mark in its place (Ruling 67's left gap); a mark the font lacks is Manrope's, as
     * the watch draws it.
     */
    @Test
    fun t7AStickyMarkTheFontDrawsButTheModelLacksIsAnError() {
        for (variant in HostInk.Variant.entries) {
            val host = host(variant)
            for (text in listOf("كم.", "كم:", "১০০.", "১০০:")) {
                val failure = assertFailsWith<IllegalStateException>(text) { host.line(text, 600, rtl = text.first() == 'ك') }
                assertTrue("qibla-ink.py" in failure.message.orEmpty(), "$text: ${failure.message}")
            }
            // The Naskh fonts' cmap has no '?' (Arabic writes ؟), so after Arabic it is Manrope's.
            val asked = host.line("كم?", 600, rtl = true)
            assertEquals(host.line("كم", 600, rtl = true).advance + host.line("?", 600, rtl = false).advance, asked.advance, 5e-4f)
            // Bengali's comma, which the phone groups its digits with, is in the model.
            host.line("১,০০০", 600, rtl = false)
        }
    }

    /**
     * T7: the space between two Arabic, Urdu or Bengali words is Manrope's, as Android draws it.
     * U+0020 is not one of minikin's sticky marks, and minikin gives a character to the first family
     * that has it, which is Manrope; so a caption is its words, each whole from the model and shaped
     * alone, with Manrope's space between them (0.200 em), never the system font's own (Naskh's
     * 0.221, Bengali's 0.26), which `qibla-ink.py` keeps only for its check that no shaping crosses a
     * space. Every caption the page draws over a detail in those languages, in both variants.
     */
    @Test
    fun t7TheSpaceBetweenTheSystemFontsWordsIsManropes() {
        var held = 0
        for (variant in HostInk.Variant.entries) {
            val host = host(variant)
            val space = host.line(" ", 800, rtl = false)
            assertEquals(0.2f, space.advance, 0.0005f, "Manrope ExtraBold's space")
            assertEquals(emptyList(), space.boxes)
            for ((folder, language) in listOf("values-ar" to "ARABIC", "values-ur" to "URDU", "values-bn" to "BENGALI")) {
                val captions = listOf("qibla_facing_qibla", "qibla_best_effort_title", "qibla_no_sensor_title").map { QiblaSweep.phoneString(folder, it) } +
                    QiblaSweep.watchWord(language, "holdLevel")
                for (caption in captions) {
                    val words = caption.split(' ')
                    if (words.size < 2) continue
                    val rtl = language != "BENGALI"
                    val whole = host.line(caption, 800, rtl)
                    val parts = words.map { host.line(it, 800, rtl) }
                    val set = parts.sumOf { it.advance.toDouble() } + space.advance * (words.size - 1)
                    assertEquals(set.toFloat(), whole.advance, 1e-4f, "$folder '$caption', ${variant.name.lowercase()}: its words and Manrope's space")
                    assertEquals(parts.sumOf { it.boxes.size }, whole.boxes.size, "$folder '$caption', ${variant.name.lowercase()}: each word's glyphs, and none for a space")
                    held++
                }
            }
        }
        assertEquals(2 * 11, held, "the eleven captions of two words or more, in both variants")
    }

    /** The judge's exact gap is the least of a sweep of the headings a twentieth of a degree apart, on the old detail and the new. */
    @Test
    fun theJudgesExactGapIsTheHeadingSweepsLeast() {
        val english = form("en", "west")
        val drawn = letters(english, 1f, HostInk.Variant.UI)
        val host = host(HostInk.Variant.UI)
        for ((text, y) in listOf("Makkah · 6,600 km away" to 155f, "6,600 km" to 155f, "20,000 km" to 156.4f)) {
            val ink = host.line(text, 600, rtl = false).scaled(10f)
            val placed = ink.boxes.map { it.moved(100f - ink.advance / 2, y) }
            val exact = Judge.letterGap(placed, drawn)
            var swept = Double.MAX_VALUE
            for (k in 0 until 7_200) {
                drawn.forEachIndexed { i, letter ->
                    val a = Math.toRadians(i * 90.0 - k * 0.05)
                    val x = (100 + Judge.LETTERS * sin(a)).toFloat()
                    val yy = (100 - Judge.LETTERS * cos(a)).toFloat()
                    for (l in letter) for (g in placed) swept = min(swept, Judge.boxGap(g, l.moved(x, yy)))
                }
            }
            if (exact >= 0) assertEquals(swept, exact, 0.02, "'$text'") else assertTrue(swept <= 0.0, "'$text': $exact exact, $swept swept")
            println("[measure] '$text' at 10, y $y, font 1.0: the least gap to a turning letter ${"%.2f".format(exact)} units (swept ${"%.2f".format(swept)})")
        }
    }

    // ---- Helpers


    /** Every row of the sweep, on the turning dial, the dimmed one, or both. */
    private fun each(dimmed: Boolean? = null, check: (QiblaSweep.Series, Int) -> Unit) {
        var rows = 0
        for (series in SWEEP) {
            if (dimmed != null && (series.bearing != null) != dimmed) continue
            for (i in SCALES.indices) {
                check(series, i)
                rows++
            }
        }
        assertTrue(rows > 0)
    }

    /**
     * T2's table for the device pass (Ruling 66 item 9): each shot's size, baseline and least gap to
     * a turning letter, in each of the system fonts' variants (emulator-5558 draws the elegant
     * Naskh: its glyph bounds and its pixels are the elegant ش's, R66's device turn).
     */
    private fun printTable() {
        for ((language, digits) in listOf("en" to "west", "ar" to "arab", "ar" to "west", "id" to "west", "bn" to "beng", "fr" to "west", "tr" to "west", "ur" to "west", "ur" to "ext", "bn" to "west")) {
            for ((km, variant) in listOf(6_600, 7_900, 8_600, 5_300, 11_000, 20_000).flatMap { km -> QiblaSweep.variants(form(language, digits)).map { km to it } }) {
                val form = form(language, digits)
                val series = SWEEP.single { it.form == form && it.km == km && it.bearing == null && it.wider == 1.0 && it.variant == variant }
                val row = listOf(1f, 1.15f, 1.3f).joinToString(" | ") { f ->
                    val i = SCALES.indexOf(f)
                    "$f: ${series.size[i]} at y ${"%.1f".format(series.y[i])}, gap ${"%.2f".format(series.letterGap[i])}"
                }
                println("[table] $form ${variant.name.lowercase()} '${QiblaSweep.text(form, km, null)}' $row")
            }
        }
    }

    /** The page's layout of [screen] on [feed] at [fontScale], as the page asks for it: the detail's size and baseline, and the caption's box. */
    private fun screenLayout(screen: QiblaScreen, feed: WatchFeed, fontScale: Float): Triple<Float, Float, QiblaType.CaptionBox> {
        val rtl = feed.ui.rtl
        val host = host(HostInk.Variant.UI)
        val letters = { grow: Float ->
            screen.dialLetters.mapIndexed { i, letter ->
                val line = if (i == 0) QiblaType.north else QiblaType.cardinal
                val ink = host.line(letter, if (i == 0) 800 else 600, rtl).scaled(line.units * grow)
                QiblaFit.letterInk(ink, width = ink.advance, penStart = 0f, grow = grow)
            }
        }
        val detail = QiblaFit.detail(screen.captionLine, fontScale, ink = { size -> host.line(screen.detail, 600, rtl).scaled(size) }, letters)
        // The caption's box over a turning dial keeps to the same letters' room (Ruling 68 item 4).
        val glyphs = host.line(screen.caption, 800, rtl).boxes
        val turning = QiblaType.Turning(glyphs.minOf { it.top }, glyphs.maxOf { it.bottom }, letters)
        val box = QiblaType.captionBox(screen.captionLine, fontScale, round = true, turning) { size, width ->
            if (host.line(screen.caption, 800, rtl).advance * size > width) 2 else 1
        }
        return Triple(detail.size, detail.y, box)
    }

    /** The ink a selection's box gives each glyph: from where the glyph before it ends across to its own end, and Manrope's line from its ascent to its descent (hhea 2132 and 600 of 2000). */
    private fun selectionInk(ink: Ink): Ink {
        var left = 0f
        val boxes = ink.boxes.sortedBy { it.left }.map { box -> InkBox(left, max(left, box.right), -2132f / 2000f, 600f / 2000f).also { left = box.right } }
        return Ink(boxes, ink.advance)
    }

    /** How far below its baseline any caption's ink reaches, as a share of its size: ExtraBold over every caption and tilt notice the phone sends in a Latin script, and every digit. */
    private fun captionDescent(): Float {
        val host = host(HostInk.Variant.UI)
        val texts = listOf("values", "values-fr", "values-tr", "values-id").flatMap { folder ->
            CAPTION_KEYS.map { QiblaSweep.phoneString(folder, it).replace(Regex("%\\d\\$[sd]"), "") }
        } + listOf("ENGLISH", "FRENCH", "TURKISH", "INDONESIAN").map { QiblaSweep.watchWord(it, "holdLevel") } + "0123456789"
        return texts.maxOf { t -> host.line(t, 800, rtl = false).boxes.maxOfOrNull { it.bottom } ?: 0f }
    }

    /** How far above its baseline any detail's ink reaches, as a share of its size, in every form and both variants. */
    private fun detailAscent(): Float = FORMS.flatMap { form -> QiblaSweep.variants(form).map { form to it } }.maxOf { (form, variant) ->
        listOf(QiblaSweep.text(form, 20_000, null), QiblaSweep.text(form, 19_000, 359), QiblaSweep.text(form, 8_800, 208)).maxOf { t ->
            -(host(variant).line(t, 600, form.rtl).boxes.minOfOrNull { it.top } ?: 0f)
        }
    }

    /** A caption the page draws over a detail: its line, its text, and whether its dial is dimmed (its detail then the bearing and the distance). */
    private data class Caption(val line: Line, val text: String, val dimmed: Boolean)

    /**
     * [form]'s captions over a detail: the bearing (each of the dimmed sweep's [bearings], 23 and 359
     * among them), "Facing qibla" and the tilt notice over the distance; a given-up compass's two
     * titles over the bearing and the distance. Low's titles have no detail under them.
     */
    private fun captionsOverADetail(form: QiblaSweep.Form, bearings: List<Int>): List<Caption> {
        val bearing = QiblaSweep.phoneString(form.folder, "qibla_bearing")
        return bearings.map { Caption(QiblaType.bearing, QiblaSweep.fill(bearing, QiblaSweep.digits(form, "$it")), dimmed = false) } +
            Caption(QiblaType.facing, QiblaSweep.phoneString(form.folder, "qibla_facing_qibla"), dimmed = false) +
            Caption(QiblaType.level, QiblaSweep.watchWord(QiblaSweep.LANGUAGES.getValue(form.language), "holdLevel"), dimmed = false) +
            listOf("qibla_best_effort_title", "qibla_no_sensor_title").map { Caption(QiblaType.title, QiblaSweep.phoneString(form.folder, it), dimmed = true) }
    }

    /** A caption's last line as the page draws it: its text, and its glyphs' boxes on the dial. */
    private class CaptionLine(val last: String, val boxes: List<InkBox>)

    /**
     * [caption]'s last line under [fontScale], as the page breaks it: in its box
     * ([QiblaType.captionBox], over a turning dial in the letters' room), at its size as TextAutoSize
     * steps it (the largest from the line's floor to the box's size, a half at a time, whose lines fit
     * the box), as Android's simple strategy breaks it there. Its ink is that last line's at the
     * line's largest size, centred across the dial at the caption's last baseline ([QiblaFit.captionY]).
     */
    private fun captionOnTheDial(form: QiblaSweep.Form, variant: HostInk.Variant, caption: Caption, fontScale: Float): CaptionLine {
        val line = caption.line
        // Over a dial that turns, the box keeps to the letters' room (Ruling 68 item 4).
        val turning = if (caption.dimmed) null else host(variant).line(caption.text, 800, form.rtl).boxes.let { glyphs ->
            QiblaType.Turning(glyphs.minOf { it.top }, glyphs.maxOf { it.bottom }) { grow -> letters(form, grow, variant) }
        }
        val box = QiblaType.captionBox(line, fontScale, round = true, turning) { size, width -> wrapped(caption.text, size, width, variant, form.rtl).size }
        val sizes = line.range(fontScale)
        var size = sizes.start
        while (size + STEP <= box.size + EPSILON && wrapped(caption.text, size + STEP, box.width, variant, form.rtl).size <= box.maxLines) size += STEP
        val last = wrapped(caption.text, size, box.width, variant, form.rtl).take(box.maxLines).last()
        val ink = host(variant).line(last, 800, form.rtl).scaled(sizes.endInclusive)
        return CaptionLine(last, ink.centred().map { it.moved(100f, QiblaFit.captionY(line, fontScale)) })
    }

    /** [text]'s ink as the detail draws it at [size], centred across the dial, about its baseline. */
    private fun detailInk(form: QiblaSweep.Form, variant: HostInk.Variant, text: String, size: Float): List<InkBox> =
        detailInks.getOrPut(Triple(text, size, variant to form.rtl)) { host(variant).line(text, 600, form.rtl).scaled(size).centred().map { it.moved(100f, 0f) } }

    /** The room, in units, from the lowest ink of [caption] down to the highest of [detail] set at its baseline [y], across the whole width: below 0 where they would overlap. */
    private fun captionRoom(caption: CaptionLine, detail: List<InkBox>, y: Float): Float =
        detail.minOf { it.top } + y - caption.boxes.maxOf { it.bottom }

    /** [text]'s lines at [size] in [width] units, as Android's simple strategy breaks it, in ExtraBold. */
    private fun wrapped(text: String, size: Float, width: Float, variant: HostInk.Variant = HostInk.Variant.UI, rtl: Boolean = false): List<String> {
        val host = host(variant)
        val out = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && host.line(trial, 800, rtl).advance * size > width) {
                out += line
                line = word
            } else {
                line = trial
            }
        }
        return out + line
    }

    private companion object {
        const val AIR = 1f
        const val EPSILON = 1e-3f

        /** TextAutoSize's step for a caption, in units (`QiblaPage`'s). */
        const val STEP = 0.5f

        /** The details' ink by text, size, font variant and direction, measured once for every caption over them. */
        val detailInks = HashMap<Triple<String, Float, Pair<HostInk.Variant, Boolean>>, List<InkBox>>()

        val CAPTIONS = listOf(QiblaType.bearing, QiblaType.facing, QiblaType.level, QiblaType.title)
        val CAPTION_KEYS = listOf("qibla_bearing", "qibla_facing_qibla", "qibla_needs_calibrating", "qibla_interference_title", "qibla_best_effort_title", "qibla_no_sensor_title")

        val SAMPLES: List<Pair<String, WatchFeed>> by lazy {
            listOf("en" to TestFeeds.sample, "ar-sa" to TestFeeds.other("ar-sa"), "ar-ly12" to TestFeeds.other("ar-ly12"), "en12" to TestFeeds.other("en12"))
        }
    }
}
