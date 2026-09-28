package world.taqwa.app.feature.settings

import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 11's review fix: [methodAngleRanges] must read London Unified's day-of-year curves honestly
 * rather than only its static `fajrAngle` fallback (LondonUnifiedCurve's own comment: Fajr moves
 * between about 12° and 16.4°, Isha between 8.6° and 15.6° over the year).
 */
class MethodAngleRangesTest {

    private val london = requireNotNull(Registry.byId("gb.london.lupt")?.method) { "gb.london.lupt has no method" }

    @Test
    fun `London Unified's Fajr is read as a curve not a fixed angle`() {
        val angles = methodAngleRanges(london)
        assertTrue(angles.fajrIsCurve, "London Unified's Fajr should read as a curve")
        // LondonUnifiedCurve.kt (Task 7g's gaps): Fajr moves between about 11.9 deg and 16.4 deg over the year.
        assertEquals(11.874, angles.fajr.first)
        assertEquals(16.34, angles.fajr.second)
    }

    @Test
    fun `London Unified's Isha is read as a curve not a fixed angle`() {
        val angles = methodAngleRanges(london)
        assertTrue(london.isha is IshaRule.Angle)
        assertTrue(angles.ishaIsCurve, "London Unified's Isha should read as a curve")
        // LondonUnifiedCurve.kt (Task 7g's gaps): Isha moves between 8.6 deg and 15.6 deg over the year.
        assertEquals(8.625, angles.isha!!.first)
        assertEquals(15.586, angles.isha.second)
    }

    @Test
    fun `Umm al-Qura's plain Fajr angle is not read as a curve`() {
        val method = requireNotNull(Registry.byId("sa.ummalqura")?.method)
        val angles = methodAngleRanges(method)
        assertFalse(angles.fajrIsCurve)
        assertEquals(angles.fajr.first, angles.fajr.second)
    }

    @Test
    fun `Diyanet's daily sun model is read honestly`() {
        val diyanet = requireNotNull(Registry.byId("tr.diyanet")?.method)
        assertTrue(usesDailySun(diyanet), "Diyanet takes the sun's position once a day")
        assertFalse(usesDailySun(london), "London Unified takes the exact moment of each event")
    }

    @Test
    fun `Kemenag's deeper horizon is read honestly`() {
        val kemenag = requireNotNull(Registry.byId("id.kemenag")?.method)
        assertTrue(dipsHorizon(kemenag), "Kemenag's -1 degree horizon is deeper than the plain default")
        assertFalse(dipsHorizon(london), "London Unified uses the plain sea-level horizon")
    }

    @Test
    fun `a whole-degree angle shows no decimal`() {
        assertEquals("18", formatAngle(18.0, EnglishPlatformFormat))
    }

    @Test
    fun `a fractional angle shows one decimal never rounded away`() {
        assertEquals("18.5", formatAngle(18.5, EnglishPlatformFormat))
        assertEquals("19.5", formatAngle(19.5, EnglishPlatformFormat))
        assertEquals("18.3", formatAngle(18.3, EnglishPlatformFormat))
    }

    @Test
    fun `a negative angle keeps its sign`() {
        assertEquals("−0.8", formatAngle(-0.8333, EnglishPlatformFormat))
    }

    /**
     * Ruling R73: eastern and southern Libya's About screen is generated straight from the resolved
     * method, never hand-written per place, so it must read the local 19.5° Fajr and the sunset + 1
     * Maghrib note there honestly, while a western city keeps the national 18.5° and no such note.
     */
    @Test
    fun `an eastern libyan place reads its own local fajr angle and maghrib note honestly`() {
        val benghazi = requireNotNull(Registry.resolve(Place(32.1167, 20.0667, "Africa/Tripoli", "LY")).method)
        val angles = methodAngleRanges(benghazi)
        assertFalse(angles.fajrIsCurve)
        assertEquals(19.5, angles.fajr.first)
        assertEquals(1, benghazi.authorityMinutes[Prayer.MAGHRIB])

        val tripoli = requireNotNull(Registry.resolve(Place(32.8872, 13.1913, "Africa/Tripoli", "LY")).method)
        assertEquals(18.5, methodAngleRanges(tripoli).fajr.first)
        assertEquals(0, tripoli.authorityMinutes[Prayer.MAGHRIB])
    }
}
