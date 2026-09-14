package world.taqwa.app.about

/**
 * Where the About screen's links go (privacy spec §6.3). One place to change when the
 * repository moves or taqwa.world hosts the policy. The repository has been public since
 * 13 September 2026, so all of them resolve; the store forms carry the policy one.
 */
object AboutLinks {
    /** Where "Report a problem" and the crash sheet address their email; the site and the policy say the same. */
    const val SUPPORT_EMAIL = "support@taqwa.world"
    const val WEBSITE = "https://taqwa.world/"
    const val PRIVACY_POLICY = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md"
    const val SOURCE = "https://github.com/MohamedAbulgasem/Taqwa"
    const val LICENCE = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/LICENSE"

    /** The site speaks the app's seven languages, English at the root; open the one the app is in. */
    fun website(languageCode: String): String = if (languageCode == "en") WEBSITE else "$WEBSITE$languageCode/"

    /** The same policy as [PRIVACY_POLICY], rendered by the site in the app's language. */
    fun privacyPolicy(languageCode: String): String = website(languageCode) + "privacy/"
}
