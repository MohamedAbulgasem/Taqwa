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

    /** The best offline, installed voice for [language]: highest quality, then the preferred country. */
    fun best(language: String, candidates: List<VoiceCandidate>): VoiceCandidate? {
        val wanted = normalize(language)
        val countries = PREFERRED[wanted].orEmpty()
        return candidates
            .filter { normalize(it.language) == wanted && it.offline && it.installed }
            .sortedWith(
                compareByDescending<VoiceCandidate> { it.quality }
                    .thenBy { voice -> countries.indexOf(voice.country.uppercase()).let { if (it < 0) countries.size else it } }
                    .thenBy { it.id },
            )
            .firstOrNull()
    }

    /** True when [language] is offered but not yet usable offline: a download away. */
    fun downloadable(language: String, candidates: List<VoiceCandidate>): Boolean {
        val wanted = normalize(language)
        return candidates.any { normalize(it.language) == wanted && !(it.offline && it.installed) }
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
