package world.taqwa.app.about

/**
 * Where the About screen's three links go (privacy spec §6.3). One place to change when the
 * repository moves or taqwa.world hosts the policy. The repository has been public since
 * 13 September 2026, so all three resolve; the store forms carry the first one.
 */
object AboutLinks {
    const val PRIVACY_POLICY = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md"
    const val SOURCE = "https://github.com/MohamedAbulgasem/Taqwa"
    const val LICENCE = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/LICENSE"
}
