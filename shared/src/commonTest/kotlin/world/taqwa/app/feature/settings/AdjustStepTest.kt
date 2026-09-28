package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AdjustStepTest {

    private val diyanet = "tr.diyanet"

    @Test
    fun aLaterTimeNeverAsks() {
        assertEquals(AdjustStep.Apply(1), adjustStep(0, 1, null, diyanet, margin = 1))
        assertEquals(AdjustStep.Apply(12), adjustStep(11, 12, null, diyanet, margin = 1))
    }

    @Test
    fun theFirstStepBelowTheTimetableAsks() {
        assertEquals(AdjustStep.Ask(-1, strong = false), adjustStep(0, -1, null, diyanet, margin = 1))
    }

    @Test
    fun aConfirmationUnderAnotherTimetableAsksAgain() {
        assertEquals(AdjustStep.Ask(-1, strong = false), adjustStep(0, -1, "gb.london.lupt", diyanet, margin = 2))
    }

    @Test
    fun onceConfirmedTheStepsWithinTheMarginAreTheUsersOwn() {
        assertEquals(AdjustStep.Apply(-2), adjustStep(-1, -2, diyanet, diyanet, margin = 2))
        assertEquals(AdjustStep.Apply(-1), adjustStep(0, -1, diyanet, diyanet, margin = 2))
    }

    @Test
    fun theStepPastTheMarginAsksMoreStrongly() {
        assertEquals(AdjustStep.Ask(-3, strong = true), adjustStep(-2, -3, diyanet, diyanet, margin = 2))
    }

    @Test
    fun stepsBeyondTheMarginOnceAskedDoNotAskAgain() {
        assertEquals(AdjustStep.Apply(-4), adjustStep(-3, -4, diyanet, diyanet, margin = 2))
    }

    @Test
    fun aStepBackTowardsTheTimetableNeverAsks() {
        assertEquals(AdjustStep.Apply(-2), adjustStep(-3, -2, null, diyanet, margin = 1))
    }

    @Test
    fun aPausedValueStepsBackTowardsTheTimetableWithoutAsking() {
        // −3 was set for London Unified; under Diyanet it is paused but keeps its stepper.
        assertEquals(AdjustStep.Apply(-2), adjustStep(-3, -2, "gb.london.lupt", diyanet, margin = 1))
        assertEquals(AdjustStep.Apply(0), adjustStep(-1, 0, "gb.london.lupt", diyanet, margin = 1))
        // A step further from it is an earlier time under Diyanet, so it asks, and strongly past the margin.
        assertEquals(AdjustStep.Ask(-4, strong = true), adjustStep(-3, -4, "gb.london.lupt", diyanet, margin = 1))
    }

    @Test
    fun withNoMarginClaimedOnlyTheFirstStepAsks() {
        assertEquals(AdjustStep.Ask(-1, strong = false), adjustStep(0, -1, null, "default.safe", margin = null))
        assertEquals(AdjustStep.Apply(-30), adjustStep(-29, -30, "default.safe", "default.safe", margin = null))
    }

    @Test
    fun aFirstStepAlreadyPastTheMarginAsksStrongly() {
        // Class A's margin is a minute: a value of −2 under a new timetable is past it at once.
        assertEquals(AdjustStep.Ask(-2, strong = true), adjustStep(-1, -2, null, diyanet, margin = 1))
    }

    @Test
    fun reconfirmingAPausedValueAsksStronglyOnlyPastTheMargin() {
        assertEquals(AdjustStep.Ask(-2, strong = false), reconfirmStep(-2, margin = 2))
        assertEquals(AdjustStep.Ask(-5, strong = true), reconfirmStep(-5, margin = 2))
        assertEquals(AdjustStep.Ask(-5, strong = false), reconfirmStep(-5, margin = null))
    }
}
