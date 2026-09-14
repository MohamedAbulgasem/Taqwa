package world.taqwa.app.i18n

import world.taqwa.app.domain.HighLatitudePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighLatitudeCopyTest {

    private fun note(tag: String, polar: Boolean = false, rule: HighLatitudePreference?) =
        HighLatitudeCopy.note(tag, polar, rule)

    /**
     * The correction this task carries. The one-seventh and twilight rules bind in **summer**,
     * when a short night would otherwise put Fajr absurdly early — not in winter. The copy this
     * replaced said the sun "never sets far enough here", which describes the wrong half of the
     * year in the case a user is most likely to be reading it.
     */
    @Test
    fun theSubstitutionNoteBlamesShortSummerNightsNotAWinterSun() {
        val seventh = note("en", rule = HighLatitudePreference.SEVENTH_OF_NIGHT)!!
        assertEquals(
            "Nights are short here at this time of year. Fajr and Isha use the one-seventh rule.",
            seventh,
        )
        assertTrue(!seventh.contains("never sets far enough"))

        val twilight = note("en", rule = HighLatitudePreference.TWILIGHT_ANGLE)!!
        assertEquals(
            "Nights are short here at this time of year. Fajr and Isha use the twilight angle rule.",
            twilight,
        )
    }

    /** Polar day really is the sun not rising or setting, so that sentence is unchanged. */
    @Test
    fun thePolarNoteStillLeadsWithEveryTimeBeingSubstituted() {
        val polar = note("en", polar = true, rule = HighLatitudePreference.SEVENTH_OF_NIGHT)!!
        assertTrue(polar.startsWith("The sun does not rise or set here today."))
        assertTrue(polar.contains("All times are calculated for the nearest latitude"))
        assertTrue(polar.endsWith("with Fajr and Isha using the one-seventh rule."))
    }

    @Test
    fun theNoteIsArabicOnAnArabicDeviceWithNoEnglishLeftInIt() {
        listOf(
            note("ar-LY", rule = HighLatitudePreference.SEVENTH_OF_NIGHT)!!,
            note("ar-EG", polar = true, rule = HighLatitudePreference.TWILIGHT_ANGLE)!!,
            note("ar", rule = HighLatitudePreference.MIDDLE_OF_NIGHT)!!,
        ).forEach { arabic ->
            assertTrue(arabic.none { it in 'a'..'z' || it in 'A'..'Z' }, arabic)
            assertTrue(arabic.any { it in '؀'..'ۿ' }, arabic)
        }
    }

    @Test
    fun theArabicPolarNoteNamesTheRuleTheSameWayTheEnglishOneDoes() {
        val arabic = note("ar-LY", polar = true, rule = HighLatitudePreference.SEVENTH_OF_NIGHT)!!
        assertTrue(arabic.contains("قاعدة سُبع الليل"), arabic)
    }

    @Test
    fun noRuleInForceMeansNoNoteAtAll() {
        assertNull(note("en", rule = null))
        assertNull(note("ar", rule = null))
    }


    @Test
    fun urduAndBengaliNotesEndWithTheirOwnFullStop() {
        val ur = note("ur-PK", rule = HighLatitudePreference.SEVENTH_OF_NIGHT)!!
        assertTrue(ur.endsWith("۔"), ur)
        assertTrue(ur.contains("فجر") && ur.contains("عشاء"), ur)
        val bn = note("bn-BD", rule = HighLatitudePreference.TWILIGHT_ANGLE)!!
        assertTrue(bn.endsWith("।"), bn)
        assertTrue(bn.contains("ফজর") && bn.contains("এশা"), bn)
        val polar = note("ur-PK", polar = true, rule = HighLatitudePreference.MIDDLE_OF_NIGHT)!!
        assertTrue(!polar.contains("{"), polar)
    }
}
