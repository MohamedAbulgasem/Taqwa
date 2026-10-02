package world.taqwa.app.feature.onboarding

import world.taqwa.app.location.LocationPermission
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where onboarding's location screen goes once the system has its answer (App Review 5.1.1(iv)). */
class OnboardingFlowTest {

    @Test
    fun `allowing location moves on to the next screen`() {
        assertEquals(LocationAnswer.CONTINUE, locationAnswer(LocationPermission.GRANTED))
    }

    @Test
    fun `declining location opens the city search rather than moving on without one`() {
        assertEquals(LocationAnswer.CHOOSE_CITY, locationAnswer(LocationPermission.DENIED))
    }

    @Test
    fun `a prompt still unanswered leaves the screen where it is`() {
        assertEquals(LocationAnswer.STAY, locationAnswer(LocationPermission.NOT_REQUESTED))
    }
}
