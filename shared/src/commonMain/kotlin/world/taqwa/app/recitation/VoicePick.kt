package world.taqwa.app.recitation

/**
 * One voice as a platform describes it, reduced to what the choice needs (read-aloud spec §4).
 * [language] and [country] are as the platform reports them ("in", "ind", "ar-001" all happen).
 */
data class VoiceCandidate(
    val id: String,
    val language: String,
    val country: String,
    val offline: Boolean,
    val installed: Boolean,
    val quality: Int,
)

/** The choice of voice, shared by both platforms so one test holds it. */
object VoicePick {

    /**
     * The best offline, installed voice for [language], on both platforms: the highest quality,
     * then the preferred country, then — between voices the engine rates alike — one whose id
     * ends in `-local`, then the id, so the choice never rests on the order an engine lists in.
     *
     * The `-local` step is for Google's engine, whose on-phone voices are named that way
     * (`en-us-x-iol-local`) beside aliases such as `en-US-language` that also report themselves
     * offline: a voice whose own name says it runs on the phone is the safer of two equals.
     */
    fun best(language: String, candidates: List<VoiceCandidate>): VoiceCandidate? {
        val wanted = normalize(language)
        val countries = PREFERRED[wanted].orEmpty()
        return candidates
            .filter { normalize(it.language) == wanted && it.offline && it.installed }
            .sortedWith(
                compareByDescending<VoiceCandidate> { it.quality }
                    .thenBy { voice -> countries.indexOf(voice.country.uppercase()).let { if (it < 0) countries.size else it } }
                    .thenBy { if (it.id.endsWith(LOCAL_SUFFIX, ignoreCase = true)) 0 else 1 }
                    .thenBy { it.id },
            )
            .firstOrNull()
    }

    /** True when [language] is offered but not yet usable offline: a download away. A
     * network-only voice has nothing to download, so it does not count. */
    fun downloadable(language: String, candidates: List<VoiceCandidate>): Boolean {
        val wanted = normalize(language)
        return candidates.any { normalize(it.language) == wanted && it.offline && !it.installed }
    }

    /** Legacy and three-letter codes, and region suffixes, to the database's two letters. */
    fun normalize(language: String): String =
        when (val code = language.lowercase().substringBefore('-').substringBefore('_')) {
            "in", "ind" -> "id"
            "eng" -> "en"
            "ara" -> "ar"
            "fra", "fre" -> "fr"
            "tur" -> "tr"
            "urd" -> "ur"
            "ben" -> "bn"
            else -> code
        }

    /** How Google's engine names a voice that synthesizes on the phone. */
    private const val LOCAL_SUFFIX = "-local"

    private val PREFERRED = mapOf(
        "en" to listOf("US", "GB", "AU", "IE", "ZA", "IN"),
        "fr" to listOf("FR", "CA", "BE", "CH"),
        "ar" to listOf("001", "SA", "EG", "XA"),
        "bn" to listOf("BD", "IN"),
        "ur" to listOf("PK", "IN"),
        "tr" to listOf("TR"),
        "id" to listOf("ID"),
    )
}
