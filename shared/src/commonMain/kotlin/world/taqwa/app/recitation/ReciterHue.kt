package world.taqwa.app.recitation

/**
 * The colour a reciter's monogram disc is drawn in — one hue per reciter, so the ten rows of the
 * picker are told apart by colour as much as by name, and so the 40 dp disc on the player bar says
 * whose voice is playing at a glance (spec 3a §3, §5.5).
 *
 * Two colours per theme: a **deep tinted disc** and a **luminous glyph** on it. They are the amber
 * family's own recipe — Taqwa's accent is a deep amber with a bright ring — walked around the wheel
 * and kept at the app's muted, earthy saturation, so nine other hues can sit beside amber without
 * any of them reading as a different app.
 *
 * These are ARGB literals, which everywhere else in the tree live only in `design/Palette.kt` and
 * `widget/WidgetPalette.kt`. They are here instead because they are not theme roles: they are per
 * reciter, chosen by the manifest (a reciter added later brings its hue name with it), and a
 * `TaqwaColors` field per reciter would be the wrong shape entirely. `Long`, not `Color`, so the
 * enum stays free of Compose and can be read by anything.
 */
enum class ReciterHue(
    val discLight: Long,
    val glyphLight: Long,
    val discDark: Long,
    val glyphDark: Long,
) {
    AMBER(0xFF7A5606, 0xFFF6DFA6, 0xFF4A3406, 0xFFF0B429),
    MOSS(0xFF3F5B2E, 0xFFDDEBC9, 0xFF253619, 0xFFB7D095),
    PLUM(0xFF5A3050, 0xFFEDD3E8, 0xFF361D30, 0xFFC79BBE),
    CLAY(0xFF7A3F26, 0xFFF7DDCE, 0xFF482516, 0xFFD59B79),
    SKY(0xFF2C4E66, 0xFFD3E6F3, 0xFF1A2F3D, 0xFF95C0DC),
    OLIVE(0xFF5A5A1E, 0xFFECEBC5, 0xFF363611, 0xFFC3C286),
    ROSE(0xFF75304A, 0xFFF7D5E1, 0xFF461C2C, 0xFFD494AB),
    TEAL(0xFF1F5350, 0xFFCDE9E6, 0xFF123130, 0xFF8CC9C4),
    SAND(0xFF6E5B34, 0xFFF2E6CB, 0xFF42361F, 0xFFD3BE8C),
    SLATE(0xFF3C4650, 0xFFDDE3E9, 0xFF232A31, 0xFF9FAEBB),
    ;

    /** The disc colour for the theme in force. */
    fun disc(dark: Boolean): Long = if (dark) discDark else discLight

    /** The glyph colour for the theme in force. */
    fun glyph(dark: Boolean): Long = if (dark) glyphDark else glyphLight

    companion object {
        /**
         * The hue named in the manifest, matched case-insensitively; **amber for anything else**,
         * because a hue this build has never heard of arrives from a manifest written for a later
         * one, and a reciter with no disc at all would be worse than a reciter in the app's own
         * colour.
         */
        fun of(name: String): ReciterHue =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AMBER
    }
}

/**
 * The launch set (spec 3a §12.1): the ten reciters the bundled manifest is expected to carry, in
 * the order the picker shows them, with the two facts the app decides rather than measures — the
 * inter-ayah gap and the hue.
 *
 * This is **not** a second copy of the catalogue. Names, bitrates, sizes and hashes come from the
 * manifest alone, and a reciter added or withdrawn there needs no change here. It exists so that
 * the ten launch ids are written down once in the app: to give the hue table something to be
 * checked against, and to order a manifest that ever comes back in a different order.
 */
object Reciters {
    data class Launch(val id: String, val gapMs: Int, val hue: ReciterHue)

    val LAUNCH: List<Launch> = listOf(
        Launch("ar.alafasy", 300, ReciterHue.AMBER),
        Launch("ar.abdulbasitmurattal", 80, ReciterHue.MOSS),
        Launch("ar.mahermuaiqly", 300, ReciterHue.PLUM),
        Launch("ar.husary", 80, ReciterHue.CLAY),
        Launch("ar.minshawi", 80, ReciterHue.SKY),
        Launch("ar.abdurrahmaansudais", 300, ReciterHue.OLIVE),
        Launch("ar.saoodshuraym", 300, ReciterHue.ROSE),
        Launch("ar.shaatree", 80, ReciterHue.TEAL),
        Launch("ar.hudhaify", 80, ReciterHue.SAND),
        Launch("ar.ahmedajamy", 80, ReciterHue.SLATE),
    )

    val IDS: List<String> = LAUNCH.map { it.id }
}

/**
 * The single Arabic letter the monogram disc carries: the first letter of the reciter's **given**
 * name, not of the family name the English word order puts last and not of the definite article.
 * Mishary → «م», Abdul Basit → «ع», Saud → «س», Abu Bakr → «أ».
 *
 * The ten are written out rather than derived, because a name is not a string problem: «عبد
 * الباسط» is two words for one given name, «أبو بكر» likewise, and a rule clever enough to know
 * that is a rule that will one day be wrong about a name nobody tested. The general rule below is
 * only for a reciter who arrives by manifest after this build shipped — first word, article
 * stripped — which happens to give the same answer for all ten, and that is the point of the
 * table: it pins the answer even if the rule is later changed.
 */
fun monogramInitial(nameAr: String): String {
    TABLE[nameAr.trim()]?.let { return it }
    val first = nameAr.trim().split(' ', '\u00A0').firstOrNull { it.isNotBlank() } ?: return ""
    val bare = if (first.length > 2 && first.startsWith(ARTICLE)) first.removePrefix(ARTICLE) else first
    return bare.take(1)
}

/** The Arabic name exactly as the manifest publishes it, to the letter drawn on the disc. */
private val TABLE: Map<String, String> = mapOf(
    "مشاري راشد العفاسي" to "م",     // Mishary Rashid Alafasy
    "عبد الباسط عبد الصمد" to "ع",   // Abdul Basit Abdus-Samad
    "ماهر المعيقلي" to "م",          // Maher Al Muaiqly
    "محمود خليل الحصري" to "م",      // Mahmoud Khalil Al-Husary
    "محمد صديق المنشاوي" to "م",     // Mohamed Siddiq Al-Minshawi
    "عبد الرحمن السديس" to "ع",      // Abdur-Rahman As-Sudais
    "سعود الشريم" to "س",            // Saud Ash-Shuraim
    "أبو بكر الشاطري" to "أ",        // Abu Bakr Ash-Shatri
    "علي الحذيفي" to "ع",            // Ali Al-Hudhaify
    "أحمد العجمي" to "أ",            // Ahmed Al-Ajmi
)

private const val ARTICLE = "ال"
