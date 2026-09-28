package world.taqwa.app.feature.settings

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MosqueMatcherTest {

    private fun hm(h: Int, m: Int) = h * 60 + m

    /** Invented timetables and board times (ruling R69: no printed time); the board shows Fajr 4:47, Isha 21:13. */
    private val diyanet = MosqueCandidate("gb.diyanet", hm(4, 47), hm(21, 13))
    private val lupt = MosqueCandidate("automatic", hm(5, 2), hm(20, 31))
    private val wifaq = MosqueCandidate("gb.wifaqul", hm(4, 48), hm(21, 4))

    @Test
    fun bothTimesEqualIsAnExactMatch() {
        assertEquals(MosqueMatch.Both("gb.diyanet", exact = true), MosqueMatcher.match(hm(4, 47), hm(21, 13), listOf(lupt, wifaq, diyanet)))
    }

    @Test
    fun aCandidateOneMinuteLaterStillMatches() {
        val oneLater = MosqueCandidate("x", hm(4, 48), hm(21, 14))
        assertEquals(MosqueMatch.Both("x", exact = false), MosqueMatcher.match(hm(4, 47), hm(21, 13), listOf(oneLater)))
    }

    @Test
    fun aCandidateEarlierThanTheBoardNeverMatches() {
        val oneEarlier = MosqueCandidate("x", hm(4, 46), hm(21, 13))
        val result = MosqueMatcher.match(hm(4, 47), hm(21, 13), listOf(oneEarlier))
        // Its Isha matches; its Fajr is a minute before the board, so both never do.
        assertEquals(MosqueMatch.OneTime("x", matched = Prayer.ISHA, differs = Prayer.FAJR), result)
    }

    @Test
    fun twoMinutesLaterIsNoLongerAMatch() {
        val twoLater = MosqueCandidate("x", hm(4, 49), hm(21, 15))
        assertEquals(MosqueMatch.None, MosqueMatcher.match(hm(4, 47), hm(21, 13), listOf(twoLater)))
    }

    @Test
    fun anExactMatchWinsOverALaterOneListedFirst() {
        val later = MosqueCandidate("later", hm(4, 48), hm(21, 14))
        assertEquals(MosqueMatch.Both("gb.diyanet", exact = true), MosqueMatcher.match(hm(4, 47), hm(21, 13), listOf(later, diyanet)))
    }

    @Test
    fun withoutAnExactMatchTheLatestWins() {
        val fajrLater = MosqueCandidate("fajr", hm(4, 48), hm(21, 13))
        val bothLater = MosqueCandidate("both", hm(4, 48), hm(21, 14))
        assertEquals(MosqueMatch.Both("both", exact = false), MosqueMatcher.match(hm(4, 47), hm(21, 13), listOf(fajrLater, bothLater)))
    }

    @Test
    fun equalCandidatesGoToTheFirstListed() {
        val twin = MosqueCandidate("twin", hm(4, 47), hm(21, 13))
        assertEquals(MosqueMatch.Both("gb.diyanet", exact = true), MosqueMatcher.match(hm(4, 47), hm(21, 13), listOf(diyanet, twin)))
    }

    @Test
    fun bothTimesAnHourOffSayTheBoardMayShowWinterTime() {
        // A board printed on winter time, read in summer: every time on it is an hour before the clock's.
        assertEquals(MosqueMatch.HourApart("gb.diyanet"), MosqueMatcher.match(hm(3, 47), hm(20, 13), listOf(lupt, diyanet)))
        // And the other way.
        assertEquals(MosqueMatch.HourApart("gb.diyanet"), MosqueMatcher.match(hm(5, 47), hm(22, 13), listOf(lupt, diyanet)))
    }

    @Test
    fun anHourApartAnExactFitWinsThenTheLatest() {
        val laterFirst = MosqueCandidate("later", hm(4, 48), hm(21, 14))
        val exact = MosqueCandidate("exact", hm(4, 47), hm(21, 13))
        // Board an hour behind: "exact" is exactly an hour after it, "later" an hour and a minute.
        assertEquals(MosqueMatch.HourApart("exact"), MosqueMatcher.match(hm(3, 47), hm(20, 13), listOf(laterFirst, exact)))
        val fajrLater = MosqueCandidate("fajr", hm(4, 48), hm(21, 13))
        val bothLater = MosqueCandidate("both", hm(4, 48), hm(21, 14))
        assertEquals(MosqueMatch.HourApart("both"), MosqueMatcher.match(hm(3, 47), hm(20, 13), listOf(fajrLater, bothLater)))
    }

    @Test
    fun anHourOffOneWayAndNotTheOtherIsNoWinterTime() {
        assertEquals(MosqueMatch.None, MosqueMatcher.match(hm(3, 47), hm(22, 13), listOf(diyanet)))
    }

    @Test
    fun oneTimeMatchingNamesTheTimeThatDiffers() {
        val result = MosqueMatcher.match(hm(4, 47), hm(21, 35), listOf(lupt, diyanet))
        assertEquals(MosqueMatch.OneTime("gb.diyanet", matched = Prayer.FAJR, differs = Prayer.ISHA), result)
    }

    @Test
    fun ofSeveralOneTimeMatchesTheNearestOtherTimeWins() {
        val far = MosqueCandidate("far", hm(4, 47), hm(21, 55))
        val near = MosqueCandidate("near", hm(4, 47), hm(21, 25))
        assertEquals(MosqueMatch.OneTime("near", Prayer.FAJR, Prayer.ISHA), MosqueMatcher.match(hm(4, 47), hm(21, 20), listOf(far, near)))
    }

    @Test
    fun nothingMatchingIsNone() {
        assertEquals(MosqueMatch.None, MosqueMatcher.match(hm(4, 8), hm(22, 40), listOf(lupt, wifaq, diyanet)))
        assertEquals(MosqueMatch.None, MosqueMatcher.match(hm(4, 47), hm(21, 13), emptyList()))
    }

    @Test
    fun aTwelveHourBoardReadsAsTheEvening() {
        assertEquals(MosqueMatch.Both("gb.diyanet", exact = true), MosqueMatcher.match(hm(4, 47), hm(9, 13), listOf(diyanet)))
    }

    @Test
    fun boardTimesParseInEveryShapeTyped() {
        assertEquals(hm(5, 14), MosqueMatcher.parseBoardTime("5:14"))
        assertEquals(hm(5, 14), MosqueMatcher.parseBoardTime("05.14"))
        assertEquals(hm(5, 14), MosqueMatcher.parseBoardTime("5h14"))
        assertEquals(hm(5, 14), MosqueMatcher.parseBoardTime("514"))
        assertEquals(hm(20, 27), MosqueMatcher.parseBoardTime("2027"))
        assertEquals(hm(20, 27), MosqueMatcher.parseBoardTime("٢٠:٢٧"))
        assertEquals(hm(20, 27), MosqueMatcher.parseBoardTime("۲۰:۲۷"))
        assertEquals(hm(5, 14), MosqueMatcher.parseBoardTime("৫:১৪"))
    }

    @Test
    fun anUnfinishedOrImpossibleTimeIsNotOneYet() {
        assertNull(MosqueMatcher.parseBoardTime(""))
        assertNull(MosqueMatcher.parseBoardTime("5"))
        assertNull(MosqueMatcher.parseBoardTime("50"))
        assertNull(MosqueMatcher.parseBoardTime("5:0"))
        assertNull(MosqueMatcher.parseBoardTime("25:00"))
        assertNull(MosqueMatcher.parseBoardTime("5:61"))
        assertNull(MosqueMatcher.parseBoardTime("12345"))
    }

    @Test
    fun aBoardFieldKeepsFourDigitsInAnyScript() {
        assertEquals("514", boardDigits("5:14"))
        assertEquals("2027", boardDigits("٢٠٢٧"))
        assertEquals("2027", boardDigits("202734"))
    }
}
