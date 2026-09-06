package world.taqwa.app.nav

/**
 * The three destinations the bottom bar switches between. Each owns exactly one root [Screen];
 * everything else in [Screen] is a child pushed on top of whichever root is showing.
 *
 * There is deliberately no per-tab back stack. Sub-screens are one or two deep and always end in
 * a decision the user then wants to see reflected on Today, so remembering that Settings was
 * three levels down when they last left it would be a nuisance, not a courtesy. [Navigator
 * .selectTab] therefore replaces the stack outright.
 */
enum class Tab(val root: Screen) {
    TODAY(Screen.Today),
    QIBLA(Screen.Qibla),
    SETTINGS(Screen.Settings),
}

/**
 * True for exactly the three tab roots. The tab bar is drawn only under these; a pushed
 * sub-screen keeps the full-screen layout and the back link it already had.
 */
fun isTabRoot(screen: Screen): Boolean = tabOf(screen) != null

/** The tab [screen] is the root of, or null when it is a pushed child (or onboarding). */
fun tabOf(screen: Screen): Tab? = Tab.entries.firstOrNull { it.root == screen }
