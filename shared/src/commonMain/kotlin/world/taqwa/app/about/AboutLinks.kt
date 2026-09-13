package world.taqwa.app.about

/**
 * Where the About screen's links go (privacy spec §6.3). One place to change when the
 * repository moves or taqwa.world hosts the policy. The repository has been public since
 * 13 September 2026, so all of them resolve; the store forms carry the policy one.
 */
object AboutLinks {
    const val WEBSITE = "https://taqwa.world/"
    const val PRIVACY_POLICY = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md"
    const val SOURCE = "https://github.com/MohamedAbulgasem/Taqwa"
    const val LICENCE = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/LICENSE"

    /** The site speaks the app's seven languages, English at the root; open the one the app is in. */
    fun website(languageCode: String): String = if (languageCode == "en") WEBSITE else "$WEBSITE$languageCode/"
}
