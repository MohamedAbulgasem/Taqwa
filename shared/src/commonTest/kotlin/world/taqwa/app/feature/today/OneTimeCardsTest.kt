package world.taqwa.app.feature.today

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OneTimeCardsTest {

    private fun pick(
        cautiousPlace: Boolean = false,
        shiaRegion: Boolean = false,
        cautiousSeen: Boolean = false,
        sunniSeen: Boolean = false,
        shown: OneTimeCard? = null,
        answered: Boolean = false,
    ) = OneTimeCards.pick(cautiousPlace, shiaRegion, cautiousSeen, sunniSeen, shown, answered)

    @Test
    fun aCautiousPlaceShowsTheCautiousCard() {
        assertEquals(OneTimeCard.CAUTIOUS, pick(cautiousPlace = true))
    }

    @Test
    fun aShiaRegionShowsTheSunniCard() {
        assertEquals(OneTimeCard.SUNNI, pick(shiaRegion = true))
    }

    @Test
    fun whereBothApplyTheCautiousCardComesFirst() {
        assertEquals(OneTimeCard.CAUTIOUS, pick(cautiousPlace = true, shiaRegion = true))
    }

    @Test
    fun aShownCardStaysForTheLaunchThoughItIsNowStoredAsSeen() {
        assertEquals(
            OneTimeCard.CAUTIOUS,
            pick(cautiousPlace = true, shiaRegion = true, cautiousSeen = true, shown = OneTimeCard.CAUTIOUS),
        )
    }

    @Test
    fun anAnsweredCardGoesAndTheOtherWaitsForTheNextLaunch() {
        assertNull(pick(cautiousPlace = true, shiaRegion = true, cautiousSeen = true, shown = OneTimeCard.CAUTIOUS, answered = true))
    }

    @Test
    fun aShownCardUnansweredStillKeepsTheOtherAwayThisLaunch() {
        // The cautious card was shown in Toronto; in Dammam the same launch shows nothing.
        assertNull(pick(shiaRegion = true, cautiousSeen = true, shown = OneTimeCard.CAUTIOUS))
    }

    @Test
    fun theNextLaunchShowsTheOtherCardWhetherOrNotTheFirstWasAnswered() {
        // Seen once, answered or not: the Sunni card is not held back.
        assertEquals(OneTimeCard.SUNNI, pick(cautiousPlace = true, shiaRegion = true, cautiousSeen = true))
    }

    @Test
    fun aSeenCardNeverComesBack() {
        assertNull(pick(cautiousPlace = true, cautiousSeen = true))
        assertNull(pick(shiaRegion = true, sunniSeen = true))
    }

    @Test
    fun elsewhereThereIsNoCard() {
        assertNull(pick())
    }
}
