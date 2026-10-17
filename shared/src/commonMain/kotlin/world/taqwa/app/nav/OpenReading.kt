package world.taqwa.app.nav

/**
 * Lands the reader on a reading screen from anywhere: the ayah widget's tap, the media
 * notification's "open the ayah being recited". [target] is a [Screen.Reader] or a
 * [Screen.Mushaf], already resolved by the caller.
 *
 * The rule that matters is where the Quran root ends up: at the **bottom** of the stack.
 * [Navigator.currentTab] is read from the bottom, and the player bar (spec 3a §5.3) is drawn only
 * while that tab is current — so a reader pushed on top of the Prayer tab, Quran root and all,
 * played its surah with no bar until the tab was left and re-entered. From another tab, or from a
 * stack shaped that way, the stack is therefore replaced with the Quran root first.
 *
 * On the Quran tab itself: a reading screen already on top is replaced rather than stacked (a
 * second widget tap, the same rule as the mode toggle), the root gets the reader pushed on it, and
 * a sub-screen (the reciter's downloads, say) gets the root pushed over it first so that one Back
 * from the reader reaches the surah list the ayah came from (D3, S23 round).
 */
fun Navigator.openReading(target: Screen) {
    val onQuran = currentTab == Tab.QURAN
    val top = current
    if (onQuran && (top is Screen.Reader || top is Screen.Mushaf)) {
        replace(target)
        return
    }
    if (!onQuran) selectTab(Tab.QURAN)
    if (current != Screen.Quran) push(Screen.Quran)
    push(target)
}

/**
 * The voice moving on from surah [before] to [now] (spec §15.1), by playing on or by a skip from
 * the bar, the lock screen or a headset: a reader on the surah that was playing is moved to the
 * one now playing, at its first ayah, and follows it from the moment it opens, as a reader the
 * voice opened does ([Screen.Reader.followVoice]). The voice moved it there, not a touch, and the
 * short first ayahs of Juz ʿAmma would otherwise go unfollowed for the four seconds. A reader on
 * any other surah, and the Mushaf, which follows by page on its own, are left where they are.
 */
fun Navigator.followSurahSkip(before: Int, now: Int) {
    val top = current
    if (top is Screen.Reader && top.surah == before) replace(Screen.Reader(now, 1, followVoice = true))
}
