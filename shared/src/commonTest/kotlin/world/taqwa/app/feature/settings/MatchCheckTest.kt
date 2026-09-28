package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Match my mosque's check belongs to the timetable it checked, never to the next match. */
class MatchCheckTest {

    private val diyanet = TimetableOption("gb.diyanet", "authority_diyanet_europe")
    private val isna = TimetableOption("other.isna", "method_isna")
    private val warning = EarlierFinding(CheckedTime.FAJR, 21)

    private fun result(named: TimetableOption?, check: MatchCheck?) =
        MatchResult(MosqueMatch.Both(named?.id ?: "", exact = true), named, emptyList(), check)

    @Test
    fun theMatchedTimetablesOwnCheckIsShown() {
        assertEquals(MatchCheck.Done("gb.diyanet", warning), result(diyanet, MatchCheck.Done("gb.diyanet", warning)).checkOfMatch())
        assertEquals(MatchCheck.InUse("gb.diyanet"), result(diyanet, MatchCheck.InUse("gb.diyanet")).checkOfMatch())
    }

    @Test
    fun aCheckLeftFromAnotherMatchReadsAsNotYetStarted() {
        // ISNA matched a moment ago and was checked clean; the board now matches Diyanet instead.
        assertEquals(MatchCheck.Checking("gb.diyanet", 0f), result(diyanet, MatchCheck.Done("other.isna", null)).checkOfMatch())
        assertEquals(MatchCheck.Checking("gb.diyanet", 0f), result(diyanet, MatchCheck.InUse("other.isna")).checkOfMatch())
        assertEquals(MatchCheck.Checking("other.isna", 0f), result(isna, MatchCheck.Checking("gb.diyanet", 0.8f)).checkOfMatch())
    }

    @Test
    fun noCheckYetReadsAsNotYetStarted() {
        assertEquals(MatchCheck.Checking("gb.diyanet", 0f), result(diyanet, null).checkOfMatch())
    }

    @Test
    fun noMatchedTimetableHasNoCheck() {
        assertNull(result(null, MatchCheck.Done("gb.diyanet", null)).checkOfMatch())
    }
}
