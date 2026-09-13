package world.taqwa.app.about

/**
 * Where the About screen's three links go (privacy spec §6.3). One place to change when the
 * repository goes public under another name or taqwa.world exists. They resolve only once the
 * Taqwa repository is public — a release-day step, not a code one.
 */
object AboutLinks {
    const val PRIVACY_POLICY = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md"
    const val SOURCE = "https://github.com/MohamedAbulgasem/Taqwa"
    const val LICENCE = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/LICENSE"
}
