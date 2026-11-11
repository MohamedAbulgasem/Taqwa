package world.taqwa.app.wear.qibla

import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The cases Ruling 66 item 6 sweeps, laid out once for every test that reads them ([SWEEP]).
 *
 * Every figure the phone sends from 100 km (two significant figures, `WatchFeedBuilder.roundedKm`),
 * in all seven languages and each digit set the phone writes them in, the mark between thousands
 * each one takes (`DigitGrouping`), on the turning dial (the distance alone, `distanceOnDial`) and
 * the dimmed one (with the bearings whose ink reaches nearest the ticks, [bearings], and
 * `qiblaDetailOnDial`), at every font size from 0.85 to 2.0 in hundredths, measured as the host
 * measures and 3 % wider, in the system fonts' compact and elegant variants where Arabic and
 * Bengali are drawn: 48,896 series of 116 font sizes, 5,671,936 rows, 708,992 of them on the
 * turning dial. Each is laid out as the page lays it out ([layout]) and judged by the test's own
 * geometry ([Judge]), against the letters as drawn at its font size.
 */
internal object QiblaSweep {

    /** How the phone writes a distance in one of its languages: the folder, the language, its digits, the mark between thousands (`DigitGrouping.separator`), and the page's direction. */
    data class Form(val folder: String, val language: String, val digits: String, val mark: Char, val rtl: Boolean) {
        override fun toString() = "$language/${DIGIT_NAMES.getValue(digits)}"
    }

    /** One layout: a form, a figure, the bearing on a dimmed dial (null on a turning one), the font size, how much wider it is measured, and the system fonts' variant. */
    data class Case(val form: Form, val km: Int, val bearing: Int?, val fontScale: Float, val wider: Double, val variant: HostInk.Variant) {
        val dimmed get() = bearing != null
        override fun toString() = "$form '${text(form, km, bearing)}' at font size $fontScale${if (wider > 1) ", 3 % wider" else ""}, ${variant.name.lowercase()} fonts"
    }

    /** A detail as the page lays it out: its size, its lines, its baseline, whether it is cut, and its glyphs' ink on the dial (units from the screen's top left, y down). */
    data class Laid(val size: Float, val lines: Int, val y: Float, val cut: Boolean, val ink: List<InkBox>) {
        override fun toString() = "(size $size, $lines line${if (lines > 1) "s" else ""}, y ${"%.1f".format(y)}${if (cut) ", cut" else ""})"
    }

    /** One figure's layouts across the font sizes, with what the judge found: the least gap to a turning letter as drawn (NaN on a dimmed dial) and to the ticks. */
    class Series(val form: Form, val km: Int, val bearing: Int?, val wider: Double, val variant: HostInk.Variant) {
        val size = FloatArray(SCALES.size)
        val lines = IntArray(SCALES.size)
        val cut = BooleanArray(SCALES.size)
        val y = FloatArray(SCALES.size)
        val letterGap = FloatArray(SCALES.size)
        val tickGap = FloatArray(SCALES.size)
        fun case(i: Int) = Case(form, km, bearing, SCALES[i], wider, variant)
        fun row(i: Int) = "${case(i)} (size ${size[i]}, ${lines[i]} line(s), y ${"%.1f".format(y[i])}${if (cut[i]) ", cut" else ""}, " +
            "letters ${"%.2f".format(letterGap[i])}, ticks ${"%.2f".format(tickGap[i])})"
    }

    /**
     * The page's layout of [case] (Ruling 66 items 2 to 4): the short form, one line, its size on the
     * 0.5 grid against the letters' glyph ink (`QiblaFit.detail`). The one place the sweep asks the
     * page, so the same tests could judge the layout before Ruling 66 (965c06b5's).
     */
    fun layout(case: Case): Laid {
        val text = text(case.form, case.km, case.bearing)
        val ink = host(case.variant).line(text, 600, case.form.rtl)
        val caption = if (case.dimmed) QiblaType.title else QiblaType.bearing
        val detail = QiblaFit.detail(caption, case.fontScale, ink = { size -> ink.scaled(size).wider(case.wider) }) { grow ->
            if (case.dimmed) emptyList() else letters(case.form, grow, case.variant)
        }
        return Laid(detail.size, lines = 1, y = detail.y, cut = false, ink = detail.ink)
    }

