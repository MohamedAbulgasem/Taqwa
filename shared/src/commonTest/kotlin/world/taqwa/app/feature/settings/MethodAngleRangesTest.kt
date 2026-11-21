package world.taqwa.app.feature.settings

import world.taqwa.app.domain.Prayer
import kotlinx.datetime.LocalDate
import world.taqwa.app.feature.common.authorityNameRes
import world.taqwa.app.feature.prayer.WhoseTimes
import world.taqwa.app.i18n.EnglishPlatformFormat
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.method.EndOfEating
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.registry.EntryClass
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

    /**
     * The trust audit's F31 (ruling R209): what a method sets beside its angles is read off the method the place is
     * served, so the sentence can name it: a Dhuhr or an Asr never before a clock time, an Isha never less than so
     * long after Maghrib, an end of eating so many minutes before Fajr or at a deeper dawn than Fajr's. The rule, not
     * the figures: each is whatever the served method carries, and none is silently dropped.
     */
    @Test
    fun `the method served in Tajikistan names its Dhuhr clock its Isha floor and its deeper end of eating`() {
        val dushanbe = requireNotNull(Registry.resolve(Place(38.5598, 68.787, "Asia/Dushanbe", "TJ")).method)
        val extras = methodExtras(dushanbe)
        assertEquals(requireNotNull(dushanbe.fixedDhuhrLocalMinutes), extras.dhuhrNotBeforeMinutes)
        assertEquals(requireNotNull(dushanbe.ishaFloorMinutesAfterMaghrib), extras.ishaFloorMinutesAfterMaghrib)
        val dawn = requireNotNull(extras.eatingDawnDegrees) { "eating ends at a deeper dawn than Fajr's, and the sentence says so" }
        assertTrue(dawn > dushanbe.fajrAngle)
        assertEquals(null, extras.asrNotBeforeMinutes)
        assertEquals(null, extras.eatingMinutesBeforeFajr)
    }

    @Test
    fun `the method served in Cote d'Ivoire names its Dhuhr and Asr clocks and its minutes before Fajr`() {
        val abidjan = requireNotNull(Registry.resolve(Place(5.3364, -4.0267, "Africa/Abidjan", "CI")).method)
        val extras = methodExtras(abidjan)
        assertEquals(requireNotNull(abidjan.fixedDhuhrLocalMinutes), extras.dhuhrNotBeforeMinutes)
        assertEquals(requireNotNull(abidjan.fixedAsrLocalMinutes), extras.asrNotBeforeMinutes)
        val eating = abidjan.endOfEating
        assertTrue(eating is EndOfEating.MinutesBeforeFajr)
        assertEquals(eating.minutes, extras.eatingMinutesBeforeFajr)
        assertEquals(null, extras.eatingDawnDegrees, "minutes before Fajr, not a dawn of its own")
    }

    @Test
    fun `no clock floor or end of eating a method carries is left out of what the sentence reads`() {
        for (method in Registry.entries.mapNotNull { it.method }) {
            val extras = methodExtras(method)
            assertEquals(method.fixedDhuhrLocalMinutes, extras.dhuhrNotBeforeMinutes, method.id)
            assertEquals(method.fixedAsrLocalMinutes, extras.asrNotBeforeMinutes, method.id)
            assertEquals(method.ishaFloorMinutesAfterMaghrib, extras.ishaFloorMinutesAfterMaghrib, method.id)
            assertEquals((method.endOfEating as? EndOfEating.MinutesBeforeFajr)?.minutes, extras.eatingMinutesBeforeFajr, method.id)
        }
    }

    @Test
    fun `a curve has no single end of eating angle to name`() {
        assertEquals(null, methodExtras(london).eatingDawnDegrees)
        val plain = requireNotNull(Registry.byId("sa.ummalqura")?.method)
        assertEquals(null, methodExtras(plain).eatingDawnDegrees, "eating ends at Fajr's own dawn: nothing more to say")
    }

    /**
     * The trust audit's F30 (ruling R209): where Taqwa holds part of a body's timetable and calculates on the safe
     * side of it, the entry names the body, so the card and About never say "no local timetable is known" there;
     * the kind stays Taqwa's own calculation, and the name has a string.
     */
    @Test
    fun `Tajikistan and Cote d'Ivoire name the body whose timetable they are on the safe side of`() {
        for (place in listOf(Place(38.5598, 68.787, "Asia/Dushanbe", "TJ"), Place(5.3364, -4.0267, "Africa/Abidjan", "CI"))) {
            val entry = Registry.resolve(place).entry
            assertEquals(EntryClass.D_NONE, entry.entryClass, entry.id)
            val body = requireNotNull(entry.safeSideOfKey) { "${entry.id} names no body" }
            assertTrue(authorityNameRes(body) != null, "$body has no string")
        }
    }

    /**
     * The second audit's G8: the Palestinian Dar al-Iftaa's tables for 13 Arab towns in Israel are held and the Levant
     * default's Fajr was refit on them, so Nazareth and Umm al-Fahm are told whose timetable they are beside, never that
     * no local timetable is known.
     */
    @Test
    fun `Arab towns in Israel name the Palestinian Dar al-Iftaa whose town tables they are on the safe side of`() {
        for (place in listOf(Place(32.70087, 35.29719, "Asia/Jerusalem", "IL"), Place(32.5194, 35.1536, "Asia/Jerusalem", "IL"))) {
            val entry = Registry.resolve(place).entry
            assertEquals("default.safe.levant", entry.id)
            assertEquals(EntryClass.D_NONE, entry.entryClass, entry.id)
            assertEquals("authority_pa_iftaa", entry.safeSideOfKey)
        }
    }

    /**
     * Ruling R285 (the re-audit's C3, 15 Nov 2026): at Ceuta and Melilla Habous's own Sebta and Melilla tables ride beside
     * Spain's safe default (ruling R261), so the end of eating and the sunrise are Habous's where earlier. The place
     * names Habous as the body its calculated day is on the safe side of, on the card and in About, never "no local
     * timetable is known"; Spain's entry itself names no one, so Madrid and the strait's Spanish shore still say so.
     */
    @Test
    fun `Ceuta and Melilla name Habous whose own tables ride beside Spain's day`() {
        for (place in listOf(Place(35.88919, -5.32042, "Africa/Ceuta", "ES"), Place(35.29369, -2.93833, "Africa/Ceuta", "ES"))) {
            val r = Registry.resolve(place)
            assertEquals("default.safe.europe", r.entry.id)
            assertEquals(EntryClass.D_NONE, r.entryClass)
            assertEquals("authority_habous", r.safeSideOfTableKey)
            assertEquals("authority_habous", r.safeSideOfKey)
            assertTrue(authorityNameRes("authority_habous") != null)
            assertEquals("authority_habous", WhoseTimes.of(PrayerEngine.dayTimes(place, LocalDate(2027, 2, 10), EngineSettings())).safeSideOfKey)
        }
        assertEquals(null, Registry.byId("default.safe.europe")!!.safeSideOfKey, "Spain's entry names no body: all of Spain would be mislabelled")
        // About's sentence at both cities: eating ends at Spain's 18° dawn or Habous's, never also "N minutes before Fajr".
        val spain = Registry.resolve(Place(35.88919, -5.32042, "Africa/Ceuta", "ES")).method!!
        assertEquals(18.0, eatingBesideDawnDegrees(spain))
        assertEquals(null, methodExtras(spain).eatingMinutesBeforeFajr)
        // A method whose eating ends minutes before its Fajr names no dawn angle beside a table: the two would contradict.
        val minutes = spain.copy(endOfEating = EndOfEating.MinutesBeforeFajr(10))
        assertEquals(null, eatingBesideDawnDegrees(minutes))
        assertEquals(spain.fajrAngle, eatingBesideDawnDegrees(spain.copy(endOfEating = EndOfEating.SameAsFajrDawn)))
        for (place in listOf(Place(40.4168, -3.7038, "Europe/Madrid", "ES"), Place(36.0128, -5.6045, "Europe/Madrid", "ES"))) {
            val r = Registry.resolve(place)
            assertEquals(null, r.safeSideOfKey, "$place")
            assertEquals(null, WhoseTimes.of(PrayerEngine.dayTimes(place, LocalDate(2027, 2, 10), EngineSettings())).safeSideOfKey, "$place")
        }
    }

    @Test
    fun `only Taqwa's own calculation names a body it is on the safe side of`() {
        val named = Registry.entries.filter { it.safeSideOfKey != null }
        assertTrue(named.isNotEmpty())
        assertTrue(named.all { it.entryClass == EntryClass.D_NONE })
    }
}
