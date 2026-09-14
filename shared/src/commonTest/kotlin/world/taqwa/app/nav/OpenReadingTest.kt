package world.taqwa.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `openReading` is how anything outside the Quran tab — the ayah widget, the media notification —
 * lands the reader on a reading screen. The one rule that matters: the Quran root ends up at the
 * **bottom** of the stack, because [Navigator.currentTab] is read from the bottom and the player
 * bar is shown only while that tab is current. A widget tap that merely pushed the Quran root on
 * top of the Prayer tab played the surah with no bar at all until the tab was left and re-entered.
 */
class OpenReadingTest {

    @Test
    fun aWidgetTapFromThePrayerTabPutsTheQuranRootUnderTheReader() {
        val n = Navigator(Screen.Today)

        n.openReading(Screen.Reader(2, 255, selectAyah = true))

        assertEquals(listOf(Screen.Quran, Screen.Reader(2, 255, selectAyah = true)), n.backStack.value)
        assertEquals(Tab.QURAN, n.currentTab)
    }

    @Test
    fun fromASettingsSubScreenTheWholeStackIsReplaced() {
        val n = Navigator(Screen.Today)
        n.selectTab(Tab.SETTINGS)
        n.push(Screen.Appearance)

        n.openReading(Screen.Mushaf(42, 2, 255))

        assertEquals(listOf(Screen.Quran, Screen.Mushaf(42, 2, 255)), n.backStack.value)
    }

    @Test
    fun fromTheQuranRootOnlyTheReaderIsPushed() {
        val n = Navigator(Screen.Quran)

        n.openReading(Screen.Reader(36, 1))

        assertEquals(listOf(Screen.Quran, Screen.Reader(36, 1)), n.backStack.value)
    }

    @Test
    fun aReadingScreenAlreadyOpenIsReplacedNotStacked() {
        val n = Navigator(Screen.Quran)
        n.push(Screen.Reader(2, 1))

        n.openReading(Screen.Reader(2, 255, selectAyah = true))

        assertEquals(listOf(Screen.Quran, Screen.Reader(2, 255, selectAyah = true)), n.backStack.value)
    }

    @Test
    fun aReadingScreenLeftOnTopOfAnotherTabIsStraightenedOut() {
        // The stack shape the old widget path produced: the reader on a Quran root pushed over
        // Prayer. A second tap must not preserve it by replacing in place.
        val n = Navigator(Screen.Today)
        n.push(Screen.Quran)
        n.push(Screen.Reader(2, 1))

        n.openReading(Screen.Reader(3, 7, selectAyah = true))

        assertEquals(listOf(Screen.Quran, Screen.Reader(3, 7, selectAyah = true)), n.backStack.value)
        assertEquals(Tab.QURAN, n.currentTab)
    }

    @Test
    fun fromAQuranSubScreenTheRootIsPushedAgainSoBackReturnsToIt() {
        // Existing behaviour of the notification path, kept: a reader opened over the Quran
        // tab's own sub-screens gets the root under it, and one Back reaches the surah list.
        val n = Navigator(Screen.Quran)
        n.push(Screen.RecitationSettings)

        n.openReading(Screen.Reader(1, 1))

        assertEquals(listOf(Screen.Quran, Screen.RecitationSettings, Screen.Quran, Screen.Reader(1, 1)), n.backStack.value)
    }
}
