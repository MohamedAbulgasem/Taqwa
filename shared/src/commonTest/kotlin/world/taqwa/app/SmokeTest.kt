package world.taqwa.app

import com.batoulapps.adhan2.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun adhanLibraryIsOnTheClasspath() {
        val makkah = Coordinates(21.4225, 39.8262)
        assertEquals(21.4225, makkah.latitude)
    }
}