    /** Every case, laid out once and judged, the series in order of form, variant, bearing, figure and widening. */
    val SWEEP: List<Series> by lazy {
        val out = ArrayList<Series>()
        for (form in FORMS) for (variant in variants(form)) for (bearing in listOf<Int?>(null) + bearings(form, variant)) {
            for (km in FIGURES) for (wider in WIDER) {
                val series = Series(form, km, bearing, wider, variant)
                val byScale = HashMap<Float, Laid>()
                SCALES.forEachIndexed { i, fontScale ->
                    // Below 1.0 and above 1.3 every measure is capped, so each is laid out once.
                    val capped = fontScale.coerceIn(1f, 1.3f)
                    val laid = byScale.getOrPut(capped) { layout(Case(form, km, bearing, capped, wider, variant)) }
                    series.size[i] = laid.size
                    series.lines[i] = laid.lines
                    series.cut[i] = laid.cut
                    series.y[i] = laid.y
                    series.letterGap[i] = if (bearing != null) Float.NaN else Judge.letterGap(laid.ink, letters(form, grow(fontScale), variant)).toFloat()
                    series.tickGap[i] = Judge.tickGap(laid.ink).toFloat()
                }
                out += series
            }
        }
        out
    }

    fun variants(form: Form): List<HostInk.Variant> =
        if (form.language in listOf("ar", "ur", "bn")) HostInk.Variant.entries else listOf(HostInk.Variant.UI)

    /**
     * The bearings a dimmed dial's detail is swept with: the five whose measured ink reaches nearest
     * the ticks in [form]'s digits ([bearingReach], as T3 judges a line; the rest of the line is the
     * same, so these make the widest lines), the sample's 23 and the last, 359. By the ink, not the
     * advance (Ruling 67 item 2): in Bengali digits every bearing of three figures has one advance,
     * and the advance took 100 to 104, where ২০০° reaches nearer, its ২ beginning nearer its pen and
     * running below the baseline.
     */
    fun bearings(form: Form, variant: HostInk.Variant): List<Int> =
        ((0..359).sortedBy { bearingReach(form, variant, it) }.take(5) + 23 + 359).distinct()

    /**
     * How far inside the ticks a dimmed dial's detail with [bearing] keeps, as T3 judges it: the
     * farthest figure the phone sends, 20,000 km, at the drawing's size, where the page places it.
     */
    fun bearingReach(form: Form, variant: HostInk.Variant, bearing: Int): Double {
        val ink = host(variant).line(text(form, 20_000, bearing), 600, form.rtl).scaled(QiblaType.detail.units)
        return Judge.tickGap(QiblaFit.placed(QiblaType.title, 1f, QiblaType.detail.units, ink, emptyList()).ink)
    }

    // ---- The words

    /** The detail [form]'s phone sends: the distance alone on a turning dial, or the bearing and the distance on a dimmed one, in its short forms (`WatchWords.kt`). */
    fun text(form: Form, km: Int, bearing: Int?): String =
        if (bearing == null) fill(shortForm(form.language, "distanceOnDial"), figure(form, km))
        else fill(shortForm(form.language, "qiblaDetailOnDial"), digits(form, "$bearing"), figure(form, km))

    fun fill(template: String, vararg values: String) = values.foldIndexed(template) { i, t, v -> t.replace("%${i + 1}\$s", v) }

    /** [km] as [form] writes it: its digits, grouped from 1,000 with its mark. */
    fun figure(form: Form, km: Int): String = digits(form, if (km < 1_000) "$km" else "${km / 1_000}${form.mark}${"%03d".format(km % 1_000)}")

    fun digits(form: Form, text: String): String = text.map { if (it in '0'..'9') form.digits[it - '0'] else it }.joinToString("")

    /** The dial's short forms in [language] (`en` and the rest), as `WatchWords.kt` writes them for the feed. */
    fun shortForm(language: String, field: String): String = SHORT_FORMS.getValue(language).getValue(field)

    // ---- The letters

    /** The dial's letters in [form]'s language, north first (the phone's `qibla_cardinal_*`), each its ink about its middle at [grow], as the page draws it. */
    fun letters(form: Form, grow: Float, variant: HostInk.Variant): List<List<InkBox>> = letterCache.getOrPut(Triple(form, grow, variant)) {
        cardinals(form.folder).mapIndexed { i, letter ->
            val line = if (i == 0) QiblaType.north else QiblaType.cardinal
            val ink = host(variant).line(letter, if (i == 0) 800 else 600, form.rtl).scaled(line.units * grow)
            QiblaFit.letterInk(ink, width = ink.advance, penStart = 0f, grow = grow)
        }
    }

