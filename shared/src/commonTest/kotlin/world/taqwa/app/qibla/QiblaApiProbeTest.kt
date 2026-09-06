package world.taqwa.app.qibla

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import kotlin.test.Test
import kotlin.test.assertTrue

class QiblaApiProbeTest {
    @Test
    fun adhanQiblaReturnsADegreeBearingForLondon() {
        val q = Qibla(Coordinates(51.5074, -0.1278))
        assertTrue(q.direction in 0.0..360.0, "was ${q.direction}")
    }
}
