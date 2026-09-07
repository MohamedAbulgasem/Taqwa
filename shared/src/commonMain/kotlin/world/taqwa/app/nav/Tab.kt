package world.taqwa.app.nav

/**
 * The destinations the bottom bar switches between. Each owns exactly one root [Screen];
 * everything else in [Screen] is a child pushed on top of whichever root is showing.
 *
 * Three tabs now. Qibla was a tab for one iteration and is now a card on the Prayer screen
 * (iteration 8), with the compass itself pushed from there; the Quran tab arrived with slice 2,
 * sitting between Prayer and Settings. [PRAYER]'s root is still [Screen.Today] in code: only the
 * label changed, and renaming the screen, its view model and its tests would be churn for a word.
 *
 * There is deliberately no per-tab back stack. Sub-screens are one or two deep and always end in
 * a decision the user then wants to see reflected on the Prayer screen, so remembering that
 * Settings was three levels down when they last left it would be a nuisance, not a courtesy.
 * [Navigator.selectTab] therefore replaces the stack outright.
 */
enum class Tab(val root: Screen) {
    PRAYER(Screen.Today),
    QURAN(Screen.Quran),
    SETTINGS(Screen.Settings),
}

/**
 * True for exactly the tab roots. The tab bar is drawn only under these; a pushed sub-screen
 * keeps the full-screen layout and the back link it already had.
 */
fun isTabRoot(screen: Screen): Boolean = tabOf(screen) != null

/** The tab [screen] is the root of, or null when it is a pushed child (or onboarding). */
fun tabOf(screen: Screen): Tab? = Tab.entries.firstOrNull { it.root == screen }
