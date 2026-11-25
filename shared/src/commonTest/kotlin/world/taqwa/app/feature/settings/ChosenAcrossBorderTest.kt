package world.taqwa.app.feature.settings

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.app.prayer.engine.DayPipeline
import world.taqwa.app.prayer.engine.EngineSettings
import world.taqwa.app.prayer.engine.PrayerEngine
import world.taqwa.app.prayer.engine.TimetableChoice
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Place
import world.taqwa.app.prayer.engine.registry.Registry
import world.taqwa.app.prayer.engine.registry.data.ProofStamps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ruling R318 (the owner's ruling of 18 Nov 2026, the final audit's B1): a timetable whose figure the proof reads across
 * an area only where a fix is placed in its country alone (MUIS's and the Supreme Council's nearest-city cells, Tayma's
 * town, JAKIM's handed zones) claims no figure where it is chosen at a border place in the neighbour's country and
 * resolved in its own through the second country (ruling R259): About says "Not measured" there, and no time moves. A
 * place the area holds, chosen there under a neighbour's zone, keeps the figure. The places are the ones the app's
 * FixCountry gives these fixes (the tools' `ChosenAcrossBorderPlacesTest` holds them to the bundled cities).
 */
class ChosenAcrossBorderTest {

    private val date = LocalDate(2026, 6, 21)

    /** Batu Pahat, Johor, on a Singapore network: its own Malaysia, Singapore beside (Hong Kah, 104 km). */
    private val batuPahat = Place(1.8548, 102.9325, "Asia/Singapore", "MY", "Johor", alsoCountryCode = "SG")

    /** Dammam on a Bahraini network: its own Saudi Arabia, Bahrain beside (Jidd Hafs, 50 km). */
    private val dammam = Place(26.4207, 50.0888, "Asia/Bahrain", "SA", "Eastern Province", alsoCountryCode = "BH", alsoAdmin1 = "Manama")

    /** Woodlands, Singapore, on a Malaysian network: its own Singapore, Malaysia beside (Johor Bahru). */
    private val woodlands = Place(1.43801, 103.78877, "Asia/Kuala_Lumpur", "SG", null, alsoCountryCode = "MY", alsoAdmin1 = "Johor")

    /** Singapore's own point on a Malaysian network: inside MUIS's cell. */
    private val singapore = Place(1.28967, 103.85007, "Asia/Kuala_Lumpur", "SG", null, alsoCountryCode = "MY", alsoAdmin1 = "Johor")

    /** Manama on a Saudi network: inside the Supreme Council's cell. */
    private val manama = Place(26.22787, 50.58565, "Asia/Riyadh", "BH", "Manama", alsoCountryCode = "SA", alsoAdmin1 = "Eastern Province")

    /** Johor Bahru on a Singapore network: inside JAKIM's cell. */
    private val johorBahru = Place(1.4655, 103.7578, "Asia/Singapore", "MY", "Johor", alsoCountryCode = "SG")

    /** Cholpon-Ata on a Kyrgyz network (ruling R259): the Muftiate's units are read by their circles, whatever the country. */
    private val cholponAta = Place(42.648, 77.071, "Asia/Bishkek", "KZ", "Almaty", alsoCountryCode = "KG", alsoAdmin1 = "Issyk-Kul")

    private fun chosen(place: Place, id: String) = PrayerEngine.dayTimes(
        place, date,
        EngineSettings(timetable = TimetableChoice.Entry(id), timetableConfirmed = true, timetableConfirmedUnder = PrayerEngine.automaticEntryId(place)),
    )

    @Test
    fun `an area's timetable chosen across the border in the neighbour's country claims no figure`() {
        for ((place, id) in listOf(batuPahat to "sg.muis", dammam to "bh.council", woodlands to "my.jakim")) {
            val day = chosen(place, id)
            val at = "$id at ${place.lat},${place.lon}"
            assertEquals(id, day.effective.entry.id, at)
            assertFalse(day.effective.measured, at)
            // A checked authority's class becomes its published method's, as anywhere it is not measured.
            assertEquals(EntryClass.D_AUTHORITY, day.effective.entryClass, at)
            // About: the published method's template, and no figure, so "Not measured for …".
            val state = assertIs<AboutTimesUiState.AuthorityUnchecked>(aboutTimesUiState(day, place, date), at)
            assertNotNull(state.stamp, at)
            assertNull(uncheckedWorstMinutes(state.resolution, state.stamp), at)
            // No time moves: the day is the entry's own in its country at the point.
            val own = Registry.resolveEntry(assertNotNull(Registry.byId(id)), assertNotNull(Registry.alsoPlace(place)))
            val its = DayPipeline.day(own, date, TimeZone.of(place.zoneId), day.school)
            assertEquals(its.fajr, day.day.fajr, "$at Fajr")
            assertEquals(its.sunrise, day.day.sunrise, "$at sunrise")
            assertEquals(its.dhuhr, day.day.dhuhr, "$at Dhuhr")
            assertEquals(its.asr, day.day.asr, "$at Asr")
            assertEquals(its.maghrib, day.day.maghrib, "$at Maghrib")
            assertEquals(its.isha, day.day.isha, "$at Isha")
            assertEquals(its.endOfEating, day.day.endOfEating, "$at end of eating")
        }
    }

    @Test
    fun `chosen at a place its area holds the timetable keeps its figure`() {
        for ((place, id) in listOf(singapore to "sg.muis", manama to "bh.council", johorBahru to "my.jakim")) {
            val day = chosen(place, id)
            val at = "$id at ${place.lat},${place.lon}"
            assertEquals(id, day.effective.entry.id, at)
            assertTrue(day.effective.measured, at)
            // MUIS's and JAKIM's checked "at most N", the Supreme Council's published method's "up to N" (class D).
            assertNotNull(measuredStartsWorst(day.effective, assertNotNull(ProofStamps.of(id))), at)
            when (val state = aboutTimesUiState(day, place, date)) {
                is AboutTimesUiState.AuthorityChecked -> assertTrue(state.resolution.measured, at)
                is AboutTimesUiState.AuthorityUnchecked -> assertNotNull(uncheckedWorstMinutes(state.resolution, state.stamp), at)
                else -> error("$at: ${state::class.simpleName}")
            }
        }
    }

    @Test
    fun `a timetable read by its circles keeps its figure across the border`() {
        // The Kyrgyz Muftiate's reach rows read each unit's whole circle, whatever the country (ReachRows.SPECS).
        val day = chosen(cholponAta, "kg.default")
        val own = Registry.resolveEntry(assertNotNull(Registry.byId("kg.default")), assertNotNull(Registry.alsoPlace(cholponAta)))
        assertEquals("kg.default", day.effective.entry.id)
        // Measured outright, not merely as the Muftiate's own resolution there is (Cholpon-Ata's unit is measured today).
        assertTrue(day.effective.measured)
        assertEquals(own.measured, day.effective.measured)
        assertEquals(own.entryClass, day.effective.entryClass)
    }
}
