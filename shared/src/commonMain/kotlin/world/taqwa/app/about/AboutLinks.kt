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

    /** The site is bilingual; open the half the app is already speaking. */
    fun website(arabic: Boolean): String = if (arabic) WEBSITE + "ar/" else WEBSITE
}
