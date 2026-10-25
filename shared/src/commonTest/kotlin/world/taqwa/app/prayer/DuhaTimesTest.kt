package world.taqwa.app.prayer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.astro.SunClock
import world.taqwa.app.prayer.engine.astro.SunModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Duha's rules (Duha and Iqama spec §2) at the five places of §2.2, then each rule alone with
 * injected instants. In the places' tests the shown sunrise is the day's first minute, so the
 * true sunrise is the later of the two and decides, as it does wherever an authority's own
 * sunrise is not later than the sun's.
 */
class DuhaTimesTest {

    private val tripoli = GeoLocation(32.8872, 13.1913, "Africa/Tripoli")
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London")
    private val oslo = GeoLocation(59.9139, 10.7522, "Europe/Oslo")
    private val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo")

    private fun at(place: GeoLocation, date: LocalDate, hour: Int, minute: Int): Instant =
        date.atTime(hour, minute).toInstant(TimeZone.of(place.timeZoneId))

    private fun duha(place: GeoLocation, date: LocalDate): Duha? =
        DuhaTimes.forDay(place, date, date.atStartOfDayIn(TimeZone.of(place.timeZoneId)))

    private fun assertDuha(place: GeoLocation, date: LocalDate, start: Pair<Int, Int>, best: Pair<Int, Int>, end: Pair<Int, Int>) {
        assertEquals(
            Duha(
                start = at(place, date, start.first, start.second),
                best = at(place, date, best.first, best.second),
                end = at(place, date, end.first, end.second),
            ),
            duha(place, date),
        )
    }

    @Test
    fun `Tripoli in late October is decided by fifty minutes after sunrise`() {
        assertDuha(tripoli, LocalDate(2026, 10, 25), start = 8 to 10, best = 10 to 6, end = 12 to 41)
    }

    @Test
    fun `London at midwinter is decided by the sun at six and a half degrees`() {
        assertDuha(london, LocalDate(2026, 12, 21), start = 9 to 11, best = 10 to 2, end = 11 to 48)
    }

    @Test
    fun `Oslo at midwinter has its best time at its start because the midpoint comes earlier`() {
        assertDuha(oslo, LocalDate(2026, 12, 21), start = 11 to 51, best = 11 to 51, end = 12 to 5)
    }

    @Test
    fun `Oslo at midsummer has a long morning`() {
        assertDuha(oslo, LocalDate(2026, 6, 21), start = 5 to 19, best = 8 to 37, end = 13 to 8)
    }

    @Test
    fun `Tromso in early February has no Duha because the sun stays under six and a half degrees`() {
        assertNull(duha(tromso, LocalDate(2026, 2, 5)))
    }

    @Test
    fun `Tromso at midwinter has no Duha because the sun never rises`() {
        assertNull(duha(tromso, LocalDate(2026, 12, 21)))
    }

    @Test
    fun `Tromso at midsummer has no Duha because under the midnight sun there is no sunrise`() {
        val date = LocalDate(2026, 6, 21)
        // Precondition: the sun climbs through 6.5° that morning (Oslo's zone is two hours ahead
        // of UTC in June), so the missing true sunrise is the only rule that can keep Duha away.
        val sun = SunClock(tromso.latitude, tromso.longitude, date, 2 * 3600, SunModel.EXACT)
        assertNotNull(sun.altitudeTime(DuhaTimes.START_ALTITUDE_DEG, morning = true))
        assertNull(sun.altitudeTime(DayPipeline.REAL_HORIZON_DEG, morning = true))
        assertNull(duha(tromso, date))
    }

    // The rules alone, on a morning with the sun's times set by hand (UTC).

    private fun utc(time: String): Instant = Instant.parse("2026-10-25T${time}Z")

    @Test
    fun `a shown sunrise later than the true one moves the start and the best time`() {
        val asTheSunHasIt = DuhaTimes.of(utc("06:00:00"), utc("05:58:00"), utc("06:20:00"), utc("12:00:00"))
        assertEquals(Duha(utc("06:50:00"), utc("09:00:00"), utc("11:50:00")), asTheSunHasIt)

        val asTheAuthorityShowsIt = DuhaTimes.of(utc("06:00:00"), utc("06:10:00"), utc("06:20:00"), utc("12:00:00"))
        assertEquals(Duha(utc("07:00:00"), utc("09:05:00"), utc("11:50:00")), asTheAuthorityShowsIt)
    }

    @Test
    fun `a start at or after the end gives no Duha`() {
        assertNull(DuhaTimes.of(utc("11:00:00"), utc("11:00:00"), utc("11:30:00"), utc("12:00:00")))
        assertNull(DuhaTimes.of(utc("11:01:00"), utc("11:01:00"), utc("11:30:00"), utc("12:00:00")))
    }

    @Test
    fun `a start one minute before the end still gives Duha`() {
        assertEquals(
            Duha(utc("11:49:00"), utc("11:49:00"), utc("11:50:00")),
            DuhaTimes.of(utc("10:59:00"), utc("10:59:00"), utc("11:30:00"), utc("12:00:00")),
        )
    }

    @Test
    fun `the best time is never before the start`() {
        // The sun reaches 6.5° at 11:40, long after the 11:00 midpoint of sunrise and noon.
        val duha = DuhaTimes.of(utc("10:00:00"), utc("10:00:00"), utc("11:40:00"), utc("12:00:00"))
        assertEquals(Duha(utc("11:40:00"), utc("11:40:00"), utc("11:50:00")), duha)
    }

    @Test
    fun `the start and the best time are rounded up and the end down`() {
        val duha = DuhaTimes.of(utc("06:00:30"), utc("05:58:00"), utc("06:20:00"), utc("12:00:30"))
        assertEquals(Duha(utc("06:51:00"), utc("09:01:00"), utc("11:50:00")), duha)
    }

    @Test
    fun `a start on a whole minute stays on it`() {
        val duha = DuhaTimes.of(utc("06:00:00"), utc("06:00:00"), utc("06:50:00"), utc("12:00:00"))
        assertEquals(utc("06:50:00"), duha?.start)
    }

    @Test
    fun `no true sunrise gives no Duha`() {
        assertNull(DuhaTimes.of(null, utc("06:00:00"), utc("06:20:00"), utc("12:00:00")))
    }

    @Test
    fun `a sun that never reaches six and a half degrees gives no Duha`() {
        assertNull(DuhaTimes.of(utc("06:00:00"), utc("06:00:00"), null, utc("12:00:00")))
    }
}
