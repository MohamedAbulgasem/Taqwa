package world.taqwa.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun selectingATabLandsOnItsRootWithNothingBehindIt() {
        val n = Navigator(Screen.Today)
        n.selectTab(Tab.QIBLA)
        assertEquals(Screen.Qibla, n.current)
        assertEquals(Tab.QIBLA, n.currentTab)
        assertEquals(1, n.backStack.value.size)
        assertFalse(n.pop())
    }

    @Test
    fun aSubScreenStacksOnTheTabRootAndPopReturnsToIt() {
        val n = Navigator(Screen.Today)
        n.selectTab(Tab.SETTINGS)
        n.push(Screen.PrayerTimesSettings)
        n.push(Screen.MethodPicker)
        // The tab does not change under a pushed child — the bar would otherwise lose its
        // highlight the moment anyone opened a picker.
        assertEquals(Tab.SETTINGS, n.currentTab)
        assertTrue(n.pop())
        assertTrue(n.pop())
        assertEquals(Screen.Settings, n.current)
    }

    @Test
    fun selectingATabFromASubScreenReplacesTheWholeStack() {
        val n = Navigator(Screen.Today)
        n.selectTab(Tab.SETTINGS)
        n.push(Screen.LocationSettings)
        n.push(Screen.CitySearch)
        n.selectTab(Tab.TODAY)
        assertEquals(Screen.Today, n.current)
        assertEquals(listOf<Screen>(Screen.Today), n.backStack.value)
    }

    @Test
    fun exactlyThreeScreensAreTabRoots() {
        val roots = listOf(Screen.Today, Screen.Qibla, Screen.Settings)
        roots.forEach { assertTrue(isTabRoot(it), "$it should be a tab root") }
        listOf(
            Screen.Onboarding,
            Screen.PrayerTimesSettings,
            Screen.NotificationSettings,
            Screen.MethodPicker,
            Screen.HighLatitudePicker,
            Screen.ManualAdjustments,
            Screen.LocationSettings,
            Screen.CitySearch,
            Screen.Appearance,
            Screen.Attribution,
        ).forEach { assertFalse(isTabRoot(it), "$it should not be a tab root") }
        assertEquals(roots, Tab.entries.map { it.root })
    }

    @Test
    fun onboardingBelongsToNoTabSoTheBarStaysHidden() {
        val n = Navigator(Screen.Onboarding)
        assertNull(n.currentTab)
        n.push(Screen.CitySearch)
        assertNull(n.currentTab)
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