    private val letterCache = HashMap<Triple<Form, Float, HostInk.Variant>, List<List<InkBox>>>()

    fun cardinals(folder: String): List<String> = listOf("north", "east", "south", "west").map { phoneString(folder, "qibla_cardinal_$it") }

    /** How far the wearer's font size grows the dial's letters (they never shrink). */
    fun grow(fontScale: Float) = QiblaType.north.range(fontScale).endInclusive / QiblaType.north.units

    // ---- The constants

    private val hosts = HashMap<HostInk.Variant, HostInk>()
    fun host(variant: HostInk.Variant): HostInk = hosts.getOrPut(variant) { HostInk(variant) }

    const val WESTERN = "0123456789"
    const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
    const val EXTENDED = "۰۱۲۳۴۵۶۷۸۹"
    const val BENGALI = "০১২৩৪৫৬৭৮৯"
    val DIGIT_NAMES = mapOf(WESTERN to "Western", ARABIC_INDIC to "Arabic-Indic", EXTENDED to "Extended", BENGALI to "Bengali")

    /** Each language with each digit set the phone writes it in, and the mark it groups with there (`DigitGrouping.separator`). */
    val FORMS = listOf(
        Form("values", "en", WESTERN, ',', rtl = false),
        Form("values-fr", "fr", WESTERN, ' ', rtl = false),
        Form("values-tr", "tr", WESTERN, '.', rtl = false),
        Form("values-id", "id", WESTERN, '.', rtl = false),
        Form("values-ar", "ar", ARABIC_INDIC, '٬', rtl = true),
        Form("values-ar", "ar", WESTERN, ',', rtl = true),
        Form("values-ur", "ur", WESTERN, ',', rtl = true),
        Form("values-ur", "ur", EXTENDED, '٬', rtl = true),
        Form("values-bn", "bn", BENGALI, ',', rtl = false),
        Form("values-bn", "bn", WESTERN, ',', rtl = false),
    )

    /** [language] in the digits whose name begins with [digits] ("west", "arab", "ext", "beng"). */
    fun form(language: String, digits: String): Form =
        FORMS.single { it.language == language && DIGIT_NAMES.getValue(it.digits).lowercase().startsWith(digits) }

    /** Every distance the phone sends from 100 km, to two significant figures: tens to 990, hundreds to 9,900, thousands to 20,000. */
    val FIGURES = (100..990 step 10) + (1_000..9_900 step 100) + (10_000..20_000 step 1_000)

    /** Every font size from 0.85 to 2.0 in hundredths. */
    val SCALES: List<Float> = (85..200).map { (it / 100.0).toFloat() }

    /** Wear OS's own font sizes above 1.0, which the sweep holds by name. */
    val WEAR = listOf(1.06f, 1.12f, 1.18f, 1.24f)

    /** Each line measured as the host measures it, and 3 % wider about its middle. */
    val WIDER = listOf(1.0, 1.03)

    private val phoneStrings = File(System.getProperty("taqwa.sharedStrings") ?: "../shared/src/commonMain/composeResources")
    private val watchWords = File(System.getProperty("taqwa.watchWords") ?: "../shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchWords.kt")

    fun phoneString(folder: String, key: String): String {
        val xml = File(File(phoneStrings, folder), "strings.xml").readText()
        return Regex("""<string name="$key">(.*?)</string>""").find(xml)?.groupValues?.get(1)?.replace("\\'", "'") ?: error("$folder has no $key")
    }

    /** [language]'s block of `WatchWords.SCREENS` (`ENGLISH` and the rest). */
    fun watchWordsBlock(language: String): String {
        val text = watchWords.readText()
        val screens = text.substring(text.indexOf("private val SCREENS"))
        val start = screens.indexOf("UiLanguage.$language to WatchScreenWords(")
        check(start >= 0) { "no $language in WatchWords.SCREENS" }
        val end = screens.indexOf("UiLanguage.", start + 1).let { if (it < 0) screens.length else it }
        return screens.substring(start, end)
    }

