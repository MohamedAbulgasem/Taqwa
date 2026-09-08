package world.taqwa.app.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A list in a StateFlow is the whole requirement: a couple of tab roots and a handful of pushed
 * children. The tab bar arrived early, on the owner's call after seeing Today on a phone, but it
 * brings no per-tab history with it — [selectTab] replaces the stack rather than juggling three.
 */
class Navigator(start: Screen) {

    private val _backStack = MutableStateFlow(listOf(start))
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()

    val current: Screen get() = _backStack.value.last()

    fun push(screen: Screen) {
        _backStack.value = _backStack.value + screen
    }

    /** Returns false at the root so the caller can let the platform handle the back gesture. */
    fun pop(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.value = _backStack.value.dropLast(1)
        return true
    }

    fun replaceAll(screen: Screen) {
        _backStack.value = listOf(screen)
    }

    /**
     * Pop then push: [screen] takes the current screen's place, with whatever was behind it
     * untouched. Used by the reader's mode toggle (spec §2.2) — translation and Mushaf must not
     * stack on top of each other, or the back button would bounce between the two modes of the
     * same reading position instead of leaving the surah.
     */
    fun replace(screen: Screen) {
        _backStack.value = _backStack.value.dropLast(1) + screen
    }

    /**
     * The tab currently showing, read from the bottom of the stack so a pushed sub-screen still
     * reports the tab it was opened from. Null during onboarding, which has no tab bar.
     */
    val currentTab: Tab? get() = tabOf(_backStack.value.first())

    /**
     * Switches tabs by replacing the stack with [tab]'s root. See [Tab] for why there is no
     * per-tab history to restore.
     */
    fun selectTab(tab: Tab) = replaceAll(tab.root)
}
