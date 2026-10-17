package world.taqwa.app.settings

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ZoneIds.resolvable] turns a stored zone id into one this phone's time zone data can parse. The
 * phones it exists for (Android 9 ships tzdata 2018e, Android 8 an older release, and neither
 * changes it unless the phone's maker updates it) cannot be run here, so `known` stands in for
 * their data where a test needs one; the default `known` is this platform's own.
 */
class ZoneIdsTest {

    @Test
    fun aZoneThePhoneKnowsIsKeptAsItIs() {
        assertEquals("Europe/London", ZoneIds.resolvable("Europe/London"))
        assertEquals("Europe/Kyiv", ZoneIds.resolvable("Europe/Kyiv", known = { true }))
    }

    /** Kyiv was named Kiev until tzdata 2022b; the 2018e on an Android 9 phone has only the old name. */
    @Test
    fun aZoneThePhoneKnowsOnlyByItsOldNameReadsAsTheOldName() {
        val tzdata2018e: (String) -> Boolean = { it != "Europe/Kyiv" }

        assertEquals("Europe/Kiev", ZoneIds.resolvable("Europe/Kyiv", known = tzdata2018e, fallback = { "UTC" }))
    }

    @Test
    fun aZoneThePhoneKnowsByNoNameReadsAsThePhonesOwnZone() {
        val knowsNeither: (String) -> Boolean = { it != "Europe/Kyiv" && it != "Europe/Kiev" }

        assertEquals("Africa/Tripoli", ZoneIds.resolvable("Europe/Kyiv", known = knowsNeither, fallback = { "Africa/Tripoli" }))
        assertEquals(TimeZone.currentSystemDefault().id, ZoneIds.resolvable("Mars/Olympus_Mons"))
    }

    /** Every old name is one this platform still parses, or reading it would crash where the new one did. */
    @Test
    fun everyOldNameParsesHere() {
        assertEquals(4, ZoneIds.legacyNames.size)
        ZoneIds.legacyNames.values.forEach { TimeZone.of(it) }
    }

    /** The Location screen names the zone as picked while the stand-in keeps its clock: Kyiv, drawn in Kiev, is named Kyiv. */
    @Test
    fun theScreenNamesThePickedZoneWhileTheStandInKeepsItsClock() {
        assertEquals("Europe/Kyiv", ZoneIds.named(inUse = "Europe/Kiev", picked = "Europe/Kyiv"))
        assertEquals("Europe/London", ZoneIds.named(inUse = "Europe/London", picked = "Europe/London"))
    }

    /**
     * A zone read as the phone's own is named as that zone: every time is on its clock, and the
     * row is the clue when they look hours wrong (Nuuk on an Android 9 phone set to Tripoli).
     */
    @Test
    fun theScreenNamesThePhonesZoneWhenTheTimesAreOnIt() {
        val inUse = ZoneIds.resolvable("America/Nuuk", known = { it != "America/Nuuk" }, fallback = { "Africa/Tripoli" })

        assertEquals("Africa/Tripoli", ZoneIds.named(inUse = inUse, picked = "America/Nuuk"))
    }
}
