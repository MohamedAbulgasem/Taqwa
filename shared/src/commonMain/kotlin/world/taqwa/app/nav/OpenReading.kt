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