    /**
     * A field of [language]'s block of `WatchWords.SCREENS`, as the feed carries it: a `\$` is a
     * dollar, and a `\u` escape (a direction mark or a no-break space a reviewer writes so) is the
     * character it writes, as Kotlin reads them.
     */
    fun watchWord(language: String, field: String): String =
        Regex("""$field = "([^"]*)"""").find(watchWordsBlock(language))?.groupValues?.get(1)
            ?.replace("\\$", "$")
            ?.replace(Regex("""\\u([0-9A-Fa-f]{4})""")) { it.groupValues[1].toInt(16).toChar().toString() }
            ?: error("no $field for $language")

    val LANGUAGES = mapOf("en" to "ENGLISH", "fr" to "FRENCH", "tr" to "TURKISH", "id" to "INDONESIAN", "ar" to "ARABIC", "ur" to "URDU", "bn" to "BENGALI")

    private val SHORT_FORMS: Map<String, Map<String, String>> by lazy {
        LANGUAGES.mapValues { (_, name) -> listOf("distanceOnDial", "qiblaDetailOnDial").associateWith { watchWord(name, it) } }
    }

    init {
        check(WEAR.all { w -> SCALES.any { abs(it - w) < 1e-6f } }) { "Wear OS's font sizes in the sweep" }
        check(SCALES.first() == 0.85f && SCALES.last() == 2f && SCALES.size == 116)
    }
}

/**
 * The test's own geometry, written apart from the page's ([QiblaFit]): the dial's letters' middles
 * turn on a circle 72.05 units from the middle (`qiblaDial`'s 19 units inside the 92 rim, drawn 1.05
 * times over), and its ticks begin 82.55 out (9 units in).
 *
 * The least gap, over every heading, between a glyph's box G of the detail and a letter's glyph box
 * L about the letter's middle is exact: the letter touches G wherever its middle lies in G − L (the
 * box of every g − l), so the gap is how far the letters' circle passes from that box: 72.05 less
 * its farthest corner where the box lies inside the circle, its nearest point less 72.05 where it
 * lies outside, and below 0 where the circle crosses it. `QiblaFitTest` holds it to a sweep of the
 * headings a twentieth of a degree apart.
 */
internal object Judge {
    const val LETTERS = 92.0 - 19 * 1.05
    const val TICKS = 92.0 - 9 * 1.05

    fun letterGap(detail: List<InkBox>, letters: List<List<InkBox>>): Double {
        var least = Double.MAX_VALUE
        for (letter in letters) for (l in letter) for (g in detail) least = min(least, pairGap(g, l))
        return least
    }

    fun tickGap(detail: List<InkBox>): Double {
        var far = 0.0
        for (b in detail) for (x in listOf(b.left, b.right)) for (y in listOf(b.top, b.bottom)) far = max(far, length(x - 100.0, y - 100.0))
        return TICKS - far
    }

    /** The gap over the circle between [g] (on the dial) and [l] (about a letter's middle): see [Judge]. */
    fun pairGap(g: InkBox, l: InkBox): Double {
        val x0 = g.left - l.right - 100.0
        val x1 = g.right - l.left - 100.0
        val y0 = g.top - l.bottom - 100.0
        val y1 = g.bottom - l.top - 100.0
        val far = max(max(length(x0, y0), length(x0, y1)), max(length(x1, y0), length(x1, y1)))
        val near = length(if (x0 > 0) x0 else if (x1 < 0) x1 else 0.0, if (y0 > 0) y0 else if (y1 < 0) y1 else 0.0)
        return when {
            far < LETTERS -> LETTERS - far
            near > LETTERS -> near - LETTERS
            else -> -min(far - LETTERS, LETTERS - near)
        }
    }

    /** How far apart two boxes are: 0 or less where they meet. */
    fun boxGap(a: InkBox, b: InkBox): Double {
        val dx = max(a.left - b.right, b.left - a.right).toDouble()
        val dy = max(a.top - b.bottom, b.top - a.bottom).toDouble()
        return if (dx < 0 && dy < 0) max(dx, dy) else length(max(dx, 0.0), max(dy, 0.0))
    }

    private fun length(x: Double, y: Double) = sqrt(x * x + y * y)
}

/** [this] measured [k] times wider about its middle: every x and the advance scaled, so the middle of the advance stays the middle. */
internal fun Ink.wider(k: Double): Ink =
    if (k == 1.0) this else Ink(boxes.map { InkBox(it.left * k.toFloat(), it.right * k.toFloat(), it.top, it.bottom) }, advance * k.toFloat())
