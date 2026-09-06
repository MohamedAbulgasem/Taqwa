package world.taqwa.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    @Test
    fun startsOnTheGivenScreen() {
        assertEquals(Screen.Today, Navigator(Screen.Today).current)
    }

    @Test
    fun pushAndPopReturnToWhereYouWere() {
        val n = Navigator(Screen.Today)
        n.push(Screen.Settings)
        n.push(Screen.Appearance)
        assertEquals(Screen.Appearance, n.current)
        assertTrue(n.pop())
        assertEquals(Screen.Settings, n.current)
    }

    @Test
    fun popAtTheRootIsRefusedSoTheAppNeverEmptiesItself() {
        val n = Navigator(Screen.Today)
        assertFalse(n.pop())
        assertEquals(Screen.Today, n.current)
    }

    @Test
    fun replaceAllClearsHistory() {
        val n = Navigator(Screen.Onboarding)
        n.push(Screen.Settings)
        n.replaceAll(Screen.Today)
        assertEquals(Screen.Today, n.current)
        assertFalse(n.pop())
    }
}
