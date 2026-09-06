package world.taqwa.app.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Slice 1 has a root screen and a handful of pushed children, so a list in a StateFlow is the
 * whole requirement. Revisit when the tab bar arrives in slice 2.
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
}
